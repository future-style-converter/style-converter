// _shared.ts — primitives shared by the 5 BorderImage* triplets.
// border-image is the most compositionally-involved member of the border family
// (CSS Backgrounds & Borders §6).  The CSS parser emits per-property objects;
// our job is to serialise them back to the native CSS syntax.  See:
//   src/main/kotlin/app/parsing/css/properties/longhands/borders/image/*.

import type { CSSProperties } from 'react';                               // React style-object typing
import { extractLength, toCssLength, type LengthValue } from '../../core/types/LengthValue';// length parser
import { extractKeyword } from '../../core/types/KeywordValue';            // keyword normaliser

// --- border-image-source ---------------------------------------------------
// IR shapes (from examples/properties/borders/border-image.json):
//   {type:'none'}                                   the `none` keyword
//   {type:'url', url:'border.png'}                   url(...) token
//   {type:'gradient', gradient:'linear-gradient(...)'} raw gradient CSS
export type BorderImageSourceValue =
  | { kind: 'none' }                                                      // explicit none
  | { kind: 'url'; url: string }                                          // url reference
  | { kind: 'gradient'; css: string };                                    // any <image> gradient

// Parse an IR border-image-source payload; undefined when unrecognised.
export function parseBorderImageSource(data: unknown): BorderImageSourceValue | undefined {
  if (!data || typeof data !== 'object') return undefined;                // require object
  const o = data as Record<string, unknown>;                              // widen
  if (o.type === 'none') return { kind: 'none' };                         // keyword 'none'
  if (o.type === 'url' && typeof o.url === 'string') {                    // url('...')
    return { kind: 'url', url: o.url };
  }
  if (o.type === 'gradient' && typeof o.gradient === 'string') {          // gradient CSS
    return { kind: 'gradient', css: o.gradient };
  }
  return undefined;                                                        // unknown shape
}

// Serialise to the CSS `border-image-source` value.
export function borderImageSourceToCss(v: BorderImageSourceValue): string {
  if (v.kind === 'none') return 'none';                                   // keyword
  if (v.kind === 'url') return `url(${v.url})`;                            // url reference
  return v.css;                                                            // pre-formed gradient
}

// --- Per-edge structure (slice / width / outset) ---------------------------
// Each of the three properties is expressed as {top, right, bottom, left},
// each edge carrying a typed value. Edge value types vary per property.
// For slice: {type:'number', value:N} or {type:'percentage', value:N}.
// For width: {type:'length',...} | {type:'number',...} | {type:'auto'}.
// For outset: {type:'length',...} | {type:'number',...} — no auto allowed.

// Edge value after parsing (keeps enough info to emit the CSS token).
export type EdgeValue =
  | { kind: 'length'; value: LengthValue }                                // Xpx / Xem / ...
  | { kind: 'number'; value: number }                                     // bare number (unitless)
  | { kind: 'percent'; value: number }                                    // N%
  | { kind: 'auto' };                                                     // only for border-image-width

// Parse a single edge payload. `allowAuto` gates the auto branch per property.
function extractEdge(data: unknown, allowAuto: boolean): EdgeValue | undefined {
  if (!data || typeof data !== 'object') return undefined;                // require object
  const o = data as Record<string, unknown>;                              // widen
  if (o.type === 'auto') return allowAuto ? { kind: 'auto' } : undefined; // auto only on width
  if (o.type === 'number' && typeof o.value === 'number') {               // unitless number
    return { kind: 'number', value: o.value };
  }
  if (o.type === 'percentage' && typeof o.value === 'number') {           // percentage
    return { kind: 'percent', value: o.value };
  }
  if (o.type === 'length') {                                              // length (px / em / ...)
    const len = extractLength(o);                                         // reuse length parser
    if (len.kind === 'unknown') return undefined;
    return { kind: 'length', value: len };
  }
  return undefined;                                                        // unknown shape
}

// Serialise one edge value to a CSS token.
function edgeToCss(v: EdgeValue): string {
  if (v.kind === 'auto') return 'auto';
  if (v.kind === 'number') return String(v.value);                        // e.g. "30"
  if (v.kind === 'percent') return `${v.value}%`;                          // e.g. "30%"
  return toCssLength(v.value);                                             // length token
}

// A four-edge record — undefined edges mean the parser didn't emit them.
export interface QuadEdge { top?: EdgeValue; right?: EdgeValue; bottom?: EdgeValue; left?: EdgeValue; }

// Parse a `{top,right,bottom,left}` IR payload into a QuadEdge.
export function extractQuad(data: unknown, allowAuto: boolean): QuadEdge | undefined {
  if (!data || typeof data !== 'object') return undefined;                // require object
  const o = data as Record<string, unknown>;                              // widen
  const t = extractEdge(o.top, allowAuto);                                // parse each edge
  const r = extractEdge(o.right, allowAuto);
  const b = extractEdge(o.bottom, allowAuto);
  const l = extractEdge(o.left, allowAuto);
  if (!t && !r && !b && !l) return undefined;                             // nothing recognised
  const out: QuadEdge = {};
  if (t) out.top = t;
  if (r) out.right = r;
  if (b) out.bottom = b;
  if (l) out.left = l;
  return out;
}

// Serialise a QuadEdge to the CSS 1-to-4-value shorthand.
// CSS accepts any count 1..4; we always emit all four tokens so the renderer
// never has to worry about CSS defaulting rules.
export function quadToCss(v: QuadEdge): string {
  const fill = (e?: EdgeValue): string => (e ? edgeToCss(e) : '0');       // missing -> CSS default 0
  return `${fill(v.top)} ${fill(v.right)} ${fill(v.bottom)} ${fill(v.left)}`;
}

// --- border-image-repeat ---------------------------------------------------
// IR flavors: a single UPPERCASE keyword or a {horizontal, vertical} pair.
const REPEAT_KEYWORDS = new Set(['stretch', 'repeat', 'round', 'space']);  // CSS B&B §6.6

// Parse repeat IR: {horizontal, vertical} pair or bare keyword.
export function parseBorderImageRepeat(data: unknown): { horizontal: string; vertical: string } | undefined {
  if (data && typeof data === 'object' && !Array.isArray(data)) {         // pair form
    const o = data as Record<string, unknown>;
    const h = extractKeyword(o.horizontal)?.normalized;                   // normalise
    const v = extractKeyword(o.vertical)?.normalized;                     // normalise
    if (h && v && REPEAT_KEYWORDS.has(h) && REPEAT_KEYWORDS.has(v)) {     // both valid
      return { horizontal: h, vertical: v };
    }
  }
  const kw = extractKeyword(data)?.normalized;                            // single keyword
  if (kw && REPEAT_KEYWORDS.has(kw)) return { horizontal: kw, vertical: kw };
  return undefined;                                                        // unknown
}

// Serialise repeat back to CSS.  When both axes equal, CSS accepts either
// "keyword" or "keyword keyword" — we emit the compact form.
export function borderImageRepeatToCss(v: { horizontal: string; vertical: string }): string {
  if (v.horizontal === v.vertical) return v.horizontal;                   // single token form
  return `${v.horizontal} ${v.vertical}`;                                  // two-token form
}

// --- Single-key emit helpers ----------------------------------------------
// Keep appliers one-line.  React/csstype recognises every key below.
export function emitSource(v?: BorderImageSourceValue): Partial<CSSProperties> {
  return v ? { borderImageSource: borderImageSourceToCss(v) } : {};
}
export function emitSlice(v?: QuadEdge): Partial<CSSProperties> {
  return v ? { borderImageSlice: quadToCss(v) } : {};
}
export function emitWidth(v?: QuadEdge): Partial<CSSProperties> {
  return v ? { borderImageWidth: quadToCss(v) } : {};
}
export function emitOutset(v?: QuadEdge): Partial<CSSProperties> {
  return v ? { borderImageOutset: quadToCss(v) } : {};
}
export function emitRepeat(v?: { horizontal: string; vertical: string }): Partial<CSSProperties> {
  return v ? { borderImageRepeat: borderImageRepeatToCss(v) } : {};
}

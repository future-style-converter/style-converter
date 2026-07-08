// _shared.ts — primitives shared by the 24 Border*Side property triplets.
// Per-side border rendering on the web is trivial (every side maps to a native
// CSS property) so all the interesting logic — IR-shape tolerance, keyword
// resolution, dynamic-color passthrough — lives here and the individual
// triplet files are thin adapters.
//
// Why share?  Each of the 24 triplets (4 physical + 4 logical × 3 sub-kinds)
// repeats the same parse-and-emit dance; centralising keeps every file well
// under the 200-line ceiling from CLAUDE.md → *Per-property contract*.

import type { CSSProperties } from 'react';                               // typed keys of React style object
import { extractLength, toCssLength, type LengthValue } from '../../core/types/LengthValue';// width values
import { extractColor, type ColorValue } from '../../core/types/ColorValue';// color values
import { colorToCss } from '../../color/DynamicColorCss';                 // dynamic-color reconstruction
import { extractKeyword } from '../../core/types/KeywordValue';            // style keyword parse

// -- Width ------------------------------------------------------------------
// The CSS parser already resolves `thin|medium|thick` to px (IR carries
// {px:1, original:'thin'} — see examples/properties/borders/border-widths.json
// after ./gradlew run). Negative or unknown inputs fall back to `0` so the
// applier never emits a value CSS would reject.  Mirrors the flavors in
// src/main/kotlin/app/parsing/css/properties/longhands/borders/sides/BorderTopWidthPropertyParser.kt.
export function extractBorderSideWidth(data: unknown): LengthValue | undefined {
  const v = extractLength(data);                                          // reuse shared length parser
  if (v.kind === 'unknown') return undefined;                             // no value recognised
  // Auto isn't valid for border widths per spec — drop to keep output clean.
  if (v.kind === 'auto') return undefined;                                // CSS rejects `auto` for width
  return v;                                                               // exact/relative/calc all OK
}

// Turn a width value into the CSS string we'll emit (just delegates to toCssLength).
export function borderSideWidthToCss(v: LengthValue): string {
  return toCssLength(v);                                                  // "2px" / "0.5em" / "calc(2px + 4px)"
}

// -- Style ------------------------------------------------------------------
// Valid CSS border-style keywords, per the CSS Backgrounds & Borders spec §5.
// The IR emits UPPERCASE bare strings (see `BorderTopStyle: "SOLID"`), so
// we lowercase + validate to keep only recognised values.
const BORDER_STYLES = new Set<string>([
  'none', 'hidden', 'dotted', 'dashed', 'solid',                          // single-line styles
  'double', 'groove', 'ridge', 'inset', 'outset',                         // multi-line + 3d styles
]);

// Returns a lowercased keyword when it matches the CSS spec, otherwise undefined.
export function extractBorderSideStyle(data: unknown): string | undefined {
  const kw = extractKeyword(data);                                         // shared keyword normaliser
  if (!kw) return undefined;                                              // no keyword found
  return BORDER_STYLES.has(kw.normalized) ? kw.normalized : undefined;    // reject garbage
}

// -- Color ------------------------------------------------------------------
// Border colors share the ColorValue alphabet with BackgroundColor/Color
// (Phase 4). Dynamic colors (currentColor / color-mix / light-dark / relative
// / var) passthrough via DynamicColorCss so rtl/light-dark still work at paint time.
export function extractBorderSideColor(data: unknown): ColorValue | undefined {
  const c = extractColor(data);                                           // shared color parser
  return c.kind === 'unknown' ? undefined : c;                            // drop unparseable
}

// Serialise a ColorValue to CSS — same path as BackgroundColorApplier.
export function borderSideColorToCss(c: ColorValue): string {
  return colorToCss(c);                                                   // rgba(...) or dynamic reconstruction
}

// -- Output surface ----------------------------------------------------------
// Every side applier returns a partial CSSProperties keyed on the single CSS
// property it owns.  Picking the subset gives us strong typing without
// pulling in unrelated keys.
export type BorderSideWidthKey =
  | 'borderTopWidth' | 'borderRightWidth' | 'borderBottomWidth' | 'borderLeftWidth'
  | 'borderBlockStartWidth' | 'borderBlockEndWidth'
  | 'borderInlineStartWidth' | 'borderInlineEndWidth';
export type BorderSideStyleKey =
  | 'borderTopStyle' | 'borderRightStyle' | 'borderBottomStyle' | 'borderLeftStyle'
  | 'borderBlockStartStyle' | 'borderBlockEndStyle'
  | 'borderInlineStartStyle' | 'borderInlineEndStyle';
export type BorderSideColorKey =
  | 'borderTopColor' | 'borderRightColor' | 'borderBottomColor' | 'borderLeftColor'
  | 'borderBlockStartColor' | 'borderBlockEndColor'
  | 'borderInlineStartColor' | 'borderInlineEndColor';

// Helpers that build single-key output objects — thin wrappers so each
// applier is just one function call.
export function emitWidth(key: BorderSideWidthKey, v?: LengthValue): Partial<CSSProperties> {
  if (!v) return {};                                                      // keep output object minimal
  return { [key]: borderSideWidthToCss(v) } as Partial<CSSProperties>;    // cast: single typed key
}
export function emitStyle(key: BorderSideStyleKey, v?: string): Partial<CSSProperties> {
  if (!v) return {};                                                      // unset -> no emit
  return { [key]: v } as Partial<CSSProperties>;                          // keyword already validated
}
export function emitColor(key: BorderSideColorKey, v?: ColorValue): Partial<CSSProperties> {
  if (!v) return {};                                                      // no color set
  return { [key]: borderSideColorToCss(v) } as Partial<CSSProperties>;    // CSS string
}

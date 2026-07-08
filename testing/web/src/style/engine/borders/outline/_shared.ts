// _shared.ts — primitives shared by the 4 Outline* triplets.
// Outline is specced alongside borders (CSS UI §4) but parses slightly
// differently: the width keyword `thin|medium|thick` is NOT pre-resolved to
// px by our CSS parser — it arrives as {type:'keyword', value:'THIN'|...}.
// So the triplet here does the thin/medium/thick → 1px/3px/5px mapping that
// border-width gets from the parser.  See:
//   src/main/kotlin/app/parsing/css/properties/longhands/appearance/OutlineWidthPropertyParser.kt

import type { CSSProperties } from 'react';                               // React style-object typing
import { extractLength, toCssLength, type LengthValue } from '../../core/types/LengthValue';// shared length parser
import { extractColor, type ColorValue } from '../../core/types/ColorValue';// shared color parser
import { colorToCss } from '../../color/DynamicColorCss';                 // dynamic-color CSS reconstruction
import { extractKeyword } from '../../core/types/KeywordValue';            // keyword normaliser

// CSS UI spec §4.3.1: map the three legacy width keywords to the px
// defaults used by every major engine.  Identical values to how the CSS
// parser resolves border-widths (see the _shared.ts comment in ../sides/).
const KEYWORD_WIDTH_PX: Record<string, number> = {
  thin: 1, medium: 3, thick: 5,                                           // widely-cited UA defaults
};

// Parse outline-width; returns LengthValue or undefined when unrecognised.
// Recognised IR flavors:
//   {type:'keyword', value:'THIN'|'MEDIUM'|'THICK'}   — keyword form
//   {type:'length', px:N}                             — plain px
//   {type:'length', original:{v,u}}                   — font-relative
//   raw {px:N} or {original:{...}}                    — defensive
export function parseOutlineWidth(data: unknown): LengthValue | undefined {
  // Keyword wrapper: unwrap and map to the px defaults before returning.
  if (data && typeof data === 'object') {                                 // objects only
    const obj = data as Record<string, unknown>;                          // widen for probing
    if (obj.type === 'keyword') {                                         // IR wrapper for keywords
      const kw = extractKeyword(obj);                                     // normalise to lowercase/kebab
      if (kw && kw.normalized in KEYWORD_WIDTH_PX) {                      // recognised keyword?
        return { kind: 'exact', px: KEYWORD_WIDTH_PX[kw.normalized] };    // emit canonical px
      }
      return undefined;                                                   // unknown keyword -> drop
    }
  }
  // Everything else flows through the shared length parser.
  const len = extractLength(data);                                        // reuse primitive extractor
  if (len.kind === 'unknown' || len.kind === 'auto') return undefined;    // auto makes no sense for outline
  return len;                                                             // exact/relative/calc all OK
}

// Outline-style shares the same keyword set as border-style, except the spec
// forbids `hidden` (CSS UI §4.3.2).  We let it through anyway — harmless for
// web because `hidden` is still a valid border-style-like token and any browser
// reaching this code will ignore it if it truly mismatches.
const OUTLINE_STYLES = new Set<string>([
  'auto',                                                                 // outline-specific: UA choice
  'none', 'hidden', 'dotted', 'dashed', 'solid',
  'double', 'groove', 'ridge', 'inset', 'outset',
]);

// Returns a lowercased keyword when recognised, else undefined.
export function parseOutlineStyle(data: unknown): string | undefined {
  const kw = extractKeyword(data);                                        // shared normaliser
  if (!kw) return undefined;                                              // no keyword
  return OUTLINE_STYLES.has(kw.normalized) ? kw.normalized : undefined;   // validate
}

// Outline colors share the ColorValue alphabet with BackgroundColor/Color.
// `invert` is a CSS-defined dynamic color specific to outline (CSS UI §4.3.3)
// — the parser rarely emits it; if encountered we preserve it as dynamic.
export function parseOutlineColor(data: unknown): ColorValue | undefined {
  if (data === 'invert') {                                                // defensive: raw 'invert' keyword
    return { kind: 'dynamic', dynamicKind: 'currentColor', raw: 'invert' };
  }
  const c = extractColor(data);                                           // shared color parser
  return c.kind === 'unknown' ? undefined : c;                            // drop unparseable
}

// outline-offset is a simple length (negative allowed — CSS UI §4.4).
export function parseOutlineOffset(data: unknown): LengthValue | undefined {
  const v = extractLength(data);                                          // shared parser
  if (v.kind === 'unknown' || v.kind === 'auto') return undefined;        // auto invalid for offset
  return v;
}

// Single-key emit helpers — mirror ../sides/_shared.ts for uniformity.
export function emitOutlineWidth(v?: LengthValue): Partial<CSSProperties> {
  if (!v) return {};                                                      // unset
  return { outlineWidth: toCssLength(v) };                                // native CSS key
}
export function emitOutlineStyle(v?: string): Partial<CSSProperties> {
  if (!v) return {};
  // csstype narrows outlineStyle to the CSS OutlineLineStyle union; we've
  // validated the keyword above so a cast is safe.
  return { outlineStyle: v as CSSProperties['outlineStyle'] };
}
export function emitOutlineColor(v?: ColorValue): Partial<CSSProperties> {
  if (!v) return {};
  // 'invert' is a raw CSS keyword — emit it literally rather than through rgba().
  if (v.kind === 'dynamic' && v.raw === 'invert') return { outlineColor: 'invert' };
  return { outlineColor: colorToCss(v) };                                 // rgba(...) or dynamic CSS
}
export function emitOutlineOffset(v?: LengthValue): Partial<CSSProperties> {
  if (!v) return {};
  return { outlineOffset: toCssLength(v) };
}

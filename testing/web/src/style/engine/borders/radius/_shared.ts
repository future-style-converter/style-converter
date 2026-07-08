// _shared.ts — primitives shared by the 8 Border*Radius corner triplets.
// Physical corners: BorderTopLeftRadius / BorderTopRightRadius /
// BorderBottomRightRadius / BorderBottomLeftRadius.
// Logical corners (CSS Logical Properties L1 §3.2):
// BorderStartStartRadius / BorderStartEndRadius /
// BorderEndStartRadius  / BorderEndEndRadius.
//
// CSS border-radius accepts either a single length/percentage (circular corner)
// or a pair for elliptical corners: `40px 20px`.  The CSS parser emits one of:
//   {px:N}                                            simple px
//   {original:{v,u}}                                  font-relative / percent
//   {type:'percentage', value:N}  or  v/u PERCENT     % via original
//   {horizontal:<Len>, vertical:<Len>}                elliptical pair
// See src/main/kotlin/app/parsing/css/properties/longhands/borders/radius/BorderTopLeftRadiusPropertyParser.kt.

import type { CSSProperties } from 'react';                               // React CSS key typing
import { extractLength, toCssLength, type LengthValue } from '../../core/types/LengthValue';// shared length parser

// Config value: either a single length (circular) or a horizontal/vertical
// pair (elliptical).  Keeping both in the union lets the applier emit the
// correct CSS form without re-parsing.
export type BorderRadiusValue =
  | { kind: 'single'; value: LengthValue }                                // circle radius
  | { kind: 'pair'; horizontal: LengthValue; vertical: LengthValue };     // ellipse radii

// Parse one IR payload into a BorderRadiusValue, or undefined if unrecognised.
// The elliptical pair only appears when the CSS was per-corner `40px 20px`
// (see examples/properties/borders/border-radius-physical.json Radius_Elliptical_*).
export function extractBorderCornerRadius(data: unknown): BorderRadiusValue | undefined {
  // Pair shape — presence of `horizontal` + `vertical` is the discriminator.
  if (data && typeof data === 'object') {                                 // guard: only objects can be pairs
    const obj = data as Record<string, unknown>;                          // widen for property probing
    if ('horizontal' in obj && 'vertical' in obj) {                       // elliptical form
      const h = extractLength(obj.horizontal);                            // parse horizontal
      const v = extractLength(obj.vertical);                              // parse vertical
      if (h.kind === 'unknown' || v.kind === 'unknown') return undefined; // drop if either fails
      return { kind: 'pair', horizontal: h, vertical: v };                // valid pair
    }
  }
  // Fall-through: single circular radius.
  const single = extractLength(data);                                     // generic length parser
  if (single.kind === 'unknown') return undefined;                        // unparseable -> drop
  return { kind: 'single', value: single };                               // circular
}

// Serialise a BorderRadiusValue to a CSS string.
// - Circular: emit "16px" / "50%" / "1rem" / "calc(...)".
// - Elliptical: emit "<h> <v>" which CSS parses as per-axis radii.
// CSS border-*-radius natively accepts both forms (BB&B spec §5.5).
export function borderRadiusToCss(v: BorderRadiusValue): string {
  if (v.kind === 'single') return toCssLength(v.value);                   // simple length
  return `${toCssLength(v.horizontal)} ${toCssLength(v.vertical)}`;        // space-separated pair
}

// CSS output keys the 8 corner appliers can target.  Typed so TS rejects typos.
export type BorderRadiusKey =
  | 'borderTopLeftRadius' | 'borderTopRightRadius'
  | 'borderBottomRightRadius' | 'borderBottomLeftRadius'
  | 'borderStartStartRadius' | 'borderStartEndRadius'
  | 'borderEndStartRadius' | 'borderEndEndRadius';

// Small helper so every applier is one function call.
export function emitRadius(
  key: BorderRadiusKey,
  v?: BorderRadiusValue,
): Partial<CSSProperties> {
  if (!v) return {};                                                      // unset -> empty object
  return { [key]: borderRadiusToCss(v) } as Partial<CSSProperties>;       // single-key partial
}

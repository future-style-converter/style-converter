// BorderImageSliceExtractor.ts — folds `BorderImageSlice` IR properties into a config.
// IR shape flavors: a `{top,right,bottom,left, fill}` object where each edge is
//   {type:'number', value:N}       bare number (offset in image pixels)
//   {type:'percentage', value:N}    percentage of the image side length
// plus `fill: true|false` — the optional `fill` keyword of the grammar
// (`<number-percentage>{1,4} && fill?`, css-backgrounds-3 §6.1) carried by
// BorderImageSliceProperty.kt; dropping it erases the painted middle region.
// See CSS B&B §6.2.

import { extractQuad } from './_shared';                             // shared parse/validate
import type { BorderImageSliceConfig, BorderImageSlicePropertyType } from './BorderImageSliceConfig';
import { BORDER_IMAGE_SLICE_PROPERTY_TYPE } from './BorderImageSliceConfig';

// Minimal IRProperty shape — keeps engine module decoupled from IR types.
interface IRPropertyLike { type: string; data: unknown; }

// Type-narrowing predicate — used by registry/renderer.
export function isBorderImageSliceProperty(type: string): type is BorderImageSlicePropertyType {
  return type === BORDER_IMAGE_SLICE_PROPERTY_TYPE;
}

// Main entrypoint — last-write-wins fold.
export function extractBorderImageSlice(properties: IRPropertyLike[]): BorderImageSliceConfig {
  const cfg: BorderImageSliceConfig = {};                                         // blank accumulator
  for (const p of properties) {                                          // single pass
    if (!isBorderImageSliceProperty(p.type)) continue;                              // skip unrelated
    const v = extractQuad(p.data, false);                                                     // validate & parse
    if (v) {                                                       // last recognised wins
      cfg.quad = v;                                                // stamp the quad
      // Carry the `fill` keyword alongside the quad — the IR always emits the
      // boolean (default false, see BorderImageSliceProperty.kt), and the
      // applier must append the trailing ' fill' token or the browser paints
      // no middle region while both natives correctly do.
      cfg.fill = (p.data as Record<string, unknown>).fill === true; // strict boolean read
    }
  }
  return cfg;
}

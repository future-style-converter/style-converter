// BorderImageSliceExtractor.ts — folds `BorderImageSlice` IR properties into a config.
// IR shape flavors: a `{top,right,bottom,left}` object where each edge is
//   {type:'number', value:N}       bare number (offset in image pixels)
//   {type:'percentage', value:N}    percentage of the image side length
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
    if (v) cfg.quad = v;                                           // last recognised wins
  }
  return cfg;
}

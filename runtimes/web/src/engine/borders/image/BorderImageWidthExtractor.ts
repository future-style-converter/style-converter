// BorderImageWidthExtractor.ts — folds `BorderImageWidth` IR properties into a config.
// IR shape flavors: `{top,right,bottom,left}` object, each edge is
//   {type:'length', ...}  |  {type:'number', value:N}  |  {type:'auto'}
// border-image-width is the only BorderImage* that allows `auto` per edge
// (CSS B&B §6.4).  The shared extractor's `allowAuto=true` branch gates it.

import { extractQuad } from './_shared';                             // shared parse/validate
import type { BorderImageWidthConfig, BorderImageWidthPropertyType } from './BorderImageWidthConfig';
import { BORDER_IMAGE_WIDTH_PROPERTY_TYPE } from './BorderImageWidthConfig';

// Minimal IRProperty shape — keeps engine module decoupled from IR types.
interface IRPropertyLike { type: string; data: unknown; }

// Type-narrowing predicate — used by registry/renderer.
export function isBorderImageWidthProperty(type: string): type is BorderImageWidthPropertyType {
  return type === BORDER_IMAGE_WIDTH_PROPERTY_TYPE;
}

// Main entrypoint — last-write-wins fold.
export function extractBorderImageWidth(properties: IRPropertyLike[]): BorderImageWidthConfig {
  const cfg: BorderImageWidthConfig = {};                                         // blank accumulator
  for (const p of properties) {                                          // single pass
    if (!isBorderImageWidthProperty(p.type)) continue;                              // skip unrelated
    const v = extractQuad(p.data, true);                                                     // validate & parse
    if (v) cfg.quad = v;                                           // last recognised wins
  }
  return cfg;
}

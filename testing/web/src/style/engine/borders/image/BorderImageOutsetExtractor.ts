// BorderImageOutsetExtractor.ts — folds `BorderImageOutset` IR properties into a config.
// IR shape flavors: `{top,right,bottom,left}`, each edge is
//   {type:'length', ...}  |  {type:'number', value:N}
// No `auto` allowed (CSS B&B §6.5).

import { extractQuad } from './_shared';                             // shared parse/validate
import type { BorderImageOutsetConfig, BorderImageOutsetPropertyType } from './BorderImageOutsetConfig';
import { BORDER_IMAGE_OUTSET_PROPERTY_TYPE } from './BorderImageOutsetConfig';

// Minimal IRProperty shape — keeps engine module decoupled from IR types.
interface IRPropertyLike { type: string; data: unknown; }

// Type-narrowing predicate — used by registry/renderer.
export function isBorderImageOutsetProperty(type: string): type is BorderImageOutsetPropertyType {
  return type === BORDER_IMAGE_OUTSET_PROPERTY_TYPE;
}

// Main entrypoint — last-write-wins fold.
export function extractBorderImageOutset(properties: IRPropertyLike[]): BorderImageOutsetConfig {
  const cfg: BorderImageOutsetConfig = {};                                         // blank accumulator
  for (const p of properties) {                                          // single pass
    if (!isBorderImageOutsetProperty(p.type)) continue;                              // skip unrelated
    const v = extractQuad(p.data, false);                                                     // validate & parse
    if (v) cfg.quad = v;                                           // last recognised wins
  }
  return cfg;
}

// BorderImageRepeatExtractor.ts — folds `BorderImageRepeat` IR properties into a config.
// IR shape flavors: single UPPERCASE keyword ("STRETCH"|"REPEAT"|"ROUND"|"SPACE")
// or a pair {horizontal:"REPEAT", vertical:"STRETCH"} for two-value form.
// See CSS B&B §6.6.

import { parseBorderImageRepeat } from './_shared';                             // shared parse/validate
import type { BorderImageRepeatConfig, BorderImageRepeatPropertyType } from './BorderImageRepeatConfig';
import { BORDER_IMAGE_REPEAT_PROPERTY_TYPE } from './BorderImageRepeatConfig';

// Minimal IRProperty shape — keeps engine module decoupled from IR types.
interface IRPropertyLike { type: string; data: unknown; }

// Type-narrowing predicate — used by registry/renderer.
export function isBorderImageRepeatProperty(type: string): type is BorderImageRepeatPropertyType {
  return type === BORDER_IMAGE_REPEAT_PROPERTY_TYPE;
}

// Main entrypoint — last-write-wins fold.
export function extractBorderImageRepeat(properties: IRPropertyLike[]): BorderImageRepeatConfig {
  const cfg: BorderImageRepeatConfig = {};                                         // blank accumulator
  for (const p of properties) {                                          // single pass
    if (!isBorderImageRepeatProperty(p.type)) continue;                              // skip unrelated
    const v = parseBorderImageRepeat(p.data);                                                     // validate & parse
    if (v) cfg.repeat = v;                                           // last recognised wins
  }
  return cfg;
}

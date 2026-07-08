// OutlineOffsetExtractor.ts — folds `OutlineOffset` IR properties into a config.
// IR shape flavors: plain length, negative allowed (CSS UI §4.4)
//   {px:N} / {px:-N}                      inset/outset offset
//   {original:{v,u:'REM'|...}}            font-relative offset

import { parseOutlineOffset } from './_shared';                             // shared parse/validate
import type { OutlineOffsetConfig, OutlineOffsetPropertyType } from './OutlineOffsetConfig';
import { OUTLINE_OFFSET_PROPERTY_TYPE } from './OutlineOffsetConfig';

// Minimal IRProperty shape — keeps engine module decoupled from IR types.
interface IRPropertyLike { type: string; data: unknown; }

// Type-narrowing predicate — used by registry/renderer.
export function isOutlineOffsetProperty(type: string): type is OutlineOffsetPropertyType {
  return type === OUTLINE_OFFSET_PROPERTY_TYPE;
}

// Main entrypoint — last-write-wins fold.
export function extractOutlineOffset(properties: IRPropertyLike[]): OutlineOffsetConfig {
  const cfg: OutlineOffsetConfig = {};                                         // blank accumulator
  for (const p of properties) {                                          // single pass
    if (!isOutlineOffsetProperty(p.type)) continue;                              // skip unrelated
    const v = parseOutlineOffset(p.data);                                     // validate & parse
    if (v) cfg.offset = v;                                           // last recognised wins
  }
  return cfg;
}

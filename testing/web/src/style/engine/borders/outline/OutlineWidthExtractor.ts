// OutlineWidthExtractor.ts — folds `OutlineWidth` IR properties into a config.
// IR shape flavors (from examples/properties/borders/outline.json):
//   {type:'keyword', value:'THIN'|'MEDIUM'|'THICK'}   keyword form (UA-defined px)
//   {type:'length', px:N}                             plain px
//   {type:'length', original:{v,u:'REM'|...}}         font-relative
// Keyword → px resolution lives in _shared.ts (CSS UI §4.3.1 defaults).

import { parseOutlineWidth } from './_shared';                             // shared parse/validate
import type { OutlineWidthConfig, OutlineWidthPropertyType } from './OutlineWidthConfig';
import { OUTLINE_WIDTH_PROPERTY_TYPE } from './OutlineWidthConfig';

// Minimal IRProperty shape — keeps engine module decoupled from IR types.
interface IRPropertyLike { type: string; data: unknown; }

// Type-narrowing predicate — used by registry/renderer.
export function isOutlineWidthProperty(type: string): type is OutlineWidthPropertyType {
  return type === OUTLINE_WIDTH_PROPERTY_TYPE;
}

// Main entrypoint — last-write-wins fold.
export function extractOutlineWidth(properties: IRPropertyLike[]): OutlineWidthConfig {
  const cfg: OutlineWidthConfig = {};                                         // blank accumulator
  for (const p of properties) {                                          // single pass
    if (!isOutlineWidthProperty(p.type)) continue;                              // skip unrelated
    const v = parseOutlineWidth(p.data);                                     // validate & parse
    if (v) cfg.width = v;                                           // last recognised wins
  }
  return cfg;
}

// OutlineColorExtractor.ts — folds `OutlineColor` IR properties into a config.
// IR shape flavors: shared with BackgroundColor (Phase 4)
//   {srgb:{r,g,b[,a]}, original:...}      static sRGB
//   {original:"currentColor"}             dynamic currentColor
//   "invert" (raw)                        outline-specific dynamic keyword (CSS UI §4.3.3)

import { parseOutlineColor } from './_shared';                             // shared parse/validate
import type { OutlineColorConfig, OutlineColorPropertyType } from './OutlineColorConfig';
import { OUTLINE_COLOR_PROPERTY_TYPE } from './OutlineColorConfig';

// Minimal IRProperty shape — keeps engine module decoupled from IR types.
interface IRPropertyLike { type: string; data: unknown; }

// Type-narrowing predicate — used by registry/renderer.
export function isOutlineColorProperty(type: string): type is OutlineColorPropertyType {
  return type === OUTLINE_COLOR_PROPERTY_TYPE;
}

// Main entrypoint — last-write-wins fold.
export function extractOutlineColor(properties: IRPropertyLike[]): OutlineColorConfig {
  const cfg: OutlineColorConfig = {};                                         // blank accumulator
  for (const p of properties) {                                          // single pass
    if (!isOutlineColorProperty(p.type)) continue;                              // skip unrelated
    const v = parseOutlineColor(p.data);                                     // validate & parse
    if (v) cfg.color = v;                                           // last recognised wins
  }
  return cfg;
}

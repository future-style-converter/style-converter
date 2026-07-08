// BorderBlockEndColorExtractor.ts — folds `BorderBlockEndColor` IR properties into a config.
// IR shape flavors (from examples/properties/borders/border-colors.json):
//   {srgb:{r,g,b[,a]}, original:"#ff3366"|"crimson"|...}  static sRGB
//   {original:"currentColor"}                             dynamic: currentColor
//   {original:{type:'color-mix'|'light-dark'|'relative'|'var', ...}} dynamic forms
// Shares the ColorValue alphabet with BackgroundColor/Color (Phase 4).

import { extractBorderSideColor } from './_shared';                       // shared parse/validate logic
import type { BorderBlockEndColorConfig, BorderBlockEndColorPropertyType } from './BorderBlockEndColorConfig';
import { BORDER_BLOCK_END_COLOR_PROPERTY_TYPE } from './BorderBlockEndColorConfig';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Type-narrowing predicate — used by the registry/renderer for dispatch gating.
export function isBorderBlockEndColorProperty(type: string): type is BorderBlockEndColorPropertyType {
  return type === BORDER_BLOCK_END_COLOR_PROPERTY_TYPE;
}

// Main entrypoint — last-write-wins fold (CSS cascade is already resolved upstream).
export function extractBorderBlockEndColor(properties: IRPropertyLike[]): BorderBlockEndColorConfig {
  const cfg: BorderBlockEndColorConfig = {};                                   // blank accumulator
  for (const p of properties) {                                         // single pass
    if (!isBorderBlockEndColorProperty(p.type)) continue;                                // skip unrelated
    const v = extractBorderSideColor(p.data);                                     // validate & parse
    if (v) cfg.color = v;                                           // last recognised value wins
  }
  return cfg;
}

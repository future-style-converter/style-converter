// BorderRightColorExtractor.ts — folds `BorderRightColor` IR properties into a config.
// IR shape flavors (from examples/properties/borders/border-colors.json):
//   {srgb:{r,g,b[,a]}, original:"#ff3366"|"crimson"|...}  static sRGB
//   {original:"currentColor"}                             dynamic: currentColor
//   {original:{type:'color-mix'|'light-dark'|'relative'|'var', ...}} dynamic forms
// Shares the ColorValue alphabet with BackgroundColor/Color (Phase 4).

import { extractBorderSideColor } from './_shared';                       // shared parse/validate logic
import type { BorderRightColorConfig, BorderRightColorPropertyType } from './BorderRightColorConfig';
import { BORDER_RIGHT_COLOR_PROPERTY_TYPE } from './BorderRightColorConfig';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Type-narrowing predicate — used by the registry/renderer for dispatch gating.
export function isBorderRightColorProperty(type: string): type is BorderRightColorPropertyType {
  return type === BORDER_RIGHT_COLOR_PROPERTY_TYPE;
}

// Main entrypoint — last-write-wins fold (CSS cascade is already resolved upstream).
export function extractBorderRightColor(properties: IRPropertyLike[]): BorderRightColorConfig {
  const cfg: BorderRightColorConfig = {};                                   // blank accumulator
  for (const p of properties) {                                         // single pass
    if (!isBorderRightColorProperty(p.type)) continue;                                // skip unrelated
    const v = extractBorderSideColor(p.data);                                     // validate & parse
    if (v) cfg.color = v;                                           // last recognised value wins
  }
  return cfg;
}

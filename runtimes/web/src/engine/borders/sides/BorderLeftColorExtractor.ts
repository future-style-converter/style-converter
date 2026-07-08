// BorderLeftColorExtractor.ts — folds `BorderLeftColor` IR properties into a config.
// IR shape flavors (from examples/properties/borders/border-colors.json):
//   {srgb:{r,g,b[,a]}, original:"#ff3366"|"crimson"|...}  static sRGB
//   {original:"currentColor"}                             dynamic: currentColor
//   {original:{type:'color-mix'|'light-dark'|'relative'|'var', ...}} dynamic forms
// Shares the ColorValue alphabet with BackgroundColor/Color (Phase 4).

import { extractBorderSideColor } from './_shared';                       // shared parse/validate logic
import type { BorderLeftColorConfig, BorderLeftColorPropertyType } from './BorderLeftColorConfig';
import { BORDER_LEFT_COLOR_PROPERTY_TYPE } from './BorderLeftColorConfig';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Type-narrowing predicate — used by the registry/renderer for dispatch gating.
export function isBorderLeftColorProperty(type: string): type is BorderLeftColorPropertyType {
  return type === BORDER_LEFT_COLOR_PROPERTY_TYPE;
}

// Main entrypoint — last-write-wins fold (CSS cascade is already resolved upstream).
export function extractBorderLeftColor(properties: IRPropertyLike[]): BorderLeftColorConfig {
  const cfg: BorderLeftColorConfig = {};                                   // blank accumulator
  for (const p of properties) {                                         // single pass
    if (!isBorderLeftColorProperty(p.type)) continue;                                // skip unrelated
    const v = extractBorderSideColor(p.data);                                     // validate & parse
    if (v) cfg.color = v;                                           // last recognised value wins
  }
  return cfg;
}

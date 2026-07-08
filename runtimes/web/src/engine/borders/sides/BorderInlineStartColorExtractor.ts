// BorderInlineStartColorExtractor.ts — folds `BorderInlineStartColor` IR properties into a config.
// IR shape flavors (from examples/properties/borders/border-colors.json):
//   {srgb:{r,g,b[,a]}, original:"#ff3366"|"crimson"|...}  static sRGB
//   {original:"currentColor"}                             dynamic: currentColor
//   {original:{type:'color-mix'|'light-dark'|'relative'|'var', ...}} dynamic forms
// Shares the ColorValue alphabet with BackgroundColor/Color (Phase 4).

import { extractBorderSideColor } from './_shared';                       // shared parse/validate logic
import type { BorderInlineStartColorConfig, BorderInlineStartColorPropertyType } from './BorderInlineStartColorConfig';
import { BORDER_INLINE_START_COLOR_PROPERTY_TYPE } from './BorderInlineStartColorConfig';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Type-narrowing predicate — used by the registry/renderer for dispatch gating.
export function isBorderInlineStartColorProperty(type: string): type is BorderInlineStartColorPropertyType {
  return type === BORDER_INLINE_START_COLOR_PROPERTY_TYPE;
}

// Main entrypoint — last-write-wins fold (CSS cascade is already resolved upstream).
export function extractBorderInlineStartColor(properties: IRPropertyLike[]): BorderInlineStartColorConfig {
  const cfg: BorderInlineStartColorConfig = {};                                   // blank accumulator
  for (const p of properties) {                                         // single pass
    if (!isBorderInlineStartColorProperty(p.type)) continue;                                // skip unrelated
    const v = extractBorderSideColor(p.data);                                     // validate & parse
    if (v) cfg.color = v;                                           // last recognised value wins
  }
  return cfg;
}

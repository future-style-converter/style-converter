// BorderBlockEndWidthExtractor.ts — folds `BorderBlockEndWidth` IR properties into a config.
// IR shape flavors (from examples/properties/borders/border-widths.json after
// ./gradlew run conversion):
//   {px:1, original:"thin"}               keyword pre-resolved by parser
//   {px:6.0}                              plain numeric px
//   {original:{v,u:'EM'|'REM'...}}        font-relative (parser keeps original)
//   {expr:'calc(2px + 4px)'}              calc — carried as raw expression
// See primitive extractor core/types/LengthValue.ts for the full alphabet.

import { extractBorderSideWidth } from './_shared';                       // shared parse/validate logic
import type { BorderBlockEndWidthConfig, BorderBlockEndWidthPropertyType } from './BorderBlockEndWidthConfig';
import { BORDER_BLOCK_END_WIDTH_PROPERTY_TYPE } from './BorderBlockEndWidthConfig';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Type-narrowing predicate — used by the registry/renderer for dispatch gating.
export function isBorderBlockEndWidthProperty(type: string): type is BorderBlockEndWidthPropertyType {
  return type === BORDER_BLOCK_END_WIDTH_PROPERTY_TYPE;
}

// Main entrypoint — last-write-wins fold (CSS cascade is already resolved upstream).
export function extractBorderBlockEndWidth(properties: IRPropertyLike[]): BorderBlockEndWidthConfig {
  const cfg: BorderBlockEndWidthConfig = {};                                   // blank accumulator
  for (const p of properties) {                                         // single pass
    if (!isBorderBlockEndWidthProperty(p.type)) continue;                                // skip unrelated
    const v = extractBorderSideWidth(p.data);                                     // validate & parse
    if (v) cfg.width = v;                                           // last recognised value wins
  }
  return cfg;
}

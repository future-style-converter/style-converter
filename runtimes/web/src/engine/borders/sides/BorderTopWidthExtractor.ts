// BorderTopWidthExtractor.ts — folds `BorderTopWidth` IR properties into a config.
// IR shape flavors (from examples/properties/borders/border-widths.json after
// ./gradlew run conversion):
//   {px:1, original:"thin"}               keyword pre-resolved by parser
//   {px:6.0}                              plain numeric px
//   {px:-?, original:{v,u:'EM'|'REM'...}} font-relative (parser keeps original)
//   {expr:'calc(2px + 4px)'}              calc — carried as raw expression
// See the primitive extractor at core/types/LengthValue.ts for the full alphabet.

import { extractBorderSideWidth } from './_shared';                       // shared parse/validate logic
import type { BorderTopWidthConfig, BorderTopWidthPropertyType } from './BorderTopWidthConfig';
import { BORDER_TOP_WIDTH_PROPERTY_TYPE } from './BorderTopWidthConfig';

// Minimal IRProperty shape — keeps engine modules decoupled from the IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Type-narrowing predicate used by the registry/renderer for dispatch gating.
export function isBorderTopWidthProperty(type: string): type is BorderTopWidthPropertyType {
  return type === BORDER_TOP_WIDTH_PROPERTY_TYPE;                         // single property type
}

// Main entrypoint — last-write-wins fold (CSS cascade is already resolved).
export function extractBorderTopWidth(properties: IRPropertyLike[]): BorderTopWidthConfig {
  const cfg: BorderTopWidthConfig = {};                                   // blank accumulator
  for (const p of properties) {                                           // single pass
    if (!isBorderTopWidthProperty(p.type)) continue;                      // skip unrelated
    const w = extractBorderSideWidth(p.data);                             // validate & parse
    if (w) cfg.width = w;                                                 // last recognised value wins
  }
  return cfg;
}

// BorderStartEndRadiusExtractor.ts — folds `BorderStartEndRadius` IR properties into a config.
// IR shape flavors (from examples/properties/borders/border-radius-*):
//   {px:N}                                        simple px radius
//   {original:{v,u:'EM'|'REM'|...}}                 font-relative / percent
//   {type:'percentage', value:N}                  parser may emit this
//   {horizontal:<Len>, vertical:<Len>}            elliptical pair
// The shared extractor in _shared.ts recognises every flavor.

import { extractBorderCornerRadius } from './_shared';                   // shared parser
import type { BorderStartEndRadiusConfig, BorderStartEndRadiusPropertyType } from './BorderStartEndRadiusConfig';
import { BORDER_START_END_RADIUS_PROPERTY_TYPE } from './BorderStartEndRadiusConfig';

// Minimal IRProperty shape — keep engine module decoupled from IR types.
interface IRPropertyLike { type: string; data: unknown; }

// Type-narrowing predicate — drives dispatch in the registry/renderer.
export function isBorderStartEndRadiusProperty(type: string): type is BorderStartEndRadiusPropertyType {
  return type === BORDER_START_END_RADIUS_PROPERTY_TYPE;
}

// Main entrypoint — last-write-wins fold (cascade is already resolved).
export function extractBorderStartEndRadius(properties: IRPropertyLike[]): BorderStartEndRadiusConfig {
  const cfg: BorderStartEndRadiusConfig = {};                                         // blank accumulator
  for (const p of properties) {                                          // single pass
    if (!isBorderStartEndRadiusProperty(p.type)) continue;                              // skip unrelated
    const r = extractBorderCornerRadius(p.data);                          // validate & parse
    if (r) cfg.radius = r;                                                // last recognised wins
  }
  return cfg;
}

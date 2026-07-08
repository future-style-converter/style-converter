// BorderEndEndRadiusExtractor.ts — folds `BorderEndEndRadius` IR properties into a config.
// IR shape flavors (from examples/properties/borders/border-radius-*):
//   {px:N}                                        simple px radius
//   {original:{v,u:'EM'|'REM'|...}}                 font-relative / percent
//   {type:'percentage', value:N}                  parser may emit this
//   {horizontal:<Len>, vertical:<Len>}            elliptical pair
// The shared extractor in _shared.ts recognises every flavor.

import { extractBorderCornerRadius } from './_shared';                   // shared parser
import type { BorderEndEndRadiusConfig, BorderEndEndRadiusPropertyType } from './BorderEndEndRadiusConfig';
import { BORDER_END_END_RADIUS_PROPERTY_TYPE } from './BorderEndEndRadiusConfig';

// Minimal IRProperty shape — keep engine module decoupled from IR types.
interface IRPropertyLike { type: string; data: unknown; }

// Type-narrowing predicate — drives dispatch in the registry/renderer.
export function isBorderEndEndRadiusProperty(type: string): type is BorderEndEndRadiusPropertyType {
  return type === BORDER_END_END_RADIUS_PROPERTY_TYPE;
}

// Main entrypoint — last-write-wins fold (cascade is already resolved).
export function extractBorderEndEndRadius(properties: IRPropertyLike[]): BorderEndEndRadiusConfig {
  const cfg: BorderEndEndRadiusConfig = {};                                         // blank accumulator
  for (const p of properties) {                                          // single pass
    if (!isBorderEndEndRadiusProperty(p.type)) continue;                              // skip unrelated
    const r = extractBorderCornerRadius(p.data);                          // validate & parse
    if (r) cfg.radius = r;                                                // last recognised wins
  }
  return cfg;
}

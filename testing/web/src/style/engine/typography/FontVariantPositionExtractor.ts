// FontVariantPositionExtractor.ts — folds `FontVariantPosition` IR properties into a FontVariantPositionConfig.
// Family: keyword.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { FontVariantPositionConfig, FONT_VARIANT_POSITION_PROPERTY_TYPE, FontVariantPositionPropertyType } from './FontVariantPositionConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isFontVariantPositionProperty(type: string): type is FontVariantPositionPropertyType {
  return type === FONT_VARIANT_POSITION_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // Pure enum: bare IR string like 'AUTO'/'LINE_THROUGH'. Normalise to kebab-case.
  return kwLower(data);                                              // '' -> undefined via kwLower
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractFontVariantPosition(properties: IRPropertyLike[]): FontVariantPositionConfig {
  const cfg: FontVariantPositionConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isFontVariantPositionProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

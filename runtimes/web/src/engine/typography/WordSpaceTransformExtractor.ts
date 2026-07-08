// WordSpaceTransformExtractor.ts — folds `WordSpaceTransform` IR properties into a WordSpaceTransformConfig.
// Family: keyword.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { WordSpaceTransformConfig, WORD_SPACE_TRANSFORM_PROPERTY_TYPE, WordSpaceTransformPropertyType } from './WordSpaceTransformConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isWordSpaceTransformProperty(type: string): type is WordSpaceTransformPropertyType {
  return type === WORD_SPACE_TRANSFORM_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // Pure enum: bare IR string like 'AUTO'/'LINE_THROUGH'. Normalise to kebab-case.
  return kwLower(data);                                              // '' -> undefined via kwLower
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractWordSpaceTransform(properties: IRPropertyLike[]): WordSpaceTransformConfig {
  const cfg: WordSpaceTransformConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isWordSpaceTransformProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

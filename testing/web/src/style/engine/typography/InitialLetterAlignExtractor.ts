// InitialLetterAlignExtractor.ts — folds `InitialLetterAlign` IR properties into a InitialLetterAlignConfig.
// Family: keyword.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { InitialLetterAlignConfig, INITIAL_LETTER_ALIGN_PROPERTY_TYPE, InitialLetterAlignPropertyType } from './InitialLetterAlignConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isInitialLetterAlignProperty(type: string): type is InitialLetterAlignPropertyType {
  return type === INITIAL_LETTER_ALIGN_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // Pure enum: bare IR string like 'AUTO'/'LINE_THROUGH'. Normalise to kebab-case.
  return kwLower(data);                                              // '' -> undefined via kwLower
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractInitialLetterAlign(properties: IRPropertyLike[]): InitialLetterAlignConfig {
  const cfg: InitialLetterAlignConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isInitialLetterAlignProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

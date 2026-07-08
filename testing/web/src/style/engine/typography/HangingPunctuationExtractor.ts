// HangingPunctuationExtractor.ts — folds `HangingPunctuation` IR properties into a HangingPunctuationConfig.
// Family: keyword-list.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { HangingPunctuationConfig, HANGING_PUNCTUATION_PROPERTY_TYPE, HangingPunctuationPropertyType } from './HangingPunctuationConfig';
import { kwList } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isHangingPunctuationProperty(type: string): type is HangingPunctuationPropertyType {
  return type === HANGING_PUNCTUATION_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // Array of enum tokens — IR serialises as ["TOKEN_A","TOKEN_B"].
  // Each token kebab-cased + space-joined per CSS multi-value grammar.
  return kwList(data);                                               // '' -> undefined
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractHangingPunctuation(properties: IRPropertyLike[]): HangingPunctuationConfig {
  const cfg: HangingPunctuationConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isHangingPunctuationProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

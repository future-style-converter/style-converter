// HyphenateLimitLastExtractor.ts — folds `HyphenateLimitLast` IR properties into a HyphenateLimitLastConfig.
// Family: keyword.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { HyphenateLimitLastConfig, HYPHENATE_LIMIT_LAST_PROPERTY_TYPE, HyphenateLimitLastPropertyType } from './HyphenateLimitLastConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isHyphenateLimitLastProperty(type: string): type is HyphenateLimitLastPropertyType {
  return type === HYPHENATE_LIMIT_LAST_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // Pure enum: bare IR string like 'AUTO'/'LINE_THROUGH'. Normalise to kebab-case.
  return kwLower(data);                                              // '' -> undefined via kwLower
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractHyphenateLimitLast(properties: IRPropertyLike[]): HyphenateLimitLastConfig {
  const cfg: HyphenateLimitLastConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isHyphenateLimitLastProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

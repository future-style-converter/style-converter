// OrphansExtractor.ts — folds `Orphans` IR properties into a OrphansConfig.
// Family: number.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { OrphansConfig, ORPHANS_PROPERTY_TYPE, OrphansPropertyType } from './OrphansConfig';
import { numberOf } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isOrphansProperty(type: string): type is OrphansPropertyType {
  return type === ORPHANS_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // Unitless integer — widows/orphans.  Accept bare numbers or {value}/{count}.
  const n = numberOf(data);                                          // normalised reader
  return n;                                                          // numeric CSS value
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractOrphans(properties: IRPropertyLike[]): OrphansConfig {
  const cfg: OrphansConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isOrphansProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

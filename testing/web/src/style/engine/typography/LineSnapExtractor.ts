// LineSnapExtractor.ts — folds `LineSnap` IR properties into a LineSnapConfig.
// Family: keyword.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { LineSnapConfig, LINE_SNAP_PROPERTY_TYPE, LineSnapPropertyType } from './LineSnapConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isLineSnapProperty(type: string): type is LineSnapPropertyType {
  return type === LINE_SNAP_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // Pure enum: bare IR string like 'AUTO'/'LINE_THROUGH'. Normalise to kebab-case.
  return kwLower(data);                                              // '' -> undefined via kwLower
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractLineSnap(properties: IRPropertyLike[]): LineSnapConfig {
  const cfg: LineSnapConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isLineSnapProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

// DominantBaselineAdjustExtractor.ts — folds `DominantBaselineAdjust` IR properties into a DominantBaselineAdjustConfig.
// Family: dominant-baseline-adjust.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { DominantBaselineAdjustConfig, DOMINANT_BASELINE_ADJUST_PROPERTY_TYPE, DominantBaselineAdjustPropertyType } from './DominantBaselineAdjustConfig';
import { kwLower, lengthCss } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isDominantBaselineAdjustProperty(type: string): type is DominantBaselineAdjustPropertyType {
  return type === DOMINANT_BASELINE_ADJUST_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // DominantBaselineAdjust: {type:'auto'} | {type:'percentage',value} | length
  if (!data || typeof data !== 'object') return kwLower(data);
  const o = data as Record<string, unknown>;
  if (o.type === 'auto') return 'auto';
  if (o.type === 'percentage' && typeof o.value === 'number') return `${o.value}%`;
  return lengthCss(data);
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractDominantBaselineAdjust(properties: IRPropertyLike[]): DominantBaselineAdjustConfig {
  const cfg: DominantBaselineAdjustConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isDominantBaselineAdjustProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

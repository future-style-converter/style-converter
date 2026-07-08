// BaselineShiftExtractor.ts — folds `BaselineShift` IR properties into a BaselineShiftConfig.
// Family: baseline-shift.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { BaselineShiftConfig, BASELINE_SHIFT_PROPERTY_TYPE, BaselineShiftPropertyType } from './BaselineShiftConfig';
import { kwLower, lengthCss, percentCss } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isBaselineShiftProperty(type: string): type is BaselineShiftPropertyType {
  return type === BASELINE_SHIFT_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // BaselineShift: {type:'baseline'|'sub'|'super'} | length | {type:'percentage'}.
  if (!data || typeof data !== 'object') return kwLower(data);
  const o = data as Record<string, unknown>;
  if (o.type === 'baseline' || o.type === 'sub' || o.type === 'super') return String(o.type);
  if (o.type === 'percentage' && typeof o.value === 'number') return `${o.value}%`;
  const p = percentCss(data); if (p) return p;
  return lengthCss(data);
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractBaselineShift(properties: IRPropertyLike[]): BaselineShiftConfig {
  const cfg: BaselineShiftConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isBaselineShiftProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

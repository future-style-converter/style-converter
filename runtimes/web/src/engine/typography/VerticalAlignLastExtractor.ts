// VerticalAlignLastExtractor.ts — folds `VerticalAlignLast` IR properties into a VerticalAlignLastConfig.
// Family: vertical-align.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { VerticalAlignLastConfig, VERTICAL_ALIGN_LAST_PROPERTY_TYPE, VerticalAlignLastPropertyType } from './VerticalAlignLastConfig';
import { kwLower } from './_shared';
import { extractLength, toCssLength } from '../core/types/LengthValue';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isVerticalAlignLastProperty(type: string): type is VerticalAlignLastPropertyType {
  return type === VERTICAL_ALIGN_LAST_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // VerticalAlign: length | percentage | {type:'keyword',value:'BASELINE'}.
  if (data && typeof data === 'object') {                            // envelope guard
    const o = data as Record<string, unknown>;
    if (o.type === 'keyword' && typeof o.value === 'string') return kwLower(o.value);
    // Percentage envelope: IR uses either .value or .percentage as the numeric key.
    if (o.type === 'percentage') {
      const n = typeof o.percentage === 'number' ? o.percentage
              : typeof o.value === 'number' ? o.value : null;
      if (n !== null) return `${n}%`;
    }
    const l = extractLength(data);                                   // length alphabet
    if (l.kind !== 'unknown') return toCssLength(l);                 // 'Npx'/'Nem'/...
  }
  return kwLower(data);                                              // bare keyword fallback
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractVerticalAlignLast(properties: IRPropertyLike[]): VerticalAlignLastConfig {
  const cfg: VerticalAlignLastConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isVerticalAlignLastProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

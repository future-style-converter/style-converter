// MaxLinesExtractor.ts — folds `MaxLines` IR properties into a MaxLinesConfig.
// Family: max-lines.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { MaxLinesConfig, MAX_LINES_PROPERTY_TYPE, MaxLinesPropertyType } from './MaxLinesConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isMaxLinesProperty(type: string): type is MaxLinesPropertyType {
  return type === MAX_LINES_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // MaxLines: {type:'none'} | {type:'count', value:N}
  if (!data || typeof data !== 'object') return kwLower(data);
  const o = data as Record<string, unknown>;
  if (o.type === 'none') return 'none';
  if (o.type === 'count' && typeof o.value === 'number') return o.value;
  return undefined;
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractMaxLines(properties: IRPropertyLike[]): MaxLinesConfig {
  const cfg: MaxLinesConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isMaxLinesProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

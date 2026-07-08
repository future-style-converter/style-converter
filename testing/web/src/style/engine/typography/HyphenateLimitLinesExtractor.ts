// HyphenateLimitLinesExtractor.ts — folds `HyphenateLimitLines` IR properties into a HyphenateLimitLinesConfig.
// Family: hyphenate-limit-lines.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { HyphenateLimitLinesConfig, HYPHENATE_LIMIT_LINES_PROPERTY_TYPE, HyphenateLimitLinesPropertyType } from './HyphenateLimitLinesConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isHyphenateLimitLinesProperty(type: string): type is HyphenateLimitLinesPropertyType {
  return type === HYPHENATE_LIMIT_LINES_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // HyphenateLimitLines: {type:'no-limit'} | {type:'number', value:N}
  if (!data || typeof data !== 'object') return kwLower(data);
  const o = data as Record<string, unknown>;
  if (o.type === 'no-limit') return 'no-limit';
  if (o.type === 'number' && typeof o.value === 'number') return o.value;
  return undefined;
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractHyphenateLimitLines(properties: IRPropertyLike[]): HyphenateLimitLinesConfig {
  const cfg: HyphenateLimitLinesConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isHyphenateLimitLinesProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

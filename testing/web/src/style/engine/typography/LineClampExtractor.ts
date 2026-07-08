// LineClampExtractor.ts — folds `LineClamp` IR properties into a LineClampConfig.
// Family: line-clamp.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { LineClampConfig, LINE_CLAMP_PROPERTY_TYPE, LineClampPropertyType } from './LineClampConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isLineClampProperty(type: string): type is LineClampPropertyType {
  return type === LINE_CLAMP_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // LineClamp: {type:'none'} | {type:'lines', count:N}
  // Native key is line-clamp; applier also emits the -webkit-box trio.
  if (!data || typeof data !== 'object') return kwLower(data);
  const o = data as Record<string, unknown>;
  if (o.type === 'none') return 'none';
  if (o.type === 'lines' && typeof o.count === 'number') return o.count;
  return undefined;
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractLineClamp(properties: IRPropertyLike[]): LineClampConfig {
  const cfg: LineClampConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isLineClampProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

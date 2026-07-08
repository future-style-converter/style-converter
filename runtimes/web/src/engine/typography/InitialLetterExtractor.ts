// InitialLetterExtractor.ts — folds `InitialLetter` IR properties into a InitialLetterConfig.
// Family: initial-letter.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { InitialLetterConfig, INITIAL_LETTER_PROPERTY_TYPE, InitialLetterPropertyType } from './InitialLetterConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isInitialLetterProperty(type: string): type is InitialLetterPropertyType {
  return type === INITIAL_LETTER_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // InitialLetter: {type:'normal'} | {type:'size', size:N, sink?:N}
  // CSS grammar: <number> <integer>?  (size then optional sink lines).
  if (!data || typeof data !== 'object') return kwLower(data);
  const o = data as Record<string, unknown>;
  if (o.type === 'normal') return 'normal';
  if (o.type === 'size' && typeof o.size === 'number') {
    return typeof o.sink === 'number' ? `${o.size} ${o.sink}` : String(o.size);
  }
  return undefined;
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractInitialLetter(properties: IRPropertyLike[]): InitialLetterConfig {
  const cfg: InitialLetterConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isInitialLetterProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

// IsolationExtractor.ts — parses `Isolation` IR strings into a validated token.

import type { IsolationConfig, IsolationPropertyType } from './IsolationConfig';
import { ISOLATION_PROPERTY_TYPE } from './IsolationConfig';

// Minimal IR property shape.
interface IRPropertyLike { type: string; data: unknown; }

// Registry predicate.
export function isIsolationProperty(type: string): type is IsolationPropertyType {
  return type === ISOLATION_PROPERTY_TYPE;
}

// Parse one IR payload to the CSS token or null on unrecognised shapes.
function parseValue(data: unknown): 'auto' | 'isolate' | null {
  if (typeof data !== 'string') return null;                          // IR shape is always a string
  const lc = data.toLowerCase();                                      // 'AUTO' -> 'auto'
  if (lc === 'auto' || lc === 'isolate') return lc;                   // known values only
  return null;                                                        // reject anything else
}

// Entry point — last write wins.
export function extractIsolation(properties: IRPropertyLike[]): IsolationConfig {
  const cfg: IsolationConfig = {};                                    // blank accumulator
  for (const p of properties) {
    if (!isIsolationProperty(p.type)) continue;                       // filter
    const v = parseValue(p.data);                                     // normalise
    if (v === null) continue;                                         // drop unknown
    cfg.value = v;                                                    // last write wins
  }
  return cfg;
}

// LineGridExtractor.ts — folds `LineGrid` IR properties into a LineGridConfig.
// Family: line-grid.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { LineGridConfig, LINE_GRID_PROPERTY_TYPE, LineGridPropertyType } from './LineGridConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isLineGridProperty(type: string): type is LineGridPropertyType {
  return type === LINE_GRID_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // LineGrid: {type:'match-parent'|'create'} | {type:'named', name:'my-grid'}
  if (!data || typeof data !== 'object') return kwLower(data);
  const o = data as Record<string, unknown>;
  if (o.type === 'match-parent' || o.type === 'create') return String(o.type);
  if (o.type === 'named' && typeof o.name === 'string') return o.name; // <custom-ident>
  return undefined;
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractLineGrid(properties: IRPropertyLike[]): LineGridConfig {
  const cfg: LineGridConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isLineGridProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

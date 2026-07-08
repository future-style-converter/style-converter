// BlockEllipsisExtractor.ts — folds `BlockEllipsis` IR properties into a BlockEllipsisConfig.
// Family: block-ellipsis.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { BlockEllipsisConfig, BLOCK_ELLIPSIS_PROPERTY_TYPE, BlockEllipsisPropertyType } from './BlockEllipsisConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isBlockEllipsisProperty(type: string): type is BlockEllipsisPropertyType {
  return type === BLOCK_ELLIPSIS_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // BlockEllipsis: {type:'none'|'auto'} | {type:'custom', value:'...'}
  if (!data || typeof data !== 'object') return kwLower(data);
  const o = data as Record<string, unknown>;
  if (o.type === 'none' || o.type === 'auto') return String(o.type);
  if (o.type === 'custom' && typeof o.value === 'string') {
    return `"${o.value.replace(/"/g, '\\"')}"`;                    // CSS string literal
  }
  return undefined;
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractBlockEllipsis(properties: IRPropertyLike[]): BlockEllipsisConfig {
  const cfg: BlockEllipsisConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isBlockEllipsisProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

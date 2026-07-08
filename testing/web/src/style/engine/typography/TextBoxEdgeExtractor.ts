// TextBoxEdgeExtractor.ts — folds `TextBoxEdge` IR properties into a TextBoxEdgeConfig.
// Family: text-box-edge.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { TextBoxEdgeConfig, TEXT_BOX_EDGE_PROPERTY_TYPE, TextBoxEdgePropertyType } from './TextBoxEdgeConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isTextBoxEdgeProperty(type: string): type is TextBoxEdgePropertyType {
  return type === TEXT_BOX_EDGE_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // TextBoxEdge: { over:'CAP'|'ALPHABETIC'|..., under:'...' }.
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  const over = typeof o.over === 'string' ? kwLower(o.over) : undefined;
  const under = typeof o.under === 'string' ? kwLower(o.under) : undefined;
  if (!over && !under) return undefined;
  // CSS syntax: '<over>' or '<over> <under>' — omit the second when equal.
  if (over && under && over !== under) return `${over} ${under}`;
  return over || under;
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractTextBoxEdge(properties: IRPropertyLike[]): TextBoxEdgeConfig {
  const cfg: TextBoxEdgeConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isTextBoxEdgeProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

// TextCombineUprightExtractor.ts — folds `TextCombineUpright` IR properties into a TextCombineUprightConfig.
// Family: text-combine-upright.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { TextCombineUprightConfig, TEXT_COMBINE_UPRIGHT_PROPERTY_TYPE, TextCombineUprightPropertyType } from './TextCombineUprightConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isTextCombineUprightProperty(type: string): type is TextCombineUprightPropertyType {
  return type === TEXT_COMBINE_UPRIGHT_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // TextCombineUpright: {type:'none'|'all'} | {type:'digits',count:2|3|4}.
  if (!data || typeof data !== 'object') return kwLower(data);       // bare keyword
  const o = data as Record<string, unknown>;
  if (o.type === 'none' || o.type === 'all') return String(o.type);  // CSS keyword
  if (o.type === 'digits' && typeof o.count === 'number') return `digits ${o.count}`;
  return undefined;
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractTextCombineUpright(properties: IRPropertyLike[]): TextCombineUprightConfig {
  const cfg: TextCombineUprightConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isTextCombineUprightProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

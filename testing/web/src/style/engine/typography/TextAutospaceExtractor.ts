// TextAutospaceExtractor.ts — folds `TextAutospace` IR properties into a TextAutospaceConfig.
// Family: keyword-list.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { TextAutospaceConfig, TEXT_AUTOSPACE_PROPERTY_TYPE, TextAutospacePropertyType } from './TextAutospaceConfig';
import { kwList } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isTextAutospaceProperty(type: string): type is TextAutospacePropertyType {
  return type === TEXT_AUTOSPACE_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // Array of enum tokens — IR serialises as ["TOKEN_A","TOKEN_B"].
  // Each token kebab-cased + space-joined per CSS multi-value grammar.
  return kwList(data);                                               // '' -> undefined
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractTextAutospace(properties: IRPropertyLike[]): TextAutospaceConfig {
  const cfg: TextAutospaceConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isTextAutospaceProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

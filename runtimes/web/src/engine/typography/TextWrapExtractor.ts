// TextWrapExtractor.ts — folds `TextWrap` IR properties into a TextWrapConfig.
// Family: keyword.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { TextWrapConfig, TEXT_WRAP_PROPERTY_TYPE, TextWrapPropertyType } from './TextWrapConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isTextWrapProperty(type: string): type is TextWrapPropertyType {
  return type === TEXT_WRAP_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // Pure enum: bare IR string like 'AUTO'/'LINE_THROUGH'. Normalise to kebab-case.
  return kwLower(data);                                              // '' -> undefined via kwLower
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractTextWrap(properties: IRPropertyLike[]): TextWrapConfig {
  const cfg: TextWrapConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isTextWrapProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

// TextWrapStyleExtractor.ts — folds `TextWrapStyle` IR properties into a TextWrapStyleConfig.
// Family: keyword.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { TextWrapStyleConfig, TEXT_WRAP_STYLE_PROPERTY_TYPE, TextWrapStylePropertyType } from './TextWrapStyleConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isTextWrapStyleProperty(type: string): type is TextWrapStylePropertyType {
  return type === TEXT_WRAP_STYLE_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // Pure enum: bare IR string like 'AUTO'/'LINE_THROUGH'. Normalise to kebab-case.
  return kwLower(data);                                              // '' -> undefined via kwLower
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractTextWrapStyle(properties: IRPropertyLike[]): TextWrapStyleConfig {
  const cfg: TextWrapStyleConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isTextWrapStyleProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

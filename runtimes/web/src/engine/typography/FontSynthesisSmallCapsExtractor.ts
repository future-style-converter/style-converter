// FontSynthesisSmallCapsExtractor.ts — folds `FontSynthesisSmallCaps` IR properties into a FontSynthesisSmallCapsConfig.
// Family: keyword.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { FontSynthesisSmallCapsConfig, FONT_SYNTHESIS_SMALL_CAPS_PROPERTY_TYPE, FontSynthesisSmallCapsPropertyType } from './FontSynthesisSmallCapsConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isFontSynthesisSmallCapsProperty(type: string): type is FontSynthesisSmallCapsPropertyType {
  return type === FONT_SYNTHESIS_SMALL_CAPS_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // Pure enum: bare IR string like 'AUTO'/'LINE_THROUGH'. Normalise to kebab-case.
  return kwLower(data);                                              // '' -> undefined via kwLower
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractFontSynthesisSmallCaps(properties: IRPropertyLike[]): FontSynthesisSmallCapsConfig {
  const cfg: FontSynthesisSmallCapsConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isFontSynthesisSmallCapsProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

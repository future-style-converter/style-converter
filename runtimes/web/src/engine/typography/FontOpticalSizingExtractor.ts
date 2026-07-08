// FontOpticalSizingExtractor.ts — folds `FontOpticalSizing` IR properties into a FontOpticalSizingConfig.
// Family: keyword.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { FontOpticalSizingConfig, FONT_OPTICAL_SIZING_PROPERTY_TYPE, FontOpticalSizingPropertyType } from './FontOpticalSizingConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isFontOpticalSizingProperty(type: string): type is FontOpticalSizingPropertyType {
  return type === FONT_OPTICAL_SIZING_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // Pure enum: bare IR string like 'AUTO'/'LINE_THROUGH'. Normalise to kebab-case.
  return kwLower(data);                                              // '' -> undefined via kwLower
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractFontOpticalSizing(properties: IRPropertyLike[]): FontOpticalSizingConfig {
  const cfg: FontOpticalSizingConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isFontOpticalSizingProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

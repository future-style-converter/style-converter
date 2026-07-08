// TextEmphasisColorExtractor.ts — folds `TextEmphasisColor` IR properties into a TextEmphasisColorConfig.
// Family: color.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { TextEmphasisColorConfig, TEXT_EMPHASIS_COLOR_PROPERTY_TYPE, TextEmphasisColorPropertyType } from './TextEmphasisColorConfig';
import { colorCss } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isTextEmphasisColorProperty(type: string): type is TextEmphasisColorPropertyType {
  return type === TEXT_EMPHASIS_COLOR_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // ColorValue alphabet — static sRGB / dynamic color-mix / currentColor / var().
  return colorCss(data);                                             // rgba(...) or dynamic passthrough
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractTextEmphasisColor(properties: IRPropertyLike[]): TextEmphasisColorConfig {
  const cfg: TextEmphasisColorConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isTextEmphasisColorProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

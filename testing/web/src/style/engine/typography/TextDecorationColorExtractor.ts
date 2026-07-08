// TextDecorationColorExtractor.ts — folds `TextDecorationColor` IR properties into a TextDecorationColorConfig.
// Family: color.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { TextDecorationColorConfig, TEXT_DECORATION_COLOR_PROPERTY_TYPE, TextDecorationColorPropertyType } from './TextDecorationColorConfig';
import { colorCss } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isTextDecorationColorProperty(type: string): type is TextDecorationColorPropertyType {
  return type === TEXT_DECORATION_COLOR_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // ColorValue alphabet — static sRGB / dynamic color-mix / currentColor / var().
  return colorCss(data);                                             // rgba(...) or dynamic passthrough
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractTextDecorationColor(properties: IRPropertyLike[]): TextDecorationColorConfig {
  const cfg: TextDecorationColorConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isTextDecorationColorProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

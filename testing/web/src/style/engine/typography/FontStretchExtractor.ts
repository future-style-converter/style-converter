// FontStretchExtractor.ts — folds `FontStretch` IR properties into a FontStretchConfig.
// Family: font-stretch.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { FontStretchConfig, FONT_STRETCH_PROPERTY_TYPE, FontStretchPropertyType } from './FontStretchConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isFontStretchProperty(type: string): type is FontStretchPropertyType {
  return type === FONT_STRETCH_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // FontStretch flavours (see FontStretchPropertyParser.kt):
  //   { percentage: N, original: { keyword?, type:'keyword'|'percent' } }
  // When original.keyword is present, emit the keyword (more idiomatic and
  // preserves author intent).  Otherwise emit the percentage.
  if (data && typeof data === 'object') {                            // envelope guard
    const o = data as Record<string, unknown>;
    const orig = o.original as Record<string, unknown> | undefined;
    if (orig && typeof orig.keyword === 'string') return kwLower(orig.keyword); // prefer keyword
    if (typeof o.percentage === 'number') return `${o.percentage}%`; // numeric fallback
  }
  return kwLower(data);                                              // bare keyword
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractFontStretch(properties: IRPropertyLike[]): FontStretchConfig {
  const cfg: FontStretchConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isFontStretchProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

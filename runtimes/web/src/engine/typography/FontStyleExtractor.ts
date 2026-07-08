// FontStyleExtractor.ts — folds `FontStyle` IR properties into a FontStyleConfig.
// Family: font-style.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { FontStyleConfig, FONT_STYLE_PROPERTY_TYPE, FontStylePropertyType } from './FontStyleConfig';
import { kwLower, angleCss } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isFontStyleProperty(type: string): type is FontStylePropertyType {
  return type === FONT_STYLE_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // FontStyle flavours (see FontStylePropertyParser.kt):
  //   'normal' | 'italic' | 'oblique'  — bare strings
  //   { oblique: { deg: N } }         — oblique with angle
  if (typeof data === 'string') return kwLower(data);                // bare keyword path
  if (data && typeof data === 'object') {                            // object path
    const o = data as Record<string, unknown>;
    if (o.oblique && typeof o.oblique === 'object') {                // oblique <angle>
      const a = angleCss(o.oblique);                                 // normalised deg
      if (a) return `oblique ${a}`;                                  // spec: 'oblique <angle>'
    }
  }
  return undefined;
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractFontStyle(properties: IRPropertyLike[]): FontStyleConfig {
  const cfg: FontStyleConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isFontStyleProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

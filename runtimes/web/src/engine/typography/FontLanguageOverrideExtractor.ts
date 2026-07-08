// FontLanguageOverrideExtractor.ts — folds `FontLanguageOverride` IR properties into a FontLanguageOverrideConfig.
// Family: font-language-override.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { FontLanguageOverrideConfig, FONT_LANGUAGE_OVERRIDE_PROPERTY_TYPE, FontLanguageOverridePropertyType } from './FontLanguageOverrideConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isFontLanguageOverrideProperty(type: string): type is FontLanguageOverridePropertyType {
  return type === FONT_LANGUAGE_OVERRIDE_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // FontLanguageOverride: {type:'normal'} | {type:'language-tag', tag:'ENG'}
  if (!data || typeof data !== 'object') return kwLower(data);
  const o = data as Record<string, unknown>;
  if (o.type === 'normal') return 'normal';
  if (o.type === 'language-tag' && typeof o.tag === 'string') return `"${o.tag}"`; // CSS string literal
  return undefined;
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractFontLanguageOverride(properties: IRPropertyLike[]): FontLanguageOverrideConfig {
  const cfg: FontLanguageOverrideConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isFontLanguageOverrideProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

// FontFeatureSettingsExtractor.ts — folds `FontFeatureSettings` IR properties into a FontFeatureSettingsConfig.
// Family: font-feature-settings.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { FontFeatureSettingsConfig, FONT_FEATURE_SETTINGS_PROPERTY_TYPE, FontFeatureSettingsPropertyType } from './FontFeatureSettingsConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isFontFeatureSettingsProperty(type: string): type is FontFeatureSettingsPropertyType {
  return type === FONT_FEATURE_SETTINGS_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // FontFeatureSettings: {type:'normal'} | {type:'features', features:[{tag,value?}]}
  // CSS syntax: "tag1" value?, "tag2" value?  (value 0/1 or integer).
  if (!data || typeof data !== 'object') return kwLower(data);       // 'normal' passthrough
  const o = data as Record<string, unknown>;
  if (o.type === 'normal') return 'normal';
  if (o.type === 'features' && Array.isArray(o.features)) {
    const parts: string[] = [];
    for (const f of o.features) {                                    // each feature entry
      if (!f || typeof f !== 'object') continue;
      const g = f as Record<string, unknown>;
      if (typeof g.tag !== 'string') continue;
      const tag = `"${g.tag}"`;                                      // CSS requires quoted tag
      parts.push(typeof g.value === 'number' ? `${tag} ${g.value}` : tag);
    }
    return parts.length ? parts.join(', ') : undefined;
  }
  return undefined;
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractFontFeatureSettings(properties: IRPropertyLike[]): FontFeatureSettingsConfig {
  const cfg: FontFeatureSettingsConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isFontFeatureSettingsProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

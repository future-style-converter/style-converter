// FontVariationSettingsExtractor.ts — folds `FontVariationSettings` IR properties into a FontVariationSettingsConfig.
// Family: font-variation-settings.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { FontVariationSettingsConfig, FONT_VARIATION_SETTINGS_PROPERTY_TYPE, FontVariationSettingsPropertyType } from './FontVariationSettingsConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isFontVariationSettingsProperty(type: string): type is FontVariationSettingsPropertyType {
  return type === FONT_VARIATION_SETTINGS_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // FontVariationSettings: {type:'normal'} | {type:'variations', variations:[{axis,value}]}
  // CSS syntax: "axis" value, "axis" value  (value is numeric).
  if (!data || typeof data !== 'object') return kwLower(data);       // 'normal'
  const o = data as Record<string, unknown>;
  if (o.type === 'normal') return 'normal';
  if (o.type === 'variations' && Array.isArray(o.variations)) {
    const parts: string[] = [];
    for (const v of o.variations) {                                  // iterate axes
      if (!v || typeof v !== 'object') continue;
      const g = v as Record<string, unknown>;
      if (typeof g.axis !== 'string' || typeof g.value !== 'number') continue;
      parts.push(`"${g.axis}" ${g.value}`);                          // CSS requires quoted axis
    }
    return parts.length ? parts.join(', ') : undefined;
  }
  return undefined;
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractFontVariationSettings(properties: IRPropertyLike[]): FontVariationSettingsConfig {
  const cfg: FontVariationSettingsConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isFontVariationSettingsProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

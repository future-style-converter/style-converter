// FontSizeAdjustExtractor.ts — folds `FontSizeAdjust` IR properties into a FontSizeAdjustConfig.
// Family: font-size-adjust.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { FontSizeAdjustConfig, FONT_SIZE_ADJUST_PROPERTY_TYPE, FontSizeAdjustPropertyType } from './FontSizeAdjustConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isFontSizeAdjustProperty(type: string): type is FontSizeAdjustPropertyType {
  return type === FONT_SIZE_ADJUST_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // FontSizeAdjust flavours (see FontSizeAdjustPropertyParser.kt):
  //   {type:'none'} | {type:'from-font'} | {type:'number',value:N}
  //   {type:'metric-value', metric:'ex-height'|'cap-height'|'ch-width'|'ic-width'|'ic-height', value:N}
  if (!data || typeof data !== 'object') return kwLower(data);
  const o = data as Record<string, unknown>;
  if (o.type === 'none') return 'none';
  if (o.type === 'from-font') return 'from-font';
  if (o.type === 'number' && typeof o.value === 'number') return o.value;
  if (o.type === 'metric-value' && typeof o.metric === 'string' && typeof o.value === 'number') {
    return `${o.metric} ${o.value}`;                                 // CSS syntax: '<metric> <number>'
  }
  return undefined;
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractFontSizeAdjust(properties: IRPropertyLike[]): FontSizeAdjustConfig {
  const cfg: FontSizeAdjustConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isFontSizeAdjustProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

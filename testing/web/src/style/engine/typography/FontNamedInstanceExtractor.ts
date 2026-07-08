// FontNamedInstanceExtractor.ts — folds `FontNamedInstance` IR properties into a FontNamedInstanceConfig.
// Family: font-named-instance.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { FontNamedInstanceConfig, FONT_NAMED_INSTANCE_PROPERTY_TYPE, FontNamedInstancePropertyType } from './FontNamedInstanceConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isFontNamedInstanceProperty(type: string): type is FontNamedInstancePropertyType {
  return type === FONT_NAMED_INSTANCE_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // FontNamedInstance: {type:'auto'} | {type:'named', name:'Bold'}
  // Note: CSS `font-named-instance` is Level-4 — browsers may not honour it yet;
  // we still emit it verbatim so downstream screenshots can document support.
  if (!data || typeof data !== 'object') return kwLower(data);
  const o = data as Record<string, unknown>;
  if (o.type === 'auto') return 'auto';
  if (o.type === 'named' && typeof o.name === 'string') return `"${o.name}"`;// quoted per spec
  return undefined;
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractFontNamedInstance(properties: IRPropertyLike[]): FontNamedInstanceConfig {
  const cfg: FontNamedInstanceConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isFontNamedInstanceProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

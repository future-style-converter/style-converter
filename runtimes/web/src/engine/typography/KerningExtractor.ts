// KerningExtractor.ts — folds `Kerning` IR properties into a KerningConfig.
// Family: kerning.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { KerningConfig, KERNING_PROPERTY_TYPE, KerningPropertyType } from './KerningConfig';
import { kwLower, lengthCss } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isKerningProperty(type: string): type is KerningPropertyType {
  return type === KERNING_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // Kerning (SVG): {type:'auto'} | length value.
  if (!data || typeof data !== 'object') return kwLower(data);
  const o = data as Record<string, unknown>;
  if (o.type === 'auto') return 'auto';
  return lengthCss(data);                                            // Npx/Nem/...
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractKerning(properties: IRPropertyLike[]): KerningConfig {
  const cfg: KerningConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isKerningProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

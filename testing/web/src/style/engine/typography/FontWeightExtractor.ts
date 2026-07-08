// FontWeightExtractor.ts — folds `FontWeight` IR properties into a FontWeightConfig.
// Family: font-weight.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { FontWeightConfig, FONT_WEIGHT_PROPERTY_TYPE, FontWeightPropertyType } from './FontWeightConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isFontWeightProperty(type: string): type is FontWeightPropertyType {
  return type === FONT_WEIGHT_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // FontWeight: bare 'normal'|'bold'|'bolder'|'lighter' OR numeric 100..900.
  if (typeof data === 'number') return data;                         // numeric weight passes through
  const kw = kwLower(data);                                          // keyword path
  if (!kw) return undefined;                                         // unknown input
  // Browser resolves bolder/lighter relative to inherited weight — pass through.
  return kw;
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractFontWeight(properties: IRPropertyLike[]): FontWeightConfig {
  const cfg: FontWeightConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isFontWeightProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

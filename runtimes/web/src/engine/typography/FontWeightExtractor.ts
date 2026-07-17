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
// Three wire shapes flow through here (see FontWeightProperty.kt serialize()):
//   • bare numeric  : 700                            (font-weight: 700)
//   • keyword form  : { weight: 700, original: "bold" }  (font-weight: bold/normal)
//   • plain string  : "bolder" / "lighter"           (relative keywords, no numeric)
function parse(data: unknown): string | number | undefined {
  if (typeof data === 'number') return data;                         // numeric weight passes through
  // Keyword-form object — the parser resolves bold→700 / normal→400 per
  // css-fonts-4 §2.2 and stashes the source token in `original`. Prefer the
  // resolved numeric so web renders the same weight the natives bucket from.
  // Without this branch the object fell to kwLower() (which only reads bare
  // strings) and font-weight:bold silently dropped on web.
  if (data && typeof data === 'object' && !Array.isArray(data)) {
    const o = data as Record<string, unknown>;                       // shape probe
    if (typeof o.weight === 'number' && Number.isFinite(o.weight)) return o.weight; // resolved numeric wins
    if (typeof o.original === 'string') return kwLower(o.original);  // no numeric → fall back to source keyword
    return undefined;                                                // unknown object shape — drop, cascade unaffected
  }
  const kw = kwLower(data);                                          // bare-string keyword path
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

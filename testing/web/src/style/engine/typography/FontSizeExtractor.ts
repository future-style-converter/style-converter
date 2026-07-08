// FontSizeExtractor.ts — folds `FontSize` IR properties into a FontSizeConfig.
// Family: font-size.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { FontSizeConfig, FONT_SIZE_PROPERTY_TYPE, FontSizePropertyType } from './FontSizeConfig';
import { kwLower, lengthCss } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isFontSizeProperty(type: string): type is FontSizePropertyType {
  return type === FONT_SIZE_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // FontSize flavours (see FontSizePropertyParser.kt):
  //   { px:N, original:{...} }                     — resolved length
  //   { original: { keyword:'large', type:'absolute' } }  — keyword
  //   { original: { keyword:'larger', type:'relative' } } — relative keyword
  //   { original: { expr:'calc(...)', type:'expression' } } — calc
  // Prefer keyword (preserves intent) then calc, then px.
  if (data && typeof data === 'object') {                            // envelope guard
    const o = data as Record<string, unknown>;
    const orig = o.original as Record<string, unknown> | undefined;
    if (orig) {                                                      // keyword/calc paths
      if (typeof orig.keyword === 'string') return kwLower(orig.keyword);
      if (typeof orig.expr === 'string') {                           // calc expression passthrough
        const raw = orig.expr.trim();
        return raw.startsWith('calc(') ? raw : `calc(${raw})`;
      }
    }
    if (typeof o.px === 'number') return `${o.px}px`;                // resolved length
  }
  return lengthCss(data);                                            // final fallback
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractFontSize(properties: IRPropertyLike[]): FontSizeConfig {
  const cfg: FontSizeConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isFontSizeProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

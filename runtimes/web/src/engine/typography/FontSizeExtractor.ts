// FontSizeExtractor.ts — folds `FontSize` IR properties into a FontSizeConfig.
// Family: font-size.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { FontSizeConfig, FONT_SIZE_PROPERTY_TYPE, FontSizePropertyType } from './FontSizeConfig';
import { isWholeVarExpression } from '../core/types/LengthValue';
import { kwLower, lengthCss } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isFontSizeProperty(type: string): type is FontSizePropertyType {
  return type === FONT_SIZE_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // FontSize flavours (see FontSizePropertyParser.kt + FontSizeSerializer in
  // FontSizeProperty.kt — the serializer is the authority on the wire shape):
  //   { px:N, original:{...} }                     — resolved length
  //   { original: { keyword:'large', type:'absolute' } }  — keyword
  //   { original: { keyword:'larger', type:'relative' } } — relative keyword
  //   { original: { expr:'calc(...)', type:'expression' } } — calc
  //   { original: { type:'length', original:{v,u} } }  — RELATIVE length
  //     (em/rem/…): IRLengthSerializer omits the top-level px when the unit
  //     is not absolute AND deep-flattens the IRLength wire directly into
  //     the envelope (live shape verified against ./gradlew :converter:run
  //     output for `font-size: 1.5em` — there is NO `value` key on the wire).
  //   { original: { type:'percentage', value:N } }  — <percentage> of parent size
  // Prefer keyword (preserves intent), then calc, then relative length /
  // percentage (which have NO px to fall back to), then px.
  if (data && typeof data === 'object') {                            // envelope guard
    const o = data as Record<string, unknown>;
    const orig = o.original as Record<string, unknown> | undefined;
    if (orig) {                                                      // keyword/calc paths
      if (typeof orig.keyword === 'string') return kwLower(orig.keyword);
      if (typeof orig.expr === 'string') {                           // calc/var expression passthrough
        const raw = orig.expr.trim();
        // Whole-value var() (wave-6 preserved dynamic value, e.g.
        // `font-size: var(--type)`) emits VERBATIM — a calc() wrapper would
        // corrupt keyword-valued tokens and break the preservation contract.
        if (isWholeVarExpression(raw)) return raw;
        return raw.startsWith('calc(') ? raw : `calc(${raw})`;
      }
      // Relative <length> (css-fonts-4 §3.1): the LIVE serializer deep-flattens
      // the IRLength wire INTO the envelope — `{type:'length', original:{v,u}}`
      // — so the {v,u} pair sits under `orig.original` (no `value` key; verified
      // against real converter output for `font-size: 1.5em`). Keep `orig.value`
      // as a fallback for the older nested shape so pre-flatten IR still renders.
      // Re-emit the ORIGINAL CSS token ('1.5rem' / '2em') via the shared length
      // alphabet — Chromium resolves relative units natively, keeping the
      // reference honest vs. the natives.
      if (orig.type === 'length') {
        const css = lengthCss(orig.original ?? orig.value);          // live flattened wire, then legacy nesting
        if (css !== undefined) return css;                           // unrecognised inner → try px below
      }
      // <percentage> (css-fonts-4 §3.1): percentage of the PARENT element's
      // computed font size — only the browser can resolve it, so pass '<N>%'.
      if (orig.type === 'percentage' && typeof orig.value === 'number') {
        return `${orig.value}%`;                                     // e.g. 120 → '120%'
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

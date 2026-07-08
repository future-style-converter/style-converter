// FontMaxSizeExtractor.ts — folds `FontMaxSize` IR properties into a FontMaxSizeConfig.
// Family: length-keyword.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { FontMaxSizeConfig, FONT_MAX_SIZE_PROPERTY_TYPE, FontMaxSizePropertyType } from './FontMaxSizeConfig';
import { lengthCss } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isFontMaxSizeProperty(type: string): type is FontMaxSizePropertyType {
  return type === FONT_MAX_SIZE_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // Mixed flavour: LengthValue OR bare keyword ('normal','auto','none',...).
  // Several flavours the base `extractLength` alphabet doesn't cover:
  //   {type:'percentage', percentage:N}   — text-indent / vertical-align / thickness
  //   {type:'number', value:N}            — tab-size integer form
  //   {original:{original:{v,u}}, px:0}   — LetterSpacing/WordSpacing em nested form
  if (data && typeof data === 'object') {                            // extra shape guard
    const o = data as Record<string, unknown>;
    // Percentage envelope with alternate key name (`percentage` instead of `value`).
    if (o.type === 'percentage' && typeof o.percentage === 'number') return `${o.percentage}%`;
    // Numeric envelope for tab-size.
    if (o.type === 'number' && typeof o.value === 'number') return o.value;
    // Letter-spacing/word-spacing nested-original form: px is 0 because the
    // parser couldn't resolve em without computed context — prefer the inner
    // original so the relative unit makes it to the browser intact.
    const inner = o.original as Record<string, unknown> | undefined;
    if (inner && typeof inner === 'object') {
      const innerInner = inner.original as Record<string, unknown> | undefined;
      if (innerInner && typeof innerInner.v === 'number' && typeof innerInner.u === 'string') {
        const u = String(innerInner.u).toLowerCase();                 // normalise unit casing
        const unit = u === 'percent' ? '%' : u;                       // CSS % is spelled '%'
        return `${innerInner.v}${unit}`;
      }
    }
  }
  const direct = lengthCss(data);                                    // first-pass length
  if (direct) return direct;                                         // got it
  if (data && typeof data === 'object') {                            // try inner 'original'
    const inner = (data as Record<string, unknown>).original;
    if (inner !== undefined) return lengthCss(inner);                // recurse one level
  }
  return undefined;                                                  // couldn't parse
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractFontMaxSize(properties: IRPropertyLike[]): FontMaxSizeConfig {
  const cfg: FontMaxSizeConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isFontMaxSizeProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

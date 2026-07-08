// LineHeightExtractor.ts — folds `LineHeight` IR properties into a LineHeightConfig.
// Family: line-height.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { LineHeightConfig, LINE_HEIGHT_PROPERTY_TYPE, LineHeightPropertyType } from './LineHeightConfig';
import { kwLower, lengthCss } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isLineHeightProperty(type: string): type is LineHeightPropertyType {
  return type === LINE_HEIGHT_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // LineHeight flavours (see LineHeightPropertyParser.kt):
  //   { multiplier:N, original:{type:'number'|'percentage'|...} | 'normal' }
  // Prefer emitting 'normal' verbatim, a unitless multiplier, or a length.
  if (data && typeof data === 'object') {                            // envelope guard
    const o = data as Record<string, unknown>;
    if (o.original === 'normal') return 'normal';                    // keyword passthrough
    const orig = o.original as Record<string, unknown> | undefined;
    if (orig) {                                                      // typed branch
      if (orig.type === 'number' && typeof orig.value === 'number') return orig.value;
      if (orig.type === 'percentage' && typeof orig.value === 'number') return `${orig.value}%`;
      if (orig.type === 'length') {                                  // delegate length path
        const l = lengthCss(orig);
        if (l) return l;
      }
    }
    if (typeof o.multiplier === 'number') return o.multiplier;       // fallback unitless
  }
  return kwLower(data);                                              // 'normal'
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractLineHeight(properties: IRPropertyLike[]): LineHeightConfig {
  const cfg: LineHeightConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isLineHeightProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

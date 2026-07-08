// TextShadowExtractor.ts — folds `TextShadow` IR properties into a TextShadowConfig.
// Family: text-shadow.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { TextShadowConfig, TEXT_SHADOW_PROPERTY_TYPE, TextShadowPropertyType } from './TextShadowConfig';
import { extractLength, toCssLength } from '../core/types/LengthValue';
import { extractColor } from '../core/types/ColorValue';
import { colorToCss } from '../color/DynamicColorCss';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isTextShadowProperty(type: string): type is TextShadowPropertyType {
  return type === TEXT_SHADOW_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // TextShadow: list of { x, y, blur, c } — empty array means 'none'.
  // Native CSS grammar: `<x> <y> <blur>? <color>?` repeated, comma-separated.
  if (!Array.isArray(data)) return undefined;                        // nothing to emit
  if (data.length === 0) return 'none';                              // CSS `none` clears inherited
  const parts: string[] = [];                                        // accumulator
  for (const s of data) {                                            // iterate shadows
    if (!s || typeof s !== 'object') continue;                       // skip garbage
    const o = s as Record<string, unknown>;
    const xL = extractLength(o.x); const yL = extractLength(o.y);    // parse offsets
    const blL = extractLength(o.blur);                               // parse blur
    const x = xL.kind !== 'unknown' ? toCssLength(xL) : '0';         // default 0
    const y = yL.kind !== 'unknown' ? toCssLength(yL) : '0';         // default 0
    const blur = blL.kind !== 'unknown' ? toCssLength(blL) : '0';    // default 0
    const c = extractColor(o.c ?? o.color);                          // color optional
    const colour = c.kind !== 'unknown' ? colorToCss(c) : '';        // omit when absent
    parts.push(`${x} ${y} ${blur}${colour ? ' ' + colour : ''}`);    // per-spec ordering
  }
  return parts.length ? parts.join(', ') : undefined;
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractTextShadow(properties: IRPropertyLike[]): TextShadowConfig {
  const cfg: TextShadowConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isTextShadowProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

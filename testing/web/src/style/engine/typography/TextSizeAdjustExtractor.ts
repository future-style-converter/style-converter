// TextSizeAdjustExtractor.ts — folds `TextSizeAdjust` IR properties into a TextSizeAdjustConfig.
// Family: text-size-adjust.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { TextSizeAdjustConfig, TEXT_SIZE_ADJUST_PROPERTY_TYPE, TextSizeAdjustPropertyType } from './TextSizeAdjustConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isTextSizeAdjustProperty(type: string): type is TextSizeAdjustPropertyType {
  return type === TEXT_SIZE_ADJUST_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // TextSizeAdjust: {type:'auto'|'none'} | {type:'percentage', value:N}.
  if (!data || typeof data !== 'object') return kwLower(data);
  const o = data as Record<string, unknown>;
  if (o.type === 'auto' || o.type === 'none') return String(o.type);
  if (o.type === 'percentage' && typeof o.value === 'number') return `${o.value}%`;
  return undefined;
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractTextSizeAdjust(properties: IRPropertyLike[]): TextSizeAdjustConfig {
  const cfg: TextSizeAdjustConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isTextSizeAdjustProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

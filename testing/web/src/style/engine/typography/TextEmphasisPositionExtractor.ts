// TextEmphasisPositionExtractor.ts — folds `TextEmphasisPosition` IR properties into a TextEmphasisPositionConfig.
// Family: text-emphasis-position.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { TextEmphasisPositionConfig, TEXT_EMPHASIS_POSITION_PROPERTY_TYPE, TextEmphasisPositionPropertyType } from './TextEmphasisPositionConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isTextEmphasisPositionProperty(type: string): type is TextEmphasisPositionPropertyType {
  return type === TEXT_EMPHASIS_POSITION_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // TextEmphasisPosition: bare 'OVER'|'UNDER' | {vertical,horizontal}
  if (typeof data === 'string') return kwLower(data);
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  const vParts: string[] = [];
  if (typeof o.vertical === 'string') { const k = kwLower(o.vertical); if (k) vParts.push(k); }
  if (typeof o.horizontal === 'string') { const k = kwLower(o.horizontal); if (k) vParts.push(k); }
  return vParts.length ? vParts.join(' ') : undefined;
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractTextEmphasisPosition(properties: IRPropertyLike[]): TextEmphasisPositionConfig {
  const cfg: TextEmphasisPositionConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isTextEmphasisPositionProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

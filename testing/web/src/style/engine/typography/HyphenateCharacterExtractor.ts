// HyphenateCharacterExtractor.ts — folds `HyphenateCharacter` IR properties into a HyphenateCharacterConfig.
// Family: hyphenate-character.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { HyphenateCharacterConfig, HYPHENATE_CHARACTER_PROPERTY_TYPE, HyphenateCharacterPropertyType } from './HyphenateCharacterConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isHyphenateCharacterProperty(type: string): type is HyphenateCharacterPropertyType {
  return type === HYPHENATE_CHARACTER_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // HyphenateCharacter: {type:'auto'} | {type:'string', value:'-'}
  if (!data || typeof data !== 'object') return kwLower(data);
  const o = data as Record<string, unknown>;
  if (o.type === 'auto') return 'auto';
  if (o.type === 'string' && typeof o.value === 'string') {
    return `"${o.value.replace(/"/g, '\\"')}"`;                    // CSS double-quoted string
  }
  return undefined;
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractHyphenateCharacter(properties: IRPropertyLike[]): HyphenateCharacterConfig {
  const cfg: HyphenateCharacterConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isHyphenateCharacterProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

// TextSpacingExtractor.ts — folds `TextSpacing` IR properties into a TextSpacingConfig.
// Family: text-spacing.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { TextSpacingConfig, TEXT_SPACING_PROPERTY_TYPE, TextSpacingPropertyType } from './TextSpacingConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isTextSpacingProperty(type: string): type is TextSpacingPropertyType {
  return type === TEXT_SPACING_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // TextSpacing: {type:'auto'|'none'|'normal'} | {type:'raw', value:'trim-start'}.
  if (!data || typeof data !== 'object') return kwLower(data);
  const o = data as Record<string, unknown>;
  if (o.type === 'auto' || o.type === 'none' || o.type === 'normal') return String(o.type);
  if (o.type === 'raw' && typeof o.value === 'string') return o.value; // already kebab-case
  return undefined;
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractTextSpacing(properties: IRPropertyLike[]): TextSpacingConfig {
  const cfg: TextSpacingConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isTextSpacingProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

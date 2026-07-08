// TextOverflowExtractor.ts — folds `TextOverflow` IR properties into a TextOverflowConfig.
// Family: text-overflow.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { TextOverflowConfig, TEXT_OVERFLOW_PROPERTY_TYPE, TextOverflowPropertyType } from './TextOverflowConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isTextOverflowProperty(type: string): type is TextOverflowPropertyType {
  return type === TEXT_OVERFLOW_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // TextOverflow: discriminated on a fully-qualified class name emitted by the
  // Kotlin parser (see TextOverflowProperty.TextOverflowValue subclasses).
  // Supports clip/ellipsis/fade/fade(length)/custom string/two-value.
  if (typeof data === 'string') return kwLower(data);                // bare 'CLIP'|'ELLIPSIS'
  if (!data || typeof data !== 'object') return undefined;
  const o = data as Record<string, unknown>;
  const tag = typeof o.type === 'string' ? o.type.split('.').pop() : '';
  switch (tag) {
    case 'Clip': return 'clip';                                      // CSS keyword
    case 'Ellipsis': return 'ellipsis';                              // CSS keyword
    case 'Fade':                                                     // fade() functional
      return typeof o.length === 'string' ? `fade(${o.length})` : 'fade';
    case 'CustomString':                                             // quoted-string form
      return typeof o.value === 'string' ? `"${o.value.replace(/"/g,'\\"')}"` : undefined;
    case 'TwoValue': {                                               // '<start> <end>' pair
      const s = typeof o.start === 'string' ? o.start.toLowerCase() : 'clip';
      const e = typeof o.end === 'string' ? o.end.toLowerCase() : 'clip';
      return `${s} ${e}`;
    }
    default: return kwLower(data);                                   // last resort
  }
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractTextOverflow(properties: IRPropertyLike[]): TextOverflowConfig {
  const cfg: TextOverflowConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isTextOverflowProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

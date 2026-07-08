// HyphenateLimitCharsExtractor.ts — folds `HyphenateLimitChars` IR properties into a HyphenateLimitCharsConfig.
// Family: hyphenate-limit-chars.  IR shapes catalogued during Phase-6 survey of
// examples/properties/typography/*.json after `./gradlew run` conversion.

import { HyphenateLimitCharsConfig, HYPHENATE_LIMIT_CHARS_PROPERTY_TYPE, HyphenateLimitCharsPropertyType } from './HyphenateLimitCharsConfig';
import { kwLower } from './_shared';

// Minimal IRProperty shape — keeps engine modules decoupled from IR types dir.
interface IRPropertyLike { type: string; data: unknown; }

// Narrowing predicate; mirrors the other engine modules (Phase 4/5 pattern).
export function isHyphenateLimitCharsProperty(type: string): type is HyphenateLimitCharsPropertyType {
  return type === HYPHENATE_LIMIT_CHARS_PROPERTY_TYPE;                                           // exact string match
}

// Per-family parse routine — returns the CSS value string (or undefined to drop).
function parse(data: unknown): string | number | undefined {
  // HyphenateLimitChars: {type:'auto'} | {type:'values', wordMin?, charsBefore?, charsAfter?}
  // CSS grammar: <auto> | <int>{1,3} — position-dependent defaults apply.
  if (!data || typeof data !== 'object') return kwLower(data);
  const o = data as Record<string, unknown>;
  if (o.type === 'auto') return 'auto';
  if (o.type === 'values') {
    const parts = [
      typeof o.wordMin === 'number' ? String(o.wordMin) : 'auto',
      typeof o.charsBefore === 'number' ? String(o.charsBefore) : 'auto',
      typeof o.charsAfter === 'number' ? String(o.charsAfter) : 'auto',
    ];
    // Trim trailing 'auto' tokens to emit the most compact native form.
    while (parts.length > 1 && parts[parts.length - 1] === 'auto') parts.pop();
    return parts.join(' ');
  }
  return undefined;
}

// Main entrypoint — last write wins, mirroring CSS cascade semantics.
export function extractHyphenateLimitChars(properties: IRPropertyLike[]): HyphenateLimitCharsConfig {
  const cfg: HyphenateLimitCharsConfig = {};                                      // blank accumulator
  for (const p of properties) {
    if (!isHyphenateLimitCharsProperty(p.type)) continue;                         // filter unrelated
    const v = parse(p.data);                                          // convert payload
    if (v !== undefined) cfg.value = v;                               // record result
  }
  return cfg;
}

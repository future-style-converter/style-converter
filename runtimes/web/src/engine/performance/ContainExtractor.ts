// ContainExtractor.ts — folds IR `Contain` values into a CSS `contain` string.
//
// WIRE SHAPE (the reason this file no longer delegates to keywordOrRaw):
// ContainProperty is `data class ContainProperty(val values: List<ContainValue>)`
// (converter/src/main/kotlin/app/irmodels/properties/performance/ContainProperty.kt),
// and kotlinx.serialization flattens a single-field data class whose field is a
// list to a BARE JSON ARRAY.  Measured on the wave-35 full-corpus css-contain
// IR (tools/titan/runs/wave35-webmap/sections/css-contain/out/tmpOutput.json):
//
//     {"type":"Contain","data":["STRICT"]}
//     {"type":"Contain","data":["LAYOUT","PAINT"]}
//     {"type":"Contain","data":["INLINE_SIZE"]}
//
// `keywordOrRaw` has no array branch — it probes `.keyword`/`.value`/`.raw`/
// `.type` on the object and returns undefined for an Array — so EVERY `contain`
// declaration in the corpus was silently dropped and the web runtime emitted no
// containment at all (wave-35 web map: css-contain 176/280, 104 failing cells,
// the classification army root-caused all 173 `web-engine` rows to exactly this).
// The old unit pin passed because it fed `data: 'STRICT'`, a shape the converter
// never emits.
//
// Grammar mirrored: css-contain-1 §2 —
//   contain: none | strict | content | [ [size | inline-size] || layout || style || paint ]
// The IR enum (ContainProperty.ContainValue) is that grammar in SHOUTY_SNAKE,
// so the whole conversion is "kebab each token, join with a space" once the
// tokens are validated against the enum.

import { foldLast, kebab, type IRPropertyLike } from '../_phase10_shared';
import { logUnhandled } from '../PropertyTracker';
import type { ContainConfig } from './ContainConfig';
import { CONTAIN_PROPERTY_TYPE } from './ContainConfig';

// The exact ContainProperty.ContainValue enum, kebab-cased.  Anything outside
// this set is not a value the CSS reader can have produced, so it is reported
// rather than smuggled into the declaration (CLAUDE.md: no silent fallthroughs).
const CONTAIN_KEYWORDS = new Set([
  'none', 'strict', 'content', 'size', 'layout', 'style', 'paint', 'inline-size',
]);

// One IR leaf → the CSS value string, or undefined when nothing usable is there.
// Handles the array wire (normal), a bare/space-separated string (defensive —
// this is what the pre-wave-36 unit pin exercised and what a hand-authored
// conformance fixture may carry) and the `{values:[…]}` object form a future
// serializer flag could produce.
export function parseContain(data: unknown): string | undefined {
  // 1. Normal emission: a bare JSON array of enum names.
  const tokens = tokensOf(data);
  if (tokens === undefined) return undefined;
  const out: string[] = [];
  for (const t of tokens) {
    const k = kebab(t);                                      // INLINE_SIZE → inline-size
    if (k === undefined) continue;                           // non-string element: skip
    if (!CONTAIN_KEYWORDS.has(k)) {
      // Not in the reader's enum — name it instead of emitting invalid CSS,
      // which the browser would drop wholesale (taking the valid tokens with it).
      logUnhandled(CONTAIN_PROPERTY_TYPE, `unknown keyword "${k}"`);
      continue;
    }
    if (!out.includes(k)) out.push(k);                       // de-dupe, preserve order
  }
  if (out.length === 0) return undefined;
  // `none` is exclusive per the grammar; if it appears at all the whole
  // declaration is `none` (the reader only ever emits it alone, but a
  // hand-authored fixture could pair it and CSS would then be invalid).
  if (out.includes('none')) return 'none';
  return out.join(' ');
}

// Pull the raw token list out of whichever of the three shapes we were given.
function tokensOf(data: unknown): unknown[] | undefined {
  if (data === undefined || data === null) return undefined;
  if (Array.isArray(data)) return data;                      // {"data":["LAYOUT","PAINT"]}
  if (typeof data === 'string') {                            // {"data":"layout paint"}
    const parts = data.trim().split(/\s+/).filter(Boolean);
    return parts.length ? parts : undefined;
  }
  if (typeof data === 'object') {                            // {"data":{"values":[…]}}
    const v = (data as Record<string, unknown>).values;
    if (Array.isArray(v)) return v;
  }
  return undefined;
}

export function extractContain(properties: IRPropertyLike[]): ContainConfig {
  return { value: foldLast(properties, CONTAIN_PROPERTY_TYPE, parseContain) };
}

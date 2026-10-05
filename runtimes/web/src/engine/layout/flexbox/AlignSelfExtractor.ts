// AlignSelfExtractor.ts — folds IR `AlignSelf` properties into a AlignSelfConfig.
// Every variant in parsing/css/properties/longhands/layout/**/AlignSelfPropertyParser.kt
// is a single typed keyword; `kebab()` handles the SHOUTY_SNAKE -> kebab-case rewrite
// and drops unknown shapes (returns undefined) so cascade is safe.
//
// Wave 52 (lane L7, static-position T3): the converter now types the two-token
// <baseline-position> values (css-align-3 §4.2) — `last baseline` arrives as
// LAST_BASELINE. kebab() would turn that into `last-baseline`, which is NOT CSS
// (`CSS.supports('align-self', 'last-baseline')` is false) and the browser would
// drop it exactly as it dropped the old Generic passthrough — the 12
// css-grid/abspos/*-last-baseline-* web cells (0.9954–0.9983, mark at the
// grid-area start instead of the `safe self-end` fallback). Two-token keywords
// therefore map to their SPACE-separated CSS spelling before kebab() runs.

import { AlignSelfConfig, ALIGNSELF_PROPERTY_TYPE } from './AlignSelfConfig';
import { foldLast, kebab, type IRPropertyLike } from '../_shared';

// IR enum name → CSS value for the keywords whose CSS spelling has a SPACE
// (css-align-3 §4.2 <baseline-position>). FIRST_BASELINE is not emitted by the
// converter (it types `first baseline` as BASELINE, the same value) but is
// tolerated for hand-written IR.
const SPACED_KEYWORDS: Record<string, string> = {
  LAST_BASELINE: 'last baseline',                                   // §4.2 last-baseline alignment
  FIRST_BASELINE: 'first baseline',                                 // §4.2 ≡ `baseline`
};

// One IR payload → CSS keyword: spaced table first, then the generic kebab rewrite.
export function alignSelfKeyword(data: unknown): string | undefined {
  if (typeof data === 'string' && data.toUpperCase() in SPACED_KEYWORDS) {
    return SPACED_KEYWORDS[data.toUpperCase()];                     // keep the space — kebab() would break it
  }
  return kebab(data);                                               // single keywords: FLEX_END -> flex-end
}

export function extractAlignSelf(properties: IRPropertyLike[]): AlignSelfConfig {
  // Last-write-wins cascade — identical to every other engine Extractor.
  return { value: foldLast(properties, ALIGNSELF_PROPERTY_TYPE, alignSelfKeyword) };
}

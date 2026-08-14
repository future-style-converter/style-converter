// WillChangeExtractor.ts — IR `WillChange` → the CSS `will-change` value.
//
// ── wave-40 lane T1: the LIST WIRE ───────────────────────────────────────────
//
// This used to fold through `keywordOrRaw`, which reads SCALAR wires only
// ({keyword}/{value}/{raw}/{type:'none'|'auto'|'normal'}). The converter emits
// `will-change` as a LIST — `WillChangeProperty(properties: List<WillChangeValue>)`
// in irmodels/properties/performance/WillChangeProperty.kt, serialised as
// `[{type:'property-name', name:'transform'}, …]` with tag-only variants
// `{type:'auto'|'scroll-position'|'contents'}`. An Array hits none of
// `keywordOrRaw`'s branches, so it returned undefined and EVERY `will-change`
// declaration was silently dropped on web.
//
// WHY IT IS NOT COSMETIC. `will-change` is a real rendering hint, and CSS
// Transforms 2 makes it observable: a compositing-promoted descendant of a
// `backface-visibility: hidden` + `transform-style: preserve-3d` box is
// VISIBLE, an unpromoted one is not. MEASURED with puppeteer on the pipeline's
// own Chromium (_diag40/T1/probe3.mjs), same markup twice:
//   child WITH    `will-change: transform` → 10 000 green px
//   child WITHOUT it                       →      0 green px
// That is exactly wave39-final's css-transforms/composited-under-rotateY-180deg-preserve-3d:
// our composed capture was BLANK (ink 0.00 % vs the ref's 4.27 %), tripping
// the presence + coverage-ratio vetoes at SSIM 0.9565. The four sibling
// `composited-under-rotateY-180deg*` tests score 1.0000 because their visible
// half does not depend on the promotion.
//
// Mirrors css-will-change-1 §3 (`<animateable-feature>#`): a COMMA-separated
// list, `auto` alone, or the two non-property features.

import { foldLast, kebab, type IRPropertyLike } from '../_phase10_shared';
import type { WillChangeConfig } from './WillChangeConfig';

/** The two tag-only features css-will-change-1 §3 defines beside a property
 *  name, plus `auto` (the initial value, which the parser also round-trips).
 *  A CLOSED set: an unrecognised tag drops its entry rather than inventing a
 *  token the browser would discard as invalid — and an invalid entry would
 *  poison the WHOLE comma list, not just itself. */
const TAG_ONLY_FEATURES = new Set(['auto', 'scroll-position', 'contents']);

/** One wire entry → its CSS token, or undefined when the shape is unknown. */
function featureToken(raw: unknown): string | undefined {
  // Bare string wire (defensive — a hand-authored fixture may say "transform").
  if (typeof raw === 'string') {
    const s = raw.trim().toLowerCase();
    return s.length > 0 ? s : undefined;
  }
  if (!raw || typeof raw !== 'object') return undefined;                            // unknown primitive
  const o = raw as Record<string, unknown>;
  // `{type:'property-name', name:'transform'}` — the common case. The name is a
  // CSS property identifier already; emit it verbatim (lowercased).
  if (o.type === 'property-name') {
    const name = typeof o.name === 'string' ? o.name.trim().toLowerCase() : '';
    return name.length > 0 ? name : undefined;
  }
  // `{type:'auto'|'scroll-position'|'contents'}` — tag-only variants. `kebab`
  // is the same SHOUTY_SNAKE→kebab helper every enum leaf uses, so an
  // `AUTO`-style tag reads too.
  const tag = kebab(o.type);
  if (tag && TAG_ONLY_FEATURES.has(tag)) return tag;
  return undefined;                                                                 // no silent fallthrough
}

/** List wire → the comma-separated CSS value; undefined when nothing readable. */
function parseOne(data: unknown): string | undefined {
  if (data === null || data === undefined) return undefined;                        // absent
  // Bare keyword wire (`will-change: auto` may arrive unwrapped).
  if (typeof data === 'string') return featureToken(data);
  if (!Array.isArray(data)) {
    // Scalar object wire — keep the historical single-value tolerance.
    return featureToken(data);
  }
  const tokens = data.map(featureToken).filter((t): t is string => t !== undefined);
  if (tokens.length === 0) return undefined;                                        // nothing legible → drop
  return tokens.join(', ');                                                          // css-will-change-1 §3
}

export function extractWillChange(properties: IRPropertyLike[]): WillChangeConfig {
  return { value: foldLast(properties, 'WillChange', parseOne) };
}

// _shared.ts — primitives used by every layout triplet.
// Layout is the simplest phase to map because CSS is literally native; this
// file therefore focuses on *shape-tolerance* (the IR emits several equivalent
// payloads for the same CSS concept) rather than value translation.  Every
// helper is pure, returns `undefined` on unknown shapes so cascade last-
// write-wins is safe, and ≤200-line ceiling is never threatened by callers.

import { extractLength as extractLengthRaw } from '../../core/types/ValueExtractors';
// Minimal IR property shape: we deliberately avoid importing IRModels so the
// engine stays decoupled from any single IR file version (pattern stolen
// from typography/_shared.ts).
export interface IRPropertyLike { type: string; data: unknown; }

// Lowercase + kebab-case normaliser for bare IR enum strings
// ('FLEX_START' -> 'flex-start').  All CSS keywords in the layout category
// accept kebab-case; parser emits SHOUTY_SNAKE, so every applier runs this.
// See parsing/css/properties/longhands/layout/*PropertyParser.kt for the full
// keyword set each IR enum draws from.
export function kebab(data: unknown): string | undefined {
  if (typeof data !== 'string') return undefined;                                 // non-string = unknown shape
  if (data.length === 0) return undefined;                                        // defensive — never emit ''
  return data.toLowerCase().replace(/_/g, '-');                                   // SHOUTY_SNAKE -> kebab-case
}

// A length whose IR shape the layout parsers may emit as any of:
//   - bare number              (treated as percentage, e.g. FlexBasis=50)
//   - { px:N }                 (normalised pixel length)
//   - { type:'length', px:N }  (typed length)
//   - { type:'percentage', value:N }
//   - { expr:'calc(…)' }       (pre-normalisation escape hatch)
//   - 'auto' / 'normal'        (keyword)
// CSS accepts the result verbatim — this helper just picks the right emission.
export function layoutLength(data: unknown): string | undefined {
  if (data === undefined || data === null) return undefined;                       // absent → drop
  if (typeof data === 'string') return kebab(data) ?? data;                        // keyword path (auto, normal)
  if (typeof data === 'number') return `${data}%`;                                 // bare number → percentage (Top/Left/FlexBasis parsers)
  if (typeof data !== 'object') return undefined;                                  // other primitives: unknown
  const o = data as Record<string, unknown>;                                       // narrowed object access
  if (typeof o.expr === 'string') return o.expr;                                   // calc() / var() escape hatch passthrough
  if (o.type === 'percentage' && typeof o.value === 'number') return `${o.value}%`;// typed percentage
  if (o.type === 'length' && typeof o.px === 'number') return `${o.px}px`;         // typed length → px
  if (typeof o.px === 'number') return `${o.px}px`;                                // untyped length → px
  // original length with {v,u} — fall back to core ValueExtractor.
  const viaCore = extractLengthRaw(data);                                          // '10em' / '10%' / null
  return viaCore ?? undefined;                                                     // propagate null→undefined for uniform gating
}

// Array-or-single helper: turns an array of string tokens into a
// space-separated CSS list, or returns a single token.  Used by
// GridTemplate*, PositionTryOptions, AlignTracks (multi), AnchorName (multiple).
export function spaceList(parts: ReadonlyArray<string | undefined>): string | undefined {
  const tokens = parts.filter((t): t is string => typeof t === 'string' && t.length > 0);
  return tokens.length ? tokens.join(' ') : undefined;                             // empty → drop
}

// Last-write-wins fold.  Every layout extractor is a filter over the full IR
// list followed by a reduce that records the final matching payload; this
// helper centralises the pattern so each Extractor body stays ≤10 lines.
export function foldLast<T>(
  properties: IRPropertyLike[],
  type: string,
  parse: (data: unknown) => T | undefined,
): T | undefined {
  let out: T | undefined;                                                          // default = absent
  for (const p of properties) {                                                    // iterate; no early exit (cascade)
    if (p.type !== type) continue;                                                 // filter foreign types
    const v = parse(p.data);                                                       // shape-tolerant parse
    if (v !== undefined) out = v;                                                  // record last valid hit
  }
  return out;
}

// Helper for the many enum-only properties (Display, FlexDirection, FlexWrap,
// JustifyContent, AlignItems, AlignContent, AlignSelf, JustifyItems, Clear,
// Float, Overlay, ReadingFlow, GridAutoFlow-bare, AnchorScope, PositionTryOrder,
// PositionVisibility).  The extractor is literally `foldLast(props, TYPE, kebab)`
// and the applier emits `{ cssKey: config.value }` — the whole triplet fits
// comfortably in ≤30 lines.  Kept as a factory to avoid copy-paste drift.
export function enumExtractor(type: string) {
  return (properties: IRPropertyLike[]) => ({ value: foldLast(properties, type, kebab) });
}

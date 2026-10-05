// _dispatch.ts — Phase-10 global (CSS `all` longhand-reset) dispatch.
import type { CSSProperties } from 'react';
import { extractAll } from './AllExtractor';
import { applyAll } from './AllApplier';
import { ALL_PROPERTY_TYPE } from './AllConfig';
import { keywordOrRaw } from '../_phase10_shared';
interface IRPropertyLike { type: string; data: unknown }

// `{ all: <kw> }` from the LAST keyword `All` entry (or `{}` when none).
// StyleBuilder emits it as the FIRST key of the style object (wave 52, below).
export function applyGlobalPhase10(properties: IRPropertyLike[]): CSSProperties {
  return applyAll(extractAll(properties));
}

// css-cascade-4 §3.1: the two longhands the `all` shorthand never resets.
export const ALL_RESET_EXEMPT_TYPES: ReadonlySet<string> = new Set(['Direction', 'UnicodeBidi']);

/**
 * Wave 52 (lane L11, brief `colour-not-reaching-text` F1) — the own-list half
 * of the ORDER-AWARE `all` reset; twin of Compose `global/AllReset.kt` and iOS
 * `GlobalExtractor.applyingAllReset(own:inherited:)`.
 *
 * Why: the style object is consumed in KEY ORDER (React's style writer and
 * CssText's `Object.entries` both walk it), and `all` used to be inserted
 * LAST (StyleBuilder's phase-10 tail), so the browser applied the shorthand
 * AFTER `color: green` and reset it — `css-cascade/all-prop-initial-color`
 * painted black in a serif face where the source says `all: initial;
 * color: green` (css-cascade-4 §6.4: the later longhand wins). The fix emits
 * `all` FIRST and filters the list here so the remaining keys are exactly the
 * declarations that FOLLOW the last `all` in source order:
 *   - the LAST keyword `All` at index i governs (§6.4);
 *   - own[0, i) is dropped except `Direction` / `UnicodeBidi` (§3.1 — the
 *     shorthand does not touch them, so keeping them is CSS-identical);
 *   - own(i, end] is kept, minus any further `All` entry.
 * The inherited half of the rule needs no code on web: the browser itself
 * resolves `initial` / `inherit` / `unset` / `revert*` against the DOM parent.
 * Returns the SAME array when there is no keyword `All` (the whole corpus).
 */
export function applyAllReset<T extends IRPropertyLike>(properties: T[]): T[] {
  // Step 1 — index of the last `All` whose payload yields a keyword.
  let i = -1;
  properties.forEach((p, k) => {
    if (p.type === ALL_PROPERTY_TYPE && keywordOrRaw(p.data) !== undefined) i = k;
  });
  // No `all` on this element: identity (the fast path every other list takes).
  if (i < 0) return properties;
  // Step 2 — before the shorthand: only the §3.1 exemptions survive.
  const before = properties.slice(0, i).filter((p) => ALL_RESET_EXEMPT_TYPES.has(p.type));
  // Step 3 — after the shorthand: kept verbatim, any further `All` removed.
  const after = properties.slice(i + 1).filter((p) => p.type !== ALL_PROPERTY_TYPE);
  // Source order preserved within each half.
  return [...before, ...after];
}

// apps/web-harness/capture-divergence.mjs
//
// B-RC3 (wave 21) — detector for composed-capture PAINT-DIVERGENCE-PRONE
// WPT tests.
//
// THE BUG THIS GUARDS AGAINST (capture-side only — NOT the web engine):
// headless Chromium mis-PAINTS `position:absolute` boxes whose containing
// block is promoted OUTSIDE a `column-span:all` spanner (CSS Multi-column
// §6.1: a spanner interrupts the multicol flow, so an abspos whose
// positioned ancestor sits outside it resolves against that outer box) —
// but ONLY on the tall multi-canvas COMPOSED capture page. In-page geometry
// is CORRECT (getBoundingClientRect puts the boxes at their canvas
// corners); the compositor paints their ink ~2312px away. The same test on
// a fresh page with a single canvas paints correctly, so the workaround is
// to RE-CAPTURE flagged tests on an isolated page (capture-isolated.mjs)
// while everything else keeps the fast batch path.
//
// DETECTOR CHOICE: the pragmatic IR predicate — a test is flagged when its
// fixture carries BOTH (a) a `column-span:all` component (the spanner that
// triggers Chromium's containing-block promotion path) AND (b) an
// out-of-flow positioned component (`position:absolute|fixed` — both are
// the out-of-flow family whose containing block is resolved via ancestor
// promotion, CSS Positioned Layout §3.1). This over-approximates (a
// spanner+abspos test that happens to paint fine still takes the isolated
// path) but never under-approximates within the observed failure family,
// and the only cost of a false positive is one extra ~1s isolated capture.
// Every flagged test is LOGGED by the capture driver — no silent
// divergence-handling, per the per-property contract's no-fallthrough rule.
//
// Grouping reuses tools/titan/split-combined-ir.mjs's `splitCombinedIr`
// VERBATIM (the same slot.parent-walk grouping ComposedCaptureGallery
// mirrors), so the emitted keys are byte-identical to the composed page's
// `data-capture-name` values — the driver can intersect them directly.
//
// Dependency note: split-combined-ir.mjs only imports node builtins + the
// dependency-free safe-name.mjs, so this cross-workspace import is as safe
// as capture-screenshots.mjs's existing safe-name import.

import { splitCombinedIr } from '../../tools/titan/split-combined-ir.mjs';

/**
 * Normalise an IR property's data payload to an UPPER-CASE keyword string.
 * The Kotlin enum serializer writes enums upper-cased ("ABSOLUTE", "ALL")
 * while spec-grade longhand parsers write the literal CSS value lowercased
 * ("absolute", "all") — the CaptureGallery parentCreatesContext predicate
 * hit exactly this dual-shape hazard, so we accept both from day one.
 * Non-string payloads (objects/numbers/null) return '' so callers' keyword
 * comparisons simply fail to match instead of throwing.
 */
function keywordOf(data) {
  return typeof data === 'string' ? data.toUpperCase() : '';
}

/**
 * True when the component declares `column-span: all` — the spanner that
 * interrupts the multicol flow (css-multicol-1 §6.1) and triggers Chromium's
 * abspos containing-block promotion path. `column-span: none` is inert
 * (the initial value — no interruption, no promotion), so only ALL fires.
 */
export function componentSpansAllColumns(component) {
  // Defensive `?? []`: a component with no properties key groups as inert.
  return (component?.properties ?? []).some(
    (p) => p?.type === 'ColumnSpan' && keywordOf(p.data) === 'ALL'
  );
}

/**
 * True when the component is OUT-OF-FLOW positioned — `position: absolute`
 * or `position: fixed` (CSS Positioned Layout §3.1: the two values whose
 * box is removed from flow and whose containing block is resolved by
 * ancestor promotion — the resolution Chromium mis-paints next to a
 * spanner). `relative`/`sticky`/`static` stay in flow and are NOT prone.
 */
export function componentIsOutOfFlow(component) {
  return (component?.properties ?? []).some(
    (p) => p?.type === 'Position' && ['ABSOLUTE', 'FIXED'].includes(keywordOf(p.data))
  );
}

/**
 * The per-test predicate: a test's component set is divergence-prone when
 * it contains BOTH a spanner and an out-of-flow box (in ANY components —
 * the promoted containing block sits outside the spanner, so the two
 * triggers are usually different components; requiring co-location on one
 * component would miss the observed failure entirely).
 */
export function isDivergenceProne(components) {
  // Array.some twice is O(2n) on tiny per-test arrays (≤ ~20 components) —
  // clarity beats a fused single pass here.
  return (
    (components ?? []).some(componentSpansAllColumns) &&
    (components ?? []).some(componentIsOutOfFlow)
  );
}

/**
 * Full-document entry point for the capture driver: group the COMBINED IR
 * document (exactly what the composed page fetched from
 * /ir-components.json) into per-test component sets and return the Set of
 * test keys that must take the isolated capture path. Keys come from
 * splitCombinedIr, so they equal the composed page's `data-capture-name`
 * values byte-for-byte (both sides run the same rootTestKey/testKeyOf
 * algorithm).
 */
export function divergenceProneTestKeys(combined) {
  const prone = new Set();
  // splitCombinedIr throws on a non-IR input — the driver wants that loud
  // (a malformed served IR must fail the capture, not silently flag nothing).
  for (const { key, doc } of splitCombinedIr(combined)) {
    if (isDivergenceProne(doc.components)) prone.add(key);
  }
  return prone;
}

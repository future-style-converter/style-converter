// tools/visual/capture-divergence.test.mjs
//
// Unit tests for apps/web-harness/capture-divergence.mjs — the B-RC3
// (wave 21) detector that flags composed-capture WPT tests whose fixture
// pairs a `column-span:all` spanner with an out-of-flow (absolute/fixed)
// box. Headless Chromium mis-paints exactly that family on the tall
// multi-canvas composed page (~2312px paint offset with CORRECT geometry
// — css-multicol/abspos-containing-block-outside-spanner, web 0.932), so
// the capture driver routes flagged tests to an isolated single-canvas
// page. These tests pin:
//
//   1. The per-component predicates against BOTH serializer shapes
//      (Kotlin enum upper-case "ALL"/"ABSOLUTE" and spec-parser
//      lower-case), the same dual-shape hazard the CaptureGallery
//      parentCreatesContext predicate was bitten by.
//   2. The per-test AND (spanner alone or abspos alone must NOT flag —
//      every extra flag costs an isolated re-capture, and under-flagging
//      re-opens the silent-loss this wave exists to close).
//   3. The full-document grouping against the LIVE wire: synthetic
//      combined docs shaped exactly like build-combined-fixture output
//      (prefixed roots + raw-named children linked by slot.parent), plus
//      the real per-test IR vendored in tools/titan/fixtures/ (retro R10,
//      A8#3: this used to read a pruned wave21-gate run directory and so
//      skipped forever).
//
// Lives in tools/visual/ so CI's `node --test tools/visual/*.test.mjs
// tools/titan/*.test.mjs` glob picks it up (same placement rationale as
// capture-url.test.mjs).

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  componentSpansAllColumns,
  componentIsOutOfFlow,
  isDivergenceProne,
  divergenceProneTestKeys,
} from '../../apps/web-harness/capture-divergence.mjs';

// Shorthand builders keeping each case focused on the driving property.
const comp = (over = {}) => ({ id: 'c', name: 'c', properties: [], ...over });
const prop = (type, data) => ({ type, data });

// ── componentSpansAllColumns ───────────────────────────────────────────────

test('spanner predicate: ColumnSpan ALL fires (Kotlin enum upper-case wire)', () => {
  // Exact live shape: {"type":"ColumnSpan","data":"ALL"} (wave21-gate
  // css-multicol per-test IR).
  assert.equal(componentSpansAllColumns(comp({ properties: [prop('ColumnSpan', 'ALL')] })), true);
});

test('spanner predicate: lower-case "all" fires too (spec-parser shape)', () => {
  // Longhand parsers write the literal CSS value — both shapes must flag.
  assert.equal(componentSpansAllColumns(comp({ properties: [prop('ColumnSpan', 'all')] })), true);
});

test('spanner predicate: ColumnSpan NONE is inert (initial value, css-multicol-1 §6.1)', () => {
  // `none` interrupts nothing — no containing-block promotion, no bug.
  assert.equal(componentSpansAllColumns(comp({ properties: [prop('ColumnSpan', 'NONE')] })), false);
});

test('spanner predicate: non-string / missing payloads never throw, never flag', () => {
  // Defensive: a malformed property or a property-less component groups
  // as inert instead of crashing the capture driver.
  assert.equal(componentSpansAllColumns(comp({ properties: [prop('ColumnSpan', { odd: 1 })] })), false);
  assert.equal(componentSpansAllColumns(comp({ properties: undefined })), false);
  assert.equal(componentSpansAllColumns(undefined), false);
});

// ── componentIsOutOfFlow ───────────────────────────────────────────────────

test('out-of-flow predicate: absolute and fixed fire, in-flow values do not', () => {
  // CSS Positioned Layout §3.1 — absolute/fixed are the out-of-flow family
  // whose containing block is resolved by ancestor promotion (the path
  // Chromium mis-paints next to a spanner); relative/sticky/static stay in
  // flow and must not cost an isolated re-capture.
  assert.equal(componentIsOutOfFlow(comp({ properties: [prop('Position', 'ABSOLUTE')] })), true);
  assert.equal(componentIsOutOfFlow(comp({ properties: [prop('Position', 'FIXED')] })), true);
  assert.equal(componentIsOutOfFlow(comp({ properties: [prop('Position', 'absolute')] })), true);
  assert.equal(componentIsOutOfFlow(comp({ properties: [prop('Position', 'RELATIVE')] })), false);
  assert.equal(componentIsOutOfFlow(comp({ properties: [prop('Position', 'STICKY')] })), false);
  assert.equal(componentIsOutOfFlow(comp({ properties: [prop('Position', 'STATIC')] })), false);
});

// ── isDivergenceProne (the per-test AND) ───────────────────────────────────

test('prone: spanner + abspos on DIFFERENT components flags (the real topology)', () => {
  // In the observed failure the promoted containing block sits OUTSIDE the
  // spanner — the two triggers are separate components. Requiring
  // co-location would miss the bug entirely.
  const components = [
    comp({ id: 'a', properties: [prop('ColumnSpan', 'ALL')] }),
    comp({ id: 'b', properties: [prop('Position', 'ABSOLUTE')] }),
  ];
  assert.equal(isDivergenceProne(components), true);
});

test('prone: spanner alone / abspos alone / empty do NOT flag', () => {
  // Either half alone renders fine on the composed page (e.g.
  // always-balancing-before-column-span: spanner, no abspos; the
  // abspos-autopos family: abspos, no spanner) — flagging them would
  // needlessly slow every multicol/position section.
  assert.equal(isDivergenceProne([comp({ properties: [prop('ColumnSpan', 'ALL')] })]), false);
  assert.equal(isDivergenceProne([comp({ properties: [prop('Position', 'ABSOLUTE')] })]), false);
  assert.equal(isDivergenceProne([]), false);
  assert.equal(isDivergenceProne(undefined), false);
});

// ── divergenceProneTestKeys (grouped, full-document) ───────────────────────

test('keys: groups via slot.parent chains — a CHILD spanner flags its test', () => {
  // Synthetic combined doc shaped like build-combined-fixture output: only
  // ROOTS carry the `wpt__<section>__<stem>__<idx>` prefix; children keep
  // raw extracted names and link via slot.parent. The live prone test
  // (abspos-containing-block-outside-spanner) carries its spanner on a
  // grandchild — the grouping walk must attribute it to the root's key.
  const combined = {
    irVersion: 2, minReaderVersion: 2,
    components: [
      // Test A (prone): root → child spanner; sibling root carries abspos.
      { id: 'a0', name: 'wpt__css-multicol__prone-test__0', properties: [] },
      { id: 'a0c', name: 'prone-test__0__0', properties: [prop('ColumnSpan', 'ALL')], slot: { parent: 'a0' } },
      { id: 'a1', name: 'wpt__css-multicol__prone-test__1', properties: [prop('Position', 'ABSOLUTE')] },
      // Test B (not prone): spanner only.
      { id: 'b0', name: 'wpt__css-multicol__span-only__0', properties: [prop('ColumnSpan', 'ALL')] },
      // Test C (not prone): abspos only.
      { id: 'c0', name: 'wpt__css-position__abs-only__0', properties: [prop('Position', 'FIXED')] },
    ],
  };
  const keys = divergenceProneTestKeys(combined);
  assert.deepEqual([...keys], ['wpt__css-multicol__prone-test']);
});

test('keys: malformed document throws loudly (no silent zero-flag run)', () => {
  // A served IR that is not an IR document must crash the capture, not
  // quietly disable the guard.
  assert.throws(() => divergenceProneTestKeys({ nope: true }), /components array/);
});

// ── Live-artifact pins (VENDORED wave49-final css-multicol per-test IR) ───

// Retro R10 (finding A8#3): both pins below used to read
// `tools/titan/runs/wave21-gate/…`, a gitignored run directory that was
// pruned waves ago — so they skipped on every sweep, on every machine,
// permanently rather than hermetically. They now read the byte-verbatim
// per-test IR vendored under tools/titan/fixtures/ (see that dir's README
// for the provenance rules), so they EXECUTE on a fresh checkout and on CI.
const MULTICOL_IR = resolve(
  dirname(fileURLToPath(import.meta.url)),
  '../titan/fixtures/per-test-ir/wave49-final/css-multicol',
);

// Load one vendored per-test document's components by its file stem.
const loadMulticol = (stem) =>
  JSON.parse(readFileSync(resolve(MULTICOL_IR, `${stem}.json`), 'utf8')).components;

test('keys: the live css-multicol wire flags exactly the spanner+abspos family', () => {
  // Pin against the REAL wire that motivated B-RC3 — four documents with
  // known verdicts, combined the way build-combined-fixture combines them.
  const combined = {
    irVersion: 2, minReaderVersion: 2,
    components: [
      // PRONE — the B-RC3 poster child (web 0.932, paint ~2312px off).
      ...loadMulticol('wpt__css-multicol__abspos-containing-block-outside-spanner'),
      // PRONE — same spanner+abspos family.
      ...loadMulticol('wpt__css-multicol__abspos-after-spanner'),
      // NOT prone — spanner, no out-of-flow box.
      ...loadMulticol('wpt__css-multicol__always-balancing-before-column-span'),
      // NOT prone — abspos, no spanner.
      ...loadMulticol('wpt__css-multicol__abspos-autopos-contained-by-viewport-000'),
    ],
  };
  assert.deepEqual([...divergenceProneTestKeys(combined)].sort(), [
    'wpt__css-multicol__abspos-after-spanner',
    'wpt__css-multicol__abspos-containing-block-outside-spanner',
  ]);
});

test('keys: the vendored css-multicol set flags exactly the 7 wave49-final tests', () => {
  // Sweep the whole vendored set (not a hand-picked pair): 9 documents —
  // the 7 the detector flags in the live wave49-final css-multicol section
  // plus the 2 controls above. Measured 2026-09-05 by running this same
  // `divergenceProneTestKeys` over BOTH the vendored dir and the full
  // 48-document live section: identical 7-key sets, which is what makes the
  // subset a faithful stand-in. The wave-21 record for the first three:
  // static-pos and after-spanner are cost-only over-flags on this page
  // (their isolated captures came back byte-identical to the batch PNGs),
  // while containing-block-outside-spanner is the true positive (batch:
  // 20000 red px / 0 green; isolated: 20000 green px at the geometry rects
  // / 0 red). Pinning the whole set keeps any future detector tweak honest
  // about its live blast radius.
  const components = [];
  for (const f of readdirSync(MULTICOL_IR).filter((f) => f.endsWith('.json')).sort()) {
    components.push(...JSON.parse(readFileSync(resolve(MULTICOL_IR, f), 'utf8')).components);
  }
  const keys = divergenceProneTestKeys({ irVersion: 2, minReaderVersion: 2, components });
  assert.deepEqual([...keys].sort(), [
    'wpt__css-multicol__abspos-after-spanner',
    'wpt__css-multicol__abspos-after-spanner-static-pos',
    'wpt__css-multicol__abspos-containing-block-outside-spanner',
    'wpt__css-multicol__column-balancing-with-span-and-oof-001',
    'wpt__css-multicol__column-balancing-with-span-and-oof-002',
    'wpt__css-multicol__column-height-006',
    'wpt__css-multicol__column-height-013',
  ]);
});

test('keys: the vendored subset still matches the LIVE section when a run is on disk', (t) => {
  // Drift guard for the vendoring itself: when a campaign worktree carries
  // the wave49-final run, the full 48-document section must flag the SAME
  // 7 keys the vendored 9-document set does. Skip-guarded — but unlike the
  // pins above, nothing else depends on it: the pins execute everywhere.
  const irDir = resolve(
    dirname(fileURLToPath(import.meta.url)),
    '../titan/runs/wave49-final/sections/css-multicol/per-test-ir',
  );
  if (!existsSync(irDir)) {
    t.skip('wave49-final run artifacts not present on this machine (vendored pins above still ran)');
    return;
  }
  const live = [];
  for (const f of readdirSync(irDir).filter((f) => f.endsWith('.json')).sort()) {
    live.push(...JSON.parse(readFileSync(resolve(irDir, f), 'utf8')).components);
  }
  const vendored = [];
  for (const f of readdirSync(MULTICOL_IR).filter((f) => f.endsWith('.json')).sort()) {
    vendored.push(...JSON.parse(readFileSync(resolve(MULTICOL_IR, f), 'utf8')).components);
  }
  assert.deepEqual(
    [...divergenceProneTestKeys({ irVersion: 2, minReaderVersion: 2, components: live })].sort(),
    [...divergenceProneTestKeys({ irVersion: 2, minReaderVersion: 2, components: vendored })].sort(),
  );
});

// ── Driver source pin: batch loop must screenshot EVERY canvas ─────────────

test('driver: prone canvases are batch-screenshotted too (scroll-sequence parity), then overwritten', () => {
  // REGRESSION PIN for the wave-21 adversarial catch: the first B-RC3
  // driver SKIPPED flagged canvases in the batch per-element loop. Each
  // elementHandle.screenshot scrolls its canvas into view, so skipping
  // shifted the scroll SEQUENCE — and flipped the paint of an UNFLAGGED
  // scroll-sensitive neighbor (css-multicol/abspos-multicol-in-second-
  // outer-clipped went green→red vs the wave-21 archived capture, 7500px).
  // The batch loop must therefore screenshot every non-zero-dim canvas —
  // including prone ones, whose slot the isolated pass overwrites — so the
  // batch environment stays byte-identical to the pre-B-RC3 driver. The
  // driver is top-level-await CLI (unimportable), so this is a source pin,
  // same style as wpt-white-canvas.test.mjs's wiring scans.
  const here = dirname(fileURLToPath(import.meta.url));
  const src = readFileSync(resolve(here, '../../apps/web-harness/capture-screenshots.mjs'), 'utf8');
  // The prone branch queues WITHOUT `continue` — the screenshot below runs.
  assert.match(src, /if \(isProne\) isolatedQueue\.push\(entry\.name\);/);
  // The buggy skip shape must never come back: prone.has(...) guarding a
  // bare queue-then-continue before the screenshot call.
  assert.doesNotMatch(src, /prone\.has\(entry\.name\)\)\s*\{\s*isolatedQueue\.push\(entry\.name\);\s*continue;/);
});

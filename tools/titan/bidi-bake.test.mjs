// tools/titan/bidi-bake.test.mjs — unit pins for the wave-23 BIDI BAKE
// (bidi-bake.mjs; the static trigger it gates on lives in
// extract-fixture.mjs and is pinned in extract-fixture.test.mjs).
//
// Coverage map (mirrors the module's contract sections):
//   1. tunables + shared vocabulary (the exact epsilon, budget, block-ish
//      display set, author-level unicode-bidi set, lossy reason string);
//   2. the direction predicates (hasRtlCodepoint / hasStrongLtrCodepoint /
//      runDirectionConflict) — the mixed-run guard's whole basis;
//   3. groupCharRuns — the geometric definition of a UAX#9 level run:
//      LTR/RTL adjacency, line splits, tab breaks, edge-whitespace trim,
//      combining marks, collapsed line-end spaces;
//   4. geometry → property maps (paddingBoxOrigin / boxProperties /
//      rootProperties / runProperties), including the initial-value elisions;
//   5. root selection + plan scoping (selectBakeRoots, planBidiBake) — the
//      minimal-enclosure rule, the no-text-runs prune that keeps the
//      currently-perfect rtl LAYOUT tests untouched, and every bail;
//   6. the merge (appendChildComponent / componentIdForPath /
//      applyBidiBakePlan) — id shape, paint order, honesty stamps;
//   7. the PROVING SET — the three css-text/bidi wall fixtures' baked
//      geometry (skip-guarded: fixtures/wpt is a generated artifact, so
//      these pins only run on a machine that has run the bake);
//   8. a source-wiring pin for the extract-fixture.mjs CLI integration.

import test from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  RUN_ADJACENCY_EPS, MAX_BIDI_RUNS, BLOCKISH_DISPLAYS,
  AUTHOR_UNICODE_BIDI_VALUES, BIDI_BAKE_LOSSY_REASON,
  hasRtlCodepoint, hasStrongLtrCodepoint, hasStrongRtlLetter, runDirectionConflict,
  r2, px, groupCharRuns,
  isDescendantPath, selectBakeRoots, planBidiBake,
  paddingBoxOrigin, boxProperties, rootProperties, runProperties, needsExplicitRtl,
  appendChildComponent, componentIdForPath, applyBidiBakePlan, fixtureHasPseudo,
} from './bidi-bake.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(__dirname, '..', '..');
const FIX = join(REPO_ROOT, 'fixtures', 'wpt', 'css-text');

// ── 1. Tunables ─────────────────────────────────────────────────────────────

test('bidi-bake: tunables are pinned (a silent widening cannot land)', () => {
  // Sub-pixel: consecutive glyph boxes in one bidi level abut exactly, and
  // the smallest REAL gap this corpus contains is a 96.33px tab advance.
  assert.equal(RUN_ADJACENCY_EPS, 0.75);
  // The bake dissolves flow into absolute boxes; past this many runs the
  // fixture stops being a meaningful cross-platform comparison.
  assert.equal(MAX_BIDI_RUNS, 300);
  // A bake root must be able to carry an explicit used width/height AND host
  // absolutely positioned children — inline boxes can do neither.
  assert.ok(BLOCKISH_DISPLAYS.includes('block'));
  assert.ok(BLOCKISH_DISPLAYS.includes('flex'));
  assert.ok(BLOCKISH_DISPLAYS.includes('grid'));
  assert.ok(!BLOCKISH_DISPLAYS.includes('inline'));
  assert.ok(!BLOCKISH_DISPLAYS.includes('contents'));
});

test('bidi-bake: plain `isolate` is NOT an author bidi signal', () => {
  // The HTML rendering spec gives every block container `unicode-bidi:
  // isolate`; treating it as a signal would bake every block of every
  // triggered document (measured: Chromium reports it for a bare <p>).
  assert.deepEqual(AUTHOR_UNICODE_BIDI_VALUES,
    ['embed', 'bidi-override', 'isolate-override', 'plaintext']);
  assert.ok(!AUTHOR_UNICODE_BIDI_VALUES.includes('isolate'));
  assert.ok(!AUTHOR_UNICODE_BIDI_VALUES.includes('normal'));
});

test('bidi-bake: the lossy reason is a fresh string (never a score-gate tag)', () => {
  assert.equal(BIDI_BAKE_LOSSY_REASON, 'baked-bidi-visual-order');
  // inject-wpt-block's SCORE_EXCLUDED_TAGS are all `requires-*`; the bake's
  // marker must never look like one, or it would start excluding scores.
  assert.ok(!BIDI_BAKE_LOSSY_REASON.startsWith('requires-'));
});

// ── 2. Direction predicates ─────────────────────────────────────────────────

test('bidi-bake: hasRtlCodepoint covers the RTL script blocks, not the controls', () => {
  assert.equal(hasRtlCodepoint('فارسی'), true);      // Arabic
  assert.equal(hasRtlCodepoint('שלום'), true);       // Hebrew
  assert.equal(hasRtlCodepoint('\u{1E900}'), true);  // Adlam (astral — code-point walk)
  assert.equal(hasRtlCodepoint('français'), false);
  assert.equal(hasRtlCodepoint('0123 !?'), false);
  // U+200E LRM is a CONTROL, strong L — folding it into the RTL set would
  // make the mixed-run guard bail on ordinary LTR runs.
  assert.equal(hasRtlCodepoint('‎'), false);
  // U+FEFF ZWNBSP sits just past the Arabic Presentation Forms-B letters.
  assert.equal(hasRtlCodepoint('﻿'), false);
  assert.equal(hasRtlCodepoint('ﻼ'), true);
});

test('bidi-bake: hasStrongLtrCodepoint counts letters only', () => {
  assert.equal(hasStrongLtrCodepoint('Hello'), true);
  assert.equal(hasStrongLtrCodepoint('français'), true);
  // Digits are UAX#9 WEAK and punctuation NEUTRAL — neither makes a run
  // direction-ambiguous, so neither may trip the guard.
  assert.equal(hasStrongLtrCodepoint('0123'), false);
  assert.equal(hasStrongLtrCodepoint('!?“”, '), false);
  // An RTL letter is strong R, never strong L.
  assert.equal(hasStrongLtrCodepoint('سلام'), false);
});

test('bidi-bake: runDirectionConflict fires only on a genuinely mixed run', () => {
  assert.equal(runDirectionConflict('français'), false);
  assert.equal(runDirectionConflict('فارسی'), false);
  // Weak/neutral characters ride along with either direction.
  assert.equal(runDirectionConflict('سلام !'), false);
  assert.equal(runDirectionConflict('0'), false);
  // Strong L + strong R in ONE run has no single bidi level: the three text
  // stacks would each re-resolve it against their own paragraph direction.
  assert.equal(runDirectionConflict('abcسلام'), true);
});

test('bidi-bake: runDirectionConflict catches unicode-bidi: bidi-override', () => {
  // SKEPTIC WAVE-23 regression pin. Measured in Chromium under the canvas
  // contract: `direction:ltr; unicode-bidi:bidi-override` on `سلام` paints
  // the glyphs LEFT-TO-RIGHT, so the grouper reports dir='ltr' for a string
  // of strong-R letters. Re-emitted as a plain positioned box the same string
  // paints `مالس` — reversed. The string alone has only ONE strong class, so
  // the mixed test cannot see it; the measured direction can.
  assert.equal(runDirectionConflict('سلام'), false);            // no dir ⇒ old behaviour
  assert.equal(runDirectionConflict('سلام', 'rtl'), false);     // the normal case
  assert.equal(runDirectionConflict('سلام', 'ltr'), true);      // override
  assert.equal(runDirectionConflict('hello', 'ltr'), false);
  assert.equal(runDirectionConflict('hello', 'rtl'), true);     // override, other way
  // Arabic-Indic DIGITS are AN, not strong R, and an AN run legitimately
  // advances left-to-right (level 2) inside an RTL paragraph — it must NOT
  // read as an override even though its codepoints sit in the Arabic block.
  assert.equal(hasStrongRtlLetter('٠١٢٣'), false);
  assert.equal(hasStrongRtlLetter('سلام'), true);
  assert.equal(runDirectionConflict('٠١٢٣', 'ltr'), false);
  // A run with no measured direction (single glyph) can contradict nothing.
  assert.equal(runDirectionConflict('س', null), false);
});

// ── 3. groupCharRuns ────────────────────────────────────────────────────────

// Helper: build the walker's char shape from a compact [char, x, y, w, h] list.
const chars = (...rows) => rows.map(([c, x, y, w, h]) =>
  ({ c, rects: w === null ? [] : [{ x, y, w, h }] }));

test('groupCharRuns: an LTR level is one run with the union box', () => {
  const runs = groupCharRuns(chars(['a', 10, 0, 5, 20], ['b', 15, 0, 6, 20], ['c', 21, 0, 4, 20]));
  assert.equal(runs.length, 1);
  assert.equal(runs[0].text, 'abc');
  assert.equal(runs[0].dir, 'ltr');
  assert.deepEqual([runs[0].x, runs[0].y, runs[0].width, runs[0].height], [10, 0, 15, 20]);
});

test('groupCharRuns: an RTL level advances LEFTWARD and stays one run', () => {
  // Logical order س ل ا م, each box to the LEFT of the previous — exactly
  // the shape Chromium reports for بidi text (measured on bidi-lines-002).
  const runs = groupCharRuns(chars(['س', 30, 0, 10, 39], ['ل', 24, 0, 6, 39], ['ا', 18, 0, 6, 39]));
  assert.equal(runs.length, 1);
  assert.equal(runs[0].text, 'سلا');           // LOGICAL order is preserved
  assert.equal(runs[0].dir, 'rtl');
  assert.deepEqual([runs[0].x, runs[0].width], [18, 22]);
});

test('groupCharRuns: a level change splits, and each side keeps its own box', () => {
  // "ab" LTR at 0..10, then an RTL level at 30..40 (not adjacent), then the
  // LTR continuation at 40 — three runs, three boxes.
  const runs = groupCharRuns(chars(
    ['a', 0, 0, 5, 20], ['b', 5, 0, 5, 20],
    ['ب', 35, 0, 5, 20], ['ا', 30, 0, 5, 20],
    ['x', 40, 0, 5, 20],
  ));
  assert.deepEqual(runs.map((r) => r.text), ['ab', 'با', 'x']);
  assert.deepEqual(runs.map((r) => r.dir), ['ltr', 'rtl', null]);
  assert.deepEqual(runs.map((r) => r.x), [0, 30, 40]);
});

test('groupCharRuns: a different line top always breaks the run', () => {
  const runs = groupCharRuns(chars(['a', 0, 0, 5, 20], ['b', 5, 20, 5, 20]));
  assert.deepEqual(runs.map((r) => [r.text, r.y]), [['a', 0], ['b', 20]]);
});

test('groupCharRuns: a TAB breaks the run and is never re-emitted', () => {
  // The tab's own box IS the tab stop (measured 96.33px in bidi-tab-001);
  // re-emitting `\t` would make every engine recompute that advance.
  const runs = groupCharRuns(chars(['\t', 0, 0, 96.33, 24], ['0', 96.33, 0, 12.05, 24]));
  assert.equal(runs.length, 1);
  assert.equal(runs[0].text, '0');
  assert.equal(runs[0].x, 96.33);              // the advance survives as GEOMETRY
});

test('groupCharRuns: edge whitespace is trimmed and the box shrinks with it', () => {
  const runs = groupCharRuns(chars(
    [' ', 0, 0, 9, 39], ['H', 9, 0, 20, 39], ['i', 29, 0, 5, 39], [' ', 34, 0, 9, 39],
  ));
  assert.equal(runs.length, 1);
  assert.equal(runs[0].text, 'Hi');
  assert.deepEqual([runs[0].x, runs[0].width], [9, 25]);
});

test('groupCharRuns: an INTERNAL space is kept (white-space: pre carries it)', () => {
  const runs = groupCharRuns(chars(['a', 0, 0, 5, 20], [' ', 5, 0, 4, 20], ['b', 9, 0, 5, 20]));
  assert.equal(runs.length, 1);
  assert.equal(runs[0].text, 'a b');
});

test('groupCharRuns: a collapsed line-end space (zero-width) breaks the run', () => {
  // Chromium reports a soft-wrapped space as two ZERO-width boxes (one per
  // line). It is not ink and must not bridge the two lines.
  const runs = groupCharRuns([
    ...chars(['a', 0, 0, 5, 20]),
    { c: ' ', rects: [{ x: 5, y: 0, w: 0, h: 20 }, { x: 0, y: 20, w: 0, h: 20 }] },
    ...chars(['b', 0, 20, 5, 20]),
  ]);
  assert.deepEqual(runs.map((r) => [r.text, r.y]), [['a', 0], ['b', 20]]);
});

test('groupCharRuns: a zero-width combining mark rides along, a newline does not', () => {
  const runs = groupCharRuns([
    ...chars(['e', 0, 0, 5, 20]),
    { c: '́', rects: [{ x: 5, y: 0, w: 0, h: 20 }] },   // COMBINING ACUTE
    ...chars(['\n', 5, 0, null, 20]),
    ...chars(['x', 0, 20, 5, 20]),
  ]);
  assert.deepEqual(runs.map((r) => r.text), ['é', 'x']);
  // The mark carries no box of its own, so it cannot widen the union.
  assert.equal(runs[0].width, 5);
});

test('groupCharRuns: a whitespace-only fragment produces no run at all', () => {
  assert.deepEqual(groupCharRuns(chars([' ', 0, 0, 9, 20], ['\t', 9, 0, 20, 20])), []);
});

// ── 4. Geometry → property maps ─────────────────────────────────────────────

test('bidi-bake: r2 / px quantise to 2 dp', () => {
  assert.equal(r2(29.0912), 29.09);
  assert.equal(px(29.0912), '29.09px');
  assert.equal(px(0), '0px');
});

test('paddingBoxOrigin: abspos children resolve against the PADDING box', () => {
  // bidi-lines-001's `border: solid` → 3px medium on every side; the div's
  // border box starts at (16,108), so its children measure from (19,111).
  assert.deepEqual(paddingBoxOrigin({ rect: { x: 16, y: 108 }, borderLeft: 3, borderTop: 3 }),
    { x: 19, y: 111 });
});

test('boxProperties: used border-box rect, relative to the containing block', () => {
  assert.deepEqual(
    boxProperties({ x: 28.03, y: 66, width: 108.38, height: 24 }, { x: 16, y: 68 }),
    { position: 'absolute', left: '12.03px', top: '-2px',
      width: '108.38px', height: '24px', 'box-sizing': 'border-box' });
});

test('rootProperties: adds position:relative only for a STATIC root', () => {
  assert.deepEqual(rootProperties({ width: 346.19, height: 246 }, 'static'), {
    width: '346.19px', height: '246px', 'box-sizing': 'border-box', position: 'relative',
  });
  // An already-positioned root keeps its own scheme — relative would be a
  // no-op there, but overwriting `absolute` would move the box.
  assert.equal(rootProperties({ width: 10, height: 10 }, 'absolute').position, undefined);
  assert.equal(rootProperties({ width: 10, height: 10 }, 'relative').position, undefined);
});

test('runProperties: the context-free run box', () => {
  const props = runProperties(
    { text: 'فارسی', dir: 'rtl', x: 273.09, y: 151, width: 76, height: 39 },
    { color: 'rgb(0, 0, 0)', fontFamily: 'Inter, sans-serif', fontSize: '32px',
      fontStyle: 'normal', fontWeight: '400',
      letterSpacing: 'normal', wordSpacing: '0px', textTransform: 'none' },
    { x: 19, y: 111 });
  assert.equal(props.position, 'absolute');
  assert.deepEqual([props.left, props.top, props.width, props.height],
    ['254.09px', '40px', '76px', '39px']);
  // line-height pinned to the measured box ⇒ zero half-leading ⇒ the
  // baseline lands where Chromium put it.
  assert.equal(props['line-height'], '39px');
  // Context neutralisers: the run must not inherit a paragraph direction or
  // alignment from the boxes the bake dissolved. A PURE strong-R run orders
  // identically either way, so it keeps the neutral `ltr` (skeptic wave-23:
  // emitting `rtl` here would shift its ink sub-pixel for no gain — see the
  // neutral-flip pin below for the case that DOES need it).
  assert.equal(props.direction, 'ltr');
  assert.equal(props['text-align'], 'left');
  assert.equal(props['white-space'], 'pre');
  // Initial values are elided — a fixture full of `letter-spacing: normal`
  // is noise, and every extra declaration is another parser surface.
  assert.equal('letter-spacing' in props, false);
  assert.equal('word-spacing' in props, false);
  assert.equal('text-transform' in props, false);
});

test('runProperties: the run states its OWN level direction (neutral-flip regression)', () => {
  // Skeptic wave-23 REGRESSION PIN. Measured in Chromium under the canvas
  // contract: `<div dir=rtl>!سلام</div>` groups into ONE level-1 run whose
  // logical text is `!سلام`; Chromium paints it `مالس!` (the `!` at the RIGHT
  // end). Re-emitted with a hard-coded `direction: ltr` the same string
  // paints `!مالس` — UAX#9 N1/N2 resolve the boundary neutral against the
  // paragraph level, so the `!` jumps to the LEFT end. The trailing-neutral
  // (`سلام!`) and `سلام (12)` variants flip the same way, and
  // runDirectionConflict() cannot catch any of them (a neutral is not a
  // strong-L letter, so the run is not "direction-ambiguous" by that test).
  const rtlNeutral = runProperties(
    { text: '!سلام', dir: 'rtl', x: 0, y: 0, width: 64.56, height: 39 },
    { color: 'rgb(0, 0, 0)', fontFamily: 'Inter', fontSize: '32px',
      fontStyle: 'normal', fontWeight: '400' }, { x: 0, y: 0 });
  assert.equal(rtlNeutral.direction, 'rtl');
  assert.equal(needsExplicitRtl({ text: '!سلام', dir: 'rtl' }), true);
  assert.equal(needsExplicitRtl({ text: 'سلام (', dir: 'rtl' }), true);
  // The narrow trigger: a PURE strong-R run needs nothing (it would only
  // move its own ink sub-pixel), and an LTR level never does.
  assert.equal(needsExplicitRtl({ text: 'سلام', dir: 'rtl' }), false);
  assert.equal(needsExplicitRtl({ text: 'فارسی', dir: 'rtl' }), false);
  assert.equal(needsExplicitRtl({ text: 'hello!', dir: 'ltr' }), false);
  assert.equal(needsExplicitRtl({ text: '!', dir: null }), false);
  // An LTR level run keeps ltr — and a run with neutrals but no RTL is
  // already correct under ltr, so nothing about the LTR side moves.
  const ltrNeutral = runProperties(
    { text: 'hello, world!', dir: 'ltr', x: 0, y: 0, width: 100, height: 20 },
    { color: 'red', fontFamily: 'Inter', fontSize: '16px',
      fontStyle: 'normal', fontWeight: '400' }, { x: 0, y: 0 });
  assert.equal(ltrNeutral.direction, 'ltr');
  // A run that never continued past its first glyph has NO order to preserve
  // (groupCharRuns leaves `dir` null); it falls back to the neutral ltr.
  const single = runProperties(
    { text: '!', dir: null, x: 0, y: 0, width: 8, height: 20 },
    { color: 'red', fontFamily: 'Inter', fontSize: '16px',
      fontStyle: 'normal', fontWeight: '400' }, { x: 0, y: 0 });
  assert.equal(single.direction, 'ltr');
});

test('runProperties: non-initial metrics DO ride along', () => {
  const props = runProperties(
    { text: 'x', dir: 'ltr', x: 0, y: 0, width: 5, height: 20 },
    { color: 'red', fontFamily: 'monospace', fontSize: '20px', fontStyle: 'italic',
      fontWeight: '700', letterSpacing: '2px', wordSpacing: '4px', textTransform: 'uppercase' },
    { x: 0, y: 0 });
  assert.equal(props['letter-spacing'], '2px');
  assert.equal(props['word-spacing'], '4px');
  // The run string is the RAW node value; the geometry was measured on the
  // TRANSFORMED text, so the transform must travel with it.
  assert.equal(props['text-transform'], 'uppercase');
  assert.equal(props['font-style'], 'italic');
  assert.equal(props['font-weight'], '700');
});

// ── 5. Root selection + plan scoping ────────────────────────────────────────

test('isDescendantPath: prefix ⇔ ancestor, and never reflexive', () => {
  assert.equal(isDescendantPath([1], [1, 0]), true);
  assert.equal(isDescendantPath([1], [1]), false);
  assert.equal(isDescendantPath([1], [2, 0]), false);
  assert.equal(isDescendantPath([1, 0], [1]), false);
});

// Helper: a minimal walk element record.
const el = (path, over = {}) => ({
  path, tag: over.tag ?? 'div',
  rect: over.rect ?? { x: 0, y: 0, width: 100, height: 20 },
  rectCount: over.rectCount ?? 1,
  borderLeft: over.borderLeft ?? 0, borderTop: over.borderTop ?? 0,
  display: over.display ?? 'block', position: over.position ?? 'static',
  decoration: over.decoration ?? 'none',
  texts: over.texts ?? [],
  bidiAffected: over.bidiAffected ?? false,
});
// Helper: one text node whose characters form a single LTR run.
const oneRun = (text, x = 0, y = 0) => ([{
  style: { color: 'rgb(0,0,0)', fontFamily: 'Inter', fontSize: '16px',
           fontStyle: 'normal', fontWeight: '400',
           letterSpacing: 'normal', wordSpacing: '0px', textTransform: 'none' },
  chars: [...text].map((c, i) => ({ c, rects: [{ x: x + i * 5, y, w: 5, h: 20 }] })),
}]);

test('selectBakeRoots: the MINIMAL block enclosure, not the outermost', () => {
  const elements = [
    el([0], { tag: 'section' }),
    el([0, 0], { tag: 'p', texts: oneRun('عرب'), bidiAffected: true }),
  ];
  const { roots, reason } = selectBakeRoots(elements);
  assert.equal(reason, null);
  // The <p> is itself block-level, so the <section> around it stays in flow.
  assert.deepEqual(roots.map((r) => r.path), [[0, 0]]);
});

test('selectBakeRoots: an affected INLINE climbs to its block ancestor', () => {
  const elements = [
    el([0], { tag: 'div' }),
    el([0, 0], { tag: 'span', display: 'inline', texts: oneRun('!'), bidiAffected: true }),
  ];
  assert.deepEqual(selectBakeRoots(elements).roots.map((r) => r.path), [[0]]);
});

test('selectBakeRoots: nested roots collapse to the ancestor', () => {
  const elements = [
    el([0], { bidiAffected: true, texts: oneRun('a') }),
    el([0, 0], { bidiAffected: true, texts: oneRun('b') }),
  ];
  assert.deepEqual(selectBakeRoots(elements).roots.map((r) => r.path), [[0]]);
});

test('selectBakeRoots: a top-level affected inline WITH text bails, without text passes over', () => {
  const withText = [el([0], { tag: 'span', display: 'inline', bidiAffected: true, texts: oneRun('שלום') })];
  assert.match(selectBakeRoots(withText).reason, /no block-level bake root for <span>/);
  const withoutText = [el([0], { tag: 'span', display: 'inline', bidiAffected: true })];
  assert.equal(selectBakeRoots(withoutText).reason, null);
  assert.deepEqual(selectBakeRoots(withoutText).roots, []);
});

test('planBidiBake: an rtl LAYOUT test with no text is SKIPPED, never baked', () => {
  // THE SCOPE GUARD. `direction: rtl` on text-free boxes (measured:
  // css-flexbox/abspos/abspos-autopos-*-rtl, css-grid/abspos/
  // descendant-static-position-00{2,4} — all currently PERFECT) is a layout
  // question the engines already answer. Freezing their coordinates would
  // replace a real test with a replay.
  const walk = { bodyTextIsBidi: false, elements: [
    el([0], { bidiAffected: true }),
    el([0, 0], { bidiAffected: true }),
  ] };
  const { bail, plan, note } = planBidiBake(walk);
  assert.equal(bail, null);
  assert.equal(plan, null);
  assert.equal(note, 'no bidi text runs to bake');
});

test('planBidiBake: a triggered test with nothing affected is SKIPPED', () => {
  const { bail, plan, note } = planBidiBake({ bodyTextIsBidi: false, elements: [el([0])] });
  assert.equal(bail, null);
  assert.equal(plan, null);
  assert.equal(note, 'no bidi-affected element');
});

test('planBidiBake: the happy path — root, box, run, hide', () => {
  const walk = { bodyTextIsBidi: false, elements: [
    el([0], { rect: { x: 16, y: 100, width: 200, height: 40 }, borderLeft: 3, borderTop: 3,
              bidiAffected: true }),
    el([0, 0], { tag: 'span', display: 'inline', rect: { x: 19, y: 103, width: 50, height: 20 },
                 texts: oneRun('ab', 19, 103) }),
    el([0, 1], { tag: 'br', rectCount: 0 }),
  ] };
  const { bail, plan } = planBidiBake(walk);
  assert.equal(bail, null);
  assert.deepEqual(plan.roots.map((r) => r.path), [[0]]);
  assert.deepEqual(plan.boxes.map((b) => b.path), [[0, 0]]);
  assert.deepEqual(plan.hides, [[0, 1]]);
  assert.equal(plan.runs.length, 1);
  assert.equal(plan.runs[0].text, 'ab');
  // The run measures from its OWNER's padding box (the span at 19,103 with
  // no border of its own), not from the root's.
  assert.deepEqual([plan.runs[0].props.left, plan.runs[0].props.top], ['0px', '0px']);
  // The span's box measures from the ROOT's padding box (16+3, 100+3).
  assert.deepEqual([plan.boxes[0].props.left, plan.boxes[0].props.top], ['0px', '0px']);
  // The root gets the used size and a containing block.
  assert.equal(plan.roots[0].props.width, '200px');
  assert.equal(plan.roots[0].props.position, 'relative');
});

test('planBidiBake: <br> inside a root is hidden even when it HAS a box', () => {
  // Chromium gives <br> a zero-width client rect; with every line now
  // absolutely positioned, a surviving line-break component would inject
  // flow the baked layout no longer expects.
  const walk = { bodyTextIsBidi: false, elements: [
    el([0], { bidiAffected: true, texts: oneRun('ab') }),
    el([0, 0], { tag: 'br', rect: { x: 0, y: 0, width: 0, height: 20 }, rectCount: 1 }),
  ] };
  const { plan } = planBidiBake(walk);
  assert.deepEqual(plan.hides, [[0, 0]]);
  assert.equal(plan.boxes.length, 0);
});

test('planBidiBake: every bail leaves nothing planned', () => {
  const base = (over) => ({ bodyTextIsBidi: false, elements: [
    el([0], { bidiAffected: true, texts: oneRun('ab'), ...over.root }),
    ...(over.child ? [el([0, 0], over.child)] : []),
  ] });
  // Body-level bidi text has no component to anchor absolute runs to.
  assert.match(planBidiBake({ bodyTextIsBidi: true, elements: [] }).bail, /directly under <body>/);
  // A propagated decoration would silently vanish off an abspos run.
  assert.match(planBidiBake(base({ root: { decoration: 'underline' } })).bail,
    /text-decoration-line: underline/);
  // A root must be ONE box — it carries the used size.
  assert.match(planBidiBake(base({ root: { rectCount: 2 } })).bail, /bake root .* 2 client rects/);
  // A fragmented inline has no single box to bake.
  assert.match(planBidiBake(base({ child: { tag: 'span', display: 'inline', rectCount: 3 } })).bail,
    /fragmented inline <span>/);
});

test('planBidiBake: a direction-ambiguous run bails the whole test', () => {
  // Only `unicode-bidi: bidi-override` can produce one (Chromium lays strong
  // R glyphs out left-to-right there) — and the resulting string would be
  // re-resolved differently by each text stack.
  const walk = { bodyTextIsBidi: false, elements: [
    el([0], { bidiAffected: true, texts: oneRun('aس') }),
  ] };
  assert.match(planBidiBake(walk).bail, /direction-ambiguous run/);
});

test('planBidiBake: the run budget is enforced', () => {
  // 301 single-character text nodes, each its own run, on separate lines.
  const texts = [];
  for (let i = 0; i < MAX_BIDI_RUNS + 1; i++) texts.push(...oneRun('a', 0, i * 20));
  const walk = { bodyTextIsBidi: false, elements: [el([0], { bidiAffected: true, texts })] };
  assert.match(planBidiBake(walk).bail, /run budget exceeded \(301 > 300\)/);
});

// ── 6. The merge ────────────────────────────────────────────────────────────

test('componentIdForPath: ids are `<stem>__N…`, the buildComponents shape', () => {
  assert.equal(componentIdForPath('bidi__bidi-tab-001', [1, 0]), 'bidi__bidi-tab-001__1__0');
  assert.equal(componentIdForPath('s', [0]), 's__0');
});

test('appendChildComponent: continues past existing children (ids + paint order)', () => {
  const parent = { children: { 'p__0': { id: 'p__0' } } };
  const id = appendChildComponent(parent, 'p', { color: 'red' }, 'hi');
  // Static siblings keep their ids; the run lands AFTER them, so it paints
  // on top — what a text-over-background stack needs.
  assert.equal(id, 'p__1');
  assert.deepEqual(Object.keys(parent.children), ['p__0', 'p__1']);
  assert.deepEqual(parent.children['p__1'], { id: 'p__1', properties: { color: 'red' }, _text: 'hi' });
  // A childless parent grows a children map on demand.
  const leaf = {};
  assert.equal(appendChildComponent(leaf, 'q', {}, 'x'), 'q__0');
});

test('applyBidiBakePlan: edits, stamps and lossy roll-up', () => {
  const fixture = {
    _wpt: { test: 't', lossy: false, lossyReasons: ['em/rem'] },
    components: {
      s__0: { properties: { width: '10em' }, _text: 'Hello سلام', children: {
        s__0__0: { id: 's__0__0', properties: { color: 'blue' }, _text: '!' },
        s__0__1: { id: 's__0__1', properties: {}, _role: 'line-break' },
      } },
    },
  };
  const touched = applyBidiBakePlan(fixture, 's', {
    roots: [{ path: [0], props: { width: '346.19px', position: 'relative' } }],
    boxes: [{ path: [0, 0], props: { position: 'absolute', left: '10px' } }],
    hides: [[0, 1]],
    runs: [{ ownerPath: [0], props: { position: 'absolute', left: '28.3px' }, text: 'Hello' }],
  });
  assert.equal(touched, 4);
  const root = fixture.components.s__0;
  // The baked px REPLACES the lossy `10em` — same key, later assignment.
  assert.equal(root.properties.width, '346.19px');
  assert.equal(root.properties.position, 'relative');
  // Text dissolved into runs; the LOUD marker stays on the root.
  assert.equal('_text' in root, false);
  assert.equal(root._lossy, true);
  assert.deepEqual(root._lossyReasons, [BIDI_BAKE_LOSSY_REASON]);
  // Box baked, its own text moved out.
  assert.equal(root.children.s__0__0.properties.position, 'absolute');
  assert.equal('_text' in root.children.s__0__0, false);
  // The <br> is removed from flow explicitly.
  assert.equal(root.children.s__0__1.properties.display, 'none');
  // The run is appended after the static children.
  assert.deepEqual(root.children.s__0__2, {
    id: 's__0__2', properties: { position: 'absolute', left: '28.3px' }, _text: 'Hello',
  });
  // Honesty stamps: the delivery record and the fixture-level roll-up.
  assert.equal(fixture._wpt.bidiBaked, true);
  assert.equal(fixture._wpt.lossy, true);
  assert.deepEqual(fixture._wpt.lossyReasons, ['em/rem', BIDI_BAKE_LOSSY_REASON]);
});

test('applyBidiBakePlan: a ref-shaped _wpt block (no lossy field) keeps its shape', () => {
  // Ref fixtures carry `{ref, of, specSection}` only; inlineFixtureAssets
  // leaves them alone and so must the bake — never invent a lossy field.
  const fixture = { _wpt: { ref: 'r', of: 't' }, components: { s__0: { properties: {} } } };
  applyBidiBakePlan(fixture, 's', { roots: [{ path: [0], props: {} }], boxes: [], hides: [], runs: [] });
  assert.equal(fixture._wpt.bidiBaked, true);
  assert.equal('lossy' in fixture._wpt, false);
  assert.equal('lossyReasons' in fixture._wpt, false);
});

test('fixtureHasPseudo: ::before/::after geometry bails the bake', () => {
  // A pseudo box is anchored to a box the bake dissolves into absolute
  // children; no run can carry it, so its presence must stop the bake before
  // anything is edited.
  assert.equal(fixtureHasPseudo({ components: { a: { properties: {}, _text: 'x' } } }), false);
  assert.equal(fixtureHasPseudo({ components: { a: { _pseudo: { before: {} } } } }), true);
  // Nested under a child component counts too — the scan is whole-fixture.
  assert.equal(fixtureHasPseudo({ components: { a: { children: { a__0: { _pseudo: {} } } } } }), true);
  assert.equal(fixtureHasPseudo({}), false);
  assert.equal(fixtureHasPseudo(undefined), false);
});

test('applyBidiBakePlan: a missing component is a hard error, never a half-bake', () => {
  const fixture = { _wpt: {}, components: {} };
  assert.throws(
    () => applyBidiBakePlan(fixture, 's', { roots: [{ path: [7], props: {} }], boxes: [], hides: [], runs: [] }),
    /no component at 7/);
});

// ── 7. The proving set (skip-guarded — fixtures/wpt is generated) ───────────

const readFixture = (name) => {
  const p = join(FIX, `${name}.json`);
  return existsSync(p) ? JSON.parse(readFileSync(p, 'utf8')) : null;
};
const lines1 = readFixture('bidi__bidi-lines-001');
const lines2 = readFixture('bidi__bidi-lines-002');
const tab1   = readFixture('bidi__bidi-tab-001');
const baked  = (f) => f && f._wpt.bidiBaked === true;

test('proving: bidi-lines-001 — plaintext lines land at Chromium\'s own x', { skip: !baked(lines1) }, () => {
  const div = lines1.components['bidi__bidi-lines-001__1'];
  // The lossy `width: 10em` became the used px; the box gained a size and a
  // containing block; the merged logical-order text is gone.
  assert.equal(div.properties.width, '346.19px');
  assert.equal(div.properties.height, '246px');
  assert.equal(div.properties.position, 'relative');
  assert.equal('_text' in div, false);
  assert.deepEqual(div._lossyReasons.includes(BIDI_BAKE_LOSSY_REASON), true);
  const runs = Object.values(div.children).map((c) => ({
    t: c._text, left: c.properties.left, top: c.properties.top,
  }));
  // Six lines, alternating: the French ones flush LEFT of the padding box
  // (10.09px = the 0.5ch padding), the Persian ones flush RIGHT — the exact
  // assertion of the test, delivered as coordinates.
  assert.equal(runs.length, 6);
  assert.deepEqual(runs.map((r) => r.t),
    ['français', 'فارسی', 'français', 'فارسی', 'français', 'فارسی']);
  assert.deepEqual(runs.map((r) => r.left),
    ['10.09px', '254.09px', '10.09px', '254.09px', '10.09px', '254.09px']);
  assert.deepEqual(runs.map((r) => r.top),
    ['0px', '40px', '80px', '120px', '160px', '200px']);
});

test('proving: bidi-lines-001 — the intro <p> splits at the inline bidi boundary', { skip: !baked(lines1) }, () => {
  const p = lines1.components['bidi__bidi-lines-001__0'];
  const runs = Object.values(p.children);
  // Line 2 is three runs: LTR prefix, the isolated Persian word, LTR suffix.
  // The static path could only offer ONE reordered string ('inline-run-*').
  const line2 = runs.filter((c) => c.properties.top === '20px');
  assert.equal(line2.length, 3);
  assert.equal(line2[1]._text, 'فارسی');
  assert.equal(line2[1].properties.left, '279.09px');
  // The closing curly quote sits to the RIGHT of the isolate, as UAX#9 puts
  // it — the run boundary is geometric, not a guess.
  assert.equal(line2[2]._text, '” are');
});

test('proving: bidi-lines-002 — the intro <p> is untouched, the RTL div is baked', { skip: !baked(lines2) }, () => {
  // Scope proof: a pure-ASCII paragraph in a triggered document keeps the
  // static shape (no position, no children, its text intact).
  const p = lines2.components['bidi__bidi-lines-002__0'];
  assert.equal(p.properties.position, undefined);
  assert.equal('children' in p, false);
  assert.match(p._text, /^This test passes/);
  // The RTL div is baked, its <br>s removed from flow, its spans positioned.
  const div = lines2.components['bidi__bidi-lines-002__1'];
  assert.equal(div.properties.position, 'relative');
  const kids = Object.values(div.children);
  assert.equal(kids.filter((c) => c._role === 'line-break')
    .every((c) => c.properties.display === 'none'), true);
  // The Persian run keeps LOGICAL order and lands on the right-hand side.
  const salam = kids.find((c) => c._text === 'سلام');
  assert.equal(salam.properties.left, '256.53px');
  // …and "Hello" on the left of the same box, with its leading space trimmed.
  const hello = kids.find((c) => c._text === 'Hello');
  assert.equal(hello.properties.left, '28.3px');
});

test('proving: bidi-tab-001 — the tab advance survives as geometry', { skip: !baked(tab1) }, () => {
  // Eight <div dir>s baked; the intro <p> (no dir, no RTL) untouched.
  const roots = Object.entries(tab1.components)
    .filter(([, c]) => (c._lossyReasons ?? []).includes(BIDI_BAKE_LOSSY_REASON));
  assert.equal(roots.length, 8);
  assert.equal('_lossyReasons' in tab1.components['bidi__bidi-tab-001__0'], false);
  // dir=ltr box: the yellow span starts at the div's left edge and the "0"
  // sits one full tab advance (96.33px) into it.
  const ltrSpan = tab1.components['bidi__bidi-tab-001__1'].children['bidi__bidi-tab-001__1__0'];
  assert.equal(ltrSpan.properties.left, '0px');
  assert.equal(ltrSpan.properties.width, '108.38px');
  const ltrZero = ltrSpan.children['bidi__bidi-tab-001__1__0__0'];
  assert.equal(ltrZero._text, '0');
  assert.equal(ltrZero.properties.left, '96.33px');
  // dir=rtl box: the SAME span width, right-aligned inside the div, and the
  // "0" now at the span's left edge — the tab advanced leftward. That flip
  // is the whole assertion of the test, and it came out of layout, not out
  // of a tab-stop reimplementation.
  const rtlSpan = tab1.components['bidi__bidi-tab-001__2'].children['bidi__bidi-tab-001__2__0'];
  assert.equal(rtlSpan.properties.left, '12.03px');
  assert.equal(rtlSpan.properties.width, '108.38px');
  assert.equal(rtlSpan.children['bidi__bidi-tab-001__2__0__0'].properties.left, '0px');
});

test('proving: every baked run is a single-direction string', { skip: !baked(lines1) }, () => {
  // The invariant the whole wire rests on — a run all three text stacks
  // order identically without whole-paragraph bidi.
  for (const fx of [lines1, lines2, tab1].filter(Boolean)) {
    const walkTree = (c) => {
      if (typeof c._text === 'string' && c.properties?.position === 'absolute') {
        assert.equal(runDirectionConflict(c._text), false, `mixed run ${JSON.stringify(c._text)}`);
      }
      for (const kid of Object.values(c.children ?? {})) walkTree(kid);
    };
    for (const c of Object.values(fx.components)) walkTree(c);
  }
});

// ── 8. CLI wiring ───────────────────────────────────────────────────────────

test('bidi-bake: extract-fixture.mjs wires the opt-in activation + browser close', () => {
  // Source-scan pin (the inject-wpt-block.test convention for wiring): the
  // bake must stay OPT-IN and dynamically imported, or every batch run would
  // pay the puppeteer import, and the shared browser must be closed in the
  // same `finally` the post-load one is.
  const src = readFileSync(join(__dirname, 'extract-fixture.mjs'), 'utf8');
  assert.match(src, /process\.argv\.includes\('--bidi-bake'\)\s*\n?\s*\|\| process\.env\.BIDI_BAKE === '1'/);
  assert.match(src, /await import\('\.\/bidi-bake\.mjs'\)/);
  assert.match(src, /if \(bidiBake\) await bidiBake\.closeBidiBakeBrowser\(\)/);
  // …and it must run AFTER the post-load augmentation, so it measures the
  // settled tree the fixture actually carries.
  assert.ok(src.indexOf('postLoadAugmentFixture') < src.indexOf('bidiBakeFixture'));
});

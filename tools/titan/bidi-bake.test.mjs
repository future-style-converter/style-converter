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
    width: '346.19px', height: '246px', 'box-sizing': 'border-box',
    // Wave 35: the consumed paragraph inputs are retired on the root — see
    // the next test for why they have to be.
    direction: 'ltr', 'unicode-bidi': 'normal', 'text-align': 'left',
    position: 'relative',
  });
  // An already-positioned root keeps its own scheme — relative would be a
  // no-op there, but overwriting `absolute` would move the box.
  assert.equal(rootProperties({ width: 10, height: 10 }, 'absolute').position, undefined);
  assert.equal(rootProperties({ width: 10, height: 10 }, 'relative').position, undefined);
});

test('rootProperties: retires the paragraph inputs the bake consumed', () => {
  // The reorder rides the descendants' PHYSICAL left/top from here on, so a
  // baked root must not still advertise the direction that produced it: a
  // runtime that re-applies it mirrors the whole subtree (measured on
  // wave-34 bidi-lines-002 — iOS 0.9358, Android 0.9311 against the browser
  // ref, both an exact mirror of Chromium's coordinates, while web — for
  // which `left` is physical — sat at 0.993).
  for (const scheme of ['static', 'relative', 'absolute']) {
    const p = rootProperties({ width: 100, height: 50 }, scheme);
    assert.equal(p.direction, 'ltr', `${scheme}: direction must be neutralised`);
    assert.equal(p['unicode-bidi'], 'normal', `${scheme}: unicode-bidi must be neutralised`);
    assert.equal(p['text-align'], 'left', `${scheme}: text-align must be physical`);
  }
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

test('bidi-bake: THIS module\'s CLI sweeps stale run lists too', () => {
  // Wave 35 — the two entry points into the same bake must emit the same
  // wire. extract-fixture.mjs's `--bidi-bake` path has called dropStaleRuns
  // since wave 34; this module's own CLI did not, so a fixture baked through
  // `node tools/titan/bidi-bake.mjs …` shipped `_runs` WITHOUT `_text` —
  // the self-contradictory pair whose only possible meaning is "a later
  // stage dissolved this inline flow". Measured on bidi-lines-002 baked via
  // this CLI: a phantom inline copy of the dissolved text painted over the
  // positioned runs, web-vs-ref 0.993 → 0.9737. Source-scan pin (same
  // convention as the wiring test above) because `main()` is a process
  // entry point, not a callable unit.
  const src = readFileSync(join(__dirname, 'bidi-bake.mjs'), 'utf8');
  assert.match(src, /dropStaleRuns,?\n?/);                      // imported
  assert.match(src, /dropStaleRuns\(result\.fixture\) \+ dropStaleRuns\(result\.refFixture\)/);
  // …and the sweep must run AFTER the bake (it exists to clean up what the
  // bake dissolved) and BEFORE the fixture is written.
  assert.ok(src.indexOf('await bidiBakeFixture(result.fixture, rel)')
            < src.indexOf('dropStaleRuns(result.fixture)'));
  assert.ok(src.indexOf('dropStaleRuns(result.fixture)')
            < src.indexOf('await writeFixturePair(result)'));
});

// ── 9. Wave 54 lane L1 — unit P: a bake root's SPENT padding is zeroed ──────
//
// Unit P is wave-53 hunk P (a8ffd1c6, fix-pass state b0edb788^) re-landed as
// its OWN revert unit (tools/titan/results/wave54-plan/PLAN.md §2 L1). Its pins
// read the VERBATIM frozen bake outputs (tools/titan/results/wave53-plan/
// bidi-baked-fixtures/, sha1 per PROVENANCE.txt, asserted below) — the fixture
// side of the wave53-final per-test IR (e.g. counter-suffix__0__4 PaddingRight
// {original 3em} there = `padding: "0 3em"` here). Walk records are Chromium's
// computed values for those roots (3em at 16px = 48px; the CSS initial
// background-clip / -origin / overflow). MUTATIONS (executed, red → byte-exact
// restore → green, sha256): tools/titan/results/wave54-rtl-marker-bake/_note.md.

import { createHash } from 'node:crypto';
import { paddingIsSpent } from './bidi-bake.mjs';

// The frozen bake outputs, read byte-exact (a re-frozen copy must fail loudly).
const FROZEN = join(__dirname, 'results', 'wave53-plan', 'bidi-baked-fixtures');
const frozen = (rel, sha1) => {
  const buf = readFileSync(join(FROZEN, rel));
  assert.equal(createHash('sha1').update(buf).digest('hex'), sha1, `${rel} is not the PROVENANCE payload`);
  return JSON.parse(buf.toString('utf8'));
};
// Depth-first component lookup by id (fixture children maps are id-keyed).
const findCmp = (node, id) => {
  for (const [k, c] of Object.entries(node?.children ?? node?.components ?? {})) {
    if (k === id) return c;
    const hit = findCmp(c, id);
    if (hit) return hit;
  }
  return null;
};
// The text style the walker reads off counter-suffix's `<li>` (verbatim run props).
const CS_STYLE = { color: 'rgb(0, 0, 0)',
  fontFamily: 'Inter, -apple-system, "system-ui", "Segoe UI", Roboto, Oxygen, Ubuntu, sans-serif',
  fontSize: '16px', fontStyle: 'normal', fontWeight: '400',
  letterSpacing: 'normal', wordSpacing: '0px', textTransform: 'none' };
// One LTR word laid out at [x, x+w) on line y (per-char advances sum to w).
const word = (text, x, y, adv) => {
  let at = x;
  return [{ style: CS_STYLE, chars: [...text].map((c, i) => {
    const r = { x: +at.toFixed(2), y, w: adv[i], h: 20 }; at += adv[i]; return { c, rects: [r] };
  }) }];
};
// Chromium's computed guard inputs for an unclipped, unscrolled root.
const UNGUARDED = { backgroundClip: 'border-box', backgroundOrigin: 'padding-box', overflow: ['visible', 'visible'] };
// counter-suffix's RTL list `k` (4 = decimal, 5 = hebrew): the root and its two
// items, at the wave53-final wire's geometry (root W160 H48; li Left 48 Top 0/24
// W64 H24; text runs Left 39.36 / 39.03, Top 2, W 24.64 / 24.97, H 20).
const rtlList = (k, y0) => [
  { ...el([0, k], { tag: 'ol', rect: { x: 0, y: y0, width: 160, height: 48 }, bidiAffected: true }),
    padding: [0, 48, 0, 48], ...UNGUARDED },
  el([0, k, 0], { tag: 'li', display: 'list-item', bidiAffected: true, rect: { x: 48, y: y0, width: 64, height: 24 },
    texts: word('foo', 87.36, y0 + 2, [5.4, 9.62, 9.62]) }),
  el([0, k, 1], { tag: 'li', display: 'list-item', bidiAffected: true, rect: { x: 48, y: y0 + 24, width: 64, height: 24 },
    texts: word('bar', 87.03, y0 + 26, [9.66, 8.72, 6.59]) }),
];
// The whole counter-suffix RTL half as a walk (marker facts optional — unit M′).
const counterSuffixWalk = (markers = undefined) => ({ bodyTextIsBidi: false, ...(markers ? { markers } : {}), elements: [
  el([0], { rect: { x: 0, y: 0, width: 160, height: 288 } }), ...rtlList(4, 192), ...rtlList(5, 240)] });
const CS_FIXTURE = ['css-counter-styles/counter-suffix.json', '6d4ae51bc39e0fd5d4f4e1285bb8056671279e2b'];

test('V3 unit P: verbatim counter-suffix__0__4 — `padding: 0` alone, in the shorthand\'s own key position', () => {
  // planBidiBake hands the root's walk record (resolved padding 0/48/0/48) to rootProperties.
  const { bail, plan } = planBidiBake(counterSuffixWalk());
  assert.equal(bail, null);
  for (const k of ['0.4', '0.5']) assert.equal(plan.roots.find((r) => r.path.join('.') === k).props.padding, '0', k);
  // Apply to the VERBATIM frozen fixture: the root's only change is padding "0 3em" → "0".
  const fx = frozen(...CS_FIXTURE);
  const before = structuredClone(findCmp(fx, 'counter-suffix__0__4').properties);
  assert.equal(before.padding, '0 3em');
  applyBidiBakePlan(fx, 'counter-suffix', { roots: plan.roots, boxes: [], hides: [], runs: [] });
  const after = findCmp(fx, 'counter-suffix__0__4').properties;
  assert.deepEqual(Object.keys(after).filter((k) => k.startsWith('padding')), ['padding']);
  // Same keys in the same ORDER, and every value but `padding` unchanged: the smallest wire change.
  assert.deepEqual(Object.keys(after), Object.keys(before));
  assert.deepEqual({ ...after, padding: before.padding }, before);
  assert.equal(after.padding, '0');
  // A root spelling its padding as longhands / logical sides loses every one of them.
  const longhand = { _wpt: {}, components: { s__0: { id: 's__0',
    properties: { 'padding-left': '48px', 'padding-inline-end': '48px', color: 'red' } } } };
  applyBidiBakePlan(longhand, 's', { roots: [{ path: [0], props: plan.roots[0].props }], boxes: [], hides: [], runs: [] });
  assert.deepEqual(Object.keys(longhand.components.s__0.properties).filter((k) => k.startsWith('padding')), ['padding']);
});

test('V3 unit P: a padding that paints (content-box clip / origin) is NOT spent', () => {
  const rec = rtlList(4, 192)[0];
  assert.equal(paddingIsSpent(rec), true);
  // css-backgrounds-3 §2.7-2.8: a layer clipped / positioned to the content box keeps the padding.
  for (const g of [{ backgroundClip: 'content-box' }, { backgroundOrigin: 'content-box' }]) {
    assert.equal(paddingIsSpent({ ...rec, ...g }), false, JSON.stringify(g));
    assert.equal(rootProperties(rec.rect, 'relative', { ...rec, ...g }).padding, undefined);
  }
  // No walk record (every pre-wave-54 caller) → never spent.
  assert.equal(paddingIsSpent(null), false);
  assert.equal(rootProperties(rec.rect, 'relative').padding, undefined);
});

test('V3 unit P: a clipping / scrolling root keeps its padding (the overflow guard)', () => {
  // css-overflow-3 §3: the padding box is a scroll container's clip edge.
  const rec = rtlList(4, 192)[0];
  for (const overflow of [['hidden', 'visible'], ['visible', 'auto'], ['clip', 'clip'], ['scroll', 'scroll']]) {
    assert.equal(paddingIsSpent({ ...rec, overflow }), false, overflow.join('/'));
    assert.equal(rootProperties(rec.rect, 'static', { ...rec, overflow }).padding, undefined, overflow.join('/'));
  }
  assert.equal(paddingIsSpent({ ...rec, overflow: ['visible', 'visible'] }), true);
});

test('V3 unit P: verbatim bidi-lines-002 root (`0 0.5ch`) with its 5 hidden <br>', () => {
  // The frozen root __1, verbatim; the walk: 5 spans + 5 <br> under the bordered
  // root (0.5ch of 2em Inter = 9.92px; `border: solid` = 3px medium).
  const fx = frozen('css-text/bidi__bidi-lines-002.json', '51daea969744ccf6b0bba7413c6d028f2f7de830');
  const root = findCmp(fx, 'bidi__bidi-lines-002__1');
  const before = structuredClone(root.properties);
  assert.equal(before.padding, '0 0.5ch');
  // Only the STATIC elements are walked (the frozen file also holds the two baked runs, `_tag`-less).
  const kids = Object.keys(root.children).filter((id) => root.children[id]._tag).map((id, i) => (root.children[id]._tag === 'br'
    ? el([1, i], { tag: 'br', rect: { x: 0, y: 0, width: 0, height: 39 } })
    : el([1, i], { tag: 'span', display: 'inline', rect: { x: 29, y: 111 + 20 * i, width: 9.2, height: 39 } })));
  const walk = { bodyTextIsBidi: false, elements: [
    { ...el([1], { rect: { x: 6, y: 108, width: 346.19, height: 206 }, borderLeft: 3, borderTop: 3,
      bidiAffected: true, texts: word('Hello', 37.3, 151, [15, 15, 15, 15, 17.12]) }),
      padding: [0, 9.92, 0, 9.92], ...UNGUARDED }, ...kids] };
  const { plan } = planBidiBake(walk);
  const brs = kids.filter((k) => k.tag === 'br').map((k) => k.path.join('.'));
  assert.deepEqual(brs, ['1.1', '1.3', '1.5', '1.7', '1.9']);
  assert.deepEqual(plan.hides.map((p) => p.join('.')), brs);
  applyBidiBakePlan(fx, 'bidi__bidi-lines-002', { roots: plan.roots, boxes: [], hides: plan.hides, runs: [] });
  assert.equal(root.properties.padding, '0');
  assert.deepEqual(Object.keys(root.properties), Object.keys(before));
  // The <br>s generate no box, so the zeroed padding cannot move them either.
  for (const p of brs) assert.equal(root.children[`bidi__bidi-lines-002__${p.replace('.', '__')}`].properties.display, 'none');
});

test('V3b unit P: VERBATIM zero-padding roots (dir-style-02a ×6, dir-selector-change-003/-004) stay deep-equal AND in order', () => {
  // These three documents must stay byte-identical in wire and capture
  // (expectations.json carrierRule.zeroPaddingBakeRootsMustStayByteIdentical).
  const docs = [
    ['selectors/dir-style-02a.json', '2e32e283540f2b0490909c9fa5ac778213c87e2a', 'dir-style-02a', 6],
    ['selectors/dir-selector-change-003.json', '261660c4709394b1353044f5a1cbce60efc39f25', 'dir-selector-change-003', 1],
    ['selectors/dir-selector-change-004.json', '57b15cc01cc348ee28b2c68177989cc7928f407d', 'dir-selector-change-004', 1],
  ];
  for (const [rel, sha1, stem, n] of docs) {
    const fx = frozen(rel, sha1);
    // Every bake root of the document (stamped by the bake that froze it).
    const roots = Object.entries(fx.components).filter(([, c]) => (c._lossyReasons ?? []).includes(BIDI_BAKE_LOSSY_REASON));
    assert.equal(roots.length, n, rel);
    const before = structuredClone(roots.map(([, c]) => c.properties));
    // Chromium's record for each: zero padding on every side, unguarded.
    const plan = { roots: roots.map(([id, c]) => {
      const path = id.slice(stem.length + 2).split('__').map(Number);
      const rec = { ...el(path, { rect: { x: 0, y: 0, width: 358, height: parseFloat(c.properties.height) }, position: 'relative' }),
        padding: [0, 0, 0, 0], ...UNGUARDED };
      const props = rootProperties(rec.rect, rec.position, rec);
      assert.equal('padding' in props, false, `${rel} ${id}`);
      return { path, props };
    }), boxes: [], hides: [], runs: [] };
    applyBidiBakePlan(fx, stem, plan);
    // Same keys, same values, same ORDER — the per-test IR stays byte-identical.
    roots.forEach(([id, c], i) => {
      assert.deepEqual(Object.keys(c.properties), Object.keys(before[i]), `${rel} ${id} key order`);
      for (const k of Object.keys(before[i]).filter((x) => x.startsWith('padding'))) assert.equal(c.properties[k], before[i][k]);
    });
  }
});

test('unit P wiring: the in-page walker records the resolved padding and both guards\' inputs', () => {
  // Source-scan pin (the walker runs in Chromium only): without these four
  // fields paddingIsSpent sees no padding and unit P silently does nothing.
  const src = readFileSync(join(__dirname, 'bidi-bake.mjs'), 'utf8');
  const walker = src.slice(src.indexOf('function inPageBidiWalker('), src.indexOf('export function planBidiBake('));
  assert.match(walker, /padding: \[cs\.paddingTop, cs\.paddingRight, cs\.paddingBottom, cs\.paddingLeft\]/);
  assert.match(walker, /backgroundClip: cs\.backgroundClip,/);
  assert.match(walker, /backgroundOrigin: cs\.backgroundOrigin,/);
  assert.match(walker, /overflow: \[cs\.overflowX, cs\.overflowY\],/);
});

// ── 10. Wave 54 lane L1 — unit M′: RTL ::marker runs owned by the bake ROOT ──
//
// Wave-53 hunk M (bidi-marker-bake.mjs, restored from b0edb788^) with ONE
// design change (PLAN.md §2 L1): the marker runs are measured from and OWNED
// BY the enclosing relative bake root, never the abspos `<li>` — under the
// `<li>` the Compose Column loop of a host-inactive document gave run 2 +20 px
// and run 3 no slot (wave54-plan/rtl-marker-bake.md §3, wave53-probe). Wave
// 53's V1 V2 V4 V5 VF VF1-3 are ported to root ownership; V6 / V7 are new.
// Model advances in rtlProbe (' ' 4.48, '.' 4.42, tabular digits 9.9, 'א'
// 10.2, 'ב' 10.0); V6 uses the CDP-measured geometry instead. MUTATIONS
// (executed, red → byte-exact restore → green, sha256): the lane note.

import {
  planMarker, parseSnapshotMarkers, matchMarkers, collectMarkerFacts, MARKER_PROBE_EPS, MARKER_STAMPS,
} from './bidi-marker-bake.mjs';
import { bakeCounterStyles } from './counter-style-bake.mjs';

// The computed ::marker style: the li's font + the UA tabular-nums (css-lists-3 Appendix A).
const MARKER_STYLE = { ...CS_STYLE, fontVariantNumeric: 'tabular-nums' };
// The probe's answer for one RTL item: logical chars, rects relative to the span
// (RTL visual order: the suffix space leftmost, at the li's border edge).
const rtlProbe = (glyph, gw, { cdp = true, y = 192 } = {}) => {
  const w = +(4.48 + 4.42 + gw).toFixed(2);
  return { direction: 'rtl', position: 'outside', type: 'x', image: false, contentStart: 48, contentEnd: 112,
    style: MARKER_STYLE, text: `${glyph}. `, textModelled: false,
    cdpBox: cdp ? { x: 112, y, width: w, height: 24 } : null,
    glyphs: { width: w, chars: [
      { c: glyph, rects: [{ x: 8.9, y: 0, w: gw, h: 20 }] },
      { c: '.', rects: [{ x: 4.48, y: 0, w: 4.42, h: 20 }] },
      { c: ' ', rects: [{ x: 0, y: 0, w: 4.48, h: 20 }] },
    ] } };
};
const CS_MARKERS = (opts = {}) => ({ '0.4.0': rtlProbe('1', 9.9, opts), '0.4.1': rtlProbe('2', 9.9, { ...opts, y: 216 }),
  '0.5.0': rtlProbe('א', 10.2, { ...opts, y: 240 }), '0.5.1': rtlProbe('ב', 10.0, { ...opts, y: 264 }) });
// The runs a plan hands to one owner path ('0.4' = the decimal root, '0.4.0' its first item).
const runsOf = (plan, path) => plan.runs.filter((r) => r.ownerPath.join('.') === path);
const pxNum = (v) => Number.parseFloat(v);
const ITEMS = ['0.4.0', '0.4.1', '0.5.0', '0.5.1'];

test('V1 unit M′: RTL item markers bake as ROOT-owned runs on the inline-START (right) side, `none` on the item', () => {
  for (const cdp of [true, false]) {                      // the CDP box, then the analytic MODEL
    const { bail, plan } = planBidiBake(counterSuffixWalk(CS_MARKERS({ cdp })));
    assert.equal(bail, null);
    for (const p of ITEMS) {
      const box = plan.boxes.find((b) => b.path.join('.') === p);
      // Every item box stops Blink's own marker (and the counter-style stamp).
      assert.equal(box.props['list-style-type'], 'none', `${p} (cdp ${cdp})`);
      // The item keeps exactly its own text run — the marker is not its child.
      assert.deepEqual(runsOf(plan, p).map((r) => r.text), [p.endsWith('0') ? 'foo' : 'bar'], `${p} (cdp ${cdp})`);
      // The analytic path is stamped as a model; the measured one is not.
      assert.deepEqual(box.lossy ?? [], cdp ? [] : [MARKER_STAMPS.boxModel]);
    }
    // A no-strong-letter marker is split per grapheme, in visual order, per item row.
    const dec = runsOf(plan, '0.4'), heb = runsOf(plan, '0.5');
    assert.deepEqual(dec.map((r) => r.text), ['.', '1', '.', '2']);
    // A strong-R marker stays ONE run and states its own rtl direction.
    assert.deepEqual(heb.map((r) => r.text), ['א.', 'ב.']);
    assert.ok(heb.every((r) => r.props.direction === 'rtl'));
    // Every marker run sits past the items' right border edge (root x 48 + 64 = 112).
    assert.ok([...dec, ...heb].every((r) => pxNum(r.props.left) >= 112), `side (cdp ${cdp})`);
    // The ink lands where the ref's does: '.' at root x116.48, on its item's text row (top 2 / 26).
    assert.deepEqual([dec[0].props.left, dec[0].props['font-variant-numeric']], ['116.48px', 'tabular-nums']);
    assert.deepEqual([...dec, ...heb].map((r) => r.props.top), ['2px', '2px', '26px', '26px', '2px', '26px']);
    // 4 text runs + 6 marker runs — the predicted `10 runs` log line; unit P still holds with markers on.
    assert.equal(plan.runs.length, 10);
    assert.ok(plan.roots.every((r) => r.props.padding === '0'));
  }
});

test('V2 unit M′: a ROOT list item (arabic-indic-101 shape) gets no marker run', () => {
  const walk = { bodyTextIsBidi: false, markers: { '0.0': rtlProbe('١', 9.9) }, elements: [
    el([0], { tag: 'ol', rect: { x: 0, y: 0, width: 358, height: 48 } }),
    { ...el([0, 0], { tag: 'li', display: 'list-item', bidiAffected: true, rect: { x: 48, y: 192, width: 64, height: 24 },
      texts: word('foo', 87.36, 194, [5.4, 9.62, 9.62]) }), padding: [0, 0, 0, 0] },
  ] };
  const { plan } = planBidiBake(walk);
  assert.deepEqual(plan.roots.map((r) => r.path), [[0, 0]]);
  // Every runtime paints a root's marker today: no run, no `none`.
  assert.deepEqual(plan.runs.map((r) => r.text), ['foo']);
  assert.equal(plan.roots[0].props['list-style-type'], undefined);
});

// The geometry Chromium gave the wave-53 bake for counter-suffix's RTL markers
// (tools/titan/results/wave53-lists-bakes/marker-probe.out.txt: DOMSnapshot
// boxes x112 w18.96875 / 18.921875 / 17.609375; planned runs '.' li-left 68.3
// w4.3, '1' 72.59 w10.38, 'א.' 68.3 w14.62, 'ב.' 68.3 w13.31 — the
// wave53-probe per-test IR carries the same numbers), as per-glyph span
// offsets that reproduce those runs. Glyph tops are 0: planMarker subtracts
// the span's own first top (VF3), so they cannot move a run.
const cdpFacts = (type, text, boxW, y, glyphs) => ({ direction: 'rtl', position: 'outside', type, image: false,
  contentStart: 48, contentEnd: 112, style: MARKER_STYLE, text, textModelled: false,
  cdpBox: { x: 112, y, width: boxW, height: 24 },
  glyphs: { width: +boxW.toFixed(2), chars: glyphs.map(([c, x, w]) => ({ c, rects: [{ x, y: 0, w, h: 20 }] })) } });
const CS_MARKERS_CDP = {
  '0.4.0': cdpFacts('decimal', '1. ', 18.96875, 192, [['1', 8.59, 10.38], ['.', 4.3, 4.3], [' ', 0, 4.3]]),
  '0.4.1': cdpFacts('decimal', '2. ', 18.96875, 216, [['2', 8.59, 10.38], ['.', 4.3, 4.3], [' ', 0, 4.3]]),
  '0.5.0': cdpFacts('hebrew', 'א. ', 18.921875, 240, [['א', 8.6, 10.32], ['.', 4.3, 4.3], [' ', 0, 4.3]]),
  '0.5.1': cdpFacts('hebrew', 'ב. ', 17.609375, 264, [['ב', 8.6, 9.01], ['.', 4.3, 4.3], [' ', 0, 4.3]]),
};

test('V6 unit M′: every marker run is owned by its ENCLOSING ROOT at the measured root-relative geometry', () => {
  const { bail, plan } = planBidiBake(counterSuffixWalk(CS_MARKERS_CDP));
  assert.equal(bail, null);
  const marks = plan.runs.filter((r) => !['foo', 'bar'].includes(r.text));
  // [owner root, text, left, width, top] — wave54-plan/rtl-marker-bake.md §4's predicted wire.
  const want = [['0.4', '.', 116.3, 4.3, 2], ['0.4', '1', 120.59, 10.38, 2], ['0.4', '.', 116.3, 4.3, 26],
    ['0.4', '2', 120.59, 10.38, 26], ['0.5', 'א.', 116.3, 14.62, 2], ['0.5', 'ב.', 116.3, 13.31, 26]];
  assert.equal(marks.length, want.length);
  marks.forEach((r, i) => {
    const [owner, text, left, width, top] = want[i];
    assert.equal(r.ownerPath.join('.'), owner, `${text} owner`);
    assert.equal(r.text, text);
    assert.ok(Math.abs(pxNum(r.props.left) - left) <= 0.05, `${text} left ${r.props.left} vs ${left}`);
    assert.ok(Math.abs(pxNum(r.props.width) - width) <= 0.05, `${text} width ${r.props.width} vs ${width}`);
    assert.equal(pxNum(r.props.top), top, `${text} top`);
  });
  // CDP measured both box and string: no model stamp on any item.
  for (const p of ITEMS) assert.equal(plan.boxes.find((b) => b.path.join('.') === p).lossy, undefined, p);
  // The wire, on the VERBATIM frozen fixture (the wave-53-open bake output: text
  // runs already in place, so only the marker runs and the item props are new):
  // each root gains its marker runs AFTER its two items (+6 components, 23 → 29
  // on the wire) and every item keeps exactly ONE child, its text run.
  const fx = frozen(...CS_FIXTURE);
  const count = (n) => Object.values(n.children ?? n.components ?? {}).reduce((a, c) => a + 1 + count(c), 0);
  const n0 = count(fx);
  applyBidiBakePlan(fx, 'counter-suffix', { roots: plan.roots, boxes: plan.boxes, hides: [], runs: marks });
  assert.equal(count(fx), n0 + 6);
  assert.deepEqual(Object.keys(findCmp(fx, 'counter-suffix__0__4').children),
    ['counter-suffix__0__4__0', 'counter-suffix__0__4__1', 'counter-suffix__0__4__2', 'counter-suffix__0__4__3',
      'counter-suffix__0__4__4', 'counter-suffix__0__4__5']);
  assert.deepEqual(Object.keys(findCmp(fx, 'counter-suffix__0__5').children),
    ['counter-suffix__0__5__0', 'counter-suffix__0__5__1', 'counter-suffix__0__5__2', 'counter-suffix__0__5__3']);
  for (const id of ['counter-suffix__0__4__0', 'counter-suffix__0__4__1', 'counter-suffix__0__5__0', 'counter-suffix__0__5__1']) {
    assert.deepEqual(Object.keys(findCmp(fx, id).children), [`${id}__0`], `${id} keeps one child`);
    assert.equal(findCmp(fx, id).properties['list-style-type'], 'none');
  }
});

test('V7 unit M′: no item box owns more runs than it has text runs (the Compose stack-shape guard)', () => {
  // The marker-free plan counts each box's own text runs; with markers on (CDP,
  // model, analytic) no box may own more — an abspos box with ≥ 2 abspos runs in
  // a host-inactive document is the shape Compose mis-stacks (brief §3, §5).
  const bare = planBidiBake(counterSuffixWalk()).plan;
  const own = new Map(bare.boxes.map((b) => [b.path.join('.'), runsOf(bare, b.path.join('.')).length]));
  for (const markers of [CS_MARKERS_CDP, CS_MARKERS(), CS_MARKERS({ cdp: false })]) {
    const { plan } = planBidiBake(counterSuffixWalk(markers));
    assert.equal(plan.boxes.length, own.size);
    for (const b of plan.boxes) {
      const k = b.path.join('.');
      assert.ok(runsOf(plan, k).length <= own.get(k), `${k} owns ${runsOf(plan, k).length} runs > ${own.get(k)} text runs`);
    }
  }
});

// V4 needs the real corpus source (tools/wpt is gitignored) — skip-guarded.
const CS_TEST = 'css/css-counter-styles/counter-suffix.html';
const csReady = existsSync(join(REPO_ROOT, 'tools', 'wpt', CS_TEST)) && existsSync(join(__dirname, 'wpt-buckets.json'));
test('V4 unit M′ × counter-style bake: stamped 6, declined 2 (10 / 2 without the marker bake); root runs never stamped', { skip: !csReady }, async () => {
  const { extractFixture } = await import('./extract-fixture.mjs');
  const html = readFileSync(join(REPO_ROOT, 'tools', 'wpt', CS_TEST), 'utf8');
  const run = async (markers) => {
    const { fixture } = await extractFixture(CS_TEST);
    applyBidiBakePlan(fixture, 'counter-suffix', planBidiBake(counterSuffixWalk(markers)).plan);
    return { fixture, r: bakeCounterStyles(fixture, html) };
  };
  const off = await run(undefined);
  assert.deepEqual([off.r.stamped, off.r.declined], [10, 2]);
  const on = await run(CS_MARKERS_CDP);
  assert.deepEqual([on.r.stamped, on.r.declined], [6, 2]);
  for (const k of [4, 5]) {
    const root = findCmp(on.fixture, `counter-suffix__0__${k}`);
    // No meta.markerText source on the four RTL items any more, and none on the
    // root-owned runs (the counter-style bake stamps `_tag: li` children only).
    for (const c of Object.values(root.children)) assert.equal(c._markerText, undefined, c.id);
    // Each item keeps ONE child; the root's runs follow its two items.
    for (const i of [0, 1]) assert.equal(Object.keys(root.children[`counter-suffix__0__${k}__${i}`].children).length, 1);
    assert.equal(Object.keys(root.children).length, k === 4 ? 6 : 4);
  }
});

test('V5 unit M′: a > EPS probe mismatch is MARKER-scoped — analytic edge + stamp, never a whole-test bail', () => {
  const good = planBidiBake(counterSuffixWalk(CS_MARKERS())).plan;
  const m = CS_MARKERS();
  // Item 0.4.0's CDP box disagrees with the probe advance by 3 px (and sits elsewhere).
  m['0.4.0'] = { ...m['0.4.0'], cdpBox: { x: 100, y: 192, width: m['0.4.0'].glyphs.width + 3, height: 24 } };
  assert.ok(3 > MARKER_PROBE_EPS);
  const { bail, plan } = planBidiBake(counterSuffixWalk(m));
  assert.equal(bail, null);
  assert.ok(plan, 'a plan, not { bail }');
  // That item: the analytic inline-start edge (x112) — not the mismatching x100 — and the stamp.
  assert.deepEqual(runsOf(plan, '0.4'), runsOf(good, '0.4'));
  assert.deepEqual(plan.boxes.find((b) => b.path.join('.') === '0.4.0').lossy, [MARKER_STAMPS.mismatch]);
  // Everything else — text runs, the other root's markers, the roots — is deep-equal.
  for (const p of [...ITEMS, '0.5']) assert.deepEqual(runsOf(plan, p), runsOf(good, p));
  assert.deepEqual(plan.roots, good.roots);
});

test('planMarker: declines leave the item as it was, stamped; no ::marker → null', () => {
  // `origin` is the ROOT's padding-box origin (wave 54); `first` the item's first run.
  const first = { run: { y: 194 }, style: CS_STYLE }, origin = { x: 0, y: 192 };
  assert.equal(planMarker(undefined, origin, first), null);
  assert.equal(planMarker({ ...rtlProbe('1', 9.9), type: 'none' }, origin, first), null);
  const declined = { lossy: [MARKER_STAMPS.notBaked] };
  assert.deepEqual(planMarker({ ...rtlProbe('1', 9.9), image: true }, origin, first), declined);
  assert.deepEqual(planMarker(rtlProbe('1', 9.9), origin, null), declined);
  assert.deepEqual(planMarker({ ...rtlProbe('1', 9.9), text: null }, origin, first), declined);
  // A marker font unlike the first line's: its glyph tops would be a guess.
  assert.deepEqual(planMarker({ ...rtlProbe('1', 9.9), style: { ...MARKER_STYLE, fontSize: '20px' } }, origin, first), declined);
  // A modelled string is stamped as such.
  assert.deepEqual(planMarker({ ...rtlProbe('1', 9.9), textModelled: true }, origin, first).lossy, [MARKER_STAMPS.textModel]);
});

test('parseSnapshotMarkers / matchMarkers: the CDP half, by tag + used box', () => {
  // A minimal DOMSnapshot: node 0 <li>, node 1 its ::marker (box + LayoutText "1. ").
  const snap = { strings: ['LI', '::marker', 'marker', '1. '], documents: [{
    nodes: { parentIndex: [-1, 0], nodeName: [0, 1], pseudoType: { index: [1], value: [2] } },
    layout: { nodeIndex: [0, 1, 1], bounds: [[48, 192, 64, 24], [112, 192, 18.8, 24], [112, 194, 18.8, 20]], text: [-1, -1, 3] },
  }] };
  const ms = parseSnapshotMarkers(snap);
  assert.deepEqual(ms, [{ hostTag: 'li', hostRect: { x: 48, y: 192, width: 64, height: 24 },
    box: { x: 112, y: 192, width: 18.8, height: 24 }, text: '1. ' }]);
  assert.deepEqual(parseSnapshotMarkers({}), []);
  // Match by tag + rect; an ambiguous match is no fact at all.
  const cand = { key: '0.4.0', tag: 'li', rect: { x: 48, y: 192, width: 64, height: 24 } };
  assert.equal(matchMarkers([cand], ms)['0.4.0'].text, '1. ');
  assert.deepEqual(matchMarkers([cand], [...ms, ...ms]), {});
  assert.deepEqual(matchMarkers([{ ...cand, tag: 'div' }], ms), {});
});

test('VF unit M′: collectMarkerFacts — list-free → {} with no CDP call; a page fault declines items, never throws', async () => {
  // The bake's call site has no try/catch of its own (extract-fixture.mjs), so a
  // throw here would cost the WHOLE fixture — the 13 marker-free bidi docs too.
  let cdpCalls = 0;
  const page = { createCDPSession: async () => { cdpCalls++; throw new Error('no CDP'); },
    evaluate: async () => { throw new Error('page gone'); } };
  assert.deepEqual(await collectMarkerFacts(page, [el([0], { tag: 'div' })]), {});
  assert.equal(cdpCalls, 0);
  const facts = await collectMarkerFacts(page, [el([0, 0], { tag: 'li', display: 'list-item' })]);
  assert.equal(cdpCalls, 1);
  assert.match(facts['0.0'].error, /page gone/);
  // …and that fact makes planMarker decline the item, loudly.
  assert.deepEqual(planMarker(facts['0.0'], { x: 0, y: 0 }, { run: { y: 0 }, style: CS_STYLE }),
    { lossy: [MARKER_STAMPS.notBaked] });
});

test('unit M′ wiring: the marker facts are read AFTER the walk and the mapping check', () => {
  // Source-scan pin (this file's wiring convention): the probe span must never
  // perturb the page the walk measured, and a mapping bail must cost no CDP call.
  const src = readFileSync(join(__dirname, 'bidi-bake.mjs'), 'utf8');
  const at = (s) => src.indexOf(s);
  assert.ok(at('page.evaluate(inPageBidiWalker') < at('await collectMarkerFacts(page'));
  assert.ok(at("reason: `element-mapping-mismatch") < at('await collectMarkerFacts(page'));
  assert.ok(at('await collectMarkerFacts(page') < at('const { bail, plan, note } = planBidiBake(walk);'));
});

test('VF1 unit M′: an item the in-page probe cannot re-find by its rect is DECLINED and stamped, never skipped', async () => {
  // The REAL inPageMarkerProbe (the fake page's evaluate calls it) against a
  // document whose only <li> sits nowhere near the walked rects.
  const saved = Object.getOwnPropertyDescriptor(globalThis, 'document');
  globalThis.document = { getElementsByTagName: () => [{ getBoundingClientRect: () => ({ left: 0, top: 0, width: 1, height: 1 }) }] };
  try {
    const page = { createCDPSession: async () => ({ send: async () => ({ strings: [], documents: [] }), detach: async () => {} }),
      evaluate: async (fn, arg) => fn(arg) };
    const walk = counterSuffixWalk();
    const facts = await collectMarkerFacts(page, walk.elements);
    // One error fact per list item — no CDP / model fields merged into it.
    assert.deepEqual(Object.keys(facts).sort(), ITEMS);
    for (const f of Object.values(facts)) assert.deepEqual(f, { error: 'list item not re-found by rect' });
    // planBidiBake: each item keeps its marker (no `none`), stamped; only the 4 text runs.
    const { bail, plan } = planBidiBake({ ...walk, markers: facts });
    assert.equal(bail, null);
    for (const p of ITEMS) {
      const box = plan.boxes.find((b) => b.path.join('.') === p);
      assert.equal(box.props['list-style-type'], undefined, p);
      assert.deepEqual(box.lossy, [MARKER_STAMPS.notBaked], p);
    }
    assert.deepEqual(plan.runs.map((r) => r.text), ['foo', 'bar', 'foo', 'bar']);
  } finally {
    // Restore the global exactly (node has no `document`).
    if (saved) Object.defineProperty(globalThis, 'document', saved); else delete globalThis.document;
  }
});

test('VF2 unit M′: a box\'s marker stamps reach the FIXTURE component (_lossy + _lossyReasons, merged once)', () => {
  // The honesty contract rests on this merge: a declined / modelled marker is
  // read back from the fixture (and the gate's extract log), not from the plan.
  const fixture = { _wpt: { test: 't', lossy: false, lossyReasons: [] }, components: { s__0: { properties: {}, children: {
    s__0__0: { id: 's__0__0', properties: {}, _lossyReasons: ['percentage'] },
    s__0__1: { id: 's__0__1', properties: {} } } } } };
  applyBidiBakePlan(fixture, 's', { roots: [{ path: [0], props: {} }], hides: [], runs: [], boxes: [
    { path: [0, 0], props: { position: 'absolute' }, lossy: [MARKER_STAMPS.notBaked, MARKER_STAMPS.notBaked, 'percentage'] },
    { path: [0, 1], props: { position: 'absolute' } }] });
  const [stamped, plain] = ['s__0__0', 's__0__1'].map((k) => fixture.components.s__0.children[k]);
  assert.equal(stamped._lossy, true);
  assert.deepEqual(stamped._lossyReasons, ['percentage', MARKER_STAMPS.notBaked]);
  // A box with no marker stamp gains no lossy field at all.
  assert.equal('_lossy' in plain, false);
  assert.equal('_lossyReasons' in plain, false);
});

test('VF3 unit M′: marker glyph tops drop the probe span\'s half-leading (a tall body line-height)', () => {
  // The span inherits BODY's line-height, so each glyph's q.y carries that
  // line's half-leading; `first.run.y` is already a content-area top. q.y 14 =
  // a body `line-height: 48px` over a 20-px content area ((48 − 20) / 2).
  const first = { run: { y: 194 }, style: CS_STYLE };
  const shifted = (qy) => { const f = rtlProbe('1', 9.9);
    return { ...f, glyphs: { ...f.glyphs, chars: f.glyphs.chars.map((ch) => ({ ...ch, rects: ch.rects.map((q) => ({ ...q, y: q.y + qy })) })) } }; };
  const tops = (m) => m.runs.map((r) => r.props.top);
  // Same tops as the text run (root-top 192 + 2) with or without the leading.
  assert.deepEqual(tops(planMarker(shifted(0), { x: 0, y: 192 }, first)), ['2px', '2px']);
  assert.deepEqual(tops(planMarker(shifted(14), { x: 0, y: 192 }, first)), ['2px', '2px']);
});

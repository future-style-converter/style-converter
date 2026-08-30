#!/usr/bin/env node
//
// Unit tests for tools/titan/novel-ink.mjs (wave-49).
//
// Two kinds of pin here, and the distinction matters:
//
//  1. PREDICATE pins run on novelInk blocks copied VERBATIM out of the
//     wave48-final corpus sweep (tools/titan/runs/wave48-final/sections/*,
//     recomputed pixel-by-pixel by the lane's sweep). Those runs are
//     gitignored, so the measured block is transcribed here with its source
//     cell named — that is the payload the predicate really sees.
//  2. METRIC pins build PNG pairs in-process (pngjs, the same house pattern
//     inject-wpt-block.test.mjs uses) because the corpus captures are
//     gitignored too. Each fixture isolates ONE mechanism.
//
// The standing constraint "a new check must be proven able to fail before it
// is trusted" is discharged by the mutation test at the bottom: a correct
// capture is confirmed silent, then one region is repainted and the check
// trips.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { PNG } from 'pngjs';

import {
  computeNovelInk, computeNovelInkFailed,
  NOVEL_INK_TOLERANCE, NOVEL_INK_DELTA_E_MIN, NOVEL_INK_FRACTION_MIN,
  NOVEL_INK_ABS_MIN_PCT, NOVEL_INK_PALETTE_COVERAGE_MIN, NOVEL_INK_CANVAS_BG,
} from './novel-ink.mjs';

// ── Fixture helpers ────────────────────────────────────────────────────────

/** A white 200x200 canvas — the corpus-v4 WPT canvas colour, so the "did the
 *  capture paint here" test in computeNovelInk sees real blank page. */
function canvas(w = 200, h = 200) {
  const png = new PNG({ width: w, height: h });
  png.data.fill(0xFF);
  return png;
}

/** Paint an axis-aligned rect. Mirrors how every reftest in this corpus draws
 *  its pass/fail swatch (a solid block of one colour). */
function rect(png, x0, y0, w, h, [r, g, b]) {
  for (let y = y0; y < y0 + h; y++) {
    for (let x = x0; x < x0 + w; x++) {
      const i = (y * png.width + x) * 4;
      png.data[i] = r; png.data[i + 1] = g; png.data[i + 2] = b; png.data[i + 3] = 255;
    }
  }
  return png;
}

// The two colours the corpus's dominant reftest idiom uses. Named here ONLY to
// build fixtures — nothing in novel-ink.mjs knows either of them.
const GREEN = [0, 128, 0];
const RED = [255, 0, 0];
const BLUE = [0, 0, 255];
const BLACK = [0, 0, 0];

// ── Metric pins ────────────────────────────────────────────────────────────

test('novel ink: a total hue swap of a small patch scores fraction 1', () => {
  // The wave-49 headline shape, reduced: ref paints a green block, the capture
  // paints the SAME block in red. Whole-canvas histogram KL cannot see this
  // (the patch is 25 % of the canvas here and only 4.3 % in the real corpus
  // cell); the divergent-region normalization must read it as total.
  const ref = rect(canvas(), 50, 50, 100, 100, GREEN);
  const cap = rect(canvas(), 50, 50, 100, 100, RED);
  const ni = computeNovelInk(cap, ref);
  assert.equal(ni.divergentPx, 100 * 100);
  assert.equal(ni.divergentInkPx, 100 * 100);
  assert.equal(ni.novelFractionOfDivergentInk, 1);
  assert.equal(computeNovelInkFailed(ni), true);
});

test('novel ink: a DISPLACED same-colour block is not novel', () => {
  // The commonest honest divergence: right colour, wrong place. Every pixel of
  // the capture's paint inside the disagreement is a colour the reference
  // uses, so the numerator must be zero and the veto must stay silent — this
  // is what keeps the check from degenerating into "any layout difference".
  const ref = rect(canvas(), 50, 50, 100, 100, GREEN);
  const cap = rect(canvas(), 70, 50, 100, 100, GREEN);
  const ni = computeNovelInk(cap, ref);
  assert.ok(ni.divergentInkPx > 0, 'the fixture must actually disagree');
  assert.equal(ni.novelPx, 0);
  assert.equal(computeNovelInkFailed(ni), false);
});

test('novel ink: antialiasing greys between two reference colours are not novel', () => {
  // Palette closure under blending. The capture paints a mid-grey halo the
  // reference does not contain as its own colour, but grey is a convex blend
  // of the reference's black and white — exactly what subpixel coverage
  // produces. Without blend closure this would read as novel ink on every
  // text-heavy pair.
  const ref = rect(canvas(), 40, 40, 120, 120, BLACK);
  const cap = rect(canvas(), 40, 40, 120, 120, BLACK);
  rect(cap, 40, 40, 120, 8, [128, 128, 128]);   // a fat AA edge
  const ni = computeNovelInk(cap, ref);
  assert.ok(ni.divergentInkPx > 0, 'the fixture must actually disagree');
  assert.equal(ni.novelPx, 0, 'a black↔white blend is not a novel colour');
  assert.equal(computeNovelInkFailed(ni), false);
});

test('novel ink: pixels the capture left blank are excluded from the ratio', () => {
  // "Wrong colour AND wrong place". Ref: one green block. Capture: an equal
  // red block somewhere else and nothing where the ref painted. The blank half
  // of the disagreement belongs to the presence/coverage vetoes, so the ratio
  // must be measured on the capture's INK only — here that is all-red.
  const ref = rect(canvas(), 20, 20, 60, 60, GREEN);
  const cap = rect(canvas(), 120, 120, 60, 60, RED);
  const ni = computeNovelInk(cap, ref);
  assert.equal(ni.divergentPx, 2 * 60 * 60, 'both blocks disagree');
  assert.equal(ni.divergentInkPx, 60 * 60, 'only the capture-painted half counts');
  assert.equal(ni.novelFractionOfDivergentInk, 1);
  assert.equal(computeNovelInkFailed(ni), true);
});

test('novel ink: a sub-tolerance shift is not divergence at all', () => {
  // NOVEL_INK_TOLERANCE is the shared 8/255 slack. A uniform shift inside it
  // must produce zero divergent pixels, so no pair can be vetoed for
  // compositor rounding.
  const ref = rect(canvas(), 50, 50, 100, 100, GREEN);
  const cap = rect(canvas(), 50, 50, 100, 100, [GREEN[0] + NOVEL_INK_TOLERANCE, GREEN[1], GREEN[2]]);
  const ni = computeNovelInk(cap, ref);
  assert.equal(ni.divergentPx, 0);
  assert.equal(computeNovelInkFailed(ni), false);
});

test('novel ink: a colour-rich reference makes the pair unjudgeable', () => {
  // The validity precondition. A horizontal hue sweep occupies far more bins
  // than the palette retains, so "absent from the reference" is not a sound
  // claim. The block is still returned (diagnostics), but the predicate must
  // abstain — this is the mechanism that removed the one measured false
  // positive (css-images/gradient/gradient-decreasing-hue-hsl, android).
  const ref = canvas();
  for (let x = 0; x < 200; x++) {
    // Rough RGB sweep — the point is bin COUNT, not colorimetric accuracy.
    const c = [Math.round(255 * Math.abs(Math.sin(x / 30))),
      Math.round(255 * Math.abs(Math.sin(x / 30 + 2))),
      Math.round(255 * Math.abs(Math.sin(x / 30 + 4)))];
    rect(ref, x, 0, 1, 200, c);
  }
  const cap = PNG.sync.read(PNG.sync.write(ref));
  rect(cap, 90, 90, 20, 20, [10, 255, 10]);   // a small foreign patch
  const ni = computeNovelInk(cap, ref);
  assert.ok(ni.paletteCoveragePct < NOVEL_INK_PALETTE_COVERAGE_MIN,
    `expected a truncated palette, got coverage ${ni.paletteCoveragePct}`);
  assert.equal(computeNovelInkFailed(ni), false, 'must abstain, not veto');
});

test('novel ink: mismatched buffers are UNKNOWN, never FAILED', () => {
  // Same "unknown ≠ divergent" stance every other gate in this pipeline takes.
  assert.equal(computeNovelInk(canvas(200, 200), canvas(100, 100)), null);
  assert.equal(computeNovelInkFailed(null), false);
  assert.equal(computeNovelInkFailed(undefined), false);
  assert.equal(computeNovelInkFailed({}), false);
  // A block predating the validity precondition has no coverage field → abstain.
  assert.equal(computeNovelInkFailed(
    { novelFractionOfDivergentInk: 1, novelPct: 50 }), false);
});

test('novel ink: the canvas constant is the corpus-v4 white', () => {
  // The only colour the module names, and it names a CANVAS, not a hue. If the
  // corpus canvas ever moves off white this pin fails loudly rather than
  // letting "the capture painted here" quietly invert.
  assert.deepEqual({ ...NOVEL_INK_CANVAS_BG }, { r: 0xFF, g: 0xFF, b: 0xFF });
});

// ── Predicate pins on VERBATIM wave48-final blocks ─────────────────────────

test('predicate: the wave-49 headline cell vetoes', () => {
  // VERBATIM from the sweep of tools/titan/runs/wave48-final/sections/
  // css-view-transitions/manifest.json, test
  // css/css-view-transitions/hit-test-unrelated-element.html, web-ref.
  // Recorded there as ssim 1.0000 / wptPass TRUE while all three captures
  // paint the reference's green square pure red.
  const block = { divergentPx: 10000, divergentInkPx: 10000, novelPx: 10000,
    novelFractionOfDivergentInk: 1, novelFractionOfDivergent: 1, novelPct: 4.2735,
    refPaletteSize: 3, paletteCoveragePct: 1,
    novelClasses: [{ rgb: [255, 0, 0], px: 10000, deltaE: 45.81 }] };
  assert.equal(computeNovelInkFailed(block), true);
});

test('predicate: the pre-registered fraction bar abstains at 0.4482', () => {
  // VERBATIM from the same sweep: CSS2/abspos/
  // remove-block-between-inline-and-abspos.html, ios-ref. The capture paints a
  // red rectangle inside an over-large green blob — a genuine defect the
  // wave-49 bar does NOT catch, because the extra in-palette green outweighs
  // the red. Pinned so the known recall gap is visible in the suite rather
  // than discovered again, and so any future re-calibration of
  // NOVEL_INK_FRACTION_MIN has to update a test that says what it costs.
  const block = { divergentPx: 16305, divergentInkPx: 16063, novelPx: 7200,
    novelFractionOfDivergentInk: 0.4482, novelFractionOfDivergent: 0.4416,
    novelPct: 3.0769, refPaletteSize: 24, paletteCoveragePct: 0.9985,
    novelClasses: [{ rgb: [255, 0, 0], px: 7200, deltaE: 31.19 }] };
  assert.ok(block.novelFractionOfDivergentInk < NOVEL_INK_FRACTION_MIN);
  assert.equal(computeNovelInkFailed(block), false);
});

test('predicate: the measured false positive is abstained on by coverage', () => {
  // VERBATIM: css-images/gradient/gradient-decreasing-hue-hsl.html, android-ref.
  // Both PNGs were opened during calibration; the render is CORRECT, and the
  // 275 "novel" pixels are gradient interpolation noise against a rainbow
  // reference whose palette the top-24 cap truncates to 76 %.
  const block = { divergentPx: 275, divergentInkPx: 275, novelPx: 275,
    novelFractionOfDivergentInk: 1, novelFractionOfDivergent: 1, novelPct: 0.1175,
    refPaletteSize: 24, paletteCoveragePct: 0.7644,
    novelClasses: [{ rgb: [10, 255, 10], px: 188, deltaE: 32.11 },
      { rgb: [0, 245, 246], px: 87, deltaE: 25.79 }] };
  assert.equal(computeNovelInkFailed(block), false);
});

test('predicate: the novel colour need not be RED — a black-for-green swap vetoes', () => {
  // VERBATIM: selectors/invalidation/class-id-attr.html, web-ref (ssim 0.9987,
  // recorded wptPass TRUE). Drawn in the wave-49 SECOND-PASS random sample of
  // currently-passing cells (seed 4904, disjoint from the first 48) and
  // hand-verified by opening both PNGs: the reference renders "This should be
  // green / And this too" in GREEN, the web capture renders it BLACK.
  //
  // This pin is the COLOUR-AGNOSTICISM evidence. The corpus's dominant defect
  // is red-where-green-expected, so a check tuned to that hue would look
  // identical on the headline numbers. Here the novel class is (0,0,0) at
  // CIEDE2000 43.1 from anything the reference paints or blends to, and the
  // predicate fires on exactly the same arithmetic. Nothing in novel-ink.mjs
  // distinguishes this case from the red one.
  const block = { divergentPx: 1393, divergentInkPx: 1393, novelPx: 1031,
    novelFractionOfDivergentInk: 0.7401, novelFractionOfDivergent: 0.7401,
    novelPct: 0.4406, refPaletteSize: 24, paletteCoveragePct: 0.9993,
    novelClasses: [{ rgb: [0, 0, 0], px: 377, deltaE: 43.1 },
      { rgb: [37, 37, 37], px: 72, deltaE: 37.02 },
      { rgb: [76, 76, 76], px: 54, deltaE: 30.14 }] };
  assert.equal(computeNovelInkFailed(block), true);
});

test('predicate: an unbiased-sample true positive vetoes', () => {
  // VERBATIM: css-flexbox/abspos/abspos-autopos-vlr-rtl.html, android-ref
  // (ssim 0.9966, recorded wptPass TRUE, colorFailed FALSE — the histogram-KL
  // veto slept). Also from the second-pass random sample, hand-verified: the
  // reference is a plain green square, the capture frames it in red.
  //
  // Pinned because it is a true positive the check found WITHOUT the census
  // rule's help. The wave-49 recall figure of 12/18 was measured on cells
  // SELECTED for a large red area (the census rule wants capRedPct > 0.5),
  // which correlates with this check's own novelPct bar; this cell shows the
  // predicate also fires on cells drawn at random from the passing set.
  const block = { divergentPx: 6343, divergentInkPx: 6138, novelPx: 4400,
    novelFractionOfDivergentInk: 0.7168, novelFractionOfDivergent: 0.6937,
    novelPct: 1.8803, refPaletteSize: 24, paletteCoveragePct: 0.9985,
    novelClasses: [{ rgb: [255, 0, 0], px: 4400, deltaE: 31.19 }] };
  assert.equal(computeNovelInkFailed(block), true);
});

test('predicate: an ordinary correct render scores zero novel ink', () => {
  // VERBATIM: css-cascade/revert-layer-004.html, web-ref — hand-verified
  // correct (green square, matching reference) during the wave-49 sample.
  const block = { divergentPx: 295, divergentInkPx: 212, novelPx: 0,
    novelFractionOfDivergentInk: 0, novelFractionOfDivergent: 0, novelPct: 0,
    refPaletteSize: 24, paletteCoveragePct: 0.9985, novelClasses: [] };
  assert.equal(computeNovelInkFailed(block), false);
});

test('predicate: both bars are load-bearing', () => {
  // Mass without majority, and majority without mass, must each abstain —
  // otherwise one of the two constants is decoration.
  const base = { paletteCoveragePct: 1 };
  assert.equal(computeNovelInkFailed(
    { ...base, novelFractionOfDivergentInk: NOVEL_INK_FRACTION_MIN - 0.01, novelPct: 50 }), false);
  assert.equal(computeNovelInkFailed(
    { ...base, novelFractionOfDivergentInk: 1, novelPct: NOVEL_INK_ABS_MIN_PCT - 0.001 }), false);
  assert.equal(computeNovelInkFailed(
    { ...base, novelFractionOfDivergentInk: NOVEL_INK_FRACTION_MIN, novelPct: NOVEL_INK_ABS_MIN_PCT }), true);
});

// ── Mutation / negative control ────────────────────────────────────────────

test('MUTATION: a byte-perfect capture is silent, and one repainted region trips it', () => {
  // Step 1 — the negative control. A capture identical to the reference must
  // produce zero divergence and no veto. A check that cannot be silent is
  // worthless; a check that cannot fire is worse.
  const ref = rect(rect(canvas(390, 600), 216, 16, 100, 100, BLUE), 216, 316, 100, 100, GREEN);
  const clean = PNG.sync.read(PNG.sync.write(ref));
  const before = computeNovelInk(clean, ref);
  assert.equal(before.divergentPx, 0);
  assert.equal(computeNovelInkFailed(before), false, 'identical pair must not veto');

  // Step 2 — mutate. Repaint the green block in a colour the reference does
  // not contain (and that is not a blend of any two colours it does), leaving
  // geometry untouched. This is the real corpus defect in miniature.
  const mutated = PNG.sync.read(PNG.sync.write(ref));
  rect(mutated, 216, 316, 100, 100, RED);
  const after = computeNovelInk(mutated, ref);
  assert.equal(after.divergentPx, 100 * 100, 'only the repainted block differs');
  assert.equal(after.novelFractionOfDivergentInk, 1);
  assert.ok(after.novelClasses[0].deltaE >= NOVEL_INK_DELTA_E_MIN,
    'the repaint must clear the perceptual bar');
  assert.equal(computeNovelInkFailed(after), true, 'the mutation must trip the check');

  // Step 3 — the mutation must be invisible to the metric this check replaces.
  // 100x100 of 390x600 is 4.3 % of the canvas: exactly the coverage regime in
  // which a whole-canvas histogram cannot move. Asserted structurally here
  // (the KL computation itself is pinned in the metrics module's own suite):
  // the defect is 4.3 % of the frame, two orders below a majority.
  assert.ok(after.novelPct < 5 && after.novelPct > 4,
    `expected a ~4.3 % small-area defect, got ${after.novelPct}`);
});

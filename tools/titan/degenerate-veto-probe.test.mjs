#!/usr/bin/env node
//
// Pins for tools/titan/degenerate-veto-probe.mjs — the displacement-aware
// successor to the disarmed novel-ink veto (BACKLOG obligation #4).
//
// Four layers, in the order a sceptic should read them:
//   1. the PRE-REGISTERED constants, exactly — the bars are novel-ink's own,
//      reused verbatim so the successor is comparable and not tuned; a silent
//      change to any of them invalidates the wave50-B12 measurement.
//   2. the DISPLACEMENT MODEL itself, on synthetic images whose answer is known
//      by construction: a mark moved by less than the radius is explained, the
//      same mark moved by more than the radius is not, and a mark that changes
//      colour in place is never explained however small the move.
//   3. the MUTATION TEST the campaign requires of any new check: each bar is
//      moved by one step and the predicate's answer must flip. A check that
//      cannot be made to fail is not a check.
//   4. the ARMING gate: the module ships DISARMED, and the pure predicate is
//      computable either way.
//
// Synthetic images only — no run dir, no device, no network.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { PNG } from 'pngjs';
import {
  DEGENERATE_TOLERANCE, DEGENERATE_SHIFT_RADIUS, DEGENERATE_FRACTION_MIN,
  DEGENERATE_ABS_MIN_PCT, modalColour, computeDegenerate, degenerateVetoFailed,
  isArmed, isScored, captureName,
} from './degenerate-veto-probe.mjs';

// ── helpers: images whose content is known by construction ──────────────────

/** A W×H opaque image filled with `bg`, with an optional axis-aligned `fg`
 *  rectangle at (x,y,w,h). Colours are triples; no colour is named anywhere. */
function img(W, H, bg, rect = null) {
  const p = new PNG({ width: W, height: H });
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const i = (y * W + x) * 4;
    const inRect = rect && x >= rect.x && x < rect.x + rect.w && y >= rect.y && y < rect.y + rect.h;
    const c = inRect ? rect.c : bg;
    p.data[i] = c[0]; p.data[i + 1] = c[1]; p.data[i + 2] = c[2]; p.data[i + 3] = 255;
  }
  return p;
}
const BG = [255, 255, 255];   // the modal colour of every synthetic reference below
const INK_A = [10, 20, 200];  // one arbitrary paint colour
const INK_B = [200, 20, 10];  // a second, far from the first in every channel

// ── 1. pre-registered constants ─────────────────────────────────────────────

test('constants are the pre-registered values the wave50-B12 measurement used', () => {
  assert.equal(DEGENERATE_TOLERANCE, 8, 'the shared per-channel slack, imported not re-declared');
  assert.equal(DEGENERATE_SHIFT_RADIUS, 4, 'the rigid-translation slack, in pixels');
  assert.equal(DEGENERATE_FRACTION_MIN, 0.5, "novel-ink's NOVEL_INK_FRACTION_MIN, reused verbatim");
  assert.equal(DEGENERATE_ABS_MIN_PCT, 0.1, "novel-ink's NOVEL_INK_ABS_MIN_PCT, reused verbatim");
});

test('the scorer idiom and the capture-name derivation are shared, not re-invented', () => {
  assert.equal(isScored({ ssim: 0.99 }), true);
  assert.equal(isScored({ ssim: 0.99, scoreExcluded: 'native-font-parity' }), false);
  assert.equal(isScored({ ssim: null }), false);
  assert.equal(captureName('css/css-flexbox/abspos/abspos-autopos-htb-ltr.html'),
    'wpt__css-flexbox__abspos__abspos-autopos-htb-ltr.png');
});

test('the canvas is DERIVED as the reference modal colour, never asserted', () => {
  // A frame that is mostly one paint and partly another: the modal colour is
  // whichever covers more pixels, so the probe adapts to any corpus canvas.
  const mostlyA = img(100, 100, INK_A, { x: 0, y: 0, w: 10, h: 10, c: INK_B });
  const m = modalColour(mostlyA);
  assert.ok(Math.abs(m.r - INK_A[0]) <= 4 && Math.abs(m.b - INK_A[2]) <= 4, 'modal colour follows the image');
});

// ── 2. the displacement model ───────────────────────────────────────────────

test('identical images: nothing diverges, nothing is unexplained, nothing fires', () => {
  const a = img(100, 100, BG, { x: 20, y: 20, w: 20, h: 20, c: INK_A });
  const b = img(100, 100, BG, { x: 20, y: 20, w: 20, h: 20, c: INK_A });
  const blk = computeDegenerate(a, b);
  assert.equal(blk.divergentPx, 0);
  assert.equal(blk.unexplainedInkPx, 0);
  assert.equal(blk.refDroppedPx, 0);
  assert.equal(degenerateVetoFailed(blk), false);
});

test('a mark displaced by LESS than the radius is explained away in both directions', () => {
  const a = img(100, 100, BG, { x: 23, y: 20, w: 20, h: 20, c: INK_A }); // +3 px, inside radius 4
  const b = img(100, 100, BG, { x: 20, y: 20, w: 20, h: 20, c: INK_A });
  const blk = computeDegenerate(a, b);
  assert.ok(blk.divergentPx > 0, 'the images do disagree');
  assert.equal(blk.unexplainedInkPx, 0, 'every added pixel sits within the radius of reference ink');
  assert.equal(blk.refDroppedPx, 0, 'every reference pixel sits within the radius of capture ink');
  assert.equal(degenerateVetoFailed(blk), false, 'a small rigid translation is not a wrong answer');
});

test('the SAME mark displaced by MORE than the radius is not explained, and fires', () => {
  const a = img(100, 100, BG, { x: 60, y: 60, w: 20, h: 20, c: INK_A }); // far from the reference mark
  const b = img(100, 100, BG, { x: 20, y: 20, w: 20, h: 20, c: INK_A });
  const blk = computeDegenerate(a, b);
  assert.equal(blk.unexplainedInkPx, 400, 'the whole moved mark is unexplained');
  assert.equal(blk.refDroppedPx, 400, 'and the whole original mark is dropped');
  assert.equal(blk.unexplainedInkFraction, 1);
  assert.equal(degenerateVetoFailed(blk), true);
});

test('a recoloured mark in PLACE is unexplained — displacement never excuses a colour change', () => {
  const a = img(100, 100, BG, { x: 20, y: 20, w: 20, h: 20, c: INK_B });
  const b = img(100, 100, BG, { x: 20, y: 20, w: 20, h: 20, c: INK_A });
  const blk = computeDegenerate(a, b);
  assert.equal(blk.unexplainedInkPx, 400, 'no translation of the reference carries this paint');
  assert.equal(degenerateVetoFailed(blk), true);
});

test('this is the novel-ink MISS MECHANISM (a): recoloured AND moved still fires', () => {
  // The reference paints two marks; the capture paints only one of them, in the
  // other mark's place. Every capture pixel is in a colour the reference DOES
  // use, so a global-palette novelty test scores zero — the measured miss.
  const b = img(120, 120, BG, { x: 10, y: 10, w: 20, h: 20, c: INK_A });
  for (let y = 60; y < 80; y++) for (let x = 60; x < 80; x++) {        // second reference mark
    const i = (y * 120 + x) * 4; b.data[i] = INK_B[0]; b.data[i + 1] = INK_B[1]; b.data[i + 2] = INK_B[2];
  }
  const a = img(120, 120, BG, { x: 10, y: 10, w: 20, h: 20, c: INK_B }); // one mark, other colour, other place
  const blk = computeDegenerate(a, b);
  assert.ok(blk.unexplainedInkPx > 0, 'the capture paint is far from any reference pixel of that colour');
  assert.ok(blk.refDroppedPx > 0, 'and one reference mark went nowhere');
  assert.equal(degenerateVetoFailed(blk), true);
});

test('missing content fires through the reference-side half alone', () => {
  const a = img(100, 100, BG);                                          // the capture painted nothing
  const b = img(100, 100, BG, { x: 20, y: 20, w: 20, h: 20, c: INK_A });
  const blk = computeDegenerate(a, b);
  assert.equal(blk.divergentInkPx, 0, 'the capture added no paint at all');
  assert.equal(blk.unexplainedInkFraction, 0, '0/0 is a known zero, not an unknown');
  assert.equal(blk.refDroppedPx, 400, 'the whole reference mark is dropped');
  assert.equal(degenerateVetoFailed(blk), true);
});

test('unknown is never failed: no decode, no common area, empty reference', () => {
  assert.equal(computeDegenerate(null, img(10, 10, BG)), null);
  assert.equal(computeDegenerate(img(0, 0, BG), img(10, 10, BG)), null);
  assert.equal(degenerateVetoFailed(null), false);
  assert.equal(degenerateVetoFailed({}), false, 'a block without the numeric fields is unknown');
  assert.equal(degenerateVetoFailed({ unexplainedInkFraction: 1, unexplainedInkPct: 9 }), false,
    'a partial block is unknown, not a fire');
});

test('a frame-size mismatch is recorded, not silently cropped away', () => {
  const blk = computeDegenerate(img(100, 120, BG), img(100, 100, BG));
  assert.equal(blk.frameMismatch, true);
  assert.equal(blk.frameH, 100, 'measured on the common area');
});

// ── 3. the mutation test — every bar can be made to flip ────────────────────

test('MUTATION: each pre-registered bar, moved one step, flips the answer', () => {
  // A block sitting exactly on both bars: the predicate must fire…
  const onBar = { unexplainedInkFraction: DEGENERATE_FRACTION_MIN, unexplainedInkPct: DEGENERATE_ABS_MIN_PCT,
    refDroppedFraction: 0, refDroppedPct: 0 };
  assert.equal(degenerateVetoFailed(onBar), true, 'both bars met exactly → fire');
  // …and must stop firing when EITHER bar is missed by the smallest step the
  // reported precision can express (fractions 1e-4, percentages 1e-4).
  assert.equal(degenerateVetoFailed({ ...onBar, unexplainedInkFraction: DEGENERATE_FRACTION_MIN - 0.0001 }), false,
    'below the fraction bar → silent');
  assert.equal(degenerateVetoFailed({ ...onBar, unexplainedInkPct: DEGENERATE_ABS_MIN_PCT - 0.0001 }), false,
    'below the mass bar → silent');
  // The reference-side half is an independent route to the same answer.
  const refBar = { unexplainedInkFraction: 0, unexplainedInkPct: 0,
    refDroppedFraction: DEGENERATE_FRACTION_MIN, refDroppedPct: DEGENERATE_ABS_MIN_PCT };
  assert.equal(degenerateVetoFailed(refBar), true, 'the dropped-ink half fires on its own');
  assert.equal(degenerateVetoFailed({ ...refBar, refDroppedPct: DEGENERATE_ABS_MIN_PCT - 0.0001 }), false);
});

test('MUTATION: widening the radius excuses a displacement it must not excuse', () => {
  // A 12 px move of a 20 px mark: the two marks still overlap, so only the
  // non-overlapping bands diverge — this is the partially-explained regime a
  // real displaced render lives in, not the trivially-disjoint one above.
  const a = img(100, 100, BG, { x: 32, y: 20, w: 20, h: 20, c: INK_A });
  const b = img(100, 100, BG, { x: 20, y: 20, w: 20, h: 20, c: INK_A });
  assert.equal(degenerateVetoFailed(computeDegenerate(a, b, { radius: DEGENERATE_SHIFT_RADIUS })), true,
    'at the shipped radius a 12 px move is a defect');
  assert.equal(degenerateVetoFailed(computeDegenerate(a, b, { radius: 12 })), false,
    'at a widened radius the same move is excused — which is why the radius is pinned above');
});

test('the model degrades smoothly: a partly-overlapping move is partly explained', () => {
  // Stated because it is the probe's most important limitation and the reason
  // the wave50-B12 recall number is what it is: a mark moved by a little more
  // than the radius has most of its divergent band explained anyway, so the
  // fraction bar is not reached and the probe stays silent on small offsets.
  const a = img(100, 100, BG, { x: 26, y: 20, w: 20, h: 20, c: INK_A }); // +6 px, radius 4
  const b = img(100, 100, BG, { x: 20, y: 20, w: 20, h: 20, c: INK_A });
  const blk = computeDegenerate(a, b);
  assert.ok(blk.unexplainedInkPx > 0, 'some of the band is beyond the radius');
  assert.ok(blk.unexplainedInkFraction < DEGENERATE_FRACTION_MIN, 'but not the majority of it');
  assert.equal(degenerateVetoFailed(blk), false, 'so a 6 px offset is a MISS — stated, not hidden');
});

// ── 4. the arming gate ──────────────────────────────────────────────────────

test('the probe SHIPS DISARMED and arms only under its own variable', () => {
  assert.equal(isArmed({}), false, 'absent → disarmed');
  assert.equal(isArmed({ TITAN_DEGENERATE_VETO: '0' }), false);
  assert.equal(isArmed({ TITAN_DEGENERATE_VETO: 'true' }), false, 'only the exact "1" arms it');
  assert.equal(isArmed({ TITAN_DEGENERATE_VETO: '1' }), true);
  // Arming changes nothing about the measurement — the predicate is pure.
  const blk = computeDegenerate(img(100, 100, BG, { x: 60, y: 60, w: 20, h: 20, c: INK_A }),
    img(100, 100, BG, { x: 20, y: 20, w: 20, h: 20, c: INK_A }));
  assert.equal(degenerateVetoFailed(blk), true, 'computable while disarmed');
});

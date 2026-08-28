#!/usr/bin/env node
// Known-value controls for tools/visual/compare-screenshots-metrics.mjs.
//
// ## Why this file exists alongside compare-screenshots.test.mjs
//
// The existing suite tests every helper in exactly two shapes: "same PNG
// against itself → the perfect value" and "a real cross-platform pair → a
// number inside [0, 1]". Both are satisfied by a stub that returns the
// perfect value unconditionally. Neither would notice a metric that has
// stopped measuring anything — and one of them HAS (see the edge-SSIM
// section below, which this file pins as a bug rather than papering over).
//
// That gap stopped being academic on 2026-08-28, when ΔE95 was promoted
// from "computed, printed, gated nothing" to a GATING metric in
// `pairRegressed` — used by both the baseline gate and the cross-platform
// gate. A wrong ΔE now silently changes pass/fail on 327 pairs, and its
// only pins were "identical → ~0" and "≥ 0 and finite".
//
// Every control below has an answer derivable WITHOUT running the code:
// an analytic CIEDE2000 identity, a hand-counted percentile index, a
// closed-form KL divergence. That is deliberate — a control row with a
// known answer is what exposes a metric returning a plausible-looking
// zero, where a range assertion sails straight past it.
//
// Run: node --test tools/visual/compare-screenshots-metrics.test.mjs

import { test } from 'node:test';
import assert from 'node:assert/strict';

import {
  computeLabDeltaE,
  computeEdgeSsim,
  computePerChannelSsim,
  computeHistogramKL,
} from './compare-screenshots-metrics.mjs';

/**
 * Build an RGBA image of the shape the helpers consume — `{ data, width,
 * height }`, 4 opaque bytes per pixel. `fn(x, y)` returns [r, g, b].
 * Hand-built rather than loaded from baseline/ so every expected value in
 * this file is arithmetic a reviewer can redo on paper.
 */
function img(w, h, fn) {
  const data = new Uint8Array(w * h * 4);
  for (let y = 0; y < h; y += 1) {
    for (let x = 0; x < w; x += 1) {
      const i = (y * w + x) * 4;
      const [r, g, b] = fn(x, y);
      data[i] = r; data[i + 1] = g; data[i + 2] = b; data[i + 3] = 255;
    }
  }
  return { data, width: w, height: h };
}

/** Solid fill helper — the common case. */
const solid = (w, h, rgb) => img(w, h, () => rgb);

const BLACK = [0, 0, 0];
const WHITE = [255, 255, 255];

// ── B7 · ΔE — the metric that now decides pass/fail ─────────────────────────

test('computeLabDeltaE: black vs white is exactly ΔE00 100 (analytic control)', () => {
  // CIEDE2000 collapses to a closed form on the neutral axis: C' = 0 on both
  // sides kills the chroma and hue terms, and at L̄ = 50 the lightness
  // weighting is S_L = 1 + 0.015·(50−50)²/√(20+(50−50)²) = 1. So ΔE00 =
  // ΔL'/(k_L·S_L) = 100/1 = 100, exactly.
  //
  // Pins two ways to be silently wrong: (a) feeding culori raw 0–255 bytes
  // instead of 0–1 floats — white would land out of gamut and L* would not
  // be 100; (b) swapping the Lab conversion for a plain byte or Euclidean
  // RGB distance, which reads 255 (or 441) here, not 100.
  const d = computeLabDeltaE(solid(10, 10, BLACK), solid(10, 10, WHITE), 1);
  assert.deepEqual(d, { mean: 100, max: 100, p95: 100 });
});

test('computeLabDeltaE: p95 is floor(0.95·(n−1)) — the exact percentile index', () => {
  // The gate reads p95, so the index expression IS the gate's sensitivity.
  // With n = 100 samples the index is floor(0.95 × 99) = 94. Sorting k
  // maximal deltas to the top puts a non-zero at index 94 iff 94 ≥ 100 − k,
  // i.e. iff k ≥ 6. So five bad pixels must report p95 = 0 and six must
  // report p95 = 100 — a one-pixel-wide boundary.
  //
  // This is what separates the shipped convention from the plausible
  // alternative floor(0.95·n) = 95, which would flip at k ≥ 5. A gate
  // reading one percentile while the ledger was seeded from the other
  // silently re-scores every entry.
  const base = solid(10, 10, BLACK);
  const withBadPixels = (k) => {
    const b = solid(10, 10, BLACK);
    for (let p = 0; p < k; p += 1) {
      const i = p * 4;
      b.data[i] = 255; b.data[i + 1] = 255; b.data[i + 2] = 255;
    }
    return b;
  };
  assert.equal(computeLabDeltaE(base, withBadPixels(5), 1).p95, 0, '5/100 must sit below p95');
  assert.equal(computeLabDeltaE(base, withBadPixels(6), 1).p95, 100, '6/100 must reach p95');
  // max is the global maximum in both cases — it is not percentile-indexed,
  // so a change to the index must not move it.
  assert.equal(computeLabDeltaE(base, withBadPixels(5), 1).max, 100);
});

test('computeLabDeltaE: mean divides by SAMPLED pixels, not by the buffer', () => {
  // 6 pixels at ΔE 100 out of 100 sampled → mean exactly 6.0. Dividing by
  // the byte length (400) would give 1.5; dividing by the full pixel count
  // while sampling a subset would under-report on every strided run. Both
  // read as "the platforms agree better than they do".
  const b = solid(10, 10, BLACK);
  for (let p = 0; p < 6; p += 1) {
    const i = p * 4;
    b.data[i] = 255; b.data[i + 1] = 255; b.data[i + 2] = 255;
  }
  assert.equal(computeLabDeltaE(solid(10, 10, BLACK), b, 1).mean, 6);
});

test('computeLabDeltaE: stride counts PIXELS, not bytes', () => {
  // The default fast path is stride 4 — every 4th PIXEL, i.e. every 16th
  // byte. A `i += stride` typo would step 4 BYTES and land mid-pixel,
  // reading the green channel of one image against the red of the other:
  // garbage that still looks like a plausible small number.
  //
  // Control: a single differing pixel at index 0 IS sampled; the same
  // difference moved to index 1 is NOT. Byte-stepping would see both.
  const base = solid(4, 1, BLACK);
  const diffAt = (px) => {
    const b = solid(4, 1, BLACK);
    const i = px * 4;
    b.data[i] = 255; b.data[i + 1] = 255; b.data[i + 2] = 255;
    return b;
  };
  assert.equal(computeLabDeltaE(base, diffAt(0), 4).max, 100, 'pixel 0 is on the stride');
  assert.equal(computeLabDeltaE(base, diffAt(1), 4).max, 0, 'pixel 1 is skipped by stride 4');
});

test('computeLabDeltaE: a fully transparent pair returns null, never 0', () => {
  // Alpha-0 pixels are excluded because the colour beneath is undefined.
  // If exclusion left an empty sample set and the function still returned
  // {mean: 0, max: 0, p95: 0}, a pair with NO comparable pixels would score
  // as perfect agreement and pass the gate. Null is the only honest answer,
  // and `pairRegressed` is written to treat null as "no signal".
  const transparent = solid(4, 1, BLACK);
  for (let i = 3; i < transparent.data.length; i += 4) transparent.data[i] = 0;
  assert.equal(computeLabDeltaE(transparent, solid(4, 1, WHITE), 1), null);
});

test('computeLabDeltaE: a shape mismatch returns null, never a partial score', () => {
  // Two differently-sized buffers have no pixel correspondence at all.
  // Scoring the overlap would report a number for a comparison that was
  // never made — the "size mismatch" class the pad sentinel exists to expose.
  assert.equal(computeLabDeltaE(solid(4, 1, BLACK), solid(5, 1, BLACK), 1), null);
});

// ── B3 · edge SSIM — CURRENTLY DEGENERATE (bug pin) ─────────────────────────

test('computeEdgeSsim is DEGENERATE today — BUG PIN, delete when B3 is fixed', async () => {
  // THIS TEST ASSERTS BROKEN BEHAVIOUR ON PURPOSE. Do not "fix" it by
  // relaxing anything; fix compare-screenshots-metrics.mjs and delete it.
  //
  // `sobelEdges` runs sharp's `convolve` with the zero-sum Sobel-X kernel
  // and no `scale`/`offset`. On a uchar image libvips clamps the signed
  // response, and the output comes back ALL ZERO for every input — verified
  // directly (max = 0, non-zero count = 0 on a 64×64 hard vertical edge;
  // adding `offset: 128` makes the response appear, proving the kernel and
  // the plumbing are fine and the clamp is the cause).
  //
  // So B3 compares an all-zero buffer against an all-zero buffer and returns
  // 1.0 for EVERYTHING. Confirmed on live data: all 24 cross-platform pairs
  // in tools/visual/report/manifest.json read exactly 1, and six baseline
  // pairs of entirely unrelated components (ΔE95 up to 49.7) also read 1.
  //
  // Blast radius — every consumer is deciding on a constant:
  //   · classify-divergence.mjs `edge-shift` needs edgeSsim < 0.85, so that
  //     label is UNREACHABLE in production (its unit tests inject synthetic
  //     metrics and therefore pass).
  //   · the edge FLOORS guarding color-drift / sub-pixel-noise /
  //     glyph-metric-noise are always satisfied, so they no longer
  //     discriminate.
  //   · compare-screenshots.mjs's phase-B `edgeSsim < 0.95` warning can
  //     never fire.
  //   · docs/STATUS.md cites "edgeSsim 1.0" as evidence that the Android-web
  //     ~0.87 wall is AA-only — evidence that is vacuous while every pair
  //     reads 1.0.
  const flat = solid(64, 64, [50, 50, 50]);
  const hardEdge = img(64, 64, (x) => (x < 32 ? [20, 20, 20] : [230, 230, 230]));
  assert.equal(
    await computeEdgeSsim(flat, hardEdge),
    1,
    'if this now differs from 1, B3 has been repaired — delete this test and un-todo the next one',
  );
});

test('computeEdgeSsim distinguishes a hard edge from a flat field', { todo: 'B3 Sobel output is identically zero — see the bug pin above' }, async () => {
  // The behaviour B3 is supposed to have, kept executable so the fix has a
  // target. A flat field has no Sobel-X response; a mid-image vertical step
  // has a strong one. Their edge maps cannot be similar.
  const flat = solid(64, 64, [50, 50, 50]);
  const hardEdge = img(64, 64, (x) => (x < 32 ? [20, 20, 20] : [230, 230, 230]));
  const e = await computeEdgeSsim(flat, hardEdge);
  assert.ok(e !== null && e < 0.85, `expected an edge-shift-grade score, got ${e}`);
});

// ── B2 · per-channel SSIM — the channel index is the whole point ────────────

test('computePerChannelSsim names the channel that actually diverged', async () => {
  // The existing tests only check identical → all 1.0 and cross-platform →
  // inside [0, 1]. Both pass if the channel offsets are swapped, which is
  // the one bug this metric can have that matters: it would report a red
  // divergence as blue and send a reader to the wrong applier.
  //
  // Control: structure injected into ONE channel while the other two stay
  // flat. The diverged channel must collapse; the untouched channels and
  // alpha must stay exactly 1.0.
  const flat = solid(64, 64, [100, 100, 100]);
  const blueStructure = img(64, 64, (x) => [100, 100, x % 8 < 4 ? 40 : 220]);
  const blue = await computePerChannelSsim(flat, blueStructure);
  assert.ok(blue.b < 0.1, `blue channel must collapse, got ${blue.b}`);
  assert.equal(blue.r, 1, 'red was untouched');
  assert.equal(blue.g, 1, 'green was untouched');
  assert.equal(blue.a, 1, 'alpha was untouched');

  const redStructure = img(64, 64, (x) => [x % 8 < 4 ? 40 : 220, 100, 100]);
  const red = await computePerChannelSsim(flat, redStructure);
  assert.ok(red.r < 0.1, `red channel must collapse, got ${red.r}`);
  assert.equal(red.b, 1, 'a swapped R/B offset would show up exactly here');
});

// ── B4 · histogram KL — closed form, including the smoothing ────────────────

test('computeHistogramKL matches the closed form, Laplace smoothing included', () => {
  // A = 50 black + 50 white pixels; B = 100 black. Per channel, n = 100 and
  // the smoothed denominator is n + 256 = 356:
  //   P(0) = P(255) = 51/356,  Q(0) = 101/356,  Q(255) = 1/356
  //   D(P‖Q) = (51/356)·[ln(51/101) + ln(51)] = 0.4654
  // Every other bucket contributes ln(1) = 0.
  //
  // Pins the smoothing itself: without the +1, Q(255) = 0 and the sum is
  // +Infinity — which `+x.toFixed(4)` turns into NaN, and NaN silently
  // renders as an empty cell rather than an error.
  const half = img(10, 10, (x, y) => (y < 5 ? [0, 0, 0] : [255, 255, 255]));
  const black = solid(10, 10, BLACK);
  assert.deepEqual(computeHistogramKL(half, black), { r: 0.4654, g: 0.4654, b: 0.4654 });
});

test('computeHistogramKL is asymmetric — argument order is load-bearing', () => {
  // KL is a divergence, not a distance: D(A‖B) = 0.4654 but D(B‖A) =
  // (101/356)·ln(101/51) + (1/356)·ln(1/51) = 0.1828. A swapped call site
  // would keep producing sane-looking small numbers, so equality here would
  // be the only symptom.
  const half = img(10, 10, (x, y) => (y < 5 ? [0, 0, 0] : [255, 255, 255]));
  const black = solid(10, 10, BLACK);
  assert.equal(computeHistogramKL(black, half).r, 0.1828);
  assert.notEqual(computeHistogramKL(black, half).r, computeHistogramKL(half, black).r);
});

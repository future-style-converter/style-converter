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
// The B3 real-pair control loads two committed baseline PNGs (no capture run
// needed) and contrasts edge SSIM against plain SSIM of the same pair, so it
// needs the PNG decoder, file access, and ssim.js itself.
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';
import { ssim as computeSsim } from 'ssim.js';

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

// ── B3 · edge SSIM — acceptance controls for the repaired Sobel path ────────
//
// History: until 2026-08-29 `computeEdgeSsim` returned exactly 1 for EVERY
// input. sharp's `convolve` defaults `scale` to the kernel sum, a Sobel
// kernel sums to zero, and the resulting divide-by-zero yielded an all-zero
// edge map on both sides — two of which are perfectly similar. Every test
// below except the two "exactly 1.0" controls failed against that
// implementation (verified before the fix landed); together they make a
// return-to-degeneracy impossible to miss.

// Path to the committed baseline captures — real render output, so the
// real-pair control below cannot be satisfied by anything tuned only to
// synthetic step edges.
const __dirname = dirname(fileURLToPath(import.meta.url));
const BASELINE = resolve(__dirname, 'baseline');

/** Decode one committed baseline PNG into the `{ data, width, height }`
 *  shape the metric helpers consume. pngjs only — no sharp, so the loader
 *  shares zero code with the implementation under test. */
const loadBaseline = (name) => PNG.sync.read(readFileSync(resolve(BASELINE, name)));

test('computeEdgeSsim: identical images → exactly 1.0', async () => {
  // Identity control: same buffer twice must produce identical edge maps,
  // and SSIM of identical images is exactly 1 (numerator equals denominator
  // term-for-term). Anything below 1 here means the edge extraction is
  // non-deterministic; null means the pipeline broke on a trivial input.
  // Legitimate as an EXACT assertion because the harness A/A noise floor is
  // byte-zero (docs/STATUS.md 2026-08-28).
  const edge = img(64, 64, (x) => (x < 32 ? [20, 20, 20] : [230, 230, 230]));
  assert.equal(await computeEdgeSsim(edge, edge), 1);
});

test('computeEdgeSsim distinguishes a hard edge from a flat field', async () => {
  // The core discrimination the metric exists for. A flat field has zero
  // gradient everywhere; a mid-image vertical step has a strong 2px-wide
  // Sobel response band. Their edge maps cannot be similar — the ~13 of 54
  // window columns that overlap the band collapse toward 0 while the rest
  // score 1, and the measured aggregate is 0.8224.
  //
  // The 0.85 ceiling is load-bearing beyond "well below 0.9": it pins that
  // classify-divergence.mjs's `edge-shift` label (edgeSsim < 0.85) is
  // REACHABLE on a physically real edge difference — under the degenerate
  // always-1 implementation that label was dead code in production.
  const flat = solid(64, 64, [50, 50, 50]);
  const hardEdge = img(64, 64, (x) => (x < 32 ? [20, 20, 20] : [230, 230, 230]));
  const e = await computeEdgeSsim(flat, hardEdge);
  assert.ok(e !== null && e < 0.85, `expected an edge-shift-grade score, got ${e}`);
});

test('computeEdgeSsim grades edge displacement — 1px above absence, 8px well below 1', async () => {
  // Measured behaviour of SSIM over Sobel magnitude maps (2026-08-29 probe):
  //   flat vs edge@32          0.8224   (absence of the edge)
  //   edge@32 vs edge@33 (1px) 0.8438   (AA-jitter-scale displacement)
  //   edge@32 vs edge@40 (8px) 0.6759   (gross displacement)
  //
  // Two properties are pinned. (1) A 1px displacement — the cross-platform
  // AA-rounding case this metric exists to tolerate — ranks ABOVE total
  // absence of the edge. (2) A displacement is never mistaken for identity.
  //
  // Deliberately NOT pinned: "8px shift ranks above absence". SSIM cannot
  // deliver that ordering — in windows containing both band positions the
  // two maps are ANTI-correlated (edge here vs edge there → negative
  // structure term), which SSIM scores as worse than band-vs-nothing (zero
  // covariance), and the shifted pair damages ~2× the window area. Any
  // implementation whose final step is SSIM over edge maps ranks a large
  // shift below absence; the classifier recovers the distinction through
  // pHash (edge-shift = low edgeSsim + LOW hamming; see
  // classify-divergence.mjs Section 4 rule 4).
  const flat = solid(64, 64, [50, 50, 50]);
  const edgeAt = (c) => img(64, 64, (x) => (x < c ? [20, 20, 20] : [230, 230, 230]));
  const absence = await computeEdgeSsim(flat, edgeAt(32));
  const shift1 = await computeEdgeSsim(edgeAt(32), edgeAt(33));
  const shift8 = await computeEdgeSsim(edgeAt(32), edgeAt(40));
  assert.ok(shift1 > absence, `1px displacement (${shift1}) must rank above edge absence (${absence})`);
  assert.ok(shift1 < 1, `1px displacement must not read as identity, got ${shift1}`);
  assert.ok(shift8 !== null && shift8 < 0.9, `8px displacement must score clearly below 1, got ${shift8}`);
});

test('computeEdgeSsim sees HORIZONTAL edges — the axis the old X-only Sobel was blind to', async () => {
  // Adversarial-review requirement. The repair computes BOTH Sobel
  // directions; an X-only kernel scores gy = 0 everywhere, so a horizontal
  // edge (the top/bottom border of every component box) would compare as
  // FLAT — i.e. horizontal-edge-vs-flat would read 1.0, quietly restoring
  // half of the original degeneracy. This is the same control as the
  // vertical case, rotated 90°, and must produce the same discrimination.
  const flat = img(64, 64, () => [50, 50, 50]);
  const hEdge = img(64, 64, (x, y) => (y < 32 ? [20, 20, 20] : [230, 230, 230]));
  const score = await computeEdgeSsim(hEdge, flat);
  assert.ok(score !== null && score < 0.9,
    `horizontal edge vs flat must discriminate, got ${score}`);
  // And identity still holds on the rotated input.
  assert.equal(await computeEdgeSsim(hEdge, hEdge), 1);
});

test('computeEdgeSsim: flat vs flat in different colours → exactly 1.0', async () => {
  // CORRECT by design, not a blind spot: an edge metric measures edge
  // structure and nothing else. Two flat fields both have identically-zero
  // gradient maps — equal maps, SSIM exactly 1 — regardless of fill colour.
  // Colour divergence is ΔE's (B7) and the histogram's (B4) job; keeping B3
  // colour-blind is what lets the classifier separate "recoloured" from
  // "moved" (color-drift requires a HIGH edge floor precisely because a
  // recolour leaves edges alone).
  //
  // This is also the control that kills the forbidden fallback: plain RGB
  // SSIM on these two fields reads ≈0.29 (luminance term), so a silent
  // "fall back to image SSIM on failure" — explicitly banned by the B3 spec
  // note — fails here instead of hiding.
  const greyFlat = solid(64, 64, [50, 50, 50]);
  const redFlat = solid(64, 64, [200, 30, 30]);
  assert.equal(await computeEdgeSsim(greyFlat, redFlat), 1);
});

test('computeEdgeSsim on a real baseline pair is finite, in (0,1], and is not plain SSIM', async () => {
  // Real-data control on two committed captures of the same component from
  // different platforms — iOS vs Android 090_Button_Primary (a
  // fixtures/visual-test.json component, so its baselines are refreshed by
  // every UPDATE_BASELINE run and cannot be orphaned), both 390×62 (equal
  // dims by construction of this pair, so no padding step can distort the
  // comparison), 204 genuinely differing pixels (measured 2026-09-05:
  // edge 0.9481 vs plain 0.9533). The pair used to be 000_AR_Single1 — an
  // orphan of a fixture pruned 2026-07-08 whose 36 PNGs the retrospective
  // removed (A12#4), which is why the pin now rides a LIVE fixture.
  //
  // Three pins: (a) the metric survives real render output (no null); (b) a
  // pair with real pixel differences must NOT read exactly 1 — under the
  // degenerate implementation every real pair read exactly 1, so this line
  // alone would have caught it; (c) the edge score differs from plain SSIM
  // of the same pair, proving B3 measures the gradient structure rather
  // than repackaging the base metric.
  const a = loadBaseline('iOS__090_Button_Primary.png');
  const b = loadBaseline('Android__090_Button_Primary.png');
  assert.equal(`${a.width}x${a.height}`, `${b.width}x${b.height}`, 'the control pair must share dimensions (no padding step)');
  const e = await computeEdgeSsim(a, b);
  assert.ok(e !== null && Number.isFinite(e), `expected a finite score, got ${e}`);
  assert.ok(e > 0 && e <= 1, `edge SSIM out of (0,1]: ${e}`);
  assert.ok(e < 1, `a pair with 204 differing pixels must not score exactly 1, got ${e}`);
  const plain = +computeSsim(a, b, { ssim: 'fast' }).mssim.toFixed(4);
  assert.notEqual(e, plain, `edge SSIM (${e}) must not equal plain SSIM (${plain}) on a real pair`);
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

// ── computeLabDeltaE sampling phase ─────────────────────────────────────────

test('computeLabDeltaE sees a 1px hairline at an ODD column of a 390-wide image', () => {
  // The old linear stride's phase shifted by (width mod stride) per row —
  // at the corpus width 390 (≡ 2 mod 4) sampled columns alternated between
  // x ≡ 0 and x ≡ 2 (mod 4), so ODD columns were never examined and a 1px
  // vertical hairline there was deterministically invisible to the gating
  // ΔE95, on every run (the zero noise floor made the blindness stable).
  // The per-row phase rotation covers every residue class.
  const W = 390, H = 40;
  const mk = (hair) => {
    const d = Buffer.alloc(W * H * 4);
    for (let i = 0; i < W * H; i++) { d[i*4] = 26; d[i*4+1] = 26; d[i*4+2] = 46; d[i*4+3] = 255; }
    if (hair) for (let y = 0; y < H; y++) { const i = (y*W + 201) * 4; d[i] = 255; d[i+1] = 0; d[i+2] = 0; }
    return { data: d, width: W, height: H };
  };
  const r = computeLabDeltaE(mk(false), mk(true), 4);
  assert.ok(r !== null && r.max > 10, `odd-column hairline must register (got ${JSON.stringify(r)})`);
  // Identity stays exactly zero — the phase rotation must not manufacture
  // deltas out of sampling asymmetry.
  const same = computeLabDeltaE(mk(true), mk(true), 4);
  assert.deepEqual(same, { mean: 0, max: 0, p95: 0 });
});

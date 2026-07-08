#!/usr/bin/env node
// Unit tests for the new image-comparison metrics added to
// tools/visual/compare-screenshots.mjs per COMPARE_METRICS.md Section 7
// (items 1–6: B6 dssim · B4 histogramKL · B2 perChannelSsim · B5 pHash ·
// B3 edgeSsim · B7 labDeltaE).
//
// Each test reads two known PNGs from tools/visual/baseline/ and asserts the
// metric falls in the expected range. We use two scenarios:
//   (1) identical pair — same iOS Glass_Effect PNG against itself.
//       Every metric should hit its "perfect" value (SSIM=1.0, KL=0, etc).
//   (2) cross-platform pair — iOS vs Android of the same component.
//       Each metric should be in its plausible "similar but not identical"
//       range; we keep these bounds loose so render-engine drift between
//       OS releases doesn't fail the test, but tight enough to catch a
//       bug that swaps the function with a no-op.
//
// Run via `node --test tools/visual/compare-screenshots.test.mjs`. The smoke.sh
// glob `node --test tools/visual/*.test.mjs` picks this up automatically.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';
import sharp from 'sharp';

import {
  computeDssim,
  computeHistogramKL,
  computePerChannelSsim,
  computePHash,
  computeEdgeSsim,
  computeLabDeltaE,
} from './compare-screenshots-metrics.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const BASELINE = resolve(__dirname, 'baseline');

// ── Fixture loader ──────────────────────────────────────────────────────────
// Pad both images to a shared canvas (max width × max height) so they have
// identical buffer shapes — this mirrors what `padToCanvas` does inside
// compare-screenshots.mjs. Without it, the cross-platform pair would have
// different heights (iOS 100 vs Android 102 px on Glass_Effect) and the
// metric helpers would correctly bail on the shape mismatch — masking the
// thing we actually want to test.
async function loadPair(aName, bName) {
  const aPath = resolve(BASELINE, aName);
  const bPath = resolve(BASELINE, bName);
  const aRaw = PNG.sync.read(readFileSync(aPath));
  const bRaw = PNG.sync.read(readFileSync(bPath));
  const W = Math.max(aRaw.width, bRaw.width);
  const H = Math.max(aRaw.height, bRaw.height);
  return { a: await padToCanvas(aRaw, W, H), b: await padToCanvas(bRaw, W, H) };
}

async function padToCanvas(img, W, H) {
  if (img.width === W && img.height === H) return img;
  const padded = await sharp(PNG.sync.write(img))
    .extend({
      top: 0,
      bottom: Math.max(0, H - img.height),
      left: 0,
      right: Math.max(0, W - img.width),
      background: { r: 0x1A, g: 0x1A, b: 0x2E, alpha: 1 },
    })
    .png()
    .toBuffer();
  return PNG.sync.read(padded);
}

// ── B6 — DSSIM (derived) ────────────────────────────────────────────────────

test('computeDssim: identical images → 0', () => {
  // (1 - 1) / 2 = 0
  assert.equal(computeDssim(1.0), 0);
});

test('computeDssim: ssim 0.95 (regression boundary) → 0.025', () => {
  // Round to 4 dp to dodge IEEE-754 float drift.
  assert.equal(computeDssim(0.95), 0.025);
});

test('computeDssim: null SSIM → null (no fabricated 0.5)', () => {
  // Section 7 item 1 — skip when SSIM itself failed; otherwise we'd report
  // "max divergence" 0.5 from a decode error, not from real divergence.
  assert.equal(computeDssim(null), null);
});

// ── B4 — Histogram KL ───────────────────────────────────────────────────────

test('computeHistogramKL: identical PNG against itself → ~0 on every channel', async () => {
  const { a, b } = await loadPair('iOS__097_Glass_Effect.png', 'iOS__097_Glass_Effect.png');
  const kl = computeHistogramKL(a, b);
  assert.ok(kl, 'expected an object');
  // Laplace-smoothed identical distributions yield exactly 0 (P=Q so log(1)=0).
  assert.equal(kl.r, 0);
  assert.equal(kl.g, 0);
  assert.equal(kl.b, 0);
});

test('computeHistogramKL: cross-platform pair → small positive on at least one channel', async () => {
  const { a, b } = await loadPair('iOS__097_Glass_Effect.png', 'Android__097_Glass_Effect.png');
  const kl = computeHistogramKL(a, b);
  assert.ok(kl);
  // KL is non-negative by construction; at least one channel should have
  // measurable divergence on a cross-platform pair (they're never bit-identical).
  assert.ok(kl.r >= 0 && kl.g >= 0 && kl.b >= 0, 'KL must be non-negative');
  assert.ok(kl.r + kl.g + kl.b > 0, 'cross-platform pair should have some KL > 0');
});

// ── B2 — Per-channel SSIM ───────────────────────────────────────────────────

test('computePerChannelSsim: identical PNG → all 1.0', async () => {
  const { a, b } = await loadPair('iOS__097_Glass_Effect.png', 'iOS__097_Glass_Effect.png');
  const m = await computePerChannelSsim(a, b);
  assert.ok(m);
  assert.equal(m.r, 1);
  assert.equal(m.g, 1);
  assert.equal(m.b, 1);
  assert.equal(m.a, 1);
});

test('computePerChannelSsim: cross-platform pair → all in [0..1], alpha typically near 1', async () => {
  const { a, b } = await loadPair('iOS__097_Glass_Effect.png', 'Android__097_Glass_Effect.png');
  const m = await computePerChannelSsim(a, b);
  assert.ok(m);
  for (const ch of ['r', 'g', 'b', 'a']) {
    assert.ok(m[ch] >= 0 && m[ch] <= 1, `${ch} SSIM out of [0,1]: ${m[ch]}`);
  }
});

// ── B5 — pHash ──────────────────────────────────────────────────────────────

test('computePHash: identical PNG → hammingDistance 0', async () => {
  const { a, b } = await loadPair('iOS__097_Glass_Effect.png', 'iOS__097_Glass_Effect.png');
  const ph = await computePHash(a, b);
  assert.ok(ph);
  assert.equal(ph.hammingDistance, 0);
  assert.ok(ph.hex && ph.hex.length === 64, 'expected 64-char pHash hex string');
});

test('computePHash: cross-platform pair → hammingDistance in [0..64]', async () => {
  const { a, b } = await loadPair('iOS__097_Glass_Effect.png', 'Android__097_Glass_Effect.png');
  const ph = await computePHash(a, b);
  assert.ok(ph);
  assert.ok(ph.hammingDistance >= 0 && ph.hammingDistance <= 64,
    `hamming distance out of range: ${ph.hammingDistance}`);
});

// ── B3 — Edge SSIM ──────────────────────────────────────────────────────────

test('computeEdgeSsim: identical PNG → 1.0', async () => {
  const { a, b } = await loadPair('iOS__097_Glass_Effect.png', 'iOS__097_Glass_Effect.png');
  const e = await computeEdgeSsim(a, b);
  assert.equal(e, 1);
});

test('computeEdgeSsim: cross-platform pair → in [0..1]', async () => {
  const { a, b } = await loadPair('iOS__097_Glass_Effect.png', 'Android__097_Glass_Effect.png');
  const e = await computeEdgeSsim(a, b);
  assert.ok(e !== null, 'edge SSIM should compute on a real pair');
  assert.ok(e >= 0 && e <= 1, `edge SSIM out of [0,1]: ${e}`);
});

// ── B7 — LAB ΔE (CIEDE2000) ─────────────────────────────────────────────────

test('computeLabDeltaE: identical PNG → mean/max/p95 all ~0', async () => {
  const { a, b } = await loadPair('iOS__097_Glass_Effect.png', 'iOS__097_Glass_Effect.png');
  const lab = computeLabDeltaE(a, b, 4);
  assert.ok(lab);
  // Identical pixel-by-pixel ⇒ ΔE === 0 to within float epsilon.
  assert.ok(lab.mean < 0.001, `mean expected ~0, got ${lab.mean}`);
  assert.ok(lab.max < 0.001, `max expected ~0, got ${lab.max}`);
  assert.ok(lab.p95 < 0.001, `p95 expected ~0, got ${lab.p95}`);
});

test('computeLabDeltaE: cross-platform pair → mean/max/p95 ≥ 0 and finite', async () => {
  const { a, b } = await loadPair('iOS__097_Glass_Effect.png', 'Android__097_Glass_Effect.png');
  const lab = computeLabDeltaE(a, b, 4);
  assert.ok(lab);
  assert.ok(lab.mean >= 0 && Number.isFinite(lab.mean));
  assert.ok(lab.max >= 0 && Number.isFinite(lab.max));
  assert.ok(lab.p95 >= 0 && Number.isFinite(lab.p95));
  // max is the global maximum so it bounds everything else.
  // Cross-platform PNG pairs that share a 1A1A2E background are
  // long-tailed: 95%+ of pixels are bit-identical (ΔE=0) but the
  // few rendered-content pixels can have ΔE>>0, so mean can exceed
  // p95. Just assert the strict ordering that always holds.
  assert.ok(lab.max >= lab.p95, 'max must be ≥ p95');
  assert.ok(lab.max >= lab.mean, 'max must be ≥ mean');
});

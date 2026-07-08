#!/usr/bin/env node
// Unit tests for the B-EXT typography divergence helpers added in
// testing/compare-screenshots-text-metrics.mjs (B8, B9, B10).
//
// Each metric gets ≥1 pass case + ≥1 edge / failure case (CLAUDE.md
// hard rule on test rigor). All assertions hit real PNG fixtures
// committed under testing/fixtures/text-metrics/ — generated once via
// testing/_gen-text-metrics-fixtures.mjs (Section 8 q7: "Don't generate
// at test time — sharp's font rendering varies across CI runners,
// breaking the test"). Re-run that generator only when the metric
// semantics change.
//
// Run via `node --test testing/compare-screenshots-text-metrics.test.mjs`.
// The smoke.sh glob `node --test testing/*.test.mjs` picks this up.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  computeBaselineY,
  classifyAaStrategy,
  computeGlyphSpacing,
  readPng,
  B9_SUBPIXEL_RATIO_MIN,
  B9_GREY_RATIO_MAX,
  B9_NONE_HI_FRACTION_MIN,
} from './compare-screenshots-text-metrics.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const FIXT = resolve(__dirname, 'fixtures/text-metrics');

// ─── B8 ───────────────────────────────────────────────────────────────────

test('computeBaselineY: synthetic baseline at 4× row 200 → 50.0 px @1×', () => {
  const png = readPng(`${FIXT}/b8_synthetic_baseline_200.png`);
  const y = computeBaselineY(png);
  assert.equal(y, 50);
});

test('computeBaselineY: synthetic baseline at 4× row 204 → 51.0 px @1×', () => {
  const png = readPng(`${FIXT}/b8_synthetic_baseline_204.png`);
  const y = computeBaselineY(png);
  assert.equal(y, 51);
});

test('computeBaselineY: 1.0-px @1× delta between fixtures (above 0.5-px JND)', () => {
  // Spec Section 4 / B8: ≥0.5 px = "drift" (orange/red verdict). The
  // synthetic 4-row shift was sized to land in this band — proves the
  // helper resolves the threshold boundary.
  const a = computeBaselineY(readPng(`${FIXT}/b8_synthetic_baseline_200.png`));
  const b = computeBaselineY(readPng(`${FIXT}/b8_synthetic_baseline_204.png`));
  assert.equal(b - a, 1.0);
});

test('computeBaselineY: empty (all-white) image → null (no glyph mass)', () => {
  // Failure mode: a probe component that failed to render leaves a
  // background-only PNG. The helper must NOT report "baseline at row 0";
  // it must return null so the manifest correctly says "data missing".
  const png = readPng(`${FIXT}/b8_synthetic_baseline_200.png`);
  // Wipe to all white in-place. Single-channel-darkness scan must miss.
  for (let i = 0; i < png.data.length; i += 4) {
    png.data[i] = png.data[i + 1] = png.data[i + 2] = 255;
    png.data[i + 3] = 255;
  }
  const y = computeBaselineY(png);
  assert.equal(y, null);
});

test('computeBaselineY: null/undefined input → null (no throw)', () => {
  assert.equal(computeBaselineY(null), null);
  assert.equal(computeBaselineY(undefined), null);
  assert.equal(computeBaselineY({}), null);
});

// ─── B9 ───────────────────────────────────────────────────────────────────

test('classifyAaStrategy: subpixel-AA fixture → "subpixel"', () => {
  // The synthetic fixture deliberately exaggerates R-B chroma at the
  // edges (red on one side of the line, blue on the other) so the
  // R-B FFT carries strong high-frequency energy → ratio > 0.30.
  const png = readPng(`${FIXT}/b9_subpixel_45deg.png`);
  const label = classifyAaStrategy(png);
  assert.equal(label, 'subpixel');
});

test('classifyAaStrategy: greyscale-AA fixture → "greyscale"', () => {
  // Mid-grey edge pixels on both sides — R == G == B, so chroma is
  // identically zero → ratio < B9_GREY_RATIO_MAX. Luma carries the
  // line edge → hi-freq fraction crosses B9_GREY_HI_FRACTION_MIN.
  const png = readPng(`${FIXT}/b9_greyscale_45deg.png`);
  const label = classifyAaStrategy(png);
  assert.equal(label, 'greyscale');
});

test('classifyAaStrategy: bilevel (no-AA) fixture → "none"', () => {
  // No edge pixels at all besides the 1-px-wide diagonal → almost no
  // hi-freq energy in either channel.
  const png = readPng(`${FIXT}/b9_none_45deg.png`);
  const label = classifyAaStrategy(png);
  assert.equal(label, 'none');
});

test('classifyAaStrategy: thresholds match exported constants', () => {
  // Sanity: future tuning will revisit these literals; the test guards
  // that the spec's named exports stay reachable AND in the right ballpark.
  // Bumping the constants requires updating both the file AND this
  // assertion intentionally so an accidental flip isn't silent.
  assert.ok(B9_SUBPIXEL_RATIO_MIN > B9_GREY_RATIO_MAX,
    'subpixel threshold must be strictly above greyscale ceiling');
  assert.ok(B9_NONE_HI_FRACTION_MIN > 0 && B9_NONE_HI_FRACTION_MIN < 1,
    'lFraction split must lie in (0, 1)');
});

test('classifyAaStrategy: short image (height < 128) → "unknown"', () => {
  // FFT requires a 128-px slice; a too-short image must short-circuit
  // to "unknown" rather than throwing or reading garbage.
  const png = readPng(`${FIXT}/b9_subpixel_45deg.png`);
  const small = { data: png.data.slice(0, png.width * 4 * 64), width: png.width, height: 64 };
  const label = classifyAaStrategy(small);
  assert.equal(label, 'unknown');
});

// ─── B10 ──────────────────────────────────────────────────────────────────

test('computeGlyphSpacing: mono synthetic → mean ≈ 24 px @1×, low stddev', () => {
  // The mono fixture has 5 black blocks separated by uniform gaps of
  // 16 px @1× = 64 px @4× (gap) + 8 px @1× = 32 px @4× (block width).
  // Inter-peak distance = blockW + gap = 96 px @4× = 24 px @1×.
  const png = readPng(`${FIXT}/b10_mono_synthetic.png`);
  const r = computeGlyphSpacing(png, 24);
  assert.ok(r, 'mono fixture must yield a result');
  assert.equal(r.count, 4); // 5 blocks → 4 inter-glyph gaps
  // Allow ±1 px tolerance for peak-detection rounding.
  assert.ok(Math.abs(r.mean - 24) < 1, `mean expected ~24, got ${r.mean}`);
  // Mono fixture has uniform gaps → stddev should be tiny.
  assert.ok(r.stddev < 0.5, `mono stddev expected <0.5, got ${r.stddev}`);
});

test('computeGlyphSpacing: kerned synthetic → larger stddev (hinting drift)', () => {
  // Kerned fixture widens 2 of the 4 gaps; mean drifts, stddev jumps.
  // Spec Section 4 / B10: stddev > 1.5 = hinting drift verdict.
  const png = readPng(`${FIXT}/b10_kerned_synthetic.png`);
  const r = computeGlyphSpacing(png, 24);
  assert.ok(r);
  assert.equal(r.count, 4);
  // The kerned mix of 64-px and 128-px gaps yields a stddev clearly
  // above mono's <0.5 threshold (in 1× px ⇒ ~8 expected).
  assert.ok(r.stddev > 1.5,
    `kerned stddev expected >1.5 (hinting-drift band), got ${r.stddev}`);
});

test('computeGlyphSpacing: meanEm computed when fontSizePx given', () => {
  const png = readPng(`${FIXT}/b10_mono_synthetic.png`);
  const r = computeGlyphSpacing(png, 24);
  assert.ok(r.meanEm !== undefined, 'meanEm must be present when fontSizePx is set');
  // mean ~24 px / 24 px font size = 1.0 em.
  assert.ok(Math.abs(r.meanEm - 1.0) < 0.05,
    `meanEm expected ~1.0, got ${r.meanEm}`);
});

test('computeGlyphSpacing: empty image → null (no fabricated zeros)', () => {
  // Failure mode parity with B8: a fully-white image must not yield
  // "0 px spacing" — that would silently become a "broken" verdict
  // downstream when the right answer is "no measurement available".
  const png = readPng(`${FIXT}/b10_mono_synthetic.png`);
  for (let i = 0; i < png.data.length; i += 4) {
    png.data[i] = png.data[i + 1] = png.data[i + 2] = 255;
    png.data[i + 3] = 255;
  }
  const r = computeGlyphSpacing(png, 24);
  assert.equal(r, null);
});

test('computeGlyphSpacing: null/garbage input → null', () => {
  assert.equal(computeGlyphSpacing(null, 24), null);
  assert.equal(computeGlyphSpacing({}, 24), null);
});

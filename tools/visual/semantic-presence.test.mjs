#!/usr/bin/env node
// Unit tests for the semantic-presence gate in
// tools/visual/compare-screenshots-metrics.mjs, plus the one integration
// control that ties it to pad-canvas.mjs.
//
// ## Why this needed its own file
//
// `computeSemanticPresence` had ZERO direct tests. It is referenced only in
// comments elsewhere in the suite. It is also the single guard that stops a
// degenerate pair — two essentially blank captures whose matching dark
// background scores SSIM 0.91 — from being read as agreement. It runs on
// every one of the 327 fixture pairs (compare-screenshots.mjs) AND on every
// WPT diff in the TITAN corpus (inject-wpt-block.mjs, against a WHITE
// canvas via the third parameter). A wrong number here does not throw and
// does not look wrong: it re-opens the pilot-001 false positive that the
// gate was built to close, everywhere at once.
//
// The controls are all exact pixel counts, so an off-by-one in the
// tolerance comparison or a dropped background parameter shows up as a
// wrong integer rather than as a plausible float.
//
// Run: node --test tools/visual/semantic-presence.test.mjs

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { PNG } from 'pngjs';

import {
  computeSemanticPresence,
  computeLabDeltaE,
  CANONICAL_BG,
  SEMANTIC_PRESENCE_TOLERANCE,
  SEMANTIC_PRESENCE_EMPTY_PCT,
} from './compare-screenshots-metrics.mjs';
import { classifyDivergence } from './classify-divergence.mjs';
import { padToCanvas, PAD_SENTINEL } from './pad-canvas.mjs';

/** RGBA image in the shape the metric helpers consume. `fn(x,y) → [r,g,b]`. */
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

/** The canonical capture background, as an [r,g,b] triple. */
const BG = [CANONICAL_BG.r, CANONICAL_BG.g, CANONICAL_BG.b];
/** An arbitrary saturated "ink" colour, far from the background on every channel. */
const INK = [0x3a, 0x98, 0xdb];
/** 10×10 = 100 pixels, so every coverage figure below is a whole percent. */
const blank = () => img(10, 10, () => BG);

// ── Coverage arithmetic ─────────────────────────────────────────────────────

test('coverage is an exact pixel fraction, measured per side', () => {
  // 7 ink pixels of 100 → 7.000 %. The blank peer → 0.000 %. Exact integers
  // because the canvas is 100 pixels; anything that changes the denominator
  // (counting bytes, counting only one channel, skipping the last row)
  // lands on a non-integer and is caught here rather than shrugged at.
  const ink = img(10, 10, (x, y) => (y * 10 + x < 7 ? INK : BG));
  const p = computeSemanticPresence(ink, blank());
  assert.equal(p.aCoveragePct, 7);
  assert.equal(p.bCoveragePct, 0);
});

test('the per-channel tolerance is EXCLUSIVE at the boundary', () => {
  // The predicate is `delta > tolerance`, so a pixel exactly `tolerance`
  // away from the background is still background. That boundary is the
  // whole reason the constant is 8: it absorbs compositor rounding without
  // counting it as ink. Slipping to `>=` would let quantisation noise on a
  // genuinely blank capture push coverage over 5 % and disarm the gate on
  // exactly the captures it exists for; slipping the other way would call
  // faint real content blank.
  const nudged = (delta) => {
    const im = blank();
    im.data[0] = CANONICAL_BG.r + delta;      // one pixel, red channel only
    return im;
  };
  const t = SEMANTIC_PRESENCE_TOLERANCE;
  assert.equal(computeSemanticPresence(nudged(t), blank()).aCoveragePct, 0, 'exactly tolerance is still background');
  assert.equal(computeSemanticPresence(nudged(t + 1), blank()).aCoveragePct, 1, 'one past tolerance is ink');
});

test('a caller-supplied background is honoured — the TITAN white canvas', () => {
  // tools/titan/inject-wpt-block.mjs passes WPT_CANVAS_BG (white) because
  // its corpus renders on a white page. If a refactor dropped the third
  // parameter, or ignored it in the inner loop, a blank WHITE capture would
  // measure 100 % coverage against the dark default — the no-content gate
  // would never fire anywhere in the WPT corpus, and every blank-vs-blank
  // WPT pair would keep whatever SSIM its matching background earned it.
  // The failure is silent in both directions, which is why both are pinned.
  const white = img(10, 10, () => [255, 255, 255]);
  assert.equal(computeSemanticPresence(white, white).aCoveragePct, 100,
    'white is 100 % ink against the dark default');
  assert.equal(computeSemanticPresence(white, white, { r: 255, g: 255, b: 255 }).aCoveragePct, 0,
    'white is 0 % ink against a white canvas');
});

test('a shape mismatch returns null, never zero coverage', () => {
  // Zero would read as "both sides blank" and flip a size-mismatched pair
  // to `no-content` — hiding a real size bug behind a label that says the
  // comparison was meaningless. Null makes the classifier fall through to
  // its normal tree, which is the documented contract.
  assert.equal(computeSemanticPresence(img(4, 1, () => BG), img(5, 1, () => BG)), null);
});

test('the echoed threshold and tolerance describe the run that produced them', () => {
  // The manifest carries these two scalars so a later reviewer can tell why
  // a pair was or was not flagged without re-grepping the code. If they
  // ever stop tracking the constants actually used, the manifest documents
  // a gate that did not run.
  const p = computeSemanticPresence(blank(), blank());
  assert.equal(p.threshold, SEMANTIC_PRESENCE_EMPTY_PCT);
  assert.equal(p.tolerance, SEMANTIC_PRESENCE_TOLERANCE);
});

// ── The consumer: the classifier's no-content flip point ────────────────────

test("the classifier's no-content flip point IS SEMANTIC_PRESENCE_EMPTY_PCT", () => {
  // compare-screenshots-metrics.mjs exports SEMANTIC_PRESENCE_EMPTY_PCT
  // saying it exists "so the classifier and the metric helper share one
  // threshold value" — but classify-divergence.mjs defines its own private
  // `NO_CONTENT_COVERAGE_PCT = 5` and never imports it. They are two
  // independent literals that happen to agree.
  //
  // So this test measures the flip BEHAVIOURALLY against the exported
  // constant instead of reading either literal: move one side's coverage a
  // thousandth below the constant and the label must be `no-content`; sit
  // exactly on it and it must not be. If either literal is edited alone,
  // the manifest's echoed `threshold` starts lying about the gate that
  // fired, and this goes red.
  const quiet = {
    ssim: 0.99, pixelMismatchedCount: 0, pixelMismatchedPct: 0,
    edgeSsim: 1, pHash: { hammingDistance: 0 }, labDeltaE: { mean: 0, max: 0, p95: 0 },
  };
  const at = (cov) => classifyDivergence({
    ...quiet,
    semanticPresence: { aCoveragePct: cov, bCoveragePct: 50, threshold: 5, tolerance: 8 },
  });
  assert.equal(at(SEMANTIC_PRESENCE_EMPTY_PCT - 0.001), 'no-content', 'just below must flip');
  assert.notEqual(at(SEMANTIC_PRESENCE_EMPTY_PCT), 'no-content', 'the threshold itself must pass');
});

// ── Integration control: does the pad sentinel reach the METRICS? ───────────

test('an under-sized capture is visible to ΔE and to coverage, not just to pixelmatch', async () => {
  // pad-canvas.test.mjs proves the magenta sentinel changes raw pixels. It
  // does NOT prove any metric sees it — and the metrics are what the gate
  // reads. This is the end-to-end control for the failure mode the sentinel
  // was introduced for: platform B renders the component at half height.
  //
  // A: 10×10, ink in the top half, background below.
  // B: 10×5, the ink half only — the trailing rows simply missing.
  //
  // Padded through the SHIPPING padToCanvas, B's missing half becomes
  // magenta, and the known answers are:
  //   ΔE95   49.707  (magenta vs #1A1A2E)  → far above the gate's 5.0
  //   cover  A 50 %  ·  B 100 %            → a 50-point split
  //
  // With the old #1A1A2E fill both readings were ΔE95 0.000 and 50 % / 50 %
  // — a perfect score for a component that lost half its height.
  //
  // It is also the control that catches an un-awaited padToCanvas: a
  // Promise reaching a metric helper throws inside its try/catch and comes
  // back as null, so asserting a known NUMBER here fails where "is it in
  // range?" would have quietly accepted the null.
  const A = img(10, 10, (x, y) => (y < 5 ? INK : BG));
  const Bshort = new PNG({ width: 10, height: 5 });
  for (let i = 0; i < Bshort.data.length; i += 4) {
    Bshort.data[i] = INK[0]; Bshort.data[i + 1] = INK[1]; Bshort.data[i + 2] = INK[2]; Bshort.data[i + 3] = 255;
  }
  const B = await padToCanvas(Bshort, 10, 10);
  assert.equal(B.height, 10, 'sanity: the pad actually happened');
  assert.equal(B.data[(5 * 10) * 4], PAD_SENTINEL.r, 'sanity: the padded band is the sentinel');

  const de = computeLabDeltaE(A, B, 1);
  assert.equal(de.p95, 49.707, 'the lost half must dominate ΔE95, not read as 0');

  const p = computeSemanticPresence(A, B);
  assert.equal(p.aCoveragePct, 50);
  assert.equal(p.bCoveragePct, 100, 'the sentinel counts as ink, so the sides cannot look alike');
});

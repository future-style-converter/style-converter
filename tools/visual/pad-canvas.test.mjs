#!/usr/bin/env node
// Unit tests for tools/visual/pad-canvas.mjs.
//
// These pin the regression that motivated the module: when the pad colour
// equalled the capture background (#1A1A2E), a platform that rendered a
// component TOO SHORT had its shortfall filled with exactly the colour the
// taller platform was already showing there — so "missing trailing content"
// scored a perfect 0.00 % pixel diff. The `under-sized output is visible`
// test below fails if anyone reverts the sentinel.
//
// Run via `node --test tools/visual/pad-canvas.test.mjs`; the smoke.sh glob
// `node --test tools/visual/*.test.mjs` picks it up automatically.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { PNG } from 'pngjs';

import { padToCanvas, PAD_SENTINEL } from './pad-canvas.mjs';

/** The capture background every harness canvas paints — #1A1A2E. */
const CAPTURE_BG = { r: 0x1A, g: 0x1A, b: 0x2E };

/** Solid-colour PNG of the given size. */
function solid(w, h, { r, g, b }) {
  const png = new PNG({ width: w, height: h });
  for (let i = 0; i < png.data.length; i += 4) {
    png.data[i] = r; png.data[i + 1] = g; png.data[i + 2] = b; png.data[i + 3] = 255;
  }
  return png;
}

/** Read pixel (x, y) as {r,g,b,a}. */
function px(img, x, y) {
  const i = (img.width * y + x) * 4;
  return { r: img.data[i], g: img.data[i + 1], b: img.data[i + 2], a: img.data[i + 3] };
}

/** Count pixels differing between two same-sized images. */
function countDiff(a, b) {
  let n = 0;
  for (let i = 0; i < a.data.length; i += 4) {
    if (a.data[i] !== b.data[i] || a.data[i + 1] !== b.data[i + 1] || a.data[i + 2] !== b.data[i + 2]) n += 1;
  }
  return n;
}

// ── Identity ────────────────────────────────────────────────────────────────

test('padToCanvas: exact fit returns the SAME object, untouched', async () => {
  // This early-return is why the sentinel change is a no-op for equal-sized
  // comparisons — i.e. the overwhelming majority of the 327 pairs.
  const img = solid(8, 8, CAPTURE_BG);
  const out = await padToCanvas(img, 8, 8);
  assert.equal(out, img, 'must be referentially identical, not a re-encode');
});

// ── The regression this module exists for ───────────────────────────────────

test('under-sized output is VISIBLE against a taller peer', async () => {
  // Platform A renders the component at full height with a background tail.
  const tall = solid(10, 20, CAPTURE_BG);
  // Platform B drops the trailing rows entirely — the bug we must catch.
  const short = solid(10, 10, CAPTURE_BG);

  const padded = await padToCanvas(short, 10, 20);
  const differing = countDiff(tall, padded);

  // With the old #1A1A2E fill this was exactly 0 — a perfect score for a
  // component that lost half its height.
  assert.equal(differing, 100, 'all 10×10 padded pixels must register as different');
  assert.ok(differing > 0, 'under-size must never score as identical');
});

test('padded region is the sentinel, not the capture background', async () => {
  const img = await padToCanvas(solid(4, 2, CAPTURE_BG), 4, 4);
  const filled = px(img, 0, 3);                        // inside the padded band
  assert.deepEqual(
    { r: filled.r, g: filled.g, b: filled.b },
    { r: PAD_SENTINEL.r, g: PAD_SENTINEL.g, b: PAD_SENTINEL.b },
  );
  assert.notDeepEqual(
    { r: filled.r, g: filled.g, b: filled.b },
    CAPTURE_BG,
    'the fill must never equal the capture background — that was the bug',
  );
});

test('original pixels keep their coordinates (pad, never stretch)', async () => {
  // Anchoring top-left is what keeps borders and shadows aligned; a resample
  // here would quietly corrupt every geometry conclusion the harness draws.
  const src = solid(4, 2, { r: 12, g: 34, b: 56 });
  const out = await padToCanvas(src, 8, 6);
  assert.equal(out.width, 8);
  assert.equal(out.height, 6);
  for (let y = 0; y < 2; y += 1) {
    for (let x = 0; x < 4; x += 1) {
      assert.deepEqual(px(out, x, y), { r: 12, g: 34, b: 56, a: 255 }, `pixel ${x},${y} moved`);
    }
  }
});

test('padding grows on both axes independently', async () => {
  const out = await padToCanvas(solid(3, 5, CAPTURE_BG), 7, 5);   // width only
  assert.equal(out.width, 7);
  assert.equal(out.height, 5);
  assert.equal(px(out, 6, 0).r, PAD_SENTINEL.r, 'right band filled');
  assert.equal(px(out, 0, 0).r, CAPTURE_BG.r, 'content untouched');
});

test('two equally-padded images still compare as identical', async () => {
  // The baseline gate pads BOTH sides to the same canvas. If the sentinel
  // leaked asymmetry into that path it would fabricate regressions, so pin it.
  const a = await padToCanvas(solid(6, 4, CAPTURE_BG), 6, 9);
  const b = await padToCanvas(solid(6, 4, CAPTURE_BG), 6, 9);
  assert.equal(countDiff(a, b), 0, 'identical inputs must stay identical after padding');
});

test('PAD_SENTINEL is frozen and opaque', async () => {
  // A translucent sentinel would blend toward the background and re-open the
  // hole; freezing stops a caller mutating the shared object via sharp.
  assert.ok(Object.isFrozen(PAD_SENTINEL));
  assert.equal(PAD_SENTINEL.alpha, 1);
});

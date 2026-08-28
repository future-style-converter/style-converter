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

// ── Contract gaps the tests above leave open ────────────────────────────────

test('an OVER-sized image is returned untouched, never cropped or resampled', async () => {
  // `extend` only grows, so the negative deltas are clamped to 0 and the
  // image comes back at its ORIGINAL size — not the requested canvas. That
  // is the right choice (cropping would destroy content, resizing would
  // destroy pixel alignment and every geometry conclusion drawn from it),
  // but it means the caller owns computing a canvas that is the max over
  // BOTH sides. compare-screenshots.mjs:800 does exactly that for the
  // baseline pair; a caller that forgets gets two differently-shaped
  // buffers, and every metric helper then bails to null rather than
  // reporting a wrong number.
  //
  // Pinning it here so a future "just make it always return W×H" — via
  // sharp's `resize` — cannot land quietly: that would stretch one platform
  // onto another's canvas and make a size divergence score as a match,
  // which is the exact failure the pad sentinel exists to expose.
  const out = await padToCanvas(solid(20, 20, CAPTURE_BG), 10, 10);
  assert.equal(out.width, 20, 'must not crop to the requested canvas');
  assert.equal(out.height, 20, 'must not resample to the requested canvas');
});

test('every live padToCanvas call site awaits it', async () => {
  // padToCanvas is async, and an un-awaited call hands a Promise to a
  // metric helper. Every helper is wrapped in try/catch and returns null on
  // a throw, so the symptom is not a stack trace — it is a whole run of
  // blank or NaN metric cells that looks like a decode problem. That
  // happened once already during this wave's measurement work.
  //
  // A control row with a known answer is the primary defence (see
  // semantic-presence.test.mjs). This is the cheap structural backstop: it
  // fails the moment a new call site is added without `await`, instead of
  // waiting for someone to notice the numbers are missing.
  const { readdirSync, statSync, readFileSync } = await import('node:fs');
  const { join, resolve, dirname } = await import('node:path');
  const { fileURLToPath } = await import('node:url');
  const toolsDir = resolve(dirname(fileURLToPath(import.meta.url)), '..');

  const offenders = [];
  const walk = (dir) => {
    for (const entry of readdirSync(dir)) {
      if (entry === 'node_modules') continue;                 // vendored code is not ours
      const p = join(dir, entry);
      if (statSync(p).isDirectory()) { walk(p); continue; }
      // Only non-test source: tests legitimately reference the name in prose.
      if (!entry.endsWith('.mjs') || entry.endsWith('.test.mjs')) continue;
      readFileSync(p, 'utf8').split('\n').forEach((line, i) => {
        if (!/padToCanvas\s*\(/.test(line)) return;           // not a call
        if (/function\s+padToCanvas/.test(line)) return;      // the definition itself
        if (/^\s*(\/\/|\*)/.test(line)) return;               // a comment mentioning it
        if (!/await\s+padToCanvas\s*\(/.test(line)) offenders.push(`${p}:${i + 1} ${line.trim()}`);
      });
    }
  };
  walk(toolsDir);
  assert.deepEqual(offenders, [], 'un-awaited padToCanvas call site(s)');
});

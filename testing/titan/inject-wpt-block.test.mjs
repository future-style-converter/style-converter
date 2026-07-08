#!/usr/bin/env node
//
// Unit tests for testing/titan/inject-wpt-block.mjs's pure helpers.
//
// Coverage targets the swarm-002 RC2 stitch-before-diff fix: the legacy
// path compared only the first per-component capture against the full
// browser-ref, producing false structural-divergence on multi-element
// reftests. The new `stitchPngsVertically` helper composes all per-test
// captures into a single PNG before the diff runs.
//
// We use Node's built-in test runner (pattern from
// testing/titan/extract-fixture.test.mjs) so the smoke harness picks
// these up via `node --test testing/titan/*.test.mjs`.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { promises as fs } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { PNG } from 'pngjs';

import { stitchPngsVertically, safe } from './inject-wpt-block.mjs';

// ── Helpers ────────────────────────────────────────────────────────────────

/** Write a solid-colour PNG of the given dimensions to a tmp path. Returns
 *  the path. Uses pngjs directly so tests don't depend on sharp / external
 *  fixtures. */
async function writePng(dir, name, w, h, rgba) {
  const png = new PNG({ width: w, height: h });
  for (let i = 0; i < png.data.length; i += 4) {
    png.data[i]     = rgba[0];
    png.data[i + 1] = rgba[1];
    png.data[i + 2] = rgba[2];
    png.data[i + 3] = rgba[3] ?? 255;
  }
  const path = join(dir, name);
  await fs.writeFile(path, PNG.sync.write(png));
  return path;
}

/** Read a PNG and return its width / height / first-row pixel for
 *  spot-check assertions. */
async function readPng(path) {
  const buf = await fs.readFile(path);
  const png = PNG.sync.read(buf);
  return {
    width: png.width,
    height: png.height,
    pixelAt: (x, y) => {
      const i = (y * png.width + x) * 4;
      return [png.data[i], png.data[i + 1], png.data[i + 2], png.data[i + 3]];
    },
  };
}

/** Fresh per-test scratch dir under the OS tmpdir. */
async function tmpDir(label) {
  const d = await fs.mkdtemp(join(tmpdir(), `inject-wpt-test-${label}-`));
  return d;
}

// ── stitchPngsVertically ────────────────────────────────────────────────────

test('stitchPngsVertically: single input returns the same path (legacy compat)', async () => {
  // When a test produces only one per-component capture, stitching is a
  // no-op — we return the input path unchanged so the diff path matches
  // the pre-RC2 baseline byte-for-byte. This is the dominant case for
  // single-element reftests (clip-001, the bulk of bucket-A).
  const dir = await tmpDir('single');
  const a = await writePng(dir, 'a.png', 100, 50, [255, 0, 0, 255]);
  const out = await stitchPngsVertically([a], join(dir, '_cache'), 'k');
  assert.equal(out, a);
});

test('stitchPngsVertically: stitches 3 inputs into a single tall PNG', async () => {
  // Three 100x50 captures should stitch into a 100x150 composed PNG.
  // Matches the clip-002 multi-component-test geometry (3 outer boxes,
  // each in its own per-component capture).
  const dir = await tmpDir('stitch3');
  const cacheDir = join(dir, '_stitched');
  const a = await writePng(dir, 'a.png', 100, 50, [255, 0, 0, 255]);
  const b = await writePng(dir, 'b.png', 100, 50, [0, 255, 0, 255]);
  const c = await writePng(dir, 'c.png', 100, 50, [0, 0, 255, 255]);

  const outPath = await stitchPngsVertically([a, b, c], cacheDir, 'clip-002');
  assert.ok(outPath.endsWith('clip-002.png'), `expected cache key in path, got ${outPath}`);

  const out = await readPng(outPath);
  assert.equal(out.width, 100);
  assert.equal(out.height, 150);
  // First row of each input should land at y=0, y=50, y=100 respectively.
  assert.deepEqual(out.pixelAt(0, 0),   [255, 0, 0, 255], 'red band');
  assert.deepEqual(out.pixelAt(0, 50),  [0, 255, 0, 255], 'green band');
  assert.deepEqual(out.pixelAt(0, 100), [0, 0, 255, 255], 'blue band');
});

test('stitchPngsVertically: variable-width inputs pad to canvas with #1A1A2E bg', async () => {
  // When per-component captures have differing widths (rare but possible
  // for mixed inline/block fixtures), the composed canvas should use
  // max(input widths) and fill the gap with the canonical #1A1A2E
  // background — matches CaptureCanvas's own background so the seam is
  // invisible in the eventual diff.
  const dir = await tmpDir('padwidth');
  const cacheDir = join(dir, '_stitched');
  const wide  = await writePng(dir, 'wide.png',  200, 30, [255, 255, 255, 255]);
  const narrow = await writePng(dir, 'narrow.png', 100, 30, [0, 0, 0, 255]);

  const outPath = await stitchPngsVertically([wide, narrow], cacheDir, 'mix');
  const out = await readPng(outPath);
  assert.equal(out.width, 200, 'canvas width = max input width');
  assert.equal(out.height, 60, 'canvas height = sum of input heights');
  // Wide input fills row 0 completely → white pixel at x=150 y=0.
  assert.deepEqual(out.pixelAt(150, 0), [255, 255, 255, 255]);
  // Narrow input ends at x=99 on row 30 → gap at x=150 y=30 is the
  // canvas background, which is the canonical #1A1A2E (0x1A,0x1A,0x2E).
  assert.deepEqual(out.pixelAt(150, 30), [0x1A, 0x1A, 0x2E, 255]);
  // Narrow row body remains black at x=0 y=30.
  assert.deepEqual(out.pixelAt(0, 30), [0, 0, 0, 255]);
});

test('stitchPngsVertically: writes the composed PNG to the cache dir', async () => {
  // The cache dir is created on demand and the output file name uses
  // the supplied cacheKey verbatim so subsequent inject re-runs can
  // re-use the stitched PNG without recomputing.
  const dir = await tmpDir('cache');
  const cacheDir = join(dir, '_stitched');
  const a = await writePng(dir, 'a.png', 10, 10, [10, 20, 30, 255]);
  const b = await writePng(dir, 'b.png', 10, 10, [40, 50, 60, 255]);
  const out = await stitchPngsVertically([a, b], cacheDir, 'my-cache-key');
  assert.equal(out, join(cacheDir, 'my-cache-key.png'));
  // The cache directory should now exist and contain the stitched PNG.
  const stat = await fs.stat(out);
  assert.ok(stat.size > 0, 'stitched PNG should be non-empty');
});

// ── safe() — sanitisation parity with the platform capture loops ───────────

test('safe(): replaces non-safe characters with underscore', () => {
  // Mirrors the iOS / Android / web capture loop's filename rule. Used
  // to glob per-component captures back out of the screenshot dir.
  assert.equal(safe('wpt/css-overflow/clip-002.html'), 'wpt_css-overflow_clip-002.html');
  assert.equal(safe('plain__component'), 'plain__component');
  assert.equal(safe('a b c'), 'a_b_c');
});

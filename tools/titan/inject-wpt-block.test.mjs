#!/usr/bin/env node
//
// Unit tests for tools/titan/inject-wpt-block.mjs's pure helpers.
//
// Coverage targets the swarm-002 RC2 stitch-before-diff fix: the legacy
// path compared only the first per-component capture against the full
// browser-ref, producing false structural-divergence on multi-element
// reftests. The new `stitchPngsVertically` helper composes all per-test
// captures into a single PNG before the diff runs.
//
// We use Node's built-in test runner (pattern from
// tools/titan/extract-fixture.test.mjs) so the smoke harness picks
// these up via `node --test tools/titan/*.test.mjs`.

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

// ── stale-path defect 1 pin (R5 restructure) ───────────────────────────────
//
// The default --web-dir must point at the LIVE harness capture dir. The
// pre-R5 `testing/web/screenshots` default silently yielded zero web
// captures, nulling every browserRef diff in run-titan.sh smoke runs. The
// default lives inside main()'s argument plumbing (not exported), so this
// pin scans the module source for the join() segments.

test('default webDir points at apps/web-harness/screenshots (defect 1 stays fixed)', async () => {
  const src = await fs.readFile(new URL('./inject-wpt-block.mjs', import.meta.url), 'utf8');
  assert.match(src, /'apps',\s*'web-harness',\s*'screenshots'/, 'live default missing');
  assert.doesNotMatch(src, /'testing',\s*'web'/, 'pre-R5 testing/web path resurfaced');
});

// ── diffPlatformVsRef — the Phase-4 per-platform ref-diff helper ───────────
//
// One helper serves web/ios/android because all three platforms write
// identical `<idx>_<safeKey>.png` filenames into their own capture dir.
// These tests pin the matching, the null contract (no captures → null,
// NOT an error object), and that a real match produces metrics + the
// stitchedComponents count.

import { diffPlatformVsRef } from './inject-wpt-block.mjs';

test('diffPlatformVsRef: returns null when the platform dir is missing or empty', async () => {
  // Missing dir — the web-only smoke's ios/android case.
  const none = await diffPlatformVsRef({
    platformDir: '/nonexistent/dir', matchingKeys: ['a'], refPng: '/nope.png', fuzzy: null, cacheKey: 'k',
  });
  assert.equal(none, null);
  // Present-but-unmatched dir (stale unrelated captures must not diff).
  const dir = await tmpDir('dppr-empty');
  await writePng(dir, '001_other_component.png', 4, 4, [1, 2, 3, 255]);
  const unmatched = await diffPlatformVsRef({
    platformDir: dir, matchingKeys: ['wpt__css-color__t1__0'], refPng: '/nope.png', fuzzy: null, cacheKey: 'k',
  });
  assert.equal(unmatched, null);
});

test('diffPlatformVsRef: matched captures produce metrics with stitchedComponents', async () => {
  const dir = await tmpDir('dppr-match');
  const refDir = await tmpDir('dppr-ref');
  // Two components for one test, plus the browser-ref they diff against.
  const key1 = 'wpt__css-color__t-001.html__0';
  const key2 = 'wpt__css-color__t-001.html__1';
  // 32px squares — ssim.js's windowed 'fast' path needs ≥11px per side,
  // so tiny 8px fixtures silently yield ssim:null (caught + nulled).
  await writePng(dir, `000_${safe(key1)}.png`, 32, 32, [0, 128, 0, 255]);
  await writePng(dir, `001_${safe(key2)}.png`, 32, 32, [0, 128, 0, 255]);
  const refPng = await writePng(refDir, 'ref.png', 32, 64, [0, 128, 0, 255]);
  const diff = await diffPlatformVsRef({
    platformDir: dir, matchingKeys: [key1, key2], refPng, fuzzy: null, cacheKey: 't-001',
  });
  assert.ok(diff, 'expected a diff object');
  assert.equal(diff.stitchedComponents, 2);
  // Identical solid-green composite vs ref → perfect scores.
  assert.equal(diff.ssim, 1);
});

// ── diffComposedWebVsRef — the WPT COMPOSED web-ref helper ──────────────────
//
// The composed web capture writes ONE PNG per test named `<safe(testKey)>.png`
// (no per-component stitch). These tests pin: the null contract when no
// composed PNG exists (caller falls back to stitch), and that a present
// composed PNG diffs DIRECTLY against the ref (no stitch) with the
// `composed:true` provenance marker.

import { diffComposedWebVsRef } from './inject-wpt-block.mjs';

test('diffComposedWebVsRef: returns null when no composed PNG exists', async () => {
  // Empty dir → the caller must fall back to the per-component stitch path.
  const dir = await tmpDir('dcwr-none');
  const none = await diffComposedWebVsRef({
    webDir: dir, testKey: 'wpt__css-color__t-002', refPng: '/nope.png', fuzzy: null,
  });
  assert.equal(none, null);
  // Missing webDir also yields null (defensive — web-only guard).
  const noDir = await diffComposedWebVsRef({
    webDir: null, testKey: 'wpt__css-color__t-002', refPng: '/nope.png', fuzzy: null,
  });
  assert.equal(noDir, null);
});

test('diffComposedWebVsRef: composed PNG diffs directly vs ref (no stitch)', async () => {
  const dir = await tmpDir('dcwr-match');
  const refDir = await tmpDir('dcwr-ref');
  const testKey = 'wpt__css-color__t-003';
  // ONE composed PNG named for the test key, plus a same-size solid ref.
  // 40px squares clear ssim.js's ≥11px window requirement.
  await writePng(dir, `${safe(testKey)}.png`, 40, 40, [0, 128, 0, 255]);
  const refPng = await writePng(refDir, 'ref.png', 40, 40, [0, 128, 0, 255]);
  const diff = await diffComposedWebVsRef({ webDir: dir, testKey, refPng, fuzzy: null });
  assert.ok(diff, 'expected a diff object');
  assert.equal(diff.composed, true, 'composed provenance marker');
  // No stitchedComponents key — this path never stitches.
  assert.equal(diff.stitchedComponents, undefined);
  // Identical solid-green composite vs ref → perfect SSIM.
  assert.equal(diff.ssim, 1);
});

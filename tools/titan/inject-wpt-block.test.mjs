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

import {
  stitchPngsVertically, safe, checkFuzzyMatch, computeWptPass,
  computeColorComposite, isColorDivergent, COLOR_DIVERGENT_KL_THRESHOLD,
  applyNaScoreGate,
} from './inject-wpt-block.mjs';

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

test('stitchPngsVertically: variable-width inputs pad to canvas with the WHITE WPT bg', async () => {
  // When per-component captures have differing widths (rare but possible
  // for mixed inline/block fixtures), the composed canvas should use
  // max(input widths) and fill the gap with the WHITE WPT canvas
  // background (corpus-v4 boundary — matches the platforms' WPT capture
  // canvases + the white browser-ref) so the seam is invisible in the
  // eventual diff.
  const dir = await tmpDir('padwidth');
  const cacheDir = join(dir, '_stitched');
  // Red wide band (deliberately NOT white so the fill pixel and the
  // background pixel below are distinguishable in the assertions).
  const wide  = await writePng(dir, 'wide.png',  200, 30, [255, 0, 0, 255]);
  const narrow = await writePng(dir, 'narrow.png', 100, 30, [0, 0, 0, 255]);

  const outPath = await stitchPngsVertically([wide, narrow], cacheDir, 'mix');
  const out = await readPng(outPath);
  assert.equal(out.width, 200, 'canvas width = max input width');
  assert.equal(out.height, 60, 'canvas height = sum of input heights');
  // Wide input fills row 0 completely → red pixel at x=150 y=0.
  assert.deepEqual(out.pixelAt(150, 0), [255, 0, 0, 255]);
  // Narrow input ends at x=99 on row 30 → gap at x=150 y=30 is the
  // canvas background: WHITE since corpus-v4 (was #1A1A2E through v3).
  assert.deepEqual(out.pixelAt(150, 30), [255, 255, 255, 255]);
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

// ── wptPass — the WPT-native pass verdict (ssim ≥ 0.95 OR within fuzzy) ─────
//
// checkFuzzyMatch decides whether a browser-ref diff fits the test's declared
// <meta fuzzy> tolerance; computeWptPass turns "ssim clears 0.95 OR fuzzy fits"
// into the honest WPT reftest verdict WITHOUT overwriting the raw ssim.

test('computeWptPass: ssim ≥ 0.95 passes regardless of fuzzy', () => {
  assert.equal(computeWptPass(0.97, null), true);   // clean SSIM pass, no fuzzy tag
  assert.equal(computeWptPass(0.95, false), true);  // exactly at the bar
  assert.equal(computeWptPass(0.99, true), true);   // both paths true
});

test('computeWptPass: a fuzzy-within-tolerance pair passes even though ssim < 0.95', () => {
  // The whole point: a test declaring a fuzzy tolerance that the diff fits
  // is a WPT PASS even when perceptual SSIM sits below the strict 0.95 bar.
  assert.equal(computeWptPass(0.80, true), true);
});

test('computeWptPass: a real failure (low ssim, no/failed fuzzy) is false', () => {
  assert.equal(computeWptPass(0.80, false), false); // fuzzy present but exceeded
  assert.equal(computeWptPass(0.80, null), false);  // no fuzzy tag at all
  assert.equal(computeWptPass(null, null), false);  // ssim uncomputable, no fuzzy
});

test('checkFuzzyMatch → computeWptPass: within-tolerance fuzzy flips a sub-0.95 ssim to pass', () => {
  // Fuzzy shape from extract-fixture.mjs: { maxDifference:{min,max}, totalPixels:{min,max} }.
  // A diff of 40 mismatched pixels and a max ΔE of 3 fits inside 50px / ΔE 5.
  const fuzzy = { maxDifference: { min: 0, max: 5 }, totalPixels: { min: 0, max: 50 } };
  const metrics = { ssim: 0.82, pixelMismatchedCount: 40, labDeltaE: { max: 3 } };
  const fuzzyMatch = checkFuzzyMatch(metrics, fuzzy);
  assert.equal(fuzzyMatch, true);
  assert.equal(computeWptPass(metrics.ssim, fuzzyMatch), true);

  // A diff that BUSTS the pixel budget is not a pass (ssim still < 0.95).
  const overBudget = { ssim: 0.82, pixelMismatchedCount: 999, labDeltaE: { max: 3 } };
  const noMatch = checkFuzzyMatch(overBudget, fuzzy);
  assert.equal(noMatch, false);
  assert.equal(computeWptPass(overBudget.ssim, noMatch), false);
});

// ── color-aware triage signals (TITAN-WHITE lane) ──────────────────────────
//
// Raw SSIM is near color-blind: wave-9 measured a FULL red→green repaint
// moving SSIM by 0.0001 (docs/STATUS.md). Every browser-ref diff now
// carries colorComposite (= ssim − min(0.2, labDeltaE.mean/50)) and
// colorDivergent (max-channel histogramKL over the calibrated threshold).
// Neither feeds wptPass — the recorded-value pins below hold both formulas
// AND the calibration against the wave-9 measurements.

test('computeColorComposite: composite = ssim − min(0.2, labDeltaE.mean/50)', () => {
  // Mid-range: mean ΔE 5 → penalty 0.1.
  assert.equal(computeColorComposite(0.9, { mean: 5 }), 0.8);
  // Penalty cap: a saturated full-image hue error (mean 50 → raw 1.0)
  // clamps at 0.2 so structure still dominates the composite.
  assert.equal(computeColorComposite(0.99, { mean: 50 }), 0.79);
  assert.equal(computeColorComposite(0.99, { mean: 500 }), 0.79);
  // AA-noise color error is a sub-0.01 nudge, not a penalty.
  assert.equal(computeColorComposite(0.97, { mean: 0.4 }), 0.962);
});

test('computeColorComposite: wave-9 red→green case is visibly penalised', () => {
  // Recorded values (tools/titan/runs/wave9-gate/sections/css-flexbox
  // manifest, iOS-web rows): ssim 0.9252, labDeltaE.mean 4.206 — the pair
  // whose full hue swap SSIM alone shrugged at. The composite drops it by
  // mean/50 ≈ 0.084: visible in any triage sort.
  assert.equal(computeColorComposite(0.9252, { mean: 4.206 }), 0.8411);
});

test('computeColorComposite: degraded inputs degrade loudly-but-safely', () => {
  // No ssim → no base score; the diff is already error-shaped.
  assert.equal(computeColorComposite(null, { mean: 5 }), null);
  // No labDeltaE (all-transparent pair / metric error) → color-UNKNOWN is
  // not color-PENALISED: composite falls back to the raw ssim.
  assert.equal(computeColorComposite(0.97, null), 0.97);
  assert.equal(computeColorComposite(0.97, {}), 0.97);
});

test('colorDivergent threshold catches the measured red→green KL but not AA noise', () => {
  // Threshold pin: 0.1 — ≥5× the measured AA ceiling, 2.4× under the
  // measured hue swap (margins documented at the constant).
  assert.equal(COLOR_DIVERGENT_KL_THRESHOLD, 0.1);
  // The wave-9 measured red→green repaint (css-flexbox manifest, iOS-web
  // rows): histogramKL {r:0.0609, g:0.2396, b:0.0205} — MUST stamp.
  assert.equal(isColorDivergent({ r: 0.0609, g: 0.2396, b: 0.0205 }), true);
  // Calibration negatives from the wave9c-gate css-break + wave9-gate
  // css-flexbox manifests: the largest max-channel KL among AA-noise /
  // correctly-rendering passing pairs — 0.0197 (css-break) and 0.0024
  // (css-flexbox). Neither may stamp.
  assert.equal(isColorDivergent({ r: 0.0197, g: 0.0102, b: 0.0088 }), false);
  assert.equal(isColorDivergent({ r: 0.0024, g: 0.0023, b: 0.0012 }), false);
});

test('diffWebVsRef wires both color signals onto every browser-ref diff', async () => {
  // End-to-end wiring pin — the wave-9 blindness case in miniature: a flat
  // red capture diffed against a flat green ref. Raw SSIM barely notices
  // (≈0.9997, the measured Δ0.0001 phenomenon), so the diff MUST carry the
  // color signals that do: a composite dragged down by the capped ΔE
  // penalty and a colorDivergent stamp from the saturated histogram KL.
  const dir = await tmpDir('colorwiring');
  const red   = await writePng(dir, 'red.png',   20, 20, [255, 0, 0, 255]);
  const green = await writePng(dir, 'green.png', 20, 20, [0, 128, 0, 255]);
  const { diffWebVsRef } = await import('./inject-wpt-block.mjs');
  const d = await diffWebVsRef(red, green);
  assert.ok(typeof d.ssim === 'number' && d.ssim > 0.99, `SSIM should be color-blind here (got ${d.ssim})`);
  // The composite is the ssim minus the CAPPED penalty (flat saturated hue
  // swap → mean ΔE ≫ 10 → the 0.2 cap), so it sits ~0.2 under the ssim.
  assert.ok(typeof d.colorComposite === 'number', 'colorComposite missing from the diff');
  assert.ok(Math.abs(d.ssim - d.colorComposite - 0.2) < 1e-9, `cap penalty expected (ssim ${d.ssim} → composite ${d.colorComposite})`);
  // …and the histogram stamp fires.
  assert.equal(d.colorDivergent, true, 'colorDivergent must stamp a full hue swap');
  // wptPass itself must stay the raw-SSIM criterion — the color signals
  // are triage-only (the pass series definition is frozen).
  assert.equal(computeWptPass(d.ssim, null), true);
});

test('colorDivergent: any single channel over threshold stamps; absent KL never does', () => {
  // Per-channel semantics — a hue swap can live in ONE channel.
  assert.equal(isColorDivergent({ r: 0.0, g: 0.0, b: 0.11 }), true);
  // Missing/degenerate metric → unknown, not divergent.
  assert.equal(isColorDivergent(null), false);
  assert.equal(isColorDivergent(undefined), false);
  assert.equal(isColorDivergent({}), false);
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
  // wptPass rides along the metric block (ssim ≥ 0.95 path here; no fuzzy).
  assert.equal(diff.wptPass, true);
  assert.equal(diff.wptFuzzyMatch, null); // fuzzy:null → no tolerance declared
});

// ── diffComposedVsRef — the WPT COMPOSED web-ref helper ──────────────────
//
// The composed web capture writes ONE PNG per test named `<safe(testKey)>.png`
// (no per-component stitch). These tests pin: the null contract when no
// composed PNG exists (caller falls back to stitch), and that a present
// composed PNG diffs DIRECTLY against the ref (no stitch) with the
// `composed:true` provenance marker.

import { diffComposedVsRef } from './inject-wpt-block.mjs';

test('diffComposedVsRef: returns null when no composed PNG exists', async () => {
  // Empty dir → the caller must fall back to the per-component stitch path.
  const dir = await tmpDir('dcwr-none');
  const none = await diffComposedVsRef({
    platformDir: dir, testKey: 'wpt__css-color__t-002', refPng: '/nope.png', fuzzy: null,
  });
  assert.equal(none, null);
  // Missing webDir also yields null (defensive — web-only guard).
  const noDir = await diffComposedVsRef({
    platformDir: null, testKey: 'wpt__css-color__t-002', refPng: '/nope.png', fuzzy: null,
  });
  assert.equal(noDir, null);
});

test('diffComposedVsRef: composed PNG diffs directly vs ref (no stitch)', async () => {
  const dir = await tmpDir('dcwr-match');
  const refDir = await tmpDir('dcwr-ref');
  const testKey = 'wpt__css-color__t-003';
  // ONE composed PNG named for the test key, plus a same-size solid ref.
  // 40px squares clear ssim.js's ≥11px window requirement.
  await writePng(dir, `${safe(testKey)}.png`, 40, 40, [0, 128, 0, 255]);
  const refPng = await writePng(refDir, 'ref.png', 40, 40, [0, 128, 0, 255]);
  const diff = await diffComposedVsRef({ platformDir: dir, testKey, refPng, fuzzy: null });
  assert.ok(diff, 'expected a diff object');
  assert.equal(diff.composed, true, 'composed provenance marker');
  // No stitchedComponents key — this path never stitches.
  assert.equal(diff.stitchedComponents, undefined);
  // Identical solid-green composite vs ref → perfect SSIM.
  assert.equal(diff.ssim, 1);
  // wptPass is recorded on the composed path too.
  assert.equal(diff.wptPass, true);
});

// ── wave-8: NA scoring gate ────────────────────────────────────────────────
//
// Corpus-honesty regression source: wpt-buckets.json tagged the css-break
// background-image-000/001/002 tests notApplicable (requires-bundled-asset),
// but the section scoring counted their browserRef diffs anyway — FIX-E only
// flipped the divergence LABEL while wptPass/ssim still fed mean-SSIM /
// passAt95 aggregations over `wpt.results[].browserRef.diffs`. The gate
// neutralises the SCORING fields on NA-tagged tests.

test('wave8: applyNaScoreGate neutralises wptPass and stamps scoreExcluded on NA tests', () => {
  const web = { ssim: 0.38, wptPass: false };
  const ios = { ssim: 0.14, wptPass: false };
  // requires-bundled-asset is the (only) harness-delivery tag the narrowed
  // gate excludes on; broad capability tags stay scored (see SCORE_EXCLUDED_TAGS).
  const isNa = applyNaScoreGate(['requires-bundled-asset'], [web, ios, null]);
  assert.equal(isNa, true);
  // Scoring fields neutralised…
  assert.equal(web.wptPass, null);
  assert.equal(web.scoreExcluded, true);
  assert.equal(ios.wptPass, null);
  assert.equal(ios.scoreExcluded, true);
  // …but raw diagnostic metrics stay intact for investigators.
  assert.equal(web.ssim, 0.38);
  assert.equal(ios.ssim, 0.14);
});

test('wave8: applyNaScoreGate is a no-op for untagged tests', () => {
  const web = { ssim: 0.99, wptPass: true };
  // undefined and [] both mean "not NA".
  assert.equal(applyNaScoreGate(undefined, [web]), false);
  assert.equal(applyNaScoreGate([], [web]), false);
  assert.equal(web.wptPass, true);
  assert.equal(web.scoreExcluded, undefined);
});

test('wave8: applyNaScoreGate skips error-shaped and absent diffs', () => {
  const err = { error: 'capture failed' };
  // Must not throw on nulls and must not stamp scoring fields onto error
  // records (they never carried any).
  assert.equal(applyNaScoreGate(['requires-bundled-asset'], [null, err, undefined]), true);
  // Broad capability tags do NOT exclude — the corpus keeps scoring them.
  assert.equal(applyNaScoreGate(['requires-fragmentation', 'requires-print-medium'], [null, err, undefined]), false);
  assert.equal(err.wptPass, undefined);
  assert.equal(err.scoreExcluded, undefined);
});

test('wave8: buildResults source carries the scoreEligible contract', async () => {
  // Source-scan pin: the per-test result object MUST expose scoreEligible
  // (the one boolean scoring paths filter on) wired to the NA gate, and the
  // gate MUST run over the three browser-ref diffs. Guards against a
  // refactor silently dropping the corpus-honesty gate.
  const src = await fs.readFile(new URL('./inject-wpt-block.mjs', import.meta.url), 'utf8');
  assert.match(src, /scoreEligible:\s*!isNa/,
    'results must expose scoreEligible: !isNa');
  assert.match(src, /applyNaScoreGate\(naTags,\s*\[webRefDiff,\s*iosRefDiff,\s*androidRefDiff\]\)/,
    'the NA gate must neutralise all three browser-ref diffs');
});

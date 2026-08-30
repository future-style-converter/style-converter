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
  // wave-35 lane B2 — the Rule 15 font-face wall's membership pin.
  FONT_FACE_WALL_TAGS,
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
  // WPT-NATIVE quantities (the 2026-08-29 rewire): the budget is checked
  // against the RAW differing-pixel count and the MAX PER-CHANNEL delta
  // (0–255) — not pixelmatch's thresholded count, not CIEDE2000. The old
  // substitution understated the diff (400 pixels at channel delta 135
  // read as pixelmatch count 0) and rescued out-of-budget pairs.
  const fuzzy = { maxDifference: { min: 0, max: 5 }, totalPixels: { min: 0, max: 50 } };
  const metrics = { ssim: 0.82, fuzzyDifferingPixels: 40, fuzzyMaxChannelDelta: 3 };
  const fuzzyMatch = checkFuzzyMatch(metrics, fuzzy);
  assert.equal(fuzzyMatch, true);
  assert.equal(computeWptPass(metrics.ssim, fuzzyMatch), true);

  // A diff that BUSTS the pixel budget is not a pass (ssim still < 0.95).
  const overBudget = { ssim: 0.82, fuzzyDifferingPixels: 999, fuzzyMaxChannelDelta: 3 };
  const noMatch = checkFuzzyMatch(overBudget, fuzzy);
  assert.equal(noMatch, false);

  // A diff that busts the PER-CHANNEL budget is not a pass either — the
  // half the old substitution could never enforce (labDeltaE.max is in
  // CIEDE2000 units, not channel units).
  const overDelta = { ssim: 0.82, fuzzyDifferingPixels: 40, fuzzyMaxChannelDelta: 6 };
  assert.equal(checkFuzzyMatch(overDelta, fuzzy), false);

  // A manifest predating the native quantities FAILS CLOSED: absent
  // evidence can never rescue a pair into a pass.
  const legacy = { ssim: 0.82, pixelMismatchedCount: 40, labDeltaE: { max: 3 } };
  assert.equal(checkFuzzyMatch(legacy, fuzzy), false);
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
  // wave-13: the gate call must ALSO thread the extractor's delivery record
  // (meta.lossyReasons) so the requires-bundled-asset exclusion stays
  // delivery-aware. wave-16: AND the post-load delivery stamp
  // (meta.postLoadExtracted, strict-true) so wall-tagged tests whose
  // post-script state post-load-extract.mjs delivered are re-scored — see
  // the wave-16 gate-interplay tests below. wave-20: AND the structure
  // stamp (meta.structureExtracted, strict-true) — the appendChild family's
  // delivery record, treated exactly like the state stamp.
  // wave-35: AND the font-face delivery stamp (meta.fontFacesDelivered,
  // strict-true) — the Rule 15 wall's own delivery record, threaded through
  // the identical keyMap channel. A gate call that drops it would silently
  // exclude every requires-font-face test even when the section's combined
  // document carries the file.
  assert.match(src, /applyNaScoreGate\(naTags,\s*\[webRefDiff,\s*iosRefDiff,\s*androidRefDiff\],\s*meta\.lossyReasons,\s*meta\.postLoadExtracted\s*===\s*true,\s*meta\.structureExtracted\s*===\s*true,\s*meta\.fontFacesDelivered\s*===\s*true\)/,
    'the NA gate must neutralise all three browser-ref diffs AND receive the delivery record AND both post-load stamps AND the font-face stamp');
});

// ── wave-35 lane B2: the Rule 15 FONT-FACE delivery wall ───────────────────
//
// Same shape as the wave-16 post-load pins below: the tag alone excludes, the
// delivery stamp re-admits, and the stamp is strict-true so an old combined
// fixture with no stamp at all keeps the conservative arm.

test('wave35: FONT_FACE_WALL_TAGS is exactly the Rule 15 tag', () => {
  // Membership pin — this set decides whether a whole family of tests is
  // scored, so a silent widening must never land unreviewed (same discipline
  // as EXTRACTION_WALL_TAGS' own pin).
  assert.deepEqual([...FONT_FACE_WALL_TAGS], ['requires-font-face']);
});

test('wave35: requires-font-face excludes when no face was delivered', () => {
  const diffs = [{ ssim: 0.98, wptPass: false }];
  assert.equal(applyNaScoreGate(['requires-font-face'], diffs, [], false, false, false), true);
  assert.equal(diffs[0].wptPass, null);
  assert.equal(diffs[0].scoreExcluded, true);
});

test('wave35: an absent font-face stamp keeps the conservative exclusion', () => {
  // undefined ⇒ the caller has no font channel at all (a pre-wave-35 combined
  // fixture). Absence of evidence must not promote a test into the scored set.
  const diffs = [{ ssim: 0.98, wptPass: false }];
  assert.equal(applyNaScoreGate(['requires-font-face'], diffs, [], false, false, undefined), true);
});

test('wave35: the delivery stamp re-admits a requires-font-face test', () => {
  const diffs = [{ ssim: 0.98, wptPass: false }];
  assert.equal(applyNaScoreGate(['requires-font-face'], diffs, [], false, false, true), false);
  // Untouched: a re-admitted test keeps its real pass/fail, which is the
  // whole point — the score measures the runtimes again.
  assert.equal(diffs[0].wptPass, false);
  assert.equal(diffs[0].scoreExcluded, undefined);
});

test('wave35: the font stamp does NOT re-admit the script-mutation wall', () => {
  // The two walls have DIFFERENT delivery records; a font file says nothing
  // about whether a script ran. Cross-re-admission would be the exact
  // dishonesty the separate sets exist to prevent.
  const diffs = [{ ssim: 0.98, wptPass: false }];
  assert.equal(applyNaScoreGate(['requires-script-mutation'], diffs, [], false, false, true), true);
});

test('wave35: post-load stamps do NOT re-admit a font-starved test', () => {
  // …and the converse. A test carrying both families stays excluded until
  // BOTH records are delivered.
  const diffs = [{ ssim: 0.98, wptPass: false }];
  assert.equal(applyNaScoreGate(['requires-font-face'], diffs, [], true, true, false), true);
  const both = [{ ssim: 0.98, wptPass: false }];
  assert.equal(applyNaScoreGate(['requires-font-face', 'requires-script-mutation'], both, [], true, true, true), false);
});

// ── wave-13 corpus-v4.3 SCORING boundary ───────────────────────────────────
//
// Three coupled scoring-honesty changes (canvas unchanged — only SCORING):
//   1. the SEMANTIC-PRESENCE pass gate (computePresenceFailed vetoes wptPass),
//   2. the DELIVERY-AWARE requires-bundled-asset exclusion (applyNaScoreGate
//      cross-checks the extractor's lossyReasons),
//   3. the NATIVE-PARITY secondary metric (computeNativeParity).
// Every threshold below is pinned to RECORDED wave12-gate values
// (tools/titan/runs/wave12-gate/sections — white-canvas ink coverage
// recomputed on the padded capture/ref pairs, tolerance 8).

import {
  computePresenceFailed, WPT_PRESENCE_REF_MIN_PCT, WPT_PRESENCE_RATIO_MIN,
  WPT_CANVAS_BG, computeNativeParity,
} from './inject-wpt-block.mjs';

test('wave13: presence-gate thresholds and canvas are the calibrated pins', () => {
  // The calibration margins documented at the constants only hold for THESE
  // values — a silent threshold drift invalidates every pin below.
  assert.equal(WPT_PRESENCE_REF_MIN_PCT, 0.02);
  assert.equal(WPT_PRESENCE_RATIO_MIN, 0.05);
  // Ink is measured against the corpus-v4 WHITE WPT canvas, NOT the
  // 327-pair dark #1A1A2E.
  assert.deepEqual(WPT_CANVAS_BG, { r: 0xFF, g: 0xFF, b: 0xFF });
});

test('wave13: presence gate MUST fail the measured wave12 vacuous passes', () => {
  // Recorded wave12-gate coverage (capture aCoveragePct / ref bCoveragePct):
  // appearance-auto-input-non-widget-001 — ssim 0.9711 "passed" with ALL
  // THREE platform captures fully blank vs a ref carrying 1.119 % ink.
  assert.equal(computePresenceFailed({ aCoveragePct: 0.000, bCoveragePct: 1.119 }), true);
  // accent-color-visited — ssim 0.9967 "passed" blank vs the ~13px checkbox
  // (0.068 % ink — the SMALLEST visibly-inked ref in the gate; this pin also
  // guards the REF_MIN floor staying under it).
  assert.equal(computePresenceFailed({ aCoveragePct: 0.000, bCoveragePct: 0.068 }), true);
  // background-attachment-fixed-inside-transform-1 android-ref — the third
  // measured blank capture (ssim 0.9762 vs a 7.404 %-ink ref). Not named in
  // the wave-13 finding but the same vacuum class; the gate catches it too.
  assert.equal(computePresenceFailed({ aCoveragePct: 0.000, bCoveragePct: 7.404 }), true);
});

test('wave13: presence gate MUST NOT fail any measured substantive pass', () => {
  // The WORST substantive ratio in the wave12-gate: contrast-color-
  // interpolation ios-ref, capture 0.800 % vs ref 5.103 % (ratio 0.157 —
  // the capture rendered less ink than the ref but DID render). The 0.05
  // ratio floor sits ~3× under this.
  assert.equal(computePresenceFailed({ aCoveragePct: 0.800, bCoveragePct: 5.103 }), false);
  // Next-worst: backface-visibility-hidden-001 ios-ref (0.951 / 4.092).
  assert.equal(computePresenceFailed({ aCoveragePct: 0.951, bCoveragePct: 4.092 }), false);
  // Tiny-ink-BOTH-sides: background-color-animation-with-table2 android-ref
  // (0.043 / 0.047) — ref below the 0.02 floor is out of the gate's
  // jurisdiction, so near-blank-vs-near-blank agreement stays a pass.
  assert.equal(computePresenceFailed({ aCoveragePct: 0.043, bCoveragePct: 0.047 }), false);
  // Blank-vs-blank: background-color-transparent-animation-in-body /
  // background-color-animation-with-zero-alpha (0.000 / 0.000, ssim 1) —
  // tests whose PASS criterion IS "render nothing" must keep passing.
  assert.equal(computePresenceFailed({ aCoveragePct: 0.000, bCoveragePct: 0.000 }), false);
  // Ordinary matched-ink passes far from any boundary.
  assert.equal(computePresenceFailed({ aCoveragePct: 0.681, bCoveragePct: 0.803 }), false); // accent-color-parent-currentcolor ios
  assert.equal(computePresenceFailed({ aCoveragePct: 17.153, bCoveragePct: 17.250 }), false); // a98rgb-004 android
  // Over-painting capture (3d-rendering-context-and-inline ios: 5.104 /
  // 0.856) — the gate is deliberately one-directional; SSIM already owns
  // the over-paint direction.
  assert.equal(computePresenceFailed({ aCoveragePct: 5.104, bCoveragePct: 0.856 }), false);
});

test('wave13: presence gate treats unknown presence as NOT failed', () => {
  // computeSemanticPresence returns null on shape mismatch; pre-v4.3 diffs
  // carry no block at all. Unknown ≠ failed (same stance as isColorDivergent).
  assert.equal(computePresenceFailed(null), false);
  assert.equal(computePresenceFailed(undefined), false);
  assert.equal(computePresenceFailed({}), false);
  // Non-numeric fields (hand-edited manifest) → unknown → not failed.
  assert.equal(computePresenceFailed({ aCoveragePct: null, bCoveragePct: 5 }), false);
  assert.equal(computePresenceFailed({ aCoveragePct: 'x', bCoveragePct: 5 }), false);
});

test('wave13: computeWptPass presence veto beats BOTH the ssim and fuzzy paths', () => {
  // The whole point of the gate: the measured vacuous passes cleared 0.95
  // on raw SSIM — presenceFailed must veto regardless.
  assert.equal(computeWptPass(0.9711, null, true), false);  // appearance-auto-input… recorded ssim
  assert.equal(computeWptPass(0.9967, null, true), false);  // accent-color-visited recorded ssim
  // A declared fuzzy tolerance cannot rescue a presence failure either —
  // WPT fuzzy budgets assume both sides actually rendered.
  assert.equal(computeWptPass(0.80, true, true), false);
  // presenceFailed=false and the omitted-argument legacy shape keep the
  // frozen two-argument semantics bit-for-bit.
  assert.equal(computeWptPass(0.9711, null, false), true);
  assert.equal(computeWptPass(0.9711, null), true);
});

test('wave13: diffWebVsRef stamps white-canvas semanticPresence on every ref diff', async () => {
  // Miniature of the vacuous-pass geometry: a fully-blank white capture vs
  // a mostly-white ref with one small dark widget. 64×64 canvas, 16×16
  // widget → ref ink 256/4096 = 6.25 %, capture ink 0 %.
  const dir = await tmpDir('presence-wiring');
  const blank = await writePng(dir, 'blank.png', 64, 64, [255, 255, 255, 255]);
  // Hand-build the widget ref (writePng is solid-only): white field with a
  // black 16×16 block at (8,8).
  const png = new PNG({ width: 64, height: 64 });
  for (let y = 0; y < 64; y++) {
    for (let x = 0; x < 64; x++) {
      const i = (y * 64 + x) * 4;
      const ink = x >= 8 && x < 24 && y >= 8 && y < 24;   // the "widget"
      png.data[i] = png.data[i + 1] = png.data[i + 2] = ink ? 0 : 255;
      png.data[i + 3] = 255;
    }
  }
  const refPath = join(dir, 'ref.png');
  await fs.writeFile(refPath, PNG.sync.write(png));

  const { diffWebVsRef } = await import('./inject-wpt-block.mjs');
  const d = await diffWebVsRef(blank, refPath);
  // a = capture, b = ref (diffWebVsRef argument order — pinned because the
  // gate's directionality depends on it).
  assert.ok(d.semanticPresence, 'semanticPresence block missing from ref diff');
  assert.equal(d.semanticPresence.aCoveragePct, 0, 'blank capture must measure 0 % ink');
  assert.equal(d.semanticPresence.bCoveragePct, 6.25, 'widget ref must measure 6.25 % ink');
  assert.equal(computePresenceFailed(d.semanticPresence), true);
});

test('wave13: composed diff path presence-vetoes a blank capture even under a generous fuzzy', async () => {
  // End-to-end through diffComposedVsRef: same blank-vs-widget geometry,
  // PLUS a fuzzy tolerance generous enough that the legacy verdict would
  // have been a PASS via the fuzzy path (256 mismatched px ≤ 100000,
  // ΔE ≤ 255) — the presence veto must still win.
  const dir = await tmpDir('presence-composed');
  const refDir = await tmpDir('presence-composed-ref');
  const testKey = 'wpt__css-ui__vacuous-001';
  await writePng(dir, `${safe(testKey)}.png`, 64, 64, [255, 255, 255, 255]);
  const png = new PNG({ width: 64, height: 64 });
  for (let y = 0; y < 64; y++) {
    for (let x = 0; x < 64; x++) {
      const i = (y * 64 + x) * 4;
      const ink = x >= 8 && x < 24 && y >= 8 && y < 24;
      png.data[i] = png.data[i + 1] = png.data[i + 2] = ink ? 0 : 255;
      png.data[i + 3] = 255;
    }
  }
  const refPng = join(refDir, 'ref.png');
  await fs.writeFile(refPng, PNG.sync.write(png));

  const fuzzy = { maxDifference: { min: 0, max: 255 }, totalPixels: { min: 0, max: 100000 } };
  const diff = await diffComposedVsRef({ platformDir: dir, testKey, refPng, fuzzy });
  assert.ok(diff, 'expected a diff object');
  assert.equal(diff.wptFuzzyMatch, true, 'the generous fuzzy DOES match — that is the trap');
  assert.equal(diff.presenceFailed, true, 'blank capture vs inked ref must stamp presenceFailed');
  assert.equal(diff.wptPass, false, 'presence veto must beat the fuzzy pass');
  // Raw metrics stay honest and untouched for investigators.
  assert.ok(typeof diff.ssim === 'number');
});

test('wave13: presence gate leaves content-matched composed diffs untouched', async () => {
  // Positive control: identical inked composed capture and ref → the stamp
  // is present (uniform triage field) but false, and wptPass stays true.
  const dir = await tmpDir('presence-ok');
  const refDir = await tmpDir('presence-ok-ref');
  const testKey = 'wpt__css-ui__honest-001';
  await writePng(dir, `${safe(testKey)}.png`, 40, 40, [0, 128, 0, 255]);
  const refPng = await writePng(refDir, 'ref.png', 40, 40, [0, 128, 0, 255]);
  const diff = await diffComposedVsRef({ platformDir: dir, testKey, refPng, fuzzy: null });
  assert.equal(diff.presenceFailed, false);
  assert.equal(diff.wptPass, true);
});

// ── wave-13: delivery-aware requires-bundled-asset exclusion ───────────────
//
// The textual Rule 20 regex (wpt-not-applicable.mjs) never consults the
// wave-8 inliner, so wpt-buckets.json stale-tags tests whose assets
// extract-fixture.mjs's inlineFixtureAssets() delivered as data URIs.
// applyNaScoreGate now cross-checks the tag against the extractor's
// lossyReasons — the actual delivery record.

test('wave13: bundled-asset tag WITHOUT extractor corroboration no longer excludes', () => {
  // The three measured wave12 css-backgrounds stale exclusions (assets
  // 218–961 B, all < MAX_INLINE_ASSET_BYTES, per-test IR carries data URIs):
  //   background-color-animation-with-images — lossyReasons []
  //   background-334                          — ['inline-run-merged','percentage']
  //   background-attachment-350               — ['inline-run-merged']
  // None carry 'requires-bundled-asset' from the extractor → all three must
  // now be SCORED (css-backgrounds denominator 9 → 12).
  for (const reasons of [[], ['inline-run-merged', 'percentage'], ['inline-run-merged']]) {
    const web = { ssim: 0.9584, wptPass: true };
    const isNa = applyNaScoreGate(['requires-bundled-asset'], [web], reasons);
    assert.equal(isNa, false, `reasons=${JSON.stringify(reasons)} must not exclude`);
    assert.equal(web.wptPass, true, 'scoring fields must stay intact');
    assert.equal(web.scoreExcluded, undefined);
  }
});

test('wave13: bundled-asset tag WITH extractor corroboration still excludes', () => {
  // When inlineFixtureAssets could NOT deliver (missing / ≥8 KB / non-raster
  // asset) it stamps the same tag into lossyReasons — the exclusion is then
  // genuine and the wave-8 neutralisation applies unchanged.
  const web = { ssim: 0.38, wptPass: false };
  const isNa = applyNaScoreGate(
    ['requires-bundled-asset'],
    [web],
    ['requires-bundled-asset', 'inline-run-merged'],
  );
  assert.equal(isNa, true);
  assert.equal(web.wptPass, null);
  assert.equal(web.scoreExcluded, true);
  assert.equal(web.ssim, 0.38, 'raw metrics stay for investigators');
});

test('wave13: absent delivery record falls back to the conservative legacy exclusion', () => {
  // A pre-wave-8 combined fixture whose keyMap has no lossyReasons at all
  // supplies undefined — absent delivery EVIDENCE must not promote a test
  // into the scored set, so the tag alone excludes (old behaviour). This is
  // also what keeps the wave-8 tests above passing unchanged.
  const web = { ssim: 0.5, wptPass: false };
  assert.equal(applyNaScoreGate(['requires-bundled-asset'], [web], undefined), true);
  assert.equal(web.scoreExcluded, true);
});

test('wave13: capability tags stay scored regardless of the delivery record', () => {
  // Broad capability tags were never in SCORE_EXCLUDED_TAGS; the delivery
  // record must not change that in either direction.
  const web = { ssim: 0.9, wptPass: false };
  assert.equal(applyNaScoreGate(['requires-form-control-rendering'], [web], []), false);
  assert.equal(applyNaScoreGate(['requires-float-layout'], [web], ['requires-float-layout']), false);
  assert.equal(web.scoreExcluded, undefined);
});

// ── wave-13: nativeParity secondary metric ─────────────────────────────────
//
// Capability-walled tests (notApplicable-tagged) get their already-computed
// cross-platform pair SSIMs surfaced as `nativeParity` so "browser parity
// blocked by <tag>" is visibly distinguishable from real native divergence.

test('wave13: computeNativeParity summarises pair SSIMs for tagged tests', () => {
  // Recorded wave12-gate css-ui rows: accent-color-visited's composed row
  // carried iOS-Android/iOS-web/Android-web all at ssim 1 (the natives agree
  // perfectly on the blank render the form-control wall forces), while
  // accent-color-parent-currentcolor carried 0.9914/0.9995/0.9919.
  const p = computeNativeParity(
    { 'iOS-Android': { ssim: 0.9914 }, 'iOS-web': { ssim: 0.9995 }, 'Android-web': { ssim: 0.9919 } },
    ['requires-form-control-rendering'],
  );
  assert.deepEqual(p, {
    pairs: { 'iOS-Android': 0.9914, 'iOS-web': 0.9995, 'Android-web': 0.9919 },
    min: 0.9914,
    max: 0.9995,
  });
});

test('wave13: computeNativeParity is null for untagged tests and missing pair data', () => {
  const pairs = { 'iOS-Android': { ssim: 0.99 }, 'iOS-web': null, 'Android-web': null };
  // Untagged → the test is not capability-walled → no secondary metric.
  assert.equal(computeNativeParity(pairs, []), null);
  assert.equal(computeNativeParity(pairs, undefined), null);
  // Tagged but NO pair carries a numeric ssim (web-only run) → null, so a
  // non-null nativeParity always has a meaningful range.
  assert.equal(computeNativeParity({ 'iOS-Android': null, 'iOS-web': null }, ['requires-float-layout']), null);
  assert.equal(computeNativeParity(null, ['requires-float-layout']), null);
  // Partial pair data still reports (single-pair range collapses to a point).
  assert.deepEqual(computeNativeParity(pairs, ['requires-float-layout']),
    { pairs: { 'iOS-Android': 0.99 }, min: 0.99, max: 0.99 });
});

test('wave13: buildResults matches composed-mode rows so pairs (and nativeParity) populate', async () => {
  // Source-scan pin: composed-mode compare rows are named
  // `<safe(testKey)>.png` (no NNN_ prefix, no __idx suffix) — measured on
  // all 7 wave12-gate sections: the per-component lookup alone left EVERY
  // test with pairs:null while rows[] held full cross-platform SSIMs. The
  // pair aggregation must also consult the composed row and the result
  // object must expose nativeParity.
  const src = await fs.readFile(new URL('./inject-wpt-block.mjs', import.meta.url), 'utf8');
  assert.match(src, /ix\[`\$\{safe\(testKey\)\}\.png`\]/,
    'pair aggregation must look up the composed row by test key');
  assert.match(src, /nativeParity:\s*anyMatched\s*\?\s*computeNativeParity\(pairs,\s*naTags\)\s*:\s*null/,
    'results must expose nativeParity wired to computeNativeParity');
});

// ── wave-15 EXTRACTION-WALL scoring boundary ───────────────────────────────
//
// Finding: 9 of the 12 scored css-position tests were the script-mutation
// wall scored as renderer failure — the tagger fired requires-script-mutation
// but applyNaScoreGate only excluded on requires-bundled-asset. The wall tags
// exclude UNCONDITIONALLY: script execution has no delivery record by
// construction (the extractor never runs scripts, so lossyReasons can never
// corroborate or refute the tag — contrast the wave-13 delivery-aware
// bundled-asset branch, which stays byte-for-byte unchanged).

import { EXTRACTION_WALL_TAGS, computeLowContentDensity, WPT_LOW_CONTENT_DENSITY_MAX_COVERAGE_PCT } from './inject-wpt-block.mjs';

test('wave29: EXTRACTION_WALL_TAGS is exactly the three delivery-wall tags', () => {
  // Pin the exact set — silently widening the wall (excluding more tests)
  // or narrowing it (re-scoring the wall) must break a test first.
  // wave-29 (lane ANCHOR) added requires-anchor-positioning-runtime: a
  // NON-script member, admitted on the set's real defining property (the
  // pipeline cannot deliver the input the ref encodes), not on its original
  // shared mechanism. See the constant's comment for the measured evidence.
  assert.deepEqual([...EXTRACTION_WALL_TAGS].sort(), [
    'requires-anchor-positioning-runtime',
    'requires-script-driven-scroll',
    'requires-script-mutation',
  ]);
});

test('wave29: applyNaScoreGate excludes anchor tests, post-load stamp re-admits', () => {
  // The measured wave28-final worst row: anchor-center-overflow-004 at
  // web-ref 0.536 — scored as renderer failure because the anchor tag was
  // not a wall tag. lossyReasons is an EMPTY ARRAY (every static asset was
  // delivered; there is no asset to be lossy about), so only the wall
  // branch can exclude it.
  const web = { ssim: 0.536, wptPass: false };
  assert.equal(
    applyNaScoreGate(['requires-anchor-positioning-runtime'], [web], []), true);
  assert.equal(web.wptPass, null);          // never a real pass/fail
  assert.equal(web.scoreExcluded, true);    // aggregators filter on this
  assert.equal(web.ssim, 0.536);            // raw diagnostics stay intact

  // …and the post-load bake's stamp re-admits it: the 34-property bake
  // snapshots used insets, which IS the anchored geometry, so the diff
  // measures the runtimes again. Same generic wall branch as wave-16.
  const baked = { ssim: 0.536, wptPass: false };
  assert.equal(
    applyNaScoreGate(['requires-anchor-positioning-runtime'], [baked], [], true),
    false);
  assert.equal(baked.wptPass, false);       // untouched — a real fail again
  assert.equal(baked.scoreExcluded, undefined);
});

test('wave15: applyNaScoreGate excludes on requires-script-mutation UNCONDITIONALLY', () => {
  const web = { ssim: 0.54, wptPass: false };
  // The css-position lane's worst row: overlay-transition-backdrop's
  // blank-vs-green diff at 0.54. lossyReasons is an EMPTY ARRAY (the
  // extractor delivered every static asset — there simply is no asset to be
  // lossy about); the delivery-aware branch would therefore NOT exclude,
  // which is exactly the wave-15 bug. The wall branch must exclude anyway.
  const isNa = applyNaScoreGate(['requires-script-mutation'], [web], []);
  assert.equal(isNa, true);
  assert.equal(web.wptPass, null);          // never a real pass/fail
  assert.equal(web.scoreExcluded, true);    // aggregators filter on this
  assert.equal(web.ssim, 0.54);             // raw diagnostics stay intact
});

test('wave15: applyNaScoreGate excludes on requires-script-driven-scroll too', () => {
  // Rule 18 is the same wall (post-script scroll offsets baked into the
  // ref) — also unconditional, also regardless of a populated lossy record.
  const ios = { ssim: 0.71, wptPass: false };
  assert.equal(applyNaScoreGate(['requires-script-driven-scroll'], [ios], ['some-other-reason']), true);
  assert.equal(ios.wptPass, null);
  assert.equal(ios.scoreExcluded, true);
});

test('wave15: the delivery-aware bundled-asset branch is unchanged by the wall', () => {
  // wave-13 behaviour must survive: a stale textual requires-bundled-asset
  // tag with a delivery record that does NOT corroborate it stays SCORED.
  const web = { ssim: 0.97, wptPass: true };
  assert.equal(applyNaScoreGate(['requires-bundled-asset'], [web], []), false);
  assert.equal(web.wptPass, true);
  assert.equal(web.scoreExcluded, undefined);
  // …and a corroborated tag still excludes (delivery-aware exclusion).
  const ios = { ssim: 0.30, wptPass: false };
  assert.equal(applyNaScoreGate(['requires-bundled-asset'], [ios], ['requires-bundled-asset']), true);
  assert.equal(ios.scoreExcluded, true);
});

test('wave15: broad capability tags STILL do not exclude (wave-8 denominator lesson)', () => {
  // The wall is narrow: capability tags describe tests the harness delivers
  // and renders — their divergence is real information and stays scored.
  const web = { ssim: 0.80, wptPass: false };
  assert.equal(applyNaScoreGate(
    ['requires-fragmentation', 'requires-float-layout', 'requires-form-control-rendering'],
    [web], []), false);
  assert.equal(web.scoreExcluded, undefined);
});

// ── wave-16 POST-LOAD gate interplay ───────────────────────────────────────
//
// The extraction wall becomes CROSSABLE: post-load-extract.mjs loads the
// TEST page in Chromium, snapshots per-element computed state after the
// page's scripts ran, bakes it into the fixture, and stamps
// `_wpt.postLoadExtracted: true`. That stamp is the delivery record the
// wave-15 comments said couldn't exist — threaded to the gate as the 4th
// parameter (via build-combined-fixture's keyMap, the lossyReasons channel).

test('wave16: postLoadExtracted=true re-scores a wall-tagged test', () => {
  // The wall was "cannot deliver the post-script state"; post-load
  // delivered it, so the diff measures the RUNTIMES again and must count.
  const web = { ssim: 0.97, wptPass: true };
  const isNa = applyNaScoreGate(['requires-script-mutation'], [web], [], true);
  assert.equal(isNa, false);
  assert.equal(web.wptPass, true);           // scoring fields untouched
  assert.equal(web.scoreExcluded, undefined); // not stamped — the test is scored
});

test('wave16: postLoadExtracted=true re-scores requires-script-driven-scroll too', () => {
  // Both wall tags are the same no-script-execution gap; the stamp clears
  // both (a delivered fixture passed the scroll guard, so a scroll-tagged
  // test that got the stamp really was delivered scroll-free).
  const ios = { ssim: 0.96, wptPass: true };
  assert.equal(applyNaScoreGate(['requires-script-driven-scroll'], [ios], [], true), false);
  assert.equal(ios.scoreExcluded, undefined);
});

test('wave16: absent/false/truthy-but-not-true stamps keep the wave-15 exclusion', () => {
  // Strict `=== true` on purpose: only the extractor's explicit stamp may
  // re-score the wall — undefined (legacy keyMap), false (static-only), and
  // accidental truthy values (1, 'yes') all stay conservatively excluded.
  for (const stamp of [undefined, false, 1, 'yes']) {
    const web = { ssim: 0.54, wptPass: false };
    assert.equal(applyNaScoreGate(['requires-script-mutation'], [web], [], stamp), true,
      `stamp ${JSON.stringify(stamp)} must keep the exclusion`);
    assert.equal(web.wptPass, null);
    assert.equal(web.scoreExcluded, true);
  }
});

test('wave16: the stamp does NOT touch the bundled-asset branch', () => {
  // Asset delivery has its own ground truth (lossyReasons); post-load says
  // nothing about assets. A corroborated requires-bundled-asset exclusion
  // survives even a post-load-extracted fixture.
  const web = { ssim: 0.30, wptPass: false };
  assert.equal(applyNaScoreGate(['requires-bundled-asset'], [web],
    ['requires-bundled-asset'], true), true);
  assert.equal(web.scoreExcluded, true);
});

test('wave16: keyMap threads postLoadExtracted from the fixture _wpt block', async () => {
  // Source-scan pin on build-combined-fixture.mjs — the stamp must ride the
  // keyMap (the same channel lossyReasons uses) or the gate would never see
  // it. Guards against a refactor silently dropping the wiring.
  const src = await fs.readFile(new URL('./build-combined-fixture.mjs', import.meta.url), 'utf8');
  assert.match(src, /postLoadExtracted:\s*fixture\._wpt\?\.postLoadExtracted\s*===\s*true/,
    'keyMap entries must carry postLoadExtracted from fixture._wpt');
});

// ── wave-20 STRUCTURE-EXTRACTION gate interplay ────────────────────────────
//
// Structure fixtures (the appendChild family: post-load-extract.mjs
// serialized the post-script DOM and re-extracted the component TREE) stamp
// `_wpt.structureExtracted` ALONGSIDE `_wpt.postLoadExtracted`. The gate
// treats EITHER stamp as the wall's delivery record — threaded as the 5th
// parameter via the same keyMap channel — so structure delivery must never
// depend on the state stamp riding along.

test('wave20: structureExtracted=true alone re-scores a wall-tagged test', () => {
  // 4th param (postLoadExtracted) deliberately false: either stamp delivers.
  const web = { ssim: 0.97, wptPass: true };
  const isNa = applyNaScoreGate(['requires-script-mutation'], [web], [], false, true);
  assert.equal(isNa, false);
  assert.equal(web.wptPass, true);            // scoring fields untouched
  assert.equal(web.scoreExcluded, undefined); // the test is scored
});

test('wave20: non-true structure stamps keep the wave-15 exclusion', () => {
  // Same strictness as the wave-16 stamp: only the extractor's explicit
  // `=== true` may re-score the wall.
  for (const stamp of [undefined, false, 1, 'yes']) {
    const web = { ssim: 0.54, wptPass: false };
    assert.equal(applyNaScoreGate(['requires-script-mutation'], [web], [], false, stamp), true,
      `structure stamp ${JSON.stringify(stamp)} must keep the exclusion`);
    assert.equal(web.wptPass, null);
    assert.equal(web.scoreExcluded, true);
  }
});

test('wave20: the structure stamp does NOT touch the bundled-asset branch', () => {
  // Same boundary as the wave-16 stamp: asset delivery has its own ground
  // truth (lossyReasons) and structure extraction says nothing about assets.
  const web = { ssim: 0.30, wptPass: false };
  assert.equal(applyNaScoreGate(['requires-bundled-asset'], [web],
    ['requires-bundled-asset'], false, true), true);
  assert.equal(web.scoreExcluded, true);
});

test('wave20: keyMap threads structureExtracted and the manifest surfaces it', async () => {
  // Source-scan pins: the structure stamp must ride the keyMap channel AND
  // appear on the per-test manifest row next to postLoadExtracted, so
  // dashboards can tell tree-re-extracted fixtures from state-only ones.
  const combined = await fs.readFile(new URL('./build-combined-fixture.mjs', import.meta.url), 'utf8');
  assert.match(combined, /structureExtracted:\s*fixture\._wpt\?\.structureExtracted\s*===\s*true/,
    'keyMap entries must carry structureExtracted from fixture._wpt');
  const inject = await fs.readFile(new URL('./inject-wpt-block.mjs', import.meta.url), 'utf8');
  assert.match(inject, /structureExtracted:\s*meta\.structureExtracted\s*===\s*true/,
    'the manifest row must surface structureExtracted');
});

// ── wave-15 LOW-CONTENT-DENSITY triage flag ────────────────────────────────
//
// Metrology observation: attachment-fixed-inside-transform-1 passes vs-ref
// at ssim 0.963 with ~90 % of BOTH images white while the actual content is
// MISPLACED (the body-root Height stacking issue, queued separately) —
// whole-canvas SSIM was dominated by background-vs-background agreement.
// The flag is triage colour ONLY: it feeds neither wptPass nor scoreExcluded.

test('wave15: low-content-density threshold is the calibrated pin', () => {
  // ">85 % of both images is background" ⇔ each side's ink coverage < 15 %.
  assert.equal(WPT_LOW_CONTENT_DENSITY_MAX_COVERAGE_PCT, 15);
});

test('wave15: computeLowContentDensity flags the measured vacuous-tall-doc pass', () => {
  // attachment-fixed-inside-transform-1: ~90 % white on both sides — the
  // recorded wave12-gate ref coverage is 7.404 % (android row); a capture in
  // the same regime must flag.
  assert.equal(computeLowContentDensity({ aCoveragePct: 9.6, bCoveragePct: 7.404 }), true);
  // Blank-vs-blank (0/0) is trivially low-density too — the presence gate
  // separately decides pass/fail; this flag just marks low confidence.
  assert.equal(computeLowContentDensity({ aCoveragePct: 0.0, bCoveragePct: 0.0 }), true);
});

test('wave15: computeLowContentDensity does NOT flag content-dense pairs', () => {
  // a98rgb-004 android (recorded wave12-gate): 17.153/17.250 — both sides
  // above the 15 % bar, SSIM there compares real ink.
  assert.equal(computeLowContentDensity({ aCoveragePct: 17.153, bCoveragePct: 17.25 }), false);
  // ONE dense side is enough to skip the flag (the SSIM already compares
  // real content on that side; flagging would only add noise).
  assert.equal(computeLowContentDensity({ aCoveragePct: 40.0, bCoveragePct: 7.4 }), false);
  assert.equal(computeLowContentDensity({ aCoveragePct: 7.4, bCoveragePct: 40.0 }), false);
});

test('wave15: computeLowContentDensity treats unknown presence as not flagged', () => {
  // Same "unknown ≠ flagged" stance as computePresenceFailed/isColorDivergent.
  assert.equal(computeLowContentDensity(null), false);
  assert.equal(computeLowContentDensity(undefined), false);
  assert.equal(computeLowContentDensity({}), false);
  assert.equal(computeLowContentDensity({ aCoveragePct: 'x', bCoveragePct: 5 }), false);
});

test('wave15: diffWebVsRef stamps lowContentDensity on every browser-ref diff', async () => {
  // Source-scan pin: the metric assembly must wire the flag off the SAME
  // semanticPresence block the presence gate consumes, so the two signals
  // can never diverge in provenance.
  const src = await fs.readFile(new URL('./inject-wpt-block.mjs', import.meta.url), 'utf8');
  assert.match(src, /metrics\.lowContentDensity\s*=\s*computeLowContentDensity\(metrics\.semanticPresence\)/,
    'diffWebVsRef must stamp lowContentDensity from semanticPresence');
});

// ── wave-22 HONEST-FRAME scoring (fitToRefFrame / countOverflowInk / fold) ──
//
// Pins the anti-dilution mechanism (skeptic-proved on the wave-21 text-decor
// movers): the legacy union-frame SSIM white-padded BOTH sides, so a capture
// TALLER than the ref gained mostly-white rows of near-perfect agreement and
// out-scored an equal-frame capture that was provably closer on content.
// The honest score runs on the REF's own frame and folds out-of-frame ink
// in as fully-divergent area — see the helper banner in inject-wpt-block.mjs.

/** White canvas with one black rect — the minimal "page with ink" builder. */
async function writeInkPng(dir, name, w, h, rect) {
  const png = new PNG({ width: w, height: h });
  png.data.fill(0xFF);                       // opaque white canvas
  const [rx, ry, rw, rh] = rect;
  for (let y = ry; y < ry + rh; y++) {
    for (let x = rx; x < rx + rw; x++) {
      const i = (y * w + x) * 4;
      png.data[i] = 0; png.data[i + 1] = 0; png.data[i + 2] = 0;  // black ink
    }
  }
  const path = join(dir, name);
  await fs.writeFile(path, PNG.sync.write(png));
  return path;
}

test('wave22: fitToRefFrame is the identity on an already-matching frame', async () => {
  const { fitToRefFrame } = await import('./inject-wpt-block.mjs');
  const img = new PNG({ width: 8, height: 8 });
  img.data.fill(0x11);
  assert.equal(fitToRefFrame(img, 8, 8), img);  // same object, no copy
});

test('wave22: fitToRefFrame white-pads a smaller capture and crops a larger one', async () => {
  const { fitToRefFrame } = await import('./inject-wpt-block.mjs');
  // Smaller: 2x2 gray into a 4x3 frame → gray overlap, white pad.
  const small = new PNG({ width: 2, height: 2 });
  small.data.fill(0x40);
  const padded = fitToRefFrame(small, 4, 3);
  assert.equal(padded.width, 4); assert.equal(padded.height, 3);
  assert.equal(padded.data[0], 0x40);                       // (0,0) copied
  assert.equal(padded.data[(0 * 4 + 3) * 4], 0xFF);         // (3,0) white pad
  assert.equal(padded.data[(2 * 4 + 0) * 4], 0xFF);         // (0,2) white pad
  // Larger: 4x4 gray into a 2x2 frame → cropped copy only.
  const big = new PNG({ width: 4, height: 4 });
  big.data.fill(0x40);
  const cropped = fitToRefFrame(big, 2, 2);
  assert.equal(cropped.width, 2); assert.equal(cropped.height, 2);
  assert.equal(cropped.data[(1 * 2 + 1) * 4], 0x40);        // (1,1) copied
});

test('wave22: countOverflowInk counts only out-of-frame ink, honouring tolerance', async () => {
  const { countOverflowInk } = await import('./inject-wpt-block.mjs');
  const img = new PNG({ width: 10, height: 10 });
  img.data.fill(0xFF);
  const put = (x, y, v) => { const i = (y * 10 + x) * 4; img.data[i] = v; img.data[i+1] = v; img.data[i+2] = v; };
  put(3, 8, 0x00);    // below a 10x6 frame → ink
  put(2, 2, 0x00);    // INSIDE the frame → never counted
  put(9, 9, 0xF8);    // delta 7 < tolerance 8 → not ink
  assert.equal(countOverflowInk(img, 10, 6), 1);
  // Width overflow: same image against a 5x10 frame → (9,9) still under
  // tolerance, (3,8) now IN frame? x=3 < 5 but y=8 < 10 → in frame; (2,2)
  // in frame → zero from those; nothing else inked right of x=5.
  assert.equal(countOverflowInk(img, 5, 10), 0);
  // Full-frame: nothing outside → 0.
  assert.equal(countOverflowInk(img, 10, 10), 0);
});

test('wave22: honest frame — a taller mostly-white WRONG capture no longer out-scores a closer equal-frame one', async () => {
  const { diffWebVsRef } = await import('./inject-wpt-block.mjs');
  const dir = await tmpDir('honest-frame');
  // Ref: 60x60, black bar rows 10-19.
  const ref = await writeInkPng(dir, 'ref.png', 60, 60, [0, 10, 60, 10]);
  // Wrong render: 60x240, bar pushed to rows 100-109 (outside the ref
  // frame entirely) — the wave-21 "wrapped/overflowing" shape whose union
  // padding used to dilute the score upward.
  const wrong = await writeInkPng(dir, 'wrong.png', 60, 240, [0, 100, 60, 10]);
  // Close render: equal frame, bar 4px low (rows 14-23) — honest content
  // divergence only.
  const close = await writeInkPng(dir, 'close.png', 60, 60, [0, 14, 60, 10]);
  const dWrong = await diffWebVsRef(wrong, ref);
  const dClose = await diffWebVsRef(close, ref);
  // The closer render must WIN under honest-frame scoring.
  assert.ok(dClose.ssim > dWrong.ssim,
    `close ${dClose.ssim} must out-score wrong ${dWrong.ssim}`);
  // Overflow provenance: the wrong capture's bar is 60x10 = 600 ink px
  // outside the 60x60 frame, all folded into the mean.
  assert.equal(dWrong.frame.overflowInkPx, 600);
  assert.equal(dWrong.frame.capH, 240);
  assert.equal(dWrong.frame.refH, 60);
  // The fold direction: honest ssim ≤ the pre-fold ref-frame ssim.
  assert.ok(dWrong.ssim <= dWrong.frame.ssimRefFrame);
  // Equal-frame pair: identity fit, zero overflow, fold is a no-op.
  assert.equal(dClose.frame.overflowInkPx, 0);
  assert.equal(dClose.ssim, dClose.frame.ssimRefFrame);
});

test('wave22: identical same-size pair still scores 1.0 (alias tests stay 1.000)', async () => {
  const { diffWebVsRef } = await import('./inject-wpt-block.mjs');
  const dir = await tmpDir('honest-identity');
  const a = await writeInkPng(dir, 'a.png', 40, 40, [5, 5, 20, 8]);
  const b = await writeInkPng(dir, 'b.png', 40, 40, [5, 5, 20, 8]);
  const d = await diffWebVsRef(a, b);
  assert.equal(d.ssim, 1);
  assert.equal(d.frame.overflowInkPx, 0);
});

// ── wave-25 CAL-RC6: the SCORING-HONESTY vetoes ──────────────────────────────
//
// Two measured lies motivated this boundary, both from css-gaps in the
// wave24-final corpus, both scored wptPass=true:
//   * flex-gap-decorations-001 — the natives paint a FILLED RED square where
//     the ref's own text reads "filled green square and no red". SSIM 0.9938
//     (iOS) / 0.9850 (Android): luminance structure is identical, only the
//     hue is the opposite of the assertion.
//   * flex-gap-decorations-002 — the natives carry 0.47 % ink against the
//     ref's 4.43 %, a 9.4× deficit, and still scored 0.9509 because the
//     canvas is ~95 % white on both sides.
// The pins below hold the calibration that fixes both WITHOUT flipping a
// single demonstrably-correct render.

import {
  computeColorFailed, computeCoverageRatioFailed,
  WPT_COLOR_FAIL_DELTA_E_MIN, WPT_COVERAGE_RATIO_MAX,
  normalizeRefsRoot, LIVE_CANVAS_REV, KNOWN_STALE_CANVAS_REVS,
} from './inject-wpt-block.mjs';
// The live canvas rev is OWNED by capture-browser-ref.mjs; this import is
// what keeps the scorer's copy from drifting (see the pin below).
import { CANVAS_REV } from './capture-browser-ref.mjs';

test('CAL-RC6 colour veto: the measured css-gaps red-square lie now FAILS', () => {
  // flex-gap-decorations-001, android-ref: histogramKL max 0.1016 (stamp
  // fires) and mean ΔE 3.137 (well past the 2.3 JND) — a real hue flip.
  assert.equal(computeColorFailed(true, { mean: 3.137, max: 100, p95: 0.198 }), true);
  // iOS sibling of the same test: ΔE 2.911.
  assert.equal(computeColorFailed(true, { mean: 2.911, max: 100, p95: 0 }), true);
  // And it vetoes the verdict outright, at a passing SSIM.
  assert.equal(computeWptPass(0.9938, null, false, true, false), false);
});

test('CAL-RC6 colour veto: pixel-EXACT pairs that carry the stamp still PASS', () => {
  // THE REFUTATION the calibration produced (documented on
  // WPT_COLOR_FAIL_DELTA_E_MIN): a bare "colorDivergent ⇒ fail" rule flips 58
  // wave24-final passes, and some are pixel-exact —
  // css-color/background-color-rgb-001 web-ref is ssim 1.0000,
  // pixelMismatchedPct 0.000, mean ΔE 0.00, histogramKL 0.1602. Histogram KL
  // explodes on near-empty bins, so a handful of AA pixels on a near-uniform
  // image fabricate the stamp. The ΔE corroboration keeps these passing.
  assert.equal(computeColorFailed(true, { mean: 0.0, max: 0, p95: 0 }), false);
  assert.equal(computeColorFailed(true, { mean: 0.08, max: 12, p95: 0 }), false);
  // …and the two other css-color/css-overflow families that sit at ΔE ≈ 0.01
  // with KL up to 1.34 (cross-fade-premultiplied-alpha web-ref, ssim 1.0000).
  assert.equal(computeColorFailed(true, { mean: 0.07, max: 3, p95: 0 }), false);
  assert.equal(computeWptPass(1, null, false, false, false), true);
});

test('CAL-RC6 colour veto: unknown colour is not divergent colour', () => {
  // Stamp absent → nothing to corroborate.
  assert.equal(computeColorFailed(false, { mean: 40 }), false);
  assert.equal(computeColorFailed(undefined, { mean: 40 }), false);
  // Stamp present but ΔE unavailable (degenerate/size-mismatched pair) →
  // same "unknown ≠ divergent" stance isColorDivergent takes.
  assert.equal(computeColorFailed(true, null), false);
  assert.equal(computeColorFailed(true, { mean: NaN }), false);
  // The JND threshold itself is the calibrated pin.
  assert.equal(WPT_COLOR_FAIL_DELTA_E_MIN, 2.3);
});

test('CAL-RC6 coverage-ratio veto: the measured wave24-final flip set', () => {
  assert.equal(WPT_COVERAGE_RATIO_MAX, 2);
  // css-gaps/flex-gap-decorations-002 ios+android: 0.47 vs 4.427 (9.4×).
  assert.equal(computeCoverageRatioFailed({ aCoveragePct: 0.47, bCoveragePct: 4.427 }), true);
  // css-color contrast-color-interpolation ios: the green square the ref
  // demands is entirely absent — 0.8 vs 5.103 (6.4×).
  assert.equal(computeCoverageRatioFailed({ aCoveragePct: 0.8, bCoveragePct: 5.103 }), true);
  // css-transforms/3d-rendering-context-and-inline ios: the capture paints a
  // RED square under "Nothing should appear except this sentence" — the
  // OVER-paint direction, which the one-directional presence gate ignored:
  // 5.104 vs 0.856 (6.0×).
  assert.equal(computeCoverageRatioFailed({ aCoveragePct: 5.104, bCoveragePct: 0.856 }), true);
  assert.equal(computePresenceFailed({ aCoveragePct: 5.104, bCoveragePct: 0.856 }), false);
  // css-text/boundary-shaping: the capture breaks "office" onto three lines
  // where the ref shapes one ffi ligature — 0.115 vs 0.372 (3.2×).
  assert.equal(computeCoverageRatioFailed({ aCoveragePct: 0.115, bCoveragePct: 0.372 }), true);
  // css-backgrounds background-color-animation-with-table1: extra table
  // cells — 0.135 vs 0.047 (2.9×), both far under the old absolute 5 % bar.
  assert.equal(computeCoverageRatioFailed({ aCoveragePct: 0.135, bCoveragePct: 0.047 }), true);
  // css-images/cross-fade-cross-origin-orientation: the image is missing —
  // 1.986 vs 4.123 (2.08×), the tightest flip in the set.
  assert.equal(computeCoverageRatioFailed({ aCoveragePct: 1.986, bCoveragePct: 4.123 }), true);
});

test('CAL-RC6 coverage-ratio veto: the 1.4–2.0 margin band is deliberately left passing', () => {
  // These are real but PARTIAL divergences; the conservative first cut keeps
  // them on the SSIM bar so the flip set contains only verified defects.
  // change-insets-inside-strict-containment-nested web-ref, 1.65×.
  assert.equal(computeCoverageRatioFailed({ aCoveragePct: 8.798, bCoveragePct: 5.326 }), false);
  // display-contents-details-001 ios-ref, 1.77×.
  assert.equal(computeCoverageRatioFailed({ aCoveragePct: 0.303, bCoveragePct: 0.171 }), false);
  // Exactly at the threshold is a PASS (strict >), so a 2.0× pair survives.
  assert.equal(computeCoverageRatioFailed({ aCoveragePct: 2, bCoveragePct: 1 }), false);
  assert.equal(computeCoverageRatioFailed({ aCoveragePct: 2.01, bCoveragePct: 1 }), true);
});

test('CAL-RC6 coverage-ratio veto: blank-vs-blank stays the honest pass', () => {
  // "Render nothing" IS the pass criterion for several tests
  // (background-color-transparent-animation-in-body,
  // background-color-animation-with-zero-alpha) — both sides measure 0.000 %.
  assert.equal(computeCoverageRatioFailed({ aCoveragePct: 0, bCoveragePct: 0 }), false);
  // Sub-floor ink on both sides: no ratio can mean anything there
  // (background-color-animation-with-table2 android, 0.043/0.047, is an
  // honest pass the presence gate also declines to judge).
  assert.equal(computeCoverageRatioFailed({ aCoveragePct: 0.019, bCoveragePct: 0.001 }), false);
  assert.equal(computeCoverageRatioFailed({ aCoveragePct: 0.043, bCoveragePct: 0.047 }), false);
  // One side blank against an inked one is the strongest asymmetry there is.
  assert.equal(computeCoverageRatioFailed({ aCoveragePct: 0, bCoveragePct: 1.119 }), true);
  assert.equal(computeCoverageRatioFailed({ aCoveragePct: 7.404, bCoveragePct: 0 }), true);
  // Unknown/degenerate presence → never a veto.
  assert.equal(computeCoverageRatioFailed(null), false);
  assert.equal(computeCoverageRatioFailed({ aCoveragePct: 'x', bCoveragePct: 1 }), false);
});

test('CAL-RC6: every veto is unconditional — SSIM and fuzzy cannot rescue it', () => {
  // A perfect SSIM and a satisfied fuzzy budget both lose to each veto.
  assert.equal(computeWptPass(1, true, true, false, false), false);   // presence
  assert.equal(computeWptPass(1, true, false, true, false), false);   // colour
  assert.equal(computeWptPass(1, true, false, false, true), false);   // coverage ratio
  // Legacy call shapes keep working: the new args default to false.
  assert.equal(computeWptPass(0.96, null), true);
  assert.equal(computeWptPass(0.94, null), false);
  assert.equal(computeWptPass(0.94, true), true);
});

// ── wave-25 CAL-RC1: --refs-root canvas-rev normalisation ────────────────────

test('normalizeRefsRoot upgrades a known-stale canvas-rev tail to the live rev', () => {
  // The literal run-titan.sh / section-runner.sh actually pass.
  assert.equal(
    normalizeRefsRoot('tools/wpt/refs/9b5435e5/white-black-ink-font-lh'),
    `tools/wpt/refs/9b5435e5/${LIVE_CANVAS_REV}`);
  // Older revs upgrade too — none of their PNGs may reach a live diff.
  assert.equal(normalizeRefsRoot('/a/refs/sha/white'), `/a/refs/sha/${LIVE_CANVAS_REV}`);
  assert.equal(normalizeRefsRoot('/a/refs/sha/white-black-ink-font'), `/a/refs/sha/${LIVE_CANVAS_REV}`);
});

test('normalizeRefsRoot leaves the live rev, unknown tails and null alone', () => {
  const live = `tools/wpt/refs/sha/${LIVE_CANVAS_REV}`;
  assert.equal(normalizeRefsRoot(live), live);
  // An unrecognised tail is someone reproducing a historical corpus by hand —
  // rewriting it would silently take their run somewhere they did not ask for.
  assert.equal(normalizeRefsRoot('tools/wpt/refs/sha/my-experiment'), 'tools/wpt/refs/sha/my-experiment');
  // --refs-root omitted, and a single-segment path with no rev tail.
  assert.equal(normalizeRefsRoot(null), null);
  assert.equal(normalizeRefsRoot('refs'), 'refs');
});

test('normalizeRefsRoot sees a stale rev through a TRAILING SEPARATOR', () => {
  // `--refs-root .../white-black-ink-font-lh/` names the same directory as the
  // un-slashed spelling, and every downstream join behaves identically — so a
  // trailing slash must not be able to hide a stale rev. Before the fix the
  // tail regex could not match through it and the normaliser fell through as
  // "no rev tail", reading the PREVIOUS canvas contract's refs with no
  // stderr line and no other symptom: exactly the silent scoring corruption
  // this function exists to delete.
  assert.equal(
    normalizeRefsRoot('tools/wpt/refs/9b5435e5/white-black-ink-font-lh/'),
    `tools/wpt/refs/9b5435e5/${LIVE_CANVAS_REV}`);
  // Windows-shaped separator, same hazard.
  assert.equal(
    normalizeRefsRoot('C:\\wpt\\refs\\sha\\white\\'),
    `C:\\wpt\\refs\\sha\\${LIVE_CANVAS_REV}`);
  // The live rev with a trailing slash is already correct — returned verbatim,
  // no rewrite and no stderr noise.
  const liveSlashed = `tools/wpt/refs/sha/${LIVE_CANVAS_REV}/`;
  assert.equal(normalizeRefsRoot(liveSlashed), liveSlashed);
  // Degenerate all-separator input must not throw or invent a path.
  assert.equal(normalizeRefsRoot('/'), '/');
});

test('the scorer\'s LIVE_CANVAS_REV equals capture-browser-ref\'s CANVAS_REV', () => {
  // The rev is OWNED by capture-browser-ref.mjs (it writes the tree). This
  // module keeps a literal copy so the scorer does not drag puppeteer into
  // its import graph; this pin is what makes the copy safe. A bump that
  // forgets either side fails here.
  assert.equal(LIVE_CANVAS_REV, CANVAS_REV);
  // …and the rev it replaced must be listed as stale, or the shell scripts'
  // untouched literal would silently keep pointing at the old tree.
  assert.ok(KNOWN_STALE_CANVAS_REVS.includes('white-black-ink-font-lh'));
  assert.ok(!KNOWN_STALE_CANVAS_REVS.includes(LIVE_CANVAS_REV),
    'the live rev must never be listed as stale');
});

// ── wave-29 S-RC3: REF_UNACHIEVABLE_TAGS, the third exclusion family ────────
//
// Unlike the other two families this one is not about our pipeline: the
// committed ref is a target Chromium itself does not hit when it renders the
// test page (measured 0.9394 on css-pseudo/active-selection-051..054, under
// the 0.95 gate). It therefore excludes UNCONDITIONALLY, with no delivery
// record and no stamp that re-admits — the pins below are exactly that
// asymmetry, since a future refactor that folded this branch into either of
// the other two would silently restore the dishonest score.

import { REF_UNACHIEVABLE_TAGS } from './inject-wpt-block.mjs';

test('wave29 S-RC3: REF_UNACHIEVABLE_TAGS is exactly browser-ref-divergent', () => {
  // Pin the exact membership: this family has NO route back, so admitting a
  // tag to it permanently removes those tests from the denominator.
  assert.deepEqual([...REF_UNACHIEVABLE_TAGS], ['browser-ref-divergent']);
});

test('wave29 S-RC3: browser-ref-divergent excludes and neutralises the diffs', () => {
  const web = { ssim: 0.9394, wptPass: false };
  const ios = { ssim: 0.91, wptPass: false };
  assert.equal(applyNaScoreGate(['browser-ref-divergent'], [web, ios], []), true);
  assert.equal(web.wptPass, null);
  assert.equal(web.scoreExcluded, true);
  assert.equal(ios.wptPass, null);
  assert.equal(ios.scoreExcluded, true);
});

test('wave29 S-RC3: the post-load / structure stamps do NOT re-admit it', () => {
  // The wall branch falls to `delivered`; this one must not. Running the
  // test's scripts in Chromium reproduces precisely the diverging render —
  // that IS the measurement — so a stamp is not evidence of anything here.
  const web = { ssim: 0.9394, wptPass: false };
  assert.equal(
    applyNaScoreGate(['browser-ref-divergent'], [web], [], true, true), true);
  assert.equal(web.scoreExcluded, true);
});

test('wave29 S-RC3: it excludes even alongside a wall tag a stamp would re-admit', () => {
  // Ordering pin: the ref-unachievable branch is checked FIRST, so a test
  // carrying BOTH families cannot be re-admitted through the wall branch.
  const web = { ssim: 0.9394, wptPass: false };
  assert.equal(
    applyNaScoreGate(['requires-script-mutation', 'browser-ref-divergent'],
      [web], [], true, true), true);
  assert.equal(web.scoreExcluded, true);
});

test('wave29 S-RC3: lossyReasons is never consulted for it', () => {
  // Contrast requires-bundled-asset, which only excludes when the extractor
  // corroborates. An empty lossy record must not un-exclude this tag.
  const web = { ssim: 0.9394, wptPass: false };
  assert.equal(applyNaScoreGate(['browser-ref-divergent'], [web], []), true);
  const web2 = { ssim: 0.9394, wptPass: false };
  assert.equal(applyNaScoreGate(['browser-ref-divergent'], [web2], ['percentage']), true);
});

test('wave29 S-RC3: a plain capability tag still scores (denominator intact)', () => {
  // The wave-8 lesson pinned once more against the NEW branch: adding a
  // third family must not start excluding the broad capability tags.
  const web = { ssim: 0.80, wptPass: false };
  assert.equal(applyNaScoreGate(['requires-runtime-selection'], [web], []), false);
  assert.equal(web.wptPass, false);
  assert.equal(web.scoreExcluded, undefined);
});

// ── wave-30 B4(b): NATIVE_FONT_PARITY_TAGS, the PER-PLATFORM family ────────
//
// The first exclusion in this file that is not one boolean for all three
// platforms. It names a FONT BOUNDARY: ref and web share the Chromium-macOS
// fallback face for §6 non-Latin counter-style glyphs, Compose and SwiftUI
// each resolve their own, so native-vs-ref SSIM is typography-bound while
// web-vs-ref stays an honest measurement. Measured across css-counter-styles
// (runs/wave29-final): web 0.9623–0.9986 with 11/12 passing, natives
// 0.7244–0.9917 degrading with glyph count.
//
// The pins hold the asymmetry itself — web untouched, natives neutralised,
// `scoreEligible` unaffected — because a refactor that folded this into
// applyNaScoreGate would silently drop the one honest column in the family.
//
// wave-48 W2: the gate became PER-TEST. The tag still arms it, but it fires
// only for members of NATIVE_FONT_PARITY_REFUSED_TESTS — the 13 cells the
// wave48-w2 re-measure (both natives re-fed on the current tree; every score
// reproduced wave48-cal to 4 decimals) showed still font-bounded. The pins
// below therefore split into three families of their own: the refusal list's
// exact membership (a silent widening re-hides honest measurements, a silent
// shrink floods the corpus with font noise), the fire path for a LISTED
// test, and the decline path for an UNLISTED tagged test — the 15 cells the
// re-measure unexcluded.

import {
  applyNativeFontParityGate, NATIVE_FONT_PARITY_TAGS,
  NATIVE_FONT_PARITY_PLATFORMS, NATIVE_FONT_PARITY_STAMP,
  NATIVE_FONT_PARITY_REFUSED_TESTS,
} from './inject-wpt-block.mjs';

test('wave30 B4b: the family and its platform list are exactly pinned', () => {
  // Membership is blast radius: each tag removes BOTH natives from the
  // denominator for every listed test carrying it.
  assert.deepEqual([...NATIVE_FONT_PARITY_TAGS], ['requires-non-latin-font-parity']);
  // 'web-ref' being ABSENT is the whole contract of this family.
  assert.deepEqual([...NATIVE_FONT_PARITY_PLATFORMS], ['ios-ref', 'android-ref']);
  assert.equal(NATIVE_FONT_PARITY_STAMP, 'native-font-parity');
});

test('wave48 W2: the refusal list is exactly pinned — no silent widening or shrink', () => {
  // The full membership, sorted, straight from the wave48-w2 measurement
  // table (each retained line's scores are commented in the module). Any
  // membership change MUST re-run the measurement and update this pin.
  assert.deepEqual([...NATIVE_FONT_PARITY_REFUSED_TESTS].sort(), [
    'css/css-counter-styles/arabic-indic/css3-counter-styles-102.html',
    'css/css-counter-styles/armenian/css3-counter-styles-007.html',
    'css/css-counter-styles/armenian/css3-counter-styles-008.html',
    'css/css-counter-styles/bengali/css3-counter-styles-117.html',
    'css/css-counter-styles/cambodian/css3-counter-styles-159.html',
    'css/css-counter-styles/cjk-decimal/css3-counter-styles-001.html',
    'css/css-counter-styles/cjk-decimal/css3-counter-styles-004.html',
    'css/css-counter-styles/cjk-earthly-branch/css3-counter-styles-201.html',
    'css/css-counter-styles/cjk-earthly-branch/css3-counter-styles-202.html',
    'css/css-counter-styles/cjk-heavenly-stem/css3-counter-styles-204.html',
    'css/css-counter-styles/cjk-heavenly-stem/css3-counter-styles-205.html',
    'css/css-counter-styles/counter-suffix.html',
    'css/css-lists/counter-004.html',
  ]);
  // The 15 unexcluded tests must NEVER quietly re-enter: each was measured
  // clear of the font boundary at wave48-w2 (13 with both natives ≥0.95;
  // name-case-sensitivity failing only the shared coverage veto web fails
  // too; counter-cjk-decimal an Android blank-paint bug, not a face).
  for (const t of [
    'css/css-counter-styles/arabic-indic/css3-counter-styles-101.html',
    'css/css-counter-styles/arabic-indic/css3-counter-styles-103.html',
    'css/css-counter-styles/armenian/css3-counter-styles-006.html',
    'css/css-counter-styles/armenian/css3-counter-styles-009.html',
    'css/css-counter-styles/bengali/css3-counter-styles-116.html',
    'css/css-counter-styles/bengali/css3-counter-styles-118.html',
    'css/css-counter-styles/cambodian/css3-counter-styles-158.html',
    'css/css-counter-styles/cambodian/css3-counter-styles-160.html',
    'css/css-counter-styles/cjk-decimal/counter-cjk-decimal.html',
    'css/css-counter-styles/cjk-decimal/css3-counter-styles-005.html',
    'css/css-counter-styles/cjk-earthly-branch/css3-counter-styles-203.html',
    'css/css-counter-styles/cjk-heavenly-stem/css3-counter-styles-206.html',
    'css/css-counter-styles/counter-style-at-rule/name-case-sensitivity.html',
    'css/css-lists/content-property/marker-text-matches-armenian.html',
    'css/css-lists/content-property/marker-text-matches-georgian.html',
  ]) {
    assert.equal(NATIVE_FONT_PARITY_REFUSED_TESTS.has(t), false,
      `${t} was unexcluded at wave48-w2 — re-adding it needs a new measurement`);
  }
});

// Shorthand: a LISTED member (arabic-indic 102) and an UNLISTED tagged test
// (arabic-indic 103, unexcluded at wave48-w2) — the pair every fire/decline
// pin below is built from.
const LISTED = 'css/css-counter-styles/arabic-indic/css3-counter-styles-102.html';
const UNLISTED = 'css/css-counter-styles/arabic-indic/css3-counter-styles-103.html';

test('wave30 B4b: natives are neutralised, web-ref is untouched', () => {
  // The measured arabic-indic 102 row (wave48-w2): web 0.9791 pass, ios
  // 0.9688 pass, android 0.8230 fail. After the gate the two native verdicts
  // are gone and the web verdict — the one that measures our counter
  // algorithm — remains.
  const web = { ssim: 0.9791, wptPass: true };
  const ios = { ssim: 0.9688, wptPass: true };
  const android = { ssim: 0.8230, wptPass: false };
  const stamped = applyNativeFontParityGate(['requires-non-latin-font-parity'],
    { 'web-ref': web, 'ios-ref': ios, 'android-ref': android }, LISTED);
  assert.deepEqual(stamped, ['ios-ref', 'android-ref']);
  assert.equal(web.wptPass, true);
  assert.equal(web.scoreExcluded, undefined);
  assert.equal(ios.wptPass, null);
  assert.equal(ios.scoreExcluded, 'native-font-parity');
  assert.equal(android.wptPass, null);
  assert.equal(android.scoreExcluded, 'native-font-parity');
});

test('wave30 B4b: a PASSING native diff of a LISTED test is excluded too', () => {
  // arabic-indic 102's ios cell measures 0.9688 T while its android sibling
  // is font-bound at 0.8230 — the cut is per-TEST, so the pass leaves the
  // denominator with the failure: whether THIS cell clears 0.95 is decided
  // by how many fallback glyphs it paints, not by runtime correctness.
  const ios = { ssim: 0.9688, wptPass: true };
  applyNativeFontParityGate(['requires-non-latin-font-parity'],
    { 'ios-ref': ios }, LISTED);
  assert.equal(ios.wptPass, null);
  assert.equal(ios.scoreExcluded, 'native-font-parity');
});

test('wave48 W2: an UNLISTED tagged test is SCORED — the unexclusion itself', () => {
  // arabic-indic 103 (wave48-w2: ios 0.9978 T, android 0.9913 T) carries the
  // tag but left the refusal list. The gate must not touch it: this is the
  // pin that fails if the blanket per-tag behavior ever returns.
  const ios = { ssim: 0.9978, wptPass: true };
  const android = { ssim: 0.9913, wptPass: true };
  const stamped = applyNativeFontParityGate(['requires-non-latin-font-parity'],
    { 'ios-ref': ios, 'android-ref': android }, UNLISTED);
  assert.deepEqual(stamped, []);
  assert.equal(ios.wptPass, true);
  assert.equal(ios.scoreExcluded, undefined);
  assert.equal(android.wptPass, true);
  assert.equal(android.scoreExcluded, undefined);
});

test('wave48 W2: no testRel ⇒ decline on unknown — the gate never fires blind', () => {
  // A caller that cannot say WHICH test cannot prove the test still-bounded,
  // and an over-fire costs a measurement we can never get back (the wave-30
  // fix-T4 stance). Tag present, diffs present, test unknown → no-op.
  const ios = { ssim: 0.8513, wptPass: false };
  assert.deepEqual(applyNativeFontParityGate(['requires-non-latin-font-parity'],
    { 'ios-ref': ios }), []);
  assert.equal(ios.wptPass, false);
  assert.equal(ios.scoreExcluded, undefined);
});

test('wave30 B4b: raw metrics survive on every platform', () => {
  // Only the SCORING fields are neutralised — an investigator asking "how far
  // off was the fallback face?" must still be able to read the number. Same
  // stance as applyNaScoreGate. armenian 007's wave48-w2 ios cell.
  const ios = { ssim: 0.8513, pixelMismatchedPct: 4.2, pHash: 11, wptPass: false };
  applyNativeFontParityGate(['requires-non-latin-font-parity'], { 'ios-ref': ios },
    'css/css-counter-styles/armenian/css3-counter-styles-007.html');
  assert.equal(ios.ssim, 0.8513);
  assert.equal(ios.pixelMismatchedPct, 4.2);
  assert.equal(ios.pHash, 11);
});

test('wave30 B4b: absent platforms and error-shaped diffs are skipped', () => {
  // A platform with no captures this run contributes null; an error record
  // carries no scoring fields to neutralise. Neither may be stamped, and
  // neither may throw.
  const err = { error: 'ref missing' };
  const stamped = applyNativeFontParityGate(['requires-non-latin-font-parity'],
    { 'web-ref': null, 'ios-ref': null, 'android-ref': err }, LISTED);
  assert.deepEqual(stamped, []);
  assert.equal(err.scoreExcluded, undefined);
  // …and a missing diffs bag at all is a no-op, not a crash.
  assert.deepEqual(applyNativeFontParityGate(['requires-non-latin-font-parity'], undefined, LISTED), []);
});

test('wave30 B4b: an untagged test (or no tags at all) is never touched', () => {
  // Even a LISTED test needs the tag to arm the gate — the refusal list
  // narrows the tag's match set, it never replaces the tag.
  const ios = { ssim: 0.80, wptPass: false };
  assert.deepEqual(applyNativeFontParityGate(['requires-form-control-rendering'], { 'ios-ref': ios }, LISTED), []);
  assert.deepEqual(applyNativeFontParityGate(undefined, { 'ios-ref': ios }, LISTED), []);
  assert.deepEqual(applyNativeFontParityGate([], { 'ios-ref': ios }, LISTED), []);
  assert.equal(ios.wptPass, false);
  assert.equal(ios.scoreExcluded, undefined);
});

test('wave30 B4b: the tag does NOT belong to any WHOLE-TEST exclusion family', () => {
  // scoreEligible must stay TRUE — the test is still scored, on web. If this
  // tag ever entered SCORE_EXCLUDED/WALL/REF_UNACHIEVABLE, applyNaScoreGate
  // would null the web verdict too and the family's one honest column (which
  // includes armenian 008's REAL 0.9435 web failure) would vanish.
  const web = { ssim: 0.9791, wptPass: true };
  const ios = { ssim: 0.8807, wptPass: false };
  assert.equal(applyNaScoreGate(['requires-non-latin-font-parity'], [web, ios], []), false);
  assert.equal(web.wptPass, true);
  assert.equal(web.scoreExcluded, undefined);
  assert.equal(ios.scoreExcluded, undefined);   // untouched by THAT gate
  assert.equal(EXTRACTION_WALL_TAGS.has('requires-non-latin-font-parity'), false);
  assert.equal(REF_UNACHIEVABLE_TAGS.has('requires-non-latin-font-parity'), false);
});

test('wave30 B4b: the stamp is TRUTHY so every existing aggregator filter holds', () => {
  // aggregate-sections.mjs and the corpus recipes filter with `if
  // (d.scoreExcluded)`. The string keeps them byte-for-byte correct while
  // recording WHY one platform left a denominator its sibling stayed in —
  // `true` would make a font boundary indistinguishable from a delivery gap.
  // cambodian 159's wave48-w2 ios cell — a retained refusal-list member.
  const ios = { ssim: 0.8758, wptPass: false };
  applyNativeFontParityGate(['requires-non-latin-font-parity'], { 'ios-ref': ios },
    'css/css-counter-styles/cambodian/css3-counter-styles-159.html');
  assert.ok(ios.scoreExcluded, 'stamp must be truthy for the existing filters');
  assert.notEqual(ios.scoreExcluded, true, 'the reason must be readable off the diff');
});

test('wave30 B4b: buildResults source carries the CALLER CONTRACT + the result field', async () => {
  // Source-scan pin, same discipline as the wave-8 `scoreEligible: !isNa`
  // pin above — and for the same reason: applyNativeFontParityGate's JSDoc
  // states a CALLER CONTRACT ("run this only when applyNaScoreGate did NOT
  // already exclude the whole test") that no unit assertion could reach,
  // because the pure function cannot see whether the whole-test gate fired.
  //
  // MEASURED GAP THIS CLOSES (skeptic-3, wave-30): deleting the `isNa ? [] :`
  // guard from buildResults left all 89 tests in this file green. With the
  // guard gone, a test carrying BOTH a whole-test family (undeliverable
  // asset / extraction wall / unachievable ref) and this one would have its
  // `scoreExcluded: true` OVERWRITTEN with 'native-font-parity' on both
  // natives — silently downgrading "the harness never delivered the inputs"
  // to "the natives lack a face", which is exactly the confusion the string
  // stamp exists to prevent. Zero tests carry both families today; this pin
  // is what keeps that from becoming a silent wrong answer when one does.
  const src = await fs.readFile(new URL('./inject-wpt-block.mjs', import.meta.url), 'utf8');
  assert.match(src, /const fontParityExcluded = isNa \? \[\] : applyNativeFontParityGate\(naTags, \{/,
    'the per-platform gate must run ONLY when the whole-test gate did not fire');
  // wave-48 W2: the pipeline must hand the gate the test identity, or the
  // decline-on-unknown default silently turns the whole family off.
  assert.match(src, /\}, testRel\);\s+\/\/ wave-48 W2/,
    'the call site must pass testRel — the gate is per-test now');
  // …and the per-test result must surface which platforms it stamped, or a
  // manifest reader sees `scoreEligible: true` with no way to learn that two
  // of the three columns left the denominator.
  assert.match(src, /nativeFontParityExcluded: fontParityExcluded,/,
    'results must expose nativeFontParityExcluded');
});

// ── wave-36 M6: THE REFUSED FAMILY — a NEGATIVE pin ─────────────────────────
//
// Every test above pins what an exclusion family CONTAINS. This one pins what
// no family may contain. `requires-print-medium` was formally proposed for an
// exclusion family in wave-36 (the mining map's #5 opportunity, 136 failing
// cells) and REFUSED on measurement: over all 275 scored tests carrying the
// tag, the refs' own headless Chromium — rendering the TEST page under
// capture-browser-ref.mjs's exact canvas contract — reaches the committed ref
// on 193, including 140 of the 141 cells our pipeline already passes. The
// refs are rasterised in SCREEN medium (capture-browser-ref.mjs never calls
// emulateMediaType('print')), so for most `-print` tests pagination never
// enters the diff at all. Precision of the proposed exclusion: 0.298.
//
// Without this pin the refusal lives only in prose, and prose does not fail a
// build. The full rationale is on REFUSED_EXCLUSION_TAGS in the module and in
// wpt-not-applicable.mjs's Rule 5 banner.
//
// wave-37 W5 added the second member, `requires-view-transitions`, refused on
// the same discipline and a different number: the ::view-transition pseudo
// tree IS unreachable by construction (UA-generated, top-layer, holds snapshot
// images — never in the DOM, so no extractor or post-load bake can serialize
// it), but 50 of the 192 scored css-view-transitions cells pass anyway, 47 of
// them with real ink and 21 at SSIM exactly 1.0000, because a transition
// frozen at `.ready` shows the OLD snapshot = the pre-script DOM. Precision of
// the proposed exclusion: 142/192 = 0.740, against 1.000 for Rule 42 and the
// font-face wall. Ten narrowings were measured; the best was 0.898 (wave-36
// M6's own normalised test-vs-ref source-delta device, 59 fires). The full
// table is tools/titan/results/wave37-W5-decisions.json.

import {
  REFUSED_EXCLUSION_TAGS, SCORE_EXCLUDED_TAGS,
} from './inject-wpt-block.mjs';

test('wave36 M6: the refused tags are in NO exclusion family', () => {
  assert.deepEqual([...REFUSED_EXCLUSION_TAGS],
    ['requires-print-medium', 'requires-view-transitions']);
  const families = {
    SCORE_EXCLUDED_TAGS,
    EXTRACTION_WALL_TAGS,
    FONT_FACE_WALL_TAGS,
    REF_UNACHIEVABLE_TAGS,
    NATIVE_FONT_PARITY_TAGS,
  };
  for (const tag of REFUSED_EXCLUSION_TAGS) {
    for (const [name, set] of Object.entries(families)) {
      assert.equal(set.has(tag), false,
        `${tag} was measured and REFUSED — it must not be in ${name}`);
    }
  }
});

test('wave36 M6: a print-medium tag alone never score-excludes a test', () => {
  // End-to-end through the gate, with the three delivery stamps in their
  // most exclusion-friendly state (all false / absent): a test whose ONLY
  // notApplicable tag is requires-print-medium keeps its score.
  const web = { ssim: 0.9278, wptPass: false };
  assert.equal(applyNaScoreGate(['requires-print-medium'], [web]), false);
  assert.equal(web.wptPass, false);
  assert.equal(web.scoreExcluded, undefined);
  // …and it does not become excludable in the company of the other broad
  // capability tags the 8 repaired CSS2/pagination tests actually carry.
  const web2 = { ssim: 0.9278, wptPass: false };
  assert.equal(applyNaScoreGate(
    ['requires-fragmentation', 'requires-print-medium', 'requires-table-layout'],
    [web2]), false);
  assert.equal(web2.wptPass, false);
});

// ── wave-37 W5: the VIEW-TRANSITIONS refusal, pinned ────────────────────────
//
// css-view-transitions was the mining map's worst big section (195 tests, 192
// scored, 26 % pass, 142 failing cells) and the obvious candidate for a fifth
// exclusion family: the View Transitions pseudo tree is genuinely unreachable
// — `::view-transition`, `::view-transition-group/-image-pair/-old/-new` are
// UA-generated top-layer boxes holding SNAPSHOT IMAGES, never in the DOM, so
// neither the static extractor nor post-load-extract.mjs's computed-style bake
// (it walks real elements) can serialize them.
//
// It is refused anyway, on the wave-8 denominator rule: the wall is not
// separable from the accidentally-deliverable subset. A transition frozen at
// `.ready` with `animation-play-state: paused` paints the OLD snapshot, which
// IS the pre-script DOM — css-view-transitions/escaped-name renders three
// green boxes on both sides (12.821 % ink each, SSIM 1.0000). 50 of the 192
// scored cells pass, 47 with real ink, 21 at exactly 1.0000. Precision of the
// whole-tag exclusion: 142/192 = 0.740.
//
// These pins hold the DECISION, not the prose: the tag must keep firing as a
// label, and must never gain the power to drop a test out of the denominator.

test('wave37 W5: a view-transitions tag alone never score-excludes a test', () => {
  // The exact shape of the 142 failing cells: tagged, no delivery stamp that
  // could re-admit it, and still scored. If a future wave adds the tag to any
  // exclusion family this flips and the 50 passing cells vanish silently.
  const web = { ssim: 0.8449, wptPass: false };
  assert.equal(applyNaScoreGate(['requires-view-transitions'], [web]), false);
  assert.equal(web.wptPass, false);
  assert.equal(web.scoreExcluded, undefined);
});

test('wave37 W5: the view-transitions tag does not smuggle in a wall exclusion', () => {
  // 92 of the 195 section tests ALSO carry requires-script-mutation, a real
  // EXTRACTION_WALL member — but 178 of them bake post-load, and the stamp is
  // what re-admits them. The pin holds both arms so neither the wall nor the
  // view-transition label can be read as doing the other's job.
  const baked = { ssim: 1, wptPass: true };
  assert.equal(applyNaScoreGate(
    ['requires-script-mutation', 'requires-view-transitions'], [baked],
    undefined, /* postLoadExtracted */ true), false);
  assert.equal(baked.wptPass, true, 'a post-load-baked test stays scored');

  const unbaked = { ssim: 0.3056, wptPass: false };
  assert.equal(applyNaScoreGate(
    ['requires-script-mutation', 'requires-view-transitions'], [unbaked]), true,
    'without the stamp the SCRIPT wall excludes — not the view-transition tag');
  assert.equal(unbaked.scoreExcluded, true);
});

test('wave37 W5: the print refusal covers the @page-canvas proposal too', async () => {
  // Wave-36 refused the print EXCLUSION; wave-37 refused the print
  // SIMULATION (honour `@page { size }` on the composed canvas). Both live on
  // the same constant, and the second is only defensible while the refs stay
  // screen-medium — so the pin names the fact it rests on.
  const src = await fs.readFile(new URL('./inject-wpt-block.mjs', import.meta.url), 'utf8');
  assert.match(src, /ALL 157 css-page ref PNGs are 390 px wide/,
    'the @page-canvas refusal must record the measurement that refused it');
  const refSrc = await fs.readFile(
    new URL('./capture-browser-ref.mjs', import.meta.url), 'utf8');
  assert.equal(/emulateMediaType/.test(refSrc), false,
    'refs are rasterised in SCREEN medium — the moment that changes, the ' +
    '@page-canvas refusal has to be re-measured, not inherited');
});

test('wave37 W5: the refusal denominators are re-derivable from the committed map', async () => {
  // The two refusals rest on counts, and counts rot. `webmap-v1.json` is the
  // committed full-corpus web map both were measured on (the per-run
  // manifests under tools/titan/runs/ are gitignored, so it is the only
  // durable source), and this pin recomputes the precision figures quoted on
  // REFUSED_EXCLUSION_TAGS straight from it. If a future map re-pin moves the
  // numbers, this fails and the refusal gets re-argued instead of inherited.
  const map = JSON.parse(await fs.readFile(
    new URL('./results/webmap-v1.json', import.meta.url), 'utf8'));
  const by = Object.fromEntries(map.sections.map((s) => [s.section, s]));

  const vt = by['css-view-transitions'];
  assert.equal(vt.scored, 192);
  assert.equal(vt.pass, 50);
  assert.equal(vt.fail, 142);
  // Precision of the whole-tag exclusion = failing / scored. 0.740 is far
  // under the ≥0.95 bar the accepted families sit at, and every narrowing
  // measured in wave-37 topped out at 0.898.
  assert.equal(Number((vt.fail / vt.scored).toFixed(3)), 0.740);

  const pg = by['css-page'];
  assert.equal(pg.scored, 157);
  assert.equal(pg.pass, 71);
  assert.equal(pg.fail, 86);
  // The paged section is scored end to end — no test in it is admitted-out by
  // an exclusion rule, which is the wave-36 M6 decision still holding.
  assert.equal(pg.ineligible, 0);
});

test('wave37 W5: the decisions artifact agrees with the map it was derived from', async () => {
  // tools/titan/results/wave37-W5-decisions.json carries the per-test rows the
  // two refusals were argued from (the wave35-webmap manifests themselves are
  // gitignored, so it is the durable copy). It must not drift from the map:
  // an artifact that disagrees with webmap-v1 is worse than no artifact,
  // because the refusals cite its narrowing table by name.
  const here = (p) => new URL(p, import.meta.url);
  const map = JSON.parse(await fs.readFile(here('./results/webmap-v1.json'), 'utf8'));
  const art = JSON.parse(await fs.readFile(here('./results/wave37-W5-decisions.json'), 'utf8'));
  const by = Object.fromEntries(map.sections.map((s) => [s.section, s]));
  for (const section of ['css-view-transitions', 'css-page']) {
    const d = art.decisions[section];
    assert.equal(d.scored, by[section].scored, `${section} scored`);
    assert.equal(d.pass, by[section].pass, `${section} pass`);
    assert.equal(d.fail, by[section].fail, `${section} fail`);
    assert.equal(art.rows[section].length, by[section].tests, `${section} row count`);
  }
  // The two facts the refusals actually turn on, held as data rather than prose.
  assert.deepEqual(art.decisions['css-page'].distinctRefPngWidths, [390],
    'every css-page ref is a screen-medium render at the canvas width');
  assert.equal(art.decisions['css-view-transitions'].precisionOfWholeTagExclusion, 0.74);
  assert.ok(art.decisions['css-view-transitions'].narrowingsMeasured
    .every((n) => n.precision === null || n.precision < 0.95),
    'no narrowing may reach the bar while the refusal stands');
});

test('wave37 W5: the at-HEAD verification runs lost no cells and only strengthen the refusal', async () => {
  // Both sections were re-run web-only at HEAD (run-id wave37-W5) against the
  // pre-FX map. The lane changes nothing the pipeline executes, so the bar is
  // absolute: zero cells lost. And because every extractor improvement grows
  // the accidentally-deliverable subset, the view-transition exclusion's
  // precision must MOVE DOWN, never up — if it ever climbs past 0.95 the
  // refusal is due for a re-argument rather than an inheritance.
  const art = JSON.parse(await fs.readFile(
    new URL('./results/wave37-W5-decisions.json', import.meta.url), 'utf8'));
  for (const section of ['css-page', 'css-view-transitions']) {
    const v = art.verification[section];
    assert.equal(v.cellsLost, 0, `${section} must lose no cells`);
    assert.ok(v.head.pass >= v.map.pass, `${section} must not regress vs the map`);
    assert.equal(v.head.scored, v.map.scored, `${section} denominator must not move`);
  }
  const vt = art.verification['css-view-transitions'];
  assert.equal(vt.precisionOfWholeTagExclusionAtHead, 0.719);
  assert.ok(vt.precisionOfWholeTagExclusionAtHead
    < art.decisions['css-view-transitions'].precisionOfWholeTagExclusion,
    'a better extractor must weaken the exclusion case, not strengthen it');
});

// ── wave-49 NOVEL-INK wiring + BACKLOG #5 column presence ──────────────────
//
// novel-ink.mjs owns the metric and carries its own suite; these pins cover
// only what THIS module does with it: stamp it on every diff, keep it OUT of
// wptPass unless the operator opts in, and refuse to report a run that lost a
// whole platform column.

import {
  NOVEL_INK_VETO_ENABLED, novelInkVetoActive,
  assertPlatformColumns, PLATFORM_COLUMN_KEYS,
} from './inject-wpt-block.mjs';

test('novel-ink veto is OFF unless TITAN_NOVEL_INK_VETO=1', () => {
  // The wave-49 calibration cleared the false-positive bar (0 fires on 51
  // hand-verified correct renders) but MISSED the recall bar (12/18 on the
  // census-SELECTED defect set, 2/12 on an unbiased one, vs 95 % required), so
  // the campaign rule says ship it dark. This suite runs without the env var
  // set, so the switch must read false and the gate helper must swallow even a
  // true stamp.
  assert.equal(NOVEL_INK_VETO_ENABLED, process.env.TITAN_NOVEL_INK_VETO === '1');
  if (!NOVEL_INK_VETO_ENABLED) {
    assert.equal(novelInkVetoActive(true), false, 'a true stamp must not veto while dark');
  }
  // Non-true stamps never veto, in either mode.
  assert.equal(novelInkVetoActive(false), false);
  assert.equal(novelInkVetoActive(undefined), false);
});

test('computeWptPass: the sixth argument is a real unconditional veto', () => {
  // Proves the wiring point itself can fail — a switch nobody can flip is not
  // a switch. A pixel-perfect pair (ssim 1) is a pass until the novel-ink veto
  // is handed in, then it is a fail regardless of ssim or fuzzy.
  assert.equal(computeWptPass(1, null, false, false, false, false), true);
  assert.equal(computeWptPass(1, null, false, false, false, true), false);
  // A declared fuzzy match cannot rescue it either (same stance as the other
  // three vetoes above it).
  assert.equal(computeWptPass(0.1, true, false, false, false, true), false);
  // Omitting the argument keeps every legacy five-argument call shape passing.
  assert.equal(computeWptPass(1, null, false, false, false), true);
});

test('assertPlatformColumns: a silently-empty column is a failure, a declared skip is not', () => {
  // BACKLOG #5. The 327-net once went green with the whole Android column
  // missing; "no rows" read as "no failures". The eligibility idiom used here
  // is the scorer's own: numeric ssim and not scoreExcluded.
  const scored = (ssim) => ({ ssim });
  const results = {
    t1: { browserRef: { diffs: { 'web-ref': scored(0.99), 'ios-ref': scored(0.98) } } },
    t2: { browserRef: { diffs: { 'web-ref': scored(0.97), 'ios-ref': scored(0.96) } } },
  };
  const bad = assertPlatformColumns(results, {});
  assert.deepEqual(bad.counts, { 'web-ref': 2, 'ios-ref': 2, 'android-ref': 0 });
  assert.deepEqual(bad.missing, ['android-ref'], 'the empty column must be named');
  // Declaring the skip makes the same manifest honest.
  const declared = assertPlatformColumns(results, { SKIP_ANDROID: '1' });
  assert.deepEqual(declared.missing, []);
  // Every switch name must match the ones test-all.sh reads, or the escape
  // hatch silently does nothing.
  assert.deepEqual(PLATFORM_COLUMN_KEYS,
    { 'web-ref': 'SKIP_WEB', 'ios-ref': 'SKIP_IOS', 'android-ref': 'SKIP_ANDROID' });
});

test('column presence: fatal only on opt-in, so --web-only sections keep running', async () => {
  // section-runner --web-only invokes inject with empty native dirs, does NOT
  // declare SKIP_IOS/SKIP_ANDROID, and runs under `set -e`. A bare non-zero
  // exit would abort every web-only section, so the assertion warns by default
  // and only bites under TITAN_REQUIRE_ALL_COLUMNS=1. Pinned against the
  // module source because the branch lives inside main()'s tail.
  const src = await fs.readFile(new URL('./inject-wpt-block.mjs', import.meta.url), 'utf8');
  assert.match(src, /TITAN_REQUIRE_ALL_COLUMNS === '1'/, 'opt-in switch missing');
  assert.match(src, /if \(fatal\) process\.exitCode = 3;/, 'exit must be gated on the switch');
  // The warning itself is unconditional — that is the part that would have
  // caught the silently-Android-less 327-net.
  assert.match(src, /produced ZERO scored browser-ref diffs/, 'the signal must always be emitted');
});

test('assertPlatformColumns: excluded and error cells do not prop a column up', () => {
  // A column whose only rows are score-excluded (not-applicable gate) or
  // error-shaped carries no evidence — it must still register as missing,
  // otherwise the assertion is defeated by exactly the rows it should ignore.
  const results = {
    t1: {
      browserRef: {
        diffs: {
          'web-ref': { ssim: 0.99 },
          'ios-ref': { ssim: 0.99, scoreExcluded: true },
          'android-ref': { error: 'boom' },
        },
      },
    },
  };
  const r = assertPlatformColumns(results, {});
  assert.deepEqual(r.counts, { 'web-ref': 1, 'ios-ref': 0, 'android-ref': 0 });
  assert.deepEqual(r.missing.sort(), ['android-ref', 'ios-ref']);
  // All-empty is a DIFFERENT failure (nothing captured at all) and must not be
  // reported as three missing columns — that would fire on every dry run.
  assert.deepEqual(assertPlatformColumns({}, {}).missing, []);
});

test('diffComposedVsRef stamps novelInk + novelInkFailed on every diff', async () => {
  // The block must ride along for triage even while the veto is dark, so a
  // manifest row can be queried for the wrong-answer signal without re-running
  // the metrics. Identical composed capture vs ref → zero novel ink.
  const dir = await tmpDir('novelink-composed');
  const refDir = await tmpDir('novelink-ref');
  const testKey = 'wpt__css-color__t-049';
  await writePng(dir, `${safe(testKey)}.png`, 40, 40, [0, 128, 0, 255]);
  const refPng = await writePng(refDir, 'ref.png', 40, 40, [0, 128, 0, 255]);
  const diff = await diffComposedVsRef({ platformDir: dir, testKey, refPng, fuzzy: null });
  assert.ok(diff.novelInk, 'novelInk block must be stamped');
  assert.equal(diff.novelInk.divergentPx, 0);
  assert.equal(diff.novelInkFailed, false);
  assert.equal(diff.wptPass, true);
});

test('EXECUTED REPRO: the wave-48 hole, and that the switch closes it', async () => {
  // Reproduces css-view-transitions/hit-test-unrelated-element's geometry
  // exactly: a 390x600 white canvas, a 100x100 blue block at (216,16) and a
  // 100x100 GREEN block at (216,316); the capture is identical except the
  // green block is pure RED. That is the shape whose real corpus cell scored
  // ssim 1.0000 / colorDivergent FALSE / wptPass TRUE on all three platforms.
  const dir = await tmpDir('wave48-hole');
  const mk = (fill) => {
    const p = new PNG({ width: 390, height: 600 });
    p.data.fill(0xFF);
    const paint = (x0, y0, w, h, c) => {
      for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) {
        const i = (y * 390 + x) * 4;
        p.data[i] = c[0]; p.data[i + 1] = c[1]; p.data[i + 2] = c[2]; p.data[i + 3] = 255;
      }
    };
    paint(216, 16, 100, 100, [0, 0, 255]);
    paint(216, 316, 100, 100, fill);
    return p;
  };
  const testKey = 'wpt__css-view-transitions__hole-repro';
  await fs.writeFile(join(dir, `${safe(testKey)}.png`), PNG.sync.write(mk([255, 0, 0])));
  const refPng = join(dir, 'ref.png');
  await fs.writeFile(refPng, PNG.sync.write(mk([0, 128, 0])));
  const diff = await diffComposedVsRef({ platformDir: dir, testKey, refPng, fuzzy: null });

  // The hole, measured: structure is perfect and the colour veto sleeps
  // because whole-canvas histogram KL is coverage-weighted and the swap covers
  // only 4.27 % of the frame.
  assert.equal(diff.ssim, 1, 'SSIM is blind to hue');
  assert.equal(diff.colorDivergent, false, 'histogram KL stays under its bar');
  assert.equal(diff.colorFailed, false, 'so the wave-25 colour veto never arms');
  // The instrument DOES know: 10 000 pixels at a full 255 channel delta.
  assert.equal(diff.fuzzyDifferingPixels, 100 * 100);
  assert.equal(diff.fuzzyMaxChannelDelta, 255);
  // The wave-49 measurement names it, area-normalized on the divergence.
  assert.equal(diff.novelInkFailed, true);
  assert.equal(diff.novelInk.novelFractionOfDivergentInk, 1);
  // And, with the veto dark (this suite's environment), the pair STILL passes.
  // That is the honest state of the gate as shipped, pinned so nobody reads
  // the presence of novel-ink code as the hole being closed.
  if (!NOVEL_INK_VETO_ENABLED) {
    assert.equal(diff.wptPass, true, 'veto is off by default — the hole is open');
  }
  // Flipping the switch is what closes it: same inputs, opposite verdict.
  assert.equal(computeWptPass(diff.ssim, diff.wptFuzzyMatch, diff.presenceFailed,
    diff.colorFailed, diff.coverageRatioFailed, true), false);
});

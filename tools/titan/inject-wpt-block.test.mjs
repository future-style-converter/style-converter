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
  // wave-13: the gate call must ALSO thread the extractor's delivery record
  // (meta.lossyReasons) so the requires-bundled-asset exclusion stays
  // delivery-aware. wave-16: AND the post-load delivery stamp
  // (meta.postLoadExtracted, strict-true) so wall-tagged tests whose
  // post-script state post-load-extract.mjs delivered are re-scored — see
  // the wave-16 gate-interplay tests below. wave-20: AND the structure
  // stamp (meta.structureExtracted, strict-true) — the appendChild family's
  // delivery record, treated exactly like the state stamp.
  assert.match(src, /applyNaScoreGate\(naTags,\s*\[webRefDiff,\s*iosRefDiff,\s*androidRefDiff\],\s*meta\.lossyReasons,\s*meta\.postLoadExtracted\s*===\s*true,\s*meta\.structureExtracted\s*===\s*true\)/,
    'the NA gate must neutralise all three browser-ref diffs AND receive the delivery record AND both post-load stamps');
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

test('wave15: EXTRACTION_WALL_TAGS is exactly the two script-execution tags', () => {
  // Pin the exact set — silently widening the wall (excluding more tests)
  // or narrowing it (re-scoring the wall) must break a test first.
  assert.deepEqual([...EXTRACTION_WALL_TAGS].sort(),
    ['requires-script-driven-scroll', 'requires-script-mutation']);
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

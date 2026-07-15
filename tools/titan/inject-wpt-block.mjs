#!/usr/bin/env node
//
// tools/titan/inject-wpt-block.mjs
//
// Post-processes the manifest produced by compare-screenshots.mjs into
// the v4 shape spec'd in TITAN_ARCHITECTURE.md §7.1. Adds a top-level
// `wpt:` block with per-test results and bumps `manifestVersion: 3 → 4`.
//
// Why this is a separate post-processor (rather than baking it into
// compare-screenshots.mjs):
//   1. Compare-screenshots is shared with the existing 327-pair regression
//      pipeline — adding a per-row `wpt` lookup there would slow it down
//      for runs that have no WPT context.
//   2. The mapping from "manifest row" to "WPT test" is purely a
//      run-time concern (depends on which combined fixture you fed in).
//      A post-processor keeps that knowledge OUT of the canonical
//      comparison module.
//   3. compare-screenshots.mjs's `manifest.wpt` field is set to `null`
//      for non-WPT runs — the v4 bump (Section 7.1) is backward-compatible.
//      We could instead emit v3 here and rely on the absence of `wpt:`,
//      but spec is explicit: bump version to signal the new shape.
//
// Usage:
//   node inject-wpt-block.mjs --manifest <PATH> --tests <SMOKE> \
//       --wpt-ref <SHA> --run-id <ISO> --refs-root <PATH> \
//       --capture-log <PATH>
//
// Reads:
//   - manifest.json (v3 from compare-screenshots)
//   - SMOKE_TESTS list
//   - fixtures/wpt/_smoke-combined.json (for the keyMap from
//     build-combined-fixture)
//   - tools/titan/wpt-buckets.json (for bucket reasons of skipped tests)
//
// Writes:
//   - same manifest path, in-place, with v4 shape (atomic write via
//     temp + rename)
//
// Exit codes:
//   0 — wrote v4 manifest
//   1 — usage / input
//   2 — IO error

import { promises as fs } from 'node:fs';
import { resolve, dirname, join, basename } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';
import sharp from 'sharp';
import pixelmatchDefault from 'pixelmatch';
import { ssim as computeSsim } from 'ssim.js';
import { classifyDivergence } from '../visual/classify-divergence.mjs';
import {
  computeDssim,
  computeHistogramKL,
  computePerChannelSsim,
  computePHash,
  computeEdgeSsim,
  computeLabDeltaE,
} from '../visual/compare-screenshots-metrics.mjs';
// The ONE canonical compare-pipeline sanitiser (dot KEPT). This module is the
// consumer side of the pipeline — its diff globs MUST use the exact same rule
// the feeders/web capture used to WRITE the filenames, or a key with a "."
// silently drops a platform column. Sharing the helper guarantees agreement.
import { safe } from './safe-name.mjs';

const pixelmatch = pixelmatchDefault.default ?? pixelmatchDefault;

const __filename = fileURLToPath(import.meta.url);
const __dirname  = dirname(__filename);
const REPO_ROOT  = resolve(__dirname, '..', '..');

function arg(name) {
  const i = process.argv.indexOf(name);
  return i >= 0 ? process.argv[i + 1] : null;
}

// CLI args parsed lazily inside main() so importing this file from unit
// tests (which want only the pure helpers like stitchPngsVertically) doesn't
// trip the usage-check exit. Each main()-only constant is shadowed inside
// the function below.
const MANIFEST_PATH = arg('--manifest');
const TESTS_FILE    = arg('--tests');
const WPT_REF       = arg('--wpt-ref');
const RUN_ID        = arg('--run-id') || new Date().toISOString();
const REFS_ROOT     = arg('--refs-root');     // e.g. tools/wpt/refs/<sha>
const CAPTURE_LOG   = arg('--capture-log');   // for duration
// --combined / --web-dir let the section-runner point at per-section paths.
// Default mirrors Phase 1's _smoke-combined fixture so run-titan.sh keeps
// working unchanged.
const COMBINED_ARG  = arg('--combined');
const WEB_DIR_ARG   = arg('--web-dir');
// Phase-4 native-vs-ref pairs: iOS/Android capture dirs. All three
// platforms write IDENTICAL per-component filenames into their own dir
// (collectCaptures() in tools/visual/compare-screenshots.mjs keys rows by
// raw filename), so the same glob/stitch/diff path serves every platform.
// Defaults are the live harness dirs; a platform with no matching PNGs
// simply contributes no diff (web-only runs behave exactly as before).
const IOS_DIR_ARG     = arg('--ios-dir');
const ANDROID_DIR_ARG = arg('--android-dir');

// True when the script is invoked directly (`node inject-wpt-block.mjs ...`);
// false when imported as a module (e.g. inject-wpt-block.test.mjs). Reused
// to gate the usage-check below and the bottom-of-file main() invocation.
const IS_CLI = process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1]);

if (IS_CLI && (!MANIFEST_PATH || !TESTS_FILE || !WPT_REF)) {
  console.error('usage: inject-wpt-block.mjs --manifest <PATH> --tests <FILE> --wpt-ref <SHA> [--run-id <ISO>] [--refs-root <PATH>] [--capture-log <PATH>] [--combined <PATH>] [--web-dir <PATH>] [--ios-dir <PATH>] [--android-dir <PATH>]');
  process.exit(1);
}

const COMBINED_FIXTURE = COMBINED_ARG
  ? resolve(COMBINED_ARG)
  : join(REPO_ROOT, 'fixtures', 'wpt', '_smoke-combined.json');
const BUCKETS_PATH     = join(REPO_ROOT, 'tools', 'titan', 'wpt-buckets.json');

/** Build per-test rollup entries from the manifest rows array. */
function rowIndex(rows) {
  // compare-screenshots names rows by capture filename: "<NNN>_<safeName>.png".
  // We need to look them up by safeName (== component key with non-safe chars
  // replaced). Build an index keyed on the bare-name part (no padding, no png).
  const ix = {};
  for (const r of rows) {
    if (!r?.name) continue;
    const m = /^(\d+)_(.+)\.png$/.exec(r.name);
    if (!m) {
      ix[r.name] = r;
      continue;
    }
    const safeName = m[2];
    ix[safeName] = r;
  }
  return ix;
}

// `safe()` (the compare-pipeline sanitiser) is imported from
// ./safe-name.mjs above — the single source of truth shared with the feeders
// and web capture drivers. It is re-exported at the bottom of this file so
// existing `import { safe } from './inject-wpt-block.mjs'` call sites and
// tests keep resolving to the one implementation.

// ── PNG helpers (mirror compare-screenshots.mjs's padToCanvas + diffPair) ───

async function loadPng(p) {
  const buf = await fs.readFile(p);
  return PNG.sync.read(buf);
}

/** Pad to (W,H) with the canonical #1A1A2E background — matches
 *  compare-screenshots.mjs so browser-ref pairs are directly comparable to
 *  inter-platform pairs. */
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

/**
 * Stitch a list of PNG files vertically into a single composed PNG.
 *
 * Returns the path to the stitched PNG, cached in `cacheDir/<cacheKey>.png`
 * so re-runs are O(1). Width = max(inputs[].width); height = sum of input
 * heights. Inputs narrower than the canvas are left-aligned and padded with
 * the canonical #1A1A2E background (matches padToCanvas + CaptureCanvas's
 * own background so seams are invisible).
 *
 * Rationale (swarm-002 RC2, tools/titan/investigations/swarm-002/
 * css-overflow__clip-002.json): the legacy inject-wpt-block compared ONLY
 * the FIRST per-component capture (e.g. clip-002's 003_..__0.png at 390x182)
 * against the full 390x600 browser-ref. The smaller PNG was padded with
 * dark background, then pixelmatch + pHash + SSIM ran. pixelMismatch came
 * back ~0 (most of the padded area was identical to ref background), but
 * pHash fingerprints macro structure and reported hammingDistance=32 — one
 * box vs three. That tripped classify-divergence.mjs's
 * `STRUCTURAL_PHASH_MIN` gate → false structural-divergence.
 *
 * Stitching all per-component captures into a single composed PNG before
 * diffing means the diff sees the same number of painted regions on both
 * sides — pHash distance drops to noise, the false positive goes away.
 *
 * Cache key includes the input paths' mtimes + sizes so an out-of-date
 * cached stitch is rebuilt automatically when a capture re-runs.
 */
async function stitchPngsVertically(inputPaths, cacheDir, cacheKey) {
  // Single input → nothing to stitch; return the input path verbatim.
  // Pre-stitch optimisation; also keeps single-component tests identical
  // to the legacy diff path so we don't introduce noise where there is none.
  if (inputPaths.length === 1) return inputPaths[0];
  // Decode every input PNG once. PNG.sync.read returns a plain object with
  // { width, height, data: Buffer<RGBA8888> } — no bitblt method, so we
  // copy bytes manually below.
  const metas = await Promise.all(inputPaths.map(async (p) => {
    const buf = await fs.readFile(p);
    const png = PNG.sync.read(buf);
    return { path: p, png, width: png.width, height: png.height };
  }));
  // Canvas width = max input width; height = sum of input heights.
  const W = Math.max(...metas.map((m) => m.width));
  const H = metas.reduce((s, m) => s + m.height, 0);
  // Build a fresh PNG buffer initialised to the canonical #1A1A2E background
  // so any sub-canvas gap is invisible (matches CaptureCanvas's own bg).
  const composed = new PNG({ width: W, height: H });
  for (let i = 0; i < composed.data.length; i += 4) {
    composed.data[i]     = 0x1A;
    composed.data[i + 1] = 0x1A;
    composed.data[i + 2] = 0x2E;
    composed.data[i + 3] = 0xFF;
  }
  // Row-by-row copy into the composed buffer at the running y-offset,
  // left-aligned. Each row in the source covers `srcW * 4` bytes; we drop
  // them into the dest at offset `(y * W + 0) * 4`. Inputs narrower than W
  // leave the canvas-bg trail to the right of the copied row.
  let yOffset = 0;
  for (const m of metas) {
    const srcRowBytes = m.width * 4;
    for (let row = 0; row < m.height; row++) {
      const srcStart = row * srcRowBytes;
      const dstStart = ((yOffset + row) * W) * 4;
      m.png.data.copy(composed.data, dstStart, srcStart, srcStart + srcRowBytes);
    }
    yOffset += m.height;
  }
  // Cache the result so subsequent inject calls don't re-stitch.
  await fs.mkdir(cacheDir, { recursive: true });
  const outPath = join(cacheDir, `${cacheKey}.png`);
  await fs.writeFile(outPath, PNG.sync.write(composed));
  return outPath;
}

/** Compute a full B1–B7 metric block for a (web, ref) pair. Mirrors
 *  diffPair() in compare-screenshots.mjs but skips the diff-PNG write
 *  (we don't need it for the wpt: block — Phase 5's titan.html dashboard
 *  will re-derive on demand). */
async function diffWebVsRef(webPath, refPath) {
  const a = await loadPng(webPath);
  const b = await loadPng(refPath);
  const W = Math.max(a.width, b.width);
  const H = Math.max(a.height, b.height);
  const A = await padToCanvas(a, W, H);
  const B = await padToCanvas(b, W, H);

  // Pixelmatch with the same threshold as compare-screenshots.mjs.
  const diff = new PNG({ width: W, height: H });
  const mismatched = pixelmatch(A.data, B.data, diff.data, W, H, {
    threshold: 0.25,
    includeAA: true,
  });
  const pixelPct = (mismatched / (W * H)) * 100;

  // SSIM
  let ssimScore = null;
  try {
    const aImg = { data: new Uint8ClampedArray(A.data), width: W, height: H };
    const bImg = { data: new Uint8ClampedArray(B.data), width: W, height: H };
    const r = computeSsim(aImg, bImg, { ssim: 'fast' });
    ssimScore = +r.mssim.toFixed(4);
  } catch { /* leave null */ }

  const dssim = computeDssim(ssimScore);
  const histogramKL = computeHistogramKL(A, B);
  const perChannelSsim = await computePerChannelSsim(A, B);
  const pHash = await computePHash(A, B);
  const edgeSsim = await computeEdgeSsim(A, B);
  const labDeltaE = computeLabDeltaE(A, B, 4);

  const metrics = {
    pixelMismatchedCount: mismatched,
    pixelMismatchedPct: +pixelPct.toFixed(3),
    ssim: ssimScore,
    dssim,
    perChannelSsim,
    edgeSsim,
    histogramKL,
    pHash,
    labDeltaE,
  };
  metrics.divergence = classifyDivergence(metrics);
  return metrics;
}

/** Apply the WPT fuzzy tolerance (Section 5.3): if the test carries a
 *  <meta name="fuzzy">, check whether the browser-ref pair fits inside it.
 *  Returns true/false when a fuzzy range exists, null when the test declared
 *  none. The fuzzy shape is `{ maxDifference:{min,max}, totalPixels:{min,max} }`
 *  (extract-fixture.mjs). Does NOT change the classifier label — it feeds the
 *  wptPass verdict below. */
function checkFuzzyMatch(metrics, fuzzy) {
  if (!fuzzy) return null;
  const px = metrics.pixelMismatchedCount ?? Infinity;
  const maxDelta = metrics.labDeltaE?.max ?? Infinity;
  return (px <= fuzzy.totalPixels.max && maxDelta <= fuzzy.maxDifference.max);
}

/** The WPT-native PASS verdict for a browser-ref pair. A pair PASSES when the
 *  raw SSIM clears the 0.95 bar OR the diff fits inside the test's declared
 *  <meta fuzzy> tolerance — WPT's OWN reftest criterion (a pixel-exact/within-
 *  fuzz match is a pass upstream). `fuzzyMatch` is checkFuzzyMatch's result
 *  (true | false | null). This is recorded ALONGSIDE the raw `ssim` (which is
 *  never overwritten), so a future pass-rate computation can use the honest
 *  WPT bar without inflating the SSIM number itself. Pure + exported for
 *  unit tests. */
function computeWptPass(ssim, fuzzyMatch) {
  const ssimPass = typeof ssim === 'number' && ssim >= 0.95;
  return ssimPass || fuzzyMatch === true;
}

/** Glob ONE platform's capture dir for a test's per-component PNGs (all
 *  three platforms write identical `<idx>_<safeKey>.png` filenames into
 *  their own dir — see collectCaptures() in compare-screenshots.mjs),
 *  stitch them vertically (swarm-002 RC2 contract), and diff the
 *  composite against the browser-ref. Returns null when the platform has
 *  no matching captures (web-only runs, missing platform) — the wpt
 *  block then simply omits that pair instead of recording noise. */
async function diffPlatformVsRef({ platformDir, matchingKeys, refPng, fuzzy, cacheKey }) {
  if (!platformDir) return null;
  const files = await fs.readdir(platformDir).catch(() => []);
  // Match per IR component key in declared component-list order so the
  // stitch order mirrors the on-page/document order on every platform.
  const matched = [];
  for (const key of matchingKeys) {
    const safeKey = safe(key);
    const f = files.find((x) => x.endsWith(`_${safeKey}.png`));
    if (f) matched.push(join(platformDir, f));
  }
  if (matched.length === 0) return null;
  try {
    // Per-platform stitch cache — same layout the web path always used.
    const composed = await stitchPngsVertically(matched, join(platformDir, '_stitched'), cacheKey);
    const diff = await diffWebVsRef(composed, refPng);   // metric fn is platform-agnostic (PNG pair in, metrics out)
    diff.wptFuzzyMatch = checkFuzzyMatch(diff, fuzzy);
    // WPT-native pass: raw SSIM ≥ 0.95 OR within the declared fuzzy tolerance.
    // Raw `diff.ssim` is left untouched so downstream can honour both bars.
    diff.wptPass = computeWptPass(diff.ssim, diff.wptFuzzyMatch);
    diff.stitchedComponents = matched.length;
    return diff;
  } catch (err) {
    return { error: String(err?.message ?? err) };
  }
}

/** Diff a per-test COMPOSED web capture against the browser-ref, WITHOUT
 *  the vertical stitch.
 *
 *  The web harness's WPT_COMPOSED capture mode
 *  (apps/web-harness/src/ui/ComposedCaptureGallery.tsx) renders all of a
 *  test's components COMPOSED on ONE browser-ref-framed canvas and writes a
 *  single PNG named `<safe(testKey)>.png`, where testKey =
 *  `wpt__<section>__<stem>` (the same key split-combined-ir.mjs and the
 *  build-combined-fixture component naming use). That composite already
 *  reproduces the reference page's layout (bar heights, gaps, the 16px body
 *  padding), so we diff it DIRECTLY against the ref — no stitchPngsVertically,
 *  whose vertical concatenation of per-component crops is exactly the
 *  geometry error the composed mode removes.
 *
 *  Returns null when no composed PNG exists for this test (e.g. a
 *  per-component/legacy web capture) so the caller can fall back to the
 *  stitch path — nothing else breaks. On a real match the returned metric
 *  block carries `composed:true` so dashboards can tell the two web-ref
 *  diff provenances apart. */
async function diffComposedVsRef({ platformDir, testKey, refPng, fuzzy }) {
  if (!platformDir) return null;
  // The composed capture filename is the sanitised test key + .png. safe()
  // here is the SAME rule capture-screenshots.mjs (web) and the native
  // composed inbox capture apply to the canvas/test name. ONE helper serves
  // web + iOS + Android because all three emit an identically-named composed
  // PNG (`<safe(testKey)>.png`) in their own capture dir.
  const composedPath = join(platformDir, `${safe(testKey)}.png`);
  if (!_existsSync(composedPath)) return null;   // no composed capture → caller stitches
  try {
    const diff = await diffWebVsRef(composedPath, refPng);  // one composite PNG vs the ref
    diff.wptFuzzyMatch = checkFuzzyMatch(diff, fuzzy);       // Section 5.3 fuzzy tolerance
    // WPT-native pass: raw SSIM ≥ 0.95 OR within the declared fuzzy tolerance.
    diff.wptPass = computeWptPass(diff.ssim, diff.wptFuzzyMatch);
    diff.composed = true;                                    // provenance marker
    return diff;
  } catch (err) {
    return { error: String(err?.message ?? err) };
  }
}

/** Walk SMOKE_TESTS and assemble manifest.wpt.results from manifest rows
 *  + the keyMap saved by build-combined-fixture. */
async function buildResults({ tests, manifest, keyMap, bucketsIdx, refsRoot, webDir, iosDir, androidDir }) {
  const ix = rowIndex(manifest.rows ?? []);
  const results = {};
  let bucketA = 0, bucketB = 0, bucketC = 0;

  for (const testRel of tests) {
    // Find every component key that came from this test.
    const matchingKeys = Object.entries(keyMap)
      .filter(([_, v]) => v.test === testRel)
      .map(([k]) => k);

    if (matchingKeys.length === 0) {
      // Test wasn't in the combined fixture (e.g. extraction failed).
      // Skip — doesn't count toward A/B/C totals (it's an infra miss).
      results[testRel] = {
        bucket: 'missing-extraction',
        lossy: false,
        specSection: specSectionOf(testRel),
        components: [],
        pairs: null,
        browserRef: null,
        divergence: 'no-data',
      };
      continue;
    }

    // First-key wins for the test-level wpt metadata (all keys for a
    // given test share section/bucket/lossy/etc).
    const meta = keyMap[matchingKeys[0]];
    if (meta.bucket === 'A') bucketA++;
    else if (meta.bucket === 'B') bucketB++;
    else bucketC++;

    // Aggregate per-pair metrics from the matched manifest rows. For a
    // multi-component test we average the SSIM and union the divergence
    // labels (severest wins — see SEVERITY_RANK).
    const pairKinds = ['iOS-Android', 'iOS-web', 'Android-web'];
    const pairAccum = Object.fromEntries(pairKinds.map((k) => [k, []]));
    let anyMatched = false;
    for (const key of matchingKeys) {
      const row = ix[safe(key)] ?? ix[key];
      if (!row) continue;
      anyMatched = true;
      for (const k of pairKinds) {
        const p = row.pairs?.[k];
        if (p) pairAccum[k].push(p);
      }
    }

    const pairs = {};
    for (const k of pairKinds) {
      const list = pairAccum[k];
      if (list.length === 0) { pairs[k] = null; continue; }
      // Aggregate: take the worst (severest) pair across components for
      // this test. Worst SSIM, worst divergence label.
      const worstSsim = Math.min(...list.map((p) => p.ssim ?? 1));
      const sumPx = list.reduce((s, p) => s + (p.pixelMismatchedPct ?? 0), 0);
      const worstDiv = severestDivergence(list.map((p) => p.divergence));
      pairs[k] = {
        components: list.length,
        ssim: +worstSsim.toFixed(4),
        pixelMismatchedPctMean: +(sumPx / list.length).toFixed(4),
        divergence: worstDiv,
        // Pass-through of the first row's metric block so dashboards can
        // drill in to a representative pair without re-reading every
        // capture. Phase 4 will replace this with browser-ref pairs too.
        sample: list[0],
      };
    }

    // Browser-ref pairs. For the WPT test we render the upstream Chromium
    // ref once and compare EVERY captured platform against it (Phase 4:
    // web-ref + ios-ref + android-ref — the same "spec compliance" signal
    // per platform). Platforms with no captures this run contribute no
    // pair — the unattended web-only smoke behaves exactly as Phase 1 did.
    // The stitch-before-diff contract (swarm-002 RC2: compare a vertical
    // composite of ALL per-test captures, never just the first) lives in
    // diffPlatformVsRef and applies uniformly to all three platforms.
    const refStem = basename(testRel, '.html');
    const refPng = refsRoot
      ? join(refsRoot, meta.section, `${refStem}.png`)
      : null;
    const browserRefAvailable = refPng ? await_fs_exists_sync(refPng) : false;

    let webRefDiff = null, iosRefDiff = null, androidRefDiff = null;
    if (browserRefAvailable) {
      const cacheKey = safe(testRel.replace(/\.html$/, ''));
      // WPT test key = `wpt__<section>__<stem>` — matches the composed
      // capture filename the web harness emits (ComposedCaptureGallery.tsx)
      // and build-combined-fixture.mjs's `wpt__<section>__<stem>__<idx>`
      // root naming. section is parts[1] of the test path (meta.section);
      // stem is the .html basename (refStem, computed above).
      const testKey = `wpt__${meta.section}__${refStem}`;
      // Each platform prefers the COMPOSED single-PNG diff when its harness
      // produced one (`<safe(testKey)>.png` — WPT_COMPOSED web / composed
      // inbox natives), and falls back to the legacy per-component vertical
      // stitch when it didn't, so non-composed runs are byte-for-byte
      // unchanged. Same helper, same contract, all three platforms.
      const composedOrStitch = async (dir) => {
        const c = await diffComposedVsRef({ platformDir: dir, testKey, refPng, fuzzy: meta.fuzzy });
        return c ?? diffPlatformVsRef({ platformDir: dir, matchingKeys, refPng, fuzzy: meta.fuzzy, cacheKey });
      };
      webRefDiff = await composedOrStitch(webDir);
      iosRefDiff = await composedOrStitch(iosDir);
      androidRefDiff = await composedOrStitch(androidDir);
    }

    // Test-level divergence label = severest across all available signals.
    // When inter-platform pairs are missing (web-only mode), the browser-ref
    // pairs become the source of truth.
    const labelSources = pairKinds.map((k) => pairs[k]?.divergence).filter(Boolean);
    if (webRefDiff?.divergence) labelSources.push(webRefDiff.divergence);
    if (iosRefDiff?.divergence) labelSources.push(iosRefDiff.divergence);
    if (androidRefDiff?.divergence) labelSources.push(androidRefDiff.divergence);
    let divergence = labelSources.length ? severestDivergence(labelSources) : 'no-data';

    // FIX-E: auto-bucket override. If the WPT exclusion-rule scanner in
    // tools/titan/wpt-not-applicable.mjs (output: bucketsIdx.notApplicable)
    // matched this test against one or more architectural rules, override
    // the divergence label to `test-not-applicable` and surface the
    // matching tags. Rationale (full discussion in classify-divergence.mjs's
    // CLASSIFIER_VERSION v3 comment): a test that was never expected to
    // render correctly on Compose/SwiftUI shouldn't be SSIM-gated at all —
    // its perceptual metrics are noise. Without this override, the W4
    // manifest contained 3,676 tests labelled `mixed` / `structural-
    // divergence` / `no-data` that the bucketer had already flagged for
    // exclusion (counted at FIX-E implementation time), defeating the
    // FIX-D projection of 1,471 reclassifications.
    //
    // The override fires AFTER `divergence` is computed (rather than
    // skipping the metric aggregation entirely) so the existing per-pair
    // diagnostic data is still recorded for an investigator who wants to
    // drill into "what would this test have scored if rendered?". Only
    // the test-level label flips. Idempotent: re-running inject on a
    // manifest that already has the override applied produces the same
    // result.
    const naTags = bucketsIdx?.notApplicable?.[testRel];
    if (Array.isArray(naTags) && naTags.length > 0) {
      divergence = 'test-not-applicable';
    }

    results[testRel] = {
      bucket: meta.bucket,
      lossy: !!meta.lossy,
      lossyReasons: meta.lossyReasons ?? [],
      specSection: meta.section,
      components: matchingKeys,
      fuzzy: meta.fuzzy ?? null,
      pairs: anyMatched ? pairs : null,
      browserRef: {
        available: browserRefAvailable,
        path: refPng ? relativeFromRepo(refPng) : null,
        // Phase 4: one entry per captured platform, all against the SAME
        // Chromium browser-ref (the pipeline canvas is identical across
        // platforms by the CaptureCanvas contract, so no per-platform
        // re-render is needed). A platform with no captures this run is
        // simply absent — dashboards render that as an explicit gap.
        diffs: (webRefDiff || iosRefDiff || androidRefDiff)
          ? {
              ...(webRefDiff ? { 'web-ref': webRefDiff } : {}),
              ...(iosRefDiff ? { 'ios-ref': iosRefDiff } : {}),
              ...(androidRefDiff ? { 'android-ref': androidRefDiff } : {}),
            }
          : null,
      },
      divergence,
      browserRefDivergence: (webRefDiff?.divergence || iosRefDiff?.divergence || androidRefDiff?.divergence)
        ? {
            ...(webRefDiff?.divergence ? { web: webRefDiff.divergence } : {}),
            ...(iosRefDiff?.divergence ? { ios: iosRefDiff.divergence } : {}),
            ...(androidRefDiff?.divergence ? { android: androidRefDiff.divergence } : {}),
          }
        : null,
      // FIX-E: surface the matching exclusion tags so dashboards / reviewers
      // can group flipped tests by reason ("all 3,869 requires-inline-FC
      // hits", "all 1,718 requires-form-control-rendering hits", etc.).
      // Always present (empty array when no match) so consumers don't need
      // to null-check; the divergence label is the truth source for "did
      // the override fire" and the tags are the diagnostic colour.
      notApplicableTags: Array.isArray(naTags) ? naTags : [],
    };
  }

  return { results, totals: { A: bucketA, B: bucketB, C: bucketC } };
}

// Synchronous existsSync without importing fs/sync (we already have promises
// imported). Cheap helper that wraps statSync via a require trick is overkill;
// just import existsSync.
import { existsSync as _existsSync } from 'node:fs';
function await_fs_exists_sync(p) { return _existsSync(p); }

function relativeFromRepo(p) {
  return p.startsWith(REPO_ROOT) ? p.slice(REPO_ROOT.length + 1) : p;
}

const SEVERITY_RANK = {
  identical: 1,
  'sub-pixel-noise': 2,
  unknown: 3,
  mixed: 4,
  'edge-shift': 5,
  'color-drift': 6,
  'structural-divergence': 7,
  'no-content': 8,
  // FIX-E: out-of-scope wins over every real-signal label. A pair labelled
  // test-not-applicable should dominate any sibling pair's structural /
  // mixed / etc. signal because the test itself shouldn't be SSIM-gated.
  // Mirrors the SEVERITY_RANK in classify-divergence.mjs (kept as
  // independent literals to avoid an import cycle through the metrics
  // module — same trade-off the existing `unknown: 3` duplicate took).
  'test-not-applicable': 9,
  'no-data': 0,   // missing data ranks below identical so it doesn't override real signals
};
function severestDivergence(labels) {
  let best = 'no-data';
  let bestRank = -1;
  for (const l of labels) {
    const r = SEVERITY_RANK[l] ?? SEVERITY_RANK.unknown;
    if (r > bestRank) { best = l; bestRank = r; }
  }
  return best;
}

function specSectionOf(testRel) {
  const parts = testRel.split('/');
  return parts.length >= 3 ? parts[1] : 'css';
}

/** Build the skipped block from the bucket index: every test the orchestrator
 *  was asked to run that's bucket-C in the index gets a record with reason. */
function buildSkippedBlock(tests, bucketsIdx) {
  const out = {};
  const cSet = new Set(bucketsIdx?.buckets?.C ?? []);
  const reasons = bucketsIdx?.reasons ?? {};
  for (const t of tests) {
    if (cSet.has(t)) out[t] = reasons[t] ?? 'bucket-C (no reason recorded)';
  }
  return out;
}

/** Approximate run duration from the capture log's timing lines.
 *  test-all.sh prints "↳ <stage> took <N>s" per stage and a final
 *  "total elapsed: <N>s". We sum them best-effort; missing log → null. */
async function readDurations(capturePath) {
  if (!capturePath) return null;
  try {
    const log = await fs.readFile(capturePath, 'utf8');
    const total = /total elapsed:\s*(\d+)s/i.exec(log)?.[1];
    return {
      captureMs: total ? Number(total) * 1000 : null,
      // Phase 1 doesn't separate compareMs; future phases (dedicated
      // chunked workers) will write per-stage millis here.
      compareMs: null,
    };
  } catch {
    return null;
  }
}

async function main() {
  const [manifestRaw, testsRaw, combinedRaw, bucketsRaw, durations] = await Promise.all([
    fs.readFile(MANIFEST_PATH, 'utf8'),
    fs.readFile(TESTS_FILE, 'utf8'),
    fs.readFile(COMBINED_FIXTURE, 'utf8'),
    fs.readFile(BUCKETS_PATH, 'utf8').catch(() => '{}'),
    readDurations(CAPTURE_LOG),
  ]);

  const manifest = JSON.parse(manifestRaw);
  const tests = testsRaw.split('\n').map((s) => s.trim())
    .filter((s) => s && !s.startsWith('#'));
  const combined = JSON.parse(combinedRaw);
  const keyMap = combined._wpt?.keyMap ?? {};
  const bucketsIdx = JSON.parse(bucketsRaw);

  const { results, totals } = await buildResults({
    tests, manifest, keyMap, bucketsIdx,
    refsRoot: REFS_ROOT,
    // Per-section runs override --web-dir to their isolated capture path so
    // browser-ref diffs target this section's web PNGs rather than whatever
    // happens to be in apps/web-harness/screenshots/ at the moment. The
    // default is the live harness capture dir — the pre-R5 `testing/web/`
    // path yielded zero web captures, silently nulling every browserRef
    // diff in run-titan.sh smoke runs (TITAN stale-path defect 1).
    webDir: WEB_DIR_ARG ? resolve(WEB_DIR_ARG) : join(REPO_ROOT, 'apps', 'web-harness', 'screenshots'),
    // Native capture dirs (Phase 4). Same override/default pattern as
    // --web-dir; a dir with no matching PNGs contributes no pair, so
    // web-only runs are byte-identical to the Phase-1 behaviour.
    iosDir: IOS_DIR_ARG ? resolve(IOS_DIR_ARG) : join(REPO_ROOT, 'apps', 'ios-harness', 'screenshots'),
    androidDir: ANDROID_DIR_ARG ? resolve(ANDROID_DIR_ARG) : join(REPO_ROOT, 'apps', 'android-harness', 'screenshots'),
  });

  const skipped = buildSkippedBlock(tests, bucketsIdx);

  manifest.manifestVersion = 4;
  // Replace manifest.wpt entirely — the assignment clears any prior
  // skeleton-with-`incomplete:true` beacon written by section-runner.sh's
  // Step 6.5. The `incomplete` field is intentionally absent here so a
  // verify pass treats this manifest as healthy. Idempotent on re-run:
  // calling inject-wpt-block twice in a row produces the same manifest.wpt
  // (modulo runId + duration, both of which are caller-supplied).
  manifest.wpt = {
    ref: WPT_REF,
    runId: RUN_ID,
    totalTests: tests.length,
    buckets: totals,
    duration: durations,
    results,
    skipped,
  };

  // Atomic write: tmp file → rename. Avoids a half-written manifest if
  // we get killed mid-write.
  const tmp = MANIFEST_PATH + '.tmp.' + process.pid;
  await fs.writeFile(tmp, JSON.stringify(manifest, null, 2) + '\n', 'utf8');
  await fs.rename(tmp, MANIFEST_PATH);

  process.stderr.write(
    `inject-wpt-block: wrote v4 manifest (totalTests=${tests.length} ` +
    `A=${totals.A} B=${totals.B} C=${totals.C})\n`
  );
}

// Only invoke main() when this file is the entry point (node ...mjs ...);
// importing it for unit tests should not run the orchestrator.
if (IS_CLI) {
  main().catch((err) => {
    console.error('inject-wpt-block: fatal:', err);
    process.exit(2);
  });
}

// Pure helpers exported for unit tests (tools/titan/inject-wpt-block.test.mjs).
// The orchestrator side of this script remains CLI-driven via the IS_CLI gate
// above, so importing doesn't trigger a usage-error exit.
export {
  stitchPngsVertically, diffWebVsRef, diffPlatformVsRef, diffComposedVsRef,
  safe, checkFuzzyMatch, computeWptPass,
};

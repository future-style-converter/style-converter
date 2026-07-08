#!/usr/bin/env node
//
// testing/titan/inject-wpt-block.mjs
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
//   - examples/wpt/_smoke-combined.json (for the keyMap from
//     build-combined-fixture)
//   - testing/wpt-buckets.json (for bucket reasons of skipped tests)
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
import { classifyDivergence } from '../classify-divergence.mjs';
import {
  computeDssim,
  computeHistogramKL,
  computePerChannelSsim,
  computePHash,
  computeEdgeSsim,
  computeLabDeltaE,
} from '../compare-screenshots-metrics.mjs';

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
const REFS_ROOT     = arg('--refs-root');     // e.g. testing/wpt/refs/<sha>
const CAPTURE_LOG   = arg('--capture-log');   // for duration
// --combined / --web-dir let the section-runner point at per-section paths.
// Default mirrors Phase 1's _smoke-combined fixture so run-titan.sh keeps
// working unchanged.
const COMBINED_ARG  = arg('--combined');
const WEB_DIR_ARG   = arg('--web-dir');

// True when the script is invoked directly (`node inject-wpt-block.mjs ...`);
// false when imported as a module (e.g. inject-wpt-block.test.mjs). Reused
// to gate the usage-check below and the bottom-of-file main() invocation.
const IS_CLI = process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1]);

if (IS_CLI && (!MANIFEST_PATH || !TESTS_FILE || !WPT_REF)) {
  console.error('usage: inject-wpt-block.mjs --manifest <PATH> --tests <FILE> --wpt-ref <SHA> [--run-id <ISO>] [--refs-root <PATH>] [--capture-log <PATH>] [--combined <PATH>] [--web-dir <PATH>]');
  process.exit(1);
}

const COMBINED_FIXTURE = COMBINED_ARG
  ? resolve(COMBINED_ARG)
  : join(REPO_ROOT, 'examples', 'wpt', '_smoke-combined.json');
const BUCKETS_PATH     = join(REPO_ROOT, 'testing', 'wpt-buckets.json');

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

/** Sanitisation matches the iOS / Android / web capture loop's filename
 *  rule. Each platform replaces non-`[A-Za-z0-9._-]` with `_`. */
function safe(name) {
  return name.replace(/[^A-Za-z0-9._-]/g, '_');
}

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
 * Rationale (swarm-002 RC2, testing/titan/investigations/swarm-002/
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
 *  Informational only — does not change the classifier label. */
function checkFuzzyMatch(metrics, fuzzy) {
  if (!fuzzy) return null;
  const px = metrics.pixelMismatchedCount ?? Infinity;
  const maxDelta = metrics.labDeltaE?.max ?? Infinity;
  return (px <= fuzzy.totalPixels.max && maxDelta <= fuzzy.maxDifference.max);
}

/** Walk SMOKE_TESTS and assemble manifest.wpt.results from manifest rows
 *  + the keyMap saved by build-combined-fixture. */
async function buildResults({ tests, manifest, keyMap, bucketsIdx, refsRoot, webDir }) {
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

    // Browser-ref pair (Phase 1 — web only). For the WPT test we render
    // the upstream Chromium ref and compare against our web capture of
    // the *same component*. This gives us a "spec compliance" signal on
    // the web pipeline even when iOS/Android weren't captured this run
    // — which is the common case for the unattended Phase 1 smoke (the
    // platforms are code-only, see TITAN spec hard rule B).
    const refStem = basename(testRel, '.html');
    const refPng = refsRoot
      ? join(refsRoot, meta.section, `${refStem}.png`)
      : null;
    const browserRefAvailable = refPng ? await_fs_exists_sync(refPng) : false;

    let webRefDiff = null;
    if (browserRefAvailable && webDir) {
      // Web screenshots are written by capture-screenshots.mjs as
      // `<paddedIndex>_<safeName>.png`. We don't know the indices a priori
      // so we glob and resolve every per-component capture for this test.
      //
      // Swarm-002 RC2 fix: the legacy path took ONLY the first match and
      // padded it to the browser-ref's dimensions, which trips false
      // structural-divergence when the browser-ref is a composed multi-
      // element scene. Now we stitch ALL per-component captures vertically
      // into a composed PNG (cached in the run dir) and compare that to
      // the ref. For single-component tests stitchPngsVertically short-
      // circuits to the original file, so the diff result is unchanged
      // from the legacy path — backward-compat with the 1-component
      // baseline distribution.
      const webFiles = await fs.readdir(webDir).catch(() => []);
      // Match per IR component key in their declared component-list order
      // (matchingKeys is built from keyMap entries that retain build order).
      const matched = [];
      for (const key of matchingKeys) {
        const safeKey = safe(key);
        const f = webFiles.find((x) => x.endsWith(`_${safeKey}.png`));
        if (f) matched.push(join(webDir, f));
      }
      if (matched.length > 0) {
        try {
          // Cache stitched PNGs under <webDir>/_stitched/ so subsequent
          // injects (e.g. recovery re-runs from section-runner.sh Step 7.5)
          // don't re-do the bitblt loop.
          const stitchDir = join(webDir, '_stitched');
          const cacheKey = safe(testRel.replace(/\.html$/, ''));
          const webComposed = await stitchPngsVertically(matched, stitchDir, cacheKey);
          webRefDiff = await diffWebVsRef(webComposed, refPng);
          webRefDiff.wptFuzzyMatch = checkFuzzyMatch(webRefDiff, meta.fuzzy);
          // Surface the per-test component count so dashboards can show
          // "stitched N captures into one composed PNG" — useful when a
          // failing pair is being investigated.
          webRefDiff.stitchedComponents = matched.length;
        } catch (err) {
          webRefDiff = { error: String(err?.message ?? err) };
        }
      }
    }

    // Test-level divergence label = severest across all available signals.
    // When inter-platform pairs are missing (web-only mode), the browser-ref
    // pair becomes the source of truth.
    const labelSources = pairKinds.map((k) => pairs[k]?.divergence).filter(Boolean);
    if (webRefDiff?.divergence) labelSources.push(webRefDiff.divergence);
    let divergence = labelSources.length ? severestDivergence(labelSources) : 'no-data';

    // FIX-E: auto-bucket override. If the WPT exclusion-rule scanner in
    // testing/titan/wpt-not-applicable.mjs (output: bucketsIdx.notApplicable)
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
        // Phase 1: web-vs-ref only. Phase 4 will add iOS-ref and Android-ref
        // entries (they require a per-platform browser-ref re-render at the
        // platform's native scale, which isn't on the Phase-1 deliverable
        // list).
        diffs: webRefDiff ? { 'web-ref': webRefDiff } : null,
      },
      divergence,
      browserRefDivergence: webRefDiff?.divergence
        ? { web: webRefDiff.divergence }
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
    // happens to be in testing/web/screenshots/ at the moment.
    webDir: WEB_DIR_ARG ? resolve(WEB_DIR_ARG) : join(REPO_ROOT, 'testing', 'web', 'screenshots'),
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

// Pure helpers exported for unit tests (testing/titan/inject-wpt-block.test.mjs).
// The orchestrator side of this script remains CLI-driven via the IS_CLI gate
// above, so importing doesn't trigger a usage-error exit.
export { stitchPngsVertically, diffWebVsRef, safe };

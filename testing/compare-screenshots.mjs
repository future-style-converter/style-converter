#!/usr/bin/env node
//
// compare-screenshots.mjs
//
// Cross-platform component screenshot comparison.
//
// Reads per-component PNGs from:
//   apps/ios-harness/screenshots/
//   apps/android-harness/screenshots/
//   apps/web-harness/screenshots/
//
// For each component (matched by filename like `012_Color_HSL.png`) it:
//
//   1. Normalizes all three images to the same dimensions (padding rather
//      than stretching, so borders / shadows / layouts stay aligned).
//   2. Computes a pairwise **pixelmatch** diff — percentage of pixels
//      whose RGB differs by more than an anti-aliasing-aware threshold.
//   3. Computes a pairwise **SSIM** (structural similarity) score — a
//      perceptual metric that tolerates font rendering / subpixel AA but
//      still catches "the shadow is missing" / "the shape is wrong".
//   4. Optionally diffs against a committed baseline in testing/baseline/
//      and fails hard if any component regresses beyond the threshold.
//
// Outputs:
//   - testing/report/                  (HTML report + diff PNGs + manifest.json)
//   - testing/report/index.html        (open in any browser)
//   - testing/report/manifest.json     (structured data for CI / tooling)
//
// Exit codes:
//   0 — success or no baseline
//   1 — at least one component regressed beyond thresholds
//   2 — script / IO error
//
// Usage:
//     node compare-screenshots.mjs [options]
//
// Options:
//     --baseline                Compare against testing/baseline/ and fail
//                               on regressions. Without this flag only the
//                               cross-platform report is generated.
//     --update-baseline         Copy the current captures into testing/baseline/.
//                               Use after intentional visual changes.
//     --ssim-threshold <n>      Minimum SSIM for a component to pass against
//                               the baseline (default 0.95).
//     --pixel-threshold <n>     Max % of pixels allowed to differ (default 2).
//     --full-lab                Disable LAB ΔE stride sampling. Default
//                               samples 1-in-4 pixels (~30 ms/pair). Full
//                               sampling adds ~90 ms/pair (~40s on baseline).
//

import { readdirSync, existsSync, mkdirSync, rmSync, copyFileSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve, dirname, basename, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';
import sharp from 'sharp';
import pixelmatchDefault from 'pixelmatch';
import { ssim as computeSsim } from 'ssim.js';
// COMPARE_METRICS Section 7 items 1–6 — new per-pair metrics, extracted
// into a sibling module so they can be unit-tested in isolation.
import {
  computeDssim,
  computeHistogramKL,
  computePerChannelSsim,
  computePHash,
  computeEdgeSsim,
  computeLabDeltaE,
  computeSemanticPresence,
} from './compare-screenshots-metrics.mjs';
// COMPARE_METRICS Section 7 item 7 — divergence classifier (B-3). Pure
// function over a metric block; lives in a separate module so it can be
// unit-tested without booting the whole comparison pipeline.
import { classifyDivergence } from './classify-divergence.mjs';
// COMPARE_METRICS Section 5 / item 9 — HTML report extracted into a
// sibling module to keep this file under the per-file size budget.
import { renderHTML } from './compare-screenshots-html.mjs';

const pixelmatch = pixelmatchDefault.default ?? pixelmatchDefault;

const __dirname = dirname(fileURLToPath(import.meta.url));

// ── Paths ────────────────────────────────────────────────────────────────────
// Each path can be overridden via env. Defaults preserve the legacy 327-pair
// pipeline contract (test-all.sh + the visual-test fixture). The TITAN
// section-runner (testing/titan/section-runner.sh) sets the per-platform
// screenshot dirs + REPORT_DIR + MANIFEST_OUT to per-section paths so 30+
// agents can run compare in parallel without racing on testing/report/ or
// the screenshot trees. See testing/titan/swarm-dispatch.md.
const PLATFORMS = ['iOS', 'Android', 'web'];

const paths = {
  iOS:     process.env.IOS_SCREENSHOTS_DIR     || resolve(__dirname, '../apps/ios-harness/screenshots'),
  Android: process.env.ANDROID_SCREENSHOTS_DIR || resolve(__dirname, '../apps/android-harness/screenshots'),
  web:     process.env.WEB_SCREENSHOTS_DIR     || resolve(__dirname, '../apps/web-harness/screenshots'),
  baseline: resolve(__dirname, 'baseline'),
  // REPORT_DIR controls where index.html + diffs/ + images/ are written.
  // MANIFEST_OUT (read at write time below) overrides only the manifest.json
  // path — useful when several runners share a report dir but each needs
  // its own machine-readable result file.
  report:   process.env.REPORT_DIR             || resolve(__dirname, 'report'),
};

// ── Args ─────────────────────────────────────────────────────────────────────
const args = process.argv.slice(2);
const useBaseline    = args.includes('--baseline');
const updateBaseline = args.includes('--update-baseline');
const ssimThreshold  = Number(getArg('--ssim-threshold') ?? 0.95);
const pixelThreshold = Number(getArg('--pixel-threshold') ?? 2);
// Optional label (e.g. the input IR filename) so the report headline says
// which test case it was generated from. Populated by test-all.sh.
const inputLabel     = getArg('--input') ?? process.env.TEST_INPUT ?? '';
// COMPARE_METRICS Section 8 (decision 2) / B7 — stride-sample LAB ΔE 1-in-4
// by default to keep cost ~30ms/pair instead of ~120ms. `--full-lab` opts
// into pixel-accurate scoring for one-off accuracy runs (~40s extra on the
// 327-pair baseline).
const fullLab        = args.includes('--full-lab');

function getArg(name) {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : undefined;
}

// ── Baseline update shortcut ─────────────────────────────────────────────────
if (updateBaseline) {
  await syncBaseline();
  process.exit(0);
}

// ── Main ─────────────────────────────────────────────────────────────────────
// `await main()` is placed at the bottom of the file so all module-level
// `const` declarations have been initialized before the async work starts
// reaching into them. ESM hoists bindings but not initializers; accessing
// a const before its declaration throws "Cannot access X before
// initialization" (TDZ).

async function main() {
  console.log('→ collecting captures…');
  const captures = collectCaptures();
  const names = sortedUnion(captures);
  console.log(`  ${names.length} unique component(s) across platforms`);

  rmSync(paths.report, { recursive: true, force: true });
  mkdirSync(paths.report, { recursive: true });
  mkdirSync(join(paths.report, 'diffs'), { recursive: true });
  mkdirSync(join(paths.report, 'images'), { recursive: true });

  // Copy originals into report/images/ so the HTML can reference them without
  // relying on relative paths that break when the report is moved.
  for (const p of PLATFORMS) {
    const dst = join(paths.report, 'images', p);
    mkdirSync(dst, { recursive: true });
    for (const name of Object.keys(captures[p])) {
      copyFileSync(captures[p][name], join(dst, name));
    }
  }
  if (useBaseline && existsSync(paths.baseline)) {
    const dst = join(paths.report, 'images', 'baseline');
    mkdirSync(dst, { recursive: true });
    for (const f of readdirSync(paths.baseline)) {
      if (f.endsWith('.png')) copyFileSync(join(paths.baseline, f), join(dst, f));
    }
  }

  // Per-component analysis.
  const rows = [];
  let regressionCount = 0;

  for (const name of names) {
    process.stdout.write(`  ${name}… `);
    const row = await analyzeComponent(name, captures);
    rows.push(row);

    if (useBaseline && row.baseline && row.baseline.regressed) {
      regressionCount += 1;
      process.stdout.write('REGRESSION\n');
    } else {
      process.stdout.write(summarize(row) + '\n');
    }
  }

  // Write HTML + manifest.
  // COMPARE_METRICS Section 6 — bump manifestVersion to 2 to signal that
  // the per-pair blocks carry the B2/B3/B4/B5/B6/B7 metrics. External
  // tools can detect "this report has the new metrics" with one field
  // check rather than probing each row.
  //
  // COMPARE_METRICS_B8-B10.md Section 6 — bumped again 2 → 3 for the
  // top-level `perPlatformProbes` field. Graceful rollout (Section 8 q8):
  // the writer always emits v3, but `perPlatformProbes` is null until
  // testing/compute-text-metrics.mjs inlines actual probe data. v2
  // consumers (smoke.sh exit-code check, baseline-stats walking pairs.*)
  // ignore unknown top-level keys, so the bump is informational.
  //
  // TITAN_ARCHITECTURE.md §7.1 — bumped again 3 → 4 for the top-level
  // `wpt` block. Same graceful-rollout pattern as perPlatformProbes:
  // we always emit v4, but `wpt` is null on non-WPT runs (the only writer
  // that fills it is testing/titan/inject-wpt-block.mjs, which runs as a
  // post-processor in run-titan.sh). v3-aware consumers (smoke.sh exit
  // check, baseline-stats walking rows[].pairs.*) treat unknown top-level
  // keys as ignorable; the bump is informational. Tests that probe for
  // `manifest.wpt !== undefined` need the explicit-null sentinel here so
  // they can branch on "field absent" vs "field present but no data" cleanly.
  const manifest = {
    manifestVersion: 4,
    generatedAt: new Date().toISOString(),
    inputLabel,
    thresholds: {
      ssim: ssimThreshold,
      pixel: pixelThreshold,
      edgeSsim: 0.80,        // Section 3 placeholder (data-derived later)
      labDeltaEP95: 5.0,     // Section 3 — "clearly different" boundary
      pHashHamming: 10,      // Section 3 — Wang et al. 2008 standard
      // B-EXT spec Section 6 — info-only, no CI gating. compute-text-metrics
      // overwrites these with the same values when it runs (it doesn't
      // know we already wrote them, but the values are stable).
      b8BaselineDeltaPx: 1.0,
      b10MeanDeltaPx: 1.5,
      b10StddevDeltaPx: 1.5,
    },
    // Null until compute-text-metrics.mjs inlines real probe data. Kept
    // explicit (vs absent) so v3 readers can branch on `=== null` instead
    // of `'perPlatformProbes' in manifest` for the "no data yet" state.
    perPlatformProbes: null,
    // Null on non-WPT runs; testing/titan/inject-wpt-block.mjs replaces
    // this with the §7.1 `{ ref, runId, totalTests, buckets, results,
    // skipped, ... }` block when run-titan.sh post-processes the manifest.
    // Kept explicit so v4 readers can detect "WPT pipeline didn't run"
    // via `manifest.wpt === null` rather than `'wpt' in manifest`.
    wpt: null,
    rows,
  };
  // MANIFEST_OUT lets the TITAN section-runner write the per-section manifest
  // to a stable per-section path while still letting `report/` host the HTML
  // for visual debugging. Defaults to the legacy `<report>/manifest.json` so
  // the 327-pair pipeline + run-titan.sh Phase 1 flow are unchanged.
  const manifestOut = process.env.MANIFEST_OUT || join(paths.report, 'manifest.json');
  writeFileSync(manifestOut, JSON.stringify(manifest, null, 2));
  writeFileSync(
    join(paths.report, 'index.html'),
    renderHTML(rows, {
      useBaseline,
      ssimThreshold,
      pixelThreshold,
      regressionCount,
      inputLabel,
      // B-EXT spec Section 7 step 12 — pass through the (possibly null)
      // perPlatformProbes block so the HTML renderer can append the
      // typography section. Null here means "compute-text-metrics hasn't
      // run yet"; the renderer returns an empty string in that case so
      // the existing report layout is unaffected.
      perPlatformProbes: manifest.perPlatformProbes,
    })
  );

  console.log(`\n✓ report: ${join(paths.report, 'index.html')}`);

  // Phase B (LOG-ONLY) — cross-platform divergence-drift check against
  // testing/baseline-stats.json. Spec Section 6: "divergence label
  // downgraded between baseline and current". Compares the recorded
  // classifier label per cross-platform pair against this run's label
  // and warns when a row's label severity increased. Doesn't gate CI.
  // Only runs if baseline-stats.json exists; otherwise silently skips
  // (first run has no recorded baseline yet — generate one with
  // `node testing/baseline-stats.mjs`).
  await runPhaseBDriftCheck(rows);

  if (useBaseline) {
    // Count how many baseline comparisons actually ran. A row only counts
    // if its .baseline.platforms contained at least one pair of {current,
    // baseline} images. Without this check, running `--baseline` with
    // zero captures would print "✓ no regressions" (because the regressed
    // counter stayed at 0) and falsely green-light CI.
    let checked = 0;
    for (const r of rows) {
      if (!r.baseline) continue;
      for (const info of Object.values(r.baseline.platforms)) {
        if (info && info.ssim !== undefined) checked += 1;
      }
    }

    if (checked === 0) {
      console.error('✗ baseline mode requested but 0 comparisons ran');
      console.error('  (no current captures match any baseline image — is the baseline empty,');
      console.error('   or did every capture step fail?)');
      process.exit(2);
    }

    if (regressionCount > 0) {
      console.error(`✗ ${regressionCount} component(s) regressed beyond thresholds (${checked} platform-comparisons ran)`);
      process.exit(1);
    } else {
      console.log(`✓ no regressions vs baseline (${checked} platform-comparisons ran)`);
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────

function collectCaptures() {
  const captures = { iOS: {}, Android: {}, web: {} };
  for (const p of PLATFORMS) {
    if (!existsSync(paths[p])) {
      console.warn(`  ⚠ ${p}: ${paths[p]} not found`);
      continue;
    }
    for (const f of readdirSync(paths[p])) {
      if (!f.endsWith('.png')) continue;
      // Skip ancillary images (e.g. iOS simulator.png)
      if (/^simulator|^logcat/.test(f)) continue;
      captures[p][f] = join(paths[p], f);
    }
  }
  return captures;
}

function sortedUnion(captures) {
  const all = new Set();
  for (const p of PLATFORMS) Object.keys(captures[p]).forEach((k) => all.add(k));
  return [...all].sort();
}

async function analyzeComponent(name, captures) {
  // Load + align every platform's PNG for this component. A single
  // corrupt / non-PNG file in any screenshots directory used to crash
  // the whole comparison run — now we isolate the failure per-platform
  // and surface it as `platforms[p].error` so the report can show which
  // file is broken without losing the rest of the comparison.
  const loaded = {};
  const loadErrors = {};
  for (const p of PLATFORMS) {
    const path = captures[p][name];
    if (!path) { loaded[p] = null; continue; }
    try {
      loaded[p] = await loadPng(path);
    } catch (e) {
      loaded[p] = null;
      loadErrors[p] = e.message ?? String(e);
      console.warn(`  ⚠ ${p}/${name}: ${loadErrors[p]}`);
    }
  }

  // Compute shared canvas size: the max width (platforms should all be 390
  // by contract — if they're not, we flag it) and max height so everyone
  // gets padded identically.
  const nonNull = PLATFORMS.map((p) => loaded[p]).filter(Boolean);
  if (nonNull.length === 0) {
    // Every platform failed to decode (or files missing). Return a
    // well-shaped row so `renderRow` can still produce a sensible entry
    // per-platform error cell, rather than crashing on undefined
    // lookups in `r.platforms[p]`.
    const platforms = {};
    for (const p of PLATFORMS) {
      if (loadErrors[p]) platforms[p] = { present: false, error: loadErrors[p] };
      else platforms[p] = { present: false };
    }
    const pairs = {
      'iOS-Android': null, 'iOS-web': null, 'Android-web': null,
    };
    return { name, platforms, pairs, baseline: null, errors: loadErrors };
  }
  const canvasW = Math.max(...nonNull.map((i) => i.width));
  const canvasH = Math.max(...nonNull.map((i) => i.height));

  const normalized = {};
  for (const p of PLATFORMS) {
    normalized[p] = loaded[p]
      ? await padToCanvas(loaded[p], canvasW, canvasH)
      : null;
  }

  // Pairwise comparisons for the HTML report.
  const pairs = {};
  const pairSpecs = [
    ['iOS', 'Android'],
    ['iOS', 'web'],
    ['Android', 'web'],
  ];

  for (const [a, b] of pairSpecs) {
    const key = `${a}-${b}`;
    if (!normalized[a] || !normalized[b]) {
      pairs[key] = null;
      continue;
    }
    pairs[key] = await diffPair(
      normalized[a],
      normalized[b],
      canvasW,
      canvasH,
      `${name.replace(/\.png$/, '')}__${key}.png`
    );
  }

  // Per-platform dimension metadata. `error` populated when the PNG failed
  // to decode; the HTML report renders it as a red cell instead of "missing".
  const platforms = {};
  for (const p of PLATFORMS) {
    if (loaded[p]) {
      platforms[p] = {
        present: true,
        width: loaded[p].width,
        height: loaded[p].height,
        image: `images/${p}/${name}`,
      };
    } else if (loadErrors[p]) {
      platforms[p] = { present: false, error: loadErrors[p] };
    } else {
      platforms[p] = { present: false };
    }
  }

  // Baseline comparison — we compare each platform's current capture to its
  // baseline counterpart (same platform, same component name).
  let baseline = null;
  if (useBaseline && existsSync(paths.baseline)) {
    baseline = await compareBaseline(name, normalized, canvasW, canvasH);
  }

  return { name, canvasW, canvasH, platforms, pairs, baseline };
}

async function loadPng(path) {
  const buf = readFileSync(path);
  return PNG.sync.read(buf);
}

/**
 * Pad `img` (width × height) onto a (W × H) canvas filled with the standard
 * capture background (#1A1A2E) — no stretching, no resampling. Keeps the
 * component pixel-aligned even when one platform produced a taller image
 * than another.
 */
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
 * Pixel-diff via pixelmatch + SSIM via ssim.js, plus the six COMPARE_METRICS
 * Section 1 metrics (B2 perChannelSsim · B3 edgeSsim · B4 histogramKL ·
 * B5 pHash · B6 dssim · B7 labDeltaE). Returns diff metrics and writes a
 * visual diff PNG to the report folder.
 *
 * Backward-compat contract (Section 2 / Section 6): every new field is
 * OPTIONAL and independently nullable. Missing or null fields render as "—"
 * in the report and never gate regression checks during Phase A.
 */
async function diffPair(a, b, W, H, diffFilename) {
  const diff = new PNG({ width: W, height: H });
  // `threshold` controls per-pixel color-delta tolerance. 0.25 is generous
  // enough that cross-platform font AA doesn't flag entire glyph edges as
  // mismatches, but still catches real changes (colors shifting, borders
  // appearing / disappearing, shadows moving).
  // `includeAA: true` skips AA pixels entirely — usually desirable for this
  // kind of cross-renderer comparison.
  const mismatched = pixelmatch(a.data, b.data, diff.data, W, H, {
    threshold: 0.25,
    includeAA: true,
    diffColor: [255, 80, 80],
    alpha: 0.15,
  });

  const diffPath = join(paths.report, 'diffs', diffFilename);
  writeFileSync(diffPath, PNG.sync.write(diff));

  const pixelPct = (mismatched / (W * H)) * 100;

  // SSIM — rescale to a small grid if the image is huge to keep this fast.
  const ssimScore = await safeSsim(a, b);

  // ── New metrics (Section 7 items 1–6, in spec order) ───────────────────────
  // Each helper from compare-screenshots-metrics.mjs follows the safeSsim
  // try/catch pattern: any failure (corrupt buffer, sharp-phash on tiny
  // image, etc) returns null and the rest of the row continues unaffected.

  // B6 — DSSIM derived from the existing SSIM result.
  const dssim = computeDssim(ssimScore);
  // B4 — color-histogram KL divergence (inline; no new dep). Position-
  // independent so this catches "right colors, wrong layout" vs vice versa.
  const histogramKL = computeHistogramKL(a, b);
  // B2 — per-channel SSIM (R/G/B/A). Informational per Section 8
  // decision 5 — emitted but does not gate regression.
  const perChannelSsim = await computePerChannelSsim(a, b);
  // B5 — perceptual hash + Hamming distance (64-bit DCT default,
  // Section 8 decision 6).
  const pHash = await computePHash(a, b);
  // B3 — edge-map SSIM. Sobel-3 kernel (Section 8 decision 4). Catches
  // AA-only differences without conflating fill diffs.
  const edgeSsim = await computeEdgeSsim(a, b);
  // B7 — CIE LAB ΔE (CIEDE2000). Stride-sampled 1-in-4 by default
  // (Section 8 decision 2). `--full-lab` CLI flag disables stride.
  const labDeltaE = computeLabDeltaE(a, b, fullLab ? 1 : 4);
  // Semantic-presence gate (round 91, fixes pilot-001 false positive). Per-
  // image foreground coverage vs the canonical #1A1A2E capture background;
  // consumed by classifyDivergence() as an early-exit "no-content" label
  // when both sides extracted to empty placeholders. Independently nullable
  // (Section 2 contract) so an out-of-range capture buffer doesn't break
  // the rest of the row — the classifier just falls through to its existing
  // 7-label decision tree when the signal is missing.
  const semanticPresence = computeSemanticPresence(a, b);

  // Section 4 — assemble the metric block first, then classify in one pass
  // so the same shape we serialise to manifest.json is the shape the
  // classifier sees. Avoids drift between "classified value" and "stored value".
  const metrics = {
    pixelMismatchedCount: mismatched,
    pixelMismatchedPct: +pixelPct.toFixed(3),
    ssim: ssimScore,
    diffImage: `diffs/${diffFilename}`,
    // ── New metrics (Section 2: every field optional, may be null) ───────────
    dssim,
    perChannelSsim,
    edgeSsim,
    histogramKL,
    pHash,
    labDeltaE,
    semanticPresence,
  };
  // Section 7 item 7 — classifier wired in (B-3). Returns one of:
  // identical · sub-pixel-noise · color-drift · edge-shift ·
  // structural-divergence · mixed · unknown. Always present so the
  // report renderer can rely on the field existing.
  metrics.divergence = classifyDivergence(metrics);
  return metrics;
}

async function safeSsim(a, b) {
  // ssim.js expects ImageData-like objects (data: Uint8ClampedArray, width, height)
  try {
    const aImg = { data: new Uint8ClampedArray(a.data), width: a.width, height: a.height };
    const bImg = { data: new Uint8ClampedArray(b.data), width: b.width, height: b.height };
    const r = computeSsim(aImg, bImg, { ssim: 'fast' });
    return +r.mssim.toFixed(4);
  } catch (e) {
    return null;
  }
}

// (B2–B7 metric implementations live in compare-screenshots-metrics.mjs so
// they can be unit-tested in isolation. See the imports at the top of this
// file.)

async function compareBaseline(name, normalized, canvasW, canvasH) {
  const result = { platforms: {}, regressed: false };

  for (const p of PLATFORMS) {
    const baselinePath = join(paths.baseline, `${p}__${name}`);
    if (!existsSync(baselinePath) || !normalized[p]) {
      result.platforms[p] = { present: false };
      continue;
    }

    // The canvas dimensions passed in are the max across the CURRENT
    // captures only. When a baseline image has different dimensions
    // (e.g. a property now renders at the correct size where it
    // previously rendered wrong), the baseline may overflow canvasW/H.
    // `padToCanvas` uses sharp's `extend`, which only grows — passing
    // an already-larger image fails with "Image to extend contains an
    // area that is outside requested dimensions".
    //
    // Fix: compute a per-pair canvas that's max of baseline and current
    // dims on BOTH axes, then pad both images to that.
    const baselineRaw = await loadPng(baselinePath);
    const cur = normalized[p];
    const pairW = Math.max(canvasW, baselineRaw.width, cur.width);
    const pairH = Math.max(canvasH, baselineRaw.height, cur.height);

    const baseImg = await padToCanvas(baselineRaw, pairW, pairH);
    // Re-pad current if the pair canvas grew beyond the earlier pad.
    const curImg = (cur.width === pairW && cur.height === pairH)
      ? cur
      : await padToCanvas(cur, pairW, pairH);

    const pair = await diffPair(
      baseImg,
      curImg,
      pairW,
      pairH,
      `${name.replace(/\.png$/, '')}__baseline-${p}.png`
    );
    // Phase A gate (CI-blocking, unchanged). Spec Section 6:
    // "Phase A — keep the existing `regressed = ssim < threshold ||
    // pixelPct > threshold` gate." This is what gates `--baseline` exit 1.
    const regressed =
      pair.pixelMismatchedPct > pixelThreshold ||
      (pair.ssim !== null && pair.ssim < ssimThreshold);
    if (regressed) result.regressed = true;

    // Phase B (LOG-ONLY, not gating). Spec Section 6:
    // "Print warnings prefixed `[phase-b]` so they're easy to grep, but
    // DON'T fail the build (Phase A behavior — SSIM-only — remains the
    // gate). Phase C will gate on classifier in a future PR."
    //
    // We surface three signals on each baseline-vs-current pair:
    //   1. Classifier label is anything other than `identical` —
    //      a baseline diff that the classifier doesn't call `identical`
    //      indicates drift; spec calls out "label downgraded between
    //      baseline and current". For baseline-vs-current the baseline
    //      label is by construction `identical` (image vs itself
    //      should classify as identical), so any other label is a
    //      downgrade.
    //   2. labDeltaE.p95 > 2 — Section 3 threshold for "barely
    //      perceptible" boundary; spec lists "labDeltaE.p95 increase
    //      >2" as a Phase B trigger.
    //   3. pHash hamming > 3 — spec lists "pHash hamming increase
    //      >3". Baseline pHash is by construction 0 (image vs itself
    //      via the `--update-baseline` workflow), so >3 == drift.
    //   4. edgeSsim < 0.95 — spec lists "edgeSsim drop >0.05". Baseline
    //      edgeSsim is by construction 1.0, so <0.95 == >0.05 drop.
    if (!regressed) {
      // Strong-signal-only triggers: spec says "edgeSsim drop >0.05,
      // labDeltaE.p95 increase >2, pHash hamming increase >3" plus
      // "label downgraded". For baseline-vs-current the recorded
      // baseline label is implicitly `identical` (same image vs itself
      // when the baseline was captured), so anything STRONGER than
      // sub-pixel-noise is a real downgrade. We deliberately let
      // `sub-pixel-noise` slide — for cross-platform AA noise, every
      // pair would warn and the signal would be drowned out.
      const phaseBWarnings = [];
      const strongLabels = new Set([
        'color-drift', 'edge-shift', 'structural-divergence', 'mixed',
      ]);
      if (pair.divergence && strongLabels.has(pair.divergence)) {
        phaseBWarnings.push(`divergence=${pair.divergence}`);
      }
      if (pair.labDeltaE?.p95 != null && pair.labDeltaE.p95 > 2) {
        phaseBWarnings.push(`ΔE.p95=${pair.labDeltaE.p95}`);
      }
      if (pair.pHash?.hammingDistance != null && pair.pHash.hammingDistance > 3) {
        phaseBWarnings.push(`pHash=${pair.pHash.hammingDistance}`);
      }
      if (pair.edgeSsim != null && pair.edgeSsim < 0.95) {
        phaseBWarnings.push(`edgeSsim=${pair.edgeSsim}`);
      }
      if (phaseBWarnings.length > 0) {
        // Prefixed `[phase-b]` for easy `grep` in CI logs. stderr so
        // the line shows up alongside the existing red error stream
        // even though it doesn't fail the build.
        console.warn(`  [phase-b] ${p}/${name}: ${phaseBWarnings.join(' · ')}`);
      }
    }
    result.platforms[p] = { ...pair, regressed };
  }

  return result;
}

// COMPARE_METRICS Section 7 item 10 — Phase B regression-check, log-only.
// Compares this run's per-pair classifier labels against the recorded
// labels in testing/baseline-stats.json (produced by baseline-stats.mjs).
// Warns on label-severity downgrades but does NOT exit non-zero.
// Spec Section 6: "Phase B — keep edgeSsim / histogramKL informational
// until Section 3's data-derived thresholds are committed". Phase C
// (future PR) will gate on these.
async function runPhaseBDriftCheck(rows) {
  const statsPath = resolve(__dirname, 'baseline-stats.json');
  if (!existsSync(statsPath)) {
    // Quiet skip: first run before any baseline is recorded. The phase-B
    // user can run baseline-stats.mjs and re-run to opt into checks.
    return;
  }
  let prior;
  try {
    prior = JSON.parse(readFileSync(statsPath, 'utf-8'));
  } catch (e) {
    // Corrupt baseline-stats — print a warning and bail so a malformed
    // file doesn't silently disable the drift check forever.
    console.warn(`  [phase-b] could not parse ${statsPath}: ${e.message ?? e}`);
    return;
  }
  // Severity ordering used for "downgrade" detection — must match
  // classify-divergence.mjs's SEVERITY_RANK so signal interpretation is
  // consistent across modules.
  const rank = {
    identical: 1,
    'sub-pixel-noise': 2,
    unknown: 3,
    mixed: 4,
    'edge-shift': 5,
    'color-drift': 6,
    'structural-divergence': 7,
  };
  let downgrades = 0;
  for (const r of rows) {
    for (const [pairKey, pair] of Object.entries(r.pairs ?? {})) {
      if (!pair) continue;
      const key = `${r.name}__${pairKey}`;
      const priorLabel = prior.perPair?.[key];
      const currentLabel = pair.divergence;
      if (!priorLabel || !currentLabel) continue;
      // Downgrade = current rank > prior rank (i.e. moved toward
      // structural). Equal-rank or improvement is fine.
      const priorRank = rank[priorLabel] ?? rank.unknown;
      const currentRank = rank[currentLabel] ?? rank.unknown;
      if (currentRank > priorRank) {
        downgrades += 1;
        console.warn(`  [phase-b] ${key}: ${priorLabel} → ${currentLabel}`);
      }
    }
  }
  if (downgrades > 0) {
    console.warn(`  [phase-b] ${downgrades} label downgrade(s) vs ${statsPath} (log-only — Phase C will gate)`);
  }
}

async function syncBaseline() {
  console.log('→ updating baseline from current captures…');

  // Guard against clobbering a good baseline with nothing. If the current
  // capture run produced zero PNGs (e.g. test-all.sh was run with all
  // platforms skipped, or every platform failed), bail out rather than
  // silently wiping testing/baseline/ and pretending we updated it.
  let totalAvailable = 0;
  for (const p of PLATFORMS) {
    if (!existsSync(paths[p])) continue;
    totalAvailable += readdirSync(paths[p])
      .filter((f) => f.endsWith('.png') && !/^simulator|^logcat/.test(f))
      .length;
  }
  if (totalAvailable === 0) {
    console.error('✗ refusing to update baseline: no current captures found.');
    console.error('  expected PNGs under apps/{ios,android,web}-harness/screenshots/');
    console.error('  (did a capture step fail, or was everything skipped?)');
    process.exit(2);
  }

  rmSync(paths.baseline, { recursive: true, force: true });
  mkdirSync(paths.baseline, { recursive: true });
  let count = 0;
  for (const p of PLATFORMS) {
    if (!existsSync(paths[p])) continue;
    for (const f of readdirSync(paths[p])) {
      if (!f.endsWith('.png')) continue;
      if (/^simulator|^logcat/.test(f)) continue;
      copyFileSync(join(paths[p], f), join(paths.baseline, `${p}__${f}`));
      count += 1;
    }
  }
  console.log(`✓ wrote ${count} baseline image(s) to ${paths.baseline}`);
}

function summarize(row) {
  const bits = [];
  for (const [k, v] of Object.entries(row.pairs)) {
    if (!v) continue;
    bits.push(`${k} ${v.ssim?.toFixed(2) ?? '—'}`);
  }
  return bits.join(' · ');
}

// ─────────────────────────────────────────────────────────────────────────────
// HTML report — rendering extracted to compare-screenshots-html.mjs
// (item 9; CLAUDE.md ≤300-line file budget). The exported `renderHTML`
// imported at the top of this file is called directly from main().
// ─────────────────────────────────────────────────────────────────────────────


// Entry point — placed at EOF so every module-level const above has already
// been initialized before main() runs.
await main();

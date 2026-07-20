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
  // wave-13 SCORING gate: the round-91 semantic-presence guard the 327-pair
  // rows[].pairs already carry, reused here against the corpus-v4 WHITE WPT
  // canvas (third `bg` argument) so browser-ref diffs can detect the
  // blank-capture-vs-mostly-blank-ref vacuous-pass class.
  computeSemanticPresence,
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

/** Pad to (W,H) with the WPT capture canvas background — WHITE from the
 *  corpus-v4 white-canvas boundary (capture-browser-ref.mjs CANVAS_BG and
 *  all three platform WPT capture modes paint white now), so a shorter
 *  capture's padding blends into the ref's white tail instead of stamping
 *  a dark block over it. This module serves ONLY WPT browser-ref diffs;
 *  the 327-pair pipeline keeps its own dark padToCanvas in
 *  compare-screenshots.mjs untouched. */
async function padToCanvas(img, W, H) {
  if (img.width === W && img.height === H) return img;
  const padded = await sharp(PNG.sync.write(img))
    .extend({
      top: 0,
      bottom: Math.max(0, H - img.height),
      left: 0,
      right: Math.max(0, W - img.width),
      // The corpus-v4 WHITE canvas (documented boundary — all WPT numbers
      // shift at v4; do not compare against v1..v3 manifests).
      background: { r: 0xFF, g: 0xFF, b: 0xFF, alpha: 1 },
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
 * the WHITE WPT canvas background (corpus-v4 boundary — matches padToCanvas
 * above + every platform's WPT capture canvas so seams are invisible).
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
  // Build a fresh PNG buffer initialised to the WHITE WPT canvas background
  // (corpus-v4 boundary) so any sub-canvas gap blends with the white
  // captures/ref instead of stamping dark seams between stitched crops.
  const composed = new PNG({ width: W, height: H });
  for (let i = 0; i < composed.data.length; i += 4) {
    composed.data[i]     = 0xFF;
    composed.data[i + 1] = 0xFF;
    composed.data[i + 2] = 0xFF;
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
  // Color-aware triage signals (TITAN-WHITE lane) — every browser-ref diff
  // carries both. diffWebVsRef serves ONLY the wpt: block's ref pairs
  // (stitch + composed paths), so the 327-pair manifest rows are untouched.
  // Neither field feeds wptPass (raw ssim ≥ 0.95 stays the one criterion).
  metrics.colorComposite = computeColorComposite(metrics.ssim, metrics.labDeltaE);
  metrics.colorDivergent = isColorDivergent(metrics.histogramKL);
  // wave-13 SCORING gate (corpus-v4.3): per-image ink coverage against the
  // WHITE WPT canvas, computed on the SAME padded pair every other metric
  // sees. a = the platform capture, b = the browser ref (argument order of
  // this function). Unlike the color signals above this one DOES feed
  // wptPass — see computePresenceFailed + computeWptPass below.
  metrics.semanticPresence = computeSemanticPresence(A, B, WPT_CANVAS_BG);
  // wave-15 LOW-CONTENT-DENSITY triage flag: true when BOTH images are
  // > 85 % background (each side's ink coverage < 15 %) — whole-canvas SSIM
  // is then dominated by background agreement, so a high score can coexist
  // with misplaced content (measured: attachment-fixed-inside-transform-1,
  // ssim 0.963 with ~90 % of both images white and the content in the wrong
  // place). Triage colour only — feeds neither wptPass nor scoreExcluded.
  metrics.lowContentDensity = computeLowContentDensity(metrics.semanticPresence);
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
 *  unit tests.
 *
 *  wave-13 SEMANTIC-PRESENCE gate (corpus-v4.3 SCORING boundary): the third
 *  argument, `presenceFailed` (computePresenceFailed's result), VETOES the
 *  pass unconditionally. Why: raw SSIM + fuzzy are both structure/pixel
 *  budgets over the WHOLE canvas, so a BLANK capture "passes" against a
 *  mostly-blank ref whose only content is a small widget — measured on the
 *  wave12-gate: appearance-auto-input-non-widget-001 scored ssim 0.9711 with
 *  ALL THREE platform captures fully blank, and accent-color-visited scored
 *  0.9967 blank-vs-a-~13px-checkbox. Those are delivery/render vacuums, not
 *  renderer agreement; counting them as PASS inflates the corpus pass rate.
 *  A presence-failed pair is a FAIL regardless of ssim AND regardless of a
 *  declared fuzzy tolerance (WPT fuzzy budgets assume both sides rendered).
 *  Default false keeps the two-argument legacy call shape passing. */
function computeWptPass(ssim, fuzzyMatch, presenceFailed = false) {
  // The presence veto runs FIRST: a capture that renders none of the ref's
  // ink can never be a pass, whatever the whole-canvas metrics say.
  if (presenceFailed === true) return false;
  const ssimPass = typeof ssim === 'number' && ssim >= 0.95;
  return ssimPass || fuzzyMatch === true;
}

/** The WPT capture canvas background — WHITE, the corpus-v4 boundary
 *  (capture-browser-ref.mjs CANVAS_BG + every platform's WPT capture mode
 *  paint white; padToCanvas above pads white). The presence guard counts
 *  "ink" as deviation from THIS canvas, not the 327-pair dark #1A1A2E. */
const WPT_CANVAS_BG = { r: 0xFF, g: 0xFF, b: 0xFF };

/** Presence-gate thresholds — calibrated against the wave12-gate manifests
 *  (tools/titan/runs/wave12-gate/sections; recorded values pinned in
 *  inject-wpt-block.test.mjs, reproducible by recomputing white-canvas
 *  coverage on the padded capture/ref pairs):
 *
 *  REF_MIN_PCT = 0.02 — the ref must visibly carry ink before asymmetry can
 *  mean anything. Measured: the SMALLEST visibly-inked ref in the gate is
 *  accent-color-visited's ~13px checkbox at 0.068 % coverage (3.4× above
 *  the floor), while genuinely-blank refs (background-color-transparent-
 *  animation-in-body, background-color-animation-with-zero-alpha — tests
 *  whose PASS criterion IS "render nothing") measure exactly 0.000 %.
 *  A blank ref therefore never arms the gate: blank-vs-blank stays a pass.
 *
 *  RATIO_MIN = 0.05 — the capture must carry at least 5 % of the ref's ink
 *  mass. Measured separation: every vacuous pass in the gate has capture
 *  coverage exactly 0.000 % (ratio 0.0 — appearance-auto-input-non-widget-001
 *  cap 0.000/ref 1.119; accent-color-visited cap 0.000/ref 0.068; plus the
 *  blank android capture of background-attachment-fixed-inside-transform-1,
 *  cap 0.000/ref 7.404), while the WORST substantive pass ratio is 0.157
 *  (contrast-color-interpolation ios, cap 0.800/ref 5.103). 0.05 sits ~3×
 *  under the substantive floor and strictly above the measured vacuous
 *  ceiling of 0.0 — both margins comfortable. */
const WPT_PRESENCE_REF_MIN_PCT = 0.02;
const WPT_PRESENCE_RATIO_MIN   = 0.05;

/** wave-13 SEMANTIC-PRESENCE gate predicate (pure + exported for the unit
 *  calibration pins). Takes the `semanticPresence` block diffWebVsRef stamps
 *  ({ aCoveragePct: CAPTURE ink %, bCoveragePct: REF ink % } — argument
 *  order of diffWebVsRef) and answers "does the ref carry visible content
 *  the capture essentially lacks?".
 *
 *  Fails (returns true) only when BOTH hold:
 *    1. the ref visibly has ink       (bCoveragePct ≥ REF_MIN_PCT), and
 *    2. the capture carries < 5 % of the ref's ink mass
 *                                     (aCoveragePct / bCoveragePct < RATIO_MIN).
 *  Deliberately ONE-directional (ref-has, capture-lacks): the reverse
 *  asymmetry (capture over-paints) is real divergence the whole-canvas SSIM
 *  already punishes, and several honest passes over-paint slightly
 *  (wave12-gate: 3d-rendering-context-and-inline ios cap 5.104/ref 0.856
 *  still ssim-fails on its own).
 *  `null`/absent presence (size-mismatch guard in computeSemanticPresence,
 *  or a pre-v4.3 diff) → false: UNKNOWN presence is not FAILED presence —
 *  same "unknown ≠ divergent" stance as isColorDivergent above. */
/** wave-15 LOW-CONTENT-DENSITY triage flag threshold: a side is "mostly
 *  background" when its white-canvas ink coverage is under 15 % — i.e. more
 *  than 85 % of the image is the WPT_CANVAS_BG white. Chosen per the wave-15
 *  metrology observation (css-backgrounds attachment-fixed-inside-transform-1
 *  passes vs-ref at ssim 0.963 with ~90 % of BOTH images white while the
 *  actual content is MISPLACED — the body-root Height stacking issue, queued
 *  separately): when both sides are overwhelmingly background, whole-canvas
 *  SSIM is dominated by background-vs-background agreement and a "pass" says
 *  little about where the ink actually landed. */
const WPT_LOW_CONTENT_DENSITY_MAX_COVERAGE_PCT = 15;

/** wave-15 LOW-CONTENT-DENSITY triage predicate (pure + exported for unit
 *  pins). Takes the same `semanticPresence` block computePresenceFailed
 *  consumes ({ aCoveragePct: capture ink %, bCoveragePct: ref ink % }) and
 *  flags pairs where BOTH images are > 85 % background (coverage < 15 % on
 *  each side). TRIAGE-ONLY — deliberately feeds NEITHER wptPass nor
 *  scoreExcluded (same stance as colorComposite/colorDivergent: a visibility
 *  channel, not a pass/fail input), because low ink density is the NORM for
 *  WPT reftests (most wave12-gate refs measure under 8 % coverage) — the
 *  flag means "read this row's SSIM with less confidence", not "this row is
 *  wrong". Unknown/absent presence → false (unknown ≠ low-density, the
 *  isColorDivergent stance). */
export function computeLowContentDensity(semanticPresence) {
  // Degenerate/missing metric block (size mismatch, pre-v4.3 diff) → cannot
  // judge density, never flag.
  if (!semanticPresence || typeof semanticPresence !== 'object') return false;
  const cap = semanticPresence.aCoveragePct;   // platform capture ink %
  const ref = semanticPresence.bCoveragePct;   // browser-ref ink %
  // Non-numeric fields (defensive: hand-edited manifests) → unknown → false.
  if (typeof cap !== 'number' || typeof ref !== 'number') return false;
  // BOTH sides must be mostly background — one inked side means the SSIM is
  // comparing real content and the flag would only add noise.
  return cap < WPT_LOW_CONTENT_DENSITY_MAX_COVERAGE_PCT
      && ref < WPT_LOW_CONTENT_DENSITY_MAX_COVERAGE_PCT;
}
// Exported for the unit pins alongside the other calibrated constants.
export { WPT_LOW_CONTENT_DENSITY_MAX_COVERAGE_PCT };

function computePresenceFailed(semanticPresence) {
  // Degenerate/missing metric → cannot judge, never veto.
  if (!semanticPresence || typeof semanticPresence !== 'object') return false;
  const cap = semanticPresence.aCoveragePct;   // platform capture ink %
  const ref = semanticPresence.bCoveragePct;   // browser-ref ink %
  // Non-numeric fields (defensive: hand-edited manifests) → unknown → pass.
  if (typeof cap !== 'number' || typeof ref !== 'number') return false;
  // Blank (or sub-floor) ref: nothing to be missing — blank-vs-blank is the
  // honest pass for "renders nothing" tests. Also keeps the tiny-ink-both-
  // sides pair (wave12 background-color-animation-with-table2 android,
  // cap 0.043/ref 0.047) out of the gate's jurisdiction.
  if (ref < WPT_PRESENCE_REF_MIN_PCT) return false;
  // The asymmetry test proper: capture ink mass under 5 % of the ref's.
  return (cap / ref) < WPT_PRESENCE_RATIO_MIN;
}

/** Color-aware composite score for a browser-ref diff (TITAN-WHITE lane).
 *
 *  WHY: raw SSIM is effectively COLOR-BLIND — it runs on luminance
 *  structure, so wave-9 measured a FULL red→green repaint moving SSIM by
 *  only 0.0001 (docs/STATUS.md, wave-9 section). A test can therefore sit
 *  at ssim 0.99 while painting the wrong hue everywhere. The composite
 *  folds the perceptual color error back in:
 *
 *      colorComposite = ssim − min(0.2, labDeltaE.mean / 50)
 *
 *  Formula rationale:
 *    - labDeltaE.mean is the mean CIEDE2000 ΔE over sampled pixels
 *      (compare-screenshots-metrics.mjs). ΔE ≈ 2.3 is the classic
 *      just-noticeable difference; a whole-image hue swap like the wave-9
 *      red→green case lands mean ΔE in the tens.
 *    - /50 normalises so a saturated full-image hue error (mean ≈ 50,
 *      e.g. red↔green is ~86 max) saturates the penalty, while AA-noise
 *      pairs (mean < 0.5) lose < 0.01.
 *    - min(…, 0.2) caps the penalty so structure still dominates the
 *      score: a perfect-structure wrong-hue pair floors at ssim − 0.2,
 *      clearly below the 0.95 bar but not nuked to zero.
 *
 *  This is a VISIBILITY/TRIAGE signal only — the wptPass criterion stays
 *  raw ssim ≥ 0.95 (computeWptPass above, deliberately untouched) so the
 *  corpus pass-rate series keeps one definition. Returns null when ssim is
 *  unavailable; falls back to the raw ssim when labDeltaE could not be
 *  computed (color-unknown ≠ color-penalised). Pure + exported for unit
 *  pins. */
function computeColorComposite(ssim, labDeltaE) {
  // No SSIM → no base score to penalise; the diff is already error-shaped.
  if (typeof ssim !== 'number') return null;
  const mean = labDeltaE?.mean;
  // labDeltaE is null for degenerate pairs (all-transparent, metric error):
  // treat as "no color information", not "no color error".
  if (typeof mean !== 'number' || !Number.isFinite(mean)) return +ssim.toFixed(4);
  const penalty = Math.min(0.2, mean / 50);
  return +(ssim - penalty).toFixed(4);
}

/** Per-channel histogram-KL threshold for the colorDivergent stamp.
 *
 *  Calibration (recorded values — pinned in inject-wpt-block.test.mjs):
 *    - The wave-9 measured red→green repaint carries histogramKL
 *      {r:0.0609, g:0.2396, b:0.0205} (tools/titan/runs/wave9-gate/
 *      sections/css-flexbox manifest, iOS-web rows) — the canonical
 *      "SSIM missed a full hue swap" case. The stamp MUST catch g=0.2396.
 *    - AA-noise / correctly-rendering browser-ref pairs in the wave9c-gate
 *      css-break + wave9-gate css-flexbox manifests top out at max-channel
 *      KL 0.0197 (css-break non-white-ink passes) and 0.0024 (css-flexbox
 *      passes). The stamp must NOT fire there.
 *  0.1 sits ≥5× above the measured AA ceiling and 2.4× below the measured
 *  hue-swap value — both margins comfortable. NOTE: the white-ink-
 *  camouflage passes (css-break abspos-in-opacity, max KL 0.30–0.41 on the
 *  pre-white-canvas corpus) DO stamp colorDivergent — that is intentional:
 *  their color MASS genuinely diverges, and the stamp is triage colour,
 *  not a pass/fail input. */
const COLOR_DIVERGENT_KL_THRESHOLD = 0.1;

/** True when ANY channel's histogram KL exceeds the calibrated threshold —
 *  the boolean triage twin of computeColorComposite (same wave-9 rationale:
 *  surface hue swaps SSIM cannot see). histogramKL absent/null (size
 *  mismatch, metric error) → false: unknown is not divergent, and the raw
 *  metrics are still on the diff for investigators. Pure + exported for
 *  unit pins. */
function isColorDivergent(histogramKL) {
  if (!histogramKL || typeof histogramKL !== 'object') return false;
  const channels = [histogramKL.r, histogramKL.g, histogramKL.b];
  return channels.some((v) => typeof v === 'number' && v > COLOR_DIVERGENT_KL_THRESHOLD);
}

/** Tags that mean the HARNESS cannot even DELIVER the test's inputs —
 *  scores for these measure a delivery gap, not renderer divergence, so
 *  they are excluded from headline scoring. Deliberately NARROW: broad
 *  capability tags (requires-fragmentation, requires-viewport-canvas,
 *  requires-float-layout, …) stay SCORED — those tests render something
 *  comparable and their divergence is real information the corpus-v1..v3
 *  history already counts (excluding them would gut the denominator from
 *  82 to ~14 and make every corpus snapshot incomparable). First cut of
 *  this gate excluded on ANY notApplicable tag and did exactly that —
 *  caught at the wave-8 device gate when every section row came back
 *  [NA-excluded]. */
const SCORE_EXCLUDED_TAGS = new Set(['requires-bundled-asset']);

/** wave-15 EXTRACTION-WALL tags — the honest-scoring boundary's second
 *  exclusion family. Like SCORE_EXCLUDED_TAGS these mark tests whose score
 *  measures a HARNESS gap, not renderer divergence — but here the gap is the
 *  EXTRACTION wall, not asset delivery: the static extractor captures the
 *  pre-script DOM (wpt-not-applicable.mjs Rule 4 rationale), so a test whose
 *  visual output depends on post-load script execution (DOM mutation,
 *  top-layer promotion, script-driven scroll offsets) compares a state the
 *  pipeline never even attempts to produce. Measured wave-15: 9 of the 12
 *  scored css-position tests were exactly this wall — the tagger fired
 *  requires-script-mutation but the gate only excluded on
 *  requires-bundled-asset, so the wall was scored as renderer failure.
 *
 *  UNCONDITIONAL by construction (contrast the delivery-aware
 *  requires-bundled-asset branch): the wave-13 cross-check works because the
 *  extractor stamps a per-asset delivery record (lossyReasons from
 *  inlineFixtureAssets) that can corroborate or refute the textual tag.
 *  Script execution has NO analogous delivery record — the extractor never
 *  runs scripts, so there is nothing it could stamp to say "this one was
 *  actually delivered". With no ground truth to consult, the textual tag is
 *  the best signal available and excludes on its own; this is the
 *  wave-14/13-consistent EXTENSION of the boundary, not a loosening of it.
 *
 *  Deliberately NARROW like SCORE_EXCLUDED_TAGS: broad capability tags
 *  (requires-fragmentation, requires-float-layout, …) still describe tests
 *  the harness DOES deliver and render — those stay scored (the wave-8
 *  lesson: excluding on any tag gutted the denominator 82 → ~14).
 *  'requires-script-driven-scroll' (Rule 18) is included because it is a
 *  strict subset of the same wall — post-script scroll offsets baked into
 *  the ref are unreachable for the identical no-script-execution reason. */
const EXTRACTION_WALL_TAGS = new Set([
  'requires-script-mutation',      // Rule 4  — post-load DOM / top-layer mutation
  'requires-script-driven-scroll', // Rule 18 — post-load scroll-offset mutation
]);
// Exported for unit pins (inject-wpt-block.test.mjs asserts the exact tag
// set so a silent widening/narrowing of the wall cannot land unreviewed).
export { EXTRACTION_WALL_TAGS };

/** wave-8 corpus-honesty gate (pure — exported for unit tests): when a test
 *  carries ≥1 HARNESS-DELIVERY tag (SCORE_EXCLUDED_TAGS — not every
 *  notApplicable tag), its browser-ref diffs are EXCLUDED from scoring:
 *  `wptPass` is nulled (it was never a meaningful pass/fail — the harness
 *  never delivered the inputs) and `scoreExcluded: true` is stamped so any
 *  mean-SSIM / passAt95 aggregation over `wpt.results[].browserRef.diffs`
 *  can filter without consulting the buckets index. Raw metrics (ssim,
 *  pixelMismatchedPct, pHash, …) are deliberately left intact for
 *  investigators asking "what would this test have scored if rendered?" —
 *  only the SCORING fields are neutralised. Error-shaped diffs (`{error}`)
 *  are left alone.
 *
 *  wave-13 DELIVERY-AWARE refinement (corpus-v4.3 SCORING boundary): the
 *  `requires-bundled-asset` tag in wpt-buckets.json is a TEXTUAL
 *  url(support/…) regex (wpt-not-applicable.mjs Rule 20) that never consults
 *  the wave-8 asset inliner — extract-fixture.mjs's inlineFixtureAssets()
 *  inlines raster support assets < MAX_INLINE_ASSET_BYTES (8 KB) as data
 *  URIs, and only marks the component `_lossy` with reason
 *  'requires-bundled-asset' when an asset is genuinely UNDELIVERABLE
 *  (missing / ≥ 8 KB / non-raster). Rule 20 stays a pure string-grep by
 *  design (the wave-8 no-IO purity constraint documented at its RX comment:
 *  classifyAll must remain a pure function over ~24k files), so THIS gate is
 *  the sanctioned IO-adjacent post-pass: it cross-checks the tag against the
 *  extractor's actual delivery signal (`lossyReasons`, threaded from the
 *  combined fixture's keyMap — no new IO, the data is already in memory).
 *  Measured wave-13 staleness this repairs: css-backgrounds
 *  background-color-animation-with-images / background-334 /
 *  background-attachment-350 (assets 218–961 B, all inlined; per-test IR
 *  provably carries data URIs; extractor lossyReasons carry NO
 *  'requires-bundled-asset') were score-excluded on the raw tag — the
 *  css-backgrounds scoring denominator moves 9 → 12.
 *
 *  Contract per tag: `requires-bundled-asset` excludes ONLY when the
 *  extractor corroborates it (lossyReasons includes the same tag). A caller
 *  that supplies NO lossyReasons array at all (undefined/null — e.g. a
 *  pre-wave-8 combined fixture with no lossy fields in its keyMap) gets the
 *  CONSERVATIVE legacy behaviour (tag alone excludes): absent delivery
 *  evidence must not silently promote a test into the scored set.
 *
 *  wave-15 EXTRACTION-WALL extension: tags in EXTRACTION_WALL_TAGS
 *  (requires-script-mutation, requires-script-driven-scroll) exclude
 *  UNCONDITIONALLY — lossyReasons is never consulted for them, because
 *  script execution has no delivery record by construction (see the
 *  EXTRACTION_WALL_TAGS comment). Measured wave-15: 9/12 scored
 *  css-position tests were the script-mutation wall scored as renderer
 *  failure.
 *
 *  @param {string[]|undefined} naTags  notApplicable tags for the test
 *  @param {Array<object|null>} refDiffs diffs to neutralise (mutated in place)
 *  @param {string[]|undefined} [lossyReasons] extractor lossy reasons for the
 *         test (keyMap meta.lossyReasons); array ⇒ delivery-aware, absent ⇒
 *         conservative legacy exclusion
 *  @returns {boolean} true when the test is score-excluded */
export function applyNaScoreGate(naTags, refDiffs, lossyReasons) {
  const isNa = Array.isArray(naTags) && naTags.some((t) => {
    // wave-15 extraction-wall branch: script-execution tags exclude
    // UNCONDITIONALLY — no lossyReasons cross-check is possible because the
    // extractor never runs scripts and so has no delivery record to consult
    // (full rationale at EXTRACTION_WALL_TAGS above). Checked FIRST so the
    // delivery-aware bundled-asset branch below stays byte-for-byte the
    // wave-13 behaviour for its own tag.
    if (EXTRACTION_WALL_TAGS.has(t)) return true;
    // Not a harness-delivery tag → never excludes (unchanged wave-8 rule).
    if (!SCORE_EXCLUDED_TAGS.has(t)) return false;
    // Delivery-aware branch: when the extractor's lossy record is available
    // it is the ground truth — the textual tag only excludes if the
    // extractor ALSO says the asset was undeliverable (same tag string,
    // stamped by inlineFixtureAssets). Assets it inlined as data URIs ARE
    // delivered, so the test renders with real inputs and must be scored.
    if (Array.isArray(lossyReasons)) return lossyReasons.includes(t);
    // No delivery record supplied → conservative legacy behaviour.
    return true;
  });
  if (!isNa) return false;
  for (const d of refDiffs ?? []) {
    // Skip absent platforms (null) and error records — neither carries
    // scoring fields to neutralise.
    if (!d || typeof d !== 'object' || d.error) continue;
    d.wptPass = null;        // never a real pass/fail for an NA test
    d.scoreExcluded = true;  // aggregators filter on this stamp
  }
  return true;
}

/** wave-13 NATIVE-PARITY secondary metric (pure + exported for unit tests).
 *
 *  WHY: tests carrying capability notApplicable tags (requires-form-control-
 *  rendering, requires-float-layout, requires-orthogonal-flow, …) hit a
 *  KNOWN capability wall against the browser ref — their browser-ref score
 *  measures the wall, not the runtimes. But the CROSS-PLATFORM pair SSIMs
 *  (iOS-Android / iOS-web / Android-web) are already computed for the same
 *  test and answer a different, still-meaningful question: do the three
 *  runtimes at least agree WITH EACH OTHER on what they render? Surfacing
 *  that as `nativeParity` lets dashboards visibly distinguish "browser
 *  parity blocked by <capability tag>, native parity 0.98–1.0" (a wall,
 *  runtimes consistent) from "native parity ALSO low" (real cross-runtime
 *  divergence hiding behind the wall). Nothing here feeds wptPass or
 *  scoreEligible — it is a triage/visibility channel only.
 *
 *  Only stamped for tests that carry ≥1 notApplicable tag (the capability-
 *  walled population; untagged tests' pair data already speaks for itself in
 *  `pairs`) and only when at least one cross-platform pair has a numeric
 *  SSIM (single-platform runs → null, dashboards render the gap).
 *
 *  @param {object|null} pairs   the per-test aggregated pairs block
 *                               ({'iOS-Android':{ssim,…}|null, …})
 *  @param {string[]|undefined} naTags notApplicable tags for the test
 *  @returns {{pairs: Object<string,number>, min: number, max: number}|null} */
export function computeNativeParity(pairs, naTags) {
  // No capability tag → not a walled test → no secondary metric needed.
  if (!Array.isArray(naTags) || naTags.length === 0) return null;
  // No cross-platform pair data at all (web-only smoke) → nothing to report.
  if (!pairs || typeof pairs !== 'object') return null;
  const parityPairs = {};
  let min = null, max = null;
  for (const [kind, p] of Object.entries(pairs)) {
    // Absent platforms contribute null pairs; skip anything without a
    // numeric SSIM (error rows carry none) — no silent zero-filling.
    if (!p || typeof p.ssim !== 'number') continue;
    parityPairs[kind] = p.ssim;                       // already worst-of-components (buildResults)
    min = min === null ? p.ssim : Math.min(min, p.ssim);
    max = max === null ? p.ssim : Math.max(max, p.ssim);
  }
  // All pairs missing → null, NOT {pairs:{}}: consumers can trust that a
  // non-null nativeParity always has a meaningful min/max range.
  if (min === null) return null;
  return { pairs: parityPairs, min, max };
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
    // wave-13 presence gate: stamped on EVERY diff (true/false, uniform for
    // triage queries — "presenceFailed:true" is the blank-capture beacon).
    diff.presenceFailed = computePresenceFailed(diff.semanticPresence);
    // WPT-native pass: raw SSIM ≥ 0.95 OR within the declared fuzzy
    // tolerance — VETOED by the semantic-presence gate (corpus-v4.3: a blank
    // capture can no longer "pass" a mostly-blank ref, see computeWptPass).
    // Raw `diff.ssim` is left untouched so downstream can honour all bars.
    diff.wptPass = computeWptPass(diff.ssim, diff.wptFuzzyMatch, diff.presenceFailed);
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
    // wave-13 presence gate (same stamp + veto as the stitch path — the two
    // measured wave12 vacuous passes came through THIS composed path).
    diff.presenceFailed = computePresenceFailed(diff.semanticPresence);
    // WPT-native pass: raw SSIM ≥ 0.95 OR within fuzzy — presence-vetoed.
    diff.wptPass = computeWptPass(diff.ssim, diff.wptFuzzyMatch, diff.presenceFailed);
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

    // The WPT test key (`wpt__<section>__<stem>`) is shared naming between
    // the composed capture filename (ComposedCaptureGallery.tsx / native
    // composed inboxes), build-combined-fixture's component roots, and —
    // crucially for the pair aggregation below — compare-screenshots ROW
    // names in composed-mode runs. Computed once up here (wave-13) so both
    // the pair lookup and the browser-ref block share one definition.
    const refStem = basename(testRel, '.html');
    const testKey = `wpt__${meta.section}__${refStem}`;

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
    // wave-13: composed-mode runs name their compare rows after the ONE
    // composed capture per test — `<safe(testKey)>.png`, no NNN_ prefix and
    // no __<idx> suffix — so the per-component lookup above finds nothing
    // and every composed run recorded `pairs: null` (measured across all 7
    // wave12-gate section manifests: 0 tests with pairs while rows[] held
    // full iOS-Android/iOS-web/Android-web SSIMs). Without this the
    // nativeParity metric below would be permanently null on the very runs
    // it exists for. rowIndex() files non-`NNN_*.png` rows under their FULL
    // name, so the composed row lives at `<safe(testKey)>.png`. Composed
    // and per-component captures are mutually exclusive within one run
    // (the harness emits one style), so this can't double-count a pair.
    const composedRow = ix[`${safe(testKey)}.png`];
    if (composedRow) {
      anyMatched = true;
      for (const k of pairKinds) {
        const p = composedRow.pairs?.[k];
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
    // refStem + testKey are computed above (before the pair aggregation) —
    // the browser-ref path just reuses them.
    const refPng = refsRoot
      ? join(refsRoot, meta.section, `${refStem}.png`)
      : null;
    const browserRefAvailable = refPng ? await_fs_exists_sync(refPng) : false;

    let webRefDiff = null, iosRefDiff = null, androidRefDiff = null;
    if (browserRefAvailable) {
      const cacheKey = safe(testRel.replace(/\.html$/, ''));
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
    // wave-8 corpus-honesty gate: NA-tagged tests must never feed headline
    // scoring. FIX-E above only flipped the divergence LABEL — the
    // browserRef diffs still carried wptPass:true/false + raw SSIM, and
    // every mean-SSIM / passAt95 aggregation over
    // `wpt.results[].browserRef.diffs` (the corpus-v* reproduce recipe)
    // counted known harness-delivery gaps as renderer divergence. The gate
    // nulls wptPass + stamps scoreExcluded:true on each diff (raw metrics
    // stay for investigators) and surfaces a per-test `scoreEligible`
    // boolean that aggregators MUST filter on.
    // wave-13: the gate is now DELIVERY-AWARE — meta.lossyReasons (the
    // extractor's actual asset-delivery record from the combined fixture's
    // keyMap) is threaded in so a stale textual requires-bundled-asset tag
    // no longer excludes a test whose assets the wave-8 inliner delivered
    // as data URIs (see applyNaScoreGate's wave-13 comment for the three
    // measured css-backgrounds cases; denominator 9 → 12 there).
    const isNa = applyNaScoreGate(naTags, [webRefDiff, iosRefDiff, androidRefDiff], meta.lossyReasons);
    if (isNa) {
      divergence = 'test-not-applicable';
    }

    results[testRel] = {
      // wave-8: the one boolean scoring paths filter on. false ⇔ the test
      // carries ≥1 notApplicable tag; its metrics are diagnostic-only.
      scoreEligible: !isNa,
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
      // wave-13 NATIVE-PARITY secondary metric: for capability-walled tests
      // (≥1 notApplicable tag) surface the already-computed cross-platform
      // pair SSIMs so "browser parity blocked by <tag>" runs are visibly
      // distinguishable from real native divergence. null for untagged
      // tests and for tagged tests with no cross-platform pair data (see
      // computeNativeParity). anyMatched gates on the same condition the
      // `pairs` field uses so the two stay consistent.
      nativeParity: anyMatched ? computeNativeParity(pairs, naTags) : null,
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
  computeColorComposite, isColorDivergent, COLOR_DIVERGENT_KL_THRESHOLD,
  // wave-13 corpus-v4.3 SCORING boundary: the semantic-presence gate
  // (predicate + its calibrated thresholds) exported so the unit pins can
  // hold the wave12-gate calibration without re-deriving coverage.
  computePresenceFailed, WPT_PRESENCE_REF_MIN_PCT, WPT_PRESENCE_RATIO_MIN,
  WPT_CANVAS_BG,
};

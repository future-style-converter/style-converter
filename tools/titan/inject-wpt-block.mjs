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
// basename dropped at the wave-21 collision fix — the one stem-derivation
// site (the composed testKey / ref-PNG stem) now uses fixtureStem().
import { resolve, dirname, join } from 'node:path';
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
  // wave-22 HONEST-FRAME scoring: the per-channel "is this pixel background"
  // tolerance (8/255) reused by countOverflowInk so "overflow ink" and the
  // presence gate agree on what counts as ink vs canvas.
  SEMANTIC_PRESENCE_TOLERANCE,
} from '../visual/compare-screenshots-metrics.mjs';
// The ONE canonical compare-pipeline sanitiser (dot KEPT). This module is the
// consumer side of the pipeline — its diff globs MUST use the exact same rule
// the feeders/web capture used to WRITE the filenames, or a key with a "."
// silently drops a platform column. Sharing the helper guarantees agreement.
// fixtureStem: the ONE canonical fixture-stem derivation (wave-21 collision
// fix) — the composed testKey and the browser-ref PNG lookup below must use
// the SAME subdir-encoded stem the producers (build-combined-fixture's
// component keys, capture-browser-ref's cache paths) derived, or a nested
// test would glob for captures/refs under a name nobody wrote.
import { safe, fixtureStem } from './safe-name.mjs';
// wave-49 NOVEL-INK: the area-normalized wrong-answer detector. Stamped on
// EVERY browser-ref diff for triage; it only becomes a wptPass VETO when
// TITAN_NOVEL_INK_VETO is set (see NOVEL_INK_VETO_ENABLED below) because its
// wave-49 calibration MISSED the recall bar (it clears the false-positive bar
// and the mutation test) — full numbers at NOVEL_INK_VETO_ENABLED.
import { computeNovelInk, computeNovelInkFailed } from './novel-ink.mjs';

const pixelmatch = pixelmatchDefault.default ?? pixelmatchDefault;

const __filename = fileURLToPath(import.meta.url);
const __dirname  = dirname(__filename);
const REPO_ROOT  = resolve(__dirname, '..', '..');

function arg(name) {
  const i = process.argv.indexOf(name);
  return i >= 0 ? process.argv[i + 1] : null;
}

// ── wave-25 CAL-RC1: the --refs-root canvas-rev normaliser ───────────────────
//
// run-titan.sh and section-runner.sh pass a LITERAL
// `$WPT_DIR/refs/$WPT_REF/<canvas-rev>` as --refs-root. That literal is a
// second copy of capture-browser-ref.mjs's CANVAS_REV, and through wave-24 the
// two had to be edited together: bump the constant, forget the scripts, and
// the scorer keeps reading refs from the PREVIOUS canvas contract — every diff
// silently measured against stale geometry, with no error anywhere (the PNGs
// exist; they are just wrong). CAL-RC1 bumps the rev, so instead of paying
// that coupling again we delete it: a refs-root whose LAST segment is a
// known-stale rev is rewritten to the live one.
//
// Deliberately conservative:
//   * only a segment in KNOWN_STALE_CANVAS_REVS is rewritten. An unknown tail
//     is left EXACTLY as given (someone reproducing a historical corpus with a
//     hand-built path keeps their path), and the swap is announced on stderr —
//     never silent.
//   * a root already on the live rev, a null root, and a root with no rev tail
//     all pass through untouched.
export const LIVE_CANVAS_REV = 'white-black-ink-font-lh-imgpad-htmlpins';
// Every canvas contract that has ever produced a refs tree, newest first.
// Pinned against capture-browser-ref.mjs's CANVAS_REV in the unit tests, so a
// future bump that forgets to append here fails `node --test`.
export const KNOWN_STALE_CANVAS_REVS = [
  // wave-30 A5: inherited text pins still on :where(body), so any ref
  // declaring color/font-family/line-height at :root/html rasterised the
  // CLOBBERED value (capture-browser-ref.mjs canvasFrameCss A5 note).
  'white-black-ink-font-lh-imgpad',
  'white-black-ink-font-lh',   // corpus-v4.1 typography, CSS-padded frame
  'white-black-ink-font',      // v4.1 scratch: no line-height pin
  'white-black-ink',           // ink-only, no font pin
  'white',                     // corpus-v4.0 white canvas, white ink
];
export function normalizeRefsRoot(root) {
  if (!root) return root;                       // --refs-root omitted → nothing to fix
  // TRAILING SEPARATORS FIRST. `--refs-root .../white-black-ink-font-lh/` is
  // the same directory as the un-slashed form and every path join below
  // behaves identically — but the tail regex cannot see a rev through it, so
  // without this strip the slashed spelling would fall through as "no rev
  // tail" and read the STALE tree in complete silence. That is precisely the
  // failure this normaliser exists to delete, so it must not survive a
  // one-character edit in run-titan.sh / section-runner.sh.
  const trimmed = root.replace(/[\\/]+$/, '');
  if (!trimmed) return root;                    // root was only separators → nothing to fix
  // Split on both separators so a Windows-shaped path normalises too; the
  // rejoin below uses whatever separator the caller used.
  const m = /^(.*)([\\/])([^\\/]+)$/.exec(trimmed);
  if (!m) return root;                          // single-segment path → no rev tail
  const [, head, sepChar, tail] = m;
  if (tail === LIVE_CANVAS_REV) return root;    // already current
  if (!KNOWN_STALE_CANVAS_REVS.includes(tail)) return root;  // unknown → caller knows best
  const fixed = `${head}${sepChar}${LIVE_CANVAS_REV}`;
  // LOUD, never silent: this line is the record that the scorer did not read
  // the path it was handed.
  process.stderr.write(`[inject-wpt-block] --refs-root canvas-rev '${tail}' is stale — ` +
    `reading refs from '${LIVE_CANVAS_REV}' instead\n`);
  return fixed;
}

// CLI args parsed lazily inside main() so importing this file from unit
// tests (which want only the pure helpers like stitchPngsVertically) doesn't
// trip the usage-check exit. Each main()-only constant is shadowed inside
// the function below.
const MANIFEST_PATH = arg('--manifest');
const TESTS_FILE    = arg('--tests');
const WPT_REF       = arg('--wpt-ref');
const RUN_ID        = arg('--run-id') || new Date().toISOString();
const REFS_ROOT     = normalizeRefsRoot(arg('--refs-root'));  // tools/wpt/refs/<sha>/<canvas-rev>
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

// ── wave-22 HONEST-FRAME scoring helpers ─────────────────────────────────────
//
// Mechanism this repairs (skeptic-proved on the wave-21 text-decor movers):
// the legacy SSIM ran on the UNION frame with BOTH sides white-padded, so a
// capture TALLER than the ref grew the mean's denominator with rows that are
// mostly white-vs-white agreement — DILUTING real divergence. Measured:
// text-decoration-color's wave21-gate captures (390x1124–1136, text WRAPPED —
// wrong) scored 0.71–0.86 while the wave21-final unwrapped fixes (390x600,
// provably closer on equal-frame crops: 0.636 vs 0.575) scored LOWER
// (0.49–0.71). Same inflation on css-text empty-span-001: the wave-20
// capture with 100px phantom gaps (390x1512, mostly white) out-scored the
// far-closer wave-21 render. Honest renders must win.
//
// The honest frame is the REF's own frame — the fixed 390x600 canvas every
// browser-ref is captured on:
//   - capture SHORTER/NARROWER: pad with the WHITE canvas. Neutral where
//     the ref is white too; automatically divergent where the ref has ink
//     (white capture pixel vs ref ink → low local SSIM). Unchanged.
//   - capture TALLER/WIDER: the ref-frame region is compared 1:1, and the
//     capture's OUT-OF-FRAME INK is folded into the mean as fully-divergent
//     area: score = mssim_refFrame × A_ref / (A_ref + overflowInkPx).
//     Out-of-frame WHITE contributes nothing (a white tail over the ref's
//     white canvas extension is genuinely neutral) — so the denominator can
//     no longer be inflated by empty rows, and overflow content is punished
//     instead of hidden.

/** Fit `img` (pngjs PNG) to a W×H frame: white-filled canvas, overlap
 *  copied top-left aligned. Pure buffer ops (no sharp) — exported for unit
 *  pins. Returns `img` itself when it already matches the frame. */
export function fitToRefFrame(img, W, H) {
  if (img.width === W && img.height === H) return img;
  const out = new PNG({ width: W, height: H });
  // White opaque canvas — the corpus-v4 WPT canvas (WPT_CANVAS_BG).
  out.data.fill(0xFF);
  // Copy the overlapping rect row by row (crop when img is larger,
  // white-pad remains when smaller).
  const copyW = Math.min(img.width, W);
  const copyH = Math.min(img.height, H);
  for (let row = 0; row < copyH; row++) {
    const srcStart = row * img.width * 4;
    img.data.copy(out.data, row * W * 4, srcStart, srcStart + copyW * 4);
  }
  return out;
}

/** Count the capture pixels OUTSIDE the ref frame (x ≥ W or y ≥ H) that
 *  carry ink — i.e. deviate from the WHITE WPT canvas by more than the
 *  shared presence tolerance on any channel. These are the pixels the
 *  ref-frame crop would silently discard; the honest score counts each as
 *  fully-divergent area instead. Pure + exported for unit pins. */
export function countOverflowInk(img, W, H, tolerance = SEMANTIC_PRESENCE_TOLERANCE) {
  let ink = 0;
  for (let y = 0; y < img.height; y++) {
    // Rows fully inside the frame only contribute their right-of-frame
    // columns; rows below the frame contribute every column.
    const xStart = y < H ? W : 0;
    if (xStart >= img.width) continue;
    for (let x = xStart; x < img.width; x++) {
      const i = (y * img.width + x) * 4;
      if (
        Math.abs(img.data[i]     - 0xFF) > tolerance ||
        Math.abs(img.data[i + 1] - 0xFF) > tolerance ||
        Math.abs(img.data[i + 2] - 0xFF) > tolerance
      ) ink++;
    }
  }
  return ink;
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
  // Deliberately still the UNION frame: mismatch COUNTS (which the WPT
  // fuzzy budgets consume) must keep seeing overflow ink pixel-for-pixel,
  // and union counting is already honest for counts (white-vs-white adds
  // zero mismatches — only means can be diluted, not counts).
  const diff = new PNG({ width: W, height: H });
  const mismatched = pixelmatch(A.data, B.data, diff.data, W, H, {
    threshold: 0.25,
    includeAA: true,
  });
  const pixelPct = (mismatched / (W * H)) * 100;

  // SSIM — wave-22 HONEST-FRAME (see the helper banner above): scored on
  // the REF's own frame, with the capture's out-of-frame ink folded in as
  // fully-divergent area. Same-size pairs are byte-identical to the legacy
  // union-frame score (fitToRefFrame is the identity, overflow is 0).
  let ssimScore = null;
  let frame = null;
  try {
    const refArea = b.width * b.height;
    const aFit = fitToRefFrame(a, b.width, b.height);
    const aImg = { data: new Uint8ClampedArray(aFit.data), width: b.width, height: b.height };
    const bImg = { data: new Uint8ClampedArray(b.data), width: b.width, height: b.height };
    const r = computeSsim(aImg, bImg, { ssim: 'fast' });
    // Overflow-ink fold: every out-of-frame ink pixel joins the mean as
    // zero-similarity area; out-of-frame WHITE is neutral by construction.
    const overflowInkPx = countOverflowInk(a, b.width, b.height);
    ssimScore = +((r.mssim * refArea) / (refArea + overflowInkPx)).toFixed(4);
    // Provenance block for dashboards/investigators: the frame geometry,
    // the pre-fold ref-frame SSIM, and the fold input. Null when SSIM
    // itself failed (error-shaped diffs carry no frame data).
    frame = {
      refW: b.width, refH: b.height,
      capW: a.width, capH: a.height,
      overflowInkPx,
      ssimRefFrame: +r.mssim.toFixed(4),
    };
  } catch { /* leave null */ }

  // WPT-NATIVE fuzzy quantities, in the units the <meta fuzzy> budget is
  // written in. The budget's maxDifference bounds the MAX PER-CHANNEL RGB
  // DELTA (0–255) over every differing pixel, and totalPixels bounds the
  // COUNT of pixels that differ AT ALL. checkFuzzyMatch used to substitute
  // (a) labDeltaE.max — CIEDE2000 units on a 0–100-ish scale, computed on a
  // 1-in-4 subsample — for the per-channel budget, and (b) pixelmatch's
  // YIQ-thresholded count (threshold 0.25, blind to uniform shifts up to
  // 66/255 luma) for the raw count. Both substitutions understate the
  // diff, so out-of-budget pairs were rescued into wptPass. Measured while
  // verifying: 400 pixels differing by channel delta 135 read as
  // pixelmatch count 0. One exact pass, no thresholds, no sampling.
  let fuzzyDifferingPixels = 0;
  let fuzzyMaxChannelDelta = 0;
  for (let i = 0; i < A.data.length; i += 4) {
    const dr = Math.abs(A.data[i] - B.data[i]);
    const dg = Math.abs(A.data[i + 1] - B.data[i + 1]);
    const db = Math.abs(A.data[i + 2] - B.data[i + 2]);
    const d = dr > dg ? (dr > db ? dr : db) : (dg > db ? dg : db);
    if (d > 0) {
      fuzzyDifferingPixels++;
      if (d > fuzzyMaxChannelDelta) fuzzyMaxChannelDelta = d;
    }
  }

  const dssim = computeDssim(ssimScore);
  const histogramKL = computeHistogramKL(A, B);
  const perChannelSsim = await computePerChannelSsim(A, B);
  const pHash = await computePHash(A, B);
  const edgeSsim = await computeEdgeSsim(A, B);
  const labDeltaE = computeLabDeltaE(A, B, 4);

  const metrics = {
    pixelMismatchedCount: mismatched,
    // The WPT-native diff quantities (see the pass above) — what
    // checkFuzzyMatch actually consumes.
    fuzzyDifferingPixels,
    fuzzyMaxChannelDelta,
    pixelMismatchedPct: +pixelPct.toFixed(3),
    ssim: ssimScore,
    // wave-22 HONEST-FRAME provenance: frame geometry + pre-fold ref-frame
    // SSIM + overflow-ink count behind the honest `ssim` above (null when
    // SSIM errored). Lets an investigator separate "content divergence"
    // from "overflow penalty" without re-reading the captures.
    frame,
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
  // wave-49 NOVEL-INK block: of the pixels where the two images disagree, how
  // much of the CAPTURE's paint is in a colour the reference never uses. This
  // is the area-normalized measurement histogramKL cannot make (KL is
  // coverage-weighted over the whole canvas, so a total hue swap confined to a
  // small patch cannot move it — measured: css-view-transitions/
  // hit-test-unrelated-element paints the ref's green square pure red on all
  // three platforms at ssim 1.0000 and histogramKL max 0.0591, under the 0.1
  // bar). Computed on the SAME padded pair every other metric sees.
  metrics.novelInk = computeNovelInk(A, B);
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
  // WPT-native quantities ONLY (see their computation above). The ??
  // Infinity fallback means a manifest predating them can never be
  // rescued into a pass by the fuzzy budget — absent evidence fails
  // closed, the same direction every other gate here fails.
  const px = metrics.fuzzyDifferingPixels ?? Infinity;
  const maxDelta = metrics.fuzzyMaxChannelDelta ?? Infinity;
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
 *  Default false keeps the two-argument legacy call shape passing.
 *
 *  wave-25 CAL-RC6 SCORING-HONESTY boundary — two more unconditional vetoes,
 *  both defaulting false so every legacy call shape still compiles:
 *
 *    `colorFailed` (computeColorFailed): the pair's colour MASS diverges.
 *    SSIM is luminance-structure only — measured, a full red→green repaint
 *    moves it by 0.0001 — so css-gaps/flex-gap-decorations-001 painted a
 *    FILLED RED square against a ref whose own text says "filled green
 *    square and no red" and scored wptPass at ssim 0.9938. Structure agreed
 *    perfectly; the answer was the opposite of the test's assertion.
 *
 *    `coverageRatioFailed` (computeCoverageRatioFailed): the two sides carry
 *    wildly different amounts of ink. The old presence gate only looked at
 *    "ref has, capture lacks" past an absolute floor, so a capture painting
 *    3× the ref's ink (extra table cells), or 1/3 of it (unshaped text), or
 *    a whole missing image sailed through on background agreement alone.
 *
 *  Both are VETOES, exactly like the presence gate: a failing pair cannot be
 *  rescued by SSIM or by a declared fuzzy budget. */
function computeWptPass(ssim, fuzzyMatch, presenceFailed = false,
                        colorFailed = false, coverageRatioFailed = false,
                        novelInkVeto = false) {
  // The presence veto runs FIRST: a capture that renders none of the ref's
  // ink can never be a pass, whatever the whole-canvas metrics say.
  if (presenceFailed === true) return false;
  // Colour-mass veto (CAL-RC6): right shape, wrong answer.
  if (colorFailed === true) return false;
  // Ink-mass-asymmetry veto (CAL-RC6): right colours, wrong amount of them.
  if (coverageRatioFailed === true) return false;
  // wave-49 NOVEL-INK veto: the capture's disagreement is mostly paint in a
  // colour the reference never uses. Callers pass `false` unless
  // TITAN_NOVEL_INK_VETO is set — the wave-49 calibration cleared the
  // false-positive bar (0 fires on 51 hand-verified correct renders) but
  // MISSED the recall bar (12/18 on the census-selected defect set, and only
  // 2/12 on an unbiased one, against the 95 % required), so it ships OFF by
  // default rather than as a half-calibrated gate. Numbers below.
  if (novelInkVeto === true) return false;
  const ssimPass = typeof ssim === 'number' && ssim >= 0.95;
  return ssimPass || fuzzyMatch === true;
}

// ── wave-49 NOVEL-INK: the veto's OFF-by-default switch ──────────────────────
//
// DECISION RULE, fixed BEFORE measuring: the check ships ON only if it fires
// on ≥95 % of a hand-verified true-defect set AND on ≤2 % of ≥40 hand-verified
// correct renders, AND a mutation test proves it can fail. Measured once on
// wave48-final (4111 scored browser-ref cells, 3312 of them passing):
//
//   • MUTATION TEST                       ← PASSES (novel-ink.test.mjs bottom:
//     identical pair silent, one repainted 100x100 region trips the check)
//   • false positives   0 / 51  = 0.0 %   ← CLEARS the 2 % bar
//       78 randomly-drawn currently-passing cells were opened as REF|CAPTURE
//       pairs (two disjoint samples, seeds 4902 and 4904). 51 were confirmed
//       correct renders; the other 27 were themselves visibly wrong (see the
//       note below). The check fired twice in all 78, both on cells
//       hand-verified as DEFECTS — so zero false positives on the 51.
//   • recall           12 / 18  = 66.7 %  ← MISSES the 95 % bar
//       …and that 66.7 % is the OPTIMISTIC figure. Those 18 were drawn from
//       the wave-49 red-square census, whose rule requires capRedPct > 0.5 —
//       i.e. the set was selected for a LARGE wrong-coloured area, which is
//       the very quantity this check's novelPct bar measures. On the 12
//       wrong-colour defects that turned up unselected in the 78-cell random
//       sample, recall is 2/12 = 16.7 %. Both numbers miss the bar; the
//       honest one is the second.
//
// The dominant miss mechanism is measured, not guessed: when a render is BOTH
// wrong-coloured AND displaced, the divergent region fills with the
// reference's OWN palette (the displaced original) alongside the novel paint,
// so novelFractionOfDivergentInk falls under 0.5. The second mechanism is
// small marks — a recoloured underline or a 1px stripe clears the fraction bar
// but not the 0.1 %-of-frame mass bar.
//
// So the block is stamped on every diff (free triage signal, and the honest
// per-cell record) but does NOT feed wptPass unless an operator opts in. One
// env var, read once at module load so a run cannot change gate semantics
// halfway through.
//
// SIDE FINDING, recorded because it is larger than the thing this lane fixed:
// 27 of those 78 randomly-drawn PASSING cells (35 %) are visibly wrong renders
// — wrong colour, wrong position, wrong line breaking, missing glyphs. The
// red-square class this check targets is one visible slice of a much wider
// degeneracy in the pass column, and no colour-based check can reach the rest.
const NOVEL_INK_VETO_ENABLED = process.env.TITAN_NOVEL_INK_VETO === '1';

/** Gate the novel-ink stamp behind the opt-in switch. Kept as a named helper
 *  (rather than inlining the `&&`) so both diff paths provably apply the SAME
 *  rule and a unit test can pin it. */
function novelInkVetoActive(novelInkFailed) {
  return NOVEL_INK_VETO_ENABLED && novelInkFailed === true;
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

// ── wave-25 CAL-RC6: the colour-mass veto ────────────────────────────────────
//
// Minimum mean CIEDE2000 ΔE for `colorDivergent` to become a PASS/FAIL input
// rather than triage colour. 2.3 is the classic just-noticeable-difference:
// below it the two images are the same colour to a human eye.
//
// WHY A CORROBORATOR AT ALL — the lane brief asked for "colorDivergent ⇒ hard
// fail, full stop", and CALIBRATION REFUTED IT. Replaying the wave24-final
// corpus (720 browser-ref diffs, 587 passing), the bare stamp flips 58 passes
// — but a chunk of those are PIXEL-EXACT: css-color/background-color-rgb-001
// web-ref sits at ssim 1.0000, pixelMismatchedPct 0.000, mean ΔE 0.00, and
// still carries histogramKL 0.1602. Histogram KL divides by near-empty bins,
// so on a near-uniform image a handful of AA pixels produce a large ratio out
// of nothing. Hard-failing on the raw stamp would have failed demonstrably
// correct renders — exactly the "zero TRUE passes may flip" bar. Requiring a
// perceptual second opinion keeps every one of those (ΔE ≈ 0) and keeps the
// real cases (ΔE 2.3 … 12.6). The stamp itself is UNCHANGED so the historical
// triage series stays comparable.
const WPT_COLOR_FAIL_DELTA_E_MIN = 2.3;

/** wave-25 CAL-RC6 colour-mass veto predicate (pure + exported for the unit
 *  pins). Fails only when BOTH hold:
 *    1. the histogram-KL stamp fired (isColorDivergent — some channel's
 *       distribution genuinely moved), and
 *    2. the mean CIEDE2000 ΔE is at or above the JND, i.e. a human would
 *       see the difference.
 *  Unknown ΔE (null labDeltaE — degenerate/size-mismatched pair) → NOT a
 *  failure: same "unknown ≠ divergent" stance as isColorDivergent itself. */
export function computeColorFailed(colorDivergent, labDeltaE) {
  if (colorDivergent !== true) return false;
  const mean = labDeltaE?.mean;
  // No perceptual reading → no corroboration → no veto (the diff still
  // carries the raw metrics for an investigator).
  if (typeof mean !== 'number' || !Number.isFinite(mean)) return false;
  return mean >= WPT_COLOR_FAIL_DELTA_E_MIN;
}

// ── wave-25 CAL-RC6: the ink-mass-asymmetry veto ─────────────────────────────
//
// Maximum tolerated max/min ink-coverage ratio between the two sides.
//
// WHAT IT REPLACES: the semanticPresence block echoes `threshold: 5`
// (SEMANTIC_PRESENCE_EMPTY_PCT) — the absolute "this image is empty" bar — and
// nothing consumed it as a pass/fail input, so any pair whose two sides both
// measured under it was scored on whole-canvas SSIM alone. Under 5 % ink the
// canvas is ≥ 95 % background and SSIM is dominated by background-vs-
// background agreement, which is how a capture missing an entire image scored
// 0.9717. The absolute bar is replaced by a RATIO one, applied at every
// coverage level: it is the asymmetry, not the absolute density, that says the
// two sides disagree about how much got painted.
//
// CALIBRATION on the wave24-final corpus (720 diffs / 587 passes), universal
// (no absolute bypass), with every flipped family opened and eyeballed:
//   ratio > 2   → 39 flips   ← chosen
//   ratio > 2.5 → 38
//   ratio > 3   → 26
//   ratio > 5   →  2
// Every one of the 39 is a verified render defect, sampled across all five
// affected families: css-text/boundary-shaping (24 pairs; the capture breaks
// "office" into three lines where the ref shapes one ffi ligature),
// css-color/animation/contrast-color-interpolation (3; the green square the
// ref demands is simply absent), css-transforms/3d-rendering-context-and-inline
// (1; capture paints a RED square under "Nothing should appear except this
// sentence"), css-backgrounds background-color-animation-with-table1/3/4 (9;
// capture paints extra table cells), css-images/cross-fade-cross-origin-
// orientation (1; the image is missing), css-gaps/flex-gap-decorations-002 (2;
// capture ink 0.47 % vs ref 4.43 %). ZERO true passes flip. The 1.4–2.0 band
// left alone holds pairs (change-insets-inside-strict-containment-nested,
// abspos-in-opacity-001/002) whose divergence is real but partial — they stay
// scored on SSIM, which is the conservative call for a first cut.
const WPT_COVERAGE_RATIO_MAX = 2;

/** wave-25 CAL-RC6 ink-mass-asymmetry veto predicate (pure + exported for the
 *  unit pins). Consumes the same `semanticPresence` block the presence gate
 *  reads ({ aCoveragePct: capture ink %, bCoveragePct: ref ink % }).
 *
 *  Deliberately SYMMETRIC, unlike computePresenceFailed: over-painting is as
 *  wrong as under-painting (the measured red-square-on-a-blank-ref cases are
 *  over-paints), and the whole-canvas SSIM demonstrably does not punish
 *  either when the canvas is mostly background.
 *
 *  Blank-vs-blank is NOT a failure: when the larger side is itself under the
 *  presence floor (WPT_PRESENCE_REF_MIN_PCT), neither image carries enough
 *  ink for a ratio to mean anything — and "render nothing" IS the pass
 *  criterion for several tests (background-color-transparent-animation-in-
 *  body, background-color-animation-with-zero-alpha both measure 0.000 %).
 *  A zero-ink side opposite an inked one is an infinite ratio and fails. */
export function computeCoverageRatioFailed(semanticPresence) {
  // Degenerate/missing metric (size mismatch, pre-v4.3 diff) → cannot judge.
  if (!semanticPresence || typeof semanticPresence !== 'object') return false;
  const cap = semanticPresence.aCoveragePct;   // platform capture ink %
  const ref = semanticPresence.bCoveragePct;   // browser-ref ink %
  // Non-numeric fields (defensive: hand-edited manifests) → unknown → pass.
  if (typeof cap !== 'number' || typeof ref !== 'number') return false;
  const mx = Math.max(cap, ref);
  const mn = Math.min(cap, ref);
  // Both sides essentially blank → no ink to disagree about.
  if (mx < WPT_PRESENCE_REF_MIN_PCT) return false;
  // One side blank, the other inked → the strongest possible asymmetry.
  if (mn === 0) return true;
  return (mx / mn) > WPT_COVERAGE_RATIO_MAX;
}

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
// Exported for unit pins alongside the four families below — wave-36 M6's
// REFUSED_EXCLUSION_TAGS pin has to be able to assert membership in ALL five
// sets, and this was the only one the module kept private.
export { SCORE_EXCLUDED_TAGS };

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
 *  the ref are unreachable for the identical no-script-execution reason.
 *
 *  wave-29 ANCHOR extension — the wall is not only about SCRIPTS. The two
 *  original members share a mechanism (post-load script execution), but the
 *  DEFINING property of this set is narrower and mechanism-free: the
 *  pipeline cannot deliver the input the ref was rendered from, so the
 *  resulting diff measures the harness, not the runtimes.
 *  `requires-anchor-positioning-runtime` (wpt-not-applicable.mjs Rule 40)
 *  meets that bar for a NON-script reason: CSS Anchor Positioning L1 makes
 *  the anchored box's used position a function of ANOTHER element's border
 *  box, resolved at layout time. None of the three runtimes implements it,
 *  and the per-component card surface additionally severs the .anchor /
 *  .anchored DOM relationship the resolution needs — so the fixture that
 *  reaches the runtimes provably lacks the geometry the ref encodes.
 *  Measured (wave28-final css-anchor-position, 12 tests): 8 of the 9 scored
 *  tests failed, web/ios/android-ref SSIM spanning 0.536–0.987 (the bulk of
 *  the anchor-center-overflow family sits at 0.54–0.74). On 7 of those 8 the
 *  three platforms agree to within 0.03 of each other — the signature of a
 *  shared MISSING INPUT, not three independent renderer bugs (three
 *  unrelated renderers do not agree to that precision on a divergence they
 *  each invented). The exception is anchor-center-overflow-005 (web 0.842 vs
 *  ios 0.612 / android 0.610): there the shared missing input is compounded
 *  by a real web-vs-native divergence, so it is NOT evidence for this wall —
 *  it is recorded here so the argument is not read as stronger than the
 *  data. Numbers re-derived from
 *  tools/titan/runs/wave28-final/sections/css-anchor-position/manifest.json.
 *
 *  Re-admission is the SAME post-load stamp, and it is not a formality:
 *  post-load-extract.mjs's 34-property bake snapshots `top/right/bottom/
 *  left` as CSSOM RESOLVED values, which for an out-of-flow box are the
 *  USED insets — i.e. the anchored position AFTER Chromium resolved
 *  position-anchor / position-area / anchor-center, so a baked anchor
 *  fixture is scored again and its divergence measures the runtimes.
 *  That premise holds only SOMETIMES, and the bake does not assume it:
 *  post-load-extract.mjs's anchorInsetMismatch() re-measures resolved-vs-
 *  painted per anchored box and bails (`anchor-inset-undeliverable`) when
 *  Chromium kept the alignment shift out of the serialized inset. MEASURED
 *  end-to-end over this section: 3 of 12 bake (anchor-center-002,
 *  -no-default, -safe), 6 bail anchor-inset-undeliverable, 3 bail on scroll.
 *  Corpus-wide the same run over every bucket-A/B test this tag newly gates
 *  (87 of them, all previously scored): 78 bake and stay scored, 9 become
 *  score-excluded. The three anchor-center-scroll-* tests stay excluded:
 *  they carry requires-script-driven-scroll too, and the bake's
 *  NO-SCROLL-OFFSETS scope boundary bails on them (no scroll model in IR).
 *
 *  This is why the tag joins the set rather than SCORE_EXCLUDED_TAGS:
 *  score-exclusion alone would hide the failures without ever offering a
 *  route back, and the wall set is the only family post-load-extract.mjs
 *  activates on (its hasWallTag() imports THIS constant). */
const EXTRACTION_WALL_TAGS = new Set([
  'requires-script-mutation',            // Rule 4  — post-load DOM / top-layer mutation
  'requires-script-driven-scroll',       // Rule 18 — post-load scroll-offset mutation
  'requires-anchor-positioning-runtime', // Rule 40 — anchored geometry (wave-29)
]);
// Exported for unit pins (inject-wpt-block.test.mjs asserts the exact tag
// set so a silent widening/narrowing of the wall cannot land unreviewed).
export { EXTRACTION_WALL_TAGS };

/** wave-35 lane B2 FONT-FACE DELIVERY tags — the extraction wall's newest
 *  member, and the one with the cleanest delivery record of them all.
 *
 *  THE WALL. `requires-font-face` (wpt-not-applicable.mjs Rule 15) names a
 *  test whose visible assertion depends on a font FILE the author declared:
 *  css-text/boundary-shaping-001…008 all say `@font-face { font-family: test;
 *  src: url(LinLibertine_Re-4.7.5.woff) }` plus `body { font: 36px test }`
 *  precisely because that face carries the "fi"/"ffi" LIGATURES the tests
 *  assert on. When the pipeline delivers no face, all four surfaces shape with
 *  a fallback that HAS no such ligature, so the diff against the ref measures
 *  a missing input — the extraction wall's exact definition (see
 *  EXTRACTION_WALL_TAGS: "the pipeline cannot deliver the input the ref was
 *  rendered from").
 *
 *  WHY IT IS A SEPARATE SET rather than a fourth member of that one: its
 *  delivery record is a DIFFERENT stamp. The wall set re-admits on
 *  postLoadExtracted / structureExtracted, which say nothing about fonts; this
 *  one re-admits on `fontFacesDelivered`, threaded through the identical
 *  keyMap channel by build-combined-fixture.mjs. Merging the sets would make
 *  a post-load bake silently re-admit a font-starved test.
 *
 *  THE STAMP IS NOT A FORMALITY, and it is stricter than "the extractor saw an
 *  @font-face". It is true only when the section's combined document actually
 *  carries every face this test declared, UNSHADOWED — a later test claiming
 *  the same (family, weight, style) slot with a different file takes the slot
 *  (css-fonts-4 §4.1 last-wins, enforced first-wins at the merge) and the
 *  loser is stamped FALSE, because its text would shape from a sibling test's
 *  file. Downstream of that stamp the file reaches all four surfaces: the web
 *  harness mounts it off /wpt-font/ (vite.config.ts), and the two feeders copy
 *  it into the device sandboxes where each runtime's DocumentFontRegistry
 *  registers it (Compose `Font(file:)`, CoreText
 *  `CTFontManagerRegisterFontsForURL`).
 *
 *  MEASURED before this wave (runs/wave34-final, the 8 tests corpus-wide that
 *  carry the tag — all css-text/boundary-shaping): every one scored
 *  web/ios/android-ref 0.9765–0.9811 and FAILED, each on `coverageRatioFailed`
 *  (capture ink 0.115–0.121% against the ref's 0.357–0.372% — the fallback
 *  face paints roughly a third of the ref's ink). Three unrelated renderers
 *  agreeing to within 0.005 of each other while all three sit the same
 *  distance from the ref is the signature of a shared MISSING INPUT, not of
 *  three independent renderer bugs — the same argument that put the anchor
 *  tag in the wall set.
 *
 *  Undelivered ⇒ excluded, delivered ⇒ scored. Neither arm flatters us: the
 *  first stops counting a delivery gap as a renderer failure, and the second
 *  refuses the excuse the moment the face is actually on the page. */
const FONT_FACE_WALL_TAGS = new Set([
  'requires-font-face', // Rule 15 — an author-declared @font-face file (wave-35)
]);
// Exported for unit pins, same discipline as the sets above: the exact
// membership of an exclusion family must never widen unreviewed.
export { FONT_FACE_WALL_TAGS };

/** wave-29 S-RC3 REF-UNACHIEVABLE tags — the honest-scoring boundary's THIRD
 *  WHOLE-TEST exclusion family, and the only one that is not about us.
 *  (wave-30 added a FOURTH family below, NATIVE_FONT_PARITY_TAGS; it is not
 *  listed here because it excludes PER PLATFORM and so never routes through
 *  applyNaScoreGate. This comment used to say "third and last".)
 *
 *  The other two families both say "our pipeline could not deliver the input"
 *  — SCORE_EXCLUDED_TAGS for assets, EXTRACTION_WALL_TAGS for post-load state
 *  — and both therefore have (or could have) a delivery record that re-admits
 *  the test. This family says something categorically different: the
 *  COMMITTED REF IS NOT A REACHABLE TARGET. The ref PNG was rasterised by
 *  headless Chromium from the reftest's `*-ref.html`; when that same Chromium
 *  renders the TEST page it does not reproduce its own ref, so the SSIM
 *  ceiling for ANY Chrome-faithful renderer sits below the 0.95 gate. Scoring
 *  a runtime against it measures a browser bug.
 *
 *  MEASURED (wave-29, the sole member's four tests, headless Chromium 151
 *  under capture-browser-ref.mjs's exact canvas contract, diffed with
 *  diffWebVsRef against refs/<sha>/white-black-ink-font-lh-imgpad/css-pseudo):
 *  active-selection-051/052/053/054 all score 0.9394 — Chromium vs its own
 *  ref. Same-family controls active-selection-056 (1.0000) and -057 (0.9543)
 *  clear the gate and are deliberately NOT tagged (the Rule 42 detector
 *  declines on both). Full mechanism — `color: transparent` plus a
 *  `::selection` block with no valid `color`, where the pass condition is the
 *  OS-default highlight FOREGROUND that Chromium declines to apply — is
 *  documented at wpt-not-applicable.mjs Rule 42.
 *
 *  UNCONDITIONAL, with NO re-admission route, and that asymmetry is the
 *  point:
 *    - lossyReasons cannot corroborate it (the extractor's delivery record
 *      describes OUR inputs; the defect is in the target);
 *    - the post-load / structure stamps cannot re-admit it (running the
 *      test's scripts in Chromium reproduces precisely the render that
 *      diverges — that IS the measurement above);
 *    - so the branch below is checked BEFORE both and ignores every stamp.
 *  If a future WPT re-pin lands a corrected ref, the fix is to re-render the
 *  refs and DELETE this tag, not to weaken the gate. */
const REF_UNACHIEVABLE_TAGS = new Set([
  'browser-ref-divergent', // Rule 42 — OS-default highlight foreground (wave-29)
]);
// Exported for unit pins, same discipline as EXTRACTION_WALL_TAGS: the exact
// membership of an exclusion family must never widen unreviewed.
export { REF_UNACHIEVABLE_TAGS };

/** wave-30 B4(b) NATIVE FONT-PARITY tags — the honest-scoring boundary's
 *  FOURTH family, and the first that is PER-PLATFORM rather than per-test.
 *
 *  The other three families all answer "is this test scorable at all?" with
 *  one boolean for every platform, because the thing they name (an
 *  undeliverable asset, an unexecuted script, a wrong ref) is the same on all
 *  three. This one is not: it names a FONT BOUNDARY between the two native
 *  rasterisers and the Chromium-macOS one that produced the ref.
 *
 *  The pipeline pins one face end to end — capture-browser-ref.mjs's
 *  REF_FONT_STACK plus the embedded Inter Regular/Bold, mirrored by the web
 *  harness, Compose's InterFontFamily and the iOS registered "Inter". Inter
 *  covers Latin, Greek and Cyrillic and nothing else, so a test painting
 *  glyphs outside that coverage sends each surface to a DIFFERENT fallback:
 *  ref and web both land on the same macOS CoreText face (fair comparison
 *  preserved), Compose lands on the emulator's Noto subset and SwiftUI on
 *  Apple's system faces. Different advance widths and glyph shapes for the
 *  SAME correct string ⇒ the native SSIM against the ref is bounded by
 *  typography, not by anything the runtimes compute.
 *
 *  MEASURED (runs/wave29-final/sections/css-counter-styles/manifest.json, all
 *  12 scored tests; full table at wpt-not-applicable.mjs Rule 43):
 *  web clears 0.95 on 11 of 12 (0.9623–0.9986) while the natives run
 *  0.7244–0.9917 and degrade monotonically with GLYPH COUNT — the "10+"
 *  members (arabic-indic 102, armenian 007, bengali 117, cambodian 159)
 *  collapse to 0.72–0.88 while their "1-9" siblings pass. That is a per-glyph
 *  typographic residue accumulating, not a wrong marker string; the web
 *  column is the proof our counter ALGORITHM is right.
 *
 *  SO THE EXCLUSION IS NATIVE-ONLY. web-ref keeps its score and keeps feeding
 *  headline numbers — excluding it would hide the one honest measurement in
 *  the family (including armenian 008's REAL 0.9435 web failure, the §7.1.4
 *  fallback divergence, which is not font-bound and must stay visible).
 *  `scoreEligible` therefore stays TRUE: the test IS still scored, just not
 *  on every platform, and a per-test boolean cannot express that.
 *
 *  NO RE-ADMISSION STAMP, but a real closing move: bundle a Noto subset
 *  covering the css-counter-styles-3 §6 scripts in ALL FOUR pipelines (ref
 *  @font-face payload, web harness /fonts, Compose res/font, iOS registered
 *  faces) and bump CANVAS_REV so every Latin-only-stack ref retires. The four
 *  surfaces then share a face again and this tag is DELETED, not weakened.
 *
 *  wave-48 W2 — THE MATCH SET IS NOW PER-TEST, not per-tag (the queue-#2
 *  unexclusion re-measure). Everything the blanket exclusion waited for is
 *  in: the wave-47 ListMarkerRow baseline-claim split, the default-ON mono
 *  pin, the wave-45 Noto verdict. All 28 tagged cells in the current corpus
 *  sample (25 css-counter-styles + 3 css-lists — no other section feeds a
 *  tagged test, verified over every wave48-cal manifest) were re-fed on both
 *  natives with the current tree (run wave48-w2: private port-5694 emulator
 *  + the seed sim; EVERY cell reproduced its wave48-cal score to 4 decimals,
 *  so the instrument is deterministic) and re-scored IGNORING the tag.
 *  15 of 28 no longer measure a font boundary — 13 clear raw SSIM 0.95 on
 *  BOTH natives; counter-style-at-rule/name-case-sensitivity ssim-clears on
 *  both (0.9535/0.9542) and fails only the coverage-ratio veto exactly as
 *  web itself does (0.9593 F — the missing ink is marker CONTENT, a shared
 *  upstream gap, not a fallback face); cjk-decimal/counter-cjk-decimal
 *  passes iOS at 0.9934 while Android paints a BLANK canvas from the same
 *  IR iOS paints fully (zero ink is a runtime content drop, not typography —
 *  fallback-face divergence re-shapes ink, it never deletes it). Those 15
 *  are UNEXCLUDED: the gate now consults NATIVE_FONT_PARITY_REFUSED_TESTS
 *  below and fires only where the font bound is still the binding
 *  constraint. */
const NATIVE_FONT_PARITY_TAGS = new Set([
  'requires-non-latin-font-parity', // Rule 43 — §6 non-Latin counter styles (wave-30)
]);
// Exported for unit pins, same discipline as the three families above.
export { NATIVE_FONT_PARITY_TAGS };

/** The per-diff platform keys the font-parity family neutralises — exactly
 *  the two NATIVE rasterisers. 'web-ref' is deliberately absent and that
 *  absence is the whole contract, so it is pinned rather than inlined. */
export const NATIVE_FONT_PARITY_PLATFORMS = Object.freeze(['ios-ref', 'android-ref']);

/** The value stamped on `scoreExcluded` for this family.
 *
 *  A STRING, not `true`. Every existing aggregator filters on truthiness
 *  (`if (d.scoreExcluded) continue`), so a string keeps them byte-for-byte
 *  correct — while a reader that wants to know WHY one platform dropped out
 *  of a denominator its siblings stayed in can read the reason straight off
 *  the diff. `true` would make a per-platform font boundary indistinguishable
 *  from a whole-test delivery gap in the manifest. */
export const NATIVE_FONT_PARITY_STAMP = 'native-font-parity';

/** wave-48 W2 — the PER-TEST refusal list that replaces the blanket match.
 *
 *  The TAG machinery is untouched (wpt-not-applicable.mjs Rule 43 still
 *  stamps `requires-non-latin-font-parity` on every §6 non-Latin user); what
 *  shrank is the gate's MATCH SET: applyNativeFontParityGate fires only for
 *  tests named here — the cells the wave-48 re-measure (run wave48-w2, both
 *  natives re-fed on the current tree; every score reproduced its
 *  wave48-cal twin to 4 decimals) showed still bounded under the 0.95 gate
 *  by the font boundary this family names.
 *
 *  DECISION RULE (stated in advance, applied per test): unexclude only where
 *  BOTH natives clear raw SSIM 0.95, or where one does and the other's
 *  residual is a named NON-font mechanism; retain otherwise. A tagged test
 *  NOT listed is scored normally — the wave-30 fix-T4 stance ("over-fire
 *  costs a measurement we can never get back; decline on unknown") applied
 *  at test granularity. Consequence accepted deliberately: a future corpus
 *  expansion (BACKLOG queue #10) admits new tagged tests SCORED, and their
 *  first honest number decides whether they earn a line here — never the
 *  tag alone.
 *
 *  Keys are manifest-relative test paths (INDEX-FREE, the ledger
 *  discipline). Every retained line carries its measured wave48-w2
 *  ios/android raw SSIM — the evidence the retention rests on. */
export const NATIVE_FONT_PARITY_REFUSED_TESTS = Object.freeze(new Set([
  // ios 0.9688 (clears) / android 0.8230 — Arabic-Indic digit face/advance
  // residue accumulating over 25 rows on the emulator's Noto face. The iOS
  // PASS stays excluded with it: the cut is per-TEST, and a pass decided by
  // fallback-glyph count is the same measurement as its failing sibling.
  'css/css-counter-styles/arabic-indic/css3-counter-styles-102.html',
  // ios 0.8513 / android 0.8507 — the wave-31(c) mechanism: one wrap-point
  // advance-width divergence shifts every row below it by a full advance.
  'css/css-counter-styles/armenian/css3-counter-styles-007.html',
  // ios 0.9420 / android 0.9423 — web FAILS the same test at 0.9435: the
  // §7.1.4 armenian-10000 fallback row renders on one line where Chromium
  // wraps it to two. The native residual matches web's within 0.002, i.e.
  // it is no longer font-bound; retained ONLY because neither native clears
  // 0.95 (the rule's conservative side). Re-label candidate — see the
  // wave-48 W2 lane report.
  'css/css-counter-styles/armenian/css3-counter-styles-008.html',
  // ios 0.8907 / android 0.8517 — Bengali digit residue, the wave-30
  // glyph-count monotone ("10+" member).
  'css/css-counter-styles/bengali/css3-counter-styles-117.html',
  // ios 0.8758 (+ colour-mass veto) / android 0.7456 — Khmer numeral
  // residue, worst Android cell of the simple-numeric family.
  'css/css-counter-styles/cambodian/css3-counter-styles-159.html',
  // ios 0.9653 (clears) / android 0.9039 — the PNGs show the same correct
  // two CJK columns with visibly different stroke forms: emulator Noto CJK
  // vs the macOS system face. Font-bound, so clause B declines.
  'css/css-counter-styles/cjk-decimal/css3-counter-styles-001.html',
  // ios 0.7098 / android 0.6715 — cjk-decimal "10+" member, both bounded.
  'css/css-counter-styles/cjk-decimal/css3-counter-styles-004.html',
  // ios 0.8613 / android 0.8583 — §6.3 complex CJK, both bounded.
  'css/css-counter-styles/cjk-earthly-branch/css3-counter-styles-201.html',
  // ios 0.6762 / android 0.6573 — §6.3 complex CJK, both bounded.
  'css/css-counter-styles/cjk-earthly-branch/css3-counter-styles-202.html',
  // ios 0.8831 / android 0.8805 — §6.3 complex CJK, both bounded.
  'css/css-counter-styles/cjk-heavenly-stem/css3-counter-styles-204.html',
  // ios 0.6787 / android 0.6592 — §6.3 complex CJK, both bounded.
  'css/css-counter-styles/cjk-heavenly-stem/css3-counter-styles-205.html',
  // ios 0.8883 / android 0.8901 — web FAILS at 0.8867 with the same visible
  // defect (every item's text painted twice + the korean/RTL marker rows
  // missing): a shared upstream marker/extraction gap, not typography.
  // Retained only because neither native clears 0.95. Re-label candidate.
  'css/css-counter-styles/counter-suffix.html',
  // ios 0.7532 / android 0.7531 — BOTH natives lay the georgian counter
  // string ONE GLYPH PER LINE (a zero-width wrap defect, plainly non-font;
  // web fails separately at 0.7779). Neither clears 0.95 so the rule
  // retains it — but this is a real native layout bug lead, not a font
  // boundary. Re-label candidate.
  'css/css-lists/counter-004.html',
]));

/** wave-36 M6 — THE REFUSED FAMILY. Tags that were formally proposed for an
 *  exclusion family, MEASURED against the corpus, and REJECTED. This constant
 *  exists so the refusal is enforceable: inject-wpt-block.test.mjs asserts
 *  that none of these appears in ANY of the five sets above, so a future wave
 *  cannot quietly add one without deleting this pin and re-doing the
 *  measurement that refused it.
 *
 *  `requires-print-medium` (wpt-not-applicable.mjs Rule 5). The wave-36
 *  mining map's #5 opportunity asked whether paged media is a capability tier
 *  like animation-runtime or scroll-state. It is not, for a reason about the
 *  REFS rather than about pagination: capture-browser-ref.mjs rasterises every
 *  ref in SCREEN medium (it never calls emulateMediaType('print')), so for
 *  most `-print` tests the acceptance target is an ordinary screen render and
 *  pagination never enters the diff.
 *
 *  MEASURED over all 275 scored tests carrying the tag, by the Rule 42/43
 *  ceiling method (render the TEST in the refs' own Chromium under
 *  capture-browser-ref.mjs's canvas contract, diff with diffWebVsRef):
 *  Chromium reaches the committed ref on 193 of 275, INCLUDING 140 of the 141
 *  cells we already pass. Excluding on the tag would therefore silence 193
 *  reachable targets to hide 82 unreachable ones — precision 0.298, and the
 *  wave-8 denominator-gutting failure repeated. Every cheap static narrowing
 *  scored no better (@page 0.383, @page{size} 0.380, forced break 0.268, full
 *  normalised test-vs-ref source delta 0.304).
 *
 *  The tag stays SCORED. Its 53 failures with a ≥0.95 ceiling are ordinary
 *  bugs — wave-36 M6 fixed 8 of them in the extractor (the `<table border=1>`
 *  presentational-attribute mapping). The 82 unreachable ones are closed by
 *  giving the pipeline a print medium on both sides + a CANVAS_REV bump, not
 *  by an exclusion.
 *
 *  wave-37 W5 FOLLOW-UP — the OTHER half of the print question, and the same
 *  answer. Wave-36 refused the EXCLUSION; the map's css-page row (157 tests,
 *  45.2 %, 86 failures) left open whether the harness could instead SIMULATE
 *  the page box — honour `@page { size / margin }` on the composed canvas the
 *  way it honours `?width=`. Measured on the frozen wave35-webmap css-page
 *  manifest and REFUSED, for a reason that follows straight from the sentence
 *  above: the refs never use the page size.
 *    - ALL 157 css-page ref PNGs are 390 px wide — CANVAS_WIDTH, i.e.
 *      REF_RENDER_WIDTH + 2·CANVAS_PAD_PX — while the tests they answer
 *      declare `@page { size: }` of 293px, 300×50px, 400×300px, 500px, a5,
 *      `portrait`, … Ref heights DO vary (600 … 2304) because a screen-medium
 *      render is one continuous flow. `@page size` shapes neither side today.
 *    - The refs hand-encode the EXPECTED PAGED RESULT as ordinary DOM at that
 *      358 px viewport: page-margin-007-print-ref.html writes seven 300 px
 *      `.pagebox` divs for a `size: 400px 200px; margin: 50px` page;
 *      page-background-005-print-ref.html writes three `.pageborder` boxes.
 *      A page-sized canvas would therefore move US away from a target that
 *      was authored at the canvas width — and it would relayout the 12
 *      currently-PASSING css-page cells that declare a non-canvas `@page
 *      size` (basic-pagination-001/002, monolithic-overflow-031,
 *      page-rule-specificity-001/002, page-size-001/002/003/006/011/013/014).
 *      Zero failing cells can converge on a property the ref ignores, so the
 *      proposal's precision is not merely low, it is undefined-over-zero.
 *    - What the 86 failures actually need, classified from source + the
 *      frozen frames (one test may need several): fragmentation 58 (forced
 *      break / named-page switch in the test, or a ref PNG taller than the
 *      600 px floor — i.e. a real paged-layout engine), the CSS Paged Media
 *      §5.3 sixteen-box margin grid 25, page-box painting 19, orthogonal flow
 *      6, print-medium media-query resolution 1, script mutation 1. Twelve
 *      `margin-boxes/*` cells need the margin grid and NOTHING else — the
 *      largest genuinely bounded sub-mechanism in the section, and still a
 *      feature (the §5.3 auto/percentage dimension algorithm), not a canvas
 *      tweak. None of the 86 is an ordinary renderer bug reachable without a
 *      paged model; the eight that no signal claimed were hand-read and are
 *      paged too (`subpixel-page-size-*` page overflow, `page-margin-
 *      negative*` negative page margins, `safe-printable-inset-*`
 *      `page-margin-safety`, `media-queries-003` `<frameset>` + print MQ).
 *
 *  `requires-view-transitions` (wpt-not-applicable.mjs Rule 16). Wave-37 W5's
 *  other question: css-view-transitions is the map's worst big section (195
 *  tests, 192 scored, 26 % pass, 142 failing cells), and the View Transitions
 *  pseudo tree really is unreachable BY CONSTRUCTION — `::view-transition`,
 *  `::view-transition-group/-image-pair/-old/-new` are UA-generated boxes in
 *  the top layer holding SNAPSHOT IMAGES of elements. They are not in the DOM,
 *  so neither the static extractor nor post-load-extract.mjs's computed-style
 *  bake (which walks real elements) can ever serialize them. Pixel evidence,
 *  wave35-webmap captures vs frozen refs: 3d-transform-incoming diverges three
 *  ways at once — the `::view-transition { background: pink }` root layer we
 *  paint white, the new-vs-old snapshot choice (ref blue, ours the pre-script
 *  green), and `::view-transition-image-pair(hidden) { visibility: hidden }`
 *  suppressing a box we still paint.
 *
 *  AND YET IT IS REFUSED, because the wall is not separable from a large
 *  ACCIDENTALLY-DELIVERABLE subset. 50 of the 192 scored cells PASS today; 47
 *  of them carry real ink (ref coverage 1.068 % … 76.496 %) and 21 sit at SSIM
 *  exactly 1.0000 — only 3 are near-blank-vs-blank. They pass because a
 *  transition frozen at `.ready` with `animation-play-state: paused` shows the
 *  OLD snapshot, which IS the pre-script DOM our extractor already serializes
 *  (escaped-name: three green boxes, 12.821 % ink on both sides, SSIM 1.0000).
 *  Excluding on the tag would silence those 50 — precision 142/192 = 0.740,
 *  against 1.000 for Rule 42 and the font-face wall and an effective ~1.0 for
 *  the anchor wall (0.89 raw, but its post-load stamp re-admitted 78 of 87).
 *
 *  NO NARROWING REACHES THE BAR EITHER, and the ceiling is flat because ~10 %
 *  of EVERY sub-population passes: `::view-transition {…paint…}` 0.895 (95
 *  fires), a pseudo styled with `transform` 0.895 (19), `width/height` on a
 *  pseudo 0.885 (26), clip/object-fit on a pseudo 0.857 (28),
 *  `::view-transition-image-pair` 0.775 (80), a normalised test-vs-ref source
 *  delta > 0.6 0.898 (59) — wave-36 M6's own device — plus the whole `scoped/`
 *  and `nested/` subtrees at 0.526 / 0.625. TEN candidates, none ≥ 0.95; the
 *  full table is tools/titan/results/wave37-W5-decisions.json.
 *
 *  RE-MEASURED AT HEAD, and the refusal only gets stronger. Both sections were
 *  re-run web-only at wave-36-final (run-id wave37-W5) against the pre-FX map
 *  they were classified from: css-view-transitions 50 → 54 of 192, css-page
 *  71 → 75 of 157, ZERO cells lost on either. The four view-transition gains
 *  (massive-element-right-and-left-*-onscreen-old/new, no-root-capture,
 *  nothing-captured) push the exclusion's precision DOWN to 138/192 = 0.719 —
 *  i.e. every improvement to the extractor enlarges the deliverable subset the
 *  exclusion would have silenced. The four css-page gains are the whole
 *  page-name-img-001…004 cluster reaching SSIM 1.0000 on wave-36's image
 *  delivery, which also trims the fragmentation bucket below: the 86/58
 *  classification is the map's, and is an UPPER bound on what needs paging.
 *
 *  So the tag stays SCORED and the 138 failures stay visible as what they are:
 *  a capability tier the harness does not have. It is closed by a view-
 *  transition CAPTURE mode (drive the transition in the ref browser and
 *  serialize the settled pseudo tree as ordinary boxes), not by an exclusion —
 *  and a future wave that revisits it must beat 0.719, not re-argue it. Per-
 *  test rows, the ten-narrowing table and both verification runs are in
 *  tools/titan/results/wave37-W5-decisions.json. */
export const REFUSED_EXCLUSION_TAGS = Object.freeze([
  'requires-print-medium',    // Rule 5  — paged media (wave-36 M6, wave-37 W5)
  'requires-view-transitions', // Rule 16 — the UA pseudo tree (wave-37 W5)
]);

/** wave-30 B4(b) per-platform gate (pure — exported for unit tests).
 *
 *  Neutralises the SCORING fields (`wptPass` → null, `scoreExcluded` →
 *  NATIVE_FONT_PARITY_STAMP) on the two native browser-ref diffs of a
 *  font-parity-tagged test, and leaves 'web-ref' completely untouched. Raw
 *  metrics (ssim, pixelMismatchedPct, pHash, …) survive on every platform,
 *  exactly like applyNaScoreGate — an investigator asking "how far off was
 *  the fallback face?" must still be able to read the number.
 *
 *  CALLER CONTRACT: run this only when applyNaScoreGate did NOT already
 *  exclude the whole test. A test carrying both families is wholly excluded,
 *  and overwriting the `true` stamp with this string would silently downgrade
 *  "the harness never delivered the inputs" to "the natives lack a face".
 *
 *  @param {string[]|undefined} naTags  notApplicable tags for the test
 *  @param {Object<string,object|null>} diffsByPlatform browser-ref diffs keyed
 *         by the SAME platform labels the manifest uses ('web-ref' /
 *         'ios-ref' / 'android-ref'); mutated in place. Absent platforms
 *         (null — no captures this run) and error-shaped diffs are skipped:
 *         neither carries scoring fields to neutralise.
 *  @param {string|undefined} testRel  manifest-relative test path (e.g.
 *         'css/css-counter-styles/armenian/css3-counter-styles-007.html').
 *         wave-48 W2: the gate fires only when this names a member of
 *         NATIVE_FONT_PARITY_REFUSED_TESTS — a caller that cannot say WHICH
 *         test never fires it (decline on unknown: an over-fire costs a
 *         measurement we can never get back).
 *  @returns {string[]} the platform keys actually stamped, in
 *         NATIVE_FONT_PARITY_PLATFORMS order (empty ⇒ the gate did not fire,
 *         or fired with no native captures present) */
export function applyNativeFontParityGate(naTags, diffsByPlatform, testRel) {
  // The tag arms the gate; wave-48 W2 made it fire per-TEST, not per-tag.
  const tagged = Array.isArray(naTags) && naTags.some((t) => NATIVE_FONT_PARITY_TAGS.has(t));
  // Only tests the wave-48 re-measure showed still font-bounded are
  // neutralised; every other tagged test (including any future corpus
  // admission) is scored normally and its honest number triaged then.
  const fires = tagged && typeof testRel === 'string'
    && NATIVE_FONT_PARITY_REFUSED_TESTS.has(testRel);
  if (!fires) return [];
  const stamped = [];
  for (const key of NATIVE_FONT_PARITY_PLATFORMS) {
    const d = diffsByPlatform?.[key];
    if (!d || typeof d !== 'object' || d.error) continue;
    d.wptPass = null;                            // never a real pass/fail: the face differs
    d.scoreExcluded = NATIVE_FONT_PARITY_STAMP;  // aggregators filter on truthiness
    stamped.push(key);
  }
  return stamped;
}

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
 *  wave-16 POST-LOAD refinement: the extraction wall is no longer strictly
 *  "no delivery record by construction". The opt-in post-load extraction mode
 *  (tools/titan/post-load-extract.mjs) loads the TEST page in Chromium, lets
 *  its scripts run, and bakes per-element COMPUTED state into the fixture —
 *  when it succeeds it stamps `_wpt.postLoadExtracted: true` (threaded here
 *  as the `postLoadExtracted` parameter via the combined fixture's keyMap,
 *  exactly like the wave-13 lossyReasons channel). That stamp IS the
 *  delivery record the wave-15 comment said couldn't exist: the post-script
 *  state was captured and delivered, so the wall no longer stands between
 *  the ref and the fixture — the test is scoreEligible again and its
 *  divergence measures the RUNTIMES. Absent/false keeps the wave-15
 *  unconditional exclusion byte-for-byte (static extraction never delivers
 *  post-script state). The stamp deliberately does NOT touch the
 *  bundled-asset branch: asset delivery has its own ground truth
 *  (lossyReasons) and post-load says nothing about assets.
 *
 *  wave-29 S-RC3 REF-UNACHIEVABLE extension: tags in REF_UNACHIEVABLE_TAGS
 *  exclude UNCONDITIONALLY and are checked BEFORE both other branches —
 *  neither lossyReasons nor the post-load/structure stamps are consulted,
 *  because the defect is in the committed REF, not in anything this pipeline
 *  delivers (see that constant's comment for the measurement). This is the
 *  one exclusion family with no route back: a corrected ref, not a stamp, is
 *  what retires it.
 *
 *  @param {string[]|undefined} naTags  notApplicable tags for the test
 *  @param {Array<object|null>} refDiffs diffs to neutralise (mutated in place)
 *  @param {string[]|undefined} [lossyReasons] extractor lossy reasons for the
 *         test (keyMap meta.lossyReasons); array ⇒ delivery-aware, absent ⇒
 *         conservative legacy exclusion
 *  @param {boolean} [postLoadExtracted] wave-16: true ⇔ the fixture carries
 *         `_wpt.postLoadExtracted` (post-load mode delivered the post-script
 *         state); neutralises EXTRACTION_WALL_TAGS exclusions only
 *  @param {boolean} [structureExtracted] wave-20: true ⇔ the fixture carries
 *         `_wpt.structureExtracted` (the component TREE itself was
 *         re-extracted from the serialized post-script DOM — the appendChild
 *         family). Treated exactly like postLoadExtracted: either stamp is a
 *         delivery record that re-admits wall-tagged tests. Threaded via the
 *         same keyMap channel.
 *  @param {boolean} [fontFacesDelivered] wave-35: true ⇔ the combined document
 *         carries every `@font-face` this test declared, unshadowed (the
 *         `_wpt.keyMap[…].fontFacesDelivered` stamp build-combined-fixture.mjs
 *         writes). Re-admits FONT_FACE_WALL_TAGS only — it says nothing about
 *         scripts, so it never touches the other branches.
 *  @returns {boolean} true when the test is score-excluded */
export function applyNaScoreGate(naTags, refDiffs, lossyReasons, postLoadExtracted, structureExtracted, fontFacesDelivered) {
  // wave-20: one delivery boolean for the wall branch — the wall falls when
  // EITHER stamp says the post-script state/structure was delivered (strict
  // === true on both, same conservatism as the wave-16 single stamp; in
  // practice structure fixtures carry BOTH stamps, but the gate must not
  // depend on that coupling).
  const delivered = postLoadExtracted === true || structureExtracted === true;
  const isNa = Array.isArray(naTags) && naTags.some((t) => {
    // wave-29 S-RC3 ref-unachievable branch, checked FIRST and returning
    // unconditionally: the committed ref is not a target any Chrome-faithful
    // renderer can hit, so neither the extractor's delivery record nor the
    // post-load/structure stamps are relevant evidence (full rationale at
    // REF_UNACHIEVABLE_TAGS above). Deliberately ABOVE the wall branch so a
    // test carrying both families can never be re-admitted by `delivered`.
    if (REF_UNACHIEVABLE_TAGS.has(t)) return true;
    // wave-15 extraction-wall branch: script-execution tags exclude
    // UNCONDITIONALLY — no lossyReasons cross-check is possible because the
    // extractor never runs scripts and so has no delivery record to consult
    // (full rationale at EXTRACTION_WALL_TAGS above). Checked FIRST so the
    // delivery-aware bundled-asset branch below stays byte-for-byte the
    // wave-13 behaviour for its own tag.
    // wave-16: …unless post-load extraction DELIVERED the post-script state
    // (wave-20: or the post-script STRUCTURE — see `delivered` above). The
    // wall was the inability to deliver that state; delivered ⇒ the score
    // measures the runtimes again.
    if (EXTRACTION_WALL_TAGS.has(t)) return !delivered;
    // wave-35 font-face branch: the same wall shape with its OWN delivery
    // record (full rationale at FONT_FACE_WALL_TAGS). Strict === true, like
    // every stamp here: a caller with no font channel at all (an old combined
    // fixture whose keyMap predates the stamp) gets the conservative arm, so
    // absence of evidence never promotes a test into the scored set.
    if (FONT_FACE_WALL_TAGS.has(t)) return fontFacesDelivered !== true;
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
    // wave-25 CAL-RC6 vetoes: stamped on EVERY diff (true/false, uniform for
    // triage queries) so a manifest row says WHY it failed without re-running
    // the metrics. See computeWptPass for what each one means.
    diff.colorFailed = computeColorFailed(diff.colorDivergent, diff.labDeltaE);
    diff.coverageRatioFailed = computeCoverageRatioFailed(diff.semanticPresence);
    // wave-49: the novel-ink verdict is ALWAYS stamped (honest per-cell record
    // + triage), and only reaches wptPass when the operator opted in.
    diff.novelInkFailed = computeNovelInkFailed(diff.novelInk);
    // WPT-native pass: raw SSIM ≥ 0.95 OR within the declared fuzzy
    // tolerance — VETOED by the semantic-presence gate (corpus-v4.3: a blank
    // capture can no longer "pass" a mostly-blank ref, see computeWptPass)
    // and by the two wave-25 honesty vetoes above.
    // Raw `diff.ssim` is left untouched so downstream can honour all bars.
    diff.wptPass = computeWptPass(diff.ssim, diff.wptFuzzyMatch, diff.presenceFailed,
      diff.colorFailed, diff.coverageRatioFailed, novelInkVetoActive(diff.novelInkFailed));
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
    // wave-25 CAL-RC6 vetoes — same stamps as the stitch path. BOTH measured
    // css-gaps scoring lies (001's red-square colour flip, 002's 9.4× ink
    // deficit) came through THIS composed path.
    diff.colorFailed = computeColorFailed(diff.colorDivergent, diff.labDeltaE);
    diff.coverageRatioFailed = computeCoverageRatioFailed(diff.semanticPresence);
    // wave-49 novel-ink: same always-stamp / opt-in-veto contract as the
    // stitch path above. BOTH measured wave-48 wrong-colour lies (the
    // css-view-transitions red squares) came through THIS composed path.
    diff.novelInkFailed = computeNovelInkFailed(diff.novelInk);
    // WPT-native pass: raw SSIM ≥ 0.95 OR within fuzzy — presence-, colour-
    // and coverage-ratio-vetoed.
    diff.wptPass = computeWptPass(diff.ssim, diff.wptFuzzyMatch, diff.presenceFailed,
      diff.colorFailed, diff.coverageRatioFailed, novelInkVetoActive(diff.novelInkFailed));
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
    // wave-21 collision fix: the stem is the subdir-encoded fixtureStem()
    // (safe-name.mjs), matching what build-combined-fixture keyed the roots
    // as and where capture-browser-ref cached the ref PNG — a bare basename
    // here would miss both for every nested test. Top-level unchanged.
    const refStem = fixtureStem(testRel);
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
    // wave-16: meta.postLoadExtracted (threaded from the per-test fixture's
    // `_wpt.postLoadExtracted` via build-combined-fixture's keyMap, the same
    // channel lossyReasons rides) re-scores wall-tagged tests whose
    // post-script state the post-load extractor delivered.
    // wave-20: meta.structureExtracted rides the same keyMap channel — either
    // delivery stamp re-admits a wall-tagged test (see applyNaScoreGate).
    // wave-35: meta.fontFacesDelivered rides the same keyMap channel as the
    // two stamps above and re-admits the Rule 15 font-face wall (and only it).
    const isNa = applyNaScoreGate(naTags, [webRefDiff, iosRefDiff, androidRefDiff], meta.lossyReasons, meta.postLoadExtracted === true, meta.structureExtracted === true, meta.fontFacesDelivered === true);
    if (isNa) {
      divergence = 'test-not-applicable';
    }
    // wave-30 B4(b) PER-PLATFORM font-parity gate. Runs only when the test was
    // NOT wholly excluded above: a test carrying both families is already
    // neutralised on every platform, and re-stamping the two native diffs
    // would downgrade "the harness never delivered the inputs" to "the natives
    // lack a face" in the manifest (see applyNativeFontParityGate's caller
    // contract). The test-level `divergence` label deliberately does NOT flip
    // — web-ref is still scored, so the label still means something.
    const fontParityExcluded = isNa ? [] : applyNativeFontParityGate(naTags, {
      'web-ref': webRefDiff, 'ios-ref': iosRefDiff, 'android-ref': androidRefDiff,
    }, testRel);  // wave-48 W2: the gate is per-test — see the refusal list.

    results[testRel] = {
      // wave-8: the one boolean scoring paths filter on. false ⇔ the test
      // carries ≥1 notApplicable tag; its metrics are diagnostic-only.
      scoreEligible: !isNa,
      bucket: meta.bucket,
      lossy: !!meta.lossy,
      lossyReasons: meta.lossyReasons ?? [],
      // wave-16: surfaced so dashboards can distinguish "wall-tagged but
      // post-load-delivered (scored)" from "wall-tagged, static-only
      // (excluded)" without re-reading the per-test fixture.
      postLoadExtracted: meta.postLoadExtracted === true,
      // Wave 23 — informational bidi-bake provenance (see build-combined-fixture).
      bidiBaked: meta.bidiBaked === true,
      // Wave 27 — informational counter-style-bake provenance (see
      // build-combined-fixture); true ⇔ ≥1 `<li>` marker was resolved
      // upstream instead of by the runtime's own keyword table.
      counterStyleBaked: meta.counterStyleBaked === true,
      // Wave 38 — informational view-transition-bake provenance (see
      // build-combined-fixture). true ⇔ this row's boxes ARE the settled
      // `::view-transition` pseudo tree, with the page's own components
      // retired, rather than the document the source describes. Purely
      // provenance: `requires-view-transitions` is a REFUSED exclusion tag,
      // so these rows are scored either way — the corresponding lossy reason
      // is view-transition-bake.mjs's VT_BAKE_LOSSY_REASON,
      // 'baked-view-transition-tree', which rides `lossyReasons` above.
      viewTransitionBaked: meta.viewTransitionBaked === true,
      // wave-20: surfaced alongside — true ⇔ the component tree itself was
      // re-extracted from the serialized post-script DOM (appendChild
      // family), not just state-overlaid.
      structureExtracted: meta.structureExtracted === true,
      // Wave 35 — surfaced beside the other delivery stamps so a dashboard can
      // tell "font-face tagged but DELIVERED (scored)" from "font-face tagged,
      // no file (excluded)" without re-reading the combined fixture.
      fontFacesDelivered: meta.fontFacesDelivered === true,
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
      // wave-30 B4(b): the platform keys whose browser-ref diff was
      // score-excluded by the NATIVE font boundary while the others kept
      // scoring. Always present (empty array = the gate did not fire), same
      // no-null-check convention as notApplicableTags — a dashboard reading
      // `scoreEligible: true` next to a two-entry array here is looking at a
      // test scored on web only, which is exactly what happened.
      nativeFontParityExcluded: fontParityExcluded,
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
// ── wave-49 / BACKLOG #5: the column-presence assertion ──────────────────────
//
// WHY: the 327-pair net once went green with the ENTIRE Android column
// silently skipped — every Android capture was missing, so every Android diff
// was simply absent, and "no rows" read as "no failures". The same hole exists
// here: diffPlatformVsRef/diffComposedVsRef return `null` when a platform dir
// has no matching PNG, so a run where one harness never captured produces a
// manifest whose remaining columns look perfectly healthy.
//
// THE ASSERTION: a platform column is PRESENT when at least one scored
// browser-ref diff exists for it. A run in which some columns are present and
// another is entirely empty is a DELIVERY FAILURE, not a result — unless the
// operator declared that platform skipped (SKIP_IOS / SKIP_ANDROID / SKIP_WEB,
// the same switches test-all.sh reads). Deliberately NOT "3 × N or fail": a
// per-test column can legitimately be missing (an extraction miss, a
// not-applicable test), and demanding equal N would fail honest runs. What
// cannot be legitimate is a whole column at zero while its siblings have data.
const PLATFORM_COLUMN_KEYS = Object.freeze({
  'web-ref': 'SKIP_WEB', 'ios-ref': 'SKIP_IOS', 'android-ref': 'SKIP_ANDROID',
});

/** Pure + exported for unit pins. `results` is the buildResults map; `env` is
 *  the environment to read the SKIP_* declarations from. Returns
 *  `{ counts, missing }` — `missing` lists the platform keys that produced
 *  ZERO scored diffs while at least one sibling produced some and the operator
 *  did NOT declare them skipped. An empty `missing` means the run is honest. */
function assertPlatformColumns(results, env = process.env) {
  const counts = {};
  for (const key of Object.keys(PLATFORM_COLUMN_KEYS)) counts[key] = 0;
  for (const r of Object.values(results ?? {})) {
    const diffs = r?.browserRef?.diffs;
    if (!diffs) continue;
    for (const key of Object.keys(PLATFORM_COLUMN_KEYS)) {
      const d = diffs[key];
      // The scorer's own eligibility idiom — a cell counts only when it
      // carries a numeric ssim and was not score-excluded.
      if (d && typeof d.ssim === 'number' && !d.scoreExcluded) counts[key]++;
    }
  }
  const present = Object.values(counts).filter((n) => n > 0).length;
  const missing = present === 0 ? []   // nothing captured at all: a different failure, not this one
    : Object.entries(counts)
      .filter(([key, n]) => n === 0 && env[PLATFORM_COLUMN_KEYS[key]] !== '1')
      .map(([key]) => key);
  return { counts, missing };
}

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

  // BACKLOG #5 column-presence assertion. Runs AFTER the manifest is written
  // so an investigator still gets the artefact to look at, then fails the
  // process so no downstream aggregate can quote a silently-one-platform-short
  // run as a result. See assertPlatformColumns' banner for why this is
  // "column at zero while siblings have data", not "3 × N".
  const columns = assertPlatformColumns(results);
  process.stderr.write(
    `inject-wpt-block: wrote v4 manifest (totalTests=${tests.length} ` +
    `A=${totals.A} B=${totals.B} C=${totals.C}) scored-cells ` +
    Object.entries(columns.counts).map(([k, n]) => `${k}=${n}`).join(' ') + '\n'
  );
  if (columns.missing.length > 0) {
    // WARN by default, FATAL on opt-in. Why not fatal outright: section-runner
    // --web-only legitimately runs inject with empty native dirs and does NOT
    // declare SKIP_IOS/SKIP_ANDROID, and it runs under `set -e` — a bare
    // non-zero exit here would abort every web-only section. The signal is
    // still emitted unconditionally (that alone would have caught the
    // silently-Android-less 327-net); TITAN_REQUIRE_ALL_COLUMNS=1 is what a
    // three-platform gate run sets to make it bite.
    const fatal = process.env.TITAN_REQUIRE_ALL_COLUMNS === '1';
    process.stderr.write(
      `inject-wpt-block: ${fatal ? 'FATAL' : 'WARNING'} — platform column(s) ` +
      `[${columns.missing.join(', ')}] produced ZERO scored browser-ref diffs while other ` +
      'columns did. That is a capture/delivery failure, not a result. Declare the matching ' +
      'SKIP_* env var if the platform was intentionally not run.\n'
    );
    if (fatal) process.exitCode = 3;   // distinct from the usage (1) / IO (2) codes above
  }
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
  // wave-25 CAL-RC6 SCORING-HONESTY boundary: the two new vetoes and their
  // calibrated thresholds, exported so the unit pins hold the wave24-final
  // calibration (and the refutation of the bare-colorDivergent rule) without
  // re-deriving coverage or ΔE.
  WPT_COLOR_FAIL_DELTA_E_MIN, WPT_COVERAGE_RATIO_MAX,
  // wave-49 NOVEL-INK boundary: the opt-in switch's state and the helper that
  // applies it, exported so the unit pins can prove the veto is OFF by default
  // and that computeWptPass's sixth argument is a real veto when it is ON.
  NOVEL_INK_VETO_ENABLED, novelInkVetoActive,
  // wave-49 / BACKLOG #5 column-presence assertion (see its banner).
  assertPlatformColumns, PLATFORM_COLUMN_KEYS,
};

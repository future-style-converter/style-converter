// compare-screenshots-metrics.mjs
//
// Extracted per-pair image-comparison metric helpers from
// compare-screenshots.mjs, kept in a separate module so they can be
// unit-tested in isolation (tools/visual/compare-screenshots.test.mjs).
//
// Each helper implements one of the COMPARE_METRICS.md Section 1 metrics:
//   B2 — computePerChannelSsim   (R/G/B/A SSIM individually)
//   B3 — computeEdgeSsim         (Sobel-3 edge map → SSIM)
//   B4 — computeHistogramKL      (256-bucket KL divergence per channel)
//   B5 — computePHash            (sharp-phash 64-bit + Hamming distance)
//   B6 — computeDssim            (derived: (1 - mssim) / 2)
//   B7 — computeLabDeltaE        (CIEDE2000 mean/max/p95)
//
// Plus the post-rollout "semantic-presence" gate (round 91+): a per-image
// foreground-pixel ratio measured against the canonical capture background
// (#1A1A2E). Used by classify-divergence.mjs to flip pairs to the
// `no-content` label when one or both sides extracted to empty placeholders
// — see TITAN-INVESTIGATOR's pilot-001 finding on
// css-break/block-end-aligned-abspos-with-overflow where SSIM 0.9135 was
// driven entirely by the matching dark backgrounds and the actual semantic
// content (paragraph + green square + hotpink square) was missing on the
// web side. Without this gate the false-positive labels as
// structural-divergence / mixed / sub-pixel-noise depending on the pair —
// none of which surface "the capture pipeline produced nothing comparable".
//
// Each function follows the `safeXxx` contract from compare-screenshots.mjs:
// any internal failure is swallowed and returned as `null`. Callers MUST
// treat null as "metric unavailable" and not gate regression on it.
//
// Inputs are PNG-decoded image objects: `{ data: Uint8Array, width, height }`
// where `data` is RGBA, 4 bytes per pixel.

import { PNG } from 'pngjs';
import { ssim as computeSsim } from 'ssim.js';
import phashDefault from 'sharp-phash';
import { converter, differenceCiede2000 } from 'culori';

// sharp-phash exports the function under `default` in some interop modes.
const phash = phashDefault.default ?? phashDefault;

// ─────────────────────────────────────────────────────────────────────────────
// B6 — DSSIM (derived)
// ─────────────────────────────────────────────────────────────────────────────
//
// Canonical DSSIM = (1 - SSIM) / 2. We derive rather than install the Rust
// `dssim` CLI to keep CI portable (Section 8 decision 1). Skip when the
// SSIM input is null so we don't fabricate a 0.5 "max divergence" reading
// out of a decode error.

export function computeDssim(ssimScore) {
  if (ssimScore === null || ssimScore === undefined) return null;
  return +(((1 - ssimScore) / 2)).toFixed(4);
}

// ─────────────────────────────────────────────────────────────────────────────
// B4 — Color-histogram KL divergence
// ─────────────────────────────────────────────────────────────────────────────
//
//   D_KL(P‖Q) = Σ P(i) log(P(i)/Q(i))
//
// Position-independent: catches "right colors, wrong layout" vs vice
// versa. Laplace-smoothed (Section 1 / B4) so zero-count buckets don't
// blow up `log(0)`. Lower = more similar; 0 = identical distribution.

export function computeHistogramKL(a, b) {
  try {
    if (a.data.length !== b.data.length) return null;
    const klR = klDivergenceForChannel(a.data, b.data, 0);
    const klG = klDivergenceForChannel(a.data, b.data, 1);
    const klB = klDivergenceForChannel(a.data, b.data, 2);
    return { r: +klR.toFixed(4), g: +klG.toFixed(4), b: +klB.toFixed(4) };
  } catch (e) {
    return null;
  }
}

function klDivergenceForChannel(dataA, dataB, channelOffset) {
  // 256-bucket histograms over one RGBA byte stride (offset 0/1/2 = R/G/B).
  const histA = new Uint32Array(256);
  const histB = new Uint32Array(256);
  let total = 0;
  for (let i = channelOffset; i < dataA.length; i += 4) {
    histA[dataA[i]]++;
    histB[dataB[i]]++;
    total++;
  }
  // Laplace smoothing: P(i) = (count(i) + 1) / (total + 256).
  const denom = total + 256;
  let kl = 0;
  for (let i = 0; i < 256; i++) {
    const pi = (histA[i] + 1) / denom;
    const qi = (histB[i] + 1) / denom;
    kl += pi * Math.log(pi / qi);
  }
  return kl;
}

// ─────────────────────────────────────────────────────────────────────────────
// B2 — Per-channel SSIM
// ─────────────────────────────────────────────────────────────────────────────
//
// Run ssim.js four times — once per RGBA channel. Each invocation feeds a
// synthetic ImageData with R=G=B=channel and A=255, which keeps ssim.js's
// internal greyscale conversion a no-op. Cost: ~4× the existing SSIM cost
// (~30ms for a 390×500 pair). Identical → all 1.0; diverged → approaches
// 0 on the affected channel(s); alpha typically stays 1.0 (good signal).
//
// Section 8 decision 5 — informational only; do NOT gate regression.

export async function computePerChannelSsim(a, b) {
  try {
    const r = perChannelSsimOne(a, b, 0);
    const g = perChannelSsimOne(a, b, 1);
    const bScore = perChannelSsimOne(a, b, 2);
    const alpha = perChannelSsimOne(a, b, 3);
    if (r === null || g === null || bScore === null || alpha === null) return null;
    return { r, g, b: bScore, a: alpha };
  } catch (e) {
    return null;
  }
}

function perChannelSsimOne(a, b, channelOffset) {
  // Re-pack a single channel into a fresh RGBA ImageData (R=G=B=value, A=255)
  // so ssim.js sees a normal RGBA buffer of the shape it already handles.
  const len = a.width * a.height * 4;
  const aOut = new Uint8ClampedArray(len);
  const bOut = new Uint8ClampedArray(len);
  for (let i = 0, j = channelOffset; i < len; i += 4, j += 4) {
    const av = a.data[j];
    const bv = b.data[j];
    aOut[i] = aOut[i + 1] = aOut[i + 2] = av; aOut[i + 3] = 255;
    bOut[i] = bOut[i + 1] = bOut[i + 2] = bv; bOut[i + 3] = 255;
  }
  const aImg = { data: aOut, width: a.width, height: a.height };
  const bImg = { data: bOut, width: b.width, height: b.height };
  try {
    const r = computeSsim(aImg, bImg, { ssim: 'fast' });
    return +r.mssim.toFixed(4);
  } catch (e) {
    return null;
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// B5 — Perceptual hash (64-bit DCT) + Hamming distance
// ─────────────────────────────────────────────────────────────────────────────
//
// sharp-phash returns a 64-character "0"/"1" string per image. Hamming
// distance = number of differing bit positions (0..64). Industry threshold
// (Wang et al., 2008): ≤10 ≈ visually identical, ≤20 ≈ similar, >20 ≈
// different. Section 8 decision 6 — default 64-bit (sharp-phash default).
//
// Failure mode: sharp-phash throws on tiny images (<32×32). Catch and
// return { hex: null, hammingDistance: null } — the row still renders.

export async function computePHash(a, b) {
  try {
    // Re-encode the in-memory PNG to a buffer; sharp-phash accepts buffers
    // directly and we don't want to depend on the original file path.
    const aBuf = PNG.sync.write(a);
    const bBuf = PNG.sync.write(b);
    const aHash = await phash(aBuf);
    const bHash = await phash(bBuf);
    let hamming = 0;
    for (let i = 0; i < aHash.length; i++) {
      if (aHash[i] !== bHash[i]) hamming++;
    }
    return { hex: aHash, hammingDistance: hamming };
  } catch (e) {
    return { hex: null, hammingDistance: null };
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// B3 — Edge-map SSIM (Sobel-3, pure-JS gradient magnitude)
// ─────────────────────────────────────────────────────────────────────────────
//
// Section 1 / B3 — extract an edge map from each greyscaled image, then SSIM
// the edge maps. Catches AA-only differences without conflating fill diffs.
// Section 8 decision 4 — Sobel-3 (fast) over Scharr-5 (accurate) still holds:
// the kernels below are the standard 3×3 Sobel pair.
//
// REPAIRED 2026-08-29. The original implementation ran sharp's `convolve`
// with the zero-sum Sobel-X kernel and no `scale`/`offset`; sharp defaults
// `scale` to the kernel sum, so it divided by zero and produced an all-zero
// edge map for EVERY input — two of which are perfectly similar, hence the
// metric read exactly 1.0 on all 327 pairs (docs/STATUS.md 2026-08-28).
// The repair drops sharp entirely and computes Sobel in plain JS, chosen by
// measurement over the "fix sharp's scale/offset" route:
//   · sharp with `scale: 8, offset: 128` (arithmetic says gx/8+128 fits
//     0..255 exactly) measured ALL-255 output even in flat regions where
//     gx = 0 must map to 128 — libvips' convolve semantics do not match the
//     documented sum/scale+offset formula, so any fix built on them rests on
//     behaviour we cannot predict. `scale: 1` clamps the negative lobe at 0
//     (half the edge signal lost) and plain `offset: 128` saturates strong
//     edges. Measured 2026-08-29, probe preserved in the lane report.
//   · pure JS is EXACT (verified: a 0→255 vertical step yields nonzero
//     response only in the two edge-adjacent columns, peak 4·255/8) and
//     measured 0.50 ms per 390×132 capture vs 3.63 ms for the sharp
//     pipeline — the PNG re-encode round-trip alone cost more than the
//     whole JS convolution.
//
// Two semantic upgrades over the pre-repair code, both deliberate:
//   · BOTH Sobel directions, not Sobel-X alone. An X-only map is blind to
//     horizontal edges — i.e. the top/bottom borders of every component box
//     in these captures — which would leave a whole edge class ungated.
//   · L1 gradient magnitude (|gx|+|gy|) scaled by 1/8: the theoretical
//     maximum 8·255 maps exactly onto the 0..255 byte range, so NO value
//     can clip — the saturation trap that sank the sharp routes is
//     structurally impossible here.
//
// Section 1 / B3 explicitly says NOT to fall back to RGB SSIM on failure
// — that would defeat the purpose. Return null instead. (The flat-vs-flat
// unit control enforces this: gradient maps of two flat fields are equal →
// 1.0, where an RGB-SSIM fallback would read ≈0.29.)

// Kept async: every call site already awaits it, and keeping the signature
// stable means the repair changes values, not plumbing.
export async function computeEdgeSsim(a, b) {
  try {
    // Pure-JS edge extraction — synchronous, exact, no encode round-trip.
    const aEdges = sobelEdges(a);
    const bEdges = sobelEdges(b);
    if (!aEdges || !bEdges) return null;
    // Final step per Section 1 / B3: SSIM over the two edge maps, same
    // ssim.js configuration as the base metric so scores are comparable.
    const r = computeSsim(aEdges, bEdges, { ssim: 'fast' });
    return +r.mssim.toFixed(4);
  } catch (e) {
    // safeXxx contract (file header): swallow, return null, never fabricate.
    return null;
  }
}

function sobelEdges(img) {
  const W = img.width, H = img.height;
  // Greyscale via ITU-R BT.601 luma (0.299 R + 0.587 G + 0.114 B) — the
  // classic image-processing weighting; the exact standard is immaterial
  // for edge detection as long as both images get the same one. Alpha is
  // ignored: the capture pipeline emits fully opaque pixels (padToCanvas
  // extends with opaque background), so premultiplication is a non-issue.
  const grey = new Uint8ClampedArray(W * H);
  for (let p = 0, i = 0; p < W * H; p += 1, i += 4) {
    grey[p] = 0.299 * img.data[i] + 0.587 * img.data[i + 1] + 0.114 * img.data[i + 2];
  }
  // Clamped-coordinate lookup = replicate border padding, matching libvips'
  // default extend-copy behaviour the old pipeline used. Replication means
  // the outermost pixel ring produces no artificial "frame" edge — a border
  // artefact would add identical bright rectangles to BOTH maps and inflate
  // their similarity everywhere.
  const at = (x, y) => grey[(y < 0 ? 0 : y >= H ? H - 1 : y) * W + (x < 0 ? 0 : x >= W ? W - 1 : x)];
  // Output straight into RGBA (R=G=B=magnitude, A=255) for ssim.js — its
  // greyscale conversion becomes a no-op, same marshalling rationale as the
  // per-channel helper above.
  const rgba = new Uint8ClampedArray(W * H * 4);
  for (let y = 0; y < H; y += 1) {
    for (let x = 0; x < W; x += 1) {
      // Standard 3×3 Sobel pair. gx: [-1 0 1; -2 0 2; -1 0 1] — right
      // column minus left column, centre row double-weighted. gy is its
      // transpose. Each is a signed response in [-1020, 1020] (4·255).
      const tl = at(x - 1, y - 1), tc = at(x, y - 1), tr = at(x + 1, y - 1);
      const ml = at(x - 1, y),                         mr = at(x + 1, y);
      const bl = at(x - 1, y + 1), bc = at(x, y + 1), br = at(x + 1, y + 1);
      const gx = (tr + 2 * mr + br) - (tl + 2 * ml + bl);
      const gy = (bl + 2 * bc + br) - (tl + 2 * tc + tr);
      // L1 magnitude / 8: |gx|+|gy| ≤ 2040, so the division maps the full
      // response range onto 0..255 with zero clipping (see header). The
      // Uint8ClampedArray write rounds to nearest — deterministic, so the
      // A/A floor stays byte-zero.
      const mag = (Math.abs(gx) + Math.abs(gy)) / 8;
      const j = (y * W + x) * 4;
      rgba[j] = rgba[j + 1] = rgba[j + 2] = mag;
      rgba[j + 3] = 255;
    }
  }
  return { data: rgba, width: W, height: H };
}

// ─────────────────────────────────────────────────────────────────────────────
// B7 — CIE LAB ΔE (CIEDE2000)
// ─────────────────────────────────────────────────────────────────────────────
//
// Perceptual color distance in CIE LAB space.
//
//   ΔE < 1  → imperceptible
//   ΔE < 2  → barely perceptible
//   ΔE > 5  → clearly different
//
// Section 1 / B7 — culori's `converter('lab65')` + `differenceCiede2000`.
// Section 8 decision 2 — stride-sample 1-in-`stride` pixels by default
// (caller passes `stride = 4` for the default fast path, `stride = 1`
// when the `--full-lab` CLI flag is set). Stride-1-in-4 brings cost from
// ~120ms to ~30ms with <1% accuracy loss on screenshots.
//
// Failure mode (Section 1 / B7): exclude alpha-0 pixels — transparent
// background pixels would dominate the mean. Returns null if no valid
// (non-transparent) pixels were sampled.

export function computeLabDeltaE(a, b, stride = 4) {
  try {
    if (a.data.length !== b.data.length) return null;
    const toLab = converter('lab65');
    const dE = differenceCiede2000();
    const deltas = [];
    // SAMPLING PHASE — rotated per row, and that rotation is load-bearing.
    // The old loop strode the LINEAR buffer by 4 pixels, so the sampled
    // columns depended on width mod stride: at the corpus's 390px width
    // (390 = 4·97 + 2) each row's phase shifted by 2, alternating between
    // x ≡ 0 and x ≡ 2 (mod 4) — ODD columns were never examined on any
    // row of any 390-wide capture. A 1px vertical hairline at an odd x
    // was deterministically invisible to the gating ΔE95, on every run,
    // forever (the zero noise floor made the blindness perfectly stable).
    // Rotating the phase by row (row % stride) covers every residue class
    // within each stride-sized row band while keeping the sample count,
    // and therefore the ~30ms/pair cost, identical.
    const width = a.width;
    if (!width) {
      // No geometry (defensive: every real caller passes padToCanvas
      // output, which carries width) — fall back to the linear walk
      // rather than guessing one.
      const pixelStride = 4 * stride;
      for (let i = 0; i < a.data.length; i += pixelStride) {
        if (a.data[i + 3] === 0 || b.data[i + 3] === 0) continue;
        const ca = toLab({ mode: 'rgb', r: a.data[i] / 255, g: a.data[i + 1] / 255, b: a.data[i + 2] / 255 });
        const cb = toLab({ mode: 'rgb', r: b.data[i] / 255, g: b.data[i + 1] / 255, b: b.data[i + 2] / 255 });
        deltas.push(dE(ca, cb));
      }
      return summarizeDeltas(deltas);
    }
    const height = Math.floor(a.data.length / 4 / width);
    for (let y = 0; y < height; y++) {
      const phase = y % stride;
      for (let x = phase; x < width; x += stride) {
        const i = (y * width + x) * 4;
      // Skip pixels where EITHER side is fully transparent — the colour
      // beneath is undefined and would skew the distribution.
        if (a.data[i + 3] === 0 || b.data[i + 3] === 0) continue;
        const ca = toLab({ mode: 'rgb', r: a.data[i] / 255, g: a.data[i + 1] / 255, b: a.data[i + 2] / 255 });
        const cb = toLab({ mode: 'rgb', r: b.data[i] / 255, g: b.data[i + 1] / 255, b: b.data[i + 2] / 255 });
        deltas.push(dE(ca, cb));
      }
    }
    return summarizeDeltas(deltas);
  } catch (e) {
    return null;
  }
}

/** Shared summary for both sampling paths (phased and linear-fallback). */
function summarizeDeltas(deltas) {
  if (deltas.length === 0) return null;
  // Sort once for both max + p95. p95 picks the 95th percentile via
  // index = floor(0.95 × (n-1)) — robust to single-pixel outliers.
  deltas.sort((x, y) => x - y);
  const sum = deltas.reduce((s, v) => s + v, 0);
  const mean = sum / deltas.length;
  const max = deltas[deltas.length - 1];
  const p95 = deltas[Math.floor(0.95 * (deltas.length - 1))];
  return {
    mean: +mean.toFixed(3),
    max: +max.toFixed(3),
    p95: +p95.toFixed(3),
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// Semantic-presence gate (round 91 — fixes pilot-001 false positive)
// ─────────────────────────────────────────────────────────────────────────────
//
// Per-image foreground coverage against the canonical capture background
// (#1A1A2E = rgb(26, 26, 46)). For each image we count the pixels whose
// (R,G,B) deviates from the canonical background by more than `tolerance`
// per-channel — this is "ink on the page". If both sides have <5% coverage,
// the comparison is "no semantic content": both screenshots are essentially
// blank canvases and any high-SSIM signal is an artefact of the matching
// dark background, not a meaningful similarity between rendered components.
//
// Tolerance rationale:
//   - tolerance=8 per channel: catches PNG quantisation noise + sub-pixel
//     AA bleed from a single pixel of foreground without flagging the
//     background itself. The capture pipeline writes background pixels at
//     exactly (26,26,46) via sharp's `extend` (compare-screenshots.mjs
//     line 440), so the only deviations come from compositor rounding —
//     well under 8/255.
//   - 5% threshold: a 390×500 capture has 195 000 pixels; 5% = 9750 pixels
//     ≈ a single 100×100 component covers ~5%. Anything below that is
//     "no rendered content" not "tiny rendered content".
//
// Output shape: `{ aCoveragePct, bCoveragePct, threshold, tolerance }`.
// `null` only on shape mismatch (defensive — diffPair already pads to a
// shared canvas, so this should never fire in the live pipeline).
//
// Performance: one pass over each buffer; ~5ms for a 390×500 PNG. Cheaper
// than every other metric in this file because no convolution / FFT.

// Canonical capture background: #1A1A2E (matches sharp `extend` background
// in padToCanvas, compare-screenshots.mjs line 440). Single source of truth.
export const CANONICAL_BG = { r: 0x1A, g: 0x1A, b: 0x2E };
// Per-channel tolerance for "is this pixel background?" — see header comment.
export const SEMANTIC_PRESENCE_TOLERANCE = 8;
// Coverage% under which an image is declared "empty" by the classifier
// (consumed in classify-divergence.mjs). Exposed as a const so the
// classifier and the metric helper share one threshold value.
export const SEMANTIC_PRESENCE_EMPTY_PCT = 5;

// The optional third parameter `bg` (wave-13 SCORING gate) lets a caller
// measure "ink on the page" against a DIFFERENT canonical background than the
// 327-pair pipeline's dark #1A1A2E — the TITAN WPT browser-ref diffs run on
// the corpus-v4 WHITE canvas (inject-wpt-block.mjs padToCanvas +
// capture-browser-ref.mjs CANVAS_BG are all white), so their presence guard
// must count deviation from WHITE. Defaulting to CANONICAL_BG keeps every
// existing 327-pair call site byte-identical.
export function computeSemanticPresence(a, b, bg = CANONICAL_BG) {
  try {
    if (a.data.length !== b.data.length) return null;
    const aCoverage = foregroundCoverage(a.data, SEMANTIC_PRESENCE_TOLERANCE, bg);
    const bCoverage = foregroundCoverage(b.data, SEMANTIC_PRESENCE_TOLERANCE, bg);
    return {
      aCoveragePct: +aCoverage.toFixed(3),
      bCoveragePct: +bCoverage.toFixed(3),
      // Echo the thresholds used so a future reviewer of the manifest can
      // tell why a pair was (or wasn't) flagged without re-grepping the
      // code. Cheap (two scalars) vs the value of self-describing data.
      threshold: SEMANTIC_PRESENCE_EMPTY_PCT,
      tolerance: SEMANTIC_PRESENCE_TOLERANCE,
    };
  } catch (e) {
    return null;
  }
}

// Count the fraction of pixels whose RGB deviates from the given canonical
// background (`bg`, default the 327-pair dark #1A1A2E) by more than
// `tolerance` per-channel. Single-pass over the RGBA buffer. Alpha is
// ignored — the capture pipeline writes opaque pixels everywhere (extend
// fills with alpha 1, see padToCanvas), so transparent pixels are a non-
// concern; ignoring alpha keeps this fast and keeps the threshold trivial
// to reason about ("pixels visibly different from the backdrop").
function foregroundCoverage(data, tolerance, bg = CANONICAL_BG) {
  let foreground = 0;
  let total = 0;
  // Stride 4 = RGBA; we only read R/G/B.
  for (let i = 0; i < data.length; i += 4) {
    // PAD SENTINEL EXCLUSION — from numerator AND denominator, so the
    // metric reads "ink density of the actually-captured region".
    //
    // Two verified requirements collide on this pixel class and this is
    // the only accounting that satisfies both:
    //   · An under-sized BLANK capture must still classify no-content
    //     (counting the sentinel as ink made a broken half-height blank
    //     read as 50% covered, voiding no-content for exactly the
    //     captures most likely to be broken — the pipeline hunt's
    //     finding).
    //   · An under-sized INKED capture must not read as coverage-alike
    //     with its full-height pair (Lane E's integration control: the
    //     10×5 ink half must read 100% against the full capture's 50%).
    // Skipping the sentinel entirely gives blank-short 0/real = 0% (first
    // requirement) and ink-short real-ink/real = 100% vs 50% (second).
    // The under-size itself stays loudly visible to ΔE and pixelmatch —
    // that is the sentinel's actual job; coverage's job is the
    // no-content gate.
    if (data[i] === 0xFF && data[i + 1] === 0x00 && data[i + 2] === 0xFF) continue;
    total++;
    const dr = Math.abs(data[i]     - bg.r);
    const dg = Math.abs(data[i + 1] - bg.g);
    const db = Math.abs(data[i + 2] - bg.b);
    // Logical OR, not max() — short-circuits faster on the common case
    // (pure-background pixel where all three deltas are 0).
    if (dr > tolerance || dg > tolerance || db > tolerance) foreground++;
  }
  if (total === 0) return 0;
  return (foreground / total) * 100;
}

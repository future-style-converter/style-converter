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
import sharp from 'sharp';
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
// B3 — Edge-map SSIM (Sobel-3)
// ─────────────────────────────────────────────────────────────────────────────
//
// Section 1 / B3 — apply 3×3 Sobel-X to greyscaled images, then SSIM the
// edge maps. Catches AA-only differences without conflating fill diffs.
// Section 8 decision 4 — Sobel-3 (fast) over Scharr-5 (accurate). The
// kernel is exactly the one specified in the spec:
//
//     -1  0  1
//     -2  0  2
//     -1  0  1
//
// Section 1 / B3 explicitly says NOT to fall back to RGB SSIM on failure
// — that would defeat the purpose. Return null instead.

export async function computeEdgeSsim(a, b) {
  try {
    const aEdges = await sobelEdges(a);
    const bEdges = await sobelEdges(b);
    if (!aEdges || !bEdges) return null;
    const r = computeSsim(aEdges, bEdges, { ssim: 'fast' });
    return +r.mssim.toFixed(4);
  } catch (e) {
    return null;
  }
}

async function sobelEdges(img) {
  // sharp ingests an encoded PNG; we re-serialize the in-memory image.
  // greyscale → Sobel-3 convolve → raw single-channel buffer.
  const buf = PNG.sync.write(img);
  const { data: greyEdges, info } = await sharp(buf)
    .greyscale()
    .convolve({ width: 3, height: 3, kernel: [-1, 0, 1, -2, 0, 2, -1, 0, 1] })
    .raw()
    .toBuffer({ resolveWithObject: true });
  // Marshal the single-channel buffer into RGBA (R=G=B=value, A=255) for
  // ssim.js. Single-channel input would force ssim.js into a code path
  // that's less battle-tested across versions.
  const W = info.width, H = info.height;
  const rgba = new Uint8ClampedArray(W * H * 4);
  for (let i = 0, j = 0; i < greyEdges.length; i++, j += 4) {
    const v = greyEdges[i];
    rgba[j] = rgba[j + 1] = rgba[j + 2] = v;
    rgba[j + 3] = 255;
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
    // Step 4 bytes per pixel × `stride` pixels at a time.
    const pixelStride = 4 * stride;
    for (let i = 0; i < a.data.length; i += pixelStride) {
      // Skip pixels where EITHER side is fully transparent — the colour
      // beneath is undefined and would skew the distribution.
      if (a.data[i + 3] === 0 || b.data[i + 3] === 0) continue;
      const ca = toLab({ mode: 'rgb', r: a.data[i] / 255, g: a.data[i + 1] / 255, b: a.data[i + 2] / 255 });
      const cb = toLab({ mode: 'rgb', r: b.data[i] / 255, g: b.data[i + 1] / 255, b: b.data[i + 2] / 255 });
      deltas.push(dE(ca, cb));
    }
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
  } catch (e) {
    return null;
  }
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

export function computeSemanticPresence(a, b) {
  try {
    if (a.data.length !== b.data.length) return null;
    const aCoverage = foregroundCoverage(a.data, SEMANTIC_PRESENCE_TOLERANCE);
    const bCoverage = foregroundCoverage(b.data, SEMANTIC_PRESENCE_TOLERANCE);
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

// Count the fraction of pixels whose RGB deviates from CANONICAL_BG by more
// than `tolerance` per-channel. Single-pass over the RGBA buffer. Alpha is
// ignored — the capture pipeline writes opaque pixels everywhere (extend
// fills with alpha 1, see padToCanvas), so transparent pixels are a non-
// concern; ignoring alpha keeps this fast and keeps the threshold trivial
// to reason about ("pixels visibly different from the dark backdrop").
function foregroundCoverage(data, tolerance) {
  let foreground = 0;
  let total = 0;
  // Stride 4 = RGBA; we only read R/G/B.
  for (let i = 0; i < data.length; i += 4) {
    total++;
    const dr = Math.abs(data[i]     - CANONICAL_BG.r);
    const dg = Math.abs(data[i + 1] - CANONICAL_BG.g);
    const db = Math.abs(data[i + 2] - CANONICAL_BG.b);
    // Logical OR, not max() — short-circuits faster on the common case
    // (pure-background pixel where all three deltas are 0).
    if (dr > tolerance || dg > tolerance || db > tolerance) foreground++;
  }
  if (total === 0) return 0;
  return (foreground / total) * 100;
}

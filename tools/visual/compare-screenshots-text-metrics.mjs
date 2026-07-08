// compare-screenshots-text-metrics.mjs
//
// B-EXT (docs/reports/COMPARE_METRICS_B8-B10.md) typography divergence probes.
// Three per-platform metrics:
//
//   B8  — sub-pixel baseline positioning   (computeBaselineY)
//   B9  — anti-aliasing strategy           (classifyAaStrategy)
//   B10 — inter-glyph spacing / kerning    (computeGlyphSpacing)
//
// Each helper follows the same `safeXxx` contract as
// compare-screenshots-metrics.mjs (Section 5 of B-EXT spec): any internal
// failure is swallowed and returned as `null`. Callers treat null as
// "metric unavailable" and never gate CI on it — these are decoration-only.
//
// All inputs are PNG-decoded image objects: `{ data, width, height }`
// where `data` is RGBA, 4 bytes per pixel. The probe pipeline captures
// at 4× CSS resolution (Section 1.1); helpers downscale internally for
// the px-in-CSS-coords conversion the manifest reports.

import { PNG } from 'pngjs';
import { readFileSync } from 'node:fs';
import FFTLib from 'fft.js';

// fft.js exports the constructor as default (CommonJS interop dance).
const FFT = FFTLib.default ?? FFTLib;

// ── B8 — sub-pixel baseline positioning ─────────────────────────────────────
//
// The strategy: in a 4× white-bg / black-text probe (B8 fixtures use
// `color:#000000` on `background:#ffffff`), find the bottom-most row that
// contains a "dark enough" run of pixels. That row IS the baseline (B8
// fixtures are intentionally all-caps, no descenders, so the bottom of
// the visible bbox === baseline). Returning the row in 1×-CSS-pixel
// coords (= row4x / 4) yields a value the manifest's `subpixelBaseline`
// field can compare across platforms.
//
// Edge cases:
//   • Zero text → no dark pixels → null. The manifest schema explicitly
//     allows null per-platform readings (Section 3).
//   • Anti-aliased text fades to grey at the edge. We scan with a
//     threshold of 128 (mid-grey) so a single AA pixel doesn't shift
//     the baseline; we require ≥ MIN_DARK_RUN consecutive dark cells in
//     the row to count as "real glyph mass".

const B8_DARK_THRESHOLD = 128;
const B8_MIN_DARK_RUN = 4; // px @4× — at least 1 px @1×

export function computeBaselineY(img) {
  try {
    if (!img || !img.data || !img.width || !img.height) return null;
    // Scan rows bottom-up. The lowest row with sufficient glyph mass is
    // the baseline (B8 fixtures have no descenders by design).
    for (let y = img.height - 1; y >= 0; y--) {
      let run = 0;
      let maxRun = 0;
      for (let x = 0; x < img.width; x++) {
        // Greyscale via luminance (Rec.601). Probe fixtures are
        // intentionally pure black-on-white so any reasonable luma
        // weighting reaches the same threshold decision.
        const i = (y * img.width + x) * 4;
        const lum = 0.299 * img.data[i] + 0.587 * img.data[i + 1] + 0.114 * img.data[i + 2];
        if (lum < B8_DARK_THRESHOLD) {
          run++;
          if (run > maxRun) maxRun = run;
        } else {
          run = 0;
        }
      }
      if (maxRun >= B8_MIN_DARK_RUN) {
        // Convert from 4× capture coords back to 1× CSS-px so the manifest
        // value is comparable to the spec's threshold table (Section 4 — B8).
        // Round to 2 dp; the underlying capture quantises at 0.25 px @1×.
        return +(y / 4).toFixed(2);
      }
    }
    // No glyph mass found at all — most likely the fixture failed to render.
    // Returning null (not 0) so the manifest doesn't claim "baseline at row 0".
    return null;
  } catch (e) {
    return null;
  }
}

// ── B9 — anti-aliasing strategy classifier ──────────────────────────────────
//
// 1-D FFT along the line normal in a B9 diagonal-line fixture distinguishes
// three AA regimes by how much energy lives in the high-frequency band:
//
//   • subpixel  — ClearType-style; chroma channel (R-B) carries
//     high-frequency energy because each subpixel triplet is exploited
//     independently. ratio = energyHi(C)/energyHi(L) > 0.3.
//   • greyscale — single-pass coverage AA; luminance has SOFTENED edges,
//     so the high-freq fraction lFrac = energyHi(L)/energyTotal(L) is
//     LOWER than a sharp bilevel line. Chroma stays near zero.
//   • none      — bilevel rasterisation; sharp 1-px transitions in luma
//     pump the high-freq band, so lFrac is HIGH (not low — see note).
//
// Anything that fits none of these → 'unknown' (no silent fallthrough).
//
// EMPIRICAL REFINEMENT (per spec Section 4 — "Implementer should refine
// after first probe pass"): the original draft (RESEARCH.md:152-156)
// defined greyscale as `lFrac > 0.4` and none as `lFrac < 0.1`. That's
// inverted vs. observed behaviour of our 1-px diagonal-line probe
// fixtures: a sharp bilevel line has more relative high-freq energy than
// a luminance-smoothed one, because smoothing redistributes energy
// toward DC. Validated against fixtures b9_{none,greyscale,subpixel}_45deg.png:
//   subpixel: ratio=2.81, lFrac=0.19  → 'subpixel'
//   greyscale: ratio=0.00, lFrac=0.19 → 'greyscale'  (low lFrac = smoothed)
//   none:     ratio=0.00, lFrac=0.51  → 'none'       (high lFrac = sharp bilevel)
// The boundary between greyscale and none was placed at lFrac=0.30, the
// midpoint of the two observed values, so each class has equal margin.
// Re-tune once iOS/Android probe captures land if the cluster shifts.

// Exported so the unit test + future tuning can read them without
// duplicating the literals. Each value justified inline above.
export const B9_SUBPIXEL_RATIO_MIN = 0.30;
export const B9_GREY_RATIO_MAX = 0.05;
// Boundary between greyscale (smoothed → lower lFrac) and none (bilevel
// → higher lFrac). See empirical-refinement note above.
export const B9_NONE_HI_FRACTION_MIN = 0.30;

// FFT input must be a power-of-two length. The B9 fixtures are 128 wide
// → after 4× capture they're 512 px. We sample a single 128-px slice
// across the line normal so the FFT input stays compact (~1 ms).
const B9_FFT_SIZE = 128;

export function classifyAaStrategy(img) {
  try {
    if (!img || !img.data || !img.width || !img.height) return 'unknown';
    if (img.height < B9_FFT_SIZE) return 'unknown';
    // Sample a vertical column through the centre of the image. For a
    // diagonal line the perpendicular-to-line normal is angle-dependent;
    // a vertical column samples ALL angles' edge gradient at the line
    // crossing. The line's exact crossing point shifts with angle, but
    // we centre on the image midpoint where every B9 fixture's line
    // passes through.
    const cx = Math.floor(img.width / 2);
    const startY = Math.floor((img.height - B9_FFT_SIZE) / 2);
    const lum = new Float64Array(B9_FFT_SIZE);
    const chroma = new Float64Array(B9_FFT_SIZE);
    for (let k = 0; k < B9_FFT_SIZE; k++) {
      const i = ((startY + k) * img.width + cx) * 4;
      const r = img.data[i], g = img.data[i + 1], b = img.data[i + 2];
      // Luminance (Rec.601) and chroma (R - B) per spec Section 1.2.
      // R-B chosen over Lab a*/b* because it's a single subtraction —
      // discriminator is identical at a fraction of the cost (Section 8 q6).
      lum[k] = 0.299 * r + 0.587 * g + 0.114 * b;
      chroma[k] = r - b;
    }
    const energyL = fftEnergyBands(lum);
    const energyC = fftEnergyBands(chroma);
    if (!energyL || !energyC) return 'unknown';
    // Guard div-by-zero on a fixture that's all one colour (no edge at all).
    if (energyL.hi <= 1e-9) return 'none';
    const ratio = energyC.hi / energyL.hi;
    const lFraction = energyL.hi / (energyL.total + 1e-9);
    if (ratio > B9_SUBPIXEL_RATIO_MIN) return 'subpixel';
    // ratio in greyscale range → use lFraction to split smoothed (greyscale)
    // from bilevel (none); see empirical-refinement note above.
    if (ratio < B9_GREY_RATIO_MAX) {
      return lFraction >= B9_NONE_HI_FRACTION_MIN ? 'none' : 'greyscale';
    }
    return 'unknown';
  } catch (e) {
    return 'unknown';
  }
}

function fftEnergyBands(samples) {
  // 1-D real FFT magnitude spectrum, split into "hi" (top quartile of
  // bins) and "total". Power-of-two length is required by fft.js;
  // samples.length is exactly B9_FFT_SIZE so this always holds.
  const N = samples.length;
  if ((N & (N - 1)) !== 0) return null;
  const fft = new FFT(N);
  // fft.js packs complex output as interleaved [re, im, re, im, ...] of
  // length 2N. The realTransform helper writes the same packing for a
  // real-valued input array.
  const out = fft.createComplexArray();
  fft.realTransform(out, samples);
  fft.completeSpectrum(out);
  let total = 0;
  let hi = 0;
  // Half the bins are unique (the rest are conjugates). The top-quartile
  // band picks bins in [N/2, N), but we only iterate the unique half
  // [0, N/2) and re-classify "hi" as the upper half of THAT range
  // (i.e. bins ≥ N/4) — that's our high-freq band per spec.
  const halfN = N / 2;
  const hiCutoff = N / 4;
  for (let k = 1; k < halfN; k++) {
    const re = out[2 * k];
    const im = out[2 * k + 1];
    const mag = Math.hypot(re, im);
    total += mag;
    if (k >= hiCutoff) hi += mag;
  }
  return { hi, total };
}

// ── B10 — inter-glyph spacing / kerning ─────────────────────────────────────
//
// Project the dark-pixel mass of the text band onto the X axis (sum each
// column's "darkness"). The result is a 1-D signal whose local maxima
// = glyph-centre x positions; consecutive-maxima distances = inter-glyph
// gaps. Mean is "average gap"; stddev is "gap variance" (= hinting drift,
// per spec Section 4 / B10 — different glyphs hinted differently leads
// to per-pair variance even when the mean stays put).
//
// Heuristic: peak detection uses a simple slope-change finder with a
// minimum prominence of MIN_PROMINENCE — values below the band's median
// don't count as glyph centres (catches a stray AA halo between letters).
// fontSizePx is read from the fixture name suffix (e.g. "_24") so meanEm
// can be computed (mean / fontSizePx). null fontSizePx → meanEm omitted.

// Threshold for "this column has enough dark mass to be inside a glyph".
// Tuned for our pure-black-on-white probe fixtures — every ink column has
// luma ~0; every gap column has luma 255. Mid-grey rejects AA halos.
const B10_DARK_THRESHOLD = 128;
// Minimum number of columns between two detected peaks before we accept
// the second one as a separate glyph. Set to ~40% of font-size in 4×
// capture coords; below that two peaks are inside one glyph (e.g. the
// two stems of an "n").
const B10_MIN_PEAK_SPACING_PCT = 0.4;

export function computeGlyphSpacing(img, fontSizePx) {
  try {
    if (!img || !img.data || !img.width || !img.height) return null;
    // Build the column-darkness projection across the full image height.
    // Background pixels contribute 0 (luma above threshold); ink pixels
    // contribute the inverse luma so the projection peaks at glyph centres.
    const proj = new Float64Array(img.width);
    for (let y = 0; y < img.height; y++) {
      for (let x = 0; x < img.width; x++) {
        const i = (y * img.width + x) * 4;
        const lum = 0.299 * img.data[i] + 0.587 * img.data[i + 1] + 0.114 * img.data[i + 2];
        if (lum < B10_DARK_THRESHOLD) {
          // Inverse-luma: pure black (lum=0) → 1.0, mid-grey → ~0.5,
          // anything above threshold → 0. Keeps AA half-pixels weighted
          // proportionally to their ink content.
          proj[x] += (B10_DARK_THRESHOLD - lum) / B10_DARK_THRESHOLD;
        }
      }
    }
    // Minimum peak spacing in 4× px. Default to 24 px @4× = 6 px @1×
    // when fontSize is unknown; otherwise 0.4 × fontSize × 4×.
    const minSpacing = Math.max(2, Math.round(((fontSizePx ?? 16) * 4) * B10_MIN_PEAK_SPACING_PCT));
    const peaks = findPeaks(proj, minSpacing);
    if (peaks.length < 2) {
      // Need at least two peaks to compute a single inter-glyph gap.
      // Returning null (not zero) so the manifest doesn't fabricate
      // "0 px spacing" out of an empty render.
      return null;
    }
    // Inter-peak distances in 4× px → divide by 4 for 1× CSS-px output.
    const gaps = [];
    for (let i = 1; i < peaks.length; i++) gaps.push((peaks[i] - peaks[i - 1]) / 4);
    const sum = gaps.reduce((s, g) => s + g, 0);
    const mean = sum / gaps.length;
    // Population stddev (not sample) — we have the entire gap set, not a
    // sample of it, so dividing by N (not N-1) is correct.
    const variance = gaps.reduce((s, g) => s + (g - mean) ** 2, 0) / gaps.length;
    const stddev = Math.sqrt(variance);
    const out = {
      mean: +mean.toFixed(3),
      stddev: +stddev.toFixed(3),
      count: gaps.length,
    };
    if (fontSizePx && fontSizePx > 0) {
      // meanEm — Section 8 q4: report both raw px and font-size-normalised.
      out.meanEm = +(mean / fontSizePx).toFixed(4);
    }
    return out;
  } catch (e) {
    return null;
  }
}

function findPeaks(signal, minSpacing) {
  // Plateau-aware local-maximum detector with a "prominence above the
  // active background" gate. Returns indices of glyph-centre x positions
  // sorted ascending. Used by B10 glyph-centre detection.
  //
  // Why plateau-aware: real glyphs and our synthetic black-block fixtures
  // both produce flat-top runs in the column-darkness projection (every
  // column inside a glyph saturates equally to the row-count of dark
  // pixels). A strict slope-change finder would miss every peak because
  // signal[i] === signal[i-1] inside the plateau. Solution: detect
  // contiguous runs of "elevated" cells (above the prominence floor) and
  // emit the centre index of each run as the peak.
  const N = signal.length;
  if (N < 3) return [];
  // Prominence floor: the median of NON-ZERO cells. Pure background
  // (lots of zeros) shouldn't drag the floor down to zero — we want
  // "what does an in-glyph cell look like?". Falls back to 0.5 when
  // every cell is zero (empty image; the empty-image test asserts this
  // path returns [] which then yields null one frame up).
  let nonZeroSum = 0, nonZeroCount = 0, maxV = 0;
  for (let i = 0; i < N; i++) {
    if (signal[i] > 0) { nonZeroSum += signal[i]; nonZeroCount++; }
    if (signal[i] > maxV) maxV = signal[i];
  }
  if (nonZeroCount === 0 || maxV === 0) return [];
  const meanNonZero = nonZeroSum / nonZeroCount;
  // Half-mean-of-non-zero: keeps "real glyph mass" but rejects single
  // AA halo pixels and faint stray ink. Tunable; kept conservative so
  // sparse text (e.g. monospaced "i") still registers.
  const prominence = Math.max(0.5, meanNonZero * 0.5);
  // ── Pass 1: identify contiguous "elevated" runs and emit each run's
  // centre column as a candidate peak.
  const peaks = [];
  let runStart = -1;
  for (let i = 0; i < N; i++) {
    if (signal[i] > prominence) {
      if (runStart < 0) runStart = i;
    } else if (runStart >= 0) {
      // End of a run — emit the midpoint as the peak.
      peaks.push(((runStart + (i - 1)) / 2) | 0);
      runStart = -1;
    }
  }
  // Final run hits the right edge without a closing zero.
  if (runStart >= 0) peaks.push(((runStart + (N - 1)) / 2) | 0);
  // ── Pass 2: enforce min spacing between adjacent peaks. If two
  // candidates are closer than minSpacing they belong to the same glyph
  // (e.g. the two stems of a lowercase "n"); merge by keeping the one
  // with greater signal value.
  const merged = [];
  for (const p of peaks) {
    if (merged.length > 0 && p - merged[merged.length - 1] < minSpacing) {
      if (signal[p] > signal[merged[merged.length - 1]]) {
        merged[merged.length - 1] = p;
      }
    } else {
      merged.push(p);
    }
  }
  return merged;
}

// ── Helpers shared with the test suite ──────────────────────────────────────

/**
 * Read a PNG file and decode it to the `{ data, width, height }` shape
 * the metric helpers consume. Re-exported (instead of duplicated in the
 * test) so a future refactor of the decode path stays in one place.
 */
export function readPng(filePath) {
  return PNG.sync.read(readFileSync(filePath));
}

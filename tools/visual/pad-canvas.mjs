// pad-canvas.mjs — normalize two captures onto a common canvas before any
// metric reads them.
//
// ## Why the fill colour is load-bearing
//
// The shared canvas is sized to the MAX width/height across the images being
// compared, and the shorter image is padded to fit. For most of this
// harness's life that padding was #1A1A2E — the exact colour every capture
// uses as its background.
//
// That made UNDER-SIZED output invisible. If one platform rendered a
// component too short (collapsed height, dropped trailing content, a missing
// final row), its shortfall was filled with precisely the colour the taller
// platform was already displaying in that region. pixelmatch reported 0.00 %,
// SSIM reported 1.0. Over-size was caught, because the taller image has real
// content where the shorter one has background — but under-size, which is the
// "missing element" failure mode, scored perfect.
//
// The fill is now magenta: the graphics convention for "not real content",
// and verified absent from all 363 committed baselines (near-magenta scan —
// r>230, b>230, g<25 — matched zero files), so it can never collide with
// something a runtime actually drew.
//
// ## Blast radius
//
// `padToCanvas` early-returns when the image already matches the canvas, so
// equal-sized comparisons — the overwhelming majority — are byte-for-byte
// unchanged. In the baseline gate both the baseline and the current capture
// are padded to the SAME canvas, so a matching pair still fills identically
// and still scores identically. The numbers only move where the sizes
// genuinely disagree, which is exactly the signal this exists to surface.

import { PNG } from 'pngjs';
import sharp from 'sharp';

/**
 * Fill colour for padded regions. Deliberately NOT the capture background
 * (#1A1A2E) — see the file header for the failure mode that caused.
 */
export const PAD_SENTINEL = Object.freeze({ r: 0xFF, g: 0x00, b: 0xFF, alpha: 1 });

/**
 * Pad `img` onto a (W × H) canvas filled with PAD_SENTINEL. No stretching,
 * no resampling — sharp's `extend` only grows, keeping every original pixel
 * at its original coordinate so the comparison stays pixel-aligned.
 *
 * @param {import('pngjs').PNG} img source image
 * @param {number} W target canvas width  (must be ≥ img.width)
 * @param {number} H target canvas height (must be ≥ img.height)
 * @returns {Promise<import('pngjs').PNG>} the padded image, or `img` untouched
 */
export async function padToCanvas(img, W, H) {
  // Exact fit → nothing to fill. This is the common case and the reason the
  // sentinel change is a no-op for same-sized comparisons.
  if (img.width === W && img.height === H) return img;

  const padded = await sharp(PNG.sync.write(img))
    .extend({
      top: 0,                                   // anchor top-left: content keeps its origin
      bottom: Math.max(0, H - img.height),      // clamp — extend refuses negatives
      left: 0,
      right: Math.max(0, W - img.width),
      background: { ...PAD_SENTINEL },          // sharp mutates its options object
    })
    .png()
    .toBuffer();

  return PNG.sync.read(padded);
}

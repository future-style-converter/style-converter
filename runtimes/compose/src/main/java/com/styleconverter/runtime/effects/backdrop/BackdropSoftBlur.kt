package com.styleconverter.runtime.effects.backdrop

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Wave 35 lane B3 — a software reimplementation of **Skia's** blur, run on
 * the sampled backdrop plate instead of handing the plate to
 * `RenderEffect.createBlurEffect`.
 *
 * ## The measurement that motivated it
 *
 * Per-tile mean absolute error against the Chrome 151 ref of
 * `css/filter-effects/backdrop-filter-boundary.html` — tile INTERIORS, inset
 * 15px so capture alignment cannot contribute — on the frozen wave34-final
 * captures (`_diag35/B3`):
 *
 * ```
 *   tile   σ     Android (RenderEffect)   iOS (CoreImage)
 *   1      3      6.23                     1.37
 *   2      6      9.12                     1.59
 *   4     24      9.36                     0.54
 *   5     48      6.50                     0.23
 * ```
 *
 * ## What the residual actually is (and what it is NOT)
 *
 * The obvious reading — "RenderEffect blurs with a box³ approximation and a
 * box³ is not a Gaussian" — is WRONG, and this file exists because measuring
 * it refuted it. The tile plates are computable off-device: each `.fg` sits
 * at (5,5) on a `.bg` painting `resources/reference.png`, so the exact input
 * to the filter is that PNG cropped to 150×80, and the expected output is the
 * frozen ref. Running both candidate models over that plate
 * (`_diag35/B3/model-check2.mjs`) gives:
 *
 * ```
 *   σ      true Gaussian     Skia three-pass box
 *   3        1.414              0.158
 *   6        1.604              0.119
 *  24        0.545              0.072
 *  48        0.143              0.164
 * ```
 *
 * The three-pass BOX reproduces the browser essentially exactly, and the true
 * Gaussian reproduces **iOS's** numbers term for term (1.41/1.60/0.55/0.14 vs
 * iOS's 1.37/1.59/0.54/0.23) — which identifies iOS's model, not Chrome's.
 * So Chrome's `backdrop-filter: blur()` IS Skia's three-pass box, and a
 * correct implementation of it lands at ~0.1 MAE — better than iOS.
 *
 * Android's 6–9 therefore comes from somewhere OTHER than the kernel shape.
 * The remaining candidate (not proven here, recorded as the open question) is
 * that Skia's GPU blur RESCALES for σ above ~4 — it downsamples by σ/4, blurs
 * at the reduced σ and bilinear-upsamples — so the emulator's RenderEffect
 * output carries resample error the CPU reference path never has. Whatever
 * the mechanism, running the browser's own kernel over the plate ourselves
 * removes the whole class.
 *
 * ## Shape
 *
 * Pure integer/float math over an ARGB [IntArray] — no Android types — so the
 * window rule, the edge model and the convolution all pin on the JVM
 * (BackdropSoftBlurTest). The bitmap glue lives in [BackdropImageOps].
 *
 * Colour space: samples stay in 8-bit **sRGB**, un-linearised —
 * filter-effects-1 §5 pins CSS filter functions to
 * `color-interpolation-filters: sRGB`, which is what the ref encodes.
 *
 * Cost: each pass is a SLIDING WINDOW, so the whole blur is O(w·h) with the
 * constant 6 (three passes × two axes) and is INDEPENDENT of σ. A 390×600
 * plate at σ=96 costs ~1.4M accumulator updates — which is why, unlike a
 * direct Gaussian convolution, this path needs no σ threshold at all.
 */
object BackdropSoftBlur {

    /**
     * Skia's `BlurSigmaToBoxSize`: `w = floor(σ·3·√(2π)/4 + 0.5)`. Three box
     * passes of this width converge (central limit) on a Gaussian of that σ,
     * and reproducing the constant EXACTLY is what puts the output on the
     * browser's pixels rather than merely near them. Floored at 1 so a
     * sub-pixel σ still runs an identity-width pass instead of dividing by 0.
     */
    fun windowFor(sigma: Float): Int =
        floor(sigma * 3.0 * sqrt(2.0 * PI) / 4.0 + 0.5).toInt().coerceAtLeast(1)

    /**
     * The three passes' tap windows as `[lo, hi]` pairs, Skia's parity rule:
     *  - ODD w: three identical windows of radius (w−1)/2 — already centred;
     *  - EVEN w: a centred window does not exist, so Skia uses `w`, `w`
     *    offset the other way, and `w+1`. The offsets sum to zero, so the
     *    combined support stays symmetric about the pixel.
     * Pinned in BackdropSoftBlurTest against both parities; the even branch
     * is what takes σ=3 from 2.06 MAE (naive even handling) to 0.158.
     */
    fun passes(window: Int): List<IntArray> {
        val w = window.coerceAtLeast(1)
        if (w % 2 == 1) {
            val r = (w - 1) / 2
            return listOf(intArrayOf(-r, r), intArrayOf(-r, r), intArrayOf(-r, r))
        }
        val h = w / 2
        return listOf(intArrayOf(-h, h - 1), intArrayOf(-h + 1, h), intArrayOf(-h, h))
    }

    /**
     * `SkTileMode::kMirror` index mapping: reflect about each edge with the
     * edge pixel duplicated (…2 1 0 | 0 1 2 … n−1 | n−1 n−2…), period 2n.
     * This is the extension the RenderEffect path asked for and the one the
     * WPT reference file (`support/simulate-backdrop-blur.js`) builds its
     * expectation from with `scale(-1)` copies of the crop — so the blurred
     * region can only ever see reflections of the element's own backdrop,
     * never the lime page behind it.
     */
    fun mirror(i: Int, n: Int): Int {
        if (n <= 1) return 0
        val period = 2 * n
        var x = i % period
        if (x < 0) x += period
        return if (x < n) x else period - 1 - x
    }

    /** Engagement guard: a real blur over a real patch. No σ ceiling — see the class kdoc's cost note. */
    fun engages(width: Int, height: Int, sigma: Float): Boolean =
        sigma > 0f && width > 0 && height > 0

    /**
     * Blur one ARGB8888 patch (0xAARRGGBB, NON-premultiplied — the shape
     * `Bitmap.getPixels` hands back) to standard deviation [sigma].
     *
     * The convolution runs on PREMULTIPLIED channels: averaging straight
     * (unassociated) colour next to a transparent pixel would drag that
     * pixel's meaningless RGB into the result and fringe it. The backdrop
     * plate is normally opaque, where premultiplying is the identity — but
     * a transparent-root snapshot is not, and the fringe would be a silent
     * wrong answer.
     *
     * @return a NEW array; the input is not modified.
     */
    fun blurArgb(pixels: IntArray, width: Int, height: Int, sigma: Float): IntArray {
        val n = width * height
        // Premultiplied planes, interleaved a,r,g,b so one pass touches one
        // cache line per pixel.
        var src = FloatArray(n * 4)
        for (i in 0 until n) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            val af = a / 255f
            src[i * 4] = a.toFloat()
            src[i * 4 + 1] = ((p ushr 16) and 0xFF) * af
            src[i * 4 + 2] = ((p ushr 8) and 0xFF) * af
            src[i * 4 + 3] = (p and 0xFF) * af
        }
        var dst = FloatArray(n * 4)
        val windows = passes(windowFor(sigma))
        // Separability: a 2-D box³ is the outer product of two 1-D ones, and
        // the six passes commute, so all three horizontals run before all
        // three verticals (one fewer buffer role-swap than interleaving).
        for (w in windows) { boxHorizontal(src, dst, width, height, w[0], w[1]); val t = src; src = dst; dst = t }
        for (w in windows) { boxVertical(src, dst, width, height, w[0], w[1]); val t = src; src = dst; dst = t }
        // Un-premultiply back to the 0xAARRGGBB the bitmap wants. A fully
        // transparent result carries no colour by definition.
        val out = IntArray(n)
        for (i in 0 until n) {
            val a = src[i * 4]
            val ai = a.roundToInt().coerceIn(0, 255)
            out[i] = if (ai == 0) 0 else {
                val inv = 255f / a
                (ai shl 24) or
                    ((src[i * 4 + 1] * inv).roundToInt().coerceIn(0, 255) shl 16) or
                    ((src[i * 4 + 2] * inv).roundToInt().coerceIn(0, 255) shl 8) or
                    (src[i * 4 + 3] * inv).roundToInt().coerceIn(0, 255)
            }
        }
        return out
    }

    /**
     * One horizontal box pass over the window `[lo, hi]`, as a sliding sum:
     * O(1) per output pixel regardless of the window width. `idx` resolves
     * the mirrored source column for every slot the window ever occupies,
     * once per pass, which is what lets the running sum step across the
     * reflected edge without special-casing it. Accumulators are Double —
     * a Float running sum drifts visibly over a few hundred add/subtract
     * cycles at 8-bit magnitudes.
     */
    private fun boxHorizontal(src: FloatArray, dst: FloatArray, w: Int, h: Int, lo: Int, hi: Int) {
        val m = hi - lo + 1
        val inv = 1.0 / m
        val idx = IntArray(w + m - 1) { mirror(it + lo, w) }
        for (y in 0 until h) {
            val row = y * w * 4
            var a = 0.0; var r = 0.0; var g = 0.0; var b = 0.0
            // Prime with the window sitting over output column 0.
            for (j in 0 until m) {
                val s = row + idx[j] * 4
                a += src[s]; r += src[s + 1]; g += src[s + 2]; b += src[s + 3]
            }
            for (x in 0 until w) {
                val d = row + x * 4
                dst[d] = (a * inv).toFloat(); dst[d + 1] = (r * inv).toFloat()
                dst[d + 2] = (g * inv).toFloat(); dst[d + 3] = (b * inv).toFloat()
                if (x + 1 < w) {
                    // Slide: drop the sample leaving the window, add the one entering.
                    val o = row + idx[x] * 4
                    val i = row + idx[x + m] * 4
                    a += src[i] - src[o]; r += src[i + 1] - src[o + 1]
                    g += src[i + 2] - src[o + 2]; b += src[i + 3] - src[o + 3]
                }
            }
        }
    }

    /** The vertical twin of [boxHorizontal] — same sliding window, row stride. */
    private fun boxVertical(src: FloatArray, dst: FloatArray, w: Int, h: Int, lo: Int, hi: Int) {
        val m = hi - lo + 1
        val inv = 1.0 / m
        val idx = IntArray(h + m - 1) { mirror(it + lo, h) }
        for (x in 0 until w) {
            var a = 0.0; var r = 0.0; var g = 0.0; var b = 0.0
            for (j in 0 until m) {
                val s = (idx[j] * w + x) * 4
                a += src[s]; r += src[s + 1]; g += src[s + 2]; b += src[s + 3]
            }
            for (y in 0 until h) {
                val d = (y * w + x) * 4
                dst[d] = (a * inv).toFloat(); dst[d + 1] = (r * inv).toFloat()
                dst[d + 2] = (g * inv).toFloat(); dst[d + 3] = (b * inv).toFloat()
                if (y + 1 < h) {
                    val o = (idx[y] * w + x) * 4
                    val i = (idx[y + m] * w + x) * 4
                    a += src[i] - src[o]; r += src[i + 1] - src[o + 1]
                    g += src[i + 2] - src[o + 2]; b += src[i + 3] - src[o + 3]
                }
            }
        }
    }

    /**
     * `invert(amount)` over an ARGB patch, filter-effects-1 §6.1 — the same
     * curve [BackdropChain.invertChannel] pins, applied in place on the
     * colour channels only (alpha is untouched by §8.6). Kept here so the
     * software path runs the WHOLE chain in CSS order instead of splitting
     * colour ops off to a separate draw-time filter.
     */
    fun invertArgb(pixels: IntArray, amount: Float) {
        val a = amount.coerceIn(0f, 1f)
        // c' = a + c·(1 − 2a) in 0..255 — one multiply-add per channel.
        val scale = 1f - 2f * a
        val bias = a * 255f
        for (i in pixels.indices) {
            val p = pixels[i]
            val rr = (bias + ((p ushr 16) and 0xFF) * scale).roundToInt().coerceIn(0, 255)
            val gg = (bias + ((p ushr 8) and 0xFF) * scale).roundToInt().coerceIn(0, 255)
            val bb = (bias + (p and 0xFF) * scale).roundToInt().coerceIn(0, 255)
            pixels[i] = (p and 0xFF000000.toInt()) or (rr shl 16) or (gg shl 8) or bb
        }
    }
}

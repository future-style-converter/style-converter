package com.styleconverter.runtime.effects.backdrop

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp

/**
 * Wave 35 lane B3 — the bitmap half of the software backdrop pipeline: crop
 * the pass-A plate, run the [BackdropChain] over the pixels IN CSS ORDER with
 * [BackdropSoftBlur]'s Skia-exact kernel, and hand back a ready-to-blit patch.
 *
 * Why a second path at all, when [BackdropRenderEffects] already renders
 * every chain: on the measured corpus `RenderEffect.createBlurEffect` lands
 * 6.2–9.4 MAE from the Chrome ref where the browser's own kernel, computed
 * over the same plate, lands at ~0.1 (the tables in [BackdropSoftBlur]'s
 * header). A blur is the only op where the two paths can differ — a colour
 * matrix is exact on both — so this path engages ONLY for chains that carry
 * one, and every invert-only chain keeps the frozen RenderEffect render
 * byte-for-byte.
 *
 * Every refusal below returns null and the caller keeps the hardware path;
 * the one refusal that could surprise (a plate software cannot read) logs
 * once rather than downgrading silently.
 */
internal object BackdropImageOps {

    private const val TAG = "BackdropImageOps"

    /** One-shot dedupe so a per-frame refusal cannot flood logcat. */
    private val logged = HashSet<String>()

    private fun logOnce(message: String) {
        if (logged.add(message)) Log.i(TAG, message)
    }

    /**
     * Render the chain over the sampled crop, or null to keep the hardware
     * path.
     *
     * @param backdrop the pass-A plate (canvas pixel space).
     * @param sample the crop [BackdropSampleGeometry] resolved — already
     *   clamped inside the plate, so the reads below cannot go out of bounds.
     * @param chain the ordered invert/blur chain.
     * @param radiusToPx Dp→px for the blur radii; injected (rather than read
     *   off a Density here) so the σ arithmetic matches the caller's draw
     *   scope exactly, the same way [BackdropChain.totalBlurSigmaPx] takes it.
     */
    fun render(
        backdrop: ImageBitmap,
        sample: BackdropSample,
        chain: BackdropChain,
        radiusToPx: (Dp) -> Float,
    ): ImageBitmap? {
        // Colour-matrix-only chains are EXACT on the RenderEffect path —
        // nothing to gain, and keeping them there keeps every invert-only
        // fixture (backdrop-filter-basic/-opacity/-box-shadow/-clip-rect-2)
        // byte-identical. Not a refusal worth logging.
        if (!chain.hasBlur) return null
        // A blur that resolves to 0px at this density is the identity on both
        // paths (BackdropChain.of already drops the authored blur(0)), and a
        // degenerate crop has nothing to convolve.
        val maxSigma = chain.ops.filterIsInstance<BackdropOp.Blur>()
            .maxOfOrNull { BackdropChain.blurSigmaPx(radiusToPx(it.radius)) } ?: return null
        if (!BackdropSoftBlur.engages(sample.width, sample.height, maxSigma)) return null
        // Read the crop. A HARDWARE-config plate refuses getPixels; so does a
        // recycled one. Either way the hardware path is the honest fallback.
        val pixels = try {
            val source = backdrop.asAndroidBitmap()
            IntArray(sample.width * sample.height).also {
                source.getPixels(it, 0, sample.width, sample.srcLeft, sample.srcTop, sample.width, sample.height)
            }
        } catch (e: RuntimeException) {
            logOnce("backdrop plate not readable in software (${e.javaClass.simpleName}) — keeping RenderEffect")
            return null
        }
        // Run the chain IN ORDER: `invert(1) blur(4px)` blurs the inverted
        // plate, `blur(4px) invert(1)` inverts the blurred one. (The two
        // happen to commute for an opaque plate — invert is affine and every
        // box window is DC-normalised — but ordering them is free and correct.)
        var current = pixels
        for (op in chain.ops) when (op) {
            is BackdropOp.Invert -> BackdropSoftBlur.invertArgb(current, op.amount)
            is BackdropOp.Blur -> {
                val sigma = BackdropChain.blurSigmaPx(radiusToPx(op.radius))
                // blur(0) is the identity — skip rather than run three
                // width-1 passes (same rule the RenderEffect builder applies).
                if (sigma > 0f) current = BackdropSoftBlur.blurArgb(
                    current, sample.width, sample.height, sigma,
                )
            }
        }
        // ARGB_8888 so the un-premultiplied ints above land verbatim; the
        // caller blits it 1:1, so no scaling filter ever touches these pixels.
        return Bitmap.createBitmap(current, sample.width, sample.height, Bitmap.Config.ARGB_8888)
            .asImageBitmap()
    }
}

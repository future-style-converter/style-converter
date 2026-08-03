package com.styleconverter.runtime.effects.backdrop

import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas

/**
 * The hardware (API 31+) half of the backdrop painter, split out of
 * [BackdropPainter] to keep both files inside the engine's size rule: this is
 * one value family (RenderEffect-backed chains), the other file is the
 * geometry + software fallback.
 *
 * Why RenderEffect at all: it is the only Android API that runs a true
 * Gaussian with an explicit edge [Shader.TileMode], which is exactly what
 * filter-effects-2 §2 asks for at the backdrop root boundary — and it chains,
 * so the CSS function order survives intact.
 */
@RequiresApi(Build.VERSION_CODES.S)
internal object BackdropRenderEffects {

    /**
     * Record the sampled crop into a RenderNode, hang the chain off it as a
     * RenderEffect, and draw the node at the crop's element-local origin.
     */
    fun draw(
        scope: DrawScope,
        backdrop: ImageBitmap,
        sample: BackdropSample,
        chain: BackdropChain,
        // The element's own `opacity` (see BackdropModifier.elementAlpha).
        alpha: Float = 1f,
        // The border box's origin inside the draw node (the element's resolved
        // left/top margin bands — this node is outer of the margin step, see
        // BackdropSampleGeometry.borderBox). Zero for a margin-less element,
        // which is why both parameters default.
        originX: Float = 0f,
        originY: Float = 0f,
    ) = with(scope) {
        val node = RenderNode("backdrop-filter")
        // The node's own space is the crop: (0,0)..(w,h).
        node.setPosition(0, 0, sample.width, sample.height)
        // RenderNode.setAlpha applies to the node's whole composited result,
        // i.e. AFTER the RenderEffect chain — which is the order
        // filter-effects-2 §2 wants (filter the backdrop, then composite the
        // filtered image into the element's group at the group's opacity).
        node.alpha = alpha
        node.setRenderEffect(effectFor(scope, chain))
        val rc = node.beginRecording()
        try {
            // Blit the crop 1:1. The source rect is already clamped inside the
            // bitmap by BackdropSampleGeometry, so this can never read out of
            // bounds; anything the blur wants BEYOND it comes from the
            // effect's TileMode.CLAMP — the spec's edge duplication.
            rc.drawBitmap(
                backdrop.asAndroidBitmap(),
                android.graphics.Rect(
                    sample.srcLeft, sample.srcTop,
                    sample.srcLeft + sample.width, sample.srcTop + sample.height,
                ),
                android.graphics.Rect(0, 0, sample.width, sample.height),
                null,
            )
        } finally {
            // endRecording must run even if the blit throws, or the node stays
            // in a recording state and the next frame trips an IllegalState.
            node.endRecording()
        }
        val canvas = drawContext.canvas.nativeCanvas
        val save = canvas.save()
        // Place the (possibly padded, hence possibly negative) crop origin,
        // measured from the BORDER box — so shift by the border box's own
        // position inside this draw node first.
        canvas.translate(originX + sample.dstLeft.toFloat(), originY + sample.dstTop.toFloat())
        canvas.drawRenderNode(node)
        canvas.restoreToCount(save)
    }

    /**
     * The chain as a RenderEffect, built in CSS order: each op wraps the
     * previous one as its input, so `invert(1) blur(4px)` blurs the inverted
     * backdrop and the reverse order inverts the blurred one.
     */
    private fun effectFor(scope: DrawScope, chain: BackdropChain): RenderEffect? {
        var effect: RenderEffect? = null
        for (op in chain.ops) {
            effect = when (op) {
                is BackdropOp.Invert -> {
                    // Same 4×5 matrix the element-filter path uses for
                    // `filter: invert()`; pinned scalar-for-scalar in
                    // BackdropChainTest.
                    val cf = android.graphics.ColorMatrixColorFilter(
                        android.graphics.ColorMatrix(BackdropChain.invertColorMatrix(op.amount)),
                    )
                    if (effect == null) RenderEffect.createColorFilterEffect(cf)
                    else RenderEffect.createColorFilterEffect(cf, effect)
                }
                is BackdropOp.Blur -> {
                    val sigma = BackdropChain.blurSigmaPx(with(scope) { op.radius.toPx() })
                    // blur(0) is the identity — skip rather than ask the
                    // platform for a zero-radius effect.
                    if (sigma <= 0f) effect
                    // TileMode.CLAMP: the backdrop root's edge pixels extend
                    // outward, so a box at the canvas edge blurs against a
                    // duplicated edge instead of fading to transparent.
                    else if (effect == null)
                        RenderEffect.createBlurEffect(sigma, sigma, Shader.TileMode.CLAMP)
                    else RenderEffect.createBlurEffect(sigma, sigma, effect, Shader.TileMode.CLAMP)
                }
            }
        }
        return effect
    }
}

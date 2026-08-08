package com.styleconverter.runtime.effects.backdrop

import android.os.Build
import android.util.Log
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Pass B's painter: samples the backdrop root image under one element's border
 * box, runs the filter chain over it, and draws the result — under whatever
 * the element itself paints next (the caller's `drawContent()`).
 *
 * All the decisions here are Android draw-API plumbing; every rule they
 * implement lives in the pure helpers ([BackdropChain], [BackdropSampleGeometry],
 * [BackdropClipGeometry]) so they can be pinned without a device.
 */
object BackdropPainter {

    private const val TAG = "BackdropPainter"

    /**
     * @param scope the element's own draw scope. Its `size` is the MARGIN box
     *   (this draw node is outer of the margin step), which is exactly why the
     *   border box arrives as [box] instead of being read off the scope.
     * @param backdrop pass-A snapshot, canvas pixel space.
     * @param elemLeftPx / [elemTopPx] the element's BORDER-box origin in that
     *   same canvas pixel space (window position + margin bands − canvas
     *   origin).
     * @param box the border box inside the draw node's own space — origin
     *   ([BackdropBorderBox.localLeft]/[BackdropBorderBox.localTop]) and size.
     * @param chain the ordered invert/blur chain.
     * @param radii resolved border-radius, or null for a square box.
     */
    fun paint(
        scope: DrawScope,
        backdrop: ImageBitmap,
        elemLeftPx: Int,
        elemTopPx: Int,
        box: BackdropBorderBox,
        chain: BackdropChain,
        radii: BackdropCornerRadii?,
        // The element's own `opacity` (see BackdropModifier.elementAlpha).
        // Defaulted so the parameter is additive for any other caller.
        alpha: Float = 1f,
    ) = with(scope) {
        // Border box in px, margin bands already removed by the caller.
        val boxW = box.width
        val boxH = box.height
        // Where that box starts inside this draw node — non-zero only for an
        // element with left/top margin. Every draw below is offset by it.
        val originX = box.localLeft
        val originY = box.localTop
        // Which backdrop pixels this element may read: the BORDER BOX, clamped
        // into the image. No σ term — filter-effects-2 §2 clips the backdrop to
        // the border box before filtering, and TileMode.MIRROR (below) supplies
        // everything the Gaussian wants beyond it. See BackdropSampleGeometry's
        // header for the wave-34 measurement that refuted the grow-by-3σ model.
        val sample = BackdropSampleGeometry.sample(
            elemLeft = elemLeftPx,
            elemTop = elemTopPx,
            // Rounded (not truncated) so a fractional layout edge lands on the
            // same pixel the element's own paint uses.
            elemWidth = Math.round(boxW),
            elemHeight = Math.round(boxH),
            srcWidth = backdrop.width,
            srcHeight = backdrop.height,
        )
        if (sample == null) {
            // No silent fallthrough: a degenerate or off-canvas box draws no
            // patch, and says so once, instead of leaving a plausible-looking
            // wrong rectangle behind.
            Log.w(TAG, "backdrop sample empty: box=${boxW}x$boxH at ($elemLeftPx,$elemTopPx) " +
                "src=${backdrop.width}x${backdrop.height}")
            return@with
        }

        // The filtered patch is clipped to the border box (filter-effects-2 §2)
        // — including its rounded corners. clipPath/clipRect both restore on
        // exit, so nothing leaks into the element's own paint that follows.
        val clip = radii?.takeIf { !it.isSquare }
            ?.let { borderBoxPath(it, originX, originY, boxW, boxH) }
        if (clip != null) {
            clipPath(clip) { drawPatch(this, backdrop, sample, chain, alpha, originX, originY) }
        } else {
            // Square box: the sample IS the border box, so the patch already
            // lands inside it — the rect clip stays as the belt-and-braces cut
            // for a fractional box whose rounded pixel size overshoots by one.
            clipRect(
                left = originX, top = originY,
                right = originX + boxW, bottom = originY + boxH,
            ) {
                drawPatch(this, backdrop, sample, chain, alpha, originX, originY)
            }
        }
    }

    /**
     * Draw the sampled crop with the chain applied, positioned so the crop's
     * pixels land exactly where they sat on the canvas.
     *
     * [originX]/[originY] shift every destination by the border box's own
     * origin inside the draw node (the left/top margin bands), so the sample's
     * border-box-relative `dstLeft`/`dstTop` stay pure geometry.
     */
    private fun drawPatch(
        scope: DrawScope,
        backdrop: ImageBitmap,
        sample: BackdropSample,
        chain: BackdropChain,
        alpha: Float,
        originX: Float,
        originY: Float,
    ) = with(scope) {
        // ── Wave-35 lane B3: SOFTWARE BLUR path ───────────────────────────
        // RenderEffect's blur lands 6.2–9.4 MAE from the Chrome ref on the
        // measured corpus, while the browser's OWN kernel (Skia's three-pass
        // box, exact parity rule) computed over the same plate lands at ~0.1
        // — the two tables in BackdropSoftBlur's header, with the off-device
        // model check that separates kernel shape from pipeline error.
        // A blurred chain therefore convolves here instead, with the same
        // MIRROR edge model the hardware path asks for. Returns null (→ the
        // hardware path below, unchanged) for every chain WITHOUT a blur, for
        // a degenerate σ/crop, and for a plate software cannot read, so
        // every invert-only fixture keeps its frozen render byte-for-byte.
        BackdropImageOps.render(backdrop, sample, chain) { it.toPx() }?.let { patch ->
            drawImage(
                image = patch,
                // The patch IS the crop, so it blits 1:1 at the crop's own
                // border-box-relative origin — the exact placement the
                // software fallback below uses.
                dstOffset = IntOffset(
                    sample.dstLeft + Math.round(originX),
                    sample.dstTop + Math.round(originY),
                ),
                dstSize = IntSize(sample.width, sample.height),
                // Element opacity composites the FILTERED patch, matching
                // RenderNode.setAlpha's after-the-effect order.
                alpha = alpha,
            )
            return@with
        }
        // Hardware path (API 31+): a RenderNode carrying the chain as a
        // RenderEffect. Still the route for every colour-matrix-only chain
        // (exact there) and for a plate the software path cannot read.
        val hardware = drawContext.canvas.nativeCanvas.isHardwareAccelerated
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hardware) {
            BackdropRenderEffects.draw(scope, backdrop, sample, chain, alpha, originX, originY)
            return@with
        }
        // Software / pre-31 path: colour-matrix ops still render exactly
        // (drawImage takes a ColorFilter), but there is no RenderEffect, so a
        // blur in the chain CANNOT be honoured. Say so — an unblurred backdrop
        // is a visible, attributable miss, not a silent approximation.
        if (chain.hasBlur) {
            Log.w(TAG, "backdrop blur unsupported here (sdk=${Build.VERSION.SDK_INT}, " +
                "hardware=$hardware) — drawing the unblurred backdrop")
        }
        try {
            drawImage(
                image = backdrop,
                srcOffset = IntOffset(sample.srcLeft, sample.srcTop),
                srcSize = IntSize(sample.width, sample.height),
                // Border-box-relative crop origin, shifted into draw-node space
                // by the margin bands. Rounded so a fractional band lands on
                // the same pixel column the rect clip above cuts at.
                dstOffset = IntOffset(
                    sample.dstLeft + Math.round(originX),
                    sample.dstTop + Math.round(originY),
                ),
                dstSize = IntSize(sample.width, sample.height),
                // Element opacity rides the draw's own alpha — the same knob
                // Compose's drawImage exposes, so no extra layer is needed.
                alpha = alpha,
                colorFilter = colorFilterFor(chain),
            )
        } catch (e: IllegalArgumentException) {
            // A pass-A snapshot can come back as a HARDWARE-config bitmap,
            // which a software canvas refuses to draw. Losing one patch must
            // not take the capture loop down with it — the fixture is then
            // honestly missing its backdrop, and says so here.
            Log.e(TAG, "backdrop patch draw refused on a software canvas", e)
        }
    }

    /**
     * The colour-matrix half of the chain as a Compose ColorFilter, for the
     * software / pre-31 path. Matrices multiply in chain order, matching the
     * element-filter path's own combination rule.
     */
    private fun colorFilterFor(chain: BackdropChain): ColorFilter? {
        var matrix: ColorMatrix? = null
        for (op in chain.ops) if (op is BackdropOp.Invert) {
            val next = ColorMatrix(BackdropChain.invertColorMatrix(op.amount))
            matrix = matrix?.apply { timesAssign(next) } ?: next
        }
        return matrix?.let { ColorFilter.colorMatrix(it) }
    }

    /**
     * The border box as a Path, with the element's own elliptical corners,
     * placed at ([left], [top]) inside the draw node — the margin bands, which
     * are zero for the overwhelming majority of elements.
     */
    private fun borderBoxPath(
        radii: BackdropCornerRadii,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
    ): Path =
        Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(left, top, left + width, top + height),
                    topLeft = CornerRadius(radii.topLeftX, radii.topLeftY),
                    topRight = CornerRadius(radii.topRightX, radii.topRightY),
                    bottomRight = CornerRadius(radii.bottomRightX, radii.bottomRightY),
                    bottomLeft = CornerRadius(radii.bottomLeftX, radii.bottomLeftY),
                ),
            )
        }
}

package com.styleconverter.runtime.borders.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.core.images.ImageCache
import com.styleconverter.runtime.core.images.rememberCachedGradient
import com.styleconverter.runtime.core.images.rememberCachedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Applies CSS border-image using custom Canvas drawing.
 *
 * ## CSS Property
 * ```css
 * .decorative-border {
 *     border-image-source: url('border.png');
 *     border-image-slice: 30;
 *     border-image-width: 20px;
 *     border-image-outset: 5px;
 *     border-image-repeat: round;
 * }
 * ```
 *
 * ## Compose Limitation
 *
 * Compose's `Modifier.border()` only supports:
 * - Solid colors
 * - Basic shapes (rectangle, rounded rectangle, circle)
 * - No image-based borders
 *
 * ## Implementation Strategy
 *
 * CSS border-image uses a 9-slice scaling approach:
 * ```
 * ┌─────┬─────────┬─────┐
 * │  1  │    2    │  3  │  ← top slice
 * ├─────┼─────────┼─────┤
 * │  4  │    5    │  6  │  ← middle (fill if sliceFill=true)
 * ├─────┼─────────┼─────┤
 * │  7  │    8    │  9  │  ← bottom slice
 * └─────┴─────────┴─────┘
 *   ↑        ↑        ↑
 * left    middle   right
 * slice            slice
 * ```
 *
 * - Corners (1, 3, 7, 9): Drawn without scaling
 * - Edges (2, 4, 6, 8): Stretched, repeated, or rounded based on repeat value
 * - Center (5): Filled only if sliceFill is true
 *
 * ## Usage
 * ```kotlin
 * BorderImageApplier.BorderImageBox(
 *     config = borderImageConfig,
 *     modifier = Modifier.size(200.dp)
 * ) {
 *     // Content
 * }
 * ```
 *
 * ## Limitations
 * - Network images loaded asynchronously
 * - Performance: Custom drawing for each frame
 * (Retro P2e, finding A6#15: "Gradient sources require separate
 * implementation" used to head this list and has been false for a long
 * time — `BorderImageSourceValue.Gradient` is rasterised by
 * [rememberCachedGradient] + `renderGradientToBitmap` a few lines into
 * [BorderImageBox], covering linear / radial / conic and their repeating
 * forms. What IS still true is narrower and lives at the parser:
 * `parseGradient` returns null for a syntax it cannot read, and a null
 * bitmap paints no border image at all.)
 */
object BorderImageApplier {

    /**
     * Composable that renders content with a border image.
     *
     * @param config Border image configuration
     * @param modifier Modifier for the container
     * @param fallbackBorderWidth Fallback width if not specified in config
     * @param content Content to render inside the border
     */
    @Composable
    fun BorderImageBox(
        config: BorderImageConfig,
        modifier: Modifier = Modifier,
        fallbackBorderWidth: Dp = 8.dp,
        content: @Composable BoxScope.() -> Unit
    ) {
        if (!config.hasBorderImage) {
            // No border image, just render content
            Box(modifier = modifier, content = content)
            return
        }

        val density = LocalDensity.current

        // Resolve the four border-image-width values against the element's
        // COMPUTED border widths (css-backgrounds-3 §5.3). When every side
        // resolves to 0 — the spec outcome for the initial `1` (or any
        // `<number>`) multiplier on a border-less element — the border
        // image paints NOTHING and must not inset the content either.
        // Browsers render Borders_C06 (`border-image-width: 2`, no border)
        // as a plain background box; the old 8dp fallback painted a thick
        // gradient frame here (Android-web SSIM 0.5861).
        val resolvedTop = config.widthTop.resolve(config.computedBorderTop, config.sliceTop)
        val resolvedRight = config.widthRight.resolve(config.computedBorderRight, config.sliceRight)
        val resolvedBottom = config.widthBottom.resolve(config.computedBorderBottom, config.sliceBottom)
        val resolvedLeft = config.widthLeft.resolve(config.computedBorderLeft, config.sliceLeft)
        if (resolvedTop <= 0.dp && resolvedRight <= 0.dp &&
            resolvedBottom <= 0.dp && resolvedLeft <= 0.dp
        ) {
            // Zero-width border image area — nothing to paint, no inset.
            Box(modifier = modifier, content = content)
            return
        }

        // Use cached image loading
        val urlBitmap = when (val source = config.source) {
            is BorderImageSourceValue.Url -> rememberCachedImage(source.url)
            else -> null
        }

        // Use cached gradient generation
        val gradientBitmap = when (val source = config.source) {
            is BorderImageSourceValue.Gradient -> rememberCachedGradient(
                gradientString = source.gradient,
                width = 256,
                height = 256,
                generator = { str, w, h -> renderGradientToBitmap(str, w, h) }
            )
            else -> null
        }

        val imageBitmap = urlBitmap ?: gradientBitmap

        // Border widths resolved above against the computed border-widths.
        val borderTop = resolvedTop
        val borderRight = resolvedRight
        val borderBottom = resolvedBottom
        val borderLeft = resolvedLeft

        // Calculate outsets. css-backgrounds-3 §5.4: <length> is literal,
        // <number> is a multiple of the computed border-width, initial 0.
        val outsetTop = config.outsetTop.resolveOutset(config.computedBorderTop)
        val outsetRight = config.outsetRight.resolveOutset(config.computedBorderRight)
        val outsetBottom = config.outsetBottom.resolveOutset(config.computedBorderBottom)
        val outsetLeft = config.outsetLeft.resolveOutset(config.computedBorderLeft)

        Box(
            modifier = modifier
                // Outset is deliberately NOT a layout modifier: css-backgrounds-3
                // §5.4 — "the border image area ... can extend outside the border
                // box" and the outset region "does not trigger scrolling" nor
                // "affect layout". Painting outward is safe here because
                // Modifier.drawBehind draws with an UNCLIPPED DrawScope — Compose
                // only clips a node's drawing when an explicit clip modifier
                // (Modifier.clip / clipToBounds / graphicsLayer(clip = true)) is
                // present, and neither this chain nor the harness's CaptureCanvas
                // wraps components in one (its 16dp canvas padding gives the
                // overdraw room inside the captured rect). The pre-fix code
                // applied outset as an inward .padding(), i.e. the exact INVERSE
                // of the spec: it shrank the border box and pulled the image in.
                .drawBehind {
                    imageBitmap?.let { bitmap ->
                        drawBorderImage(
                            bitmap = bitmap,
                            config = config,
                            borderTop = borderTop.toPx(),
                            borderRight = borderRight.toPx(),
                            borderBottom = borderBottom.toPx(),
                            borderLeft = borderLeft.toPx(),
                            // Expand the destination geometry outward: outset
                            // (§5.4) PLUS the computed border band PLUS the
                            // resolved CSS padding. The compensation exists
                            // because this drawBehind sits AFTER the
                            // component's own modifier chain, which already
                            // contains StyleApplier.borderContentInset AND
                            // LayoutFacade's padding modifier — so the
                            // DrawScope's (0,0)..(w,h) is the padded CONTENT
                            // box, not the border box. Without adding both
                            // insets back, the 9-slice frame painted inset
                            // inside the content area (the wave-3 device gate
                            // showed the amber frame floating ~20px inside the
                            // perimeter; the skeptic repro showed padding 20px
                            // + border 4px pulling it a further 20px in).
                            // §5.3/§5.4: the border image area IS the border box (+
                            // outset) — reconstruct it by expanding each side
                            // by exactly what the chain inset (destExpansion).
                            outsetTop = destExpansion(outsetTop.toPx(), config.computedBorderTop.toPx(), config.resolvedPaddingTop.toPx()),
                            outsetRight = destExpansion(outsetRight.toPx(), config.computedBorderRight.toPx(), config.resolvedPaddingRight.toPx()),
                            outsetBottom = destExpansion(outsetBottom.toPx(), config.computedBorderBottom.toPx(), config.resolvedPaddingBottom.toPx()),
                            outsetLeft = destExpansion(outsetLeft.toPx(), config.computedBorderLeft.toPx(), config.resolvedPaddingLeft.toPx())
                        )
                    }
                },
            // NO content padding here — deliberately. css-backgrounds-3 §5:
            // the border-image properties "do not affect layout"; content is
            // inset by border-width ONLY, and ComponentRenderer already
            // chains StyleApplier.borderContentInset (the computed border
            // widths) into `modifier` before wrapping in this Box. The
            // previous extraContentInset padding reserved the part of the
            // resolved border-image-width exceeding the border band, which
            // Chromium never does — skeptic repro: border 10px +
            // border-image-width 15px leaves the web content inset at 10px,
            // while the extra 5px padding here shrank the Android content
            // box and diverged from both web and iOS.
            content = content
        )
    }

    /**
     * Per-side outward expansion of the drawBehind destination: the §5.4
     * outset (the only spec'd growth beyond the border box) plus the two
     * insets the component's modifier chain already applied BEFORE this
     * drawBehind — the computed border band (StyleApplier
     * .borderContentInset) and the resolved CSS padding (LayoutFacade /
     * PaddingApplier). Summing them reconstructs the border box (+outset)
     * from the DrawScope's padded content box. Skeptic repro pinned in
     * BorderImageSpecFixesTest: padding 20px + border 4px + outset 0 on a
     * 200px box → 24px per side, so the dest spans the full 200px.
     * Pure arithmetic, internal for JVM tests.
     */
    internal fun destExpansion(
        outsetPx: Float,
        computedBorderPx: Float,
        resolvedPaddingPx: Float
    ): Float = outsetPx + computedBorderPx + resolvedPaddingPx

    /**
     * The four slice offsets in image pixels AFTER the overlap fix. css-backgrounds-3
     * §5.2 empties the edges of overlapping slices ("if the sum of the right and
     * left widths is equal to or greater than the width of the image"); both
     * natives instead apply §5.3's rule for overlapping WIDTHS ("proportionally
     * reduced until they no longer overlap") to the slices, so the degenerate
     * case still paints (reduceSlices). Plain holder so the reduction stays a
     * pure, JVM-testable function.
     */
    internal data class ReducedSlices(
        val top: Float,
        val right: Float,
        val bottom: Float,
        val left: Float
    )

    /**
     * Apply the proportional slice reduction (css-backgrounds-3 §5.3's
     * width-overlap rule, used here on §5.2 slices — see ReducedSlices): when
     * left+right exceeds the image width (or top+bottom its height) BOTH
     * sides scale by extent/sum, so opposing slice lines never cross.
     * Without this, any slice ≥ 50% (e.g. `border-image-slice: 60%` on a
     * 30×30 source → 18+18 = 36 > 30) produces a NEGATIVE center-column
     * width and inverted src rects downstream. Mirrors the iOS
     * BorderImageMath.nineGrid overlap branch exactly (same clamp, same
     * factor) so both natives paint the identical degenerate geometry.
     * Negative inputs are clamped to 0 first — negative slices are
     * invalid per the §5.2 grammar, matching iOS slicePx's clamp.
     * Pure arithmetic, internal for JVM tests.
     */
    internal fun reduceSlices(
        top: Float,
        right: Float,
        bottom: Float,
        left: Float,
        imageWidth: Float,
        imageHeight: Float
    ): ReducedSlices {
        // Clamp invalid negative inputs before the overlap check (§5.2
        // grammar: slice values are non-negative).
        var t = kotlin.math.max(top, 0f)
        var r = kotlin.math.max(right, 0f)
        var b = kotlin.math.max(bottom, 0f)
        var l = kotlin.math.max(left, 0f)
        // Horizontal overlap: left+right may not exceed the image width.
        // The sum > 0 guard avoids 0/0 on a degenerate zero-width image.
        if (l + r > imageWidth && l + r > 0f) {
            // §5.3-style factor — both sides shrink by the SAME ratio.
            val f = imageWidth / (l + r)
            l *= f
            r *= f
        }
        // Vertical overlap: top+bottom may not exceed the image height.
        if (t + b > imageHeight && t + b > 0f) {
            // Same proportional factor on the vertical axis.
            val f = imageHeight / (t + b)
            t *= f
            b *= f
        }
        // Reduced offsets — center extents are now non-negative.
        return ReducedSlices(top = t, right = r, bottom = b, left = l)
    }

    /**
     * Border-image destination rectangle in the draw scope's local
     * coordinates: the element's border box ([width] × [height] at origin
     * 0,0) EXPANDED outward by the four resolved outsets — css-backgrounds-3
     * §5.4: "The border-image-outset properties specify the amount by which
     * the border image area extends beyond the border box." Origin moves to
     * (−outsetLeft, −outsetTop); size grows by the per-axis outset sums.
     * Pure Compose-geometry math (no DrawScope), internal for JVM tests.
     */
    internal fun outsetDestRect(
        width: Float,
        height: Float,
        outsetTop: Float,
        outsetRight: Float,
        outsetBottom: Float,
        outsetLeft: Float
    ): Rect = Rect(
        // Left/top edges shift OUTWARD (negative local coords) by their outset.
        // `0f - x` instead of unary `-x`: IEEE-754 negation of +0.0 yields
        // −0.0, and Compose Rect's equals compares Floats BITWISE (via
        // Float.equals), so Rect(−0.0,…) != Rect(0.0,…) even though they
        // print identically — a zero outset must produce exactly +0.0.
        left = 0f - outsetLeft,
        top = 0f - outsetTop,
        // Right/bottom edges extend past the border box by their outset.
        right = width + outsetRight,
        bottom = height + outsetBottom
    )

    /**
     * Draw the 9-slice border image into the outset-expanded destination
     * rect (border box grown per css-backgrounds-3 §5.4 — outsets default
     * to 0, keeping the destination exactly the border box).
     */
    private fun DrawScope.drawBorderImage(
        bitmap: ImageBitmap,
        config: BorderImageConfig,
        borderTop: Float,
        borderRight: Float,
        borderBottom: Float,
        borderLeft: Float,
        outsetTop: Float = 0f,
        outsetRight: Float = 0f,
        outsetBottom: Float = 0f,
        outsetLeft: Float = 0f
    ) {
        val imageWidth = bitmap.width.toFloat()
        val imageHeight = bitmap.height.toFloat()

        // Calculate slice sizes in image pixels, then apply the §5.3-style
        // proportional reduction (ReducedSlices): opposing slices whose sum exceeds the
        // image extent scale down by extent/sum — without it, slice ≥ 50%
        // makes (imageWidth − sliceRight) < sliceLeft and the center/edge
        // source rects below get NEGATIVE widths (drawImage crash / garbage
        // geometry). Mirrors the iOS BorderImageMath.nineGrid overlap fix
        // so the two natives agree on the degenerate range.
        val slices = reduceSlices(
            top = config.sliceTop.toPixels(imageHeight),
            right = config.sliceRight.toPixels(imageWidth),
            bottom = config.sliceBottom.toPixels(imageHeight),
            left = config.sliceLeft.toPixels(imageWidth),
            imageWidth = imageWidth,
            imageHeight = imageHeight
        )
        // Reduced per-side slice px — every src rect below uses these.
        val sliceTop = slices.top
        val sliceRight = slices.right
        val sliceBottom = slices.bottom
        val sliceLeft = slices.left

        // Destination area: the border box (`size`) expanded by the outsets
        // (§5.4). drawBehind's DrawScope is unclipped, so the negative /
        // past-size coordinates below paint outside the layout bounds as the
        // spec requires (see the BorderImageBox comment for the clip audit).
        val dest = outsetDestRect(
            width = size.width,
            height = size.height,
            outsetTop = outsetTop,
            outsetRight = outsetRight,
            outsetBottom = outsetBottom,
            outsetLeft = outsetLeft
        )
        // Integer dest edges — drawImage takes IntRect geometry; truncation
        // matches the pre-existing rounding of the border-band edges below.
        val destLeft = dest.left.toInt()
        val destTop = dest.top.toInt()
        val destRight = dest.right.toInt()
        val destBottom = dest.bottom.toInt()

        // Source rectangles (from the image)
        val srcTopLeft = IntRect(0, 0, sliceLeft.toInt(), sliceTop.toInt())
        val srcTopCenter = IntRect(sliceLeft.toInt(), 0, (imageWidth - sliceRight).toInt(), sliceTop.toInt())
        val srcTopRight = IntRect((imageWidth - sliceRight).toInt(), 0, imageWidth.toInt(), sliceTop.toInt())

        val srcMiddleLeft = IntRect(0, sliceTop.toInt(), sliceLeft.toInt(), (imageHeight - sliceBottom).toInt())
        val srcMiddleCenter = IntRect(sliceLeft.toInt(), sliceTop.toInt(), (imageWidth - sliceRight).toInt(), (imageHeight - sliceBottom).toInt())
        val srcMiddleRight = IntRect((imageWidth - sliceRight).toInt(), sliceTop.toInt(), imageWidth.toInt(), (imageHeight - sliceBottom).toInt())

        val srcBottomLeft = IntRect(0, (imageHeight - sliceBottom).toInt(), sliceLeft.toInt(), imageHeight.toInt())
        val srcBottomCenter = IntRect(sliceLeft.toInt(), (imageHeight - sliceBottom).toInt(), (imageWidth - sliceRight).toInt(), imageHeight.toInt())
        val srcBottomRight = IntRect((imageWidth - sliceRight).toInt(), (imageHeight - sliceBottom).toInt(), imageWidth.toInt(), imageHeight.toInt())

        // Destination rectangles — the 9-slice grid of the OUTSET-EXPANDED
        // dest rect (§5.4: "the [border-image] widths ... are measured from
        // the border image area's boundary", i.e. the border bands hug the
        // expanded rect's edges, not the border box's).
        val dstTopLeft = IntRect(destLeft, destTop, destLeft + borderLeft.toInt(), destTop + borderTop.toInt())
        val dstTopCenter = IntRect(destLeft + borderLeft.toInt(), destTop, destRight - borderRight.toInt(), destTop + borderTop.toInt())
        val dstTopRight = IntRect(destRight - borderRight.toInt(), destTop, destRight, destTop + borderTop.toInt())

        val dstMiddleLeft = IntRect(destLeft, destTop + borderTop.toInt(), destLeft + borderLeft.toInt(), destBottom - borderBottom.toInt())
        val dstMiddleCenter = IntRect(destLeft + borderLeft.toInt(), destTop + borderTop.toInt(), destRight - borderRight.toInt(), destBottom - borderBottom.toInt())
        val dstMiddleRight = IntRect(destRight - borderRight.toInt(), destTop + borderTop.toInt(), destRight, destBottom - borderBottom.toInt())

        val dstBottomLeft = IntRect(destLeft, destBottom - borderBottom.toInt(), destLeft + borderLeft.toInt(), destBottom)
        val dstBottomCenter = IntRect(destLeft + borderLeft.toInt(), destBottom - borderBottom.toInt(), destRight - borderRight.toInt(), destBottom)
        val dstBottomRight = IntRect(destRight - borderRight.toInt(), destBottom - borderBottom.toInt(), destRight, destBottom)

        // Draw corners (no scaling needed for repeat, just stretch)
        drawImageRect(bitmap, srcTopLeft, dstTopLeft)
        drawImageRect(bitmap, srcTopRight, dstTopRight)
        drawImageRect(bitmap, srcBottomLeft, dstBottomLeft)
        drawImageRect(bitmap, srcBottomRight, dstBottomRight)

        // Draw edges based on repeat mode
        drawEdge(bitmap, srcTopCenter, dstTopCenter, config.repeatHorizontal, isHorizontal = true)
        drawEdge(bitmap, srcBottomCenter, dstBottomCenter, config.repeatHorizontal, isHorizontal = true)
        drawEdge(bitmap, srcMiddleLeft, dstMiddleLeft, config.repeatVertical, isHorizontal = false)
        drawEdge(bitmap, srcMiddleRight, dstMiddleRight, config.repeatVertical, isHorizontal = false)

        // Draw center if fill is enabled
        if (config.sliceFill) {
            drawImageRect(bitmap, srcMiddleCenter, dstMiddleCenter)
        }
    }

    /**
     * Helper function to draw an image from source rect to destination rect.
     */
    private fun DrawScope.drawImageRect(
        bitmap: ImageBitmap,
        srcRect: IntRect,
        dstRect: IntRect
    ) {
        drawImage(
            image = bitmap,
            srcOffset = IntOffset(srcRect.left, srcRect.top),
            srcSize = IntSize(srcRect.width, srcRect.height),
            dstOffset = IntOffset(dstRect.left, dstRect.top),
            dstSize = IntSize(dstRect.width, dstRect.height)
        )
    }

    /**
     * Draw an edge region with the specified repeat mode.
     */
    private fun DrawScope.drawEdge(
        bitmap: ImageBitmap,
        srcRect: IntRect,
        dstRect: IntRect,
        repeatMode: BorderImageRepeatValue,
        isHorizontal: Boolean
    ) {
        when (repeatMode) {
            BorderImageRepeatValue.STRETCH -> {
                // Simple stretch
                drawImageRect(bitmap, srcRect, dstRect)
            }
            BorderImageRepeatValue.REPEAT -> {
                // Tile the image
                drawRepeatedEdge(bitmap, srcRect, dstRect, isHorizontal, scaled = false)
            }
            BorderImageRepeatValue.ROUND -> {
                // Tile with scaling to fit evenly
                drawRepeatedEdge(bitmap, srcRect, dstRect, isHorizontal, scaled = true)
            }
            BorderImageRepeatValue.SPACE -> {
                // Tile with spacing (similar to round but with gaps)
                drawSpacedEdge(bitmap, srcRect, dstRect, isHorizontal)
            }
        }
    }

    /**
     * Draw repeated tiles along an edge.
     */
    private fun DrawScope.drawRepeatedEdge(
        bitmap: ImageBitmap,
        srcRect: IntRect,
        dstRect: IntRect,
        isHorizontal: Boolean,
        scaled: Boolean
    ) {
        val srcWidth = srcRect.width
        val srcHeight = srcRect.height
        val dstWidth = dstRect.width
        val dstHeight = dstRect.height

        if (isHorizontal) {
            val tileWidth = if (scaled) {
                val count = kotlin.math.max(1, kotlin.math.round(dstWidth.toFloat() / srcWidth).toInt())
                dstWidth / count
            } else {
                srcWidth
            }

            var x = dstRect.left
            while (x < dstRect.right) {
                val width = minOf(tileWidth, dstRect.right - x)
                drawImageRect(
                    bitmap,
                    srcRect,
                    IntRect(x, dstRect.top, x + width, dstRect.bottom)
                )
                x += tileWidth
            }
        } else {
            val tileHeight = if (scaled) {
                val count = kotlin.math.max(1, kotlin.math.round(dstHeight.toFloat() / srcHeight).toInt())
                dstHeight / count
            } else {
                srcHeight
            }

            var y = dstRect.top
            while (y < dstRect.bottom) {
                val height = minOf(tileHeight, dstRect.bottom - y)
                drawImageRect(
                    bitmap,
                    srcRect,
                    IntRect(dstRect.left, y, dstRect.right, y + height)
                )
                y += tileHeight
            }
        }
    }

    /**
     * Draw tiles with spacing between them.
     */
    private fun DrawScope.drawSpacedEdge(
        bitmap: ImageBitmap,
        srcRect: IntRect,
        dstRect: IntRect,
        isHorizontal: Boolean
    ) {
        val srcWidth = srcRect.width
        val srcHeight = srcRect.height
        val dstWidth = dstRect.width
        val dstHeight = dstRect.height

        if (isHorizontal) {
            val count = kotlin.math.max(1, dstWidth / srcWidth)
            if (count == 1) {
                drawImageRect(bitmap, srcRect, dstRect)
                return
            }
            val spacing = (dstWidth - count * srcWidth) / (count - 1)

            var x = dstRect.left
            repeat(count) {
                drawImageRect(
                    bitmap,
                    srcRect,
                    IntRect(x, dstRect.top, x + srcWidth, dstRect.bottom)
                )
                x += srcWidth + spacing
            }
        } else {
            val count = kotlin.math.max(1, dstHeight / srcHeight)
            if (count == 1) {
                drawImageRect(bitmap, srcRect, dstRect)
                return
            }
            val spacing = (dstHeight - count * srcHeight) / (count - 1)

            var y = dstRect.top
            repeat(count) {
                drawImageRect(
                    bitmap,
                    srcRect,
                    IntRect(dstRect.left, y, dstRect.right, y + srcHeight)
                )
                y += srcHeight + spacing
            }
        }
    }

    /**
     * Load an image from a URL with caching.
     */
    private suspend fun loadImageFromUrl(urlString: String): ImageBitmap? {
        // Use ImageCache for cached loading
        return ImageCache.loadImage(urlString)
    }

    /**
     * Load an image from a URL without caching (legacy fallback).
     */
    private suspend fun loadImageFromUrlDirect(urlString: String): ImageBitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(urlString)
                val inputStream = url.openStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                bitmap?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    // ==================== GRADIENT RENDERING ====================

    /**
     * Render a CSS gradient string to a bitmap.
     *
     * Supports:
     * - linear-gradient(angle, colors...)
     * - radial-gradient(colors...)
     * - conic-gradient(colors...)
     * - repeating-linear-gradient, repeating-radial-gradient
     *
     * @param gradientString The CSS gradient string
     * @param width Bitmap width
     * @param height Bitmap height
     * @return ImageBitmap with the rendered gradient
     */
    private suspend fun renderGradientToBitmap(
        gradientString: String,
        width: Int,
        height: Int
    ): ImageBitmap? = withContext(Dispatchers.Default) {
        try {
            val parsed = parseGradient(gradientString)
            if (parsed == null) return@withContext null

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            val shader = when (parsed) {
                is ParsedGradient.Linear -> createLinearGradientShader(parsed, width.toFloat(), height.toFloat())
                is ParsedGradient.Radial -> createRadialGradientShader(parsed, width.toFloat(), height.toFloat())
                is ParsedGradient.Conic -> createConicGradientShader(parsed, width.toFloat(), height.toFloat())
            }

            paint.shader = shader
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

            bitmap.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parsed gradient representation.
     */
    private sealed interface ParsedGradient {
        data class Linear(
            val angle: Float, // degrees
            val colors: IntArray,
            val positions: FloatArray?,
            val repeating: Boolean
        ) : ParsedGradient

        data class Radial(
            val centerX: Float, // 0-1
            val centerY: Float, // 0-1
            val colors: IntArray,
            val positions: FloatArray?,
            val repeating: Boolean
        ) : ParsedGradient

        data class Conic(
            val centerX: Float, // 0-1
            val centerY: Float, // 0-1
            val startAngle: Float, // degrees
            val colors: IntArray,
            val positions: FloatArray?,
            val repeating: Boolean
        ) : ParsedGradient
    }

    /**
     * Parse a CSS gradient string.
     */
    private fun parseGradient(gradientString: String): ParsedGradient? {
        val normalized = gradientString.trim().lowercase()

        return when {
            normalized.startsWith("linear-gradient") ||
            normalized.startsWith("repeating-linear-gradient") -> {
                parseLinearGradient(gradientString, normalized.startsWith("repeating"))
            }
            normalized.startsWith("radial-gradient") ||
            normalized.startsWith("repeating-radial-gradient") -> {
                parseRadialGradient(gradientString, normalized.startsWith("repeating"))
            }
            normalized.startsWith("conic-gradient") ||
            normalized.startsWith("repeating-conic-gradient") -> {
                parseConicGradient(gradientString, normalized.startsWith("repeating"))
            }
            else -> null
        }
    }

    /**
     * Parse linear-gradient CSS function.
     */
    private fun parseLinearGradient(gradientString: String, repeating: Boolean): ParsedGradient.Linear? {
        val content = extractGradientContent(gradientString) ?: return null
        val parts = splitGradientParts(content)
        if (parts.isEmpty()) return null

        var angle = 180f // Default: to bottom
        var colorStartIndex = 0

        // Check if first part is an angle or direction
        val firstPart = parts[0].trim()
        if (firstPart.endsWith("deg")) {
            angle = firstPart.dropLast(3).toFloatOrNull() ?: 180f
            colorStartIndex = 1
        } else if (firstPart.startsWith("to ")) {
            angle = parseDirection(firstPart)
            colorStartIndex = 1
        }

        val colorStops = parseColorStops(parts.subList(colorStartIndex, parts.size))
        if (colorStops.isEmpty()) return null

        val colors = colorStops.map { it.first }.toIntArray()
        val positions = colorStops.map { it.second }.takeIf { it.all { pos -> pos != null } }
            ?.map { it!! }?.toFloatArray()

        return ParsedGradient.Linear(angle, colors, positions, repeating)
    }

    /**
     * Parse radial-gradient CSS function.
     */
    private fun parseRadialGradient(gradientString: String, repeating: Boolean): ParsedGradient.Radial? {
        val content = extractGradientContent(gradientString) ?: return null
        val parts = splitGradientParts(content)
        if (parts.isEmpty()) return null

        var centerX = 0.5f
        var centerY = 0.5f
        var colorStartIndex = 0

        // Check for position/shape specification
        val firstPart = parts[0].trim()
        if (firstPart.contains("at ")) {
            val posStr = firstPart.substringAfter("at ").trim()
            val (x, y) = parsePosition(posStr)
            centerX = x
            centerY = y
            colorStartIndex = 1
        }

        val colorStops = parseColorStops(parts.subList(colorStartIndex, parts.size))
        if (colorStops.isEmpty()) return null

        val colors = colorStops.map { it.first }.toIntArray()
        val positions = colorStops.map { it.second }.takeIf { it.all { pos -> pos != null } }
            ?.map { it!! }?.toFloatArray()

        return ParsedGradient.Radial(centerX, centerY, colors, positions, repeating)
    }

    /**
     * Parse conic-gradient CSS function.
     */
    private fun parseConicGradient(gradientString: String, repeating: Boolean): ParsedGradient.Conic? {
        val content = extractGradientContent(gradientString) ?: return null
        val parts = splitGradientParts(content)
        if (parts.isEmpty()) return null

        var centerX = 0.5f
        var centerY = 0.5f
        var startAngle = 0f
        var colorStartIndex = 0

        // Check for "from X at Y" specification
        val firstPart = parts[0].trim()
        if (firstPart.startsWith("from ")) {
            val fromStr = firstPart.substringAfter("from ").trim()
            if (fromStr.contains("at ")) {
                startAngle = fromStr.substringBefore("at ").trim().replace("deg", "").toFloatOrNull() ?: 0f
                val posStr = fromStr.substringAfter("at ").trim()
                val (x, y) = parsePosition(posStr)
                centerX = x
                centerY = y
            } else {
                startAngle = fromStr.replace("deg", "").toFloatOrNull() ?: 0f
            }
            colorStartIndex = 1
        } else if (firstPart.contains("at ")) {
            val posStr = firstPart.substringAfter("at ").trim()
            val (x, y) = parsePosition(posStr)
            centerX = x
            centerY = y
            colorStartIndex = 1
        }

        val colorStops = parseColorStops(parts.subList(colorStartIndex, parts.size))
        if (colorStops.isEmpty()) return null

        val colors = colorStops.map { it.first }.toIntArray()
        val positions = colorStops.map { it.second }.takeIf { it.all { pos -> pos != null } }
            ?.map { it!! }?.toFloatArray()

        return ParsedGradient.Conic(centerX, centerY, startAngle, colors, positions, repeating)
    }

    /**
     * Extract content from gradient function (everything inside parentheses).
     */
    private fun extractGradientContent(gradientString: String): String? {
        val start = gradientString.indexOf('(')
        val end = gradientString.lastIndexOf(')')
        if (start == -1 || end == -1 || end <= start) return null
        return gradientString.substring(start + 1, end)
    }

    /**
     * Split gradient parts by comma, respecting nested parentheses.
     */
    private fun splitGradientParts(content: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var current = StringBuilder()

        for (char in content) {
            when (char) {
                '(' -> {
                    depth++
                    current.append(char)
                }
                ')' -> {
                    depth--
                    current.append(char)
                }
                ',' -> {
                    if (depth == 0) {
                        parts.add(current.toString().trim())
                        current = StringBuilder()
                    } else {
                        current.append(char)
                    }
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) {
            parts.add(current.toString().trim())
        }
        return parts
    }

    /**
     * Parse direction keyword to angle.
     */
    private fun parseDirection(direction: String): Float {
        return when (direction.lowercase().trim()) {
            "to top" -> 0f
            "to top right", "to right top" -> 45f
            "to right" -> 90f
            "to bottom right", "to right bottom" -> 135f
            "to bottom" -> 180f
            "to bottom left", "to left bottom" -> 225f
            "to left" -> 270f
            "to top left", "to left top" -> 315f
            else -> 180f
        }
    }

    /**
     * Parse position keywords to fractions.
     */
    private fun parsePosition(posStr: String): Pair<Float, Float> {
        val parts = posStr.split(" ").map { it.trim().lowercase() }
        var x = 0.5f
        var y = 0.5f

        for (part in parts) {
            when (part) {
                "left" -> x = 0f
                "center" -> { /* already default */ }
                "right" -> x = 1f
                "top" -> y = 0f
                "bottom" -> y = 1f
            }
            if (part.endsWith("%")) {
                val value = part.dropLast(1).toFloatOrNull()?.div(100f)
                if (value != null) {
                    if (parts.indexOf(part) == 0) x = value else y = value
                }
            }
        }
        return x to y
    }

    /**
     * Parse color stops.
     * Returns list of (color ARGB int, position 0-1 or null).
     */
    private fun parseColorStops(parts: List<String>): List<Pair<Int, Float?>> {
        if (parts.isEmpty()) return emptyList()

        return parts.mapNotNull { part ->
            val colorAndPos = parseColorStop(part.trim())
            colorAndPos
        }.ifEmpty {
            // Fallback: black to white
            listOf(
                android.graphics.Color.BLACK to 0f,
                android.graphics.Color.WHITE to 1f
            )
        }
    }

    /**
     * Parse a single color stop (e.g., "red 50%", "#fff", "rgb(255,0,0) 25%").
     */
    private fun parseColorStop(part: String): Pair<Int, Float?>? {
        val trimmed = part.trim()
        if (trimmed.isEmpty()) return null

        // Check for position at end
        val posMatch = Regex("""(.+?)\s+(\d+(?:\.\d+)?%?)$""").find(trimmed)

        val colorStr: String
        val position: Float?

        if (posMatch != null) {
            colorStr = posMatch.groupValues[1].trim()
            val posStr = posMatch.groupValues[2]
            position = if (posStr.endsWith("%")) {
                posStr.dropLast(1).toFloatOrNull()?.div(100f)
            } else {
                posStr.toFloatOrNull()
            }
        } else {
            colorStr = trimmed
            position = null
        }

        val color = parseColor(colorStr) ?: return null
        return color to position
    }

    /**
     * Parse a CSS color string to ARGB int.
     */
    private fun parseColor(colorStr: String): Int? {
        val trimmed = colorStr.trim().lowercase()

        // Named colors
        NAMED_COLORS[trimmed]?.let { return it }

        // Hex colors
        if (trimmed.startsWith("#")) {
            return try {
                android.graphics.Color.parseColor(trimmed)
            } catch (e: Exception) {
                null
            }
        }

        // rgb/rgba
        if (trimmed.startsWith("rgb")) {
            val match = Regex("""rgba?\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*([\d.]+))?\s*\)""").find(trimmed)
            if (match != null) {
                val r = match.groupValues[1].toIntOrNull() ?: 0
                val g = match.groupValues[2].toIntOrNull() ?: 0
                val b = match.groupValues[3].toIntOrNull() ?: 0
                val a = match.groupValues.getOrNull(4)?.toFloatOrNull() ?: 1f
                return android.graphics.Color.argb((a * 255).toInt(), r, g, b)
            }
        }

        return null
    }

    /**
     * Create LinearGradient shader from parsed data.
     */
    private fun createLinearGradientShader(
        gradient: ParsedGradient.Linear,
        width: Float,
        height: Float
    ): LinearGradient {
        val angleRad = (gradient.angle - 90) * PI.toFloat() / 180f
        val centerX = width / 2
        val centerY = height / 2
        val diag = kotlin.math.sqrt(width * width + height * height) / 2

        val startX = centerX - cos(angleRad) * diag
        val startY = centerY - sin(angleRad) * diag
        val endX = centerX + cos(angleRad) * diag
        val endY = centerY + sin(angleRad) * diag

        return if (gradient.positions != null) {
            LinearGradient(
                startX, startY, endX, endY,
                gradient.colors, gradient.positions,
                if (gradient.repeating) Shader.TileMode.REPEAT else Shader.TileMode.CLAMP
            )
        } else {
            LinearGradient(
                startX, startY, endX, endY,
                gradient.colors, null,
                if (gradient.repeating) Shader.TileMode.REPEAT else Shader.TileMode.CLAMP
            )
        }
    }

    /**
     * Create RadialGradient shader from parsed data.
     */
    private fun createRadialGradientShader(
        gradient: ParsedGradient.Radial,
        width: Float,
        height: Float
    ): RadialGradient {
        val centerX = gradient.centerX * width
        val centerY = gradient.centerY * height
        val radius = kotlin.math.max(width, height) / 2

        return if (gradient.positions != null) {
            RadialGradient(
                centerX, centerY, radius,
                gradient.colors, gradient.positions,
                if (gradient.repeating) Shader.TileMode.REPEAT else Shader.TileMode.CLAMP
            )
        } else {
            RadialGradient(
                centerX, centerY, radius,
                gradient.colors, null,
                if (gradient.repeating) Shader.TileMode.REPEAT else Shader.TileMode.CLAMP
            )
        }
    }

    /**
     * Create SweepGradient shader from parsed data.
     */
    private fun createConicGradientShader(
        gradient: ParsedGradient.Conic,
        width: Float,
        height: Float
    ): SweepGradient {
        val centerX = gradient.centerX * width
        val centerY = gradient.centerY * height

        // Note: SweepGradient starts at 3 o'clock position (0°)
        // CSS conic-gradient starts at 12 o'clock (top)
        // We need to rotate the colors array to account for this
        return if (gradient.positions != null) {
            SweepGradient(centerX, centerY, gradient.colors, gradient.positions)
        } else {
            SweepGradient(centerX, centerY, gradient.colors, null)
        }
    }

    /**
     * Common named colors.
     */
    private val NAMED_COLORS = mapOf(
        "black" to android.graphics.Color.BLACK,
        "white" to android.graphics.Color.WHITE,
        "red" to android.graphics.Color.RED,
        "green" to android.graphics.Color.GREEN,
        "blue" to android.graphics.Color.BLUE,
        "yellow" to android.graphics.Color.YELLOW,
        "cyan" to android.graphics.Color.CYAN,
        "magenta" to android.graphics.Color.MAGENTA,
        "gray" to android.graphics.Color.GRAY,
        "grey" to android.graphics.Color.GRAY,
        "orange" to 0xFFFFA500.toInt(),
        "purple" to 0xFF800080.toInt(),
        "pink" to 0xFFFFC0CB.toInt(),
        "transparent" to android.graphics.Color.TRANSPARENT
    )

    /**
     * Convert BorderImageSliceEdge to pixels.
     */
    private fun BorderImageSliceEdge?.toPixels(dimension: Float): Float {
        if (this == null) return 0f
        return if (isPercentage) {
            (value / 100f) * dimension
        } else {
            value
        }
    }

    /**
     * Resolve one border-image-width value per css-backgrounds-3 §5.3.
     *
     * @param computedBorder the side's COMPUTED border-width (0 when the
     *        side has no border-style) — the basis for `<number>` values
     *        and for the initial value `1`.
     * @param slice the side's border-image-slice, used for `auto` (§5.3:
     *        auto = the intrinsic size of the corresponding slice; for our
     *        gradient/bitmap sources the slice's px value is that size).
     */
    internal fun BorderImageDimension?.resolve(
        computedBorder: Dp,
        slice: BorderImageSliceEdge?
    ): Dp {
        return when (this) {
            // Initial value is the NUMBER 1 → 1 × computed border-width.
            null -> computedBorder
            // auto → slice size when it's an absolute px count; percentage
            // slices refer to the source image, whose px size we don't know
            // here, so fall back to the computed border width.
            BorderImageDimension.Auto ->
                if (slice != null && !slice.isPercentage) slice.value.dp else computedBorder
            is BorderImageDimension.Length -> value
            // TODO(spec §5.3): percentages refer to the border image AREA
            // dimension (border box width/height), which is only known at
            // draw time. Approximated against the computed border width —
            // logged as an honest gap rather than silently wrong: the old
            // code multiplied an arbitrary 8dp constant instead.
            is BorderImageDimension.Percentage -> computedBorder * (value / 100f)
            // <number> = multiple of the computed border-width.
            is BorderImageDimension.Number -> computedBorder * value
        }
    }

    /**
     * Resolve one border-image-outset value per css-backgrounds-3 §5.4:
     * lengths are literal, numbers multiply the computed border-width,
     * initial value 0.
     */
    internal fun BorderImageDimension?.resolveOutset(computedBorder: Dp): Dp {
        return when (this) {
            null -> 0.dp
            BorderImageDimension.Auto -> 0.dp // not a valid outset value
            is BorderImageDimension.Length -> value
            is BorderImageDimension.Percentage -> 0.dp // not valid for outset
            is BorderImageDimension.Number -> computedBorder * value
        }
    }

    /**
     * Apply border image as a modifier.
     *
     * Note: This requires the image to be pre-loaded.
     *
     * @param config Border image configuration
     * @param bitmap Pre-loaded image bitmap
     * @param fallbackBorderWidth Fallback border width
     * @return Modifier with border image drawing
     */
    fun applyBorderImage(
        modifier: Modifier,
        config: BorderImageConfig,
        bitmap: ImageBitmap?,
        fallbackBorderWidth: Dp = 8.dp
    ): Modifier {
        if (!config.hasBorderImage || bitmap == null) {
            return modifier
        }

        // Same §5.3 resolution as BorderImageBox — number/initial values
        // multiply the computed border-width, so a border-less element
        // draws nothing here either.
        val borderTop = config.widthTop.resolve(config.computedBorderTop, config.sliceTop)
        val borderRight = config.widthRight.resolve(config.computedBorderRight, config.sliceRight)
        val borderBottom = config.widthBottom.resolve(config.computedBorderBottom, config.sliceBottom)
        val borderLeft = config.widthLeft.resolve(config.computedBorderLeft, config.sliceLeft)
        if (borderTop <= 0.dp && borderRight <= 0.dp &&
            borderBottom <= 0.dp && borderLeft <= 0.dp
        ) {
            return modifier
        }

        return modifier.drawBehind {
            drawBorderImage(
                bitmap = bitmap,
                config = config,
                borderTop = borderTop.toPx(),
                borderRight = borderRight.toPx(),
                borderBottom = borderBottom.toPx(),
                borderLeft = borderLeft.toPx(),
                // §5.4 outward expansion by the outset ONLY — unlike
                // BorderImageBox this variant attaches to an arbitrary
                // caller-supplied modifier position, so it cannot assume a
                // border/padding inset sits before it in the chain and adds
                // no destExpansion compensation. (No current call sites;
                // ComponentRenderer routes through BorderImageBox.)
                outsetTop = config.outsetTop.resolveOutset(config.computedBorderTop).toPx(),
                outsetRight = config.outsetRight.resolveOutset(config.computedBorderRight).toPx(),
                outsetBottom = config.outsetBottom.resolveOutset(config.computedBorderBottom).toPx(),
                outsetLeft = config.outsetLeft.resolveOutset(config.computedBorderLeft).toPx()
            )
        }
    }

    /**
     * CSS border-image property notes.
     */
    object Notes {
        // Retro P2e (finding A6#15): this constant said "Gradient border
        // images are not yet implemented. They would require parsing the
        // gradient string and creating a shader-based image." Both halves
        // are now history — that is exactly what `parseGradient` +
        // `renderGradientToBitmap` do (android.graphics Linear/Radial/Sweep
        // shaders drawn into a 256x256 bitmap, memoised by
        // rememberCachedGradient). The note is kept as a POINTER rather than
        // deleted because nothing reads these constants programmatically and
        // a reader arriving here should land on the implementation.
        const val GRADIENT_SUPPORT = """
            Gradient border images ARE implemented: parseGradient parses the
            CSS gradient string (linear / radial / conic, incl. repeating)
            and renderGradientToBitmap rasterises it through an
            android.graphics shader; rememberCachedGradient memoises the
            256x256 result. Unparseable syntax yields a null bitmap and the
            border image is not painted.
        """

        const val PERFORMANCE = """
            Border images are drawn every frame using drawBehind.
            For static borders, consider caching the result or
            using a custom CompositionLocal.
        """

        const val NETWORK_IMAGES = """
            Network images are loaded asynchronously.
            The border will not appear until the image loads.
            Consider adding a loading state or placeholder.
        """
    }
}

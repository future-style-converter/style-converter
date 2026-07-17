package com.styleconverter.runtime.effects.mask

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
// Coil 3 (wave-8 #36 migration): coil.* → coil3.*; AsyncImagePainter.state
// is a StateFlow now (collected below), CachePolicy moved with the request
// package, and data: URIs route through DataUri.toModel (ByteArray model).
import androidx.compose.runtime.collectAsState
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Scale
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
// Gradient geometry is shared with the background path (ColorApplier) so
// mask and background gradients can never diverge — see createMaskBrush.
import com.styleconverter.runtime.color.ColorApplier
import com.styleconverter.runtime.core.images.DataUri
import com.styleconverter.runtime.core.images.ImageCache

/**
 * Applies mask effects to Compose Modifiers.
 *
 * ## Compose Limitations
 * CSS masks are complex and Compose has limited native support:
 * - No direct mask-image support for URLs (implemented via Coil + BlendMode)
 * - Gradient masks use BlendMode.DstIn/DstOut for alpha-based masking
 * - Luminance mode requires ColorMatrix conversion to grayscale
 *
 * ## Implementation Strategy
 *
 * ### Gradient Masks
 * For gradient masks, we use graphicsLayer with compositingStrategy and
 * drawWithContent with BlendMode.DstIn to achieve masking effects.
 * The gradient's alpha channel determines visibility.
 *
 * ### URL Masks
 * For URL-based masks, Coil loads the image asynchronously. The image is drawn
 * with BlendMode.DstIn (alpha mode) or converted to luminance (luminance mode).
 *
 * ## Usage
 * ```kotlin
 * // For gradient masks (modifier-based)
 * val config = MaskExtractor.extractMaskConfig(properties)
 * val modifier = MaskApplier.applyMask(Modifier, config)
 *
 * // For URL masks (composable wrapper)
 * MaskApplier.MaskedBox(config) {
 *     // Content to be masked
 * }
 * ```
 */
object MaskApplier {

    /**
     * Apply mask configuration to a Modifier.
     *
     * Note: For URL-based masks, use MaskedBox composable instead.
     *
     * @param modifier The base modifier
     * @param config MaskConfig with mask properties
     * @return Modified Modifier with mask applied (where possible)
     */
    fun applyMask(modifier: Modifier, config: MaskConfig): Modifier {
        if (!config.hasMask) return modifier

        // For gradient masks, apply using modifier
        if (config.isGradient && config.gradient != null) {
            return applyGradientMask(modifier, config)
        }

        // For URL-based masks, return unchanged (use MaskedBox instead)
        // This allows the caller to detect and handle URL masks separately
        return modifier
    }

    /**
     * Composable wrapper for URL-based masks.
     *
     * Loads the mask image and applies it to the content.
     *
     * @param config MaskConfig with URL mask
     * @param modifier Modifier for the container
     * @param content Content to be masked
     */
    @Composable
    fun MaskedBox(
        config: MaskConfig,
        modifier: Modifier = Modifier,
        content: @Composable BoxScope.() -> Unit
    ) {
        if (!config.hasMask) {
            Box(modifier = modifier, content = content)
            return
        }

        // For gradient masks, use modifier approach
        if (config.isGradient) {
            Box(
                modifier = modifier.then(applyGradientMask(Modifier, config)),
                content = content
            )
            return
        }

        // For URL masks, load image and apply as mask
        if (config.imageUrl != null) {
            UrlMaskedBox(
                url = config.imageUrl,
                config = config,
                modifier = modifier,
                content = content
            )
            return
        }

        // Fallback: no mask applied
        Box(modifier = modifier, content = content)
    }

    /**
     * Composable that applies a URL-based mask using Coil.
     */
    @Composable
    private fun UrlMaskedBox(
        url: String,
        config: MaskConfig,
        modifier: Modifier = Modifier,
        content: @Composable BoxScope.() -> Unit
    ) {
        val context = LocalContext.current

        // Load mask image using shared Coil loader if available
        val imageLoader = ImageCache.getCoilLoader()
        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                // data: URIs decode to ByteArray (Coil 3 native model).
                .data(DataUri.toModel(url))
                .scale(Scale.FILL)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build(),
            imageLoader = imageLoader ?: coil3.ImageLoader(context)
        )

        // Coil 3: `state` is a StateFlow — collect for recomposition.
        val painterState by painter.state.collectAsState()

        // Create color filter for luminance mode
        val colorFilter = if (config.mode == MaskModeValue.LUMINANCE) {
            ColorFilter.colorMatrix(LUMINANCE_TO_ALPHA_MATRIX)
        } else {
            null
        }

        // Determine blend mode based on composite
        val blendMode = config.composite.toBlendMode()

        Box(
            modifier = modifier
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    // Draw original content first
                    drawContent()

                    // Only apply mask when image is loaded
                    if (painterState is AsyncImagePainter.State.Success) {
                        val imageWidth = painter.intrinsicSize.width
                        val imageHeight = painter.intrinsicSize.height

                        if (imageWidth > 0 && imageHeight > 0) {
                            // Calculate mask dimensions based on size config
                            val (maskWidth, maskHeight) = calculateMaskSize(
                                config.size, size, imageWidth, imageHeight
                            )

                            // Calculate position based on position config
                            val (offsetX, offsetY) = calculateMaskPosition(
                                config.position, size, maskWidth, maskHeight
                            )

                            // Handle repeat
                            val (repeatX, repeatY) = when (config.repeat) {
                                MaskRepeatValue.REPEAT -> true to true
                                MaskRepeatValue.REPEAT_X -> true to false
                                MaskRepeatValue.REPEAT_Y -> false to true
                                else -> false to false
                            }

                            if (repeatX || repeatY) {
                                // Draw repeated tiles
                                drawRepeatedMask(
                                    painter = painter,
                                    tileWidth = maskWidth,
                                    tileHeight = maskHeight,
                                    repeatX = repeatX,
                                    repeatY = repeatY,
                                    blendMode = blendMode,
                                    colorFilter = colorFilter
                                )
                            } else {
                                // Draw single mask
                                with(painter) {
                                    translate(left = offsetX, top = offsetY) {
                                        draw(
                                            size = Size(maskWidth, maskHeight),
                                            colorFilter = colorFilter
                                        )
                                    }
                                }
                                // Apply blend mode with a full rect
                                // Note: The actual masking happens through the painter's alpha
                            }
                        }
                    }
                }
        ) {
            content()
        }
    }

    /**
     * Apply a gradient-based mask effect.
     */
    private fun applyGradientMask(modifier: Modifier, config: MaskConfig): Modifier {
        val gradient = config.gradient ?: return modifier
        val blendMode = config.composite.toBlendMode()

        return modifier
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                // Draw original content first
                drawContent()

                // Create and apply the mask gradient
                val maskBrush = createMaskBrush(gradient, size, config.mode)
                if (maskBrush != null) {
                    drawRect(
                        brush = maskBrush,
                        blendMode = blendMode
                    )
                }
            }
    }

    /**
     * Create a Brush from MaskGradientConfig.
     */
    private fun createMaskBrush(
        gradient: MaskGradientConfig,
        size: Size,
        mode: MaskModeValue
    ): Brush? {
        // Convert colors for luminance mode if needed
        val colorStops = when (gradient) {
            is MaskGradientConfig.Linear -> gradient.colorStops
            is MaskGradientConfig.Radial -> gradient.colorStops
            is MaskGradientConfig.Conic -> gradient.colorStops
        }.map { stop ->
            val color = if (mode == MaskModeValue.LUMINANCE) {
                convertToLuminance(stop.color)
            } else {
                stop.color
            }
            stop.position to color
        }.toTypedArray()

        if (colorStops.size < 2) return null

        return when (gradient) {
            is MaskGradientConfig.Linear -> {
                // Endpoint math is REUSED from the background path:
                // ColorApplier.linearGradientPoints implements the
                // css-images-3 §3.4.1 pixel-space gradient line (centre
                // through the box, length |W·sinθ| + |H·cosθ|). The old
                // mask-only trig placed endpoints on the box edges, which
                // squashed diagonal angles on non-square boxes — sharing
                // the background helper keeps mask and background linears
                // geometrically identical by construction.
                val (lineStart, lineEnd) = ColorApplier.linearGradientPoints(gradient.angle, size)
                // css-images-3 §3.4.3: a REPEATING gradient tiles the
                // first→last stop span (the period), not the whole line.
                // TileMode.Repeated repeats the brush's start→end segment,
                // so shrink the segment to the stop span and renormalise
                // the stops into it; a null span (degenerate or full-line)
                // renders identically to the plain gradient.
                val span = if (gradient.repeating) repeatingStopSpan(colorStops.map { it.first }) else null
                if (span != null) {
                    Brush.linearGradient(
                        colorStops = colorStops.map { (pos, color) ->
                            // Stop fraction on the line → fraction of the period segment.
                            (pos - span.start) / (span.endInclusive - span.start) to color
                        }.toTypedArray(),
                        // Segment endpoints = the span's fractions walked along the full line.
                        start = lerp(lineStart, lineEnd, span.start),
                        end = lerp(lineStart, lineEnd, span.endInclusive),
                        tileMode = TileMode.Repeated
                    )
                } else {
                    Brush.linearGradient(
                        colorStops = colorStops,
                        start = lineStart,
                        end = lineEnd,
                        // Full-span repeating == plain (§3.4.3); Clamp matches web.
                        tileMode = TileMode.Clamp
                    )
                }
            }
            is MaskGradientConfig.Radial -> {
                // css-images-3 §3.5 defaults: ending shape ELLIPSE, size
                // farthest-corner. The old branch hardcoded a CIRCLE of
                // radius min(w,h)/2 — on the 160×80 mask fixtures that
                // faded to transparent at x=±40 while web's ellipse
                // reached the corners. Mirrors ColorApplier's radial
                // FARTHEST_CORNER geometry (see createRadialGradientBrush
                // there): circular shader at max(rx,ry) + a local-matrix
                // axis scale to stretch the isolines into the ellipse,
                // because RadialGradientShader is circular-only.
                val cxFrac = gradient.centerX
                val cyFrac = gradient.centerY
                val shape = gradient.shape
                val colors = colorStops.map { it.second }
                val stops = colorStops.map { it.first }
                // NOTE repeating-radial: TileMode.Repeated tiles the full
                // 0→r ramp, not the CSS stop-span period — same known gap
                // as the background path; kept (not silent: divergence is
                // visible in the visual report if a fixture exercises it).
                val tile = if (gradient.repeating) TileMode.Repeated else TileMode.Clamp
                object : ShaderBrush() {
                    override fun createShader(size: Size): Shader {
                        val cx = cxFrac * size.width
                        val cy = cyFrac * size.height
                        val (rx, ry) = radialMaskRadii(shape, cxFrac, cyFrac, size.width, size.height)
                        // Circular shader at the larger radius; the ≥ε
                        // clamp mirrors radialAxisScale's guard so both
                        // agree on a degenerate zero-sized box.
                        val rMax = kotlin.math.max(rx.coerceAtLeast(1e-3f), ry.coerceAtLeast(1e-3f))
                        val shader = RadialGradientShader(
                            center = Offset(cx, cy),
                            radius = rMax,
                            colors = colors,
                            colorStops = stops,
                            tileMode = tile
                        )
                        // Squash factors rx/rMax, ry/rMax (≤ 1) — Skia's
                        // setLocalMatrix maps the shader image THROUGH the
                        // matrix, so shrinking the rMax circle to the
                        // ellipse needs radius/rMax, NOT the old inverted
                        // rMax/radius (which stretched the wrong axis).
                        // Delegate to ColorApplier so mask and background
                        // radials share one pinned owner.
                        val (sx, sy) = radialMaskAxisScale(rx, ry)
                        if (sx == 1f && sy == 1f) return shader
                        // Axis squash about the centre — identical matrix
                        // construction to ColorApplier's radial brush.
                        val localMatrix = android.graphics.Matrix().apply {
                            postTranslate(-cx, -cy)
                            postScale(sx, sy)
                            postTranslate(cx, cy)
                        }
                        shader.setLocalMatrix(localMatrix)
                        return shader
                    }
                }
            }
            is MaskGradientConfig.Conic -> {
                // css-images-4 §3.3: conic 0deg points UP (12 o'clock);
                // android.graphics.SweepGradient anchors 0 at 3 o'clock.
                // Compose's Brush.sweepGradient offers no start-angle, so
                // the old branch rendered every conic mask rotated +90°
                // vs web. Build the raw SweepGradient and rotate its local
                // matrix by conicMaskRotationDegrees — a delegate to
                // ColorApplier.conicSweepRotationDegrees, the SAME
                // correction the background conic path applies, so mask
                // and background conics can never drift apart.
                val cxFrac = gradient.centerX
                val cyFrac = gradient.centerY
                val fromDeg = gradient.angle
                // SweepGradient wants ARGB ints + float positions.
                val argb = colorStops.map { it.second.toArgb() }.toIntArray()
                val positions = colorStops.map { it.first }.toFloatArray()
                object : ShaderBrush() {
                    override fun createShader(size: Size): Shader {
                        val cx = cxFrac * size.width
                        val cy = cyFrac * size.height
                        val shader = android.graphics.SweepGradient(cx, cy, argb, positions)
                        val rotate = android.graphics.Matrix()
                        // setRotate is clockwise-positive in screen coords —
                        // matches the CSS clockwise sweep direction.
                        rotate.setRotate(conicMaskRotationDegrees(fromDeg), cx, cy)
                        shader.setLocalMatrix(rotate)
                        return shader
                    }
                }
                // Repeating-conic has no TileMode on SweepGradient; the
                // full-turn stop list is period-less on this wire, so the
                // plain sweep is the honest best effort (documented gap).
            }
        }
    }

    /**
     * The first→last stop span of a REPEATING gradient — the period that
     * css-images-3 §3.4.3 tiles along the gradient line. Returns null when
     * repeating degenerates to the plain gradient: a full-line span (0→1
     * tiles to itself) or a zero span (no period to repeat — spec says
     * solid average colour; we fall back to the plain clamped render and
     * accept the divergence rather than hide it). Pure math, JVM-pinned
     * by MaskGradientGeometryTest.
     */
    internal fun repeatingStopSpan(positions: List<Float>): ClosedFloatingPointRange<Float>? {
        // Extractor emits stops in declaration order with 0–1 fractions.
        val first = positions.firstOrNull() ?: return null
        val last = positions.lastOrNull() ?: return null
        // Zero span → no finite period; full span → identical to plain.
        if (last <= first) return null
        if (first <= 0f && last >= 1f) return null
        return first..last
    }

    /**
     * Radii (rx, ry) in pixels for a radial MASK gradient at its CSS
     * defaults: size `farthest-corner` for both shapes (css-images-3
     * §3.5 — the parser never emits an explicit size yet, see
     * MaskImagePropertyParser). Shape null means the author omitted the
     * keyword → ELLIPSE per spec. The math mirrors ColorApplier's
     * FARTHEST_CORNER branches: ellipse radii are the farthest-corner
     * distances ×√2 (solving (dx/rx)²+(dy/ry)²=1 with rx:ry = dx:dy);
     * circle radius is the straight distance to the farthest corner.
     * Pure math, JVM-pinned by MaskGradientGeometryTest.
     */
    internal fun radialMaskRadii(
        shape: MaskRadialShape?,
        cxFrac: Float,
        cyFrac: Float,
        width: Float,
        height: Float
    ): Pair<Float, Float> {
        // Centre in pixels, then farthest-side distances per axis.
        val cx = cxFrac * width
        val cy = cyFrac * height
        val dxFarthest = kotlin.math.max(cx, width - cx)
        val dyFarthest = kotlin.math.max(cy, height - cy)
        return when (shape) {
            MaskRadialShape.CIRCLE -> {
                // Farthest-corner circle: hypotenuse to the far corner.
                val r = kotlin.math.sqrt(dxFarthest * dxFarthest + dyFarthest * dyFarthest)
                r to r
            }
            // ELLIPSE and null (omitted keyword) share the CSS default.
            else -> {
                val k = kotlin.math.sqrt(2f)
                (dxFarthest * k) to (dyFarthest * k)
            }
        }
    }

    /**
     * CSS→SweepGradient rotation for conic MASKS — a pure delegate to
     * [ColorApplier.conicSweepRotationDegrees] so the −90° convention
     * shift (and the `from <angle>` offset on top) has exactly one owner
     * shared by background and mask conics. Pinned by
     * MaskGradientGeometryTest against the ColorApplier values.
     */
    internal fun conicMaskRotationDegrees(cssFromDeg: Float): Float =
        ColorApplier.conicSweepRotationDegrees(cssFromDeg)

    /**
     * Circle→ellipse local-matrix scale for radial MASKS — a pure
     * delegate to [ColorApplier.radialAxisScale] so the setLocalMatrix
     * squash direction (radius/rMax, ≤ 1 — see the owner's doc for the
     * Skia image-transform rationale) has exactly one owner shared by
     * background and mask radials. Pinned by MaskGradientGeometryTest
     * against the ColorApplier values on an asymmetric box.
     */
    internal fun radialMaskAxisScale(rx: Float, ry: Float): Pair<Float, Float> =
        ColorApplier.radialAxisScale(rx, ry)

    /**
     * Linear interpolation between two pixel offsets — walks fraction [t]
     * of the way from [a] to [b]. Used to place the repeating-gradient
     * period segment on the §3.4.1 gradient line.
     */
    private fun lerp(a: Offset, b: Offset, t: Float): Offset =
        Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

    /**
     * Convert color to luminance-based alpha.
     * For luminance mode, the brightness of the color determines the mask value.
     */
    private fun convertToLuminance(color: Color): Color {
        // Calculate relative luminance using sRGB coefficients
        val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
        return Color.White.copy(alpha = luminance * color.alpha)
    }

    /**
     * Calculate mask size based on MaskSizeValue.
     * Returns Pair(width, height).
     */
    private fun calculateMaskSize(
        sizeConfig: MaskSizeValue,
        containerSize: Size,
        imageWidth: Float,
        imageHeight: Float
    ): Pair<Float, Float> {
        return when (sizeConfig) {
            is MaskSizeValue.Auto -> imageWidth to imageHeight
            is MaskSizeValue.Cover -> {
                val scale = maxOf(
                    containerSize.width / imageWidth,
                    containerSize.height / imageHeight
                )
                imageWidth * scale to imageHeight * scale
            }
            is MaskSizeValue.Contain -> {
                val scale = minOf(
                    containerSize.width / imageWidth,
                    containerSize.height / imageHeight
                )
                imageWidth * scale to imageHeight * scale
            }
            is MaskSizeValue.Dimensions -> {
                val w = sizeConfig.width?.value ?: imageWidth
                val h = sizeConfig.height?.value ?: imageHeight
                w to h
            }
        }
    }

    /**
     * Calculate mask position based on MaskPositionValue.
     * Returns Pair(offsetX, offsetY).
     */
    private fun calculateMaskPosition(
        positionConfig: MaskPositionValue,
        containerSize: Size,
        maskWidth: Float,
        maskHeight: Float
    ): Pair<Float, Float> {
        val xFraction = when (val x = positionConfig.x) {
            is PositionComponent.Keyword -> when (x.position) {
                HorizontalPosition.LEFT -> 0f
                HorizontalPosition.CENTER -> 0.5f
                HorizontalPosition.RIGHT -> 1f
                else -> 0f
            }
            is PositionComponent.Percentage -> x.value / 100f
            is PositionComponent.Length -> x.value.value / containerSize.width
        }

        val yFraction = when (val y = positionConfig.y) {
            is PositionComponent.Keyword -> when (y.position) {
                VerticalPosition.TOP -> 0f
                VerticalPosition.CENTER -> 0.5f
                VerticalPosition.BOTTOM -> 1f
                else -> 0f
            }
            is PositionComponent.Percentage -> y.value / 100f
            is PositionComponent.Length -> y.value.value / containerSize.height
        }

        val offsetX = (containerSize.width - maskWidth) * xFraction
        val offsetY = (containerSize.height - maskHeight) * yFraction

        return offsetX to offsetY
    }

    /**
     * Draw a repeated mask pattern.
     */
    private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRepeatedMask(
        painter: Painter,
        tileWidth: Float,
        tileHeight: Float,
        repeatX: Boolean,
        repeatY: Boolean,
        blendMode: BlendMode,
        colorFilter: ColorFilter?
    ) {
        val tilesX = if (repeatX) (size.width / tileWidth).toInt() + 2 else 1
        val tilesY = if (repeatY) (size.height / tileHeight).toInt() + 2 else 1

        for (row in 0 until tilesY) {
            for (col in 0 until tilesX) {
                translate(
                    left = col * tileWidth,
                    top = row * tileHeight
                ) {
                    with(painter) {
                        draw(
                            size = Size(tileWidth, tileHeight),
                            colorFilter = colorFilter
                        )
                    }
                }
            }
        }
    }

    /**
     * Extension to convert MaskCompositeValue to BlendMode.
     */
    private fun MaskCompositeValue.toBlendMode(): BlendMode {
        return when (this) {
            MaskCompositeValue.ADD -> BlendMode.DstIn
            MaskCompositeValue.SUBTRACT -> BlendMode.DstOut
            MaskCompositeValue.INTERSECT -> BlendMode.DstIn
            MaskCompositeValue.EXCLUDE -> BlendMode.Xor
        }
    }

    /**
     * Check if mask mode uses luminance.
     */
    fun useLuminanceMode(config: MaskConfig): Boolean {
        return config.mode == MaskModeValue.LUMINANCE
    }

    /**
     * Check if config requires the MaskedBox composable (URL-based mask).
     */
    fun requiresMaskedBox(config: MaskConfig): Boolean {
        return config.isUrlMask
    }

    /**
     * Color matrix to convert RGB to luminance-based alpha.
     * Uses sRGB luminance coefficients.
     */
    private val LUMINANCE_TO_ALPHA_MATRIX = ColorMatrix(
        floatArrayOf(
            0f, 0f, 0f, 0f, 255f,  // Red -> White
            0f, 0f, 0f, 0f, 255f,  // Green -> White
            0f, 0f, 0f, 0f, 255f,  // Blue -> White
            0.2126f, 0.7152f, 0.0722f, 0f, 0f  // RGB luminance -> Alpha
        )
    )
}

/**
 * Helper extension to apply translate within DrawScope.
 */
private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.translate(
    left: Float = 0f,
    top: Float = 0f,
    block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit
) {
    drawContext.transform.translate(left, top)
    block()
    drawContext.transform.translate(-left, -top)
}

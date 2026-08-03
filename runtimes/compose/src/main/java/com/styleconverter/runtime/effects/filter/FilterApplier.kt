package com.styleconverter.runtime.effects.filter

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
// The two-pass backdrop render lives in its own module (effects/backdrop/);
// this applier is only its registration point — see applyBackdropFilters.
import com.styleconverter.runtime.effects.backdrop.backdropFilterTwoPass
import kotlin.math.cos
import kotlin.math.sin

/**
 * Applies filter effects to Compose modifiers.
 *
 * ## Implementation Notes
 *
 * ### Direct Modifier Support
 * - `blur()` - Uses Modifier.blur() directly
 * - `opacity()` - Uses Modifier.alpha() directly
 *
 * ### ColorMatrix-based Filters
 * ColorMatrix filters (brightness, contrast, grayscale, etc.) are applied via
 * graphicsLayer with RenderEffect on Android 12+ for true element-wide filtering.
 * On older versions, uses drawWithContent with ColorFilter as a fallback.
 *
 * ### Platform Support
 * - Android 12+ (API 31+): Full RenderEffect support for element-wide filtering
 * - Android 10-11: Fallback using drawWithContent + ColorFilter
 * - Below Android 10: Limited support
 */
object FilterApplier {

    /**
     * Apply all filters to a modifier.
     *
     * @param modifier The base modifier
     * @param config The filter configuration
     * @return Modified modifier with filters applied
     */
    fun applyFilters(
        modifier: Modifier,
        config: FilterConfig,
        // The element's own border-radius, threaded in for the backdrop path:
        // filter-effects-2 §2 clips the filtered backdrop to the BORDER BOX,
        // rounded corners included (backdrop-filter-clip-rect.html asserts
        // exactly that). Defaults to NONE so every existing call site keeps
        // its behaviour and its committed captures.
        radiusConfig: com.styleconverter.runtime.borders.radius.BorderRadiusConfig =
            com.styleconverter.runtime.borders.radius.BorderRadiusConfig.NONE,
        // The element's own `opacity`, for the backdrop path only. Element
        // filters ignore it (ColorApplier's alpha layer already covers them,
        // sitting INSIDE this chain). Defaults to 1 = fully opaque, so every
        // existing call site keeps its behaviour and its committed captures.
        elementAlpha: Float = 1f,
        // The element's resolved margin bands, for the backdrop path only —
        // see applyBackdropFilters. Default NONE = "border box == this node".
        marginInsets: com.styleconverter.runtime.spacing.MarginInsets =
            com.styleconverter.runtime.spacing.MarginInsets.NONE,
    ): Modifier {
        // Backdrop FIRST (outermost in chain order), foreground filters after,
        // so a caller that wants both in one call gets the same relative order
        // EffectsFacade builds by hand. The two halves are separate public
        // entry points because EffectsFacade has to interleave the shadow step
        // between them — see applyBackdropFilters' KDoc.
        var result = applyBackdropFilters(modifier, config, radiusConfig, elementAlpha, marginInsets)
        result = applyForegroundFilters(result, config)
        return result
    }

    /**
     * The `filter` half of [applyFilters]: the element's OWN pixels.
     *
     * Split out so EffectsFacade can place the element's box-shadow BETWEEN
     * the backdrop node and this chain (see [applyBackdropFilters]).
     */
    fun applyForegroundFilters(modifier: Modifier, config: FilterConfig): Modifier {
        var result = modifier

        // Separate filters by type for optimal application
        val blurFilters = config.filters.filterIsInstance<FilterFunction.Blur>()
        val opacityFilters = config.filters.filterIsInstance<FilterFunction.Opacity>()
        val dropShadowFilters = config.filters.filterIsInstance<FilterFunction.DropShadow>()
        val colorMatrixFilters = config.filters.filter { it.isColorMatrixFilter() }

        // Apply blur first.
        //
        // Unbounded edge treatment: CSS `filter: blur()` (Filter Effects 1
        // §10.1) does NOT clip the result to the element's border box — the
        // Gaussian halo bleeds past the bounds, which is exactly what
        // Chrome renders. Compose's Modifier.blur defaults to
        // BlurredEdgeTreatment.Rectangle, which clamps + clips at the
        // bounds and produced hard-edged blurs on Android
        // (filter-functions 002_Filter_BlurLarge: web soft halo vs Android
        // sharp rect, Android-web SSIM 0.85).
        blurFilters.forEach { blur ->
            result = result.blur(blur.radius, BlurredEdgeTreatment.Unbounded)
        }

        // Apply drop shadows
        dropShadowFilters.forEach { shadow ->
            result = applyDropShadow(result, shadow)
        }

        // Apply color matrix filters (combined for efficiency)
        if (colorMatrixFilters.isNotEmpty()) {
            result = applyColorMatrixFilters(result, colorMatrixFilters)
        }

        // Apply opacity last
        opacityFilters.forEach { opacity ->
            result = result.alpha(opacity.amount.coerceIn(0f, 1f))
        }

        return result
    }

    /**
     * Check if a filter function is ColorMatrix-based.
     */
    private fun FilterFunction.isColorMatrixFilter(): Boolean {
        return when (this) {
            is FilterFunction.Brightness,
            is FilterFunction.Contrast,
            is FilterFunction.Grayscale,
            is FilterFunction.HueRotate,
            is FilterFunction.Saturate,
            is FilterFunction.Sepia,
            is FilterFunction.Invert -> true
            else -> false
        }
    }

    /**
     * Apply color matrix filters using the best available method.
     */
    private fun applyColorMatrixFilters(
        modifier: Modifier,
        filters: List<FilterFunction>
    ): Modifier {
        val colorMatrix = buildCombinedColorMatrix(filters) ?: return modifier

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: Use RenderEffect for true element-wide filtering
            applyColorMatrixWithRenderEffect(modifier, colorMatrix)
        } else {
            // Fallback: Use drawWithContent
            applyColorMatrixWithDraw(modifier, colorMatrix)
        }
    }

    /**
     * Apply color matrix using RenderEffect (Android 12+).
     * This provides true element-wide filtering including children.
     */
    private fun applyColorMatrixWithRenderEffect(
        modifier: Modifier,
        colorMatrix: ColorMatrix
    ): Modifier {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            modifier.graphicsLayer {
                val androidMatrix = android.graphics.ColorMatrix(colorMatrix.values)
                renderEffect = RenderEffect.createColorFilterEffect(
                    android.graphics.ColorMatrixColorFilter(androidMatrix)
                ).asComposeRenderEffect()
            }
        } else {
            modifier
        }
    }

    /**
     * Apply color matrix using drawWithContent (fallback for older Android).
     * This draws content with the color filter applied.
     */
    private fun applyColorMatrixWithDraw(
        modifier: Modifier,
        colorMatrix: ColorMatrix
    ): Modifier {
        val colorFilter = ColorFilter.colorMatrix(colorMatrix)

        return modifier.drawWithContent {
            // Draw content with color filter applied
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    this.colorFilter = colorFilter
                }

                // Save the layer with the color filter
                canvas.saveLayer(
                    bounds = androidx.compose.ui.geometry.Rect(
                        0f, 0f, size.width, size.height
                    ),
                    paint = paint
                )

                // Draw the content
                drawContent()

                // Restore the layer
                canvas.restore()
            }
        }
    }

    /**
     * Apply backdrop filters — the registration point for the two-pass
     * backdrop render (effects/backdrop/).
     *
     * ## Why this used to be a no-op
     * CSS `backdrop-filter` filters what is BEHIND the element, not the
     * element itself. Compose's `Modifier.blur()` / `graphicsLayer`
     * `RenderEffect` filter the element's OWN rendering including its
     * children, so wiring `backdrop-filter: blur(10px)` to them obliterated
     * foreground content (`Glass_Effect` rendered its label blurred into
     * invisibility). Dropping the filter entirely was the lesser wrong, and
     * that is what shipped: the KDoc's own conclusion was that a real
     * implementation "would need to capture the parent layer, blur the
     * snapshot, and composite".
     *
     * ## What replaces it
     * Exactly that, made possible by the fact that the composed WPT canvas is
     * a CONTROLLED tree we render twice
     * ([com.styleconverter.runtime.effects.backdrop.BackdropPassCoordinator]):
     * pass A records the canvas with every backdrop element's paint
     * suppressed (that recording IS the backdrop root image), pass B samples
     * the patch under each element's border box, runs the chain over it,
     * draws it, and lets the element paint on top.
     *
     * Three gates keep this from touching anything it should not:
     *  1. the coordinator is DISABLED unless a two-pass host armed it for a
     *     document that actually declares `backdrop-filter`;
     *  2. the chain must be renderable by this lane (invert + blur only) —
     *     anything else refuses ALL-OR-NOTHING and keeps the historical no-op
     *     rather than rendering a recognised prefix. iOS's BackdropPlan.plan
     *     enforces the identical rule, so the two natives never paint
     *     different things for the same mixed chain;
     *  3. with no pass-A image published, the installed modifier degenerates
     *     to a plain `drawContent()`.
     * Together they make the per-component capture path and the committed
     * 327-pair dark-stage baseline byte-identical to before this lane.
     *
     * ## Why this is a separate entry point from [applyForegroundFilters]
     * EffectsFacade must chain the element's own box-shadow BETWEEN the two.
     * The shadow paints via `drawBehind`, so with the shadow OUTER of this
     * node its ink lands on the pass-A canvas — i.e. the element's own shadow
     * ends up inside its own sampled backdrop, which filter-effects-2 §2 does
     * not do (a box-shadow is part of the ELEMENT's paint, drawn above the
     * filtered backdrop, not part of the backdrop). Installing this node
     * OUTSIDE the shadow makes pass A's early return suppress the shadow too.
     */
    fun applyBackdropFilters(
        modifier: Modifier,
        config: FilterConfig,
        radiusConfig: com.styleconverter.runtime.borders.radius.BorderRadiusConfig =
            com.styleconverter.runtime.borders.radius.BorderRadiusConfig.NONE,
        elementAlpha: Float = 1f,
        // The element's resolved margin bands: this node ends up OUTER of the
        // margin step (StyleApplier step 4 emits margins as absolutePadding),
        // so it is sized by the MARGIN box while the spec samples and clips
        // the BORDER box. See BackdropSampleGeometry.borderBox.
        marginInsets: com.styleconverter.runtime.spacing.MarginInsets =
            com.styleconverter.runtime.spacing.MarginInsets.NONE,
        // The element's resolved POSITION offset: this node is likewise OUTER
        // of step 4's `absoluteOffset`, so without it the backplate is sampled
        // AND painted at the element's un-offset slot — a perfect filter of
        // the wrong rectangle. See BackdropModifier.positionOffset for the
        // measured failure it repairs.
        positionOffset: androidx.compose.ui.unit.DpOffset =
            androidx.compose.ui.unit.DpOffset.Zero,
    ): Modifier {
        // Gate 0 — `backdrop-filter` absent entirely. Cheapest possible out,
        // and the reason every non-backdrop element pays nothing for this lane.
        if (!config.hasBackdropFilters) return modifier

        // Gate 1 — plain (non-snapshot) read, so this decision is made once
        // while the chain is built and can never trigger a recomposition that
        // would discard the per-element position slots mid-capture.
        val coordinator = com.styleconverter.runtime.effects.backdrop
            .BackdropPassCoordinator.current
        if (!coordinator.enabled) return modifier

        // Gate 2 — invert + blur only; null means "not renderable here", for
        // the WHOLE chain. Logged rather than dropped in silence (CLAUDE.md:
        // no silent fallthroughs) so a capture log names the chain that was
        // refused instead of implying a complete render — the same disclosure
        // BackdropLog.reportOnce prints on iOS.
        val chain = com.styleconverter.runtime.effects.backdrop.BackdropChain.of(
            config.backdropFilters,
        ) ?: run {
            // `BackdropChain.of` returns null for TWO different reasons, and
            // only one of them is a refusal. `backdrop-filter: none` (and a
            // chain of nothing but `none`) is an exact IDENTITY: nothing was
            // dropped, so announcing a refusal would put a false miss in the
            // capture log — the mirror of iOS's `BackdropPlan.isRefused`,
            // which deliberately excludes the identities. Only an
            // out-of-lane-scope function is disclosed.
            val outOfScope = config.backdropFilters.filter {
                it !is FilterFunction.None &&
                    it !is FilterFunction.Invert &&
                    it !is FilterFunction.Blur
            }
            if (outOfScope.isNotEmpty()) {
                android.util.Log.w(
                    "BackdropChain",
                    "backdrop-filter chain refused wholesale (lane scope is invert + blur): " +
                        outOfScope.joinToString { it::class.simpleName ?: "?" },
                )
            }
            return modifier
        }

        // Registered: the element now suppresses its paint during pass A and
        // draws its filtered backdrop during pass B.
        return modifier.backdropFilterTwoPass(
            chain = chain,
            radiusConfig = radiusConfig,
            coordinator = coordinator,
            marginInsets = marginInsets,
            positionOffset = positionOffset,
            elementAlpha = elementAlpha,
        )
    }

    /**
     * Apply drop shadow effect using drawBehind.
     */
    /**
     * Convert a CSS drop-shadow <blur-radius> (px) into the radius
     * parameter [android.graphics.BlurMaskFilter] needs to reproduce the
     * spec's Gaussian.
     *
     * Filter Effects 1 §10.1: the <blur-radius> r means a Gaussian with
     * standard deviation σ = r/2. BlurMaskFilter maps its `radius` input
     * to σ ≈ radius·0.57735 + 0.5 (the legacy Android convert-radius-to-
     * sigma formula), so we invert: radius = (σ − 0.5) / 0.57735, floored
     * at a tiny positive value because BlurMaskFilter throws on radius ≤ 0.
     * Pure function — pinned by FilterDropShadowMathTest on the JVM.
     */
    internal fun dropShadowMaskRadius(blurPx: Float): Float =
        (((blurPx / 2f) - 0.5f) / 0.57735f).coerceAtLeast(0.1f)

    private fun applyDropShadow(modifier: Modifier, shadow: FilterFunction.DropShadow): Modifier {
        return modifier.drawBehind {
            val offsetX = shadow.offsetX.toPx()
            val offsetY = shadow.offsetY.toPx()
            val blur = shadow.blurRadius.toPx()

            drawIntoCanvas { canvas ->
                // The previous implementation drew a FULL-SIZE rect at
                // (0,0) and relied on Paint.setShadowLayer for the offset
                // shadow. On a hardware-accelerated canvas (every Compose
                // RenderNode) setShadowLayer only applies to TEXT draws —
                // for rects it's silently ignored, so no shadow ever
                // rendered (filter-functions 016/017: web showed the
                // offset shadow, Android showed none) and the full-size
                // rect itself hid exactly under the content.
                //
                // Instead, draw the shadow silhouette directly: a rect
                // offset by (dx, dy) in the shadow colour, blurred with a
                // BlurMaskFilter (supported on hardware canvases since the
                // Skia-backed HWUI in Android P). Filter Effects 1 §10.1:
                // drop-shadow's <blur-radius> r means a Gaussian with
                // σ = r/2. BlurMaskFilter(radius) maps its radius to
                // σ ≈ radius·0.57735 + 0.5, so we invert that to hit the
                // spec's σ.
                val paint = Paint().apply {
                    color = shadow.color
                    if (blur > 0f) {
                        asFrameworkPaint().maskFilter =
                            android.graphics.BlurMaskFilter(
                                dropShadowMaskRadius(blur),
                                android.graphics.BlurMaskFilter.Blur.NORMAL)
                    }
                }

                canvas.drawRect(
                    left = offsetX,
                    top = offsetY,
                    right = offsetX + size.width,
                    bottom = offsetY + size.height,
                    paint = paint
                )
            }
        }
    }

    /**
     * Build a combined ColorMatrix from multiple filter functions.
     */
    private fun buildCombinedColorMatrix(filters: List<FilterFunction>): ColorMatrix? {
        if (filters.isEmpty()) return null

        var matrix: ColorMatrix? = null

        filters.forEach { filter ->
            matrix = when (filter) {
                is FilterFunction.Brightness -> applyBrightness(matrix, filter.amount)
                is FilterFunction.Contrast -> applyContrast(matrix, filter.amount)
                is FilterFunction.Grayscale -> applyGrayscale(matrix, filter.amount)
                is FilterFunction.HueRotate -> applyHueRotate(matrix, filter.degrees)
                is FilterFunction.Saturate -> applySaturate(matrix, filter.amount)
                is FilterFunction.Sepia -> applySepia(matrix, filter.amount)
                is FilterFunction.Invert -> applyInvert(matrix, filter.amount)
                else -> matrix
            }
        }

        return matrix
    }

    // ==================== PUBLIC API FOR EXTERNAL USE ====================

    /**
     * Generate a ColorFilter from a list of filter functions.
     * Useful for applying to images or custom drawing.
     */
    fun generateColorFilter(filters: List<FilterFunction>): ColorFilter? {
        val matrix = buildCombinedColorMatrix(filters)
        return matrix?.let { ColorFilter.colorMatrix(it) }
    }

    /**
     * Generate a combined ColorMatrix from filter config.
     */
    fun generateColorMatrix(config: FilterConfig): ColorMatrix? {
        val colorMatrixFilters = config.filters.filter { it.isColorMatrixFilter() }
        return buildCombinedColorMatrix(colorMatrixFilters)
    }

    // ==================== COLOR MATRIX GENERATORS ====================

    /**
     * Apply brightness adjustment to a color matrix.
     * @param brightness 1.0 = normal, <1.0 = darker, >1.0 = brighter
     */
    private fun applyBrightness(existing: ColorMatrix?, brightness: Float): ColorMatrix {
        val matrix = existing ?: ColorMatrix()
        val scale = brightness

        val brightnessMatrix = ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, 0f,
            0f, scale, 0f, 0f, 0f,
            0f, 0f, scale, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))

        matrix.timesAssign(brightnessMatrix)
        return matrix
    }

    /**
     * Apply contrast adjustment to a color matrix.
     * @param contrast 1.0 = normal, <1.0 = less contrast, >1.0 = more contrast
     */
    private fun applyContrast(existing: ColorMatrix?, contrast: Float): ColorMatrix {
        val matrix = existing ?: ColorMatrix()
        val translate = (1f - contrast) / 2f * 255f

        val contrastMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))

        matrix.timesAssign(contrastMatrix)
        return matrix
    }

    /**
     * Apply grayscale effect to a color matrix.
     * @param amount 0.0 = no effect, 1.0 = fully grayscale
     */
    private fun applyGrayscale(existing: ColorMatrix?, amount: Float): ColorMatrix {
        val matrix = existing ?: ColorMatrix()
        val invAmount = 1f - amount.coerceIn(0f, 1f)

        // Luminance-preserving grayscale matrix
        val grayscaleMatrix = ColorMatrix(floatArrayOf(
            0.2126f + 0.7874f * invAmount, 0.7152f - 0.7152f * invAmount, 0.0722f - 0.0722f * invAmount, 0f, 0f,
            0.2126f - 0.2126f * invAmount, 0.7152f + 0.2848f * invAmount, 0.0722f - 0.0722f * invAmount, 0f, 0f,
            0.2126f - 0.2126f * invAmount, 0.7152f - 0.7152f * invAmount, 0.0722f + 0.9278f * invAmount, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))

        matrix.timesAssign(grayscaleMatrix)
        return matrix
    }

    /**
     * Apply hue rotation to a color matrix.
     * @param degrees Rotation angle in degrees
     */
    private fun applyHueRotate(existing: ColorMatrix?, degrees: Float): ColorMatrix {
        val matrix = existing ?: ColorMatrix()

        val radians = Math.toRadians(degrees.toDouble())
        val cos = cos(radians).toFloat()
        val sin = sin(radians).toFloat()

        // Luminance-preserving hue rotation
        val lumR = 0.213f
        val lumG = 0.715f
        val lumB = 0.072f

        val hueRotateMatrix = ColorMatrix(floatArrayOf(
            lumR + cos * (1 - lumR) + sin * (-lumR),
            lumG + cos * (-lumG) + sin * (-lumG),
            lumB + cos * (-lumB) + sin * (1 - lumB),
            0f, 0f,

            lumR + cos * (-lumR) + sin * 0.143f,
            lumG + cos * (1 - lumG) + sin * 0.140f,
            lumB + cos * (-lumB) + sin * (-0.283f),
            0f, 0f,

            lumR + cos * (-lumR) + sin * (-(1 - lumR)),
            lumG + cos * (-lumG) + sin * lumG,
            lumB + cos * (1 - lumB) + sin * lumB,
            0f, 0f,

            0f, 0f, 0f, 1f, 0f
        ))

        matrix.timesAssign(hueRotateMatrix)
        return matrix
    }

    /**
     * Apply saturation adjustment to a color matrix.
     * @param saturation 0.0 = grayscale, 1.0 = normal, >1.0 = oversaturated
     */
    private fun applySaturate(existing: ColorMatrix?, saturation: Float): ColorMatrix {
        val matrix = existing ?: ColorMatrix()

        val invAmount = 1f - saturation
        val r = 0.213f * invAmount
        val g = 0.715f * invAmount
        val b = 0.072f * invAmount

        val saturateMatrix = ColorMatrix(floatArrayOf(
            r + saturation, g, b, 0f, 0f,
            r, g + saturation, b, 0f, 0f,
            r, g, b + saturation, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))

        matrix.timesAssign(saturateMatrix)
        return matrix
    }

    /**
     * Apply sepia tone effect to a color matrix.
     * @param amount 0.0 = no effect, 1.0 = fully sepia
     */
    private fun applySepia(existing: ColorMatrix?, amount: Float): ColorMatrix {
        val matrix = existing ?: ColorMatrix()
        val amt = amount.coerceIn(0f, 1f)
        val invAmt = 1f - amt

        val sepiaMatrix = ColorMatrix(floatArrayOf(
            invAmt + amt * 0.393f, amt * 0.769f, amt * 0.189f, 0f, 0f,
            amt * 0.349f, invAmt + amt * 0.686f, amt * 0.168f, 0f, 0f,
            amt * 0.272f, amt * 0.534f, invAmt + amt * 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))

        matrix.timesAssign(sepiaMatrix)
        return matrix
    }

    /**
     * Apply color inversion to a color matrix.
     * @param amount 0.0 = no effect, 1.0 = fully inverted
     */
    private fun applyInvert(existing: ColorMatrix?, amount: Float): ColorMatrix {
        val matrix = existing ?: ColorMatrix()
        val amt = amount.coerceIn(0f, 1f)

        // newColor = amount * (1 - color) + (1 - amount) * color
        val scale = 1f - 2f * amt
        val translate = amt * 255f

        val invertMatrix = ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))

        matrix.timesAssign(invertMatrix)
        return matrix
    }
}

package com.styleconverter.runtime.effects.filter

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
// The wave-43 CSS-opacity mechanism — the unbounded saveLayerAlpha group.
// `filter: opacity()` reuses it verbatim (see applyForegroundFilters) so the
// filter function and the property can never drift apart in clip physics.
import com.styleconverter.runtime.color.OpacityApplier
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
 * - `opacity()` - Reuses color/OpacityApplier's unbounded saveLayerAlpha
 *   group (wave 44: `Modifier.alpha` IS graphicsLayer(alpha, clip = TRUE) —
 *   the identical record-time crop wave 43 evicted from the CSS `opacity`
 *   property, and Filter Effects 1 §10.2 gives filter functions no clip
 *   either — see the comment at the apply site)
 *
 * ### ColorMatrix-based Filters
 * ColorMatrix filters (brightness, contrast, grayscale, etc.) are applied as
 * a GROUP saveLayer with a ColorMatrix ColorFilter — every API level, one
 * path (see [applyGroupColorFilters] for why the former API-31 RenderEffect
 * route was retired: its offscreen buffer cropped positioned elements to
 * their un-offset layout slot).
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
        // Same relative order EffectsFacade builds by hand (see its apply):
        // the colour-matrix GROUP layer outermost — filter-effects-2 §2 puts
        // the filtered backdrop INSIDE the element's group, so the element's
        // own `filter` must wrap the backplate node — then the backdrop node,
        // then the rest of the foreground chain. The halves are separate
        // public entry points because EffectsFacade has to interleave the
        // shadow step between them — see applyBackdropFilters' KDoc.
        var result = applyGroupColorFilters(modifier, config)
        result = applyBackdropFilters(result, config, radiusConfig, elementAlpha, marginInsets)
        result = applyForegroundFilters(result, config)
        return result
    }

    /**
     * The `filter` half of [applyFilters] that stays INNER of the two draw
     * lanes EffectsFacade interleaves above it (see its apply): blur,
     * drop-shadow and opacity over the element's OWN pixels.
     *
     * The colour-matrix half of the `filter` list is NOT here any more — it
     * moved to [applyGroupColorFilters], installed OUTERMOST in the facade,
     * because filter-effects-2 §2 composites the filtered backplate into the
     * element's group BEFORE the element's own `filter` applies (the wave-41
     * `backdrop-filter-plus-filter.html` fix; the function's own KDoc has the
     * measurement). Note the list was ALREADY applied by type rather than in
     * authored order (blur first, matrices after), so the split does not lose
     * any ordering fidelity that existed here.
     */
    fun applyForegroundFilters(modifier: Modifier, config: FilterConfig): Modifier {
        var result = modifier

        // Separate filters by type for optimal application
        val blurFilters = config.filters.filterIsInstance<FilterFunction.Blur>()
        val opacityFilters = config.filters.filterIsInstance<FilterFunction.Opacity>()
        val dropShadowFilters = config.filters.filterIsInstance<FilterFunction.DropShadow>()

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
            // filter-effects-1 §8.2: blur()'s parameter IS the Gaussian
            // STANDARD DEVIATION, not a radius — so it cannot be handed
            // straight to Modifier.blur, whose parameter Skia converts with
            // σ = radius·0.57735 + 0.5 (the same legacy formula this file
            // already inverts for drop-shadow, see dropShadowMaskRadius).
            // Passing the CSS length through unconverted under-blurred
            // everything, and by a VARYING factor because the relation is
            // affine rather than a scale.
            result = result.blur(Dp(blurSigmaToSkiaRadius(blur.radius.value)),
                                 BlurredEdgeTreatment.Unbounded)
        }

        // Apply drop shadows
        dropShadowFilters.forEach { shadow ->
            result = applyDropShadow(result, shadow)
        }

        // Apply opacity last (the pre-existing by-type order of this chain).
        //
        // Wave 44 (lane U4): composite through the wave-43 CSS-opacity
        // mechanism — color/OpacityApplier's UNBOUNDED framework-canvas
        // saveLayerAlpha group — instead of `Modifier.alpha`. AlphaKt
        // bytecode (ui-android 1.11.4) shows Modifier.alpha IS
        // graphicsLayer(alpha, clip = TRUE): a record-time crop at the
        // node's own bounds, the exact physics wave 43 measured and evicted
        // from the `opacity` property (composited-filters-under-opacity,
        // android-ref 0.9401 — OpacityApplier's KDoc carries the full
        // story). Filter Effects 1 §10.2 defines `opacity()` as a pure
        // alpha multiply over the element's rendering and gives filter
        // functions no border-box clip, so descendants painting outside
        // the bounds (abspos children, ink overflow) must survive here
        // exactly as they do under the property. §10.2's over-100% rule
        // ("values of amount over 100% … must be clamped to 1") and the
        // negative-value floor are OpacityApplier's own coerceIn(0f, 1f),
        // so the clamp the old call carried is preserved, not dropped.
        opacityFilters.forEach { opacity ->
            result = OpacityApplier.applyOpacity(result, opacity.amount)
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
     * The colour-matrix half of the element's `filter` list, applied as a
     * GROUP layer: `canvas.saveLayer(bounds, paint-with-ColorFilter)` around
     * everything chained inside it, so the matrix runs over the element's
     * whole rendering at restore time — Skia's own group-with-colour-filter
     * mechanism, on every API level.
     *
     * ## Why this is a saveLayer and not `graphicsLayer { renderEffect }`
     * The previous path (API 31+) hung the matrix on a RenderEffect. HWUI
     * renders a RenderNode carrying a RenderEffect into an offscreen buffer
     * SIZED TO THE NODE'S OWN BOUNDS — and this node sits at StyleApplier
     * step 3, OUTER of step 4's `absoluteOffset`, so a positioned element
     * paints OUTSIDE those bounds and was cropped away. Measured on WPT
     * `backdrop-filter-plus-filter.html` (wave40-final): the `.bluebox`
     * (`filter: invert(1); position: absolute; left: 60px; top: 110px`)
     * rendered ZERO pixels on Android while its unfiltered sibling rendered
     * exactly at its offset — the offset box and the layer's un-offset slot
     * do not even intersect. The saveLayer takes explicit bounds instead
     * ([FilterGroupGeometry.groupLayerBounds] — the un-offset box UNION the
     * offset box), so the offset paint stays inside the group. The matrix
     * itself is unchanged (ColorMatrixColorFilter operates on unpremultiplied
     * colour on both paths), so a static element renders identically.
     *
     * ## Why this is installed OUTERMOST (see EffectsFacade.apply)
     * filter-effects-2 §2's Backdrop Filter algorithm composites the filtered
     * backdrop as the bottom-most content of the element's transparency
     * group, and the group is THEN rendered with the element's own `filter`.
     * So `filter: invert(1)` + `backdrop-filter: blur(10px)` must invert the
     * blurred backplate too — Chrome's `backdrop-filter-plus-filter.html`
     * ref is exactly that (dark-purple body = invert(white blurred backdrop
     * + translucent green bg)). This node therefore wraps the backplate node
     * rather than sitting inside it. During pass A the backplate node
     * suppresses all content, so this layer composites nothing — the group
     * cannot leak into the Backdrop Root Image.
     *
     * @param positionOffset the element's resolved position offset
     *   (`PositionApplier.resolvedOffset` — the value form of the very
     *   `absoluteOffset` step 4 chains), threaded exactly like the shadow
     *   and backplate lanes thread it. Default Zero keeps every static call
     *   site byte-identical.
     */
    fun applyGroupColorFilters(
        modifier: Modifier,
        config: FilterConfig,
        positionOffset: androidx.compose.ui.unit.DpOffset =
            androidx.compose.ui.unit.DpOffset.Zero,
    ): Modifier {
        // Only the matrix ops live here; blur/drop-shadow/opacity stay in
        // applyForegroundFilters. No matrix ops → no layer at all, so the
        // overwhelming majority of elements pay nothing. The MATRIX is built
        // at chain time (androidx ColorMatrix is pure float math, JVM-safe);
        // the ColorFilter wrapping it is built inside the draw lambda below,
        // because ColorFilter.colorMatrix constructs an android.graphics
        // object — a throwing stub in the JVM unit suite, which introspects
        // this chain (FilterGroupApplierTest) without ever drawing it.
        val colorMatrixFilters = config.filters.filter { it.isColorMatrixFilter() }
        val colorMatrix = buildCombinedColorMatrix(colorMatrixFilters) ?: return modifier

        return modifier.drawWithContent {
            // Compose draw scopes only expose saveLayer through the native
            // canvas — same route the pre-31 fallback always used.
            drawIntoCanvas { canvas ->
                // The group's paint carries the matrix; Skia applies it when
                // the layer is composited at restore(). Per-draw allocation,
                // matching the Paint the drop-shadow lane allocates per draw.
                val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(colorMatrix) }
                // Bounds cover the un-offset node box AND the position-offset
                // box (Dp→px is legal here — DrawScope is a Density), fixing
                // the crop measured in the KDoc above.
                canvas.saveLayer(
                    bounds = FilterGroupGeometry.groupLayerBounds(
                        widthPx = size.width,
                        heightPx = size.height,
                        positionOffsetXPx = positionOffset.x.toPx(),
                        positionOffsetYPx = positionOffset.y.toPx(),
                    ),
                    paint = paint,
                )
                // Everything inner — backplate patch, background, borders,
                // children — renders into the group.
                drawContent()
                // Composite the group through the matrix.
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
    /**
     * Convert a CSS `blur(<length>)` σ into the radius Skia needs.
     *
     * filter-effects-1 §8.2: the parameter of blur() IS the standard
     * deviation. Skia (via RenderEffect.createBlurEffect, which backs
     * Modifier.blur) maps its radius input to σ ≈ radius·0.57735 + 0.5 —
     * the same relation [dropShadowMaskRadius] inverts — so we invert it
     * here too. The only difference between the two helpers is the σ the
     * spec asks for: drop-shadow's <blur-radius> r means σ = r/2
     * (§10.1), while blur()'s parameter is σ itself.
     *
     * MEASURED before and after, with the step-edge estimator (the
     * derivative of a blurred step edge IS the Gaussian, so its second
     * moment is σ). Passing the raw CSS length gave a ratio to web that
     * DRIFTED with radius — 0.93, 0.75, 0.68, 0.68 at R = 1, 2, 4, 8 —
     * which is the signature of an affine relation being treated as an
     * identity, and Skia's formula predicted every measurement.
     *
     * Floors at 0: σ below 0.5 is unreachable through this API (Skia's
     * +0.5 offset), so a sub-half-pixel blur renders as no blur rather
     * than as a negative radius. That is a platform limit, logged here
     * rather than silently clamped somewhere deeper.
     */
    internal fun blurSigmaToSkiaRadius(sigmaPx: Float): Float =
        ((sigmaPx - 0.5f) / 0.57735f).coerceAtLeast(0f)

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

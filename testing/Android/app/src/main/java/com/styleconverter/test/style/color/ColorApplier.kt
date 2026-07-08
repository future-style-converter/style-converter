package com.styleconverter.test.style.color

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import com.styleconverter.test.style.background.RepeatingGradientHelper
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Applies color-related styling to Compose Modifiers.
 *
 * ## Supported Features
 * - Solid background colors
 * - Linear gradients (including repeating)
 * - Radial gradients (including repeating)
 * - Conic/sweep gradients (repeating not fully supported)
 * - Opacity/alpha
 *
 * ## Limitations
 * - Multiple background layers: Only the first gradient is applied
 * - Image URLs: Not rendered (use Coil or similar for image loading)
 * - Repeating conic gradients: Compose sweepGradient doesn't support TileMode
 * - Gradient sizing: Uses approximated coordinates; actual size requires DrawScope
 *
 * ## Usage
 * ```kotlin
 * val config = ColorExtractor.extractColorConfig(properties)
 * val modifier = ColorApplier.applyColors(Modifier, config)
 * ```
 */
object ColorApplier {

    /**
     * Apply all color-related properties to a Modifier.
     *
     * Order of application:
     * 1. Opacity FIRST (outermost in this group) — `Modifier.alpha`
     *    introduces a graphics layer that wraps everything BELOW it in
     *    the chain (and the children). Applying it first means the bg
     *    paint, the gradient draw, AND the children all render INSIDE
     *    the alpha layer, so `opacity: 0` actually hides everything.
     *    Previously opacity was applied LAST here, which put the alpha
     *    layer INSIDE the bg drawer — bg painted at full opacity and
     *    only the (text) children faded. `Edge_ZeroOpacity` rendered as
     *    a fully-opaque red rectangle on Android while iOS / web
     *    correctly produced a fully-transparent surface (SSIM 1.00).
     * 2. Background images (gradients) - drawn first (bottom layer)
     * 3. Solid background color - drawn on top of gradients
     *
     * Note: `StyleApplier.applyConfig` calls this AFTER borders, so border
     * draws sit OUTSIDE the alpha layer and stay opaque. That's a known
     * compromise — CSS `opacity: 0` should hide borders too. Moving the
     * alpha layer to the outermost position would require restructuring
     * the StyleApplier chain (border draw is between width and bg) and
     * is left as future work; the common case (`opacity: 0`) currently
     * matters more than border-fade correctness because outline / border
     * tests use intermediate opacities, not `0`.
     *
     * @param modifier The base modifier to extend
     * @param config ColorConfig containing all color properties
     * @return Modified Modifier with color properties applied
     */
    fun applyColors(modifier: Modifier, config: ColorConfig): Modifier {
        var result = modifier

        // 1. Opacity FIRST → alpha layer wraps bg + children below it.
        config.opacity?.let { alpha ->
            result = result.alpha(alpha.coerceIn(0f, 1f))
        }

        // 2. Background COLOR is the bottom-most layer of the box's
        // background per CSS Backgrounds 3 §3.10 — every image layer
        // paints on top of it. Compose's `Modifier.background(...)`
        // chain stacks new fills above prior ones, so the bg-color
        // call must come BEFORE the image-layer calls below to land
        // underneath them. (Previously the call sat after the image
        // loop and painted ON TOP, which silently overwrote any
        // semi-transparent gradient layered against a solid colour.)
        config.backgroundColor?.let { color ->
            result = result.background(color)
        }

        // 3. Background images (gradients) - drawn inside the alpha
        // layer. Per CSS Backgrounds 3 §3.5 layer indexing, the FIRST
        // entry in the comma-separated list paints ON TOP and the last
        // paints at the bottom. Compose's `Modifier.background(...)`
        // chains add the new fill on top of whatever is already there,
        // so we must apply the layers in REVERSE source order to land
        // the first-source layer last (i.e. on top).
        // CSS `background-clip: text` masks the bg-image to the glyph
        // shape — the placeholder text renderer (ComponentRenderer)
        // handles the visible "gradient text" output via TextStyle.brush
        // when this flag is set, so painting the rectangular bg-image
        // here would just print a coloured box behind the text. Skip
        // in that case.
        if (!config.suppressBackgroundImage) {
            // Per CSS Compositing 1 §3.2 background-blend-mode composites
            // each background-image LAYER against the union of layers
            // beneath it (and the bg-color underneath). Compose's
            // `Modifier.background()` chains don't expose per-pair blend
            // targets, so for the per-layer case we fall back to a
            // drawWithContent path that wraps each layer's draw in a
            // saveLayer with paint.blendMode. The `[i]`-th source layer
            // gets blend mode `backgroundBlendModes[i]`. Reverse-iter
            // semantic still applies: source order index 0 paints on top.
            val blendModes = config.backgroundBlendModes
            val anyBlend = blendModes.any { it != androidx.compose.ui.graphics.BlendMode.SrcOver }
            if (anyBlend && config.backgroundImages.isNotEmpty()) {
                // Build the brush list ahead of time so the drawWithContent
                // closure (which runs per frame) doesn't re-extract.
                val brushes: List<Pair<androidx.compose.ui.graphics.Brush, androidx.compose.ui.graphics.BlendMode>> =
                    config.backgroundImages.mapIndexedNotNull { idx, image ->
                        val brush = brushFor(image, config) ?: return@mapIndexedNotNull null
                        val blend = blendModes.getOrNull(idx)
                            ?: androidx.compose.ui.graphics.BlendMode.SrcOver
                        brush to blend
                    }
                if (brushes.isNotEmpty()) {
                    result = result.drawWithContent {
                        // Bottom-up paint per CSS layer order: last source
                        // layer paints first, first source layer paints
                        // last (on top). Each one wrapped in its own
                        // saveLayer so paint.blendMode targets the
                        // composite of everything painted before it.
                        drawIntoCanvas { canvas ->
                            val rect = androidx.compose.ui.geometry.Rect(
                                0f, 0f, this.size.width, this.size.height)
                            // Children draw FIRST so the bg layers blend
                            // against them too — but for now we want the
                            // bg layered behind content, so call drawContent
                            // AFTER stacking the layers.
                            for ((brush, mode) in brushes.reversed()) {
                                val paint = androidx.compose.ui.graphics.Paint().apply {
                                    this.blendMode = mode
                                }
                                canvas.saveLayer(rect, paint)
                                drawRect(brush = brush,
                                         topLeft = androidx.compose.ui.geometry.Offset.Zero,
                                         size = this.size)
                                canvas.restore()
                            }
                            drawContent()
                        }
                    }
                }
            } else {
                config.backgroundImages.asReversed().forEach { bgImage ->
                    result = applyBackgroundImage(result, bgImage, config)
                }
            }
        }

        return result
    }

    /**
     * Apply a single background color to a Modifier.
     *
     * @param modifier The base modifier
     * @param color The background color
     * @return Modified Modifier with background color
     */
    fun applyBackgroundColor(modifier: Modifier, color: Color): Modifier {
        return modifier.background(color)
    }

    /**
     * Apply opacity to a Modifier.
     *
     * @param modifier The base modifier
     * @param alpha Opacity value (0.0-1.0)
     * @return Modified Modifier with alpha applied
     */
    fun applyOpacity(modifier: Modifier, alpha: Float): Modifier {
        return modifier.alpha(alpha.coerceIn(0f, 1f))
    }

    /**
     * Build the Brush for a single background-image layer without any
     * sized-tile drawing. Used by the per-layer blend-mode path which
     * needs a brush per layer to feed into a saveLayer pipeline.
     * Returns null for Url / None layers (no image loading wired up)
     * and for repeating gradients that we don't currently re-tile in
     * the blend pipeline (they need a size to bake the tile — the
     * blend path always paints across the full draw rect anyway, so
     * we approximate via the non-repeating branch's size-aware shader).
     */
    private fun brushFor(image: BackgroundImageConfig, config: ColorConfig): Brush? {
        return when (image) {
            is BackgroundImageConfig.LinearGradient ->
                createLinearGradientBrush(image, config.backgroundPosition, TileMode.Clamp)
            is BackgroundImageConfig.RadialGradient ->
                createRadialGradientBrush(image, config.backgroundPosition, TileMode.Clamp)
            is BackgroundImageConfig.ConicGradient ->
                createSweepGradientBrush(image, config.backgroundPosition)
            is BackgroundImageConfig.Url -> null
            is BackgroundImageConfig.None -> null
        }
    }

    /**
     * Apply a background image (gradient or URL) to a Modifier.
     *
     * @param modifier Base modifier
     * @param image Background image config
     * @param config Full color config
     * @param size Optional size for gradient calculations
     */
    private fun applyBackgroundImage(
        modifier: Modifier,
        image: BackgroundImageConfig,
        config: ColorConfig,
        size: Size = Size(500f, 500f)
    ): Modifier {
        // For NON-repeating gradients we always want TileMode.Clamp regardless
        // of background-repeat. CSS background-repeat only re-tiles a gradient
        // when an explicit background-size makes the tile smaller than the
        // box; that pathway isn't wired into the gradient brushes here, so
        // honouring `repeat` would mis-tile the gradient inside its own
        // arbitrary brush-coordinate space (see createLinearGradientBrush()'s
        // hard-coded gradientLength=1000) and produce wrap-around colour
        // bands rather than the single fill CSS specifies.
        // The `repeating-linear-gradient(...)` form is handled by the
        // dedicated RepeatingGradientHelper branch below and is unaffected.
        val gradientTileMode = TileMode.Clamp

        // Build the brush.
        val brush: Brush? = when (image) {
            is BackgroundImageConfig.LinearGradient -> {
                if (image.repeating)
                    RepeatingGradientHelper.createRepeatingLinearGradient(
                        angle = image.angle, colorStops = image.colorStops, size = size)
                else
                    createLinearGradientBrush(image, config.backgroundPosition, gradientTileMode)
            }
            is BackgroundImageConfig.RadialGradient -> {
                if (image.repeating)
                    RepeatingGradientHelper.createRepeatingRadialGradient(
                        centerX = image.centerX, centerY = image.centerY,
                        colorStops = image.colorStops, size = size)
                else
                    createRadialGradientBrush(image, config.backgroundPosition, gradientTileMode)
            }
            is BackgroundImageConfig.ConicGradient -> {
                if (image.repeating)
                    RepeatingGradientHelper.createRepeatingConicGradient(
                        centerX = image.centerX, centerY = image.centerY,
                        startAngle = image.angle, colorStops = image.colorStops, size = size)
                else
                    createSweepGradientBrush(image, config.backgroundPosition)
            }
            is BackgroundImageConfig.Url -> null
            is BackgroundImageConfig.None -> null
        }
        if (brush == null) return modifier
        // Kotlin smart-cast doesn't propagate into the `drawBehind`
        // lambda below, so re-bind a non-null local for clarity.
        val nonNullBrush: Brush = brush

        // CSS `background-size` + `background-position` decide where the
        // brush tile draws inside the box. When `background-size` carries
        // explicit dimensions (and the layer isn't a `repeating-*-gradient`
        // — those bake their own tile into the brush), we can't just call
        // `Modifier.background(brush)` because that always fills the
        // entire element. Instead we draw the brush into a sized rect via
        // `Modifier.drawBehind` at the configured top-left.  Without
        // background-size or with size=auto we fall back to the prior
        // full-fill path so previously-correct fixtures don't regress.
        val sized = config.backgroundSize as? BackgroundSizeConfig.Dimensions
        if (sized == null) {
            return modifier.background(nonNullBrush)
        }
        val pos = config.backgroundPosition
        val repeat = config.backgroundRepeat
        return modifier.drawBehind {
            // Resolve the per-axis tile size against the box. Dp values
            // (Dp? width / height) are taken verbatim; percent values
            // resolve against the box; missing axis defaults to 0 so the
            // tile collapses cleanly when only one axis was specified.
            // CSS spec (Backgrounds 3 §3.9): when one axis of background-size
            // is omitted, it defaults to `auto`. For gradient brushes (no
            // intrinsic size), `auto` resolves to the container axis.
            // Previously: tileH defaulted to 0 → return → no draw → 0.74 SSIM
            // divergence vs web (Auditor round 8 finding).
            val tileW = sized.width?.toPx()
                ?: sized.widthPercent?.let { this.size.width * it / 100f }
                ?: this.size.width
            val tileH = sized.height?.toPx()
                ?: sized.heightPercent?.let { this.size.height * it / 100f }
                ?: this.size.height
            if (tileW <= 0f || tileH <= 0f) return@drawBehind
            // CSS background-position percent of (containerSize − tileSize).
            val freeX = (this.size.width - tileW).coerceAtLeast(0f)
            val freeY = (this.size.height - tileH).coerceAtLeast(0f)
            val anchorX = freeX * pos.x
            val anchorY = freeY * pos.y
            // background-repeat: NO_REPEAT draws a single tile at the
            // anchor; the *_REPEAT / SPACE / ROUND variants tile across
            // the box. SPACE and ROUND get the same approximation as
            // REPEAT here — accurate gap distribution / radius rounding
            // is a follow-up but the tiled fill alone restores the
            // RepeatSpaceRound fixture's bg-color underlay parity.
            val tileX = repeat == BackgroundRepeatConfig.REPEAT ||
                        repeat == BackgroundRepeatConfig.REPEAT_X ||
                        repeat == BackgroundRepeatConfig.SPACE ||
                        repeat == BackgroundRepeatConfig.ROUND
            val tileY = repeat == BackgroundRepeatConfig.REPEAT ||
                        repeat == BackgroundRepeatConfig.REPEAT_Y ||
                        repeat == BackgroundRepeatConfig.SPACE ||
                        repeat == BackgroundRepeatConfig.ROUND
            val xs: List<Float> = if (tileX) {
                // Walk left from anchor, then right, until we cover [0, w].
                val out = mutableListOf<Float>()
                var x = anchorX
                while (x > -tileW) { out.add(x); x -= tileW }
                x = anchorX + tileW
                while (x < this.size.width) { out.add(x); x += tileW }
                out
            } else listOf(anchorX)
            val ys: List<Float> = if (tileY) {
                val out = mutableListOf<Float>()
                var y = anchorY
                while (y > -tileH) { out.add(y); y -= tileH }
                y = anchorY + tileH
                while (y < this.size.height) { out.add(y); y += tileH }
                out
            } else listOf(anchorY)
            val tileSize = androidx.compose.ui.geometry.Size(tileW, tileH)
            for (x in xs) for (y in ys) {
                drawRect(brush = nonNullBrush,
                         topLeft = androidx.compose.ui.geometry.Offset(x, y),
                         size = tileSize)
            }
        }
    }

    /**
     * Create a linear gradient Brush from configuration.
     *
     * CSS angle conversion:
     * - CSS: 0deg = to top (upward), 90deg = to right
     * - Compose: Uses start/end offsets
     *
     * @param gradient LinearGradient configuration
     * @param position Background position configuration
     * @param tileMode Tile mode for repeating
     * @return Brush for the gradient, or null if invalid
     */
    private fun createLinearGradientBrush(
        gradient: BackgroundImageConfig.LinearGradient,
        position: BackgroundPositionConfig = BackgroundPositionConfig(),
        tileMode: TileMode = TileMode.Clamp
    ): Brush? {
        if (gradient.colorStops.size < 2) return null

        // CSS spec (Images Module 4 §3.4.1): the gradient line passes through
        // the centre of the box at the requested angle, and is exactly long
        // enough so that its perpendicular at each end touches the corner of
        // the box closest to that end. CSS measures the angle clockwise from
        // "to top": 0deg = up (gradient ends at top), 90deg = right, etc.
        // We need a size-aware shader because the line's length and endpoints
        // depend on the actual draw bounds — using a hard-coded 1000-pixel
        // brush coordinate space (the previous implementation) parked the
        // start/end far outside small elements and clamped them all to the
        // first stop.
        val cssRad = (gradient.angle * PI.toFloat() / 180f)
        // CSS direction unit vector in screen coordinates (Y down).
        // 0deg = "to top" = (0, -1); 90deg = "to right" = (+1, 0); 180deg = (0, +1).
        val dirX = sin(cssRad)
        val dirY = -cos(cssRad)
        val colors = gradient.colorStops.map { it.color }
        val stops = gradient.colorStops.map { it.position }

        // Background-position offset is in 0..1 fractions of the box.
        // We translate the gradient line by that fraction of the box size.
        val posX = position.x
        val posY = position.y

        return object : ShaderBrush() {
            override fun createShader(size: Size): Shader {
                val w = size.width
                val h = size.height
                // CSS gradient-line length: |W·sinθ| + |H·cosθ|. This
                // guarantees the perpendiculars at each end touch the
                // matching corners of the box.
                val lineLen = abs(w * sin(cssRad)) + abs(h * cos(cssRad))
                val cx = w / 2f + posX * w
                val cy = h / 2f + posY * h
                val sx = cx - dirX * lineLen / 2f
                val sy = cy - dirY * lineLen / 2f
                val ex = cx + dirX * lineLen / 2f
                val ey = cy + dirY * lineLen / 2f
                return LinearGradientShader(
                    from = Offset(sx, sy),
                    to = Offset(ex, ey),
                    colors = colors,
                    colorStops = stops,
                    tileMode = tileMode
                )
            }
        }
    }

    /**
     * Create a radial gradient Brush from configuration.
     *
     * @param gradient RadialGradient configuration
     * @param position Background position configuration
     * @param tileMode Tile mode for repeating
     * @return Brush for the gradient, or null if invalid
     */
    private fun createRadialGradientBrush(
        gradient: BackgroundImageConfig.RadialGradient,
        position: BackgroundPositionConfig = BackgroundPositionConfig(),
        tileMode: TileMode = TileMode.Clamp
    ): Brush? {
        if (gradient.colorStops.size < 2) return null

        // CSS radial gradients have two distinct radii (rx, ry) when the
        // ending shape is `ellipse` (the default), and a single radius
        // (r = rx = ry) when it's `circle`. The size keyword (closest-side
        // / closest-corner / farthest-side / farthest-corner — or simply
        // <length>{1,2}, currently unsupported) decides how far each radius
        // extends. Compose's Brush.radialGradient is circular-only, so we
        // emit a custom RadialGradientShader and pre-stretch the bounds to
        // simulate the elliptical shape.
        val colors = gradient.colorStops.map { it.color }
        val stops = gradient.colorStops.map { it.position }
        val shape = gradient.shape ?: BackgroundImageConfig.RadialShape.ELLIPSE
        val sizeKw = gradient.size ?: BackgroundImageConfig.RadialSize.FARTHEST_CORNER
        val cxFrac = gradient.centerX
        val cyFrac = gradient.centerY

        return object : ShaderBrush() {
            override fun createShader(size: Size): Shader {
                val w = size.width
                val h = size.height
                val cx = cxFrac * w
                val cy = cyFrac * h
                // Distances from centre to each side / corner — CSS spec.
                val dLeft = cx; val dRight = w - cx
                val dTop = cy; val dBottom = h - cy
                val dxClosest = kotlin.math.min(dLeft, dRight)
                val dxFarthest = kotlin.math.max(dLeft, dRight)
                val dyClosest = kotlin.math.min(dTop, dBottom)
                val dyFarthest = kotlin.math.max(dTop, dBottom)
                val (rx, ry) = when (shape) {
                    BackgroundImageConfig.RadialShape.ELLIPSE -> when (sizeKw) {
                        BackgroundImageConfig.RadialSize.CLOSEST_SIDE ->
                            dxClosest to dyClosest
                        BackgroundImageConfig.RadialSize.FARTHEST_SIDE ->
                            dxFarthest to dyFarthest
                        BackgroundImageConfig.RadialSize.CLOSEST_CORNER -> {
                            // CSS spec: ellipse with the closest-side aspect
                            // ratio (a:b = dxClosest:dyClosest) passing through
                            // the closest corner at (dxClosest, dyClosest).
                            // Solving (dxC/rx)² + (dyC/ry)² = 1 with rx/ry =
                            // dxC/dyC simplifies to rx = dxC·√2, ry = dyC·√2.
                            val k = kotlin.math.sqrt(2f)
                            (dxClosest * k) to (dyClosest * k)
                        }
                        BackgroundImageConfig.RadialSize.FARTHEST_CORNER -> {
                            // Same derivation with the farthest corner.
                            val k = kotlin.math.sqrt(2f)
                            (dxFarthest * k) to (dyFarthest * k)
                        }
                    }
                    BackgroundImageConfig.RadialShape.CIRCLE -> {
                        val r = when (sizeKw) {
                            BackgroundImageConfig.RadialSize.CLOSEST_SIDE -> kotlin.math.min(dxClosest, dyClosest)
                            BackgroundImageConfig.RadialSize.FARTHEST_SIDE -> kotlin.math.max(dxFarthest, dyFarthest)
                            BackgroundImageConfig.RadialSize.CLOSEST_CORNER ->
                                kotlin.math.sqrt(dxClosest * dxClosest + dyClosest * dyClosest)
                            BackgroundImageConfig.RadialSize.FARTHEST_CORNER ->
                                kotlin.math.sqrt(dxFarthest * dxFarthest + dyFarthest * dyFarthest)
                        }
                        r to r
                    }
                }
                // RadialGradientShader is circular only. To simulate an
                // ellipse with rx ≠ ry, build a circular shader of radius
                // max(rx,ry) centred at (cx,cy) and pre-multiply by a local
                // matrix that scales one axis (rx/ry or ry/rx). The matrix
                // is applied to the SHADER output, so we want the inverse:
                // a matrix that maps draw-space (x,y) → shader-space scaled
                // such that the elliptical isolines become circular.
                val rMax = kotlin.math.max(rx.coerceAtLeast(1e-3f), ry.coerceAtLeast(1e-3f))
                val sx = rMax / rx.coerceAtLeast(1e-3f)
                val sy = rMax / ry.coerceAtLeast(1e-3f)
                val shader = RadialGradientShader(
                    center = Offset(cx, cy),
                    radius = rMax,
                    colors = colors,
                    colorStops = stops,
                    tileMode = tileMode
                )
                if (sx == 1f && sy == 1f) return shader
                // Apply the axis pre-scale around (cx,cy).
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

    /**
     * Create a sweep (conic) gradient Brush from configuration.
     *
     * Note: Compose's sweepGradient does not support:
     * - TileMode (repeating gradients)
     * - Starting angle offset
     *
     * @param gradient ConicGradient configuration
     * @param position Background position configuration
     * @return Brush for the gradient, or null if invalid
     */
    private fun createSweepGradientBrush(
        gradient: BackgroundImageConfig.ConicGradient,
        @Suppress("UNUSED_PARAMETER") position: BackgroundPositionConfig = BackgroundPositionConfig()
    ): Brush? {
        if (gradient.colorStops.size < 2) return null

        // The previous implementation multiplied the centre fraction by
        // a hard-coded 500px and passed Offset(cx*500, cy*500) into
        // `Brush.sweepGradient(center=...)`. For elements smaller than
        // 500px (e.g. the 160×160 Audit_ConicFrom30degAt25 box) that
        // parked the sweep centre way off-canvas, so non-default `at
        // <pos>` values just looked centred. Use a ShaderBrush with
        // `createShader(size)` so the centre is always the actual
        // fraction × draw-size, matching how the linear / radial brushes
        // already work.
        val cxFrac = gradient.centerX
        val cyFrac = gradient.centerY
        val colors = gradient.colorStops.map { it.color }
        val stops = gradient.colorStops.map { it.position }
        return object : ShaderBrush() {
            override fun createShader(size: Size): Shader {
                val cx = cxFrac * size.width
                val cy = cyFrac * size.height
                return android.graphics.SweepGradient(cx, cy, colors.map { it.toArgb() }.toIntArray(),
                                                      if (stops.isEmpty()) null else stops.toFloatArray())
            }
        }
    }
}

package com.styleconverter.runtime.color

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
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
import com.styleconverter.runtime.background.RepeatingGradientHelper
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
 * - Image URLs: Not rendered (use Coil or similar for image loading)
 * - Repeating conic gradients: Compose sweepGradient doesn't support TileMode
 *
 * Multiple background layers paint back-to-front per css-backgrounds-3 §2
 * (last source layer at the bottom, first on top), each with ITS OWN
 * background-size / background-repeat entry from the comma lists (§2.3
 * cyclic pairing) — including the `space` / `round` tile distribution
 * (BackgroundTileMath). Wave-9 closed the "only the first gradient is
 * applied" deferral.
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
        //
        // background-clip: padding-box / content-box (css-backgrounds-3
        // §3.11) shrinks the PAINTING AREA — the fill starts inside the
        // border band (padding-box) or inside border+padding (content-box)
        // and the uncovered ring stays transparent. Modifier.background
        // always floods the full box, so the clipped case draws an inset
        // rect via drawBehind instead (same paint position in the chain).
        config.backgroundColor?.let { color ->
            val insets = config.backgroundClipInsets
            result = if (insets != null && insets.hasInsets) {
                result.drawBehind {
                    val l = insets.left.toPx()
                    val t = insets.top.toPx()
                    val w = (size.width - l - insets.right.toPx()).coerceAtLeast(0f)
                    val h = (size.height - t - insets.bottom.toPx()).coerceAtLeast(0f)
                    drawRect(color = color, topLeft = Offset(l, t), size = Size(w, h))
                }
            } else {
                result.background(color)
            }
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
                        val brush = brushFor(image) ?: return@mapIndexedNotNull null
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
                // Back-to-front paint per css-backgrounds-3 §2: iterate the
                // source list in REVERSE so the first source layer is the
                // LAST modifier appended (Compose chains paint later entries
                // on top). Each layer gets its OWN size/repeat entry from
                // the §2.3 comma lists via layerValue's cyclic pairing.
                for (idx in config.backgroundImages.indices.reversed()) {
                    result = applyBackgroundImage(
                        modifier = result,
                        image = config.backgroundImages[idx],
                        config = config,
                        layerSize = layerValue(config.backgroundSizes, idx)
                            ?: config.backgroundSize,
                        layerRepeat = layerValue(config.backgroundRepeats, idx)
                            ?: BackgroundRepeatAxes.from(config.backgroundRepeat)
                    )
                }
            }
        }

        return result
    }

    /**
     * css-backgrounds-3 §2.3 list pairing: entry `i` of a comma list styles
     * background-image layer `i`; when the list is SHORTER than the image
     * list "the missing values are filled in by repeating the list" —
     * hence the modulo. Empty list → null (caller falls back to the legacy
     * single-value field). Internal for direct JVM pinning.
     */
    internal fun <T> layerValue(list: List<T>, index: Int): T? =
        if (list.isEmpty()) null else list[index % list.size]

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
    private fun brushFor(image: BackgroundImageConfig): Brush? {
        return when (image) {
            // Gradient brushes take no background-position: position moves
            // the tile (css-backgrounds-3 §3.6), never the shader inside it.
            is BackgroundImageConfig.LinearGradient ->
                createLinearGradientBrush(image, TileMode.Clamp)
            is BackgroundImageConfig.RadialGradient ->
                createRadialGradientBrush(image, TileMode.Clamp)
            is BackgroundImageConfig.ConicGradient ->
                createSweepGradientBrush(image)
            is BackgroundImageConfig.Url -> null
            is BackgroundImageConfig.None -> null
        }
    }

    /**
     * Apply ONE background-image layer (gradient or URL) to a Modifier.
     *
     * @param modifier Base modifier
     * @param image Background image config for this layer
     * @param config Full color config (position + shared fields)
     * @param layerSize THIS layer's background-size entry (§2.3 pairing)
     * @param layerRepeat THIS layer's two-axis background-repeat entry
     * @param size Optional size for gradient calculations
     */
    private fun applyBackgroundImage(
        modifier: Modifier,
        image: BackgroundImageConfig,
        config: ColorConfig,
        layerSize: BackgroundSizeConfig = config.backgroundSize,
        layerRepeat: BackgroundRepeatAxes = BackgroundRepeatAxes.from(config.backgroundRepeat),
        size: Size = Size(500f, 500f)
    ): Modifier {
        // For NON-repeating gradients we always want TileMode.Clamp regardless
        // of background-repeat. CSS background-repeat only re-tiles a gradient
        // when an explicit background-size makes the tile smaller than the
        // box; that pathway is handled by the sized-tile drawBehind branch
        // below (tile-pinned shader + BackgroundTileMath plans), so honouring
        // `repeat` at the SHADER level too would double-tile the gradient and
        // produce wrap-around colour bands rather than the single per-tile
        // fill CSS specifies.
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
                    createLinearGradientBrush(image, gradientTileMode)
            }
            is BackgroundImageConfig.RadialGradient -> {
                if (image.repeating)
                    RepeatingGradientHelper.createRepeatingRadialGradient(
                        centerX = image.centerX, centerY = image.centerY,
                        colorStops = image.colorStops, size = size)
                else
                    createRadialGradientBrush(image, gradientTileMode)
            }
            is BackgroundImageConfig.ConicGradient -> {
                if (image.repeating)
                    RepeatingGradientHelper.createRepeatingConicGradient(
                        centerX = image.centerX, centerY = image.centerY,
                        startAngle = image.angle, colorStops = image.colorStops, size = size)
                else
                    createSweepGradientBrush(image)
            }
            is BackgroundImageConfig.Url -> null
            is BackgroundImageConfig.None -> null
        }
        if (brush == null) return modifier
        // Kotlin smart-cast doesn't propagate into the `drawBehind`
        // lambda below, so re-bind a non-null local for clarity.
        val nonNullBrush: Brush = brush

        // CSS `background-size` + `background-position` decide where the
        // brush tile draws inside the box. When THIS LAYER's size entry
        // carries explicit dimensions (and the layer isn't a
        // `repeating-*-gradient` — those bake their own tile into the
        // brush), we can't just call `Modifier.background(brush)` because
        // that always fills the entire element. Instead we draw the brush
        // into sized tiles via `Modifier.drawBehind`. Without
        // background-size or with size=auto/cover/contain we fall back to
        // the full-fill path (gradients have no intrinsic dimensions, so
        // auto/cover/contain all resolve to the box — css-backgrounds-3
        // §3.9) so previously-correct fixtures don't regress.
        val sized = layerSize as? BackgroundSizeConfig.Dimensions
        // Make the comment above TRUE in code (wave-1 skeptic finding): a
        // `repeating-*-gradient` brush bakes FIXED pixel endpoints computed
        // against a default 500x500 size (RepeatingGradientHelper), so
        // pinShaderToTile cannot re-pin it — each tile would sample a
        // near-constant slice of a ~707px ramp (solid first-stop colour
        // instead of stripes). Until the repeating helpers accept a tile
        // size, repeating gradients keep the full-box path (the pre-wave
        // behavior); parity gap tracked in the wave-1 follow-ups.
        val isRepeating = when (image) {
            is BackgroundImageConfig.LinearGradient -> image.repeating
            is BackgroundImageConfig.RadialGradient -> image.repeating
            is BackgroundImageConfig.ConicGradient -> image.repeating
            else -> false
        }
        if (sized == null || isRepeating) {
            return modifier.background(nonNullBrush)
        }
        val pos = config.backgroundPosition
        return modifier.drawBehind {
            // Resolve the per-axis tile size against the box. Dp values
            // (Dp? width / height) are taken verbatim; percent values are
            // 0..1 FRACTIONS of the box (the Dimensions contract — the
            // previous `* it / 100f` here double-divided, shrinking `50%`
            // to 0.5% of the box); a missing axis is `auto`, which for
            // gradient brushes (no intrinsic size) resolves to the
            // container axis (Auditor round 8 finding).
            val tileW = sized.width?.toPx()
                ?: sized.widthPercent?.let { this.size.width * it }
                ?: this.size.width
            val tileH = sized.height?.toPx()
                ?: sized.heightPercent?.let { this.size.height * it }
                ?: this.size.height
            if (tileW <= 0f || tileH <= 0f) return@drawBehind
            // CSS background-position: percent of (containerSize − tileSize)
            // free space, plus any absolute px offset (css-backgrounds-3
            // §3.6 — `background-position-x: 20px` is a raw edge offset).
            // NO clamp: when the tile is LARGER than the box, free space is
            // negative and the anchor goes negative too — `100%` of a 320px
            // tile in a 160px box anchors at −160 so the tile's END edge
            // aligns with the box's end edge, matching Chromium and the iOS
            // port (BackgroundImageGeometry.axisOffset). The old
            // coerceAtLeast(0f) silently left oversized tiles start-aligned
            // (wave-1 skeptic finding, both lenses).
            val freeX = this.size.width - tileW
            val freeY = this.size.height - tileH
            val anchorX = freeX * pos.x + pos.xOffset.toPx()
            val anchorY = freeY * pos.y + pos.yOffset.toPx()
            // Per-axis §3.7 tile plans: REPEAT walks edge-to-edge, SPACE
            // fits whole tiles + equal gaps, ROUND rescales the tile so a
            // whole count fills the axis, NO_REPEAT anchors a single tile.
            // Pure math — planTilePass combines the two axisPlans and is
            // pinned by ColorApplierGradientTileTest.
            val pass = planTilePass(this.size, tileW, tileH, anchorX, anchorY, layerRepeat)
            // Empty origin list = degenerate plan (nothing to draw).
            if (pass.origins.isEmpty()) return@drawBehind
            // Runaway-lattice guard (wave-1 skeptic finding): a tiny explicit
            // background-size on a large element (1px tiles on a 390x844 box
            // = ~330k origins) would issue hundreds of thousands of draw
            // calls PER FRAME on the UI thread — a multi-second stall. Past
            // the cap we degrade to one full-box fill (the pre-wave
            // rendering): visually wrong in the same way the old code was,
            // but never a hang. Mirrors iOS's tileCap=4096 in
            // BackgroundImageGeometry.tileRects.
            if (pass.origins.size > 4096) {
                drawRect(brush = nonNullBrush)
                return@drawBehind
            }
            // Pin the gradient's shader geometry to the TILE, not the box:
            // css-images-4 §3.4.1 sizes the gradient line against the
            // "gradient box", which for a background is the background-size
            // tile — NOT the element. Without the pin every tile's
            // createShader received the full DrawScope size, so a 30px tile
            // showed only the top slice of a 120px-long gradient (solid
            // first-stop colour instead of the full ramp).
            val tileBrush = pinShaderToTile(nonNullBrush, pass.tileSize)
            // css-backgrounds-3 §2.2: the background PAINTING AREA is the
            // border box — tiles walking past an edge are clipped, never
            // painted outside. The REPEAT plan deliberately overhangs both
            // edges (grid coverage), so without this clip the overhanging
            // tiles bled up to one tile-width past the box (painted bbox
            // 210px on a 200px box in the wave audit).
            clipRect(0f, 0f, pass.clip.width, pass.clip.height) {
                for (origin in pass.origins) {
                    // TRANSLATE the draw space per tile instead of offsetting
                    // the rect: the pinned ShaderBrush anchors its shader at
                    // the current origin, so translating renders the FULL
                    // gradient inside every tile (offsetting the rect would
                    // sample one box-anchored gradient — every tile but the
                    // first showed clamped edge colours).
                    translate(left = origin.x, top = origin.y) {
                        drawRect(brush = tileBrush,
                                 topLeft = androidx.compose.ui.geometry.Offset.Zero,
                                 size = pass.tileSize)
                    }
                }
            }
        }
    }

    /**
     * The resolved draw commands for ONE sized-tile background pass:
     * [clip] is the painting-area rect the tiles are clipped to
     * (css-backgrounds-3 §2.2 — the border box), [tileSize] the per-tile
     * draw size after any `round` rescale, [origins] the cartesian product
     * of the two per-axis §3.7 plans. Pure data so plain JVM tests can pin
     * that overhanging REPEAT tiles stay bounded by [clip].
     */
    internal data class TilePass(
        val clip: Size,
        val tileSize: Size,
        val origins: List<Offset>
    )

    /**
     * Combine the two per-axis BackgroundTileMath plans into one [TilePass].
     * Kept free of DrawScope so ColorApplierGradientTileTest can assert the
     * geometry (overflowing origins + border-box clip) on the JVM — the
     * drawBehind lambda above is a thin consumer of this plan.
     */
    internal fun planTilePass(
        box: Size,
        tileW: Float,
        tileH: Float,
        anchorX: Float,
        anchorY: Float,
        repeat: BackgroundRepeatAxes
    ): TilePass {
        // One §3.7 plan per axis — REPEAT/SPACE/ROUND/NO_REPEAT semantics
        // live in BackgroundTileMath (pinned by BackgroundTileMathTest).
        val planX = BackgroundTileMath.axisPlan(box.width, tileW, anchorX, repeat.x)
        val planY = BackgroundTileMath.axisPlan(box.height, tileH, anchorY, repeat.y)
        // Cartesian product: every X origin pairs with every Y origin
        // (background tiling is a rectangular grid, css-backgrounds-3 §3.7).
        val origins = planX.origins.flatMap { x -> planY.origins.map { y -> Offset(x, y) } }
        // Clip is always the full border box — the painting area for the
        // default background-clip (§2.2); tile math never widens it.
        return TilePass(clip = box, tileSize = Size(planX.tileSize, planY.tileSize), origins = origins)
    }

    /**
     * Wrap a gradient [brush] so its shader is ALWAYS built against the
     * background-size [tile], no matter what draw size Compose hands to
     * createShader. css-images-4 §3.4.1: gradient-line length / radial
     * radii / sweep centres resolve against the gradient box = the TILE
     * when background-size is explicit (matches the web runtime, which
     * rasterizes the gradient into a tile-sized canvas). Non-shader
     * brushes (solid colours) pass through untouched — they have no
     * size-dependent geometry to pin.
     */
    internal fun pinShaderToTile(brush: Brush, tile: Size): Brush {
        // Identity for non-shader brushes — nothing size-dependent to pin.
        if (brush !is ShaderBrush) return brush
        // Explicit re-bind: smart-casts don't reliably propagate into
        // object-expression captures (same pattern as nonNullBrush above).
        val shaderBrush: ShaderBrush = brush
        return object : ShaderBrush() {
            // Delegate with the TILE size — the DrawScope's element size
            // (the `size` arg) is deliberately ignored per the doc above.
            override fun createShader(size: Size): Shader = shaderBrush.createShader(tile)
        }
    }

    /**
     * Create a linear gradient Brush from configuration.
     *
     * CSS angle conversion:
     * - CSS: 0deg = to top (upward), 90deg = to right
     * - Compose: Uses start/end offsets
     *
     * NOTE: background-position is deliberately NOT a parameter. Per
     * css-backgrounds-3 §3.6 position places the background-image TILE
     * inside the box; it never shifts the gradient geometry INSIDE its
     * own tile (css-images-4 §3.4.1 centres the gradient line on the
     * gradient box unconditionally). The previous posX·w / posY·h centre
     * shift here double-applied the position — once in the tile translate,
     * once inside the shader — skewing the ramp for any non-0 position.
     *
     * @param gradient LinearGradient configuration
     * @param tileMode Tile mode for repeating
     * @return Brush for the gradient, or null if invalid
     */
    private fun createLinearGradientBrush(
        gradient: BackgroundImageConfig.LinearGradient,
        tileMode: TileMode = TileMode.Clamp
    ): Brush? {
        if (gradient.colorStops.size < 2) return null

        // Shader inputs are size-independent; hoist them out of the
        // per-frame createShader call.
        val colors = gradient.colorStops.map { it.color }
        val stops = gradient.colorStops.map { it.position }
        val angle = gradient.angle

        return object : ShaderBrush() {
            override fun createShader(size: Size): Shader {
                // Endpoint math is pure and lives in linearGradientPoints
                // so plain JVM tests can pin it (android.graphics shaders
                // can't be built in non-instrumented tests).
                val (from, to) = linearGradientPoints(angle, size)
                return LinearGradientShader(
                    from = from,
                    to = to,
                    colors = colors,
                    colorStops = stops,
                    tileMode = tileMode
                )
            }
        }
    }

    /**
     * css-images-4 §3.4.1 gradient-line endpoints for a linear gradient of
     * [angleDeg] over a gradient box of [size]: the line passes through the
     * CENTRE of the box at the requested angle and is exactly long enough
     * (|W·sinθ| + |H·cosθ|) that the perpendicular at each end touches the
     * corner closest to that end. CSS measures the angle clockwise from
     * "to top": 0deg = up, 90deg = right. A size-aware shader is required
     * because length/endpoints depend on the actual draw bounds — the old
     * hard-coded 1000px brush space parked the endpoints far outside small
     * elements and clamped everything to the first stop. Pure math —
     * pinned by ColorApplierGradientTileTest.
     */
    internal fun linearGradientPoints(angleDeg: Float, size: Size): Pair<Offset, Offset> {
        // Degrees → radians once; both the direction vector and the
        // corner-touching length formula consume the same angle.
        val cssRad = angleDeg * PI.toFloat() / 180f
        // CSS direction unit vector in screen coordinates (Y down):
        // 0deg = "to top" = (0, -1); 90deg = "to right" = (+1, 0).
        val dirX = sin(cssRad)
        val dirY = -cos(cssRad)
        // §3.4.1 gradient-line length: |W·sinθ| + |H·cosθ| guarantees the
        // end perpendiculars touch the matching corners of the box.
        val lineLen = abs(size.width * sin(cssRad)) + abs(size.height * cos(cssRad))
        // Line centre = BOX centre, unconditionally (§3.4.1). Position
        // placement happens in the tile translate, never here.
        val cx = size.width / 2f
        val cy = size.height / 2f
        // Walk half the line each way from the centre along the direction.
        return Offset(cx - dirX * lineLen / 2f, cy - dirY * lineLen / 2f) to
               Offset(cx + dirX * lineLen / 2f, cy + dirY * lineLen / 2f)
    }

    /**
     * Create a radial gradient Brush from configuration.
     *
     * background-position is NOT a parameter (same rationale as the linear
     * builder): the radial centre comes from the gradient's own `at <pos>`
     * (css-images-4 §3.2), resolved against the gradient box — background
     * -position only moves the tile, outside this brush.
     *
     * @param gradient RadialGradient configuration
     * @param tileMode Tile mode for repeating
     * @return Brush for the gradient, or null if invalid
     */
    private fun createRadialGradientBrush(
        gradient: BackgroundImageConfig.RadialGradient,
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
     * background-position is NOT a parameter (same rationale as the linear
     * builder): the sweep centre comes from conic-gradient's own `at <pos>`
     * (css-images-4 §3.3) — background-position only moves the tile.
     *
     * @param gradient ConicGradient configuration
     * @return Brush for the gradient, or null if invalid
     */
    private fun createSweepGradientBrush(
        gradient: BackgroundImageConfig.ConicGradient
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
        // CSS `from` angle, degrees. 0 when the author omitted `from …`.
        val fromDeg = gradient.angle
        return object : ShaderBrush() {
            override fun createShader(size: Size): Shader {
                val cx = cxFrac * size.width
                val cy = cyFrac * size.height
                val shader = android.graphics.SweepGradient(cx, cy, colors.map { it.toArgb() }.toIntArray(),
                                                            if (stops.isEmpty()) null else stops.toFloatArray())
                // Convention fix: android.graphics.SweepGradient places its
                // 0-position at 3 o'clock (+x axis) sweeping clockwise,
                // while CSS conic-gradient() (css-images-4 §3.3) starts at
                // 12 o'clock. On the visual-test Gradient_Conic wheel this
                // rendered every hue rotated +90° vs web (red at 3 o'clock
                // instead of 12; Android-web SSIM 0.88 / 14.9% pixels).
                // Rotate the shader by (from - 90)° around the centre:
                // -90° re-homes 0deg to 12 o'clock, and the previously
                // ignored CSS `from <angle>` offset composes on top.
                val rotate = android.graphics.Matrix()
                rotate.setRotate(conicSweepRotationDegrees(fromDeg), cx, cy)
                shader.setLocalMatrix(rotate)
                return shader
            }
        }
    }

    /**
     * Degrees to rotate an android SweepGradient so it matches a CSS
     * conic-gradient whose `from` angle is [cssFromDeg].
     *
     * CSS (css-images-4 §3.3): conic 0deg points UP (12 o'clock),
     * increasing clockwise. Android SweepGradient: position 0 points
     * RIGHT (3 o'clock = 90deg in CSS terms), increasing clockwise.
     * Matrix.setRotate is clockwise-positive in screen coordinates, so
     * the correction is a plain (from − 90) — pure math, JVM-testable.
     */
    internal fun conicSweepRotationDegrees(cssFromDeg: Float): Float = cssFromDeg - 90f
}

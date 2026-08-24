package com.styleconverter.runtime.color

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
// BlendMode.Plus powers the cross-fade() additive compositor (weighted
// premultiplied SUM per css-images-4 §2.6.2 — see applyCrossFade).
import androidx.compose.ui.graphics.BlendMode
// drawImage addresses destination geometry in integer px (same convention
// as MaskApplier's tile lattice — the other consumer of these plans).
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
// url() background layers: DataUri gates the scheme (only data: decodes
// synchronously), SyncImageDecode owns the shared decode + LRU (the same
// pipeline the wave-3 url() mask fix pinned), IRLog keeps the remote-url
// no-op loud without breaking the plain-JVM suite.
import com.styleconverter.runtime.core.images.DataUri
import com.styleconverter.runtime.core.images.SyncImageDecode
import com.styleconverter.runtime.core.ir.IRLog
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Applies color-related styling to Compose Modifiers.
 *
 * ## Supported Features
 * - Solid background colors
 * - Linear gradients (including repeating)
 * - Radial gradients (including repeating)
 * - Conic/sweep gradients (repeating not fully supported)
 * - url() images from `data:` URIs (synchronous decode via
 *   SyncImageDecode — the wave-3 mask pipeline — tiled through the same
 *   BackgroundTileMath plans the gradient path draws with)
 * - Opacity/alpha
 *
 * ## Limitations
 * - Remote (http/file/relative) image URLs: documented, once-logged no-op —
 *   async fetch cannot be capture-deterministic (first frame would race)
 * - Repeating conic gradients: SweepGradient has no TileMode, so the
 *   §3.4.4 copies are materialised as explicit stops (wave 47 — real
 *   period, capped at GradientStopResolver.MAX_COPIES with the §3.3.3
 *   average-solid degrade past the cap)
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
     * 1. Opacity FIRST (outermost in this group) — OpacityApplier's
     *    transparency group wraps everything BELOW it in the chain (and
     *    the children). Applying it first means the bg paint, the
     *    gradient draw, AND the children all render INSIDE the group, so
     *    `opacity: 0` actually hides everything. Previously opacity was
     *    applied LAST here, which put the alpha layer INSIDE the bg
     *    drawer — bg painted at full opacity and only the (text)
     *    children faded. `Edge_ZeroOpacity` rendered as a fully-opaque
     *    red rectangle on Android while iOS / web correctly produced a
     *    fully-transparent surface (SSIM 1.00). Wave-43 V2 swapped the
     *    mechanism from `Modifier.alpha` — which is graphicsLayer(alpha,
     *    clip=TRUE) in bytecode and cropped overflowing children at the
     *    element's bounds (css-color/composited-filters-under-opacity,
     *    android-ref 0.9401) — to OpacityApplier's UNBOUNDED saveLayer
     *    group; CSS opacity never clips (css-color-4 §2.2).
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

        // 1. Opacity FIRST → the transparency group wraps bg + children
        // below it. Delegated to OpacityApplier (the dedicated per-property
        // applier, twin of iOS color/OpacityApplier.swift): an unbounded
        // saveLayer group per css-color-4 §2.2 — never a clipping
        // graphicsLayer (see OpacityApplier's header for the bytecode
        // proof that Modifier.alpha ≡ graphicsLayer(alpha, clip=true)).
        config.opacity?.let { alpha ->
            result = OpacityApplier.applyOpacity(result, alpha)
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
     * Apply opacity to a Modifier — kept as a public convenience surface;
     * the real semantics (clamp, opacity-1 fast path, the UNBOUNDED
     * css-color-4 §2.2 transparency group that replaced the clipping
     * `Modifier.alpha`) live in the dedicated [OpacityApplier].
     *
     * @param modifier The base modifier
     * @param alpha Opacity value (0.0-1.0; out-of-range clamps per spec)
     * @return Modified Modifier with the transparency group applied
     */
    fun applyOpacity(modifier: Modifier, alpha: Float): Modifier {
        return OpacityApplier.applyOpacity(modifier, alpha)
    }

    /**
     * Build the Brush for a single background-image layer without any
     * sized-tile drawing. Used by the per-layer blend-mode path which
     * needs a brush per layer to feed into a saveLayer pipeline.
     * Returns null for Url / None layers (no image loading wired up).
     * Repeating gradients render their REAL §3.4.4 lattice here since
     * wave 47 — the resolver materialises the copies inside the factory,
     * so the blend path no longer approximates them as one clamp ramp.
     */
    private fun brushFor(image: BackgroundImageConfig): Brush? {
        return when (image) {
            // Gradient brushes take no background-position: position moves
            // the tile (css-backgrounds-3 §3.6), never the shader inside it.
            // Wave 47: repeating flavours resolve their real §3.4.4 period
            // inside the factories now, so the blend path composites the
            // true lattice too (previously approximated as non-repeating).
            is BackgroundImageConfig.LinearGradient ->
                createLinearGradientBrush(image)
            is BackgroundImageConfig.RadialGradient ->
                createRadialGradientBrush(image)
            is BackgroundImageConfig.ConicGradient ->
                createSweepGradientBrush(image)
            is BackgroundImageConfig.Url -> {
                // The per-layer blend pipeline needs a Brush; a bitmap tile
                // lattice has none. Wiring url() layers INTO the saveLayer
                // blend stack is future work — log so the drop is never
                // silent (no fixture pairs blend-mode with url() yet).
                warnOnce(image.url, "background-image: url() layers are not yet " +
                    "composited in the background-blend-mode path — layer skipped")
                null
            }
            // A bare <color> image (cross-fade argument) — a solid brush.
            is BackgroundImageConfig.SolidColor ->
                androidx.compose.ui.graphics.SolidColor(image.color)
            is BackgroundImageConfig.CrossFade -> {
                // cross-fade() is a MULTI-PASS additive composite, not a
                // single Brush — it renders via the dedicated drawBehind
                // path in applyBackgroundImage. Wiring it into the
                // per-layer blend-mode saveLayer stack is future work; log
                // so the drop is never silent.
                warnOnce("cross-fade", "background-image: cross-fade() layers are " +
                    "not yet composited in the background-blend-mode path — layer skipped")
                null
            }
            // `none` IS the rendered result per css-backgrounds-3 §3.1
            // (an image layer that draws nothing) — not a fallthrough.
            is BackgroundImageConfig.None -> null
        }
    }

    /**
     * cross-fade() compositor (css-images-4 §2.6.2, A-RC2). The spec
     * defines the result as the WEIGHTED SUM of the premultiplied images:
     * `result = Σ wᵢ × premult(imageᵢ)` — including alpha, so six 10%
     * layers of an opaque gradient yield EXACTLY alpha 0.6 (the
     * target-alpha WPT). Sequential src-over stacking would give
     * 1−0.9⁶ ≈ 0.47 — wrong — so each image draws with alpha = wᵢ and
     * BlendMode.Plus (component-wise premultiplied ADD) inside an
     * isolated saveLayer (transparent-black base). Premultiplied-alpha
     * behaviour (the premultiplied-alpha WPT: 1%-alpha red + opaque
     * green → translucent green, near-zero red) falls out of Plus
     * operating on premultiplied components — the same math as the
     * SwiftUI twin's .plusLighter ZStack.
     */
    private fun applyCrossFade(
        modifier: Modifier,
        crossFade: BackgroundImageConfig.CrossFade
    ): Modifier {
        return modifier.drawBehind {
            drawIntoCanvas { canvas ->
                // Isolate the additive stack from the destination: Plus
                // must accumulate over TRANSPARENT, not over whatever the
                // box already painted (background-color layers below).
                canvas.saveLayer(
                    androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height),
                    androidx.compose.ui.graphics.Paint()
                )
                for (entry in crossFade.entries) {
                    // Each sub-image contributes wᵢ × its premultiplied
                    // pixels. brushFor covers gradient/color sub-images;
                    // url() sub-images have no brush (logged inside).
                    val brush = brushFor(entry.image) ?: continue
                    drawRect(
                        brush = brush,
                        alpha = entry.weight,
                        blendMode = BlendMode.Plus
                    )
                }
                canvas.restore()
            }
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
     */
    private fun applyBackgroundImage(
        modifier: Modifier,
        image: BackgroundImageConfig,
        config: ColorConfig,
        layerSize: BackgroundSizeConfig = config.backgroundSize,
        layerRepeat: BackgroundRepeatAxes = BackgroundRepeatAxes.from(config.backgroundRepeat)
    ): Modifier {
        // url() layers take a dedicated bitmap-tile path (wave 9): the
        // brush pipeline below is gradient-only (shaders), while an image
        // layer draws decoded pixels through the SAME BackgroundTileMath
        // plans. Previously `Url -> null` fell through to `return modifier`
        // with no log — a silent no-op that violated the house rule and
        // left `background: red url(...)` painting only the red.
        if (image is BackgroundImageConfig.Url) {
            return applyUrlBackground(modifier, image.url, config, layerSize, layerRepeat)
        }
        // cross-fade() takes the dedicated multi-pass additive path — a
        // weighted premultiplied SUM cannot be expressed as one Brush.
        // Size/position/repeat knobs on a cross-fade layer are follow-up
        // work (no fixture pairs them yet); the composite fills the box.
        if (image is BackgroundImageConfig.CrossFade) {
            return applyCrossFade(modifier, image)
        }
        // Every gradient shader uses TileMode.Clamp: CSS background-repeat
        // only re-tiles a gradient when an explicit background-size makes
        // the tile smaller than the box — the sized-tile drawBehind branch
        // below (tile-pinned shader + BackgroundTileMath plans) — and the
        // `repeating-*-gradient()` FORM is materialised as explicit §3.4.4
        // stop copies inside the brush factories themselves (wave 47,
        // GradientStopResolver.expandRepeating), so no shader-level repeat
        // mode is ever wanted; it would double-tile the ramp.

        // Build the brush. All three flavours (plain AND repeating) route
        // through the size-aware factories — the RepeatingGradientHelper
        // dispatch is gone (wave 47): its fraction-range × diagonal period
        // could never carry the px period of `…, white 30px` (the
        // wave-46 gradient-border-box android-ref 0.646 defect) and its
        // centre-phased lattice ignored the §3.4.1 line start.
        val brush: Brush? = when (image) {
            is BackgroundImageConfig.LinearGradient -> createLinearGradientBrush(image)
            is BackgroundImageConfig.RadialGradient -> createRadialGradientBrush(image)
            is BackgroundImageConfig.ConicGradient -> createSweepGradientBrush(image)
            // A bare <color> image — solid fill of the layer box.
            is BackgroundImageConfig.SolidColor ->
                androidx.compose.ui.graphics.SolidColor(image.color)
            // Unreachable: url() layers returned through the bitmap-tile
            // path above; the arm exists only for `when` exhaustiveness.
            is BackgroundImageConfig.Url -> null
            // Unreachable: cross-fade() returned through applyCrossFade
            // above; the arm exists only for `when` exhaustiveness.
            is BackgroundImageConfig.CrossFade -> null
            // `none` draws nothing by definition (css-backgrounds-3 §3.1).
            is BackgroundImageConfig.None -> null
        }
        if (brush == null) return modifier
        // Kotlin smart-cast doesn't propagate into the `drawBehind`
        // lambda below, so re-bind a non-null local for clarity.
        val nonNullBrush: Brush = brush

        // CSS `background-size` + `background-position` + `background-repeat`
        // decide where the brush tile draws inside the box. The sized-tile
        // drawBehind path below is entered whenever a knob can move the
        // picture off the plain full-box fill — the routing predicate
        // gradientNeedsGeometry (the Compose mirror of iOS's
        // BackgroundImageApplier.gradientNeedsGeometry) decides. With no
        // knob set we keep `Modifier.background(brush)` BYTE-IDENTICAL to
        // the pre-wave behavior so knob-less gradient baselines never move.
        // When a knob is set WITHOUT an explicit size (sized == null), the
        // tile is the BOX: gradients have no intrinsic dimensions, so auto
        // (and cover/contain) resolve to the box — css-backgrounds-3 §3.9.
        // Previously this gate required explicit Dimensions, so
        // `background-position: 40px 0` without a size rendered UNSHIFTED
        // on Android while web/iOS wrapped the box-sized tile with a
        // visible seam at x=40 (wave-1 skeptic deferral, both lenses).
        val sized = layerSize as? BackgroundSizeConfig.Dimensions
        // Route through gradientNeedsGeometry so Compose and iOS agree on
        // WHEN tile geometry matters (explicit size, non-default position,
        // or a non-`repeat` axis). The old repeating-gradient exclusion is
        // GONE (wave 47): its whole rationale was RepeatingGradientHelper's
        // fixed 500×500 endpoints, which pinShaderToTile could not re-pin —
        // the new resolver-backed brushes are fully size-aware (the §3.4.4
        // copies re-materialise per createShader size), so a repeating
        // gradient with an explicit background-size tiles correctly like
        // the iOS predicate, which never had the exclusion.
        if (!gradientNeedsGeometry(layerSize, config.backgroundPosition, layerRepeat)) {
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
            // container axis (Auditor round 8 finding). `sized == null`
            // (position/repeat knob without an explicit size) resolves
            // BOTH axes to the box — §3.9 auto for an intrinsic-less
            // image, mirroring iOS's tile-equals-box routing; the
            // pinShaderToTile call below then pins to the box size, which
            // is exactly what Modifier.background would have handed the
            // shader (a no-op pin).
            val tileW = sized?.width?.toPx()
                ?: sized?.widthPercent?.let { this.size.width * it }
                ?: this.size.width
            val tileH = sized?.height?.toPx()
                ?: sized?.heightPercent?.let { this.size.height * it }
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
                pass.origins.forEachIndexed { i, origin ->
                    // TRANSLATE the draw space per tile instead of offsetting
                    // the rect: the pinned ShaderBrush anchors its shader at
                    // the current origin, so translating renders the FULL
                    // gradient inside every tile (offsetting the rect would
                    // sample one box-anchored gradient — every tile but the
                    // first showed clamped edge colours).
                    translate(left = origin.x, top = origin.y) {
                        // Draw the SNAPPED per-tile extent (drawSizes[i], from
                        // the plan's integer shared edges) — not tileSize:
                        // abutting AA'd rects on fractional edges leak a
                        // background seam (Repeat_Round straggler). The
                        // shader stays pinned to the fractional tileSize
                        // above; a snapped-wide tile just clamps its last
                        // sub-pixel column to the edge colour, which is
                        // invisible next to the adjacent tile's first stop.
                        drawRect(brush = tileBrush,
                                 topLeft = androidx.compose.ui.geometry.Offset.Zero,
                                 size = pass.drawSizes[i])
                    }
                }
            }
        }
    }

    // ==================== URL (data:) BACKGROUND LAYERS ====================

    /** Logcat tag for the loud url-layer diagnostics below. */
    private const val TAG = "ColorApplier"

    /**
     * Once-per-URL guard for the no-op/failure warnings: applyColors runs
     * on every recomposition, and a per-frame log would flood logcat —
     * "no silent fallthroughs" wants loud, not spammy. Synchronized set:
     * draw happens on the UI thread, tests reset from the test thread.
     * (Same pattern as MaskApplier.warnedMaskUrls.)
     */
    private val warnedBackgroundUrls = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** Log a url-layer diagnostic ONCE per URL (see [warnedBackgroundUrls]);
     *  IRLog falls back to stdout under plain JVM. */
    private fun warnOnce(url: String, message: String) {
        if (warnedBackgroundUrls.add(url)) IRLog.warn(TAG, message)
    }

    /** Test hook: clear the warn-once guard (and the shared decode cache)
     *  so JVM test cases start from a known state. Test-only by contract. */
    internal fun resetUrlBackgroundStateForTest() {
        warnedBackgroundUrls.clear()
        SyncImageDecode.resetForTest()
    }

    /**
     * Apply ONE `background-image: url(...)` layer — the wave-9 mirror of
     * the wave-3 url() MASK fix, in the LIVE background path.
     *
     * data: URIs decode synchronously through [SyncImageDecode] (the
     * shared DataUri → BitmapFactory → LRU pipeline) and draw as a tile
     * lattice via the EXISTING [planTilePass]/BackgroundTileMath plans:
     * tile size per css-backgrounds-3 §3.9 (auto = the image's natural
     * px), anchor per §3.6 background-position (free-space × fraction +
     * px offset — including the new shorthand parse), lattice per §3.7
     * background-repeat (default `repeat`, both axes), clipped to the
     * border box per §2.2.
     *
     * Anything else (http/https/file/relative) is a documented no-op —
     * Compose has no synchronous fetch, and an async load would race the
     * harness's first-frame capture, so the honest behavior is "layer
     * skipped + one loud log", never a flaky half-render.
     */
    private fun applyUrlBackground(
        modifier: Modifier,
        url: String,
        config: ColorConfig,
        layerSize: BackgroundSizeConfig,
        layerRepeat: BackgroundRepeatAxes
    ): Modifier {
        // Remote/relative schemes: documented, once-logged no-op (above).
        if (!DataUri.isDataUri(url)) {
            warnOnce(
                url,
                "background-image: url($url) — non-data: schemes are a documented no-op on " +
                    "Compose (async fetch is capture-nondeterministic); layer skipped"
            )
            return modifier
        }
        // Inline payload → bitmap, synchronously (cached after first use).
        val image = SyncImageDecode.decodeDataUri(url)
        if (image == null) {
            // Malformed payload or undecodable bytes: browsers treat a
            // failed background-image load as a transparent layer (the
            // element renders without it) — mirror that, loudly.
            warnOnce(url, "background-image: data: URI failed to decode — layer skipped " +
                "(browsers render failed image layers as transparent)")
            return modifier
        }
        // A zero-dimension bitmap has no drawable tile (and would divide
        // by zero in the aspect-ratio math) — skip it visibly.
        if (image.width <= 0 || image.height <= 0) {
            warnOnce(url, "background-image: decoded bitmap has a zero dimension — layer skipped")
            return modifier
        }
        val pos = config.backgroundPosition
        // drawBehind (not Modifier.background): the layer paints under the
        // content but ON TOP of any modifier chained before it — exactly
        // where the reversed layer loop in applyColors slots it.
        return modifier.drawBehind {
            // §3.9 tile size: explicit dimensions / cover / contain resolve
            // against the box; `auto` keeps the image's natural px (the
            // px==dp density-1 convention the capture harness runs at).
            val tile = imageTileSize(
                box = this.size,
                imageW = image.width.toFloat(),
                imageH = image.height.toFloat(),
                layerSize = layerSize,
                // Dp → px with the REAL draw density on device.
                dpToPx = { it.toPx() }
            )
            // Degenerate tile (explicit 0 size) → nothing to draw; CSS
            // renders no layer for a zero-sized tile.
            if (tile.width <= 0f || tile.height <= 0f) return@drawBehind
            // §3.6 anchor: percent of the free space + absolute px offset,
            // unclamped so oversized tiles end-align correctly (the same
            // no-clamp rule the gradient path documents).
            val anchor = tileAnchor(this.size, tile, pos, dpToPx = { it.toPx() })
            // §3.7 lattice from the SAME pinned planner gradients use.
            val pass = planTilePass(this.size, tile.width, tile.height,
                                    anchor.x, anchor.y, layerRepeat)
            // Empty origin list = degenerate plan (nothing to draw).
            if (pass.origins.isEmpty()) return@drawBehind
            // Runaway-lattice guard (mirrors the gradient path's 4096 cap
            // and iOS's tileCap): past the cap draw ONE anchored tile —
            // bounded work, visibly wrong in a debuggable way, never a
            // multi-second per-frame stall.
            if (pass.origins.size > 4096) {
                drawImage(
                    image = image,
                    dstOffset = IntOffset(anchor.x.roundToInt(), anchor.y.roundToInt()),
                    dstSize = IntSize(tile.width.roundToInt(), tile.height.roundToInt())
                )
                return@drawBehind
            }
            // §2.2: the painting area is the border box — overhanging
            // REPEAT tiles clip at the edges instead of bleeding out.
            clipRect(0f, 0f, pass.clip.width, pass.clip.height) {
                pass.origins.forEachIndexed { i, origin ->
                    drawImage(
                        image = image,
                        // AxisPlan edges are already pixel-snapped for the
                        // abutting modes; roundToInt is exact on them.
                        dstOffset = IntOffset(origin.x.roundToInt(), origin.y.roundToInt()),
                        // Segment extent (drawSizes[i]), not the raw tile:
                        // REPEAT/ROUND edges snap so adjacent tiles abut on
                        // integer px — a ≤1px sub-pixel rescale beats a
                        // visible AA seam (the mask path's same trade).
                        dstSize = IntSize(
                            pass.drawSizes[i].width.roundToInt(),
                            pass.drawSizes[i].height.roundToInt()
                        )
                    )
                }
            }
        }
    }

    /**
     * css-backgrounds-3 §3.9 concrete-size resolution for a RASTER image
     * layer (a tile WITH intrinsic dimensions — unlike gradients):
     *   auto            → the natural size (imageW × imageH)
     *   cover / contain → uniform scale by the max/min box-to-image ratio
     *   dimensions      → per-axis px / percent-of-box; ONE auto axis
     *                     scales to preserve the intrinsic ratio (§3.9's
     *                     "auto resolves via the aspect ratio" rule —
     *                     `background-size: 80px auto` on a 2:1 image is
     *                     80×40, not 80×naturalH)
     * [dpToPx] injects the density so this stays pure for the JVM pins
     * (tests pass `{ it.value }` — the px==dp harness convention).
     * Callers guarantee imageW/imageH > 0.
     */
    internal fun imageTileSize(
        box: Size,
        imageW: Float,
        imageH: Float,
        layerSize: BackgroundSizeConfig,
        dpToPx: (Dp) -> Float
    ): Size = when (layerSize) {
        // auto auto → intrinsic size, unscaled (§3.9 bullet 1).
        BackgroundSizeConfig.Auto -> Size(imageW, imageH)
        // cover: smallest uniform scale that fills BOTH axes (may crop).
        BackgroundSizeConfig.Cover -> {
            val scale = maxOf(box.width / imageW, box.height / imageH)
            Size(imageW * scale, imageH * scale)
        }
        // contain: largest uniform scale that fits INSIDE both axes.
        BackgroundSizeConfig.Contain -> {
            val scale = minOf(box.width / imageW, box.height / imageH)
            Size(imageW * scale, imageH * scale)
        }
        is BackgroundSizeConfig.Dimensions -> {
            // Explicit axis: px verbatim, percent as fraction-of-box (the
            // Dimensions contract — *Percent fields are 0..1 fractions).
            val w = layerSize.width?.let(dpToPx)
                ?: layerSize.widthPercent?.let { box.width * it }
            val h = layerSize.height?.let(dpToPx)
                ?: layerSize.heightPercent?.let { box.height * it }
            when {
                // Both axes pinned → use them (aspect ratio may distort).
                w != null && h != null -> Size(w, h)
                // One auto axis → preserve the intrinsic ratio (§3.9).
                w != null -> Size(w, w * imageH / imageW)
                h != null -> Size(h * imageW / imageH, h)
                // Dimensions with neither axis = effectively auto auto.
                else -> Size(imageW, imageH)
            }
        }
    }

    /**
     * css-backgrounds-3 §3.6 first-tile anchor: percent positions place
     * the tile at fraction × (box − tile) FREE space — so 100%/100%
     * (`right bottom`) end-aligns the tile — plus any absolute px offset.
     * Deliberately UNCLAMPED: negative free space (tile larger than box)
     * must anchor negative so the tile's far edge aligns (the gradient
     * path's wave-1 no-clamp rule, one owner away). Pure for JVM pinning;
     * [dpToPx] as in [imageTileSize].
     */
    internal fun tileAnchor(
        box: Size,
        tile: Size,
        pos: BackgroundPositionConfig,
        dpToPx: (Dp) -> Float
    ): Offset {
        // Free space per axis — negative when the tile overhangs the box.
        val freeX = box.width - tile.width
        val freeY = box.height - tile.height
        // fraction × free space + absolute offset (both §3.6 components).
        return Offset(
            freeX * pos.x + dpToPx(pos.xOffset),
            freeY * pos.y + dpToPx(pos.yOffset)
        )
    }

    /**
     * True when a size/position/repeat knob can move a GRADIENT layer off
     * the plain full-box fill — i.e. when applyBackgroundImage must take
     * the sized-tile drawBehind path instead of `Modifier.background`.
     * The Compose mirror of iOS's
     * BackgroundImageApplier.gradientNeedsGeometry, so both mobile
     * runtimes route layers through tile geometry under the SAME
     * conditions. Pure (data in, Boolean out) and internal so JUnit pins
     * the routing without a DrawScope (ColorApplierGradientTileTest).
     */
    internal fun gradientNeedsGeometry(
        layerSize: BackgroundSizeConfig,
        position: BackgroundPositionConfig,
        repeat: BackgroundRepeatAxes
    ): Boolean {
        // NOTE (wave 47): the former `isRepeatingGradient` exclusion is
        // removed — the resolver-backed repeating brushes rebuild their
        // §3.4.4 stop lattice per createShader size, so pinShaderToTile
        // pins them like any other gradient. The predicate is now the
        // exact mirror of iOS BackgroundImageApplier.gradientNeedsGeometry
        // (which never excluded repeating flavours).
        // Explicit dimensions move the tile away from the box. The other
        // size flavors (auto/cover/contain) all resolve to the box for an
        // intrinsic-less gradient (css-backgrounds-3 §3.9) — exactly what
        // the full-box fill already paints, so they don't route alone.
        if (layerSize is BackgroundSizeConfig.Dimensions) return true
        // Non-default position: a percent/px offset shifts even a
        // box-sized tile, and the default REPEAT wraps it around with a
        // visible seam (§3.6) — web and iOS both render that wrap, so
        // Android must route it too (the wave-1 skeptic deferral).
        if (position.hasPosition) return true
        // A non-`repeat` axis changes the lattice (§3.7): no-repeat drops
        // the wrap once position moves the tile, space/round re-place it.
        // With auto size + default position the box-sized tile paints the
        // same picture either way — routing is still correct, just
        // unnecessary for plain repeat (same note as the iOS predicate).
        if (repeat.x != AxisRepeat.REPEAT || repeat.y != AxisRepeat.REPEAT) return true
        // No knob set: keep the byte-identical Modifier.background path.
        return false
    }

    /**
     * The resolved draw commands for ONE sized-tile background pass:
     * [clip] is the painting-area rect the tiles are clipped to
     * (css-backgrounds-3 §2.2 — the border box), [tileSize] the SHADER
     * pitch after any `round` rescale (fractional, e.g. 200/7 — gradient
     * geometry resolves against it per css-images-4 §3.4.1), [origins] the
     * cartesian product of the two per-axis §3.7 plans, and [drawSizes]
     * each tile's DRAWN extent (parallel to [origins]). Drawn extents come
     * from the axis plans' pixel-snapped edges: abutting lattices
     * (repeat/round) vary ±1px per tile so adjacent rects share integer
     * edges — fractional-edge neighbours are independently antialiased and
     * never sum to full coverage, leaking a background seam at every
     * interior boundary (the Repeat_Round straggler). Pure data so plain
     * JVM tests can pin overflow, clip and snapping.
     */
    internal data class TilePass(
        val clip: Size,
        val tileSize: Size,
        val origins: List<Offset>,
        val drawSizes: List<Size>
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
        // Origin and drawn extent are built together so index i of both
        // lists describes the same tile — the drawn extent is the axis
        // plan's [start, end) segment, NOT the uniform tileSize (round /
        // fractional repeat snap edges, so widths vary ±1px per tile).
        val origins = mutableListOf<Offset>()
        val drawSizes = mutableListOf<Size>()
        for (i in planX.origins.indices) {
            for (j in planY.origins.indices) {
                origins.add(Offset(planX.origins[i], planY.origins[j]))
                drawSizes.add(Size(planX.ends[i] - planX.origins[i],
                                   planY.ends[j] - planY.origins[j]))
            }
        }
        // Clip is always the full border box — the painting area for the
        // default background-clip (§2.2); tile math never widens it.
        return TilePass(clip = box, tileSize = Size(planX.tileSize, planY.tileSize),
                        origins = origins, drawSizes = drawSizes)
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
     * The paintable stop list for a gradient — wave-36 lane M8.
     *
     * css-images-3 §3.4.4 (and §3.4.1's "premultiplied ramp") define the
     * degenerate ONE-stop gradient: `linear-gradient(green)`,
     * `linear-gradient(to right, green 90%)` and
     * `repeating-linear-gradient(green 50px)` all paint the gradient box
     * UNIFORMLY in that colour — there is no second stop to ramp toward, so
     * every position on the gradient line takes the sole stop's colour.
     * Chromium, WebKit and this repo's web + SwiftUI runtimes all do exactly
     * that (SwiftUI's LinearGradient paints a solid fill for a one-entry stop
     * array, which is why iOS already passes the family).
     *
     * Compose's LinearGradientShader / RadialGradientShader / SweepGradient
     * all require at least two entries, so every brush factory below used to
     * `return null` on a one-stop gradient. Returning null does NOT paint
     * nothing — it makes the background layer fall through, so the WPT
     * `gradient-single-stop-00{1..8}` tests (an abspos RED div behind a
     * gradient-painted GREEN div) rasterised RED on Android while ref, web
     * and iOS rasterised GREEN. Eight depth-48 cells, one guard.
     *
     * Contract: null ⇒ genuinely unpaintable (no stops at all — the brush
     * factory keeps its early return, so a malformed payload still fails
     * visibly rather than inventing a colour); a one-stop list is widened to
     * the same colour at 0 and 1, which every shader renders as the uniform
     * fill the spec asks for; two-or-more is returned untouched, so every
     * pre-wave-36 capture is byte-identical.
     *
     * Pure (list in, list out) and internal so JUnit pins it without a
     * DrawScope. The SwiftUI twin is behavioural, not textual: its
     * `GradientApplier.resolveStops` already carries a one-stop list through
     * (`max(1, count - 1)` guards the even-spread divide) and
     * `srgbSubdivided` short-circuits on `count < 2`.
     *
     * Wave 47: the ColorApplier brush factories now widen inside
     * GradientStopResolver.shaderStops (the same §3.4.4 rule, applied
     * after the fixup pipeline); this function remains the widening for
     * the background/RepeatingGradientHelper utility entry points
     * (createStripesPattern / createCheckerboardApproximation), which sit
     * outside the wave-47 pipeline.
     */
    internal fun paintableStops(stops: List<ColorStop>): List<ColorStop>? = when {
        // No stops at all — nothing the spec can tell us to paint.
        stops.isEmpty() -> null
        // The degenerate one-stop ramp: the same colour at both ends of the
        // gradient line. Positions are forced to 0/1 rather than reusing the
        // declared position because a sole stop's position is irrelevant to
        // the painted result (§3.4.4 fixes every earlier stop to the first
        // stop's colour and every later one to the last stop's colour — with
        // one stop those are the same colour, so the box is uniform).
        stops.size == 1 -> listOf(
            stops[0].copy(position = 0f),
            stops[0].copy(position = 1f)
        )
        // The overwhelming case: leave the declared ramp exactly as it is.
        else -> stops
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
     * Wave 47 (lane Z1): stop resolution moved INSIDE createShader and
     * behind GradientStopResolver — the §3.4.3 fixup needs the
     * gradient-line LENGTH (|W·sinθ| + |H·cosθ|, §3.4.1) to resolve
     * <length> stop positions, and that length only exists per draw
     * size. `repeating` is handled HERE by materialising the §3.4.4
     * expansion as explicit stops (replacing RepeatingGradientHelper's
     * period-less fraction×diagonal approximation — the gradient-border-
     * box 0.646 defect), so TileMode stays Clamp: the expanded list
     * already covers [0, 1].
     *
     * @param gradient LinearGradient configuration
     * @return Brush for the gradient, or null if it has no stops at all
     */
    private fun createLinearGradientBrush(
        gradient: BackgroundImageConfig.LinearGradient
    ): Brush? {
        // No stops at all — genuinely unpaintable (the wave-36 M8 null
        // contract; the one-stop widening now lives in shaderStops).
        if (gradient.colorStops.isEmpty()) return null
        val angle = gradient.angle

        return object : ShaderBrush() {
            override fun createShader(size: Size): Shader {
                // Endpoint math is pure and lives in linearGradientPoints
                // so plain JVM tests can pin it (android.graphics shaders
                // can't be built in non-instrumented tests).
                val (from, to) = linearGradientPoints(angle, size)
                // §3.4.1 gradient-line length — the quantity a <length>
                // stop position divides by (iOS twin: lineLengthPx).
                val lineLen = lineLengthPx(angle, size)
                // Full §3.4.3 pipeline: fixup → repeat expansion → clip
                // → interp subdivision. Empty only when colorStops was
                // empty (guarded above), so the !! is contract-safe.
                val (colors, stops) = GradientStopResolver.shaderStops(
                    gradient.colorStops, lengthPx = lineLen,
                    repeating = gradient.repeating, interp = gradient.interp)!!
                return LinearGradientShader(
                    from = from,
                    to = to,
                    colors = colors,
                    colorStops = stops,
                    // Clamp even for repeating: the resolver materialises
                    // the §3.4.4 copies across [0, 1] itself.
                    tileMode = TileMode.Clamp
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
        val lineLen = lineLengthPx(angleDeg, size)
        // Line centre = BOX centre, unconditionally (§3.4.1). Position
        // placement happens in the tile translate, never here.
        val cx = size.width / 2f
        val cy = size.height / 2f
        // Walk half the line each way from the centre along the direction.
        return Offset(cx - dirX * lineLen / 2f, cy - dirY * lineLen / 2f) to
               Offset(cx + dirX * lineLen / 2f, cy + dirY * lineLen / 2f)
    }

    /**
     * css-images-3 §3.1.1 gradient-line LENGTH in px — the quantity a
     * <length> stop position divides by. Single source of truth for the
     * endpoint math above AND the resolver call in the linear factory
     * (wave 47); byte-parallel with iOS GradientApplier.lineLengthPx.
     * Internal so GradientStopResolverTest pins it on the JVM.
     */
    internal fun lineLengthPx(angleDeg: Float, size: Size): Float {
        val rad = angleDeg * PI.toFloat() / 180f
        // |W·sinθ| + |H·cosθ| — the perpendiculars touch the corners.
        return abs(size.width * sin(rad)) + abs(size.height * cos(rad))
    }

    /**
     * Create a radial gradient Brush from configuration.
     *
     * background-position is NOT a parameter (same rationale as the linear
     * builder): the radial centre comes from the gradient's own `at <pos>`
     * (css-images-4 §3.2), resolved against the gradient box — background
     * -position only moves the tile, outside this brush.
     *
     * Wave 47 (lane Z1): stop resolution goes through GradientStopResolver
     * inside createShader — the ending-shape radius rMax is the length a
     * <length> stop divides by, and `repeating` materialises §3.4.4
     * copies as explicit stops (no more RepeatingGradientHelper detour).
     *
     * @param gradient RadialGradient configuration
     * @return Brush for the gradient, or null if it has no stops at all
     */
    private fun createRadialGradientBrush(
        gradient: BackgroundImageConfig.RadialGradient
    ): Brush? {
        // No stops at all — genuinely unpaintable (wave-36 M8 contract;
        // the one-stop widening now lives in the resolver's shaderStops).
        if (gradient.colorStops.isEmpty()) return null

        // CSS radial gradients have two distinct radii (rx, ry) when the
        // ending shape is `ellipse` (the default), and a single radius
        // (r = rx = ry) when it's `circle`. The size keyword (closest-side
        // / closest-corner / farthest-side / farthest-corner — or simply
        // <length>{1,2}, currently unsupported) decides how far each radius
        // extends. Compose's Brush.radialGradient is circular-only, so we
        // emit a custom RadialGradientShader and pre-stretch the bounds to
        // simulate the elliptical shape.
        val shape = gradient.shape ?: BackgroundImageConfig.RadialShape.ELLIPSE
        val sizeKw = gradient.size ?: BackgroundImageConfig.RadialSize.FARTHEST_CORNER
        // GradientCoord axes (A-RC8): FRACTION or PX — resolved against
        // the actual draw size below, the only place it exists.
        val coordX = gradient.centerX
        val coordY = gradient.centerY

        return object : ShaderBrush() {
            override fun createShader(size: Size): Shader {
                val w = size.width
                val h = size.height
                // fraction(axis) × axis ≡ the px value for PX coords, so
                // `at 100px 50px` lands exactly 100/50 px from the origin.
                val cx = coordX.fraction(w) * w
                val cy = coordY.fraction(h) * h
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
                // max(rx,ry) centred at (cx,cy) and squash it down to the
                // ellipse with a local matrix — the scale factors come from
                // radialAxisScale (rx/rMax, ry/rMax), which documents the
                // Skia setLocalMatrix direction and is JVM-pinned so the
                // squash can never invert again.
                val rMax = kotlin.math.max(rx.coerceAtLeast(1e-3f), ry.coerceAtLeast(1e-3f))
                val (sx, sy) = radialAxisScale(rx, ry)
                // Wave 47: the radial gradient LINE runs from the centre
                // to the ending shape (css-images-3 §3.5) — rMax is the
                // px length a <length> stop divides by. The resolver also
                // materialises `repeating-radial-gradient` copies across
                // [0, rMax] (beyond rMax Skia's clamp holds the loc-1
                // colour — same geometric limit as the iOS twin, visible
                // only for sizes that end short of every corner).
                val (colors, stops) = GradientStopResolver.shaderStops(
                    gradient.colorStops, lengthPx = rMax,
                    repeating = gradient.repeating, interp = gradient.interp)!!
                val shader = RadialGradientShader(
                    center = Offset(cx, cy),
                    radius = rMax,
                    colors = colors,
                    colorStops = stops,
                    // Clamp even for repeating — the copies are explicit.
                    tileMode = TileMode.Clamp
                )
                if (sx == 1f && sy == 1f) return shader
                // Apply the axis squash around (cx,cy): translate the
                // centre to the origin, scale, translate back — so the
                // ellipse stays centred where CSS put it.
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
     * Local-matrix axis scale (sx, sy) that turns the circular
     * RadialGradientShader of radius rMax = max(rx, ry) into the CSS
     * ellipse with radii (rx, ry).
     *
     * Direction matters: Skia's setLocalMatrix transforms the shader
     * IMAGE by M — the painted pattern in draw space is the circle mapped
     * THROUGH the matrix (Skia samples the shader at M⁻¹·p, so the color
     * at shader-space q lands at draw-space M·q). To squash the rMax
     * circle DOWN to the ellipse we therefore scale each axis by
     * radius/rMax (both ≤ 1). The previous inline rMax/radius values were
     * the exact INVERSE: on a wide box (rx > ry, farthest-corner default)
     * they stretched the circle further along Y instead of squashing it,
     * so every elliptical radial gradient (and mask — see
     * MaskApplier.radialMaskAxisScale, a delegate to this) squashed the
     * WRONG axis vs web.
     *
     * Radii are clamped ≥ 1e-3 so a zero-sized box cannot yield 0/0.
     * Internal so JVM tests can pin the scale DIRECTION
     * (ColorApplierGradientTileTest).
     */
    internal fun radialAxisScale(rx: Float, ry: Float): Pair<Float, Float> {
        // ε-clamp mirrors the shader-radius guard in the caller so both
        // computations agree on degenerate boxes.
        val rxC = rx.coerceAtLeast(1e-3f)
        val ryC = ry.coerceAtLeast(1e-3f)
        // The circular shader is built at the LARGER radius; each axis
        // then shrinks by its own radius/rMax (the max axis stays 1).
        val rMax = kotlin.math.max(rxC, ryC)
        return (rxC / rMax) to (ryC / rMax)
    }

    /**
     * Create a sweep (conic) gradient Brush from configuration.
     *
     * background-position is NOT a parameter (same rationale as the linear
     * builder): the sweep centre comes from conic-gradient's own `at <pos>`
     * (css-images-4 §3.3) — background-position only moves the tile.
     *
     * Wave 47 (lane Z1): stops resolve through GradientStopResolver.
     * Conic stop positions are <angle-percentage> fractions of the full
     * turn — a <length> never applies, so lengthPx is null (a px stop
     * would breadcrumb + degrade to unpositioned, the iOS twin rule).
     * `repeating-conic-gradient` materialises its §3.4.4 copies across
     * the turn as explicit stops — replacing RepeatingGradientHelper's
     * hand-rolled ≤10-repetition expansion (SweepGradient itself has no
     * TileMode, so explicit copies are the only spec-shaped mechanism).
     *
     * @param gradient ConicGradient configuration
     * @return Brush for the gradient, or null if it has no stops at all
     */
    private fun createSweepGradientBrush(
        gradient: BackgroundImageConfig.ConicGradient
    ): Brush? {
        // No stops at all — genuinely unpaintable (wave-36 M8 contract;
        // the one-stop widening now lives in the resolver's shaderStops).
        if (gradient.colorStops.isEmpty()) return null

        // The previous implementation multiplied the centre fraction by
        // a hard-coded 500px and passed Offset(cx*500, cy*500) into
        // `Brush.sweepGradient(center=...)`. For elements smaller than
        // 500px (e.g. the 160×160 Audit_ConicFrom30degAt25 box) that
        // parked the sweep centre way off-canvas, so non-default `at
        // <pos>` values just looked centred. Use a ShaderBrush with
        // `createShader(size)` so the centre is always the actual
        // fraction × draw-size, matching how the linear / radial brushes
        // already work.
        // GradientCoord axes (A-RC8) — resolved in createShader like radial.
        val coordX = gradient.centerX
        val coordY = gradient.centerY
        // Angle-fraction stops are size-independent — resolve them once,
        // outside the per-size createShader call (unlike linear/radial,
        // whose <length> stops need the draw size).
        val (colors, stops) = GradientStopResolver.shaderStops(
            gradient.colorStops, lengthPx = null,
            repeating = gradient.repeating, interp = gradient.interp)!!
        // CSS `from` angle, degrees. 0 when the author omitted `from …`.
        val fromDeg = gradient.angle
        return object : ShaderBrush() {
            override fun createShader(size: Size): Shader {
                // fraction × axis ≡ px value for PX coords (see radial).
                val cx = coordX.fraction(size.width) * size.width
                val cy = coordY.fraction(size.height) * size.height
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

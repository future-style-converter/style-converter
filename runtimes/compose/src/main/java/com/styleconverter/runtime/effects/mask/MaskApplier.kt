package com.styleconverter.runtime.effects.mask

// BitmapFactory is the SYNCHRONOUS raster decode for data:-URI mask
// sources — the same decode ImageCache's data: branch uses; no Coil here
// because the Modifier path must have the bitmap on the FIRST draw frame
// (capture determinism) and Coil is async by construction.
import android.graphics.BitmapFactory
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
// drawImage addresses destination geometry in integer px.
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
// Tile placement math is shared with the background path so mask-repeat
// and background-repeat can never diverge (css-masking-1 §4.5 defines
// mask-repeat with css-backgrounds-3 §3.7 semantics).
import com.styleconverter.runtime.color.AxisRepeat
import com.styleconverter.runtime.color.BackgroundTileMath
// Gradient geometry is shared with the background path (ColorApplier) so
// mask and background gradients can never diverge — see createMaskBrush.
import com.styleconverter.runtime.color.ColorApplier
import com.styleconverter.runtime.core.images.DataUri
// IRLog: android.util.Log on device, stdout under plain-JVM tests — the
// no-op branches below must log loudly WITHOUT breaking the JVM suite.
import com.styleconverter.runtime.core.ir.IRLog
import kotlin.math.roundToInt

/**
 * Applies mask effects to Compose Modifiers.
 *
 * ## Compose Limitations
 * CSS masks are complex and Compose has limited native support:
 * - Gradient masks use BlendMode.DstIn/DstOut for alpha-based masking
 * - url() masks render ONLY from `data:` URIs (decoded synchronously);
 *   remote schemes are a documented, logged no-op — async fetch cannot
 *   be capture-deterministic (the first frame would race the load)
 * - Luminance mode requires ColorMatrix conversion to grayscale
 *
 * ## Implementation Strategy
 *
 * ### Gradient Masks
 * For gradient masks, we use graphicsLayer with compositingStrategy and
 * drawWithContent with BlendMode.DstIn to achieve masking effects.
 * The gradient's alpha channel determines visibility.
 *
 * ### URL Masks (data: URIs)
 * The payload decodes inline via [DataUri] + BitmapFactory (cached), and
 * the bitmap draws as a tile lattice — mask-size/position/repeat resolved
 * through the SAME BackgroundTileMath the background path draws with —
 * inside ONE saveLayer composited onto the content with BlendMode.DstIn,
 * so content alpha is multiplied by mask alpha exactly once
 * (EffectsFacade owns the single application; see StyleApplier step 3.5
 * for the wave-2 double-apply post-mortem).
 *
 * ## Usage
 * ```kotlin
 * val config = MaskExtractor.extractMaskConfig(properties)
 * val modifier = MaskApplier.applyMask(Modifier, config)
 * ```
 */
object MaskApplier {

    /** Logcat tag for the loud no-op diagnostics below. */
    private const val TAG = "MaskApplier"

    /**
     * Apply mask configuration to a Modifier.
     *
     * Gradient masks AND url(data:) masks both render here — this is the
     * only mask entry point EffectsFacade calls (the old Coil-based
     * MaskedBox composable was dead code nothing invoked, removed with
     * this path's introduction).
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

        // url() masks: data: URIs decode inline and composite with the
        // same DstIn strategy as gradients; remote schemes stay a
        // documented, once-logged no-op (see applyUrlMask).
        val url = config.imageUrl
        if (url != null) {
            return applyUrlMask(modifier, config, url)
        }

        // hasImage with no drawable source — reachable when the extractor
        // recognises a mask-image value but cannot recover a source from
        // it (e.g. its generic-gradient fallback emits isGradient=true
        // with gradient=null, or a url object misses its `url` key).
        // Not silent: log once, then leave the content unmasked.
        warnOnce("<no-source>", "mask config has hasImage=true but no url/gradient source recovered — rendering unmasked")
        return modifier
    }

    // ==================== URL (data:) MASKS ====================

    /**
     * Once-per-URL guard for the no-op/failure warnings: this factory
     * runs on every recomposition, and a per-frame Log.w would flood
     * logcat — "no silent fallthroughs" wants loud, not spammy.
     * Synchronized set: draw happens on the UI thread, tests reset from
     * the test thread.
     */
    private val warnedMaskUrls = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** Cap on cached decoded mask bitmaps — mask sources are tiny
     *  (fixture masks are ≤ a few KB), so a small bound suffices. */
    private const val MASK_CACHE_MAX_ENTRIES = 16

    /**
     * url → decoded [ImageBitmap]. Mirrors ImageCache's LRU memory-cache
     * pattern but stays LOCAL and synchronous: ImageCache.loadImage is a
     * suspend API with no synchronous lookup (a draw-time mask needs the
     * bitmap NOW), and android.util.LruCache is a throwing stub under the
     * plain-JVM unit suite that pins this routing (no Robolectric in this
     * repo). accessOrder=true + removeEldestEntry is the textbook JDK LRU.
     */
    private val maskBitmapCache =
        object : LinkedHashMap<String, ImageBitmap>(MASK_CACHE_MAX_ENTRIES, 0.75f, true) {
            // Evict the least-recently-used entry once past the cap.
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>): Boolean =
                size > MASK_CACHE_MAX_ENTRIES
        }

    /**
     * Raster bytes → [ImageBitmap]. An `internal var` seam: the default
     * is the platform BitmapFactory decode (the same call ImageCache's
     * data:-URI branch performs); the plain-JVM suite swaps in a fake
     * because android.graphics is a throwing stub off-device. Production
     * code must never reassign this.
     */
    internal var rasterMaskDecoder: (ByteArray) -> ImageBitmap? = { bytes ->
        try {
            // BitmapFactory returns null (does not throw) for undecodable
            // bytes on device — null IS the visible-failure contract.
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (t: Throwable) {
            // android.jar's "not mocked" stub throw under plain JVM (and a
            // defensive belt on-device, e.g. OOM on a hostile payload) →
            // the same null contract; the caller logs the failure once.
            null
        }
    }

    /**
     * Decode a `data:` URI mask source to an [ImageBitmap], cached per
     * URL. Returns null when [url] is not a data URI or the payload is
     * malformed/undecodable — callers must surface that loudly.
     * Synchronous by design: data URIs carry their bytes inline (no I/O),
     * which is exactly what first-frame capture determinism needs.
     */
    internal fun decodeDataUriMask(url: String): ImageBitmap? {
        // Cache hit first — decoding per recomposition would waste work,
        // and returning the SAME bitmap keeps captures deterministic.
        synchronized(maskBitmapCache) { maskBitmapCache[url] }?.let { return it }
        // RFC 2397 payload → bytes; DataUri owns the %xx-unescape +
        // strict-base64 rules (pinned by its own JVM suite).
        val bytes = DataUri.decode(url) ?: return null
        // bytes → platform bitmap through the seam above.
        val bitmap = rasterMaskDecoder(bytes) ?: return null
        // Populate the LRU under the same lock the lookup uses.
        synchronized(maskBitmapCache) { maskBitmapCache.put(url, bitmap) }
        return bitmap
    }

    /** Test hook: clear the decode cache and the warn-once guard so JVM
     *  test cases start from a known state. Test-only by contract. */
    internal fun resetUrlMaskStateForTest() {
        synchronized(maskBitmapCache) { maskBitmapCache.clear() }
        warnedMaskUrls.clear()
    }

    /** Log a url-mask diagnostic ONCE per URL (see [warnedMaskUrls]);
     *  IRLog falls back to stdout under plain JVM. */
    private fun warnOnce(url: String, message: String) {
        if (warnedMaskUrls.add(url)) IRLog.warn(TAG, message)
    }

    /**
     * Apply a url() mask on the Modifier path.
     *
     * data: URIs render; anything else (http/https/file/relative) is the
     * documented no-op — Compose has no synchronous fetch, and an async
     * load would race the harness's first-frame capture, so the honest
     * behavior is "unmasked + one loud log", never a flaky half-render.
     */
    private fun applyUrlMask(modifier: Modifier, config: MaskConfig, url: String): Modifier {
        // Remote/relative schemes: documented, once-logged no-op (above).
        if (!DataUri.isDataUri(url)) {
            warnOnce(
                url,
                "mask-image: url($url) — non-data: schemes are a documented no-op on Compose " +
                    "(async fetch is capture-nondeterministic); rendering unmasked"
            )
            return modifier
        }
        // Inline payload → bitmap, synchronously (cached after first use).
        val mask = decodeDataUriMask(url)
        if (mask == null) {
            // Malformed payload or undecodable bytes: browsers treat a
            // failed mask-image load as no mask layer (the element renders
            // unmasked) — mirror that, loudly, not silently.
            warnOnce(url, "mask-image: data: URI failed to decode — rendering unmasked (browsers ignore failed mask loads)")
            return modifier
        }
        // Same single-application chain as the gradient path: flatten the
        // content into ONE offscreen layer, then composite the mask onto
        // it exactly once. EffectsFacade is the only caller — StyleApplier
        // step 3.5 documents the wave-2 alpha² double-apply this design
        // prevents.
        return modifier
            .graphicsLayer {
                // Without Offscreen, DstIn would erase through to whatever
                // renders BEHIND the element instead of just its content.
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                // Element content first — the mask multiplies THIS layer.
                drawContent()
                // Then the tiled image mask in one saveLayer pass.
                drawImageMask(mask, config)
            }
    }

    /**
     * Draw [mask] over the content as a CSS mask layer.
     *
     * Geometry: mask-size (§4.8 — default `auto` = intrinsic px),
     * mask-position (§4.6 — default 0% 0% = top-left), mask-repeat
     * (§4.5 — default `repeat`, tiling BOTH axes; css-masking-1 defers
     * the placement rules to css-backgrounds-3 §3.7, so the lattice comes
     * from BackgroundTileMath.axisPlan, the same pinned owner the
     * background path draws with, including space/round and the snapped
     * abutting edges that close antialiasing seams).
     *
     * Compositing: all tiles draw SrcOver INTO one saveLayer; the layer
     * then composites onto the content with the mask blend (DstIn for the
     * default `add`). Per-tile DstIn would be wrong for no-repeat: pixels
     * OUTSIDE the tile rect would keep full content alpha, where CSS says
     * uncovered areas are transparent black (content hidden). The same
     * property makes a degenerate lattice (zero-sized tile → empty plan)
     * hide the element entirely — matching the spec's transparent-black
     * mask layer, i.e. what browsers do for `mask-size: 0 0`.
     */
    private fun DrawScope.drawImageMask(mask: ImageBitmap, config: MaskConfig) {
        // Intrinsic size in bitmap px used as draw px — the density-1
        // convention this file already applies (Dp.value is read as px in
        // calculateMaskSize), matching the capture harness setup.
        val imageW = mask.width.toFloat()
        val imageH = mask.height.toFloat()
        // mask-size: default Auto keeps the intrinsic dimensions
        // (css-masking-1 §4.8 / backgrounds §3.9 `auto`).
        val (tileW, tileH) = calculateMaskSize(config.size, size, imageW, imageH)
        // mask-position: the anchor of the first tile (free-space ×
        // fraction — §4.6 positive-percentage semantics).
        val (anchorX, anchorY) = calculateMaskPosition(config.position, size, tileW, tileH)
        // mask-repeat → per-axis placement modes, then one axis plan each.
        val (modeX, modeY) = maskRepeatAxes(config.repeat)
        val planX = BackgroundTileMath.axisPlan(size.width, tileW, anchorX, modeX)
        val planY = BackgroundTileMath.axisPlan(size.height, tileH, anchorY, modeY)
        // mask-mode (css-masking-1 §7.4.3): `match-source` resolves to
        // ALPHA for raster <image> sources — luminance is only the
        // match-source default for SVG <mask> element references.
        // TODO(svg-masks): when SVG mask sources land on this wire,
        // MATCH_SOURCE must flip to luminance for them. Explicit
        // `luminance` reuses the gradient path's luminance→alpha matrix.
        val colorFilter = if (config.mode == MaskModeValue.LUMINANCE) {
            ColorFilter.colorMatrix(LUMINANCE_TO_ALPHA_MATRIX)
        } else {
            null // ALPHA / MATCH_SOURCE(raster): the bitmap's alpha IS the mask
        }
        // The whole lattice composites in ONE pass (rationale in KDoc).
        val layerPaint = Paint().apply { blendMode = config.composite.toBlendMode() }
        val canvas = drawContext.canvas
        canvas.saveLayer(Rect(Offset.Zero, size), layerPaint)
        // Cartesian product of the two axis plans — the same walk the
        // background tile renderer performs.
        for (row in planY.origins.indices) {
            for (col in planX.origins.indices) {
                drawImage(
                    image = mask,
                    // AxisPlan edges are already pixel-snapped for the
                    // abutting modes; roundToInt is exact on them.
                    dstOffset = IntOffset(
                        planX.origins[col].roundToInt(),
                        planY.origins[row].roundToInt()
                    ),
                    // Segment extent, not the raw tile: REPEAT/ROUND edges
                    // are snapped so adjacent tiles abut on integer px — a
                    // ≤1px sub-pixel rescale beats a visible AA seam (see
                    // AxisPlan's seam post-mortem).
                    dstSize = IntSize(
                        (planX.ends[col] - planX.origins[col]).roundToInt(),
                        (planY.ends[row] - planY.origins[row]).roundToInt()
                    ),
                    colorFilter = colorFilter
                )
            }
        }
        // Composite the finished mask layer onto the content (DstIn).
        canvas.restore()
    }

    /**
     * mask-repeat → per-axis [AxisRepeat] modes. css-masking-1 §4.5
     * defines mask-repeat with css-backgrounds-3 §3.7 semantics, so the
     * mapping targets the background path's enum directly. Internal:
     * pinned by the plain-JVM suite (MaskUrlImageTest).
     */
    internal fun maskRepeatAxes(repeat: MaskRepeatValue): Pair<AxisRepeat, AxisRepeat> = when (repeat) {
        // The initial value: edge-to-edge tiling on both axes.
        MaskRepeatValue.REPEAT -> AxisRepeat.REPEAT to AxisRepeat.REPEAT
        // Single tile at the position anchor.
        MaskRepeatValue.NO_REPEAT -> AxisRepeat.NO_REPEAT to AxisRepeat.NO_REPEAT
        // Horizontal strip: tile x, anchor y.
        MaskRepeatValue.REPEAT_X -> AxisRepeat.REPEAT to AxisRepeat.NO_REPEAT
        // Vertical strip: anchor x, tile y.
        MaskRepeatValue.REPEAT_Y -> AxisRepeat.NO_REPEAT to AxisRepeat.REPEAT
        // §3.7 space: whole tiles, equal gaps between them.
        MaskRepeatValue.SPACE -> AxisRepeat.SPACE to AxisRepeat.SPACE
        // §3.7 round: rescale so a whole number of tiles fits exactly.
        MaskRepeatValue.ROUND -> AxisRepeat.ROUND to AxisRepeat.ROUND
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

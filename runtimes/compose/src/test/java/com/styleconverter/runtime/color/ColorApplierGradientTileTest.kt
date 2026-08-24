package com.styleconverter.runtime.color

// Pins the wave-B gradient-background-tile fixes in ColorApplier:
//
// (1) TILE-PINNED SHADER — the sized-tile drawBehind branch used to hand
//     each tile's ShaderBrush the FULL element size, so a 30×30 tile of a
//     `linear-gradient(red, blue)` on a 120px-tall box sampled only the
//     top slice of a 120px gradient line: every tile rendered solid red
//     instead of the red→blue ramp. ColorApplier.pinShaderToTile now
//     forces createShader to resolve against the background-size tile
//     (css-images-4 §3.4.1: the gradient box IS the tile), matching web.
//
// (2) PAINTING-AREA CLIP — css-backgrounds-3 §2.2 clips background
//     painting to the border box, but REPEAT plans deliberately overhang
//     both edges (grid coverage), so unclipped tiles bled up to one
//     tile-width past the box (painted bbox 210px on a 200px box).
//     planTilePass exposes the plan as pure data: tests below pin that
//     origins DO overflow while `clip` stays the border box — the
//     drawBehind lambda is a thin consumer wrapping the loop in
//     clipRect(0,0,clip.width,clip.height).
//
// (3) NO POSITION SHIFT IN THE SHADER — background-position places the
//     TILE (css-backgrounds-3 §3.6, applied via the tile translate); it
//     never shifts the gradient line inside its own tile. The old
//     createLinearGradientBrush baked posX·w/posY·h into the line CENTRE,
//     double-applying position. linearGradientPoints is now pure and
//     position-free (compile-level: it takes only angle + size); tests
//     pin that the line midpoint is ALWAYS the box centre.
//
// android.graphics shaders cannot be constructed in plain JVM unit tests
// (android.jar stubs throw), so — like BackgroundTileMathTest and
// ColorApplierConicTest — these tests pin the extracted PURE geometry,
// plus the pin-wrapper's size forwarding via a recording fake brush.

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ColorApplierGradientTileTest {

    // ---- (1) pinShaderToTile: shader geometry resolves against the tile ----

    /**
     * Fake brush that records the size handed to createShader. It can't
     * RETURN a real Shader on the JVM (android.graphics.Shader is a
     * stub that throws), so it aborts with a sentinel AFTER recording —
     * the geometry handoff is what's under test, not the shader object.
     */
    private class RecordingShaderBrush : ShaderBrush() {
        var receivedSize: Size? = null
        override fun createShader(size: Size): Shader {
            receivedSize = size
            throw UnsupportedOperationException("sentinel: size recorded, no JVM Shader available")
        }
    }

    @Test
    fun `pinned brush builds its shader against the tile, not the element`() {
        val inner = RecordingShaderBrush()
        // 30×30 background-size tile on a (hypothetical) 200×120 element.
        val pinned = ColorApplier.pinShaderToTile(inner, Size(30f, 30f)) as ShaderBrush
        try {
            // Compose would call createShader with the DrawScope's element
            // size — the regression handed exactly this size to the shader.
            pinned.createShader(Size(200f, 120f))
            fail("sentinel expected — RecordingShaderBrush never returns")
        } catch (expected: UnsupportedOperationException) {
            // Sentinel: the recording already happened.
        }
        // The wrapped brush must have seen the TILE size (css-images-4
        // §3.4.1 gradient box = the background-size tile).
        assertEquals(Size(30f, 30f), inner.receivedSize)
    }

    @Test
    fun `non-shader brushes pass through the pin untouched`() {
        // Solid colours have no size-dependent geometry — pinning must be
        // the identity, not a wrap (a wrap would crash applyTo on a
        // non-ShaderBrush cast downstream).
        val solid = SolidColor(Color.Red)
        assertSame(solid, ColorApplier.pinShaderToTile(solid, Size(30f, 30f)))
    }

    // ---- (1b) linear gradient line geometry (what the pinned size feeds) ----

    private fun assertOffset(expected: Offset, actual: Offset, eps: Float = 0.001f) {
        assertEquals("x", expected.x, actual.x, eps)
        assertEquals("y", expected.y, actual.y, eps)
    }

    @Test
    fun `to-bottom line spans exactly the tile height`() {
        // 180deg = "to bottom". On a 30×30 TILE the line must run
        // (15,0)→(15,30): lineLen |30·sin180|+|30·cos180| = 30. Under the
        // old bug the same gradient resolved on the 120px element, so the
        // 30px tile showed only the first quarter of the ramp.
        val (from, to) = ColorApplier.linearGradientPoints(180f, Size(30f, 30f))
        assertOffset(Offset(15f, 0f), from)
        assertOffset(Offset(15f, 30f), to)
    }

    @Test
    fun `to-right line spans exactly the box width`() {
        // 90deg = "to right" on 200×100: horizontal line through the
        // vertical centre, full width (css-images-4 §3.4.1).
        val (from, to) = ColorApplier.linearGradientPoints(90f, Size(200f, 100f))
        assertOffset(Offset(0f, 50f), from)
        assertOffset(Offset(200f, 50f), to)
    }

    @Test
    fun `diagonal line length follows the corner-touching formula`() {
        // §3.4.1: lineLen = |W·sinθ| + |H·cosθ|; 45deg on a 100×100 box
        // → 100·√2 ≈ 141.421 (perpendiculars touch opposite corners).
        val (from, to) = ColorApplier.linearGradientPoints(45f, Size(100f, 100f))
        val dx = to.x - from.x
        val dy = to.y - from.y
        assertEquals(141.421f, kotlin.math.sqrt(dx * dx + dy * dy), 0.01f)
    }

    // ---- (3) position never shifts the line inside its tile ----

    @Test
    fun `gradient line midpoint is always the box centre`() {
        // The signature is position-free by design; this pins the geometry
        // so a future "helpful" position parameter can't silently re-shift
        // the centre (the old code moved it by posX·w / posY·h).
        for (angle in listOf(0f, 30f, 90f, 135f, 217f)) {
            val (from, to) = ColorApplier.linearGradientPoints(angle, Size(240f, 80f))
            assertEquals("cx@$angle", 120f, (from.x + to.x) / 2f, 0.001f)
            assertEquals("cy@$angle", 40f, (from.y + to.y) / 2f, 0.001f)
        }
    }

    // ---- (2) tile pass: overhanging plans stay bounded by the clip ----

    @Test
    fun `repeat plan overhangs the box and clip pins it to the border box`() {
        // The audit geometry: 200×100 box, 30×30 tiles, repeat both axes.
        val pass = ColorApplier.planTilePass(
            box = Size(200f, 100f), tileW = 30f, tileH = 30f,
            anchorX = 0f, anchorY = 0f,
            repeat = BackgroundRepeatAxes(AxisRepeat.REPEAT, AxisRepeat.REPEAT)
        )
        // The REPEAT grid deliberately overshoots: last column starts at
        // 180 and ends at 210 — 10px past the 200px box (the unclipped
        // painted bbox measured in the audit evidence).
        val maxRight = pass.origins.maxOf { it.x } + pass.tileSize.width
        assertEquals(210f, maxRight, 0.001f)
        // …which is exactly why the clip must be the border box, never
        // widened to cover the overshoot (css-backgrounds-3 §2.2).
        assertEquals(Size(200f, 100f), pass.clip)
        assertTrue("tiles must overflow the clip (clip is load-bearing)",
                   maxRight > pass.clip.width)
        // Grid completeness: 7 columns (0..180) × 4 rows (0..90).
        assertEquals(28, pass.origins.size)
        // Integer pitch → snapped drawn extents equal the tile exactly
        // (the snap is the identity here), one per origin.
        assertEquals(28, pass.drawSizes.size)
        assertTrue(pass.drawSizes.all { it == Size(30f, 30f) })
    }

    @Test
    fun `round plan rescales the drawn tile in the pass`() {
        // §3.7 round: 140px axis / 50px tile → 3 tiles of 140/3 px pitch.
        // The pass's tileSize is what the pinned SHADER receives (stays
        // fractional); the drawRect consumes the per-tile SNAPPED extents
        // in drawSizes (edges 0/47/93/140 → widths 47/46/47) so abutting
        // AA'd rects share integer edges instead of leaking a seam.
        val pass = ColorApplier.planTilePass(
            box = Size(140f, 140f), tileW = 50f, tileH = 50f,
            anchorX = 0f, anchorY = 0f,
            repeat = BackgroundRepeatAxes(AxisRepeat.ROUND, AxisRepeat.ROUND)
        )
        assertEquals(140f / 3f, pass.tileSize.width, 0.001f)
        assertEquals(140f / 3f, pass.tileSize.height, 0.001f)
        assertEquals(9, pass.origins.size)
        // drawSizes is index-parallel to origins (tile k = origins[k] +
        // drawSizes[k]); flattening order is x-outer/y-inner, so tile
        // (col i, row j) sits at index i·rows + j.
        assertEquals(9, pass.drawSizes.size)
        // Snapped per-axis widths: col/row 0 → 47, 1 → 46, 2 → 47.
        assertEquals(Size(47f, 47f), pass.drawSizes[0])     // (0,0)
        assertEquals(Size(46f, 46f), pass.drawSizes[4])     // (1,1)
        assertEquals(Size(47f, 47f), pass.drawSizes[8])     // (2,2)
        // Every tile's closing edge lands EXACTLY on a neighbour's origin
        // or the 140px area edge — shared integer edges are the seam fix.
        pass.origins.zip(pass.drawSizes).forEach { (o, s) ->
            val right = o.x + s.width
            assertTrue("right edge $right", right == 47f || right == 93f || right == 140f)
        }
    }

    @Test
    fun `no-repeat plan places the single tile at the position anchor`() {
        // background-position moves the TILE (§3.6) — the anchor lands in
        // the plan's origin, NOT in the shader (see midpoint test above).
        val pass = ColorApplier.planTilePass(
            box = Size(200f, 100f), tileW = 30f, tileH = 30f,
            anchorX = 35f, anchorY = 10f,
            repeat = BackgroundRepeatAxes(AxisRepeat.NO_REPEAT, AxisRepeat.NO_REPEAT)
        )
        assertEquals(listOf(Offset(35f, 10f)), pass.origins)
    }

    @Test
    fun `degenerate tile yields an empty pass`() {
        // Zero-size tiles draw nothing (mirrors BackgroundTileMath's CSS
        // zero-image rule); the drawBehind branch early-returns on this.
        val pass = ColorApplier.planTilePass(
            box = Size(200f, 100f), tileW = 0f, tileH = 30f,
            anchorX = 0f, anchorY = 0f,
            repeat = BackgroundRepeatAxes(AxisRepeat.REPEAT, AxisRepeat.REPEAT)
        )
        assertTrue(pass.origins.isEmpty())
    }

    // ---- (2b) geometry ROUTING: gradientNeedsGeometry (lane PW) --------
    //
    // The old gate was `sized == null → Modifier.background(brush)`, so
    // background-position without an explicit background-size was silently
    // dropped on Android: web and iOS wrap a box-sized tile through the
    // offset (seam at the anchor), Compose painted the plain full-box
    // gradient. gradientNeedsGeometry now mirrors iOS's
    // BackgroundImageApplier.gradientNeedsGeometry: any non-default
    // position or non-`repeat` axis routes through the tile path with
    // tile = box (auto size for an intrinsic-less gradient = the box,
    // css-backgrounds-3 §3.9). These tests pin BOTH directions of the
    // predicate so the default path can never re-widen (baseline safety)
    // and the knob paths can never re-narrow (the regression itself).

    // Shorthands for the CSS initial values of the three knobs.
    private val defaultPos = BackgroundPositionConfig()
    private val defaultRepeat = BackgroundRepeatAxes(AxisRepeat.REPEAT, AxisRepeat.REPEAT)

    @Test
    fun `position-only gradient routes through the tile path`() {
        // The repro knob: `background-position: 40px 0px`, NO size. The
        // 40px offset lives in xOffset (raw px edge offset, §3.6).
        val pos = BackgroundPositionConfig(xOffset = 40.dp)
        assertTrue(ColorApplier.gradientNeedsGeometry(
            layerSize = BackgroundSizeConfig.Auto, position = pos,
            repeat = defaultRepeat))
    }

    @Test
    fun `percent position routes too`() {
        // `background-position: 50% 50%` — fractional anchor, no px part.
        assertTrue(ColorApplier.gradientNeedsGeometry(
            layerSize = BackgroundSizeConfig.Auto,
            position = BackgroundPositionConfig.CENTER,
            repeat = defaultRepeat))
    }

    @Test
    fun `no knobs keeps the plain background path`() {
        // Baseline safety: a knob-less gradient must NOT route — the
        // Modifier.background(brush) call stays byte-identical so every
        // knob-less gradient baseline is untouched by this wave. Same for
        // cover/contain, which resolve to the box anyway (§3.9).
        for (size in listOf(BackgroundSizeConfig.Auto, BackgroundSizeConfig.Cover,
                            BackgroundSizeConfig.Contain)) {
            assertFalse("size=$size", ColorApplier.gradientNeedsGeometry(
                layerSize = size, position = defaultPos,
                repeat = defaultRepeat))
        }
    }

    @Test
    fun `explicit dimensions still route (the pre-wave gate)`() {
        // The original sized-tile pathway must keep routing unchanged.
        assertTrue(ColorApplier.gradientNeedsGeometry(
            layerSize = BackgroundSizeConfig.Dimensions(width = 30.dp, height = 30.dp),
            position = defaultPos, repeat = defaultRepeat))
    }

    @Test
    fun `non-repeat axis routes even without position`() {
        // `background-repeat: no-repeat` changes the lattice (§3.7) —
        // mirrors the iOS predicate's repeat clause axis-by-axis.
        assertTrue(ColorApplier.gradientNeedsGeometry(
            layerSize = BackgroundSizeConfig.Auto, position = defaultPos,
            repeat = BackgroundRepeatAxes(AxisRepeat.NO_REPEAT, AxisRepeat.REPEAT)))
    }

    @Test
    fun `repeating gradients route like any other when a knob is set`() {
        // Wave 47 removed the wave-2 repeating exclusion: the resolver-
        // backed brushes rebuild their §3.4.4 lattice per createShader
        // size, so pinShaderToTile pins them like any other gradient and
        // the predicate is the exact iOS mirror (which never excluded
        // repeating flavours). Same knobs as the old exclusion pin —
        // the expectation flips to routing.
        assertTrue(ColorApplier.gradientNeedsGeometry(
            layerSize = BackgroundSizeConfig.Dimensions(width = 30.dp, height = 30.dp),
            position = BackgroundPositionConfig(xOffset = 40.dp),
            repeat = defaultRepeat))
    }

    @Test
    fun `tile-equals-box plan wraps a 40px offset with the seam phase preserved`() {
        // The skeptic's repro geometry: 200×120 box, linear-gradient,
        // `background-position: 40px 0px`, no size → tile = box (§3.9).
        // REPEAT normalizes the 40px anchor into (−tile, 0] (start
        // 40 − 200 = −160), so the grid is TWO x-tiles: the wrapped tail
        // at −160 (its visible part covers x∈[0,40)) and the anchored
        // tile at 40 — the wrap seam web/iOS show at x=40.
        val pass = ColorApplier.planTilePass(
            box = Size(200f, 120f), tileW = 200f, tileH = 120f,
            anchorX = 40f, anchorY = 0f, repeat = defaultRepeat)
        // Shader pitch = the box-sized tile (the no-op pin case).
        assertEquals(Size(200f, 120f), pass.tileSize)
        // Exactly the two phase-preserving origins; y stays a single row.
        assertEquals(listOf(Offset(-160f, 0f), Offset(40f, 0f)), pass.origins)
        // Integer anchor/pitch → the snap is the identity: both tiles
        // draw the full 200×120 extent (clipped to the box by clipRect).
        assertTrue(pass.drawSizes.all { it == Size(200f, 120f) })
    }

    // ---- (4) radialAxisScale: circle→ellipse squash DIRECTION ----------
    //
    // The radial brush simulates a CSS ellipse by building a circular
    // RadialGradientShader at rMax = max(rx, ry) plus a local matrix.
    // Skia's setLocalMatrix maps the shader IMAGE through the matrix, so
    // the squash factors must be radius/rMax (≤ 1) — the old inline
    // rMax/radius was the exact inverse and stretched the wrong axis on
    // every non-circular radial (the mask path copied the same inversion;
    // MaskGradientGeometryTest pins its delegation here).

    @Test
    fun `wide-box radial squashes the vertical axis of the rMax circle`() {
        // Centred farthest-corner ellipse on a 160×80 box: rx = 80√2,
        // ry = 40√2 → the X axis is rMax (sx = 1) and Y squashes to the
        // radius ratio 0.5. The inverted pre-fix factors were (1, 2).
        val k = kotlin.math.sqrt(2f)
        val (sx, sy) = ColorApplier.radialAxisScale(80f * k, 40f * k)
        assertEquals(1f, sx, 1e-6f)
        assertEquals(0.5f, sy, 1e-6f)
    }

    @Test
    fun `tall-box radial squashes the horizontal axis instead`() {
        // ry > rx mirror: sy = 1 (Y is the shader radius) and X shrinks
        // by rx/ry — the squash follows the SHORT axis, never fixed.
        val (sx, sy) = ColorApplier.radialAxisScale(45f, 180f)
        assertEquals(0.25f, sx, 1e-6f)
        assertEquals(1f, sy, 1e-6f)
    }

    @Test
    fun `squash factors never exceed one`() {
        // The direction invariant behind the fix: the rMax circle only
        // ever SHRINKS toward the ellipse; a factor > 1 means the matrix
        // is inverted again.
        for ((rx, ry) in listOf(10f to 200f, 200f to 10f, 77f to 77f)) {
            val (sx, sy) = ColorApplier.radialAxisScale(rx, ry)
            assertTrue("($rx, $ry) → ($sx, $sy)", sx <= 1f && sy <= 1f)
            // And the long axis is untouched — exactly one of the two
            // factors is 1 unless the radii are equal (then both are).
            assertEquals(1f, kotlin.math.max(sx, sy), 0f)
        }
    }

    @Test
    fun `degenerate zero radii clamp to the epsilon guard, not NaN`() {
        // A zero-sized box collapses both radii; the ε-clamp (1e-3) keeps
        // the division finite and yields the identity scale.
        val (sx, sy) = ColorApplier.radialAxisScale(0f, 0f)
        assertEquals(1f, sx, 0f)
        assertEquals(1f, sy, 0f)
    }
}

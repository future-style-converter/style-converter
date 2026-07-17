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
import org.junit.Assert.assertEquals
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
    }

    @Test
    fun `round plan rescales the drawn tile in the pass`() {
        // §3.7 round: 140px axis / 50px tile → 3 tiles of 140/3 px. The
        // pass's tileSize is what both the drawRect AND the pinned shader
        // receive, so the rescale must surface here.
        val pass = ColorApplier.planTilePass(
            box = Size(140f, 140f), tileW = 50f, tileH = 50f,
            anchorX = 0f, anchorY = 0f,
            repeat = BackgroundRepeatAxes(AxisRepeat.ROUND, AxisRepeat.ROUND)
        )
        assertEquals(140f / 3f, pass.tileSize.width, 0.001f)
        assertEquals(140f / 3f, pass.tileSize.height, 0.001f)
        assertEquals(9, pass.origins.size)
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
}

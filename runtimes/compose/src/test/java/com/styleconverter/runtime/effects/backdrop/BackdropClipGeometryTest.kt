package com.styleconverter.runtime.effects.backdrop

// Pins the border-box corner radii the backdrop patch is clipped with.
// backdrop-filter-clip-rect.html is the test that cares: an inverting navbar
// with `border-radius: 10px 20px 30px 40px`, whose inversion must follow the
// rounded corners and stop at the border box everywhere else.

import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.borders.radius.BorderRadiusConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropClipGeometryTest {

    // Density 1: the WPT canvas works in a px==dp space, so this is also the
    // conversion the composed capture actually runs with.
    private val dpToPx: (androidx.compose.ui.unit.Dp) -> Float = { it.value }

    @Test
    fun `square box needs no path`() {
        // Null means "clip with a rectangle, allocate nothing" — the same
        // short-circuit BorderRadiusApplier takes for square corners.
        assertNull(
            BackdropClipGeometry.resolve(BorderRadiusConfig.NONE, 100f, 50f, dpToPx),
        )
    }

    @Test
    fun `four distinct corners map to physical corners in ltr`() {
        // The clip-rect navbar, corner for corner.
        val config = BorderRadiusConfig(
            topStart = 10.dp to 10.dp,
            topEnd = 20.dp to 20.dp,
            bottomEnd = 30.dp to 30.dp,
            bottomStart = 40.dp to 40.dp,
        )
        val r = BackdropClipGeometry.resolve(config, 300f, 50f, dpToPx)!!
        assertEquals(10f, r.topLeftX, 0f)
        assertEquals(20f, r.topRightX, 0f)
        assertEquals(30f, r.bottomRightX, 0f)
        assertEquals(40f, r.bottomLeftX, 0f)
        assertTrue(!r.isSquare)
    }

    @Test
    fun `rtl swaps start and end`() {
        val config = BorderRadiusConfig(topStart = 10.dp to 10.dp, topEnd = 20.dp to 20.dp)
        val r = BackdropClipGeometry.resolve(config, 300f, 50f, dpToPx, isLtr = false)!!
        // start is the RIGHT edge in RTL — same mapping EllipticalCornerShape
        // applies, so patch and box round the same corners.
        assertEquals(20f, r.topLeftX, 0f)
        assertEquals(10f, r.topRightX, 0f)
    }

    @Test
    fun `percentage axes resolve against width and height`() {
        // css-backgrounds-3 §4.4: x against the border-box WIDTH, y against
        // its HEIGHT — `border-radius: 50%` on a 300x50 box is an ellipse.
        val config = BorderRadiusConfig(
            topStart = 0.dp to 0.dp,
            topStartFraction = 0.5f to 0.5f,
        )
        val r = BackdropClipGeometry.resolve(config, 300f, 50f, dpToPx)!!
        assertEquals(150f, r.topLeftX, 0f)
        assertEquals(25f, r.topLeftY, 0f)
    }

    @Test
    fun `elliptical corner keeps its two axes`() {
        // `border-radius: 40px / 20px` — the pair must not collapse to one.
        val config = BorderRadiusConfig(topStart = 40.dp to 20.dp)
        val r = BackdropClipGeometry.resolve(config, 300f, 50f, dpToPx)!!
        assertEquals(40f, r.topLeftX, 0f)
        assertEquals(20f, r.topLeftY, 0f)
    }
}

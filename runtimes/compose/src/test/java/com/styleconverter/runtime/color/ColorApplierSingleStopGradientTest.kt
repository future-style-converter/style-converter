package com.styleconverter.runtime.color

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Wave 36, lane M8 — pins ColorApplier.paintableStops, the one-stop gradient
 * widening.
 *
 * ## What is being protected
 * `css/css-images/gradient/gradient-single-stop-00{1..8}` each paint a GREEN
 * div with a degenerate one-stop gradient (`linear-gradient(green)`,
 * `linear-gradient(to right, green 90%)`, `repeating-linear-gradient(green
 * 50px)`, and the radial/conic equivalents) directly over an abspos RED div.
 * css-images-3 §3.4.4 makes the one-stop ramp a UNIFORM fill of that colour,
 * so the correct render is a green square; Chromium, the web runtime and the
 * SwiftUI runtime all produce one.
 *
 * Compose's shaders need ≥2 entries, so all three brush factories used to
 * `return null` here — which does not paint nothing, it makes the layer fall
 * through and expose the red div underneath. All eight cells rasterised RED on
 * Android (SSIM 0.988 with a colour-mass veto) while ref/web/iOS rasterised
 * GREEN. Eight depth-48 cells, one guard.
 *
 * ## The invariant that must never break
 * A two-or-more-stop ramp must come back IDENTICAL (same list instance), so
 * every pre-wave-36 gradient capture is byte-for-byte unchanged; and a genuinely
 * empty stop list must still answer null so a malformed payload fails visibly
 * rather than inventing a colour.
 */
class ColorApplierSingleStopGradientTest {

    private val green = Color(0f, 0.5019608f, 0f, 1f)
    private val blue = Color(0f, 0f, 1f, 1f)

    /** The exact stop the converter emits for `linear-gradient(green)`:
     *  one stop, `position: null` → the extractor's even-spread default 0. */
    private val soleGreen = listOf(ColorStop(green, 0f))

    @Test
    fun `a one-stop ramp widens to the same colour at both ends`() {
        val out = ColorApplier.paintableStops(soleGreen)!!
        assertEquals(2, out.size)
        assertEquals(green, out[0].color)
        assertEquals(green, out[1].color)
        assertEquals(0f, out[0].position, 0f)
        assertEquals(1f, out[1].position, 0f)
    }

    @Test
    fun `the sole stop's declared position is irrelevant to the widening`() {
        // gradient-single-stop-002 is `linear-gradient(to right, green 90%)`:
        // §3.4.4 fixes every earlier position to the first stop's colour and
        // every later one to the last stop's, and with ONE stop those are the
        // same colour — so the box is uniform whatever the position said.
        val out = ColorApplier.paintableStops(listOf(ColorStop(green, 0.9f)))!!
        assertEquals(listOf(0f, 1f), out.map { it.position })
        assertEquals(listOf(green, green), out.map { it.color })
    }

    @Test
    fun `a two-stop ramp is returned untouched`() {
        val ramp = listOf(ColorStop(green, 0f), ColorStop(blue, 1f))
        assertSame(ramp, ColorApplier.paintableStops(ramp))
    }

    @Test
    fun `a many-stop ramp is returned untouched`() {
        val ramp = listOf(ColorStop(green, 0f), ColorStop(blue, 0.5f), ColorStop(green, 1f))
        assertSame(ramp, ColorApplier.paintableStops(ramp))
    }

    @Test
    fun `an empty stop list stays unpaintable`() {
        assertNull(ColorApplier.paintableStops(emptyList()))
    }
}

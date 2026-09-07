package com.styleconverter.runtime.background

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.styleconverter.runtime.color.ColorApplier
import com.styleconverter.runtime.color.ColorStop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Wave 46, lane Y2 — pins the one-stop guard on the REPEATING brush
 * factories (RepeatingGradientHelper header, "One-stop gradients").
 *
 * ## What is being protected
 * WPT `gradient-single-stop-003` (`repeating-linear-gradient(green 50px)`)
 * and `-005` (`repeating-radial-gradient(circle at 0 0, green)`) paint a
 * green div over an abspos RED div. css-images-3 §3.4.1 makes the
 * one-stop ramp a uniform green fill; wave 36 taught the NON-repeating
 * factories that (ColorApplier.paintableStops), but the three repeating
 * factories kept `if (colorStops.size < 2) return null` — and a null
 * brush drops the layer, exposing the red. Both cells rasterised RED on
 * Android (SSIM 0.998 vs the ref with the colour-mass veto firing) while
 * ref, web and iOS rasterised green.
 *
 * ## The invariant
 * One stop → a non-null brush WHOSE RAMP IS THAT COLOUR AT BOTH ENDS (the
 * shader sees the same colour at 0 and 1, so the fill is uniform); no
 * stops → still null, so a malformed payload fails visibly instead of
 * inventing a colour.
 *
 * ## Why the ramp is read back (retro R10, finding A8#1)
 * The wave-46 pins asserted only `assertNotNull(brush)`, which the audit's
 * mutation M3 — every stop recoloured Color.Magenta in all three
 * factories — passed. A brush of the wrong colour is exactly the red-div
 * failure class this file exists to catch, so the linear and radial pins
 * now read the brush's colour and stop lists. Compose's `LinearGradient` /
 * `RadialGradient` (androidx.compose.ui.graphics, `Brush.linearGradient` /
 * `Brush.radialGradient`) keep them as `private val colors: List<Color>`
 * and `private val stops: List<Float>?` — plain Kotlin value objects (the
 * Android shader is only created inside `createShader`), so JUnit can
 * construct the brush and read the fields reflectively with no device.
 * The conic factory returns an anonymous `ShaderBrush` whose stops live
 * only inside `createShader` (an android.graphics.SweepGradient, a stub on
 * the JVM); its ramp is pinned through the captured `effectiveStops`
 * array — the only state the anonymous object holds.
 */
class RepeatingGradientHelperTest {

    private val green = Color(0f, 0.5019608f, 0f, 1f)

    /** The exact stop the extractor builds for `repeating-linear-gradient(green 50px)`
     *  today: the px arm is dropped in color/ColorExtractor, leaving the
     *  even-spread default 0 (see the helper's "Known gap" note). */
    private val soleGreen = listOf(ColorStop(green, 0f))

    /** Read a `LinearGradient` / `RadialGradient`'s private `colors` list. */
    private fun colorsOf(brush: Brush): List<Color> = privateList(brush, "colors")

    /** Read a `LinearGradient` / `RadialGradient`'s private `stops` list. */
    private fun stopsOf(brush: Brush): List<Float> = privateList(brush, "stops")

    /** Reflective read of a private `List` field — the Compose BOM is pinned
     *  (2026.06.01, both build.gradle.kts files), so the field names are
     *  stable; a rename fails LOUDLY here. */
    @Suppress("UNCHECKED_CAST")
    private fun <T> privateList(brush: Brush, field: String): List<T> =
        brush.javaClass.getDeclaredField(field).also { it.isAccessible = true }.get(brush) as List<T>

    /** The conic factory's anonymous ShaderBrush captures `effectiveStops`
     *  (an `Array<Pair<Float, Color>>`) for its lazy `createShader`; find it
     *  by TYPE so the pin does not depend on the compiler's synthetic name. */
    @Suppress("UNCHECKED_CAST")
    private fun conicStopsOf(brush: Brush): List<Pair<Float, Color>> =
        brush.javaClass.declaredFields
            .map { it.isAccessible = true; it.get(brush) }
            .filterIsInstance<Array<*>>()
            .single { arr -> arr.all { it is Pair<*, *> } }
            .map { it as Pair<Float, Color> }

    @Test
    fun `one-stop repeating linear gradient still yields a brush - a uniform green ramp`() {
        val brush = RepeatingGradientHelper.createRepeatingLinearGradient(
            angle = 180f, colorStops = soleGreen, size = Size(100f, 100f))
        assertNotNull(brush)
        // The one-stop rule this helper's header cites (css-images-3
        // gradient colour-stops: a single stop paints a UNIFORM image):
        // the sole colour at BOTH ends of the gradient line — the widened
        // list paintableStops builds, normalised to 0 and 1.
        assertEquals(listOf(green, green), colorsOf(brush!!))
        assertEquals(listOf(0f, 1f), stopsOf(brush))
    }

    @Test
    fun `one-stop repeating radial gradient still yields a brush - a uniform green ramp`() {
        // gradient-single-stop-005: circle at 0 0 → fractions (0, 0).
        val brush = RepeatingGradientHelper.createRepeatingRadialGradient(
            centerX = 0f, centerY = 0f, colorStops = soleGreen, size = Size(100f, 100f))
        assertNotNull(brush)
        // The same one-stop widening on the radial ramp.
        assertEquals(listOf(green, green), colorsOf(brush!!))
        assertEquals(listOf(0f, 1f), stopsOf(brush))
    }

    @Test
    fun `one-stop repeating conic gradient still yields a brush - every sweep stop is green`() {
        val brush = RepeatingGradientHelper.createRepeatingConicGradient(
            centerX = 0.5f, centerY = 0.5f, startAngle = 0f,
            colorStops = soleGreen, size = Size(100f, 100f))
        assertNotNull(brush)
        // The conic path expands the ramp over its repetitions; whatever the
        // positions, EVERY expanded stop must be the sole colour (a uniform
        // sweep), and the sweep must span the full circle (a 0 and a 1 stop).
        val stops = conicStopsOf(brush!!)
        assertEquals(setOf(green), stops.map { it.second }.toSet())
        assertEquals(0f, stops.first().first)
        assertEquals(1f, stops.last().first)
    }

    @Test
    fun `the shared one-stop widening is the list every factory consumes`() {
        // The single guard all three factories route through (helper header):
        // one stop → that colour pinned at 0 and at 1, positions forced.
        // Compared as (colour, position) pairs: `copy(position = …)` keeps the
        // sole stop's other fields (declaredPosition, positionPx) verbatim,
        // which is correct and not what this pin is about.
        assertEquals(
            listOf(green to 0f, green to 1f),
            ColorApplier.paintableStops(soleGreen)!!.map { it.color to it.position },
        )
    }

    @Test
    fun `an empty stop list stays unpaintable on every factory`() {
        assertNull(RepeatingGradientHelper.createRepeatingLinearGradient(
            angle = 180f, colorStops = emptyList()))
        assertNull(RepeatingGradientHelper.createRepeatingRadialGradient(
            centerX = 0.5f, centerY = 0.5f, colorStops = emptyList()))
        assertNull(RepeatingGradientHelper.createRepeatingConicGradient(
            centerX = 0.5f, centerY = 0.5f, startAngle = 0f, colorStops = emptyList()))
    }

    @Test
    fun `two-stop ramps keep building (the pre-wave path) with their own colours`() {
        // Gradient_RepeatingLinear fixture shape: red → blue over the
        // whole line (px arm dropped upstream) — must keep returning a brush
        // that carries THOSE two colours in declaration order.
        val ramp = listOf(ColorStop(Color.Red, 0f), ColorStop(Color.Blue, 1f))
        val brush = RepeatingGradientHelper.createRepeatingLinearGradient(
            angle = 45f, colorStops = ramp, size = Size(160f, 80f))
        assertNotNull(brush)
        assertEquals(listOf(Color.Red, Color.Blue), colorsOf(brush!!))
        assertEquals(listOf(0f, 1f), stopsOf(brush))
    }
}

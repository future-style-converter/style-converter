package com.styleconverter.runtime.background

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.styleconverter.runtime.color.ColorStop
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
 * green div over an abspos RED div. css-images-3 §3.4.4 makes the
 * one-stop ramp a uniform green fill; wave 36 taught the NON-repeating
 * factories that (ColorApplier.paintableStops), but the three repeating
 * factories kept `if (colorStops.size < 2) return null` — and a null
 * brush drops the layer, exposing the red. Both cells rasterised RED on
 * Android (SSIM 0.998 vs the ref with the colour-mass veto firing) while
 * ref, web and iOS rasterised green.
 *
 * ## The invariant
 * One stop → a non-null brush (the shader sees the same colour at 0 and
 * 1, so the fill is uniform); no stops → still null, so a malformed
 * payload fails visibly instead of inventing a colour.
 *
 * Compose's Brush factories are plain Kotlin value objects (the Android
 * shader is only created inside `createShader`), so plain JUnit can
 * construct them without an instrumented device.
 */
class RepeatingGradientHelperTest {

    private val green = Color(0f, 0.5019608f, 0f, 1f)

    /** The exact stop the extractor builds for `repeating-linear-gradient(green 50px)`
     *  today: the px arm is dropped in color/ColorExtractor, leaving the
     *  even-spread default 0 (see the helper's "Known gap" note). */
    private val soleGreen = listOf(ColorStop(green, 0f))

    @Test
    fun `one-stop repeating linear gradient still yields a brush`() {
        assertNotNull(RepeatingGradientHelper.createRepeatingLinearGradient(
            angle = 180f, colorStops = soleGreen, size = Size(100f, 100f)))
    }

    @Test
    fun `one-stop repeating radial gradient still yields a brush`() {
        // gradient-single-stop-005: circle at 0 0 → fractions (0, 0).
        assertNotNull(RepeatingGradientHelper.createRepeatingRadialGradient(
            centerX = 0f, centerY = 0f, colorStops = soleGreen, size = Size(100f, 100f)))
    }

    @Test
    fun `one-stop repeating conic gradient still yields a brush`() {
        assertNotNull(RepeatingGradientHelper.createRepeatingConicGradient(
            centerX = 0.5f, centerY = 0.5f, startAngle = 0f,
            colorStops = soleGreen, size = Size(100f, 100f)))
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
    fun `two-stop ramps keep building (the pre-wave path)`() {
        // Gradient_RepeatingLinear fixture shape: red → blue over the
        // whole line (px arm dropped upstream) — must keep returning a brush.
        val ramp = listOf(ColorStop(Color.Red, 0f), ColorStop(Color.Blue, 1f))
        assertNotNull(RepeatingGradientHelper.createRepeatingLinearGradient(
            angle = 45f, colorStops = ramp, size = Size(160f, 80f)))
    }
}

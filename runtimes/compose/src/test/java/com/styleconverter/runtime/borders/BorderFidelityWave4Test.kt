package com.styleconverter.runtime.borders

// Fidelity wave 4 (applier campaign, compose lane) — pinning tests for
// two Android 3D-border divergence fixes in BorderSideApplier:
//
//  1. GROOVE/RIDGE PER-SIDE BAND ORDER: Blink composes groove as two
//     half-borders styled inset(outer)/outset(inner) — ridge swaps them —
//     and EACH half then follows the per-side darkening rule
//     `dark ⇔ (side == top‖left) == (style == inset)`. The old code
//     hardcoded groove = dark-outer/light-inner on ALL four sides, which
//     is only correct on top/left; on bottom/right the bands must mirror
//     (light-outer/dark-inner) so the trench reads as lit from the CSS
//     top-left light source. grooveRidgeBandShades owns the rule.
//
//  2. shade() GAMUT MODEL: the wave-3 ×0.65 dark factor was a single-
//     point fit at v = 0.937 (155/239 measured). Blink Color::Dark() is
//     subtractive — multiplier = max(0, (v − 0.33)/v), v = max(r,g,b) —
//     so mid-gray darkens ×0.343, not ×0.65. And when v ≤ 0.33 the dark
//     band collapses to black, so Blink derives the LIGHT band via
//     Color::Light() (black → kLightenedBlack rgb(84,84,84)) to keep the
//     two-tone visible; normal bases keep light = base unchanged.

import androidx.compose.ui.graphics.Color
import com.styleconverter.runtime.borders.sides.BorderSideApplier
import com.styleconverter.runtime.borders.sides.BorderSideApplier.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BorderFidelityWave4Test {

    // Crimson rgb(220,20,60) — the Borders_C14 fixture color, with a
    // fresh headless-Chrome band measurement on record (OutlineDarkenTest
    // cites 5px ridge crimson inner → rgb(136,12,37)).
    private val crimson = Color(220 / 255f, 20 / 255f, 60 / 255f)

    // Shorthands for the two crimson bands the palette can produce —
    // light band = base (crimson is a normal, v > 0.33 base).
    private val light = BorderSideApplier.shade(crimson, lighten = true)
    private val dark = BorderSideApplier.shade(crimson, lighten = false)

    // ── 1a. Groove band order, all four sides ────────────────────────────

    @Test
    fun `groove is dark-outer light-inner on top and left`() {
        // Outer half styled inset → dark on top/left (sunken trench wall
        // facing away from the light); inner half outset → light there.
        for (side in listOf(Side.TOP, Side.START)) {
            val (outer, inner) = BorderSideApplier.grooveRidgeBandShades(crimson, side, groove = true)
            assertEquals("outer band on $side", dark, outer)
            assertEquals("inner band on $side", light, inner)
        }
    }

    @Test
    fun `groove is light-outer dark-inner on bottom and right`() {
        // The per-side rule flips on bottom/right: the inset-styled outer
        // half goes LIGHT there — the pre-wave-4 code painted these two
        // sides inverted (dark-outer everywhere).
        for (side in listOf(Side.BOTTOM, Side.END)) {
            val (outer, inner) = BorderSideApplier.grooveRidgeBandShades(crimson, side, groove = true)
            assertEquals("outer band on $side", light, outer)
            assertEquals("inner band on $side", dark, inner)
        }
    }

    // ── 1b. Ridge band order — the exact mirror of groove ────────────────

    @Test
    fun `ridge is light-outer dark-inner on top and left`() {
        // Ridge swaps the half styles (outer = outset), so the raised rim
        // catches the light on its top/left OUTER half.
        for (side in listOf(Side.TOP, Side.START)) {
            val (outer, inner) = BorderSideApplier.grooveRidgeBandShades(crimson, side, groove = false)
            assertEquals("outer band on $side", light, outer)
            assertEquals("inner band on $side", dark, inner)
        }
    }

    @Test
    fun `ridge is dark-outer light-inner on bottom and right`() {
        // And on bottom/right the ridge's outer (outset-styled) half is
        // in shadow — mirrored from groove on the same sides.
        for (side in listOf(Side.BOTTOM, Side.END)) {
            val (outer, inner) = BorderSideApplier.grooveRidgeBandShades(crimson, side, groove = false)
            assertEquals("outer band on $side", dark, outer)
            assertEquals("inner band on $side", light, inner)
        }
    }

    @Test
    fun `groove outer band equals the inset shade of the same side`() {
        // The whole fix is REUSE of the inset/outset per-side rule: a
        // groove's outer half must shade exactly like an inset border on
        // that side (Blink literally paints it as one), on every side.
        for (side in Side.entries) {
            val (outer, _) = BorderSideApplier.grooveRidgeBandShades(crimson, side, groove = true)
            assertEquals(
                "inset-half equivalence on $side",
                BorderSideApplier.shade(crimson, lighten = BorderSideApplier.isLightBand(side, inset = true)),
                outer
            )
        }
    }

    // ── 2. shade() subtractive gamut model ───────────────────────────────

    @Test
    fun `crimson dark band matches the headless-Chrome measurement`() {
        // Independent confirmation of the model at a second measured
        // point: Blink paints crimson rgb(220,20,60) dark bands as
        // rgb(136,12,37) (outline-probe.png). v = 220/255 = 0.8627 →
        // multiplier (0.8627 − 0.33)/0.8627 = 0.6175 — NOT 0.65.
        assertEquals(136f, dark.red * 255f, 1f)   // 220 × 0.6175 ≈ 135.9
        assertEquals(12f, dark.green * 255f, 1f)  // 20 × 0.6175 ≈ 12.4
        assertEquals(37f, dark.blue * 255f, 1f)   // 60 × 0.6175 ≈ 37.1
    }

    @Test
    fun `mid-gray darkens by the subtractive 0_343 multiplier`() {
        // #808080: v = 128/255 = 0.502 → multiplier 0.3426. The old flat
        // ×0.65 would have left the dark band nearly twice as bright.
        val darkGray = BorderSideApplier.shade(Color(128 / 255f, 128 / 255f, 128 / 255f), lighten = false)
        assertEquals(0.343f, darkGray.red / (128 / 255f), 0.001f)  // the multiplier itself
        assertEquals(43.9f, darkGray.red * 255f, 1f)               // 128 × 0.3426 ≈ 43.9
    }

    @Test
    fun `black keeps a visible two-tone via the lightened-black band`() {
        // v = 0 → dark multiplier 0, so the dark band is black — and the
        // light band lifts to Blink's kLightenedBlack rgb(84,84,84) so a
        // black groove/ridge border does not vanish into a flat line.
        // (Alpha 0.4 is 8-bit exact — 0.4 × 255 = 102 — because sRGB
        // Color packs channels to 8 bits, like the wave-3 alpha pin.)
        val base = Color(0f, 0f, 0f, 0.4f)
        val darkBand = BorderSideApplier.shade(base, lighten = false)
        val lightBand = BorderSideApplier.shade(base, lighten = true)
        assertEquals(0f, darkBand.red * 255f, 0.01f)     // dark band stays black
        assertEquals(84f, lightBand.red * 255f, 0.5f)    // kLightenedBlack channel
        assertEquals(84f, lightBand.green * 255f, 0.5f)
        assertEquals(84f, lightBand.blue * 255f, 0.5f)
        assertEquals(0.4f, lightBand.alpha, 0.001f)      // alpha untouched by the lift
        assertNotEquals(darkBand, lightBand)             // the 3D contract: two tones
    }

    @Test
    fun `near-black bases lift the light band additively`() {
        // rgb(51,26,13): v = 0.2 ≤ 0.33, so the dark band bottoms out at
        // black and Color::Light() scales by min(1, 0.53)/0.2 = 2.65.
        val base = Color(51 / 255f, 26 / 255f, 13 / 255f)
        val darkBand = BorderSideApplier.shade(base, lighten = false)
        val lightBand = BorderSideApplier.shade(base, lighten = true)
        assertEquals(0f, darkBand.red * 255f, 0.01f)      // multiplier clamped to 0
        // Channel targets ±1: sRGB Color quantizes each channel to 8 bits.
        assertEquals(135.15f, lightBand.red * 255f, 1f)   // 51 × 2.65
        assertEquals(68.9f, lightBand.green * 255f, 1f)   // 26 × 2.65
        assertEquals(34.45f, lightBand.blue * 255f, 1f)   // 13 × 2.65
        // Hue is preserved: all channels share the one ×2.65 multiplier
        // (loose tolerance — 8-bit quantization skews small channels).
        assertEquals(2.65f, lightBand.red / base.red, 0.05f)
        assertEquals(2.65f, lightBand.blue / base.blue, 0.05f)
    }

    @Test
    fun `light lift keeps the max channel in gamut`() {
        // v ≈ 0.3 with min(1, v + 0.33): the max channel lands at v + 0.33
        // (never past 1) — the min() term is the gamut clamp. Tolerance
        // covers the double 8-bit quantization (base store + result store).
        val lightBand = BorderSideApplier.shade(Color(0.3f, 0.1f, 0.2f), lighten = true)
        assertEquals(0.63f, lightBand.red, 0.005f)
        assertTrue("all channels must stay in gamut", lightBand.red <= 1f && lightBand.green <= 1f && lightBand.blue <= 1f)
    }
}

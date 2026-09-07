package app.parsing.primitiveParsers

// Pins the <hue> grammar for hwb()/lch()/oklch().
//
// A HUE constant supporting angle units (deg/grad/rad/turn), negative
// values and scientific notation already existed — adopted by hsl() with a
// comment recording exactly why (`[\d.]+(?:deg)?` "failed to match
// grad/rad/turn/scientific hues and made the whole color parse null") —
// but hwb, lch and oklch never picked it up. hwb also lacked IGNORE_CASE
// and `none`. For lch/oklch the unit sat OUTSIDE the capture group, so any
// unit but a literal lowercase `deg` nulled the entire colour.
//
// Every angle identity below is exact: 100grad = 90deg, 0.25turn = 90deg,
// PI/2 rad = 90deg — so the assertions compare against the plain-degrees
// parse of the same colour rather than against hand-derived sRGB, which
// keeps them independent of the conversion matrices.

import app.parsing.css.properties.primitiveParsers.ColorParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HueUnitTest {

    private fun srgb(v: String): Triple<Double, Double, Double> {
        val c = ColorParser.parse(v)
        assertNotNull(c, "failed to parse: $v")
        val s = c.srgb
        assertNotNull(s, "no srgb for: $v")
        return Triple(s.r, s.g, s.b)
    }

    private fun assertSameColor(a: String, b: String, tol: Double = 1e-6) {
        val (r1, g1, b1) = srgb(a)
        val (r2, g2, b2) = srgb(b)
        assertEquals(r1, r2, tol, "$a vs $b (r)")
        assertEquals(g1, g2, tol, "$a vs $b (g)")
        assertEquals(b1, b2, tol, "$a vs $b (b)")
    }

    @Test
    fun `hwb accepts every angle unit`() {
        // 90deg = 100grad = 0.25turn = 1.5707963268rad — all one colour.
        assertSameColor("hwb(90 20% 10%)", "hwb(90deg 20% 10%)")
        assertSameColor("hwb(90 20% 10%)", "hwb(100grad 20% 10%)")
        assertSameColor("hwb(90 20% 10%)", "hwb(0.25turn 20% 10%)")
        assertSameColor("hwb(90 20% 10%)", "hwb(1.5707963268rad 20% 10%)", 1e-4)
    }

    @Test
    fun `hwb accepts negative hue, none, and uppercase`() {
        // -90deg wraps to 270deg (hue math is mod 360).
        assertSameColor("hwb(-90 20% 10%)", "hwb(270 20% 10%)")
        // none behaves as 0 (css-color-4 §4.1).
        assertSameColor("hwb(none 20% 10%)", "hwb(0 20% 10%)")
        // Function names are case-insensitive.
        assertSameColor("HWB(90deg 20% 10%)", "hwb(90 20% 10%)")
    }

    @Test
    fun `lch and oklch accept angle units on the hue`() {
        assertSameColor("lch(50% 40 90)", "lch(50% 40 90deg)")
        assertSameColor("lch(50% 40 90)", "lch(50% 40 100grad)")
        assertSameColor("lch(50% 40 90)", "lch(50% 40 0.25turn)")
        assertSameColor("oklch(0.7 0.15 90)", "oklch(0.7 0.15 90deg)")
        assertSameColor("oklch(0.7 0.15 90)", "oklch(0.7 0.15 0.25turn)")
    }

    @Test
    fun `lch and oklch accept negative hues`() {
        assertSameColor("lch(50% 40 -90)", "lch(50% 40 270)")
        assertSameColor("oklch(0.7 0.15 -90deg)", "oklch(0.7 0.15 270)")
    }

    @Test
    fun `the old failure mode is really gone`() {
        // Each of these previously made the ENTIRE declaration parse to
        // null — not a wrong hue, no colour at all. Retro R10 (A8#1): a
        // not-null check alone cannot tell "parsed" from "parsed to the
        // WRONG hue" — the audit's mutation M7 (grad read as degrees) passed
        // it while the sibling unit tests failed — so each input is compared
        // against its plain-degrees spelling. css-color-4 §4.1 <hue>:
        // 200grad = 180deg, 1.2rad = 68.7549354deg (1.2 × 180/π); -45deg
        // wraps to 315deg (hue is taken mod 360), and the rad identity is
        // irrational so it carries the same 1e-4 tolerance as the hwb rad
        // case above.
        assertSameColor("hwb(200grad 20% 10%)", "hwb(180 20% 10%)")
        assertSameColor("hwb(-45deg 0% 0%)", "hwb(315 0% 0%)")
        assertSameColor("lch(50% 40 1.2rad)", "lch(50% 40 68.7549354)", 1e-4)
        assertSameColor("oklch(0.7 0.15 200grad)", "oklch(0.7 0.15 180)")
    }

    @Test
    fun `garbage still rejects`() {
        // Widening the hue grammar must not have opened the gate to junk.
        assertNull(ColorParser.parse("hwb(red 20% 10%)"))
        assertNull(ColorParser.parse("lch(50% 40 90px)"))
        assertNull(ColorParser.parse("oklch(0.7 0.15 deg)"))
    }
}

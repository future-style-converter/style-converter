package app.parsing.css.properties.primitiveParsers

// Regression suite for the hsl()/hsla() HUE parser (TITAN WPT css-color/
// background-color-hsl-003 & -004). Before the fix the hue capture group was
// `[\d.]+(?:deg)?`, so a hue expressed in grad/rad/turn OR in scientific
// notation failed the whole regex → ColorParser.parse returned null → the IR
// emitted no srgb → the swatch rendered blank on all three runtimes → low SSIM.
//
// CSS Color 4 §7: the HSL hue is an <angle> or a <number>. A bare number is
// degrees; grad/rad/turn are <angle> units (400grad = 2π rad = 1turn = 360deg);
// scientific notation (1.2e2 == 120) is a valid <number>; the hue wraps mod 360.
//
// Pinned invariants:
//   1. Every unit spelling of hue 120 (bare, deg, grad, rad, turn, 1.2e2) yields
//      the SAME green as the degree form.
//   2. Hue > 360 wraps mod 360 (600deg == 240 == blue).
//   3. Pre-existing color forms (hex/rgb/named/hsl-with-degrees) are unchanged.

import app.irmodels.SRGB
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColorParserHslHueTest {

    // Absolute per-channel tolerance: grad/rad/turn hues are irrational once
    // converted, so 133.33333333grad lands ~1e-8 off exact 120° — well inside this.
    private val tol = 2e-3

    // Assert an SRGB triple (+ optional alpha) within tolerance.
    private fun assertSrgb(actual: SRGB?, r: Double, g: Double, b: Double, a: Double = 1.0) {
        assertNotNull(actual, "expected a resolved srgb, got null (hue was dropped)")
        assertTrue(kotlin.math.abs(r - actual.r) <= tol, "r: expected ~$r got ${actual.r}")
        assertTrue(kotlin.math.abs(g - actual.g) <= tol, "g: expected ~$g got ${actual.g}")
        assertTrue(kotlin.math.abs(b - actual.b) <= tol, "b: expected ~$b got ${actual.b}")
        assertTrue(kotlin.math.abs(a - actual.a) <= tol, "a: expected ~$a got ${actual.a}")
    }

    // hsl(120, 75%, 50%) — the reference green the WPT fixtures compare against.
    // {0.125, 0.875, 0.125} per ColorConversion.hslToSrgb(120, 75, 50).
    private val greenR = 0.125
    private val greenG = 0.875
    private val greenB = 0.125

    @Test
    fun `bare number hue is degrees`() {
        // Baseline: the plain-number form the parser always handled.
        assertSrgb(ColorParser.parse("hsl(120, 75%, 50%)")?.srgb, greenR, greenG, greenB)
    }

    @Test
    fun `deg hue matches bare number`() {
        // deg was the only unit the old regex stripped; keep it working.
        assertSrgb(ColorParser.parse("hsl(120deg, 75%, 50%)")?.srgb, greenR, greenG, greenB)
    }

    @Test
    fun `grad hue resolves to same green`() {
        // 133.33333333grad × 0.9 = 120deg (400grad = 360deg). Previously dropped.
        assertSrgb(ColorParser.parse("hsl(133.33333333grad, 75%, 50%)")?.srgb, greenR, greenG, greenB)
    }

    @Test
    fun `rad hue resolves to same green`() {
        // 2.0943951024rad × 180/π = 120deg (2π rad = 360deg). Previously dropped.
        assertSrgb(ColorParser.parse("hsl(2.0943951024rad, 75%, 50%)")?.srgb, greenR, greenG, greenB)
    }

    @Test
    fun `turn hue resolves to same green`() {
        // 0.3333333333turn × 360 = 120deg (1turn = 360deg). Previously dropped.
        assertSrgb(ColorParser.parse("hsl(0.3333333333turn, 75%, 50%)")?.srgb, greenR, greenG, greenB)
    }

    @Test
    fun `scientific notation hue is degrees`() {
        // 1.2e2 == 120 (both lowercase e and uppercase E). Previously dropped.
        assertSrgb(ColorParser.parse("hsl(1.2e2, 75%, 50%)")?.srgb, greenR, greenG, greenB)
        assertSrgb(ColorParser.parse("hsl(1.2E2, 75%, 50%)")?.srgb, greenR, greenG, greenB)
    }

    @Test
    fun `all hue-120 spellings agree exactly`() {
        // Cross-check every spelling collapses to the identical rgb triple.
        val forms = listOf(
            "hsl(120, 75%, 50%)",
            "hsl(120deg, 75%, 50%)",
            "hsl(133.33333333grad, 75%, 50%)",
            "hsl(2.0943951024rad, 75%, 50%)",
            "hsl(0.3333333333turn, 75%, 50%)",
            "hsl(1.2e2, 75%, 50%)"
        )
        forms.forEach { assertSrgb(ColorParser.parse(it)?.srgb, greenR, greenG, greenB) }
    }

    @Test
    fun `hue wraps mod 360`() {
        // 600deg → 240 = blue {0.125, 0.125, 0.875}; also via 1066.66grad and 10.47rad.
        val blueR = 0.125; val blueG = 0.125; val blueB = 0.875
        assertSrgb(ColorParser.parse("hsl(240, 75%, 50%)")?.srgb, blueR, blueG, blueB)
        assertSrgb(ColorParser.parse("hsl(600deg, 75%, 50%)")?.srgb, blueR, blueG, blueB)
        assertSrgb(ColorParser.parse("hsl(1066.66666666grad, 75%, 50%)")?.srgb, blueR, blueG, blueB)
        assertSrgb(ColorParser.parse("hsl(10.4719755118rad, 75%, 50%)")?.srgb, blueR, blueG, blueB)
        assertSrgb(ColorParser.parse("hsl(2.6666666666turn, 75%, 50%)")?.srgb, blueR, blueG, blueB)
    }

    @Test
    fun `angle-unit hue works with alpha in both syntaxes`() {
        // Legacy comma syntax carries alpha as 4th comma arg; modern space syntax
        // as `/ <alpha>`. Both must still parse with a grad/rad hue.
        assertSrgb(ColorParser.parse("hsla(133.33333333grad, 75%, 50%, 0.6)")?.srgb, greenR, greenG, greenB, 0.6)
        assertSrgb(ColorParser.parse("hsl(2.0943951024rad 75% 50% / 0.8)")?.srgb, greenR, greenG, greenB, 0.8)
    }

    @Test
    fun `space-separated angle-unit hue resolves`() {
        // Modern space-separated form must also accept the angle units.
        assertSrgb(ColorParser.parse("hsl(133.33333333grad 75% 50%)")?.srgb, greenR, greenG, greenB)
        assertSrgb(ColorParser.parse("hsl(0.3333333333turn 75% 50%)")?.srgb, greenR, greenG, greenB)
    }

    @Test
    fun `none hue still parses as zero`() {
        // Regression guard: the 'none' keyword must survive the widened group.
        // hue 0, s 0, l 0 → black.
        assertSrgb(ColorParser.parse("hsl(none none none)")?.srgb, 0.0, 0.0, 0.0)
    }

    @Test
    fun `existing non-hsl colors are unchanged`() {
        // Guard against collateral damage to the other color families.
        assertSrgb(ColorParser.parse("#ff0000")?.srgb, 1.0, 0.0, 0.0)
        assertSrgb(ColorParser.parse("rgb(0, 128, 255)")?.srgb, 0.0, 128.0 / 255.0, 1.0)
        assertSrgb(ColorParser.parse("red")?.srgb, 1.0, 0.0, 0.0)
        // A degree-hue hsl continues to resolve identically to before the fix.
        assertSrgb(ColorParser.parse("hsl(240deg, 100%, 50%)")?.srgb, 0.0, 0.0, 1.0)
    }

    @Test
    fun `malformed hsl still returns null`() {
        // A bogus unit ('foo') must not be silently accepted as a hue.
        assertNull(ColorParser.parse("hsl(120foo, 75%, 50%)"))
    }
}

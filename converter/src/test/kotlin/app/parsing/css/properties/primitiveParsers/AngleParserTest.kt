package app.parsing.css.properties.primitiveParsers

import app.irmodels.IRAngle.AngleUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * wave-52 lane L5 F4 (BACKLOG 0(e)) — AngleParser reads the css-values-4 §5.3
 * exponent and ASCII case-insensitive units.
 *
 * cascade-and-splitter brief §7 pin 9. Before F4 `angleRegex` was
 * `^([+-]?\d*\.?\d+)(deg|rad|grad|turn)$`: no exponent branch and a
 * case-SENSITIVE unit, so `4.5e1deg` and `45DEG` both returned null. Each
 * consumer then fell back on its own — a Raw gradient, a Generic transform,
 * and in `ColorParser.parseHslHue` a silent hue 0 (pinned separately in
 * ColorParserHslHueTest).
 *
 * MUTATIONS, executed against this file (recorded in
 * tools/titan/results/wave52-extractor-cascade/_note.md with the sha-verified
 * restore of AngleParser.kt):
 *   F4-a drop `(?:[eE][+-]?\d+)?` from the regex → the exponent rows go null;
 *   F4-b drop `RegexOption.IGNORE_CASE`          → the upper-case rows go null.
 */
class AngleParserTest {

    // Degrees within floating-point noise of the expected value.
    private fun assertDegrees(input: String, expected: Double) {
        val angle = assertNotNull(AngleParser.parse(input), "AngleParser.parse(\"$input\") must not be null")
        assertEquals(expected, angle.degrees, 1e-9, "degrees of \"$input\"")
    }

    @Test
    fun `the exponent folds into the number before the unit attaches`() {
        // css-syntax-3 §4.3.13 ("Consume a number") reads the e/E exponent as
        // part of the <number-token>; the unit is then the ordinary `deg`.
        assertDegrees("4.5e1deg", 45.0)
        assertDegrees("1e2deg", 100.0)
        assertDegrees("4.5e+1deg", 45.0)
        assertDegrees("45e0deg", 45.0)
        // 0.45 turn × 360 = 162 deg.
        assertDegrees("4.5e-1turn", 162.0)
        // A signed mantissa and a bare-fraction mantissa take the exponent too.
        assertDegrees("-1.2e2deg", -120.0)
        assertDegrees(".5e1grad", 4.5)
    }

    @Test
    fun `units are ASCII case-insensitive`() {
        // CSS units, like keywords, match case-insensitively; the factory
        // `when` already lowercased the unit, the regex never let it through.
        assertDegrees("45DEG", 45.0)
        assertDegrees("45E0DEG", 45.0)
        assertDegrees("0.5Turn", 180.0)
        assertDegrees("200GRAD", 180.0)
        // The ORIGINAL unit is still recorded normalised (lower-case enum).
        assertEquals(AngleUnit.DEG, AngleParser.parse("45DEG")?.originalUnit)
    }

    @Test
    fun `the historical spellings are unchanged`() {
        // Byte parity for every value the old regex accepted.
        assertDegrees("45deg", 45.0)
        assertDegrees("-90deg", -90.0)
        assertDegrees(".5turn", 180.0)
        assertDegrees("0", 0.0)
        assertDegrees("0.0", 0.0)
        assertEquals(AngleUnit.RAD, AngleParser.parse("1.57rad")?.originalUnit)
    }

    @Test
    fun `what is still not an angle stays null`() {
        // Not in the css-values-4 §7.1 unit table (the angle-units-001 set).
        for (bad in listOf("90degree", "100gradian", "1.57radian", "0.25turns")) {
            assertNull(AngleParser.parse(bad), bad)
        }
        // css-values-4 §5.3: a fraction needs a digit after the `.`, and an
        // exponent needs digits; a unitless non-zero number is not an angle.
        for (bad in listOf("1.deg", "1edeg", "1e+deg", "45", "1e2", "deg", "")) {
            assertNull(AngleParser.parse(bad), bad)
        }
    }
}

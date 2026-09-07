package com.styleconverter.runtime.typography

// SKEPTIC PROBE (wave 22, lane font-shorthand-lh) — adversarial cross-layer
// probes against the LIVE converter wire. Every payload below was captured
// from an actual `:converter:run` on fixtures/wpt/css-text-decor/
// text-decoration-dotted-001.json and on a purpose-built font-shorthand matrix
// fixture; nothing here is invented — with ONE exception, labelled at its
// test: the keyword-object `original` (retro R10, finding A8#5, re-probed
// 2026-09-05) is a TOLERANCE shape, because `font: inherit` and
// `line-height: inherit` both emit no LineHeight property at all.
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.renderer.composedDefaultLineHeightPx
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkepticFontShorthandLhProbeTest {

    private fun json(s: String): JsonElement = Json.parseToJsonElement(s)
    private fun prop(type: String, s: String) = IRProperty(type = type, data = json(s))

    // ---- the live wire ------------------------------------------------

    /** Exactly what `font: 92px Arial, sans-serif` produced on 2026-08-01. */
    private val LIVE_NORMAL = """{"multiplier":1.2,"original":"normal"}"""

    @Test
    fun liveConverterWire_forFontShorthand_isDeclaredNormal() {
        assertTrue(LineHeightNormal.isDeclaredNormal(json(LIVE_NORMAL)))
    }

    /** `font: 16px/2 Georgia` — the slash form must NOT read as the keyword. */
    @Test
    fun liveConverterWire_forSlashLineHeight_isNotNormal() {
        assertFalse(LineHeightNormal.isDeclaredNormal(
            json("""{"multiplier":2.0,"original":{"type":"number","value":2.0}}""")))
    }

    /**
     * TOLERANCE shape, NOT a live wire (retro R10, finding A8#5): the name
     * used to read `liveConverterWire_forFontInherit_isNotNormal`, but a
     * probe `:converter:run` (2026-09-05) shows `font: inherit` emits NO
     * LineHeight property at all — the CSS-wide keyword is runtime-dependent
     * so the converter drops it, exactly like `line-height: inherit` on its
     * own. The keyword-object payload below has never been on the wire; it
     * stays as a collision guard (an OBJECT `original` must not read as the
     * bare-string `normal` discriminator) should any producer ever emit one.
     * The live `font: inherit` answer is the absent-property case pinned by
     * [absentLineHeight_isNotDeclaredNormal] below.
     */
    @Test
    fun toleratedKeywordObjectOriginal_isNotNormal() {
        assertFalse(LineHeightNormal.isDeclaredNormal(
            json("""{"original":{"type":"keyword","keyword":"inherit"}}""")))
    }

    /** `font: 16px/24px Arial` — nested length wire, no top-level px. */
    @Test
    fun liveConverterWire_forSlashLength_isNotNormal() {
        assertFalse(LineHeightNormal.isDeclaredNormal(
            json("""{"original":{"type":"length","px":24.0}}""")))
    }

    // ---- adversarial shapes the builder claimed cannot collide ---------

    /** A REAL authored `line-height: 1.2` shares the multiplier, not `original`. */
    @Test
    fun authoredNumber1p2_doesNotMasqueradeAsNormal() {
        assertFalse(LineHeightNormal.isDeclaredNormal(
            json("""{"multiplier":1.2,"original":{"type":"number","value":1.2}}""")))
    }

    /** A NUMERIC primitive at `original` must not satisfy the string test. */
    @Test
    fun numericOriginalPrimitive_isNotNormal() {
        assertFalse(LineHeightNormal.isDeclaredNormal(json("""{"original":1.2}""")))
    }

    /** A bare string payload (not the envelope) is not the keyword either. */
    @Test
    fun barePrimitivePayload_isNotNormal() {
        assertFalse(LineHeightNormal.isDeclaredNormal(JsonPrimitive("normal")))
        assertFalse(LineHeightNormal.isDeclaredNormal(json("""[1,2]""")))
        assertFalse(LineHeightNormal.isDeclaredNormal(null))
        assertFalse(LineHeightNormal.isDeclaredNormal(JsonObject(emptyMap())))
    }

    /** Case matters on the wire: the converter lowercases, so only "normal". */
    @Test
    fun uppercaseNormalOnWire_isNotMatched() {
        assertFalse(LineHeightNormal.isDeclaredNormal(json("""{"original":"NORMAL"}""")))
    }

    // ---- cascade / cross-property contamination ------------------------

    @Test
    fun cascade_lastNormalWins_bothDirections() {
        val normalThenNumber = listOf(
            prop("LineHeight", LIVE_NORMAL),
            prop("LineHeight", """{"multiplier":2.0,"original":{"type":"number","value":2.0}}""")
        )
        val numberThenNormal = normalThenNumber.reversed()
        assertFalse(LineHeightNormal.isDeclaredNormal(normalThenNumber))
        assertTrue(LineHeightNormal.isDeclaredNormal(numberThenNormal))
    }

    /**
     * A DIFFERENT property whose payload also carries `original:"normal"`
     * (FontStyle/FontVariant ride GenericProperty strings on this wire) must
     * never leak into the LineHeight decision — the filter is by type.
     */
    @Test
    fun otherPropertyWithOriginalNormal_doesNotLeak() {
        val props = listOf(
            prop("FontStyle", """{"original":"normal"}"""),
            prop("FontVariant", """{"original":"normal"}""")
        )
        assertFalse(LineHeightNormal.isDeclaredNormal(props))
    }

    /** No LineHeight at all — the entire corpus default. */
    @Test
    fun absentLineHeight_isNotDeclaredNormal() {
        assertFalse(LineHeightNormal.isDeclaredNormal(emptyList()))
    }

    // ---- the three-state table -----------------------------------------

    @Test
    fun absentLineHeight_staysCalibrated_inBothModes() {
        assertEquals(LineHeightNormal.LineBoxSource.CALIBRATED,
            LineHeightNormal.lineBoxSource(false, declaredNormal = false, wptCapture = true))
        assertEquals(LineHeightNormal.LineBoxSource.CALIBRATED,
            LineHeightNormal.lineBoxSource(false, declaredNormal = false, wptCapture = false))
    }

    @Test
    fun declaredNumber_staysDeclared_inBothModes() {
        assertEquals(LineHeightNormal.LineBoxSource.DECLARED,
            LineHeightNormal.lineBoxSource(true, declaredNormal = false, wptCapture = true))
        assertEquals(LineHeightNormal.LineBoxSource.DECLARED,
            LineHeightNormal.lineBoxSource(true, declaredNormal = false, wptCapture = false))
    }

    @Test
    fun declaredNormal_isNaturalOnlyUnderWptCapture() {
        assertEquals(LineHeightNormal.LineBoxSource.NATURAL,
            LineHeightNormal.lineBoxSource(true, declaredNormal = true, wptCapture = true))
        assertEquals(LineHeightNormal.LineBoxSource.DECLARED,
            LineHeightNormal.lineBoxSource(true, declaredNormal = true, wptCapture = false))
    }

    /**
     * The odd combination the pin table does not name: the keyword was declared
     * but extraction produced NO number (no font-size to fold the multiplier
     * against). Outside WPT capture this must fall to CALIBRATED, not DECLARED
     * — DECLARED would force-unwrap a nil on the SwiftUI twin.
     */
    @Test
    fun declaredNormalWithNoExtractedNumber_fallsToCalibratedOutsideWpt() {
        assertEquals(LineHeightNormal.LineBoxSource.CALIBRATED,
            LineHeightNormal.lineBoxSource(false, declaredNormal = true, wptCapture = false))
        assertEquals(LineHeightNormal.LineBoxSource.NATURAL,
            LineHeightNormal.lineBoxSource(false, declaredNormal = true, wptCapture = true))
    }

    // ---- the corpus default must be byte-identical ----------------------

    /**
     * The ENTIRE 180-test corpus rides `composedDefaultLineHeightPx`. Pin the
     * two calibration constants so this lane cannot have moved them: 1.25× in
     * composed WPT (REF_LINE_HEIGHT) and the native 1.2× everywhere else.
     */
    @Test
    fun calibrationConstants_unmoved() {
        assertEquals(20f, composedDefaultLineHeightPx(true, 16f), 0.0001f)
        assertEquals(19.2f, composedDefaultLineHeightPx(false, 16f), 0.0001f)
        assertEquals(115f, composedDefaultLineHeightPx(true, 92f), 0.0001f)
        assertEquals(110.4f, composedDefaultLineHeightPx(false, 92f), 0.0001f)
    }

    // ---- end-to-end through the real extractor --------------------------

    /**
     * The live dotted-001 property list, folded by the SAME extractor the
     * renderer calls. It must still yield the 1.2 stand-in (110.4sp) — i.e.
     * `hasDeclaredValue` is TRUE for the keyword, which is precisely why the
     * NATURAL row has to be tested BEFORE the DECLARED row.
     */
    @Test
    fun liveDotted001Properties_stillExtractTheLegacyStandIn() {
        val props = listOf(
            prop("FontSize", """{"px":92.0,"original":{"type":"length","px":92.0}}"""),
            prop("FontFamily", """["Arial","sans-serif"]"""),
            prop("LineHeight", LIVE_NORMAL)
        )
        val style = TextStyleApplier.extractTextStyle(props)
        assertEquals(110.4f, style.lineHeight.value, 0.01f)
        assertTrue(LineHeightNormal.isDeclaredNormal(props))
        // …and the decision the renderer makes from those two facts:
        assertEquals(LineHeightNormal.LineBoxSource.NATURAL,
            LineHeightNormal.lineBoxSource(true, true, wptCapture = true))
        assertEquals(LineHeightNormal.LineBoxSource.DECLARED,
            LineHeightNormal.lineBoxSource(true, true, wptCapture = false))
    }

    /**
     * The sibling `<p>` in the SAME live document declares no font at all —
     * its property list must stay on the calibration in both modes.
     */
    @Test
    fun liveDotted001BareParagraph_staysCalibrated() {
        val props = emptyList<IRProperty>()
        val style = TextStyleApplier.extractTextStyle(props)
        assertEquals(androidx.compose.ui.unit.TextUnit.Unspecified, style.lineHeight)
        assertEquals(LineHeightNormal.LineBoxSource.CALIBRATED,
            LineHeightNormal.lineBoxSource(false, LineHeightNormal.isDeclaredNormal(props), true))
    }
}

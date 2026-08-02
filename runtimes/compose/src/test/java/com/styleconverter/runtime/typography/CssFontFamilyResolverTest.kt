package com.styleconverter.runtime.typography

import androidx.compose.ui.text.font.FontFamily
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Wave 24, lane LF — B-RC7 pin table for [CssFontFamilyResolver].
 *
 * ## The bug this file protects against coming back
 * `TextStyleApplier.extractFontFamily` read ONLY `data[0]` of the list
 * form and had no name for the bundled family, so the harness stack
 *   `["Inter","-apple-system","system-ui","Segoe UI","Roboto","Oxygen",
 *     "Ubuntu","sans-serif"]`
 * matched nothing and fell out at `FontFamily.Default` (Roboto) while the
 * browser ref, the web runtime and iOS all painted the bundled **Inter**
 * face. Different faces ⇒ different advances ⇒ different wrap points on
 * every text-driven capture. `TypographyExtractor.extractFontFamily`
 * meanwhile ran the payload through `ValueExtractors.extractKeyword`,
 * which returns null for a JsonArray — so it extracted null for EVERY
 * real declaration.
 *
 * ## Where the payloads come from — all LIVE, none invented
 *  * the bidi stack: `tools/titan/runs/wave23-final/sections/css-text/
 *    per-test-ir/wpt__css-text__bidi__bidi-lines-001.json`
 *  * `["monospace"]`: same run, `…__bidi__bidi-tab-001.json`
 *  * `["test"]`: same run, `…__boundary-shaping__boundary-shaping-001.json`
 *  * every dark-stage row: `./gradlew :converter:run --args="convert
 *    --from css --to ir -i fixtures/properties/typography/font-family.json"`
 *    — which is how we know the wire is ALWAYS an array, never a bare
 *    string, and therefore that the list branch carried every declaration.
 */
class CssFontFamilyResolverTest {

    private fun json(s: String): JsonElement = Json.parseToJsonElement(s)

    // ── B-RC7: the bundled family, by name, in list position ────────────

    /** The whole point of the lane. */
    @Test
    fun harnessStack_resolvesToBundledInter() {
        val wire = json(
            """["Inter","-apple-system","system-ui","Segoe UI","Roboto","Oxygen","Ubuntu","sans-serif"]"""
        )
        assertSame(InterFontFamily, CssFontFamilyResolver.resolve(wire))
    }

    /** Name matching is case-insensitive and quote-tolerant. */
    @Test
    fun interIsRecognisedRegardlessOfCaseOrQuotes() {
        assertSame(InterFontFamily, CssFontFamilyResolver.resolve(json("""["inter"]""")))
        assertSame(InterFontFamily, CssFontFamilyResolver.resolve(json("""["INTER"]""")))
        assertSame(InterFontFamily, CssFontFamilyResolver.resolve(json("""["\"Inter\""]""")))
    }

    // ── css-fonts-4 §5.2: the WALK, first available family wins ─────────

    @Test
    fun walksPastUnavailableNamesToTheGenericTail() {
        // The dark-stage FontFamily_Fallback_List row. Pre-wave-24 this
        // stopped at data[0] ("Helvetica Neue") and answered Default.
        val wire = json("""["Helvetica Neue","Helvetica","Arial","sans-serif"]""")
        assertEquals(FontFamily.SansSerif, CssFontFamilyResolver.resolve(wire))
    }

    @Test
    fun anEarlierResolvableEntryWinsOverALaterOne() {
        // First available, not last, not "most specific".
        assertEquals(FontFamily.Monospace,
            CssFontFamilyResolver.resolve(json("""["monospace","serif"]""")))
        assertEquals(FontFamily.Serif,
            CssFontFamilyResolver.resolve(json("""["Fake","serif","monospace"]""")))
    }

    @Test
    fun exhaustedListFallsBackToThePlatformDefault() {
        // css-fonts-4 §5.2: no listed family available ⇒ the UA default
        // font. Deliberately NOT InterFontFamily — substituting the
        // bundled face for `font-family: Arial` would repaint the verified
        // dark-stage rows FontFamily_QuotedSingle / _Unquoted_Named, whose
        // committed baselines captured the platform default.
        assertEquals(FontFamily.Default, CssFontFamilyResolver.resolve(json("""["Arial"]""")))
        assertEquals(FontFamily.Default,
            CssFontFamilyResolver.resolve(json("""["Helvetica Neue"]""")))
        assertEquals(FontFamily.Default, CssFontFamilyResolver.resolve(json("""["test"]""")))
    }

    // ── The dark-stage 327 protection table ─────────────────────────────

    /**
     * Every variant of `fixtures/properties/typography/font-family.json`
     * (a `verified` / `passing` property, 13 variants ≥ 0.95) at its LIVE
     * converted payload, asserting the SAME family the pre-wave-24 code
     * produced. If one of these flips, a committed baseline moved.
     */
    @Test
    fun darkStageFontFamilyFixture_isUnchanged() {
        val expected = listOf(
            """["serif"]""" to FontFamily.Serif,
            """["sans-serif"]""" to FontFamily.SansSerif,
            """["monospace"]""" to FontFamily.Monospace,
            """["cursive"]""" to FontFamily.Cursive,
            """["fantasy"]""" to FontFamily.Default,
            """["system-ui"]""" to FontFamily.Default,
            """["ui-serif"]""" to FontFamily.Serif,
            """["ui-sans-serif"]""" to FontFamily.SansSerif,
            """["ui-monospace"]""" to FontFamily.Monospace,
            """["ui-rounded"]""" to FontFamily.Default,
            """["emoji"]""" to FontFamily.Default,
            """["math"]""" to FontFamily.Default,
            """["fangsong"]""" to FontFamily.Default,
            """["Helvetica Neue"]""" to FontFamily.Default,
            """["Helvetica Neue","Helvetica","Arial","sans-serif"]""" to FontFamily.SansSerif,
            """["Arial"]""" to FontFamily.Default,
        )
        for ((wire, family) in expected) {
            assertEquals(wire, family, CssFontFamilyResolver.resolve(json(wire)))
        }
    }

    /** The legacy substring heuristics survive the unification verbatim. */
    @Test
    fun substringHeuristicsAreUnchanged() {
        assertEquals(FontFamily.Monospace,
            CssFontFamilyResolver.resolve(json("""["Courier Mono"]""")))
        assertEquals(FontFamily.Serif,
            CssFontFamilyResolver.resolve(json("""["Times New Roman Serif"]""")))
        // "sans" guard: a name containing BOTH must not land on Serif.
        assertEquals(FontFamily.SansSerif,
            CssFontFamilyResolver.resolve(json("""["Open Sans Serif Pro"]""")))
    }

    // ── Shape tolerance ─────────────────────────────────────────────────

    @Test
    fun objectAndPrimitiveShapesGoThroughTheSameMapping() {
        // Documented { "families": [...] } shape.
        assertSame(InterFontFamily,
            CssFontFamilyResolver.resolve(json("""{"families":["Inter","sans-serif"]}""")))
        // Legacy bare string (dead for converter output, live for
        // hand-authored IR) — same per-name mapping, same §5.2 terminal.
        assertEquals(FontFamily.Monospace, CssFontFamilyResolver.resolve(json(""""monospace"""")))
        assertEquals(FontFamily.Default, CssFontFamilyResolver.resolve(json(""""Arial"""")))
    }

    @Test
    fun absentOrEmptyPayloadStaysNull() {
        // null ⇒ "no declaration": the caller's own default applies
        // (ComponentRenderer's placeholder bottoms out at InterFontFamily
        // there — the harness-parity rule for the UNDECLARED case).
        assertNull(CssFontFamilyResolver.resolve(null))
        assertNull(CssFontFamilyResolver.resolve(json("[]")))
        assertNull(CssFontFamilyResolver.resolve(json("42")))
    }

    /** The two call sites now answer identically — that IS the unification. */
    @Test
    fun bothLegacyCallSitesAgree() {
        val wire = json("""["Inter","-apple-system","sans-serif"]""")
        val viaTextStyle = TextStyleApplier.extractTextStyle(
            listOf(com.styleconverter.runtime.core.ir.IRProperty("FontFamily", wire))
        ).fontFamily
        val viaTypography = TypographyExtractor.extractTypographyConfig(
            listOf("FontFamily" to wire)
        ).fontFamily
        assertSame(InterFontFamily, viaTextStyle)
        assertSame(InterFontFamily, viaTypography)
    }
}

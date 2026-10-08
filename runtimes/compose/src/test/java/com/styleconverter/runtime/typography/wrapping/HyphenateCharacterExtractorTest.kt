package com.styleconverter.runtime.typography.wrapping

import com.styleconverter.runtime.core.ir.IRDocumentDecoder
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.renderer.SlotComposer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Wave 54 (lane L3, U2-android) — the Compose hyphenate-character triplet.
//
// The extractor must read the reader's object shape (`{type: auto}` /
// `{type: string, value}`, irmodels HyphenateCharacterProperty.kt) and hand the
// string over VERBATIM: `""` is a value (css-text-4 §6.3, WPT
// hyphenate-character-001 "no visible hyphens"), never lower-cased (it is
// painted), and a quoted "auto" is a string, not the keyword.
//
// Host components are VERBATIM off tools/titan/runs/wave53-final/sections/
// css-text/per-test-ir (meta dropped, minified), decoded by the PRODUCTION
// decoder so a pin that holds on a hand-built literal but not on the wire
// cannot pass.
//
// MUTATION PROOF (executed; tools/titan/results/wave54-hyphenate-character/
// mutations.log): U2A-c lower-case the string value → the case pin fails;
// U2A-d the keyword path (read `value` before `type`, as ValueExtractors
// .extractKeyword does) → the auto and verbatim pins fail. Restored byte-exact.
class HyphenateCharacterExtractorTest {

    /** hyphenate-character-004's host, verbatim: `"/-/"` on the wire. */
    private val HOST_004 = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text__hyphens__hyphenate-character-004__1-201","name":"wpt__css-text__hyphens__hyphenate-character-004__1","properties":[{"type":"FontSize","data":{"px":16,"original":{"type":"length","px":16}}},{"type":"FontFamily","data":["monospace"]},{"type":"LineHeight","data":{"multiplier":1.2,"original":"normal"}},{"type":"Width","data":{"type":"length","original":{"v":6.5,"u":"CH"}}},{"type":"Hyphens","data":"MANUAL"},{"type":"HyphenateCharacter","data":{"type":"string","value":"/-/"}}],"text":"im­ple­men­ta­tion ini­tial­iza­tion re­al­iza­tion hy­phen­ation"}]}"""

    /** hyphenate-character-001's host as wave53-final carries it: the `""`
     *  the reader rejected before wave 54, a Generic passthrough. */
    private val HOST_001_PRE_U1 = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text__hyphens__hyphenate-character-001__1-177","name":"wpt__css-text__hyphens__hyphenate-character-001__1","properties":[{"type":"FontSize","data":{"px":16,"original":{"type":"length","px":16}}},{"type":"FontFamily","data":["monospace"]},{"type":"LineHeight","data":{"multiplier":1.2,"original":"normal"}},{"type":"Width","data":{"type":"length","original":{"v":4.5,"u":"CH"}}},{"type":"Hyphens","data":"MANUAL"},{"type":"Generic","data":{"propertyName":"hyphenate-character","rawValue":"\"\"","_unmapped":true}}],"text":"im­ple­men­ta­tion ini­tial­iza­tion re­al­iza­tion hy­phen­ation"}]}"""

    /** The single host's properties through the production decoder. */
    private fun hostProps(doc: String): List<IRProperty> =
        SlotComposer.compose(IRDocumentDecoder.decode(doc))[0].properties

    /** One HyphenateCharacter property with the given `data` JSON. */
    private fun prop(data: String) = listOf(IRProperty("HyphenateCharacter", Json.parseToJsonElement(data)))

    /** The string the seam hands to PreBreakPipeline for these properties. */
    private fun painted(props: List<IRProperty>) =
        HyphenateCharacterApplier.preBreakString(HyphenateCharacterExtractor.extract(props))

    @Test
    fun theVerbatim004WireReachesThePreBreakAsSlashHyphenSlash() {
        // -004's `"/-/"`, verbatim, end to end through decoder + triplet.
        assertEquals("/-/", painted(hostProps(HOST_004)))
    }

    @Test
    fun theEmptyStringIsAValueNotAnAbsence() {
        // The reader's post-U1 shape for -001/-002 (pinned on the converter side).
        val cfg = HyphenateCharacterExtractor.extract(prop("""{"type":"string","value":""}"""))
        // A declared "" — not null, not auto.
        assertEquals(HyphenateCharacterConfig(""), cfg)
        // And the pre-break paints nothing.
        assertEquals("", HyphenateCharacterApplier.preBreakString(cfg))
    }

    @Test
    fun autoAndNoDeclarationBothPaintTheUaHyphen() {
        // `auto` → null value → U+2010, the preBreak default (identity).
        assertEquals(HyphenateCharacterConfig(null), HyphenateCharacterExtractor.extract(prop("""{"type":"auto"}""")))
        assertEquals(WordBreakOpportunities.DEFAULT_HYPHEN_CHARACTER, painted(prop("""{"type":"auto"}""")))
        // No declaration at all → null config → the same default.
        assertNull(HyphenateCharacterExtractor.extract(emptyList()))
        assertEquals(WordBreakOpportunities.DEFAULT_HYPHEN_CHARACTER, painted(emptyList()))
    }

    @Test
    fun theStringIsNeverCaseFoldedNorReadAsAKeyword() {
        // Painted, so case is significant.
        assertEquals("AbC", painted(prop("""{"type":"string","value":"AbC"}""")))
        // A QUOTED "auto" is the four letters, not the keyword.
        assertEquals("auto", painted(prop("""{"type":"string","value":"auto"}""")))
        // U+2022 (-003 after the reader decodes `\2022`) verbatim.
        assertEquals("•", painted(prop("""{"type":"string","value":"•"}""")))
    }

    @Test
    fun theUnconvertedWireStillPaintsTheUaHyphen() {
        // Before U1 lands, -001 carries a Generic: the triplet sees no
        // HyphenateCharacter and the pre-break keeps U+2010 (U2 needs U1).
        assertNull(HyphenateCharacterExtractor.extract(hostProps(HOST_001_PRE_U1)))
        assertEquals(WordBreakOpportunities.DEFAULT_HYPHEN_CHARACTER, painted(hostProps(HOST_001_PRE_U1)))
    }

    @Test
    fun aDictionaryRunKeepsTheStringAndAnUnreadablePayloadFallsBackToAuto() {
        // hyphenate-limit-chars-001's `"-"` on a hyphens:auto run: returned
        // (the fold may still paint it) while Minikin's own hyphen is logged.
        val limit = HyphenateCharacterExtractor.extract(prop("""{"type":"string","value":"-"}"""))
        assertEquals("-", HyphenateCharacterApplier.preBreakString(limit, dictionaryHyphenation = true))
        // A bare primitive is no reader shape: breadcrumb + auto, never a guess.
        assertEquals(HyphenateCharacterConfig(null), HyphenateCharacterExtractor.extract(prop("\"x\"")))
    }
}

package com.styleconverter.runtime.visibility

// Phase 8 functional tests for the overflow keyword → OverflowBehavior
// mapping. The logic lives in style.scrolling.OverflowExtractor (a
// pre-canonical module retained because it also handles OverflowAnchor/
// OverflowClipMargin which are NOT Phase 8 scope), but the five Phase 8
// overflow longhands share its extraction path so we test them together
// here under the visibility/ folder where they're claimed in the registry.

import com.styleconverter.runtime.scrolling.OverflowBehavior
import com.styleconverter.runtime.scrolling.OverflowExtractor
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class OverflowExtractorTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(t: String, j: String) = t to parse(j)

    @Test
    fun `Overflow shorthand sets both axes`() {
        // `overflow: hidden` — shorthand that writes both overflowX and
        // overflowY. The extractor must not forget either axis.
        val cfg = OverflowExtractor.extractOverflowConfig(
            listOf(pair("Overflow", "\"hidden\""))
        )
        assertEquals(OverflowBehavior.HIDDEN, cfg.overflowX)
        assertEquals(OverflowBehavior.HIDDEN, cfg.overflowY)
    }

    @Test
    fun `OverflowX scroll leaves Y at default`() {
        val cfg = OverflowExtractor.extractOverflowConfig(
            listOf(pair("OverflowX", "\"scroll\""))
        )
        assertEquals(OverflowBehavior.SCROLL, cfg.overflowX)
        // Default is VISIBLE per CSS spec — asserting here catches any
        // accidental cross-axis write in the extractor's when-branch.
        assertEquals(OverflowBehavior.VISIBLE, cfg.overflowY)
    }

    @Test
    fun `OverflowBlock maps to Y axis (horizontal writing mode)`() {
        // In horizontal writing mode, block flow is vertical — so
        // `overflow-block: clip` writes overflowY. The applier trusts the
        // extractor's axis assignment and doesn't re-swap based on writing
        // mode at apply-time.
        val cfg = OverflowExtractor.extractOverflowConfig(
            listOf(pair("OverflowBlock", "\"clip\""))
        )
        assertEquals(OverflowBehavior.CLIP, cfg.overflowY)
    }

    @Test
    fun `OverflowInline maps to X axis (horizontal writing mode)`() {
        val cfg = OverflowExtractor.extractOverflowConfig(
            listOf(pair("OverflowInline", "\"auto\""))
        )
        assertEquals(OverflowBehavior.AUTO, cfg.overflowX)
    }

    @Test
    fun `physical then logical overflow resolves last-write-wins`() {
        // Wave-18 cleanup pin (lane-3 skeptic, shared with the iOS twin in
        // Wave18CleanupParityTests.swift). Live wire, declaration order
        // preserved by the converter (`overflow-y:hidden; overflow-block:
        // visible`): [{"type":"OverflowY","data":"HIDDEN"},
        // {"type":"OverflowBlock","data":"VISIBLE"}]. The cascade's
        // order-of-appearance rule means the LATER logical declaration
        // wins on the shared physical axis (css-logical §4 maps block→Y
        // in horizontal-tb) → visible, no clip.
        val cfg = OverflowExtractor.extractOverflowConfig(
            listOf(pair("OverflowY", "\"HIDDEN\""), pair("OverflowBlock", "\"VISIBLE\""))
        )
        assertEquals(OverflowBehavior.VISIBLE, cfg.overflowY)
        // Reverse order: the later PHYSICAL longhand wins → hidden clips.
        val rev = OverflowExtractor.extractOverflowConfig(
            listOf(pair("OverflowBlock", "\"VISIBLE\""), pair("OverflowY", "\"HIDDEN\""))
        )
        assertEquals(OverflowBehavior.HIDDEN, rev.overflowY)
    }

    @Test
    fun `unknown overflow keyword falls back to VISIBLE`() {
        // Unrecognized tokens don't crash — they fall through to the CSS
        // initial value (`visible`), matching how browsers behave.
        val cfg = OverflowExtractor.extractOverflowConfig(
            listOf(pair("OverflowX", "\"bogus\""))
        )
        assertEquals(OverflowBehavior.VISIBLE, cfg.overflowX)
    }

    @Test
    fun `all five overflow keywords round-trip through enum`() {
        // Coverage sweep: every documented CSS overflow keyword maps to a
        // distinct OverflowBehavior enum value. This is the one test that
        // fails immediately if someone adds a keyword but forgets the
        // enum entry.
        val keywords = mapOf(
            "visible" to OverflowBehavior.VISIBLE,
            "hidden" to OverflowBehavior.HIDDEN,
            "scroll" to OverflowBehavior.SCROLL,
            "auto" to OverflowBehavior.AUTO,
            "clip" to OverflowBehavior.CLIP
        )
        for ((keyword, expected) in keywords) {
            val cfg = OverflowExtractor.extractOverflowConfig(
                listOf(pair("OverflowY", "\"$keyword\""))
            )
            assertEquals("keyword $keyword", expected, cfg.overflowY)
        }
    }
}

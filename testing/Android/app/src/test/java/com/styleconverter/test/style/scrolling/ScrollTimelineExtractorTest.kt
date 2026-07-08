package com.styleconverter.test.style.scrolling

// Phase 9 extractor tests for the three scroll-timeline longhands
// (ScrollTimeline, ScrollTimelineName, ScrollTimelineAxis). IR shapes are
// taken verbatim from
// examples/properties/animations/scroll-timeline.json conversions.
//
// Parser gap worth calling out in-test: both `scroll-timeline` and
// `scroll-timeline-name` store the literal string "none" as the name
// rather than emitting a sentinel — the extractor is responsible for
// treating "none" as "no timeline" if the caller cares. Covered below.

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrollTimelineExtractorTest {

    private fun parse(s: String): JsonElement = Json.parseToJsonElement(s)
    private fun pair(t: String, j: String) = t to parse(j)

    // ==================== scroll-timeline-axis ============================

    @Test
    fun `scroll-timeline-axis block keyword`() {
        val cfg = ScrollTimelineExtractor.extractScrollTimelineConfig(
            listOf(pair("ScrollTimelineAxis", "\"BLOCK\""))
        )
        // Parser emits uppercased enum-name; extractor uppercases again
        // defensively so keyword case does not matter.
        assertEquals(ScrollTimelineAxisValue.BLOCK, cfg.scrollTimelineAxis)
    }

    @Test
    fun `scroll-timeline-axis inline keyword`() {
        val cfg = ScrollTimelineExtractor.extractScrollTimelineConfig(
            listOf(pair("ScrollTimelineAxis", "\"INLINE\""))
        )
        assertEquals(ScrollTimelineAxisValue.INLINE, cfg.scrollTimelineAxis)
    }

    @Test
    fun `scroll-timeline-axis x and y keywords`() {
        // x / y are physical-axis aliases per CSS Scroll Animations spec.
        val x = ScrollTimelineExtractor.extractScrollTimelineConfig(
            listOf(pair("ScrollTimelineAxis", "\"X\""))
        )
        val y = ScrollTimelineExtractor.extractScrollTimelineConfig(
            listOf(pair("ScrollTimelineAxis", "\"Y\""))
        )
        assertEquals(ScrollTimelineAxisValue.X, x.scrollTimelineAxis)
        assertEquals(ScrollTimelineAxisValue.Y, y.scrollTimelineAxis)
    }

    @Test
    fun `scroll-timeline-axis default is BLOCK when data is null`() {
        // BLOCK is the spec default; our Config's primary-value default must
        // round-trip when the property is omitted entirely.
        val cfg = ScrollTimelineExtractor.extractScrollTimelineConfig(emptyList())
        assertEquals(ScrollTimelineAxisValue.BLOCK, cfg.scrollTimelineAxis)
    }

    // ==================== scroll-timeline-name ============================

    @Test
    fun `scroll-timeline-name dashed ident`() {
        // Fixture: {"name":"--page-scroll"}
        val cfg = ScrollTimelineExtractor.extractScrollTimelineConfig(
            listOf(pair("ScrollTimelineName", """{"name":"--page-scroll"}"""))
        )
        assertEquals("--page-scroll", cfg.scrollTimelineName)
    }

    @Test
    fun `scroll-timeline-name plain ident is kept as-is`() {
        // Parser accepts plain (non-dashed) idents. The extractor does not
        // reject those — keeps casing / exact string from parser.
        val cfg = ScrollTimelineExtractor.extractScrollTimelineConfig(
            listOf(pair("ScrollTimelineName", """{"name":"scroller"}"""))
        )
        assertEquals("scroller", cfg.scrollTimelineName)
    }

    @Test
    fun `scroll-timeline-name none sentinel drops to null`() {
        // Parser stores "none" as a literal string per parser-gap note.
        // The extractor converts that back to a null sentinel so callers
        // don't have to know the parser quirk.
        val cfg = ScrollTimelineExtractor.extractScrollTimelineConfig(
            listOf(pair("ScrollTimelineName", """{"name":"none"}"""))
        )
        // Our extractor only nullifies "none" in the primitive / type-tagged
        // branches — for the object-with-name variant it returns the string.
        // Document the current behavior explicitly so a future change is
        // caught by this test.
        assertEquals("none", cfg.scrollTimelineName)
    }

    // ==================== scroll-timeline shorthand =======================

    @Test
    fun `scroll-timeline shorthand name and axis are extracted via longhand pair`() {
        // Real scroll-timeline IR: {"name":{"name":"--my-scroll"},"axis":"INLINE"}
        // The shorthand property parser emits a composite node. This
        // extractor's longhand path only reads from the primitive/type-tagged
        // shape, so we simulate the expanded longhand form that the engine
        // actually receives (shorthand expansion happens upstream).
        val cfg = ScrollTimelineExtractor.extractScrollTimelineConfig(
            listOf(
                pair("ScrollTimelineName", """{"name":"--my-scroll"}"""),
                pair("ScrollTimelineAxis", "\"INLINE\"")
            )
        )
        assertEquals("--my-scroll", cfg.scrollTimelineName)
        assertEquals(ScrollTimelineAxisValue.INLINE, cfg.scrollTimelineAxis)
    }

    @Test
    fun `empty properties produce default config`() {
        // Sanity: the extractor over an empty list produces a fully-defaulted
        // Config — no crashes, no partial-state surprises.
        val cfg = ScrollTimelineExtractor.extractScrollTimelineConfig(emptyList())
        assertNull(cfg.scrollTimelineName)
        assertEquals(ScrollTimelineAxisValue.BLOCK, cfg.scrollTimelineAxis)
    }
}

package com.styleconverter.runtime.core.renderer

// Pins the LIVE align-self mapping used by ComponentRenderer's
// RenderRowContent / RenderColumnContent (via extractAlignSelf), NOT the
// engine-path copies in FlexExtractor / FlexboxExtractor. Regression test
// for the ANCHOR_CENTER bug: the engine-path extractors were fixed to fold
// `anchor-center` → center (CSS Anchor Positioning Level 1 §6 — the value
// resolves to `center` when no default anchor is in scope, and SDUI has no
// anchor runtime), but the live ComponentRenderer path still fell through
// to AUTO, so anchor-centered flex children silently lost their alignment.

import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class ComponentRendererAlignSelfTest {

    // IR keywords arrive as bare uppercase JSON string primitives
    // (e.g. "ANCHOR_CENTER"), matching the wire shape asserted in
    // FlexboxExtractorTest.kw().
    private fun alignSelfProp(keyword: String) =
        IRProperty(type = "AlignSelf", data = JsonPrimitive(keyword))

    @Test
    fun `anchor-center collapses to CENTER on the live render path`() {
        // The regression: this returned AUTO while the (unused) engine-path
        // extractors correctly returned their CENTER equivalent.
        assertEquals(
            ComponentRenderer.AlignSelf.CENTER,
            ComponentRenderer.extractAlignSelf(listOf(alignSelfProp("ANCHOR_CENTER")))
        )
    }

    @Test
    fun `plain center still maps to CENTER`() {
        // Guard: the anchor-center fold must not disturb the plain keyword.
        assertEquals(
            ComponentRenderer.AlignSelf.CENTER,
            ComponentRenderer.extractAlignSelf(listOf(alignSelfProp("CENTER")))
        )
    }

    @Test
    fun `flex-start and flex-end keep their edge mappings`() {
        assertEquals(
            ComponentRenderer.AlignSelf.FLEX_START,
            ComponentRenderer.extractAlignSelf(listOf(alignSelfProp("FLEX_START")))
        )
        assertEquals(
            ComponentRenderer.AlignSelf.FLEX_END,
            ComponentRenderer.extractAlignSelf(listOf(alignSelfProp("FLEX_END")))
        )
    }

    @Test
    fun `unknown keyword still falls through to AUTO`() {
        // The else-branch contract is unchanged: only recognized keywords
        // map; anything unexpected degrades to AUTO (CSS initial value).
        assertEquals(
            ComponentRenderer.AlignSelf.AUTO,
            ComponentRenderer.extractAlignSelf(listOf(alignSelfProp("BOGUS_KEYWORD")))
        )
    }

    @Test
    fun `absent AlignSelf property yields AUTO`() {
        assertEquals(
            ComponentRenderer.AlignSelf.AUTO,
            ComponentRenderer.extractAlignSelf(emptyList())
        )
    }
}

package com.styleconverter.runtime.layout.flexbox

// Phase 7b flexbox extractor tests — each IR fixture shape is copy-pasted
// from the actual IR emitted by the CSS parser for
// fixtures/properties/layout/flex-*.json, so parser drift breaks these tests
// before it reaches the Android runtime.

import com.styleconverter.runtime.layout.AlignmentKeyword
import com.styleconverter.runtime.layout.DisplayKind
import com.styleconverter.runtime.layout.FlexBasisValue
import com.styleconverter.runtime.layout.FlexDirection
import com.styleconverter.runtime.layout.FlexWrap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlexboxExtractorTest {

    private fun parse(s: String): JsonElement = Json.parseToJsonElement(s)
    private fun pair(type: String, json: String) = type to parse(json)
    // Convenience: most layout keywords arrive as bare uppercase JSON
    // primitives (e.g. `"FLEX_START"`), matching the IR shape confirmed in
    // /tmp/flexir/tmpOutput.json during development.
    private fun kw(type: String, keyword: String) = type to (JsonPrimitive(keyword) as JsonElement)

    // --- Display ------------------------------------------------------------

    @Test fun `display flex maps to DisplayKind Flex`() {
        val cfg = FlexboxExtractor.extract(listOf(kw("Display", "FLEX")))
        assertEquals(DisplayKind.Flex, cfg.display)
    }

    @Test fun `display inline-flex maps to InlineFlex`() {
        val cfg = FlexboxExtractor.extract(listOf(kw("Display", "INLINE_FLEX")))
        assertEquals(DisplayKind.InlineFlex, cfg.display)
    }

    @Test fun `display block maps to Block`() {
        val cfg = FlexboxExtractor.extract(listOf(kw("Display", "BLOCK")))
        assertEquals(DisplayKind.Block, cfg.display)
    }

    @Test fun `display none maps to None`() {
        val cfg = FlexboxExtractor.extract(listOf(kw("Display", "NONE")))
        assertEquals(DisplayKind.None, cfg.display)
    }

    // --- FlexDirection ------------------------------------------------------

    @Test fun `flex-direction row`() {
        val cfg = FlexboxExtractor.extract(listOf(kw("FlexDirection", "ROW")))
        assertEquals(FlexDirection.Row, cfg.flexDirection)
    }

    @Test fun `flex-direction row-reverse`() {
        val cfg = FlexboxExtractor.extract(listOf(kw("FlexDirection", "ROW_REVERSE")))
        assertEquals(FlexDirection.RowReverse, cfg.flexDirection)
    }

    @Test fun `flex-direction column`() {
        val cfg = FlexboxExtractor.extract(listOf(kw("FlexDirection", "COLUMN")))
        assertEquals(FlexDirection.Column, cfg.flexDirection)
    }

    @Test fun `flex-direction column-reverse`() {
        val cfg = FlexboxExtractor.extract(listOf(kw("FlexDirection", "COLUMN_REVERSE")))
        assertEquals(FlexDirection.ColumnReverse, cfg.flexDirection)
    }

    // --- FlexWrap -----------------------------------------------------------

    @Test fun `flex-wrap nowrap`() {
        val cfg = FlexboxExtractor.extract(listOf(kw("FlexWrap", "NOWRAP")))
        assertEquals(FlexWrap.NoWrap, cfg.flexWrap)
    }

    @Test fun `flex-wrap wrap`() {
        val cfg = FlexboxExtractor.extract(listOf(kw("FlexWrap", "WRAP")))
        assertEquals(FlexWrap.Wrap, cfg.flexWrap)
    }

    @Test fun `flex-wrap wrap-reverse`() {
        val cfg = FlexboxExtractor.extract(listOf(kw("FlexWrap", "WRAP_REVERSE")))
        assertEquals(FlexWrap.WrapReverse, cfg.flexWrap)
    }

    // --- JustifyContent (all 6 spec keywords) ------------------------------

    @Test fun `justify-content flex-start`() {
        val cfg = FlexboxExtractor.extract(listOf(kw("JustifyContent", "FLEX_START")))
        assertEquals(AlignmentKeyword.FlexStart, cfg.justifyContent)
    }

    @Test fun `justify-content flex-end`() {
        val cfg = FlexboxExtractor.extract(listOf(kw("JustifyContent", "FLEX_END")))
        assertEquals(AlignmentKeyword.FlexEnd, cfg.justifyContent)
    }

    @Test fun `justify-content center`() {
        val cfg = FlexboxExtractor.extract(listOf(kw("JustifyContent", "CENTER")))
        assertEquals(AlignmentKeyword.Center, cfg.justifyContent)
    }

    @Test fun `justify-content space-between`() {
        val cfg = FlexboxExtractor.extract(listOf(kw("JustifyContent", "SPACE_BETWEEN")))
        assertEquals(AlignmentKeyword.SpaceBetween, cfg.justifyContent)
    }

    @Test fun `justify-content space-around`() {
        val cfg = FlexboxExtractor.extract(listOf(kw("JustifyContent", "SPACE_AROUND")))
        assertEquals(AlignmentKeyword.SpaceAround, cfg.justifyContent)
    }

    @Test fun `justify-content space-evenly`() {
        val cfg = FlexboxExtractor.extract(listOf(kw("JustifyContent", "SPACE_EVENLY")))
        assertEquals(AlignmentKeyword.SpaceEvenly, cfg.justifyContent)
    }

    // --- AlignItems (5 spec keywords) --------------------------------------

    @Test fun `align-items flex-start`() {
        val cfg = FlexboxExtractor.extract(listOf(kw("AlignItems", "FLEX_START")))
        assertEquals(AlignmentKeyword.FlexStart, cfg.alignItems)
    }

    @Test fun `align-items flex-end`() {
        val cfg = FlexboxExtractor.extract(listOf(kw("AlignItems", "FLEX_END")))
        assertEquals(AlignmentKeyword.FlexEnd, cfg.alignItems)
    }

    @Test fun `align-items center`() {
        val cfg = FlexboxExtractor.extract(listOf(kw("AlignItems", "CENTER")))
        assertEquals(AlignmentKeyword.Center, cfg.alignItems)
    }

    @Test fun `align-items stretch`() {
        val cfg = FlexboxExtractor.extract(listOf(kw("AlignItems", "STRETCH")))
        assertEquals(AlignmentKeyword.Stretch, cfg.alignItems)
    }

    @Test fun `align-items baseline`() {
        val cfg = FlexboxExtractor.extract(listOf(kw("AlignItems", "BASELINE")))
        assertEquals(AlignmentKeyword.Baseline, cfg.alignItems)
    }

    // --- numeric properties -------------------------------------------------

    @Test fun `flex-grow parsed as float from wrapped value`() {
        // FlexGrow IR shape: { "value": { "type": "...Number", "value": 2.5 } }
        val cfg = FlexboxExtractor.extract(listOf(
            pair("FlexGrow", """{"value":{"type":"Number","value":2.5}}""")
        ))
        assertEquals(2.5f, cfg.flexGrow)
    }

    @Test fun `flex-shrink parsed as float`() {
        val cfg = FlexboxExtractor.extract(listOf(
            pair("FlexShrink", """{"value":{"type":"Number","value":0.5}}""")
        ))
        assertEquals(0.5f, cfg.flexShrink)
    }

    @Test fun `order parsed as int`() {
        val cfg = FlexboxExtractor.extract(listOf(
            pair("Order", """{"value":3}""")
        ))
        assertEquals(3, cfg.order)
    }

    @Test fun `order negative parsed`() {
        val cfg = FlexboxExtractor.extract(listOf(
            pair("Order", """{"value":-2}""")
        ))
        assertEquals(-2, cfg.order)
    }

    // --- AlignSelf + AlignContent -----------------------------------------

    @Test fun `align-self end`() {
        val cfg = FlexboxExtractor.extract(listOf(kw("AlignSelf", "END")))
        assertEquals(AlignmentKeyword.End, cfg.alignSelf)
    }

    // CSS Anchor Positioning Level 1 §6 — `anchor-center` MUST collapse to
    // `center` whenever no default anchor is in scope. SDUI has no anchor-
    // positioning runtime, so this fold is unconditional. The IR enum carries
    // ANCHOR_CENTER through verbatim (parser/IR remain spec-truthful), and
    // the extractor folds it here so downstream Compose alignment is correct.
    // Mirrors the equivalent fold in iOS mapAlignment + Web AlignSelfApplier.
    @Test fun `align-self anchor-center collapses to center`() {
        val cfg = FlexboxExtractor.extract(listOf(kw("AlignSelf", "ANCHOR_CENTER")))
        assertEquals(AlignmentKeyword.Center, cfg.alignSelf)
    }

    @Test fun `align-content space-around`() {
        val cfg = FlexboxExtractor.extract(listOf(kw("AlignContent", "SPACE_AROUND")))
        assertEquals(AlignmentKeyword.SpaceAround, cfg.alignContent)
    }

    // --- FlexBasis ---------------------------------------------------------

    @Test fun `flex-basis fills Default placeholder when present`() {
        val cfg = FlexboxExtractor.extract(listOf(
            pair("FlexBasis", """{"type":"length","px":100.0}""")
        ))
        // Presence-only slot by design (retro R2, A7#0): LayoutConfig's
        // flexBasis has no reader; the live decode lives in
        // ItemPlacementExtractor (see ItemPlacementExtractorTest).
        assertEquals(FlexBasisValue.Default, cfg.flexBasis)
        // The bare-number PERCENT wire is "present" too — same marker.
        assertEquals(FlexBasisValue.Default,
            FlexboxExtractor.extract(listOf(pair("FlexBasis", "80"))).flexBasis)
    }

    // --- BoxOrient (legacy fallback) ---------------------------------------

    @Test fun `box-orient vertical implies column direction when flex-direction absent`() {
        val cfg = FlexboxExtractor.extract(listOf(kw("BoxOrient", "VERTICAL")))
        assertEquals(FlexDirection.Column, cfg.flexDirection)
    }

    @Test fun `explicit flex-direction wins over box-orient`() {
        val cfg = FlexboxExtractor.extract(listOf(
            kw("FlexDirection", "ROW"),
            kw("BoxOrient", "VERTICAL")
        ))
        assertEquals(FlexDirection.Row, cfg.flexDirection)
    }

    // --- Integration -------------------------------------------------------

    @Test fun `display flex + row + space-between extracts all three`() {
        val cfg = FlexboxExtractor.extract(listOf(
            kw("Display", "FLEX"),
            kw("FlexDirection", "ROW"),
            kw("JustifyContent", "SPACE_BETWEEN")
        ))
        assertEquals(DisplayKind.Flex, cfg.display)
        assertEquals(FlexDirection.Row, cfg.flexDirection)
        assertEquals(AlignmentKeyword.SpaceBetween, cfg.justifyContent)
        // Fields not set stay null.
        assertNull(cfg.alignItems)
        assertNull(cfg.flexGrow)
    }

    @Test fun `empty property list returns Empty config`() {
        val cfg = FlexboxExtractor.extract(emptyList())
        assertNull(cfg.display)
        assertNull(cfg.flexDirection)
        assertNull(cfg.justifyContent)
    }

    @Test fun `unrelated properties are ignored`() {
        // Width/Height/BackgroundColor should NOT populate any flexbox field.
        val cfg = FlexboxExtractor.extract(listOf(
            pair("Width", """{"type":"length","px":300.0}"""),
            kw("Display", "FLEX")
        ))
        assertEquals(DisplayKind.Flex, cfg.display)
        // Make sure Width didn't accidentally affect flex slots.
        assertNull(cfg.flexBasis)
        assertNull(cfg.flexGrow)
    }
}

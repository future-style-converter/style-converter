package com.styleconverter.test.style.layout.position

// Phase 7b position extractor unit tests. Fixture shapes mirror
// examples/properties/layout/position-*.json + inset-logical.json +
// z-index.json. Covers the logical→physical reconciliation and zIndex
// auto/integer/negative parsing.

import androidx.compose.ui.unit.LayoutDirection
import com.styleconverter.test.style.layout.PositionKind
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PositionLayoutExtractorTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(type: String, json: String) = type to parse(json)

    // --- Position keyword --------------------------------------------------

    @Test fun `position static parses`() {
        val e = PositionLayoutExtractor.extract(listOf(pair("Position", "\"static\"")))
        assertEquals(PositionKind.Static, e.position)
    }

    @Test fun `position relative parses`() {
        val e = PositionLayoutExtractor.extract(listOf(pair("Position", "\"relative\"")))
        assertEquals(PositionKind.Relative, e.position)
    }

    @Test fun `position absolute parses`() {
        val e = PositionLayoutExtractor.extract(listOf(pair("Position", "\"absolute\"")))
        assertEquals(PositionKind.Absolute, e.position)
    }

    @Test fun `position fixed parses`() {
        val e = PositionLayoutExtractor.extract(listOf(pair("Position", "\"fixed\"")))
        assertEquals(PositionKind.Fixed, e.position)
    }

    @Test fun `position sticky parses`() {
        val e = PositionLayoutExtractor.extract(listOf(pair("Position", "\"sticky\"")))
        assertEquals(PositionKind.Sticky, e.position)
    }

    // --- Top/right/bottom/left physical offsets ----------------------------

    @Test fun `top and left px populate inset physically`() {
        val e = PositionLayoutExtractor.extract(listOf(
            pair("Position", "\"absolute\""),
            pair("Top", """{"type":"length","px":10.0}"""),
            pair("Left", """{"type":"length","px":20.0}"""),
        ))
        val i = e.inset!!
        assertEquals(10f, i.top)
        assertEquals(20f, i.left)
        assertNull(i.bottom)
        assertNull(i.right)
    }

    @Test fun `right and bottom px populate correctly`() {
        val e = PositionLayoutExtractor.extract(listOf(
            pair("Position", "\"absolute\""),
            pair("Right", """{"type":"length","px":15.0}"""),
            pair("Bottom", """{"type":"length","px":25.0}"""),
        ))
        val i = e.inset!!
        assertEquals(15f, i.right)
        assertEquals(25f, i.bottom)
        assertNull(i.top)
        assertNull(i.left)
    }

    // --- Logical → physical inset resolution -------------------------------

    @Test fun `inset-block and inline resolve to physical under LTR`() {
        val e = PositionLayoutExtractor.extract(
            listOf(
                pair("Position", "\"relative\""),
                pair("InsetBlockStart", """{"type":"length","px":10.0}"""),
                pair("InsetBlockEnd", """{"type":"length","px":11.0}"""),
                pair("InsetInlineStart", """{"type":"length","px":12.0}"""),
                pair("InsetInlineEnd", """{"type":"length","px":13.0}"""),
            ),
            layoutDirection = LayoutDirection.Ltr,
        )
        val i = e.inset!!
        // LTR: inline-start = left, inline-end = right.
        assertEquals(10f, i.top)
        assertEquals(11f, i.bottom)
        assertEquals(12f, i.left)
        assertEquals(13f, i.right)
    }

    @Test fun `inset-inline flips under RTL`() {
        val e = PositionLayoutExtractor.extract(
            listOf(
                pair("Position", "\"relative\""),
                pair("InsetInlineStart", """{"type":"length","px":12.0}"""),
                pair("InsetInlineEnd", """{"type":"length","px":13.0}"""),
            ),
            layoutDirection = LayoutDirection.Rtl,
        )
        val i = e.inset!!
        // RTL: inline-start = right, inline-end = left.
        assertEquals(13f, i.left)
        assertEquals(12f, i.right)
    }

    @Test fun `physical top overrides logical inset-block-start`() {
        // Per our reconciliation rule: physical wins when both specified.
        val e = PositionLayoutExtractor.extract(listOf(
            pair("Position", "\"relative\""),
            pair("InsetBlockStart", """{"type":"length","px":10.0}"""),
            pair("Top", """{"type":"length","px":99.0}"""),
        ))
        assertEquals(99f, e.inset!!.top)
    }

    // --- z-index -----------------------------------------------------------

    @Test fun `z-index integer parses`() {
        val e = PositionLayoutExtractor.extract(listOf(pair("ZIndex", "5")))
        assertEquals(5, e.zIndex)
    }

    @Test fun `z-index negative parses`() {
        val e = PositionLayoutExtractor.extract(listOf(pair("ZIndex", "-3")))
        assertEquals(-3, e.zIndex)
    }

    @Test fun `z-index auto yields null`() {
        val e = PositionLayoutExtractor.extract(listOf(pair("ZIndex", "\"auto\"")))
        assertNull(e.zIndex)
    }

    // --- Empty input -------------------------------------------------------

    @Test fun `no position properties returns all-null extract`() {
        val e = PositionLayoutExtractor.extract(listOf(pair("Width", """{"type":"length","px":100.0}""")))
        assertNull(e.position)
        assertNull(e.inset)
        assertNull(e.zIndex)
    }

    @Test fun `position keyword alone with no offsets yields null inset`() {
        val e = PositionLayoutExtractor.extract(listOf(pair("Position", "\"relative\"")))
        assertEquals(PositionKind.Relative, e.position)
        assertNull(e.inset)
    }
}

package app.parsing.css.properties.longhands.effects

// Regression suite for the legacy `clip` property's rect() syntax
// (CSS 2.1 §11.1.2, restated by css-masking-1 Appendix A).
//
// Wave-37 lane W3 finding: the parser split the rect() arguments on
// commas ONLY. CSS 2.1 printed the commas but the shape has always
// admitted the comma-less spelling and every engine accepts it, which is
// what WPT `css-masking/clip/clip-rect-comma-001` pins
// (`clip: rect(50px 150px 150px 50px)`). With a comma-only split that
// value produced ONE part, tripped the `size != 4` guard, and the whole
// declaration fell out of the IR as an unmapped Generic — nothing
// clipped at all.
//
// The `auto` encoding is pinned here too because the web extractor's
// reading of it depends on it: an `auto` side is stored as a NULL field,
// which is what lets a runtime resolve it against real draw bounds.

import app.irmodels.properties.effects.ClipProperty
import app.irmodels.properties.effects.ClipValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ClipRectSyntaxTest {

    private fun rectOf(css: String): ClipValue.Rect? =
        (ClipPropertyParser.parse(css) as? ClipProperty)?.value as? ClipValue.Rect

    @Test
    fun `comma-separated rect parses (the spelling that always worked)`() {
        val r = assertNotNull(rectOf("rect(50px, 150px, 150px, 50px)"))
        assertEquals(50.0, r.top?.originalValue)
        assertEquals(150.0, r.right?.originalValue)
        assertEquals(150.0, r.bottom?.originalValue)
        assertEquals(50.0, r.left?.originalValue)
    }

    @Test
    fun `comma-less rect parses identically`() {
        // clip-rect-comma-001 — previously dropped the whole declaration.
        val r = assertNotNull(rectOf("rect(50px 150px 150px 50px)"))
        assertEquals(50.0, r.top?.originalValue)
        assertEquals(150.0, r.right?.originalValue)
        assertEquals(150.0, r.bottom?.originalValue)
        assertEquals(50.0, r.left?.originalValue)
    }

    @Test
    fun `whitespace around the commas is not a mixed spelling`() {
        // Whitespace adjacent to a comma is always allowed; what makes a
        // value invalid is a part holding TWO tokens.
        val r = assertNotNull(rectOf("rect(50px ,150px,  150px , 50px)"))
        assertEquals(150.0, r.right?.originalValue)
    }

    @Test
    fun `mixing comma and space separators is rejected`() {
        // CSS 2.1 §11.1.2: the separator style must be used consistently.
        // WPT clip-rect-comma-002/003/004 all expect NO clipping — the
        // declaration is a syntax error and must be dropped whole. A naive
        // "split on comma OR whitespace" rule passes comma-001 and breaks
        // exactly these three.
        assertNull(rectOf("rect(50px 150px, 150px, 50px)"))
        assertNull(rectOf("rect(50px, 150px 150px, 50px)"))
        assertNull(rectOf("rect(50px, 150px, 150px 50px)"))
    }

    @Test
    fun `an empty comma-delimited part is rejected`() {
        assertNull(rectOf("rect(50px, , 150px, 50px)"))
        assertNull(rectOf("rect(50px, 150px, 150px, 50px,)"))
    }

    @Test
    fun `auto sides are stored as null, in both spellings`() {
        // Null means "resolve against the draw bounds", NOT zero — the
        // web extractor re-emits these as the `auto` keyword.
        val commas = assertNotNull(rectOf("rect(auto, auto, auto, auto)"))
        assertNull(commas.top); assertNull(commas.right); assertNull(commas.bottom); assertNull(commas.left)
        val spaces = assertNotNull(rectOf("rect(50px auto 150px 100px)"))
        assertEquals(50.0, spaces.top?.originalValue)
        assertNull(spaces.right)
        assertEquals(150.0, spaces.bottom?.originalValue)
        assertEquals(100.0, spaces.left?.originalValue)
    }

    @Test
    fun `a wrong component count is still rejected`() {
        // The looser split must not turn a malformed value into a
        // plausible clip — three components is not a rect().
        assertNull(rectOf("rect(50px 150px 150px)"))
        assertNull(rectOf("rect(50px, 150px, 150px, 50px, 10px)"))
    }
}

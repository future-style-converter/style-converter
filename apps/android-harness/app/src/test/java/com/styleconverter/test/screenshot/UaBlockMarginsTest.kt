package com.styleconverter.test.screenshot

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the pure UA-default block-margin model (UaBlockMargins.kt) that
 * FIX 1 of TITAN Round 4b uses to reproduce the browser-ref's inter-`<p>` gaps
 * in composed WPT capture. No Compose/Android runtime — the decisions are pure.
 *
 * Covers: the per-tag UA margin table, the "IR-declared margin wins over UA"
 * zeroing, and the adjacent-margin COLLAPSING that turns two 16px-margin bars
 * into a single 16px gap (not 32).
 */
class UaBlockMarginsTest {

    // ── UA margin table ─────────────────────────────────────────────────────

    @Test
    fun paragraph_has1emBlockMargins() {
        // <p>: 16px top+bottom, no horizontal margin (the hsl/rgb-001 bars).
        assertEquals(UaMargins(16, 16, 0, 0), uaBlockMargins("p"))
    }

    @Test
    fun tagLookupIsCaseInsensitive() {
        assertEquals(uaBlockMargins("p"), uaBlockMargins("P"))
    }

    @Test
    fun headings_scaleWithHeadingFont() {
        assertEquals(UaMargins(21, 21, 0, 0), uaBlockMargins("h1"))
        assertEquals(UaMargins(19, 19, 0, 0), uaBlockMargins("h2"))
        assertEquals(UaMargins(16, 16, 0, 0), uaBlockMargins("h3"))
        assertEquals(UaMargins(21, 21, 0, 0), uaBlockMargins("h4"))
        assertEquals(UaMargins(27, 27, 0, 0), uaBlockMargins("h5"))
        assertEquals(UaMargins(37, 37, 0, 0), uaBlockMargins("h6"))
    }

    @Test
    fun listsAndPreHave1emBlockMargins() {
        assertEquals(UaMargins(16, 16, 0, 0), uaBlockMargins("ul"))
        assertEquals(UaMargins(16, 16, 0, 0), uaBlockMargins("ol"))
        assertEquals(UaMargins(16, 16, 0, 0), uaBlockMargins("pre"))
    }

    @Test
    fun blockquoteAndFigureHaveHorizontalInsets() {
        assertEquals(UaMargins(16, 16, 40, 40), uaBlockMargins("blockquote"))
        assertEquals(UaMargins(16, 16, 40, 40), uaBlockMargins("figure"))
    }

    @Test
    fun blockContainersAndUnknownTagsHaveZeroUaMargin() {
        // div/section/article/header/footer/main/nav/aside → 0 in the UA sheet.
        for (tag in listOf("div", "section", "article", "header", "footer",
                           "main", "nav", "aside", "span", null, "unknowntag")) {
            assertEquals("tag=$tag", UaMargins.ZERO, uaBlockMargins(tag))
        }
    }

    // ── IR-declared margin wins over UA ─────────────────────────────────────

    @Test
    fun declaredSides_derivedFromPropertyTypes() {
        assertEquals(setOf("top"), declaredMarginSides(listOf("MarginTop")))
        assertEquals(setOf("bottom"), declaredMarginSides(listOf("MarginBottom")))
        assertEquals(setOf("top", "bottom"), declaredMarginSides(listOf("MarginBlock")))
        assertEquals(setOf("top", "bottom", "left", "right"),
            declaredMarginSides(listOf("Margin")))
        assertEquals(emptySet<String>(), declaredMarginSides(listOf("BackgroundColor")))
    }

    @Test
    fun irDeclaredSideZeroesTheUaDefault() {
        // A <p> that declares its own margin-top keeps the UA bottom but the
        // top is 0 (the runtime renders the declared top — UA must not add).
        assertEquals(UaMargins(0, 16, 0, 0), effectiveUaMargins("p", setOf("top")))
        // The a98rgb-003 boxes: divs (UA 0) declaring MarginBottom / MarginTop.
        assertEquals(UaMargins.ZERO, effectiveUaMargins("div", setOf("bottom")))
    }

    @Test
    fun barWithNoDeclaredMargin_keepsFullUaDefault() {
        // The hsl/rgb-001 bars declare only BackgroundColor → full 16/16.
        assertEquals(UaMargins(16, 16, 0, 0), effectiveUaMargins("p", emptySet()))
    }

    // ── Adjacent-margin collapsing ──────────────────────────────────────────

    @Test
    fun tenParagraphBars_collapseTo16pxGaps_notFlush_notDouble() {
        // 10 <p> bars, each 16/16, stacked in a padded canvas. Expected gaps:
        //   before first = 16 (kept: padding blocks collapse to the canvas)
        //   between each = max(16,16) = 16 (collapsed, NOT 32)
        //   after last   = 16 (kept)
        val margins = List(10) { 16 to 16 }
        val gaps = collapsedVerticalGaps(margins)
        assertEquals(11, gaps.size)
        assertEquals(List(11) { 16 }, gaps)
    }

    @Test
    fun asymmetricAdjacentMargins_collapseToMax() {
        // A 16px-bottom above a 24px-top collapses to 24 (the larger).
        val gaps = collapsedVerticalGaps(listOf(16 to 16, 24 to 8))
        assertEquals(listOf(16, 24, 8), gaps)
    }

    @Test
    fun zeroMarginBlocks_produceNoGaps() {
        // Divs (UA 0) render flush — no injected spacing.
        val gaps = collapsedVerticalGaps(listOf(0 to 0, 0 to 0, 0 to 0))
        assertEquals(listOf(0, 0, 0, 0), gaps)
    }

    @Test
    fun a98rgb003Layout_bodyRootThenParagraphThenBoxes() {
        // roots: body-root(div 0/0), <p> 16/16, div w/ IR margin-bottom (0/0),
        // div w/ IR margin-top (0/0). Only the <p> injects 16px above+below.
        val margins = listOf(0 to 0, 16 to 16, 0 to 0, 0 to 0)
        val gaps = collapsedVerticalGaps(margins)
        assertEquals(listOf(0, 16, 16, 0, 0), gaps)
    }

    @Test
    fun emptyRoots_yieldSingleZeroGap() {
        assertEquals(listOf(0), collapsedVerticalGaps(emptyList()))
    }
}

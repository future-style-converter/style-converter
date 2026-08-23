package com.styleconverter.runtime.spacing

// Wave 25, lane UAM (BD-RC3) — the SHARED U1-U12 pin table for the UA
// default block margins of block-level DESCENDANTS.
//
// Every expectation in this file is asserted with the IDENTICAL number by
// the iOS twin (runtimes/swiftui/Tests/StyleConverterRuntimeTests/
// UABlockChildMarginTests.swift). Divergence between the two natives is
// precisely the bug class the shared table exists to kill.
//
// U1-U8 pin the pure merge (UaBlockChildMargins.kt); the REF-A..REF-E pins
// live in the renderer-level suites (BlockCollapsePlanForTest.kt here,
// MarginCollapseTests.swift on iOS) because they need the plan builder.
//
// ## Provenance of the numbers
// The UA table is the Round-4 browser-ref calibration, unchanged. The
// CASCADE and PARENT-EDGE rules were re-measured for this lane against
// headless Chromium at a 16px root (the same methodology
// tools/titan/capture-browser-ref.mjs uses), probing five container
// shapes; the measured geometry is quoted verbatim on each REF pin in the
// plan-builder suite.

import org.junit.Assert.assertEquals
import org.junit.Test

class UaBlockChildMarginsTest {

    // Shorthand: the merge with UA injection ON (the WPT-capture branch).
    private fun on(tag: String?, types: List<String>, declared: CollapsedMargin) =
        uaChildBlockEdges(tag, types, declared, enabled = true)

    // A zero declared pair — the classifier's answer for a margin-less
    // child (both edges are the CSS initial 0, css-box-3 §3).
    private val none = CollapsedMargin(0f, 0f)

    // ── U1: the UA table, tag by tag ────────────────────────────────────

    /** U1 — every tag the UA sheet gives a block margin, at a 16px root.
     *  Identical numbers to UABlockMargin.vertical(forTag:) on iOS, which
     *  is why the composed ROOT stack and this CHILD fold can never
     *  disagree about what a `<p>` margin is worth. */
    @Test
    fun `U1 - ua vertical table per tag`() {
        assertEquals(16f to 16f, uaVerticalBlockMargins("p"))
        assertEquals(21f to 21f, uaVerticalBlockMargins("h1"))
        assertEquals(19f to 19f, uaVerticalBlockMargins("h2"))
        assertEquals(16f to 16f, uaVerticalBlockMargins("h3"))
        assertEquals(21f to 21f, uaVerticalBlockMargins("h4"))
        assertEquals(27f to 27f, uaVerticalBlockMargins("h5"))
        assertEquals(37f to 37f, uaVerticalBlockMargins("h6"))
        assertEquals(16f to 16f, uaVerticalBlockMargins("ul"))
        assertEquals(16f to 16f, uaVerticalBlockMargins("ol"))
        assertEquals(16f to 16f, uaVerticalBlockMargins("blockquote"))
        assertEquals(16f to 16f, uaVerticalBlockMargins("pre"))
        assertEquals(16f to 16f, uaVerticalBlockMargins("figure"))
    }

    /** U2 — flow containers and unknown/absent tags carry NO UA block
     *  margin (the UA sheet declares none), so a `<div>` wrapper never
     *  invents spacing. `<li>` is explicitly in this bucket. */
    @Test
    fun `U2 - flow containers and unknown tags are zero`() {
        for (tag in listOf("div", "section", "article", "header", "footer",
                           "main", "nav", "aside", "li", "span", "unknown", null)) {
            assertEquals("tag=$tag", 0f to 0f, uaVerticalBlockMargins(tag))
        }
        // Case-insensitive: the v1 wire could carry an uppercase tag.
        assertEquals(16f to 16f, uaVerticalBlockMargins("P"))
    }

    // ── U3-U8: the per-edge cascade ─────────────────────────────────────

    /** U3 — the dark-stage 327 identity branch: with the flag OFF the
     *  merge returns the caller's declared edges VERBATIM, whatever the
     *  tag. This is the whole baseline-protection contract. */
    @Test
    fun `U3 - disabled is the identity`() {
        assertEquals(none, uaChildBlockEdges("p", emptyList(), none, enabled = false))
        assertEquals(CollapsedMargin(7f, 3f),
                     uaChildBlockEdges("h1", listOf("MarginTop", "MarginBottom"),
                                       CollapsedMargin(7f, 3f), enabled = false))
    }

    /** U4 — an UNDECLARED `<p>` child takes the UA 1em on both edges.
     *  This is the whole BD-RC3 defect: natives rendered (0,0) here, so
     *  every nested paragraph sat 16px too high. */
    @Test
    fun `U4 - undeclared p takes the ua default on both edges`() {
        assertEquals(CollapsedMargin(16f, 16f), on("p", emptyList(), none))
    }

    /** U5 — author > UA per EDGE (css-cascade-4 §6.1). A declared
     *  `margin-top: 40px` replaces the UA top and leaves the UA bottom
     *  intact — measured on the ref probe (case E, see the plan suite). */
    @Test
    fun `U5 - declared top wins, undeclared bottom keeps ua`() {
        assertEquals(CollapsedMargin(40f, 16f),
                     on("p", listOf("MarginTop"), CollapsedMargin(40f, 0f)))
    }

    /** U6 — both edges declared: the UA table never contributes. A
     *  declared ZERO also wins (author `margin-bottom: 0` really means 0,
     *  not 1em) — the declaration test is type-name based precisely so a
     *  declared 0 is distinguishable from an absent edge. */
    @Test
    fun `U6 - both edges declared pass through, including a declared zero`() {
        assertEquals(CollapsedMargin(4f, 0f),
                     on("p", listOf("MarginTop", "MarginBottom"), CollapsedMargin(4f, 0f)))
    }

    /** U7 — the LOGICAL longhands the converter emits for
     *  `margin-block-start/end` count as declared (LTR-TB → top/bottom,
     *  css-logical-1 §4.2), same set the composed ROOT fold uses. */
    @Test
    fun `U7 - logical block longhands count as declared`() {
        assertEquals(CollapsedMargin(9f, 16f),
                     on("p", listOf("MarginBlockStart"), CollapsedMargin(9f, 0f)))
        assertEquals(CollapsedMargin(16f, 9f),
                     on("p", listOf("MarginBlockEnd"), CollapsedMargin(0f, 9f)))
    }

    /** U8 — INLINE-axis margins never declare a block edge: a child with
     *  only `margin-left` still takes both UA block defaults (§8.3.1 is
     *  vertical-only, and the inline sides pass through the applier). */
    @Test
    fun `U8 - inline margins do not suppress the ua block defaults`() {
        assertEquals(CollapsedMargin(16f, 16f),
                     on("p", listOf("MarginLeft", "MarginRight", "MarginInlineStart"), none))
    }

    /** U9 — a `<div>` child with declared margins is untouched by the
     *  merge even with the flag ON (UA 0 on both edges), so mixing UA
     *  prose and plain wrappers in one container is safe. */
    @Test
    fun `U9 - div child keeps its declared margins verbatim`() {
        assertEquals(CollapsedMargin(20f, 20f),
                     on("div", listOf("MarginTop", "MarginBottom"), CollapsedMargin(20f, 20f)))
        assertEquals(none, on("div", emptyList(), none))
    }

    /** U10 — heading tags feed their OWN (larger/smaller) em, so an
     *  `<h1>` child contributes 21px where a `<p>` contributes 16px; the
     *  §8.3.1 fold then takes the max of the two adjoining values. */
    @Test
    fun `U10 - heading children contribute their own em`() {
        assertEquals(CollapsedMargin(21f, 21f), on("h1", emptyList(), none))
        assertEquals(CollapsedMargin(37f, 37f), on("h6", emptyList(), none))
    }

    // ── U13-U15 (wave 46, lane Y8): the own-font basis ──────────────────

    /** U13 — a NULL basis is the identity: the two-arg table equals the
     *  one-arg table for every tag, which is the contract that keeps every
     *  corpus element without a font-size declaration byte-identical. */
    @Test
    fun `U13 - null basis is the 16px-root table verbatim`() {
        for (tag in listOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol",
                           "blockquote", "pre", "figure", "div", "li", null)) {
            assertEquals("tag=$tag", uaVerticalBlockMargins(tag), uaVerticalBlockMargins(tag, null))
        }
        // The default parameter on the merge is the same identity.
        assertEquals(CollapsedMargin(16f, 16f), uaChildBlockEdges("p", emptyList(), none, enabled = true))
    }

    /** U14 — THE measured case: a `<p>` whose own computed size is 19.2px
     *  (`font-size: larger` of 16, inherit-computed-001) carries 1em ×
     *  19.2 = 19.2px on both edges — the browser-ref's row 35 (16 pad +
     *  19.2) against the 16px table's row 32. A 1em tag scales linearly
     *  (a 92px `<p>` → 92), and a font-sized `<div>` still has none. */
    @Test
    fun `U14 - 1em tags resolve against the own font basis`() {
        assertEquals(19.2f to 19.2f, uaVerticalBlockMargins("p", 19.2f))
        assertEquals(92f to 92f, uaVerticalBlockMargins("ul", 92f))
        assertEquals(13f to 13f, uaVerticalBlockMargins("blockquote", 13f))
        assertEquals(0f to 0f, uaVerticalBlockMargins("div", 19.2f))
        // Through the merge: declared edges still win per edge (§6.1).
        assertEquals(CollapsedMargin(19.2f, 19.2f),
                     uaChildBlockEdges("p", emptyList(), none, enabled = true, ownFontSizePx = 19.2f))
        assertEquals(CollapsedMargin(40f, 19.2f),
                     uaChildBlockEdges("p", listOf("MarginTop"), CollapsedMargin(40f, 0f),
                                       enabled = true, ownFontSizePx = 19.2f))
        // And the dark-stage flag still short-circuits BEFORE the basis.
        assertEquals(none, uaChildBlockEdges("p", emptyList(), none, enabled = false, ownFontSizePx = 19.2f))
    }

    /** U15 — headings scale by their OWN em factor over the given basis
     *  (html.css `h1 { margin-block: .67em }` … `h6 { 2.33em }`): an `<h1>`
     *  the author resized to 40px carries .67 × 40 = 26.8px. */
    @Test
    fun `U15 - headings scale by their ua em factor`() {
        val (top, bottom) = uaVerticalBlockMargins("h1", 40f)
        assertEquals(26.8f, top, 1e-4f)
        assertEquals(26.8f, bottom, 1e-4f)
        assertEquals(2.33f * 10f, uaVerticalBlockMargins("h6", 10f).first, 1e-4f)
    }
}

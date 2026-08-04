package com.styleconverter.runtime.lists

// Wave 30, lane 3 — pin table for ListItemMarkerGate (fix B1): WHICH
// boxes generate a ::marker from their own `display`.
//
// ## The defect these pin against
// Both natives gated marker synthesis on the PARENT tag alone, so a
// `<div style="display:list-item">` — the shape
// wpt/css-lists/change-list-style-position-002 and -003 are built from —
// painted no marker while web (which just sets `display:list-item` and
// lets Chromium generate the box) painted one. On -003 that left the
// inner box 20px too high (one line box) on BOTH natives: min-SSIM 0.9314
// Android↔web / 0.9317 iOS↔web.
//
// TWIN of the iOS suite ListItemMarkerGateTests.swift — same cases, same
// expectations. Change one, change both.
//
// The @Composable paint itself needs androidTest; these cover the pure
// decision, the same split ListMarkerRowTest documents.

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListItemMarkerGateTest {

    private fun json(s: String): JsonElement = Json.parseToJsonElement(s)

    private fun pair(type: String, wire: String): Pair<String, JsonElement?> =
        type to json(wire)

    /**
     * The EXACT root payload of the live
     * `wpt__css-lists__change-list-style-position-003` document: a tagless
     * component with `Display: LIST_ITEM` and `ListStylePosition: INSIDE`
     * and no `list-style-type` at all.
     */
    private val outerItem = listOf(
        pair("Display", "\"LIST_ITEM\""),
        pair("ListStylePosition", "\"INSIDE\"")
    )

    /** Its child in the same document — the one that adds `decimal`. */
    private val innerItem = outerItem + pair("ListStyleType", "\"decimal\"")

    // ── isListItemDisplay: the css-lists-3 §3.1 predicate ───────────────

    @Test
    fun `the live wire spelling is recognised`() {
        assertTrue(ListItemMarkerGate.isListItemDisplay(outerItem))
    }

    @Test
    fun `the CSS spelling is recognised too`() {
        // schema/spec/05-versioning.md tolerates a foreign producer's
        // unknown property DATA, so the hyphenated CSS keyword must not
        // silently fall through to "not a list item".
        assertTrue(ListItemMarkerGate.isListItemDisplay(
            listOf(pair("Display", "\"list-item\""))))
    }

    @Test
    fun `every other display is not a list item`() {
        for (keyword in listOf("BLOCK", "INLINE", "FLEX", "GRID", "NONE", "INLINE_BLOCK")) {
            assertFalse(keyword,
                ListItemMarkerGate.isListItemDisplay(listOf(pair("Display", "\"$keyword\""))))
        }
        // No Display entry at all — the `<ul>`/`<li>` shape of
        // change-list-style-type-001, which must keep the parent-loop path.
        assertFalse(ListItemMarkerGate.isListItemDisplay(
            listOf(pair("ListStyleType", "\"square\""))))
    }

    @Test
    fun `a later Display entry shadows an earlier one`() {
        // Last-wins, the fold rule every other extractor in this package
        // uses — a dynamic bucket can append a second Display.
        assertFalse(ListItemMarkerGate.isListItemDisplay(listOf(
            pair("Display", "\"LIST_ITEM\""), pair("Display", "\"BLOCK\""))))
        assertTrue(ListItemMarkerGate.isListItemDisplay(listOf(
            pair("Display", "\"BLOCK\""), pair("Display", "\"LIST_ITEM\""))))
    }

    // ── ownMarkerConfig / ownMarkerText ─────────────────────────────────

    @Test
    fun `a self-marking item with no type declaration takes the initial disc`() {
        // css-lists-3 §3.1: `list-style-type`'s INITIAL value is `disc`.
        // HTML §15.3.9's ul/ol rules are declarations on a CONTAINER
        // element and match no `display:list-item` div, so the initial
        // value is what stands — and web paints exactly that (a 5×5 bullet
        // at the outer item's content origin, ink rows 30–34).
        assertEquals(ListStyleType.DISC,
            ListItemMarkerGate.ownMarkerConfig(outerItem).listStyleType)
        assertEquals("•", ListItemMarkerGate.ownMarkerText(outerItem))
    }

    @Test
    fun `the items own list-style-type wins`() {
        assertEquals(ListStyleType.DECIMAL,
            ListItemMarkerGate.ownMarkerConfig(innerItem).listStyleType)
        // Ordinal 1 — the documented KNOWN GAP (no sibling index at this
        // call site). Both corpus documents nest their items, so 1 is the
        // right number for every one of them.
        assertEquals("1.", ListItemMarkerGate.ownMarkerText(innerItem))
    }

    // ── rendersOwnLeadingMarker: the four gates ─────────────────────────

    @Test
    fun `both list items of the live position-003 document render a marker`() {
        assertTrue(ListItemMarkerGate.rendersOwnLeadingMarker(null, outerItem))
        assertTrue(ListItemMarkerGate.rendersOwnLeadingMarker(null, innerItem))
    }

    @Test
    fun `a tagged li is left to the parent-loop path`() {
        // change-list-style-type-002 / add-inline-child-after-marker-001
        // put `Display: LIST_ITEM` on the `<li>` ITSELF. Firing here as
        // well would paint that item TWO markers.
        assertFalse(ListItemMarkerGate.rendersOwnLeadingMarker("li", innerItem))
        assertFalse(ListItemMarkerGate.rendersOwnLeadingMarker("LI", innerItem))
    }

    @Test
    fun `an outside marker keeps the current no-marker behaviour`() {
        // MEASURED gate, not an oversight: css-lists-3 §3.2 hangs an
        // `outside` marker in the item's MARGIN area, and a leading line
        // box would instead push the item's content down a line the
        // browser does not have. change-list-style-position-002 — three
        // nested `outside` list items — scores 1.0000 / 1.0000 / 0.9992
        // today with no markers at all, and its reference hangs all three
        // LEFT of the border box.
        val outside = listOf(
            pair("Display", "\"LIST_ITEM\""),
            pair("ListStylePosition", "\"OUTSIDE\"")
        )
        assertFalse(ListItemMarkerGate.rendersOwnLeadingMarker(null, outside))
        // …and the INITIAL value of list-style-position is `outside`
        // (css-lists-3 §3.1), so an item that declares no position at all
        // is the same case.
        assertFalse(ListItemMarkerGate.rendersOwnLeadingMarker(
            null, listOf(pair("Display", "\"LIST_ITEM\""))))
    }

    @Test
    fun `list-style-type none generates no marker box`() {
        // css-lists-3 §3.1 — the item has NO marker, so its content must
        // start at its own content edge and no line box may be reserved.
        assertFalse(ListItemMarkerGate.rendersOwnLeadingMarker(
            null, outerItem + pair("ListStyleType", "\"none\"")))
    }

    @Test
    fun `a plain block box never renders one`() {
        // The whole 327-pair dark stage: no Display LIST_ITEM anywhere, so
        // this gate is inert for every committed baseline.
        assertFalse(ListItemMarkerGate.rendersOwnLeadingMarker(
            "div", listOf(pair("Display", "\"BLOCK\""),
                          pair("ListStylePosition", "\"INSIDE\""))))
    }
}

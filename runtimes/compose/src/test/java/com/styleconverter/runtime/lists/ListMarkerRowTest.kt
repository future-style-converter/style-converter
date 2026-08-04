package com.styleconverter.runtime.lists

// Wave 28, lane MC — pin table for ListMarkerRow, the two geometry
// decisions ComponentRenderer.RenderListItemMarker makes for a
// synthesized `::marker` box. TWIN of the iOS pins in
// runtimes/swiftui/Tests/StyleConverterRuntimeTests/
// ListMarkerAbsposItemRasterTests.swift (which can additionally
// RASTERIZE — Compose's JVM unit stage cannot, so these cover the pure
// half, the same split ListMarkerTextStyleTest documents).
//
// ## The two defects these pin against
// Measured on the LIVE wave27-final section
// tools/titan/runs/wave27-final/sections/css-counter-styles/ — the baked
// `meta.markerText` was already being consumed, yet Android scored 3/12:
//
//   platform | marker x | reference-glyph x | row pitch
//   web      | 217–233  | 244–253           | 32px
//   iOS      | 217–235  | 264–280           | 31px
//   Android  | 218–231  | 268–280           | 43px
//
// 1. The reference column is displaced on BOTH natives, because the
//    marker row makes the marker a SIBLING of the item and every item
//    here anchors an absolutely positioned glyph to the item's own box
//    (css-position-3 §2.1). rendersInsideOverlay is the repair.
// 2. Android's 43px pitch against a declared `height: 31.25px`, because
//    an unconditional baseline claim adds a baseline-less item's marker
//    ascent to the row's height. alignsByBaseline is the repair.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.unit.dp

class ListMarkerRowTest {

    /** An out-of-flow text run — the shape the counter-style bake emits. */
    private fun absposText(text: String) = IRComponent(
        id = "t", name = "t", _text = text,
        properties = listOf(IRProperty("Position", JsonPrimitive("ABSOLUTE")))
    )

    /** The same run left in normal flow. */
    private fun flowText(text: String) =
        IRComponent(id = "t", name = "t", _text = text)

    /**
     * One `<li>` from the live css3-counter-styles-101 IR: RELATIVE,
     * 158×31.25, holding ONLY an absolutely positioned glyph.
     */
    private fun bakedItem(child: IRComponent) = IRComponent(
        id = "li", name = "li", _tag = "li", markerText = "١.",
        properties = listOf(IRProperty("Position", JsonPrimitive("RELATIVE"))),
        children = listOf(child)
    )

    // ── The baseline predicate ──────────────────────────────────────────

    @Test
    fun `an item whose only child is out of flow exposes no baseline`() {
        // css-position-3 §2.1: the abspos run paints through the parent's
        // positioned overlay and contributes neither height nor a baseline
        // to the item's principal box. This is EVERY item in the
        // counter-styles corpus.
        assertFalse(
            ListMarkerRow.itemExposesTextBaseline(bakedItem(absposText("١")))
        )
    }

    @Test
    fun `the same run in normal flow does expose one`() {
        // The control: only the out-of-flow-ness matters, not the shape.
        assertTrue(
            ListMarkerRow.itemExposesTextBaseline(bakedItem(flowText("١")))
        )
    }

    @Test
    fun `an item's own leading text exposes one`() {
        // `_text` renders as an in-flow label ahead of the children.
        assertTrue(
            ListMarkerRow.itemExposesTextBaseline(
                IRComponent(id = "li", name = "li", _text = "hello")
            )
        )
    }

    @Test
    fun `an empty item exposes none`() {
        // No text anywhere ⇒ nothing to share a baseline with; the row
        // must top-align rather than invent one.
        assertFalse(
            ListMarkerRow.itemExposesTextBaseline(IRComponent(id = "li", name = "li"))
        )
    }

    // ── Decision 2: the row's cross-axis alignment ──────────────────────

    @Test
    fun `the baseline claim is chosen per item, not applied unconditionally`() {
        // A silent flip back to "always claim" reintroduces defect 2 on
        // every baseline-less item. Same truth table as iOS's
        // ListMarkerRow.rowAlignment(itemExposesTextBaseline:), whose
        // `.top` / `.firstTextBaseline` are the SwiftUI spellings of
        // these two booleans.
        assertFalse(ListMarkerRow.alignsByBaseline(false))
        assertTrue(ListMarkerRow.alignsByBaseline(true))
    }

    // ── Decision 1: which placement the marker takes ────────────────────

    @Test
    fun `inside plus no in-flow text is the overlay placement`() {
        // css-lists-3 §3.2 — an `inside` marker is the item's FIRST INLINE
        // BOX, so it lives inside the principal box and cannot move it.
        assertTrue(
            ListMarkerRow.rendersInsideOverlay(
                ListStylePosition.INSIDE, itemExposesTextBaseline = false)
        )
    }

    @Test
    fun `inside on an item WITH text keeps the row`() {
        // The marker must push that text along the line; an overlay would
        // paint on top of the first word. Documented KNOWN GAP — the row
        // still displaces the item's box for this case.
        assertFalse(
            ListMarkerRow.rendersInsideOverlay(
                ListStylePosition.INSIDE, itemExposesTextBaseline = true)
        )
    }

    @Test
    fun `outside keeps the row even with no in-flow text`() {
        // §3.2 puts an `outside` marker in the item's MARGIN area, left of
        // the border box — drawing it at the content-box origin would move
        // it right by its own width. Its own (still deferred) displacement
        // of the item is called out at the renderer's marker branch.
        assertFalse(
            ListMarkerRow.rendersInsideOverlay(
                ListStylePosition.OUTSIDE, itemExposesTextBaseline = false)
        )
    }

    @Test
    fun `an unresolvable position keeps the row`() {
        // Null = the parent is not a list container — the ONLY condition
        // under which ListStyleExtractor.resolveMarkerConfig returns null.
        // Unknown position ⇒ unchanged behaviour, never a guess. On
        // Compose this arm is DEFENSIVE ONLY: RenderListItemMarker is
        // reached solely from an `isListParent` branch. (An untagged child
        // under a real <ol> is NOT this case — it resolves a non-null
        // config carrying the container's own position; see
        // ListMarkerRow.rendersInsideOverlay's reachability note.)
        assertFalse(
            ListMarkerRow.rendersInsideOverlay(null, itemExposesTextBaseline = false)
        )
    }

    // ── The shared constant ─────────────────────────────────────────────

    @Test
    fun `the marker gap is the value iOS also uses`() {
        // 4dp / 4pt, and NOTHING else: the renderer used to paint
        // "$marker " as well, stacking a ~7dp trailing space at the 25px
        // these items inherit on top of this padding, which iOS never had.
        assertEquals(4.dp, ListMarkerRow.gapDp)
    }
}

package com.styleconverter.runtime.layout

// Wave-38 lane N1 — pins for the BLOCK-context UA widget line box.
// Swift twin: UAWidgetBlockLineTests.swift (identical table, identical
// chain expectations); the two suites are what keeps the natives locked.

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UAWidgetBlockLineTest {

    // ── the ref-probed table ────────────────────────────────────────────

    @Test
    fun lineBoxTable_matchesTheAppearanceRevertRefSolve() {
        // Every entry is (lineHeightPx, topLeadPx) — see the file header
        // for the per-row ref band each one was solved against.
        assertEquals(22.0 to 1.0, UAWidgetBlockLine.lineBox(UAWidgetIntrinsics.Kind.TEXT_FIELD))
        assertEquals(22.0 to 1.0, UAWidgetBlockLine.lineBox(UAWidgetIntrinsics.Kind.BUTTON_LIKE))
        assertEquals(40.0 to 0.0, UAWidgetBlockLine.lineBox(UAWidgetIntrinsics.Kind.TEXTAREA))
        assertEquals(22.0 to 0.0, UAWidgetBlockLine.lineBox(UAWidgetIntrinsics.Kind.RANGE))
        assertEquals(20.0 to 3.0, UAWidgetBlockLine.lineBox(UAWidgetIntrinsics.Kind.CHECKBOX))
        assertEquals(20.0 to 3.0, UAWidgetBlockLine.lineBox(UAWidgetIntrinsics.Kind.RADIO))
        assertEquals(27.0 to 0.0, UAWidgetBlockLine.lineBox(UAWidgetIntrinsics.Kind.COLOR))
        assertEquals(21.0 to 2.0, UAWidgetBlockLine.lineBox(UAWidgetIntrinsics.Kind.SELECT))
        assertEquals(70.0 to 0.0, UAWidgetBlockLine.lineBox(UAWidgetIntrinsics.Kind.LISTBOX))
        assertEquals(20.0 to 3.0, UAWidgetBlockLine.lineBox(UAWidgetIntrinsics.Kind.METER))
        assertEquals(20.0 to 3.0, UAWidgetBlockLine.lineBox(UAWidgetIntrinsics.Kind.PROGRESS))
    }

    @Test
    fun textAnchorHasNoBlockLineBox() {
        // A text-only <a> is not a widget replica — it already renders
        // through the text path with the block's own line metrics.
        assertNull(UAWidgetBlockLine.lineBox(UAWidgetIntrinsics.Kind.TEXT_ANCHOR))
    }

    // ── the appearance-revert-001 chain, row by row ─────────────────────

    /**
     * Replays css-ui appearance-revert-001.tentative: 14 widgets, each the
     * only inline-level box of its own <div>, no wire geometry at all. The
     * expectations ARE the composed-WPT ref's painted band tops (content
     * origin y16), so a table edit that breaks the ref fails here first.
     */
    @Test
    fun appearanceRevertChain_reproducesEveryRefBandTop() {
        // (tag, type, multiple, expected border-box top in the ref)
        val rows = listOf<Triple<String, String?, Double>>(
            Triple("input", "text", 17.0),
            Triple("input", "search", 39.0),
            Triple("textarea", null, 60.0),
            Triple("input", "button", 101.0),
            Triple("input", "submit", 123.0),
            Triple("input", "reset", 145.0),
            // Range: ref INK top is 168; the paint plan's thumb sits 2px
            // into the box, so the border box is 166.
            Triple("input", "range", 166.0),
            Triple("input", "checkbox", 191.0),
            Triple("input", "radio", 211.0),
            Triple("input", "color", 228.0),
            Triple("select", null, 257.0),
            // <select multiple> — the listbox fork.
            Triple("select-multiple", null, 276.0),
            // Meter/progress: ref INK tops 353/373 with the plan's 4px bar
            // inset → border boxes 349/369.
            Triple("meter", null, 349.0),
            Triple("progress", null, 369.0),
        )
        // The body's content origin in the composed ref.
        var rowTop = 16.0
        rows.forEach { (tagSpec, typeAttr, expectedBoxTop) ->
            val multiple = tagSpec == "select-multiple"
            val tag = if (multiple) "select" else tagSpec
            val kind = UAWidgetIntrinsics.kind(tag, typeAttr, multiple)
            val (lineHeightPx, _) = UAWidgetBlockLine.lineBox(kind)!!
            val h = UAWidgetIntrinsics.spec(kind).fixedHpx!!
            // No wire geometry on this test — the lead is the whole story.
            val lead = UAWidgetBlockLine.leadFor(
                tag = tag, typeAttr = typeAttr, multiple = multiple,
                hasWireHeight = false, wireMarginTopPx = 0.0, wireMarginBottomPx = 0.0,
            )
            val top = lead?.topPx ?: 0.0
            val bottom = lead?.bottomPx ?: 0.0
            assertEquals("box top for $tagSpec/$typeAttr", expectedBoxTop, rowTop + top, 0.0)
            // The renderer's advance must BE the line box, or the next row
            // starts in the wrong place and the drift compounds.
            assertEquals("advance for $tagSpec/$typeAttr", lineHeightPx, top + h + bottom, 0.0)
            rowTop += lineHeightPx
        }
        // Last row top + its line box = the stack's bottom (progress row
        // 366 + 20): a whole-chain regression tripwire.
        assertEquals(386.0, rowTop, 0.0)
    }

    // ── the gates ───────────────────────────────────────────────────────

    @Test
    fun wireHeightSuppressesTheLead() {
        // A post-load-extracted element already carries its used box; the
        // renderer pins it, so padding the content would squeeze the
        // replica instead of leading it (css-ui accent-color-visited is
        // byte-exact today and must stay so).
        assertNull(
            UAWidgetBlockLine.leadFor(
                tag = "input", typeAttr = "checkbox", multiple = false,
                hasWireHeight = true, wireMarginTopPx = 3.0, wireMarginBottomPx = 3.0,
            )
        )
    }

    @Test
    fun verticalWritingModeSuppressesTheLead() {
        // The whole table is a horizontal-tb §10.8 solve; under a vertical
        // writing mode the block axis is x, so a top/bottom pad displaces
        // the replica instead of leading it. Device-measured on iOS
        // w38-n1: without this gate the four css-writing-modes
        // forms/{checkbox,radio}-appearance-native-vertical-* rows moved
        // 0.7795→0.7665 and 0.7796→0.7630 against the ref.
        for (kw in listOf(false)) {
            assertNull(
                "vertical writing mode must not lead (horizontal=$kw)",
                UAWidgetBlockLine.leadFor(
                    tag = "input", typeAttr = "checkbox", multiple = false,
                    hasWireHeight = false, wireMarginTopPx = 0.0, wireMarginBottomPx = 0.0,
                    horizontalWritingMode = kw,
                )
            )
        }
        // …and the horizontal default is untouched: the SAME call with the
        // gate open still returns the ref-probed 3px lead.
        val horizontal = UAWidgetBlockLine.leadFor(
            tag = "input", typeAttr = "checkbox", multiple = false,
            hasWireHeight = false, wireMarginTopPx = 0.0, wireMarginBottomPx = 0.0,
            horizontalWritingMode = true,
        )!!
        assertEquals(3.0, horizontal.topPx, 0.0)
    }

    @Test
    fun theAxisGateDefaultsOpenSoEveryFrozenCallSiteIsUnchanged() {
        // The parameter is defaulted precisely so the wave-38 chain test
        // above (and any caller that cannot read a writing mode) keeps the
        // horizontal solve. This pins that default rather than trusting it.
        assertEquals(
            UAWidgetBlockLine.leadFor(
                tag = "input", typeAttr = "text", multiple = false,
                hasWireHeight = false, wireMarginTopPx = 0.0, wireMarginBottomPx = 0.0,
                horizontalWritingMode = true,
            ),
            UAWidgetBlockLine.leadFor(
                tag = "input", typeAttr = "text", multiple = false,
                hasWireHeight = false, wireMarginTopPx = 0.0, wireMarginBottomPx = 0.0,
            )
        )
    }

    @Test
    fun wireMarginTopIsSubtractedNotStacked() {
        // Blink's UA `margin: 3px` on a checkbox rides the wire whenever
        // computed styles were extracted. The table's 3px topLead is the
        // SAME margin, so the lead must fall to zero rather than double it.
        val lead = UAWidgetBlockLine.leadFor(
            tag = "input", typeAttr = "checkbox", multiple = false,
            hasWireHeight = false, wireMarginTopPx = 3.0, wireMarginBottomPx = 3.0,
        )!!
        assertEquals(0.0, lead.topPx, 0.0)
        // 20 line box − 3 wire top − 0 lead − 13 box − 3 wire bottom = 1.
        assertEquals(1.0, lead.bottomPx, 0.0)
    }

    @Test
    fun aWireMarginLargerThanTheLeadClampsAtZero() {
        // The element's own ascent then already exceeds the strut, so no
        // extra space is owed — and a negative pad would be nonsense.
        val lead = UAWidgetBlockLine.leadFor(
            tag = "progress", typeAttr = null, multiple = false,
            hasWireHeight = false, wireMarginTopPx = 40.0, wireMarginBottomPx = 0.0,
        )
        // 40 already overruns the 20px line box → both pads clamp to 0 →
        // no lead at all (the frozen composition).
        assertNull(lead)
    }

    @Test
    fun nonWidgetInputTypesAreVetoedRatherThanFoldedToTextField() {
        // UAWidgetIntrinsics.kind maps these to TEXT_FIELD (the HTML
        // invalid-type default), whose 21px control box does not describe
        // them at all — appearance-auto-input-non-widget-001's assertion.
        listOf("hidden", "image", "file").forEach { t ->
            assertNull(
                t,
                UAWidgetBlockLine.leadFor(
                    tag = "input", typeAttr = t, multiple = false,
                    hasWireHeight = false, wireMarginTopPx = 0.0, wireMarginBottomPx = 0.0,
                )
            )
        }
    }

    @Test
    fun anchorsAndUnknownTagsGetNoLead() {
        // <a> resolves to TEXT_ANCHOR (no line box entry); an unknown tag
        // folds to the same harmless default rather than a guessed box.
        assertNull(
            UAWidgetBlockLine.leadFor(
                tag = "a", typeAttr = null, multiple = false,
                hasWireHeight = false, wireMarginTopPx = 0.0, wireMarginBottomPx = 0.0,
            )
        )
        assertNull(
            UAWidgetBlockLine.leadFor(
                tag = "div", typeAttr = null, multiple = false,
                hasWireHeight = false, wireMarginTopPx = 0.0, wireMarginBottomPx = 0.0,
            )
        )
    }

    @Test
    fun exactFitKindsReportNoLead() {
        // COLOR and LISTBOX are the two kinds whose line box EQUALS their
        // border box — a 0/0 lead, reported as null so the renderer keeps
        // the frozen, unpadded composition on those rows.
        assertNull(
            UAWidgetBlockLine.leadFor(
                tag = "input", typeAttr = "color", multiple = false,
                hasWireHeight = false, wireMarginTopPx = 0.0, wireMarginBottomPx = 0.0,
            )
        )
        assertNull(
            UAWidgetBlockLine.leadFor(
                tag = "select", typeAttr = null, multiple = true,
                hasWireHeight = false, wireMarginTopPx = 0.0, wireMarginBottomPx = 0.0,
            )
        )
    }
}

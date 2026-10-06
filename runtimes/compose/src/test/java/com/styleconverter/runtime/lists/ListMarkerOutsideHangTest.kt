package com.styleconverter.runtime.lists

// Wave 52, lane L6 (T5) — pin table for the `outside` marker hang
// (ListMarkerOutsideHang.place + ListMarkerRow.hangsOutside). TWIN of
// runtimes/swiftui/Tests/StyleConverterRuntimeTests/
// ListMarkerOutsideHangTests.swift: same numbers, same identities.
// Compose's JVM stage cannot lay out (no Robolectric), so these cover the
// pure geometry the Layout's measure policy feeds — the same split
// ListMarkerRowTest documents.
//
// The numbers are the MEASURED counter-suffix row 1 (wave51-fix): the
// `<ol>`'s content edge is at x 64 (16 px body margin + 3em = 48 px
// padding), the ref's marker ink runs x 46–58 (13 px) and the text starts
// at 64; today's natives put the marker at 64–73 and the text at 80.
//
// MUTATION PROOF (EXECUTED 2026-09-25 at authoring, RE-EXECUTED 2026-10-05
// by tools/titan/results/wave52-counters-and-lists/mutate.py, entry
// `hang-width-kt` in mutations.log): making `place` report
// `itemWidth + markerWidth + gapPx` as the pair's width — re-adding the
// marker to the row's extent, the pre-T5 Row behaviour — turned `the pair
// reports the item's size only` and the RTL pin red (`expected:<158> but
// was:<175>`) and left the identity pins green; restored byte-exact.
// The renderer WIRING (seam-1.patch) has no JVM pin — Compose cannot lay
// out here — so the device A/B is its proof; the iOS twin's wiring IS
// raster-pinned (seam-2.patch's ListMarkerOutsideHangRasterTests).

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListMarkerOutsideHangTest {

    // The wave51-fix counter-suffix row-1 shape: marker 13×24 (ink 46–58 on
    // the ref), item 158×24 (the `<ol>`'s 10em − 6em padding at 16 px…
    // the width the Row hands the item today), gap = ListMarkerRow.gapDp
    // at 1 dp/px = 4.
    private val gap = 4
    private fun hang(aligns: Boolean, mb: Int? = 18, ib: Int? = 18, rtl: Boolean = false) =
        ListMarkerOutsideHang.place(
            markerWidth = 13, markerHeight = 24, markerBaseline = mb,
            itemWidth = 158, itemHeight = 24, itemBaseline = ib,
            gapPx = gap, alignsByBaseline = aligns, rtl = rtl
        )

    // ── the two identities the brief names ───────────────────────────────

    @Test
    fun `the pair reports the item's size only`() {
        // css-lists-3 §3.5: the item's content edge does not move, so the
        // pair is exactly the item — no marker width, no gap, no marker
        // height. This is the pin the pre-T5 Row fails (it reported 175).
        val p = hang(aligns = true)
        assertEquals(158, p.width)
        assertEquals(24, p.height)
    }

    @Test
    fun `marker right edge plus gap equals the item's left edge`() {
        // The hang identity: markerX + markerWidth + gap == 0 (the item's
        // border-box start, which is the pair's origin).
        val p = hang(aligns = true)
        assertEquals(0, p.markerX + 13 + gap)
        // In page coordinates with the item at x 64 that is marker ink at
        // 47–60 — the ref's 46–58 within the 4 px gap's rounding.
        assertEquals(47, 64 + p.markerX)
    }

    // ── the block axis: baseline or top, chosen per item ─────────────────

    @Test
    fun `baseline alignment offsets the marker by the two baselines' difference`() {
        // Marker baseline 18, item baseline 20 → the marker moves down 2 so
        // both ink runs sit on ONE line (css-lists-3 §3.5).
        assertEquals(2, hang(aligns = true, mb = 18, ib = 20).markerY)
        // Equal baselines: no offset.
        assertEquals(0, hang(aligns = true).markerY)
    }

    @Test
    fun `without a baseline claim, or without a baseline, the boxes stack by their tops`() {
        // The Row's `Alignment.Top` fallback, verbatim: a baseline-less item
        // (the abspos-only counter-style items) never pulls the marker.
        assertEquals(0, hang(aligns = false, mb = 18, ib = 30).markerY)
        assertEquals(0, hang(aligns = true, mb = null, ib = 30).markerY)
        assertEquals(0, hang(aligns = true, mb = 18, ib = null).markerY)
    }

    // ── RTL: the inline-END side ─────────────────────────────────────────

    @Test
    fun `under rtl the marker hangs after the item's end edge`() {
        // css-lists-3 §3.5 under `direction: rtl`; Compose's `place` is not
        // mirrored, so the mirror is explicit: START edge at width + gap.
        val p = hang(aligns = true, rtl = true)
        assertEquals(158 + gap, p.markerX)
        assertEquals(158, p.width)
    }

    // ── the gate: which items take the hang ──────────────────────────────

    @Test
    fun `only an outside position hangs the marker`() {
        // `inside` keeps the Row / overlay; null (unknown) keeps the Row.
        assertTrue(ListMarkerRow.hangsOutside(ListStylePosition.OUTSIDE))
        assertFalse(ListMarkerRow.hangsOutside(ListStylePosition.INSIDE))
        assertFalse(ListMarkerRow.hangsOutside(null))
        // The two decisions are disjoint: an item can never be both hung
        // and overlaid, whatever its text exposes.
        for (exposes in listOf(true, false)) {
            assertFalse(ListMarkerRow.hangsOutside(ListStylePosition.INSIDE) &&
                ListMarkerRow.rendersInsideOverlay(ListStylePosition.INSIDE, exposes))
            assertFalse(ListMarkerRow.rendersInsideOverlay(ListStylePosition.OUTSIDE, exposes))
        }
    }
}

package com.styleconverter.runtime.layout.grid

// Wave 19 (lane GRID-DISTRIBUTION) pins.
//
// Half 1 — GridContentDistribution.trackOrigins: css-align-3 §5.3 content
// distribution of the track group + direction:rtl column mirroring. The
// numeric rows P1-P12 are lifted from the LIVE wave18 IR of the failing WPT
// family (css-grid descendant-static-position-002/003/004: 40/43/44px and
// 10px+33px column templates inside 100px / 20px content boxes) — the exact
// geometry Chrome renders for those captures. THIS TABLE IS THE
// CROSS-PLATFORM CONTRACT: GridContentDistributionTests.swift pins the SAME
// rows against the SwiftUI twin, byte-for-byte.
//
// Half 2 — GridRenderer.implicitColumnCount: css-grid-1 §7.5 implicit
// column enumeration for template-less grids (the RC-B3 phantom-track fix:
// the legacy hard-coded 2 split display-contents-alignment-002's single
// spliced item into half the container).

import com.styleconverter.runtime.core.placement.GridClaims
import org.junit.Assert.assertEquals
import org.junit.Test

class GridContentDistributionTest {

    // Tolerance for Double math (all pins are exact binary fractions or
    // thirds — 1e-9 keeps the twin tables numerically honest).
    private val eps = 1e-9

    // Shorthand: run the pure function and compare per-element.
    private fun pin(
        widths: List<Double>,
        gap: Double,
        extent: Double,
        justify: GridContentDistribution.Justify,
        rtl: Boolean,
        expected: List<Double>
    ) {
        val got = GridContentDistribution.trackOrigins(widths, gap, extent, justify, rtl)
        assertEquals("origin count", expected.size, got.size)
        expected.zip(got).forEachIndexed { i, (e, g) ->
            assertEquals("origin[$i]", e, g, eps)
        }
    }

    // ── LTR justify-content (descendant-static-position-003 geometry) ────

    @Test
    fun `P1 start keeps the group at the content-box origin`() =
        // 001-family regression pin: start alignment never moves anything.
        pin(listOf(40.0), 0.0, 100.0, GridContentDistribution.Justify.START, false, listOf(0.0))

    @Test
    fun `P2 end shifts a 40px track by the full 60px leftover`() =
        // 003 sub-grid 1: the +60px shift the lane's pixel proof cites.
        pin(listOf(40.0), 0.0, 100.0, GridContentDistribution.Justify.END, false, listOf(60.0))

    @Test
    fun `P3 end shifts a 43px track by 57px`() =
        // 003 sub-grids 2/3 (43px template).
        pin(listOf(43.0), 0.0, 100.0, GridContentDistribution.Justify.END, false, listOf(57.0))

    @Test
    fun `P4 end packs a 10+33 group flush right`() =
        // 003 sub-grids 5/6: both tracks shift together, order kept.
        pin(listOf(10.0, 33.0), 0.0, 100.0, GridContentDistribution.Justify.END, false,
            listOf(57.0, 67.0))

    @Test
    fun `P11 center splits the leftover evenly`() =
        // LTR center: (100−40)/2 = 30.
        pin(listOf(40.0), 0.0, 100.0, GridContentDistribution.Justify.CENTER, false, listOf(30.0))

    // ── RTL mirroring (descendant-static-position-002 geometry) ──────────

    @Test
    fun `P5 rtl start overflows a 40px track past the LEFT edge of a 20px box`() =
        // 002 sub-grids 1/4: start = right edge in RTL → left edge at −20.
        pin(listOf(40.0), 0.0, 20.0, GridContentDistribution.Justify.START, true, listOf(-20.0))

    @Test
    fun `P6 rtl start overflows a 43px track to minus 23`() =
        // 002 sub-grids 2/3.
        pin(listOf(43.0), 0.0, 20.0, GridContentDistribution.Justify.START, true, listOf(-23.0))

    @Test
    fun `P7 rtl reverses physical column order`() =
        // 002 sub-grids 5/6: col 1 (10px) hugs the right edge, col 2 (33px)
        // spills 23px past the left edge.
        pin(listOf(10.0, 33.0), 0.0, 20.0, GridContentDistribution.Justify.START, true,
            listOf(10.0, -23.0))

    // ── RTL + center (descendant-static-position-004 geometry) ───────────

    @Test
    fun `P8 rtl center of a 40px track lands at 30`() =
        // 004 sub-grids 1/4: center is direction-symmetric.
        pin(listOf(40.0), 0.0, 100.0, GridContentDistribution.Justify.CENTER, true, listOf(30.0))

    @Test
    fun `P9 rtl center of a 44px track lands at 28`() =
        // 004 sub-grids 2/3.
        pin(listOf(44.0), 0.0, 100.0, GridContentDistribution.Justify.CENTER, true, listOf(28.0))

    @Test
    fun `P10 rtl center of a 10+33 group mirrors the pair`() =
        // 004 sub-grids 5/6: logical lead 28.5 → col 1 right of col 2.
        pin(listOf(10.0, 33.0), 0.0, 100.0, GridContentDistribution.Justify.CENTER, true,
            listOf(61.5, 28.5))

    @Test
    fun `P12 rtl end packs the group flush LEFT with col 1 rightmost of it`() =
        // end = the LEFT physical edge in RTL; order still reversed.
        pin(listOf(10.0, 33.0), 0.0, 100.0, GridContentDistribution.Justify.END, true,
            listOf(33.0, 0.0))

    // ── space-* distribution + §5.3 fallbacks ────────────────────────────

    @Test
    fun `P13 space-between widens the inner gap only`() =
        // leftover 50 into the single inner gap on top of the 10px gap.
        pin(listOf(20.0, 20.0), 10.0, 100.0,
            GridContentDistribution.Justify.SPACE_BETWEEN, false, listOf(0.0, 80.0))

    @Test
    fun `P14 space-around leads with a half share`() =
        // leftover 50: lead 12.5, between 10+25 → [12.5, 67.5].
        pin(listOf(20.0, 20.0), 10.0, 100.0,
            GridContentDistribution.Justify.SPACE_AROUND, false, listOf(12.5, 67.5))

    @Test
    fun `P15 space-evenly deals n+1 equal shares`() =
        // leftover 50 into 3 shares of 50/3 → [50/3, 190/3].
        pin(listOf(20.0, 20.0), 10.0, 100.0,
            GridContentDistribution.Justify.SPACE_EVENLY, false,
            listOf(50.0 / 3.0, 190.0 / 3.0))

    @Test
    fun `P16 space-between falls back to start on overflow`() =
        // leftover −20 → §5.3 fallback: start packing, plain gaps.
        pin(listOf(60.0, 60.0), 0.0, 100.0,
            GridContentDistribution.Justify.SPACE_BETWEEN, false, listOf(0.0, 60.0))

    @Test
    fun `P17 space-around falls back to center on overflow`() =
        // leftover −20 → centered overflow (−10 both sides).
        pin(listOf(60.0, 60.0), 0.0, 100.0,
            GridContentDistribution.Justify.SPACE_AROUND, false, listOf(-10.0, 50.0))

    @Test
    fun `P18 gap participates in the footprint under end`() =
        // 10+20+6gap = 36 → leftover 64; second origin 64+10+6.
        pin(listOf(10.0, 20.0), 6.0, 100.0,
            GridContentDistribution.Justify.END, false, listOf(64.0, 80.0))

    @Test
    fun `P19 empty template distributes nothing`() =
        pin(emptyList(), 0.0, 100.0, GridContentDistribution.Justify.END, false, emptyList())

    // ── chained: track sizing feeds distribution (003 end-to-end) ────────

    @Test
    fun `sized 40px track under justify-content end lands at 60`() {
        // The exact 003 sub-grid-1 pipeline: template {"px":40} → sizing →
        // distribution — proves the two pure halves compose.
        val widths = GridRenderer.computeTrackWidths(
            listOf(GridRenderer.TrackSpec.Px(40f)), listOf(0f), 100f, 0f)
        pin(widths.map { it.toDouble() }, 0.0, 100.0,
            GridContentDistribution.Justify.END, false, listOf(60.0))
    }

    // ── implicitColumnCount (RC-B3 phantom-track fix) ────────────────────

    @Test
    fun `template-less row-flow grid has ONE implicit column`() {
        // display-contents-alignment-002 shape: one spliced item, row flow.
        assertEquals(1, GridRenderer.implicitColumnCount(
            GridRenderer.GridAutoFlow.ROW, listOf(GridClaims())))
        // More items still stack in the single column (§8.5 row cursor).
        assertEquals(1, GridRenderer.implicitColumnCount(
            GridRenderer.GridAutoFlow.ROW, List(3) { GridClaims() }))
        // No items at all → still one column, never zero.
        assertEquals(1, GridRenderer.implicitColumnCount(
            GridRenderer.GridAutoFlow.ROW, emptyList()))
    }

    @Test
    fun `column-flow opens one implicit column per item`() {
        // §8.5 column-major cursor: 3 auto items → 3 implicit columns.
        assertEquals(3, GridRenderer.implicitColumnCount(
            GridRenderer.GridAutoFlow.COLUMN, List(3) { GridClaims() }))
        // dense variant flows the same way.
        assertEquals(2, GridRenderer.implicitColumnCount(
            GridRenderer.GridAutoFlow.COLUMN_DENSE, List(2) { GridClaims() }))
    }

    @Test
    fun `explicit numeric column claims grow the implicit grid`() {
        // grid-column-start: 3 → tracks 1..3 must exist (§7.5).
        assertEquals(3, GridRenderer.implicitColumnCount(
            GridRenderer.GridAutoFlow.ROW, listOf(GridClaims(colStart = 3))))
        // grid-column-end: 4 → bounds track 3.
        assertEquals(3, GridRenderer.implicitColumnCount(
            GridRenderer.GridAutoFlow.ROW, listOf(GridClaims(colEnd = 4))))
        // start 2 + span 2 → reaches track 3.
        assertEquals(3, GridRenderer.implicitColumnCount(
            GridRenderer.GridAutoFlow.ROW,
            listOf(GridClaims(colStart = 2, colEndSpan = 2))))
        // bare span 2 with auto lines still needs 2 tracks.
        assertEquals(2, GridRenderer.implicitColumnCount(
            GridRenderer.GridAutoFlow.ROW, listOf(GridClaims(colEndSpan = 2))))
        // negative lines resolve against the explicit grid — ignored here.
        assertEquals(1, GridRenderer.implicitColumnCount(
            GridRenderer.GridAutoFlow.ROW, listOf(GridClaims(colStart = -1))))
    }
}

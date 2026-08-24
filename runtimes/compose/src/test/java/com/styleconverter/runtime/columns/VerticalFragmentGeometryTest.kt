package com.styleconverter.runtime.columns

// Wave-47 lane Z2 — pins the VERTICAL-writing-mode fragment V-table
// (VerticalFragmentGeometry) against the css-break reference geometry. The
// SAME expected values are pinned on the iOS runtime
// (VerticalFragmentGeometryTests), so drift here is a cross-platform
// divergence, not a refactor.
//
// Reference derivations (browser used values, from the WPT sources +
// Chromium refs):
//  * background-image-001 (vertical-rl): .mc content box 120w × 470h,
//    columns:3 gap:10 → colH = (470−20)/3 = 150; child block-size 350 →
//    C=350, W=120 → F=3. rl bands walk leftward: [230,350) [110,230)
//    [0,110), the partial band RIGHT-aligned (ref col3's 10px yellow strip
//    at the left edge).
//  * background-image-002 (vertical-lr): same numbers, lr bands walk
//    rightward: [0,120) [120,240) [240,350), partial band LEFT-aligned.
//  * borders-006 (vertical-lr): container 100w × 320h content, columns:3
//    gap:10 → colH = (320−20)/3 = 100; child 250 + 2×10 borders → C=270 →
//    bands [0,100) [100,200) [200,270) — block-start (left) border in
//    fragment 0, block-end (right) border in fragment 2.
//  * borders-007 (vertical-rl): same numbers, bands [170,270) [70,170)
//    [0,70), partial right-aligned.

import com.styleconverter.runtime.columns.FragmentGeometry.Fragment
import org.junit.Assert.assertEquals
import org.junit.Test

class VerticalFragmentGeometryTest {

    private fun run(c: Int, w: Int, colH: Int, g: Int, n: Int, rtl: Boolean) =
        VerticalFragmentGeometry.fragmentGeometry(
            childBlockSizePx = c, columnBlockSizePx = w,
            columnInlineSizePx = colH, columnGapPx = g,
            columnCount = n, blockRtl = rtl)

    @Test
    fun `V1 - background-image-001 vertical-rl - right-aligned bands walking leftward`() {
        // tx = W − C + i·W = 120−350+120i → −230 / −110 / +10. The +10 on
        // the last fragment right-aligns its 110px band (ref's left strip).
        assertEquals(
            listOf(
                Fragment(0, 0, 0, 120, 150, -230, 0),
                Fragment(1, 0, 160, 120, 150, -110, 160),
                Fragment(2, 0, 320, 120, 150, 10, 320)
            ),
            run(c = 350, w = 120, colH = 150, g = 10, n = 3, rtl = true))
    }

    @Test
    fun `V2 - background-image-002 vertical-lr - left-aligned bands walking rightward`() {
        // tx = −i·W → 0 / −120 / −240; fragment 2 shows [240,350) at x∈[0,110).
        assertEquals(
            listOf(
                Fragment(0, 0, 0, 120, 150, 0, 0),
                Fragment(1, 0, 160, 120, 150, -120, 160),
                Fragment(2, 0, 320, 120, 150, -240, 320)
            ),
            run(c = 350, w = 120, colH = 150, g = 10, n = 3, rtl = false))
    }

    @Test
    fun `V3 - borders-006 vertical-lr - block borders land in first and last fragments`() {
        // C=270 (250 + 2×10 borders painted in the continuous box): band 0
        // includes the left 10px border, band 2 (70 wide) the right one.
        assertEquals(
            listOf(
                Fragment(0, 0, 0, 100, 100, 0, 0),
                Fragment(1, 0, 110, 100, 100, -100, 110),
                Fragment(2, 0, 220, 100, 100, -200, 220)
            ),
            run(c = 270, w = 100, colH = 100, g = 10, n = 3, rtl = false))
    }

    @Test
    fun `V4 - borders-007 vertical-rl - mirrored bands, partial right-aligned`() {
        // tx = 100−270+100i → −170 / −70 / +30: fragment 2's 70px band sits
        // at x∈[30,100) with the block-end (left) border inside it.
        assertEquals(
            listOf(
                Fragment(0, 0, 0, 100, 100, -170, 0),
                Fragment(1, 0, 110, 100, 100, -70, 110),
                Fragment(2, 0, 220, 100, 100, 30, 220)
            ),
            run(c = 270, w = 100, colH = 100, g = 10, n = 3, rtl = true))
    }

    @Test
    fun `V5 - fits case degenerates to a single anchored fragment`() {
        // C ≤ W: one fragment in column 0; rl still right-aligns (tx = W−C).
        assertEquals(
            listOf(Fragment(0, 0, 0, 120, 150, 40, 0)),
            run(c = 80, w = 120, colH = 150, g = 10, n = 3, rtl = true))
        assertEquals(
            listOf(Fragment(0, 0, 0, 120, 150, 0, 0)),
            run(c = 80, w = 120, colH = 150, g = 10, n = 3, rtl = false))
    }

    @Test
    fun `V6 - overflow past N columns is clipped by the fragment cap`() {
        // F = ceil(500/120) = 5 capped at N=3 — the tail never gets a column.
        assertEquals(3, run(c = 500, w = 120, colH = 150, g = 10, n = 3, rtl = false).size)
    }

    @Test
    fun `V7 - column inline size helper matches the §3-4 fit`() {
        assertEquals(150, VerticalFragmentGeometry.columnInlineSizePx(470, 3, 10))
        assertEquals(100, VerticalFragmentGeometry.columnInlineSizePx(320, 3, 10))
        // Degenerate floors: count ≥ 1, result ≥ 0.
        assertEquals(0, VerticalFragmentGeometry.columnInlineSizePx(5, 3, 10))
    }
}

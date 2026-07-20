package com.styleconverter.runtime.columns

// JUnit4 plumbing — matches the suite style used by MultiColumnUsedValuesTest.
import org.junit.Assert.assertEquals
import org.junit.Test

// Local alias so the D-table rows below read as compactly as the Swift twin.
private typealias Slot = MultiColumnDistribution.Slot

/**
 * The SHARED distribution pin table (lane ios-multichild-multicol) —
 * pins [MultiColumnDistribution.distribute]'s greedy multi-child column
 * assignment exactly as the pre-extraction inline loop computed it:
 * child order, min-height column choice, first-index tie-break, flush
 * (gapless) stacking, tallest-column container height.
 *
 * D1–D7 are MIRRORED byte-for-byte by the iOS runtime's
 * MulticolDistributionTests.swift (runtimes/swiftui) — the native-pair
 * parity gate is "match ANDROID's greedy heuristic", NOT css-multicol-1
 * §7.1's true balancing, so any change here must land on both platforms
 * in the same commit or the cross-platform SSIM gate will catch the
 * drift on the next multi-child multicol fixture.
 */
class MultiColumnDistributionTest {

    /** D1 — the raster-pin scenario: 3 equal children across 2 columns. */
    @Test
    fun `D1 three equal children in two columns - third child ties back to column 0`() {
        // Greedy trace: c0→col0@0, c1→col1@0 (col1 is now the min), then
        // c2 sees a 40/40 tie and the FIRST minimal index (col0) wins.
        assertEquals(
            listOf(Slot(0, 0), Slot(1, 0), Slot(0, 40)),
            MultiColumnDistribution.distribute(listOf(40, 40, 40), 2)
        )
    }

    /** D2 — mixed heights across 3 columns: the 4th child returns to the shortest. */
    @Test
    fun `D2 mixed heights in three columns - fourth child lands on the shortest column`() {
        // After c0..c2 fill cols 0..2 (heights 10/30/20), col0 is shortest
        // — c3 stacks there at y=10, not in a "next" round-robin column.
        assertEquals(
            listOf(Slot(0, 0), Slot(1, 0), Slot(2, 0), Slot(0, 10)),
            MultiColumnDistribution.distribute(listOf(10, 30, 20, 40), 3)
        )
    }

    /** D3 — the tie-break rule is part of the contract: lowest index wins. */
    @Test
    fun `D3 all-equal children alternate columns via the first-index tie-break`() {
        // Every pick is a tie; minByOrNull's first-minimum answer produces
        // strict alternation 0,1,0,1 with flush stacking per column.
        assertEquals(
            listOf(Slot(0, 0), Slot(1, 0), Slot(0, 20), Slot(1, 20)),
            MultiColumnDistribution.distribute(listOf(20, 20, 20, 20), 2)
        )
    }

    /** D4 — greedy is order-dependent, NOT optimal balancing (the parity point). */
    @Test
    fun `D4 greedy heuristic accepts a 70-tall result where true balancing gives 60`() {
        // 50,10,10,50 in 2 columns: greedy stacks 10+10+50 in col1 (70 tall);
        // spec-true balancing would pair 50+10 twice (60). We pin GREEDY —
        // Android parity is the gate, and iOS mirrors this exact answer.
        val slots = MultiColumnDistribution.distribute(listOf(50, 10, 10, 50), 2)
        assertEquals(listOf(Slot(0, 0), Slot(1, 0), Slot(1, 10), Slot(1, 20)), slots)
        // The container height is the greedy 70, not the balanced 60.
        assertEquals(70, MultiColumnDistribution.containerBlockSizePx(listOf(50, 10, 10, 50), slots))
    }

    /** D5 — fewer children than columns: trailing columns stay empty. */
    @Test
    fun `D5 two children in three columns leave the third column empty`() {
        // c0→col0, c1→col1; col2 never receives a child and contributes 0
        // to the container height (35 = the taller occupied column).
        val slots = MultiColumnDistribution.distribute(listOf(25, 35), 3)
        assertEquals(listOf(Slot(0, 0), Slot(1, 0)), slots)
        assertEquals(35, MultiColumnDistribution.containerBlockSizePx(listOf(25, 35), slots))
    }

    /** D6 — the single-child degenerate case stacks in column 0. */
    @Test
    fun `D6 a single child lands in column 0 and sets the container height`() {
        // One child, two columns: col0 at y=0; height = the child's own 100.
        val slots = MultiColumnDistribution.distribute(listOf(100), 2)
        assertEquals(listOf(Slot(0, 0)), slots)
        assertEquals(100, MultiColumnDistribution.containerBlockSizePx(listOf(100), slots))
    }

    /** D7 — defensive coercion: a degenerate count distributes as one column. */
    @Test
    fun `D7 a sub-1 column count is coerced to a single flush stack`() {
        // count=0 is impossible from resolveUsedColumns (>= 1 guaranteed)
        // but the pure function floors it — children stack flush in col0.
        assertEquals(
            listOf(Slot(0, 0), Slot(0, 10)),
            MultiColumnDistribution.distribute(listOf(10, 20), 0)
        )
    }

    /** Empty input — no children means no slots and a 0-height container. */
    @Test
    fun `empty child list yields no slots and zero container height`() {
        // Matches the old layout's `columnHeights.maxOrNull() ?: 0` = 0 path.
        val slots = MultiColumnDistribution.distribute(emptyList(), 3)
        assertEquals(emptyList<Slot>(), slots)
        assertEquals(0, MultiColumnDistribution.containerBlockSizePx(emptyList(), slots))
    }
}

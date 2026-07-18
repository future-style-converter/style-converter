package com.styleconverter.runtime.columns

// JUnit4 plumbing — matches the suite style used by ColumnsRegistryTest.
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the css-multicol §3.4 used-value fitting in
 * [MultiColumnApplier.resolveUsedColumns] — the guard that fixed the Android wedge
 * on WPT 3d-rendering-context-and-z-ordering-003.
 *
 * Triggering values are pinned to LIVE converter output for
 * fixtures/wpt/css-transforms/3d-rendering-context-and-z-ordering-003.json:
 * the #cube component emits {"type":"ColumnCount","data":5.0} alongside
 * {"type":"Width","data":{"type":"length","px":15.0}} (verified against
 * `./gradlew :converter:run … --to ir` on 2026-07-18). With the applier's 16.dp
 * default gap the old formula computed (15 - 4*16) / 5 = -9 and passed a negative
 * maxWidth constraint into child measurement, wedging the layout.
 */
class MultiColumnUsedValuesTest {

    @Test
    fun `wpt z-ordering-003 cube - 15px container with count 5 and default 16px gap collapses to one full-width column`() {
        // Exact wedge inputs: available = 15px (fixture width), requested = 5
        // (fixture ColumnCount data 5.0), gap = 16px (applier default 16.dp).
        val used = MultiColumnApplier.resolveUsedColumns(
            availableWidthPx = 15, requestedCount = 5, gapPx = 16
        )
        // Gaps alone (4 * 16 = 64) exceed the container — fit exactly one column…
        assertEquals(1, used.count)
        // …which fills the whole 15px of available width (no negative constraint).
        assertEquals(15, used.widthPx)
    }

    @Test
    fun `available width exactly equal to total gap keeps the count with zero-width columns`() {
        // Boundary: available == (count-1)*gap = 64 — all 5 columns' gaps still fit…
        val used = MultiColumnApplier.resolveUsedColumns(
            availableWidthPx = 64, requestedCount = 5, gapPx = 16
        )
        assertEquals(5, used.count)
        // …leaving exactly zero px per column: floored at 0, never negative.
        assertEquals(0, used.widthPx)
    }

    @Test
    fun `zero available width degrades to a single zero-width column`() {
        // Boundary: nothing to lay out at all — still one (empty) column per multicol.
        val used = MultiColumnApplier.resolveUsedColumns(
            availableWidthPx = 0, requestedCount = 5, gapPx = 16
        )
        assertEquals(1, used.count)
        assertEquals(0, used.widthPx)
    }

    @Test
    fun `huge requested count is reduced to what the gaps allow`() {
        // Boundary: absurd count — fitting caps at available/gap + 1 = 100/16 + 1 = 7.
        val used = MultiColumnApplier.resolveUsedColumns(
            availableWidthPx = 100, requestedCount = 1_000_000, gapPx = 16
        )
        assertEquals(7, used.count)
        // Remaining 100 - 6*16 = 4px split over 7 columns floors to 0 — non-negative.
        assertEquals(0, used.widthPx)
    }

    @Test
    fun `zero gap keeps the requested count without dividing by zero`() {
        // gap == 0 short-circuits the fitting division (every column trivially fits).
        val used = MultiColumnApplier.resolveUsedColumns(
            availableWidthPx = 100, requestedCount = 4, gapPx = 0
        )
        assertEquals(4, used.count)
        assertEquals(25, used.widthPx)
    }

    @Test
    fun `comfortable container is unchanged by the guard`() {
        // Regression pin: the happy path (300px, 3 cols, 16px gaps) must match the
        // pre-fix arithmetic exactly — (300 - 32) / 3 = 89 (integer division).
        val used = MultiColumnApplier.resolveUsedColumns(
            availableWidthPx = 300, requestedCount = 3, gapPx = 16
        )
        assertEquals(3, used.count)
        assertEquals(89, used.widthPx)
    }

    @Test
    fun `negative available width and invalid count or gap are floored defensively`() {
        // Negative constraint, zero count, negative gap — every input floored sanely.
        val used = MultiColumnApplier.resolveUsedColumns(
            availableWidthPx = -5, requestedCount = 0, gapPx = -3
        )
        assertEquals(1, used.count)
        assertEquals(0, used.widthPx)
    }

    @Test
    fun `near-infinite available width does not overflow`() {
        // Compose's Constraints.Infinity is Int.MAX_VALUE — the fitting math must not
        // overflow (MAX/16 + 1 and (5-1)*16 both stay comfortably in Int range).
        val used = MultiColumnApplier.resolveUsedColumns(
            availableWidthPx = Int.MAX_VALUE, requestedCount = 5, gapPx = 16
        )
        assertEquals(5, used.count)
        // Width is huge but positive — no wrap-around to negative.
        assertTrue("width must stay positive, was ${used.widthPx}", used.widthPx > 0)
        assertEquals((Int.MAX_VALUE - 64) / 5, used.widthPx)
    }

    @Test
    fun `config with malformed non-positive column-count is coerced to one`() {
        // getEffectiveColumnCount feeds SimpleColumnGrid's rowCount divider — a
        // malformed count of 0 previously produced a division by zero there.
        assertEquals(1, MultiColumnConfig(columnCount = 0).getEffectiveColumnCount(100.dp))
        assertEquals(1, MultiColumnConfig(columnCount = -3).getEffectiveColumnCount(100.dp))
        // Valid counts pass through untouched (fixture value 5).
        assertEquals(5, MultiColumnConfig(columnCount = 5).getEffectiveColumnCount(15.dp))
    }

    @Test
    fun `auto count with negative gap does not zero the divisor`() {
        // column-width:auto branch: gap floored at 0 so (width + gap) can't hit 0.
        val config = MultiColumnConfig(columnWidth = 50.dp, columnGap = (-50).dp)
        // 200 / 50 = 4 columns with the invalid gap treated as 0.
        assertEquals(4, config.getEffectiveColumnCount(200.dp))
    }
}

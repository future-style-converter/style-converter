package com.styleconverter.runtime.columns

// JUnit4 plumbing — matches the suite style used by FragmentGeometryTest.
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins line reconstruction ([GapDecorationLines]) and the interval algebra
 * ([GapIntervals]) the segment builders sit on, plus the column-direction
 * axis mapping that no WPT flex ref in this section exercises.
 */
class GapDecorationLinesTest {

    private fun rect(l: Float, t: Float, r: Float, b: Float) = GapRect(l, t, r, b)

    @Test
    fun `cross disjointness splits a wrapped row into lines`() {
        // WPT 003 layout: three lines at cross [2,52] / [62,112] / [122,172].
        val items = listOf(
            rect(2f, 2f, 52f, 52f), rect(62f, 2f, 112f, 52f), rect(122f, 2f, 172f, 52f),
            rect(2f, 62f, 102f, 112f), rect(112f, 62f, 162f, 112f),
            rect(2f, 122f, 52f, 172f)
        )
        val lines = GapDecorationLines.toLines(
            GapDecorationLines.groupIntoLines(items, mainHorizontal = true), mainHorizontal = true
        )
        assertEquals(3, lines.size)
        assertEquals(GapInterval(2f, 52f), lines[0].cross)
        assertEquals(listOf(GapInterval(52f, 62f), GapInterval(112f, 122f)), lines[0].mainGaps)
        // Line 2's single gap sits at [102,112] because item 4 is 100px wide.
        assertEquals(listOf(GapInterval(102f, 112f)), lines[1].mainGaps)
    }

    @Test
    fun `a main-axis backtrack also starts a new line`() {
        // Two lines whose cross bands OVERLAP (zero row gap, so the cross
        // test alone would merge them); the x restart is the wrap signal.
        val items = listOf(
            rect(0f, 0f, 50f, 50f), rect(60f, 0f, 110f, 50f),
            rect(0f, 50f, 50f, 100f), rect(60f, 50f, 110f, 100f)
        )
        val grouped = GapDecorationLines.groupIntoLines(items, mainHorizontal = true)
        assertEquals(2, grouped.size)
        assertEquals(2, grouped[0].size)
    }

    @Test
    fun `lines come back in cross order regardless of placement order`() {
        // `flex-wrap: wrap-reverse` places the FIRST line lowest; without
        // the cross sort the inter-line gap would come out negative and
        // every row rule would be dropped.
        val items = listOf(
            rect(0f, 60f, 50f, 110f), rect(60f, 60f, 110f, 110f),
            rect(0f, 0f, 50f, 50f), rect(60f, 0f, 110f, 50f)
        )
        val lines = GapDecorationLines.toLines(
            GapDecorationLines.groupIntoLines(items, mainHorizontal = true), mainHorizontal = true
        )
        assertEquals(GapInterval(0f, 50f), lines[0].cross)
        assertEquals(listOf(GapInterval(50f, 60f)), GapDecorationLines.betweenLineGaps(lines))
    }

    @Test
    fun `touching cut intervals merge into one hole`() {
        // The WPT 009 case: [102,112] and [112,122] must not leave a sliver.
        assertEquals(
            listOf(GapInterval(52f, 62f), GapInterval(102f, 122f)),
            GapIntervals.union(
                listOf(GapInterval(52f, 62f), GapInterval(112f, 122f), GapInterval(102f, 112f))
            )
        )
        assertEquals(
            listOf(GapInterval(2f, 52f), GapInterval(62f, 102f), GapInterval(122f, 172f)),
            GapIntervals.subtract(
                GapInterval(2f, 172f),
                listOf(GapInterval(52f, 62f), GapInterval(112f, 122f), GapInterval(102f, 112f))
            )
        )
    }

    @Test
    fun `an inset larger than the segment drops it instead of inverting it`() {
        assertEquals(null, GapIntervals.inset(GapInterval(0f, 10f), 5f))
        assertEquals(GapInterval(1f, 9f), GapIntervals.inset(GapInterval(0f, 10f), 1f))
    }

    /**
     * `flex-direction: column` swaps which physical family owns which gap:
     * the item-to-item gaps become VERTICAL (row rules) and the inter-line
     * gaps become HORIZONTAL (column rules). No WPT flex ref in the
     * css-gaps section pins this — it is derived from the physical axis
     * definition in css-gap-decorations-1 §2 and asserted here so the
     * mapping cannot silently invert.
     */
    @Test
    fun `column-direction flex swaps the two rule families`() {
        val items = listOf(
            rect(0f, 0f, 100f, 50f), rect(0f, 60f, 100f, 110f), rect(0f, 120f, 100f, 170f),
            rect(110f, 0f, 210f, 50f), rect(110f, 60f, 210f, 110f)
        )
        val segments = GapDecorationSegments.build(
            GapDecorationConfig(
                column = GapRuleSpec(style = ColumnRuleStyle.SOLID, widthPx = 10f),
                row = GapRuleSpec(style = ColumnRuleStyle.SOLID, widthPx = 10f)
            ),
            items, rect(0f, 0f, 210f, 200f), mainHorizontal = false
        )
        // Row rules: horizontal bars in each column's item-to-item gaps.
        assertEquals(
            listOf(
                rect(0f, 50f, 100f, 60f), rect(0f, 110f, 100f, 120f),
                rect(110f, 50f, 210f, 60f)
            ),
            segments.filter { it.axis == GapAxis.ROW }.map { it.rect }
        )
        // Column rule: one vertical bar between the two columns, spanning
        // the content box's block extent.
        assertEquals(
            listOf(rect(100f, 0f, 110f, 200f)),
            segments.filter { it.axis == GapAxis.COLUMN }.map { it.rect }
        )
    }
}

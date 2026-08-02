package com.styleconverter.runtime.columns

// JUnit4 plumbing — matches the suite style used by FragmentGeometryTest.
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The four WPT css-gaps/flex layouts that pin something the 003 family
 * cannot: line-vs-item cross extent (007), painting outside the container
 * (008), the cut EXTENT being the gap rather than the rule (034), and
 * unequal per-line gaps from `justify-content: space-between` (050).
 *
 * Every expected rectangle is re-derived from the matching
 * fixtures/wpt/css-gaps/flex__flex-gap-decorations-NNN__ref.json.
 */
class GapDecorationWptPinsTest {

    private fun rect(l: Float, t: Float, r: Float, b: Float) = GapRect(l, t, r, b)

    private fun solid(w: Float, brk: GapRuleBreak = GapRuleBreak.NORMAL) =
        GapRuleSpec(style = ColumnRuleStyle.SOLID, widthPx = w, breakMode = brk)

    private fun build(config: GapDecorationConfig, items: List<GapRect>, box: GapRect) =
        GapDecorationSegments.build(config, items, box, mainHorizontal = true)

    private fun of(segments: List<GapSegment>, axis: GapAxis) =
        segments.filter { it.axis == axis }.map { it.rect }

    /**
     * WPT 007 — `align-items: flex-end`, one 40px item and two 18px text
     * items. Measured in a fresh Chromium (repo launch-flag contract):
     * items at [2,24 50×18], [62,24 50×18], [122,2 50×40]; ref rules at
     * [52,2 10×40] and [112,2 10×40]. So the rules take the LINE's cross
     * extent [2,42], not their immediate neighbours' [24,42].
     */
    @Test
    fun `007 column rules span the line not the neighbouring item`() {
        val items = listOf(
            rect(2f, 24f, 52f, 42f), rect(62f, 24f, 112f, 42f), rect(122f, 2f, 172f, 42f)
        )
        val segments = build(
            GapDecorationConfig(column = solid(10f)), items, rect(2f, 2f, 172f, 42f)
        )
        assertEquals(
            listOf(rect(52f, 2f, 62f, 42f), rect(112f, 2f, 122f, 42f)),
            of(segments, GapAxis.COLUMN)
        )
        // Single line → no inter-line gap → no row rules, even if declared.
        assertEquals(emptyList<GapRect>(), of(segments, GapAxis.ROW))
    }

    /**
     * WPT 008 — `flex-wrap: nowrap`, six 50px items with `flex-shrink: 0`
     * in a 200px content box: the line overflows and the five rules are
     * painted OUTSIDE the container. Ref: five 10×50 boxes at
     * left 52 / 112 / 172 / 232 / 292.
     */
    @Test
    fun `008 rules paint in overflowing gaps outside the container box`() {
        val items = (0 until 6).map { i ->
            val x = 2f + i * 60f
            rect(x, 2f, x + 50f, 52f)
        }
        val segments = build(
            GapDecorationConfig(column = solid(10f)), items, rect(2f, 2f, 202f, 52f)
        )
        assertEquals(
            listOf(52f, 112f, 172f, 232f, 292f).map { rect(it, 2f, it + 10f, 52f) },
            of(segments, GapAxis.COLUMN)
        )
    }

    /**
     * WPT 034 — the decisive `intersection` pin: 5px rules in 90px gaps.
     * The ref cuts the row rule by the FULL 90px column gaps (segments
     * [0,60] [150,230] [330,390] [480,600]), so the cut extent is the
     * crossing GAP, not the crossing rule's 5px width.
     */
    @Test
    fun `034 intersection cuts by the crossing gap not the crossing rule width`() {
        val items = listOf(
            rect(0f, 0f, 60f, 100f), rect(150f, 0f, 230f, 100f),
            rect(320f, 0f, 390f, 100f), rect(480f, 0f, 600f, 100f),
            rect(0f, 190f, 240f, 290f), rect(330f, 190f, 600f, 290f)
        )
        val segments = build(
            GapDecorationConfig(
                column = solid(5f, GapRuleBreak.INTERSECTION),
                row = solid(5f, GapRuleBreak.INTERSECTION)
            ),
            items, rect(0f, 0f, 600f, 290f)
        )
        // Column rules: centred 5px bands in the 90px gaps.
        assertEquals(
            listOf(
                rect(102.5f, 0f, 107.5f, 100f), rect(272.5f, 0f, 277.5f, 100f),
                rect(432.5f, 0f, 437.5f, 100f), rect(282.5f, 190f, 287.5f, 290f)
            ),
            of(segments, GapAxis.COLUMN)
        )
        // Row rule: band [142.5,147.5]; the [230,320] and [240,330] cuts
        // overlap and must merge into one [230,330] hole.
        assertEquals(
            listOf(
                rect(0f, 142.5f, 60f, 147.5f), rect(150f, 142.5f, 230f, 147.5f),
                rect(330f, 142.5f, 390f, 147.5f), rect(480f, 142.5f, 600f, 147.5f)
            ),
            of(segments, GapAxis.ROW)
        )
    }

    /**
     * WPT 050 — `justify-content: space-between` with no declared
     * column-gap: line 1 distributes 12.5px between five items, line 2
     * distributes 40px between four. The row rule (break: intersection)
     * is cut by BOTH sets, yielding six segments in the ref.
     */
    @Test
    fun `050 unequal per-line distributed gaps cut the row rule six ways`() {
        val line1 = listOf(2f, 84.5f, 167f, 249.5f, 332f).map { rect(it, 2f, it + 70f, 52f) }
        val line2 = listOf(2f, 112f, 222f, 332f).map { rect(it, 62f, it + 70f, 112f) }
        val segments = build(
            GapDecorationConfig(
                column = solid(5f),
                row = solid(5f, GapRuleBreak.INTERSECTION)
            ),
            line1 + line2, rect(2f, 2f, 402f, 112f)
        )
        // Seven column rules, centred in the distributed gaps — ref lefts
        // 75.75 / 158.25 / 240.75 / 323.25 (line 1) and 89.5 / 199.5 /
        // 309.5 (line 2).
        assertEquals(
            listOf(75.75f, 158.25f, 240.75f, 323.25f).map { rect(it, 2f, it + 5f, 52f) } +
                listOf(89.5f, 199.5f, 309.5f).map { rect(it, 62f, it + 5f, 112f) },
            of(segments, GapAxis.COLUMN)
        )
        // Row rule band [54.5,59.5]; ref widths 70 / 42.5 / 15 / 15 / 42.5 / 70.
        assertEquals(
            listOf(
                rect(2f, 54.5f, 72f, 59.5f), rect(112f, 54.5f, 154.5f, 59.5f),
                rect(167f, 54.5f, 182f, 59.5f), rect(222f, 54.5f, 237f, 59.5f),
                rect(249.5f, 54.5f, 292f, 59.5f), rect(332f, 54.5f, 402f, 59.5f)
            ),
            of(segments, GapAxis.ROW)
        )
    }
}

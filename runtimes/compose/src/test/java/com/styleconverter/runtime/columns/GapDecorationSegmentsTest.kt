package com.styleconverter.runtime.columns

// JUnit4 plumbing — matches the suite style used by FragmentGeometryTest.
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the css-gap-decorations-1 flex segment model against FRESH WPT
 * reference geometry.
 *
 * Source of truth: the WPT REFERENCE documents for css-gaps/flex, which
 * express the browser's own answer as absolutely-positioned boxes —
 * fixtures/wpt/css-gaps/flex__flex-gap-decorations-NNN__ref.json. Numbers
 * below are re-derived from those refs, not copied from a stale capture.
 *
 * Shared "003-family" layout used by tests 003/004/005/009/010/011/012/013:
 *   container border 2px, content box (2,2)-(172,172), column-gap 10,
 *   row-gap 10, flex-wrap wrap, item heights 50, widths 50/50/50/100/50/
 *   50/50/50 →
 *     line 1 x [2,52] [62,112] [122,172]   cross [2,52]
 *     line 2 x [2,102] [112,162]           cross [62,112]
 *     line 3 x [2,52] [62,112] [122,172]   cross [122,172]
 */
class GapDecorationSegmentsTest {

    // ── shared fixture ─────────────────────────────────────────────────
    private fun rect(l: Float, t: Float, r: Float, b: Float) = GapRect(l, t, r, b)

    /** The eight placed item rects of the 003-family layout, in DOM order. */
    private val family003 = listOf(
        rect(2f, 2f, 52f, 52f), rect(62f, 2f, 112f, 52f), rect(122f, 2f, 172f, 52f),
        rect(2f, 62f, 102f, 112f), rect(112f, 62f, 162f, 112f),
        rect(2f, 122f, 52f, 172f), rect(62f, 122f, 112f, 172f), rect(122f, 122f, 172f, 172f)
    )

    /** The container's content box for that layout. */
    private val box003 = rect(2f, 2f, 172f, 172f)

    /** A painting spec: solid, [w]px wide, black — colour is irrelevant here. */
    private fun solid(w: Float, brk: GapRuleBreak = GapRuleBreak.NORMAL, inset: Float = 0f) =
        GapRuleSpec(style = ColumnRuleStyle.SOLID, widthPx = w, breakMode = brk, insetPx = inset)

    private fun build(config: GapDecorationConfig, items: List<GapRect> = family003, box: GapRect = box003) =
        GapDecorationSegments.build(config, items, box, mainHorizontal = true)

    private fun of(segments: List<GapSegment>, axis: GapAxis) =
        segments.filter { it.axis == axis }.map { it.rect }

    // ── WPT 003: solid 10px both families, no break, no inset ──────────
    @Test
    fun `003 solid rules span the line cross extent and the content box`() {
        val segments = build(GapDecorationConfig(column = solid(10f), row = solid(10f)))
        // (b) one column segment per adjacent item pair per line; the rule
        // is 10px in a 10px gap, so the band IS the gap.
        assertEquals(
            listOf(
                rect(52f, 2f, 62f, 52f), rect(112f, 2f, 122f, 52f),
                rect(102f, 62f, 112f, 112f),
                rect(52f, 122f, 62f, 172f), rect(112f, 122f, 122f, 172f)
            ),
            of(segments, GapAxis.COLUMN)
        )
        // (c) row rules span the full content box; the ref draws them as
        // 170px-wide bars at content-box left (ref__4 / ref__5).
        assertEquals(
            listOf(rect(2f, 52f, 172f, 62f), rect(2f, 112f, 172f, 122f)),
            of(segments, GapAxis.ROW)
        )
        // (f) rule-overlap initial ROW_OVER_COLUMN → columns paint first.
        assertEquals(GapAxis.COLUMN, segments.first().axis)
        assertEquals(GapAxis.ROW, segments.last().axis)
    }

    // ── WPT 009: BOTH families intersection, insets 0 ──────────────────
    @Test
    fun `009 row rules break at the union of both adjacent lines column gaps`() {
        val segments = build(
            GapDecorationConfig(
                column = solid(10f, GapRuleBreak.INTERSECTION),
                row = solid(10f, GapRuleBreak.INTERSECTION)
            )
        )
        // Cuts for BOTH row gaps are {[52,62],[112,122]} ∪ {[102,112]} =
        // {[52,62],[102,122]} — the touching pair must merge, otherwise a
        // spurious 10px sliver survives between 112 and 122.
        val expected = listOf(
            rect(2f, 52f, 52f, 62f), rect(62f, 52f, 102f, 62f), rect(122f, 52f, 172f, 62f),
            rect(2f, 112f, 52f, 122f), rect(62f, 112f, 102f, 122f), rect(122f, 112f, 172f, 122f)
        )
        assertEquals(expected, of(segments, GapAxis.ROW))
    }

    // ── WPT 010 / 012: intersection never shortens a COLUMN rule in flex
    @Test
    fun `column rule intersection is a proven no-op in flex`() {
        val normal = build(GapDecorationConfig(column = solid(10f), row = solid(10f)))
        val cut = build(
            GapDecorationConfig(column = solid(10f, GapRuleBreak.INTERSECTION), row = solid(10f))
        )
        // A line's cross extent lies strictly between its neighbouring
        // inter-line gaps, so there is nothing for the cut operator to
        // remove. Pinned by WPT 012, where `column-over-row` makes the
        // un-cut extent directly observable ([2,52] and [122,172]).
        assertEquals(of(normal, GapAxis.COLUMN), of(cut, GapAxis.COLUMN))
    }

    // ── SPANNING_ITEM has no flex meaning and must equal NORMAL ─────────
    @Test
    fun `spanning-item break equals normal in flex`() {
        val a = build(GapDecorationConfig(column = solid(10f), row = solid(10f)))
        val b = build(
            GapDecorationConfig(
                column = solid(10f, GapRuleBreak.SPANNING_ITEM),
                row = solid(10f, GapRuleBreak.SPANNING_ITEM)
            )
        )
        assertEquals(a, b)
    }

    // ── WPT 011: negative inset lengthens, and escapes the content box ──
    @Test
    fun `011 negative column inset extends past the content box`() {
        val segments = build(
            GapDecorationConfig(
                column = solid(2f, GapRuleBreak.INTERSECTION, inset = -2f),
                row = solid(2f)
            )
        )
        // 2px rules centre inside the 10px gaps: [52,62] → [56,58].
        assertEquals(
            listOf(
                rect(56f, 0f, 58f, 54f), rect(116f, 0f, 118f, 54f),
                rect(106f, 60f, 108f, 114f),
                rect(56f, 120f, 58f, 174f), rect(116f, 120f, 118f, 174f)
            ),
            of(segments, GapAxis.COLUMN)
        )
        // The row rules stay 2px bars centred in their gaps ([56,58]).
        assertEquals(
            listOf(rect(2f, 56f, 172f, 58f), rect(2f, 116f, 172f, 118f)),
            of(segments, GapAxis.ROW)
        )
    }

    // ── WPT 013: positive inset shortens both ends ─────────────────────
    @Test
    fun `013 positive column inset shortens both ends`() {
        val segments = build(
            GapDecorationConfig(
                column = solid(2f, GapRuleBreak.INTERSECTION, inset = 2f),
                row = solid(2f)
            )
        )
        assertEquals(
            listOf(
                rect(56f, 4f, 58f, 50f), rect(116f, 4f, 118f, 50f),
                rect(106f, 64f, 108f, 110f),
                rect(56f, 124f, 58f, 170f), rect(116f, 124f, 118f, 170f)
            ),
            of(segments, GapAxis.COLUMN)
        )
    }

    // ── WPT 012: rule-overlap flips paint order only ───────────────────
    @Test
    fun `012 column-over-row reverses paint order without moving anything`() {
        val base = GapDecorationConfig(column = solid(2f), row = solid(2f))
        val flipped = base.copy(overlap = GapRuleOverlap.COLUMN_OVER_ROW)
        val a = build(base)
        val b = build(flipped)
        // Same rectangles per family…
        assertEquals(of(a, GapAxis.COLUMN), of(b, GapAxis.COLUMN))
        assertEquals(of(a, GapAxis.ROW), of(b, GapAxis.ROW))
        // …but rows now paint FIRST so the columns land on top.
        assertEquals(GapAxis.ROW, b.first().axis)
        assertEquals(GapAxis.COLUMN, b.last().axis)
    }

    // ── the gate: nothing paints without a painting rule ───────────────
    @Test
    fun `inert config paints nothing`() {
        assertEquals(emptyList<GapSegment>(), build(GapDecorationConfig.Inert))
        // A declared style with a zero width is inert too (used width 0).
        assertEquals(
            emptyList<GapSegment>(),
            build(GapDecorationConfig(column = solid(0f)))
        )
    }
}

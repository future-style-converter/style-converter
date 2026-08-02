package com.styleconverter.runtime.columns

// Wave-24 adversarial skeptic probe (lane GAPS-A). Independent of the
// lane's own pins: every expected rectangle here was measured by the
// skeptic in a FRESH headless Chromium (repo BROWSER_LAUNCH_ARGS,
// deviceScaleFactor 1) over tools/wpt/css/css-gaps/flex, and every IR
// value shape was copied verbatim out of a LIVE `:converter:run`
// conversion of fixtures/wpt/css-gaps/flex__flex-gap-decorations-NNN.json.
//
// The point of the file is the edges the lane did NOT execute:
//   * the LIVE inset wire shape {"px": -2.0} (the lane's own extractor
//     test uses {"type":"length","px":-2.0}, which the converter does not
//     emit for *-rule-inset);
//   * the whole live-config → segments path for 011 / 012 / 003;
//   * a column-direction (mainHorizontal = false) transpose;
//   * reversed document order (row-reverse placement).

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class SkepticGapDecorationProbeTest {

    private fun r(l: Float, t: Float, rr: Float, b: Float) = GapRect(l, t, rr, b)

    /** Live wire for *-rule-width / gap lengths: {"type":"length","px":N}. */
    private fun length(px: Double): JsonElement = buildJsonObject {
        put("type", "length"); put("px", px)
    }

    /** LIVE wire for *-rule-inset — bare {"px": N}, NO "type" key. */
    private fun barePx(px: Double): JsonElement = buildJsonObject { put("px", px) }

    private fun srgb(rr: Double, g: Double, b: Double): JsonElement = buildJsonObject {
        put("srgb", buildJsonObject { put("r", rr); put("g", g); put("b", b) })
    }

    private fun of(segs: List<GapSegment>, axis: GapAxis) =
        segs.filter { it.axis == axis }.map { it.rect }

    /** The shared 003-family placed item rects, measured fresh in Chromium. */
    private val family003 = listOf(
        r(2f, 2f, 52f, 52f), r(62f, 2f, 112f, 52f), r(122f, 2f, 172f, 52f),
        r(2f, 62f, 102f, 112f), r(112f, 62f, 162f, 112f),
        r(2f, 122f, 52f, 172f), r(62f, 122f, 112f, 172f), r(122f, 122f, 172f, 172f)
    )
    private val box003 = r(2f, 2f, 172f, 172f)

    // ── 011: live config incl. the bare-px negative inset ────────────────
    @Test
    fun `011 live IR shapes drive the negative-inset geometry`() {
        val config = GapDecorationExtractor.extract(
            listOf(
                "RowRuleStyle" to JsonPrimitive("SOLID"),
                "RowRuleColor" to srgb(0.0, 0.0, 1.0),
                "RowRuleWidth" to length(2.0),
                "ColumnRuleStyle" to JsonPrimitive("SOLID"),
                "ColumnRuleColor" to srgb(1.0, 0.0, 0.0),
                "ColumnRuleWidth" to length(2.0),
                "ColumnRuleBreak" to JsonPrimitive("INTERSECTION"),
                "ColumnRuleInset" to barePx(-2.0)
            )
        )
        // The bare-px shape MUST decode, or the whole inset pin is fiction.
        assertEquals(-2f, config.column.insetPx, 0f)
        assertEquals(GapRuleBreak.INTERSECTION, config.column.breakMode)
        assertEquals(GapRuleBreak.NORMAL, config.row.breakMode)

        val segs = GapDecorationSegments.build(config, family003, box003, true)
        // Fresh Chromium ref: c1 [56,0 2x54] / [116,0 2x54],
        // columns3 [106,60 2x54], c2 [56,120 2x54] / [116,120 2x54].
        assertEquals(
            listOf(
                r(56f, 0f, 58f, 54f), r(116f, 0f, 118f, 54f),
                r(106f, 60f, 108f, 114f),
                r(56f, 120f, 58f, 174f), r(116f, 120f, 118f, 174f)
            ),
            of(segs, GapAxis.COLUMN)
        )
        // Fresh Chromium ref: row1 [2,56 170x2], row2 [2,116 170x2].
        assertEquals(
            listOf(r(2f, 56f, 172f, 58f), r(2f, 116f, 172f, 118f)),
            of(segs, GapAxis.ROW)
        )
        // ROW_OVER_COLUMN is the initial → columns painted first.
        assertEquals(GapAxis.COLUMN, segs.first().axis)
        assertEquals(GapAxis.ROW, segs.last().axis)
    }

    // ── 012: rule-overlap flips paint order only ─────────────────────────
    @Test
    fun `012 column-over-row flips order and keeps the line-exact extents`() {
        val config = GapDecorationExtractor.extract(
            listOf(
                "RowRuleStyle" to JsonPrimitive("SOLID"),
                "RowRuleWidth" to length(2.0),
                "RowRuleColor" to srgb(0.0, 0.0, 1.0),
                "ColumnRuleStyle" to JsonPrimitive("SOLID"),
                "ColumnRuleWidth" to length(2.0),
                "ColumnRuleColor" to srgb(1.0, 0.0, 0.0),
                "RuleOverlap" to JsonPrimitive("COLUMN_OVER_ROW")
            )
        )
        val segs = GapDecorationSegments.build(config, family003, box003, true)
        // Fresh ref: columns are EXACTLY the line cross ranges — they do
        // not run into the row gaps.
        assertEquals(
            listOf(
                r(56f, 2f, 58f, 52f), r(116f, 2f, 118f, 52f),
                r(106f, 62f, 108f, 112f),
                r(56f, 122f, 58f, 172f), r(116f, 122f, 118f, 172f)
            ),
            of(segs, GapAxis.COLUMN)
        )
        assertEquals(
            listOf(r(2f, 56f, 172f, 58f), r(2f, 116f, 172f, 118f)),
            of(segs, GapAxis.ROW)
        )
        assertEquals(GapAxis.ROW, segs.first().axis)
        assertEquals(GapAxis.COLUMN, segs.last().axis)
    }

    // ── 003: the model's column rules stop at the line; the ref's 5px of
    // extra length is provably hidden under the opaque row rule. ─────────
    @Test
    fun `003 column-rule slack versus the ref lies inside the row rule band`() {
        val config = GapDecorationExtractor.extract(
            listOf(
                "RowRuleStyle" to JsonPrimitive("SOLID"),
                "RowRuleWidth" to length(10.0),
                "RowRuleColor" to srgb(0.0, 0.0, 1.0),
                "ColumnRuleStyle" to JsonPrimitive("SOLID"),
                "ColumnRuleWidth" to length(10.0),
                "ColumnRuleColor" to srgb(1.0, 0.0, 0.0)
            )
        )
        val segs = GapDecorationSegments.build(config, family003, box003, true)
        val rows = of(segs, GapAxis.ROW)
        assertEquals(listOf(r(2f, 52f, 172f, 62f), r(2f, 112f, 172f, 122f)), rows)
        assertEquals(
            listOf(
                r(52f, 2f, 62f, 52f), r(112f, 2f, 122f, 52f),
                r(102f, 62f, 112f, 112f),
                r(52f, 122f, 62f, 172f), r(112f, 122f, 122f, 172f)
            ),
            of(segs, GapAxis.COLUMN)
        )
        // The fresh ref's first column bar is [52,2 10x55] = y[2,57]; ours
        // ends at 52. The disputed strip y[52,57] must be fully covered by
        // the row rule that paints AFTER it (row-over-column), otherwise
        // the model would be visibly short.
        val disputed = GapInterval(52f, 57f)
        val rowBand = GapInterval(rows[0].top, rows[0].bottom)
        assertEquals(
            "row rule must swallow the ref's extra column length",
            emptyList<GapInterval>(),
            GapIntervals.subtract(disputed, listOf(rowBand))
        )
    }

    // ── column-direction: the two families must swap physically ──────────
    @Test
    fun `column direction transposes the 009 geometry exactly`() {
        // Transpose of the 009 layout: every rect (l,t,r,b) → (t,l,b,r).
        val items = family003.map { r(it.top, it.left, it.bottom, it.right) }
        val config = GapDecorationConfig(
            // In a column-direction container the WITHIN-line gaps are
            // vertical → row-rule; the BETWEEN-line gaps are horizontal →
            // column-rule.
            column = GapRuleSpec(style = ColumnRuleStyle.SOLID, widthPx = 10f,
                breakMode = GapRuleBreak.INTERSECTION),
            row = GapRuleSpec(style = ColumnRuleStyle.SOLID, widthPx = 10f,
                breakMode = GapRuleBreak.INTERSECTION)
        )
        val segs = GapDecorationSegments.build(
            config, items, r(2f, 2f, 172f, 172f), mainHorizontal = false
        )
        // Row family = within-line, horizontal bars in the vertical gaps.
        assertEquals(
            listOf(
                r(2f, 52f, 52f, 62f), r(2f, 112f, 52f, 122f),
                r(62f, 102f, 112f, 112f),
                r(122f, 52f, 172f, 62f), r(122f, 112f, 172f, 122f)
            ),
            of(segs, GapAxis.ROW)
        )
        // Column family = between-line, vertical bars cut by both lines'
        // within-gaps (the transpose of 009's six row segments).
        assertEquals(
            listOf(
                r(52f, 2f, 62f, 52f), r(52f, 62f, 62f, 102f), r(52f, 122f, 62f, 172f),
                r(112f, 2f, 122f, 52f), r(112f, 62f, 122f, 102f), r(112f, 122f, 122f, 172f)
            ),
            of(segs, GapAxis.COLUMN)
        )
    }

    // ── the DEFAULT `*-rule-break`, on a layout where the two lines share
    // a gap band. Not in the WPT corpus; authored by the skeptic and
    // measured in a fresh headless Chromium (2×2 grid of 50px items,
    // 20px gaps, 2px rules, 120px content box at (2,2)):
    //   x = 61 (inside the column band): RED y[2,52) · WHITE y[52,61) ·
    //   BLUE y[61,63) · WHITE y[63,72) · RED y[72,122)
    //   y = 62: BLUE x[2,122)
    // i.e. the column rule STOPS at each line's cross edge — it does not
    // run through the row gap — and the row rule spans the content box.
    @Test
    fun `default rule-break keeps a shared band per-line, matching Chromium`() {
        val items = listOf(
            r(2f, 2f, 52f, 52f), r(72f, 2f, 122f, 52f),
            r(2f, 72f, 52f, 122f), r(72f, 72f, 122f, 122f)
        )
        val config = GapDecorationExtractor.extract(
            listOf(
                "ColumnRuleStyle" to JsonPrimitive("SOLID"),
                "ColumnRuleWidth" to length(2.0),
                "ColumnRuleColor" to srgb(1.0, 0.0, 0.0),
                "RowRuleStyle" to JsonPrimitive("SOLID"),
                "RowRuleWidth" to length(2.0),
                "RowRuleColor" to srgb(0.0, 0.0, 1.0)
            )
        )
        // No *-rule-break in the wire → the converter's documented CSS
        // initial, NORMAL.
        assertEquals(GapRuleBreak.NORMAL, config.column.breakMode)
        val segs = GapDecorationSegments.build(config, items, r(2f, 2f, 122f, 122f), true)
        assertEquals(
            listOf(r(61f, 2f, 63f, 52f), r(61f, 72f, 63f, 122f)),
            of(segs, GapAxis.COLUMN)
        )
        assertEquals(listOf(r(2f, 61f, 122f, 63f)), of(segs, GapAxis.ROW))
    }

    // ── the ComponentRenderer hook's Kotlin shape ────────────────────────
    // The item probe is chained as
    //     val m = if (cond) { A } else { B }
    //                 .gapItemProbe(id)
    // If Kotlin bound the trailing call to the ELSE BRANCH only, every
    // component on the `then` path would silently lose its probe and the
    // painter would never see a full item set. Pin the parse.
    @Test
    fun `a trailing call after a block-bodied if binds to the whole if`() {
        fun shape(c: Boolean): String =
            if (c) {
                "THEN"
            } else {
                "ELSE"
            }
                .plus("+PROBE")
        assertEquals("THEN+PROBE", shape(true))
        assertEquals("ELSE+PROBE", shape(false))
    }

    // ── reversed placement order (row-reverse) ───────────────────────────
    @Test
    fun `reversed report order paints NOTHING - documented limitation`() {
        // A single nowrap line whose items are reported in DOCUMENT order
        // while the layout placed them right-to-left (flex-direction:
        // row-reverse). Same three boxes as WPT 008's first three.
        //
        // GapDecorationLines.groupIntoLines' main-axis BACKTRACK signal
        // fires on every item, so the line is shattered into three
        // one-item lines: no within-line gaps, and no inter-line gaps
        // either (identical cross extents). The painter therefore emits
        // ZERO segments — a silent no-paint, not a wrong paint.
        //
        // UNREACHABLE TODAY: the Compose renderer carries
        // FlexDecision.reverse but never applies it, so placement order
        // still equals document order for row-reverse. This pins the
        // limitation so it cannot rot into a surprise the day reverse
        // placement lands. The toLines() main-start sort does NOT rescue
        // it (see the note there).
        val reversed = listOf(
            r(122f, 2f, 172f, 52f), r(62f, 2f, 112f, 52f), r(2f, 2f, 52f, 52f)
        )
        val config = GapDecorationConfig(
            column = GapRuleSpec(style = ColumnRuleStyle.SOLID, widthPx = 10f)
        )
        val segs = GapDecorationSegments.build(config, reversed, r(2f, 2f, 172f, 52f), true)
        assertEquals(3, GapDecorationLines.groupIntoLines(reversed, true).size)
        assertEquals(emptyList<GapRect>(), of(segs, GapAxis.COLUMN))
        assertEquals(emptyList<GapSegment>(), segs)
    }
}

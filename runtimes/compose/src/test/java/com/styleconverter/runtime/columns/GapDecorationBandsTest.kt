package com.styleconverter.runtime.columns

// JUnit4 plumbing — same style as GapDecorationSegmentsTest next door.
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Wave 50 lane B10 — pins the css-flexbox-1 §9.4 step 8 LINE BOX against
 * the frozen Chromium refs, and pins the fallbacks that keep every other
 * container on the pre-wave-50 item-union geometry.
 *
 * EVERY number below is measured, not derived from the CSS by hand:
 *
 *  * item rectangles come from the wave49-final captures, read back with a
 *    connected-component scan of the `#007bff` item fill — and the ref,
 *    iOS and Android captures agree to the pixel on BOTH fixtures, so the
 *    item geometry is not in dispute, only the rule geometry is:
 *      tools/titan/runs/wave49-final/sections/css-gaps/{ios,android}-screenshots/
 *      wpt__css-gaps__flex__flex-gap-decorations-045.png  (and -046)
 *  * expected rule bands come from the frozen ref:
 *      tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/
 *      white-black-ink-font-lh-imgpad-htmlpins/css-gaps/
 *      flex__flex-gap-decorations-045.png  (and -046)
 *  * the "what the union model paints today" expectations come from the
 *    same native captures, so the defect this file repairs is pinned by
 *    the pixels that exhibit it, not by an argument.
 *
 * Image-space → content-space: every capture carries a 16px image pad
 * (capture-browser-ref.mjs padPngBuffer) plus the fixture's own border, so
 * content x = image x − 18 on 045 (16 pad + 2px border) and − 16 on 046
 * (16 pad, no border).
 */
class GapDecorationBandsTest {

    private fun r(l: Float, t: Float, rr: Float, b: Float) = GapRect(l, t, rr, b)
    private fun iv(s: Float, e: Float) = GapInterval(s, e)

    /** A solid rule of [w] px; colour is irrelevant to geometry. */
    private fun solid(w: Float) = GapRuleSpec(style = ColumnRuleStyle.SOLID, widthPx = w)

    private fun crossOf(segs: List<GapSegment>, axis: GapAxis, horizontal: Boolean) =
        segs.filter { it.axis == axis }
            .map { if (horizontal) iv(it.rect.left, it.rect.right) else iv(it.rect.top, it.rect.bottom) }

    // ── WPT flex-gap-decorations-045 ────────────────────────────────────
    // `flex-direction: column; flex-wrap: wrap; align-content: stretch;`
    // width 120 content, column-gap 5, nine 50×70 items, height 400.
    // Captured item boxes (content space, all three platforms identical):
    //   line 1  x [0,50]   y [0,70] [83,153] [165,235] [248,318] [330,400]
    //   line 2  x [63,113] y [0,70] [110,180] [220,290] [330,400]
    // The main axis is VERTICAL, so the item-to-item gaps are the ROW
    // rules (gold) and the inter-line gap is the COLUMN rule (red).

    private val items045 = listOf(
        r(0f, 0f, 50f, 70f), r(0f, 83f, 50f, 153f), r(0f, 165f, 50f, 235f),
        r(0f, 248f, 50f, 318f), r(0f, 330f, 50f, 400f),
        r(63f, 0f, 113f, 70f), r(63f, 110f, 113f, 180f),
        r(63f, 220f, 113f, 290f), r(63f, 330f, 113f, 400f)
    )
    private val box045 = r(0f, 0f, 120f, 400f)

    /** The live container wire for 045 (per-test IR, wave49-final). */
    private fun config045(columnGap: Float? = 5f, stretches: Boolean = true) =
        GapDecorationConfig(
            column = solid(5f), row = solid(5f),
            columnGapPx = columnGap, rowGapPx = 0f,
            alignContentStretches = stretches
        )

    @Test
    fun `045 line boxes tile the content box, not the items`() {
        val lines = GapDecorationLines.toLines(
            GapDecorationLines.groupIntoLines(items045, mainHorizontal = false),
            mainHorizontal = false
        )
        // The union model's answer — the two item columns.
        assertEquals(listOf(iv(0f, 50f), iv(63f, 113f)), lines.map { it.cross })
        val bands = GapDecorationBands.resolve(
            lines, box045, mainHorizontal = false, crossGapPx = 5f, alignContentStretches = true
        ).map { it.cross }
        // §9.4 step 8: leftover = 120 − 50 − 50 − 5 = 15, split 8/7 (the
        // remainder goes to the leading line, FlexWrapLines.stretchLines),
        // then §9.6 packs them from the cross-start edge with the gap
        // between. Chromium's own boxes, read off the ref, are [0,58] and
        // [63,120] — the gold rules run image-x 18→76 and 81→138.
        assertEquals(listOf(iv(0f, 58f), iv(63f, 120f)), bands)
    }

    @Test
    fun `045 red column rule lands on the ref band instead of 4px left`() {
        val segs = GapDecorationSegments.build(
            config045(), items045, box045, mainHorizontal = false
        )
        // One between-line rule, spanning the content box along the main
        // (vertical) axis. Ref ink: image x [76,81] → content [58,63].
        assertEquals(
            listOf(r(58f, 0f, 63f, 400f)),
            segs.filter { it.axis == GapAxis.COLUMN }.map { it.rect }
        )
        // The defect this replaces: with the item-union extent the same
        // rule centred in [50,63] → content [54,59] → image [72,77], which
        // is exactly the red run measured in BOTH wave49-final native
        // captures of this test.
        val union = GapDecorationSegments.build(
            config045(columnGap = null), items045, box045, mainHorizontal = false
        )
        assertEquals(
            listOf(r(54f, 0f, 59f, 400f)),
            union.filter { it.axis == GapAxis.COLUMN }.map { it.rect }
        )
    }

    @Test
    fun `045 gold row rules span the stretched line, not the 50px items`() {
        val segs = GapDecorationSegments.build(
            config045(), items045, box045, mainHorizontal = false
        )
        // Four gaps on line 1, three on line 2 → seven within-line rules,
        // each running the full LINE width. Ref ink: image x [18,76] for
        // line 1 and [81,138] for line 2 → content [0,58] and [63,120].
        assertEquals(
            listOf(iv(0f, 58f), iv(0f, 58f), iv(0f, 58f), iv(0f, 58f),
                   iv(63f, 120f), iv(63f, 120f), iv(63f, 120f)),
            crossOf(segs, GapAxis.ROW, horizontal = true)
        )
        // The first gap is [70,83]; a 5px rule centred there is [74,79] →
        // image y [92,97], which is the first gold run in the ref.
        assertEquals(
            r(0f, 74f, 58f, 79f),
            segs.first { it.axis == GapAxis.ROW }.rect
        )
    }

    // ── WPT flex-gap-decorations-046 ────────────────────────────────────
    // Row flex, 140×180 content box, row-gap 5, six 70×50 items → three
    // lines. Android's captured item rows are content y [0,50] [62,112]
    // [124,174]; the main axis is HORIZONTAL, so the inter-line gaps carry
    // the ROW rules (gold) and there is no column rule at all.

    private val items046 = listOf(
        r(0f, 0f, 70f, 50f), r(70f, 0f, 140f, 50f),
        r(0f, 62f, 70f, 112f), r(70f, 62f, 140f, 112f),
        r(0f, 124f, 70f, 174f), r(70f, 124f, 140f, 174f)
    )
    private val box046 = r(0f, 0f, 140f, 180f)

    private fun config046(rowGap: Float? = 5f) = GapDecorationConfig(
        row = solid(5f), rowGapPx = rowGap, columnGapPx = 0f
    )

    @Test
    fun `046 gold row rules move onto the line-box gaps`() {
        val segs = GapDecorationSegments.build(
            config046(), items046, box046, mainHorizontal = true
        )
        // leftover = 180 − 150 − 10 = 20 → sizes 7/7/6 added to 50 each →
        // [57,57,56], packed to bands [0,57] [62,119] [124,180]. The two
        // line gaps are therefore [57,62] and [119,124].
        assertEquals(
            listOf(r(0f, 57f, 140f, 62f), r(0f, 119f, 140f, 124f)),
            segs.map { it.rect }
        )
        // Chromium splits the same leftover in fractions (56.667 per line),
        // so its bands are [0,56.67] [61.67,118.33] [123.33,180] and its
        // gold ink lands on image y [73,78] and [134,139]. Ours lands on
        // [73,78] and [135,140]: the first is exact, the second carries the
        // 1px the Compose layout ALREADY has (its third item row is
        // captured at image y 140 where Chromium's is 139 — the integral
        // §9.4 split, FlexWrapLines.stretchLines, not this painter).
        // Painting against the layout's own bands is the correct choice:
        // the rule must sit between the items that were actually placed.
    }

    @Test
    fun `046 union model reproduces the wave49-final native ink exactly`() {
        // Same inputs, cross gap withheld → the pre-wave-50 answer, which
        // must equal what the wave49-final Android capture shows: gold at
        // image y [70,74] and [132,136] → content [53.5,58.5] and
        // [115.5,120.5] (the partially-covered edge rows read as white).
        val segs = GapDecorationSegments.build(
            config046(rowGap = null), items046, box046, mainHorizontal = true
        )
        assertEquals(
            listOf(r(0f, 53.5f, 140f, 58.5f), r(0f, 115.5f, 140f, 120.5f)),
            segs.map { it.rect }
        )
        // …and it must NOT equal the corrected answer, or this file would
        // be pinning a no-op.
        assertNotEquals(
            GapDecorationSegments.build(config046(), items046, box046, mainHorizontal = true),
            segs
        )
    }

    // ── the fallbacks: every other container keeps the union geometry ───

    @Test
    fun `an unresolved cross gap refuses the reconstruction`() {
        val lines = GapDecorationLines.toLines(
            GapDecorationLines.groupIntoLines(items046, mainHorizontal = true), true
        )
        assertEquals(
            lines,
            GapDecorationBands.resolve(lines, box046, true, crossGapPx = null, alignContentStretches = true)
        )
    }

    @Test
    fun `a positioning align-content keyword refuses the reconstruction`() {
        // css-align-3 §5.1: `center`, `space-between`, … leave the lines
        // content-sized and place the line BLOCK. The item union already
        // IS the line box there, so touching it would invent geometry.
        val lines = GapDecorationLines.toLines(
            GapDecorationLines.groupIntoLines(items046, mainHorizontal = true), true
        )
        assertEquals(
            lines,
            GapDecorationBands.resolve(lines, box046, true, crossGapPx = 5f, alignContentStretches = false)
        )
    }

    @Test
    fun `lines that already tile the container are returned untouched`() {
        // The 003-family layout: 170px content box, three 50px lines,
        // row-gap 10 → leftover 0. This is the shape of every css-gaps
        // fixture that PASSES today, and it must not move.
        val items = listOf(
            r(0f, 0f, 50f, 50f), r(60f, 0f, 110f, 50f),
            r(0f, 60f, 100f, 110f),
            r(0f, 120f, 50f, 170f), r(60f, 120f, 110f, 170f)
        )
        val box = r(0f, 0f, 170f, 170f)
        val lines = GapDecorationLines.toLines(
            GapDecorationLines.groupIntoLines(items, mainHorizontal = true), true
        )
        assertEquals(
            lines,
            GapDecorationBands.resolve(lines, box, true, crossGapPx = 10f, alignContentStretches = true)
        )
    }

    @Test
    fun `bands that disagree with the placed items are rejected`() {
        // A container whose lines are packed at the cross-START edge of a
        // definite 180px box with no stretch — the legacy FlowRow shape a
        // `wrap-reverse` container still takes. The reconstruction would
        // claim [0,57] [62,119] [124,180]; the third line's items sit at
        // [110,160], outside the band claimed for it, so the union model
        // is kept and a PropertyTracker breadcrumb is left.
        val items = listOf(
            r(0f, 0f, 70f, 50f), r(70f, 0f, 140f, 50f),
            r(0f, 55f, 70f, 105f), r(70f, 55f, 140f, 105f),
            r(0f, 110f, 70f, 160f), r(70f, 110f, 140f, 160f)
        )
        val lines = GapDecorationLines.toLines(
            GapDecorationLines.groupIntoLines(items, mainHorizontal = true), true
        )
        assertEquals(
            lines,
            GapDecorationBands.resolve(lines, box046, true, crossGapPx = 5f, alignContentStretches = true)
        )
    }

    // ── the wire ───────────────────────────────────────────────────────

    @Test
    fun `the extractor reads the container gaps and align-content`() {
        // The live 046 container wire (wave49-final per-test IR):
        // RowGap {"type":"length","px":5}, no AlignContent → `normal`,
        // which stretches; no ColumnGap → the CSS initial `normal`, which
        // is 0 on a flex container (css-align-3 §8) and therefore KNOWN.
        val cfg = GapDecorationExtractor.extract(
            listOf(
                "RowRuleStyle" to JsonPrimitive("SOLID"),
                "RowRuleWidth" to buildJsonObject { put("type", "length"); put("px", 5.0) },
                "RowGap" to buildJsonObject { put("type", "length"); put("px", 5.0) }
            )
        )
        assertEquals(5f, cfg.rowGapPx)
        assertEquals(0f, cfg.columnGapPx)
        assertEquals(true, cfg.alignContentStretches)
        // A positioning keyword turns the reconstruction off.
        val centred = GapDecorationExtractor.extract(
            listOf(
                "RowRuleStyle" to JsonPrimitive("SOLID"),
                "AlignContent" to JsonPrimitive("CENTER")
            )
        )
        assertEquals(false, centred.alignContentStretches)
        // A percentage gap cannot be pre-resolved by the reader; null is
        // the honest answer and it disables the rebuild rather than
        // pretending the gap is 0.
        val pct = GapDecorationExtractor.extract(
            listOf(
                "RowRuleStyle" to JsonPrimitive("SOLID"),
                "RowGap" to buildJsonObject {
                    put("original", buildJsonObject { put("v", 50.0); put("u", "PERCENT") })
                }
            )
        )
        assertEquals(null, pct.rowGapPx)
    }

    @Test
    fun `a single line is never rebuilt`() {
        val items = listOf(r(0f, 0f, 70f, 50f), r(75f, 0f, 145f, 50f))
        val lines = GapDecorationLines.toLines(
            GapDecorationLines.groupIntoLines(items, mainHorizontal = true), true
        )
        assertEquals(
            lines,
            GapDecorationBands.resolve(lines, r(0f, 0f, 145f, 180f), true, 5f, true)
        )
    }
}

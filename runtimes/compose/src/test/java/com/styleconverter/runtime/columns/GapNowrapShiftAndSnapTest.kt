package com.styleconverter.runtime.columns

// Wave 52 (lane L10) — pins for the two Compose gap-decoration fixes:
//
//  (a) M2 — GapItemSink moves a negative-margin item's reported SLOT by
//      MarginApplier's paint shift, so css-gaps flex-gap-decorations-027's
//      five column rules land in the §9.5 outer-size gaps
//      (content x −100/−40/20/80/140, the ref's page x 118…358 minus the
//      container content origin 218) instead of four rules in a line that
//      GapDecorationLines splits at the backwards jump.
//  (b) 7(a′) — GapDecorationPainter.snapped is Chromium's
//      PixelSnappedIntRect (edges floor(v + 0.5)), the twin of the SwiftUI
//      GapDecorationsPainter.snapped and pinned with the same table as
//      FlexNowrapOuterTests.testBandSnapMatchesChromiumPixelSnap.
//
// EXECUTED MUTATIONS (tools/titan/results/wave52-flex-nowrap-gaps/
// mutations.log; restored byte-exact, sha-verified):
//   KT-H1 placedItems ignores paintShiftsDp → `027 rules…` RED
//   KT-S1 snapped returns its input       → `snap table…` RED
//   KT-B1 bands go back to the Int split   → `046 bands…` RED
//   KT-H2 (fix pass, RTL) paintShiftsFor drops its rtl → `paint shifts follow…` RED

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class GapNowrapShiftAndSnapTest {

    private fun r(l: Float, t: Float, rr: Float, b: Float) = GapRect(l, t, rr, b)

    /** 027's container: 10-px solid red column rules, column-gap 10. */
    private val config027 = GapDecorationConfig(
        column = GapRuleSpec(style = ColumnRuleStyle.SOLID, widthPx = 10f),
        columnGapPx = 10f, rowGapPx = 0f
    )

    /** Six 50×50 slots where FlexNowrapLine puts them (pin 4 / pin 3 twins). */
    private fun slots027() = listOf(0f, -90f, -30f, 30f, 90f, 150f)
        .map { r(it, 0f, it + 50f, 50f) }

    /** A sink fed the slots as the probes would report them (origin 0). */
    private fun sink(shifts: Map<String, Pair<Float, Float>>): GapItemSink {
        val ids = (0 until 6).map { "item$it" }
        val s = GapItemSink(ids, config027, mainHorizontal = true, paintShiftsDp = shifts)
        // The probe's window rects; origin stays at (0,0).
        ids.zip(slots027()).forEach { (id, rect) -> s.rects[id] = rect }
        return s
    }

    @Test
    fun `027 rules sit in the outer-size gaps once One is shifted to its painted box`() {
        // FlexNowrapLine.paintShiftPx(027's "One") == (-150, 0), pinned in
        // FlexNowrapLineTest; density 1 = the capture's px == dp.
        val items = sink(mapOf("item0" to (-150f to 0f))).placedItems(density = 1f)
        assertEquals(r(-150f, 0f, -100f, 50f), items[0])
        val box = r(0f, 0f, 200f, 50f)
        val rules = GapDecorationSegments.build(config027, items, box, mainHorizontal = true)
            .filter { it.axis == GapAxis.COLUMN }.map { it.rect.left }
        assertEquals(listOf(-100f, -40f, 20f, 80f, 140f), rules)
    }

    @Test
    fun `paint shifts follow the container direction - RTL mirrors One's pull`() {
        // 027's "One" leaf verbatim (MarginLeft {"px":-150}); "Two" has none.
        val one = IRComponent("one", "one", listOf(IRProperty("MarginLeft", Json.parseToJsonElement("""{"px":-150}"""))))
        val kids = listOf(one, IRComponent("two", "two"))
        // LTR: the anchor moves left with MarginApplier's −150 offset.
        assertEquals(mapOf("one" to (-150f to 0f)), paintShiftsFor(kids, rtl = false))
        // Rtl: the Dp Modifier.offset moves One RIGHT by 150 — so does its anchor.
        assertEquals(mapOf("one" to (150f to 0f)), paintShiftsFor(kids, rtl = true))
    }

    @Test
    fun `without the shift the slot jump splits the line and the first rule is lost`() {
        // The negative control the mutation KT-H1 reproduces: four rules.
        val items = sink(emptyMap()).placedItems(density = 1f)
        val rules = GapDecorationSegments.build(config027, items, r(0f, 0f, 200f, 50f), true)
            .filter { it.axis == GapAxis.COLUMN }.map { it.rect.left }
        assertEquals(listOf(-40f, 20f, 80f, 140f), rules)
    }

    @Test
    fun `snap table - Chromium PixelSnappedIntRect on 045 and 046 bands`() {
        // 046's fractional line gaps (content y 56.67 / 118.33) → [57,62) /
        // [118,123): image y 73–77 / 134–138, the ref's crisp rows.
        assertEquals(r(0f, 57f, 140f, 62f), GapDecorationPainter.snapped(r(0f, 170f / 3f, 140f, 185f / 3f)))
        assertEquals(r(0f, 118f, 140f, 123f), GapDecorationPainter.snapped(r(0f, 355f / 3f, 140f, 370f / 3f)))
        // 045's half-pixel column band x 57.5 → [58,63) (image x 76–80).
        assertEquals(r(58f, 0f, 63f, 70f), GapDecorationPainter.snapped(r(57.5f, 0f, 62.5f, 70f)))
        // Integral rects are untouched — every other css-gaps test.
        assertEquals(r(50f, 0f, 60f, 50f), GapDecorationPainter.snapped(r(50f, 0f, 60f, 50f)))
    }

    @Test
    fun `046 bands use the fractional share the SwiftUI twin uses`() {
        // Android-placed items (Int layout): rows [0,50] [62,112] [124,174].
        val items = listOf(
            r(0f, 0f, 70f, 50f), r(70f, 0f, 140f, 50f),
            r(0f, 62f, 70f, 112f), r(70f, 62f, 140f, 112f),
            r(0f, 124f, 70f, 174f), r(70f, 124f, 140f, 174f)
        )
        val lines = GapDecorationLines.toLines(GapDecorationLines.groupIntoLines(items, true), true)
        val bands = GapDecorationBands.resolve(lines, r(0f, 0f, 140f, 180f), true, 5f, true)
            .map { it.cross }
        // 20 px leftover / 3 lines: 56.67 each — not the Int 57/57/56.
        assertEquals(170f / 3f, bands[0].end, 0.001f)
        assertEquals(185f / 3f, bands[1].start, 0.001f)
        assertEquals(355f / 3f, bands[1].end, 0.001f)
    }
}

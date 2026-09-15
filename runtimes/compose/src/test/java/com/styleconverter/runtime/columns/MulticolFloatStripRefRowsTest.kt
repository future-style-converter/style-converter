package com.styleconverter.runtime.columns

// WAVE-50 LANE B6 — the REF-ROW pins for the float strip (BACKLOG queue 3b).
//
// WHY A SECOND FILE BESIDE MulticolFloatStripTest. That table pins the strip
// in STRIP coordinates (offsets, C, H, slice count). This one closes the last
// step to the picture: it converts a plan into the CAPTURE ROWS the band must
// paint on, and asserts them against rows read out of the frozen Chromium ref
// PNGs. Queue 3(b) says Compose "renders the 003/balancing-003 orange 3px
// high … a measured-height rounding in the Kotlin strip walk". THE STRIP WALK
// IS NOT THE CAUSE — these pins are the proof, and they are also the guard
// that stops a later lane from "fixing" 3(b) by shifting the offsets, which
// would break both refs at once.
//
// MEASUREMENT PROVENANCE (every number below was read off a PNG, not derived):
//   refs   tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/
//            white-black-ink-font-lh-imgpad-htmlpins/CSS2/
//            floats-clear__floats-clear-multicol-{003,balancing-003}.png
//   caps   tools/titan/runs/wave49-final/sections/CSS2/
//            {screenshots,android-screenshots,ios-screenshots}/wpt__CSS2__…
//   column-3 pixel column x=237 (inside the left aqua float), orange
//   = rgb(255,165,0), the multicol's silver border-box top band = rows
//   108-110 (003) / 88-90 (balancing-003), so the CONTENT top row R0 is the
//   row right after it: 111 and 91.
//
//   003 (column-fill:auto, H=100)      ref/web/iOS  aqua 111-160, orange 161-163
//                                      Android      aqua 111-157, orange 158-160
//   balancing-003 (balance, H=85)      ref/web/iOS  aqua 91-170,  orange 171-175
//                                      Android      aqua 91-165,  orange 166-170
//
// THE ANDROID SHIFT IS THE BAND'S OWN, NOT THE STRIP'S. Three independent
// readings say so:
//   (a) columns 1 and 2 are pixel-identical to the ref in both tests — a
//       shifted strip offset or a wrong C/H would move them too;
//   (b) in balancing-003 Android still paints EXACTLY 85 aqua rows in columns
//       1-2 (91-175), i.e. it balanced to the same H=85, which requires the
//       same C=255 the ref implies — a 5px-short strip would have balanced to
//       ceil(250/3)=84 and every column would be one row shorter;
//   (c) the shift equals the band's own width in both tests (3 and 5), and
//       the sibling shapes that carry the band on the CLEARED BOX ITSELF
//       rather than on a nested child — -002 / -balancing-002 — are Android
//       ref-exact (orange 161-163 / 171-175). The difference is `.clear
//       { height: 0 }` wrapping the `.bar` that owns the border: Compose's
//       `Modifier.height(0.dp)` (sizing/SizingApplier.kt, the LengthValue.Exact
//       arm) hands the child fixed 0 constraints, so `.bar` measures 0 tall,
//       and BorderSideApplier's bottom arm draws the band centred on
//       `size.height - width/2` — at height 0 that is the half-open band
//       [-w, 0), i.e. w px ABOVE the box. −3 and −5, exactly as measured.
// The repair is therefore in the sizing/border pair (a `height: 0` box must
// not clamp its overflowing child — css-overflow-3 §2 `overflow: visible`),
// which is outside this lane's ownership; it is handed over with these
// numbers in the wave-50 lane report.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MulticolFloatStripRefRowsTest {

    /** IR wire helper — keywords are bare JSON strings, lengths objects. */
    private fun prop(t: String, j: String) = IRProperty(t, Json.parseToJsonElement(j))

    /** A px length wire: `{"type":"length","px":N}` (converter shape). */
    private fun px(n: Int) = "{\"type\":\"length\",\"px\":$n}"

    /** One aqua float box exactly as the wave49-final per-test IR carries it. */
    private fun floatBox(id: String, side: String, h: Int) = IRComponent(
        id = id, name = id,
        properties = listOf(
            prop("Float", "\"$side\""), prop("Width", px(15)), prop("Height", px(h))
        )
    )

    /** `.container { width: 100% }` holding the two 240px floats of -003. */
    private fun container240() = IRComponent(
        id = "container", name = "container",
        properties = listOf(prop("Width", "{\"type\":\"percentage\",\"value\":100}")),
        children = listOf(floatBox("fL", "LEFT", 240), floatBox("fR", "RIGHT", 240))
    )

    /** `.step { height: 10px; border: 15px aqua; border-style: none solid }`. */
    private fun step() = IRComponent(
        id = "step", name = "step",
        properties = listOf(
            prop("Height", px(10)),
            prop("BorderLeftStyle", "\"SOLID\""), prop("BorderRightStyle", "\"SOLID\""),
            prop("BorderTopStyle", "\"NONE\""), prop("BorderBottomStyle", "\"NONE\""),
            prop("BorderLeftWidth", "{\"px\":15}"), prop("BorderRightWidth", "{\"px\":15}")
        )
    )

    /** `.clear { clear: left; height: 0 }` wrapping the orange `.bar`. */
    private fun clearWithBar(barWidthWire: String?) = IRComponent(
        id = "clear", name = "clear",
        properties = listOf(prop("Clear", "\"LEFT\""), prop("Height", px(0))),
        children = listOf(
            IRComponent(
                id = "bar", name = "bar",
                properties = listOfNotNull(
                    prop("BorderBottomStyle", "\"SOLID\""),
                    barWidthWire?.let { prop("BorderBottomWidth", it) }
                )
            )
        )
    )

    /**
     * The capture rows a strip band `[a, b)` paints on, given the plan's used
     * column block-size H and the capture's content top row R0.
     *
     * css-break-3 §4.1: fragment k shows strip band `[k·H, (k+1)·H)` at the
     * column's own top, so a strip offset y inside that band lands at capture
     * row `R0 + (y − k·H)`. The returned pair is INCLUSIVE, matching how the
     * PNG scan reports a run of rows.
     */
    private fun bandRows(stripStart: Int, stripEnd: Int, h: Int, contentTopRow: Int): Pair<Int, Int> {
        // The fragment that contains the band's first row.
        val column = stripStart / h
        // Both edges are read in that fragment's local coordinates; the band
        // never straddles a column in these two tests (asserted below).
        val top = contentTopRow + (stripStart - column * h)
        val bottom = contentTopRow + (stripEnd - 1 - column * h)
        return top to bottom
    }

    /** The plan under the -003 geometry: W=100, G=0, N=3. */
    private fun plan(children: List<IRComponent>, measured: List<Int>, fillAuto: Boolean, h: Int) =
        MulticolFloatStripPlan.plan(
            MulticolSpannerFlow.specsFor(children), measured, h, fillAuto, 100, 0, 3
        )!!

    @Test
    fun `003 fill - the plan puts the 3px band on the ref's rows 161-163`() {
        // Measured under the zero-flow plan: step 10, container 0 (its floats
        // are out of flow), cleared box 0 (`height: 0`).
        val p = plan(listOf(step(), container240(), clearWithBar(null)), listOf(10, 0, 0), true, 100)
        // The cleared box's offset IS the §9.5.2 clearance line: step 10 +
        // 240px float = 250. Not 247 — the number the Android capture paints.
        assertEquals(listOf(0, 10, 250), p.yOffsetsPx)
        assertEquals(100, p.columnBlockSizePx)
        assertEquals(253, p.stripInkPx)
        // The band is the cleared box's trailing ink: [250, 253).
        val rows = bandRows(250, 253, p.columnBlockSizePx, CONTENT_TOP_003)
        assertEquals("ref orange rows (frozen PNG, x=237)", 161 to 163, rows)
        // …and it lands in the THIRD column, which is what the test asserts
        // in prose ("halfway down the third column").
        assertEquals(2, 250 / p.columnBlockSizePx)
        // The aqua above it runs to the band's top row, exactly as the ref
        // scan reports (aqua 111-160 with the band starting at 161).
        assertEquals(111 to 160, bandRows(200, 250, p.columnBlockSizePx, CONTENT_TOP_003))
    }

    @Test
    fun `balancing-003 - the plan puts the 5px band on the ref's rows 171-175`() {
        // Same shape, `column-fill: balance` and a declared 5px band.
        val p = plan(
            listOf(step(), container240(), clearWithBar("{\"px\":5}")), listOf(10, 0, 0), false, 100
        )
        assertEquals(listOf(0, 10, 250), p.yOffsetsPx)
        // §7.1 reduction: ceil(255/3) = 85 — and 85 is independently
        // confirmed by the capture, where Android's OWN columns 1-2 are 85
        // aqua rows (91-175). That is the (b) reading in the banner.
        assertEquals(85, p.columnBlockSizePx)
        assertEquals(255, p.stripInkPx)
        assertEquals(171 to 175, bandRows(250, 255, p.columnBlockSizePx, CONTENT_TOP_BAL_003))
        assertEquals(2, 250 / p.columnBlockSizePx)
        assertEquals(91 to 170, bandRows(170, 250, p.columnBlockSizePx, CONTENT_TOP_BAL_003))
    }

    @Test
    fun `the Android shift is NOT reachable from any strip offset`() {
        // The negative control for the banner's claim. Android paints the
        // -003 band at rows 158-160; for the strip to produce that, the
        // cleared box would have to sit at strip 247 — and the SAME plan
        // would then compute C = 250, which under the balancing sibling
        // reduces to ceil(250/3) = 84, contradicting the 85 aqua rows Android
        // itself paints in columns 1-2. A single cause cannot be both.
        val shifted = bandRows(247, 250, 100, CONTENT_TOP_003)
        assertEquals("the Android -003 rows, IF the strip had moved", 158 to 160, shifted)
        val ifStripWereShort = MulticolFloatStripPlan.plan(
            MulticolSpannerFlow.specsFor(
                listOf(step(), container240(), clearWithBar("{\"px\":5}"))
            ),
            listOf(7, 0, 0), 100, false, 100, 0, 3
        )!!
        assertEquals(252, ifStripWereShort.stripInkPx)
        assertTrue(
            "a strip 3px short would balance to 84, not the 85 Android paints",
            ifStripWereShort.columnBlockSizePx == 84
        )
    }

    @Test
    fun `a Clear wire on a br child is IGNORED today - queue 3a, recorded not endorsed`() {
        // KNOWN GAP, pinned so the day the extractor's `br[clear]` bake lands
        // (the seam patch under tools/titan/results/wave50-B6/) someone sees
        // that the strip still has to grow a rung for it. `factsFor` reads
        // `Clear` off the CHILD's own property list; in -000/-001 the wire the
        // bake produces sits on the `<br>` GRANDCHILD, so the cleared box gets
        // no clearance and the orange stays in column 1 (wave49-final: all
        // three platforms paint it at rows 131-133 against the ref's 161-163).
        // CSS 2.1 §9.5.2 clearance on the br's LINE BOX grows the containing
        // block's height instead of moving its top edge — which is why this
        // cannot be modelled by the offset jump the `clear`-on-the-box arm
        // uses (in -001 the same box also anchors the floats).
        val brWithClear = IRComponent(
            id = "clear", name = "clear",
            properties = listOf(prop("BorderBottomStyle", "\"SOLID\"")),
            children = listOf(
                IRComponent(
                    id = "br", name = "br",
                    properties = listOf(
                        prop("Width", px(0)), prop("Height", px(0)), prop("Clear", "\"BOTH\"")
                    )
                )
            )
        )
        val facts = MulticolFloatStrip.factsFor(brWithClear)!!
        assertEquals("no clearance is read from the br", false, facts.clearsLeft)
        assertEquals("no clearance is read from the br", false, facts.clearsRight)
        // The box still contributes its own 3px band to the ink floor, so the
        // bake's `height: 20px → 0px` half does reach the strip.
        assertEquals(3.0, facts.trailingInkPx, 0.0)
    }

    private companion object {
        /** -003: silver border-box top band rows 108-110 ⇒ content top 111. */
        const val CONTENT_TOP_003 = 111

        /** -balancing-003: silver band rows 88-90 ⇒ content top 91. */
        const val CONTENT_TOP_BAL_003 = 91
    }
}

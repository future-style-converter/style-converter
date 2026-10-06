package com.styleconverter.runtime.borders.sides

// Wave 52, lane L6 (T2) — the bottom/end border band of a box SHORTER than
// its own stroke (BACKLOG queue 3(b): "Compose renders the 003/
// balancing-003 orange band w px too high, where w is the band's OWN
// width").
//
// ## The defect (measured on wave51-fix, pngjs over the run PNGs)
//   CSS2/floats-clear/floats-clear-multicol-003            android P 0.9881
//     orange x235-334 at rows 158-160  — ref rows 161-163   (3 px band, 3 px high)
//   CSS2/floats-clear/floats-clear-multicol-balancing-003  android P 0.9857
//     orange x235-334 at rows 166-170  — ref rows 171-175   (5 px band, 5 px high)
// Columns 1-2 are ref-exact on both, so the strip is not the cause: the
// `.clear { height: 0 }` parent hands its auto-height `.bar` child FIXED 0
// constraints (SizingApplier `height(v)`), the child's layout height is 0,
// and the old far-edge centre `size.height − w/2 = −w/2` painted the band
// ABOVE the box. css-backgrounds-3 §4 / css-box-4 §2: a bottom border
// occupies [contentBottom, contentBottom + w) of its OWN border box —
// [0, w) here — and a stroke outside the box is never correct.
//
// ## What is pinned: the stroke the painters draw, not just the helper
// Compose's JVM unit stage has no draw surface (android.graphics is a
// throwing stub — the split ListMarkerSymbolTest / ListMarkerRowTest
// document), so the pins read the LINE that `paintSide` / `drawDouble` /
// `drawGrooveOrRidge` hand to `DrawScope.drawLine`: `sideGeometry` and
// `doubleGeom` are pure functions of (side, width|inset, box Size) that the
// painters call with `DrawScope.size`. A Butt-capped stroke of width w on a
// horizontal line at y fills exactly the rows [y − w/2, y + w/2), so
// `inkRows` below is that fill rasterised to whole pixel rows (columns for
// a vertical line) — the plan's "100×0 box, border-bottom:3px solid → ink
// rows [0,3), none at y<0" paint test, without a Canvas.
//
// MUTATION PROOF (all EXECUTED by tools/titan/results/wave52-counters-and-
// lists/mutate.py; every restore sha256-verified byte-exact, see
// mutations.log):
//   band-clamp  helper body → pre-fix `extentPx - insetPx` (2026-10-05
//               17:18 on the helper-only pins; re-run in the fix pass on
//               these): every 0-tall / 0-wide pin red, tall pins green.
//   side-bottom sideGeometry BOTTOM → `box.height - width / 2`, helper
//               untouched (the skeptic's mutation 5, which the pre-fix-pass
//               helper-only pins MISSED): the 3/5/10 px BOTTOM pins red.
//   side-end    sideGeometry END → `box.width - width / 2`: the END pin red.
//   double-bottom doubleGeom BOTTOM → `box.height - inset`: the double and
//               groove pins red.
// Outcomes per run are the `result` lines of mutations.log.
//
// R1 FIX PASS (skeptic re-verify R1): the two-line paths no longer go
// through the single-stroke clamp, which MIRRORED any line deeper than w/2
// — this file's groove pin had asserted that mirror (outer {0,1}). It now
// pins the band TRANSLATED into [0, w): outer half {2,3} (border edge),
// inner {0,1}; the double pin pins each line, outer {2} / inner {0}.
// `doubleInk` / the groove pin read the painters' own plans (`doubleLines`,
// `grooveRidgeLines`). The R1 mutations (`double-mirror*`,
// `band-centre-mirror`, `*-lines-width`, `double-dev-bottom` = dev's
// `box.height - inset` on the new arm — the successor of `double-bottom`
// above, whose substring is gone) were executed against BOTH files; the
// counts are in BorderSideTwoLineBandTest's header and mutations.log.

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.styleconverter.runtime.borders.sides.BorderSideApplier.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.floor

class BorderSideZeroTallBandTest {

    /** Whole pixel rows (horizontal line) or columns (vertical line) a
     *  Butt-capped stroke of width [w] centred on the line [a]→[b] inks:
     *  every integer cell whose [i, i+1) overlaps [c − w/2, c + w/2). */
    private fun inkCells(a: Offset, b: Offset, w: Float, horizontal: Boolean): Set<Int> {
        // A side line is axis-aligned: its cross-axis coordinate is constant.
        val c = if (horizontal) a.y else a.x
        assertEquals("side line must be axis-aligned", c, if (horizontal) b.y else b.x, 0f)
        val lo = floor(c - w / 2f).toInt()
        val hi = ceil(c + w / 2f).toInt() - 1
        return (lo..hi).toSet()
    }

    /** Ink of the single-stroke path (`paintSide` SOLID/DASHED/INSET/…). */
    private fun singleInk(s: Side, w: Float, box: Size): Set<Int> {
        val (a, b, horizontal) = BorderSideApplier.sideGeometry(s, w, box)
        return inkCells(a, b, w, horizontal)
    }

    /** Ink of the DOUBLE path, per line (outer first): drawDouble's own
     *  plan `doubleLines` — two 1/3-width lines, w ≥ 3. */
    private fun doubleInk(s: Side, w: Float, box: Size): List<Set<Int>> {
        val line = w / 3f                                  // each line's stroke width
        val horizontal = s == Side.TOP || s == Side.BOTTOM // block-axis sides
        return BorderSideApplier.doubleLines(s, w, box).map { (a, b) ->
            inkCells(a, b, line, horizontal)               // one set per line
        }
    }

    // ── the defect shape: a 0-tall box, single stroke ───────────────────

    @Test
    fun `a 100x0 box paints its 3px bottom band at rows 0 to 2 and none above`() {
        // multicol-003: `.bar { border-bottom: 3px solid orange }` under
        // `.clear { height: 0 }` — laid-out box 100×0, stroke width 3.
        val (a, b, horizontal) = BorderSideApplier.sideGeometry(
            Side.BOTTOM, 3f, Size(100f, 0f))
        assertTrue("bottom side is a horizontal line", horizontal)
        // The line spans the full box width, start to end.
        assertEquals(0f, a.x, 0f); assertEquals(100f, b.x, 0f)
        // Ink rows [0, 3): inside the box's own border area.
        assertEquals(setOf(0, 1, 2), inkCells(a, b, 3f, horizontal))
    }

    @Test
    fun `a 0-tall box paints its 5px bottom band at rows 0 to 4`() {
        // balancing-003's 5 px band — the second measured cell.
        val ink = singleInk(Side.BOTTOM, 5f, Size(100f, 0f))
        assertEquals((0..4).toSet(), ink)
        assertTrue("no ink above the box", ink.all { it >= 0 })
    }

    @Test
    fun `the third census carrier keeps its 10px band inside the box too`() {
        // css-display/display-contents-dynamic-generated-content-fieldset-001
        // (`android P 0.9981`): parent H=0, child border-bottom 10 — the
        // band moves 10 px DOWN, toward the ref (census T2, 3 tests total).
        assertEquals((0..9).toSet(), singleInk(Side.BOTTOM, 10f, Size(100f, 0f)))
    }

    @Test
    fun `a 0-wide box paints its 3px end band at columns 0 to 2`() {
        // The inline-axis twin: END routes through the same clamp, so a
        // 0-wide box's right border lands at x [0, 3), never left of it.
        val (a, b, horizontal) = BorderSideApplier.sideGeometry(
            Side.END, 3f, Size(0f, 40f))
        assertTrue("end side is a vertical line", !horizontal)
        assertEquals(setOf(0, 1, 2), inkCells(a, b, 3f, horizontal))
    }

    // ── the frozen-baseline guarantee: boxes ≥ one stroke are unchanged ──

    @Test
    fun `a box taller than its stroke keeps the pre-fix line on every side`() {
        // 100×50 with 3 px borders: the exact pre-fix arithmetic every
        // committed baseline was captured with (far edges at extent − w/2).
        val box = Size(100f, 50f)
        assertEquals((0..2).toSet(), singleInk(Side.TOP, 3f, box))
        assertEquals((47..49).toSet(), singleInk(Side.BOTTOM, 3f, box))
        assertEquals((0..2).toSet(), singleInk(Side.START, 3f, box))
        assertEquals((97..99).toSet(), singleInk(Side.END, 3f, box))
        // The bottom line's centre is literally `size.height − w/2`.
        val (a, _, _) = BorderSideApplier.sideGeometry(Side.BOTTOM, 3f, box)
        assertEquals(48.5f, a.y, 0f)
    }

    @Test
    fun `a box exactly one stroke tall is the boundary where both formulas agree`() {
        // extent == w: `max(w/2, w − w/2)` and `w − w/2` are the same
        // number, so the clamp is continuous — no step at the boundary.
        assertEquals(1.5f, BorderSideApplier.innerEdgeStrokeCentre(3f, 1.5f), 0f)
        assertEquals((0..2).toSet(), singleInk(Side.BOTTOM, 3f, Size(100f, 3f)))
    }

    // ── the DOUBLE / groove / ridge paths translate the whole band ──────

    @Test
    fun `a double border on a 0-tall box paints rows 0 and 2 with the gap at 1`() {
        // `border-bottom: 3px double`: line = 1, insets 0.5 (outer) and
        // 2.5 (inner) from the far edge of the band translated into [0, 3)
        // — outer line at the border edge (row 2), inner at row 0, gap 1.
        assertEquals(listOf(setOf(2), setOf(0)), doubleInk(Side.BOTTOM, 3f, Size(100f, 0f)))
        // Same picture on the inline axis for a 0-wide box.
        assertEquals(listOf(setOf(2), setOf(0)), doubleInk(Side.END, 3f, Size(0f, 40f)))
    }

    @Test
    fun `a groove border on a 0-tall box keeps its outer half at the border edge`() {
        // drawGrooveOrRidge's plan: half = w/2, half-bands centred half/2
        // and 3·half/2 in from the border edge of the band translated into
        // [0, w). For w = 4 the OUTER half (grooveRidgeBandShades' first
        // colour) inks rows {2,3} and the inner {0,1} — the R1 fix; the
        // per-line clamp had mirrored them ({0,1} / {2,3}, shades swapped).
        val (outer, inner) = BorderSideApplier.grooveRidgeLines(Side.BOTTOM, 4f, Size(100f, 0f))
        assertEquals(setOf(2, 3), inkCells(outer.first, outer.second, 2f, true))
        assertEquals(setOf(0, 1), inkCells(inner.first, inner.second, 2f, true))
    }
}

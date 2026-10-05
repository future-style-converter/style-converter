package com.styleconverter.runtime.borders.sides

// Wave 52, lane L6, skeptic re-verify R1 — the TWO-LINE far-edge paths
// (`double` w ≥ 3, `groove` / `ridge` w ≥ 2, on bottom / end).
//
// ## The defect (executed probe, skeptic.md "Regression hunt — R1")
// T2's clamp `max(inset, extent − inset)` is right for the single stroke
// (inset = w/2) but doubleGeom fed it the double's inner line (5w/6) and
// groove/ridge's inner half (3w/4): any box with extent < 2·inset got that
// line MIRRORED about its inset instead of dev's `extent − inset`
// (dev = 5d9ed628, whose doubleGeom far edge is `size.height − inset`):
//   100×3 · 3px double → rows {2}         dev {0,2}
//   100×6 · 6px double → rows {4,5}       dev {0,1,4,5}
//   100×9 · 6px double → rows {4,5,7,8}   dev {3,4,7,8}
//   3×40  · 3px double END → cols {2}     dev {0,2}
//   100×4 · 4px groove inner half {2,3}   dev {0,1}
//   100×0 · 4px groove outer half {0,1}   spec {2,3} (BorderSideZeroTallBandTest)
// Fix: far edge = max(extent, w) — the band TRANSLATED into [0, w), never
// mirrored; for boxes ≥ one band tall that is dev's arithmetic EXACTLY,
// which the sweep below pins float-for-float. An independent python oracle
// over the dev / pre-R1 / fixed formulas gave the same rows (lane note §12).
//
// What is pinned: the painters' OWN plans — `doubleLines` (drawDouble) and
// `grooveRidgeLines` (drawGrooveOrRidge) — rasterised as in
// BorderSideZeroTallBandTest (Butt cap fills [c − w/2, c + w/2)).
//
// MUTATION PROOF (EXECUTED 2026-10-05 19:57–19:58 CEST by tools/titan/
// results/wave52-counters-and-lists/mutate.py on BorderSideApplier.kt
// sha256 7bcc1fe7…; every restore sha256-verified byte-exact back to
// 7bcc1fe7…; `--tests '*BorderSide*'`, 18 tests, only JUnit XML newer than
// the run start read; outcomes = the `result` lines of mutations.log,
// copied into the tracked compose-tests-r1.summary.txt — *.log is ignored):
//   double-mirror      doubleGeom BOTTOM+END back to the pre-R1
//                      `innerEdgeStrokeCentre(extent, inset)`: 9 red (p1–p5,
//                      p7, the sweep, both 0-tall two-line pins).
//   double-mirror-bottom  BOTTOM arm alone: 8 red.
//   double-mirror-end     END arm alone: 3 red (p4, sweep END, 0-wide double).
//   band-centre-mirror farEdgeBandCentre body → `max(inset, extent − inset)`: 10 red.
//   double-lines-width doubleLines passes `line` as the band: 2 red (p7, 0-tall double).
//   groove-lines-width grooveRidgeLines passes `half` as the band: 1 red (0-tall groove).
//   double-dev-bottom  dev `box.height − inset`: 3 red, ONLY the 0-tall /
//                      sub-band pins — p1–p3, p5 and the sweep stay green.

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.styleconverter.runtime.borders.sides.BorderSideApplier.Side
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.floor

class BorderSideTwoLineBandTest {

    /** Whole pixel cells a Butt-capped stroke of width [w] centred on the
     *  axis-aligned line [a]→[b] inks (rows if [horizontal], else columns). */
    private fun ink(a: Offset, b: Offset, w: Float, horizontal: Boolean): Set<Int> {
        val c = if (horizontal) a.y else a.x                     // cross-axis centre
        assertEquals("side line must be axis-aligned", c, if (horizontal) b.y else b.x, 0f)
        return (floor(c - w / 2f).toInt()..(ceil(c + w / 2f).toInt() - 1)).toSet()
    }

    /** drawDouble's plan, per line (outer first), each stroke w/3 wide. */
    private fun dbl(s: Side, w: Float, box: Size): List<Set<Int>> =
        BorderSideApplier.doubleLines(s, w, box).map { (a, b) ->
            ink(a, b, w / 3f, s == Side.TOP || s == Side.BOTTOM)   // one set per line
        }

    /** drawGrooveOrRidge's plan as (outer, inner), each stroke w/2 wide. */
    private fun groove(s: Side, w: Float, box: Size): Pair<Set<Int>, Set<Int>> {
        val (o, i) = BorderSideApplier.grooveRidgeLines(s, w, box)
        val h = s == Side.TOP || s == Side.BOTTOM                // block-axis side?
        return ink(o.first, o.second, w / 2f, h) to ink(i.first, i.second, w / 2f, h)
    }

    // ── the skeptic's probe rows: boxes ≥ one band keep dev's picture ───

    @Test
    fun `a 3px bottom double on a 3px box keeps both lines and its gap`() {
        // p1 — e.g. an empty separator div (no 30dp floor in composed WPT).
        assertEquals(listOf(setOf(2), setOf(0)), dbl(Side.BOTTOM, 3f, Size(100f, 3f)))
    }

    @Test
    fun `a 6px bottom double on a 6px box keeps dev rows 0-1 and 4-5`() {
        // p2 — the box is exactly one band tall.
        assertEquals(listOf(setOf(4, 5), setOf(0, 1)), dbl(Side.BOTTOM, 6f, Size(100f, 6f)))
    }

    @Test
    fun `a 6px bottom double on a 9px box keeps dev rows 3-4 and 7-8`() {
        // p3 — extent 9 < 2·(5w/6) = 10: the inner line was 1 px low.
        assertEquals(listOf(setOf(7, 8), setOf(3, 4)), dbl(Side.BOTTOM, 6f, Size(100f, 9f)))
    }

    @Test
    fun `a 3px end double on a 3px wide box keeps dev columns 0 and 2`() {
        // p4 — the inline-axis twin of p1.
        assertEquals(listOf(setOf(2), setOf(0)), dbl(Side.END, 3f, Size(3f, 40f)))
    }

    @Test
    fun `a 4px bottom groove on a 4px box keeps its halves apart`() {
        // p5 — outer (border-edge) half {2,3}, inner half {0,1}; the
        // mirror had drawn the inner half over the outer one. Ridge shares
        // this plan (only the shades differ), so it is pinned too.
        assertEquals(setOf(2, 3) to setOf(0, 1), groove(Side.BOTTOM, 4f, Size(100f, 4f)))
    }

    @Test
    fun `a 3px bottom double on a 2px box paints the translated band`() {
        // p7 — 0 < extent < w: the band [0, 3) with its gap (dev painted
        // {-1, 1}, the mirror {1, 2} with no gap).
        assertEquals(listOf(setOf(2), setOf(0)), dbl(Side.BOTTOM, 3f, Size(100f, 2f)))
    }

    // ── the frozen-baseline guarantee, float-for-float ──────────────────

    @Test
    fun `every box at least one band tall gets the dev far-edge lines exactly`() {
        // dev: far-edge centre = extent − inset, insets line/2 & w − line/2
        // (double) and half/2 & half/2 + half (groove/ridge) — the painters'
        // own expressions, so equality is exact (delta 0).
        for (w in listOf(2f, 2.5f, 3f, 3.75f, 4f, 5f, 6f, 9f, 10f, 14f)) {
            var e = w                                            // one band tall …
            while (e <= w + 24f) {                               // … up to 24 px taller
                val box = Size(e, e)                             // same extent on both axes
                val line = w / 3f; val half = w / 2f
                val doubleInsets = listOf(line / 2f, w - line / 2f)
                val grooveInsets = listOf(half / 2f, half / 2f + half)
                for (s in listOf(Side.BOTTOM, Side.END)) {
                    // Cross-axis coordinate of a far-edge line.
                    fun c(l: Pair<Offset, Offset>) = if (s == Side.BOTTOM) l.first.y else l.first.x
                    if (w >= 3f) BorderSideApplier.doubleLines(s, w, box).zip(doubleInsets)
                        .forEach { (l, i) -> assertEquals("double w=$w e=$e $s", e - i, c(l), 0f) }
                    val (o, n) = BorderSideApplier.grooveRidgeLines(s, w, box)
                    assertEquals("groove outer w=$w e=$e $s", e - grooveInsets[0], c(o), 0f)
                    assertEquals("groove inner w=$w e=$e $s", e - grooveInsets[1], c(n), 0f)
                }
                e += 0.25f                                       // quarter-px steps
            }
        }
    }

    @Test
    fun `the single stroke is unchanged - both far-edge helpers agree at inset w-half`() {
        // farEdgeBandCentre(e, w, w/2) must equal T2's innerEdgeStrokeCentre
        // for EVERY extent, including the 0-tall and sub-band boxes T2 pins.
        for (w in listOf(1f, 2f, 2.5f, 3f, 3.75f, 5f, 10f, 14f)) {
            var e = 0f
            while (e <= 40f) {
                assertEquals("w=$w e=$e", BorderSideApplier.innerEdgeStrokeCentre(e, w / 2f),
                    BorderSideApplier.farEdgeBandCentre(e, w, w / 2f), 0f)
                e += 0.25f                                       // quarter-px steps
            }
        }
    }

    @Test
    fun `a deep inset on a squeezed box is translated not mirrored`() {
        // Groove w = 4 inner half (inset 3) on a 0-tall box: far edge at
        // w = 4, centre 1 — the mirror gave 3.
        assertEquals(1f, BorderSideApplier.farEdgeBandCentre(0f, 4f, 3f), 0f)
        // p3's inner line (w = 6, inset 5, extent 9): dev 4 — the mirror gave 5.
        assertEquals(4f, BorderSideApplier.farEdgeBandCentre(9f, 6f, 5f), 0f)
    }

    @Test
    fun `the near-edge sides ignore the band and the box`() {
        // TOP / START measure from the near edge: inset itself, any box.
        val (t0, _) = BorderSideApplier.doubleLines(Side.TOP, 6f, Size(100f, 0f))
        assertEquals(1f, t0.first.y, 0f)                         // outer line, inset line/2
        val (s0, s1) = BorderSideApplier.grooveRidgeLines(Side.START, 4f, Size(0f, 40f))
        assertEquals(1f, s0.first.x, 0f); assertEquals(3f, s1.first.x, 0f)
    }
}

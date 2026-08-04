package com.styleconverter.runtime.lists

// Wave 30, lane 3 — pin table for RowPitchAccumulator + the ACCUMULATED
// rounding it feeds (fix B3).
//
// ## The defect these pin against (measured on the LIVE wave29-final run)
// Every composed line box was quantised in isolation, so a 31.25px CSS box
// reported 31 on EVERY row and the error accumulated. First ink row of
// each row group of wpt__css-counter-styles__armenian__
// css3-counter-styles-007 (24 single-`<li>` `<ol>`s at `font-size: 25px`):
//
//   row | web | Android  | iOS
//    2  |  93 |  96 (+3) |  95 (+2)
//   10  | 343 | 344 (+1) | 345 (+2)
//   15  | 499 | 499 ( 0) | 501 (+2)
//   20  | 656 | 653 (−3) | 656 ( 0)
//   25  | 812 | 808 (−4) | 813 (+1)
//
// Android's offset marches −0.25px/row while iOS holds a constant +1..+2;
// web renders 27 ink groups and Android 26, the last row having fallen out
// of the 6px the document lost. Compose-only, deliberately — iOS already
// averages the reference pitch and has no twin of this file.

import org.junit.Assert.assertEquals
import org.junit.Test

class ComposedRowPitchTest {

    // ── the rounding rule ───────────────────────────────────────────────

    @Test
    fun `a 31_25px box stacks to the browsers sub-pixel positions`() {
        // The whole defect in one table: heights 31,32,31,31 repeating
        // (Math.round is half-UP, so the 62.5 boundary rounds to 63), and
        // row k's TOP landing on round(k × 31.25) instead of 31k.
        var top = 0
        val heights = (0 until 24).map { k ->
            val h = ListMarkerLineBox.snappedHeightPx(
                prefixExactPx = k * 31.25f, lineCount = 1, refLineBoxPx = 31.25f)!!
            assertEquals("row $k top", Math.round(k * 31.25f), top)
            top += h
            h
        }
        // 24 rows of the OLD isolated rounding total 744; the exact total
        // is 750, and the browser's is 750.
        assertEquals(750, heights.sum())
        assertEquals(listOf(31, 32, 31, 31), heights.take(4))
    }

    @Test
    fun `the first row is byte-identical to the isolated rounding`() {
        // prefix 0 ⇒ the pre-wave-30 expression, so a container with a
        // single marker row (every css-lists `<ul>`, and the whole
        // 327-pair dark stage) cannot move.
        for (box in listOf(18f, 20f, 31.25f, 33.8f)) {
            assertEquals(ListMarkerLineBox.snappedHeightPx(1, box),
                ListMarkerLineBox.snappedHeightPx(0f, 1, box))
        }
    }

    @Test
    fun `a pre-settle row still reports its natural height`() {
        assertEquals(null, ListMarkerLineBox.snappedHeightPx(0f, 0, 31.25f))
        assertEquals(null, ListMarkerLineBox.snappedHeightPx(93.75f, -1, 31.25f))
    }

    @Test
    fun `a wrapped marker snaps to N boxes from wherever it starts`() {
        // lineCount is READ, never assumed to be 1 — a marker that wraps
        // must claim N line boxes, and the accumulated rule must respect
        // that too.
        assertEquals(63, ListMarkerLineBox.snappedHeightPx(31.25f, 2, 31.25f))
    }

    // ── the accumulator's ordering contract ─────────────────────────────

    @Test
    fun `the prefix is the exact total of every row registered before`() {
        val acc = RowPitchAccumulator()
        assertEquals(0f, acc.prefixFor("a", 31.25f), 0f)
        assertEquals(31.25f, acc.prefixFor("b", 31.25f), 0f)
        assertEquals(62.5f, acc.prefixFor("c", 31.25f), 0f)
        assertEquals(3, acc.size)
    }

    @Test
    fun `re-registering a key is idempotent`() {
        // The property the ordering contract rests on: Compose may measure
        // a subtree more than once (intrinsics, settle frame,
        // recomposition), and a second pass must re-read the same prefix
        // rather than append the row again.
        val acc = RowPitchAccumulator()
        acc.prefixFor("a", 31.25f)
        acc.prefixFor("b", 31.25f)
        assertEquals(0f, acc.prefixFor("a", 31.25f), 0f)
        assertEquals(31.25f, acc.prefixFor("b", 31.25f), 0f)
        assertEquals(2, acc.size)
    }

    @Test
    fun `a settle frame may correct a rows height without reordering it`() {
        // The pre-settle 0-line frame never registers (the snap gates on
        // lineCount > 0), but a marker that RE-lays out at a different
        // line count must update in place — its followers then read the
        // corrected prefix, and it keeps its position in the order.
        val acc = RowPitchAccumulator()
        acc.prefixFor("a", 31.25f)
        acc.prefixFor("b", 31.25f)
        acc.prefixFor("a", 62.5f)
        assertEquals(62.5f, acc.prefixFor("b", 31.25f), 0f)
        assertEquals(2, acc.size)
    }
}

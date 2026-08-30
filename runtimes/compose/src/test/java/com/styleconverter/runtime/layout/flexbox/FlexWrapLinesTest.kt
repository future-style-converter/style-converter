package com.styleconverter.runtime.layout.flexbox

// Wave 25 lane CFLEX — CAL-RC5 pins for the pure line geometry
// (css-flexbox-1 §9.3 line collection + §9.4 step 8 align-content stretch).
//
// The two shapes named below are the WPT fixtures that motivated the fix;
// they are reproduced here as plain integers so the geometry is pinned
// without an emulator.

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlexWrapLinesTest {

    // --- §9.3 line collection ------------------------------------------------

    @Test fun `gap counts against the line budget`() {
        // css-gaps flex-gap-decorations-002: four 50px items, 10px gap,
        // 110px container ⇒ exactly two per line (50+10+50 == 110).
        val lines = FlexWrapLines.breakLines(intArrayOf(50, 50, 50, 50), 110, 10)
        assertEquals(listOf(FlexWrapLines.Line(0, 1), FlexWrapLines.Line(2, 3)), lines)
    }

    @Test fun `a gap that overflows forces the earlier break`() {
        // Same items in a 109px container: the second item no longer fits
        // once the gap is charged, so each line holds one item. This is the
        // case a gap-blind line breaker gets wrong.
        val lines = FlexWrapLines.breakLines(intArrayOf(50, 50), 109, 10)
        assertEquals(listOf(FlexWrapLines.Line(0, 0), FlexWrapLines.Line(1, 1)), lines)
    }

    @Test fun `flex-gap-decorations-001 packs two 45px items per line`() {
        val lines = FlexWrapLines.breakLines(intArrayOf(45, 45, 45, 45), 100, 10)
        assertEquals(listOf(FlexWrapLines.Line(0, 1), FlexWrapLines.Line(2, 3)), lines)
    }

    @Test fun `an oversized item still gets its own line`() {
        // §9.3: a line always takes at least one item, however big.
        val lines = FlexWrapLines.breakLines(intArrayOf(500, 20), 100, 0)
        assertEquals(listOf(FlexWrapLines.Line(0, 0), FlexWrapLines.Line(1, 1)), lines)
    }

    @Test fun `an unbounded container never wraps`() {
        val lines = FlexWrapLines.breakLines(intArrayOf(500, 500, 500), Int.MAX_VALUE, 10)
        assertEquals(listOf(FlexWrapLines.Line(0, 2)), lines)
    }

    @Test fun `no items means no lines`() {
        assertEquals(emptyList<FlexWrapLines.Line>(), FlexWrapLines.breakLines(IntArray(0), 100, 10))
    }

    @Test fun `line count exposes the gap multiplier`() {
        assertEquals(2, FlexWrapLines.Line(0, 1).count)
        assertEquals(1, FlexWrapLines.Line(3, 3).count)
    }

    // --- §9.4 step 8 align-content stretch -----------------------------------

    @Test fun `definite cross size stretches zero-height lines`() {
        // flex-gap-decorations-001: two lines of width-only children (auto
        // height ⇒ hypothetical cross 0) in a 100px box with a 10px row-gap
        // ⇒ 45px per line. Before this rule both lines were 0 and the
        // Android capture came back empty.
        assertArrayEquals(
            intArrayOf(45, 45),
            FlexWrapLines.stretchLines(intArrayOf(0, 0), 100, 10)
        )
    }

    @Test fun `flex-gap-decorations-002 stretches to 50px lines`() {
        assertArrayEquals(
            intArrayOf(50, 50),
            FlexWrapLines.stretchLines(intArrayOf(0, 0), 110, 10)
        )
    }

    @Test fun `stretch adds to whatever the items already claim`() {
        // Lines that already have content grow by an equal share, they are
        // not flattened to a uniform size.
        assertArrayEquals(
            intArrayOf(30, 20),
            FlexWrapLines.stretchLines(intArrayOf(20, 10), 50, 0)
        )
    }

    @Test fun `the remainder lands on the leading lines so the sum is exact`() {
        val out = FlexWrapLines.stretchLines(intArrayOf(0, 0, 0), 100, 0)
        assertArrayEquals(intArrayOf(34, 33, 33), out)
        assertEquals(100, out.sum())
    }

    @Test fun `an auto cross size leaves the lines hugging their items`() {
        val base = intArrayOf(20, 10)
        assertArrayEquals(base, FlexWrapLines.stretchLines(base, null, 10))
    }

    @Test fun `overflowing lines are never compressed`() {
        // Negative leftover: CSS lets the content spill, it does not shrink
        // the lines to fit.
        val base = intArrayOf(80, 80)
        assertArrayEquals(base, FlexWrapLines.stretchLines(base, 100, 10))
    }

    @Test fun `a single line absorbs the whole definite cross size`() {
        assertArrayEquals(intArrayOf(100), FlexWrapLines.stretchLines(intArrayOf(30), 100, 10))
    }

    // ---- wave 47 (lane Z7): §9.6 line-block POSITIONING offsets ----

    @Test fun `null distribution packs lines at the cross start`() {
        // The pre-wave-47 accumulation, bit for bit: 0, 30+5, 30+5+30+5.
        assertArrayEquals(
            intArrayOf(0, 35, 70),
            FlexWrapLines.lineCrossOffsets(intArrayOf(30, 30, 30), 200, 5, null)
        )
    }

    @Test fun `space-between distributes the leftover into the line gaps`() {
        // WPT flex-gap-decorations-047 shape: 3×40px lines in a 200px
        // box, no row-gap → free 80 → two distributed 40px gaps, lines
        // at 0 / 80 / 160 (frozen-ref rows 16-55 / 96-135 / 176-215
        // minus the 16px canvas pad).
        assertArrayEquals(
            intArrayOf(0, 80, 160),
            FlexWrapLines.lineCrossOffsets(
                intArrayOf(40, 40, 40), 200, 0,
                FlexWrapLines.CrossDistribution.SPACE_BETWEEN
            )
        )
    }

    @Test fun `space-around halves the edge share`() {
        // 048 shape: free 80 over 3 lines → between 26.67, lead 13.33 →
        // rounded line starts 13 / 80 / 147 (ref rows 29/96/163 − 16).
        assertArrayEquals(
            intArrayOf(13, 80, 147),
            FlexWrapLines.lineCrossOffsets(
                intArrayOf(40, 40, 40), 200, 0,
                FlexWrapLines.CrossDistribution.SPACE_AROUND
            )
        )
    }

    @Test fun `space-evenly equalizes all four gaps`() {
        // 049 shape: free 80 over 4 gaps → 20 each → 20 / 80 / 140
        // (ref rows 37/97/157 − 16, ±1 subpixel).
        assertArrayEquals(
            intArrayOf(20, 80, 140),
            FlexWrapLines.lineCrossOffsets(
                intArrayOf(40, 40, 40), 200, 0,
                FlexWrapLines.CrossDistribution.SPACE_EVENLY
            )
        )
    }

    @Test fun `center and end position the block, gaps intact`() {
        // Two 30px lines + 10px row-gap in a 150px box → block 70, free 80.
        assertArrayEquals(
            intArrayOf(40, 80),
            FlexWrapLines.lineCrossOffsets(
                intArrayOf(30, 30), 150, 10, FlexWrapLines.CrossDistribution.CENTER
            )
        )
        assertArrayEquals(
            intArrayOf(80, 120),
            FlexWrapLines.lineCrossOffsets(
                intArrayOf(30, 30), 150, 10, FlexWrapLines.CrossDistribution.END
            )
        )
    }

    @Test fun `hugging or overflowing containers pack under every keyword`() {
        // No definite cross → nothing to distribute into.
        assertArrayEquals(
            intArrayOf(0, 40),
            FlexWrapLines.lineCrossOffsets(
                intArrayOf(40, 40), null, 0, FlexWrapLines.CrossDistribution.SPACE_BETWEEN
            )
        )
        // Negative free (overflow) packs — CSS spills, never compresses.
        assertArrayEquals(
            intArrayOf(0, 40),
            FlexWrapLines.lineCrossOffsets(
                intArrayOf(40, 40), 50, 0, FlexWrapLines.CrossDistribution.CENTER
            )
        )
    }

    // --- wave 48 lane W7: column-direction wrap geometry (transposed axis) ---
    //
    // The SAME pure helpers serve FlexWrapColumn on the transposed axis;
    // these pins are the verbatim wave48-cal css-gaps numbers, cross-checked
    // pixel-by-pixel against the frozen Chromium captures (web column of the
    // wave48-cal css-gaps section — content box starts at canvas x=18/y=18).

    @Test fun `045 line collection - nine 70px items in a 400px column budget`() {
        // flex-gap-decorations-045: content height 400, row-gap 0 → five
        // items per column (5×70 = 350 ≤ 400; a sixth would need 420).
        val lines = FlexWrapLines.breakLines(IntArray(9) { 70 }, 400, 0)
        assertEquals(listOf(FlexWrapLines.Line(0, 4), FlexWrapLines.Line(5, 8)), lines)
    }

    @Test fun `045 line stretch - integer split hands the remainder forward`() {
        // 045: content width 120, two 50px lines, column-gap 5 → leftover
        // 120−100−5 = 15 → share 7, remainder 1 → [58, 57]. Chromium lays
        // out 57.5/57.5 subpixel; the leading-remainder split reproduces
        // its RASTER exactly: line 2 starts at 58+5 = 63 → canvas x 81,
        // where the web capture's second-column items sit (blue 81..130).
        assertArrayEquals(
            intArrayOf(58, 57),
            FlexWrapLines.stretchLines(intArrayOf(50, 50), 120, 5)
        )
        // Packed offsets after stretch: [0, 63] — the browser's line
        // origins (and the 5px column-rule band lands at [58, 63] →
        // canvas x 76..80, exactly the web capture's red rule).
        assertArrayEquals(
            intArrayOf(0, 63),
            FlexWrapLines.lineCrossOffsets(intArrayOf(58, 57), 120, 5, null)
        )
    }

    @Test fun `043 zero leftover keeps the packed FlowColumn geometry`() {
        // flex-gap-decorations-043: content width 100, two 50px columns,
        // no column-gap → leftover 0, stretch is the identity and the
        // packed offsets equal FlowColumn's [0, 50] — the routing change
        // must not move this passing capture by a pixel.
        val base = intArrayOf(50, 50)
        assertArrayEquals(base, FlexWrapLines.stretchLines(base, 100, 0))
        assertArrayEquals(
            intArrayOf(0, 50),
            FlexWrapLines.lineCrossOffsets(base, 100, 0, null)
        )
        // And the column collection itself: 9×70 in 400 → [0..4][5..8].
        assertEquals(
            listOf(FlexWrapLines.Line(0, 4), FlexWrapLines.Line(5, 8)),
            FlexWrapLines.breakLines(IntArray(9) { 70 }, 400, 0)
        )
    }

    @Test fun `015 exact-fit columns stretch to identity`() {
        // flex-gap-decorations-015 (passing today): 6×50px items, 170px
        // budget, row-gap 10 → three per column (50+10+50+10+50 = 170);
        // width 120, column-gap 20 → leftover 120−100−20 = 0 → identity.
        assertEquals(
            listOf(FlexWrapLines.Line(0, 2), FlexWrapLines.Line(3, 5)),
            FlexWrapLines.breakLines(IntArray(6) { 50 }, 170, 10)
        )
        assertArrayEquals(
            intArrayOf(50, 50),
            FlexWrapLines.stretchLines(intArrayOf(50, 50), 120, 20)
        )
    }

    @Test fun `046 row-axis line stretch - the same step 8 on the block axis`() {
        // flex-gap-decorations-046 (ROW direction): three 50px lines,
        // content height 180, row-gap 5 → leftover 180−150−10 = 20 →
        // share 6, remainder 2 → [57, 57, 56]; packed line starts
        // [0, 62, 124] vs Chromium's subpixel 0/61.67/123.3 (canvas rows
        // 16/78/139 in the web capture, ±1 raster rounding).
        assertArrayEquals(
            intArrayOf(57, 57, 56),
            FlexWrapLines.stretchLines(intArrayOf(50, 50, 50), 180, 5)
        )
        assertArrayEquals(
            intArrayOf(0, 62, 124),
            FlexWrapLines.lineCrossOffsets(intArrayOf(57, 57, 56), 180, 5, null)
        )
    }

    @Test fun `line-stretch routing gate - stretch keyword with definite cross only`() {
        // The 045/046 route: plain wrap + stretching align-content +
        // definite cross.
        assertTrue(FlexWrapLines.routesForLineStretch(
            plainWrap = true, alignContentStretches = true, hasDefiniteCross = true))
        // wrap-reverse stays on the frozen Flow* paths (no reverse
        // ordering in the wrap layouts — both wave48-cal wrap-reverse
        // column tests pass there today).
        assertFalse(FlexWrapLines.routesForLineStretch(
            plainWrap = false, alignContentStretches = true, hasDefiniteCross = true))
        // Positioning keywords route via their own crossDistribution arm,
        // not this gate (css-align-3 §5.3: they leave the leftover free).
        assertFalse(FlexWrapLines.routesForLineStretch(
            plainWrap = true, alignContentStretches = false, hasDefiniteCross = true))
        // A hugging container has no leftover for step 8 to hand out.
        assertFalse(FlexWrapLines.routesForLineStretch(
            plainWrap = true, alignContentStretches = true, hasDefiniteCross = false))
    }
}

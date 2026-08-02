package com.styleconverter.runtime.layout.flexbox

// TEMPORARY adversarial probe file (wave-25 skeptic pass on lane CFLEX).
// Executes edges the lane's own suites do NOT cover. Delete before landing
// if the lane does not want to keep them.

import androidx.compose.foundation.layout.Arrangement
import com.styleconverter.runtime.layout.AlignmentKeyword
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZZSkepticFlexProbeTest {

    // ---- EDGE 1: Constraints packing at large pinned main sizes ----------
    // mainSizePinConstraints builds Constraints(main, main, ...). Compose
    // packs both axes into one Long with a bucketed bit budget; a large
    // pinned width alongside an infinite height band can exceed it.
    @Test
    fun `pin survives a large main size against an infinite cross band`() {
        val incoming = Constraints(0, 400, 0, Constraints.Infinity)
        val c = mainSizePinConstraints(incoming, 100_000, horizontal = true)
        assertEquals(100_000, c.minWidth)
        assertEquals(100_000, c.maxWidth)
        assertTrue(!c.hasBoundedHeight)
    }

    @Test
    fun `pin survives a large main size against a bounded cross band`() {
        val incoming = Constraints(0, 400, 0, 60_000)
        val c = mainSizePinConstraints(incoming, 60_000, horizontal = false)
        assertEquals(60_000, c.minHeight)
        assertEquals(60_000, c.maxHeight)
    }

    // ---- EDGE 2: §9.3 with a gap that alone blows the budget -------------
    @Test
    fun `a gap wider than the leftover forces a break even for tiny items`() {
        // 10 + gap10 + 10 = 30 > 15 → second item starts a new line.
        val lines = FlexWrapLines.breakLines(intArrayOf(10, 10), containerMain = 15, gap = 10)
        assertEquals(2, lines.size)
        assertEquals(FlexWrapLines.Line(0, 0), lines[0])
        assertEquals(FlexWrapLines.Line(1, 1), lines[1])
    }

    @Test
    fun `a zero-width container still puts one item per line`() {
        val lines = FlexWrapLines.breakLines(intArrayOf(5, 5, 5), containerMain = 0, gap = 0)
        assertEquals(3, lines.size)
    }

    // ---- EDGE 3: the live 001 shape, end to end through the pure half ----
    @Test
    fun `flex-gap-decorations-001 breaks two-per-line and stretches to 45px`() {
        val lines = FlexWrapLines.breakLines(intArrayOf(45, 45, 45, 45), 100, 10)
        assertEquals(2, lines.size)
        assertEquals(FlexWrapLines.Line(0, 1), lines[0])
        assertEquals(FlexWrapLines.Line(2, 3), lines[1])
        val cross = FlexWrapLines.stretchLines(intArrayOf(0, 0), containerCross = 100, gap = 10)
        assertEquals(45, cross[0])
        assertEquals(45, cross[1])
    }

    // ---- EDGE 4: stretch arithmetic boundaries ---------------------------
    @Test
    fun `an exactly-filled container leaves the lines alone`() {
        val base = intArrayOf(40, 50)
        val out = FlexWrapLines.stretchLines(base, containerCross = 100, gap = 10)
        assertEquals(40, out[0]); assertEquals(50, out[1])
    }

    @Test
    fun `gaps alone can consume the whole cross size without shrinking lines`() {
        val out = FlexWrapLines.stretchLines(intArrayOf(10, 10), containerCross = 15, gap = 20)
        assertEquals(10, out[0]); assertEquals(10, out[1])
    }

    @Test
    fun `a leftover smaller than the line count still sums exactly`() {
        val out = FlexWrapLines.stretchLines(intArrayOf(0, 0, 0), containerCross = 2, gap = 0)
        assertEquals(2, out.sum())
        assertEquals(1, out[0]); assertEquals(1, out[1]); assertEquals(0, out[2])
    }

    // ---- EDGE 4b: the align-content precondition on §9.4 step 8 ---------
    // Skeptic fix pin: `align-content: center` with a definite height must
    // NOT grow the lines (flexbox-baseline-multi-line-horiz-003/004).
    @Test
    fun `step 8 needs both a definite cross size and a stretching align-content`() {
        assertEquals(
            100,
            FlexWrapLines.definiteCrossOrNull(
                alignContentStretches = true, hasFixedCross = true, maxCross = 100
            )
        )
        assertEquals(
            null,
            FlexWrapLines.definiteCrossOrNull(
                alignContentStretches = false, hasFixedCross = true, maxCross = 100
            )
        )
        assertEquals(
            null,
            FlexWrapLines.definiteCrossOrNull(
                alignContentStretches = true, hasFixedCross = false, maxCross = 100
            )
        )
        // …and a null precondition leaves the lines exactly as step 7 sized
        // them, so the two `align-content: center` fixtures keep the
        // content-sized lines the FlowRow path already gave them.
        val base = intArrayOf(20, 20)
        assertEquals(
            listOf(20, 20),
            FlexWrapLines.stretchLines(base, containerCross = null, gap = 10).toList()
        )
    }

    // ---- EDGE 4c: the RC4 premise, executed against the live resolver ---
    // The lane asserts §9.7 freezes the 008 shape at [50]×6 and that the
    // renderer's pin is therefore the only thing standing between the IR
    // and a correct overflow. Confirm the resolver half rather than take
    // it on trust — if it ever returned shrunk sizes, the pin would be
    // pinning the WRONG number and the capture would still be wrong.
    @Test
    fun `the 008 shape freezes at six 50px items and overflows by 154px`() {
        val items = List(6) {
            FlexSizeResolver.Item(basisPx = 50.0, grow = 0.0, shrink = 0.0, minPx = 0.0)
        }
        val out = FlexSizeResolver.resolve(contentMainPx = 196.0, gapPx = 10.0, items = items)
        assertEquals(List(6) { 50.0 }, out)
        // 6×50 + 5×10 = 350 used against a 196px content box.
        val used = out!!.sum() + 10.0 * 5
        assertEquals(350.0, used, 0.001)
        assertTrue(used > 196.0)
        // …and the pin hands that number through even when the Row has
        // nothing left to offer (the tail item's incoming maxWidth is 0).
        val pinned = mainSizePinConstraints(Constraints(0, 0, 0, 200), 50, horizontal = true)
        assertEquals(50, pinned.minWidth)
        assertEquals(50, pinned.maxWidth)
    }

    // ---- EDGE 5: RC2 byte-stability at 0dp for EVERY keyword, both axes --
    // The lane pins this; re-run independently against the pre-fold mapper
    // so a future edit to mainAxisHorizontal cannot silently move the 327.
    @Test
    fun `zero-gap fold is the identity of the pre-fold mapper for all keywords`() {
        for (kw in AlignmentKeyword.values()) {
            val d = FlexDecision(
                kind = FlexContainerKind.FlowRow,
                horizontalArrangement = FlexboxApplier.toHorizontalArrangement(kw),
                verticalArrangement = FlexboxApplier.toVerticalArrangement(kw),
                horizontalAlignment = FlexboxApplier.toHorizontalAlignment(kw),
                verticalAlignment = FlexboxApplier.toVerticalAlignment(kw),
                boxAlignment = FlexboxApplier.toBoxAlignment(kw),
                reverse = false,
                justify = kw
            )
            val axes = FlexAxes.of(d, 0.dp, 0.dp)
            assertEquals("h/$kw", d.horizontalArrangement, axes.mainHorizontal)
            assertEquals("v/$kw", d.verticalArrangement, axes.mainVertical)
        }
    }

    // ---- EDGE 6: the SURVIVING gap-free arrangement ----------------------
    // The lane claims "no branch can reach a gap-free main-axis arrangement
    // any more". Probe that claim directly: FlowRow breaks lines using
    // `arrangement.spacing`, and the Space* keywords carry spacing == 0
    // even with a declared gap, so a wrapping row with
    // `justify-content: space-between; column-gap: 10px` still line-breaks
    // as if the gap were zero. Documented here as the residual defect.
    @Test
    fun `space-between still hands FlowRow a zero spacing despite the gap`() {
        val d = FlexDecision(
            kind = FlexContainerKind.FlowRow,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = FlexboxApplier.toHorizontalAlignment(AlignmentKeyword.Normal),
            verticalAlignment = FlexboxApplier.toVerticalAlignment(AlignmentKeyword.Normal),
            boxAlignment = FlexboxApplier.toBoxAlignment(AlignmentKeyword.Normal),
            reverse = false,
            justify = AlignmentKeyword.SpaceBetween
        )
        val axes = FlexAxes.of(d, 0.dp, 10.dp)
        val spacing = (axes.mainHorizontal as Arrangement.HorizontalOrVertical).spacing
        // RESIDUAL DEFECT, not a desired contract: FlowRow's line breaking
        // reads this value, so the 10px column-gap is invisible to it.
        assertEquals(0.dp, spacing)
        // Positional keywords DO carry it — that half of RC2 works.
        val d2 = d.copy(justify = AlignmentKeyword.FlexStart)
        val axes2 = FlexAxes.of(d2, 0.dp, 10.dp)
        assertEquals(10.dp, (axes2.mainHorizontal as Arrangement.HorizontalOrVertical).spacing)
    }
}

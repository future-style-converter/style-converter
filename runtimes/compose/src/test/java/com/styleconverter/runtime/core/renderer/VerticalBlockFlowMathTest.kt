package com.styleconverter.runtime.core.renderer

// Wave-47 lane Z2 — pins the vertical block-flow placement table
// (VerticalBlockFlowMath.positions): under vertical-rl the first child sits
// at the container's RIGHT edge and flow walks leftward (css-writing-modes-4
// §6.4), anchored at the container's USED block extent. Reference numbers
// from css-break background-image-001: four .mc boxes, each 122px border box
// + 20px block-end (left) margin = 142px margin box, container content
// extent 4×142 = 568 (browser: .mc4 content at x 20..142, .mc3 at 162..284).

import org.junit.Assert.assertEquals
import org.junit.Test

class VerticalBlockFlowMathTest {

    @Test
    fun `vertical-rl - first child at the right edge, flow walks leftward`() {
        // Margin boxes 142 wide each, anchor = content extent 568:
        // child 0 at 568−142 = 426 (its 20px left margin puts content at
        // 446..568 — the browser's .mc1), child 3 at 0 (content 20..142).
        assertEquals(
            listOf(426, 284, 142, 0),
            VerticalBlockFlowMath.positions(
                widths = listOf(142, 142, 142, 142), anchorWidth = 568, blockRtl = true))
    }

    @Test
    fun `vertical-lr - flow walks rightward from zero`() {
        assertEquals(
            listOf(0, 142, 284, 426),
            VerticalBlockFlowMath.positions(
                widths = listOf(142, 142, 142, 142), anchorWidth = 568, blockRtl = false))
    }

    @Test
    fun `vertical-rl with a pinned narrower box - overflow escapes past block-end (left)`() {
        // Author-pinned 200px box, content 3×100: block-start stays at the
        // right edge, the third child lands at −100 (overflow LEFT — the
        // block-end side), exactly the browser's escape direction.
        assertEquals(
            listOf(100, 0, -100),
            VerticalBlockFlowMath.positions(
                widths = listOf(100, 100, 100), anchorWidth = 200, blockRtl = true))
    }

    @Test
    fun `mixed widths accumulate their own extents`() {
        assertEquals(
            listOf(0, 50, 180),
            VerticalBlockFlowMath.positions(
                widths = listOf(50, 130, 70), anchorWidth = 250, blockRtl = false))
        assertEquals(
            listOf(200, 70, 0),
            VerticalBlockFlowMath.positions(
                widths = listOf(50, 130, 70), anchorWidth = 250, blockRtl = true))
    }
}

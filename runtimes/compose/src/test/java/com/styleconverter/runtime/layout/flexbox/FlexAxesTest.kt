package com.styleconverter.runtime.layout.flexbox

// Wave 25 lane CFLEX — CAL-RC2 pins.
//
// The defect was invisible to type checking: FlowRow was handed a valid
// Arrangement.Horizontal, just the wrong one (justify-only, gap dropped).
// So these tests assert BEHAVIOUR — they run the arrangement's own
// arrange() and check the item offsets — rather than comparing arrangement
// identities, which would pass for any object that merely exists.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.layout.AlignmentKeyword
import com.styleconverter.runtime.layout.DisplayKind
import com.styleconverter.runtime.layout.FlexDirection
import com.styleconverter.runtime.layout.FlexWrap
import com.styleconverter.runtime.layout.LayoutConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FlexAxesTest {

    // Density 1 keeps dp == px so the expected offsets read as CSS pixels.
    private val density = Density(1f)

    /** Run a horizontal arrangement the way Compose's Row/FlowRow does. */
    private fun arrangeH(a: Arrangement.Horizontal, total: Int, sizes: IntArray): IntArray {
        val out = IntArray(sizes.size)
        with(density) { with(a) { arrange(total, sizes, LayoutDirection.Ltr, out) } }
        return out
    }

    /** Vertical twin — no layout direction on the block axis. */
    private fun arrangeV(a: Arrangement.Vertical, total: Int, sizes: IntArray): IntArray {
        val out = IntArray(sizes.size)
        with(density) { with(a) { arrange(total, sizes, out) } }
        return out
    }

    private fun wrappingRow(justify: AlignmentKeyword = AlignmentKeyword.Normal): FlexDecision =
        FlexboxApplier.decide(
            LayoutConfig(
                display = DisplayKind.Flex,
                flexWrap = FlexWrap.Wrap,
                justifyContent = justify
            )
        )!!

    private fun wrappingColumn(): FlexDecision =
        FlexboxApplier.decide(
            LayoutConfig(
                display = DisplayKind.Flex,
                flexDirection = FlexDirection.Column,
                flexWrap = FlexWrap.Wrap
            )
        )!!

    // --- the defect itself ---------------------------------------------------

    @Test fun `wrapping row main axis carries column-gap`() {
        val d = wrappingRow()
        assertEquals(FlexContainerKind.FlowRow, d.kind)
        val axes = FlexAxes.of(d, rowGap = 4.dp, columnGap = 10.dp)
        // Three 50px items in a 200px line: 0, 60, 120 — NOT 0, 50, 100,
        // which is what flexDecision.horizontalArrangement produced.
        assertArrayEquals(
            intArrayOf(0, 60, 120),
            arrangeH(axes.mainHorizontal, 200, intArrayOf(50, 50, 50))
        )
    }

    @Test fun `wrapping row cross axis carries row-gap`() {
        val axes = FlexAxes.of(wrappingRow(), rowGap = 12.dp, columnGap = 10.dp)
        // Two 30px lines stacked with the block-axis gap between them.
        assertArrayEquals(
            intArrayOf(0, 42),
            arrangeV(axes.crossVertical, 100, intArrayOf(30, 30))
        )
    }

    @Test fun `wrapping column main axis carries row-gap`() {
        val d = wrappingColumn()
        assertEquals(FlexContainerKind.FlowColumn, d.kind)
        val axes = FlexAxes.of(d, rowGap = 8.dp, columnGap = 10.dp)
        assertArrayEquals(
            intArrayOf(0, 38, 76),
            arrangeV(axes.mainVertical, 200, intArrayOf(30, 30, 30))
        )
    }

    @Test fun `wrapping column cross axis carries column-gap`() {
        val axes = FlexAxes.of(wrappingColumn(), rowGap = 8.dp, columnGap = 6.dp)
        assertArrayEquals(
            intArrayOf(0, 56),
            arrangeH(axes.crossHorizontal, 200, intArrayOf(50, 50))
        )
    }

    // --- the four call sites cannot diverge ----------------------------------

    @Test fun `row and wrapping row share one main-axis arrangement`() {
        // Same justify + same gap ⇒ the non-wrapping Row and the FlowRow
        // read the SAME field, so they cannot drift apart again.
        val nowrap = FlexboxApplier.decide(LayoutConfig(display = DisplayKind.Flex))!!
        val wrap = wrappingRow()
        val a = FlexAxes.of(nowrap, 4.dp, 10.dp).mainHorizontal
        val b = FlexAxes.of(wrap, 4.dp, 10.dp).mainHorizontal
        assertArrayEquals(
            arrangeH(a, 200, intArrayOf(50, 50)),
            arrangeH(b, 200, intArrayOf(50, 50))
        )
    }

    // --- distribution keywords still win over the gap ------------------------

    @Test fun `space-between keeps distributing with a gap declared`() {
        val axes = FlexAxes.of(wrappingRow(AlignmentKeyword.SpaceBetween), 4.dp, 10.dp)
        // css-align-3 §8: distributed spacing (50px here) exceeds the
        // 10px gap, so the Space* arrangement stands — the wave-2
        // FC_SpaceBetween regression must not come back through this fold.
        assertArrayEquals(
            intArrayOf(0, 100, 200),
            arrangeH(axes.mainHorizontal, 250, intArrayOf(50, 50, 50))
        )
    }

    // --- dark-stage byte stability -------------------------------------------

    @Test fun `zero gaps reproduce the pre-fold arrangements exactly`() {
        // Every keyword, both axes: with no gap the folded arrangement is
        // the identical object the old flexDecision fields carried, so a
        // gap-free container's layout is untouched by this change.
        for (kw in AlignmentKeyword.values()) {
            val d = wrappingRow(kw)
            val axes = FlexAxes.of(d, 0.dp, 0.dp)
            assertEquals(kw.toString(), d.horizontalArrangement, axes.mainHorizontal)
            assertEquals(kw.toString(), d.verticalArrangement, axes.mainVertical)
        }
    }
}

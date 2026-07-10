package com.styleconverter.runtime.layout.flexbox

// Wave-2 pinning tests for the css-flexbox-1 §9.7 static resolver.
//
// Every expected number below is PIXEL-VERIFIED against the wave-2 web
// captures (tools/visual reference renders):
//   FR_GrowBasis  web: a=50  b≈93  c≈145  (container 320, pad 8, gap 8)
//   FR_ShrinkBasis web: a=100 b=58  c=50   (container 240, pad 8, gap 8)
//   FC_GrowBasis  web: a=30  b≈66  c≈101  (container 220, pad 6, gap 6)
// The 50px/30px minimums mirror the web harness placeholder floor
// (minWidth 50 / minHeight 30 on unsized children).

import com.styleconverter.runtime.layout.flexbox.FlexSizeResolver.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlexSizeResolverTest {

    private fun assertSizes(expected: List<Double>, actual: List<Double>?, eps: Double = 0.51) {
        val got = requireNotNull(actual) { "resolver returned null" }
        assertEquals(expected.size, got.size)
        expected.zip(got).forEachIndexed { i, (e, a) ->
            org.junit.Assert.assertEquals("item $i", e, a, eps)
        }
    }

    @Test
    fun `FR_GrowBasis - grow distributes free space over bases, min floor clamps a`() {
        // content = 320 − 16 padding = 304; bases 40 each; gaps 16.
        // a (grow 0) freezes at max(40, min 50) = 50 → free 158 over b:c=1:2.
        val sizes = FlexSizeResolver.resolve(
            contentMainPx = 304.0, gapPx = 8.0,
            items = listOf(
                Item(basisPx = 40.0, grow = 0.0, shrink = 1.0, minPx = 50.0),
                Item(basisPx = 40.0, grow = 1.0, shrink = 1.0, minPx = 50.0),
                Item(basisPx = 40.0, grow = 2.0, shrink = 1.0, minPx = 50.0)
            )
        )
        assertSizes(listOf(50.0, 92.67, 145.33), sizes)
    }

    @Test
    fun `FR_ShrinkBasis - scaled shrink with min violation redistribution`() {
        // content = 240 − 16 = 224; bases 100 each; gaps 16 → free −92.
        // a (shrink 0) frozen at 100. First pass: b→77, c→31 (violates 50)
        // → c frozen at 50, second pass gives b = 58. Web: 100/58/50.
        val sizes = FlexSizeResolver.resolve(
            contentMainPx = 224.0, gapPx = 8.0,
            items = listOf(
                Item(basisPx = 100.0, grow = 0.0, shrink = 0.0, minPx = 50.0),
                Item(basisPx = 100.0, grow = 0.0, shrink = 1.0, minPx = 50.0),
                Item(basisPx = 100.0, grow = 0.0, shrink = 3.0, minPx = 50.0)
            )
        )
        assertSizes(listOf(100.0, 58.0, 50.0), sizes)
    }

    @Test
    fun `FC_GrowBasis - column axis growth`() {
        // content = 220 − 12 = 208; bases 30 each; gaps 12 → free 106 over 1:2.
        val sizes = FlexSizeResolver.resolve(
            contentMainPx = 208.0, gapPx = 6.0,
            items = listOf(
                Item(basisPx = 30.0, grow = 0.0, shrink = 1.0, minPx = 30.0),
                Item(basisPx = 30.0, grow = 1.0, shrink = 1.0, minPx = 30.0),
                Item(basisPx = 30.0, grow = 2.0, shrink = 1.0, minPx = 30.0)
            )
        )
        assertSizes(listOf(30.0, 65.33, 100.67), sizes)
    }

    @Test
    fun `content-sized basis makes the line unresolvable`() {
        // flex-basis auto without a main size → null → renderer falls back
        // to the legacy weight path (wave-1 behaviour preserved).
        assertNull(
            FlexSizeResolver.resolve(
                contentMainPx = 300.0, gapPx = 0.0,
                items = listOf(
                    Item(basisPx = null, grow = 1.0, shrink = 1.0, minPx = 50.0),
                    Item(basisPx = 40.0, grow = 1.0, shrink = 1.0, minPx = 50.0)
                )
            )
        )
    }

    @Test
    fun `no free space and no flexible items keeps bases`() {
        // Exact fit: bases sum to the content box → everyone keeps basis.
        val sizes = FlexSizeResolver.resolve(
            contentMainPx = 210.0, gapPx = 5.0,
            items = listOf(
                Item(basisPx = 100.0, grow = 0.0, shrink = 1.0, minPx = 50.0),
                Item(basisPx = 105.0, grow = 0.0, shrink = 1.0, minPx = 50.0)
            )
        )
        assertSizes(listOf(100.0, 105.0), sizes)
    }
}

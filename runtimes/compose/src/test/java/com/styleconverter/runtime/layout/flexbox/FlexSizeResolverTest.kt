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

    // ---- wave 9: max clamps (§9.7 step 4.d max violations) ----------------------

    @Test
    fun `max violation freezes at max and redistributes to siblings`() {
        // content 300, bases 50+50, free 200 → +100 each; a hits max 80 →
        // frozen at 80, second pass hands the whole remainder to b: 220.
        val sizes = FlexSizeResolver.resolve(
            contentMainPx = 300.0, gapPx = 0.0,
            items = listOf(
                Item(basisPx = 50.0, grow = 1.0, shrink = 1.0, minPx = 10.0, maxPx = 80.0),
                Item(basisPx = 50.0, grow = 1.0, shrink = 1.0, minPx = 10.0)
            )
        )
        assertSizes(listOf(80.0, 220.0), sizes)
    }

    @Test
    fun `inflexible item with base above max freezes at max`() {
        // §9.7.2: grow 0 while growing → frozen at clamp(base) = max 60.
        val sizes = FlexSizeResolver.resolve(
            contentMainPx = 400.0, gapPx = 0.0,
            items = listOf(
                Item(basisPx = 100.0, grow = 0.0, shrink = 1.0, minPx = 10.0, maxPx = 60.0),
                Item(basisPx = 100.0, grow = 1.0, shrink = 1.0, minPx = 10.0)
            )
        )
        // b gets base + free measured against a's FROZEN 60: 400−60−100=240.
        assertSizes(listOf(60.0, 340.0), sizes)
    }

    @Test
    fun `min beats max when the declared band is inverted`() {
        // CSS 2.1 §10.4: min-width wins over a smaller max-width.
        val sizes = FlexSizeResolver.resolve(
            contentMainPx = 100.0, gapPx = 0.0,
            items = listOf(
                Item(basisPx = 100.0, grow = 0.0, shrink = 0.0, minPx = 70.0, maxPx = 40.0)
            )
        )
        assertSizes(listOf(70.0), sizes)
    }

    // ---- wave 9: intrinsic pass (resolveWithIntrinsics, §9.2 step 3.E) ----------

    @Test
    fun `auto bases fill from measured max-content then grow from them`() {
        // Row line: a declared basis 40 (grow 0), b/c content-sized with
        // measured max-content 60 / 100. content 304, gap 8 → gaps 16.
        // a freezes at min 50; free = 304−16−(50+60+100) = 78 → b +26, c +52.
        val measured = mapOf(1 to 60.0, 2 to 100.0)
        val sizes = resolveWithIntrinsics(
            contentMainRealPx = 304.0, gapRealPx = 8.0,
            items = listOf(
                Item(basisPx = 40.0, grow = 0.0, shrink = 1.0, minPx = 50.0),
                Item(basisPx = null, grow = 1.0, shrink = 1.0, minPx = 50.0),
                Item(basisPx = null, grow = 2.0, shrink = 1.0, minPx = 50.0)
            ),
            density = 1f,
            maxContentOf = { i -> measured.getValue(i) }
        )
        assertSizes(listOf(50.0, 86.0, 152.0), sizes)
    }

    @Test
    fun `oversized content bases shrink scaled by base`() {
        // Two content-sized items measuring 150 each in a 200px line:
        // bases 300, free −100, equal scaled shrink → 100 each.
        val sizes = resolveWithIntrinsics(
            contentMainRealPx = 200.0, gapRealPx = 0.0,
            items = listOf(
                Item(basisPx = null, grow = 0.0, shrink = 1.0, minPx = 50.0),
                Item(basisPx = null, grow = 0.0, shrink = 1.0, minPx = 50.0)
            ),
            density = 1f,
            maxContentOf = { 150.0 }
        )
        assertSizes(listOf(100.0, 100.0), sizes)
    }

    @Test
    fun `measured base clamps to the placeholder floor min`() {
        // Tiny measured content (20px) with the 50px web floor: the §9.7
        // min violation clamps the item at 50 even while growing siblings.
        val sizes = resolveWithIntrinsics(
            contentMainRealPx = 300.0, gapRealPx = 0.0,
            items = listOf(
                Item(basisPx = null, grow = 0.0, shrink = 1.0, minPx = 50.0),
                Item(basisPx = null, grow = 1.0, shrink = 1.0, minPx = 50.0)
            ),
            density = 1f,
            maxContentOf = { i -> if (i == 0) 20.0 else 60.0 }
        )
        // a frozen (grow 0) at max(20, 50) = 50; b takes the rest: 250.
        assertSizes(listOf(50.0, 250.0), sizes)
    }

    @Test
    fun `density scales declared dp fields but not measured px`() {
        // density 2: declared basis/min are dp→×2; the measured intrinsic
        // (already real px) passes through untouched. content 400 real px,
        // bases 2×80=160 + 100 → free 140 → all to b (grow 1).
        val sizes = resolveWithIntrinsics(
            contentMainRealPx = 400.0, gapRealPx = 0.0,
            items = listOf(
                Item(basisPx = 80.0, grow = 0.0, shrink = 1.0, minPx = 10.0),
                Item(basisPx = null, grow = 1.0, shrink = 1.0, minPx = 10.0)
            ),
            density = 2f,
            maxContentOf = { 100.0 }
        )
        assertSizes(listOf(160.0, 240.0), sizes)
    }

    @Test
    fun `measured base above declared max clamps to max`() {
        // §9.2 step 3.E + §9.7 step 4.d: a 180px max-content base under max-width 120
        // freezes at 120; the sibling absorbs the freed space.
        val sizes = resolveWithIntrinsics(
            contentMainRealPx = 300.0, gapRealPx = 0.0,
            items = listOf(
                Item(basisPx = null, grow = 1.0, shrink = 1.0, minPx = 50.0, maxPx = 120.0),
                Item(basisPx = null, grow = 1.0, shrink = 1.0, minPx = 50.0)
            ),
            density = 1f,
            maxContentOf = { i -> if (i == 0) 180.0 else 60.0 }
        )
        // bases 180+60=240, free 60 → +30 each → a 210 (max 120!) → frozen
        // at 120; second pass: free = 300−120−60 = 120 → b = 180.
        assertSizes(listOf(120.0, 180.0), sizes)
    }
}

package com.styleconverter.runtime.effects.filter

// Pins the filter-chain COMPOSITION DIRECTION (filter-effects-1 §2: in
// `filter: F1 F2`, F2 acts on F1's output, so the combined matrix is M2·M1).
//
// The old fold did `acc.timesAssign(step)` — acc × step = M1·M2 — running
// every multi-function chain REVERSED. The spec oracle caught it on first
// contact with fixtures/combinations/filter-chain-order.json: each chain
// measured exactly the OTHER chain's expected value (GrayThenSepia read
// 161-grey, SepiaThenGray read the warm 183/163/127), while iOS and web
// passed both. This is the pure-maths pin so a revert cannot ride a device
// run's absence.

import androidx.compose.ui.graphics.ColorMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FilterChainOrderTest {

    /** Apply a 4x5 ColorMatrix to an opaque rgb triple, clamped to 0..255. */
    private fun apply(m: ColorMatrix, r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val v = m.values
        fun row(o: Int) = (v[o] * r + v[o + 1] * g + v[o + 2] * b + v[o + 4] * 255f)
            .coerceIn(0f, 255f)
        return Triple(row(0), row(5), row(10))
    }

    private fun chain(vararg fns: FilterFunction): ColorMatrix {
        val m = FilterApplier.generateColorMatrix(
            FilterConfig(filters = fns.toList())
        )
        assertNotNull("chain must produce a matrix", m)
        return m!!
    }

    @Test
    fun `gray then sepia lands on the warm value`() {
        // #3498db = (52,152,219) — the fixture's base. grayscale(1) →
        // Rec.709 luma 135.58 grey; sepia(1) on a grey multiplies by the
        // sepia row sums (1.351, 1.203, 0.937) → (183.2, 163.1, 127.1) —
        // the values web and iOS measure on device.
        val m = chain(FilterFunction.Grayscale(1f), FilterFunction.Sepia(1f))
        val (r, g, b) = apply(m, 52f, 152f, 219f)
        assertEquals(183f, r, 1.5f)
        assertEquals(163f, g, 1.5f)
        assertEquals(127f, b, 1.5f)
    }

    @Test
    fun `sepia then gray lands on the grey value`() {
        // sepia(1) on #3498db → (178.7, 159.2, 124.0); grayscale(1) →
        // Rec.709 luma 160.8 → flat grey. The two chains MUST differ —
        // that difference is the entire point of the ordering rule.
        val m = chain(FilterFunction.Sepia(1f), FilterFunction.Grayscale(1f))
        val (r, g, b) = apply(m, 52f, 152f, 219f)
        assertEquals(161f, r, 1.5f)
        assertEquals(161f, g, 1.5f)
        assertEquals(161f, b, 1.5f)
        // Flat grey: channels agree with each other tighter than with the pin.
        assertEquals(r, g, 0.01f)
        assertEquals(g, b, 0.01f)
    }

    @Test
    fun `the two orderings genuinely differ`() {
        // Guard against any "fix" that makes composition commutative (e.g.
        // summing matrices): the swap signature must stay measurable.
        val a = apply(chain(FilterFunction.Grayscale(1f), FilterFunction.Sepia(1f)), 52f, 152f, 219f)
        val b = apply(chain(FilterFunction.Sepia(1f), FilterFunction.Grayscale(1f)), 52f, 152f, 219f)
        val delta = maxOf(
            kotlin.math.abs(a.first - b.first),
            kotlin.math.abs(a.second - b.second),
            kotlin.math.abs(a.third - b.third),
        )
        assert(delta > 15f) { "orderings collapsed together (Δ=$delta)" }
    }

    @Test
    fun `a single function is unaffected by the direction fix`() {
        // The single-filter case is direction-blind (M·I == I·M) — pinned so
        // the whole committed filter corpus provably cannot move.
        val m = chain(FilterFunction.Grayscale(1f))
        val (r, g, b) = apply(m, 231f, 76f, 60f)      // #e74c3c → luma 107.8
        assertEquals(108f, r, 1.5f)
        assertEquals(108f, g, 1.5f)
        assertEquals(108f, b, 1.5f)
    }
}

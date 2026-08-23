package com.styleconverter.runtime.typography

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM pins for [ComposedLinePitch] — wave 46, lane Y5.
 *
 * The fixtures model what compose-ui-text 1.11.4 actually lays out for a
 * run at CSS line box L: every line `ceil(L)` tall (LineHeightStyleSpan's
 * per-line ceil, bytecode-verified — see the object header), line i at
 * `i × ceil(L)` from the Text top. The snap's placement is the existing
 * `(round(n × L) − n × ceil(L)) / 2` trim. The expected device rows are
 * Chromium's accumulated-edge rounding, `round(i × L)`, measured on the
 * frozen css-counter-styles refs (pitch 31, 32, 31, 31 at L = 31.25).
 */
class ComposedLinePitchTest {

    /** Compose's uniform stack for [n] lines at a box of [l] px. */
    private fun tops(n: Int, l: Float): FloatArray {
        val p = kotlin.math.ceil(l)
        return FloatArray(n) { it * p }
    }

    private fun bottoms(n: Int, l: Float): FloatArray {
        val p = kotlin.math.ceil(l)
        return FloatArray(n) { (it + 1) * p }
    }

    /** The snap's own placement rule, spelled out so the test and the
     *  renderer cannot drift apart on it. */
    private fun placement(n: Int, l: Float): Int {
        val target = Math.round(n * l)
        val natural = (n * kotlin.math.ceil(l)).toInt()
        return (target - natural) / 2
    }

    @Test
    fun tenLinesAt25pxLandOnTheBrowserGridAndAdvance312point5() {
        // The lane's acceptance line: 25px × 1.25 = 31.25, ten lines.
        val l = 31.25f
        val n = 10
        val p = placement(n, l)
        assertEquals(-3, p) // (313 − 320) / 2, Kotlin truncation
        val plan = ComposedLinePitch.plan(l, tops(n, l), bottoms(n, l), p)
        assertNotNull(plan)
        val bands = plan!!.bands
        assertEquals(n, bands.size)
        // Device top of line i inside the snapped box == round(i × L).
        for (i in 0 until n) {
            val deviceTop = p + i * 32f + bands[i].deltaYPx
            assertEquals("line $i", Math.round(i * l).toFloat(), deviceTop, 0f)
        }
        // Line 0 is pulled back to the box top (undoing the centring trim)…
        assertEquals(3f, bands[0].deltaYPx, 0f)
        // …and the last line ends exactly on the snapped 313px box:
        // top 281 + ceil(L) 32 = 313 = round(10 × 31.25).
        assertEquals(-4f, bands[n - 1].deltaYPx, 0f)
        assertEquals(281f, p + 9 * 32f + bands[9].deltaYPx, 0f)
        // Adjacent bands never move apart by more than the one leading px.
        for (i in 1 until n) {
            val step = bands[i].deltaYPx - bands[i - 1].deltaYPx
            assertTrue("step $i = $step", step == 0f || step == -1f)
        }
    }

    @Test
    fun fourLinesAt14pxFollowHalfUpRounding() {
        // 14px × 1.25 = 17.5 — the .5 ties go UP like the snap's Math.round:
        // browser tops 0, 18, 35, 53; Compose tops 0, 18, 36, 54 at p = −1.
        val l = 17.5f
        val plan = ComposedLinePitch.plan(l, tops(4, l), bottoms(4, l), placement(4, l))
        assertNotNull(plan)
        val d = plan!!.bands.map { it.deltaYPx }
        assertEquals(listOf(1f, 1f, 0f, 0f), d)
    }

    @Test
    fun integerBoxIsDeclined() {
        // The corpus's inherited 16px × 1.25 = 20px: every line already sits
        // on the grid, so the run keeps its single untouched draw.
        assertNull(ComposedLinePitch.plan(20f, tops(10, 20f), bottoms(10, 20f), 0))
    }

    @Test
    fun singleLineIsDeclined() {
        assertNull(ComposedLinePitch.plan(31.25f, tops(1, 31.25f), bottoms(1, 31.25f), 0))
    }

    @Test
    fun normalBoxAndDegenerateInputsAreDeclined() {
        // `line-height: normal` resolves to no CSS box (0f sentinel upstream).
        assertNull(ComposedLinePitch.plan(0f, tops(3, 30f), bottoms(3, 30f), 0))
        assertNull(ComposedLinePitch.plan(Float.NaN, tops(3, 30f), bottoms(3, 30f), 0))
        // Mismatched arrays say nothing about the layout.
        assertNull(ComposedLinePitch.plan(31.25f, tops(3, 31.25f), bottoms(2, 31.25f), 0))
    }

    @Test
    fun nonUniformPlatformStackIsDeclined() {
        // A line taller than ceil(L) (an inline placeholder, say) breaks the
        // one-leading-pixel overlap bound, so the plan declines outright.
        val t = floatArrayOf(0f, 32f, 70f)
        val b = floatArrayOf(32f, 70f, 102f)
        assertNull(ComposedLinePitch.plan(31.25f, t, b, -1))
        // Uniform but NOT ceil(L) — the span did not produce this stack.
        assertNull(ComposedLinePitch.plan(31.25f, tops(3, 33f), bottoms(3, 33f), -1))
    }

    @Test
    fun clipBandsAreTheLinesWithOpenEnds() {
        val l = 31.25f
        val plan = ComposedLinePitch.plan(l, tops(3, l), bottoms(3, l), placement(3, l))!!
        val b = plan.bands
        // First band opens upward, last opens downward, middle is the line.
        assertEquals(0f - ComposedLinePitch.OPEN_BAND_PX, b[0].clipTopPx, 0f)
        assertEquals(32f, b[0].clipBottomPx, 0f)
        assertEquals(32f, b[1].clipTopPx, 0f)
        assertEquals(64f, b[1].clipBottomPx, 0f)
        assertEquals(64f, b[2].clipTopPx, 0f)
        assertEquals(96f + ComposedLinePitch.OPEN_BAND_PX, b[2].clipBottomPx, 0f)
    }

    @Test
    fun prefixPhaseShiftsEveryLineLikeTheAccumulator() {
        // Stacked 31.25 above: the box itself rounds 63 − 31 = 32 (the
        // wave-30 rule) and the lines inside follow the same phase.
        val l = 31.25f
        val prefix = 31.25f
        val n = 3
        val p = placement(n, l) // −1: (94 − 96) / 2
        val plan = ComposedLinePitch.plan(l, tops(n, l), bottoms(n, l), p, prefix)!!
        for (i in 0 until n) {
            val expected = Math.round(prefix + i * l) - Math.round(prefix)
            assertEquals("line $i", expected.toFloat(), p + i * 32f + plan.bands[i].deltaYPx, 0f)
        }
        // 31.25 → 62.5 → 93.75 → 125: tops 0, 32 (63−31), 63 (94−31).
        assertEquals(listOf(0f, 32f, 63f), (0 until n).map {
            p + it * 32f + plan.bands[it].deltaYPx
        })
    }
}

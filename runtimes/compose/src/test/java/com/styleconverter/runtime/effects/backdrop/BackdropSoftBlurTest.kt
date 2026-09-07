package com.styleconverter.runtime.effects.backdrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The SB pin table for [BackdropSoftBlur] (wave-35 lane B3) — Skia's window
 * rule, its even/odd parity split, the `SkTileMode::kMirror` edge model and
 * the sliding-window convolution, all on the plain JVM.
 *
 * These pins are what makes the claim auditable off-device. The reason this
 * path exists is a MEASUREMENT (BackdropSoftBlur's header): the three-pass
 * box reproduces Chrome to ~0.1 MAE on backdrop-filter-boundary's tiles while
 * a true Gaussian reproduces iOS's 1.4–1.6 instead — so "is this actually
 * Skia's kernel, parity rule included" has to be checkable here.
 */
class BackdropSoftBlurTest {

    /**
     * SB1 — the window rule is Skia's `BlurSigmaToBoxSize`,
     * `floor(σ·3·√(2π)/4 + 0.5)`. The six σ values are
     * backdrop-filter-boundary's own tiles plus backdrop-filter-basic-blur's
     * σ=10, so the constant is pinned exactly where the corpus exercises it.
     */
    @Test
    fun `SB1 window follows Skia BlurSigmaToBoxSize`() {
        // σ·1.87997 + 0.5, floored.
        assertEquals(6, BackdropSoftBlur.windowFor(3f))    // 6.14
        assertEquals(11, BackdropSoftBlur.windowFor(6f))   // 11.28
        assertEquals(19, BackdropSoftBlur.windowFor(10f))  // 19.30
        assertEquals(23, BackdropSoftBlur.windowFor(12f))  // 23.06
        assertEquals(45, BackdropSoftBlur.windowFor(24f))  // 45.62
        assertEquals(90, BackdropSoftBlur.windowFor(48f))  // 90.74
        assertEquals(180, BackdropSoftBlur.windowFor(96f)) // 180.98 → floor 180
        // A sub-pixel σ still yields a legal (identity-width) window.
        assertEquals(1, BackdropSoftBlur.windowFor(0.05f))
    }

    /**
     * SB2 — the parity split. An ODD window is already centred, so all three
     * passes share it; an EVEN one has no centred window, so Skia offsets two
     * passes the opposite way and widens the third. Getting this wrong is not
     * cosmetic: the naive even handling measured 2.06 MAE at σ=3 where this
     * rule measures 0.158.
     */
    @Test
    fun `SB2 pass windows follow Skias odd even parity rule`() {
        // Odd (σ=6 → 11): three identical radius-5 windows.
        val odd = BackdropSoftBlur.passes(11)
        assertEquals(3, odd.size)
        for (p in odd) assertEquals(listOf(-5, 5), p.toList())
        // Even (σ=3 → 6): widths 6, 6, 7 with offsets that cancel.
        val even = BackdropSoftBlur.passes(6)
        assertEquals(listOf(-3, 2), even[0].toList())
        assertEquals(listOf(-2, 3), even[1].toList())
        assertEquals(listOf(-3, 3), even[2].toList())
        // The combined support is symmetric about the pixel — that is what
        // "the offsets cancel" has to mean, and what keeps the blur centred.
        assertEquals(-even.sumOf { it[1] }, even.sumOf { it[0] })
        // Degenerate windows are floored, never zero-width.
        assertEquals(listOf(-0, 0), BackdropSoftBlur.passes(1)[0].toList())
    }

    /** SB3 — kMirror index mapping: edge pixel duplicated, period 2n. */
    @Test
    fun `SB3 mirror reproduces SkTileMode kMirror`() {
        val n = 5
        assertEquals(listOf(0, 1, 2, 3, 4), (0 until n).map { BackdropSoftBlur.mirror(it, n) })
        // …2 1 0 | 0 1 2 — the edge pixel repeats across the boundary.
        assertEquals(listOf(0, 1, 2, 3, 4), (-1 downTo -5).map { BackdropSoftBlur.mirror(it, n) })
        assertEquals(listOf(4, 3, 2, 1, 0), (5..9).map { BackdropSoftBlur.mirror(it, n) })
        // Period 2n in both directions.
        assertEquals(BackdropSoftBlur.mirror(3, n), BackdropSoftBlur.mirror(3 + 2 * n, n))
        assertEquals(BackdropSoftBlur.mirror(3, n), BackdropSoftBlur.mirror(3 - 4 * n, n))
        // A degenerate axis collapses to the only sample there is.
        assertEquals(0, BackdropSoftBlur.mirror(7, 1))
    }

    /**
     * SB4 — the EXACT impulse response of a three-pass width-3 box is the
     * discrete B-spline [1,3,6,7,6,3,1]/27 (box³ = the quadratic B-spline).
     * This is the strongest possible statement that the convolution is the
     * kernel it claims to be, and it is an integer identity — no tolerance.
     */
    @Test
    fun `SB4 three width-3 box passes give the exact B-spline impulse response`() {
        // σ=1.5 → window floor(1.5·1.87997 + 0.5) = 3 (odd).
        assertEquals(3, BackdropSoftBlur.windowFor(1.5f))
        val w = 21; val h = 1
        val plate = IntArray(w * h) { 0xFF000000.toInt() }
        // 27·255 would overflow a channel, so drive the impulse at 255 and
        // compare the profile's SHAPE against the exact weights below.
        plate[10] = 0xFFFFFFFF.toInt()
        val out = BackdropSoftBlur.blurArgb(plate, w, h, 1.5f)
        val weights = intArrayOf(1, 3, 6, 7, 6, 3, 1)
        for (k in weights.indices) {
            val want = Math.round(255f * weights[k] / 27f)
            val got = (out[7 + k] ushr 16) and 0xFF
            assertEquals("tap ${k - 3}", want, got)
        }
        // Compact support: nothing outside the 7-tap span.
        for (x in 0 until 7) assertEquals(0, (out[x] ushr 16) and 0xFF)
        for (x in 14 until w) assertEquals(0, (out[x] ushr 16) and 0xFF)
    }

    /**
     * SB5 — a UNIFORM opaque plate is a fixed point. Under a mirror edge the
     * whole neighbourhood of every pixel (reflected taps included) is the
     * same colour, so a DC-normalised kernel must return it exactly. This is
     * also the sliding-window accumulator's drift check: any leak in the
     * add/subtract cycle shows up here first.
     */
    @Test
    fun `SB5 uniform plate survives the blur exactly at every sigma`() {
        val w = 150; val h = 80
        val plate = IntArray(w * h) { 0xFF3C6E21.toInt() }
        for (sigma in listOf(3f, 6f, 12f, 24f, 48f, 96f)) {
            val out = BackdropSoftBlur.blurArgb(plate, w, h, sigma)
            for (p in out) assertEquals("sigma=$sigma", 0xFF3C6E21.toInt(), p)
        }
    }

    /**
     * SB6 — a mirrored edge imports NO outside colour. The WPT assertion of
     * backdrop-filter-boundary is literally "No lime green should be brought
     * in to the blurred regions": with a mirror edge the crop can only ever
     * see reflections of itself, so a red/blue plate's blur stays free of
     * green even at a σ whose support is many times the crop's width.
     */
    @Test
    fun `SB6 mirror edge never imports colour from outside the crop`() {
        val w = 16; val h = 4
        val plate = IntArray(w * h) { i ->
            if (i % w < w / 2) 0xFFFF0000.toInt() else 0xFF0000FF.toInt()
        }
        for (sigma in listOf(3f, 48f)) {
            val out = BackdropSoftBlur.blurArgb(plate, w, h, sigma)
            for (p in out) assertEquals("sigma=$sigma", 0, (p ushr 8) and 0xFF)
            // Alpha is untouched — the plate never gained transparency.
            for (p in out) assertEquals(0xFF, (p ushr 24) and 0xFF)
        }
    }

    /** SB7 — invert matches the shared filter-effects-1 §6.1 curve. */
    @Test
    fun `SB7 invert matches the shared invertChannel curve`() {
        // Full inversion: alpha kept (§8.6 is colour-only), each channel 255−c.
        val pixels = intArrayOf(0xFF00FF80.toInt(), 0x40123456)
        BackdropSoftBlur.invertArgb(pixels, 1f)
        assertEquals(0xFFFF007F.toInt(), pixels[0])
        assertEquals(0x40EDCBA9, pixels[1])
        // amount = 0.5 collapses every channel to the mid grey, per §8.6.
        val mid = intArrayOf(0xFF0080FF.toInt())
        BackdropSoftBlur.invertArgb(mid, 0.5f)
        assertEquals(0xFF808080.toInt(), mid[0])
        // And it agrees with BackdropChain's scalar curve — the one the
        // RenderEffect path's colour matrix is built from — to within the
        // single level 8-bit rounding can differ by.
        val partial = intArrayOf(0xFF660000.toInt())  // red = 102 = 0.4·255
        BackdropSoftBlur.invertArgb(partial, 0.25f)
        val want = BackdropChain.invertChannel(0.25f, 102f / 255f) * 255f
        assertTrue(abs(((partial[0] ushr 16) and 0xFF) - want) <= 1f)
    }

    /** SB8 — the engagement guard: a real blur over a real patch, no σ ceiling. */
    @Test
    fun `SB8 engages for every corpus sigma and refuses degenerate input`() {
        // Sliding-window cost is O(w·h) regardless of σ, so even the corpus's
        // largest blur over a full-canvas element is admitted — there is
        // deliberately no threshold to tune.
        for (sigma in listOf(3f, 6f, 10f, 12f, 24f, 48f, 96f)) {
            assertTrue("sigma=$sigma", BackdropSoftBlur.engages(390, 600, sigma))
        }
        assertFalse(BackdropSoftBlur.engages(150, 80, 0f))
        assertFalse(BackdropSoftBlur.engages(0, 80, 4f))
        assertFalse(BackdropSoftBlur.engages(150, 0, 4f))
    }
}

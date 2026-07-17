package com.styleconverter.runtime.color

// Pins css-backgrounds-3 §3.7 tile placement (BackgroundTileMath) — the
// wave-9 space/round implementation behind ColorApplier's per-layer draw.

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BackgroundTileMathTest {

    private fun assertOrigins(expected: List<Float>, plan: BackgroundTileMath.AxisPlan, eps: Float = 0.01f) {
        assertEquals("origin count", expected.size, plan.origins.size)
        expected.zip(plan.origins).forEachIndexed { i, (e, a) ->
            assertEquals("origin $i", e, a, eps)
        }
    }

    // Closing-edge companion to assertOrigins: `ends` is parallel to
    // `origins` (tile i = [origins[i], ends[i]) — the pixel-snap contract).
    private fun assertEnds(expected: List<Float>, plan: BackgroundTileMath.AxisPlan, eps: Float = 0.01f) {
        assertEquals("end count", expected.size, plan.ends.size)
        expected.zip(plan.ends).forEachIndexed { i, (e, a) ->
            assertEquals("end $i", e, a, eps)
        }
    }

    // Abutting lattices must SHARE edges exactly: ends[i] == origins[i+1]
    // (bitwise, no epsilon — shared integer edges are the whole point of
    // the snap; two AA'd rects meeting on a fractional edge leak a seam).
    private fun assertSharedEdges(plan: BackgroundTileMath.AxisPlan) {
        for (i in 0 until plan.origins.size - 1) {
            assertEquals("shared edge $i", plan.ends[i], plan.origins[i + 1], 0f)
        }
    }

    // ---- space --------------------------------------------------------------

    @Test
    fun `space distributes leftover as equal gaps with flush edges`() {
        // 140px area, 50px tile → 2 whole tiles, 40px leftover → one 40px
        // gap; first at 0, last flush at 140−50=90.
        val plan = BackgroundTileMath.axisPlan(140f, 50f, anchor = 0f, mode = AxisRepeat.SPACE)
        assertEquals(50f, plan.tileSize, 0.001f) // space never rescales
        assertOrigins(listOf(0f, 90f), plan)
    }

    @Test
    fun `space with three tiles splits leftover into two gaps`() {
        // 170 / 50 → 3 tiles, leftover 20 → 10px gaps: 0, 60, 120 (flush).
        val plan = BackgroundTileMath.axisPlan(170f, 50f, anchor = 0f, mode = AxisRepeat.SPACE)
        assertOrigins(listOf(0f, 60f, 120f), plan)
    }

    @Test
    fun `space with room for one tile falls back to the position anchor`() {
        // §3.7: fewer than two fitting → background-position decides.
        val plan = BackgroundTileMath.axisPlan(80f, 50f, anchor = 12f, mode = AxisRepeat.SPACE)
        assertOrigins(listOf(12f), plan)
    }

    // ---- round --------------------------------------------------------------

    @Test
    fun `round rescales the tile so a whole count fits`() {
        // 140 / 50 = 2.8 → rounds to 3 tiles of 140/3 ≈ 46.67. The SHADER
        // pitch stays fractional; the DRAWN edges snap to integers:
        // round(0)=0, round(46.67)=47, round(93.33)=93, round(140)=140.
        val plan = BackgroundTileMath.axisPlan(140f, 50f, anchor = 0f, mode = AxisRepeat.ROUND)
        assertEquals(140f / 3f, plan.tileSize, 0.001f)
        assertOrigins(listOf(0f, 47f, 93f), plan)
        // Per-tile widths 47/46/47 (±1px), summing to the full 140px axis.
        assertEnds(listOf(47f, 93f, 140f), plan)
        assertSharedEdges(plan)
    }

    @Test
    fun `round keeps an exact fit untouched`() {
        // Integer pitch → the snap is the identity (edges 0/50/100).
        val plan = BackgroundTileMath.axisPlan(100f, 50f, anchor = 0f, mode = AxisRepeat.ROUND)
        assertEquals(50f, plan.tileSize, 0.001f)
        assertOrigins(listOf(0f, 50f), plan)
        assertEnds(listOf(50f, 100f), plan)
    }

    @Test
    fun `round grows an oversized tile up to the full area`() {
        // 100 / 80 = 1.25 → rounds to 1 tile stretched to the whole axis
        // (round never drops below one tile).
        val plan = BackgroundTileMath.axisPlan(100f, 80f, anchor = 0f, mode = AxisRepeat.ROUND)
        assertEquals(100f, plan.tileSize, 0.001f)
        assertOrigins(listOf(0f), plan)
        assertEnds(listOf(100f), plan)
    }

    @Test
    fun `round fixture lattice snaps 200 over 7 to integer shared edges`() {
        // The Repeat_Round straggler: 200px axis / 30px tile → 7 tiles of
        // 200/7 ≈ 28.571px. Fractional origins (multiples of 200/7) made
        // both natives paint independently-AA'd rects whose boundaries
        // never summed to full coverage — a light background seam at every
        // interior edge (SSIM 0.946 vs Chromium's seamless pattern).
        val plan = BackgroundTileMath.axisPlan(200f, 30f, anchor = 0f, mode = AxisRepeat.ROUND)
        // Shader pitch stays the fractional 200/7 — never snapped.
        assertEquals(200f / 7f, plan.tileSize, 0.001f)
        // Drawn edges = round(i·200/7): 0,29,57,86,114,143,171,200.
        assertOrigins(listOf(0f, 29f, 57f, 86f, 114f, 143f, 171f), plan)
        assertEnds(listOf(29f, 57f, 86f, 114f, 143f, 171f, 200f), plan)
        // Integer edge sharing is the seam fix: exact, not epsilon-close.
        assertSharedEdges(plan)
        // Widths alternate 29/28 (±1px around the pitch) and tile the
        // whole axis: last end flush with the 200px area.
        plan.origins.zip(plan.ends).forEach { (s, e) ->
            assertTrue("width ${e - s} within ±1px of pitch", abs((e - s) - 200f / 7f) < 1f)
        }
        assertEquals(200f, plan.ends.last(), 0f)
    }

    @Test
    fun `repeat with a fractional pitch snaps shared edges too`() {
        // Any abutting fractional-pitch lattice seams, not just `round`:
        // repeat with tile 200/7 over 100px walks 0, 28.57, 57.14, 85.71
        // → snapped tiles [0,29) [29,57) [57,86) [86,114) — shared
        // integer edges, last tile overhangs (clipped by the painter).
        val plan = BackgroundTileMath.axisPlan(100f, 200f / 7f, anchor = 0f, mode = AxisRepeat.REPEAT)
        assertOrigins(listOf(0f, 29f, 57f, 86f), plan)
        assertEnds(listOf(29f, 57f, 86f, 114f), plan)
        assertSharedEdges(plan)
    }

    @Test
    fun `space and no-repeat keep exact unsnapped ends`() {
        // Non-abutting modes have no seam to close: end = start + tile
        // exactly, even for fractional geometry (gaps separate the tiles).
        val space = BackgroundTileMath.axisPlan(140f, 50f, anchor = 0f, mode = AxisRepeat.SPACE)
        assertEnds(listOf(50f, 140f), space) // starts 0/90 + the 50px tile
        val single = BackgroundTileMath.axisPlan(100f, 30f, anchor = 35.4f, mode = AxisRepeat.NO_REPEAT)
        assertOrigins(listOf(35.4f), single)
        assertEnds(listOf(65.4f), single) // fractional anchor untouched
    }

    // ---- repeat / no-repeat ---------------------------------------------------

    @Test
    fun `repeat phase-shifts the grid through the anchor and covers the area`() {
        // anchor 10, tile 30: grid …,-20,10,40,70,… — first tile starts
        // left of 0 so the area edge is covered (spec: pattern is infinite,
        // clipped to the painting area).
        val plan = BackgroundTileMath.axisPlan(100f, 30f, anchor = 10f, mode = AxisRepeat.REPEAT)
        assertOrigins(listOf(-20f, 10f, 40f, 70f), plan)
        // Full coverage: first ≤ 0 and last + tile ≥ area.
        assertTrue(plan.origins.first() <= 0f)
        assertTrue(plan.origins.last() + plan.tileSize >= 100f)
    }

    @Test
    fun `no-repeat draws a single tile at the anchor`() {
        val plan = BackgroundTileMath.axisPlan(100f, 30f, anchor = 35f, mode = AxisRepeat.NO_REPEAT)
        assertOrigins(listOf(35f), plan)
    }

    // ---- degenerate inputs ----------------------------------------------------

    @Test
    fun `zero tile or area yields no origins`() {
        assertTrue(BackgroundTileMath.axisPlan(100f, 0f, 0f, AxisRepeat.REPEAT).origins.isEmpty())
        assertTrue(BackgroundTileMath.axisPlan(0f, 50f, 0f, AxisRepeat.SPACE).origins.isEmpty())
    }
}

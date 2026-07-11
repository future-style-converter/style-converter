package com.styleconverter.runtime.color

// Pins css-backgrounds-3 §3.7 tile placement (BackgroundTileMath) — the
// wave-9 space/round implementation behind ColorApplier's per-layer draw.

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundTileMathTest {

    private fun assertOrigins(expected: List<Float>, plan: BackgroundTileMath.AxisPlan, eps: Float = 0.01f) {
        assertEquals("origin count", expected.size, plan.origins.size)
        expected.zip(plan.origins).forEachIndexed { i, (e, a) ->
            assertEquals("origin $i", e, a, eps)
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
        // 140 / 50 = 2.8 → rounds to 3 tiles of 140/3 ≈ 46.67.
        val plan = BackgroundTileMath.axisPlan(140f, 50f, anchor = 0f, mode = AxisRepeat.ROUND)
        assertEquals(140f / 3f, plan.tileSize, 0.001f)
        assertOrigins(listOf(0f, 140f / 3f, 280f / 3f), plan)
    }

    @Test
    fun `round keeps an exact fit untouched`() {
        val plan = BackgroundTileMath.axisPlan(100f, 50f, anchor = 0f, mode = AxisRepeat.ROUND)
        assertEquals(50f, plan.tileSize, 0.001f)
        assertOrigins(listOf(0f, 50f), plan)
    }

    @Test
    fun `round grows an oversized tile up to the full area`() {
        // 100 / 80 = 1.25 → rounds to 1 tile stretched to the whole axis
        // (round never drops below one tile).
        val plan = BackgroundTileMath.axisPlan(100f, 80f, anchor = 0f, mode = AxisRepeat.ROUND)
        assertEquals(100f, plan.tileSize, 0.001f)
        assertOrigins(listOf(0f), plan)
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

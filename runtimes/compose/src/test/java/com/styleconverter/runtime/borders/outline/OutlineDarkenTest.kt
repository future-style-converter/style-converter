package com.styleconverter.runtime.borders.outline

// Wave-5 pinning — Chromium Color::Dark() calibration for 3D outline
// shades, measured from a fresh headless-Chrome probe (outline-probe.png):
//   `5px ridge crimson` inner band  → rgb(136,12,37)
//   `3px groove #eee`   outer band  → rgb(154,154,154)
// The formula is an HSL lightness transform L' = max(0, L·2/3 − 0.02);
// tolerances allow ±2/255 for Chrome's own rounding.

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class OutlineDarkenTest {

    private fun channel(v: Float) = Math.round(v * 255f)

    @Test
    fun `crimson darkens to Chromium's ridge inner shade`() {
        val d = OutlineApplier.darken(Color(220f / 255f, 20f / 255f, 60f / 255f))
        // Chrome: rgb(136,12,37); allow ±2 per channel.
        assertEquals(136f, channel(d.red).toFloat(), 2f)
        assertEquals(12f, channel(d.green).toFloat(), 2f)
        assertEquals(37f, channel(d.blue).toFloat(), 2f)
    }

    @Test
    fun `light gray currentColor darkens to Chromium's groove outer shade`() {
        val d = OutlineApplier.darken(Color(0xFFEEEEEE))
        // Chrome: rgb(154,154,154) — the flat ×0.618 factor gave 147.
        assertEquals(154f, channel(d.red).toFloat(), 2f)
        assertEquals(154f, channel(d.green).toFloat(), 2f)
        assertEquals(154f, channel(d.blue).toFloat(), 2f)
    }

    @Test
    fun `alpha is preserved`() {
        val d = OutlineApplier.darken(Color(0.5f, 0.5f, 0.5f, 0.4f))
        assertEquals(0.4f, d.alpha, 0.001f)
    }
}

package com.styleconverter.runtime.borders.outline

// Retro R6 (audit finding A7#3, Compose half) — one Dark()/Light() model on
// Compose. OutlineApplier carried its own HSL transform (L' = L·2/3 − 0.02)
// while BorderSideApplier used Blink's subtractive max-channel model, so the
// same declared colour got two different dark bands as a border and as an
// outline (the two agreed only at the two wave-5 probe points, crimson and
// #eee). Both tones now delegate to BorderSideApplier.shade.
// Proven able to fail: restoring the HSL body in darken() fails
// `mid grey no longer takes the HSL value` (0.313 vs 0.172) and the
// delegation loop on #808080.

import androidx.compose.ui.graphics.Color
import com.styleconverter.runtime.borders.sides.BorderSideApplier
import org.junit.Assert.assertEquals
import org.junit.Test

class OutlineShadeDelegationTest {

    private val table = listOf(
        Color(220f / 255f, 20f / 255f, 60f / 255f),      // crimson — the Borders_C14 probe
        Color(0xFFEEEEEE),                               // #eee — the currentColor stage ink
        Color(128f / 255f, 128f / 255f, 128f / 255f),    // #808080 — where the models diverged
        Color(0f, 0f, 0f),                               // black — the kLightenedBlack lift
        Color(0.1f, 0.1f, 0.5f),                         // dark blue — Blink's contrast gate lifts
        Color(0.3f, 0.1f, 0.2f, 0.4f),                   // translucent — alpha must carry
    )

    @Test
    fun `outline dark band is the border dark band for every base`() {
        for (base in table) {
            assertEquals("darken($base)", BorderSideApplier.shade(base, lighten = false), OutlineApplier.darken(base))
        }
    }

    @Test
    fun `outline light band is the border light band for every base`() {
        for (base in table) {
            assertEquals("lighten($base)", BorderSideApplier.shade(base, lighten = true), OutlineApplier.lighten(base))
        }
    }

    @Test
    fun `mid grey no longer takes the HSL value`() {
        // HSL: L = 0.502 → L' = 0.315 (0.502·2/3 − 0.02). Blink Dark():
        // 0.502 × (0.502 − 0.33)/0.502 = 0.172. A border and an outline of
        // #808080 now paint the same 0.172 band.
        val d = OutlineApplier.darken(Color(128f / 255f, 128f / 255f, 128f / 255f))
        assertEquals(0.172f, d.red, 0.01f)
        assertEquals(d.red, d.green, 0.001f)
        assertEquals(d.red, d.blue, 0.001f)
    }

    @Test
    fun `black outline gains the lightened-black band`() {
        // `outline: 3px groove black` used to paint two indistinguishable
        // black bands; the border painter (and Blink) lift one to (84,84,84).
        val light = OutlineApplier.lighten(Color.Black)
        assertEquals(84f, light.red * 255f, 0.5f)
        assertEquals(84f, light.green * 255f, 0.5f)
        assertEquals(84f, light.blue * 255f, 0.5f)
    }
}

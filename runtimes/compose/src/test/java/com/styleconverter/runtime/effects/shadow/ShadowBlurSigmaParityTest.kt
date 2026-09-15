package com.styleconverter.runtime.effects.shadow

// Wave 50 (lane B8) — the real diagnosis of BACKLOG queue 6(c) "Android
// box-shadow ~2.4× over-blur (MultipleShadowApplier.kt)".
//
// FINDING. The LIVE box-shadow path (ShadowApplier.applyFullShadow →
// OutsetShadowPainter / InsetShadowPainter) has converted the CSS blur radius
// to Skia's mask radius through ShadowApplier.blurMaskRadius since wave 2
// (commit 0b2313a8, 2026-07-17): css-backgrounds-3 §6.1.2 defines the blur
// as a Gaussian whose standard deviation is HALF the blur radius (σ = r/2 —
// Blink's skia_utils BlurRadiusToStdDev), and Skia's BlurMaskFilter maps its
// radius argument to σ = 0.57735·radius + 0.5 (SkBlurMask::
// ConvertRadiusToSigma), so the applier inverts that map. MEASURED on the
// committed baselines by a 2D separable-Gaussian fit of the penumbra — a
// box-shadow is a Gaussian blur of a rectangle, so its coverage factorises
// into two normal CDFs and σ comes out of a 6-parameter least squares
// (script + provenance + input PNG hashes:
// tools/titan/results/wave50-B8/measure-shadow-sigma.py and
// …/shadow-sigma-baselines.json). Android is AT σ PARITY, not 2.4× wide:
//   Shadow_Simple        `5px 5px 10px rgba(0,0,0,.3)`   σ target 5.0
//                        web 5.65 · Android 4.61 · iOS 4.41  (ill-conditioned:
//                        the 5/5 offset parks the field under the opaque box)
//   Shadow_Colored       `0 4px 15px rgba(52,152,219,.5)` σ target 7.5
//                        web 7.09 · Android 7.49 · iOS 7.07  (rms ≤ 0.007,
//                        amplitude 0.98–1.04 — the conditioned case)
//   Edge_InsetRoundShadow `inset 0 0 20px rgba(0,0,0,.5)` σ target 10.0
//                        web 8.50 · Android 9.69 · iOS 8.27
// The over-blur existed ONLY in MultipleShadowApplier.kt, which passed the
// raw CSS radius to BlurMaskFilter in three places — and that object had
// ZERO call sites in the runtime, the harness, or any test since the initial
// import (954b38e8; it is in the retro's own dead-code census,
// tools/titan/results/retro-2026-09-04/p2a-compose-dead-remaining.json):
// dead code carrying a dead bug. It is deleted in this wave; the guard test
// below keeps it from coming back. The 2026-08-28 reach probe that produced
// the "2.4×" number is NOT reproducible from this tree (its fixture was never
// committed) and is not a σ error — see the artifact's _note for the two
// extent mechanisms (retro R6 knockout/opacity, round-2 F1 margin bands) the
// wave-50 device gate re-measures.
//
// This file pins the WIRE → Config → Skia-radius path on the converter's
// verbatim output for fixtures/visual-test.json (committed converter at
// 747b28e4), so the parity above is a test, not a memory. Proven able to
// fail: making blurMaskRadius return the raw CSS radius fails all four
// σ pins (RE-RUN this wave: reported σ 6.274 / 9.16 / 12.047 / [2.809, 9.738]
// instead of 5 / 7.5 / 10 / [2, 8]); restoring MultipleShadowApplier.kt from
// HEAD fails the fifth (the deletion guard). Both mutations executed, then
// reverted — all 5 green, and the 41 effects.shadow tests green with them.

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ShadowBlurSigmaParityTest {

    // Skia's radius→σ map (SkBlurMask::ConvertRadiusToSigma), used to round-
    // trip the applier's inversion back to the σ the device will blur with.
    private fun skiaSigma(maskRadius: Float): Float =
        if (maskRadius > 0f) 0.57735f * maskRadius + 0.5f else 0f

    private fun parse(s: String) = Json.parseToJsonElement(s)

    // The wire the converter emits for visual-test.json, byte for byte.
    private val shadowSimple =
        """[{"x":{"px":5.0},"y":{"px":5.0},"blur":{"px":10.0},"c":{"srgb":{"r":0.0,"g":0.0,"b":0.0,"a":0.3},"original":{"r":0,"g":0,"b":0,"a":0.3}}}]"""
    private val shadowColored =
        """[{"x":{"px":0.0},"y":{"px":4.0},"blur":{"px":15.0},"c":{"srgb":{"r":0.20392156862745098,"g":0.596078431372549,"b":0.8588235294117647,"a":0.5},"original":{"r":52,"g":152,"b":219,"a":0.5}}}]"""
    private val edgeInsetRoundShadow =
        """[{"x":{"px":0.0},"y":{"px":0.0},"blur":{"px":20.0},"c":{"srgb":{"r":0.0,"g":0.0,"b":0.0,"a":0.5},"original":{"r":0,"g":0,"b":0,"a":0.5}},"inset":true}]"""
    private val shadowMultiple =
        """[{"x":{"px":0.0},"y":{"px":2.0},"blur":{"px":4.0},"c":{"srgb":{"r":0.0,"g":0.0,"b":0.0,"a":0.1},"original":{"r":0,"g":0,"b":0,"a":0.1}}},{"x":{"px":0.0},"y":{"px":8.0},"blur":{"px":16.0},"c":{"srgb":{"r":0.0,"g":0.0,"b":0.0,"a":0.2},"original":{"r":0,"g":0,"b":0,"a":0.2}}}]"""

    // Decode a BoxShadow wire exactly as EffectsFacade hands it to the
    // extractor, and return each layer's device σ after the applier's map.
    // Dp→px is 1:1 here: the painters call `.toPx()` inside draw, and the
    // JVM suite has no Density — the CSS px IS the Dp value on the wire.
    private fun deviceSigmas(wire: String): List<Float> =
        ShadowExtractor.extractShadowConfig(listOf("BoxShadow" to parse(wire)))
            .shadows.map { skiaSigma(ShadowApplier.blurMaskRadius(it.blurRadius.value)) }

    @Test
    fun `Shadow_Simple 10px blur reaches the device as sigma 5`() {
        // §6.1.2: σ = r/2 = 5. Baseline fit: Android 4.61 / web 5.65 / iOS 4.41
        // (ill-conditioned — see the header; it is the 3-way spread that matters).
        assertEquals(listOf(5.0f), deviceSigmas(shadowSimple).map { Math.round(it * 1000f) / 1000f })
    }

    @Test
    fun `Shadow_Colored 15px blur reaches the device as sigma 7_5`() {
        // σ = 7.5; the CONDITIONED baseline fit measured Android 7.49 against
        // web 7.09 / iOS 7.07 — a 0.4-px spread, not a 2.4× one.
        assertEquals(listOf(7.5f), deviceSigmas(shadowColored).map { Math.round(it * 1000f) / 1000f })
    }

    @Test
    fun `Edge_InsetRoundShadow 20px inset blur reaches the device as sigma 10`() {
        // The inset painter (css-backgrounds-3 §6.1.3 paints inside the padding
        // box) shares blurMaskRadius; σ = 10 (fit: Android 9.69 / web 8.50 / iOS 8.27).
        val cfg = ShadowExtractor.extractShadowConfig(listOf("BoxShadow" to parse(edgeInsetRoundShadow)))
        assertEquals(true, cfg.shadows.single().inset)
        assertEquals(listOf(10.0f), deviceSigmas(edgeInsetRoundShadow).map { Math.round(it * 1000f) / 1000f })
    }

    @Test
    fun `Shadow_Multiple keeps a per-layer sigma`() {
        // Two layers, two radii: 4px → σ 2, 16px → σ 8 — the map is per
        // layer, never a shared constant (the old dead path used one Paint
        // per layer too; this pins that the live path does).
        assertEquals(listOf(2.0f, 8.0f), deviceSigmas(shadowMultiple).map { Math.round(it * 1000f) / 1000f })
    }

    @Test
    fun `the raw-radius MultipleShadowApplier stays deleted`() {
        // Re-introduction guard: the only over-blurring code was this object
        // (raw CSS radius straight into BlurMaskFilter, zero call sites). A
        // class by this name reappearing means the dead path is back.
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.styleconverter.runtime.effects.shadow.MultipleShadowApplier")
        }
    }
}

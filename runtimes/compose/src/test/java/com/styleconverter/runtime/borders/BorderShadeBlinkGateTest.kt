package com.styleconverter.runtime.borders

// Retro R6 (audit finding A7#2) — Blink's light-edge gate for the 3D border
// styles, pinned with the SAME table the Swift twin pins
// (BlinkBorderShade.swift / BordersTests.testLightBandLiftsBlinkGate — the
// twin R6's seam patch 03 landed in the integrated tree), so the natives
// cannot drift. Retro P2e had rewritten this header to "no such iOS test case
// is in the tree … the COMPOSE side alone" — true before that seam applied,
// false after it; round-2 F1 re-trued it (skeptics S3 defect 3, S6 defect 4).
//   - Compose used to lift the light band whenever the dark band collapsed to
//     black (max channel ≤ 0.33); Swift lifted pure black only. Neither is
//     Blink: box_border_painter.cc CalculateBorderStyleColor lightens via
//     Color::Light() iff color_utils::GetContrastRatio(color, color.Dark())
//     < kMinimumBorderEdgeContrastRatio (1.75f), after an early-out for
//     red ≥ 150/255 or green ≥ 92/255.
//   - Color::Dark() is 8-bit-quantised by TRUNCATION (QuantizeTo8Bit) before
//     the contrast is measured; color_utils::Linearize uses the 0.04045 knee.
//   - THE 8-BIT INPUT CONTRACT (round-2 F1 here / F2 on Swift, skeptic S3
//     defect 2): both gates pack their inputs to k/255 by ROUNDING (pack8 —
//     Compose's own Color store) BEFORE deciding, because Compose's live
//     `shade` only ever sees packed channels while iOS reads raw floats, and
//     a raw-vs-packed pair can straddle the 1.75 boundary (the two flip rows
//     below). The Swift table pins the identical 0.2137 grey row; the second
//     row is S3's coloured witness, pinned here on the Kotlin side.
// The v = 0.33 grey is the divergence witness: the old Compose gate lifted it
// (0.33 → 0.66); Blink's contrast is 2.78 ≥ 1.75, so it stays. Reference
// values from a Python port of the same three Chromium functions (the port
// reproduces Blink's early-out boundary exactly — brute force over every
// 8-bit colour finds NO colour with r ≥ 150 or g ≥ 92 that the gate lifts,
// max lifting r = 149, g = 91).
// Proven able to fail: restoring `if (darkMultiplier > 0f) return base` in
// shade() fails `v 0_33 grey is NOT lifted` (0.66 instead of 0.33) and
// `dark blue lifts` (base instead of 0.83); restoring the Swift-style
// black-only rule fails every 0 < v ≤ 0.2 row; removing the pack8 step from
// lightBandLifts fails BOTH flip rows (the 0.2137 grey stops lifting, S3's
// (0.082051, 0.237704, 0.218751) starts) — round-2 F1 ran that mutation.

import androidx.compose.ui.graphics.Color
import com.styleconverter.runtime.borders.sides.BlinkBorderShade
import com.styleconverter.runtime.borders.sides.BorderSideApplier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BorderShadeBlinkGateTest {

    private fun grey(v: Float) = Color(v, v, v)
    // Compose packs sRGB Colors to 8 bits; compare on the 0..255 lattice ±1.
    private fun c255(x: Float) = x * 255f

    // ── the shared table: greys v = 0, 0.1, 0.2, 0.33, 0.463, 0.937 ─────────

    @Test
    fun `v 0 black lifts to kLightenedBlack and darkens to black`() {
        assertTrue(BlinkBorderShade.lightBandLifts(0f, 0f, 0f))          // contrast 1.00
        assertEquals(84f, c255(BorderSideApplier.shade(grey(0f), lighten = true).red), 0.5f)
        assertEquals(0f, c255(BorderSideApplier.shade(grey(0f), lighten = false).red), 0.01f)
    }

    @Test
    fun `v 0_1 grey lifts additively`() {
        // contrast(0.1 grey, black) = 1.20 < 1.75 → Light(): min(1, 0.43)/0.1 = 4.3 → 0.43.
        assertTrue(BlinkBorderShade.lightBandLifts(0.1f, 0.1f, 0.1f))
        assertEquals(c255(0.43f), c255(BorderSideApplier.shade(grey(0.1f), lighten = true).red), 1f)
        assertEquals(0f, c255(BorderSideApplier.shade(grey(0.1f), lighten = false).red), 0.01f)
    }

    @Test
    fun `v 0_2 grey lifts additively`() {
        // contrast 1.66 < 1.75 → 0.53 (the wave-4 pin, still true under Blink).
        assertTrue(BlinkBorderShade.lightBandLifts(0.2f, 0.2f, 0.2f))
        assertEquals(c255(0.53f), c255(BorderSideApplier.shade(grey(0.2f), lighten = true).red), 1f)
    }

    @Test
    fun `v 0_33 grey is NOT lifted - the divergence witness`() {
        // Dark band is black (multiplier clamps to 0) yet contrast(0.33 grey,
        // black) = 2.78 ≥ 1.75: Blink keeps the declared colour. The old
        // max-channel gate lifted it to 0.66.
        assertFalse(BlinkBorderShade.lightBandLifts(0.33f, 0.33f, 0.33f))
        val light = BorderSideApplier.shade(grey(0.33f), lighten = true)
        assertEquals(c255(0.33f), c255(light.red), 1f)
        assertEquals(0f, c255(BorderSideApplier.shade(grey(0.33f), lighten = false).red), 0.01f)
    }

    @Test
    fun `v 0_463 grey keeps its light band and darkens subtractively`() {
        // revert-layer-015's rgb(118,118,118): contrast 3.51 → no lift;
        // dark = 0.463 × (0.463 − 0.33)/0.463 = 0.133.
        assertFalse(BlinkBorderShade.lightBandLifts(0.463f, 0.463f, 0.463f))
        assertEquals(grey(0.463f), BorderSideApplier.shade(grey(0.463f), lighten = true))
        assertEquals(c255(0.133f), c255(BorderSideApplier.shade(grey(0.463f), lighten = false).red), 1f)
    }

    @Test
    fun `v 0_937 grey keeps its light band and darkens to 0_607`() {
        // The fieldset groove #efefef carrier: contrast 2.42 → no lift; dark
        // 0.937 − 0.33 = 0.607.
        assertFalse(BlinkBorderShade.lightBandLifts(0.937f, 0.937f, 0.937f))
        assertEquals(grey(0.937f), BorderSideApplier.shade(grey(0.937f), lighten = true))
        assertEquals(c255(0.607f), c255(BorderSideApplier.shade(grey(0.937f), lighten = false).red), 1f)
    }

    // ── coloured rows ─────────────────────────────────────────────────────

    @Test
    fun `dark blue lifts although its dark band is not black`() {
        // (0.1, 0.1, 0.5): dark = ×0.34 → (0.034, 0.034, 0.17); contrast 1.38
        // < 1.75 → Light(): min(1, 0.83)/0.5 = 1.66 → (0.166, 0.166, 0.83).
        // The old Compose gate returned the base here (dark multiplier > 0).
        assertTrue(BlinkBorderShade.lightBandLifts(0.1f, 0.1f, 0.5f))
        val light = BorderSideApplier.shade(Color(0.1f, 0.1f, 0.5f), lighten = true)
        assertEquals(c255(0.166f), c255(light.red), 1f)
        assertEquals(c255(0.166f), c255(light.green), 1f)
        assertEquals(c255(0.83f), c255(light.blue), 1f)
    }

    @Test
    fun `currentColor green and crimson are never lifted`() {
        // border-color-currentcolor's green (0, 0.502, 0): Blink's early-out
        // (green ≥ 92/255) — and the contrast (3.01) agrees.
        assertFalse(BlinkBorderShade.lightBandLifts(0f, 0.5019608f, 0f))
        // crimson rgb(220,20,60): early-out (red ≥ 150/255); contrast 1.98.
        assertFalse(BlinkBorderShade.lightBandLifts(220f / 255f, 20f / 255f, 60f / 255f))
        // The wave-3 palette base (239,100,50): early-out; contrast 2.07.
        assertFalse(BlinkBorderShade.lightBandLifts(239f / 255f, 100f / 255f, 50f / 255f))
    }

    // ── the 8-bit-input flip rows (round-2 F1 / F2, skeptic S3 defect 2) ──

    @Test
    fun `v 0_2137 grey lifts ONLY on the packed channel - the shared flip row`() {
        // The ONE grey window where the raw and the packed verdicts differ:
        // on the raw float the gate says NO (Dark() is black, contrast
        // (L(0.2137) + 0.05) / 0.05 = 1.7507 ≥ 1.75) but Compose packs the
        // channel to 54/255 = 0.2118 at construction, where the contrast is
        // 1.7378 < 1.75 → LIFT. Before the contract iOS returned the base
        // (0.2137) and Compose 0.5418 — 84/255 apart; both natives now take
        // the PACKED verdict. Swift's BordersTests pins this identical row.
        val rawL = BlinkBorderShade.relativeLuminance(0.2137, 0.2137, 0.2137)
        assertTrue("raw floats: the gate would NOT lift", BlinkBorderShade.contrastRatio(rawL, 0.0) >= 1.75)
        val packedL = BlinkBorderShade.relativeLuminance(54.0 / 255.0, 54.0 / 255.0, 54.0 / 255.0)
        assertTrue("packed 54/255: the gate lifts", BlinkBorderShade.contrastRatio(packedL, 0.0) < 1.75)
        // A DIRECT call with the raw float takes the packed verdict.
        assertTrue(BlinkBorderShade.lightBandLifts(0.2137f, 0.2137f, 0.2137f))
        // Light band = 54/255 + 0.33 = 0.5418 (Compose stores 138/255).
        assertEquals(c255(0.5418f), c255(BorderSideApplier.shade(grey(0.2137f), lighten = true).red), 1f)
        assertEquals(0f, c255(BorderSideApplier.shade(grey(0.2137f), lighten = false).red), 0.01f)
    }

    @Test
    fun `S3 flip colour lifts ONLY on the raw floats - packed it stays`() {
        // S3's flip5 (0.082051, 0.237704, 0.218751), v = 0.2377 ∈ (0, 0.33]:
        // Dark() is black; raw contrast 1.7477 < 1.75 → the un-packed gate
        // LIFTS, but Compose stores (21, 61, 56)/255, where the contrast is
        // 1.7565 ≥ 1.75 → NO lift. Before the contract Kotlin's live path
        // (packed) said no while a raw-float caller — and Swift's live path —
        // said yes. Both now take the packed verdict.
        val rawL = BlinkBorderShade.relativeLuminance(0.082051, 0.237704, 0.218751)
        assertTrue("raw floats: the gate would lift", BlinkBorderShade.contrastRatio(rawL, 0.0) < 1.75)
        val packedL = BlinkBorderShade.relativeLuminance(21.0 / 255.0, 61.0 / 255.0, 56.0 / 255.0)
        assertTrue("packed (21,61,56): the gate does not lift", BlinkBorderShade.contrastRatio(packedL, 0.0) >= 1.75)
        assertFalse(BlinkBorderShade.lightBandLifts(0.082051f, 0.237704f, 0.218751f))
        // The live path agrees: the light band is the declared colour.
        val base = Color(0.082051f, 0.237704f, 0.218751f)
        assertEquals(base, BorderSideApplier.shade(base, lighten = true))
    }

    @Test
    fun `pack8 is Compose's round-half-up channel store`() {
        // 0.2137 · 255 = 54.49 → 54; 0.5 · 255 = 127.5 → 128 (half UP, as
        // Compose's `(c · 255 + 0.5).toInt()` — and Swift's `.rounded()`).
        assertEquals(54.0 / 255.0, BlinkBorderShade.pack8(0.2137f), 1e-12)
        assertEquals(128.0 / 255.0, BlinkBorderShade.pack8(0.5f), 1e-12)
        assertEquals(0.0, BlinkBorderShade.pack8(0f), 0.0)
        assertEquals(1.0, BlinkBorderShade.pack8(1f), 0.0)
        // Idempotent on what the constructor stored: Color(0.2137).red is
        // 54/255f and packs back to the same 54.
        assertEquals(54.0 / 255.0, BlinkBorderShade.pack8(grey(0.2137f).red), 1e-12)
        // S3's flip colour packs to (21, 61, 56).
        assertEquals(21.0 / 255.0, BlinkBorderShade.pack8(0.082051f), 1e-12)
        assertEquals(61.0 / 255.0, BlinkBorderShade.pack8(0.237704f), 1e-12)
        assertEquals(56.0 / 255.0, BlinkBorderShade.pack8(0.218751f), 1e-12)
    }

    // ── the ported Chromium primitives ────────────────────────────────────

    @Test
    fun `quantizeTo8Bit truncates like Color QuantizeTo8Bit`() {
        // 0.5 × nextafterf(256, 0) = 127.99999 → int 127 (rounding would give 128).
        assertEquals(127.0 / 255.0, BlinkBorderShade.quantizeTo8Bit(0.5), 1e-9)
        assertEquals(1.0, BlinkBorderShade.quantizeTo8Bit(1.0), 1e-9)      // 255.99998 → 255
        assertEquals(0.0, BlinkBorderShade.quantizeTo8Bit(0.0), 0.0)
    }

    @Test
    fun `relative luminance and contrast ratio follow color_utils`() {
        // WCAG endpoints: white 1, black 0, contrast 21.
        assertEquals(1.0, BlinkBorderShade.relativeLuminance(1.0, 1.0, 1.0), 1e-6)
        assertEquals(0.0, BlinkBorderShade.relativeLuminance(0.0, 0.0, 0.0), 0.0)
        assertEquals(21.0, BlinkBorderShade.contrastRatio(1.0, 0.0), 1e-6)
        // Symmetric in its arguments.
        assertEquals(BlinkBorderShade.contrastRatio(0.2, 0.05), BlinkBorderShade.contrastRatio(0.05, 0.2), 0.0)
        // The 0.04045 knee (Chromium's, not WCAG 2.0's 0.03928): just below it
        // is the linear branch c/12.92, just above the power branch.
        assertEquals(0.04 / 12.92, BlinkBorderShade.relativeLuminance(0.04, 0.04, 0.04), 1e-9)
        assertEquals(Math.pow((0.05 + 0.055) / 1.055, 2.4), BlinkBorderShade.relativeLuminance(0.05, 0.05, 0.05), 1e-9)
    }
}

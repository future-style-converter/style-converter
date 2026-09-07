package com.styleconverter.runtime.borders

// Fidelity wave 3 (applier campaign, lane B) — pinning tests for the
// Android border-style + border-image divergence fixes:
//
//  1. Chromium 3D palette: shade() paints the LIGHT band as the declared
//     color UNCHANGED and the DARK band per Blink Color::Dark()'s
//     subtractive model — RGB × max(0, (v − 0.33)/v), v = max(r,g,b) —
//     which reproduces the measured 155/239 = 0.649 at v = 0.937. (Wave 4
//     generalized the original single-point ×0.65 fit to this model and
//     added the dark-base Color::Light() special case — see
//     BorderFidelityWave4Test; the measured pins below still hold.) The
//     pre-wave-3 0.5 blends toward white/black tinted BOTH bands away
//     from the declared color, so every groove/ridge/inset/outset border
//     diverged from web on every band pixel.
//  2. Per-edge dash fitting: fittedDashIntervals() scales the nominal
//     dashed rhythm so an integer number of dashes spans each edge — the
//     edge starts AND ends on a full dash like Chromium; the fixed
//     [2w, w] rhythm truncated the final dash mid-way at the corners.
//  3. (SUPERSEDED) Border-image extra content inset: the wave-3 fix made
//     BorderImageBox pad max(0, resolvedWidth − computedBorderWidth). The
//     skeptic pass then showed even that partial inset violates
//     css-backgrounds-3 §5 — border-image properties do not affect layout
//     AT ALL; content is inset by border-width only (Chromium: border
//     10px + border-image-width 15px keeps the content inset at 10px).
//     extraContentInset was deleted; its former pins here went with it.
//     The successor pins live in BorderImageSpecFixesTest.

import androidx.compose.ui.graphics.Color
import com.styleconverter.runtime.borders.sides.BorderSideApplier
import org.junit.Assert.assertEquals
import org.junit.Test

class BorderFidelityWave3Test {

    // ── 1. Chromium 3D shade palette ─────────────────────────────────────

    @Test
    fun `light band is the declared color unchanged`() {
        // Chromium keeps the declared color for the light band of any
        // normal (max channel > 0.33) base — any blend toward white
        // diverges from the capture. (Near-black bases take the
        // Color::Light() lift instead — pinned in Wave4.)
        val base = Color(239 / 255f, 100 / 255f, 50 / 255f, 0.8f)
        assertEquals(base, BorderSideApplier.shade(base, lighten = true))
    }

    @Test
    fun `dark band matches the measured web capture at v of 0_937`() {
        // Measured on the web capture: declared channel 239 → dark band
        // 155 (155/239 = 0.649). Blink Color::Dark() reproduces it:
        // v = 239/255 = 0.937, multiplier (0.937 − 0.33)/0.937 = 0.6479.
        val base = Color(239 / 255f, 100 / 255f, 50 / 255f)
        val dark = BorderSideApplier.shade(base, lighten = false)
        assertEquals(155f, dark.red * 255f, 1f)     // 239 × 0.6479 ≈ 154.9
        assertEquals(65f, dark.green * 255f, 1f)    // 100 × 0.6479 ≈ 64.8
        assertEquals(32.5f, dark.blue * 255f, 1f)   // 50 × 0.6479 ≈ 32.4
    }

    @Test
    fun `dark band preserves alpha`() {
        // Chromium darkens in-gamut without touching transparency.
        val dark = BorderSideApplier.shade(Color(1f, 1f, 1f, 0.4f), lighten = false)
        assertEquals(0.4f, dark.alpha, 0.001f)
    }

    // ── 2. Per-edge dash fitting ─────────────────────────────────────────

    @Test
    fun `an exact-fit edge keeps the nominal thick rhythm`() {
        // 160px edge at w=5: nominal [10, 5]; 11 dashes + 10 gaps = 160
        // exactly (the Chromium-measured fixture shape) — scale 1.0.
        assertEquals(
            listOf(10f, 5f),
            BorderSideApplier.fittedDashIntervals(160f, 5f).toList()
        )
    }

    @Test
    fun `a non-exact edge shrinks both intervals by one factor`() {
        // 97px edge at w=5: n = round-half-up(102/15) = 7 dashes; the
        // nominal 7·10 + 6·5 = 100 shrinks by 0.97 → [9.7, 4.85].
        val fitted = BorderSideApplier.fittedDashIntervals(97f, 5f)
        assertEquals(9.7f, fitted[0], 0.001f)
        assertEquals(4.85f, fitted[1], 0.001f)
        // The invariant the fit exists for: 7 dashes + 6 gaps == the edge,
        // so the edge starts AND ends on a full dash.
        assertEquals(97f, 7 * fitted[0] + 6 * fitted[1], 0.001f)
    }

    @Test
    fun `thin widths fit with their own 6w-4w nominal ratio`() {
        // 218px edge at w=2 (nominal [12, 8]): n = round-half-up(226/20)
        // = 11 dashes; scale 218/212 stretches both intervals equally.
        val fitted = BorderSideApplier.fittedDashIntervals(218f, 2f)
        assertEquals(218f, 11 * fitted[0] + 10 * fitted[1], 0.001f)
        // The on:off ratio stays the nominal 12:8 = 1.5.
        assertEquals(1.5f, fitted[0] / fitted[1], 0.001f)
    }

    @Test
    fun `an edge shorter than one dash paints a single full dash`() {
        // 8px edge at w=5: n clamps to 1, the lone dash spans the whole
        // edge (solid) — Chromium's short-edge degenerate outcome.
        val fitted = BorderSideApplier.fittedDashIntervals(8f, 5f)
        assertEquals(8f, fitted[0], 0.001f)
    }

    @Test
    fun `degenerate zero-length edges fall back to the nominal rhythm`() {
        // Nothing to fit against — return dashedIntervals() unchanged so
        // the caller never sees NaN intervals.
        assertEquals(
            BorderSideApplier.dashedIntervals(5f).toList(),
            BorderSideApplier.fittedDashIntervals(0f, 5f).toList()
        )
    }

    // ── 3. Border-image extra content inset — REMOVED ────────────────────
    // See the header: extraContentInset was deleted per css-backgrounds-3
    // §6 (border-image never affects layout); BorderImageSpecFixesTest
    // pins the replacement dest-expansion behaviour.
}

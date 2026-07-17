package com.styleconverter.runtime.effects.mask

// Lane-M pure-math pins for MaskApplier's gradient geometry.
//
// Two diagnosed Android mask divergences live here:
//  - RADIAL: the old branch hardcoded a circle of radius min(w,h)/2,
//    while CSS defaults to an ELLIPSE sized farthest-corner
//    (css-images-3 §3.5) — on the 160×80 mask fixtures the fade died at
//    x=±40 where web reached the corners. radialMaskRadii mirrors
//    ColorApplier's FARTHEST_CORNER geometry.
//  - CONIC: the old branch used Brush.sweepGradient (0 at 3 o'clock, no
//    start angle) so every conic mask sat rotated +90° vs web.
//    conicMaskRotationDegrees is a delegate to
//    ColorApplier.conicSweepRotationDegrees — asserted equal here so the
//    two paths cannot drift.
//  - RADIAL LOCAL MATRIX: the circle→ellipse squash factors were
//    INVERTED (rMax/radius instead of radius/rMax) — Skia's
//    setLocalMatrix maps the shader image THROUGH the matrix, so the
//    old values stretched the rMax circle along the wrong axis.
//    radialMaskAxisScale delegates to ColorApplier.radialAxisScale
//    (the wave-1 background code the mask deliberately mirrored carried
//    the same inversion); both direction and delegation are pinned here.

import com.styleconverter.runtime.color.ColorApplier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class MaskGradientGeometryTest {

    // ── radialMaskRadii ────────────────────────────────────────────────

    @Test
    fun `default (null shape) is a farthest-corner ellipse`() {
        // Centred on a 160×80 box: farthest side distances are 80/40, and
        // the §3.5 farthest-corner ellipse scales both by √2.
        val (rx, ry) = MaskApplier.radialMaskRadii(null, 0.5f, 0.5f, 160f, 80f)
        assertEquals(80f * sqrt(2f), rx, 1e-3f)
        assertEquals(40f * sqrt(2f), ry, 1e-3f)
    }

    @Test
    fun `explicit ellipse matches the null default`() {
        // ELLIPSE and null must be the same geometry (null = omitted
        // keyword; the CSS grammar default fills it in).
        val a = MaskApplier.radialMaskRadii(null, 0.5f, 0.5f, 160f, 80f)
        val b = MaskApplier.radialMaskRadii(MaskRadialShape.ELLIPSE, 0.5f, 0.5f, 160f, 80f)
        assertEquals(a, b)
    }

    @Test
    fun `circle uses the farthest-corner hypotenuse on both axes`() {
        // Centred circle: r = √(80² + 40²), NOT the old min(w,h)/2 = 40.
        val (rx, ry) = MaskApplier.radialMaskRadii(MaskRadialShape.CIRCLE, 0.5f, 0.5f, 160f, 80f)
        val expected = sqrt(80f * 80f + 40f * 40f)
        assertEquals(expected, rx, 1e-3f)
        assertEquals(expected, ry, 1e-3f)
    }

    @Test
    fun `off-centre centre measures to the farthest side per axis`() {
        // Centre at 25%/25% of 100×100 → farthest distances are 75/75.
        val (rx, ry) = MaskApplier.radialMaskRadii(null, 0.25f, 0.25f, 100f, 100f)
        assertEquals(75f * sqrt(2f), rx, 1e-3f)
        assertEquals(75f * sqrt(2f), ry, 1e-3f)
    }

    // ── conicMaskRotationDegrees (reuse pin) ───────────────────────────

    @Test
    fun `conic mask rotation delegates to the background convention`() {
        // The −90° CSS↔SweepGradient shift has ONE owner (ColorApplier);
        // the mask path must return bit-identical values at every angle.
        for (from in listOf(0f, 45f, 90f, 180f, 270f, 360f)) {
            assertEquals(
                ColorApplier.conicSweepRotationDegrees(from),
                MaskApplier.conicMaskRotationDegrees(from),
                0f
            )
        }
        // And the absolute anchor: from 0deg → −90° (12 o'clock re-home).
        assertEquals(-90f, MaskApplier.conicMaskRotationDegrees(0f), 0f)
    }

    // ── radialMaskAxisScale (squash direction + reuse pin) ─────────────

    @Test
    fun `wide-box ellipse squashes the vertical axis of the circle`() {
        // Centred farthest-corner ellipse on a 160×80 box: rx = 80√2 >
        // ry = 40√2. The circular shader is built at rMax = rx, so the
        // local matrix must leave X alone (sx = 1) and SQUASH Y down to
        // the short radius (sy = ry/rx = 0.5). The inverted pre-fix
        // values (1, 2) stretched Y instead — the ellipse squashed the
        // wrong axis on every wide mask box.
        val (rx, ry) = MaskApplier.radialMaskRadii(null, 0.5f, 0.5f, 160f, 80f)
        val (sx, sy) = MaskApplier.radialMaskAxisScale(rx, ry)
        assertEquals(1f, sx, 1e-6f)
        assertEquals(0.5f, sy, 1e-6f)
        // The direction invariant behind the fix: squash factors never
        // exceed 1 — the circle only ever shrinks toward the ellipse.
        assertTrue(sx <= 1f && sy <= 1f)
    }

    @Test
    fun `tall-box ellipse squashes the horizontal axis instead`() {
        // Mirror case (ry > rx): Y is the max axis (sy = 1) and X takes
        // the squash (sx = rx/ry) — the rule is per-axis, not fixed.
        val (sx, sy) = MaskApplier.radialMaskAxisScale(30f, 90f)
        assertEquals(30f / 90f, sx, 1e-6f)
        assertEquals(1f, sy, 1e-6f)
    }

    @Test
    fun `circles need no squash at all`() {
        // rx == ry → identity scale; the applier skips the local matrix.
        val (sx, sy) = MaskApplier.radialMaskAxisScale(50f, 50f)
        assertEquals(1f, sx, 0f)
        assertEquals(1f, sy, 0f)
    }

    @Test
    fun `radial mask scale delegates to the background convention`() {
        // Like the conic rotation, the squash has ONE owner
        // (ColorApplier.radialAxisScale); the mask path must return
        // bit-identical factors so background and mask radials never
        // drift apart again.
        for ((rx, ry) in listOf(113.14f to 56.57f, 40f to 40f, 0f to 25f)) {
            assertEquals(
                ColorApplier.radialAxisScale(rx, ry),
                MaskApplier.radialMaskAxisScale(rx, ry)
            )
        }
    }

    // ── repeatingStopSpan ──────────────────────────────────────────────

    @Test
    fun `sub-span stops yield the tiling period`() {
        // black 0%, transparent 10% → period segment 0..0.1.
        val span = MaskApplier.repeatingStopSpan(listOf(0f, 0.1f))
        assertNotNull(span)
        assertEquals(0f, span!!.start, 1e-6f)
        assertEquals(0.1f, span.endInclusive, 1e-6f)
    }

    @Test
    fun `full-line stops degrade to the plain gradient`() {
        // §3.4.3: a repeating gradient whose stops span the whole line is
        // identical to its plain counterpart — no segment to tile.
        assertNull(MaskApplier.repeatingStopSpan(listOf(0f, 0.5f, 1f)))
    }

    @Test
    fun `zero-width span has no finite period`() {
        // Degenerate: all stops at one position — fall back to plain.
        assertNull(MaskApplier.repeatingStopSpan(listOf(0.3f, 0.3f)))
        // Empty list is equally degenerate.
        assertNull(MaskApplier.repeatingStopSpan(emptyList()))
    }

    @Test
    fun `interior span keeps its offsets for segment placement`() {
        // black 20%, transparent 60% → segment 0.2..0.6 (period 0.4),
        // placed by lerping the §3.4.1 line endpoints in the applier.
        val span = MaskApplier.repeatingStopSpan(listOf(0.2f, 0.6f))
        assertNotNull(span)
        assertEquals(0.2f, span!!.start, 1e-6f)
        assertEquals(0.6f, span.endInclusive, 1e-6f)
    }
}

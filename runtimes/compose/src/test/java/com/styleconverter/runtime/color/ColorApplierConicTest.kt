package com.styleconverter.runtime.color

// Pins the CSS↔Android angle convention for conic gradients.
//
// css-images-4 §3.3: conic-gradient's 0deg points UP (12 o'clock) and
// angles increase clockwise; the optional `from <angle>` rotates the
// whole gradient. android.graphics.SweepGradient instead anchors its
// 0-position at the +x axis (3 o'clock) sweeping clockwise. The visible
// regression (visual-test Gradient_Conic, `conic-gradient(from 0deg,
// red, yellow, green, blue, red)`): Android rendered red at 3 o'clock
// where web/CSS put it at 12 — the whole wheel sat rotated +90°, and
// the `from` angle was dropped entirely (Android-web SSIM 0.88, 14.9%
// mismatched pixels).
//
// The fix rotates the shader's local matrix by conicSweepRotationDegrees
// (from − 90, clockwise-positive in screen coords). Both the plain
// (ColorApplier.createSweepGradientBrush) and repeating
// (RepeatingGradientHelper.createRepeatingConicGradient) paths share
// this single function, so pinning it here covers both.

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorApplierConicTest {

    @Test
    fun `default from-angle re-homes sweep start to 12 o'clock`() {
        // from 0deg: SweepGradient must be rotated -90° so its 3-o'clock
        // start lands at CSS's 12-o'clock zero.
        assertEquals(-90f, ColorApplier.conicSweepRotationDegrees(0f), 0f)
    }

    @Test
    fun `from-angle offsets compose on top of the convention shift`() {
        // conic-gradient(from 90deg, …): the gradient start sits at
        // 3 o'clock in CSS terms — exactly SweepGradient's native start,
        // so the net rotation must be zero.
        assertEquals(0f, ColorApplier.conicSweepRotationDegrees(90f), 0f)
        // from 45deg → net -45°.
        assertEquals(-45f, ColorApplier.conicSweepRotationDegrees(45f), 0f)
        // from 270deg (9 o'clock) → +180°.
        assertEquals(180f, ColorApplier.conicSweepRotationDegrees(270f), 0f)
    }
}

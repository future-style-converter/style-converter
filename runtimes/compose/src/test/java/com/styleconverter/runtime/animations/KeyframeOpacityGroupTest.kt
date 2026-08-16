package com.styleconverter.runtime.animations

// Wave 44 (lane U4) — pins for the ANIMATED-opacity NO-CLIP fix.
//
// The defect class is the wave-43 CSS-opacity one, surviving in the legacy
// modifier-space animation path (the wave-8 property-space driver overlays
// wire keyframes as plain Opacity IRProperties, which already ride the
// fixed color/OpacityApplier; THIS path fires for built-in-name animations
// with no wire keyframes — see ComponentRenderer's dispatch guard): each
// interpolated frame applied opacity via `Modifier.alpha`, which IS
// graphicsLayer(alpha, clip = TRUE) (ui-android 1.11.4 AlphaKt bytecode —
// color/OpacityApplierTest's header carries the full physics), so children
// overflowing the animated element were cropped on every mid-animation
// frame. An animated `opacity` is the SAME css-color-4 §2.2 property, only
// time-varying (web-animations-1 animates the computed value and adds no
// clip), so the fix reuses OpacityApplier.applyOpacity — the unbounded
// saveLayerAlpha group — verbatim, and these pins assert the identical
// structural contract in interpolated-frame space.
//
// JVM-only, like the rest of this suite: applyInterpolatedState is a plain
// (non-composable) chain builder, so frames fold without a composition
// host and without touching android.graphics (a throwing stub here).

import androidx.compose.ui.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyframeOpacityGroupTest {

    // Chain-introspection helper (OpacityApplierTest idiom): fold the
    // modifier into concrete element class names so the install/skip
    // decision is assertable without a renderer.
    private fun elementNames(modifier: Modifier): List<String> =
        modifier.foldIn(emptyList<String>()) { acc, element -> acc + element.javaClass.name }

    @Test
    fun `mid-animation opacity frame installs one unbounded draw-time group`() {
        // A 50%-progress fade frame (the fadeIn built-in at t = duration/2).
        val names = elementNames(
            KeyframeAnimationApplier.applyInterpolatedState(
                Modifier, InterpolatedKeyframe(opacity = 0.5f)
            )
        )
        // Opacity is the frame's only property → exactly the group element.
        assertEquals(1, names.size)
        assertTrue(
            "expected the draw-time group element in $names",
            names.any { it.contains("DrawWithContent") }
        )
    }

    @Test
    fun `mid-animation opacity frame installs NO graphicsLayer of any kind`() {
        val names = elementNames(
            KeyframeAnimationApplier.applyInterpolatedState(
                Modifier, InterpolatedKeyframe(opacity = 0.5f)
            )
        )
        // The whole point of the fix: ANY GraphicsLayer element for an
        // opacity-only frame means node-bounds buffer physics returned —
        // clip=true crops at record time (AlphaKt) and even clip=false
        // crops at composite time (HWUI's overlapping-rendering
        // saveLayerAlpha at node bounds — OpacityApplier's KDoc).
        assertFalse(
            "no graphicsLayer may install in $names",
            names.any { it.contains("GraphicsLayer") }
        )
    }

    @Test
    fun `opacity 1 frame is the identity - no layer no group`() {
        // The animation's resting frame (fadeIn at t = end): applyOpacity's
        // >= 1 fast path keeps the chain byte-identical to a no-animation
        // element — parity with the retired `.alpha(1f)` fast path.
        assertEquals(
            emptyList<String>(),
            elementNames(
                KeyframeAnimationApplier.applyInterpolatedState(
                    Modifier, InterpolatedKeyframe(opacity = 1f)
                )
            )
        )
    }

    @Test
    fun `opacity 0 frame still installs the group`() {
        // The fade's starting frame: fully transparent MUST install the
        // group (an alpha-0 layer draws nothing) — never fall through to a
        // visible first frame.
        val names = elementNames(
            KeyframeAnimationApplier.applyInterpolatedState(
                Modifier, InterpolatedKeyframe(opacity = 0f)
            )
        )
        assertEquals(1, names.size)
        assertTrue(names.any { it.contains("DrawWithContent") })
    }

    @Test
    fun `no opacity in the frame - no group installs`() {
        // A transform-only frame must not pay for an opacity group: the
        // null-opacity branch skips applyOpacity entirely.
        val names = elementNames(
            KeyframeAnimationApplier.applyInterpolatedState(
                Modifier, InterpolatedKeyframe(translateX = 10f)
            )
        )
        assertFalse(names.any { it.contains("DrawWithContent") })
    }

    @Test
    fun `opacity plus transform frame - group for alpha, one layer for transform only`() {
        // Transforms LEGITIMATELY ride graphicsLayer (translation/scale/
        // rotation are layer properties; a transform also clips nothing by
        // itself since clip stays default-false on that call). The pin: the
        // frame's alpha must NOT be folded into that transform layer — one
        // DrawWithContent group for opacity, exactly one GraphicsLayer for
        // the transform, coexisting.
        val names = elementNames(
            KeyframeAnimationApplier.applyInterpolatedState(
                Modifier, InterpolatedKeyframe(opacity = 0.5f, translateX = 10f)
            )
        )
        assertEquals(
            "expected exactly one opacity group in $names",
            1, names.count { it.contains("DrawWithContent") }
        )
        assertEquals(
            "expected exactly one transform layer in $names",
            1, names.count { it.contains("GraphicsLayer") }
        )
    }
}

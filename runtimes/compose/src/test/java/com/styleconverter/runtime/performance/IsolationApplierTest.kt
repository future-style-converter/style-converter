package com.styleconverter.runtime.performance

// Wave 42 (lane W8) — pins for the isolation OFFSCREEN-CLIP fix.
//
// The defect (wave-41 T5 verification, WPT filter-effects/
// backdrop-filter-isolation-isolate.html, android-ref 0.8811):
// `isolation: isolate` was implemented as `graphicsLayer(compositingStrategy
// = CompositingStrategy.Offscreen)`. HWUI sizes an offscreen-composited
// RenderNode's buffer to the NODE'S OWN BOUNDS, and the isolation modifier
// installs at StyleApplier step 0 — OUTER of step 4's absoluteOffset — so
// the `.stacking-context` (`isolation: isolate; left: 120px`, holding a
// 160×160 abspos child at `left: -90px`) rendered ONLY the ink
// intersecting its un-offset 100×100 slot (visible patch == predicted
// intersection, T5's exact measurement). css-compositing-1 §4 gives the
// isolated group NO clip.
//
// The fix swaps the strategy for an UNBOUNDED canvas.saveLayer group
// (bounds = null → sized to the current clip; the same replace-the-buffer
// precedent as FilterApplier's colour-matrix saveLayer). These pins assert
// the chain decision on the JVM: platform saveLayer calls stay inside the
// draw lambda (android.graphics is a throwing stub here), so the chain can
// be folded without drawing — the FilterGroupApplierTest technique.

import androidx.compose.ui.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsolationApplierTest {

    // Fold the modifier into concrete element class names — install/skip
    // decisions become assertable without a renderer.
    private fun elementNames(modifier: Modifier): List<String> =
        modifier.foldIn(emptyList<String>()) { acc, element -> acc + element.javaClass.name }

    @Test
    fun `AUTO is the identity - no layer of any kind`() {
        // The default costs nothing: same Modifier instance out.
        val base = Modifier
        val result = IsolationApplier.applyIsolation(
            base, IsolationConfig(IsolationConfig.Value.AUTO)
        )
        assertEquals(emptyList<String>(), elementNames(result))
    }

    @Test
    fun `ISOLATE installs a draw-time group and NO graphicsLayer`() {
        val names = elementNames(
            IsolationApplier.applyIsolation(
                Modifier, IsolationConfig(IsolationConfig.Value.ISOLATE)
            )
        )
        // Exactly one drawWithContent node — the unbounded saveLayer group.
        assertTrue(
            "expected the draw-time group element in $names",
            names.any { it.contains("DrawWithContent") }
        )
        // And no graphicsLayer: the Offscreen strategy's node-bounds buffer
        // is the measured clip physics this fix retires — reintroducing ANY
        // graphicsLayer here would bring back a fixed-size surface.
        assertFalse(
            "no graphicsLayer may install in $names",
            names.any { it.contains("GraphicsLayer") }
        )
    }

    @Test
    fun `ISOLATE chain builds without touching platform classes`() {
        // The JVM discipline pin: building (not drawing) the chain must
        // never construct android.graphics objects — they throw here. The
        // successful foldIn above already proves it, but assert the size
        // too so an accidental second element is caught.
        val names = elementNames(
            IsolationApplier.applyIsolation(
                Modifier, IsolationConfig(IsolationConfig.Value.ISOLATE)
            )
        )
        assertEquals(1, names.size)
    }
}

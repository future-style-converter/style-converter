package com.styleconverter.runtime.effects.filter

// Wave 44 (lane U4) — pins for the `filter: opacity()` NO-CLIP fix.
//
// The defect class is the wave-43 CSS-opacity one, surviving here: the
// filter function's opacity was applied via `Modifier.alpha`, which IS
// graphicsLayer(alpha, clip = TRUE) (ui-android 1.11.4 AlphaKt bytecode:
// default-mask 520187 leaves alpha + clip the only explicit params, clip
// pushed iconst_1) — a record-time crop at the node's own bounds. Filter
// Effects 1 §10.2 defines `opacity()` as a pure alpha multiply over the
// element's rendering and gives filter functions no border-box clip, so
// the fix reuses color/OpacityApplier.applyOpacity — the unbounded
// saveLayerAlpha group wave 43 installed for the `opacity` PROPERTY —
// verbatim, and these pins assert the same structural contract
// OpacityApplierTest asserts there: a draw-time group installs, and NO
// GraphicsLayer element of any kind (clip=false wouldn't save it either:
// HWUI still simulates a node-bounds layer for alpha<1 overlapping
// rendering — OpacityApplier's KDoc carries the full physics).
//
// JVM-only, like the rest of this suite: android.graphics is a throwing
// stub, which is exactly why applyOpacity keeps saveLayerAlpha inside the
// draw lambda and why these chains can be folded without drawing.

import androidx.compose.ui.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterOpacityGroupTest {

    // Chain-introspection helper (OpacityApplierTest / FilterGroupApplierTest
    // idiom): fold the modifier into concrete element class names so the
    // install/skip decision is assertable without a renderer.
    private fun elementNames(modifier: Modifier): List<String> =
        modifier.foldIn(emptyList<String>()) { acc, element -> acc + element.javaClass.name }

    // A `filter: opacity(amount)`-only config — the Filter_Opacity fixture
    // shape (fixtures/properties/effects/filter-functions.json).
    private fun opacityConfig(amount: Float) =
        FilterConfig(filters = listOf(FilterFunction.Opacity(amount)))

    @Test
    fun `filter opacity below 1 installs one unbounded draw-time group`() {
        val names = elementNames(FilterApplier.applyForegroundFilters(Modifier, opacityConfig(0.5f)))
        // Exactly one drawWithContent node — the unbounded saveLayer group
        // (blur / drop-shadow absent, so the chain is the group alone).
        assertEquals(1, names.size)
        assertTrue(
            "expected the draw-time group element in $names",
            names.any { it.contains("DrawWithContent") }
        )
    }

    @Test
    fun `filter opacity below 1 installs NO graphicsLayer of any kind`() {
        val names = elementNames(FilterApplier.applyForegroundFilters(Modifier, opacityConfig(0.5f)))
        // The whole point of the fix: ANY GraphicsLayer element here means
        // node-bounds buffer physics returned — clip=true crops at record
        // time (AlphaKt) and even clip=false crops at composite time
        // (HWUI's overlapping-rendering saveLayerAlpha at node bounds).
        assertFalse(
            "no graphicsLayer may install in $names",
            names.any { it.contains("GraphicsLayer") }
        )
    }

    @Test
    fun `filter opacity 1 is the identity - no layer no group`() {
        // `opacity(1)` = identity filter: applyOpacity's >= 1 fast path
        // returns the modifier unchanged (parity with the retired
        // `.alpha(1f)`'s "== 1f -> this" fast path).
        assertEquals(
            emptyList<String>(),
            elementNames(FilterApplier.applyForegroundFilters(Modifier, opacityConfig(1f)))
        )
    }

    @Test
    fun `filter opacity above 1 clamps to 1 and stays the identity`() {
        // Filter Effects 1 §10.2: "Values of amount over 100% are allowed
        // but UAs must clamp the values to 1" — 1.5 renders like 1, which
        // renders normally (no group). The clamp moved from the retired
        // explicit coerceIn at this call site INTO applyOpacity; this pin
        // proves it was preserved, not dropped.
        assertEquals(
            emptyList<String>(),
            elementNames(FilterApplier.applyForegroundFilters(Modifier, opacityConfig(1.5f)))
        )
    }

    @Test
    fun `negative filter opacity clamps to 0 and still installs the group`() {
        // Negative amounts clamp to 0 (fully transparent): the group MUST
        // install — an alpha-0 layer draws nothing — never fall through to
        // a visible element.
        val names = elementNames(FilterApplier.applyForegroundFilters(Modifier, opacityConfig(-0.5f)))
        assertEquals(1, names.size)
        assertTrue(names.any { it.contains("DrawWithContent") })
    }

    @Test
    fun `full applyFilters route wires opacity through the group`() {
        // The live route StyleApplier takes: applyFilters = group colour
        // filters + backdrop + foreground. With opacity as the only filter,
        // the first two halves are identities (no matrix ops, no backdrop),
        // so the chain must again be the unclipped group alone.
        val names = elementNames(FilterApplier.applyFilters(Modifier, opacityConfig(0.5f)))
        assertEquals(1, names.size)
        assertTrue(names.any { it.contains("DrawWithContent") })
        assertFalse(names.any { it.contains("GraphicsLayer") })
    }

    @Test
    fun `stacked opacity filters stay per-application - two independent groups`() {
        // `filter: opacity(0.5) opacity(0.5)` multiplies to 0.25 (§10.2 —
        // each function operates on the previous function's output), so two
        // list entries must leave two INDEPENDENT groups in the chain; a
        // "merge to the last alpha" optimisation would render 0.5. Mirror
        // of OpacityApplierTest's stacked-opacity nesting pin.
        val cfg = FilterConfig(
            filters = listOf(FilterFunction.Opacity(0.5f), FilterFunction.Opacity(0.5f))
        )
        val names = elementNames(FilterApplier.applyForegroundFilters(Modifier, cfg))
        assertEquals(
            "expected exactly two nested groups in $names",
            2, names.count { it.contains("DrawWithContent") }
        )
        // And neither application may reach for a layer node.
        assertFalse(names.any { it.contains("GraphicsLayer") })
    }
}

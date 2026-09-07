package com.styleconverter.runtime.effects.filter

// Retro R6 (audit finding A12#2) — chain-shape pins for the `filter: blur()`
// route. The defect was structural: Modifier.blur installs a
// BlockGraphicsLayerElement (graphicsLayer { renderEffect; clip = false })
// whose RenderNode is sized to the node bounds, and HWUI renders a
// RenderEffect node into a buffer of exactly that size — every descendant
// pixel outside the element was cropped before the blur ran (Filter_Blur:
// "FIL" on Android where iOS/web show the whole blurred label). The fix
// records the subtree into a drawWithCache-owned GraphicsLayer sized to the
// canvas' current clip (BlurLayerNode.apply), so the chain must
// carry a DrawWithCache element and NO graphicsLayer element of any kind —
// the same structural contract FilterOpacityGroupTest asserts for opacity().
//
// Proven able to fail: reverting the blur loop to `Modifier.blur(…)` leaves a
// `BlockGraphicsLayerElement` in the chain, failing every test below.
// JVM-only: the layer, the RenderEffect and the clip read all live inside the
// draw lambda, which folding the chain never runs.

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterBlurUnboundedLayerTest {

    // Same chain-introspection helper as FilterOpacityGroupTest.
    private fun elementNames(modifier: Modifier): List<String> =
        modifier.foldIn(emptyList<String>()) { acc, element -> acc + element.javaClass.name }

    // The Filter_Blur fixture shape (`filter: blur(3px)`).
    private fun blurConfig(vararg sigmasPx: Float) =
        FilterConfig(filters = sigmasPx.map { FilterFunction.Blur(it.dp) })

    @Test
    fun `blur installs one drawWithCache layer node and no graphicsLayer`() {
        val names = elementNames(FilterApplier.applyForegroundFilters(Modifier, blurConfig(3f)))
        // Exactly one node: the clip-sized recording layer.
        assertEquals(names.joinToString(), 1, names.size)
        assertTrue("expected the drawWithCache layer node in $names", names[0].contains("DrawWithCache"))
        // The whole point: a graphicsLayer element here means node-bounds
        // buffer physics returned (Modifier.blur = BlockGraphicsLayerElement).
        assertFalse("no graphicsLayer may install in $names", names.any { it.contains("GraphicsLayer") })
    }

    @Test
    fun `each blur function gets its own layer - two functions nest two layers`() {
        // `filter: blur(2px) blur(3px)`: the second blurs the first's output
        // (filter-effects-1 §6.1 functions apply in order), so two entries
        // must leave two independent layer nodes, never a merged σ.
        val names = elementNames(FilterApplier.applyForegroundFilters(Modifier, blurConfig(2f, 3f)))
        assertEquals(names.joinToString(), 2, names.count { it.contains("DrawWithCache") })
        assertFalse(names.any { it.contains("GraphicsLayer") })
    }

    @Test
    fun `full applyFilters route keeps the blur on the layer node`() {
        // The live StyleApplier route: group colour filters (identity here,
        // no matrix ops) + backdrop (identity, none declared) + foreground.
        val names = elementNames(FilterApplier.applyFilters(Modifier, blurConfig(3f)))
        assertEquals(names.joinToString(), 1, names.size)
        assertTrue(names[0].contains("DrawWithCache"))
        assertFalse(names.any { it.contains("GraphicsLayer") })
    }

    @Test
    fun `blur keeps its place in the by-type chain - inner of drop-shadow, outer of opacity`() {
        // Pre-existing by-type order (blur, drop-shadow, opacity) must not
        // move with the node swap: chain order is outer-first in foldIn.
        val cfg = FilterConfig(
            filters = listOf(
                FilterFunction.Opacity(0.5f),
                FilterFunction.DropShadow(1.dp, 1.dp, 0.dp, androidx.compose.ui.graphics.Color.Red),
                FilterFunction.Blur(3.dp),
            )
        )
        val names = elementNames(FilterApplier.applyForegroundFilters(Modifier, cfg))
        assertEquals(names.joinToString(), 3, names.size)
        assertTrue(names[0].contains("DrawWithCache"))     // blur layer
        assertTrue(names[1].contains("DrawBehind"))        // drop-shadow silhouette
        assertTrue(names[2].contains("DrawWithContent"))   // opacity group
    }
}

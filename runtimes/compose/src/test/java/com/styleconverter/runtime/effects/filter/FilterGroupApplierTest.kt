package com.styleconverter.runtime.effects.filter

// Pins the wave-41 element-filter GROUP layer (the colour-matrix half of the
// `filter` list moved OUTER of the backplate node, applied via saveLayer
// with offset-aware bounds) — the fix for the two independent defects
// measured on WPT `css/filter-effects/backdrop-filter-plus-filter.html`
// (wave40-final gate run):
//
//  1. TOTAL CROP: the previous `graphicsLayer { renderEffect }` path forced
//     an offscreen buffer sized to the node's UN-offset layout slot, so the
//     `.bluebox` (`filter: invert(1); position: absolute; left: 60px;
//     top: 110px`) — whose offset box does not even intersect its slot —
//     rendered ZERO non-white pixels on Android (web 1.0000, Android
//     0.9168). The saveLayer path takes explicit bounds instead, computed
//     by the pure FilterGroupGeometry.groupLayerBounds pinned here.
//
//  2. SPEC ORDER: filter-effects-2 §2 composites the filtered backdrop as
//     the bottom-most content of the element's group and renders the group
//     through the element's own `filter`; Chrome's ref (dark purple =
//     invert over the blurred backplate) is the pixel proof. The chain
//     placement itself is pinned at the source in
//     BackdropContractParityTest; here we pin the value-level halves.
//
// JVM-only, like the rest of this suite: no Robolectric, android.graphics
// is a throwing stub — which is exactly why the applier builds its
// ColorFilter INSIDE the draw lambda and why these chains can be folded
// without drawing.

import androidx.compose.ui.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterGroupApplierTest {

    // Same chain-introspection helper as ShadowApplierTest: fold the
    // modifier into its concrete element class names so we can assert the
    // install/skip decision without a renderer.
    private fun elementNames(modifier: Modifier): List<String> =
        modifier.foldIn(emptyList<String>()) { acc, element -> acc + element.javaClass.name }

    // ---------------------------------------------------------------
    // FilterGroupGeometry.groupLayerBounds — the saveLayer bounds math
    // ---------------------------------------------------------------

    @Test
    fun `zero position offset degenerates to the un-offset node box`() {
        // The byte-identity guarantee for every static element (the
        // overwhelming majority, and all committed baseline captures): with
        // no offset the bounds must equal EXACTLY the (0,0,w,h) rect the
        // pre-fix fallback path always used.
        val rect = FilterGroupGeometry.groupLayerBounds(
            widthPx = 100f, heightPx = 50f,
            positionOffsetXPx = 0f, positionOffsetYPx = 0f,
        )
        assertEquals(0f, rect.left)
        assertEquals(0f, rect.top)
        assertEquals(100f, rect.right)
        assertEquals(50f, rect.bottom)
    }

    @Test
    fun `positive offset extends the far edges and keeps the near ones`() {
        // The measured plus-filter geometry: a 100×100 box offset by
        // (60,110). The union must reach the offset box's far corner
        // (160,210) — the region the RenderEffect buffer used to discard —
        // while still containing the un-offset slot from (0,0).
        val rect = FilterGroupGeometry.groupLayerBounds(
            widthPx = 100f, heightPx = 100f,
            positionOffsetXPx = 60f, positionOffsetYPx = 110f,
        )
        assertEquals(0f, rect.left)
        assertEquals(0f, rect.top)
        assertEquals(160f, rect.right)
        assertEquals(210f, rect.bottom)
    }

    @Test
    fun `negative offset extends the near edges and keeps the far ones`() {
        // `right`/`bottom` insets resolve to NEGATIVE offsets
        // (PositionConfig.offsetX) — paint slides left/up past the slot
        // origin, so the union's near edges must follow it.
        val rect = FilterGroupGeometry.groupLayerBounds(
            widthPx = 100f, heightPx = 100f,
            positionOffsetXPx = -90f, positionOffsetYPx = -30f,
        )
        assertEquals(-90f, rect.left)
        assertEquals(-30f, rect.top)
        assertEquals(100f, rect.right)
        assertEquals(100f, rect.bottom)
    }

    @Test
    fun `mixed-sign offset extends one near and one far edge`() {
        // x negative (extends left), y positive (extends bottom) — each axis
        // resolves independently.
        val rect = FilterGroupGeometry.groupLayerBounds(
            widthPx = 40f, heightPx = 60f,
            positionOffsetXPx = -10f, positionOffsetYPx = 25f,
        )
        assertEquals(-10f, rect.left)
        assertEquals(0f, rect.top)
        assertEquals(40f, rect.right)
        assertEquals(85f, rect.bottom)
    }

    // ---------------------------------------------------------------
    // applyGroupColorFilters — install/skip and the foreground split
    // ---------------------------------------------------------------

    @Test
    fun `a colour-matrix filter installs exactly one draw node`() {
        // The plus-filter element's own filter: invert(1). One group node,
        // nothing else — the layer itself only materialises at draw time.
        val config = FilterConfig(filters = listOf(FilterFunction.Invert(1f)))
        val names = elementNames(FilterApplier.applyGroupColorFilters(Modifier, config))
        assertEquals("one modifier element expected, got $names", 1, names.size)
    }

    @Test
    fun `no colour-matrix filters means no node at all`() {
        // Blur is NOT a matrix op — it stays in applyForegroundFilters — so
        // a blur-only chain must leave the modifier untouched here (the
        // "overwhelming majority pay nothing" guarantee).
        val config = FilterConfig(
            filters = listOf(FilterFunction.Blur(androidx.compose.ui.unit.Dp(4f))),
        )
        assertTrue(elementNames(FilterApplier.applyGroupColorFilters(Modifier, config)).isEmpty())
        // And an empty config is trivially untouched too.
        assertTrue(elementNames(FilterApplier.applyGroupColorFilters(Modifier, FilterConfig())).isEmpty())
    }

    @Test
    fun `applyForegroundFilters no longer installs anything for a matrix-only chain`() {
        // The split's other half: invert/sepia/etc must NOT be applied a
        // second time inside the shadow step — the group node above is now
        // their only home. A matrix-only foreground call is a no-op.
        val config = FilterConfig(filters = listOf(FilterFunction.Invert(1f)))
        assertTrue(elementNames(FilterApplier.applyForegroundFilters(Modifier, config)).isEmpty())
    }

    @Test
    fun `the combined matrix survives the split unchanged`() {
        // generateColorMatrix feeds off the same buildCombinedColorMatrix the
        // group node uses; pin invert(1)'s row form (scale 1−2a = −1, bias
        // a·255 = 255 — filter-effects-1 §8.6) so a refactor of the split
        // cannot silently alter the matrix the layer composites with.
        val matrix = FilterApplier.generateColorMatrix(
            FilterConfig(filters = listOf(FilterFunction.Invert(1f))),
        )!!
        assertEquals(-1f, matrix.values[0])   // R scale
        assertEquals(255f, matrix.values[4])  // R bias
        assertEquals(-1f, matrix.values[6])   // G scale
        assertEquals(255f, matrix.values[9])  // G bias
        assertEquals(-1f, matrix.values[12])  // B scale
        assertEquals(255f, matrix.values[14]) // B bias
        assertEquals(1f, matrix.values[18])   // alpha untouched (§8.6)
    }
}

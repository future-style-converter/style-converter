package com.styleconverter.runtime.effects

// Wave 55 (lane L6, unit U3 FO-pin) — pin P4, test only. BACKLOG 0(k)(2):
// does Compose apply an element's `filter` BEFORE its `clip-path`?
// filter-effects-1 §5 ("The filter property"): "first any filter effect is
// applied, then any clipping, masking and opacity" (css-masking-1 #placement,
// the test's own help anchor). A Compose chain paints outermost-first, so the
// clip must be OUTER of the drop-shadow draw node: the shadow is recorded
// inside the clip layer and cut by it. Reversed, the clipped square would
// cast its own shadow outside the clip (the geometry probe's
// FO-filter-after-clip fake: 10 335 reddish px).
//
// The verbatim wire: tools/titan/runs/wave54-final/sections/css-masking/
// per-test-ir/wpt__css-masking__clip-path__clip-path-filter-order.json,
// element `…__1-072` (all seven properties). What this pin cannot say:
// whether the shadow RENDERS on device — on this test's pixels a dropped
// shadow and a clipped one look the same (the [W1] evidence window).

// Compose's modifier chain type, walked by foldIn.
import androidx.compose.ui.Modifier
// The full chain, for the end-to-end variant of the pin.
import com.styleconverter.runtime.StyleApplier
// Parses the verbatim wire payloads below.
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
// JUnit4 (this module's runner).
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectsClipFilterOrderTest {

    /** IR property pair, as the extractors consume it. */
    private fun p(type: String, json: String): Pair<String, JsonElement?> = type to Json.parseToJsonElement(json)

    /** `…clip-path-filter-order__1-072`, verbatim (7 properties). */
    private val wire = listOf(
        p("Position", "\"ABSOLUTE\""),
        p("Width", """{"type":"length","px":400}"""),
        p("Height", """{"type":"length","px":400}"""),
        p("BackgroundColor", """{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}"""),
        p("WillChange", """[{"type":"property-name","name":"transform"}]"""),
        p("Filter", """[{"fn":"drop-shadow","x":{"px":16},"y":{"px":16},"r":{"px":20},"c":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}]"""),
        p("ClipPath", """{"type":"inset","t":{"px":0},"r":{"px":200},"b":{"px":200},"l":{"px":0}}"""),
    )

    /** True for a `Modifier.clip` node: graphicsLayer(shape, clip = true). */
    private fun isClipLayer(e: Modifier.Element) = e.javaClass.simpleName == "GraphicsLayerElement" &&
        // GraphicsLayerElement's private `clip` field, read reflectively (OpacityApplierTest idiom).
        e.javaClass.getDeclaredField("clip").apply { isAccessible = true }.getBoolean(e)

    /** True for the drop-shadow node FilterApplier.applyDropShadow installs. */
    private fun isShadow(e: Modifier.Element) = e.javaClass.simpleName == "DrawBehindElement"

    /** Asserts one clip layer and one shadow node, the clip FIRST (outer). */
    private fun assertClipOuterOfShadow(chain: List<Modifier.Element>) {
        val names = chain.map { it.javaClass.simpleName }
        // The clip-path inset is installed …
        assertEquals("one clip layer in $names", 1, chain.count(::isClipLayer))
        // … the drop-shadow is extracted and installed (not dropped) …
        assertEquals("one drop-shadow node in $names", 1, chain.count(::isShadow))
        // … and the clip wraps it: filter first, then clip (filter-effects-1 §5).
        assertTrue("clip must be OUTER of the drop-shadow in $names",
            chain.indexOfFirst(::isClipLayer) < chain.indexOfFirst(::isShadow))
    }

    @Test fun `P4a - EffectsFacade installs the clip outer of the drop-shadow`() {
        // The effects step on its own, from the same extractors StyleApplier uses.
        val cfg = EffectsFacade.extractConfig(wire, wptCaptureMode = true)
        assertClipOuterOfShadow(EffectsFacade.apply(Modifier, cfg).foldIn(listOf()) { acc, e -> acc + e })
    }

    @Test fun `P4b - the full StyleApplier chain keeps that order`() {
        // End to end: extractConfig → applyConfig, the renderer's own chain, both in capture mode as the captures run.
        val chain = StyleApplier.applyConfig(Modifier, StyleApplier.extractConfig(wire, wptCaptureMode = true), wptCaptureMode = true)
            .foldIn(listOf<Modifier.Element>()) { acc, e -> acc + e }
        assertClipOuterOfShadow(chain)
    }
}

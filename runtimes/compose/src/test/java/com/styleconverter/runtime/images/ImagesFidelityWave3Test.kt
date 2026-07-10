package com.styleconverter.runtime.images

// Fidelity wave 3 — pinning tests for the images-cluster collapse:
//
// ObjectFitExtractor.extractObjectPosition must NEVER throw on the
// object-shaped axes ObjectPositionPropertyParser.kt emits
// ({"type":"keyword","value":"CENTER"}, {"type":"percentage",…},
// {"type":"length",…}). The old bare `.jsonPrimitive` threw on every
// object axis, aborting StyleApplier.extractConfig for the whole element —
// Images_C01 rendered its 200x64 green box as a bare label (0.617 / 33%).

import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.styleconverter.runtime.StyleApplier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagesFidelityWave3Test {

    private fun j(s: String): JsonElement = Json.parseToJsonElement(s)

    @Test
    fun `object-position keyword objects extract without throwing`() {
        // Exact IR of Images_C01: `object-position: center`.
        val config = ObjectFitExtractor.extractObjectFitConfig(
            listOf(
                "ObjectFit" to j("\"NONE\""),
                "ObjectPosition" to
                    j("""{"x":{"type":"keyword","value":"CENTER"},"y":{"type":"keyword","value":"CENTER"}}""")
            )
        )
        assertEquals(ObjectFitValue.NONE, config.fit)
        // Center/center → the alignment must place a 0-size child at the
        // middle of the space (space 100x100, size 0 → offset 50,50).
        val offset = config.alignment.align(IntSize.Zero, IntSize(100, 100), LayoutDirection.Ltr)
        assertEquals(50, offset.x)
        assertEquals(50, offset.y)
    }

    @Test
    fun `object-position axis keywords bind to their own axis`() {
        // css-values-4 <position>: "bottom right" stored positionally as
        // x=bottom / y=right must resolve horizontal=right, vertical=bottom.
        val config = ObjectFitExtractor.extractObjectFitConfig(
            listOf(
                "ObjectPosition" to
                    j("""{"x":{"type":"keyword","value":"BOTTOM"},"y":{"type":"keyword","value":"RIGHT"}}""")
            )
        )
        val offset = config.alignment.align(IntSize.Zero, IntSize(100, 100), LayoutDirection.Ltr)
        // right → x pinned to the far edge; bottom → y pinned to the far edge.
        assertEquals(100, offset.x)
        assertEquals(100, offset.y)
    }

    @Test
    fun `object-position percentage objects fall back to center without throwing`() {
        // {"type":"percentage","percentage":25} — the offset channel isn't
        // wired into the Alignment lambda; the contract is "never crash".
        val config = ObjectFitExtractor.extractObjectFitConfig(
            listOf(
                "ObjectPosition" to
                    j("""{"x":{"type":"percentage","percentage":25.0},"y":{"type":"percentage","percentage":75.0}}""")
            )
        )
        val offset = config.alignment.align(IntSize.Zero, IntSize(100, 100), LayoutDirection.Ltr)
        assertEquals(50, offset.x)
        assertEquals(50, offset.y)
    }

    @Test
    fun `full Images_C01 cluster extracts without aborting the style bundle`() {
        // The collapse guard: the exact converter output for Images_C01 —
        // image-rendering:smooth + object-fit:none + object-position:center
        // + object-view-box:none + box styling — must survive extractConfig.
        val cluster = listOf(
            "ImageRendering" to j("\"SMOOTH\""),
            "ObjectFit" to j("\"NONE\""),
            "ObjectPosition" to
                j("""{"x":{"type":"keyword","value":"CENTER"},"y":{"type":"keyword","value":"CENTER"}}"""),
            "ObjectViewBox" to j("""{"type":"none"}"""),
            "Width" to j("""{"type":"length","px":200.0}"""),
            "Height" to j("""{"type":"length","px":64.0}"""),
            "BackgroundColor" to j("""{"srgb":{"r":0.18,"g":0.8,"b":0.44},"original":"#2ecc71"}""")
        )
        val config = StyleApplier.extractConfig(cluster)
        assertTrue(config.colors.hasColor)
        assertTrue(config.layout.hasLayout)
    }
}

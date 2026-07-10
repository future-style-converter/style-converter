package com.styleconverter.runtime.transforms

// Fidelity wave 3 — pinning tests for the transforms-cluster fixes:
//
//  1. Transform3DExtractor.extractPerspectiveOrigin must NEVER throw on the
//     object-shaped axes PerspectiveOriginPropertyParser.kt emits
//     ({"type":"bottom"} keywords, {"type":"percentage",…}, {"type":
//     "length",…}). The old `.jsonPrimitive` call threw on every object
//     axis, aborting StyleApplier.extractConfig for the whole element —
//     Transforms_C01 rendered as a bare unstyled label (0.614 / 36.3% px).
//
//  2. css-values-4 <position>: keywords bind to their own axis, so the
//     converter's positional "bottom right" (x=bottom, y=right) must land
//     as x=100% (right) / y=100% (bottom), matching the browser.
//
//  3. TransformExtractor must read ScalePropertyParser's discriminated
//     union — `scale: 150%` arrives as {"type":"uniform","value":1.5},
//     which the old x/y-only reader extracted as identity (Transforms_C02:
//     rotation rendered UNSCALED, 0.657 / 22.7% px).
//
//  4. The whole Transforms_C01 cluster must survive a full
//     StyleApplier.extractConfig round (the collapse regression guard).

import com.styleconverter.runtime.StyleApplier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransformsFidelityWave3Test {

    private fun j(s: String): JsonElement = Json.parseToJsonElement(s)

    // ── perspective-origin: object axes must not throw ──────────────────

    @Test
    fun `perspective-origin keyword objects extract without throwing and swap axes`() {
        // Exact IR of Transforms_C01: `perspective-origin: bottom right`
        // stored positionally by the converter as x=bottom / y=right.
        val config = Transform3DExtractor.extractTransform3DConfig(
            listOf("PerspectiveOrigin" to j("""{"x":{"type":"bottom"},"y":{"type":"right"}}"""))
        )
        // Browser resolution: horizontal=right (100%), vertical=bottom (100%).
        assertEquals(100f, config.perspectiveOriginX, 0.01f)
        assertEquals(100f, config.perspectiveOriginY, 0.01f)
    }

    @Test
    fun `perspective-origin percentage objects extract their values`() {
        val config = Transform3DExtractor.extractTransform3DConfig(
            listOf(
                "PerspectiveOrigin" to
                    j("""{"x":{"type":"percentage","value":25.0},"y":{"type":"percentage","value":75.0}}""")
            )
        )
        assertEquals(25f, config.perspectiveOriginX, 0.01f)
        assertEquals(75f, config.perspectiveOriginY, 0.01f)
    }

    @Test
    fun `perspective-origin length objects fall back to the 50 percent initial value`() {
        // Lengths can't resolve to a fraction without the box size — the
        // contract is "never crash, keep the initial value".
        val config = Transform3DExtractor.extractTransform3DConfig(
            listOf(
                "PerspectiveOrigin" to
                    j("""{"x":{"type":"length","px":10.0},"y":{"type":"length","px":20.0}}""")
            )
        )
        assertEquals(50f, config.perspectiveOriginX, 0.01f)
        assertEquals(50f, config.perspectiveOriginY, 0.01f)
    }

    @Test
    fun `left top keyword pair keeps its natural axes`() {
        val config = Transform3DExtractor.extractTransform3DConfig(
            listOf("PerspectiveOrigin" to j("""{"x":{"type":"left"},"y":{"type":"top"}}"""))
        )
        assertEquals(0f, config.perspectiveOriginX, 0.01f)
        assertEquals(0f, config.perspectiveOriginY, 0.01f)
    }

    // ── the Transforms_C01 collapse guard ────────────────────────────────

    @Test
    fun `full Transforms_C01 cluster extracts without aborting the style bundle`() {
        // Exact property list the converter emits for the fixture. Before
        // the fix this threw inside extractConfig and ComponentRenderer's
        // catch dropped width/height/background for the entire element.
        val cluster = listOf(
            "BackfaceVisibility" to j("\"VISIBLE\""),
            "Perspective" to j("""{"type":"length","px":1000.0}"""),
            "PerspectiveOrigin" to j("""{"x":{"type":"bottom"},"y":{"type":"right"}}"""),
            "Width" to j("""{"type":"length","px":200.0}"""),
            "Height" to j("""{"type":"length","px":80.0}"""),
            "BackgroundColor" to j("""{"srgb":{"r":0.2,"g":0.59,"b":0.85},"original":"#3498db"}""")
        )
        val config = StyleApplier.extractConfig(cluster)
        // The bundle must survive AND keep its box styling.
        assertTrue(config.colors.hasColor)
        assertTrue(config.layout.hasLayout)
    }

    // ── standalone scale / rotate composition ────────────────────────────

    @Test
    fun `scale uniform union shape extracts as uniform scale`() {
        // `scale: 150%` → {"type":"uniform","value":1.5} per ScalePropertyParser.
        val config = TransformExtractor.extractTransformConfig(
            listOf("Scale" to j("""{"type":"uniform","value":1.5}"""))
        )
        assertEquals(1.5f, config.scale!!, 0.001f)
    }

    @Test
    fun `scale 2d union shape keeps per-axis factors`() {
        val config = TransformExtractor.extractTransformConfig(
            listOf("Scale" to j("""{"type":"2d","x":1.2,"y":0.8}"""))
        )
        assertEquals(1.2f, config.scaleX!!, 0.001f)
        assertEquals(0.8f, config.scaleY!!, 0.001f)
    }

    @Test
    fun `scale none stays identity`() {
        val config = TransformExtractor.extractTransformConfig(
            listOf("Scale" to j("""{"type":"none"}"""))
        )
        assertNull(config.scale)
        assertNull(config.scaleX)
        assertNull(config.scaleY)
    }

    @Test
    fun `rotate none extracts as null and axis-angle routes to its axis`() {
        // `rotate: none` — identity, no rotation recorded.
        val none = TransformExtractor.extractTransformConfig(
            listOf("Rotate" to j("""{"type":"none"}"""))
        )
        assertNull(none.rotate)
        // `rotate: x 45deg` — css-transforms-2 §5 axis form → rotateX.
        val axis = TransformExtractor.extractTransformConfig(
            listOf("Rotate" to j("""{"type":"axis-angle","x":1.0,"y":0.0,"z":0.0,"angle":{"deg":45.0}}"""))
        )
        assertEquals(45f, axis.rotateX!!, 0.001f)
        assertNull(axis.rotate)
        // `rotate: z 45deg` — planar rotation.
        val zAxis = TransformExtractor.extractTransformConfig(
            listOf("Rotate" to j("""{"type":"axis-angle","x":0.0,"y":0.0,"z":1.0,"angle":{"deg":45.0}}"""))
        )
        assertEquals(45f, zAxis.rotate!!, 0.001f)
    }

    @Test
    fun `Transforms_C02 cluster keeps scale alongside the transform list`() {
        // scale:150% + transform:rotate(15deg): both channels must survive
        // extraction so the applier composes them (css-transforms-2 §5 puts
        // the individual properties BEFORE the transform list).
        val config = TransformExtractor.extractTransformConfig(
            listOf(
                "Rotate" to j("""{"type":"none"}"""),
                "Scale" to j("""{"type":"uniform","value":1.5}"""),
                "Transform" to j("""{"type":"functions","list":[{"fn":"rotate","a":{"deg":15.0}}]}""")
            )
        )
        assertEquals(1.5f, config.scale!!, 0.001f)
        assertTrue(config.functions.isNotEmpty())
        // rotate:none must NOT zero out the transform list's rotation.
        assertNull(config.rotate)
    }
}

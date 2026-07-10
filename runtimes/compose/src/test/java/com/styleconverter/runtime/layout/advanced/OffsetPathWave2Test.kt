package com.styleconverter.runtime.layout.advanced

// Wave-2 regression pins for the CSS Motion Path extractor.
//
// Root cause being pinned: extractOffsetAnchor called `.jsonPrimitive` on
// the object-shaped x/y components of the IR <position> value
// ({"type":"position","x":{"type":"center"},"y":{"type":"center"}}) and
// THREW. StyleApplier.extractConfig runs every extractor, so the throw
// nuked the entire style chain of any component carrying
// offset-anchor/offset-position: Layout_C13_MasonryAutoFlow lost its
// background + box on Android (0.717 vs web) and Layout_C14_OffsetPath
// lost its box too. Also pins the ray() angle key ("deg", per
// IRAngleSerializer) which was read as "angle" → always 0°.

import com.styleconverter.runtime.StyleApplier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OffsetPathWave2Test {

    private fun j(s: String): JsonElement = Json.parseToJsonElement(s)

    @Test
    fun `object-shaped offset-anchor parses without throwing`() {
        val cfg = OffsetPathExtractor.extractOffsetPathConfig(
            listOf(
                "OffsetAnchor" to j("""{"type":"position","x":{"type":"center"},"y":{"type":"center"}}"""),
                "OffsetDistance" to j("""{"type":"percentage","value":50.0}""")
            )
        )
        val anchor = cfg.offsetAnchor as OffsetAnchorValue.Position
        assertEquals(50f, anchor.x)   // center → 50%
        assertEquals(50f, anchor.y)
        // No offset-path → the motion path stays None (css-motion-1:
        // offset-distance has no effect without a path).
        assertEquals(OffsetPathValue.None, cfg.offsetPath)
    }

    @Test
    fun `keyword corners map to css-values-4 percentages`() {
        val cfg = OffsetPathExtractor.extractOffsetPathConfig(
            listOf(
                "OffsetPosition" to j("""{"type":"position","x":{"type":"left"},"y":{"type":"top"}}""")
            )
        )
        val pos = cfg.offsetPosition as OffsetAnchorValue.Position
        assertEquals(0f, pos.x)   // left → 0%
        assertEquals(0f, pos.y)   // top → 0%
    }

    @Test
    fun `ray angle reads the deg wire key`() {
        val cfg = OffsetPathExtractor.extractOffsetPathConfig(
            listOf("OffsetPath" to j("""{"type":"ray","deg":45.0}"""))
        )
        val ray = cfg.offsetPath as OffsetPathValue.Ray
        assertEquals(45f, ray.angle)
    }

    @Test
    fun `Layout_C13 property set no longer kills the whole style chain`() {
        // Full extractConfig over the exact IR of the failing fixture —
        // before the fix this threw and ComponentRenderer fell back to a
        // bare Modifier (no background, no size).
        val config = StyleApplier.extractConfig(
            listOf(
                "MasonryAutoFlow" to j("""{"type":"pack-ordered"}"""),
                "OffsetAnchor" to j("""{"type":"position","x":{"type":"center"},"y":{"type":"center"}}"""),
                "OffsetDistance" to j("""{"type":"percentage","value":50.0}"""),
                "Width" to j("""{"type":"length","px":140.0}"""),
                "Height" to j("""{"type":"length","px":96.0}"""),
                "BackgroundColor" to j("""{"srgb":{"r":0.6,"g":0.35,"b":0.71},"original":"#9b59b6"}""")
            )
        )
        // The background must survive extraction (it vanished pre-fix).
        assertTrue(config.colors.hasColor)
        // And the offset config still reports its properties for tracking.
        assertTrue(config.offsetPath.hasOffsetPathProperties)
    }
}

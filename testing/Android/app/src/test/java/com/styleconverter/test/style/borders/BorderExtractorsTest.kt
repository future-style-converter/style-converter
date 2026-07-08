package com.styleconverter.test.style.borders

// Unit tests for the Phase 5 border category extractors. Fixtures are
// pulled straight from examples/properties/borders/*.json so the IR shapes
// we test are the same ones the Android runtime renderer will see in
// production.

import androidx.compose.ui.unit.dp
import com.styleconverter.test.style.PropertyRegistry
import com.styleconverter.test.style.borders.outline.OutlineExtractor
import com.styleconverter.test.style.borders.outline.OutlineStyle
import com.styleconverter.test.style.borders.radius.BorderRadiusExtractor
import com.styleconverter.test.style.borders.sides.BorderSideExtractor
import com.styleconverter.test.style.core.types.ValueExtractors.LineStyle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BorderExtractorsTest {

    // Helper: parse a JSON string into a JsonElement.
    private fun parse(s: String): JsonElement = Json.parseToJsonElement(s)
    private fun pair(t: String, j: String) = t to parse(j)

    // ---- PropertyRegistry --------------------------------------------------

    // Touch each extractor so its init {} block runs (objects are lazy-loaded
    // in Kotlin). Without this reference the registry would be empty in the
    // test JVM and every test below would fail.
    private fun primeExtractors() {
        BorderSideExtractor.hashCode()
        BorderRadiusExtractor.hashCode()
        OutlineExtractor.hashCode()
    }

    @Test
    fun `all border-side properties register with borders-sides owner`() {
        primeExtractors()
        // Spot-check a physical and a logical property — both should be
        // claimed by borders/sides after the extractor's init block fires.
        assertEquals("borders/sides", PropertyRegistry.ownerOf("BorderTopWidth"))
        assertEquals("borders/sides", PropertyRegistry.ownerOf("BorderInlineStartColor"))
    }

    @Test
    fun `border-radius logical and physical names both register`() {
        primeExtractors()
        assertEquals("borders/radius", PropertyRegistry.ownerOf("BorderTopLeftRadius"))
        assertEquals("borders/radius", PropertyRegistry.ownerOf("BorderStartStartRadius"))
    }

    @Test
    fun `outline properties register with borders-outline owner`() {
        primeExtractors()
        assertEquals("borders/outline", PropertyRegistry.ownerOf("OutlineWidth"))
        assertEquals("borders/outline", PropertyRegistry.ownerOf("OutlineOffset"))
    }

    // ---- BorderSideExtractor ----------------------------------------------

    @Test
    fun `BorderTopWidth pixel value parses to Dp`() {
        val cfg = BorderSideExtractor.extractBorderConfig(listOf(
            pair("BorderTopWidth", """{"px":8.0}""")
        ))
        assertEquals(8.dp, cfg.top.width)
    }

    @Test
    fun `BorderTopStyle dotted parses to LineStyle-DOTTED`() {
        val cfg = BorderSideExtractor.extractBorderConfig(listOf(
            pair("BorderTopStyle", """{"keyword":"DOTTED"}""")
        ))
        assertEquals(LineStyle.DOTTED, cfg.top.style)
    }

    @Test
    fun `logical BorderBlockStartWidth maps to top side`() {
        // border-block-start maps to top in horizontal-tb writing mode, which
        // is what the runtime assumes — see the mapping in BorderSideExtractor.
        val cfg = BorderSideExtractor.extractBorderConfig(listOf(
            pair("BorderBlockStartWidth", """{"px":4.0}""")
        ))
        assertEquals(4.dp, cfg.top.width)
    }

    @Test
    fun `shorthand BorderWidth applies to all sides without specific values`() {
        val cfg = BorderSideExtractor.extractBorderConfig(listOf(
            pair("BorderWidth", """{"px":2.0}""")
        ))
        // All four sides should pick up the shorthand value.
        assertEquals(2.dp, cfg.top.width)
        assertEquals(2.dp, cfg.end.width)
        assertEquals(2.dp, cfg.bottom.width)
        assertEquals(2.dp, cfg.start.width)
    }

    // ---- BorderRadiusExtractor --------------------------------------------

    @Test
    fun `simple px radius parses as circular corner`() {
        val cfg = BorderRadiusExtractor.extractRadiusConfig(listOf(
            pair("BorderTopLeftRadius", """{"px":16.0}""")
        ))
        // Simple {px} → circular (x == y == 16dp).
        assertEquals(16.dp to 16.dp, cfg.topStart)
        assertTrue(cfg.isCircular)
    }

    @Test
    fun `elliptical pair 40px 20px parses as x-y pair`() {
        // This is the shape the CSS "40px 20px" shorthand produces in IR —
        // the very thing Phase 5 is supposed to fix.
        val cfg = BorderRadiusExtractor.extractRadiusConfig(listOf(
            pair("BorderTopLeftRadius",
                """{"horizontal":{"px":40.0},"vertical":{"px":20.0}}""")
        ))
        assertEquals(40.dp to 20.dp, cfg.topStart)
        // Must be non-circular so the applier uses the elliptical Shape path.
        assertTrue(!cfg.isCircular)
    }

    @Test
    fun `zero radius produces no-hasRadius`() {
        val cfg = BorderRadiusExtractor.extractRadiusConfig(listOf(
            pair("BorderTopLeftRadius", """{"px":0.0}""")
        ))
        assertTrue(!cfg.hasRadius)
    }

    // ---- OutlineExtractor --------------------------------------------------

    @Test
    fun `OutlineWidth px parses`() {
        val cfg = OutlineExtractor.extractOutlineConfig(listOf(
            pair("OutlineWidth", """{"px":3.0}""")
        ))
        assertEquals(3.dp, cfg.width)
    }

    @Test
    fun `OutlineStyle dashed keyword parses`() {
        val cfg = OutlineExtractor.extractOutlineConfig(listOf(
            pair("OutlineStyle", """{"keyword":"DASHED"}""")
        ))
        assertEquals(OutlineStyle.DASHED, cfg.style)
    }

    @Test
    fun `OutlineOffset px parses`() {
        val cfg = OutlineExtractor.extractOutlineConfig(listOf(
            pair("OutlineOffset", """{"px":4.0}""")
        ))
        assertEquals(4.dp, cfg.offset)
    }

    @Test
    fun `hasOutline false when only offset set`() {
        // CSS: outline without a width is invisible — hasOutline must stay
        // false so the applier short-circuits.
        val cfg = OutlineExtractor.extractOutlineConfig(listOf(
            pair("OutlineOffset", """{"px":4.0}""")
        ))
        assertTrue(!cfg.hasOutline)
    }
}

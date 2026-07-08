package com.styleconverter.test.style.core.types

// Unit tests for extractColor — one per documented variant plus each shape
// quirk (alpha key names, dynamic srgb-less shapes).

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorValueTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)

    @Test fun `hex with full rgb parses to Srgb with default alpha`() {
        val v = extractColor(parse("""{"srgb":{"r":1.0,"g":0.2,"b":0.4},"original":"#ff3366"}"""))
        assertEquals(ColorValue.Srgb(1.0, 0.2, 0.4, 1.0), v)
    }

    @Test fun `hex8 carries alpha under key a`() {
        // Quirk 6: sRGB uses "a", not "alpha".
        val v = extractColor(parse("""{"srgb":{"r":1.0,"g":0.0,"b":0.6666666666666666,"a":0.5333333333333333},"original":"#f0a8"}"""))
        assertTrue(v is ColorValue.Srgb)
        assertEquals(0.5333333333333333, (v as ColorValue.Srgb).a, 1e-9)
    }

    @Test fun `named color red`() {
        val v = extractColor(parse("""{"srgb":{"r":1.0,"g":0.0,"b":0.0},"original":"red"}"""))
        assertEquals(ColorValue.Srgb(1.0, 0.0, 0.0, 1.0), v)
    }

    @Test fun `transparent parses with explicit alpha 0`() {
        val v = extractColor(parse("""{"srgb":{"r":0.0,"g":0.0,"b":0.0,"a":0.0},"original":"transparent"}"""))
        assertEquals(0.0, (v as ColorValue.Srgb).a, 1e-9)
    }

    @Test fun `rebeccapurple named color`() {
        val v = extractColor(parse("""{"srgb":{"r":0.4,"g":0.2,"b":0.6},"original":"rebeccapurple"}"""))
        assertTrue(v is ColorValue.Srgb)
    }

    @Test fun `rgb legacy comma form resolves to Srgb`() {
        // Legacy rgb(255,0,0) → IR attaches srgb payload + keeps original=obj.
        val v = extractColor(parse("""{"srgb":{"r":1.0,"g":0.0,"b":0.0},"original":{"r":255,"g":0,"b":0}}"""))
        assertEquals(ColorValue.Srgb(1.0, 0.0, 0.0, 1.0), v)
    }

    @Test fun `hsl with alpha a`() {
        val v = extractColor(parse("""{"srgb":{"r":0.5,"g":0.5,"b":0.5,"a":0.8},"original":{"h":0,"s":0,"l":50,"a":0.8}}"""))
        assertTrue(v is ColorValue.Srgb)
    }

    @Test fun `hwb keeps srgb + typed original`() {
        // colors-modern: original carries type="hwb" but srgb is present.
        val v = extractColor(parse("""{"srgb":{"r":0.2,"g":0.8,"b":0.2},"original":{"type":"hwb","h":120.0,"w":20.0,"b":20.0}}"""))
        assertTrue(v is ColorValue.Srgb)
    }

    @Test fun `modern alpha key is 'alpha' not 'a' inside hwb original`() {
        // Quirk 6: modern spaces use "alpha" in original, but IR still exposes srgb.a.
        val v = extractColor(parse("""{"srgb":{"r":0.1,"g":0.6,"b":0.9,"a":0.6},"original":{"type":"hwb","h":200,"w":10,"b":10,"alpha":0.6}}"""))
        assertTrue(v is ColorValue.Srgb)
        assertEquals(0.6, (v as ColorValue.Srgb).a, 1e-9)
    }

    @Test fun `lab lch oklab oklch color all resolve to Srgb when srgb present`() {
        for (t in listOf("lab", "lch", "oklab", "oklch", "color")) {
            val j = """{"srgb":{"r":0.5,"g":0.5,"b":0.5},"original":{"type":"$t"}}"""
            assertTrue("type=$t", extractColor(parse(j)) is ColorValue.Srgb)
        }
    }

    @Test fun `currentColor has no srgb`() {
        // Quirk 5: dynamic variants omit srgb.
        val v = extractColor(parse("""{"original":"currentColor"}"""))
        assertTrue(v is ColorValue.Dynamic)
        assertEquals(ColorValue.DynamicKind.CURRENT_COLOR, (v as ColorValue.Dynamic).kind)
    }

    @Test fun `color-mix is Dynamic COLOR_MIX`() {
        val v = extractColor(parse("""{"original":{"type":"color-mix","in":"srgb"}}"""))
        assertEquals(ColorValue.DynamicKind.COLOR_MIX, (v as ColorValue.Dynamic).kind)
    }

    @Test fun `light-dark relative var all classify correctly`() {
        assertEquals(
            ColorValue.DynamicKind.LIGHT_DARK,
            (extractColor(parse("""{"original":{"type":"light-dark"}}""")) as ColorValue.Dynamic).kind,
        )
        assertEquals(
            ColorValue.DynamicKind.RELATIVE,
            (extractColor(parse("""{"original":{"type":"relative"}}""")) as ColorValue.Dynamic).kind,
        )
        assertEquals(
            ColorValue.DynamicKind.VAR,
            (extractColor(parse("""{"original":{"type":"var"}}""")) as ColorValue.Dynamic).kind,
        )
    }

    @Test fun `null and malformed inputs return Unknown`() {
        assertEquals(ColorValue.Unknown, extractColor(null))
        assertEquals(ColorValue.Unknown, extractColor(parse("""{}""")))
        assertEquals(ColorValue.Unknown, extractColor(parse("""{"srgb":{}}""")))
    }

    @Test fun `toComposeColor clamps via Color constructor`() {
        val v = ColorValue.Srgb(1.0, 0.0, 0.0, 1.0).toComposeColor()
        assertNotNull(v)
    }
}

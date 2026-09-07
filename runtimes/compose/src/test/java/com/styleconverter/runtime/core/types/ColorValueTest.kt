package com.styleconverter.runtime.core.types

// Unit tests for extractColor — one per documented variant plus each shape
// quirk (alpha key names, dynamic srgb-less shapes).

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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

    @Test fun `toComposeColor maps an in-range sRGB value to the exact Compose colour`() {
        // Retro R10 (A8#1): the old `assertNotNull` passed for ANY colour (the
        // audit's mutation M4 returned Color.Black). Pin the value: the wire's
        // 0..1 floats are the arguments of Compose's `Color(r, g, b, a)`
        // (androidx.compose.ui.graphics.Color.kt, sRGB overload), so pure red
        // with full alpha must be exactly Color.Red, and a translucent
        // channel set must round-trip its components through the packed
        // ARGB representation (8 bits per channel, so 1e-2 covers the
        // quantisation).
        assertEquals(Color.Red, ColorValue.Srgb(1.0, 0.0, 0.0, 1.0).toComposeColor())
        val v = ColorValue.Srgb(0.2, 0.4, 0.6, 0.5).toComposeColor()
        assertEquals(0.2f, v.red, 1e-2f)
        assertEquals(0.4f, v.green, 1e-2f)
        assertEquals(0.6f, v.blue, 1e-2f)
        assertEquals(0.5f, v.alpha, 1e-2f)
    }

    @Test fun `toComposeColor clamps via Color constructor - out-of-range channels saturate`() {
        // The original test carried this name but fed an IN-range colour and
        // asserted only non-null-ness, so it never exercised clamping at all.
        // Measured on the pinned Compose BOM (2026.06.01, ui-graphics): the
        // sRGB `Color(red, green, blue, alpha)` factory packs each channel
        // into 8 bits after saturating it to 0..1 — 1.5 → red 1.0, -0.5 →
        // red 0.0, alpha 2 → 1.0 — instead of throwing (probe run 2026-09-04
        // on this test classpath). The wire never carries such a value (the
        // converter clamps out-of-gamut sRGB before emitting: `oklch(90% 0.4
        // 120)` → b: 0.0, `rgb(300 0 0)` → r: 1.0), so this pins the
        // runtime's own safety net with a REAL out-of-range input.
        val over = ColorValue.Srgb(1.5, 0.0, 0.0, 1.0).toComposeColor()
        assertEquals(Color.Red, over)
        val under = ColorValue.Srgb(-0.5, 0.0, 0.0, 1.0).toComposeColor()
        assertEquals(0f, under.red, 0f)
        assertEquals(1f, under.alpha, 0f)
        val alphaOver = ColorValue.Srgb(0.0, 0.0, 0.0, 2.0).toComposeColor()
        assertEquals(1f, alphaOver.alpha, 0f)
    }
}

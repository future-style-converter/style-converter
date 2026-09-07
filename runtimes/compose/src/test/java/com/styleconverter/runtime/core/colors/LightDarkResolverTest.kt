package com.styleconverter.runtime.core.colors

// Wave-7 pinning tests for light-dark() color resolution (css-color-5 §7;
// spec 06 §4 note): the wire's {original:{type:"light-dark", lightColor,
// darkColor}} dynamic shape resolves to the scheme-chosen arm as a standard
// static {srgb, original} node — closing the Android drop path where the
// srgb-only extractors returned null for the dynamic shape.

import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LightDarkResolverTest {

    // The exact wire shape from schema/conformance/fixtures/v2/
    // dynamic-styling.json (LightDarkValue component).
    private val lightDarkBg = IRProperty(
        "BackgroundColor",
        Json.parseToJsonElement(
            """{"original":{"type":"light-dark","lightColor":"#ecf0f1","darkColor":"#111827"}}"""
        )
    )

    // Static color property — must pass through untouched.
    private val staticColor = IRProperty(
        "Color",
        Json.parseToJsonElement("""{"srgb":{"r":0.1,"g":0.2,"b":0.3},"original":"#1a3348"}""")
    )

    // Pull an srgb channel out of a resolved property's data for asserts.
    private fun channel(p: IRProperty, ch: String): Double? {
        val srgb = (p.data as? JsonObject)?.get("srgb") as? JsonObject
        return (srgb?.get(ch) as? JsonPrimitive)?.doubleOrNull
    }

    // Pull the original string out of a resolved property's data.
    private fun original(p: IRProperty): String? =
        ((p.data as? JsonObject)?.get("original") as? JsonPrimitive)?.contentOrNull

    @Test
    fun `light scheme resolves the light arm to static srgb`() {
        val out = LightDarkResolver.resolve(listOf(lightDarkBg, staticColor), isDarkScheme = false)
        val bg = out.first { it.type == "BackgroundColor" }
        // #ecf0f1 → r=236/255, g=240/255, b=241/255 (float-precision arm).
        assertEquals(236.0 / 255.0, channel(bg, "r")!!, 1e-6)
        assertEquals(240.0 / 255.0, channel(bg, "g")!!, 1e-6)
        assertEquals(241.0 / 255.0, channel(bg, "b")!!, 1e-6)
        // The chosen arm string becomes the original (static wire shape).
        assertEquals("#ecf0f1", original(bg))
    }

    @Test
    fun `dark scheme resolves the dark arm - one surface one scheme answer`() {
        val out = LightDarkResolver.resolve(listOf(lightDarkBg), isDarkScheme = true)
        val bg = out.first()
        // #111827 → r=17/255, g=24/255, b=39/255.
        assertEquals(17.0 / 255.0, channel(bg, "r")!!, 1e-6)
        assertEquals(24.0 / 255.0, channel(bg, "g")!!, 1e-6)
        assertEquals(39.0 / 255.0, channel(bg, "b")!!, 1e-6)
        assertEquals("#111827", original(bg))
    }

    @Test
    fun `a list without light-dark returns the same instance - identity pin`() {
        // The static corpus pays one read-only probe, never a rebuild.
        val props = listOf(staticColor)
        assertSame(props, LightDarkResolver.resolve(props, isDarkScheme = false))
        assertSame(props, LightDarkResolver.resolve(props, isDarkScheme = true))
    }

    @Test
    fun `nested light-dark inside composite data resolves too`() {
        // Bucket/border-shorthand style carrier: the color node sits inside
        // a bigger object — the walk must find and rewrite it in place.
        val border = IRProperty(
            "BorderTopColor",
            Json.parseToJsonElement(
                """{"width":{"px":3.0},"color":{"original":{"type":"light-dark","lightColor":"#ffffff","darkColor":"#000000"}}}"""
            )
        )
        val out = LightDarkResolver.resolve(listOf(border), isDarkScheme = true)
        val color = ((out.first().data as JsonObject)["color"] as JsonObject)
        val srgb = color["srgb"] as JsonObject
        // Dark arm #000000 chosen.
        assertEquals(0.0, (srgb["r"] as JsonPrimitive).doubleOrNull!!, 1e-9)
        assertEquals("#000000", (color["original"] as JsonPrimitive).contentOrNull)
        // Sibling keys survive the rewrite untouched.
        assertTrue((out.first().data as JsonObject).containsKey("width"))
    }

    @Test
    fun `named and rgb arms parse through the shared color grammar`() {
        val p = IRProperty(
            "Color",
            Json.parseToJsonElement(
                """{"original":{"type":"light-dark","lightColor":"rebeccapurple","darkColor":"rgb(255, 165, 0)"}}"""
            )
        )
        // Light arm: the named color (147-name table in CssVariableResolver).
        val light = LightDarkResolver.resolve(listOf(p), isDarkScheme = false).first()
        assertEquals(102.0 / 255.0, channel(light, "r")!!, 1e-6)  // rebeccapurple #663399
        // Dark arm: rgb() functional syntax.
        val dark = LightDarkResolver.resolve(listOf(p), isDarkScheme = true).first()
        assertEquals(165.0 / 255.0, channel(dark, "g")!!, 1e-6)   // orange g channel
    }

    @Test
    fun `an unresolvable arm preserves the dynamic envelope - no guess`() {
        // A var()-carrying arm can't be parsed statically: the node must
        // stay EXACTLY as it was (no drop, no fabricated color).
        val p = IRProperty(
            "Color",
            Json.parseToJsonElement(
                """{"original":{"type":"light-dark","lightColor":"var(--brand)","darkColor":"#000000"}}"""
            )
        )
        val outLight = LightDarkResolver.resolve(listOf(p), isDarkScheme = false).first()
        // Light run needs the unresolvable light arm → envelope untouched.
        assertEquals(p.data, outLight.data)
        assertNull(channel(outLight, "r"))
        // Dark run needs only the parseable dark arm → resolves fine.
        val outDark = LightDarkResolver.resolve(listOf(p), isDarkScheme = true).first()
        assertEquals("#000000", original(outDark))
    }

    @Test
    fun `translucent arms keep their alpha channel`() {
        val p = IRProperty(
            "BackgroundColor",
            Json.parseToJsonElement(
                """{"original":{"type":"light-dark","lightColor":"#ffffff80","darkColor":"#000000"}}"""
            )
        )
        val out = LightDarkResolver.resolve(listOf(p), isDarkScheme = false).first()
        // 8-digit hex: alpha 0x80 = 128/255 survives into the srgb payload.
        assertEquals(128.0 / 255.0, channel(out, "a")!!, 1e-2)
        // Opaque arms omit "a" (the converter's omit-opaque convention).
        val dark = LightDarkResolver.resolve(listOf(p), isDarkScheme = true).first()
        assertNull(channel(dark, "a"))
    }
}

package com.styleconverter.runtime.core.renderer

// COMPOSE-TEXT lane pins for the two ComponentRenderer-side fixes:
//
//   1. placeholderDisplayText — text-transform must apply to REAL `_text`
//      (rawText). The old branch set displayText = rawText behind a FALSE
//      comment claiming the extractor pre-transformed it, so
//      `text-transform: uppercase` on a real-text node rendered verbatim
//      lowercase on Android while the web reference uppercased it
//      (css-text-3 §2.1: the transform is a render-time operation).
//
//   2. isCurrentColorValue — `color: currentColor` ships srgb-less
//      ({"original":"currentColor"}), which extractTextColor nulled out,
//      dropping the value to the placeholder's bg-contrast pick. The
//      renderer now resolves it through the wave-9 inheritance channel
//      (css-color-4 §7.2: currentColor on `color` itself == inherit);
//      this suite pins the wire-shape detector that gates that path.

import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceholderTextAndCurrentColorTest {

    private fun j(s: String): JsonElement = Json.parseToJsonElement(s)
    private fun prop(type: String, json: String) = IRProperty(type, j(json))

    // ---- placeholderDisplayText: transform applies to rawText -----------

    @Test
    fun `text-transform uppercase applies to real rawText`() {
        // THE bug: rawText used to bypass the transform entirely.
        val out = ComponentRenderer.placeholderDisplayText(
            name = "ignored",
            rawText = "hello world",
            properties = listOf(prop("TextTransform", "\"UPPERCASE\""))
        )
        assertEquals("HELLO WORLD", out)
    }

    @Test
    fun `text-transform lowercase applies to real rawText`() {
        val out = ComponentRenderer.placeholderDisplayText(
            name = "ignored",
            rawText = "HELLO World",
            properties = listOf(prop("TextTransform", "\"LOWERCASE\""))
        )
        assertEquals("hello world", out)
    }

    @Test
    fun `text-transform capitalize applies to real rawText`() {
        val out = ComponentRenderer.placeholderDisplayText(
            name = "ignored",
            rawText = "hello world",
            properties = listOf(prop("TextTransform", "\"CAPITALIZE\""))
        )
        assertEquals("Hello World", out)
    }

    @Test
    fun `full-width stays a deliberate no-op on rawText`() {
        // Chromium — the reference — renders full-width unchanged on the
        // Latin corpus; substituting U+FFxx forms would diverge from the
        // reference captures (documented in extractTextTransform).
        val out = ComponentRenderer.placeholderDisplayText(
            name = "ignored",
            rawText = "hello 123",
            properties = listOf(prop("TextTransform", "\"FULL_WIDTH\""))
        )
        assertEquals("hello 123", out)
    }

    @Test
    fun `rawText without a transform renders verbatim, underscores included`() {
        // Underscores in REAL text are content — only the synthesized
        // component-name path strips them as fixture-name separators.
        val out = ComponentRenderer.placeholderDisplayText(
            name = "ignored",
            rawText = "keep_my_underscores",
            properties = emptyList()
        )
        assertEquals("keep_my_underscores", out)
    }

    @Test
    fun `synthesized name path still strips underscores and transforms`() {
        // The legacy placeholder behaviour is unchanged: name → spaces →
        // transform (preserves the 327-pair baseline for label fixtures).
        val out = ComponentRenderer.placeholderDisplayText(
            name = "Card_Complete",
            rawText = null,
            properties = listOf(prop("TextTransform", "\"UPPERCASE\""))
        )
        assertEquals("CARD COMPLETE", out)
    }

    @Test
    fun `empty rawText falls back to the synthesized name`() {
        // Empty string == no real text (matches the hasRawText gate).
        val out = ComponentRenderer.placeholderDisplayText(
            name = "Tag_Chip",
            rawText = "",
            properties = emptyList()
        )
        assertEquals("Tag Chip", out)
    }

    // ---- isCurrentColorValue: wire-shape detector ------------------------

    @Test
    fun `srgb-less currentColor wire is detected`() {
        // Exact ColorParser output for `color: currentColor` (IRColor with
        // CurrentColor representation, srgb null).
        assertTrue(ComponentRenderer.isCurrentColorValue(j("""{"original":"currentColor"}""")))
    }

    @Test
    fun `detection is case-insensitive`() {
        // CSS keywords are ASCII case-insensitive (css-color-4 §7.2's
        // canonical spelling is camelCase but any casing is valid input).
        assertTrue(ComponentRenderer.isCurrentColorValue(j("""{"original":"currentcolor"}""")))
    }

    @Test
    fun `a resolved color with srgb is never treated as currentColor`() {
        // A present srgb means the converter already resolved the value —
        // re-resolving would overwrite a real color with the inherited one.
        assertFalse(ComponentRenderer.isCurrentColorValue(
            j("""{"srgb":{"r":1.0,"g":0.0,"b":0.0,"a":1.0},"original":"red"}""")
        ))
    }

    @Test
    fun `ordinary named colors are not currentColor`() {
        assertFalse(ComponentRenderer.isCurrentColorValue(j("""{"original":"red"}""")))
    }

    @Test
    fun `non-object wires are not currentColor`() {
        // The Color property data is always the IRColor envelope object;
        // a bare primitive is some other property's shape — never match.
        assertFalse(ComponentRenderer.isCurrentColorValue(j("\"currentColor\"")))
        assertFalse(ComponentRenderer.isCurrentColorValue(null))
    }

    // ---- currentColor bottom-out: the harness-stage default --------------
    //
    // Wave-5 device evidence (typography/color, Color_CurrentColor):
    // `color: currentColor` with NO ancestor Color in the IR resolves to
    // the web-harness BODY's `color: #eee` — 238,238,238 OPAQUE — on the
    // reference render, while the old null-into-contrast-pick path
    // composited the 70%-alpha placeholder pick to ~171 gray over the
    // dark fixture bg (pair 0.856). The bottom-out must be the runtime's
    // default text color, not the label contrast pick.

    @Test
    fun `no inherited color bottoms out into the opaque default text color`() {
        val out = ComponentRenderer.resolveCurrentColorBottomOut(null)
        // Exactly the harness stage contract: opaque #eee.
        assertEquals(ComponentRenderer.DEFAULT_TEXT_COLOR, out)
        // Pin the constant itself: rgb(238,238,238) at FULL alpha — the
        // 70%-alpha contrast pick (0xB3EEEEEE) would fail all four checks
        // against the reference's opaque channels.
        assertEquals(1f, out.alpha, 0f)
        assertEquals(238f / 255f, out.red, 1e-4f)
        assertEquals(238f / 255f, out.green, 1e-4f)
        assertEquals(238f / 255f, out.blue, 1e-4f)
    }

    @Test
    fun `an inherited color always wins over the default`() {
        // css-color-4 §7.2: currentColor on `color` == inherit — a real
        // ancestor value must pass through untouched.
        val inherited = androidx.compose.ui.graphics.Color(0.2f, 0.4f, 0.6f, 1f)
        assertEquals(
            inherited,
            ComponentRenderer.resolveCurrentColorBottomOut(inherited)
        )
    }
}

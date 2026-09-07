package com.styleconverter.runtime.typography

import androidx.compose.ui.unit.TextUnit
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.variables.DynamicValueResolver
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * retro R5 (audit A7#5) — the Compose half of the FontFamily-only font-size
 * pin. iOS's TypographyApplier bottomed out at UIKit's 17pt body size when a
 * component carried `font-family` but no `font-size`; Compose has always
 * bottomed out at the CSS `medium` 16px (css-fonts-4 §2.5 initial value,
 * resolved by the UA default — Blink kDefaultFontSize) — the extractor leaves
 * the size Unspecified and ComponentRenderer's placeholder text substitutes
 * 16.sp (DynamicValueResolver.DEFAULT_FONT_SIZE_PX). Both natives are now
 * pinned to the same 16 (iOS: TypographyFontSizeFallbackTests).
 *
 * Payload: the VERBATIM wave49-final per-test IR of css-text-decor/
 * text-decoration-inset-007, component 1 (FontFamily + Color, no FontSize).
 */
class FontFamilyOnlyFontSizeFallbackTest {

    private fun prop(t: String, s: String) = IRProperty(t, Json.parseToJsonElement(s))

    @Test
    fun `a FontFamily-only component leaves the size to the 16px UA default`() {
        val style = TextStyleApplier.extractTextStyle(listOf(
            prop("FontFamily", """["times new roman", "serif"]"""),
            prop("Color", """{"srgb": {"r": 0, "g": 0, "b": 0}, "original": "black"}""")))
        // Nothing declared → Unspecified, so the renderer's bottom-out applies…
        assertEquals(TextUnit.Unspecified, style.fontSize)
        // …and that bottom-out is CSS medium — the same 16 iOS now falls back to.
        assertEquals(16f, DynamicValueResolver.DEFAULT_FONT_SIZE_PX)
    }
}

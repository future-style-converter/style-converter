package com.styleconverter.runtime.typography

// Wave-2 typography pins. Every IR literal below is verbatim converter
// output for the failing fidelity fixtures (typography.combos.json).

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypographyWave2Test {

    private fun j(s: String): JsonElement = Json.parseToJsonElement(s)
    private fun prop(type: String, json: String) = IRProperty(type, j(json))

    // ---- font-weight keywords (Typography_C08, 0.908) ------------------

    @Test
    fun `font-weight bolder resolves to 700 from the inherited normal`() {
        // css-fonts-4 §2.2 relative-weight table: inherited 400 → bolder 700.
        val style = TextStyleApplier.extractTextStyle(
            listOf(prop("FontWeight", "\"bolder\""))
        )
        assertEquals(FontWeight.Bold, style.fontWeight)
    }

    @Test
    fun `font-weight lighter resolves to 100 from the inherited normal`() {
        val style = TextStyleApplier.extractTextStyle(
            listOf(prop("FontWeight", "\"lighter\""))
        )
        assertEquals(FontWeight.Thin, style.fontWeight)
    }

    // ---- letter-spacing rem (Typography_C10, 0.847) --------------------

    @Test
    fun `letter-spacing rem original wins over the bogus px zero`() {
        // Converter emits px:0.0 for 0.25rem — the original must resolve
        // (0.25 × 16px root = 4px) or the tracking silently vanishes.
        val style = TextStyleApplier.extractTextStyle(
            listOf(
                prop(
                    "LetterSpacing",
                    """{"px":0.0,"original":{"type":"length","original":{"v":0.25,"u":"REM"}}}"""
                )
            )
        )
        assertEquals(4f.sp, style.letterSpacing)
    }

    @Test
    fun `letter-spacing true zero stays zero`() {
        val style = TextStyleApplier.extractTextStyle(
            listOf(prop("LetterSpacing", """{"px":0.0}"""))
        )
        assertEquals(0f.sp, style.letterSpacing)
    }

    // ---- text-wrap nowrap (Typography_C20 0.766 / C21 0.894) -----------

    @Test
    fun `text-wrap nowrap turns soft wrapping off`() {
        val cfg = TextStyleApplier.extractTextWrapConfig(
            listOf(prop("TextWrap", "\"NOWRAP\""), prop("TextWrapMode", "\"NOWRAP\""))
        )
        assertFalse(cfg.softWrap)
    }

    @Test
    fun `white-space nowrap still turns soft wrapping off`() {
        val cfg = TextStyleApplier.extractTextWrapConfig(
            listOf(prop("WhiteSpace", "\"NOWRAP\""))
        )
        assertFalse(cfg.softWrap)
    }

    @Test
    fun `text-wrap balance alone keeps wrapping on`() {
        val cfg = TextStyleApplier.extractTextWrapConfig(
            listOf(prop("TextWrapStyle", "\"BALANCE\""))
        )
        assertTrue(cfg.softWrap)
    }

    // ---- text-shadow (Typography_C18, 0.919) ---------------------------

    @Test
    fun `text-shadow first layer reaches the TextStyle`() {
        val style = TextStyleApplier.extractTextStyle(
            listOf(
                prop(
                    "TextShadow",
                    """[{"x":{"px":2.0},"y":{"px":2.0},"blur":{"px":4.0},"c":{"srgb":{"r":0.906,"g":0.298,"b":0.235},"original":"#e74c3c"}},
                        {"x":{"px":-2.0},"y":{"px":-2.0},"blur":{"px":4.0},"c":{"srgb":{"r":0.204,"g":0.596,"b":0.859},"original":"#3498db"}}]"""
                )
            )
        )
        val shadow = style.shadow
        assertNotNull(shadow)
        assertEquals(2f, shadow!!.offset.x)
        assertEquals(2f, shadow.offset.y)
        assertEquals(4f, shadow.blurRadius)
    }

    // ---- text-emphasis IR shapes (Typography_C16 0.842 / C17 0.898) ----

    @Test
    fun `sealed-interface circle style parses as filled circle`() {
        val cfg = TypographyExtractor.extractTextEmphasisConfig(
            listOf("TextEmphasisStyle" to j("""{"type":"circle"}"""))
        )
        // css-text-decor-3 §3.1: bare shape keyword defaults to FILLED.
        assertEquals(TextEmphasisStyle.FILLED_CIRCLE, cfg.style)
        assertTrue(cfg.hasEmphasis)
    }

    @Test
    fun `two-axis under-left position parses`() {
        val cfg = TypographyExtractor.extractTextEmphasisConfig(
            listOf(
                "TextEmphasis" to j("""{"style":{"type":"circle"},"color":{"srgb":{"r":0.9,"g":0.3,"b":0.24},"original":"#e74c3c"}}"""),
                "TextEmphasisPosition" to j("""{"vertical":"UNDER","horizontal":"LEFT"}""")
            )
        )
        assertEquals(TextEmphasisPosition.UNDER_LEFT, cfg.position)
        assertEquals(TextEmphasisStyle.FILLED_CIRCLE, cfg.style)
        assertNotNull(cfg.color)
    }

    // ---- font-variant-numeric array (Typography_C07, 0.844) ------------

    @Test
    fun `ordinal array reaches the feature string`() {
        val cfg = TypographyExtractor.extractFontVariantConfig(
            listOf("FontVariantNumeric" to j("""["ORDINAL"]"""))
        )
        assertTrue(cfg.numeric.ordinal)
        val features = FontVariantApplier.buildFontFeatureSettings(cfg)
        assertTrue("expected ordn in '$features'", features.contains("\"ordn\" 1"))
    }

    // ---- synthesized small-caps (Typography_C06, 0.915) ----------------

    @Test
    fun `small-caps synthesis uppercases lowercase runs at reduced size`() {
        val annotated = com.styleconverter.runtime.core.renderer.ComponentRenderer
            .synthesizeSmallCaps("Typo C06", 16f)
        // Visible text is fully uppercased…
        assertEquals("TYPO C06", annotated.text)
        // …and exactly the lowercase run ("ypo") carries the reduced size.
        val spans = annotated.spanStyles
        assertEquals(1, spans.size)
        assertEquals(1, spans[0].start)   // after the real capital 'T'
        assertEquals(4, spans[0].end)     // "ypo"
        assertEquals((16f * 0.8f).sp, spans[0].item.fontSize)
    }
}

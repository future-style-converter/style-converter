package com.styleconverter.runtime.content

// Wave-43 lane V7 — pin table for PseudoStyledDeclarations, the raw-string
// → typed-IRProperty conversion for a pseudo bucket's own styling.
//
// The value strings below are VERBATIM from the wave42-final per-test IR
// (tools/titan/runs/wave42-final/sections/*/per-test-ir/): `color: red`
// (css-display/display-contents-dynamic-before-after-001, ×7 buckets),
// `font-size: 3em` (css-contain/contain-content-011), and the census's
// whole font-family population `inherit` (×27, all ::marker).

import com.styleconverter.runtime.content.PseudoStyledDeclarations.Conversion
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PseudoStyledDeclarationsTest {

    /** Unwrap a Typed conversion or fail loudly. */
    private fun typed(c: Conversion?): com.styleconverter.runtime.core.ir.IRProperty {
        assertTrue("expected Typed, got $c", c is Conversion.Typed)
        return (c as Conversion.Typed).property
    }

    // ── color (the ×7 census population, verbatim) ──────────────────────

    @Test
    fun `color red converts to the srgb wire shape`() {
        // display-contents-dynamic-before-after-001 ::before `color: red`.
        val p = typed(PseudoStyledDeclarations.convert("color", "red"))
        assertEquals("Color", p.type)
        // The exact IRColor block ValueExtractors.extractColor reads.
        val srgb = (p.data as JsonObject)["srgb"]!!.jsonObject
        assertEquals(1.0, srgb["r"]!!.jsonPrimitive.doubleOrNull!!, 1e-9)
        assertEquals(0.0, srgb["g"]!!.jsonPrimitive.doubleOrNull!!, 1e-9)
        assertEquals(0.0, srgb["b"]!!.jsonPrimitive.doubleOrNull!!, 1e-9)
        assertEquals(1.0, srgb["a"]!!.jsonPrimitive.doubleOrNull!!, 1e-9)
    }

    @Test
    fun `hex color converts through the shared literal parser`() {
        // One color vocabulary: the same parser color-mix endpoints use.
        val p = typed(PseudoStyledDeclarations.convert("color", "#008000"))
        val srgb = (p.data as JsonObject)["srgb"]!!.jsonObject
        // 1e-6 delta: Compose Color stores Float channels, so the emitted
        // Double carries Float precision (0.5019608, not 128.0/255.0 exact).
        assertEquals(128.0 / 255.0, srgb["g"]!!.jsonPrimitive.doubleOrNull!!, 1e-6)
    }

    @Test
    fun `unresolvable color is Unsupported not guessed`() {
        // var() needs a variable scope this seam does not have.
        assertEquals(Conversion.Unsupported,
            PseudoStyledDeclarations.convert("color", "var(--ink)"))
    }

    @Test
    fun `css-wide color keywords are inert`() {
        // css-cascade-5 §7.3: inherit collapses to the run's existing ink.
        assertEquals(Conversion.Inert,
            PseudoStyledDeclarations.convert("color", "inherit"))
    }

    // ── font-size (the ×1 census population, verbatim) ──────────────────

    @Test
    fun `font-size 3em stays symbolic for the inherited base`() {
        // contain-content-011 ::after `font-size: 3em` — em on font-size
        // resolves against the INHERITED size (css-values-4 §6.1.1), a
        // base only the render site knows, so the shape stays symbolic:
        // the live {"original":{"type":"length","original":{v,u}}} wire.
        val p = typed(PseudoStyledDeclarations.convert("font-size", "3em"))
        assertEquals("FontSize", p.type)
        val original = (p.data as JsonObject)["original"]!!.jsonObject
        assertEquals("length", original["type"]!!.jsonPrimitive.contentOrNull)
        val inner = original["original"]!!.jsonObject
        assertEquals(3.0, inner["v"]!!.jsonPrimitive.doubleOrNull!!, 1e-9)
        assertEquals("EM", inner["u"]!!.jsonPrimitive.contentOrNull)
    }

    @Test
    fun `absolute font-size units normalize to px`() {
        // spec 02-values.md: absolute lengths canonicalize to 96dpi px.
        val px = typed(PseudoStyledDeclarations.convert("font-size", "48px"))
        assertEquals(48.0, ((px.data as JsonObject)["px"])!!.jsonPrimitive.doubleOrNull!!, 1e-9)
        // 12pt = 16px at 4/3 px per pt.
        val pt = typed(PseudoStyledDeclarations.convert("font-size", "12pt"))
        assertEquals(16.0, ((pt.data as JsonObject)["px"])!!.jsonPrimitive.doubleOrNull!!, 1e-9)
    }

    @Test
    fun `percentage font-size stays symbolic like em`() {
        // css-fonts-4 §2.5: % resolves against the inherited size too.
        val p = typed(PseudoStyledDeclarations.convert("font-size", "120%"))
        val original = (p.data as JsonObject)["original"]!!.jsonObject
        assertEquals("percentage", original["type"]!!.jsonPrimitive.contentOrNull)
        assertEquals(120.0, original["value"]!!.jsonPrimitive.doubleOrNull!!, 1e-9)
    }

    @Test
    fun `negative and keyword font-sizes are Unsupported`() {
        // css-fonts-4 §2.5 forbids negatives; keyword sizes are unwired
        // (census: zero) — named, never guessed.
        assertEquals(Conversion.Unsupported,
            PseudoStyledDeclarations.convert("font-size", "-4px"))
        assertEquals(Conversion.Unsupported,
            PseudoStyledDeclarations.convert("font-size", "medium"))
    }

    // ── font-family (the ×27 census population is `inherit`) ────────────

    @Test
    fun `font-family inherit is inert`() {
        // The census's ENTIRE font-family population — the run's default
        // face already is the inherited/document face at this seam.
        assertEquals(Conversion.Inert,
            PseudoStyledDeclarations.convert("font-family", "inherit"))
    }

    @Test
    fun `font-family list converts to the canonical name array`() {
        // css-fonts-4 §5.2 prioritised list → the JsonArray wire shape
        // CssFontFamilyResolver walks; quotes stay for its per-name strip.
        val p = typed(PseudoStyledDeclarations.convert("font-family", "\"Helvetica Neue\", Arial, sans-serif"))
        assertEquals("FontFamily", p.type)
        val names = (p.data as JsonArray).map { it.jsonPrimitive.contentOrNull }
        assertEquals(listOf("\"Helvetica Neue\"", "Arial", "sans-serif"), names)
    }

    // ── ownership boundary ──────────────────────────────────────────────

    @Test
    fun `non-styling declarations are outside this file`() {
        // content/display/counter-* keep their existing walk handling.
        assertNull(PseudoStyledDeclarations.convert("display", "contents"))
        assertNull(PseudoStyledDeclarations.convert("border", "1px solid red"))
    }
}

package com.styleconverter.runtime.typography

// Wave 6 (lane FONT-STYLE-OBLIQUE) regression test: `font-style:
// oblique <angle>` arrives as {"oblique":{"deg":N,…}} on the wire —
// an OBJECT, not a keyword string — and both Compose extract paths
// (TextStyleApplier.extractTextStyle and TypographyExtractor's
// TypographyConfig) fed it through extractKeyword, got null, and
// dropped the style entirely: FontStyle_Oblique_14deg rendered fully
// upright on Android while Chromium slanted it (typography SSIM 0.8215,
// worst pair Android-web).
//
// Every wire shape below is byte-copied from a live converter run
// (./gradlew :converter:run on fixtures/properties/typography/
// font-style.json, wave-6): bare keywords are plain JSON strings;
// angles are pre-normalized to degrees; non-deg source units carry an
// extra "original" {v,u} object the runtimes must tolerate and ignore.
//
// Expected mapping is pinned to the wave-6 WEB captures (the reference
// render): every harness bundles roman-only Inter, so Chromium
// SYNTHESIZES the slant with one fixed skew (Skia textSkewX -0.25 ≈
// 14deg) once the requested angle reaches css-fonts-4 §2.5's 14deg
// default oblique angle — capture 041 (14deg) and 044 (18deg) are
// pixel-identical to 038 (italic), while 040 (0deg), 042 (-10deg) and
// 043 (11.46deg) are pixel-identical to 037 (normal). Compose's fake
// italic applies the same -0.25 TextPaint skew, so FontStyle.Italic at
// deg ≥ 14 / FontStyle.Normal below reproduces web exactly.

import androidx.compose.ui.text.font.FontStyle
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class FontStyleObliqueTest {

    // Build the IRProperty from raw wire JSON so the test can only pass
    // against the exact byte shape the converter emits (wave-5 lesson:
    // never hand-assume shapes).
    private fun wire(json: String): JsonElement = Json.parseToJsonElement(json)

    // Route through the public TextStyleApplier entry point — the path
    // ComponentRenderer uses for the rendered Text.
    private fun styleOf(data: JsonElement): FontStyle? =
        TextStyleApplier.extractTextStyle(
            listOf(IRProperty(type = "FontStyle", data = data))
        ).fontStyle

    // And through TypographyExtractor's config (the StyleApplier path —
    // it takes (type, data) pairs rather than IRProperty instances).
    private fun configStyleOf(data: JsonElement): FontStyle? =
        TypographyExtractor.extractTypographyConfig(
            listOf("FontStyle" to data)
        ).fontStyle

    @Test
    fun `oblique 14deg object maps to Italic on both extract paths`() {
        // Live wire for `font-style: oblique 14deg` (the failing variant).
        val data = wire("""{"oblique": {"deg": 14.0}}""")
        assertEquals(FontStyle.Italic, styleOf(data))
        assertEquals(FontStyle.Italic, configStyleOf(data))
    }

    @Test
    fun `oblique angles below the 14deg synthesis cutoff stay Normal`() {
        // `oblique 0deg` — spec-equal to normal (css-fonts-4 §2.5).
        assertEquals(FontStyle.Normal, styleOf(wire("""{"oblique": {"deg": 0.0}}""")))
        // `oblique -10deg` — Chromium renders the upright roman
        // (wave-6 capture 042 pixel-identical to normal).
        assertEquals(FontStyle.Normal, styleOf(wire("""{"oblique": {"deg": -10.0}}""")))
    }

    @Test
    fun `converted-unit payloads keep the original v-u object and still map`() {
        // `oblique 0.2rad` → 11.459…deg, below the cutoff → Normal
        // (wave-6 web capture 043 pixel-identical to normal).
        val rad = wire(
            """{"oblique": {"deg": 11.459155902616466, "original": {"v": 0.2, "u": "RAD"}}}"""
        )
        assertEquals(FontStyle.Normal, styleOf(rad))
        assertEquals(FontStyle.Normal, configStyleOf(rad))
        // `oblique 0.05turn` → 18deg, above the cutoff → Italic
        // (wave-6 web capture 044 pixel-identical to italic).
        val turn = wire(
            """{"oblique": {"deg": 18.0, "original": {"v": 0.05, "u": "TURN"}}}"""
        )
        assertEquals(FontStyle.Italic, styleOf(turn))
        assertEquals(FontStyle.Italic, configStyleOf(turn))
    }

    @Test
    fun `bare keyword strings keep the legacy mapping`() {
        // Keywords ride the wire as plain JSON strings (live converter).
        assertEquals(FontStyle.Normal, styleOf(JsonPrimitive("normal")))
        assertEquals(FontStyle.Italic, styleOf(JsonPrimitive("italic")))
        // Bare `oblique` = `oblique 14deg` default → italic appearance
        // (wave-6 web capture 039 pixel-identical to italic 038).
        assertEquals(FontStyle.Italic, styleOf(JsonPrimitive("oblique")))
    }

    @Test
    fun `malformed oblique payload drops instead of guessing`() {
        // An oblique object without a numeric deg is not a shape the
        // converter emits — extraction must return null (tracker-visible
        // drop), never a guessed slant.
        assertEquals(null, styleOf(wire("""{"oblique": {}}""")))
    }
}

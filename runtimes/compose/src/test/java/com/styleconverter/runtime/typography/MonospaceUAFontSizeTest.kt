package com.styleconverter.runtime.typography

import androidx.compose.ui.unit.TextUnitType
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 36, lane M8 — pins the UA fixed-default font-size quirk
 * (MonospaceUAFontSize.kt) and both of its consumers.
 *
 * ## What is being protected
 * `css/css-overflow/line-clamp/block-ellipsis-*` (20 depth-48 cells) declare a
 * bare `font-family: monospace` with NO `font-size`. Chromium resolves the
 * initial `medium` keyword against `defaultFixedFontSize` (13px) for those,
 * not against `defaultFontSize` (16px) — so the frozen browser-ref captures
 * were rasterised at 13px while both natives rendered 16px, missing on BOTH
 * the glyph advance (7.78 vs 9.6 px/char) and the line pitch (16.25 vs 19.5).
 *
 * ## The invariant that must never break
 * The gate must stay OFF for every element that either declares a font-size or
 * does not lead its family list with the monospace generic — that is the whole
 * committed 327-pair baseline corpus plus `css-text/hyphens/hyphens-auto-
 * control` (`"Courier New", Courier, monospace`), an Android cell that PASSES
 * at 16px today. Both halves are pinned below.
 *
 * The IR payloads are the LIVE wire shapes from
 * tools/titan/runs/wave35-final/sections/css-overflow/per-test-ir/
 * wpt__css-overflow__line-clamp__block-ellipsis-001.json and
 * .../css-text/per-test-ir/wpt__css-text__hyphens__hyphens-auto-control.json.
 */
class MonospaceUAFontSizeTest {

    private fun json(s: String): JsonElement = Json.parseToJsonElement(s)

    /** block-ellipsis-001's FontFamily payload, verbatim. */
    private val bareMonospace = json("""["monospace"]""")

    /** hyphens-auto-control's FontFamily payload, verbatim. */
    private val courierFirst = json("""["Courier New","Courier","monospace"]""")

    // ── The family predicate ────────────────────────────────────────────────

    @Test
    fun `bare monospace generic arms the gate`() {
        assertTrue(MonospaceUAFontSize.appliesToFamily(bareMonospace))
    }

    @Test
    fun `ui-monospace generic arms the gate`() {
        assertTrue(MonospaceUAFontSize.appliesToFamily(json("""["ui-monospace"]""")))
    }

    @Test
    fun `monospace behind a concrete face does NOT arm the gate`() {
        // Blink takes the generic from the FIRST family only; a concrete face
        // first keeps kStandardFamily and therefore the 16px default.
        assertFalse(MonospaceUAFontSize.appliesToFamily(courierFirst))
    }

    @Test
    fun `a non-monospace generic does not arm the gate`() {
        assertFalse(MonospaceUAFontSize.appliesToFamily(json("""["serif"]""")))
        assertFalse(MonospaceUAFontSize.appliesToFamily(json("""["sans-serif","monospace"]""")))
    }

    @Test
    fun `absent or malformed family payloads never arm the gate`() {
        assertFalse(MonospaceUAFontSize.appliesToFamily(null))
        assertFalse(MonospaceUAFontSize.appliesToFamily(json("""[]""")))
        assertFalse(MonospaceUAFontSize.appliesToFamily(json("""42""")))
    }

    @Test
    fun `quoted and mixed-case monospace still arms the gate`() {
        // Hand-authored IR / conformance goldens may keep the quotes the
        // converter strips; normalisation must match the family resolver's.
        assertTrue(MonospaceUAFontSize.appliesToFamily(json("""["\"Monospace\""]""")))
    }

    // ── The property-list resolution ────────────────────────────────────────

    @Test
    fun `no font-size plus bare monospace resolves to 13`() {
        val props = listOf(IRProperty("FontFamily", bareMonospace))
        assertEquals(13f, MonospaceUAFontSize.resolveSp(props)!!, 0f)
    }

    @Test
    fun `an explicit font-size disarms the gate`() {
        // hyphens-manual-010 shape: monospace AND `font-size: 32px`.
        val props = listOf(
            IRProperty("FontFamily", bareMonospace),
            IRProperty("FontSize", json("""{"px":32}"""))
        )
        assertNull(MonospaceUAFontSize.resolveSp(props))
    }

    @Test
    fun `an unparseable font-size still disarms the gate`() {
        // A declared size the extractor cannot read is still a SPECIFIED
        // value, so the `medium` keyword this quirk resolves never applies.
        val props = listOf(
            IRProperty("FontFamily", bareMonospace),
            IRProperty("FontSize", json("""{"nope":true}"""))
        )
        assertNull(MonospaceUAFontSize.resolveSp(props))
    }

    @Test
    fun `the LAST FontFamily declaration decides`() {
        val props = listOf(
            IRProperty("FontFamily", bareMonospace),
            IRProperty("FontFamily", json("""["serif"]"""))
        )
        assertNull(MonospaceUAFontSize.resolveSp(props))
    }

    @Test
    fun `no FontFamily at all leaves the gate closed`() {
        assertNull(MonospaceUAFontSize.resolveSp(listOf(IRProperty("Color", json("""{"srgb":{"r":0,"g":0,"b":0}}""")))))
    }

    // ── Consumer 1: the rendered TextStyle ──────────────────────────────────

    @Test
    fun `extractTextStyle stamps 13sp for a bare monospace element`() {
        val style = TextStyleApplier.extractTextStyle(listOf(IRProperty("FontFamily", bareMonospace)))
        assertEquals(TextUnitType.Sp, style.fontSize.type)
        assertEquals(13f, style.fontSize.value, 0f)
    }

    @Test
    fun `extractTextStyle leaves fontSize Unspecified everywhere else`() {
        // The renderer's own 16.sp bottom-out must keep owning this case —
        // this is the branch the whole committed baseline corpus rides.
        val style = TextStyleApplier.extractTextStyle(listOf(IRProperty("FontFamily", json("""["serif"]"""))))
        assertTrue(style.fontSize == androidx.compose.ui.unit.TextUnit.Unspecified)
    }

    @Test
    fun `a unitless line-height multiplies the 13px base`() {
        // `line-height: 1.5` under a bare monospace family is 19.5px in a
        // browser, not 24 — the quirk moves the multiplication base too.
        val style = TextStyleApplier.extractTextStyle(
            listOf(
                IRProperty("FontFamily", bareMonospace),
                IRProperty("LineHeight", json("""{"multiplier":1.5}"""))
            )
        )
        assertEquals(19.5f, style.lineHeight.value, 0.001f)
    }

    // ── Consumer 2: the font-relative SIZING base ───────────────────────────

    @Test
    fun `the typography config carries 13sp so ch and em resolve against it`() {
        // StyleApplier.buildSpacingContext reads config.typography.fontSize as
        // the base for ch/em sizing — block-ellipsis-001's `width: 63.1ch`.
        val cfg = TypographyExtractor.extractTypographyConfig(
            listOf("FontFamily" to bareMonospace)
        )
        assertEquals(13f, cfg.fontSize!!.value, 0f)
    }

    @Test
    fun `the typography config keeps a declared font-size untouched`() {
        val cfg = TypographyExtractor.extractTypographyConfig(
            listOf("FontFamily" to bareMonospace, "FontSize" to json("""{"px":32}"""))
        )
        assertEquals(32f, cfg.fontSize!!.value, 0f)
    }

    @Test
    fun `the typography config stays null for a non-monospace element`() {
        val cfg = TypographyExtractor.extractTypographyConfig(
            listOf("FontFamily" to json("""["serif"]"""))
        )
        assertNull(cfg.fontSize)
    }
}

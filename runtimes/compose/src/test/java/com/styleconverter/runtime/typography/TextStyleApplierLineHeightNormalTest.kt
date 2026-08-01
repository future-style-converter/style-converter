package com.styleconverter.runtime.typography

// Wave 22, lane FONT — pins the EXTRACTOR half of the DECLARED-`normal`
// line-height contract: TextStyleApplier deliberately keeps consuming the
// wire's legacy 1.2 compatibility multiplier for `line-height: normal`.
//
// Why it must NOT be corrected here: `normal` is a lookup into the rendered
// face's metrics (CSS2 §10.8.1) and no fixed ratio stands in for it — Arial's
// own normal box is 1.1499em, so `font: 92px Arial` wants ≈105.8px, not the
// 110.4px a 1.2 multiplier yields. But the committed 327-pair dark-stage
// captures of `line-height: normal`
// (fixtures/properties/typography/line-height.json `LineHeight_Normal` →
// tools/visual/baseline/Android__063_Typography_LineHeight.png) were all made
// with the 1.2 box, and this lane may not re-capture them. The correction is
// therefore applied WPT-GATED one level up, in ComponentRenderer's placeholder
// line-box pick (LineHeightNormal.lineBoxSource) — so this file exists to stop
// a future reader "fixing" the extractor and silently moving those baselines.
//
// The complementary halves are pinned by LineHeightNormalTest (the gate table,
// including the outside-WPT byte-identity row) and ComposedLineBoxTest (the
// absent-line-height calibration).

import androidx.compose.ui.unit.TextUnit
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextStyleApplierLineHeightNormalTest {

    private fun prop(type: String, raw: String) =
        IRProperty(type = type, data = Json.parseToJsonElement(raw))

    // The live dotted-001 font-size (per-test-ir/…text-decoration-dotted-001.json).
    private val fontSize92 = prop("FontSize", """{"px":92,"original":{"type":"length","px":92}}""")

    /** The exact payload the fixed FontExpander emits for `font: 92px Arial`. */
    private val normalRaw = """{"multiplier":1.2,"original":"normal"}"""

    @Test
    fun `declared normal keeps the legacy 1_2 box at this layer`() {
        val style = TextStyleApplier.extractTextStyle(
            listOf(fontSize92, prop("LineHeight", normalRaw))
        )
        // 1.2 × 92 = 110.4sp — the historical value the dark-stage baselines
        // were captured with. Changing THIS number is a baseline-moving edit.
        assertEquals(110.4f, style.lineHeight.value, 0.001f)
        // …and the renderer can still tell it came from the keyword, which is
        // what lets the WPT-gated override fire without touching this path.
        assertTrue(LineHeightNormal.isDeclaredNormal(
            listOf(prop("LineHeight", normalRaw))))
    }

    @Test
    fun `declared unitless number still resolves against the font size`() {
        // A REAL authored `line-height: 2` must keep multiplying (2 × 92 =
        // 184sp) — the `normal` discriminator must never widen to catch it.
        val style = TextStyleApplier.extractTextStyle(
            listOf(fontSize92,
                   prop("LineHeight", """{"multiplier":2.0,"original":{"type":"number","value":2.0}}"""))
        )
        assertEquals(184f, style.lineHeight.value, 0.001f)
    }

    @Test
    fun `declared absolute length still wins verbatim`() {
        val style = TextStyleApplier.extractTextStyle(
            listOf(fontSize92, prop("LineHeight", """{"pixels":40.0}"""))
        )
        assertEquals(40f, style.lineHeight.value, 0.001f)
    }

    @Test
    fun `absent line-height stays Unspecified so the calibration still runs`() {
        // The branch the whole WPT corpus rides: nothing declared →
        // Unspecified → ComponentRenderer's composedDefaultLineHeightPx.
        val style = TextStyleApplier.extractTextStyle(listOf(fontSize92))
        assertEquals(TextUnit.Unspecified, style.lineHeight)
    }
}

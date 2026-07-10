package com.styleconverter.runtime.borders

// Fidelity wave 1 — pinning tests for the border-family spec fixes:
//
//  1. currentColor resolution (css-backgrounds-3 §4.1: border-*-color's
//     initial value is `currentcolor` = the element's computed `color`,
//     falling back to the harness body #eee — NOT black).
//  2. `medium` default width (§4.3: a visible border-style with no
//     declared width paints 3px, not zero).
//  3. Percentage border-radius carried as paint-time fractions
//     (§4.4: horizontal % → box width, vertical % → box height;
//     `border-radius: 50%` is a full ellipse even with no declared size).
//  4. border-image-width `<number>` resolves as a MULTIPLE of the computed
//     border width (§6.3) — zero for border-less elements, so the border
//     image paints nothing (Borders_C06: web renders nothing, Android drew
//     a 16dp gradient frame from the old hard-coded 8dp basis).

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.borders.image.BorderImageApplier
import com.styleconverter.runtime.borders.image.BorderImageDimension
import com.styleconverter.runtime.borders.image.BorderImageExtractor
import com.styleconverter.runtime.borders.image.BorderImageSliceEdge
import com.styleconverter.runtime.borders.radius.BorderRadiusExtractor
import com.styleconverter.runtime.borders.sides.BorderSideExtractor
import com.styleconverter.runtime.core.types.ValueExtractors.LineStyle
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BorderFidelityWave1Test {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(t: String, j: String) = t to parse(j)

    // ── 1. currentColor ─────────────────────────────────────────────────

    @Test
    fun `unset border color resolves to the harness body color, not black`() {
        // Borders_C04 shape: dotted bottom border, width declared, NO color.
        val cfg = BorderSideExtractor.extractBorderConfig(listOf(
            pair("BorderBottomStyle", "\"DOTTED\""),
            pair("BorderBottomWidth", """{"px":6.0}""")
        ))
        // Web resolves currentcolor → body #eee (apps/web-harness/index.html).
        assertEquals(Color(0xFFEEEEEE), cfg.bottom.color)
    }

    @Test
    fun `unset border color prefers the element's own Color property`() {
        // An explicit `color: #112233` on the element IS currentcolor.
        val cfg = BorderSideExtractor.extractBorderConfig(listOf(
            pair("Color", """{"srgb":{"r":0.06666667,"g":0.13333334,"b":0.2}}"""),
            pair("BorderTopStyle", "\"SOLID\""),
            pair("BorderTopWidth", """{"px":2.0}""")
        ))
        val c = cfg.top.color!!
        assertEquals(0.0667f, c.red, 0.01f)
        assertEquals(0.1333f, c.green, 0.01f)
        assertEquals(0.2f, c.blue, 0.01f)
    }

    @Test
    fun `explicit border color still wins over currentColor fill`() {
        val cfg = BorderSideExtractor.extractBorderConfig(listOf(
            pair("Color", """{"srgb":{"r":1.0,"g":0.0,"b":0.0}}"""),
            pair("BorderTopColor", """{"srgb":{"r":0.0,"g":0.0,"b":1.0}}"""),
            pair("BorderTopStyle", "\"SOLID\""),
            pair("BorderTopWidth", """{"px":2.0}""")
        ))
        assertEquals(0f, cfg.top.color!!.red, 0.001f)
        assertEquals(1f, cfg.top.color!!.blue, 0.001f)
    }

    // ── 2. medium default width ─────────────────────────────────────────

    @Test
    fun `visible style without width defaults to medium 3px`() {
        // Borders_Decorated shape: border-block-end-style dotted, no width.
        val cfg = BorderSideExtractor.extractBorderConfig(listOf(
            pair("BorderBlockEndStyle", "\"DOTTED\""),
            pair("BorderBlockEndColor", """{"srgb":{"r":0.067,"g":0.067,"b":0.067}}""")
        ))
        assertEquals(3.dp, cfg.bottom.width)
        assertTrue("medium-width side must count as a border", cfg.bottom.hasBorder)
    }

    @Test
    fun `style none gets no medium default`() {
        val cfg = BorderSideExtractor.extractBorderConfig(listOf(
            pair("BorderTopStyle", "\"NONE\"")
        ))
        assertNull(cfg.top.width)
        assertFalse(cfg.top.hasBorder)
    }

    // ── 3. percentage border-radius fractions ───────────────────────────

    @Test
    fun `border-radius 50 percent carries paint-time fractions on both axes`() {
        // Borders_Decorated wire shape: {"original":{"v":50.0,"u":"PERCENT"}}
        // with NO Width/Height in the IR — previously the corner silently
        // became square (plain rectangle vs web's full ellipse, 0.7746).
        val cfg = BorderRadiusExtractor.extractRadiusConfig(listOf(
            pair("BorderTopLeftRadius", """{"original":{"v":50.0,"u":"PERCENT"}}""")
        ))
        assertTrue(cfg.hasRadius)
        assertTrue(cfg.hasFraction)
        assertEquals(0.5f, cfg.topStartFraction.first!!, 0.001f)
        assertEquals(0.5f, cfg.topStartFraction.second!!, 0.001f)
    }

    @Test
    fun `mixed 40px 50 percent corner keeps px x-axis and fraction y-axis`() {
        // Borders_C05 wire shape: horizontal px, vertical percent.
        val cfg = BorderRadiusExtractor.extractRadiusConfig(listOf(
            pair("BorderEndStartRadius",
                """{"horizontal":{"px":40.0},"vertical":{"original":{"v":50.0,"u":"PERCENT"}}}""")
        ))
        assertEquals(40.dp, cfg.bottomStart.first)
        assertNull(cfg.bottomStartFraction.first)
        assertEquals(0.5f, cfg.bottomStartFraction.second!!, 0.001f)
    }

    @Test
    fun `plain px radius keeps the fixed-Dp channel and no fractions`() {
        // Byte-compat with the pre-wave extractor for the common case.
        val cfg = BorderRadiusExtractor.extractRadiusConfig(listOf(
            pair("BorderTopLeftRadius", """{"px":16.0}""")
        ))
        assertEquals(16.dp to 16.dp, cfg.topStart)
        assertFalse(cfg.hasFraction)
        assertTrue(cfg.isCircular)
    }

    // ── 4. border-image-width resolution ────────────────────────────────

    @Test
    fun `border image config captures zero computed widths without a border`() {
        // Borders_C06 shape: border-image on an element with NO border-style.
        val cfg = BorderImageExtractor.extractBorderImageConfig(listOf(
            pair("BorderImageSource",
                """{"type":"gradient","gradient":"radial-gradient(circle, red, yellow, blue)"}"""),
            pair("BorderImageWidth",
                """{"top":{"type":"number","value":2.0},"right":{"type":"number","value":2.0},"bottom":{"type":"number","value":2.0},"left":{"type":"number","value":2.0}}""")
        ))
        assertEquals(0.dp, cfg.computedBorderTop)
        assertEquals(0.dp, cfg.computedBorderLeft)
    }

    @Test
    fun `border image config captures real computed widths when bordered`() {
        val cfg = BorderImageExtractor.extractBorderImageConfig(listOf(
            pair("BorderImageSource",
                """{"type":"gradient","gradient":"linear-gradient(red, blue)"}"""),
            pair("BorderTopWidth", """{"px":20.0}"""),
            pair("BorderTopStyle", "\"SOLID\"")
        ))
        assertEquals(20.dp, cfg.computedBorderTop)
    }

    @Test
    fun `number width is a multiple of the computed border width`() {
        with(BorderImageApplier) {
            // §6.3: number × computed width. 2 × 0 = 0 → paints nothing.
            assertEquals(0.dp, BorderImageDimension.Number(2f).resolve(0.dp, null))
            // 2 × 20 = 40 for a real 20px border.
            assertEquals(40.dp, BorderImageDimension.Number(2f).resolve(20.dp, null))
            // Initial value (absent) is the number 1 → 1 × computed width.
            assertEquals(20.dp, (null as BorderImageDimension?).resolve(20.dp, null))
            // Lengths stay literal regardless of the border.
            assertEquals(12.dp, BorderImageDimension.Length(12.dp).resolve(0.dp, null))
            // auto → the slice's absolute px size when available (§6.3).
            assertEquals(
                30.dp,
                BorderImageDimension.Auto.resolve(0.dp, BorderImageSliceEdge(30f, isPercentage = false))
            )
        }
    }
}

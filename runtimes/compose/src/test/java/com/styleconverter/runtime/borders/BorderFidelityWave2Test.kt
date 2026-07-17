package com.styleconverter.runtime.borders

// Fidelity wave 2 (round 2) — pinning tests for the Android-web residue
// fixes verified against the wave-1 closeout captures:
//
//  1. P0 regression: the border-band content inset must chain INSIDE the
//     renderer's 30dp placeholder floor. StyleApplier.applyProperties no
//     longer bakes the band in; ComponentRenderer chains
//     StyleApplier.borderContentInset AFTER defaultMinSize so a min-bound
//     bordered placeholder absorbs the band into the floor exactly like
//     web's border-box `minHeight: 30px` (094_Input_Field went 54→56px
//     tall when the band stacked outside the floor).
//  2. Dotted dot fitting uses round-half-UP: kotlin.math.round is rint
//     (ties-to-even) and dropped a dot on the exact-half case
//     (Borders_C01: 15 dots vs Chromium's 16 — full-strip phase error).
//  3. `outline-width: thick` arrives as {"type":"keyword","value":"THICK"}
//     — previously unparsed → width 0 → the entire outline vanished
//     (Borders_C14 crimson ridge missing, Android-web 0.8025).
//  4. Outline 3D shading: dark shade = base × 0.618 per channel,
//     calibrated against the Borders_C14 web capture
//     (crimson rgb(220,20,60) → rgb(136,12,37)).
//  5. `color-mix(in srgb, …)` resolution: the IR carries original-only
//     metadata; web resolved it natively while Android fell back to grey
//     (Borders_C09 border-left: web purple mix vs Android rgb(119,119,119)).

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.StyleApplier
import com.styleconverter.runtime.borders.outline.OutlineApplier
import com.styleconverter.runtime.borders.outline.OutlineExtractor
import com.styleconverter.runtime.borders.outline.OutlineStyle
import com.styleconverter.runtime.borders.sides.BorderSideApplier
import com.styleconverter.runtime.borders.sides.BorderSideExtractor
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BorderFidelityWave2Test {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(t: String, j: String) = t to parse(j)
    private fun prop(t: String, j: String) = IRProperty(type = t, data = parse(j))

    // ── 1. Border-band content inset (P0 +2px regression) ───────────────

    @Test
    fun `band insets mirror each visible side's computed width`() {
        val sides = BorderSideExtractor.extractBorderConfig(listOf(
            pair("BorderTopStyle", "\"SOLID\""),
            pair("BorderTopWidth", """{"px":6.0}"""),
            pair("BorderLeftStyle", "\"SOLID\""),
            pair("BorderLeftWidth", """{"px":2.0}""")
        ))
        val (start, top, end, bottom) = StyleApplier.borderBandInsets(sides)
        assertEquals(2.dp, start)   // left = start in LTR
        assertEquals(6.dp, top)
        assertEquals(0.dp, end)     // no right border → no inset
        assertEquals(0.dp, bottom)  // no bottom border → no inset
    }

    @Test
    fun `hidden and none styles reserve no band`() {
        // css-backgrounds-3 §4.2: none/hidden compute to 0 used width, so
        // they must not push the content inward.
        val sides = BorderSideExtractor.extractBorderConfig(listOf(
            pair("BorderTopStyle", "\"NONE\""),
            pair("BorderTopWidth", """{"px":6.0}""")
        ))
        val insets = StyleApplier.borderBandInsets(sides)
        assertTrue(insets.all { it == 0.dp })
    }

    @Test
    fun `borderContentInset is the Modifier identity for borderless elements`() {
        // Identity keeps the renderer chain untouched — no stray padding
        // node that could disturb the 30dp floor on borderless placeholders
        // (their committed baselines encode the floor-only geometry).
        val m = StyleApplier.borderContentInset(
            listOf(prop("BackgroundColor", """{"srgb":{"r":1.0,"g":1.0,"b":1.0}}"""))
        )
        assertEquals(Modifier, m)
    }

    @Test
    fun `borderContentInset produces a non-identity modifier for bordered elements`() {
        // 094_Input_Field shape: 1px solid border all round.
        val m = StyleApplier.borderContentInset(listOf(
            prop("BorderTopStyle", "\"SOLID\""), prop("BorderTopWidth", """{"px":1.0}"""),
            prop("BorderRightStyle", "\"SOLID\""), prop("BorderRightWidth", """{"px":1.0}"""),
            prop("BorderBottomStyle", "\"SOLID\""), prop("BorderBottomWidth", """{"px":1.0}"""),
            prop("BorderLeftStyle", "\"SOLID\""), prop("BorderLeftWidth", """{"px":1.0}""")
        ))
        assertTrue(m != Modifier)
    }

    // ── 2. Dotted dot fitting (round-half-up, not rint) ─────────────────

    @Test
    fun `dotted count rounds the exact half case UP like Chromium`() {
        // Borders_C01: 240px side, 8px dots → (240-8)/16 = 14.5 pitches.
        // Chromium paints 16 dots; rint gave 14 pitches → 15 dots.
        assertEquals(16, BorderSideApplier.dottedDotCount(240f, 8f))
    }

    @Test
    fun `dotted count matches Chromium on the C04 shape`() {
        // Borders_C04: 200px side, 6px dots → 17 dots at ~12.1px pitch
        // (measured on the web capture).
        assertEquals(17, BorderSideApplier.dottedDotCount(200f, 6f))
    }

    @Test
    fun `degenerate dotted sides paint a single dot or nothing`() {
        assertEquals(1, BorderSideApplier.dottedDotCount(6f, 6f))
        assertEquals(0, BorderSideApplier.dottedDotCount(0f, 6f))
        assertEquals(0, BorderSideApplier.dottedDotCount(100f, 0f))
    }

    // ── 2b. Dashed intervals (width-conditional on:off rhythm) ──────────

    @Test
    fun `dashed intervals keep the measured 6w-4w tuning at w=2`() {
        // The thin tuning was calibrated against Chromium at w=2 (12px on,
        // 8px off) and the committed thin-dash baselines embed it — the
        // w<3 branch must stay byte-identical to the pre-split behaviour.
        assertEquals(listOf(12f, 8f), BorderSideApplier.dashedIntervals(2f).toList())
    }

    @Test
    fun `dashed intervals switch to Chromium's 2w-1w rhythm at w=5`() {
        // At the dashed fixture's w=5 Chromium paints ~10px on / 5px off
        // (~11 dashes on a 160px edge); 6w:4w made dashes 3x too long.
        assertEquals(listOf(10f, 5f), BorderSideApplier.dashedIntervals(5f).toList())
    }

    @Test
    fun `dashed interval boundary sits at exactly 3px`() {
        // w=3 is the first "thick" width: 2w:1w applies from 3px upward so
        // medium (3px) borders already match Chromium's long-dash rhythm.
        assertEquals(listOf(6f, 3f), BorderSideApplier.dashedIntervals(3f).toList())
    }

    // ── 3. outline-width keyword envelope ────────────────────────────────

    @Test
    fun `typed keyword envelope resolves thin-medium-thick fixed sizes`() {
        // css-backgrounds-3 §4.3 fixed sizes; envelope shape emitted by the
        // parser for `outline-width: thick` (Borders_C14).
        assertEquals(5.dp, ValueExtractors.extractBorderWidth(parse("""{"type":"keyword","value":"THICK"}""")))
        assertEquals(3.dp, ValueExtractors.extractBorderWidth(parse("""{"type":"keyword","value":"MEDIUM"}""")))
        assertEquals(1.dp, ValueExtractors.extractBorderWidth(parse("""{"type":"keyword","value":"thin"}""")))
        assertNull(ValueExtractors.extractBorderWidth(parse("""{"type":"keyword","value":"BOGUS"}""")))
    }

    @Test
    fun `C14 outline extracts as a visible 5px ridge ring at -4px offset`() {
        val cfg = OutlineExtractor.extractOutlineConfig(listOf(
            pair("OutlineColor", """{"srgb":{"r":0.8627451,"g":0.078431375,"b":0.23529412},"original":"crimson"}"""),
            pair("OutlineOffset", """{"px":-4.0}"""),
            pair("OutlineStyle", "\"RIDGE\""),
            pair("OutlineWidth", """{"type":"keyword","value":"THICK"}""")
        ))
        assertTrue(cfg.hasOutline)
        assertEquals(5.dp, cfg.width)
        assertEquals((-4).dp, cfg.offset)
        assertEquals(OutlineStyle.RIDGE, cfg.style)
    }

    @Test
    fun `outline-style alone paints the medium 3px initial width`() {
        // css-ui-4 §4.2: initial outline-width is medium (3px), not 0.
        val cfg = OutlineExtractor.extractOutlineConfig(listOf(
            pair("OutlineStyle", "\"SOLID\"")
        ))
        assertTrue(cfg.hasOutline)
        assertEquals(3.dp, cfg.width)
    }

    @Test
    fun `explicit zero outline-width still disables the ring`() {
        val cfg = OutlineExtractor.extractOutlineConfig(listOf(
            pair("OutlineStyle", "\"SOLID\""),
            pair("OutlineWidth", """{"px":0.0}""")
        ))
        assertFalse(cfg.hasOutline)
    }

    // ── 4. Outline 3D dark shade calibration ────────────────────────────

    @Test
    fun `ridge dark shade matches the Chromium raster factor`() {
        // Web paints crimson rgb(220,20,60) ridge dark bands as
        // rgb(136,12,37) — factor 0.618 per channel (Borders_C14 capture).
        val dark = OutlineApplier.darken(Color(220 / 255f, 20 / 255f, 60 / 255f))
        assertEquals(136f, dark.red * 255f, 1.5f)
        assertEquals(12f, dark.green * 255f, 1.5f)
        assertEquals(37f, dark.blue * 255f, 1.5f)
        assertEquals(1f, dark.alpha, 0.001f)
    }

    // ── 5. color-mix(in srgb) resolution ─────────────────────────────────

    @Test
    fun `srgb color-mix of red and blue resolves to the browser purple`() {
        // Borders_C09: color-mix(in srgb, red 50%, blue) → rgb(128,0,128)
        // (css-color-5 §3: gamma-encoded sRGB component interpolation).
        val c = ValueExtractors.extractColor(parse(
            """{"original":{"type":"color-mix","colorSpace":"srgb","color1":"red","percent1":50.0,"color2":"blue"}}"""
        ))!!
        assertEquals(0.5f, c.red, 0.01f)
        assertEquals(0f, c.green, 0.01f)
        assertEquals(0.5f, c.blue, 0.01f)
    }

    @Test
    fun `color-mix percent defaults follow css-color-5 normalization`() {
        // Only percent2 given → percent1 = 100 - percent2.
        val c = ValueExtractors.extractColor(parse(
            """{"original":{"type":"color-mix","colorSpace":"srgb","color1":"white","color2":"black","percent2":25.0}}"""
        ))!!
        assertEquals(0.75f, c.red, 0.01f)
        assertEquals(0.75f, c.green, 0.01f)
        assertEquals(0.75f, c.blue, 0.01f)
    }

    @Test
    fun `non-srgb color-mix stays unresolved rather than mixing wrongly`() {
        // oklch interpolation is NOT a plain component lerp — bail to null
        // (runtime-dependent) instead of producing a wrong color.
        assertNull(ValueExtractors.extractColor(parse(
            """{"original":{"type":"color-mix","colorSpace":"oklch","color1":"red","percent1":50.0,"color2":"blue"}}"""
        )))
    }

    @Test
    fun `hex literals in color-mix endpoints parse`() {
        val c = ValueExtractors.extractColor(parse(
            """{"original":{"type":"color-mix","colorSpace":"srgb","color1":"#ff0000","percent1":50.0,"color2":"#0000ff"}}"""
        ))!!
        assertEquals(0.5f, c.red, 0.01f)
        assertEquals(0.5f, c.blue, 0.01f)
    }
}

package com.styleconverter.runtime.color

// Wave-21 IMAGES lane — Compose extraction pins for:
//   A-RC8 <length> gradient centers ({"px":N} and typed lh/em wire, with
//         the font context resolved from the component's OWN FontSize /
//         LineHeight properties — pinned against the live wave21-gate
//         conic-gradient-line-height-relative-units-001 artifact:
//         FontSize {"px":50} + LineHeight {"multiplier":2} → 1lh = 100px)
//   A-RC2 cross-fade() config extraction (normalized effective weights,
//         color-layer args, whole-function drop on any bad arg)

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorExtractorGradientCenterTest {

    private fun el(s: String): JsonElement = Json.parseToJsonElement(s)

    // Two-stop red/blue stops fragment matching the converter wire.
    private val stops = """[
        {"color":{"srgb":{"r":1,"g":0,"b":0}},"position":null},
        {"color":{"srgb":{"r":0,"g":0,"b":1}},"position":null}]"""

    @Test
    fun `percent center extracts as fractions (legacy raw-number wire)`() {
        // Live wave21-gate conic-gradient-center wire: pos:{x:25,y:25}.
        val imgs = ColorExtractor.extractBackgroundImages(
            el("""[{"type":"conic-gradient","pos":{"x":25,"y":25},"stops":$stops}]"""))
        val g = imgs[0] as BackgroundImageConfig.ConicGradient
        assertEquals(GradientCoord.Kind.FRACTION, g.centerX.kind)
        assertEquals(0.25f, g.centerX.value, 1e-6f)
        // fraction() is identity for FRACTION coords.
        assertEquals(0.25f, g.centerX.fraction(200f), 1e-6f)
    }

    @Test
    fun `px center extracts as PX and resolves against the axis size`() {
        val imgs = ColorExtractor.extractBackgroundImages(
            el("""[{"type":"radial-gradient","pos":{"x":{"px":100},"y":{"px":50}},"stops":$stops}]"""))
        val g = imgs[0] as BackgroundImageConfig.RadialGradient
        assertEquals(GradientCoord.Kind.PX, g.centerX.kind)
        // `at 100px` on a 200px axis = the 0.5 fraction.
        assertEquals(0.5f, g.centerX.fraction(200f), 1e-6f)
        // Same px on a 400px axis = 0.25 — resolution is size-dependent.
        assertEquals(0.25f, g.centerX.fraction(400f), 1e-6f)
        // Degenerate axis → CSS default center, never division by zero.
        assertEquals(0.5f, g.centerX.fraction(0f), 1e-6f)
    }

    @Test
    fun `lh center resolves through the component font context`() {
        // Pinned against the live artifact: FontSize 50px + LineHeight
        // multiplier 2 → 1lh = 100px (NOT the 1.2 normal ratio).
        val ctx = ColorExtractor.fontContextOf(listOf(
            "FontSize" to el("""{"px":50}"""),
            "LineHeight" to el("""{"multiplier":2}""")
        ))
        assertEquals(100f, ctx.lineHeightPx, 1e-6f)
        val imgs = ColorExtractor.extractBackgroundImages(
            el("""[{"type":"conic-gradient","pos":{"x":{"original":{"v":1,"u":"LH"}},"y":{"px":50}},"stops":$stops}]"""),
            ctx)
        val g = imgs[0] as BackgroundImageConfig.ConicGradient
        // 1lh = 100px, typed unit resolved at extract time.
        assertEquals(GradientCoord.Kind.PX, g.centerX.kind)
        assertEquals(100f, g.centerX.value, 1e-6f)
    }

    @Test
    fun `default font context uses the CSS initials (16px, normal 1_2)`() {
        // No FontSize/LineHeight properties → 16px / 19.2px (pin P5 ratio).
        val ctx = ColorExtractor.fontContextOf(emptyList())
        assertEquals(16f, ctx.fontSizePx, 1e-6f)
        assertEquals(19.2f, ctx.lineHeightPx, 1e-6f)
    }

    @Test
    fun `absent position keeps the CSS default center`() {
        val imgs = ColorExtractor.extractBackgroundImages(
            el("""[{"type":"radial-gradient","stops":$stops}]"""))
        val g = imgs[0] as BackgroundImageConfig.RadialGradient
        assertEquals(GradientCoord.CENTER, g.centerX)
        assertEquals(GradientCoord.CENTER, g.centerY)
    }

    @Test
    fun `cross-fade extracts normalized entries`() {
        // Six 10% gradients (target-alpha wire) → six 0.1 entries.
        val arg = """{"weight":10,"image":{"type":"linear-gradient","stops":$stops}}"""
        val imgs = ColorExtractor.extractBackgroundImages(
            el("""[{"type":"cross-fade","args":[$arg,$arg,$arg,$arg,$arg,$arg]}]"""))
        val cf = imgs[0] as BackgroundImageConfig.CrossFade
        assertEquals(6, cf.entries.size)
        assertTrue(cf.entries.all { kotlin.math.abs(it.weight - 0.1f) < 1e-6f })
        assertTrue(cf.entries.all { it.image is BackgroundImageConfig.LinearGradient })
    }

    @Test
    fun `cross-fade color args become solid-color entries at 50-50`() {
        // premultiplied-alpha wire: two color args, no weights → 0.5 each.
        val imgs = ColorExtractor.extractBackgroundImages(
            el("""[{"type":"cross-fade","args":[
                {"image":{"type":"color","color":{"srgb":{"r":1,"g":0,"b":0,"a":0.01}}}},
                {"image":{"type":"color","color":{"srgb":{"r":0,"g":1,"b":0,"a":1}}}}]}]"""))
        val cf = imgs[0] as BackgroundImageConfig.CrossFade
        assertEquals(0.5f, cf.entries[0].weight, 1e-6f)
        assertTrue(cf.entries.all { it.image is BackgroundImageConfig.SolidColor })
        // Alpha rides into the Compose color — premultiplied compositing
        // in the applier depends on it surviving extraction. Tolerance is
        // one 8-bit step: ValueExtractors quantizes channels to 0..255
        // (0.01 → 3/255 ≈ 0.0118), matching the platform color pipeline.
        val red = (cf.entries[0].image as BackgroundImageConfig.SolidColor).color
        assertEquals(0.01f, red.alpha, 1f / 255f)
    }

    @Test
    fun `cross-fade accepts the FLATTENED color-layer wire (live shape)`() {
        // IRPropertySerializer.deepFlatten inlines {"type":"color",
        // "color":{…}} into {"type":"color","srgb":…,"original":…} — the
        // EXACT wire the converter run on the premultiplied-alpha fixture
        // produced. Both shapes must extract identically.
        val imgs = ColorExtractor.extractBackgroundImages(
            el("""[{"type":"cross-fade","args":[
                {"image":{"type":"color","srgb":{"r":1,"g":0,"b":0,"a":0.01}}},
                {"image":{"type":"color","srgb":{"r":0,"g":1,"b":0}}}]}]"""))
        val cf = imgs[0] as BackgroundImageConfig.CrossFade
        assertTrue(cf.entries.all { it.image is BackgroundImageConfig.SolidColor })
    }

    @Test
    fun `cross-fade with any bad arg drops the whole layer`() {
        // Partial arg lists would re-weight the rest — must drop whole.
        val imgs = ColorExtractor.extractBackgroundImages(
            el("""[{"type":"cross-fade","args":[
                {"weight":50,"image":{"type":"mystery-image"}},
                {"weight":50,"image":{"type":"color","color":{"srgb":{"r":0,"g":1,"b":0}}}}]}]"""))
        assertTrue(imgs.isEmpty())
    }

    @Test
    fun `legacy position key still tolerated`() {
        // Older snapshots wrote "position" — the fallback must survive
        // the GradientCoord migration.
        val imgs = ColorExtractor.extractBackgroundImages(
            el("""[{"type":"radial-gradient","position":{"x":10,"y":90},"stops":$stops}]"""))
        val g = imgs[0] as BackgroundImageConfig.RadialGradient
        assertEquals(0.10f, g.centerX.value, 1e-6f)
        assertEquals(0.90f, g.centerY.value, 1e-6f)
    }

    @Test
    fun `unresolvable units fall back to center not garbage`() {
        // vw needs a viewport this engine doesn't thread — documented
        // fallback is the CSS default center.
        val imgs = ColorExtractor.extractBackgroundImages(
            el("""[{"type":"conic-gradient","pos":{"x":{"original":{"v":10,"u":"VW"}},"y":50},"stops":$stops}]"""))
        val g = imgs[0] as BackgroundImageConfig.ConicGradient
        assertEquals(GradientCoord.CENTER, g.centerX)
        assertNull(null) // structural guard: nothing threw before here
    }
}

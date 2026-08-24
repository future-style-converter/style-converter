package com.styleconverter.runtime.color

// GradientStopResolverTest — wave 47, lane Z1 (the Android mirror of iOS
// GradientStopResolverTests.swift, wave 46 lane Y2 — same scenarios, same
// reference pixels, so the two natives are pinned against each other).
//
// Pins the css-images-4 §3.4.3 stop pipeline (GradientStopResolver), the
// css-color-4 §12 interpolation (GradientRamp + GradientColorMath) and
// the `positionLength` / `interp` wire reads — the mechanisms behind the
// wave-46 Android fails gradient-border-box / gradient-content-box
// (android-ref 0.646: one ramp instead of 30px stripes) and the hsl
// increasing/decreasing-hue pair (0.924 / 0.923: sRGB lerp instead of the
// hue-arc sweep). The byte-stability invariant every committed gradient
// baseline relies on is pinned first: clause-less, non-repeating stop
// lists must come out of the new pipeline EXACTLY as the pre-wave
// extractor's declared-or-`i/(n−1)` handoff fed the Skia shader — the
// five committed gradient fixtures (visual-test Gradient_Linear/Radial/
// Conic/MultiStop + Edge_GradientWithRadius) are all all-nil stop lists,
// enumerated in the dark-stage block below.

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class GradientStopResolverTest {

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Build a stop the way the extractor does: declared fraction or
     *  null, optional px, legacy `position` = declared-or-even-spread
     *  (the even-spread slot is irrelevant to the resolver — 0f here). */
    private fun stop(r: Float, g: Float, b: Float, a: Float = 1f,
                     pos: Float? = null, px: Float? = null) = ColorStop(
        color = Color(r, g, b, a),
        position = pos ?: 0f,
        declaredPosition = pos,
        positionPx = px
    )

    private val legacy = GradientInterpolation.LEGACY
    private fun interp(space: GradientInterpolation.Space,
                       hue: GradientInterpolation.HueMethod = GradientInterpolation.HueMethod.SHORTER) =
        GradientInterpolation(space, hue)

    // ── Byte stability of the legacy path (the dark stage) ──────────────

    @Test
    fun `all-nil stops keep the pre-wave even spread bit for bit`() {
        // The committed gradient baselines are ALL all-nil lists: 2 stops
        // (Gradient_Linear, Gradient_Radial), 3 (Gradient_MultiStop),
        // 4 (Edge_GradientWithRadius), 5 (Gradient_Conic). For each
        // count the fixup must reproduce the old extractor's
        // `i/(n−1)` float division EXACTLY — same operands, same op.
        for (n in 2..5) {
            val stops = List(n) { stop(it / 10f, 0f, 0f) }
            val fixed = GradientStopResolver.fixup(stops, lengthPx = null)
            for (i in 0 until n) {
                // Delta 0f = bit-identical float equality.
                assertEquals("n=$n i=$i", i.toFloat() / (n - 1), fixed[i].loc, 0f)
            }
        }
    }

    @Test
    fun `legacy non-repeating shader inputs are byte-identical to the pre-wave handoff`() {
        // End-to-end: the exact colours the extractor built come back out
        // (the sRGB packed-ARGB round trip is lossless), and no stop is
        // added or subdivided — LEGACY never subdivides on Android.
        val colors = listOf(
            Color(0.4f, 0.494117647f, 0.917647059f), // #667eea (Gradient_Linear)
            Color(0.462745098f, 0.294117647f, 0.635294118f)) // #764ba2
        val stops = colors.map { ColorStop(it, 0f, declaredPosition = null) }
        val fixedStops = listOf(0f, 1f)
        val (outColors, outStops) = GradientStopResolver.shaderStops(
            stops, lengthPx = 400f, repeating = false, interp = legacy)!!
        assertEquals(colors, outColors)
        assertEquals(fixedStops, outStops)
    }

    @Test
    fun `declared in-order percents are untouched`() {
        val fixed = GradientStopResolver.fixup(
            listOf(stop(1f, 0f, 0f, pos = 0f), stop(0f, 1f, 0f, pos = 0.5f),
                   stop(0f, 0f, 1f, pos = 1f)), lengthPx = null)
        // Declared values pass through with zero float drift.
        assertEquals(listOf(0f, 0.5f, 1f), fixed.map { it.loc })
    }

    // ── §3.4.3 fixup ─────────────────────────────────────────────────────

    @Test
    fun `stop behind its predecessor clamps forward`() {
        // WPT gradient-move-stops: `yellow, blue 70%, green 0` → green at
        // 70% (previously Skia's undefined non-monotonic handling
        // happened to approximate this; now it is the spec rule).
        val fixed = GradientStopResolver.fixup(
            listOf(stop(1f, 1f, 0f), stop(0f, 0f, 1f, pos = 0.7f),
                   stop(0f, 0.5f, 0f, pos = 0f)), lengthPx = null)
        assertEquals(listOf(0f, 0.7f, 0.7f), fixed.map { it.loc })
    }

    @Test
    fun `nil run spreads between positioned neighbours`() {
        // `a 20%, b, c, d 80%` → b 40%, c 60% (not the old global 1/3, 2/3).
        val fixed = GradientStopResolver.fixup(
            listOf(stop(1f, 0f, 0f, pos = 0.2f), stop(0f, 1f, 0f), stop(0f, 0f, 1f),
                   stop(1f, 1f, 1f, pos = 0.8f)), lengthPx = null)
        assertEquals(0.4f, fixed[1].loc, 1e-6f)
        assertEquals(0.6f, fixed[2].loc, 1e-6f)
    }

    @Test
    fun `length stops resolve against the gradient line`() {
        // gradient-border-box: `white, black, white 30px` on a 280×280
        // box at 135deg → line = 280·√2 ≈ 395.98px (css-images-3 §3.1.1).
        val len = ColorApplier.lineLengthPx(135f, Size(280f, 280f))
        assertEquals(280f * sqrt(2f), len, 1e-3f)
        val fixed = GradientStopResolver.fixup(
            listOf(stop(1f, 1f, 1f), stop(0f, 0f, 0f), stop(1f, 1f, 1f, px = 30f)),
            lengthPx = len)
        assertEquals(30f / len, fixed[2].loc, 1e-6f)
        // The unpositioned black spreads to the middle of [0, 30px].
        assertEquals(15f / len, fixed[1].loc, 1e-6f)
    }

    @Test
    fun `length stop without a line is unpositioned`() {
        // Conic callers pass null — the px stop degrades to the even
        // spread (with the GradientLog breadcrumb, iOS twin rule).
        val fixed = GradientStopResolver.fixup(
            listOf(stop(1f, 0f, 0f), stop(0f, 1f, 0f, px = 30f), stop(0f, 0f, 1f)),
            lengthPx = null)
        assertEquals(0.5f, fixed[1].loc, 0f)
    }

    // ── Repeating + clip ─────────────────────────────────────────────────

    @Test
    fun `repeating expansion tiles the period across the line`() {
        // Period 0.1 from stops at 0 / 0.05 / 0.1 → the ramp covers
        // [0, 1] with white at every multiple of 0.1 and black halfway.
        val base = GradientStopResolver.fixup(
            listOf(stop(1f, 1f, 1f), stop(0f, 0f, 0f), stop(1f, 1f, 1f, pos = 0.1f)),
            lengthPx = null)
        val tiled = GradientStopResolver.clipToUnit(
            GradientStopResolver.expandRepeating(base), legacy)
        assertEquals(0f, tiled.first().loc, 0f)
        assertEquals(1f, tiled.last().loc, 0f)
        // White stops at k·0.1 …
        val whites = tiled.filter { it.r == 1.0 && it.g == 1.0 && it.b == 1.0 }.map { it.loc }
        assertTrue(whites.any { abs(it - 0.3f) < 1e-6f })
        assertTrue(whites.any { abs(it - 0.7f) < 1e-6f })
        // … black stops at k·0.1 + 0.05.
        val blacks = tiled.filter { it.r == 0.0 && it.g == 0.0 && it.b == 0.0 }.map { it.loc }
        assertTrue(blacks.any { abs(it - 0.45f) < 1e-6f })
        assertFalse(blacks.any { abs(it - 0.4f) < 1e-6f })
        // Locations are monotonic for the Skia shader.
        assertEquals(tiled.map { it.loc }, tiled.map { it.loc }.sorted())
    }

    @Test
    fun `zero-period repeating is the average solid`() {
        // css-images-3 §3.3.3's stated degenerate rendering.
        val base = GradientStopResolver.fixup(
            listOf(stop(1f, 0f, 0f, pos = 0.5f), stop(0f, 0f, 1f, pos = 0.5f)),
            lengthPx = null)
        val out = GradientStopResolver.expandRepeating(base)
        assertEquals(2, out.size)
        assertEquals(0.5, out[0].r, 1e-9)
        assertEquals(0.5, out[0].b, 1e-9)
        assertEquals(listOf(0f, 1f), out.map { it.loc })
    }

    @Test
    fun `period too fine for the cap paints the average solid not a truncated lattice`() {
        // `repeating-linear-gradient(white, black, white 1px)` on the
        // 390px capture line: 393 copies, past MAX_COPIES (256). The
        // wave-46 F2 rule: a TRUNCATED lattice would stripe the top
        // ~256px and leave the rest flat with a hard seam — a WRONG
        // picture; §3.3.3's degenerate solid is the honest render.
        val base = GradientStopResolver.fixup(
            listOf(stop(1f, 1f, 1f), stop(0f, 0f, 0f), stop(1f, 1f, 1f, px = 1f)),
            lengthPx = 390f)
        val out = GradientStopResolver.expandRepeating(base)
        // Two stops, both ends of the line — never a partial lattice.
        assertEquals(2, out.size)
        assertEquals(listOf(0f, 1f), out.map { it.loc })
        // Average of white, black, white = 2/3 on every channel, and the
        // SAME colour at both ends, so the fill is uniform.
        for (s in out) {
            assertEquals(2.0 / 3.0, s.r, 1e-9)
            assertEquals(2.0 / 3.0, s.g, 1e-9)
            assertEquals(2.0 / 3.0, s.b, 1e-9)
            assertEquals(1.0, s.a, 0.0)
        }
        // End to end: every stop of the rendered ramp is that one colour
        // — no seam anywhere on the line.
        val ramp = GradientStopResolver.resolvedRamp(
            listOf(stop(1f, 1f, 1f), stop(0f, 0f, 0f), stop(1f, 1f, 1f, px = 1f)),
            lengthPx = 390f, repeating = true, interp = legacy)
        assertFalse(ramp.isEmpty())
        assertTrue(ramp.all { abs(it.r - 2.0 / 3.0) < 1e-9 && it.r == it.g && it.g == it.b })
    }

    @Test
    fun `thirty px period still materialises the whole lattice`() {
        // The gradient-border-box period on the same 390px line: 13 whole
        // periods, ~16 copies — far under the cap, so the degrade branch
        // must not fire and the lattice is complete.
        val base = GradientStopResolver.fixup(
            listOf(stop(1f, 1f, 1f), stop(0f, 0f, 0f), stop(1f, 1f, 1f, px = 30f)),
            lengthPx = 390f)
        val tiled = GradientStopResolver.clipToUnit(
            GradientStopResolver.expandRepeating(base), legacy)
        assertEquals(0f, tiled.first().loc, 0f)
        assertEquals(1f, tiled.last().loc, 0f)
        val period = 30f / 390f
        // White at every k·period, black halfway between — across the
        // WHOLE line, including the far end a truncation would flatten.
        for (k in 1..12) {
            assertTrue("missing white stop at k=$k",
                tiled.any { abs(it.loc - k * period) < 1e-5f && it.r == 1.0 && it.b == 1.0 })
            assertTrue("missing black stop at k=$k+half",
                tiled.any { abs(it.loc - (k + 0.5f) * period) < 1e-5f && it.r == 0.0 && it.b == 0.0 })
        }
        // Monotonic for Skia, and emphatically not the 2-stop solid.
        assertEquals(tiled.map { it.loc }, tiled.map { it.loc }.sorted())
        assertTrue(tiled.size > 3 * 13)
    }

    @Test
    fun `single-stop repeating stays a solid fill`() {
        // gradient-single-stop-003: `repeating-linear-gradient(green 50px)`.
        val ramp = GradientStopResolver.resolvedRamp(
            listOf(stop(0f, 0.5f, 0f, px = 50f)),
            lengthPx = 100f, repeating = true, interp = legacy)
        assertEquals(1, ramp.size)
        // Compose packs sRGB colours to ARGB8888, so 0.5f reads back as
        // 128/255 ≈ 0.501961 — one 8-bit quantum is the honest tolerance
        // (the OLD path carried the same quantised colour).
        assertEquals(0.5, ramp[0].g, 1.0 / 255.0)
        // shaderStops widens it to the same colour at 0 and 1 (Skia
        // shaders require ≥ 2 entries — the wave-36 M8 rule).
        val (colors, locs) = GradientStopResolver.shaderStops(
            listOf(stop(0f, 0.5f, 0f, px = 50f)),
            lengthPx = 100f, repeating = true, interp = legacy)!!
        assertEquals(2, colors.size)
        assertEquals(colors[0], colors[1])
        assertEquals(listOf(0f, 1f), locs)
    }

    @Test
    fun `stops beyond the line clip to the boundary colour`() {
        // gradient-infinity-001 shape: `lime 100px, red calc(∞)` on a
        // 100px line → lime to the end; the red never enters the box.
        val base = GradientStopResolver.fixup(
            listOf(stop(0f, 1f, 0f, px = 100f), stop(1f, 0f, 0f, px = 1e9f)),
            lengthPx = 100f)
        val out = GradientStopResolver.clipToUnit(base, legacy)
        assertEquals(0f, out.first().loc, 0f)
        assertEquals(1f, out.last().loc, 0f)
        assertTrue(out.all { it.g == 1.0 && it.r < 1e-6 })
    }

    @Test
    fun `no stops at all is null shader input`() {
        // The brush factories' contract: a malformed payload fails
        // visibly (layer dropped) rather than inventing a colour.
        assertNull(GradientStopResolver.shaderStops(
            emptyList(), lengthPx = 100f, repeating = false, interp = legacy))
    }

    // ── Interpolation clause ─────────────────────────────────────────────

    @Test
    fun `interp clause parsing`() {
        assertEquals(legacy, GradientInterpolation.parse(null))
        assertEquals(interp(GradientInterpolation.Space.OKLAB),
            GradientInterpolation.parse("in oklab"))
        assertEquals(interp(GradientInterpolation.Space.HSL, GradientInterpolation.HueMethod.LONGER),
            GradientInterpolation.parse("in hsl longer hue"))
        assertEquals(interp(GradientInterpolation.Space.LCH, GradientInterpolation.HueMethod.DECREASING),
            GradientInterpolation.parse("in lch decreasing hue"))
        // Hue method on a rectangular space is ungrammatical (§12.4).
        assertEquals(legacy, GradientInterpolation.parse("in oklab longer hue"))
        // Unsupported-but-valid space degrades to legacy (with a breadcrumb).
        assertEquals(legacy, GradientInterpolation.parse("in display-p3-linear"))
    }

    @Test
    fun `extractor reads positionLength and interp`() {
        // The EXACT wire of the wave46-final gradient-border-box per-test
        // IR (positionLength) plus the increasing-hue-hsl interp clause.
        val wire = """[{"type":"repeating-linear-gradient","angle":{"deg":135},
            "interp":"in hsl increasing hue","stops":[
            {"color":{"srgb":{"r":1,"g":1,"b":1},"original":"white"},"position":null},
            {"color":{"srgb":{"r":0,"g":0,"b":0},"original":"black"},"position":null,
             "positionLength":{"px":30}}]}]"""
        val images = ColorExtractor.extractBackgroundImages(Json.parseToJsonElement(wire))
        val g = images.single() as BackgroundImageConfig.LinearGradient
        assertTrue(g.repeating)
        assertEquals(135f, g.angle, 0f)
        assertEquals(interp(GradientInterpolation.Space.HSL,
            GradientInterpolation.HueMethod.INCREASING), g.interp)
        // Stop 0: unpositioned — declared null, px null.
        assertNull(g.colorStops[0].declaredPosition)
        assertNull(g.colorStops[0].positionPx)
        // Stop 1: the wave-40 <length> arm survives extraction now.
        assertEquals(30f, g.colorStops[1].positionPx!!, 0f)
        assertNull(g.colorStops[1].declaredPosition)
    }

    @Test
    fun `runtime-dependent length stop stays unpositioned`() {
        // `positionLength: {original: {v, u}}` with no px — em/lh etc.
        // cannot resolve against a gradient line with no font context;
        // the stop degrades to unpositioned (breadcrumbed).
        val wire = """[{"type":"linear-gradient","stops":[
            {"color":{"srgb":{"r":1,"g":0,"b":0}},"position":null},
            {"color":{"srgb":{"r":0,"g":0,"b":1}},"position":null,
             "positionLength":{"original":{"v":2,"u":"EM"}}}]}]"""
        val g = ColorExtractor.extractBackgroundImages(Json.parseToJsonElement(wire))
            .single() as BackgroundImageConfig.LinearGradient
        assertNull(g.colorStops[1].positionPx)
        assertNull(g.colorStops[1].declaredPosition)
        // And a declared percent still lands in declaredPosition.
        val wire2 = """[{"type":"linear-gradient","stops":[
            {"color":{"srgb":{"r":1,"g":0,"b":0}},"position":70},
            {"color":{"srgb":{"r":0,"g":0,"b":1}},"position":null}]}]"""
        val g2 = ColorExtractor.extractBackgroundImages(Json.parseToJsonElement(wire2))
            .single() as BackgroundImageConfig.LinearGradient
        assertEquals(0.7f, g2.colorStops[0].declaredPosition!!, 1e-6f)
    }

    // ── css-color-4 §12.5 hue arcs ───────────────────────────────────────

    @Test
    fun `hue arc selection`() {
        val sh = GradientInterpolation.HueMethod.SHORTER
        val lo = GradientInterpolation.HueMethod.LONGER
        val inc = GradientInterpolation.HueMethod.INCREASING
        val dec = GradientInterpolation.HueMethod.DECREASING
        // Examples straight from the §12.5 tables (h1 = 0, h2 = 40).
        assertEquals(0.0 to 40.0, GradientRamp.hueArc(0.0, 40.0, sh))
        assertEquals(360.0 to 40.0, GradientRamp.hueArc(0.0, 40.0, lo))
        assertEquals(0.0 to 40.0, GradientRamp.hueArc(0.0, 40.0, inc))
        assertEquals(360.0 to 40.0, GradientRamp.hueArc(0.0, 40.0, dec))
        assertEquals(40.0 to 360.0, GradientRamp.hueArc(40.0, 0.0, inc))
        assertEquals(40.0 to 0.0, GradientRamp.hueArc(40.0, 0.0, dec))
        // Equal hues under `longer` sweep the full wheel.
        assertEquals(0.0 to 360.0, GradientRamp.hueArc(0.0, 0.0, lo))
        // Shorter never exceeds 180°: 350 → 10 goes through 360.
        assertEquals(350.0 to 370.0, GradientRamp.hueArc(350.0, 10.0, sh))
    }

    // ── GradientRamp ─────────────────────────────────────────────────────

    private fun rgba(r: Double, g: Double, b: Double, a: Double = 1.0, loc: Float = 0f) =
        GradientStopResolver.RGBAStop(r, g, b, a, loc)

    @Test
    fun `hsl increasing hue red to purple sweeps through green`() {
        // gradient-increasing-hue-hsl row 3: hsl(0) → hsl(270) increasing
        // → midpoint hue 135 (spring green), not the 315 the short arc gives.
        val mid = GradientRamp.interpolate(
            rgba(1.0, 0.0, 0.0), rgba(0.5, 0.0, 1.0, loc = 1f), 0.5,
            interp(GradientInterpolation.Space.HSL, GradientInterpolation.HueMethod.INCREASING))
        val h = GradientColorMath.srgbToHSL(mid.r, mid.g, mid.b)
        assertEquals(135.0, h.c0, 0.01)
        assertEquals(100.0, h.c1, 0.01)
    }

    @Test
    fun `hsl premultiplied fade keeps saturation`() {
        // gradient-powerless-hue-hsl: red → hsl(120 100% 50% / 0) in hsl:
        // the midpoint is YELLOW at half alpha — saturation/lightness are
        // premultiplied so the transparent stop cannot wash them out.
        val mid = GradientRamp.interpolate(
            rgba(1.0, 0.0, 0.0), rgba(0.0, 1.0, 0.0, a = 0.0, loc = 1f), 0.5,
            interp(GradientInterpolation.Space.HSL))
        assertEquals(1.0, mid.r, 1e-9)
        assertEquals(1.0, mid.g, 1e-9)
        assertEquals(0.0, mid.b, 1e-9)
        assertEquals(0.5, mid.a, 1e-12)
    }

    @Test
    fun `powerless hue is carried from the other stop`() {
        // gradient-longer-hue-hsl-013 row 1: red → black in hsl longer
        // hue. Black's hue is powerless, adopts red's 0, and `longer` on
        // equal hues sweeps 360° — the midpoint is cyan-ish, l = 25.
        val mid = GradientRamp.interpolate(
            rgba(1.0, 0.0, 0.0), rgba(0.0, 0.0, 0.0, loc = 1f), 0.5,
            interp(GradientInterpolation.Space.HSL, GradientInterpolation.HueMethod.LONGER))
        val h = GradientColorMath.srgbToHSL(mid.r, mid.g, mid.b)
        assertEquals(180.0, h.c0, 0.01)
        assertEquals(25.0, h.c2, 0.01)
    }

    @Test
    fun `legacy interpolation is the plain srgb lerp`() {
        // red → transparent, legacy: non-premultiplied (0.5, 0, 0, 0.5) —
        // the clip-boundary sampling path must match the Skia ramp.
        val mid = GradientRamp.interpolate(
            rgba(1.0, 0.0, 0.0), rgba(0.0, 0.0, 0.0, a = 0.0, loc = 1f), 0.5, legacy)
        assertEquals(0.5, mid.r, 1e-12)
        assertEquals(0.5, mid.a, 1e-12)
    }

    @Test
    fun `oklab midpoint of lime and red is brighter than the srgb olive`() {
        // gradient-analogous-missing-components-004: `in oklab, lime, red`
        // passes a bright yellow-orange at the middle; sRGB gives olive.
        val mid = GradientRamp.interpolate(
            rgba(0.0, 1.0, 0.0), rgba(1.0, 0.0, 0.0, loc = 1f), 0.5,
            interp(GradientInterpolation.Space.OKLAB))
        assertTrue(mid.r > 0.7)
        assertTrue(mid.g > 0.6)
        assertTrue(mid.b < 0.05)
    }

    @Test
    fun `longer hue subdivision density`() {
        // A 360° sweep gets ≥ 36 micro-stops (one per ≤10°), not 12.
        val fine = GradientRamp.subdivided(
            listOf(rgba(1.0, 0.0, 0.0), rgba(0.0, 0.0, 0.0, loc = 1f)),
            interp(GradientInterpolation.Space.HSL, GradientInterpolation.HueMethod.LONGER))
        assertTrue(fine.size >= 37)
    }

    // ── Browser ground truth (frozen WPT reference pixels) ───────────────

    /**
     * Composite a ramp sample over white and compare to an 8-bit pixel
     * probed from the frozen Chromium reference PNGs (tools/wpt/refs/…/
     * css-images) — the SAME triples the iOS twin pins, so the two
     * natives cannot drift apart. Tolerance 4/255 covers the reference's
     * own quantisation + the ±1px probe position.
     */
    private fun assertRef(s: GradientStopResolver.RGBAStop, r: Int, g: Int, b: Int,
                          label: String, tolerance: Int = 4) {
        fun over(c: Double) = ((c * s.a + (1 - s.a)) * 255 + 0.5).toInt()
        assertTrue("$label r: ${over(s.r)} vs $r", abs(over(s.r) - r) <= tolerance)
        assertTrue("$label g: ${over(s.g)} vs $g", abs(over(s.g) - g) <= tolerance)
        assertTrue("$label b: ${over(s.b)} vs $b", abs(over(s.b) - b) <= tolerance)
    }

    @Test
    fun `powerless hue family matches the Chromium reference`() {
        // `linear-gradient(to right in <space>, red, <transparent green>)`
        // — the stop pair exactly as the converter emits it (sRGB floats,
        // alpha 0) for gradient-powerless-hue-{hsl,hwb,lch,oklch}; the
        // expected triples were probed from the frozen refs at x = 25/50/75%.
        val red = rgba(1.0, 0.0, 0.0)
        val green = rgba(0.0, 1.0, 0.0, a = 0.0, loc = 1f)
        val lchGreen = rgba(0.0010253138701899005, 1.0, 0.0, a = 0.0, loc = 1f)
        data class Row(val space: GradientInterpolation.Space,
                       val b: GradientStopResolver.RGBAStop,
                       val samples: List<Pair<Double, Triple<Int, Int, Int>>>)
        val table = listOf(
            Row(GradientInterpolation.Space.HSL, green, listOf(
                0.25 to Triple(255, 161, 65), 0.5 to Triple(255, 255, 128), 0.75 to Triple(224, 255, 192))),
            Row(GradientInterpolation.Space.HWB, green, listOf(
                0.25 to Triple(255, 161, 65), 0.5 to Triple(255, 255, 128), 0.75 to Triple(224, 255, 192))),
            Row(GradientInterpolation.Space.LCH, lchGreen, listOf(
                0.25 to Triple(224, 133, 64), 0.5 to Triple(207, 191, 128), 0.75 to Triple(213, 228, 192))),
            Row(GradientInterpolation.Space.OKLCH, green, listOf(
                0.25 to Triple(245, 115, 64), 0.5 to Triple(229, 185, 128), 0.75 to Triple(226, 229, 192))),
        )
        for (row in table) {
            for ((t, px) in row.samples) {
                val s = GradientRamp.interpolate(red, row.b, t, interp(row.space))
                assertRef(s, px.first, px.second, px.third, "${row.space} t=$t")
            }
        }
    }

    @Test
    fun `oklab lime to red matches the Chromium reference`() {
        // gradient-analogous-missing-components-004 row 5 (`in oklab`).
        val lime = rgba(0.0, 1.0, 0.0)
        val red = rgba(1.0, 0.0, 0.0, loc = 1f)
        for ((t, px) in listOf(0.25 to Triple(160, 212, 0), 0.5 to Triple(208, 168, 0),
                               0.75 to Triple(237, 114, 0))) {
            val s = GradientRamp.interpolate(lime, red, t, interp(GradientInterpolation.Space.OKLAB))
            assertRef(s, px.first, px.second, px.third, "oklab t=$t")
        }
    }

    @Test
    fun `longer hue red to black matches the Chromium reference`() {
        // gradient-longer-hue-hsl-013 row 1 — the powerless-carry wheel.
        // The box is only 100px wide, so the wheel turns 3.6°/px: one
        // pixel of probe alignment is the honest tolerance (16), not the
        // 4/255 the 200px boxes allow (same note as the iOS twin).
        val red = rgba(1.0, 0.0, 0.0)
        val black = rgba(0.0, 0.0, 0.0, loc = 1f)
        for ((t, px) in listOf(0.25 to Triple(91, 166, 25), 0.5 to Triple(31, 92, 94),
                               0.75 to Triple(32, 24, 39))) {
            val s = GradientRamp.interpolate(red, black, t,
                interp(GradientInterpolation.Space.HSL, GradientInterpolation.HueMethod.LONGER))
            assertRef(s, px.first, px.second, px.third, "longer t=$t", tolerance = 16)
        }
    }

    // ── Colour-space round trips ─────────────────────────────────────────

    @Test
    fun `every space round trips srgb`() {
        val spaces = listOf(
            GradientInterpolation.Space.SRGB, GradientInterpolation.Space.SRGB_LINEAR,
            GradientInterpolation.Space.LAB, GradientInterpolation.Space.OKLAB,
            GradientInterpolation.Space.XYZ_D65, GradientInterpolation.Space.XYZ_D50,
            GradientInterpolation.Space.HSL, GradientInterpolation.Space.HWB,
            GradientInterpolation.Space.LCH, GradientInterpolation.Space.OKLCH)
        val samples = listOf(
            GradientColorMath.Triple3(1.0, 0.0, 0.0),
            GradientColorMath.Triple3(0.0, 0.5019608, 0.0),
            GradientColorMath.Triple3(0.2, 0.4, 0.9),
            GradientColorMath.Triple3(1.0, 1.0, 1.0),
            GradientColorMath.Triple3(0.0, 0.0, 0.0),
            GradientColorMath.Triple3(0.5, 0.5, 0.5))
        for (space in spaces) {
            for (c in samples) {
                val back = GradientColorMath.toSrgb(GradientColorMath.fromSrgb(c, space), space)
                assertEquals("$space r", c.c0, back.c0, 1e-6)
                assertEquals("$space g", c.c1, back.c1, 1e-6)
                assertEquals("$space b", c.c2, back.c2, 1e-6)
            }
        }
    }

    @Test
    fun `known oklch and lab coordinates`() {
        // css-color-4 worked examples: sRGB red ≈ oklch(0.628 0.258
        // 29.23°), sRGB white = lab(100 0 0).
        val red = GradientColorMath.fromSrgb(
            GradientColorMath.Triple3(1.0, 0.0, 0.0), GradientInterpolation.Space.OKLCH)
        assertEquals(0.628, red.c0, 0.002)
        assertEquals(0.258, red.c1, 0.002)
        assertEquals(29.23, red.c2, 0.1)
        val white = GradientColorMath.fromSrgb(
            GradientColorMath.Triple3(1.0, 1.0, 1.0), GradientInterpolation.Space.LAB)
        assertEquals(100.0, white.c0, 0.01)
        assertEquals(0.0, white.c1, 0.01)
        assertEquals(0.0, white.c2, 0.01)
        // Powerless analysis: white/black hues are powerless in every
        // polar space; red's is not.
        for (space in listOf(GradientInterpolation.Space.HSL, GradientInterpolation.Space.HWB,
                             GradientInterpolation.Space.LCH, GradientInterpolation.Space.OKLCH)) {
            assertTrue("$space white", GradientColorMath.hueIsPowerless(
                GradientColorMath.fromSrgb(GradientColorMath.Triple3(1.0, 1.0, 1.0), space), space))
            assertTrue("$space black", GradientColorMath.hueIsPowerless(
                GradientColorMath.fromSrgb(GradientColorMath.Triple3(0.0, 0.0, 0.0), space), space))
            assertFalse("$space red", GradientColorMath.hueIsPowerless(
                GradientColorMath.fromSrgb(GradientColorMath.Triple3(1.0, 0.0, 0.0), space), space))
        }
    }
}

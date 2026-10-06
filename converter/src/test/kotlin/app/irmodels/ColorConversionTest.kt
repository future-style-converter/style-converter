package app.irmodels

// Colorimetric unit tests for the predefined color() spaces and CIELAB, added
// for TITAN WPT Round 5 (css-color/a98rgb-00N). Expected sRGB values are the
// ones the CSS Color 4 §17 sample conversion code produces (cross-checked by
// hand against Bruce Lindbloom's matrices). Every assertion clamps to gamut the
// same way the parser does, then compares within a small tolerance because the
// matrices are floating point.

import app.parsing.css.properties.primitiveParsers.ColorParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ColorConversionTest {

    // Absolute tolerance for a single sRGB channel (matrices are fp, ~1e-3 drift).
    private val tol = 2e-3

    private fun assertChannel(expected: Double, actual: Double, label: String) {
        assertTrue(
            kotlin.math.abs(expected - actual) <= tol,
            "$label: expected ~$expected but was $actual (Δ=${kotlin.math.abs(expected - actual)})"
        )
    }

    private fun assertSrgb(c: SRGB, r: Double, g: Double, b: Double, a: Double = 1.0) {
        assertChannel(r, c.r, "r"); assertChannel(g, c.g, "g")
        assertChannel(b, c.b, "b"); assertChannel(a, c.a, "alpha")
    }

    // ---- a98-rgb (Adobe RGB 1998) → sRGB ----

    @Test
    fun `a98-rgb black is sRGB black`() {
        // color(a98-rgb 0 0 0) — all channels 0 → sRGB (0,0,0). (WPT a98rgb-002.)
        assertSrgb(ColorConversion.a98RgbToSrgb(0.0, 0.0, 0.0).clamped(), 0.0, 0.0, 0.0)
    }

    @Test
    fun `a98-rgb white is sRGB white`() {
        // color(a98-rgb 1 1 1) — a98 and sRGB share the D65 white, so → ~(1,1,1).
        assertSrgb(ColorConversion.a98RgbToSrgb(1.0, 1.0, 1.0).clamped(), 1.0, 1.0, 1.0)
    }

    @Test
    fun `a98-rgb pure green maps to sRGB lime after gamut clip`() {
        // color(a98-rgb 0 1 0) — the a98 green primary is wider than sRGB's, so the
        // in-gamut result clips to pure sRGB green (0,1,0). (WPT a98rgb-004 ref.)
        assertSrgb(ColorConversion.a98RgbToSrgb(0.0, 1.0, 0.0).clamped(), 0.0, 1.0, 0.0)
    }

    @Test
    fun `a98-rgb pure red maps to sRGB red after gamut clip`() {
        // color(a98-rgb 1 0 0) — reddest a98 clips to sRGB red (1,0,0).
        assertSrgb(ColorConversion.a98RgbToSrgb(1.0, 0.0, 0.0).clamped(), 1.0, 0.0, 0.0)
    }

    @Test
    fun `a98-rgb mid green equals CSS named green`() {
        // color(a98-rgb 0.281363 0.498012 0.116746) is the a98 encoding of CSS
        // `green` (#008000 = sRGB 0, 0.50196, 0) — the WPT a98rgb-001 green square.
        val c = ColorConversion.a98RgbToSrgb(0.281363, 0.498012, 0.116746).clamped()
        assertSrgb(c, 0.0, 128.0 / 255.0, 0.0)
    }

    @Test
    fun `a98-rgb carries alpha through unchanged`() {
        // Alpha is not a color-space channel; it passes straight through.
        assertSrgb(ColorConversion.a98RgbToSrgb(0.0, 0.0, 0.0, 0.5).clamped(), 0.0, 0.0, 0.0, 0.5)
    }

    // ---- CIELAB (D50) → sRGB ----

    @Test
    fun `lab of the a98 green primary resolves to sRGB green`() {
        // lab(83.2141% -129.1072 87.1718) is the CIELAB of color(a98-rgb 0 1 0);
        // both must land on sRGB green (0,1,0). (WPT a98rgb-004: the two halves
        // must match.) This also exercises the D50→D65 Bradford adaptation.
        assertSrgb(ColorConversion.labToSrgb(83.2141, -129.1072, 87.1718).clamped(), 0.0, 1.0, 0.0)
    }

    @Test
    fun `lab pure lightness is sRGB white`() {
        // lab(100 0 0) is the D50 white → adapted to D65 → sRGB white (1,1,1).
        assertSrgb(ColorConversion.labToSrgb(100.0, 0.0, 0.0).clamped(), 1.0, 1.0, 1.0)
    }

    @Test
    fun `lab zero lightness is sRGB black`() {
        // lab(0 0 0) → sRGB black (0,0,0).
        assertSrgb(ColorConversion.labToSrgb(0.0, 0.0, 0.0).clamped(), 0.0, 0.0, 0.0)
    }

    // ---- Other predefined color() spaces (cheap additions) ----

    @Test
    fun `srgb-linear green gamma-encodes to sRGB green`() {
        // color(srgb-linear 0 1 0) — linear 1.0 gamma-encodes to 1.0 → (0,1,0).
        assertSrgb(ColorConversion.srgbLinearToSrgb(0.0, 1.0, 0.0).clamped(), 0.0, 1.0, 0.0)
    }

    @Test
    fun `display-p3 extremes map to sRGB black and white`() {
        assertSrgb(ColorConversion.displayP3ToSrgb(0.0, 0.0, 0.0).clamped(), 0.0, 0.0, 0.0)
        assertSrgb(ColorConversion.displayP3ToSrgb(1.0, 1.0, 1.0).clamped(), 1.0, 1.0, 1.0)
    }

    @Test
    fun `rec2020 extremes map to sRGB black and white`() {
        assertSrgb(ColorConversion.rec2020ToSrgb(0.0, 0.0, 0.0).clamped(), 0.0, 0.0, 0.0)
        assertSrgb(ColorConversion.rec2020ToSrgb(1.0, 1.0, 1.0).clamped(), 1.0, 1.0, 1.0)
    }

    // ---- Linear-light twins (wave-52 L1 F-A; css-color-4 §10.2) ----
    //
    // MUTATION RECORD (executed 2026-09-25, restored byte-exact, sha-verified
    // 82856d07…f833a): making `displayP3LinearToSrgb` gamma-decode its inputs
    // (`mul3(P3_TO_XYZ, srgbGammaToLinear(r), …)`) failed
    // `display-p3-linear green is CSS green` with g = 0.21289504620248018
    // (expected ~0.50196 — the decode reads 0.2087 as an ENCODED value, so the
    // light drops to ~0.036) and failed `linear twins differ …` with
    // linear == encoded == 0.20870253472238282. The parser-side mutations are
    // recorded in ColorParserLinearSpaceTest. Both pins are able to fail.

    @Test
    fun `display-p3-linear extremes map to sRGB black and white`() {
        // color(display-p3-linear 0 0 0) / (1 1 1): P3 and sRGB share the D65
        // white, so the linear path lands on exact black and white. WPT
        // display-p3-linear-002 (black square) and -003 (white `.test` == `.ref`).
        assertSrgb(ColorConversion.displayP3LinearToSrgb(0.0, 0.0, 0.0).clamped(), 0.0, 0.0, 0.0)
        assertSrgb(ColorConversion.displayP3LinearToSrgb(1.0, 1.0, 1.0).clamped(), 1.0, 1.0, 1.0)
    }

    @Test
    fun `display-p3-linear green is CSS green`() {
        // color(display-p3-linear 0.0383 0.2087 0.0156) is the WPT
        // display-p3-linear-001 spelling of CSS `green` (#008000 = sRGB
        // 0, 0.50196, 0). The gamma-decoding sibling gives g = 0.2129 (the
        // mutation record above measured it), far outside the tolerance.
        // Tolerance: the brief's ±1/255 — one 8-bit step, tighter than `tol`.
        val c = ColorConversion.displayP3LinearToSrgb(0.0383, 0.2087, 0.0156).clamped()
        assertTrue(kotlin.math.abs(c.r - 0.0) <= 1.0 / 255, "r: ${c.r}")
        assertTrue(kotlin.math.abs(c.g - 128.0 / 255.0) <= 1.0 / 255, "g: ${c.g} (expected ~0.50196)")
        assertTrue(kotlin.math.abs(c.b - 0.0) <= 1.0 / 255, "b: ${c.b}")
    }

    @Test
    fun `a98-rgb-linear and rec2020-linear extremes map to sRGB black and white`() {
        // The same "no decode" shape for the other two §10.2 twins; both share
        // the D65 white so the extremes are exact (no corpus test spells them
        // today — the census beside this lane's note counts 0 — pinned so the
        // arms cannot regress unnoticed when one appears).
        assertSrgb(ColorConversion.a98RgbLinearToSrgb(0.0, 0.0, 0.0).clamped(), 0.0, 0.0, 0.0)
        assertSrgb(ColorConversion.a98RgbLinearToSrgb(1.0, 1.0, 1.0).clamped(), 1.0, 1.0, 1.0)
        assertSrgb(ColorConversion.rec2020LinearToSrgb(0.0, 0.0, 0.0).clamped(), 0.0, 0.0, 0.0)
        assertSrgb(ColorConversion.rec2020LinearToSrgb(1.0, 1.0, 1.0).clamped(), 1.0, 1.0, 1.0)
    }

    @Test
    fun `linear twins differ from their gamma-encoded parents on a mid value`() {
        // The one property that makes the twin a twin: for 0 < v < 1 the
        // gamma-encoded parent DECODES v (darkening it) and the linear twin
        // does not, so the two must disagree on a mid-grey. Pinned so a lazy
        // `"display-p3-linear" -> displayP3ToSrgb` alias can never pass.
        val lin = ColorConversion.displayP3LinearToSrgb(0.2087, 0.2087, 0.2087).clamped()
        val enc = ColorConversion.displayP3ToSrgb(0.2087, 0.2087, 0.2087).clamped()
        assertTrue(lin.g - enc.g > 0.2, "linear ${lin.g} must sit well above encoded ${enc.g}")
    }

    @Test
    fun `xyz-d65 white point resolves to sRGB white`() {
        // The CIE XYZ of the D65 white is (0.95046, 1.0, 1.08906) → sRGB white.
        assertSrgb(ColorConversion.xyzD65ToSrgb(0.9504559, 1.0, 1.0890578).clamped(), 1.0, 1.0, 1.0)
    }

    // ---- End-to-end through ColorParser (the emitted `srgb` field) ----

    @Test
    fun `parser resolves color a98-rgb black to sRGB black`() {
        val ir = ColorParser.parse("color(a98-rgb 0 0 0)")
        assertNotNull(ir); assertNotNull(ir.srgb)  // smart-casts ir and ir.srgb non-null
        assertSrgb(ir.srgb, 0.0, 0.0, 0.0)
    }

    @Test
    fun `parser resolves color a98-rgb green to CSS green`() {
        val ir = ColorParser.parse("color(a98-rgb 0.281363 0.498012 0.116746)")
        assertNotNull(ir); assertNotNull(ir.srgb)
        assertSrgb(ir.srgb, 0.0, 128.0 / 255.0, 0.0)
    }

    @Test
    fun `parser resolves lab a98 green to sRGB green`() {
        // The WPT fixture spells lightness as a percentage; the parser strips '%'.
        val ir = ColorParser.parse("lab(83.2141% -129.1072 87.1718)")
        assertNotNull(ir); assertNotNull(ir.srgb)
        assertSrgb(ir.srgb, 0.0, 1.0, 0.0)
    }

    // ---- ok-space lightness clamp (wave-48 S2 note) ----
    //
    // css-color-4 §9.2: ok-space L is canonically 0..1 (100% = 1.0) and
    // out-of-range L clamps to that reference range. The old conversion-side
    // `>1 → /100` percentage heuristic mis-read a canonical L of 1.5 as an
    // unscaled percentage (→ 0.015, near-black) where Chrome clamps to 1.0.

    @Test
    fun `oklab lightness above 1 clamps to white not near-black`() {
        // oklab(1.5 0 0): L clamps to 1.0 → the achromatic OKLab white →
        // sRGB white (1,1,1). Under the old /100 heuristic this came out
        // ~(0.015)^3-scale — indistinguishable from black.
        assertSrgb(ColorConversion.oklabToSrgb(1.5, 0.0, 0.0).clamped(), 1.0, 1.0, 1.0)
    }

    @Test
    fun `oklch lightness above 1 clamps to white not near-black`() {
        // Same clamp through the OKLCH polar wrapper (its own coerceIn —
        // both ok-space converters carry the §9.2 reference-range clamp).
        assertSrgb(ColorConversion.oklchToSrgb(1.5, 0.0, 0.0).clamped(), 1.0, 1.0, 1.0)
    }

    @Test
    fun `parser resolves oklab 1_5 0 0 to sRGB white end-to-end`() {
        // End-to-end: the parser emits the canonical L (1.5) and the emitted
        // `srgb` field must be the CLAMPED white, matching Chrome's pixels.
        val ir = ColorParser.parse("oklab(1.5 0 0)")
        assertNotNull(ir); assertNotNull(ir.srgb)
        assertSrgb(ir.srgb, 1.0, 1.0, 1.0)
    }

    @Test
    fun `oklab percentage lightness still scales as before`() {
        // Guard: the parser's % arm (×0.01) is untouched — oklab(100% 0 0)
        // is canonical L = 1.0 → white. Pinned so removing the parser-side
        // scaling cannot hide behind the new conversion-side clamp.
        val ir = ColorParser.parse("oklab(100% 0 0)")
        assertNotNull(ir); assertNotNull(ir.srgb)
        assertSrgb(ir.srgb, 1.0, 1.0, 1.0)
    }

    // ---- Regression guards: previously-supported colors must not change ----

    @Test
    fun `hex red still resolves to sRGB red`() {
        assertSrgb(ColorParser.parse("#ff0000")!!.srgb!!, 1.0, 0.0, 0.0)
    }

    @Test
    fun `named green still resolves to sRGB 0-128-0`() {
        assertSrgb(ColorParser.parse("green")!!.srgb!!, 0.0, 128.0 / 255.0, 0.0)
    }

    @Test
    fun `rgb still resolves`() {
        assertSrgb(ColorParser.parse("rgb(0 128 0)")!!.srgb!!, 0.0, 128.0 / 255.0, 0.0)
    }

    @Test
    fun `hsl green still resolves to sRGB green`() {
        assertSrgb(ColorParser.parse("hsl(120 100% 50%)")!!.srgb!!, 0.0, 1.0, 0.0)
    }

    @Test
    fun `plain srgb color function still resolves`() {
        assertSrgb(ColorParser.parse("color(srgb 1 0.5 0.2)")!!.srgb!!, 1.0, 0.5, 0.2)
    }
}

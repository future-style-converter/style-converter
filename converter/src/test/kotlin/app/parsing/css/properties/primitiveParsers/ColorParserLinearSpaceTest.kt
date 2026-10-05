package app.parsing.css.properties.primitiveParsers

// wave-52 lane L1 (web-tail-colour-vt) F-A — the linear-light predefined
// colour spaces of css-color-4 §10.2: `display-p3-linear`, `a98-rgb-linear`,
// `rec2020-linear`.
//
// Before the fix `colorFunctionRegex` admitted any `[\w-]+` space name, so the
// parse SUCCEEDED, the IR carried
// `{type:"color", colorSpace:"display-p3-linear", values:[…]}` and NO `srgb`
// (wave51-fix per-test-ir css-color/display-p3-linear-001..006 — 0 of the 6
// `display-p3-linear` declarations carry `srgb`, every sibling `.ref` does),
// and all three runtimes painted nothing for the `.test` box:
// wave51-fix css-color/display-p3-linear-001 web f 0.8775 · -002 f 0.7977 ·
// -003 f 0.9484 (natives within 0.002 of web on each).
//
// Every payload below that names a WPT test is the VERBATIM corpus spelling;
// the expected sRGB is the value the SAME test's own `.ref` box declares (the
// test author already did the conversion — we pin against it, not against
// our own matrix). Tolerance is one 8-bit step (1/255) per channel.
//
// MUTATION RECORD (executed 2026-09-25, restored byte-exact, sha-verified
// 45570db2…e9af): deleting the `"display-p3-linear" ->` arm in ColorParser.kt
// failed six pins here with `srgb` null (the `else -> null` fallthrough:
// -001..-006); aliasing the arm to the gamma-decoding `displayP3ToSrgb`
// failed the -001 green pin (g = 0.21289504620248018, expected ~0.50196) and
// the -006 moss-green pin (r = 0.1658239408052024, expected ~0.44706).

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import app.irmodels.IRColor
import app.irmodels.SRGB

class ColorParserLinearSpaceTest {

    // One 8-bit step per channel: the comparison the pixel gate itself makes.
    private val step = 1.0 / 255.0

    private fun assertSrgbWithin(actual: SRGB?, r: Double, g: Double, b: Double, label: String) {
        assertNotNull(actual, "$label: srgb must be computed (the runtimes paint from it)")
        assertTrue(kotlin.math.abs(actual.r - r) <= step, "$label r: expected ~$r, was ${actual.r}")
        assertTrue(kotlin.math.abs(actual.g - g) <= step, "$label g: expected ~$g, was ${actual.g}")
        assertTrue(kotlin.math.abs(actual.b - b) <= step, "$label b: expected ~$b, was ${actual.b}")
    }

    @Test
    fun `display-p3-linear-001 green resolves to CSS green`() {
        // VERBATIM: `.test { background-color: color(display-p3-linear 0.0383 0.2087 0.0156); }`
        // "green (sRGB #008000) converted to display-p3-linear" — the ref is a
        // solid #008000 square, which the frozen ref PNG confirms at (0,128,0).
        val ir = ColorParser.parse("color(display-p3-linear 0.0383 0.2087 0.0156)")
        assertNotNull(ir)
        assertSrgbWithin(ir.srgb, 0.0, 128.0 / 255.0, 0.0, "-001")
    }

    @Test
    fun `display-p3-linear-002 black resolves to sRGB black`() {
        // VERBATIM: `color(display-p3-linear 0 0 0)` — "black (sRGB #000000)".
        val ir = ColorParser.parse("color(display-p3-linear 0 0 0)")
        assertNotNull(ir)
        assertSrgbWithin(ir.srgb, 0.0, 0.0, 0.0, "-002")
    }

    @Test
    fun `display-p3-linear-003 white equals its rgb 100 percent ref`() {
        // VERBATIM: `.ref { background-color: rgb(100% 100% 100%); }` and
        // `.test { background-color: color(display-p3-linear 1 1 1); }` — the
        // ref PNG shows one fused white 192×192 square on the grey body.
        val test = ColorParser.parse("color(display-p3-linear 1 1 1)")
        val ref = ColorParser.parse("rgb(100% 100% 100%)")
        assertNotNull(test); assertNotNull(ref); assertNotNull(ref.srgb)
        assertSrgbWithin(test.srgb, ref.srgb.r, ref.srgb.g, ref.srgb.b, "-003")
    }

    @Test
    fun `display-p3-linear-004 out-of-gamut green clips to sRGB lime like its lab ref`() {
        // VERBATIM: `.ref { background-color: lab(86.61399% -106.539 102.871); }`
        // `.test { background-color: color(display-p3-linear 0 1 0); }`. The P3
        // green primary is outside sRGB; both halves go through `.clamped()`
        // (simple clip, stated in ColorParser) and the frozen ref PNG reads
        // (0,255,0) for BOTH rectangles — so clip-equality is what the test
        // demands. (Chromium gamut-maps; here clip and map coincide on lime.)
        val test = ColorParser.parse("color(display-p3-linear 0 1 0)")
        val ref = ColorParser.parse("lab(86.61399% -106.539 102.871)")
        assertNotNull(test); assertNotNull(ref); assertNotNull(ref.srgb)
        assertSrgbWithin(test.srgb, 0.0, 1.0, 0.0, "-004 test")
        assertSrgbWithin(ref.srgb, 0.0, 1.0, 0.0, "-004 ref")
    }

    @Test
    fun `display-p3-linear-005 yellow resolves to sRGB yellow`() {
        // VERBATIM: `.ref { background-color: yellow; }` /
        // `.test { background-color: color(display-p3-linear 1 1 0.0895); }`
        // "sRGB yellow converted to display-p3-linear".
        val ir = ColorParser.parse("color(display-p3-linear 1 1 0.0895)")
        assertNotNull(ir)
        assertSrgbWithin(ir.srgb, 1.0, 1.0, 0.0, "-005")
    }

    @Test
    fun `display-p3-linear-006 moss green equals its legacy-sRGB ref`() {
        // VERBATIM: `.ref { background-color: rgb(44.8436% 53.537% 28.8112%); }`
        // `.test { background-color: color(display-p3-linear 0.183382 0.245634 0.082317); }`
        // `.test2 { background-color: color(srgb 0.448436 0.53537 0.288113); }`
        // — three spellings of lch(54% 35 118); the ref PNG reads (114,137,73).
        // This is the in-gamut, non-trivial pin: it holds the MATRIX, not just
        // the white point, to one 8-bit step against an independent value.
        val test = ColorParser.parse("color(display-p3-linear 0.183382 0.245634 0.082317)")
        val ref = ColorParser.parse("rgb(44.8436% 53.537% 28.8112%)")
        val test2 = ColorParser.parse("color(srgb 0.448436 0.53537 0.288113)")
        assertNotNull(test); assertNotNull(ref); assertNotNull(test2)
        assertNotNull(ref.srgb); assertNotNull(test2.srgb)
        assertSrgbWithin(test.srgb, ref.srgb.r, ref.srgb.g, ref.srgb.b, "-006 test vs ref")
        assertSrgbWithin(test.srgb, test2.srgb.r, test2.srgb.g, test2.srgb.b, "-006 test vs test2")
        assertSrgbWithin(test.srgb, 114.0 / 255.0, 137.0 / 255.0, 73.0 / 255.0, "-006 vs ref PNG")
    }

    @Test
    fun `the other two linear twins resolve and keep alpha`() {
        // No corpus test spells these today (census: 0 docs); pinned so the
        // arms exist and carry alpha through like every other color() space.
        val a98 = ColorParser.parse("color(a98-rgb-linear 1 1 1 / 0.5)")
        val rec = ColorParser.parse("color(rec2020-linear 0 0 0 / 25%)")
        assertNotNull(a98); assertNotNull(rec)
        assertSrgbWithin(a98.srgb, 1.0, 1.0, 1.0, "a98-rgb-linear")
        assertEquals(0.5, a98.srgb!!.a)
        assertSrgbWithin(rec.srgb, 0.0, 0.0, 0.0, "rec2020-linear")
        assertEquals(0.25, rec.srgb!!.a)
    }

    @Test
    fun `the original representation is untouched — srgb is only ADDED`() {
        // Wire-shape guard: the `original` still carries the colorSpace and the
        // raw values (no byte-shape change, schema/spec/05-versioning.md); the
        // fix adds the `srgb` field the runtimes already read, nothing else.
        val ir = ColorParser.parse("color(display-p3-linear 0.0383 0.2087 0.0156)")
        assertNotNull(ir)
        val repr = ir.representation as IRColor.ColorRepresentation.ColorFunction
        assertEquals("display-p3-linear", repr.colorSpace)
        assertEquals(listOf(0.0383, 0.2087, 0.0156), repr.values)
    }

    @Test
    fun `an unknown color space still leaves srgb null — the runtime-dependent contract`() {
        // `prophoto-rgb(-linear)` has no matrix in ColorConversion yet, so the
        // honest answer stays null (runtime-dependent), exactly as before; the
        // fix widened the table by three named arms, not by a catch-all.
        val ir = ColorParser.parse("color(prophoto-rgb-linear 0.5 0.5 0.5)")
        assertNotNull(ir)
        assertNull(ir.srgb)
    }
}

package app.parsing.css.properties.primitiveParsers

// wave-48 lane W5 — percentage components in lab()/lch()/oklab()/oklch()
// (css-color-4 §8.2/§9.1/§9.2 reference ranges).
//
// Before the fix the chroma group of lchRegex/oklchRegex was `([\d.]+|none)`
// and lab/oklab's a/b groups were `(-?[\d.]+|none)`: a percentage anywhere in
// those slots nulled the WHOLE colour. Measured on wave48-cal css-images:
// every stop of WPT gradient-{in,de}creasing-hue-lch is spelled
// `lch(50% 100% <hue>)`, so both gradients fell to the Raw fallback — the
// web runtime re-emitted the author bytes and passed (1.0000) while both
// natives painted nothing at all (iOS/Android 0.7278 F).
//
// The reference-range table under test (100% equals):
//   lab a/b → ±125   lch C → 150   oklab a/b → ±0.4   oklch C → 0.4
//   ok-space L → 1.0 (the repr stores the CANONICAL number now — the old
//   parse kept `86.64%` as 86.64 and leaned on a `>1 → /100` heuristic in
//   ColorConversion, which a typed-original re-emission would misread)
//
// Every payload below that names a WPT test is the VERBATIM corpus spelling.

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import app.irmodels.IRColor

class ColorParserLchPercentTest {

    // Two spellings of one colour must produce the SAME parse — equivalence
    // pins the % scaling without re-deriving the conversion math here.
    private fun assertSameColor(percentForm: String, numberForm: String) {
        val p = ColorParser.parse(percentForm)
        val n = ColorParser.parse(numberForm)
        assertNotNull(p, "percent form '$percentForm' failed to parse")
        assertNotNull(n, "number form '$numberForm' failed to parse")
        assertEquals(n.representation, p.representation,
            "'$percentForm' must parse to the same representation as '$numberForm'")
        assertEquals(n.srgb, p.srgb,
            "'$percentForm' must resolve to the same srgb as '$numberForm'")
        assertNotNull(p.srgb, "srgb must be computed (natives paint from it)")
    }

    @Test
    fun `lch chroma percentage scales 100 percent to 150`() {
        // VERBATIM stop of WPT css-images gradient-decreasing-hue-lch.
        assertSameColor("lch(50% 100% 0deg)", "lch(50% 150 0deg)")
        assertSameColor("lch(50% 100% 80deg)", "lch(50% 150 80deg)")
        assertSameColor("lch(50% 100% 270deg)", "lch(50% 150 270deg)")
        // Half scale, off the corpus, pins linearity.
        assertSameColor("lch(50% 50% 80deg)", "lch(50% 75 80deg)")
    }

    @Test
    fun `oklch chroma percentage scales 100 percent to 0 point 4`() {
        assertSameColor("oklch(0.7 100% 180)", "oklch(0.7 0.4 180)")
        assertSameColor("oklch(0.7 50% 180)", "oklch(0.7 0.2 180)")
    }

    @Test
    fun `lab and oklab a-b percentages scale to their reference ranges`() {
        // css-color-4 §9.1: lab ±100% = ±125.
        assertSameColor("lab(50% 100% -100%)", "lab(50% 125 -125)")
        // css-color-4 §9.2: oklab ±100% = ±0.4.
        assertSameColor("oklab(0.5 100% -50%)", "oklab(0.5 0.4 -0.2)")
    }

    @Test
    fun `negative chroma clamps to zero at parsed-value time`() {
        // css-color-4 §8.2 — negative chroma is not invalid, it clamps.
        assertSameColor("lch(50% -30 80deg)", "lch(50% 0 80deg)")
        assertSameColor("oklch(0.7 -0.1 180)", "oklch(0.7 0 180)")
    }

    @Test
    fun `ok-space lightness percentage stores the canonical 0-1 number`() {
        // VERBATIM stop of WPT css-images gradient-powerless-hue-oklch.
        val p = ColorParser.parse("oklch(86.64396175234369% 0.295 142.4953450414439 / 0)")
        assertNotNull(p)
        val repr = p.representation as IRColor.ColorRepresentation.OKLCH
        // 86.64…% of the 0..1 reference range — NOT the raw 86.64.
        assertTrue(kotlin.math.abs(repr.l - 0.8664396175234369) < 1e-12,
            "ok-space L% must scale to 0..1, got ${repr.l}")
        assertEquals(0.295, repr.c)
        assertEquals(0.0, repr.alpha)
        // The srgb the natives paint from must still be present.
        assertNotNull(p.srgb)
    }

    @Test
    fun `existing non-percent forms are byte-stable`() {
        // The pre-fix spellings must parse to identical representations —
        // nothing in the regex widening may move a currently-passing value.
        val lch = ColorParser.parse("lch(52.2% 72.2 50)")
        assertNotNull(lch)
        assertEquals(IRColor.ColorRepresentation.LCH(52.2, 72.2, 50.0, 1.0), lch.representation)
        val oklab = ColorParser.parse("oklab(0.7 -0.1 0.15)")
        assertNotNull(oklab)
        assertEquals(IRColor.ColorRepresentation.OKLab(0.7, -0.1, 0.15, 1.0), oklab.representation)
    }
}

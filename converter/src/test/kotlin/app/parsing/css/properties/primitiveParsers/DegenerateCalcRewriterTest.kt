package app.parsing.css.properties.primitiveParsers

// wave-48 lane W5 — DegenerateCalcRewriter (css-values-4 §10.9).
//
// The gradient payloads are the VERBATIM stop spellings of WPT css-images
// gradient-infinity-001/002 (wave48-cal per-test-ir carried the whole
// declarations as `{raw: …}`; both natives painted nothing, 0.9171/0.9147).
// 33554400px is the used value the capture browser itself serializes for an
// infinite length (Chromium's LayoutUnit clamp).

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DegenerateCalcRewriterTest {

    @Test
    fun `division by literal zero folds to the positive clamp`() {
        // VERBATIM: linear-gradient(to right in srgb, lime 100px, red calc(1px / 0))
        assertEquals(
            "linear-gradient(to right in srgb, lime 100px, red 33554400px)",
            DegenerateCalcRewriter.rewrite("linear-gradient(to right in srgb, lime 100px, red calc(1px / 0))"),
        )
    }

    @Test
    fun `infinity keyword folds in either operand order`() {
        // VERBATIM: … red calc(Infinity * 1px)
        assertEquals(
            "linear-gradient(to right in srgb, lime 100px, red 33554400px)",
            DegenerateCalcRewriter.rewrite("linear-gradient(to right in srgb, lime 100px, red calc(Infinity * 1px))"),
        )
        // Commuted form + case-insensitive keyword (CSS Syntax L3 §4.3).
        assertEquals("a 33554400px", DegenerateCalcRewriter.rewrite("a calc(1px * infinity)"))
    }

    @Test
    fun `sign follows the numerator and the keyword`() {
        // §10.9: -1px/0 → −∞; -Infinity * 1px → −∞; the signs XOR.
        assertEquals("x -33554400px", DegenerateCalcRewriter.rewrite("x calc(-1px / 0)"))
        assertEquals("x -33554400px", DegenerateCalcRewriter.rewrite("x calc(-Infinity * 1px)"))
        assertEquals("x 33554400px", DegenerateCalcRewriter.rewrite("x calc(-Infinity * -2px)"))
    }

    @Test
    fun `anything else refuses — author bytes stay pure for the Raw route`() {
        // No calc at all → null (caller keeps its value; nothing rewrote).
        assertNull(DegenerateCalcRewriter.rewrite("linear-gradient(red, blue)"))
        // Finite math is NOT ours.
        assertNull(DegenerateCalcRewriter.rewrite("red calc(1px / 2)"))
        // 0/0 would be NaN — a different §10.9 clause, refused.
        assertNull(DegenerateCalcRewriter.rewrite("red calc(0px / 0)"))
        // A relative unit's magnitude story belongs to the runtimes.
        assertNull(DegenerateCalcRewriter.rewrite("red calc(1em / 0)"))
        // ALL-OR-NOTHING: one degenerate + one finite calc → whole value refused,
        // so a partial rewrite can never leak into the Raw fallback bytes.
        assertNull(DegenerateCalcRewriter.rewrite("red calc(1px / 0) blue calc(1px + 1px)"))
        // var() and nesting refuse likewise.
        assertNull(DegenerateCalcRewriter.rewrite("red calc(var(--x) / 0)"))
        assertNull(DegenerateCalcRewriter.rewrite("red calc(calc(1px) / 0)"))
    }

    // ── S6 must-fix 1 pins — the rewriter must never see payload bytes ──
    // The first W5 cut ran DegenerateCalcRewriter over the WHOLE declaration
    // before tokenization, corrupting url()/quoted-string/data-URI payload
    // bytes (executed S6 proof: url('calc(1px / 0).png') → ["33554400px.png"]).
    // These pins go through BackgroundImagePropertyParser.parse — the repaired
    // integration point that now gates the rewriter to gradient layers only —
    // on the VERBATIM S6 probe payloads (scratchpad wave48/S6/probe2.json Q03/
    // Q05, probe4.json R2). All still-calc-bearing values must route to Raw
    // with byte-verbatim author input, exactly as before wave-48 lane W5.

    // Shorthand: parse one background-image value via the repaired parser.
    private fun parseBg(css: String) =
        app.parsing.css.properties.longhands.background.BackgroundImagePropertyParser.parse(css)
            as app.irmodels.properties.background.BackgroundImageProperty

    @Test
    fun `url payload containing a degenerate calc survives byte-verbatim`() {
        // VERBATIM S6 probe4 R2: the calc is FILENAME bytes, not math. The
        // surviving calc( trips the expression gate → one Raw layer whose
        // bytes are the untouched author input (no 33554400px anywhere).
        val css = "url('calc(1px / 0).png')"
        val raw = parseBg(css).images.single()
            as app.irmodels.properties.background.BackgroundImageProperty.BackgroundImage.Raw
        assertEquals(css, raw.value)
    }

    @Test
    fun `data URI payload containing a degenerate calc survives byte-verbatim`() {
        // VERBATIM S6 probe2 Q05: an SVG data URI whose width attribute
        // spells calc(1px / 0). A rewrite here would corrupt the DOCUMENT
        // ENCODED IN THE URI; the bytes must survive untouched into Raw.
        val css = """url('data:image/svg+xml;utf8,<svg><rect width="calc(1px / 0)"/></svg>')"""
        val raw = parseBg(css).images.single()
            as app.irmodels.properties.background.BackgroundImageProperty.BackgroundImage.Raw
        assertEquals(css, raw.value)
    }

    @Test
    fun `image() quoted-string payload containing a degenerate calc survives byte-verbatim`() {
        // VERBATIM S6 probe2 Q03: image() notation (css-images-4 §2.5) with a
        // <string> src whose bytes spell calc(1px / 0). Previously the srcs
        // came out corrupted ("33554400px.png"); now the surviving calc(
        // routes the whole value to Raw with pure author bytes.
        val css = "image('calc(1px / 0).png', green)"
        val raw = parseBg(css).images.single()
            as app.irmodels.properties.background.BackgroundImageProperty.BackgroundImage.Raw
        assertEquals(css, raw.value)
    }

    @Test
    fun `gradient layer folds while the url layer beside it survives byte-verbatim`() {
        // Positive pin (prescription): per-layer gating must still FOLD a
        // gradient layer (css-values-4 §10.9 clamp → typed path) while the
        // url layer next to it keeps its case-sensitive payload bytes.
        // Adapted from S6 probe2 Q07 with a calc-free url payload so the
        // folded declaration passes the expression gate into the TYPED path.
        val prop = parseBg("url('CaseSensitive.png'), linear-gradient(to right, lime 0px, red calc(1px / 0))")
        // Layer 1: a typed Url whose payload bytes are the author's, verbatim.
        val url = prop.images[0]
            as app.irmodels.properties.background.BackgroundImageProperty.BackgroundImage.Url
        assertEquals("CaseSensitive.png", url.url.url)
        // Layer 2: the gradient took the typed path with the folded clamp
        // stop (Chromium's LayoutUnit max, 33554400px) — not a Raw fallback.
        val grad = prop.images[1]
            as app.irmodels.properties.background.BackgroundImageProperty.BackgroundImage.LinearGradient
        assertEquals(33554400.0, grad.colorStops[1].positionLength?.pixels)
    }

    @Test
    fun `url layer with calc payload beside a foldable gradient goes Raw with the url bytes intact`() {
        // VERBATIM S6 probe2 Q07: the gradient layer folds, but the url
        // layer's payload still carries calc( bytes, so the expression gate
        // fires on the REWRITTEN form → one whole-value Raw in which the url
        // layer survives byte-verbatim while the gradient layer is folded
        // (valid CSS, pixel-identical — the web runtime re-emits it).
        val css = "url('calc(1px / 0).png'), linear-gradient(to right, lime 0px, red calc(1px / 0))"
        val raw = parseBg(css).images.single()
            as app.irmodels.properties.background.BackgroundImageProperty.BackgroundImage.Raw
        assertEquals("url('calc(1px / 0).png'), linear-gradient(to right, lime 0px, red 33554400px)", raw.value)
    }

    @Test
    fun `whole-declaration integration takes the typed gradient path`() {
        // The end-to-end claim: the gradient-infinity payload now parses into
        // a typed LinearGradient whose second stop carries the clamp length —
        // the shape all three runtimes render (the natives previously got Raw).
        val prop = app.parsing.css.properties.longhands.background.BackgroundImagePropertyParser
            .parse("linear-gradient(to right in srgb, lime 100px, red calc(1px / 0))")
        val bg = prop as app.irmodels.properties.background.BackgroundImageProperty
        val layer = bg.images.single()
        val grad = layer as app.irmodels.properties.background.BackgroundImageProperty.BackgroundImage.LinearGradient
        assertEquals(2, grad.colorStops.size)
        assertEquals(33554400.0, grad.colorStops[1].positionLength?.pixels)
        assertEquals("in srgb", grad.interp)
    }
}

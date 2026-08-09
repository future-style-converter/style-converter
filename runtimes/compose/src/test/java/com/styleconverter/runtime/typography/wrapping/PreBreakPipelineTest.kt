package com.styleconverter.runtime.typography.wrapping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

// PreBreakPipeline pin table — applier campaign wave 38, lane N8.
//
// The pipeline is the DECISION half of Compose's css-text-3 §5.5 rule B:
// which runs get pre-broken (and therefore rendered with softWrap off),
// and — far more importantly for the frozen corpus — which do not. Every
// decline is pinned by IDENTITY (`assertSame`), because "returns the input
// instance" is exactly the property that makes the 327-pair dark stage and
// the untouched WPT rows byte-stable by construction.
//
// Metrics as in GreedyLineBreakerTest: Chromium's monospace advance at the
// css-text/hyphens family's 32px font-size.
class PreBreakPipelineTest {

    private val CH = 19.2f
    private val mono: (String) -> Float = { s -> s.length * CH }

    /** The fixture string of css-text/hyphens-manual-010 + -none-011. */
    private val dna = "Deoxyribonucleic acid"

    /** All gates open, 10ch box — the configuration the two 0.7500 tests
     *  present. Every decline pin below flips exactly one argument. */
    private fun fire(
        text: String = dna,
        wrapWidthPx: Float = 10 * CH,
        enabled: Boolean = true,
        softWrapAllowed: Boolean = true,
        allowMidWordBreak: Boolean = false,
        preservesSpaces: Boolean = false
    ) = PreBreakPipeline.preBreak(
        text = text,
        wrapWidthPx = wrapWidthPx,
        enabled = enabled,
        softWrapAllowed = softWrapAllowed,
        allowMidWordBreak = allowMidWordBreak,
        preservesSpaces = preservesSpaces,
        measure = mono
    )

    // ── the firing case ──────────────────────────────────────────────

    /** The measured defect: Chromium keeps all 16 glyphs of
     *  "Deoxyribonucleic" on one overflowing line, Minikin
     *  character-breaks it. The pipeline hands back the CSS lines with a
     *  HARD newline between them — the caller pairs that with
     *  `softWrap = false`, which is the only switch that stops Minikin's
     *  emergency break. */
    @Test
    fun ruleBFiresOnTheOverlongUnbreakableWord() {
        val r = fire()
        assertTrue(r.fired)
        assertEquals("Deoxyribonucleic\nacid", r.text)
    }

    /** The wrap has to be REAL: at a box wide enough for the whole run
     *  nothing overflows, so the platform keeps ownership. */
    @Test
    fun aRunThatFitsIsUntouched() {
        val r = fire(wrapWidthPx = 40 * CH)
        assertFalse(r.fired)
        assertSame(dna, r.text)
    }

    /** An ordinary sentence that merely WRAPS is untouched — the platform
     *  breaks it greedily at spaces exactly like Chromium, and rewriting
     *  those breaks would put every wrapping row in the corpus at risk. */
    @Test
    fun anOrdinaryWrappingRunIsUntouched() {
        val text = "the characters inside of each black bordered rectangle"
        val r = fire(text = text, wrapWidthPx = 10 * CH)
        assertFalse(r.fired)
        assertSame(text, r.text)
    }

    // ── the gates, one per decline ───────────────────────────────────

    /** THE baseline guard: outside composed WPT capture the pipeline is
     *  inert, so no committed 327-pair capture can move. */
    @Test
    fun disabledOutsideComposedWptCapture() {
        val r = fire(enabled = false)
        assertFalse(r.fired)
        assertSame(dna, r.text)
    }

    /** `white-space: nowrap|pre` / `text-wrap: nowrap` already lay the run
     *  out on one line — there is no wrap to repair. */
    @Test
    fun declinesWhenWrappingIsOff() {
        assertSame(dna, fire(softWrapAllowed = false).text)
    }

    /** `overflow-wrap: break-word|anywhere` and `word-break: break-all`
     *  ASK for the mid-word break (css-text-3 §5.5), so Minikin is right
     *  and must keep the run. */
    @Test
    fun declinesWhenCssOptedIntoTheEmergencyBreak() {
        assertSame(dna, fire(allowMidWordBreak = true).text)
    }

    /** `pre-wrap` / `break-spaces` preserve space runs (css-text-3
     *  §4.1.2); the space-split breaker would collapse them, which is a
     *  glyph-content rewrite rather than a wrap fix. */
    @Test
    fun declinesForPreservedWhitespaceModes() {
        assertSame(dna, fire(preservesSpaces = true).text)
    }

    /** No wrap width known — an unbounded proposal, or the first
     *  composition before the latch has observed one. Declining keeps
     *  frame 1 byte-identical to the frozen behaviour. */
    @Test
    fun declinesUntilTheWrapWidthIsKnown() {
        assertSame(dna, fire(wrapWidthPx = -1f).text)
        assertSame(dna, fire(wrapWidthPx = 0f).text)
    }

    /** A run with NO space is the WHOLE-RUN case wave 21's B-RC7 gate
     *  already owns (softWrap off when `hasSoftWrapOpportunity` is false).
     *  One owner per case: the pipeline stands down so those captures stay
     *  byte-identical. */
    @Test
    fun declinesForASpacelessRunTheWholeRunGateOwns() {
        val text = "Deoxyribonucleic"
        assertSame(text, fire(text = text).text)
    }

    /** hyphens-none-012: the overlong "word" carries a hyphen-minus, so it
     *  is breakable after all and the reference DOES wrap it. Rule B must
     *  not fire — see GreedyLineBreakerTest for the measured regression
     *  the per-line veto prevents. */
    @Test
    fun declinesWhenTheOverlongWordIsHyphenated() {
        val text = "regu-lation imple-menta-tion now"
        val r = fire(text = text, wrapWidthPx = 6 * CH)
        assertFalse(r.fired)
        assertSame(text, r.text)
    }

    /** Rule A composes with rule B: `hyphens: none` deletes U+00AD BEFORE
     *  this pipeline sees the string (SoftHyphenPolicy runs first at the
     *  call site), so hyphens-none-011's authored
     *  `Deoxy&shy;ribo&shy;nucleic` arrives as the bare word and fires
     *  exactly like hyphens-manual-010's. Left in place — i.e. under
     *  `manual` — the soft hyphens are real opportunities and the veto
     *  stands the pipeline down, which is also correct. */
    @Test
    fun composesWithTheSoftHyphenPolicy() {
        val authored = "Deoxy­ribo­nucleic acid"
        // hyphens: none → stripped first → fires with the CSS lines.
        val stripped = SoftHyphenPolicy.displayString(authored, "none")
        val none = fire(text = stripped)
        assertTrue(none.fired)
        assertEquals("Deoxyribonucleic\nacid", none.text)
        // hyphens: manual → the conditional characters survive and ARE
        // break opportunities, so the run stays the platform's.
        val manual = fire(text = SoftHyphenPolicy.displayString(authored, "manual"))
        assertFalse(manual.fired)
    }
}

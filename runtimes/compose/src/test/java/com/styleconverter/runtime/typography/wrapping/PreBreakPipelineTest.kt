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
     *  `manual` — the soft hyphens are opportunities the wave-41 fold
     *  SPENDS itself (`Deoxyribo‐`/`nucleic`/`acid`, every line fitting).
     *  Until wave 52 that meant "no rule-B claim, decline" — and Minikin,
     *  which ignores U+00AD under Hyphens.None, then desperate-broke the
     *  word with NO hyphen glyph (hyphens-manual-011 android P 0.9811
     *  paints `Deoxyribo` / `nucleic`, hyphen-less; the ref paints the
     *  hyphen). Wave 52 (lane L9, F5) makes the TAKEN soft hyphen a second
     *  trigger (css-text-3 §5.3), so the `manual` half now FIRES with the
     *  measured lines — hyphens-manual-011's capture is expected to move
     *  ref-ward (its hyphen appears); the flip is pre-registered in
     *  tools/titan/results/wave52-plan/watchlist.txt. */
    @Test
    fun composesWithTheSoftHyphenPolicy() {
        val authored = "Deoxy­ribo­nucleic acid"
        // hyphens: none → stripped first → fires with the CSS lines.
        val stripped = SoftHyphenPolicy.displayString(authored, "none")
        val none = fire(text = stripped)
        assertTrue(none.fired)
        assertEquals("Deoxyribonucleic\nacid", none.text)
        // hyphens: manual → the conditional characters survive; the fold
        // takes the greedy one (after `ribo`, 10ch with the hyphen) and
        // every line fits — no rule-B claim, but a soft hyphen was TAKEN,
        // so the wave-52 trigger fires with the hyphen materialised.
        val manual = fire(text = SoftHyphenPolicy.displayString(authored, "manual"))
        assertTrue(manual.fired)
        assertEquals("Deoxyribo‐\nnucleic\nacid", manual.text)
    }

    // ── Wave 52 (lane L9, F5) — the TAKEN-SOFT-HYPHEN trigger ────────
    //
    // MUTATION PROOF (executed 2026-10-05, lane L9, mutate.py M2/M3 —
    // tools/titan/results/wave52-inline-run-wall/mutations-compose.result
    // .json): M2 `val tookSoftHyphen = false` → the -012 pin below and the
    // `manual` half of composesWithTheSoftHyphenPolicy fail (identity
    // returned where `fired` is asserted); M3 the `!dictionaryHyphenation`
    // gate removed → the dictionary pin fails (fired where identity is
    // asserted). Restored byte-exact (sha256-verified) after each run.

    /** css-text/hyphens/hyphens-manual-inline-012's paragraph, VERBATIM
     *  off wave51-fix/sections/css-text/per-test-ir: the host's runs
     *  `DNA ` + span(`means Deo&shy;xy&shy;ribo&shy;nu&shy;cleic acid`,
     *  hyphens: manual) + `.`, folded by InlineRunFold into one string,
     *  in an 8ch monospace-32px box. */
    private val MANUAL_INLINE_012 = "DNA means Deo­xy­ribo­nu­cleic acid."

    /** The measured defect: Minikin broke the 16-glyph word every 7 glyphs
     *  with no hyphen (`Deoxyri` / `bonucle` / `ic`, android f 0.9419); the
     *  ref — and iOS, which pre-breaks every run — paint
     *  `Deoxy-` / `ribonu-` / `cleic`. Nothing overflows here (every line
     *  fits 8ch), so rule B alone never fired; the taken soft hyphens do. */
    @Test
    fun firesWhenASoftHyphenBreakWasTaken() {
        val r = fire(text = MANUAL_INLINE_012, wrapWidthPx = 8 * CH)
        assertTrue(r.fired)
        assertEquals("DNA\nmeans\nDeoxy‐\nribonu‐\ncleic\nacid.", r.text)
    }

    /** `hyphens: auto` with a language tag hands the opportunities to
     *  Minikin's dictionary (AutoHyphenation) — the soft-hyphen trigger
     *  stays out of such runs even when the fold took one, or firing would
     *  hard-newline the run with softWrap OFF and suppress the
     *  hyphenation that was just switched on. */
    @Test
    fun theSoftHyphenTriggerStaysOutOfDictionaryRuns() {
        val r = PreBreakPipeline.preBreak(
            text = MANUAL_INLINE_012, wrapWidthPx = 8 * CH, enabled = true,
            softWrapAllowed = true, allowMidWordBreak = false, preservesSpaces = false,
            dictionaryHyphenation = true, measure = mono)
        assertFalse(r.fired)
        assertSame(MANUAL_INLINE_012, r.text)
        // hyphens-auto-010's wire (`regulation implementation now`, 6ch,
        // `hyphens: auto`, lang en): the overlong words are dictionary
        // opportunities, so neither trigger claims the run — identity.
        val auto010 = "regulation implementation now"
        val a = PreBreakPipeline.preBreak(
            text = auto010, wrapWidthPx = 6 * CH, enabled = true,
            softWrapAllowed = true, allowMidWordBreak = false, preservesSpaces = false,
            dictionaryHyphenation = true, measure = mono)
        assertSame(auto010, a.text)
    }

    /** A soft hyphen the fold did NOT take (the word fits) changes nothing
     *  — identity, so a wide box keeps its frozen capture. */
    @Test
    fun anUntakenSoftHyphenDoesNotFire() {
        val r = fire(text = MANUAL_INLINE_012, wrapWidthPx = 40 * CH)
        assertFalse(r.fired)
        assertSame(MANUAL_INLINE_012, r.text)
    }

    // ── Wave 52 (lane L9, F4) — the drawn-marker CLAMP on a fired run ──
    //
    // MUTATION PROOF (executed 2026-10-05, lane L9, mutate.py M5 —
    // mutations-compose.result.json): with the fired result built from the
    // untrimmed `lines` instead of `clamped`, the 025 pin below fails (the
    // untrimmed paragraph where 4 lines + "…" are asserted). Restored
    // byte-exact (sha256-verified).

    /** block-ellipsis-025, the Compose arm F3 cannot reach: the 34ch word
     *  fires rule B (an unbreakable overflow), the run renders with
     *  softWrap = false, and there TextOverflow.Ellipsis is the wave-39
     *  landmine — so the marker is BAKED into the fired string instead:
     *  four lines, the fourth the UA ellipsis alone (the ref's picture). */
    @Test
    fun aFiredRunUnderADrawnMarkerClampBakesTheEllipsis() {
        val text = "Test passes if there are 4 lines and the last one only contains … " +
            "supercalifragilisticexpialidocious supercalifragilisticexpialidocious Test fails: this should not be visible"
        val r = PreBreakPipeline.preBreak(
            text = text, wrapWidthPx = 32.5f * CH, enabled = true,
            softWrapAllowed = true, allowMidWordBreak = false, preservesSpaces = false,
            clampLines = 4, measure = mono)
        assertTrue(r.fired)
        assertEquals(
            "Test passes if there are 4 lines\nand the last one only contains …\n" +
                "supercalifragilisticexpialidocious\n…",
            r.text
        )
    }

    /** The clamp is consulted ONLY on a fired run: an ordinary wrapping
     *  paragraph under `line-clamp: 1` stays Minikin's (identity) and gets
     *  its marker from the placeholder's overflow decision instead — one
     *  owner per case, the frozen wrap positions untouched. */
    @Test
    fun theClampAloneNeverFires() {
        val text = "This text is left-aligned        Clamped"
        val r = PreBreakPipeline.preBreak(
            text = text, wrapWidthPx = 29 * CH, enabled = true,
            softWrapAllowed = true, allowMidWordBreak = false, preservesSpaces = false,
            clampLines = 1, measure = mono)
        assertFalse(r.fired)
        assertSame(text, r.text)
    }
}

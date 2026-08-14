package com.styleconverter.runtime.typography.wrapping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// GreedyLineBreaker pin table — applier campaign wave 38, lane N8.
//
// TWIN of the Swift GreedyLineBreaker (StyleEngine/typography/): same
// fold, same "first word always opens the line" rule, same per-line
// unbreakable-overflow probe. The Swift suite pins the iOS side of the
// same table; change one, change both.
//
// The measurer is INJECTED, so these pins need no font, no device and no
// Compose runtime — they pin the ARITHMETIC. MONO is the css-text/hyphens
// family's own metric: `font-family: monospace; font-size: 32px` in
// Chromium advances 19.2px per character, so a `width: 10ch` content box
// is 192px and a `width: 6ch` one is 115.2px. The strings are the WPT
// fixtures verbatim (tools/wpt/css/css-text/hyphens/).
class GreedyLineBreakerTest {

    /** Chromium's monospace advance at the family's 32px font-size. */
    private val CH = 19.2f

    /** Uniform monospace measurer — advance is purely character count. */
    private val mono: (String) -> Float = { s -> s.length * CH }

    // ── the fold ─────────────────────────────────────────────────────

    /** css-text-3 §5: greedy, no look-ahead. Three words that fit two,
     *  then one, land 2+1 — never balanced. */
    @Test
    fun greedyFoldCommitsAsSoonAsTheCandidateStopsFitting() {
        // "aa bb cc" at 6ch: "aa bb" = 5ch fits, "aa bb cc" = 8ch does not.
        assertEquals(
            listOf("aa bb", "cc"),
            GreedyLineBreaker.lines("aa bb cc", 6 * CH, mono)
        )
    }

    /** hyphens-manual-010 / hyphens-none-011, the measured defect: the
     *  16-character word does not fit the 10ch box and CSS 2.1 §9.5 leaves
     *  it ALONE on its line to overflow — `acid` still moves to line 2. */
    @Test
    fun overlongWordStaysAloneOnItsLineAndOverflows() {
        assertEquals(
            listOf("Deoxyribonucleic", "acid"),
            GreedyLineBreaker.lines("Deoxyribonucleic acid", 10 * CH, mono)
        )
    }

    /** The first word ALWAYS opens the line, even when it alone overflows
     *  — a committed line is never empty. */
    @Test
    fun firstWordOpensTheLineEvenWhenItCannotFit() {
        assertEquals(
            listOf("Deoxyribonucleic"),
            GreedyLineBreaker.lines("Deoxyribonucleic", 3 * CH, mono)
        )
    }

    /** Hard newlines are paragraph boundaries: each wraps on its own, and
     *  the count is preserved so downstream line-box math stays exact. */
    @Test
    fun hardNewlinesSplitParagraphsThatWrapIndependently() {
        assertEquals(
            listOf("aa bb", "cc", "dd"),
            GreedyLineBreaker.lines("aa bb cc\ndd", 6 * CH, mono)
        )
    }

    /** Runs of spaces collapse to one separator (Swift's
     *  `split(separator:)` omits empty subsequences — the Kotlin filter
     *  is what keeps the twins identical), and an all-space paragraph
     *  still yields one empty visual line. */
    @Test
    fun spaceRunsCollapseAndAnAllSpaceParagraphKeepsItsLine() {
        assertEquals(listOf("aa bb"), GreedyLineBreaker.lines("aa   bb", 9 * CH, mono))
        assertEquals(listOf("", "aa"), GreedyLineBreaker.lines("   \naa", 9 * CH, mono))
    }

    /** A candidate is measured as ONE string, so the separator's own
     *  advance counts against the fit — 'aa bb' is 5ch, not 4ch. */
    @Test
    fun theSeparatorAdvanceCountsAgainstTheFit() {
        // 4ch of glyphs + 1ch of space: fits at 5ch, not at 4ch.
        assertEquals(listOf("aa bb"), GreedyLineBreaker.lines("aa bb", 5 * CH, mono))
        assertEquals(listOf("aa", "bb"), GreedyLineBreaker.lines("aa bb", 4 * CH, mono))
    }

    // ── the rule-B probe ─────────────────────────────────────────────

    /** The trigger: a committed line wider than the box with no soft-wrap
     *  opportunity inside it. */
    @Test
    fun unbreakableOverflowingLineIsDetected() {
        val lines = GreedyLineBreaker.lines("Deoxyribonucleic acid", 10 * CH, mono)
        assertTrue(
            GreedyLineBreaker.hasUnbreakableOverflowingLine(lines, 10 * CH, measure = mono)
        )
    }

    /** Every line fitting ⇒ no trigger: the platform's own greedy
     *  breaking already agrees with CSS and must be left alone. */
    @Test
    fun fittingLinesDoNotTrigger() {
        val lines = GreedyLineBreaker.lines("aa bb cc", 6 * CH, mono)
        assertFalse(
            GreedyLineBreaker.hasUnbreakableOverflowingLine(lines, 6 * CH, measure = mono)
        )
    }

    /** hyphens-none-012, the wave-41 fold. `regu-lation imple-menta-tion
     *  now` at 6ch: a U+002D hyphen-minus (UAX #14 class HY) is a
     *  break-AFTER opportunity `hyphens` does not govern, and the fold
     *  now takes it itself — the Chromium ref's exact six lines,
     *  including `imple-` via the overflow fallback (at 6ch the head
     *  fits exactly; see WordBreakOpportunitiesTest for the narrower
     *  case). Wave 38's fold left these words WHOLE and relied on the
     *  per-line veto; the veto is still pinned below on the op classes
     *  the fold deliberately does NOT model. */
    @Test
    fun theFoldBreaksAfterLiteralHyphens() {
        val text = "regu-lation imple-menta-tion now"
        assertEquals(
            listOf("regu-", "lation", "imple-", "menta-", "tion", "now"),
            GreedyLineBreaker.lines(text, 6 * CH, mono)
        )
        // Every line fits, so rule B has nothing to claim.
        assertFalse(
            GreedyLineBreaker.hasUnbreakableOverflowingLine(
                GreedyLineBreaker.lines(text, 6 * CH, mono), 6 * CH, measure = mono)
        )
    }

    /** hyphens-none-013 is the same fixture with U+2010 HYPHEN instead of
     *  the ASCII one — the analyzer carves both in, so the fold output is
     *  the same shape. */
    @Test
    fun theFoldAlsoBreaksAfterTheUnicodeHyphen() {
        val text = "regu‐lation imple‐menta‐tion now"
        assertEquals(
            listOf("regu‐", "lation", "imple‐", "menta‐", "tion", "now"),
            GreedyLineBreaker.lines(text, 6 * CH, mono)
        )
    }

    /** THE VETO still exists for the opportunity classes the fold does
     *  NOT model (en/em dashes, ZWSP, ideographs): such a line stays
     *  whole, overflows, and must not claim rule B — the platform
     *  breaker owns those breaks (see DecorationOps.hasSoftWrapOpportunity,
     *  the shared UAX #14 approximation). */
    @Test
    fun anUnmodeledDashKeepsTheVeto() {
        val text = "regu–lation" // U+2013 EN DASH — not an analyzer op.
        val lines = GreedyLineBreaker.lines(text, 6 * CH, mono)
        // The fold leaves the word whole and overflowing…
        assertEquals(listOf(text), lines)
        assertTrue(mono(text) > 6 * CH)
        // …but the dash is a real platform opportunity, so no claim.
        assertFalse(
            GreedyLineBreaker.hasUnbreakableOverflowingLine(lines, 6 * CH, measure = mono)
        )
    }

    /** The soft-hyphen half of the wave-41 fold: hyphens-manual-013's
     *  `Deoxy&shy;ribonucleic acid` at 10ch. The shy is a conditional
     *  hyphenation point (css-text-3 §6.1): taking it paints U+2010 and
     *  the mark itself never reaches the output; the unbreakable tail
     *  `ribonucleic` overflows (CSS 2.1 §9.5) and DOES claim rule B —
     *  which is exactly what lets PreBreakPipeline fire and render the
     *  ref's `Deoxy-`/`ribonucleic`/`acid` instead of Minikin's
     *  hyphen-less character break (wave40-final android-ref 0.9458). */
    @Test
    fun aSoftHyphenIsTakenAndPaintsTheHyphenCharacter() {
        val text = "Deoxy­ribonucleic acid"
        val lines = GreedyLineBreaker.lines(text, 10 * CH, mono)
        assertEquals(listOf("Deoxy‐", "ribonucleic", "acid"), lines)
        // The tail is genuinely unbreakable and overflowing → rule B.
        assertTrue(
            GreedyLineBreaker.hasUnbreakableOverflowingLine(lines, 10 * CH, measure = mono)
        )
    }

    /** hyphens-manual-011 (`Deoxy&shy;ribo&shy;nucleic acid`, 10ch): the
     *  fold is GREEDY over shy points — the LAST one that fits wins
     *  (`Deoxyribo-` is exactly 10ch with its painted hyphen) — and with
     *  every line fitting, rule B stays quiet: PreBreakPipeline declines
     *  and Minikin keeps the run, so the committed wave-40 capture of
     *  that test cannot move. */
    @Test
    fun shySplitsAreGreedyAndAFittingResultStaysMinikinOwned() {
        val text = "Deoxy­ribo­nucleic acid"
        val lines = GreedyLineBreaker.lines(text, 10 * CH, mono)
        assertEquals(listOf("Deoxyribo‐", "nucleic", "acid"), lines)
        assertFalse(
            GreedyLineBreaker.hasUnbreakableOverflowingLine(lines, 10 * CH, measure = mono)
        )
    }

    /** The tolerance absorbs sub-pixel rounding between the float fit test
     *  and the integer width the layout proposes: a line measuring exactly
     *  the wrap width, or a half pixel over it, is NOT an overflow. */
    @Test
    fun halfPixelOverIsNotAnOverflow() {
        val exact: (String) -> Float = { 192.0f }
        assertFalse(
            GreedyLineBreaker.hasUnbreakableOverflowingLine(listOf("x"), 192f, measure = exact)
        )
        val halfOver: (String) -> Float = { 192.5f }
        assertFalse(
            GreedyLineBreaker.hasUnbreakableOverflowingLine(listOf("x"), 192f, measure = halfOver)
        )
        val over: (String) -> Float = { 192.6f }
        assertTrue(
            GreedyLineBreaker.hasUnbreakableOverflowingLine(listOf("x"), 192f, measure = over)
        )
    }
}

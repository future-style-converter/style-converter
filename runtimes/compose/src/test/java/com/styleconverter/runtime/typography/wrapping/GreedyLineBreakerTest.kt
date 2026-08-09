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

    /** THE VETO, and why it exists. hyphens-none-012's box is
     *  `regu-lation imple-menta-tion now` at 6ch: this breaker splits on
     *  SPACES only, so `imple-menta-tion` is one 16-char "word" that
     *  overflows — but its U+002D hyphen-minus (UAX #14 class HY) IS a
     *  break opportunity, and there the platform breaker is RIGHT
     *  (`hyphens` does not govern it). Firing here would stop a wrap the
     *  reference performs; iOS measured that regression at 0.8548 → 0.8431
     *  before adding this per-line veto. */
    @Test
    fun aHyphenatedOverlongWordIsNotUnbreakable() {
        val text = "regu-lation imple-menta-tion now"
        val lines = GreedyLineBreaker.lines(text, 6 * CH, mono)
        // The greedy fold does leave overflowing lines here…
        assertTrue(lines.any { mono(it) > 6 * CH })
        // …but none of them is unbreakable, so rule B must not fire.
        assertFalse(
            GreedyLineBreaker.hasUnbreakableOverflowingLine(lines, 6 * CH, measure = mono)
        )
    }

    /** hyphens-none-013 is the same fixture with U+2010 HYPHEN instead of
     *  the ASCII one — the shared UAX #14 approximation carves both in, so
     *  the veto holds identically. */
    @Test
    fun theVetoAlsoCoversTheUnicodeHyphen() {
        val text = "regu‐lation imple‐menta‐tion now"
        val lines = GreedyLineBreaker.lines(text, 6 * CH, mono)
        assertFalse(
            GreedyLineBreaker.hasUnbreakableOverflowingLine(lines, 6 * CH, measure = mono)
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

package com.styleconverter.runtime.typography.wrapping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// WordBreakOpportunities pin table — applier campaign wave 41, lane T3.
//
// TWIN of the Swift WordBreakOpportunitiesTests: same analyzer table,
// same split walk, same overflow-fallback rule. Change one, change both.
//
// Metrics as in GreedyLineBreakerTest: Chromium's monospace advance at
// the css-text/hyphens family's 32px font-size (19.2px per character),
// so widths read as character counts.
class WordBreakOpportunitiesTest {

    private val CH = 19.2f
    private val mono: (String) -> Float = { s -> s.length * CH }

    // ── the analyzer ─────────────────────────────────────────────────

    /** A mark-free word decomposes to itself with no ops — the identity
     *  that keeps the wave-38 fold decisions byte-stable. */
    @Test
    fun aPlainWordHasNoOps() {
        val w = WordBreakOpportunities.analyze("Deoxyribonucleic")
        assertEquals("Deoxyribonucleic", w.text)
        assertTrue(w.ops.isEmpty())
    }

    /** css-text-3 §5.3: each U+00AD is REMOVED from the display text and
     *  recorded as a hyphenation op (paintsHyphen) at its position in the
     *  cleaned word. */
    @Test
    fun softHyphensAreStrippedAndRecorded() {
        val w = WordBreakOpportunities.analyze("Deoxy­ribo­nucleic")
        assertEquals("Deoxyribonucleic", w.text)
        assertEquals(
            listOf(
                WordBreakOpportunities.Op(5, paintsHyphen = true),
                WordBreakOpportunities.Op(9, paintsHyphen = true)
            ),
            w.ops
        )
    }

    /** UAX #14 class HY/BA: a break-after op behind U+002D and U+2010;
     *  the glyph stays in the text and paints nothing extra. */
    @Test
    fun literalHyphensRecordBreakAfterOps() {
        val w = WordBreakOpportunities.analyze("imple-menta‐tion")
        assertEquals("imple-menta‐tion", w.text)
        assertEquals(
            listOf(
                WordBreakOpportunities.Op(6, paintsHyphen = false),
                WordBreakOpportunities.Op(12, paintsHyphen = false)
            ),
            w.ops
        )
    }

    /** UAX #14 forbids HY × NU — a numeric range like `1-2` keeps its
     *  halves together — and a word-final hyphen has no tail to move. A
     *  word-initial soft hyphen has no head to break off. */
    @Test
    fun digitsFinalHyphensAndInitialShysDoNotBreak() {
        assertTrue(WordBreakOpportunities.analyze("1-2").ops.isEmpty())
        assertTrue(WordBreakOpportunities.analyze("word-").ops.isEmpty())
        assertTrue(WordBreakOpportunities.analyze("­word").ops.isEmpty())
    }

    /** A soft hyphen authored right after a literal one collapses into
     *  the literal op — the break lands in one place and nothing extra
     *  is painted. */
    @Test
    fun aShyAfterALiteralHyphenCollapsesIntoIt() {
        val w = WordBreakOpportunities.analyze("foo-­bar")
        assertEquals("foo-bar", w.text)
        assertEquals(listOf(WordBreakOpportunities.Op(4, paintsHyphen = false)), w.ops)
    }

    // ── the split walk ───────────────────────────────────────────────

    /** Greedy: the LAST op whose head (hyphen glyph included) fits wins
     *  — css-text-3 §5, the same rule as the word fold itself. */
    @Test
    fun splitTakesTheLastOpportunityThatFits() {
        val w = WordBreakOpportunities.analyze("Deoxy­ribo­nucleic")
        val cut = WordBreakOpportunities.split(w, prefix = "", maxWidth = 10 * CH, measure = mono)
        assertEquals("Deoxyribo‐", cut!!.first)        // 10 glyphs — exactly 10ch.
        assertEquals("nucleic", cut.second.text)
        assertTrue(cut.second.ops.isEmpty())                // both ops are spent/rebased away.
    }

    /** The tail's surviving ops are REBASED, not recomputed — shy
     *  positions cannot be recovered from the suffix once the marks are
     *  stripped. */
    @Test
    fun tailOpsAreRebased() {
        val w = WordBreakOpportunities.analyze("Deoxy­ribo­nucleic")
        val cut = WordBreakOpportunities.split(w, prefix = "", maxWidth = 6 * CH, measure = mono)
        assertEquals("Deoxy‐", cut!!.first)            // only the first op fits 6ch.
        assertEquals("ribonucleic", cut.second.text)
        assertEquals(
            listOf(WordBreakOpportunities.Op(4, paintsHyphen = true)),   // 9 − 5.
            cut.second.ops
        )
    }

    /** The prefix participates in the fit test as ONE string — a head
     *  that fits an empty line may not fit after the current line. */
    @Test
    fun thePrefixCountsAgainstTheFit() {
        val w = WordBreakOpportunities.analyze("high­way")
        // Alone, "high‐" (5) fits a 6ch box…
        assertEquals(
            "high‐",
            WordBreakOpportunities.split(w, "", 6 * CH, mono)!!.first
        )
        // …after "to " (3) the same head is 8 > 6 and the split declines.
        assertNull(WordBreakOpportunities.split(w, "to ", 6 * CH, mono))
    }

    /** The overflow fallback: an OPENING word none of whose heads fit
     *  still breaks at its FIRST op — Chromium minimizes the overflow
     *  rather than keeping the whole word (the hyphens-none-012 ref
     *  wraps `imple-menta-tion` even where `imple-` alone overflows).
     *  Deliberately prefix-gated: mid-line, declining is correct because
     *  the space break before the word is the earlier opportunity. */
    @Test
    fun theOverflowFallbackBreaksAtTheFirstOpForOpeningWordsOnly() {
        val w = WordBreakOpportunities.analyze("imple-menta-tion")
        // 5ch: no head fits ("imple-" is 6). Fallback → first op anyway.
        val cut = WordBreakOpportunities.split(
            w, prefix = "", maxWidth = 5 * CH, measure = mono, overflowFallback = true)
        assertEquals("imple-", cut!!.first)
        assertEquals("menta-tion", cut.second.text)
        // Mid-line the fallback must NOT fire.
        assertNull(
            WordBreakOpportunities.split(
                w, prefix = "x ", maxWidth = 5 * CH, measure = mono, overflowFallback = true)
        )
    }
}

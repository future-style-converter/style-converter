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
     *  hyphenation point (css-text-3 §5.3): taking it paints U+2010 and
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

    // ── Wave 52 (lane L9, F4) — the drawn-marker CLAMP over the fold ────
    //
    // MUTATION PROOF (executed 2026-10-05, lane L9, mutate.py M4 —
    // tools/titan/results/wave52-inline-run-wall/mutations-compose.result
    // .json): with clampLines's fit loop disabled (`while (false && …)`,
    // the marker appended unconditionally) the 025 pin, `clampDropsTrailing
    // WordsUntilTheMarkerFits`, `clampMeasuresACustomMarkerAsARealGlyphRun`
    // and PreBreakPipelineTest's fired-run clamp pin fail. Restored
    // byte-exact (sha256-verified). The fix pass replaced that loop with
    // the clampHead walk, so M4 was RE-CUT and re-executed as K5 (the
    // fit test forced true — marker appended to the whole line N): the same
    // four pins plus the 030 and 028 pins fail
    // (mutations-m1-compose.result.json).

    /** css-overflow/line-clamp/block-ellipsis-025's text, VERBATIM off
     *  wave51-fix/sections/css-overflow/per-test-ir (the ONE component:
     *  `line-clamp: 4`, `width: 32.5ch`, monospace). */
    private val ELLIPSIS_025 = "Test passes if there are 4 lines and the last one only contains … " +
        "supercalifragilisticexpialidocious supercalifragilisticexpialidocious Test fails: this should not be visible"

    /** block-ellipsis-025, the ref's picture: line 3 is the 34ch word
     *  overflowing the 32.5ch box (CSS 2.1 §9.5), line 4 is the marker
     *  ALONE — the second copy of the word cannot fit beside "…" either, so
     *  css-overflow-4 §4.2 hides the whole word at its soft wrap
     *  opportunity and the ellipsis is all that remains. SwiftUI's tail
     *  truncation appended "…" AFTER the full word (ios f 0.9495); Compose
     *  clipped the word and painted no marker (android P 0.9607 — wrong). */
    @Test
    fun clampTrimsBlockEllipsis025ToFourLinesWithTheMarkerAlone() {
        val lines = GreedyLineBreaker.lines(
            ELLIPSIS_025, 32.5f * CH, mono, clamp = GreedyLineBreaker.Clamp(4))
        assertEquals(
            listOf(
                "Test passes if there are 4 lines",
                "and the last one only contains …",
                "supercalifragilisticexpialidocious",
                "…",
            ),
            lines
        )
    }

    /** The marker rides the last kept line when it fits — no word dropped. */
    @Test
    fun clampAppendsTheMarkerToAFittingLastLine() {
        // "aa bb" / "cc dd" / "ee ff" at 6ch; clamp 2 → "cc dd…" is 6ch, fits.
        assertEquals(
            listOf("aa bb", "cc dd…"),
            GreedyLineBreaker.lines("aa bb cc dd ee ff", 6 * CH, mono, clamp = GreedyLineBreaker.Clamp(2))
        )
    }

    /** css-overflow-4 §4.2: content is hidden at SOFT WRAP OPPORTUNITIES
     *  (whole words), never mid-word, until the ellipsis fits. */
    @Test
    fun clampDropsTrailingWordsUntilTheMarkerFits() {
        // "aa bb" / "cc dd" / "ee" at 5ch; clamp 2 → "cc dd…" is 6ch (no
        // fit) → drop "dd" → "cc…" fits.
        assertEquals(
            listOf("aa bb", "cc…"),
            GreedyLineBreaker.lines("aa bb cc dd ee", 5 * CH, mono, clamp = GreedyLineBreaker.Clamp(2))
        )
    }

    /** A clamp only ever REMOVES lines: a paragraph inside the cap is
     *  returned as the SAME instance (the pipeline detects "trimmed" by
     *  reference), and so is a non-positive cap. */
    @Test
    fun clampIsIdentityWhenTheParagraphFitsTheCap() {
        val lines = listOf("aa bb", "cc")
        assertTrue(lines === GreedyLineBreaker.clampLines(lines, GreedyLineBreaker.Clamp(2), 6 * CH, mono))
        assertTrue(lines === GreedyLineBreaker.clampLines(lines, GreedyLineBreaker.Clamp(3), 6 * CH, mono))
        assertTrue(lines === GreedyLineBreaker.clampLines(lines, GreedyLineBreaker.Clamp(0), 6 * CH, mono))
        // And `lines(clamp = null)` is the wave-41 fold byte for byte.
        assertEquals(
            GreedyLineBreaker.lines("aa bb cc dd ee", 5 * CH, mono),
            GreedyLineBreaker.lines("aa bb cc dd ee", 5 * CH, mono, clamp = null)
        )
    }

    /** The marker itself is configurable (css-text-4 `hyphenate-character`
     *  has no analogue here yet, but css-overflow-4 §4.2 lets `block-
     *  ellipsis: <string>` name one) and is measured like any glyph. */
    @Test
    fun clampMeasuresACustomMarkerAsARealGlyphRun() {
        // "cc dd" + "[more]" = 11ch > 5 → drop "dd" → "cc[more]" = 8 > 5 →
        // drop "cc" → the marker alone.
        assertEquals(
            listOf("aa bb", "[more]"),
            GreedyLineBreaker.lines("aa bb cc dd ee", 5 * CH, mono, clamp = GreedyLineBreaker.Clamp(2, "[more]"))
        )
    }

    // ── Wave 52 fix pass (skeptic M1) — the clamp hides at EVERY modelled
    //    soft wrap opportunity, not only U+0020 ─────────────────────────
    //
    // MUTATION PROOF (executed 2026-10-05, lane L9 fix pass, mutate.py —
    // tools/titan/results/wave52-inline-run-wall/mutations-m1-compose
    // .result.json; restored byte-exact, sha256-verified, each run):
    // K1 — isClampSeparator narrowed to U+0020: the 030 pin fails (`…`);
    // K2 — markedLine returns the display line (soft hyphens lost): the
    //      028 pin fails (`room…`);
    // K3 — the ideograph/SA decline disabled: the decline pin fails;
    // K4 — the hyphen/dash break-after arm removed: the dash pin fails.

    /** IR property off the verbatim wire (the PlaceholderOverflowMarkerTest idiom). */
    private fun prop(t: String, s: String) =
        com.styleconverter.runtime.core.ir.IRProperty(t, kotlinx.serialization.json.Json.parseToJsonElement(s))

    /** css-overflow/line-clamp/block-ellipsis-030 box 1, VERBATIM off
     *  wave51-fix/sections/css-overflow/per-test-ir: text `123<U+1680>5 789`,
     *  `line-clamp: 1` ({"type":"lines","count":1}), `width: 5ch`, monospace.
     *  U+1680 OGHAM SPACE MARK is UAX #14 class BA — a soft wrap
     *  opportunity — so css-overflow-4 §4.2 hides `5` there and the ref
     *  (box 2) paints `123…`. A U+0020-only cut baked `…` alone. The 029
     *  control (`123 5 789` at 5.1ch, the same ref) rides along. */
    @Test
    fun clampHidesAtTheOghamSpaceMarkOfBlockEllipsis030() {
        val cap = DrawnLineClamp.cap(listOf(prop("LineClamp", """{"type":"lines","count":1}""")))
        assertEquals(1, cap)
        assertEquals(
            listOf("123…"),
            GreedyLineBreaker.lines("123\u16805 789", 5 * CH, mono, clamp = GreedyLineBreaker.Clamp(cap!!))
        )
        assertEquals(
            listOf("123…"),
            GreedyLineBreaker.lines("123 5 789", 5.1f * CH, mono, clamp = GreedyLineBreaker.Clamp(cap))
        )
    }

    /** css-overflow/line-clamp/block-ellipsis-028, VERBATIM (`line-clamp: 2`,
     *  `width: 63.1ch`, `hyphens: manual`). Line 2 is exactly 63ch, so the
     *  marker needs room; both refs hide `cally` at the soft hyphen and
     *  paint the hyphen: `…room uncharacteristi‐…` (ref-a's U+2010). A
     *  word-only cut showed `…room…`, 17 cells short of the ref. */
    @Test
    fun clampHidesAtTheSoftHyphenOfBlockEllipsis028() {
        val text = "This time, Mark, who had always been the center of attention in any social gathering, " +
            "walked into the room uncharacteristi\u00ADcally quietly, barely speaking as he settled into a chair. " +
            "When asked, he said that he was fine, when he wasn't really fine."
        assertEquals(
            listOf(
                "This time, Mark, who had always been the center of attention in",
                "any social gathering, walked into the room uncharacteristi\u2010…",
            ),
            GreedyLineBreaker.lines(text, 63.1f * CH, mono, clamp = GreedyLineBreaker.Clamp(2))
        )
    }

    /** UAX #14 ID/SA opportunities sit BETWEEN letters and are not modelled:
     *  a hidden tail holding one DECLINES (same instance — the wave-51 shape
     *  stays), while an ideograph-free tail still trims at its space. */
    @Test
    fun clampDeclinesWhenTheHiddenTailHoldsAnIdeograph() {
        val cjk = listOf("aa bb", "字字字字字字", "cc")
        assertTrue(cjk === GreedyLineBreaker.clampLines(cjk, GreedyLineBreaker.Clamp(2), 5 * CH, mono))
        // Control: the ideographs stay in the KEPT part, the tail is Latin.
        assertEquals(
            listOf("x", "字字 aa…"),
            GreedyLineBreaker.clampLines(listOf("x", "字字 aa bb", "y"), GreedyLineBreaker.Clamp(2), 6 * CH, mono)
        )
    }

    /** Break-after hyphens/dashes and ZWSP are opportunities; `1-2`
     *  (HY × NU) and a word-initial hyphen (LB20.1) are not. */
    @Test
    fun clampHidesAfterAHyphenAndAtAZeroWidthSpace() {
        val c = GreedyLineBreaker.Clamp(2)
        assertEquals(listOf("x", "foo-…"), GreedyLineBreaker.clampLines(listOf("x", "foo-barbaz", "y"), c, 6 * CH, mono))
        assertEquals(listOf("x", "foo…"), GreedyLineBreaker.clampLines(listOf("x", "foo\u200Bbarbaz", "y"), c, 6 * CH, mono))
        assertEquals(listOf("x", "…"), GreedyLineBreaker.clampLines(listOf("x", "foo-1234", "y"), c, 6 * CH, mono))
        assertEquals(listOf("x", "…"), GreedyLineBreaker.clampLines(listOf("x", "-foobarbaz", "y"), c, 6 * CH, mono))
    }
}

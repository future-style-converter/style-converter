package com.styleconverter.runtime.typography.wrapping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

// SoftHyphenPolicy pin table — applier campaign wave 37, lane W7 (rule A).
//
// TWIN of the iOS suite SoftHyphenPolicyTests.swift — SAME cases, SAME
// expected strings (repo twin rule: identical pin tables). Change one,
// change both. The Swift file additionally pins rule B's
// unbreakable-overflow probe, which has no Compose counterpart: Compose
// does not pre-break (its own line breaker already breaks greedily at
// spaces like Chromium — only the emergency intra-word break differs, and
// that has no Compose-reachable seam; see the lane report).
//
// Source of truth for the strings: the WPT css-text/hyphens family at
// tools/wpt/css/css-text/hyphens/, whose fixtures are authored with
// `Deoxy&shy;ribo&shy;nucleic acid` (the extended form of "DNA").
//
// NOTE on what these pins do and do not claim. They pin the POLICY, which
// is shared; they do not claim the policy currently moves an Android
// pixel. It does not — Compose's default Hyphens.None already makes
// Minikin ignore U+00AD (device-proven, see SoftHyphenPolicy's banner),
// so the call site is a guard against the day `Hyphens.Auto` is turned
// on. The pixel win this policy earned is on iOS.
class SoftHyphenPolicyTest {

    // The literal the css-text/hyphens family is authored with.
    private val shyWord = "Deoxy\u00ADribo\u00ADnucleic acid"

    // ── §6.1 keyword gate ────────────────────────────────────────────

    /** Only `none` suppresses: `manual` is the INITIAL value and must
     *  honour explicit opportunities, `auto` adds dictionary ones on top
     *  of manual's — neither may delete the character. */
    @Test
    fun onlyNoneSuppresses() {
        assertTrue(SoftHyphenPolicy.suppresses("none"))
        assertFalse(SoftHyphenPolicy.suppresses("manual"))
        assertFalse(SoftHyphenPolicy.suppresses("auto"))
    }

    /** The wire is authored upper-case (`{"type":"Hyphens","data":"NONE"}`)
     *  and Compose feeds the enum's `.name` straight in, while iOS's
     *  extractor lowercases — both spellings must answer the same, or the
     *  policy would fire on exactly one of the two runtimes. */
    @Test
    fun keywordMatchIsCaseInsensitive() {
        assertTrue(SoftHyphenPolicy.suppresses("NONE"))
        assertTrue(SoftHyphenPolicy.suppresses("None"))
    }

    /** Absent / unrecognised keyword → the initial value `manual`, which
     *  honours soft hyphens. Answering true here would delete conditional
     *  characters from every undeclared run in the corpus. */
    @Test
    fun unknownAndNullKeywordsDoNotSuppress() {
        assertFalse(SoftHyphenPolicy.suppresses(null))
        assertFalse(SoftHyphenPolicy.suppresses(""))
        assertFalse(SoftHyphenPolicy.suppresses("break-all"))
    }

    /** Only `auto` asks for a hyphenation dictionary — the predicate
     *  behind the once-per-type wall breadcrumb. It must NOT fire for
     *  `manual`/`none`, or every text document would log the wall. */
    @Test
    fun onlyAutoWantsDictionaryHyphenation() {
        assertTrue(SoftHyphenPolicy.wantsDictionaryHyphenation("auto"))
        assertTrue(SoftHyphenPolicy.wantsDictionaryHyphenation("AUTO"))
        assertFalse(SoftHyphenPolicy.wantsDictionaryHyphenation("manual"))
        assertFalse(SoftHyphenPolicy.wantsDictionaryHyphenation("none"))
        assertFalse(SoftHyphenPolicy.wantsDictionaryHyphenation(null))
    }

    // ── §6.1 string rewrite ──────────────────────────────────────────

    /** Under `none` the conditional characters go, and NOTHING else does:
     *  the visible glyph sequence is byte-identical to the Chromium ref's
     *  "Deoxyribonucleic acid". */
    @Test
    fun noneDeletesOnlyTheSoftHyphens() {
        assertEquals("Deoxyribonucleic acid",
            SoftHyphenPolicy.displayString(shyWord, "none"))
    }

    /** Under `manual`/`auto` the string is untouched — the platform's own
     *  U+00AD handling IS the correct §6.1 behaviour there. */
    @Test
    fun manualAndAutoLeaveTheStringIntact() {
        assertSame(shyWord, SoftHyphenPolicy.displayString(shyWord, "manual"))
        assertSame(shyWord, SoftHyphenPolicy.displayString(shyWord, "auto"))
        assertSame(shyWord, SoftHyphenPolicy.displayString(shyWord, null))
    }

    /** A run with no soft hyphen is the SAME INSTANCE back under every
     *  mode — the identity contract that keeps the committed baseline
     *  corpus (and the renderer's remember{} keys) stable. */
    @Test
    fun runWithoutSoftHyphenIsIdentityUnderNone() {
        val plain = "Deoxyribonucleic acid"
        assertSame(plain, SoftHyphenPolicy.displayString(plain, "none"))
    }

    /** U+200B ZERO WIDTH SPACE is a plain break opportunity, not a
     *  hyphenation one: `hyphens` does not govern it (css-text-3 §6.1
     *  speaks only of hyphenation opportunities), so `none` must leave it
     *  in place. */
    @Test
    fun zeroWidthSpaceSurvivesNone() {
        val zwsp = "foo\u200Bbar"
        assertSame(zwsp, SoftHyphenPolicy.displayString(zwsp, "none"))
    }

    /** The constant is the code point itself, not a look-alike: a
     *  HYPHEN-MINUS or U+2010 here would delete visible ink. */
    @Test
    fun theConstantIsU00AD() {
        assertEquals(0x00AD, SoftHyphenPolicy.SOFT_HYPHEN.code)
    }
}

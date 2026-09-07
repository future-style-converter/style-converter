package com.styleconverter.runtime.typography.wrapping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// AutoHyphenation pin table — applier campaign wave 40, lane T2.
//
// TWIN of the iOS suite AutoHyphenationTests.swift — SAME cases, SAME
// expectations (repo twin rule). Change one, change both.
//
// What these pins protect is the GATE, not the hyphenator: on Compose the
// dictionary lives inside Minikin and only a device can show a break
// point. What a pure test CAN pin — and what the whole inertness argument
// rests on — is exactly when the switch is allowed to be thrown.
class AutoHyphenationTest {

    // ── §5.3: `auto` AND a language, both required ───────────────────

    /** The tagged case: WPT css-text/hyphens-auto-010 (`<body lang="en">`
     *  + `hyphens: auto`), where the Chromium ref breaks
     *  `regu-/lation`. */
    @Test
    fun autoWithALanguageEngages() {
        assertTrue(AutoHyphenation.engaged("AUTO", "en"))
        assertTrue(AutoHyphenation.engaged("auto", "en-us"))
    }

    /** The UNTAGGED case is the one WPT css-text/hyphens-auto-001
     *  asserts: "automatic hyphenation must not work without language
     *  tagging". Its `<div>` declares `hyphens: auto` and the document
     *  declares no `lang`; both natives score ~0.99 today precisely
     *  because they leave it alone, and this pin is what keeps that. */
    @Test
    fun autoWithoutALanguageDoesNotEngage() {
        assertFalse(AutoHyphenation.engaged("AUTO", null))
        assertFalse(AutoHyphenation.engaged("AUTO", ""))
        assertFalse(AutoHyphenation.engaged("AUTO", "   "))
    }

    /** `manual` is the INITIAL value and `none` forbids breaking outright
     *  — neither may ever reach the dictionary, whatever the language. */
    @Test
    fun onlyTheAutoKeywordEngages() {
        assertFalse(AutoHyphenation.engaged("manual", "en"))
        assertFalse(AutoHyphenation.engaged("MANUAL", "en"))
        assertFalse(AutoHyphenation.engaged("none", "en"))
        assertFalse(AutoHyphenation.engaged(null, "en"))
    }

    // ── the tag handed to the platform ───────────────────────────────

    /** VERBATIM (minus surrounding space): BCP-47 matching is
     *  case-insensitive and subtag-truncating, and `meta.lang` keeps the
     *  authored spelling on purpose. */
    @Test
    fun localeTagIsTheAuthoredTag() {
        assertEquals("eN-Us", AutoHyphenation.localeTag("auto", "eN-Us"))
        assertEquals("en", AutoHyphenation.localeTag("auto", "  en  "))
    }

    /** No tag when the gate is shut — the caller uses it as the whole
     *  "is hyphenation on?" answer, so it must not leak a locale. */
    @Test
    fun localeTagIsNullWhenNotEngaged() {
        assertNull(AutoHyphenation.localeTag("manual", "en"))
        assertNull(AutoHyphenation.localeTag("auto", null))
    }

    // ── the per-line breakability half (rule B's veto) ───────────────

    /** A word long enough for §6.2's shared floor (Chromium 2+2, Minikin
     *  MIN_PREFIX 2 / MIN_SUFFIX 3) may hyphenate, so a line holding one
     *  is NOT unbreakable. */
    @Test
    fun letterRunsOfFivePlusAreHyphenatable() {
        assertTrue(AutoHyphenation.hasDictionaryOpportunity("example"))
        assertTrue(AutoHyphenation.hasDictionaryOpportunity("implementation"))
        assertTrue(AutoHyphenation.hasDictionaryOpportunity("00000 example"))
    }

    /** The measured counter-case: css-text/hyphens-punctuation-001's
     *  `00000` runs overflow a 5ch box with nowhere to break, so rule B
     *  must still claim them or Minikin desperate-breaks `00000` into
     *  `0000`/`0` (android-ref 0.9385 → 0.8971 when it did). */
    @Test
    fun digitRunsAndShortWordsAreNot() {
        assertFalse(AutoHyphenation.hasDictionaryOpportunity("00000"))
        assertFalse(AutoHyphenation.hasDictionaryOpportunity("1234"))
        assertFalse(AutoHyphenation.hasDictionaryOpportunity("high"))
        assertFalse(AutoHyphenation.hasDictionaryOpportunity(""))
    }

    /** The run must be CONTIGUOUS letters: a hyphenator works on words,
     *  and `ab-cd-ef` is three two-letter words to it, not one six-letter
     *  one. (It is already breakable by UAX #14 at the hyphens, which is
     *  the other half of the veto.) */
    @Test
    fun theLetterRunMustBeContiguous() {
        assertFalse(AutoHyphenation.hasDictionaryOpportunity("ab-cd-ef"))
        assertFalse(AutoHyphenation.hasDictionaryOpportunity("a1b2c3d4e5"))
    }
}

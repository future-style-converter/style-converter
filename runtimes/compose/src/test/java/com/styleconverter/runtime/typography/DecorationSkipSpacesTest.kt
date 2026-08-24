package com.styleconverter.runtime.typography

// DecorationSkipSpacesTest.kt — applier campaign wave 47, lane Z7.
//
// Pins for css-text-decor-4 §2.6 `text-decoration-skip-spaces` (initial
// `start end`): the DecorationSkipSpaces spacer set, core-range trim and
// the trimmedExtent geometry helper. Twin of the iOS suite's
// DecorationSkipSpacesTests (change one, change both). The character
// inventory is lifted from WPT text-decoration-skip-spaces-001.html —
// the exact spacers Chromium refuses to underline at line edges in the
// frozen ref (blue band spans only "ABCDEF").

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecorationSkipSpacesTest {

    // --- the spacer set -------------------------------------------------

    /** Every character class the WPT -001 source packs around "ABCDEF"
     *  must classify as a spacer (all Zs, plus the TAB/ZWSP carve-ins). */
    @Test
    fun spacerSetMatchesWptInventory() {
        // U+0020, NBSP, OGHAM, en quad ... hair space (U+2000-U+200A),
        // NNBSP, MMSP, IDEOGRAPHIC SPACE, TAB, ZWSP -- written as
        // escapes, not literals, so the inventory is reviewable.
        val spacers = charArrayOf(
            '\u0020', '\u00A0', '\u1680', '\u2000', '\u2001', '\u2002',
            '\u2003', '\u2004', '\u2005', '\u2006', '\u2007', '\u2008',
            '\u2009', '\u200A', '\u202F', '\u205F', '\u3000', '\t', '\u200B'
        )
        for (c in spacers) {
            assertTrue("U+%04X must be a spacer".format(c.code),
                DecorationSkipSpaces.isSpacer(c))
        }
        // Ink-bearing characters must NOT classify.
        for (c in charArrayOf('A', 'F', '.', 'ß', '字')) {
            assertFalse("$c is glyph ink, not a spacer",
                DecorationSkipSpaces.isSpacer(c))
        }
    }

    // --- coreRange ------------------------------------------------------

    /** Edge runs peel off; the MID-line spacer stays inside the core —
     *  what keeps skip-spaces-002/003/004 (mid-line spacers keep their
     *  underline) behaviorally untouched. */
    @Test
    fun coreRangeTrimsEdgesOnly() {
        val line = "   AB CD   "
        val core = DecorationSkipSpaces.coreRange(line)!!
        assertEquals("AB CD", line.substring(core.first, core.last + 1))
    }

    /** No edge spacers → the identity range over the whole line. */
    @Test
    fun coreRangeIdentityWithoutEdgeSpacers() {
        assertEquals(0..22, DecorationSkipSpaces.coreRange("Grumpy wizards vex 0123"))
    }

    /** Spacers-only (and empty) lines have no core: the painter draws
     *  nothing — Chromium parity on -001's wrapped spacers-only lines. */
    @Test
    fun coreRangeNullForSpacersOnly() {
        assertNull(DecorationSkipSpaces.coreRange("   　"))
        assertNull(DecorationSkipSpaces.coreRange(""))
    }

    // --- trimmedExtent --------------------------------------------------

    /** The -001 line shape ("spacers ABCDEF spacers"): the band's left
     *  moves to the first core character's caret, the right to the caret
     *  AFTER the last one — both read through the injected accessor at
     *  WHOLE-STRING offsets (lineStart + local index). */
    @Test
    fun trimmedExtentUsesCaretsAtCoreEdges() {
        val lineText = "  ABCDEF  "   // lead 2, core 6, trail 2
        // Fake caret metric: 10px per character, line starting at
        // whole-string offset 5 and x = 40 (so caret(o) = 40 + (o-5)*10).
        val ext = DecorationSkipSpaces.trimmedExtent(
            lineText, lineStart = 5, left = 40f, right = 140f
        ) { offset -> 40f + (offset - 5) * 10f }!!
        assertEquals(60f, ext.first, 0f)   // 2 lead chars × 10 past x=40
        assertEquals(120f, ext.second, 0f) // caret after 'F' (8 chars in)
    }

    /** Byte-stability pin: a line WITHOUT edge spacers returns the
     *  untrimmed (left, right) OBJECTS bit-for-bit — the caret accessor
     *  must not even be consulted (committed 066/067 baselines and every
     *  fixture text ride this fast path). */
    @Test
    fun trimmedExtentIdentityWithoutEdgeSpacers() {
        val ext = DecorationSkipSpaces.trimmedExtent(
            "Grumpy wizards vex 0123", lineStart = 0, left = 3.25f, right = 217.75f
        ) { throw AssertionError("caret accessor must not be consulted") }!!
        assertEquals(3.25f, ext.first, 0f)
        assertEquals(217.75f, ext.second, 0f)
    }

    /** Spacers-only line → null (paint nothing at all). */
    @Test
    fun trimmedExtentNullForSpacersOnly() {
        assertNull(DecorationSkipSpaces.trimmedExtent(
            "  ", lineStart = 0, left = 0f, right = 20f) { 0f })
    }

    /** Bidi sanity valve: carets that do not bracket left-to-right (an
     *  RTL run) fall back to the untrimmed extent instead of emitting a
     *  reversed or negative-width band. */
    @Test
    fun trimmedExtentFallsBackOnReversedCarets() {
        val ext = DecorationSkipSpaces.trimmedExtent(
            " שלום", lineStart = 0, left = 0f, right = 50f
        ) { 50f }!!  // caret claims the core starts at the line's RIGHT edge
        assertEquals(0f, ext.first, 0f)
        assertEquals(50f, ext.second, 0f)
    }
}

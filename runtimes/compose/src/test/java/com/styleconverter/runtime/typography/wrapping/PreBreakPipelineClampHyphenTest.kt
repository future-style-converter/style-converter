package com.styleconverter.runtime.typography.wrapping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Wave 55 (lane L7, unit U6-compose, pin C2 — wave-54 L3 skeptic SK-X2).
//
// A FIRED run under a drawn-marker clamp is trimmed to the cap by
// GreedyLineBreaker.clampLines (wave 52 F4), which may hide line N back to an
// untaken soft hyphen — and a soft-hyphen break paints the hyphenate-character
// (css-text-3 §5.3; css-text-4 §6.3). Wave 54 threaded the run's own
// `hyphenate-character` into that call (PreBreakPipeline.kt, the clampLines
// call's `hyphenChar = hyphenChar`), but no test reached it: every clamp pin
// ran with the UA hyphen, where forgetting the argument changes nothing.
//
// The run: `ab cd<SHY>ef ghijkl<SHY>mnop` at 7 monospace cells, `hyphens:
// manual`, hyphenate-character "•" (WPT -003's decoded `\2022`). The fold
// takes the second soft hyphen (`ghijkl•`), so the pipeline FIRES (the F5
// trigger); a one-line clamp then cannot fit `ab cdef…`, hides back to the
// first soft hyphen and must paint "•" there, not U+2010.
//
// MUTATION (executed red → restore → green, sha256 in
// tools/titan/results/wave55-instruments/_note.md): drop
// `hyphenChar = hyphenChar` from the clampLines call → the head ends `‐…`.
class PreBreakPipelineClampHyphenTest {

    // A monospace measure: every code point one 10-px cell; U+00AD has no
    // advance (an untaken soft hyphen is invisible, css-text-3 §5.3).
    private val mono: (String) -> Float = { s -> s.codePoints().filter { it != 0xAD }.count() * 10f }

    // The run under test, soft hyphens explicit.
    private val text = "ab cd­ef ghijkl­mnop"

    // One preBreak call: composed-WPT capture, soft wrap on, manual hyphens.
    private fun run(clamp: Int?) = PreBreakPipeline.preBreak(
        text, 70f, enabled = true, softWrapAllowed = true, allowMidWordBreak = false,
        preservesSpaces = false, dictionaryHyphenation = false, clampLines = clamp, hyphenChar = "•", measure = mono)

    @Test fun `C2 control - unclamped, the fold takes the second soft hyphen and paints the run's hyphenate-character`() {
        val r = run(null)
        // The F5 trigger fired: a soft-hyphen break was taken.
        assertTrue(r.fired)
        // Three lines; the taken break paints "•", the untaken one paints nothing.
        assertEquals("ab cdef\nghijkl•\nmnop", r.text)
    }

    @Test fun `C2 a one-line clamp hides back to the first soft hyphen and paints the run's hyphenate-character there`() {
        val r = run(1)
        // Still fired (the clamp only trims a fired run).
        assertTrue(r.fired)
        // `ab cdef` + marker overflows 7 cells; the latest opportunity that fits is cd|ef.
        assertEquals("ab cd•" + GreedyLineBreaker.BLOCK_ELLIPSIS_MARKER, r.text)
    }
}

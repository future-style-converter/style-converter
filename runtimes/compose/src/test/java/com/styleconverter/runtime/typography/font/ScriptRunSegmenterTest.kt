package com.styleconverter.runtime.typography.font

// Wave 34, lane F1 — pin table for the PER-SCRIPT RUN SEGMENTATION that
// stands in for the per-character font fallback Compose cannot do below
// API 29 (`Typeface.CustomFallbackBuilder`; this module is minSdk 24).
//
// These pins hold the exact contract the iOS twin
// (runtimes/swiftui/Tests/StyleConverterRuntimeTests/ScriptRunFallbackTests.swift)
// asserts case-for-case: the same script table, the same run boundaries,
// the same Common-codepoint answers. A silent divergence between the two
// tables is the one failure mode that would make the two natives resolve
// DIFFERENT faces for the same string — i.e. re-open the Rule-43 boundary
// this lane exists to close — and it cannot be seen in a device capture,
// only here.
//
// The `ScriptFallbackFonts` half (which family, and the WPT gate) needs the
// Android resource system for `R.font.*`, so its JVM-safe halves are pinned
// in ScriptFallbackFontsTest (retro P2e corrected the name: the class is
// `ScriptFallbackFontsTest`, and no `…GateTest` has ever existed).

import com.styleconverter.runtime.typography.font.ScriptRunSegmenter.TextScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptRunSegmenterTest {

    // ── The script table ────────────────────────────────────────────────

    @Test
    fun `the five target scripts are recognised by their block ranges`() {
        // css-counter-styles-3 §6.1 `arabic-indic` digits U+0660–U+0669.
        assertEquals(TextScript.ARABIC, ScriptRunSegmenter.scriptOf(0x0660))
        assertEquals(TextScript.ARABIC, ScriptRunSegmenter.scriptOf(0x0669))
        // §6.2 `persian` — extended Arabic-Indic, same block.
        assertEquals(TextScript.ARABIC, ScriptRunSegmenter.scriptOf(0x06F5))
        // §6.2 additive `armenian` — the letters the ordinals are spelled
        // with (Ժ = U+053A, the "10" of armenian-007's first row).
        assertEquals(TextScript.ARMENIAN, ScriptRunSegmenter.scriptOf(0x053A))
        assertEquals(TextScript.ARMENIAN, ScriptRunSegmenter.scriptOf(0x0561))
        // §6.2 additive `hebrew`.
        assertEquals(TextScript.HEBREW, ScriptRunSegmenter.scriptOf(0x05D0))
        // §6.2 simple-numeric `bengali` digits U+09E6–U+09EF.
        assertEquals(TextScript.BENGALI, ScriptRunSegmenter.scriptOf(0x09E6))
        assertEquals(TextScript.BENGALI, ScriptRunSegmenter.scriptOf(0x09EF))
        // §6.2 `khmer`/`cambodian` digits U+17E0–U+17E9.
        assertEquals(TextScript.KHMER, ScriptRunSegmenter.scriptOf(0x17E0))
        assertEquals(TextScript.KHMER, ScriptRunSegmenter.scriptOf(0x17E9))
    }

    @Test
    fun `Inter's own coverage stays DEFAULT — Latin, Greek, Cyrillic, ASCII`() {
        // The whole point of the boundary: Inter covers these three scripts
        // and the Rule-43 table deliberately EXCLUDES `lower-greek` for the
        // same reason. Claiming a fallback face for them would swap a face
        // the ref is already matching.
        assertEquals(TextScript.DEFAULT, ScriptRunSegmenter.scriptOf('A'.code))
        assertEquals(TextScript.DEFAULT, ScriptRunSegmenter.scriptOf('z'.code))
        assertEquals(TextScript.DEFAULT, ScriptRunSegmenter.scriptOf('7'.code))
        assertEquals(TextScript.DEFAULT, ScriptRunSegmenter.scriptOf(0x03B1)) // α
        assertEquals(TextScript.DEFAULT, ScriptRunSegmenter.scriptOf(0x0410)) // А
    }

    @Test
    fun `an unknown script answers DEFAULT rather than guessing`() {
        // Honest scope: the table is the five bundled scripts and nothing
        // else. CJK, Devanagari, Thai and emoji all keep the pre-lane
        // platform cascade — never worse, never a claim we cannot back.
        assertEquals(TextScript.DEFAULT, ScriptRunSegmenter.scriptOf(0x4E00)) // 一
        assertEquals(TextScript.DEFAULT, ScriptRunSegmenter.scriptOf(0x0915)) // क
        assertEquals(TextScript.DEFAULT, ScriptRunSegmenter.scriptOf(0x0E01)) // ก
        assertEquals(TextScript.DEFAULT, ScriptRunSegmenter.scriptOf(0x1F600)) // 😀
    }

    // ── Common-codepoint attachment (the rule the wave-31 sketch got wrong)

    @Test
    fun `a FULL STOP after Armenian stays DEFAULT — the marker suffix rule`() {
        // MEASURED (fontTools cmap dump): NotoSansArmenian, NotoSansBengali
        // and NotoSansHebrew have NO U+002E FULL STOP. The css-counter-styles
        // markers are spelled `Ժ.` / `ԺԱ.` / `০.`, so an unconditional
        // "Common keeps with the preceding run" would hand the period to a
        // face with no glyph for it — `.notdef` tofu, or an unpredictable
        // platform cascade. Chromium (shape-then-refallback) keeps it in the
        // primary face; so do we.
        assertEquals(TextScript.DEFAULT, ScriptRunSegmenter.scriptOf('.'.code))
        val runs = ScriptRunSegmenter.segment("ԺԱ.")
        assertEquals(
            listOf(
                ScriptRunSegmenter.Run(0, 2, TextScript.ARMENIAN),
                ScriptRunSegmenter.Run(2, 3, TextScript.DEFAULT)
            ),
            runs
        )
    }

    @Test
    fun `the Arabic-block NEUTRALS keep with the Arabic run`() {
        // U+060C ARABIC COMMA / U+061B SEMICOLON / U+061F QUESTION MARK /
        // U+0640 TATWEEL are Script=Common but Inter has NO glyph for any of
        // them (cmap-verified), so the primary face cannot supply them and
        // §5.2 hands them to the same fallback family as the surrounding
        // Arabic. Their BLOCK membership already puts them there — which is
        // why the segmenter needs no separate Common pass.
        assertEquals(TextScript.ARABIC, ScriptRunSegmenter.scriptOf(0x060C))
        assertEquals(TextScript.ARABIC, ScriptRunSegmenter.scriptOf(0x061B))
        assertEquals(TextScript.ARABIC, ScriptRunSegmenter.scriptOf(0x061F))
        assertEquals(TextScript.ARABIC, ScriptRunSegmenter.scriptOf(0x0640))
        // One unbroken Arabic run — digits, tatweel and the Arabic comma.
        assertEquals(
            listOf(ScriptRunSegmenter.Run(0, 4, TextScript.ARABIC)),
            ScriptRunSegmenter.segment("١٠ـ،")
        )
    }

    @Test
    fun `a SPACE between two Armenian words splits the run`() {
        // The other half of the same rule: Inter DOES supply U+0020, so the
        // space takes the primary face exactly as Chromium's shaper leaves
        // it there. Three runs, not one.
        assertEquals(
            listOf(
                ScriptRunSegmenter.Run(0, 2, TextScript.ARMENIAN),
                ScriptRunSegmenter.Run(2, 3, TextScript.DEFAULT),
                ScriptRunSegmenter.Run(3, 5, TextScript.ARMENIAN)
            ),
            ScriptRunSegmenter.segment("ԺԱ ԺԲ")
        )
    }

    @Test
    fun `a BOM does not drag the following Latin onto the Arabic face`() {
        // U+FEFF sits inside the Arabic Presentation Forms-B block but is
        // Script=Common; the range deliberately stops at U+FEFE.
        assertEquals(TextScript.DEFAULT, ScriptRunSegmenter.scriptOf(0xFEFF))
        assertEquals(TextScript.ARABIC, ScriptRunSegmenter.scriptOf(0xFEFE))
    }

    // ── Segmentation ────────────────────────────────────────────────────

    @Test
    fun `adjacent codepoints of one script merge into a single run`() {
        assertEquals(
            listOf(ScriptRunSegmenter.Run(0, 3, TextScript.BENGALI)),
            ScriptRunSegmenter.segment("১২৩")
        )
    }

    @Test
    fun `a mixed Latin-Armenian-Latin string yields three runs in order`() {
        // The armenian-007 shape: Latin prose, an Armenian ordinal, Latin
        // punctuation. Offsets are UTF-16 and half-open.
        assertEquals(
            listOf(
                ScriptRunSegmenter.Run(0, 3, TextScript.DEFAULT),
                ScriptRunSegmenter.Run(3, 5, TextScript.ARMENIAN),
                ScriptRunSegmenter.Run(5, 7, TextScript.DEFAULT)
            ),
            ScriptRunSegmenter.segment("ab ԺԱ c")
        )
    }

    @Test
    fun `each script gets its OWN run — adjacent non-Latin scripts do not merge`() {
        val runs = ScriptRunSegmenter.segment("ա٠০א០")
        assertEquals(
            listOf(
                ScriptRunSegmenter.Run(0, 1, TextScript.ARMENIAN),
                ScriptRunSegmenter.Run(1, 2, TextScript.ARABIC),
                ScriptRunSegmenter.Run(2, 3, TextScript.BENGALI),
                ScriptRunSegmenter.Run(3, 4, TextScript.HEBREW),
                ScriptRunSegmenter.Run(4, 5, TextScript.KHMER)
            ),
            runs
        )
    }

    @Test
    fun `a surrogate pair is never severed between its two UTF-16 units`() {
        // 😀 is U+1F600 — two UTF-16 units, ONE scalar. A split between them
        // would hand half a character to a different SpanStyle and paint two
        // replacement glyphs. The run must be [0,2), not [0,1)+[1,2).
        assertEquals(
            listOf(ScriptRunSegmenter.Run(0, 2, TextScript.DEFAULT)),
            ScriptRunSegmenter.segment("😀")
        )
        // …and with an Armenian tail the boundary lands at 2, not 1.
        assertEquals(
            listOf(
                ScriptRunSegmenter.Run(0, 2, TextScript.DEFAULT),
                ScriptRunSegmenter.Run(2, 3, TextScript.ARMENIAN)
            ),
            ScriptRunSegmenter.segment("😀ա")
        )
    }

    @Test
    fun `the empty string is one DEFAULT run — the callers' no-op signal`() {
        assertEquals(
            listOf(ScriptRunSegmenter.Run(0, 0, TextScript.DEFAULT)),
            ScriptRunSegmenter.segment("")
        )
    }

    @Test
    fun `runs tile the string exactly — no gap, no overlap, no zero-length`() {
        // Structural invariant every call site relies on when it converts a
        // run to a SpanStyle range: an overlap would double-style, a gap
        // would leave a glyph on the wrong face.
        for (s in listOf("ԺԱ. ԺԲ", "1. ١٠", "abc", "", "០១x")) {
            var cursor = 0
            for (r in ScriptRunSegmenter.segment(s)) {
                assertEquals("run starts where the previous ended in <$s>", cursor, r.start)
                assertTrue("no zero-length run in <$s>", r.end > r.start || s.isEmpty())
                cursor = r.end
            }
            assertEquals("runs cover the whole string <$s>", s.length, cursor)
        }
    }

    // ── needsFallback: the identity pre-check ───────────────────────────

    @Test
    fun `needsFallback is false for Latin-only text — the byte-identity gate`() {
        // This is the structural half of "the 327 baselines cannot move":
        // even inside WPT capture, a string with no target-script codepoint
        // makes ScriptFallbackFonts return its input untouched.
        assertFalse(ScriptRunSegmenter.needsFallback(""))
        assertFalse(ScriptRunSegmenter.needsFallback("Test passes if the two columns match."))
        assertFalse(ScriptRunSegmenter.needsFallback("1. 2. 3."))
        assertFalse(ScriptRunSegmenter.needsFallback("αβγ АБВ")) // Greek + Cyrillic
    }

    @Test
    fun `needsFallback is true for each of the five target scripts`() {
        assertTrue(ScriptRunSegmenter.needsFallback("٠"))
        assertTrue(ScriptRunSegmenter.needsFallback("ա"))
        assertTrue(ScriptRunSegmenter.needsFallback("০"))
        assertTrue(ScriptRunSegmenter.needsFallback("א"))
        assertTrue(ScriptRunSegmenter.needsFallback("០"))
        // …including when the target script is a single glyph in Latin prose.
        assertTrue(ScriptRunSegmenter.needsFallback("row 20: Ժ."))
    }
}

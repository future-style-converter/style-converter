package com.styleconverter.runtime.typography.wrapping

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Wave 55 (lane L7, unit U6-compose, pin C3 — wave-54 L3 skeptic SK-X3).
//
// SoftHyphenCuts.took answers "did the fold take a soft-hyphen break?" by a
// WALK that aligns the display lines with the source words, and falls back to
// the wave-52 COUNT (legacyCount) only when the lines cannot be explained by
// the fold (SoftHyphenCuts.kt `walk(…) ?: legacyCount(…)`). Every existing pin
// asserts the walk aligns, so the fallback was never reached.
//
// Traced: for `im<SHY>ple` the fold's display word is `imple` with one
// hyphenation op at offset 2. The line `im‐ple` (a U+2010 INSIDE the word, not
// at a line end) neither starts with `imple` nor ends at an op head (`im‐`),
// so the walk returns null; the count then sees one U+2010 in the lines and
// none in the source → true. So the answer below is the FALLBACK's.
//
// MUTATION (executed red → restore → green, sha256 in
// tools/titan/results/wave55-instruments/_note.md): `?: legacyCount(…)` →
// `?: false`.
class SoftHyphenCutsTest {

    @Test fun `C3 lines the walk cannot align are answered by the wave-52 count, not dropped`() {
        val text = "im­ple"
        val lines = listOf("im‐ple", "x")
        // The walk cannot explain these lines as the fold's output …
        assertFalse(SoftHyphenCuts.walkAligns(text, lines, "‐"))
        // … so the fallback answers: one hyphen more in the lines than in the source.
        assertTrue(SoftHyphenCuts.took(text, lines, "‐"))
    }
}

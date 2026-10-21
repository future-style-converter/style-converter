package com.styleconverter.runtime.lists

// Wave 55, lane L5 (U2i-android) — the SEAM pin for the Row marker's end
// padding. SHIPS INSIDE seam-5.patch (tools/titan/results/
// wave55-counter-suffix-rows/), never on its own: it reads the renderer
// source, so it is red on a tree without the seam by design.
//
// WHY A SOURCE-LEVEL PIN: `runtimes/compose` unit tests are plain JVM JUnit
// (no Compose UI test, no Robolectric), so the Row placement cannot be laid
// out here; the device key `[I] cjk-decimal/css3-counter-styles-005
// android` is the picture evidence. This pin is the executable check that
// the inside-marker Row reads the string-keyed gap
// (ListMarkerSuffixGap.gapDp(marker)) and no longer the bare stand-in — the
// idiom core/renderer/SeamReachabilityTest uses for its spliced seams
// (comments and string literals stripped first, so prose never counts).
//
// Mutation EXECUTED by the lane (bracketed in the lane note): with the
// seam-5 hunk reverse-applied to ComponentRenderer.kt this goes red.

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListMarkerSuffixGapRowSeamTest {

    /** The renderer source, found by walking up to the repo root (the
     *  module's Gradle rootDir is apps/android-harness). */
    private val renderer: File by lazy {
        val rel = "runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt"
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, rel).exists()) dir = dir.parentFile
        File(requireNotNull(dir) { "repo root not found" }, rel)
    }

    /** Source minus block comments, line comments and string literals. */
    private fun code(): String =
        renderer.readText()
            .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
            .replace(Regex("""//[^\n]*"""), " ")
            .replace(Regex(""""(?:[^"\\\n]|\\.)*""""), " ")

    @Test
    fun `the inside marker Row pads by the string-keyed suffix gap`() {
        val src = code()
        // The seam: the Row marker Text's end padding is the marker's gap.
        assertTrue(
            "the Row marker padding does not read ListMarkerSuffixGap.gapDp(marker)",
            Regex("""\.padding\(end\s*=\s*com\.styleconverter\.runtime\.lists\.ListMarkerSuffixGap\.gapDp\(marker\)\)""")
                .containsMatchIn(src)
        )
        // …and the bare 4dp stand-in is gone from that padding.
        assertFalse(
            "a Row marker still pads by the bare ListMarkerRow.gapDp",
            Regex("""\.padding\(end\s*=\s*ListMarkerRow\.gapDp\)""").containsMatchIn(src)
        )
    }
}

package com.styleconverter.runtime.lists

// Wave 55, lane L5 (U2o-android) — the SEAM pin for the outside hang's
// offset. SHIPS INSIDE seam-4.patch (tools/titan/results/
// wave55-counter-suffix-rows/), never on its own: it reads the renderer
// source, so it is red on a tree without the seam by design.
//
// WHY A SOURCE-LEVEL PIN: `runtimes/compose` unit tests are plain JVM JUnit
// (no Compose UI test), so ListMarkerOutsideHang.Hang cannot be laid out
// here; the device key `[C] counter-suffix android` is the picture
// evidence. The SeamReachabilityTest module line this seam also carries
// cannot tell this hunk from seam-5's (either one reaches the module —
// executed: the lane note's M10), so this pins the hang's own call: its
// `gapPx` reads ListMarkerSuffixGap.gapDp(marker), not the bare stand-in.
// Same strip-then-match idiom as core/renderer/SeamReachabilityTest.
//
// Mutation EXECUTED by the lane (bracketed in the lane note, M10'): with
// the seam-4 hunk reverse-applied to ComponentRenderer.kt this goes red.

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListMarkerSuffixGapHangSeamTest {

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
    fun `the outside hang offset is the string-keyed suffix gap`() {
        val src = code()
        // The seam: Hang(gapPx = …) resolves the marker's own gap to px.
        assertTrue(
            "the hang's gapPx does not read ListMarkerSuffixGap.gapDp(marker)",
            Regex("""gapPx\s*=\s*with\(markerSnapDensity\)\s*\{\s*com\.styleconverter\.runtime\.lists\.ListMarkerSuffixGap\.gapDp\(marker\)\.roundToPx\(\)\s*\}""")
                .containsMatchIn(src)
        )
        // …and the bare 4dp stand-in no longer feeds it.
        assertFalse(
            "the hang still offsets by the bare ListMarkerRow.gapDp",
            Regex("""ListMarkerRow\.gapDp\.roundToPx\(\)""").containsMatchIn(src)
        )
    }
}

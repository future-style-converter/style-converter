package com.styleconverter.runtime.core.renderer

// Wave 53 (lane L2, seam-2) — the RUNS-FOLD BREADCRUMB pin.
//
// WHY THIS EXISTS: wave 53's F2 (typography/inline/InertOutOfFlowMember.kt)
// lets InlineRunFold DROP a paint-inert out-of-flow member — an abspos/fixed
// `<span>` with transparent ink — from the folded paragraph, so that box is no
// longer mounted at all. A removal from the mounted tree that the log does not
// name is a silent fallthrough (CLAUDE.md "No silent fallthroughs"; PLAN wave 53
// §9 D2 made this breadcrumb REQUIRED whenever F2 ships). The count rides
// `InlineRunFold.Outcome.Folded.droppedOutOfFlowMembers`; the only place it can
// reach logcat is the fold breadcrumb in ComponentRenderer.kt (a seam file,
// changed by tools/titan/results/wave53-soft-hyphen/seam-2.patch).
//
// It is a SOURCE-level pin for the same reason SeamReachabilityTest is: this
// module's unit tests are plain JVM JUnit4 (no Compose UI runtime), so the
// @Composable that logs cannot run here. Reading the source to pin a structural
// invariant is the established idiom (SeamReachabilityTest, columns/
// FragmentGeometryTest).
//
// MUTATION PROOF (executed, tools/titan/results/wave53-soft-hyphen/_note.md §3,
// M-seam2): with seam-2 applied this class is green; with the clause deleted
// (i.e. the pre-seam ComponentRenderer.kt bytes) it is red. Until the
// orchestrator lands seam-2 in the same commit as F2, this class is RED on the
// tree by design — it is the check that the seam was spliced.

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RunFoldBreadcrumbSeamTest {

    /** The repo root, found by walking up from the JVM's working directory
     *  (this module's Gradle rootDir is apps/android-harness) until the
     *  renderer source is visible — SeamReachabilityTest's walk-up. */
    private val repoRoot: File by lazy {
        val anchor = "runtimes/compose/src/main/java/com/styleconverter/runtime/" +
            "core/renderer/ComponentRenderer.kt"
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, anchor).exists()) dir = dir.parentFile
        requireNotNull(dir) { "repo root not found above ${System.getProperty("user.dir")}" }
    }

    /** The seam file whose fold breadcrumb must carry the count. */
    private val renderer: File by lazy {
        File(repoRoot, "runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt")
    }

    /**
     * The FOLDED-outcome breadcrumb: the source between the `meta.runs fold: `
     * log literal and the `meta.runs fold bail (` one, with `//` line comments
     * stripped so a mention in prose can never satisfy the pin.
     */
    private fun foldedBreadcrumb(): String {
        val src = renderer.readText()
        // The Folded branch's Log.i message opens with this literal…
        val start = src.indexOf("\"meta.runs fold: ")
        // …and the Bailed branch's message follows it.
        val end = src.indexOf("\"meta.runs fold bail (", start)
        assertTrue("fold breadcrumb markers not found in ComponentRenderer.kt", start >= 0 && end > start)
        // Code only: drop every line's `//` tail (the clause carries none).
        return src.substring(start, end).lines().joinToString("\n") { it.substringBefore("//") }
    }

    /** The count is read in CODE inside the Folded breadcrumb, gated on > 0
     *  (every pre-wave-53 fold logs byte-identically), and labelled. */
    @Test
    fun `the runs-fold breadcrumb names the dropped out-of-flow members`() {
        val block = foldedBreadcrumb()
        assertTrue("breadcrumb does not read droppedOutOfFlowMembers",
            block.contains("runFold.droppedOutOfFlowMembers > 0"))
        assertTrue("breadcrumb does not label the drop",
            block.contains("\${runFold.droppedOutOfFlowMembers} out-of-flow member(s) dropped"))
    }
}

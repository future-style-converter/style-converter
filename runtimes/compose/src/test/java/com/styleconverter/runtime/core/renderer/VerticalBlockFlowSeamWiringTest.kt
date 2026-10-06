package com.styleconverter.runtime.core.renderer

// Wave 52 (lane L3 · failure-ink, fix F1) — the SEAM WIRING pin, shipped
// inside seam-1.patch together with the seam it pins.
//
// WHY THIS EXISTS (wave-52 skeptic, should-fix 2): VerticalBlockFlowSeamGuardTest
// pins the PREDICATE (BakedLayoutSignature.bakedPhysicalBox) but nothing pinned
// that the vertical block-flow seam in ComponentRenderer.kt CALLS it. With
// seam-1 dropped or mis-merged (many lanes deliver hunks into that one file)
// every predicate pin stays green and
// css-writing-modes/flexbox_align-items-stretch-writing-modes silently keeps
// its frozen Column. A @Composable seam cannot execute in this module's plain
// JVM JUnit4 tests, so this is a SOURCE-level pin — the module's idiom
// (SeamReachabilityTest, columns/FragmentGeometryTest read source the same way).
//
// NEGATIVE CONTROLS, EXECUTED (fix pass 2026-10-05, log
// results/wave52-failure-ink/_seam1-verify.log):
//  - ComponentRenderer.kt at HEAD 7d9c22a7 bytes (seam-1 NOT applied): the two
//    guard tests FAIL; the consumer test stays green (HEAD consumes the guard);
//  - seam-1 applied but the `!anyBakedChild &&` engage conjunct deleted: the
//    consumer test FAILS.

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerticalBlockFlowSeamWiringTest {

    /**
     * ComponentRenderer.kt, found by walking up from the JVM's working
     * directory to the repo root (this module's Gradle rootDir is
     * apps/android-harness, the out-of-tree include) — SeamReachabilityTest's
     * walk-up, so both pins resolve the same file.
     */
    private val renderer: File by lazy {
        val anchor = "runtimes/compose/src/main/java/com/styleconverter/runtime/" +
            "core/renderer/ComponentRenderer.kt"
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, anchor).exists()) dir = dir.parentFile
        File(requireNotNull(dir) { "repo root not found" }, anchor)
    }

    /** The seam's source text, read once per test (the file is ~500 KB). */
    private fun source(): String = renderer.readText()

    /**
     * The BAKED-LAYOUT guard block: from `val anyBakedChild` to the next
     * guard's declaration (`val allTextOnlyLeaves`, the TEXT-ONLY-LEAVES
     * guard that follows it in the seam since wave 47).
     */
    private fun guardBlock(text: String): String {
        // The guard's declaration — its name is the seam's own contract.
        val start = text.indexOf("val anyBakedChild = seamInFlowChildren.any")
        assertTrue("the vertical seam's anyBakedChild guard is missing", start >= 0)
        // The following guard bounds the block (never the end of file).
        val end = text.indexOf("val allTextOnlyLeaves", start)
        assertTrue("the TEXT-ONLY-LEAVES guard no longer follows anyBakedChild", end > start)
        return text.substring(start, end)
    }

    /** The guard reads the ONE shared post-load signature predicate. */
    @Test
    fun `the vertical seam's baked guard reads BakedLayoutSignature`() {
        assertTrue(
            "anyBakedChild must call BakedLayoutSignature.bakedPhysicalBox (seam-1)",
            guardBlock(source()).contains(
                "BakedLayoutSignature.bakedPhysicalBox(child.properties)"
            )
        )
    }

    /** The wave-47 two-property heuristic (authored squares trip it) is gone. */
    @Test
    fun `the wave-47 Width-and-Height heuristic is gone from the guard`() {
        assertFalse(
            "anyBakedChild still reads the bare Width ∧ Height heuristic",
            guardBlock(source()).contains("it.type == \"Width\"")
        )
    }

    /** The guard still gates the seam's engage condition (not dead code). */
    @Test
    fun `the seam's engage condition still consumes the guard`() {
        val text = source()
        // The consumer sits after the declaration, in the `if (…)` that
        // engages VerticalBlockFlowLayout.
        val declared = text.indexOf("val anyBakedChild")
        assertTrue(
            "the engage condition no longer reads !anyBakedChild",
            declared >= 0 && text.indexOf("!anyBakedChild &&", declared) > declared
        )
    }
}

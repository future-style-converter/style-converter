package com.styleconverter.runtime.core.renderer

// Wave 54 (lane L4 · CBB-android) — the SEAM WIRING pin, shipped INSIDE
// tools/titan/results/wave54-oof-layout/seam-1.patch with the hunk it pins
// (the wave-53 FloatAvoidSeamWiringTest precedent), so the tree never carries
// a pin that is red by construction.
//
// Why: DynamicValueResolverTest pins ContainingBlockBands' rule (WPT
// content-box → declared size; dark stage → frozen subtraction), but nothing
// there proves the renderer PASSES the capture mode. The call sits inside a
// @Composable remember block that this module's plain JVM JUnit tests cannot
// execute, so this is a SOURCE-level pin — the module's idiom
// (SeamReachabilityTest, WptBoxSizingDefaultTest's threading scan).
//
// Mutation (PLAN.md §2 L4, CBB pins), executed and logged in the lane note:
// the renderer with the named argument dropped turns this test red.

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ChildContainingBlockSeamWiringTest {

    /** ComponentRenderer.kt, found by walking up to the repo root (the SeamReachabilityTest walk-up). */
    private val renderer: String by lazy {
        val anchor = "runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt"
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, anchor).exists()) dir = dir.parentFile
        File(requireNotNull(dir) { "repo root not found" }, anchor).readText()
    }

    @Test
    fun `the child containing block is computed with the WPT capture mode`() {
        // The ONE caller of DynamicValueResolver.childContainingBlock threads the
        // same flag the sizing path draws the box-sizing with (both read
        // LocalWptCaptureMode.current), so frame and block cannot disagree.
        assertTrue(renderer.contains(
            ".childContainingBlock(effectiveProperties, containingBlock, wptCaptureMode = wptCaptureModeForStretch)"))
        // …and that flag is the capture-mode CompositionLocal, read in the same frame.
        assertTrue(renderer.contains("val wptCaptureModeForStretch = LocalWptCaptureMode.current"))
    }
}

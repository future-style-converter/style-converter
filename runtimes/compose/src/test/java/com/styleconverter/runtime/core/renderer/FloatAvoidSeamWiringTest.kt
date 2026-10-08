package com.styleconverter.runtime.core.renderer

// Wave 53 (lane L4 · float-avoid) — the SEAM WIRING pin, shipped INSIDE
// tools/titan/results/wave53-float-avoid/seam-1.patch with the hunk it pins
// (the wave-52 VerticalBlockFlowSeamWiringTest precedent), so the tree never
// carries a pin that is red by construction.
//
// Why: FloatAvoidPlanTest pins the pure gate + placement, but nothing there
// proves ComponentRenderer's block child loop CALLS them. A @Composable seam
// cannot execute in this module's plain JVM JUnit4 tests (FloatAvoidLayout is
// first executed on device), so this is a SOURCE-level pin — the module's
// idiom (SeamReachabilityTest, VerticalBlockFlowSeamWiringTest).
//
// Mutation M6 (PLAN.md §2 L4), executed and logged in the lane note: the
// renderer at HEAD bytes (seam branch absent) turns every test here red.

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatAvoidSeamWiringTest {

    /** ComponentRenderer.kt, found by walking up to the repo root (the SeamReachabilityTest walk-up). */
    private val renderer: File by lazy {
        val anchor = "runtimes/compose/src/main/java/com/styleconverter/runtime/" +
            "core/renderer/ComponentRenderer.kt"
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, anchor).exists()) dir = dir.parentFile
        File(requireNotNull(dir) { "repo root not found" }, anchor)
    }

    /** The seam block: from the shape binding to the float-run plan it must precede. */
    private fun seam(): String {
        val text = renderer.readText()
        // The branch's binding — its name is the seam's contract with this pin.
        val start = text.indexOf("val floatAvoidShape =")
        assertTrue("the float-avoid seam binding is missing", start >= 0)
        // The wave-19 float-run plan follows it in the SAME block child loop.
        val end = text.indexOf("val floatSegments =", start)
        assertTrue("the float-avoid branch no longer precedes the float-run plan", end > start)
        return text.substring(start, end)
    }

    @Test
    fun `the seam is capture-gated and yields to an active clearance scope`() {
        val s = seam()
        // G1 — composed-WPT capture only: the dark-stage 327 corpus keeps its Column.
        assertTrue(s.contains("if (LocalWptCaptureMode.current &&"))
        // A container inside a §9.5.2 scope keeps FloatClearance's adjustments.
        assertTrue(s.contains("LocalFloatClearancePlan.current"))
        assertTrue(s.contains("?.scopeIds?.contains(component.id) != true"))
        // The pure gate decides the shape (no inline gate logic in the seam).
        assertTrue(s.contains("com.styleconverter.runtime.layout.FloatAvoidPlan.shape(component)"))
    }

    @Test
    fun `floats render without the self-alignment wrapper and the BFC keeps the per-child path`() {
        val s = seam()
        // The adapter owns the geometry for the admitted shape.
        assertTrue(s.contains("com.styleconverter.runtime.layout.FloatAvoidLayout(floatAvoidShape, component.id)"))
        // Floats: floatEndAlignment's TopEnd wrapper must not place them twice.
        assertTrue(s.contains("if (index < floatAvoidShape.sides.size)"))
        assertTrue(s.contains("CompositionLocalProvider(LocalSelfAlignmentHandled provides true)"))
        // The BFC: the frozen per-child renderer, so its own rendering is unchanged.
        assertTrue(s.contains("renderBlockChild(index, child)"))
        // The branch ends the child loop — nothing else renders the children twice.
        assertTrue(s.contains("return\n"))
    }
}

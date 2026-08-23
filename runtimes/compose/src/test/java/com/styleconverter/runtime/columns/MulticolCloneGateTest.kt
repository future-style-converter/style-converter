package com.styleconverter.runtime.columns

// WAVE-46 LANE Y3 — the css-break-3 §5.2 `box-decoration-break: clone`
// pass' wiring + gates, pinned the way the suite pins every other measure
// integration (MulticolRunFragmentGateTest / MultiColumnMeasureDrawBridge-
// Test precedent — no Robolectric, so the real measure body is scanned):
//
//  (1) the clone helper runs INSIDE the sole-child branch, BEFORE the
//      slice branch's line-box probe — so a clone child never reaches the
//      −i·H slice replay when the helper engages;
//  (2) the helper measures the child CAPPED at H (the single-measure
//      trick: Modifier.height coerces the declared size into the max, so
//      the laid-out box IS one clone fragment);
//  (3) every bail is loud (content-bearing child, non-px band, bands
//      filling H) — repo no-silent-fallthrough rule;
//  (4) the B-RC6 bridge contract: the helper writes the bridge in ITS
//      placement block, and MultiColumnApplier.kt keeps exactly 3 touches
//      (MultiColumnMeasureDrawBridgeTest pins the count; this test pins
//      that the clone branch did not add a fourth).

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MulticolCloneGateTest {

    /** A repo file's text, located by walking up from the test working dir. */
    private fun source(rel: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, rel).exists()) dir = dir.parentFile
        return File(requireNotNull(dir) { "repo root ($rel) not found" }, rel).readText()
    }

    private val measureSource: String by lazy {
        source("runtimes/compose/src/main/java/com/styleconverter/runtime/columns/MulticolCloneMeasure.kt")
    }
    private val applierSource: String by lazy {
        source("runtimes/compose/src/main/java/com/styleconverter/runtime/columns/MultiColumnApplier.kt")
    }

    @Test
    fun `the clone pass sits in the sole-child branch ahead of the slice replay`() {
        // The branch opens, the clone helper is consulted, THEN the slice
        // branch's line-box probe and its unbounded measure follow.
        val branch = applierSource.indexOf("} else if (overflowing == 1) {")
        val cloneCall = applierSource.indexOf("measureCloneFragments(")
        val lineProbe = applierSource.indexOf("val lineBoxPx = if (captureMode)")
        val sliceMeasure = applierSource.indexOf("maxHeight = Constraints.Infinity", cloneCall)
        assertTrue("the sole-child branch must exist", branch >= 0)
        assertTrue("the clone helper must be consulted inside the sole-child branch",
            cloneCall > branch)
        assertTrue("the clone helper must run before the slice line-box probe",
            cloneCall < lineProbe)
        assertTrue("the slice branch's unbounded measure must follow the clone call",
            sliceMeasure > cloneCall)
        // The helper's null falls through (the `?.let { return@Layout it }`
        // idiom every measure twin uses) — slice stays byte-identical.
        assertTrue(applierSource.substring(cloneCall, lineProbe)
            .contains("?.let { return@Layout it }"))
    }

    @Test
    fun `the clone helper measures the child capped at the fragmentainer block-size`() {
        // The single-measure trick: maxHeight = H, never Infinity.
        assertTrue("clone measure must cap the child at H",
            measureSource.contains("maxHeight = columnBlockSizePx"))
        assertTrue("clone measure must never measure unbounded",
            !measureSource.contains("maxHeight = Constraints.Infinity"))
        // And the replay source is the K-table, not the slice S-table.
        assertTrue(measureSource.contains("MulticolCloneGeometry.cloneFragments("))
    }

    @Test
    fun `every clone bail is logged - no silent slice fallthrough`() {
        assertTrue("content-bearing clone child must log",
            measureSource.contains("clone on a content-bearing child"))
        assertTrue("non-px band must log",
            measureSource.contains("clone with a non-px decoration band"))
        assertTrue("bands filling H must log",
            measureSource.contains("clone bands fill the whole column block-size"))
        // The slice default and the no-spec dark stage are NOT fallthroughs
        // and must return before any log (byte-identical slice path).
        assertTrue(measureSource.contains("if (spec == null || !spec.cloneDeclared) return null"))
    }

    @Test
    fun `the bridge write lives in the helper's placement block and the applier keeps 3 touches`() {
        // Helper: the publish sits after its layout() call, before place().
        val layoutCall = measureSource.indexOf("return layout(constraints.maxWidth, columnBlockSizePx)")
        val write = measureSource.indexOf("fragmentsBridge.value = fragments")
        val place = measureSource.indexOf("placeable.place(0, 0)")
        assertTrue("clone publish must be inside its placement block",
            layoutCall in 0 until write && write < place)
        // Applier: the clone call threads the bridge STATE, never touches
        // its value — the B-RC6 count stays exactly 3.
        assertEquals(3, Regex(Regex.escape("fragmentsState.value")).findAll(applierSource).count())
        assertTrue("the clone call must thread the bridge state by reference",
            applierSource.contains("fragmentsBridge = fragmentsState,\n" +
                "                            logFallback = ::logFragmentationFallbackOnce"))
    }
}

package com.styleconverter.runtime.columns

// Wave-47 lane Z2 — the B-RC6 bridge-discipline pins for the vertical
// measure helper, mirroring MultiColumnMeasureDrawBridgeTest's source-scan
// style: the measure→draw snapshot state may be written ONLY from a
// placement block (a measure-phase touch re-registers the measure pass as a
// snapshot reader and revives the nested-multicol remeasure ping-pong), and
// MultiColumnApplier.kt itself must keep its exact 3 touches.

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerticalMulticolBridgeTest {

    /** Source text located by walking up from the test working dir. */
    private fun source(rel: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, rel).exists()) dir = dir.parentFile
        return File(requireNotNull(dir) { "repo root ($rel) not found" }, rel).readText()
    }

    private val helperSource: String by lazy {
        source("runtimes/compose/src/main/java/com/styleconverter/runtime/columns/VerticalMulticolMeasure.kt")
    }

    @Test
    fun `helper writes the bridge exactly once, inside its placement block`() {
        // ONE write in the whole helper…
        assertEquals(
            "fragmentsBridge.value must be touched exactly once",
            1,
            Regex(Regex.escape("fragmentsBridge.value")).findAll(helperSource).count())
        // …and it sits after the layout(...) { opener — the placement block
        // (the same textual discipline the clone/run helpers follow).
        val write = helperSource.indexOf("fragmentsBridge.value")
        val placement = helperSource.indexOf("return layout(")
        assertTrue(
            "the bridge write must live in the placement lambda",
            placement in 0 until write)
    }

    @Test
    fun `applier keeps its exact 3 fragmentsState touches and delegates vertical to the helper`() {
        val applier = source(
            "runtimes/compose/src/main/java/com/styleconverter/runtime/columns/MultiColumnApplier.kt")
        // The wave-21 pin, unchanged by the vertical pass (the helper owns
        // the vertical write).
        assertEquals(
            "MultiColumnApplier.kt must keep exactly 3 fragmentsState.value touches",
            3,
            Regex(Regex.escape("fragmentsState.value")).findAll(applier).count())
        // The vertical pass runs BEFORE every horizontal-tb pass: its
        // delegation must appear before the spanner-flow delegation.
        val vertical = applier.indexOf("VerticalMulticolMeasure")
        val spanner = applier.indexOf("with(MulticolSpannerFlowMeasure)")
        assertTrue("vertical pass must be delegated", vertical >= 0)
        assertTrue(
            "vertical pass must run before the horizontal passes",
            vertical < spanner)
    }
}

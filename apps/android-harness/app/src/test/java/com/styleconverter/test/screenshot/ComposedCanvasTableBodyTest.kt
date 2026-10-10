package com.styleconverter.test.screenshot

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.layout.position.CanvasRootHoist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * wave-53 lane L3 (item B) — the STACK consequence of the table-body rewrite,
 * through the REAL composed-canvas chain ([composedCanvasRoots] → the pure
 * [composedRootStackPlan] / [collapsedRootStackGapsPx] fold the Column spends).
 *
 * THE DEFECT: on wave52-ship CSS2/css21-errata/s-11-1-1b-006 the composed
 * Column stacked the caption div (1-142, `margin-bottom: 10px`) and the td
 * (2-143) as separate roots: a 10-px gap above the td, then its own −15
 * margin-top as an offset — square at image rows 51-70, ref 56-75 (android
 * P 0.9944, DEGENERATE). With the body laid out as a table (CSS 2.1 §17.2.1
 * rule 2) the div's margin lives INSIDE the anonymous cell and the td's
 * margin does not apply (§8.3), so nothing stands between the canvas-owned
 * body margin and the table: 0 gap above it.
 *
 * Payload VERBATIM from tools/titan/runs/wave52-ship/sections/CSS2/per-test-ir/.
 *
 * EXECUTED MUTATION (ScreenshotCaptureScreen.kt, this class run alone, source
 * restored byte-exact — sha256 in tools/titan/results/wave53-canvas-root/_note.md):
 *   BK  composedCanvasRoots skips TableBodyForest (`return owned`) → the
 *       caption div stays in the stack, the 10-px gap is back → red.
 */
class ComposedCanvasTableBodyTest {

    /** Decode + slot-compose a v2 document exactly as the harness does. */
    private fun rootsOf(componentsJson: String): List<IRComponent> =
        com.styleconverter.runtime.core.renderer.SlotComposer.compose(
            com.styleconverter.runtime.core.ir.IRDocumentDecoder.decode(
                """{"irVersion":2,"minReaderVersion":2,"components":[$componentsJson]}"""))

    /** CSS2/css21-errata/s-11-1-1b-006 — all four roots, verbatim. */
    private val s006 = """
        {"id":"wpt__css2__css21-errata__s-11-1-1b-006__0-141","name":"wpt__CSS2__css21-errata__s-11-1-1b-006__0","properties":[{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"},{"type":"Display","data":"TABLE"},{"type":"BorderSpacing","data":{"type":"single","px":0}},{"type":"MarginTop","data":{"px":40}},{"type":"MarginRight","data":{"px":8}},{"type":"MarginBottom","data":{"px":8}},{"type":"MarginLeft","data":{"px":8}}],"meta":{"role":"body-root"}},
        {"id":"wpt__css2__css21-errata__s-11-1-1b-006__1-142","name":"wpt__CSS2__css21-errata__s-11-1-1b-006__1","properties":[{"type":"Generic","data":{"propertyName":"display","rawValue":"caption","_unmapped":true}},{"type":"MarginBottom","data":{"px":10}}],"meta":{"role":"ws-after"}},
        {"id":"wpt__css2__css21-errata__s-11-1-1b-006__2-143","name":"wpt__CSS2__css21-errata__s-11-1-1b-006__2","properties":[{"type":"Display","data":"TABLE_CELL"},{"type":"Width","data":{"type":"length","px":20}},{"type":"Height","data":{"type":"length","px":20}},{"type":"MarginTop","data":{"px":-15}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"meta":{"role":"ws-after"}},
        {"id":"wpt__css2__css21-errata__s-11-1-1b-006__3-144","name":"wpt__CSS2__css21-errata__s-11-1-1b-006__3","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Top","data":{"px":0}},{"type":"Left","data":{"px":8}}],"text":"Test passes if there is a black square below.","meta":{"sourceTag":"p"}}
    """.trimIndent()

    @Test
    fun stack_zeroGapAboveTheSyntheticTable_tdIsNoRoot() {
        val raw = rootsOf(s006)
        // The chain the composable stacks, with the margin it resolves (40/8/8/8).
        val roots = composedCanvasRoots(withCanvasOwnedBodyMargin(raw, resolveComposedCanvasMargin(raw))
            .map(::withUaBlockMarginOnHoistedRoot), resolveComposedCanvasRootBackground(raw))
        // The td is no longer a root: it lives in the synthetic table's row.
        assertTrue(roots.none { it.id == raw[2].id })
        val tableIdx = roots.indexOfFirst { it.id == raw[0].id + "#table" }
        assertEquals(1, tableIdx)
        // The fold the Column spends: no gap above the table (nor above the body-root).
        val hostActive = CanvasRootHoist.hostActivates(roots)
        val gaps = collapsedRootStackGapsPx(roots.map { composedRootStackPlan(it, hostActive) })
        assertEquals(0f, gaps[0], 0f)
        assertEquals(0f, gaps[tableIdx], 0f)
    }
}

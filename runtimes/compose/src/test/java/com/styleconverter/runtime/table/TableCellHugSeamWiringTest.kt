package com.styleconverter.runtime.table

// Wave 54 (lane L2 · table-body-cell, unit TB-android) — the SEAM WIRING pin,
// shipped INSIDE tools/titan/results/wave54-table-body-cell/seam-1.patch with
// the ComponentRenderer.kt hunk it pins (the wave-53 FloatAvoidSeamWiringTest
// precedent), so the shared tree never carries a pin that is red by
// construction before the seam lands (build-workflow rule 2b).
//
// Why: TableCellHugTest pins the pure chrome decision and TableApplier's
// half of the wiring, but nothing there proves the renderer's
// `DisplayType.TABLE ->` branch CALLS the decision and spends BOTH halves of
// it. A @Composable seam cannot execute in this module's plain JVM JUnit
// tests (the per-cell intrinsic read is first executed on device, PLAN §8
// risk 1), so this is a SOURCE-level pin — the module's idiom
// (SeamReachabilityTest, FloatAvoidSeamWiringTest).
//
// Mutations, executed with the patch applied and logged in the lane note
// (tools/titan/results/wave54-table-body-cell/_note.md): the renderer at HEAD
// bytes (no seam), `fabricatedCellBorder` back to the inline expression, and
// `cellsHugContent = chrome.hug` dropped — each turns this class red.

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TableCellHugSeamWiringTest {

    /** ComponentRenderer.kt, found by walking up to the repo root (the SeamReachabilityTest walk-up). */
    private val renderer: File by lazy {
        val anchor = "runtimes/compose/src/main/java/com/styleconverter/runtime/" +
            "core/renderer/ComponentRenderer.kt"
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, anchor).exists()) dir = dir.parentFile
        File(requireNotNull(dir) { "repo root not found" }, anchor)
    }

    /** The TABLE branch: from its `DisplayType.TABLE ->` arm to the content lambda it ends in. */
    private fun branch(): String {
        val text = renderer.readText()
        // The display switch's table arm — the only TableApplier.Table caller.
        val start = text.indexOf("DisplayType.TABLE -> {")
        assertTrue("the DisplayType.TABLE arm is missing", start >= 0)
        // The arm ends in the table's row loop; the hunk lives before it.
        val end = text.indexOf("RenderTableContent(component, textColor)", start)
        assertTrue("the TABLE arm no longer renders RenderTableContent", end > start)
        return text.substring(start, end)
    }

    @Test
    fun `the arm computes the chrome from today's stroke expression, verbatim`() {
        val b = branch()
        // The pure decision (TableCellHug.chrome) is computed BEFORE the call.
        val decide = b.indexOf("val chrome = com.styleconverter.runtime.table.TableCellHug.chrome(")
        assertTrue("the arm does not compute TableCellHug.chrome", decide >= 0)
        assertTrue("chrome is not computed before TableApplier.Table(", decide < b.indexOf("TableApplier.Table("))
        // The wave-38 predicate rides in as fabricatedDefault, unchanged —
        // the byte-identity argument for every non-anonymous table.
        assertTrue(b.contains("fabricatedDefault = !(LocalWptCaptureMode.current &&"))
        assertTrue(b.contains("component.properties.none { it.type == \"Display\" } &&"))
        assertTrue(b.contains(".uaRoleOf(component._tag) =="))
        assertTrue(b.contains("com.styleconverter.runtime.table.TableBoxTree.Role.TABLE)"))
    }

    @Test
    fun `the call spends both halves of the chrome and no inline stroke expression is left`() {
        val b = branch()
        // D1: the stroke reads the decision, not a second copy of the predicate.
        assertTrue(b.contains("fabricatedCellBorder = chrome.stroke,"))
        assertFalse("an inline fabricatedCellBorder expression survived", b.contains("fabricatedCellBorder = !("))
        // D2: the hug reaches TableApplier.Table, exactly once.
        assertEquals(1, Regex("""cellsHugContent = chrome\.hug,""").findAll(b).count())
    }
}

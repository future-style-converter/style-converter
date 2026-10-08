package com.styleconverter.runtime.typography

// Wave 54 (lane L5 · ua-heading-face, unit U1-android) — the SEAM WIRING pin,
// shipped INSIDE tools/titan/results/wave54-ua-heading-face/seam-1.patch with
// the ComponentRenderer.kt hunk it pins (the FloatAvoidSeamWiringTest /
// TableCellHugSeamWiringTest precedent), so the shared tree never carries a
// pin that is red by construction before the seam lands (rule 2b).
//
// Why: UAElementFontRuleTest pins the rule and its stand-down verdict through
// a REPLAY of the seam's cascade step, which stays green whether or not — and
// however — the renderer calls it (wave-54 L5 skeptic, finding 3).
// RenderComponent is a @Composable that this module's plain JVM JUnit cannot
// execute, so this is a SOURCE-level pin of the four facts the replay
// assumes: (1) the UA step reads the ListStyleUaRule output, (2) with the
// element's tag and post-reset OWN list, (3) with `standsDown =
// UAElementFontRule.headingStandsDown(component)`, and (4) its result is the
// list DynamicValueResolver resolves — the step runs BEFORE the resolver, so
// `em` / `ch` on a heading read the UA face.
//
// Mutations (executed, lane note "Fix pass"): HEAD's renderer (no seam);
// `standsDown = false`; the merged list in place of the ListStyleUaRule
// output; the resolver fed the pre-UA list.

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UAElementFontRuleSeamWiringTest {

    /** ComponentRenderer.kt, found by walking up to the repo root (the SeamReachabilityTest walk-up). */
    private val renderer: File by lazy {
        val anchor = "runtimes/compose/src/main/java/com/styleconverter/runtime/" +
            "core/renderer/ComponentRenderer.kt"
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, anchor).exists()) dir = dir.parentFile
        File(requireNotNull(dir) { "repo root not found" }, anchor)
    }

    /** The renderer source with whitespace runs collapsed: the pins read tokens, not layout. */
    private val source: String by lazy { renderer.readText().replace(Regex("\\s+"), " ") }

    /** How many times [needle] occurs in the flattened source. */
    private fun count(needle: String): Int = source.split(needle).size - 1

    /** (1) The list UA rule's output, bound once — the UA font step's input. */
    private val listStep = "val listUaProperties = com.styleconverter.runtime.lists.ListStyleUaRule.apply( " +
        "component._tag, allReset.own, mergeInherited(allReset.own, allReset.inherited) )"

    /** (2) + (3) The UA font step, verbatim: tag, OWN list, the list above, the stand-down verdict. */
    private val fontStep = "val rawProperties = com.styleconverter.runtime.typography.UAElementFontRule.apply( " +
        "component._tag, allReset.own, listUaProperties, " +
        "standsDown = com.styleconverter.runtime.typography.UAElementFontRule.headingStandsDown(component), )"

    @Test
    fun `the UA font step wraps the list UA rule's output with the tag, own list and stand-down verdict`() {
        // Each step exists exactly once, in cascade order (list UA rule, then the font step).
        assertEquals("the ListStyleUaRule binding", 1, count(listStep))
        assertEquals("the UA font step call", 1, count(fontStep))
        assertTrue(source.indexOf(listStep) < source.indexOf(fontStep))
        // The font step is the renderer's ONLY call of the rule (no second, post-resolver site).
        assertEquals(1, count("UAElementFontRule.apply("))
    }

    @Test
    fun `DynamicValueResolver resolves the UA step's output, after it`() {
        // The font step's result is the pre-resolution list…
        val step = source.indexOf(fontStep)
        assertTrue("the UA font step call is missing", step >= 0)
        val handoff = source.indexOf("val unresolvedProperties = rawProperties ")
        assertTrue("rawProperties must flow to unresolvedProperties", handoff > step)
        // …and the resolver reads that list after it (so `em` / `ch` see the UA face).
        val resolve = source.indexOf(".resolve(unresolvedProperties, varScope.variables, dynCtx)")
        assertTrue("the resolver must follow the UA step", resolve > handoff)
    }
}

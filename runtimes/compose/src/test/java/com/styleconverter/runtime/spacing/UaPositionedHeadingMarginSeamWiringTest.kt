package com.styleconverter.runtime.spacing

// Wave 55 (lane L4 · ua-heading-margin, unit U1-android) — the SEAM WIRING
// pin, shipped INSIDE tools/titan/results/wave55-ua-heading-margin/seam-1.patch
// with the ComponentRenderer.kt hunk it reads (the UAElementFontRuleSeamWiringTest
// precedent), so the tree never carries a pin that is red by construction
// before the seam lands (rule 2b).
//
// Why: UaPositionedHeadingMarginTest pins the step on verbatim wire through a
// replay, which stays green whatever the renderer does with it. RenderComponent
// is a @Composable this module's plain JVM JUnit cannot execute, so this is a
// SOURCE-level pin of the facts the replay assumes: (1) the step runs once,
// with the element's tag and post-reset OWN list, the ListStyleUaRule output
// as its em base and the UA font step's output as the list it extends;
// (2) it is gated on the ambient WPT-capture flag; (3) it sits after the font
// step and before the list DynamicValueResolver resolves.
//
// Mutations (executed, lane note "Pins"): HEAD's renderer (no seam);
// `enabled = true`; the step fed `listUaProperties` as the list it extends.

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UaPositionedHeadingMarginSeamWiringTest {

    /** ComponentRenderer.kt, found by walking up to the repo root (the SeamReachabilityTest walk-up). */
    private val renderer: File by lazy {
        // The seam file's repo-relative path: the walk stops where it exists.
        val anchor = "runtimes/compose/src/main/java/com/styleconverter/runtime/" +
            "core/renderer/ComponentRenderer.kt"
        // Start at the Gradle working directory (apps/android-harness/…).
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        // Climb until the anchor is visible (the repo root).
        while (dir != null && !File(dir, anchor).exists()) dir = dir.parentFile
        // A missing root is a broken pin, never a skip.
        File(requireNotNull(dir) { "repo root not found" }, anchor)
    }

    /** The renderer source with whitespace runs collapsed: the pins read tokens, not layout. */
    private val source: String by lazy { renderer.readText().replace(Regex("\\s+"), " ") }

    /** How many times [needle] occurs in the flattened source. */
    private fun count(needle: String): Int = source.split(needle).size - 1

    /** (1) + (2) The margin step, verbatim: tag, OWN list, the pre-font list, the font step's output, the flag. */
    private val marginStep = "val uaMarginProperties = com.styleconverter.runtime.spacing.UaPositionedHeadingMargin.apply( " +
        "component._tag, allReset.own, listUaProperties, rawProperties, enabled = LocalWptCaptureMode.current, )"

    /** The UA font step's binding, whose output the margin step extends. */
    private val fontStep = "val rawProperties = com.styleconverter.runtime.typography.UAElementFontRule.apply("

    @Test
    fun `the margin step runs once, capture-gated, on the font step's output`() {
        // Exactly one call, with exactly these arguments.
        assertEquals("the UA margin step call", 1, count(marginStep))
        // No second site anywhere in the renderer.
        assertEquals(1, count("UaPositionedHeadingMargin.apply("))
    }

    @Test
    fun `the margin step sits after the font step and before the resolver's input`() {
        // After the UA font step (it reads rawProperties)…
        val step = source.indexOf(marginStep)
        assertTrue("the margin step must follow the UA font step", step > source.indexOf(fontStep))
        // …its output is the pre-resolution list…
        val handoff = source.indexOf("val unresolvedProperties = uaMarginProperties ")
        assertTrue("uaMarginProperties must flow to unresolvedProperties", handoff > step)
        // …which DynamicValueResolver resolves afterwards.
        val resolve = source.indexOf(".resolve(unresolvedProperties, varScope.variables, dynCtx)")
        assertTrue("the resolver must follow the margin step", resolve > handoff)
    }
}

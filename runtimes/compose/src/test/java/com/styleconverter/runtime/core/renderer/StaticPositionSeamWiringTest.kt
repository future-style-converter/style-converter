package com.styleconverter.runtime.core.renderer

// Wave 52 (lane L7, static-position T1) — SOURCE pins for the two seam hunks
// lane L7 delivers in tools/titan/results/wave52-static-position/seam-1.patch
// (this file ships INSIDE that patch, so it lands with the hunks it pins).
// RenderComponent's RC1 branch and extractDisplayConfig are private and need
// a composition to execute, which this JVM suite has no Robolectric for; the
// pure halves are pinned in AbsposOverflowMeasureTest (K1a–K1c) and
// GridAbsposPartitionTest (K2a/K2b). These pins prove the seam CALLS them:
//   SW1 the RC1 call passes `staticPositionOwned =` the identity test
//       against CanvasRootHoist.LocalStaticPositionOwner (never a Boolean
//       local, never LocalHasPositionedAncestor);
//   SW2 the AlignItems fold delegates to GridRenderer.foldAlignItems and the
//       pre-wave-52 inline table (no self-end arm) is gone.
// NEGATIVE CONTROL EXECUTED: run on bare HEAD (seam not applied) → SW1 and
// SW2 fail (tools/titan/results/wave52-static-position/_seam1-verify.log).

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticPositionSeamWiringTest {

    /** ComponentRenderer.kt, found by walking up from the JVM working dir to
     *  the repo root (the module's Gradle rootDir is apps/android-harness). */
    private val renderer: File by lazy {
        val anchor = "runtimes/compose/src/main/java/com/styleconverter/runtime/" +
            "core/renderer/ComponentRenderer.kt"
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, anchor).exists()) dir = dir.parentFile
        File(requireNotNull(dir) { "repo root not found" }, anchor)
    }

    /** The text between [from] and the next [to] after it (the hunk's block). */
    private fun block(text: String, from: String, to: String): String {
        val start = text.indexOf(from)
        assertTrue("anchor not found: $from", start >= 0)
        val end = text.indexOf(to, start + from.length)
        assertTrue("end anchor not found: $to", end > start)
        return text.substring(start, end)
    }

    @Test
    fun `SW1 the RC1 branch passes the grid overlay's ownership by identity`() {
        // The `val itemModifier = if (hoistHostActive && … )` expression.
        val rc1 = block(renderer.readText(), ".rendersInFlowAsStaticPosition(", "zeroFlowAnchor(")
        assertTrue("fifth argument missing", rc1.contains("staticPositionOwned ="))
        assertTrue("must compare the owner local to THIS instance",
            rc1.contains("LocalStaticPositionOwner.current === component"))
        assertFalse("must not reuse the positioned-ancestor channel",
            rc1.contains("staticPositionOwned = hoistHasPositionedAncestor"))
    }

    @Test
    fun `SW2 the AlignItems fold delegates to the pinned pure table`() {
        val fold = block(renderer.readText(), "\"AlignItems\" -> {", "\"AlignContent\" -> {")
        assertTrue("fold must call GridRenderer.foldAlignItems",
            fold.contains("GridRenderer") && fold.contains(".foldAlignItems(keyword)"))
        assertFalse("the inline table without self-end must be gone",
            fold.contains("\"FLEX_END\", \"FLEX-END\", \"END\" -> AlignItems.FLEX_END"))
    }
}

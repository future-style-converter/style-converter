package com.styleconverter.runtime.core.renderer

// Wave 55 (lane L3, U1) — SOURCE pins for the one seam hunk lane L3 delivers
// in tools/titan/results/wave55-positioned-parent-slot/seam-1.patch (this
// file ships INSIDE that patch, so it lands with the hunk it pins). The
// block-child loop's mount is composed inside RenderContent, which this JVM
// suite cannot execute (no Robolectric), so — the StaticPositionSeamWiringTest
// idiom — these pins prove the seam CALLS the pinned pure gate
// (PositionedParentSlotGateTest) with the right three flags:
//   PW1 the mount is PositionedParentSlotGate.slotModifier(…), fed by the
//       canvas host, the composed-capture local and the PARENT's layout
//       direction;
//   PW2 the pre-wave-55 host-only gate (`LocalActive.current &&` on a direct
//       PositionedParentFlowSlot call) is gone from the block-child loop.
// NEGATIVE CONTROL EXECUTED: run against the pre-seam ComponentRenderer.kt
// → PW1 and PW2 fail (the bracketed mutation in the lane note).

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionedParentSlotSeamWiringTest {

    /** ComponentRenderer.kt, found by walking up from the JVM working dir to
     *  the repo root (the module's Gradle rootDir is apps/android-harness). */
    private val renderer: File by lazy {
        // The seam file's repo-relative path is the walk-up anchor.
        val anchor = "runtimes/compose/src/main/java/com/styleconverter/runtime/" +
            "core/renderer/ComponentRenderer.kt"
        // Start at the JVM's working directory and climb until it resolves.
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, anchor).exists()) dir = dir.parentFile
        File(requireNotNull(dir) { "repo root not found" }, anchor)
    }

    /** The block-child loop: from its lambda header to the child body that
     *  consumes the mount (the whole region the seam hunk owns). */
    private fun blockChildLoop(): String {
        // Both anchors are code, not prose, and occur once in the file.
        val text = renderer.readText()
        val start = text.indexOf("val renderBlockChild: @Composable (Int, IRComponent) -> Unit")
        assertTrue("renderBlockChild not found", start >= 0)
        val end = text.indexOf("val blockChildBody: @Composable () -> Unit", start)
        assertTrue("blockChildBody not found after renderBlockChild", end > start)
        return text.substring(start, end)
    }

    @Test
    fun `PW1 the block-child mount is the gate's helper, fed by all three flags`() {
        // The one call that decides the out-of-flow child's slot.
        val loop = blockChildLoop()
        assertTrue("mount must be PositionedParentSlotGate.slotModifier(",
            loop.contains("PositionedParentSlotGate") && loop.contains(".slotModifier("))
        // The canvas host keeps every pre-wave-55 mount.
        assertTrue("hostActive must read CanvasRootHoist.LocalActive",
            loop.contains("hostActive = com.styleconverter.runtime.layout.position") &&
                loop.contains(".CanvasRootHoist.LocalActive.current"))
        // The composed capture itself engages the slot (U1).
        assertTrue("composedCapture must read LocalWptComposedMode",
            loop.contains("composedCapture = LocalWptComposedMode.current"))
        // The PARENT's laid-out direction, read in its content scope.
        assertTrue("parentRtl must read the parent's LocalLayoutDirection",
            loop.contains("parentRtl = LocalLayoutDirection.current == LayoutDirection.Rtl"))
    }

    @Test
    fun `PW2 the host-only gate is gone from the block-child loop`() {
        // The pre-seam expression gated the mount on the host alone.
        val loop = blockChildLoop()
        assertFalse("the LocalActive-only guard must be gone",
            loop.contains("LocalActive.current &&"))
        // The plain mount is reached only through the gate now.
        assertFalse("no direct PositionedParentFlowSlot.mount( call in the loop",
            loop.contains("PositionedParentFlowSlot.mount("))
    }
}

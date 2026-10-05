package com.styleconverter.test.screenshot

// wave-52 lane L2 (Fix A) — SOURCE-SCAN pins that the composed canvas's
// inline-axis clip is WIRED where the contract says. The geometry itself is
// the runtime's pure helper, pinned on the JVM in runtimes/compose
// ComposedIcbClipTest; this file pins the Modifier chain, which plain
// junit:4.13.2 cannot run (no Robolectric / Compose UI test / emulator —
// the same technique as HarnessLabelChromeSourceTest and
// ComposedFullHeightCaptureTest).
//
// THE CONTRACT (page-padding-overrun brief §4 Fix A, Android):
//   1. exactly ONE clip draw node in the COMPOSED canvas, and it calls the
//      runtime helper with the FRAME (`CaptureCanvasFrame.toPx()`), never
//      the resolved body pad (`canvasPadding`) — CSS 2.1 §9.1.1: an author
//      `body { padding }` moves neither the viewport nor its crop;
//   2. the node sits AFTER the `.background(canvasBackground)` chain (an
//      earlier draw modifier wraps the later ones, so the background stays
//      OUTSIDE the clip and the frame keeps the propagated body colour) and
//      BEFORE the `.testTag("composed-capture-canvas")` that ends the chain;
//   3. the clip is `clipRect` on the band's two x-edges with the finite
//      vertical slack on BOTH y-edges — the block axis stays visible.
//
// EXECUTED MUTATIONS (applied to ScreenshotCaptureScreen.kt, this class run
// alone, source restored byte-exact — sha256 checked; record in
// tools/titan/results/wave52-composed-canvas/_note.md):
//   M1 `CaptureCanvasFrame.toPx()` → `canvasPadding.left.toPx()` (the
//      resolved pad) → clip_usesTheFrameNotTheResolvedPad red.
//   M2 the whole `.drawWithContent { clipRect … }` node moved ABOVE the
//      background chain → clip_sitsAfterTheBackgroundChain red.

// Exact-count pins (one helper call, one clipRect).
import org.junit.Assert.assertEquals
// Absence pin: the resolved pad never reaches the helper.
import org.junit.Assert.assertFalse
// Presence / ordering pins on the sliced source.
import org.junit.Assert.assertTrue
// JUnit 4's test marker — the only test API this module has.
import org.junit.Test
// The scanned source is read from disk, located by a repo-root walk-up.
import java.io.File

class ComposedCanvasIcbClipSourceTest {
    // Repo root by walk-up (this build's rootDir is apps/android-harness).
    private val repoRoot: File by lazy {
        // The file whose presence proves a directory is the repo root.
        val anchor = "apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt"
        // Start at the JVM's working directory and climb.
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        // Stop at the first ancestor that contains the anchor.
        while (dir != null && !File(dir, anchor).exists()) dir = dir.parentFile
        // A missing root is a test-setup error, named loudly.
        requireNotNull(dir) { "repo root ($anchor) not found above ${System.getProperty("user.dir")}" }
    }
    // The whole harness screen source.
    private val screenSource: String by lazy {
        File(repoRoot, "apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt").readText()
    }
    // The COMPOSED canvas only: its declaration → the completion view after it.
    private val composedRegion: String by lazy {
        // Both anchors must exist or the slice would silently be the whole file.
        assertTrue("ComposedCaptureCanvas declaration missing", screenSource.contains("private fun ComposedCaptureCanvas("))
        assertTrue("CompleteView declaration missing", screenSource.contains("private fun CompleteView("))
        screenSource.substringAfter("private fun ComposedCaptureCanvas(").substringBefore("private fun CompleteView(")
    }
    // CODE LINES ONLY of the composed region: `//` lines dropped, so prose
    // that merely mentions a call can never satisfy a wiring pin.
    private val composedCode: String by lazy {
        composedRegion.lines().filter { !it.trim().startsWith("//") }.joinToString("\n")
    }

    @Test
    fun clip_callsTheRuntimeHelperExactlyOnce() {
        // One band computation in the composed canvas — a second call would
        // be a second owner of the crop edge.
        assertEquals(1, composedCode.split("composedIcbClipBandPx(").size - 1)
        assertEquals(1, composedCode.split("clipRect(").size - 1)
    }

    @Test
    fun clip_usesTheFrameNotTheResolvedPad() {
        // The helper is fed the FRAME in px (CSS 2.1 §9.1.1 — the viewport's
        // crop does not move with an author body padding). Mutation M1 red.
        assertTrue(composedCode.contains("composedIcbClipBandPx(size.width, CaptureCanvasFrame.toPx())"))
        // The resolved pad (frame + declared) never reaches the helper.
        assertFalse(composedCode.contains("composedIcbClipBandPx(size.width, canvasPadding"))
    }

    @Test
    fun clip_sitsAfterTheBackgroundChain_andBeforeTheTestTag() {
        // Chain order in the composed Box modifier: background chain →
        // clip draw node → testTag. Positions are taken on CODE lines only.
        val bg = composedCode.indexOf("?: Modifier.background(canvasBackground)")
        val clip = composedCode.indexOf("composedIcbClipBandPx(")
        val tag = composedCode.indexOf(".testTag(\"composed-capture-canvas\")")
        assertTrue("background chain anchor missing", bg >= 0)
        assertTrue("clip node missing", clip >= 0)
        assertTrue("testTag anchor missing", tag >= 0)
        // Mutation M2 (node moved above the background) breaks the first inequality.
        assertTrue("clip must follow the background chain", bg < clip)
        assertTrue("clip must precede the testTag", clip < tag)
    }

    @Test
    fun clip_isInlineAxisOnly_withFiniteVerticalSlack() {
        // The clipRect's x-edges are the band's; both y-edges carry the
        // finite slack so the block axis stays visible (the ref never crops
        // the bottom). Read from the code lines, not the prose.
        assertTrue(composedCode.contains("left = band.leftPx"))
        assertTrue(composedCode.contains("right = band.rightPx"))
        assertTrue(composedCode.contains("top = -COMPOSED_ICB_CLIP_VERTICAL_SLACK_PX"))
        assertTrue(composedCode.contains("bottom = size.height + COMPOSED_ICB_CLIP_VERTICAL_SLACK_PX"))
    }
}

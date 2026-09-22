package com.styleconverter.test.screenshot

// Wave 51, PR A — the harness debug-label CHROME on Android, part 2 of 2:
// SOURCE-SCAN pins that the chrome is WIRED where the contract says; the
// contract and the geometry pins are HarnessLabelChromeTest.kt (part 1).
//
// WHY A SOURCE SCAN: plain junit:4.13.2 here (no Robolectric / Compose UI
// test / emulator), so the composable cannot run. P2/P3 read the source
// (ComposedFullHeightCaptureTest's technique), sliced FIRST to the
// per-component CaptureCanvas so the composed canvas's own `drawWithContent`
// (its offscreen-layer record) cannot satisfy a chrome pin, and the draw
// block is scanned with its `//` lines stripped so prose that merely
// MENTIONS `drawContent()` cannot satisfy an ordering pin either.
//
// NEGATIVE CONTROLS, EXECUTED — mutation applied to the working tree, this
// class run alone, source restored byte-exact (sha256) afterwards. Durable
// record: tools/titan/results/wave51-A/skeptic-android.md (the skeptic ran
// the lane's M1/M2 as S2/S1; its own S4/S5 were GREEN 11/11 before the two
// pins below existed — the should-fix defects this file closes). The polish
// lane re-ran all four against THIS file on 2026-09-22:
//   M1 `.drawWithContent { … }` moved AFTER `.padding(CaptureCanvasPadding)`
//      → drawNode_sitsBetweenOnGloballyPositionedAndPadding red (1 of 7).
//   M2 `!LocalWptCaptureMode.current &&` dropped from showsLabel
//      → predicate_isGatedByTheWptCaptureFlag red (1 of 7).
//   S4 `drawContent()` moved AFTER the label loop (chrome painted UNDER the
//      component) → drawOrder_contentPaintsBeforeTheLabel red (1 of 7).
//   S5 `drawRect(Color(0xB2EDEDED), …)` for `BlockLabel.COLOR` (alpha 178 →
//      (173,173,179)) → drawColour_isTheSharedBlockLabelColor red (1 of 7).
// All 7 green on the shipped tree.

// Exact-count pins (one drawContent() call; every drawRect uses the colour).
import org.junit.Assert.assertEquals
// Absence pins: no chrome in the composed canvas, no composable in BlockLabel.
import org.junit.Assert.assertFalse
// Presence / ordering pins on the sliced source.
import org.junit.Assert.assertTrue
// JUnit 4's test marker — the only test API this module has.
import org.junit.Test
// The scanned sources are read from disk, located by a repo-root walk-up.
import java.io.File

// One class per pin family, so a filtered `--tests` run is one gradle line.
class HarnessLabelChromeSourceTest {
    // Repo root by walk-up (this build's rootDir is apps/android-harness), so
    // the pins hold from any working directory gradle happens to use.
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
    // The whole harness screen source — the ONLY place the label is drawn.
    private val screenSource: String by lazy {
        File(repoRoot, "apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt").readText()
    }
    // The per-component canvas ONLY (declaration → the helper after it); the
    // composed canvas's own `drawWithContent` sits OUTSIDE this slice (C34).
    private val captureCanvasRegion: String by lazy {
        // Slice start: the per-component canvas's declaration.
        val start = "private fun CaptureCanvas("
        // Slice end: the first top-level helper declared after it.
        val end = "internal fun isOutOfFlowRoot("
        // Both anchors must exist or the slice would silently be the whole file.
        assertTrue("CaptureCanvas declaration missing", screenSource.contains(start))
        assertTrue("isOutOfFlowRoot declaration missing", screenSource.contains(end))
        // The text between the two anchors.
        screenSource.substringAfter(start).substringBefore(end)
    }
    // The composed canvas: from its declaration to the completion view.
    private val composedCanvasRegion: String by lazy {
        screenSource.substringAfter("private fun ComposedCaptureCanvas(").substringBefore("private fun CompleteView(")
    }
    // The `.drawWithContent {` block of the per-component slice, CODE LINES
    // ONLY: `//` lines are dropped so the ordering / colour pins read the
    // calls that execute, never the block's own "drawContent() runs first".
    private val drawBlockCode: String by lazy {
        // The block body: after the modifier's opening, before the next modifier.
        val block = captureCanvasRegion.substringAfter(".drawWithContent {").substringBefore(".padding(CaptureCanvasPadding)")
        // Keep every line that is not a line comment.
        block.lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
    }

    // Occurrence count of [needle] in [haystack] (0 if absent).
    private fun count(haystack: String, needle: String): Int = haystack.windowed(needle.length).count { it == needle }
    // ── P2: source scan, SLICED to the per-component CaptureCanvas ─────────

    // The geometry pin's bridge: the node paints the SAME rect list P1 pins.
    @Test
    fun captureCanvas_consumesTheSharedGeometry() {
        // The exact call, keyed on the composed root's name and the FRAME width.
        assertTrue("CaptureCanvas must call harnessLabelRects(component.name, widthPx)", captureCanvasRegion.contains("harnessLabelRects(component.name, widthPx)"))
    }

    // The coordinate-space pin: the mutation M1 turns exactly this red.
    @Test
    fun drawNode_sitsBetweenOnGloballyPositionedAndPadding() {
        // The node's space must be the unpadded outer rect onGloballyPositioned
        // reports and PixelCopy crops: AFTER .onGloballyPositioned, BEFORE
        // .padding(CaptureCanvasPadding). After .padding, (8,6) would be (24,22).
        val positioned = captureCanvasRegion.indexOf(".onGloballyPositioned")
        // The padding modifier that would shift the origin by (16,16).
        val padding = captureCanvasRegion.indexOf(".padding(CaptureCanvasPadding)")
        // The chrome's draw modifier.
        val draw = captureCanvasRegion.indexOf(".drawWithContent {")
        // All three anchors must exist for the ordering below to mean anything.
        assertTrue("onGloballyPositioned missing from CaptureCanvas", positioned >= 0)
        assertTrue(".padding(CaptureCanvasPadding) missing from CaptureCanvas", padding >= 0)
        assertTrue(".drawWithContent { missing from CaptureCanvas", draw >= 0)
        // The window: positioned < draw < padding.
        assertTrue("drawWithContent must follow .onGloballyPositioned", draw > positioned)
        assertTrue("drawWithContent must precede .padding(CaptureCanvasPadding)", draw < padding)
        // EVERY mention lies in that window: no second node hides after padding.
        var at = captureCanvasRegion.indexOf("drawWithContent")
        // Walk each mention.
        while (at >= 0) {
            // Each one is inside the window.
            assertTrue("a drawWithContent mention at $at sits outside the positioned..padding window", at > positioned && at < padding)
            // Advance past it.
            at = captureCanvasRegion.indexOf("drawWithContent", at + 1)
        }
    }

    // The draw-ORDER pin (skeptic should-fix 2, mutation S4): the component's
    // paint lands first, the chrome over it — never the label under the box.
    @Test
    fun drawOrder_contentPaintsBeforeTheLabel() {
        // Where the content paint is issued (code lines only).
        val content = drawBlockCode.indexOf("drawContent()")
        // Where the chrome's predicate opens the label branch.
        val predicate = drawBlockCode.indexOf("if (showsLabel)")
        // Where the first label pixel is painted.
        val rect = drawBlockCode.indexOf("drawRect(")
        // All three must exist in the executable part of the block.
        assertTrue("drawContent() missing from the drawWithContent block", content >= 0)
        assertTrue("if (showsLabel) missing from the drawWithContent block", predicate >= 0)
        assertTrue("drawRect( missing from the drawWithContent block", rect >= 0)
        // Order: content, then the gated branch, then the rects.
        assertTrue("drawContent() must precede if (showsLabel)", content < predicate)
        assertTrue("if (showsLabel) must precede drawRect(", predicate < rect)
        // Exactly one content paint: a second call after the loop would put
        // the component back OVER the chrome and pass the ordering above.
        assertEquals("exactly one drawContent() call", 1, count(drawBlockCode, "drawContent()"))
    }

    // The colour-USE pin (skeptic should-fix 3, mutation S5): BlockLabelTest
    // pins the constant's VALUE; this pins that the harness paints WITH it.
    @Test
    fun drawColour_isTheSharedBlockLabelColor() {
        // The literal first argument of the rect paint is the shared constant.
        assertTrue("the label rect must be painted with BlockLabel.COLOR", drawBlockCode.contains("drawRect(BlockLabel.COLOR,"))
        // No other Color( literal may exist in the block (no second ink).
        assertFalse("no Color( literal may appear in the drawWithContent block", drawBlockCode.contains("Color("))
        // EVERY drawRect in the block passes it — one stray rect is one stray colour.
        assertEquals("every drawRect( must pass BlockLabel.COLOR", count(drawBlockCode, "drawRect("), count(drawBlockCode, "drawRect(BlockLabel.COLOR,"))
    }

    // The gate pin: the mutation M2 turns exactly this red.
    @Test
    fun predicate_isGatedByTheWptCaptureFlag() {
        // TITAN (inbox/WPT) captures must stay label-free: the predicate reads
        // the SAME ambient flag the runtime's suppression gate reads.
        assertTrue("showsLabel must be gated on !LocalWptCaptureMode.current", captureCanvasRegion.contains("!LocalWptCaptureMode.current &&"))
        // …and the label is drawn only under that predicate.
        assertTrue(captureCanvasRegion.contains("if (showsLabel)"))
        // Composed-root predicate: zero composed children AND no `_text`.
        assertTrue(captureCanvasRegion.contains("component.children.isNullOrEmpty() && component._text.isNullOrEmpty()"))
    }

    // The composed (WPT document) canvas never carries chrome.
    @Test
    fun composedCanvas_drawsNoLabel() {
        // It renders under `LocalWptCaptureMode provides true`; no rect call.
        assertFalse("ComposedCaptureCanvas must not call harnessLabelRects(", composedCanvasRegion.contains("harnessLabelRects("))
    }

    // ── P3: the runtime no longer draws the label ──────────────────────────

    // The runtime half of the move: no label composable exists to re-wire.
    @Test
    fun runtime_noLongerComposesTheLabel() {
        // The renderer that used to compose the in-component placeholder.
        val rendererPath = "runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt"
        // The geometry object that used to hold the placeholder composable.
        val labelPath = "runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/BlockLabel.kt"
        // Read both from the repo, not from a compiled artifact.
        val renderer = File(repoRoot, rendererPath).readText()
        val label = File(repoRoot, labelPath).readText()
        // The in-component placeholder call is gone from the renderer…
        assertFalse("ComponentRenderer.kt still composes BlockLabelPlaceholder(", renderer.contains("BlockLabelPlaceholder("))
        // …and the composable itself is deleted, not merely un-called, so
        // nothing can re-wire the label into a component's paint chain.
        assertFalse("BlockLabel.kt still declares a composable", label.contains("@Composable"))
        assertFalse("BlockLabel.kt still declares BlockLabelPlaceholder", label.contains("fun BlockLabelPlaceholder"))
    }
}

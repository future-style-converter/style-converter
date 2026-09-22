package com.styleconverter.test.screenshot

// Wave 51, PR A — the harness debug-label CHROME on Android.
//
// THE CONTRACT PINNED (docs/DYNAMIC_CAPTURE.md "Harness label chrome"): the
// component-name debug label is drawn by the CAPTURE HARNESS as a sibling
// layer over the finished capture root — never by the runtime, never inside
// the component's paint chain — at CAPTURE-FRAME (8,6), from the PLAIN name
// (`_` → space), truncated against the FRAME width, gated by the existing
// WPT/inbox flag, exactly one per capture iff the composed root has no
// children and no `_text`. Byte-identical rects on web / iOS / Android.
//
// WHY A JVM GEOMETRY PIN + SOURCE SCAN: this module's unit tests are plain
// junit:4.13.2 (no Robolectric / Compose UI test / emulator), so the
// composable cannot run here. P1 runs the SAME pure function the draw node
// consumes; P2/P3 read source (ComposedFullHeightCaptureTest's technique),
// sliced FIRST to the per-component CaptureCanvas so the composed canvas's own
// `drawWithContent` (offscreen-layer record) cannot satisfy the pin.
//
// NEGATIVE CONTROLS, EXECUTED (wave 51 PR A, android lane) — each mutation
// applied to the working tree, this class run alone, the source restored
// byte-exact (cmp) afterwards; log: scratchpad wave51/prA/android-mutations.log:
//   M1 `.drawWithContent { … }` moved AFTER `.padding(CaptureCanvasPadding)`
//      → drawNode_sitsBetweenOnGloballyPositionedAndPadding FAILS (1 of 11).
//   M2 `!LocalWptCaptureMode.current &&` dropped from showsLabel
//      → predicate_isGatedByTheWptCaptureFlag FAILS (1 of 11).
//   M3 BlockLabel.ORIGIN_X/ORIGIN_Y = 24/22 (the old in-component footprint)
//      → geometry_firstRectIsTheCaptureFrameOrigin FAILS, and so do the three
//        other absolute-x geometry pins (62/39-glyph, <22px) — 4 of 11.
// All 11 green on the shipped tree.

import com.styleconverter.runtime.core.renderer.BlockLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HarnessLabelChromeTest {

    // ── P1: geometry, through the exact function the draw node paints ──────

    @Test
    fun geometry_firstRectIsTheCaptureFrameOrigin() {
        // 'L' row 0 is 10000 (BlockFont.gen.kt) → the first set bit of
        // "LAYOUT C01 ALIGNCONTENT" is cell 0, col 0, row 0 → PNG (8, 6).
        assertEquals(8 to 6, harnessLabelRects("Layout_C01_AlignContent", 390).first())
    }

    @Test
    fun geometry_defaultFrameKeepsSixtyTwoGlyphs() {
        // (390 - 16) / 6 = 62 whole advances. A 62-char name is drawn whole…
        val name62 = "H".repeat(62)
        val rects = harnessLabelRects(name62, 390)
        assertEquals(BlockLabel.rects(BlockLabel.normalize(name62)), rects)
        // …'H' carries 17 set bits per cell, so 62 × 17 rects, and the LAST
        // cell starts at x = 8 + 61·6 = 374 ('H' row 0 is 10001: col 0 lit).
        assertEquals(62 * 17, rects.size)
        assertTrue(rects.contains(374 to 6))
        // A 63rd glyph would start at 380 > 390 - 8 - 6 → truncated, silently.
        val rects63 = harnessLabelRects("H".repeat(63), 390)
        assertEquals(62 * 17, rects63.size)
        assertFalse(rects63.contains(380 to 6))
    }

    @Test
    fun geometry_captureWidth250KeepsThirtyNineGlyphs() {
        // CAPTURE_WIDTH=250 (docs/DYNAMIC_CAPTURE.md §2): (250 - 16) / 6 = 39.
        val rects = harnessLabelRects("H".repeat(62), 250)
        assertEquals(39 * 17, rects.size)
        // Cell 38 starts at x = 8 + 38·6 = 236; a 40th cell (242) is dropped.
        assertTrue(rects.contains(236 to 6))
        assertFalse(rects.contains(242 to 6))
    }

    @Test
    fun geometry_frameBelowTwentyTwoDrawsNothing_andTwentyTwoDrawsOneGlyph() {
        // 21px: budget 5 < one 6px advance → zero glyphs (CaptureCanvas logs
        // this once; the math stays pure). 22px: budget 6 → exactly one glyph.
        assertTrue(harnessLabelRects("H", 21).isEmpty())
        assertEquals(BlockLabel.rects("H"), harnessLabelRects("HH", 22))
        assertEquals(17, harnessLabelRects("HH", 22).size)
    }

    @Test
    fun geometry_isCaseInsensitive_throughNormalize() {
        // The C51 text-parity argument: normalize uppercases everything, so a
        // `text-transform: uppercase` root (065_Typography_Uppercase) yields
        // the same rects from its plain name as from its transformed form.
        assertEquals(
            harnessLabelRects("TYPOGRAPHY_UPPERCASE", 390),
            harnessLabelRects("typography_uppercase", 390)
        )
    }

    @Test
    fun geometry_underscoreBecomesSpaceBeforeNormalize() {
        // The atlas HAS a '_' glyph, so normalize alone would keep it; the
        // chrome contract is the name with `_` → space on every platform.
        assertEquals(BlockLabel.rects("A B"), harnessLabelRects("A_B", 390))
        assertNotEquals(BlockLabel.rects("A_B"), harnessLabelRects("A_B", 390))
    }

    // ── P2: source scan, SLICED to the per-component CaptureCanvas ─────────

    /** Repo root by walk-up (this build's rootDir is apps/android-harness). */
    private val repoRoot: File by lazy {
        val anchor = "apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt"
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, anchor).exists()) dir = dir.parentFile
        requireNotNull(dir) { "repo root ($anchor) not found above ${System.getProperty("user.dir")}" }
    }

    private val screenSource: String by lazy {
        File(repoRoot, "apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt").readText()
    }

    /** The per-component canvas ONLY (declaration → the helper after it); the
     *  composed canvas's own `drawWithContent` sits OUTSIDE this slice (C34). */
    private val captureCanvasRegion: String by lazy {
        val start = "private fun CaptureCanvas("
        val end = "internal fun isOutOfFlowRoot("
        assertTrue("CaptureCanvas declaration missing", screenSource.contains(start))
        assertTrue("isOutOfFlowRoot declaration missing", screenSource.contains(end))
        screenSource.substringAfter(start).substringBefore(end)
    }

    /** The composed canvas: from its declaration to the completion view. */
    private val composedCanvasRegion: String by lazy {
        screenSource.substringAfter("private fun ComposedCaptureCanvas(").substringBefore("private fun CompleteView(")
    }

    @Test
    fun captureCanvas_consumesTheSharedGeometry() {
        // The draw node must paint the SAME rect list P1 pins — the exact
        // call, keyed on the composed root's name and the frame width.
        assertTrue("CaptureCanvas must call harnessLabelRects(component.name, widthPx)", captureCanvasRegion.contains("harnessLabelRects(component.name, widthPx)"))
    }

    @Test
    fun drawNode_sitsBetweenOnGloballyPositionedAndPadding() {
        // The node's space must be the unpadded outer rect onGloballyPositioned
        // reports and PixelCopy crops: AFTER .onGloballyPositioned, BEFORE
        // .padding(CaptureCanvasPadding). After .padding, (8,6) would be (24,22).
        val positioned = captureCanvasRegion.indexOf(".onGloballyPositioned")
        val padding = captureCanvasRegion.indexOf(".padding(CaptureCanvasPadding)")
        val draw = captureCanvasRegion.indexOf(".drawWithContent {")
        assertTrue("onGloballyPositioned missing from CaptureCanvas", positioned >= 0)
        assertTrue(".padding(CaptureCanvasPadding) missing from CaptureCanvas", padding >= 0)
        assertTrue(".drawWithContent { missing from CaptureCanvas", draw >= 0)
        assertTrue("drawWithContent must follow .onGloballyPositioned", draw > positioned)
        assertTrue("drawWithContent must precede .padding(CaptureCanvasPadding)", draw < padding)
        // EVERY drawWithContent mention in the slice lies in that window.
        var at = captureCanvasRegion.indexOf("drawWithContent")
        while (at >= 0) {
            assertTrue("a drawWithContent mention at $at sits outside the positioned..padding window", at > positioned && at < padding)
            at = captureCanvasRegion.indexOf("drawWithContent", at + 1)
        }
    }

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

    @Test
    fun composedCanvas_drawsNoLabel() {
        // The composed (WPT document) canvas renders under
        // `LocalWptCaptureMode provides true` and never carries chrome.
        assertFalse("ComposedCaptureCanvas must not call harnessLabelRects(", composedCanvasRegion.contains("harnessLabelRects("))
    }

    // ── P3: the runtime no longer draws the label ──────────────────────────

    @Test
    fun runtime_noLongerComposesTheLabel() {
        val rendererPath = "runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt"
        val labelPath = "runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/BlockLabel.kt"
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

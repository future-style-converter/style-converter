package com.styleconverter.test.screenshot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * JVM pins for the FULL-HEIGHT composed capture (ANDROID-STITCH lane).
 *
 * The bug being pinned closed: the composed capture was PixelCopy-WINDOW-bound
 * — one PixelCopy of the [0,0..390,844] window frame — so any composed
 * document taller than the 844px window truncated (wave-15 evidence:
 * attachment-fixed-inside-transform-1 composes to 5132px; web/iOS captured
 * 390x5132, Android captured one 390x844 window of white). The fix renders the
 * composed canvas into an OFFSCREEN GraphicsLayer at its full laid-out height
 * and snapshots THAT (GraphicsLayer.toImageBitmap()), mirroring iOS's
 * window-independent ImageRenderer contract (ScreenshotManager.render).
 *
 * GraphicsLayer/Bitmap cannot execute on the plain JVM (no emulator in this
 * campaign — the orchestrator gates devices), so this suite pins:
 *   1. the PURE degenerate-size gate the snapshot helper runs first, and
 *   2. SOURCE-SCAN wiring pins on ScreenshotCaptureScreen.kt (the same
 *      feasible-without-Robolectric technique WptBoxSizingDefaultTest and
 *      FragmentGeometryTest use in the runtime), proving the composed path is
 *      offscreen-layer-backed while the per-component path (the 327-pair
 *      corpus) still runs the byte-identical window PixelCopy.
 *
 * Device-gated verification (documented, per the lane contract): a TITAN
 * composed run over attachment-fixed-inside-transform-1 must save a 390x5132
 * PNG matching web/iOS — runnable only when an emulator is allowed.
 */
class ComposedFullHeightCaptureTest {

    // ── 1. Pure gate: degenerate layer sizes never reach the PNG encoder ────

    @Test
    fun captureGate_acceptsPositiveArea() {
        // The canonical composed sizes: the 600dp min-height floor and the
        // wave-15 tall document — both must pass the gate.
        assertTrue(isCapturableLayerSize(width = 390, height = 600))
        assertTrue(isCapturableLayerSize(width = 390, height = 5132))
    }

    @Test
    fun captureGate_rejectsZeroOrNegativeAxes() {
        // A zero-area layer means the record{} modifier never ran (mis-wired
        // draw chain) — the helper must fail loudly, not save an empty PNG.
        assertFalse(isCapturableLayerSize(width = 0, height = 600))
        assertFalse(isCapturableLayerSize(width = 390, height = 0))
        assertFalse(isCapturableLayerSize(width = 0, height = 0))
        // Negative sizes are impossible from layout but the gate is total.
        assertFalse(isCapturableLayerSize(width = -1, height = 600))
        assertFalse(isCapturableLayerSize(width = 390, height = -1))
    }

    // ── 2. Source-scan wiring pins ──────────────────────────────────────────

    /** The capture screen source, located by walking up from the test working
     *  dir until the repo-relative path exists — the WptBoxSizingDefaultTest
     *  convention, robust to Gradle running tests from module subdirs. */
    private val screenSource: String by lazy {
        val rel = "apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt"
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, rel).exists()) dir = dir.parentFile
        File(requireNotNull(dir) { "repo root ($rel) not found above ${System.getProperty("user.dir")}" }, rel)
            .readText()
    }

    /** The per-component capture effect region — everything between the
     *  PER-COMPONENT marker comment and the COMPOSED marker comment. */
    private val perComponentRegion: String by lazy {
        screenSource.substringAfter("PER-COMPONENT path").substringBefore("COMPOSED path")
    }

    /** The composed capture effect region — from the COMPOSED marker comment
     *  to the phase-switch UI (`when (capturePhase)`), covering the whole
     *  composed LaunchedEffect body. */
    private val composedEffectRegion: String by lazy {
        screenSource.substringAfter("COMPOSED path").substringBefore("when (capturePhase)")
    }

    /** The composed HOST composable's source — between its declaration and the
     *  canvas composable that follows it in the file. */
    private val composedViewRegion: String by lazy {
        screenSource.substringAfter("private fun ComposedCaptureView")
            .substringBefore("private fun ComposedCaptureCanvas")
    }

    @Test
    fun composedEffect_snapshotsOffscreenLayer_notWindowPixelCopy() {
        // The composed effect must capture through the offscreen-layer helper…
        assertTrue(
            "composed capture must snapshot the offscreen GraphicsLayer",
            composedEffectRegion.contains("captureComposedLayer(layer)"))
        // …and must NOT touch the window-bound PixelCopy path — that is the
        // exact call that truncated tall documents at the 844px window edge.
        assertFalse(
            "composed capture must not window-PixelCopy (window-height truncation)",
            composedEffectRegion.contains("captureWithPixelCopy"))
    }

    @Test
    fun perComponentEffect_stillUsesWindowPixelCopy() {
        // The 327-pair corpus contract: the per-component path keeps the
        // historical window PixelCopy byte-identically (its subjects always
        // fit the window; changing its backend would invalidate baselines).
        assertTrue(
            "per-component capture must keep captureWithPixelCopy(window, bounds)",
            perComponentRegion.contains("captureWithPixelCopy(window, bounds)"))
    }

    @Test
    fun composedCanvas_recordsFullDrawIntoLayer() {
        // The canvas must re-record its whole draw pass into the hoisted layer
        // (the documented Compose composable-to-bitmap record{} idiom) — this
        // happens BEFORE any ancestor clip, which is what preserves content
        // past the window edge…
        assertTrue(
            "composed canvas must record its draw into the GraphicsLayer",
            screenSource.contains("graphicsLayer.record { this@drawWithContent.drawContent() }"))
        // …and draw the layer back so the on-window render is unchanged.
        assertTrue(
            "composed canvas must draw the recorded layer back",
            screenSource.contains("drawLayer(graphicsLayer)"))
    }

    @Test
    fun composedHost_unbindsBlockAxisConstraints() {
        // The host must measure the canvas under Constraints.Infinity on the
        // block axis via verticalScroll (never scrolled — offset stays 0):
        // without it Compose COERCES the canvas to the 844px window maxHeight
        // and the full-height layer record has nothing tall to record.
        assertTrue(
            "ComposedCaptureView must wrap the canvas in verticalScroll",
            composedViewRegion.contains(".verticalScroll(rememberScrollState())"))
    }

    @Test
    fun composedBranch_publishesLayerAfterComposition() {
        // The render branch hoists a fresh per-fixture layer and publishes it
        // to the capture effect via SideEffect (post-composition — never a
        // state write mid-composition), so the effect snapshots exactly the
        // layer this fixture's render recorded into.
        assertTrue(
            "composed branch must allocate a per-fixture layer",
            screenSource.contains("val layer = rememberGraphicsLayer()"))
        assertTrue(
            "composed branch must publish the layer via SideEffect",
            screenSource.contains("SideEffect { composedLayer = layer }"))
    }

    @Test
    fun snapshotHelper_readsBackViaToImageBitmap() {
        // The helper must go through the Compose-1.7 offscreen readback…
        assertTrue(
            "snapshot helper must call GraphicsLayer.toImageBitmap()",
            screenSource.contains("layer.toImageBitmap()"))
        // …and force a software ARGB_8888 copy so the PNG encoder never sees
        // a GPU-only HARDWARE-config bitmap.
        assertTrue(
            "snapshot helper must copy to a software ARGB_8888 bitmap",
            screenSource.contains("copy(Bitmap.Config.ARGB_8888, false)"))
    }
}

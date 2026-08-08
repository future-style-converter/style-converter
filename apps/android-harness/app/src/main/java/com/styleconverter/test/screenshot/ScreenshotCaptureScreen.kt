package com.styleconverter.test.screenshot

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
// Pass A's snapshot crosses from the capture path (android.graphics.Bitmap)
// into the runtime's painter (Compose ImageBitmap) through this adapter.
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRDocumentDecoder
import com.styleconverter.runtime.typography.font.DocumentFontRegistry
import com.styleconverter.runtime.core.renderer.ComponentHost
import com.styleconverter.runtime.core.renderer.LocalWptCaptureMode
import com.styleconverter.runtime.core.renderer.LocalWptComposedMode
import com.styleconverter.runtime.core.renderer.SlotComposer
import com.styleconverter.runtime.core.renderer.WPT_CANVAS_BACKGROUND
// wave-25 round 3 — the shared canvas-frame constant + the pure ICB-extent
// helper (the ref's render viewport = canvas − 2×frame). Imported from the
// runtime so all three platforms read ONE number for the ref's image frame.
import com.styleconverter.runtime.core.renderer.WPT_CANVAS_FRAME_DP
import com.styleconverter.runtime.core.renderer.composedIcbExtentDp
// Wave 15 — the pure composed-canvas alpha-compositing rule (body background
// blended source-over onto the white WPT canvas; see resolveComposedCanvasBackground).
import com.styleconverter.runtime.core.renderer.composedCanvasBackground
// wave-27 A-RC1 — the shared containment gate on body→canvas background
// propagation (css-contain-2 §3.5); see resolveComposedCanvasBackground.
import com.styleconverter.runtime.core.renderer.containmentBlocksCanvasPropagation
import com.styleconverter.runtime.core.renderer.captureCanvasBackground
import com.styleconverter.runtime.core.types.ValueExtractors
// wave-26 lane BF-A — the two-pass backdrop-filter render. The composed WPT
// canvas is the only backdrop root the runtime can render honestly (a
// controlled tree we can paint twice), so the host hook lives here: arm the
// coordinator per fixture, snapshot pass A, publish it, capture pass B.
import com.styleconverter.runtime.effects.backdrop.BackdropPass
import com.styleconverter.runtime.effects.backdrop.BackdropPassCoordinator
import com.styleconverter.runtime.effects.backdrop.documentDeclaresBackdropFilter
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import java.io.File

private const val TAG = "ScreenshotCapture"

// ── Color tokens (matching web & ComponentListScreen) ────────────────────────
private val BgColor = Color(0xFF1A1A2E)
private val CardBg = Color(0x08FFFFFF)
private val CardHeaderBg = Color(0x0DFFFFFF)
private val CardFooterBg = Color(0x33000000)
private val BorderColor = Color(0x1AFFFFFF)
private val HeaderBg = Color(0x4D000000)
private val TextWhite = Color.White
private val TextSubtitle = Color(0xFF888888)
private val TextIndex = Color(0xFF666666)
private val TextPropCount = Color(0xFF666666)

/**
 * Screen that automatically captures screenshots of each component.
 *
 * Uses PixelCopy API to capture the fully-composited rendered frame,
 * preserving ALL visual effects: transforms, filters, clips, alpha/opacity.
 *
 * Dynamic-capture hooks (docs/DYNAMIC_CAPTURE.md; launch-intent transport in
 * MainActivity):
 *  - [forceState]: one runtime-v1 condition resolved as ACTIVE on every
 *    captured component (spec 06 §6). PNG filenames stay identical to the
 *    base run — the recipe keeps forced runs in separate output directories.
 *  - [captureWidthDp]: render-surface width override (CAPTURE_WIDTH). The
 *    default 390 is byte-identical to the historical capture path.
 *  - [animationTime]: CAPTURE_ANIMATION_TIME (spec 07 §5) — seconds on the
 *    absolute animation timeline; every animation renders its state at t,
 *    paused. null = the historical live path.
 *
 * TITAN inbox mode ([inboxMode], this lane): when true the screen does NOT read
 * the bundled `assets/tmpOutput.json` and does NOT return to the gallery. It
 * loops forever — poll the on-device inbox for the oldest *.json IR document,
 * decode + compose + flatten it through the IDENTICAL path the bundled flow
 * uses, capture every component, consume the fixture, and poll again. The host
 * feeder (tools/titan/feed-android.mjs) pushes fixtures and pulls the PNGs.
 * The capture machinery below (CaptureView / CaptureCanvas / PixelCopy /
 * saveScreenshot) is byte-identical across both modes so WPT captures are
 * directly comparable with normal captures and the per-component filenames
 * (`%03d_<safeName>.png`) match what the compare pipeline globs for.
 *
 * TITAN composed mode ([composedMode], Round 3): layered strictly ON TOP of
 * inbox mode. Instead of capturing each flattened component to its own PNG, it
 * renders the WHOLE fed document COMPOSED (all roots in document order) on ONE
 * canvas that mirrors the Chromium browser-ref (tools/titan/capture-browser-ref
 * .mjs): 390dp wide, WHITE (the corpus-v4 canvas — WPT_CANVAS_BACKGROUND, or the
 * body-root's own background), 16dp padding, 600dp min-height, natural height —
 * then snapshots that canvas ONCE via an OFFSCREEN GraphicsLayer (NOT the
 * window-bound PixelCopy the per-component path uses — see
 * captureComposedLayer: a composed document taller than the 844px window must
 * still capture at full height, matching iOS's window-independent
 * ImageRenderer) and saves a single `<safe(testKey)>.png`
 * (ScreenshotManager.saveComposedScreenshot). This reproduces the reference
 * page's real layout (bar stacking, gaps, positions) so MULTI-component tests
 * are measured honestly instead of via inject's per-component vertical stitch.
 * The per-component path (composedMode=false) is left byte-identical, and the
 * bundled non-inbox baseline path (inboxMode=false) is untouched.
 */
@Composable
fun ScreenshotCaptureScreen(
    forceState: String? = null,
    captureWidthDp: Int = 390,
    animationTime: Double? = null,
    inboxMode: Boolean = false,
    composedMode: Boolean = false,
    onCaptureComplete: () -> Unit = {}
) {
    val context = LocalContext.current
    val screenshotManager = remember { ScreenshotManager(context) }

    // Composed root trees (v2 slot refs rebuilt by SlotComposer; v1
    // nested documents pass through) — the capture loop and the render
    // pass both walk THIS list so indices always agree.
    var roots by remember { mutableStateOf<List<IRComponent>?>(null) }
    // Document-level wire keyframes (spec 07 §1.2) — kept beside the
    // composed roots (the web harness keeps the raw wire block beside the
    // decoded document the same way) and provided to the runtime driver
    // via LocalDocumentKeyframes at the capture canvas.
    var keyframes by remember {
        mutableStateOf<Map<String, List<com.styleconverter.runtime.core.ir.IRKeyframeStop>>>(emptyMap())
    }
    var error by remember { mutableStateOf<String?>(null) }
    var currentIndex by remember { mutableIntStateOf(-1) }
    var capturePhase by remember { mutableStateOf(CapturePhase.LOADING) }
    var capturedCount by remember { mutableIntStateOf(0) }
    var failedCount by remember { mutableIntStateOf(0) }

    // PixelCopy capture state
    var shouldCapture by remember { mutableStateOf(false) }
    var cardBoundsInWindow by remember { mutableStateOf<Rect?>(null) }

    // Offscreen composed-capture layer (ANDROID-STITCH lane): the GraphicsLayer
    // the composed canvas records its full-height draw into. Published from the
    // composed render branch (SideEffect, once composed) so the capture effect
    // below can snapshot it WINDOW-INDEPENDENTLY — PixelCopy can only read the
    // [0,0..390,844] window frame, which truncated any composed document taller
    // than the window (attachment-fixed-inside-transform-1 composes to 5132px;
    // web/iOS captured full height, Android captured one white window). Null
    // outside composed mode and between fixtures.
    var composedLayer by remember { mutableStateOf<GraphicsLayer?>(null) }

    // TITAN inbox-mode state:
    //  - currentFixtureFile: the inbox *.json currently being rendered, so it
    //    can be consumed AFTER its last capture (crash-safe: an unconsumed
    //    fixture is re-picked on the next poll).
    //  - pollGeneration: bumped after each fixture to re-arm the loading effect
    //    (LaunchedEffect keyed on it) for the next poll. Unused in bundled mode.
    var currentFixtureFile by remember { mutableStateOf<File?>(null) }
    var pollGeneration by remember { mutableIntStateOf(0) }

    // TITAN composed-mode state: the WPT test key of the fixture currently being
    // composed, recovered from its inbox filename (TitanInbox.composedTestKey).
    // Becomes the single output PNG name `<safe(testKey)>.png`. Null outside
    // composed mode; carried in state so the capture effect (which runs after
    // the render settles) still knows which key to save under.
    var composedTestKey by remember { mutableStateOf<String?>(null) }

    // Get the Activity window for PixelCopy
    val activity = context as? android.app.Activity
    val window = activity?.window

    // Loading effect. Keyed on pollGeneration so inbox mode can re-arm it once
    // per pushed fixture (bundled mode runs it exactly once at generation 0).
    LaunchedEffect(pollGeneration) {
        // Belt-and-braces: only (re)load while in the LOADING phase. The inbox
        // completion path always bumps pollGeneration AND sets phase=LOADING
        // together, but a stray recomposition must never re-enter mid-capture.
        if (capturePhase != CapturePhase.LOADING) return@LaunchedEffect
        try {
            // Run-configuration marker line (part of the forced-state AND
            // seized-animation contracts: a capture script must be able to
            // VERIFY via `adb logcat -d | grep` that the run actually ran
            // with the hook active instead of silently diffing two base /
            // live captures — the data-animation-time rationale, spec 07 §5).
            // titanInbox / titanComposed are appended so the feeder can
            // grep-verify the app actually entered the requested mode (same
            // gate philosophy).
            Log.i(TAG, "Capture run config: forceState=${forceState ?: "none"} captureWidth=$captureWidthDp animationTime=${animationTime ?: "none"} titanInbox=$inboxMode titanComposed=$composedMode")

            if (!screenshotManager.hasWritePermission()) {
                error = "No write permission for screenshots directory"
                capturePhase = CapturePhase.ERROR
                return@LaunchedEffect
            }

            // Reset per-fixture capture state so each fixture starts clean —
            // critical in inbox mode where this effect re-runs per fixture.
            capturedCount = 0
            failedCount = 0
            cardBoundsInWindow = null
            shouldCapture = false
            // Drop the previous fixture's offscreen layer reference — the next
            // composed render publishes a fresh one (rememberGraphicsLayer per
            // branch entry); a stale layer must never be snapshotted.
            composedLayer = null

            val jsonString: String = if (inboxMode) {
                // DO NOT clearScreenshots() in inbox mode — the host feeder owns
                // on-device PNG lifecycle (it pulls, verifies, then clears per
                // fixture). Clearing here would race the host's pull of the
                // PREVIOUS fixture's captures and lose data.
                Log.i(TAG, "Titan inbox: polling ${screenshotManager.getInboxPath()} for next fixture…")
                var file: File? = null
                // Idle-poll until a fixture arrives. delay() suspends the
                // coroutine (no busy-wait); 200 ms is well under the host's
                // per-fixture push cadence so pickup latency stays negligible.
                while (file == null) {
                    file = screenshotManager.nextFixtureFile()
                    if (file == null) delay(200)
                }
                currentFixtureFile = file
                // Composed mode: recover the WPT test key from the inbox
                // filename now (before decode) — it names the single output PNG.
                composedTestKey = if (composedMode) TitanInbox.composedTestKey(file.name) else null
                Log.i(TAG, "Titan inbox: loaded fixture ${file.name}${if (composedMode) " (composed key=$composedTestKey)" else ""}")
                file.readText()
            } else {
                // Bundled one-shot flow: clear stale captures, read the asset.
                val deleted = screenshotManager.clearScreenshots()
                Log.i(TAG, "Cleared $deleted existing screenshots")
                context.assets.open("tmpOutput.json")
                    .bufferedReader()
                    .use { it.readText() }
            }

            // v2-aware decode (strict envelope per spec 05, v1 window
            // fallback with a deprecation warning), then the composer
            // step: rebuild render trees from slot refs. IDENTICAL decode +
            // compose for both modes — that is what makes WPT captures
            // comparable with normal captures.
            val document = IRDocumentDecoder.decode(jsonString)
            // wave-35 lane B2 — register this document's `@font-face` files
            // BEFORE composition. Ordering is load-bearing: Compose resolves
            // `font-family` while modifier chains are built, so a face
            // registered after the roots reach composition would arrive too
            // late to shape the capture and the screenshot would silently
            // record the fallback. Called UNCONDITIONALLY (including with a
            // null list, which clears): the inbox renders many documents in
            // one process and a leftover face from the previous one would
            // shadow this one's same-named family.
            val faceCount = DocumentFontRegistry.register(document.fontFaces, screenshotManager.getFontsDir())
            if (document.fontFaces?.isNotEmpty() == true) {
                val rep = DocumentFontRegistry.lastReport
                Log.i(TAG, "@font-face: declared=${rep.declared} registered=$faceCount" +
                        if (rep.declined.isEmpty()) "" else " DECLINED=${rep.declined.joinToString(",")}")
            }
            val composed = SlotComposer.compose(document)
            // Arm (or explicitly disarm) the two-pass backdrop render BEFORE
            // the roots reach composition — the coordinator's `enabled` flag
            // is read while modifier chains are built, so it must be settled
            // by then. Only the COMPOSED canvas hosts the two passes (it is
            // the single controlled backdrop root); the per-component path
            // always disarms, which is what keeps its captures — and the
            // committed 327-pair dark-stage baseline — byte-identical.
            val wantsBackdropPass = composedMode && documentDeclaresBackdropFilter(composed)
            BackdropPassCoordinator.current.beginDocument(wantsBackdropPass)
            if (wantsBackdropPass) {
                // Grep-able evidence that the two-pass path actually engaged
                // for this fixture (same gate philosophy as the run-config
                // marker line above).
                Log.i(TAG, "Backdrop two-pass armed for this fixture")
            }
            roots = composed
            // Wire keyframes ride the same decode (additive envelope key);
            // omit-when-empty on the wire → empty map = zero footprint.
            keyframes = document.keyframes ?: emptyMap()

            val flatSize = flattenComponents(composed).size
            Log.i(TAG, "Loaded ${composed.size} root components (flattened: $flatSize)")

            // Degenerate-fixture guard. Per-component mode captures the flattened
            // list, so "nothing to capture" == flatSize 0. Composed mode captures
            // the composed ROOTS on one canvas, so it's empty only when there are
            // no roots at all.
            val nothingToCapture = if (composedMode) composed.isEmpty() else flatSize == 0
            if (nothingToCapture) {
                // Degenerate fixture (no capturable components). In inbox mode,
                // consume + re-poll so it can't wedge the loop; the host times
                // out on the (zero) expected PNGs and records the failure.
                if (inboxMode) {
                    currentFixtureFile?.let { screenshotManager.consumeFixture(it) }
                    Log.w(TAG, "Titan inbox: fixture had 0 capturable components — consumed, re-polling")
                    roots = null
                    pollGeneration++
                } else {
                    capturePhase = CapturePhase.COMPLETE
                }
                return@LaunchedEffect
            }

            capturePhase = CapturePhase.CAPTURING
            currentIndex = 0
        } catch (e: Exception) {
            if (inboxMode) {
                // A malformed fixture must NOT wedge the poll loop: log,
                // consume it, and keep serving the next one. The feeder records
                // the timeout for this fixture and moves on.
                Log.e(TAG, "Titan inbox: failed to load fixture — consuming + re-polling", e)
                currentFixtureFile?.let { screenshotManager.consumeFixture(it) }
                roots = null
                pollGeneration++
            } else {
                error = "Failed to load IR: ${e.message}"
                capturePhase = CapturePhase.ERROR
                Log.e(TAG, "Error loading document", e)
            }
        }
    }

    // Capture logic using PixelCopy (PER-COMPONENT path). Composed mode uses
    // the single-capture effect below instead, so bail out here to keep the two
    // capture strategies from both firing on a shared shouldCapture toggle.
    LaunchedEffect(shouldCapture, currentIndex) {
        if (composedMode) return@LaunchedEffect
        // Flatten the COMPOSED tree depth-first pre-order so child components
        // become their own captures — matching iOS `flatten` and web `flatten`
        // exactly. Because SlotComposer inverts the converter's pre-order
        // IRFlattener, a v2 flat document yields the SAME flatten order (and
        // filename indices 000_*, 001_*, ...) as its v1 nested equivalent.
        val flat = roots?.let { flattenComponents(it) } ?: emptyList()
        if (shouldCapture && roots != null && currentIndex >= 0 && currentIndex < flat.size) {
            delay(400) // Wait for rendering + compositing

            try {
                val component = flat[currentIndex]
                val bounds = cardBoundsInWindow

                val bitmap = if (window != null && bounds != null && bounds.width() > 0 && bounds.height() > 0) {
                    captureWithPixelCopy(window, bounds)
                } else {
                    null
                }

                if (bitmap != null) {
                    val file = screenshotManager.saveScreenshot(bitmap, component.name, currentIndex)
                    if (file != null) {
                        capturedCount++
                        Log.i(TAG, "Captured: ${component.name} -> ${file.absolutePath}")
                    } else {
                        failedCount++
                        Log.e(TAG, "Failed to save: ${component.name}")
                    }
                } else {
                    failedCount++
                    Log.e(TAG, "PixelCopy failed for: ${component.name}")
                }
            } catch (e: Exception) {
                failedCount++
                Log.e(TAG, "Capture error for component $currentIndex", e)
            }

            shouldCapture = false

            // Advance through the flattened list (parents + children), not just
            // the top-level component array — keeps indices in lockstep with iOS/web.
            if (currentIndex < flat.size - 1) {
                currentIndex++
            } else {
                Log.i(TAG, "Capture complete: $capturedCount captured, $failedCount failed")
                if (inboxMode) {
                    // Consume the fixture we just finished (AFTER the last save,
                    // so a crash mid-capture leaves it in the inbox for retry —
                    // the primitive's documented contract), then re-arm the
                    // loading effect to poll for the next fixture.
                    currentFixtureFile?.let { screenshotManager.consumeFixture(it) }
                    Log.i(TAG, "Titan inbox: consumed ${currentFixtureFile?.name}, re-polling")
                    roots = null
                    currentIndex = -1
                    capturePhase = CapturePhase.LOADING
                    pollGeneration++
                } else {
                    capturePhase = CapturePhase.COMPLETE
                }
            }
        }
    }

    // Capture logic (COMPOSED path — TITAN Round 3; OFFSCREEN since the
    // ANDROID-STITCH lane). Fires once per fixture: the whole composed canvas is
    // captured to ONE bitmap and saved as `<safe(testKey)>.png`, then the
    // fixture is consumed and the poll loop re-armed. Only relevant in composed
    // mode (which is always inbox mode), so it bails out otherwise. Keyed on
    // shouldCapture alone (no per-component index in this path);
    // ComposedCaptureView re-arms shouldCapture per fixture.
    //
    // UNLIKE the per-component path above, this does NOT PixelCopy the window:
    // PixelCopy reads the composited WINDOW frame, whose height is the device
    // window (844px) — any composed document taller than that laid out past the
    // window edge and truncated (the wave-15 attachment-fixed-inside-transform-1
    // evidence: doc composes to 5132px, web/iOS saved 390x5132, Android saved
    // one 390x844 white window). Instead the composed canvas records its full
    // draw into an offscreen GraphicsLayer at its FULL laid-out size (see
    // ComposedCaptureCanvas), and we snapshot THAT layer — the native twin of
    // iOS's window-independent ImageRenderer pass (ScreenshotManager.render).
    LaunchedEffect(shouldCapture) {
        if (!composedMode) return@LaunchedEffect
        if (shouldCapture && roots != null) {
            delay(400) // let Compose finish laying out + compositing the full doc

            try {
                // Bounds still gate readiness (layout must have settled and
                // reported a non-degenerate canvas) and feed the full-height
                // log line; the layer itself carries the capture geometry.
                val bounds = cardBoundsInWindow
                val layer = composedLayer

                // ── PASS A (wave-26 lane BF-A) ───────────────────────────
                // When this fixture declares backdrop-filter, the frame that
                // just settled is the SAMPLE pass: every backdrop element
                // suppressed its own paint, so the recorded layer IS the
                // backdrop root image (filter-effects-2 §2). Snapshot it,
                // publish it, and let the SAME layout repaint as the
                // composite pass — the flip writes snapshot state read only
                // in draw lambdas, so nothing recomposes or re-measures and
                // the two passes are pixel-comparable by construction.
                val backdropCoordinator = BackdropPassCoordinator.current
                if (backdropCoordinator.pass == BackdropPass.SAMPLE) {
                    val passA = layer?.let { captureComposedLayer(it) }
                    if (passA != null) {
                        Log.i(TAG, "Backdrop pass A captured: ${passA.width}x${passA.height}")
                        backdropCoordinator.beginCompositePass(passA.asImageBitmap())
                        // One settle for the repaint, same order of magnitude
                        // as the pre-capture settle above.
                        delay(250)
                    } else {
                        // No silent fallthrough: without a backdrop image the
                        // elements would paint half-filtered. Disarm instead —
                        // they fall back to the historical no-op and the
                        // failure is attributable in the feeder's log.
                        Log.e(TAG, "Backdrop pass A failed — falling back to unfiltered backdrops")
                        backdropCoordinator.reset()
                        delay(250)
                    }
                }
                val bitmap = if (layer != null && bounds != null && bounds.width() > 0 && bounds.height() > 0) {
                    // Evidence trail for the truncation-class bug: record the
                    // canvas height actually captured so a feeder log grep can
                    // verify tall documents came through un-truncated.
                    Log.i(TAG, "Composed offscreen capture: canvas=${bounds.width()}x${bounds.height()} (window-independent)")
                    // ONE offscreen snapshot of the whole composed canvas
                    // (390 × natural height, even when ≫ the window height) —
                    // the recorded display list with every root's paint.
                    captureComposedLayer(layer)
                } else {
                    null
                }

                val key = composedTestKey
                if (bitmap != null && key != null) {
                    val file = screenshotManager.saveComposedScreenshot(bitmap, key)
                    if (file != null) {
                        capturedCount++
                        Log.i(TAG, "Composed capture: $key -> ${file.absolutePath} (${bitmap.width}x${bitmap.height})")
                    } else {
                        failedCount++
                        Log.e(TAG, "Failed to save composed capture: $key")
                    }
                } else {
                    failedCount++
                    // No silent fallthrough: a missing layer/bounds or a failed
                    // offscreen snapshot is logged as an explicit failure so the
                    // feeder's timeout accounting attributes it to this fixture.
                    Log.e(TAG, "Composed offscreen capture failed for: ${key ?: "<no key>"}")
                }
            } catch (e: Exception) {
                failedCount++
                Log.e(TAG, "Composed capture error", e)
            }

            shouldCapture = false

            // Composed mode is always inbox mode: consume the fixture we just
            // captured (AFTER the save, so a crash leaves it in the inbox for
            // retry) and re-arm the loading effect to poll for the next one.
            currentFixtureFile?.let { screenshotManager.consumeFixture(it) }
            Log.i(TAG, "Titan composed: consumed ${currentFixtureFile?.name}, re-polling")
            roots = null
            composedTestKey = null
            // The composed branch leaves composition with roots=null, which
            // releases the rememberGraphicsLayer — clear our reference so the
            // next fixture can only ever snapshot ITS freshly-published layer.
            composedLayer = null
            // Drop the pass-A bitmap and disarm the two-pass render with it:
            // the next fixture re-arms from its OWN IR scan, so a backdrop
            // document can never leak its backdrop image into the fixture
            // that follows it.
            BackdropPassCoordinator.current.reset()
            currentIndex = -1
            capturePhase = CapturePhase.LOADING
            pollGeneration++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        when (capturePhase) {
            CapturePhase.LOADING -> LoadingView()
            CapturePhase.ERROR -> ErrorView(error ?: "Unknown error")
            CapturePhase.CAPTURING -> if (composedMode) {
                roots?.let { composedRoots ->
                    // Composed WPT capture IS WPT capture → suppress the
                    // synthesized component-name placeholder (same ambient flag
                    // as the per-component inbox path) so the render matches the
                    // Chromium browser-ref. LocalWptComposedMode is ADDITIONALLY
                    // set true here (and ONLY here) so the Round-4b composed-only
                    // line-box + padding calibration in PlaceholderContent fires
                    // for composed captures but NOT for the per-component inbox
                    // path or the 327-pair baseline (both leave it default false).
                    CompositionLocalProvider(
                        LocalWptCaptureMode provides true,
                        LocalWptComposedMode provides true
                    ) {
                        // Fresh offscreen record target per composed fixture
                        // (this branch re-enters composition per fixture, so
                        // rememberGraphicsLayer allocates anew and auto-releases
                        // when roots is nulled after the save).
                        val layer = rememberGraphicsLayer()
                        // Publish AFTER composition applies (SideEffect — never
                        // a state write mid-composition) so the capture effect
                        // snapshots exactly the layer this render records into.
                        SideEffect { composedLayer = layer }
                        ComposedCaptureView(
                            roots = composedRoots,
                            forceState = forceState,
                            captureWidthDp = captureWidthDp,
                            animationTime = animationTime,
                            keyframes = keyframes,
                            graphicsLayer = layer,
                            onCanvasPositioned = { bounds -> cardBoundsInWindow = bounds },
                            onRendered = { shouldCapture = true }
                        )
                    }
                }
            } else {
                roots?.let { composedRoots ->
                    // Use the same depth-first flatten as the capture loop so
                    // the rendered component matches the one being saved.
                    val flat = flattenComponents(composedRoots)
                    if (currentIndex >= 0 && currentIndex < flat.size) {
                        // WPT-capture-mode ambient flag: inbox capture IS WPT
                        // capture, so when [inboxMode] is on we suppress the
                        // synthesized component-name placeholder (real `_text`
                        // still renders) to match the Chromium browser-ref,
                        // mirroring the web harness's WPT_MODE path. In bundled
                        // mode inboxMode is false → we provide the default
                        // false → the render tree is byte-identical to before,
                        // so the committed baseline captures are unchanged.
                        CompositionLocalProvider(LocalWptCaptureMode provides inboxMode) {
                            CaptureView(
                                component = flat[currentIndex],
                                currentIndex = currentIndex,
                                totalCount = flat.size,
                                forceState = forceState,
                                captureWidthDp = captureWidthDp,
                                animationTime = animationTime,
                                keyframes = keyframes,
                                onCardPositioned = { bounds -> cardBoundsInWindow = bounds },
                                onRendered = { shouldCapture = true }
                            )
                        }
                    }
                }
            }
            CapturePhase.COMPLETE -> CompleteView(
                capturedCount = capturedCount,
                failedCount = failedCount,
                screenshotPath = screenshotManager.getScreenshotPath(),
                adbPullCommand = screenshotManager.getAdbPullCommand(),
                onDismiss = onCaptureComplete
            )
        }
    }
}

/**
 * Capture a region of the window using PixelCopy API.
 * This captures the fully-composited hardware-rendered frame,
 * including ALL visual effects (transforms, filters, clips, alpha).
 */
private suspend fun captureWithPixelCopy(window: Window, bounds: Rect): Bitmap? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null

    val bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)

    return suspendCancellableCoroutine { continuation ->
        try {
            PixelCopy.request(
                window,
                bounds,
                bitmap,
                { result ->
                    if (result == PixelCopy.SUCCESS) {
                        continuation.resume(bitmap)
                    } else {
                        Log.e(TAG, "PixelCopy failed with result: $result")
                        continuation.resume(null)
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (e: Exception) {
            Log.e(TAG, "PixelCopy exception", e)
            continuation.resume(null)
        }
    }
}

/**
 * Snapshot the composed canvas's offscreen [GraphicsLayer] to a saveable
 * software Bitmap (COMPOSED path only — the per-component path keeps
 * [captureWithPixelCopy] byte-identically).
 *
 * Why not PixelCopy: PixelCopy reads the composited WINDOW frame, so its
 * source rect is clamped to the window's 390x844 — a composed document taller
 * than the window truncated to one window of pixels (the wave-15
 * attachment-fixed-inside-transform-1 failure: 5132px doc → 390x844 white).
 * GraphicsLayer.toImageBitmap() instead renders the layer's RECORDED display
 * list (captured at the canvas's full laid-out size by the record{} modifier
 * in ComposedCaptureCanvas) into a bitmap of the LAYER's size — the same
 * "ideal-size offscreen render" contract as iOS's ImageRenderer
 * (ScreenshotManager.render, scale 1.0), which is why iOS never had the bug.
 *
 * The ImageBitmap is copied to ARGB_8888 because toImageBitmap() may hand back
 * a HARDWARE-config bitmap on API 28+ (GPU readback), and ScreenshotManager's
 * PNG encode + any later pixel inspection need a software-accessible config;
 * copy() performs the readback explicitly.
 *
 * Device-gated verification (documented per the campaign's no-emulator gate):
 * the JVM cannot execute GraphicsLayer, so the full-height behaviour is pinned
 * by source-scan tests (ComposedFullHeightCaptureTest) and must be confirmed
 * on-device by a TITAN composed run over attachment-fixed-inside-transform-1
 * asserting the saved PNG is 390x5132, matching web/iOS.
 */
private suspend fun captureComposedLayer(layer: GraphicsLayer): Bitmap? {
    // Degenerate-layer gate (pure decision, JVM-pinned in
    // ComposedFullHeightCaptureTest): a zero-area layer means the record
    // modifier never ran — fail loudly instead of encoding an empty PNG.
    if (!isCapturableLayerSize(layer.size.width, layer.size.height)) {
        Log.e(TAG, "Composed layer has degenerate size ${layer.size} — record never ran?")
        return null
    }
    return try {
        // Render the recorded display list offscreen at full layer size (the
        // suspend readback documented for Compose 1.7's GraphicsLayer)…
        val image = layer.toImageBitmap()
        // …then force a software ARGB_8888 copy for the PNG encoder (see doc).
        image.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
    } catch (e: Exception) {
        // No silent fallthrough: surface the failure to the capture effect,
        // which logs it against the fixture's test key.
        Log.e(TAG, "GraphicsLayer snapshot failed", e)
        null
    }
}

/**
 * Pure gate for [captureComposedLayer]: a layer is snapshot-worthy only with
 * strictly positive area on both axes. Extracted (and internal) so the plain
 * JVM suite can pin the decision without a device — GraphicsLayer itself is
 * Android-only, but this rule is what stands between a mis-wired record and a
 * silently-saved empty PNG.
 */
internal fun isCapturableLayerSize(width: Int, height: Int): Boolean =
    width > 0 && height > 0

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFF60A5FA))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Preparing screenshot capture...", color = TextSubtitle)
        }
    }
}

@Composable
private fun ErrorView(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Capture Error",
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = Color(0xFFF87171)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = Color(0xFFF87171),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun CaptureView(
    component: IRComponent,
    currentIndex: Int,
    totalCount: Int,
    forceState: String? = null,
    captureWidthDp: Int = 390,
    animationTime: Double? = null,
    keyframes: Map<String, List<com.styleconverter.runtime.core.ir.IRKeyframeStop>> = emptyMap(),
    onCardPositioned: (Rect) -> Unit,
    onRendered: () -> Unit
) {
    val density = LocalDensity.current

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Progress header (dark theme)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(HeaderBg)
                .drawBottomBorder(1.dp, BorderColor)
                .padding(12.dp)
        ) {
            Text(
                text = "Capturing Screenshots",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextWhite
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (currentIndex + 1).toFloat() / totalCount },
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF60A5FA),
                trackColor = Color(0x33FFFFFF)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${currentIndex + 1} / $totalCount - ${component.name}",
                fontSize = 14.sp,
                color = TextSubtitle
            )
        }

        // Component render area — captured via PixelCopy.
        //
        // The canvas is a chromeless 390dp-wide surface with 16dp padding on
        // a solid background: #1A1A2E for the bundled/baseline path, WHITE
        // in WPT capture mode (the corpus-v4 canvas — see CaptureCanvas). Natural height (no chrome, no card
        // border, no labels). Matches the iOS `CaptureCanvas` and web
        // `<CaptureCanvas>` contract so captures are pixel-diffable.
        //
        // We wrap in `verticalScroll` so tall components (long flex stacks,
        // grids) can still fit during layout even if they exceed the
        // available vertical space. PixelCopy still grabs only the canvas
        // rect, which is the natural height of the component + padding.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            CaptureCanvas(
                component = component,
                forceState = forceState,
                animationTime = animationTime,
                keyframes = keyframes,
                canvasWidth = captureWidthDp.dp,
                onPositioned = { posInWindow, widthPx, heightPx ->
                    onCardPositioned(Rect(
                        posInWindow.x.roundToInt(),
                        posInWindow.y.roundToInt(),
                        (posInWindow.x + widthPx).roundToInt(),
                        (posInWindow.y + heightPx).roundToInt()
                    ))
                },
                onRendered = onRendered
            )
        }
    }
}

/**
 * Chromeless per-component capture surface used by the three-way screenshot
 * comparison pipeline (iOS / Android / Web).
 *
 * Contract — matches iOS `CaptureCanvas` and web `<CaptureCanvas>`:
 *   - Width            : exactly 390dp (CAPTURE_WIDTH override via the
 *                        `captureWidth` intent extra — docs/DYNAMIC_CAPTURE.md §2)
 *   - Height           : component's natural height (no clamping, no minimum)
 *   - Background       : solid #1A1A2E (no alpha compositing) on the
 *                        bundled/baseline path; solid WHITE in WPT capture
 *                        mode (LocalWptCaptureMode — the corpus-v4 white
 *                        canvas, mirrored by the white browser-ref and the
 *                        web/iOS WPT canvases so white WPT ink vanishes
 *                        identically on every surface)
 *   - Padding          : 16dp on all sides
 *   - No header, footer, border, or label — just the component.
 *
 * PixelCopy captures exactly this surface by using `onGloballyPositioned` to
 * report the canvas rect in window coordinates. The outer `CaptureView` must
 * set the emulator density to 160 (1dp == 1px) so captures land at 390 px
 * wide, matching iOS and web.
 */
@Composable
private fun CaptureCanvas(
    component: IRComponent,
    forceState: String? = null,
    animationTime: Double? = null,
    keyframes: Map<String, List<com.styleconverter.runtime.core.ir.IRKeyframeStop>> = emptyMap(),
    canvasWidth: Dp = CaptureCanvasWidth,
    onPositioned: (androidx.compose.ui.geometry.Offset, Float, Float) -> Unit,
    onRendered: () -> Unit
) {
    // Give Compose a frame to settle, then tell the caller we're ready.
    // The delay is conservatively larger for components with complex
    // sub-trees (grids, transforms) where layout may span multiple frames.
    LaunchedEffect(component.id) {
        delay(150)
        onRendered()
    }

    // `onGloballyPositioned` must come BEFORE `.padding()` in the modifier
    // chain so it reports the full 390dp outer rect (including padding +
    // background), not the post-padding inner content-box. Using the inner
    // rect would crop 16dp off every side → 358dp captures that don't line
    // up with iOS / web's 390px.
    Box(
        modifier = Modifier
            .width(canvasWidth)
            // Mode-split canvas background (TITAN-WHITE lane, pure helper in
            // the runtime's WptCaptureMode.kt): the TITAN inbox/WPT path
            // (LocalWptCaptureMode=true, provided by ScreenshotCaptureScreen)
            // paints the corpus-v4 WHITE canvas so WPT white ink vanishes
            // exactly as it does in the white browser-ref; the bundled
            // 327-pair path (flag default false) keeps the dark #1A1A2E
            // stage byte-identically.
            .background(captureCanvasBackground(LocalWptCaptureMode.current, CaptureCanvasBg))
            // Hook markers — the native twins of the web reference's
            // `data-force-state` / `data-animation-time` canvas stamps
            // (docs/DYNAMIC_CAPTURE.md §1/§4): let a capture/UI-automator
            // script verify a hooked run actually ran hooked (a seized run
            // must never silently degrade to a live capture — spec 07 §5).
            // Plain "capture-canvas" on base runs so the default path stays
            // byte-identical to the historical tag.
            .testTag(buildString {
                append("capture-canvas")
                if (forceState != null) append("-force-state-$forceState")
                if (animationTime != null) append("-anim-time-$animationTime")
            })
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                onPositioned(pos, coords.size.width.toFloat(), coords.size.height.toFloat())
            }
            .padding(CaptureCanvasPadding)
    ) {
        // Root containing block for the wave-6 dynamic-value channel: the
        // canvas CONTENT box (width − 2×16, i.e. 358dp at the default 390),
        // which is exactly the base web resolves root-level % / calc(…%…)
        // against (the web CaptureCanvas div is the component's containing
        // block; its content box is the same 358px). Height stays unknown —
        // the canvas is content-sized on the block axis, matching web.
        androidx.compose.runtime.CompositionLocalProvider(
            com.styleconverter.runtime.core.variables.LocalContainingBlock provides
                com.styleconverter.runtime.core.variables.ContainingBlock(
                    widthPx = (canvasWidth - CaptureCanvasPadding * 2).value
                ),
            // Render-surface width channel (spec 06 §4): media
            // min/max-width buckets evaluate against the CAPTURE CANVAS
            // width — never the device screen. Full canvas width (not the
            // content box): the canvas is the analogue of web's viewport-
            // sized capture surface, and DYNAMIC_CAPTURE.md pins 390 as the
            // width the default truth table is built on.
            com.styleconverter.runtime.core.media.MediaBucketEvaluator.LocalRenderSurfaceWidthPx provides
                canvasWidth.value,
            // Forced-state set (spec 06 §6): one condition per capture run,
            // resolved as active on every component under this canvas.
            com.styleconverter.runtime.core.states.DynamicStyleResolver.LocalForcedStates provides
                (forceState?.let { setOf(it) } ?: emptySet()),
            // Document keyframes channel (spec 07 §1.2): @keyframes are
            // document-scoped, the harness owns the document — same
            // division of labor as the web harness's useKeyframeRules.
            com.styleconverter.runtime.animations.KeyframeAnimationDriver.LocalDocumentKeyframes provides
                keyframes,
            // CAPTURE_ANIMATION_TIME (spec 07 §5): every animation under
            // this canvas renders its state at absolute time t, paused —
            // null keeps the historical live path byte-identical.
            com.styleconverter.runtime.animations.KeyframeAnimationDriver.LocalForcedAnimationTime provides
                animationTime
        ) {
        if (isOutOfFlowRoot(component)) {
            // A standalone capture of a `position: absolute|fixed` component
            // must mirror the web canvas semantics (CSS 2.1 §9.6 + §10.1):
            //   1. OUT OF FLOW — it contributes NO height, so the canvas
            //      collapses to padding-only (web: 390x32 for the
            //      block-flow `floating` child; Android rendered a 62px
            //      canvas with the box in flow — crop 0.833).
            //   2. Declared insets anchor at the containing block's PADDING
            //      EDGE — the canvas's outer edge here (border 0), NOT the
            //      padded content origin. The runtime chain already applies
            //      the top/left offset, so we only back out the canvas
            //      padding on an axis that carries an inset.
            //   3. PER-AXIS auto-inset rule (CSS 2.1 §10.3.7 / §10.6.4): an
            //      axis whose insets are ALL `auto` keeps the box at its
            //      STATIC position — the content origin INSIDE the canvas
            //      padding ((16,16)), which is where Chromium leaves an
            //      all-auto abs/fixed box on the web canvas. Backing out the
            //      padding there shifted the native capture 16px up-left.
            androidx.compose.ui.layout.Layout(
                // ComponentHost: the v2 entry point — publishes ITEM
                // placement parent-data (inert under this measuring
                // Layout, which only implements the out-of-flow contract)
                // then delegates to the style engine.
                content = { ComponentHost.Render(component) }
            ) { measurables, constraints ->
                val pad = CaptureCanvasPadding.roundToPx()
                // Measure with the canvas's width budget but unbounded
                // height (out-of-flow boxes don't shrink to the collapsed
                // canvas); min constraints relaxed so the box keeps its
                // natural size.
                val loose = constraints.copy(
                    minWidth = 0, minHeight = 0,
                    maxHeight = androidx.compose.ui.unit.Constraints.Infinity
                )
                val placeables = measurables.map { it.measure(loose) }
                // Per-axis anchor (rules 2 + 3 above): back out the canvas
                // padding ONLY on an axis with a declared inset (anchor =
                // padding edge); an all-auto axis stays at offset 0 — the
                // padded content origin, i.e. the CSS static position.
                val dx = if (hasHorizontalInset(component)) -pad else 0
                val dy = if (hasVerticalInset(component)) -pad else 0
                // Report 0x0 — out of flow — and paint from the per-axis
                // anchor computed above. PixelCopy's canvas rect then clips
                // exactly where the web capture does.
                layout(0, 0) {
                    placeables.forEach { it.place(dx, dy) }
                }
            }
        } else {
            // v2 runtime shim — see ComponentHost: itemPlacement
            // parent-data + engine delegation, zero extra layout nodes.
            ComponentHost.Render(component)
        }
        } // CompositionLocalProvider (root containing block)
    }
}

/**
 * True when a ROOT-LEVEL capture subject is absolutely/fixed positioned —
 * the standalone-capture out-of-flow contract above only applies to the
 * component the canvas hosts directly (children inside a tree render
 * through the runtime's positioned-container path instead).
 */
internal fun isOutOfFlowRoot(component: IRComponent): Boolean {
    return component.properties.any { p ->
        p.type == "Position" &&
            ((p.data as? JsonPrimitive)?.contentOrNull?.uppercase() in setOf("ABSOLUTE", "FIXED"))
    }
}

// IR inset property types per axis — the physical sides plus their
// LTR/horizontal-tb logical resolutions (the runtime's PositionExtractor maps
// InsetInlineStart→left, InsetBlockStart→top, … the same way), so a
// logical-inset fixture anchors identically to its physical twin.
private val HorizontalInsetTypes = setOf("Left", "Right", "InsetInlineStart", "InsetInlineEnd")
private val VerticalInsetTypes   = setOf("Top", "Bottom", "InsetBlockStart", "InsetBlockEnd")

/**
 * True when the out-of-flow capture subject declares ANY horizontal inset.
 * Per CSS 2.1 §10.3.7, `left`/`right` both `auto` keeps an absolutely
 * positioned box at its STATIC inline position, so the canvas must NOT back
 * out its padding on that axis. Presence-based approximation: an explicit
 * `left: auto` IR property would count as an inset here — accepted, since no
 * fixture declares an auto inset explicitly (Chromium treats it as all-auto).
 */
internal fun hasHorizontalInset(component: IRComponent): Boolean =
    component.properties.any { it.type in HorizontalInsetTypes }

/** Vertical twin of [hasHorizontalInset] (CSS 2.1 §10.6.4: `top`/`bottom`
 *  both `auto` → static block position) — same presence rule per axis. */
internal fun hasVerticalInset(component: IRComponent): Boolean =
    component.properties.any { it.type in VerticalInsetTypes }

// Shared capture-canvas constants. Kept at file scope so tests, debug tools,
// and future capture modes can reference the same values.
private val CaptureCanvasWidth   = 390.dp
private val CaptureCanvasPadding = 16.dp
// wave-25 round 3 — the COMPOSED canvas's image-space frame, taken from the
// runtime's shared constant so Compose, SwiftUI and web can never disagree
// about how wide the ref's frame is. Deliberately a SEPARATE name from
// [CaptureCanvasPadding] (the per-component dark-stage canvas's 16dp inset,
// which is a product/baseline choice and must never move with the ref
// contract) even though the two numbers coincide today.
private val CaptureCanvasFrame   = WPT_CANVAS_FRAME_DP.dp

/**
 * wave-24 B-RC5 — the composed canvas's resolved per-side pad. PHYSICAL
 * sides (not start/end): the WPT canvas is always LTR-framed, so naming the
 * sides left/right keeps this readable against the ref CSS and against the
 * web/iOS twins. Built by [resolveComposedCanvasPadding].
 *
 * wave-25 round 3: each side is now FRAME + AUTHOR, where the frame is the
 * unconditional image-space canvas frame ([WPT_CANVAS_FRAME_DP]) and the
 * author part is whatever the document's body-root declares (default 0).
 * See [resolveComposedCanvasPadding] for why the two stopped being the same
 * number.
 */
internal data class CanvasPadding(
    val top: Dp, val right: Dp, val bottom: Dp, val left: Dp,
) {
    /** The horizontal inset the canvas content box loses — the number the
     *  runtime's containing-block channel needs (390 − left − right = 358 at
     *  the default), replacing the old uniform `padding * 2`. */
    val horizontal: Dp get() = left + right

    companion object {
        /** The bare canvas FRAME on all four sides — what every document
         *  without a padding-declaring body-root gets. Equal to the old
         *  wave-24 default (16dp), so those captures are byte-identical;
         *  what changed is that a body-root declaring `padding: 0` no longer
         *  removes it (wave-25 round 3). */
        val DEFAULT = CanvasPadding(
            CaptureCanvasFrame, CaptureCanvasFrame,
            CaptureCanvasFrame, CaptureCanvasFrame,
        )
    }
}
private val CaptureCanvasBg      = Color(0xFF1A1A2E)
// Composed-canvas minimum height. Mirrors capture-browser-ref.mjs, whose
// injected `:where(body){min-height:100vh}` at a 600px viewport floors the ref
// PNG height at 600 (docHeight = max(scrollHeight, 600)); the web composed
// canvas uses the same `minHeight:600px`. Matching it keeps the composed PNG's
// dark tail identical to the ref's when content is shorter than 600dp.
// wave-26 (lane RES): the literal moved into the runtime
// (COMPOSED_CANVAS_MIN_HEIGHT_DP) because composedIcbHeightDp needs the same
// floor — a number spelled in two places is how the old constant ICB height
// drifted from the ref in the first place. Value unchanged (600).
private val ComposedCanvasMinHeight =
    com.styleconverter.runtime.core.renderer.COMPOSED_CANVAS_MIN_HEIGHT_DP.dp

/**
 * Resolve the composed canvas background (FIX 3, TITAN Round 4b) — the native
 * twin of the web harness's `resolveCanvasBackground`. Find the document's
 * `meta.role == 'body-root'` component (a document has one body) and read its
 * BackgroundColor through the SAME [ValueExtractors.extractColor] the renderer
 * uses, so the painted color matches what the runtime would render. When there
 * is no body-root, or it declares no background, fall back to the WPT canvas
 * default — WHITE since the corpus-v4 boundary ([WPT_CANVAS_BACKGROUND]; the
 * composed path is WPT-ONLY, so unlike the per-component canvas there is no
 * dark-stage branch here). An author body background still wins, exactly as
 * it beats the ref's zero-specificity `:where()` injection.
 *
 * Wave 15 (NATIVES-ALPHA): the extracted color is ALPHA-COMPOSITED over the
 * white canvas rather than painted verbatim — the ref's page canvas blends a
 * translucent `body{background}` source-over onto white, so `rgba(0,0,0,0)`
 * must yield WHITE (verbatim it darkened the opaque capture PNG — the iOS
 * 0.000 on background-color-transparent-animation-in-body had the same
 * verbatim bug here). The pure decision is the runtime's
 * [composedCanvasBackground] (unit-pinned in WptCanvasBackgroundTest,
 * twinned by iOS WPTCanvas.composedBackground).
 *
 * wave-27 A-RC1: the propagation is now GATED on containment. css-contain-2
 * §3.5 removes a contained root/body from css-backgrounds-3 §2.11.2's
 * propagation path, so the canvas keeps the UA white and the body-root paints
 * its own box (contain-body-bg-001..004 flooded red at ~0.62 before this).
 * The decision itself is the runtime's pure
 * [containmentBlocksCanvasPropagation] so all three canvases share one rule.
 */
internal fun resolveComposedCanvasBackground(roots: List<IRComponent>): Color {
    // A document has one body — first `body-root` wins, same as web/iOS.
    val bodyRoot = roots.firstOrNull { it.role == "body-root" }
    // Extract the body-root's own background through the SAME extractor the
    // renderer uses (null when no body-root / no declared background) …
    val bg = bodyRoot
        ?.properties?.firstOrNull { it.type == "BackgroundColor" }
        ?.data?.let { ValueExtractors.extractColor(it) }
    // … read its `Contain` leaf as the keyword list the converter emits
    // (`["LAYOUT"]`, `["STRICT"]`, `contain: none` → `["NONE"]`) …
    val contain = bodyRoot
        ?.properties?.firstOrNull { it.type == "Contain" }
        ?.data?.let { containKeywords(it) }
    // … then let the runtime's pure composed-canvas rule apply the wave-27
    // containment gate and composite the rest over the corpus-v4 white.
    return composedCanvasBackground(
        bg,
        contained = containmentBlocksCanvasPropagation(contain),
    )
}

/**
 * wave-27 A-RC1 — read a `Contain` IR leaf as its keyword list, for
 * [containmentBlocksCanvasPropagation]. Deliberately a LOCAL reader and not
 * a call into PerformanceExtractor: that extractor expands `strict`/`content`
 * into their component keywords and DROPS `none` to an empty set, both of
 * which erase the distinction this gate needs (`none` must stay visible so
 * the shared rule — not this reader — decides). Handles the two shapes the
 * wire actually carries, in the same order the applier-side reader does:
 * the normal keyword ARRAY, and a bare keyword STRING (space-separated).
 * Anything else yields null, which the gate reads as "no containment".
 */
private fun containKeywords(data: JsonElement): List<String>? = when (data) {
    // Normal emission: ContainProperty.values serialized as a string array.
    is JsonArray -> data.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    // Defensive: a single primitive, possibly a space-separated keyword list.
    is JsonPrimitive -> data.contentOrNull?.trim()?.split(Regex("\\s+"))
    // Objects / null / anything else: not a keyword list — no containment.
    else -> null
}

/**
 * wave-24 B-RC5 — the composed canvas's per-side pad, in dp. The native twin
 * of the web harness's `resolveCanvasPadding` and iOS's
 * `ComposedCaptureCanvas.resolvedPadding`.
 *
 * WHY (wave 24): capture-browser-ref.mjs used to frame every reference page
 * with a ZERO-specificity `:where(body) { padding: 16px }`. Per CSS Selectors
 * L4 §17 `:where()` contributes no specificity, so a ref declaring its OWN
 * `body { padding: 0 }` (0,0,1) WON and rendered with no body pad. This
 * canvas hardcoded 16dp, so every such test rendered at a (+16,+16) offset
 * against its ref — MEASURED as the ENTIRE divergence of
 * css-masking/clip-path-circle-007, whose test and ref both open with
 * `body, div { padding: 0; margin: 0 }`.
 *
 * WHY IT CHANGED (wave 25 round 3): at CAL-RC1 the ref pipeline stopped
 * injecting that padding entirely. The page renders at 358 wide with
 * `padding: 0` and the 16px frame is memcpy'd around the finished PNG
 * (capture-browser-ref.mjs padPngBuffer). So the two halves that wave 24
 * conflated have SPLIT:
 *   * the FRAME ([CaptureCanvasFrame]) is unconditional — no author rule can
 *     cancel an image-space translation, so a `body { padding: 0 }` ref now
 *     renders its content at (+16,+16) like every other ref;
 *   * the AUTHOR body padding is an ADDITIONAL inset INSIDE that frame,
 *     defaulting to ZERO (the ref's injected value is now 0).
 * Hence each side resolves to `frame + declared`. Concretely: no declaration
 * → 16 (unchanged, so every wave-24-era capture without a body pad is
 * byte-identical); `padding: 0` → 16 (wave 24 gave 0 — that is the stale
 * calibration this round repairs); `padding: 40px` → 56.
 *
 * PER SIDE, because the cascade is per-longhand: a ref declaring only
 * `padding-left: 0` leaves the other three sides at the frame alone. Only
 * CONCRETE px are honored — the converter emits a runtime-dependent length
 * (`em`, `%`, `calc()`) with no absolute value, and this canvas has no honest
 * answer for it, so that side keeps the bare frame rather than guessing.
 */
internal fun resolveComposedCanvasPadding(roots: List<IRComponent>): CanvasPadding {
    // Same lookup rule as resolveComposedCanvasBackground — one body per doc.
    val bodyRoot = roots.firstOrNull { it.role == "body-root" }
        ?: return CanvasPadding.DEFAULT
    // One reader for all four sides: find the longhand, extract it through the
    // SAME ValueExtractors.extractDp path the renderer's appliers use, and add
    // it to the frame when it resolved to a concrete Dp (null ⇒ property
    // absent OR runtime-dependent ⇒ this side keeps the bare frame).
    fun side(type: String): Dp {
        val dp = bodyRoot.properties.firstOrNull { it.type == type }
            ?.data?.let { ValueExtractors.extractDp(it) }
            ?: return CaptureCanvasFrame
        // CSS 2.1 §8.4 forbids negative padding; clamp defensively so a
        // malformed IR can never pull content outside the canvas frame.
        return CaptureCanvasFrame + (if (dp.value < 0f) 0.dp else dp)
    }
    return CanvasPadding(
        top    = side("PaddingTop"),
        right  = side("PaddingRight"),
        bottom = side("PaddingBottom"),
        left   = side("PaddingLeft"),
    )
}

/**
 * COMPOSED capture host (TITAN Round 3; full-height since the ANDROID-STITCH
 * lane). No progress chrome — the canvas renders the WHOLE document (all
 * roots, document order), records its draw into [graphicsLayer] for the
 * offscreen snapshot, and reports its outer rect (readiness gate + height log).
 *
 * The host wraps the canvas in `verticalScroll` for its CONSTRAINTS, not for
 * scrolling: a plain fillMaxSize Box hands the canvas maxHeight = the 844px
 * window, and Compose COERCES the measured size into constraints — the canvas
 * itself came out 844 tall and every root past the window edge was cut (the
 * wave-15 truncation: attachment-fixed-inside-transform-1 composes to 5132px).
 * `verticalScroll` measures its child with Constraints.Infinity on the block
 * axis (the same trick the per-component CaptureView already uses for tall
 * components), so the canvas lays out at its FULL natural height and the
 * recorded layer covers all of it. The scroll state is never scrolled —
 * offset stays 0, content is static, capture stays deterministic.
 */
@Composable
private fun ComposedCaptureView(
    roots: List<IRComponent>,
    forceState: String? = null,
    captureWidthDp: Int = 390,
    animationTime: Double? = null,
    keyframes: Map<String, List<com.styleconverter.runtime.core.ir.IRKeyframeStop>> = emptyMap(),
    graphicsLayer: GraphicsLayer,
    onCanvasPositioned: (Rect) -> Unit,
    onRendered: () -> Unit
) {
    // Top-start alignment: the canvas anchors at the window top-left, so the
    // visible window shows the document head while the offscreen layer holds
    // the full height (PixelCopy geometry no longer constrains this path).
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Unbounded block-axis constraints for the canvas — see the
            // function doc above. Never scrolled; rememberScrollState() stays
            // at 0 for the life of the fixture.
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopStart
    ) {
        ComposedCaptureCanvas(
            roots = roots,
            forceState = forceState,
            animationTime = animationTime,
            keyframes = keyframes,
            graphicsLayer = graphicsLayer,
            canvasWidth = captureWidthDp.dp,
            onPositioned = { posInWindow, widthPx, heightPx ->
                onCanvasPositioned(Rect(
                    posInWindow.x.roundToInt(),
                    posInWindow.y.roundToInt(),
                    (posInWindow.x + widthPx).roundToInt(),
                    (posInWindow.y + heightPx).roundToInt()
                ))
            },
            onRendered = onRendered
        )
    }
}

/**
 * The composed capture surface — the native twin of the web harness's
 * ComposedTestCanvas (apps/web-harness/src/ui/ComposedCaptureGallery.tsx),
 * framed to mirror capture-browser-ref.mjs EXACTLY so the composed PNG is
 * directly diffable against the Chromium browser-ref:
 *   - Width       : 390dp            (CANVAS_WIDTH in capture-browser-ref.mjs)
 *   - Min-height  : 600dp            (the ref's min-height:100vh at 600 viewport)
 *   - Background  : WHITE            (CANVAS_BG — the ref html+body since
 *                                     the corpus-v4 white-canvas boundary)
 *   - Padding     : 16dp all sides   (CANVAS_PAD_PX — the ref's :where(body) pad)
 *   - Height      : natural (content), floored at 600dp
 *
 * Unlike the per-component CaptureCanvas (which renders ONE flattened
 * component), this stacks EVERY composed root in a Column in document order —
 * reproducing the reference page's block flow (bar heights, inter-element gaps,
 * vertical positions) that inject's per-component vertical stitch could not.
 * Each root is rendered through the identical ComponentHost.Render entry the
 * bundled gallery and per-component capture use, so the ONLY thing that differs
 * between the two capture strategies is the composition geometry, never the
 * per-node render.
 *
 * Absolute/fixed boxes (wave 17): the composed tree is wrapped in the
 * runtime's CanvasRootHoist.Host, which hoists every out-of-flow descendant —
 * fixed at any depth, absolute with no positioned ancestor — into an overlay
 * anchored at the UNPADDED canvas origin, exactly where the Chromium ref
 * anchors them (css-position-3 §3.1/§3.2). They reserve no flow space; the
 * per-component canvas's standalone out-of-flow trick (wave 1) is unrelated
 * and untouched.
 */
@Composable
private fun ComposedCaptureCanvas(
    roots: List<IRComponent>,
    forceState: String? = null,
    animationTime: Double? = null,
    keyframes: Map<String, List<com.styleconverter.runtime.core.ir.IRKeyframeStop>> = emptyMap(),
    graphicsLayer: GraphicsLayer,
    canvasWidth: Dp = CaptureCanvasWidth,
    onPositioned: (androidx.compose.ui.geometry.Offset, Float, Float) -> Unit,
    onRendered: () -> Unit
) {
    // Settle one frame (slightly longer than the per-component 150ms because a
    // whole document has a deeper layout tree), then signal ready for capture.
    LaunchedEffect(roots) {
        delay(250)
        onRendered()
    }

    // wave-26 (lane RES residual 1) — the composed canvas's MEASURED outer
    // height in dp, frozen after the first real measurement.
    //
    // Mirrors capture-browser-ref.mjs's TWO-PASS document-height rule exactly:
    // lay out once at the floor viewport, read the document height, set the
    // viewport to THAT number, screenshot — the ref never re-measures. Freezing
    // here reproduces the same single re-layout and, crucially, makes the
    // published `100vh` basis below deterministic instead of a
    // measure↔publish oscillation. Seeded at the floor so the very first
    // composition already anchors end-inset boxes at 568 (the wave-25
    // behaviour) rather than at a degenerate zero.
    var measuredCanvasHeightDp by androidx.compose.runtime.remember(roots) {
        androidx.compose.runtime.mutableStateOf(ComposedCanvasMinHeight.value)
    }
    // The freeze latch for the state above (one measurement per fixture).
    var canvasHeightFrozen by androidx.compose.runtime.remember(roots) {
        androidx.compose.runtime.mutableStateOf(false)
    }
    // Density for the px→dp conversion of the measured size below. The
    // runtime works in a px==dp space, so the ICB numbers MUST be dp.
    val canvasDensity = androidx.compose.ui.platform.LocalDensity.current
    // The ICB the hoist overlay and the viewport channel both anchor in.
    // Width from the LIVE canvas (CAPTURE_WIDTH can override it); height from
    // the frozen measurement, floored — both minus the frame per side.
    val composedIcbHeight =
        com.styleconverter.runtime.core.renderer.composedIcbHeightDp(measuredCanvasHeightDp).dp
    val composedIcbWidth = composedIcbExtentDp(canvasWidth.value).dp

    // FIX 3 (body/root background propagation) — TITAN Round 4b. The ref frames
    // every page with a ZERO-specificity `:where(html,body){background:WHITE}`
    // (the corpus-v4 canvas),
    // so a reference that sets its OWN `body{background:…}` (specificity 0,0,1)
    // WINS and paints the whole page that color (css-color/a98rgb-003's grey
    // page). The reader tags that body background as a `meta.role:'body-root'`
    // component; we resolve its BackgroundColor and paint the composed canvas
    // with it (fallback WHITE — WPT_CANVAS_BACKGROUND), mirroring the web
    // harness's
    // resolveCanvasBackground exactly. Pure per document → memoise on identity.
    val canvasBackground = androidx.compose.runtime.remember(roots) {
        resolveComposedCanvasBackground(roots)
    }
    // wave-24 B-RC5 (canvas PAD propagation) — the twin of FIX 3 above for
    // the other half of the ref's zero-specificity body frame. A ref that
    // declares `body { padding: 0 }` beats the injected
    // `:where(body){padding:16px}`, so this canvas must too or the whole
    // render sits (+16,+16) off (clip-path-circle-007). Defaults to 16dp on
    // every side, so padding-free documents are byte-identical. Pure per
    // document → memoise on identity alongside the background.
    val canvasPadding = androidx.compose.runtime.remember(roots) {
        resolveComposedCanvasPadding(roots)
    }
    // FIX 1 (UA default margins) — TITAN Round 4b — per-root effective UA
    // block margins, still the source of the HORIZONTAL blockquote/figure
    // insets below (IR-declared sides zeroed — the runtime's margin applier
    // renders those and wins over UA). Pure/testable helpers in UaBlockMargins.kt.
    val rootMargins = androidx.compose.runtime.remember(roots) {
        roots.map { root ->
            // Wave-17 out-of-flow roots (fixed, or absolute with no
            // positioned ancestor — the canvas root has none) are hoisted to
            // the canvas-root overlay and reserve NO flow space (pin S5), so
            // their UA margins must not inject gap Spacers either — a
            // browser collapses through an out-of-flow box as if it were
            // absent (CSS 2.1 §9.3.1). Same decision function the runtime's
            // hoist uses, so gap math and hoisting can never disagree.
            if (com.styleconverter.runtime.layout.position.CanvasRootHoist
                    .shouldHoistToCanvasRoot(root.properties, hasPositionedAncestor = false)
            ) UaMargins.ZERO
            else effectiveUaMargins(root)
        }
    }
    // RC-A4 (wave 19) — per-root VERTICAL stack plan. FIX 1 only folded the
    // UA-INJECTED margins; IR-DECLARED author margins rendered in full on
    // BOTH adjacent roots and STACKED (safe-001: 20px+20px = 96px root pitch
    // vs the ref's collapsed 76px — every later root drifted +20/40/60px).
    // The plan reads each root's declared block margins through the SAME
    // classifier the runtime's §8.3.1 machinery uses
    // (BlockMarginCollapse.blockMarginsOrNull over MarginExtractor.extract),
    // folds them into the collapsed-gap Spacers, and flags the root for a
    // block-margin STRIP via the LocalCollapsedMargin override channel the
    // renderer already honors. Auto/negative/relative/calc margins bail to
    // the FIX 1 behavior (never new wrongness). Wave-19 follow-up: roots
    // with ZERO flow footprint (canvas-hoisted, or RC1 static-position
    // under an active host) are margin-TRANSPARENT — §8.3.1 collapses the
    // neighbors' margins THROUGH them into ONE gap (see
    // collapsedRootStackGapsPx); only flow-sized out-of-flow roots still
    // bail.
    val rootPlans = androidx.compose.runtime.remember(roots) {
        // Wave-19 follow-up: whether the CanvasRootHoist.Host below will
        // actually ACTIVATE — the RC1 static-position zero-flow anchor is
        // host-gated in ComponentRenderer. Wave 21 (A-RC7): activation now
        // fires for ANY out-of-flow box (hoisted OR static-position), so a
        // document whose only out-of-flow boxes are no-inset absolutes
        // (conic-gradient-line-height-relative-units-001/002) zero-flows
        // them too — this fold sees the SAME broadened decision through the
        // SAME function the Host runs (one decision, two consumers), so
        // margin transparency and the renderer's footprint stay in lockstep.
        val hostActive = com.styleconverter.runtime.layout.position.CanvasRootHoist
            .hostActivates(roots)
        roots.map { root ->
            // Hoisted roots occupy no flow space (pin S5) — and (wave-19
            // follow-up) they are margin-TRANSPARENT: a browser collapses
            // the neighbors' block margins THROUGH an out-of-flow box as if
            // it were absent (CSS 2.1 §8.3.1 in-flow precondition, §9.3.1),
            // so the fold keeps ONE adjoining set open across this slot.
            // Contribution stays (0,0): §8.3.1 "margins of absolutely
            // positioned boxes do not collapse" — its own declared margins
            // render in the overlay and never push flow content.
            if (com.styleconverter.runtime.layout.position.CanvasRootHoist
                    .shouldHoistToCanvasRoot(root.properties, hasPositionedAncestor = false)
            ) {
                RootStackMargin(0f, 0f, stripDeclared = false, marginTransparent = true)
            } else {
                // Which block sides the IR declares (any Margin* covering them).
                val declared = declaredMarginSides(root.properties.map { it.type })
                // Wave-18 RC1 static-position root under an ACTIVE host: it
                // mounts in its Column slot at 0×0 (zeroFlowAnchor), so it is
                // margin-transparent too (wave-19 follow-up). Same renderer
                // decision function — never re-derived here.
                val staticPos = hostActive &&
                    com.styleconverter.runtime.layout.position.CanvasRootHoist
                        .rendersInFlowAsStaticPosition(root.properties, hasPositionedAncestor = false)
                // Static declared (top, bottom) px via the runtime's §8.3.1
                // classifier; null bails (out-of-scope value flavors).
                // Out-of-flow roots that KEEP their flow size (host inactive
                // — the RC1 anchor never engages) bail too: they render their
                // own margins in the flow, exactly the pre-fix behavior.
                // A zero-flow static-position root does NOT bail: its own
                // declared margins join the collapse-through set (§8.3.1's
                // empty-box model — the hypothetical static box's margins
                // are adjoining) and strip from its render like any other
                // folded root, so its slot anchor stays the §8.3.1
                // hypothetical position and nothing double-renders.
                // Wave 22 (B-RC2): the classifier now also resolves `em`
                // against the root's OWN declared FontSize (css-values-4
                // §5.1.1 — the base rides the same property list), because
                // dotted-001's three `margin: .5em; font-size: 92px` divs
                // bailed on the relative flavor and painted 46+46 = 92px of
                // inter-div space where the ref collapses to ONE 46px gap.
                // StaticEmMargin.verticalEdges is a strict SUPERSET of
                // BlockMarginCollapse.blockMarginsOrNull on non-em wires
                // (pin E8), so every wave-19 R/S/T value is unchanged; the
                // runtime's own §8.3.1 plan still uses the narrow
                // classifier, keeping the dark-stage baseline byte-stable.
                val staticEdges = if (isOutOfFlowRoot(root) && !staticPos) null else
                    StaticEmMargin.verticalEdges(root.properties)
                rootStackMargin(root._tag, "top" in declared, "bottom" in declared, staticEdges)
                    // Transparency rides the SAME plan entry so the fold and
                    // the render agree on this root's (zero) flow footprint.
                    .copy(marginTransparent = staticPos)
                    // wave-26 (lane RES residual 3a): fold in the HOIST BAND
                    // the root's own §8.3.1 plan would otherwise emit as
                    // padding OUTSIDE its border box. Both are outer spacing
                    // in the same adjoining region, so leaving each owner to
                    // emit its own ADDED where the browser takes ONE max()
                    // (worked example in withHoistBand's kdoc). The band is
                    // read through the runtime's own plan builder, so the
                    // number folded here is exactly the number suppressed at
                    // the render below — one decision, two consumers.
                    .let { plan ->
                        val band = com.styleconverter.runtime.core.renderer.ComponentRenderer
                            .composedRootHoistBand(root, uaBlockMargins = true)
                        withHoistBand(plan, band.topPx to band.bottomPx)
                    }
            }
        }
    }
    val rootGaps = androidx.compose.runtime.remember(rootPlans) {
        // Wave-19 follow-up: the transparency-aware fold — collapses the
        // whole {prev bottom, transparent roots' margins, next top} set to
        // ONE §8.3.1 max() gap instead of one gap per opaque neighbor.
        collapsedRootStackGapsPx(rootPlans)
    }

    // `onGloballyPositioned` BEFORE `.padding()` so it reports the full outer
    // 390dp × natural-height rect (including padding + background), matching the
    // ref's outer canvas — same ordering rationale as CaptureCanvas.
    Box(
        modifier = Modifier
            .width(canvasWidth)
            .heightIn(min = ComposedCanvasMinHeight)
            // Offscreen full-height record (ANDROID-STITCH lane): re-record the
            // canvas's ENTIRE draw pass — the background painted by the chained
            // .background below plus every padded root — into the hoisted
            // GraphicsLayer at this node's full laid-out size, then draw the
            // layer back so the on-window render is visually unchanged. record{}
            // replays the display list into the layer BEFORE any ancestor
            // (scroll container / window) clip applies, so content past the
            // 844px window edge is captured intact; the capture effect then
            // snapshots the layer via GraphicsLayer.toImageBitmap() (the
            // documented Compose-1.7 composable-to-bitmap path), mirroring
            // iOS's window-independent ImageRenderer contract. Placed BEFORE
            // .background in the chain because an earlier draw modifier wraps
            // the later ones — drawContent() here includes the background fill.
            .drawWithContent {
                // wave-26 lane BF-A — two-pass invalidation anchor. Reading
                // the coordinator's pass HERE (a snapshot read inside the
                // canvas's own draw) is what guarantees the layer is
                // RE-RECORDED when the coordinator flips SAMPLE→COMPOSITE. A
                // descendant's invalidation alone is not enough to rely on:
                // the whole point of the flip is that the layer must hold the
                // composite pass's display list when the capture reads it.
                BackdropPassCoordinator.current.pass
                graphicsLayer.record { this@drawWithContent.drawContent() }
                drawLayer(graphicsLayer)
            }
            .background(canvasBackground)
            .testTag("composed-capture-canvas")
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                onPositioned(pos, coords.size.width.toFloat(), coords.size.height.toFloat())
                // wave-26 lane BF-A — the pass-A bitmap's origin IS this
                // canvas's top-left, and backdrop elements report their own
                // box in window space, so the coordinator needs this offset to
                // map one into the other. Published from the same callback
                // that already reports the outer rect, so the origin and the
                // capture geometry can never disagree.
                BackdropPassCoordinator.current.publishCanvasOrigin(pos)
                // wave-26 (lane RES residual 1): the SAME laid-out height the
                // capture snapshots, in dp, threaded into the ICB extents
                // computed above. Frozen after the first non-degenerate
                // measurement — the ref measures once and re-lays-out once,
                // and freezing is what makes a `100vh` document terminate
                // instead of oscillating between measure and publish.
                if (!canvasHeightFrozen && coords.size.height > 0) {
                    canvasHeightFrozen = true
                    measuredCanvasHeightDp =
                        with(canvasDensity) { coords.size.height.toDp().value }
                }
            }
            // Wave 17: the canvas padding lives OFF this outer Box and on the
            // in-flow Column below, so the CanvasRootHoist overlay (hosted
            // between the two) starts from the RAW canvas origin and applies
            // its own `canvasFrame` translation — one place decides where the
            // initial containing block is. The outer rect reported to
            // PixelCopy is unchanged. (Wave 17 pinned that translation at
            // ZERO from the then-correct measurement that the ref anchored an
            // absolute `left:100` root at canvas x=100, not 116; wave-25
            // CAL-RC1 moved the ref's frame into image space, so it is now
            // the frame itself — see CanvasRootHoist.Host's canvasFrame doc.)
    ) {
        // Same dynamic-value channels as CaptureCanvas so composed renders
        // resolve %/calc, media buckets, forced states and animation-at-t
        // identically to per-component captures (the feeder leaves force/anim
        // unset for WPT, so these default to base state).
        androidx.compose.runtime.CompositionLocalProvider(
            com.styleconverter.runtime.core.variables.LocalContainingBlock provides
                com.styleconverter.runtime.core.variables.ContainingBlock(
                    // Content box = canvas width − the RESOLVED horizontal
                    // pad (358dp at the 16dp default), the same containing
                    // block the ref's padded body gives. wave-24 B-RC5: reads
                    // the resolved pad, not the constant, so a ref that zeroes
                    // its body padding hands % widths the full 390dp — the
                    // same number Chromium gives that ref's body content box.
                    widthPx = (canvasWidth - canvasPadding.horizontal).value
                ),
            // wave-26 (lane RES residual 2): the runtime-v1 media width basis
            // is the ref's RENDER viewport (358 at the 390 default), not the
            // framed 390 image. capture-browser-ref.mjs lays the ref page out
            // in a REF_RENDER_WIDTH viewport and memcpy's the 16px frame onto
            // the finished PNG, where no media query can observe it — so a
            // `(max-width: 380px)` bucket is ACTIVE in the ref and was
            // INACTIVE here. Composed capture only (this canvas), so the
            // per-component 390 basis and the dark stage are untouched.
            com.styleconverter.runtime.core.media.MediaBucketEvaluator.LocalRenderSurfaceWidthPx provides
                composedIcbWidth.value,
            // wave-26 (lane RES residual 2): the SAME viewport for vw/vh —
            // the runtime's DynamicValueResolver rewrites vw/vh/vmin/vmax
            // against this channel when it is non-null and keeps its
            // historical LocalConfiguration screen-dp basis when it is null
            // (every non-composed path), so the 327 dark-stage baselines
            // never see it. Height is the frozen measured ICB extent, which
            // is what the ref's second-pass viewport is.
            com.styleconverter.runtime.core.renderer.LocalComposedViewport provides
                com.styleconverter.runtime.core.renderer.composedViewportFor(
                    canvasWidthDp = canvasWidth.value,
                    measuredCanvasHeightDp = measuredCanvasHeightDp,
                ),
            com.styleconverter.runtime.core.states.DynamicStyleResolver.LocalForcedStates provides
                (forceState?.let { setOf(it) } ?: emptySet()),
            com.styleconverter.runtime.animations.KeyframeAnimationDriver.LocalDocumentKeyframes provides
                keyframes,
            com.styleconverter.runtime.animations.KeyframeAnimationDriver.LocalForcedAnimationTime provides
                animationTime
        ) {
            // Wave-17 canvas-root host: collects every out-of-flow
            // descendant (fixed anywhere; absolute with no positioned
            // ancestor) and renders it in an overlay anchored HERE — the
            // unpadded canvas origin — while the in-flow tree below skips
            // it entirely (no reserved space, pin S5). Identity fast path
            // inside the host keeps fixed-free documents byte-identical.
            // The dynamic-value CompositionLocals above deliberately wrap
            // the host so hoisted boxes resolve %/media/keyframes exactly
            // like their in-flow siblings.
            // Wave 22 (B-RC3): the canvas extents are the containing block
            // every hoisted box anchors in (fixed → viewport, css-position-3
            // §3.2; ICB-anchored absolute → initial containing block, §3.1).
            // They are needed only to resolve `right`/`bottom`-only insets
            // from the END edge.
            //
            // wave-25 round 3: those extents are the ref's RENDER VIEWPORT
            // (358×568 — capture-browser-ref.mjs REF_RENDER_WIDTH /
            // REF_RENDER_MIN_HEIGHT), not the 390×600 outer image, and the
            // origin is the frame corner. Wave 22 passed 390×600 from an
            // origin of (0,0), which was right against the OLD refs (their
            // CSS body pad moved in-flow content only, leaving the ICB at the
            // image corner). Under the image-space frame both numbers shift
            // together: the multicol `bottom:0; right:0` root now lands at
            // image (16+258, 16+468) = (274,484) — flush with the content
            // corner, framed exactly like the ref raster — instead of the
            // (290,500) that assumed an unframed ICB.
            com.styleconverter.runtime.layout.position.CanvasRootHoist.Host(
                roots,
                // Width from the LIVE canvas (CAPTURE_WIDTH can override it),
                // height from the FROZEN measured canvas extent floored at
                // 600 — both minus the frame on both sides, via the runtime's
                // pure helpers.
                //
                // wave-26 (lane RES residual 1): the height was a CONSTANT
                // composedIcbExtentDp(600) = 568, i.e. the ref's FLOOR rather
                // than the ref's height. capture-browser-ref.mjs sets its
                // second viewport to max(scrollHeight, 568), so on a document
                // taller than the floor the ref's ICB grows with the content
                // and a `bottom: 0` hoisted box lands at the CONTENT bottom —
                // Android anchored it 568dp from the canvas top instead
                // (iOS reads its live geo.size.height, web's ICB div grows
                // with the page, so the defect was Android-only). Short
                // documents still resolve to 568, so every wave-25 capture
                // whose content fits the floor is byte-identical.
                canvasWidth = composedIcbWidth,
                canvasHeight = composedIcbHeight,
                canvasFrame = CaptureCanvasFrame,
            ) {
                // Document flow: roots stacked top-to-bottom. FIX 1 injects the
                // COLLAPSED UA-default vertical margins as Spacers between roots so
                // the composed page reproduces the browser-ref's inter-`<p>` gaps
                // (rootGaps has size roots+1: [beforeFirst, between…, afterLast]).
                // Any horizontal UA inset (blockquote/figure 40px) wraps the root in
                // a start/end-padded Box. Mirrors the ref's block flow + the web
                // composed canvas's `margin: revert` on the stacked roots.
                // The 16dp canvas padding lives on THIS Column (moved off the
                // outer Box, wave 17) so in-flow content keeps the ref's padded
                // body geometry while the hoist overlay stays unpadded.
                // wave-24 B-RC5: the four sides come from the resolved pad
                // (16dp each unless the body-root declares its own), applied
                // as PHYSICAL start/end here because the WPT composed canvas
                // is always LTR-framed — see CanvasPadding's doc.
                Column(modifier = Modifier.fillMaxWidth().padding(
                    start  = canvasPadding.left,
                    top    = canvasPadding.top,
                    end    = canvasPadding.right,
                    bottom = canvasPadding.bottom,
                )) {
                    // wave-34 lane H (H1) — the per-root inline-block boxes and
                    // the ROOT segment plan. `rootSegments` is null for every
                    // document with no ≥2 run of declared inline-block roots,
                    // which is all but two tests in the whole frozen corpus, and
                    // the null branch below is the frozen stacking loop verbatim.
                    // See ComposedRootInlineFlow.kt for the enumerated blast
                    // radius and why the RULES live in the runtime facade.
                    val rootBoxes = composedRootInlineBoxes(roots)
                    val rootSegments = com.styleconverter.runtime.layout.InlineBlockAtom
                        .rootSegments(
                            boxes = rootBoxes,
                            // H3 reads the gap ABOVE each root — rootGaps is
                            // roots+1 long ([before…, …, afterLast]), so the
                            // trailing entry is dropped here.
                            blockGapsAbovePx = rootGaps.take(roots.size).map { it.toDouble() },
                        )
                    // The per-root render, extracted VERBATIM from the frozen
                    // loop so both walks below emit the identical node for a
                    // given root — only the PLACEMENT ever differs.
                    val renderRootAt: @androidx.compose.runtime.Composable (Int) -> Unit = { i ->
                        val root = roots[i]
                        // RC-A4: a stripped root renders with its block margins
                        // ZEROED through the runtime's §8.3.1 override channel
                        // (the same substitution MarginApplier performs for
                        // collapsing block children) — the declared values now
                        // live in the collapsed gap Spacers instead, so adjacent
                        // declared margins fold to max() like the browser.
                        // Inline (left/right) margins are untouched by the
                        // override and still render on the root. Renderers reset
                        // the local for children, so the strip is root-only.
                        // wave-26 (lane RES residual 3a): this root's HOIST
                        // BAND is now folded into rootGaps above (see
                        // withHoistBand), so the renderer must NOT emit it a
                        // second time as padding outside the root's border
                        // box. The channel names this exact root's id, and the
                        // extractor's ids are hierarchical, so the suppression
                        // cannot leak to a descendant container — no reset
                        // plumbing, and nothing outside this loop is ever
                        // suppressed.
                        val hosted: @androidx.compose.runtime.Composable () -> Unit = {
                            androidx.compose.runtime.CompositionLocalProvider(
                                com.styleconverter.runtime.spacing.BlockMarginCollapse
                                    .LocalHoistBandSuppressedFor provides root.id,
                                // RC-A4 strip rides the SAME provider call: a
                                // stripped root's block margins are ZEROED
                                // (they live in the gap Spacers instead), an
                                // unstripped root keeps null = "no override",
                                // which is the channel's default and therefore
                                // byte-identical to the pre-wave-26 branch.
                                com.styleconverter.runtime.spacing.BlockMarginCollapse
                                    .LocalCollapsedMargin provides
                                    (if (rootPlans[i].stripDeclared)
                                        com.styleconverter.runtime.spacing.CollapsedMargin(0f, 0f)
                                    else null),
                            ) { ComponentHost.Render(root) }
                        }
                        val m = rootMargins[i]
                        if (m.left > 0 || m.right > 0) {
                            // Horizontal UA inset (blockquote/figure) — pad the root
                            // box left/right; the runtime renders inside it.
                            Box(modifier = Modifier.padding(start = m.left.dp, end = m.right.dp)) {
                                hosted()
                            }
                        } else {
                            hosted()
                        }
                    }
                    if (rootSegments == null) {
                        // The FROZEN document flow: roots stacked top-to-bottom,
                        // one gap Spacer above each.
                        roots.indices.forEach { i ->
                            // Gap ABOVE this root (collapsed with the previous root's
                            // bottom margin; the first root's is its full top margin).
                            if (rootGaps[i] > 0f) Spacer(Modifier.height(rootGaps[i].dp))
                            renderRootAt(i)
                        }
                    } else {
                        // wave-34 H1 — the segment walk. A RUN of consecutive
                        // inline-block roots becomes ONE §9.4.2 row item (the
                        // gap above its FIRST member is the block gap that
                        // still applies — the interior ones are gone because
                        // inline-level siblings on a line box have none, which
                        // H3 already proved is a no-op here); every other
                        // segment is a single root stacked exactly as above.
                        rootSegments.forEach { seg ->
                            val memberBoxes = seg.indices.mapNotNull { rootBoxes[it] }
                            if (seg.isRun && memberBoxes.size == seg.indices.size) {
                                val first = seg.indices.first()
                                if (rootGaps[first] > 0f) Spacer(Modifier.height(rootGaps[first].dp))
                                ComposedRootInlineRow(memberBoxes) {
                                    seg.indices.forEach { renderRootAt(it) }
                                }
                            } else {
                                // A run whose boxes went missing between the plan
                                // and here is a harness bug, not layout state:
                                // stack its members like the frozen loop rather
                                // than place them against a short plan.
                                seg.indices.forEach { i ->
                                    if (rootGaps[i] > 0f) Spacer(Modifier.height(rootGaps[i].dp))
                                    renderRootAt(i)
                                }
                            }
                        }
                    }
                    // Trailing gap = the last root's (uncollapsed) bottom margin.
                    if (rootGaps[roots.size] > 0f) Spacer(Modifier.height(rootGaps[roots.size].dp))
                }
            }
        }
    }
}

@Composable
private fun CompleteView(
    capturedCount: Int,
    failedCount: Int,
    screenshotPath: String,
    adbPullCommand: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CardBg)
                .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Capture Complete!",
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                color = Color(0xFF22C55E)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "$capturedCount screenshots captured",
                fontSize = 16.sp,
                color = TextWhite
            )
            if (failedCount > 0) {
                Text(
                    text = "$failedCount failed",
                    fontSize = 14.sp,
                    color = Color(0xFFF87171)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Saved to:\n$screenshotPath",
                fontSize = 12.sp,
                color = TextSubtitle
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Pull with:\n$adbPullCommand",
                fontSize = 11.sp,
                color = TextIndex
            )
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .background(Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Continue to Gallery",
                    color = Color(0xFF60A5FA),
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * Predicate: does `parent` create a paint context that its children's
 * appearance depends on?
 *
 * Mirrors `parentCreatesContext` in
 * apps/web-harness/src/ui/CaptureGallery.tsx and the Swift twin in
 * apps/ios-harness/StyleConverterTest/Screenshot/ScreenshotCaptureView.swift —
 * identical IR property-type set, identical decision rules, so the flattened
 * capture lists line up by index across iOS/Android/web for inject-wpt-block.mjs.
 *
 * Properties that flag context-creation (cross-referenced to the IR types
 * the Kotlin converter emits under src/main/kotlin/app/irmodels/properties/):
 *   - ClipPath / Mask / MaskImage / Filter / BackdropFilter  (presence)
 *   - Overflow / OverflowX / OverflowY  = 'clip' | 'hidden'
 *   - MixBlendMode != 'normal'
 *   - Transform (non-empty array), Rotate / Scale / Translate (presence)
 *   - Opacity value < 1
 *
 * Rationale (swarm-002 RC1 — tools/titan/investigations/swarm-002/
 * css-overflow__clip-002.json + filter-effects__backdrop-filter-clip-rect-zoom.json):
 * EXTFIX-A nests inner elements inside their parent's IR `children`; the
 * Compose renderer recurses correctly so the parent's clip / blend /
 * transform / opacity context applies to the child inside the parent's
 * CaptureCanvas. But the legacy depth-first flatten also emits a SECOND
 * standalone capture of every child, which renders the child WITHOUT the
 * parent's paint context — that leaked PNG never matches the browser-ref
 * and trips false structural-divergence. Suppress it on every platform.
 */
internal fun parentCreatesContext(parent: IRComponent): Boolean {
    // No properties → no paint context. Cheap guard.
    if (parent.properties.isEmpty()) return false
    // Single linear pass so the cost stays O(n) on hot capture paths.
    for (p in parent.properties) {
        when (p.type) {
            // Pure-presence cases: any active value means a paint context.
            "ClipPath", "Mask", "MaskImage", "Filter", "BackdropFilter",
            "Rotate", "Scale", "Translate" -> return true
            // Overflow keywords — only `clip` and `hidden` clip content.
            // IR `data` is either a bare string or `{value: "..."}` depending
            // on the longhand parser shape; tryStringValue handles both.
            // The Kotlin enum serializer uppercases the value ("CLIP",
            // "HIDDEN") while the spec-grade parser lowercases it — normalise
            // to lowercase before comparing so the predicate fires on both.
            "Overflow", "OverflowX", "OverflowY" -> {
                val v = tryStringValue(p.data)?.lowercase()
                if (v == "clip" || v == "hidden") return true
            }
            // Blend-mode — only non-'normal' values create a blend context.
            // Same UPPER/lower variance as Overflow above.
            "MixBlendMode" -> {
                val v = tryStringValue(p.data)?.lowercase()
                if (v != null && v != "normal") return true
            }
            // Transform — IR carries a JSON array of transform-function
            // entries; any non-empty list is non-identity (the parser drops
            // the 'none' keyword before serialising).
            "Transform" -> {
                if (p.data is JsonArray && (p.data as JsonArray).isNotEmpty()) return true
            }
            // Opacity — IR data shape varies by extractor flavor (raw number,
            // `{value:Number}`, or the Kotlin-convert
            // `{alpha:Number, original:{type:'number',value:Number}}`).
            // Values < 1 create a stacking context with backdrop dependence.
            "Opacity" -> {
                val v = tryOpacityValue(p.data)
                if (v != null && v < 1.0) return true
            }
            else -> { /* no paint-context effect on descendants */ }
        }
    }
    return false
}

/** Extract a String from an IR JsonElement that may be a bare JsonPrimitive
 *  or a `{value: "..."}` JsonObject — matches the dual carrier shape the
 *  longhand parsers emit. */
private fun tryStringValue(el: JsonElement): String? {
    (el as? JsonPrimitive)?.contentOrNull?.let { return it }
    val obj = el as? JsonObject ?: return null
    return (obj["value"] as? JsonPrimitive)?.contentOrNull
}

/** Extract a Double from an IR JsonElement that may be a bare numeric
 *  JsonPrimitive or a `{value: <Number>}` JsonObject. */
private fun tryNumericValue(el: JsonElement): Double? {
    (el as? JsonPrimitive)?.doubleOrNull?.let { return it }
    val obj = el as? JsonObject ?: return null
    return (obj["value"] as? JsonPrimitive)?.doubleOrNull
}

/** Extract an opacity scalar from an IR JsonElement. Handles every carrier
 *  shape the IR has used historically:
 *    - bare number  (0.5)
 *    - `{value: 0.5}`  (parser-uniform shape)
 *    - `{alpha: 0.5, original: {type:'number', value:0.5}}`  (Kotlin convert) */
private fun tryOpacityValue(el: JsonElement): Double? {
    (el as? JsonPrimitive)?.doubleOrNull?.let { return it }
    val obj = el as? JsonObject ?: return null
    (obj["alpha"] as? JsonPrimitive)?.doubleOrNull?.let { return it }
    (obj["value"] as? JsonPrimitive)?.doubleOrNull?.let { return it }
    val orig = obj["original"] as? JsonObject ?: return null
    return (orig["value"] as? JsonPrimitive)?.doubleOrNull
}

/**
 * Depth-first pre-order flatten of the IR component tree: parent first, then
 * each child (recursively), then the next sibling. Mirrors iOS `flatten` in
 * `ScreenshotCaptureView.swift` and web `flatten` in `CaptureGallery.tsx` so
 * filename indices (000_*, 001_*, ...) align across all three platforms for
 * any fixture that uses nested children.
 *
 * SUPPRESSES standalone child captures when the parent creates a paint
 * context (clip-path, overflow:clip|hidden, opacity<1, etc. — see
 * parentCreatesContext above). The child is already captured visually-
 * correctly inside the parent's canvas; emitting it standalone leaks an
 * un-contextualised render into the comparator. Mirrored on iOS + web.
 */
internal fun flattenComponents(components: List<IRComponent>): List<IRComponent> {
    val out = mutableListOf<IRComponent>()
    // Recursive walker — append the node, then descend into its children
    // unless this node creates a paint context that owns the child's render.
    fun walk(c: IRComponent) {
        out.add(c)
        val kids = c.children ?: return
        if (kids.isEmpty()) return
        if (parentCreatesContext(c)) return
        kids.forEach(::walk)
    }
    components.forEach(::walk)
    return out
}

private enum class CapturePhase {
    LOADING,
    CAPTURING,
    COMPLETE,
    ERROR
}

// ── Border drawing modifiers ─────────────────────────────────────────────────

private fun Modifier.drawBottomBorder(width: Dp, color: Color): Modifier =
    this.then(
        Modifier.drawWithContent {
            drawContent()
            drawLine(
                color = color,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = width.toPx()
            )
        }
    )

private fun Modifier.drawTopBorder(width: Dp, color: Color): Modifier =
    this.then(
        Modifier.drawWithContent {
            drawContent()
            drawLine(
                color = color,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = width.toPx()
            )
        }
    )

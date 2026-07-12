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
import com.styleconverter.runtime.core.renderer.ComponentHost
import com.styleconverter.runtime.core.renderer.SlotComposer
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
 */
@Composable
fun ScreenshotCaptureScreen(
    forceState: String? = null,
    captureWidthDp: Int = 390,
    animationTime: Double? = null,
    inboxMode: Boolean = false,
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

    // TITAN inbox-mode state:
    //  - currentFixtureFile: the inbox *.json currently being rendered, so it
    //    can be consumed AFTER its last capture (crash-safe: an unconsumed
    //    fixture is re-picked on the next poll).
    //  - pollGeneration: bumped after each fixture to re-arm the loading effect
    //    (LaunchedEffect keyed on it) for the next poll. Unused in bundled mode.
    var currentFixtureFile by remember { mutableStateOf<File?>(null) }
    var pollGeneration by remember { mutableIntStateOf(0) }

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
            // titanInbox is appended so the feeder can grep-verify the app
            // actually entered inbox mode (same gate philosophy).
            Log.i(TAG, "Capture run config: forceState=${forceState ?: "none"} captureWidth=$captureWidthDp animationTime=${animationTime ?: "none"} titanInbox=$inboxMode")

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
                Log.i(TAG, "Titan inbox: loaded fixture ${file.name}")
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
            val composed = SlotComposer.compose(document)
            roots = composed
            // Wire keyframes ride the same decode (additive envelope key);
            // omit-when-empty on the wire → empty map = zero footprint.
            keyframes = document.keyframes ?: emptyMap()

            val flatSize = flattenComponents(composed).size
            Log.i(TAG, "Loaded ${composed.size} root components (flattened: $flatSize)")

            if (flatSize == 0) {
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

    // Capture logic using PixelCopy
    LaunchedEffect(shouldCapture, currentIndex) {
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        when (capturePhase) {
            CapturePhase.LOADING -> LoadingView()
            CapturePhase.ERROR -> ErrorView(error ?: "Unknown error")
            CapturePhase.CAPTURING -> {
                roots?.let { composedRoots ->
                    // Use the same depth-first flatten as the capture loop so
                    // the rendered component matches the one being saved.
                    val flat = flattenComponents(composedRoots)
                    if (currentIndex >= 0 && currentIndex < flat.size) {
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
        // The canvas is a chromeless 390dp-wide surface on a solid #1A1A2E
        // background with 16dp padding. Natural height (no chrome, no card
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
 *   - Background       : solid #1A1A2E (no alpha compositing)
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
            .background(CaptureCanvasBg)
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
            //   2. Insets anchor at the containing block's PADDING EDGE —
            //      the canvas's outer edge here (border 0), NOT the padded
            //      content origin. The runtime chain already applies the
            //      top/left offset, so we only back out the canvas padding.
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
                // Report 0x0 — out of flow — and paint from the canvas's
                // outer edge (offset −padding on both axes). PixelCopy's
                // canvas rect then clips exactly where the web capture does.
                layout(0, 0) {
                    placeables.forEach { it.place(-pad, -pad) }
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

// Shared capture-canvas constants. Kept at file scope so tests, debug tools,
// and future capture modes can reference the same values.
private val CaptureCanvasWidth   = 390.dp
private val CaptureCanvasPadding = 16.dp
private val CaptureCanvasBg      = Color(0xFF1A1A2E)

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

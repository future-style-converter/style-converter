package com.styleconverter.test

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.styleconverter.test.screenshot.ScreenshotCaptureScreen
import com.styleconverter.test.ui.ComponentListScreen

private const val TAG = "MainActivity"

/**
 * Main Activity for the SDUI Test Application.
 *
 * This app loads IR models from tmpOutput.json at runtime and renders
 * Compose UI based on the component definitions. It serves as a visual
 * test bed for the Style-Converter project.
 *
 * ## How It Works
 * 1. On launch, checks for storage permissions
 * 2. Runs screenshot capture for all components (saves to /sdcard/test_screenshots/)
 * 3. Shows completion summary
 * 4. Then displays the regular component gallery for browsing
 *
 * ## Testing Workflow
 * 1. Run main project: ./gradlew :converter:run --args="convert --from css --to ir -i fixtures/visual-test.json -o out"
 * 2. Copy out/tmpOutput.json to apps/android-harness/app/src/main/assets/
 * 3. Run this Android app - screenshots are auto-captured
 * 4. Pull screenshots: adb pull /sdcard/test_screenshots/ ./screenshots/
 */
class MainActivity : ComponentActivity() {

    private var hasStoragePermission by mutableStateOf(false)
    private var permissionChecked by mutableStateOf(false)

    /**
     * Dynamic-capture hooks (docs/DYNAMIC_CAPTURE.md — Android transport is
     * launch intent extras; web is the reference implementation):
     *
     *   adb shell am start -n com.styleconverter.test/.MainActivity \
     *       --es forceState active --ei captureWidth 250
     *
     * forceState — one runtime-v1 condition (hover|active|focus|disabled|
     * checked) treated as ACTIVE at style resolution on EVERY captured
     * component (spec 06 §6). Invalid values are rejected with a warning
     * (never crash, never force-by-guess).
     */
    private fun readForceStateExtra(): String? {
        val raw = intent?.getStringExtra("forceState")?.trim()?.lowercase()
        if (raw.isNullOrEmpty()) return null
        // Validate against the contract vocabulary — mirrors the web
        // harness's CAPTURE_FORCE_STATE validation.
        return if (raw in com.styleconverter.runtime.core.states.DynamicStyleResolver.FORCEABLE_STATES) {
            Log.i(TAG, "forceState=$raw (capture run resolves this condition as active)")
            raw
        } else {
            Log.w(TAG, "Ignoring invalid forceState extra \"$raw\" — expected one of hover|active|focus|disabled|checked")
            null
        }
    }

    /**
     * captureWidth — render-surface width override in px (CAPTURE_WIDTH
     * contract, docs/DYNAMIC_CAPTURE.md §2). Default 390 = the historical
     * canvas, byte-identical captures. Non-positive values are rejected.
     */
    private fun readCaptureWidthExtra(): Int {
        val raw = intent?.getIntExtra("captureWidth", 390) ?: 390
        return if (raw > 0) raw else {
            Log.w(TAG, "Ignoring non-positive captureWidth extra $raw — using default 390")
            390
        }
    }

    /**
     * animationTime — CAPTURE_ANIMATION_TIME transport (spec 07 §5 /
     * docs/DYNAMIC_CAPTURE.md §4): seconds on each animation's absolute
     * timeline. When present, EVERY animation renders its state at t,
     * paused (the runtime evaluates state-at-t instead of running clocks
     * — the native equivalent of the web reference's WAAPI seize):
     *
     *   adb shell am start -n com.styleconverter.test/.MainActivity \
     *       --es animationTime 0.5
     *
     * Validation mirrors capture-url.mjs: finite and >= 0 (0 IS meaningful
     * — the initial frame, fill-mode arithmetic included). Absent or
     * invalid ⇒ null ⇒ the historical live path, byte-identical captures.
     */
    private fun readAnimationTimeExtra(): Double? {
        // String extra is the documented transport (`--es animationTime
        // 0.5` — fractional seconds don't fit --ei, and --ef floats lose
        // the "one spelled transport" simplicity).
        val raw = intent?.getStringExtra("animationTime")?.trim()
        if (raw.isNullOrEmpty()) return null
        val parsed = raw.toDoubleOrNull()
        return if (parsed != null && parsed.isFinite() && parsed >= 0.0) {
            Log.i(TAG, "animationTime=$parsed (capture run renders every animation at t, paused)")
            parsed
        } else {
            Log.w(TAG, "Ignoring invalid animationTime extra \"$raw\" — expected a finite number of seconds >= 0")
            null
        }
    }

    /**
     * titanInbox — TITAN inbox-capture activation flag (this lane's new rail on
     * the existing intent-extra transport, alongside forceState/animationTime):
     *
     *   adb shell am start -n com.styleconverter.test/.MainActivity \
     *       --ez titanInbox true
     *
     * When true the app SKIPS the bundled-assets auto-capture and instead loops:
     * poll the on-device inbox for the oldest *.json IR document, render + capture
     * EVERY component through the identical capture path the bundled flow uses,
     * consume the fixture, and keep polling. The host feeder
     * (tools/titan/feed-android.mjs) pushes fixtures + pulls the resulting PNGs.
     *
     * Parsing is delegated to TitanInbox.isInboxModeRequested (unit-tested) and
     * accepts BOTH `--ez titanInbox true` (boolean) and `--es titanInbox true`
     * (string) so the feeder can use either am-extra flavour.
     */
    private fun readInboxModeExtra(): Boolean {
        val boolExtra = intent?.getBooleanExtra("titanInbox", false) ?: false
        val stringExtra = intent?.getStringExtra("titanInbox")
        val on = com.styleconverter.test.screenshot.TitanInbox
            .isInboxModeRequested(boolExtra, stringExtra)
        if (on) Log.i(TAG, "titanInbox=true → inbox poll-capture mode (bundled-assets auto-capture skipped)")
        return on
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasStoragePermission = permissions.values.all { it }
        permissionChecked = true
        Log.i(TAG, "Storage permission granted: $hasStoragePermission")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Go fully immersive — hide status bar and navigation bar
        // so the full 390x844dp screen is available for content,
        // exactly matching the web's 390x844px viewport.
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Check/request storage permissions
        checkStoragePermission()

        setContent {
            // Outer: dark frame matching web's #111 body background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF111111)),
                contentAlignment = Alignment.TopCenter
            ) {
                // Inner: 390x844dp phone frame matching web's #root.
                //
                // NO border / corner clip here. The frame used to draw a
                // 1dp rgba(255,255,255,0.15) rounded border to mimic the
                // web harness's decorative #root outline — but the web
                // CAPTURE path never includes that chrome, while Android's
                // PixelCopy region spans the full 390dp width and so
                // picked up the border's left/right columns in EVERY
                // component screenshot (a constant (60,60,77) 1-px fringe
                // at x=0/x=389 over the #1A1A2E canvas — verified by
                // pixel-sampling the captures). That was a permanent
                // ~0.5% pixel-mismatch tax + edge SSIM hit on every
                // Android↔web / Android↔iOS pair. Captures must be
                // chromeless (see CaptureCanvas contract in
                // ScreenshotCaptureScreen.kt), so the cosmetic border is
                // gone for good.
                Box(
                    modifier = Modifier
                        .width(390.dp)
                        .height(844.dp)
                        .background(Color(0xFF1A1A2E))
                ) {
                    MainContent(
                        hasPermission = hasStoragePermission,
                        permissionChecked = permissionChecked,
                        // Dynamic-capture hooks from the launch intent —
                        // read once at composition setup; a capture run is
                        // one activity launch, one hook configuration.
                        forceState = readForceStateExtra(),
                        captureWidthDp = readCaptureWidthExtra(),
                        animationTime = readAnimationTimeExtra(),
                        // TITAN inbox poll-capture mode (this lane): when true
                        // the capture screen never returns to the gallery — it
                        // loops on the on-device inbox instead.
                        inboxMode = readInboxModeExtra()
                    )
                }
            }
        }
    }

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - check for MANAGE_EXTERNAL_STORAGE
            hasStoragePermission = Environment.isExternalStorageManager()
            permissionChecked = true
            if (!hasStoragePermission) {
                Log.w(TAG, "Need MANAGE_EXTERNAL_STORAGE permission on Android 11+")
                // For testing, we'll proceed anyway - screenshots will go to app-specific storage
                hasStoragePermission = true
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-10
            val writePermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            val readPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )

            if (writePermission == PackageManager.PERMISSION_GRANTED &&
                readPermission == PackageManager.PERMISSION_GRANTED
            ) {
                hasStoragePermission = true
                permissionChecked = true
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                )
            }
        } else {
            // Pre-Marshmallow
            hasStoragePermission = true
            permissionChecked = true
        }
    }
}

@Composable
private fun MainContent(
    hasPermission: Boolean,
    permissionChecked: Boolean,
    forceState: String? = null,
    captureWidthDp: Int = 390,
    animationTime: Double? = null,
    inboxMode: Boolean = false
) {
    var captureComplete by remember { mutableStateOf(false) }

    if (!permissionChecked) {
        // Still checking permissions
        return
    }

    // In inbox mode the capture screen owns the app for the whole session —
    // it never signals completion (onCaptureComplete is inert), so we never
    // fall through to the gallery. `captureComplete` only matters for the
    // bundled one-shot flow.
    if ((inboxMode || !captureComplete) && hasPermission) {
        // Run screenshot capture first — the dynamic-capture hooks apply to
        // the capture pass only (the browsing gallery stays base-state).
        ScreenshotCaptureScreen(
            forceState = forceState,
            captureWidthDp = captureWidthDp,
            animationTime = animationTime,
            inboxMode = inboxMode,
            onCaptureComplete = {
                captureComplete = true
            }
        )
    } else {
        // Show regular component gallery
        ComponentListScreen()
    }
}

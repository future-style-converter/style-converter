package com.styleconverter.runtime.spacing

// ChUnitMetrics — wave-18 lane 2 (pin P1): the CSS `ch` unit basis.
//
// css-values-4 §6.1.1 defines 1ch as the advance measure of the glyph '0' in
// the element's font, "in the inline axis of the element". This helper
// measures that advance with the SAME platform text engine Compose shells
// out to (android.graphics Paint/Typeface), so a `width: 63.1ch` box fits
// exactly 63 characters of the text it will actually render.
//
// Wave 52 lane L8 (vertical-wedges) closed two gaps in that sentence:
//  • M-A — WHICH face. A face-less element (no `font-family`) measured the
//    platform DEFAULT (Roboto) while the label paints the bundled Inter
//    (ComponentRenderer's `?: InterFontFamily` bottom-out). Measured on
//    wave51-fix css-writing-modes/ch-units-vrl-005..008: every 5ch box was
//    55 px against the ref's 63 (Inter '0' = 12.6 px at 20 px). The Inter
//    Typeface needs a Context this static object does not hold, so the
//    renderer BINDS one ([bindInterFace], called from RenderComponent via
//    the lane's seam patch) and that installs the loader
//    ([installDefaultTypeface]); with none installed (JVM tests, a caller
//    outside the renderer) the historical DEFAULT measurement stands and
//    is logged once. ONLY the two families the label paints as Inter take
//    the loader ([paintsInter]): null (no declaration — the `?:` bottom-
//    out) and InterFontFamily itself (a stack naming `Inter`). Every other
//    family keeps the face it paints: `FontFamily.Default` — what
//    CssFontFamilyResolver answers for `system-ui`, `fantasy` or a list
//    of unavailable names like `Arial` — paints the platform default
//    (Roboto), so it measures Typeface.DEFAULT exactly as before wave 52
//    (fix-pass defect: routing it to Inter sized `font-family: Arial;
//    width: 10ch` at 126 px around a ~111 px Roboto run). Only Inter
//    REGULAR is measured — the pre-wave-52 DEFAULT measurement ignored
//    weight too; one corpus carrier is bold (text-decoration-inset-004's
//    h1, `width: 15ch`), and its wrap is unchanged by the 21 px gap.
//  • M-B — WHICH axis. Under `vertical-*` + `text-orientation: upright` the
//    '0' stands up and its inline advance is its VERTICAL advance
//    (round(ascent) + round(descent), Chromium's arithmetic: 19 + 5 = 24 at
//    20 px Inter, so 5ch = 120 — the ref's orange square); every other mode
//    keeps the x-advance. The decision is `VerticalInlineAxis
//    .chAdvanceIsVertical`; callers pass the answer as [measure]'s flag.
//
// JVM unit tests have no Android graphics runtime: android.graphics.Paint is
// an unmocked stub that throws on construction. measure() catches every
// throwable and returns null, which the resolver maps to the spec's own
// 0.5em fallback (§6.1.1: "assumed to be 0.5em wide") — so the pure suite
// exercises the fallback lane deterministically, and pins the upright
// arithmetic through the injectable [verticalMetricsProbe].

import androidx.compose.ui.text.font.FontFamily
import com.styleconverter.runtime.typography.font.DocumentFontTypefaces

object ChUnitMetrics {

    // Measured advances keyed by (family cache name, font size, axis).
    // StyleApplier calls measure() once per applyConfig pass; a component
    // tree re-renders often, so we memoize. The axis is part of the key
    // because the same face at the same size has TWO ch advances (M-B).
    private val cache = HashMap<Triple<String, Float, Boolean>, Float?>()

    /** M-A: how the face-less DEFAULT measurement obtains the Inter Typeface
     *  the label paints. Installed by [bindInterFace] (the renderer owns a
     *  Context — `Resources.getFont(R.font.inter_regular)`); null (before
     *  any render, and every JVM test that does not install one) keeps
     *  the historical platform-DEFAULT measurement. `internal` so the unit
     *  suite can install a stub and restore it in tearDown. */
    @Volatile
    internal var defaultTypefaceLoader: (() -> android.graphics.Typeface?)? = null
    // The loader's answer, memoized once per install (a resource load is I/O).
    private var defaultTypefaceResolved = false
    private var defaultTypeface: android.graphics.Typeface? = null
    // One-shot latch: the DEFAULT-face breadcrumb is said once per process.
    private val defaultFaceLogged = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Install (or, with null, remove) the M-A default-face loader. Clears
     *  the memo: the Inter-painted key changes with the loader ("default" ↔
     *  "inter") and every cached Inter-painted advance with it. */
    fun installDefaultTypeface(loader: (() -> android.graphics.Typeface?)?) {
        synchronized(cache) {
            defaultTypefaceLoader = loader
            defaultTypefaceResolved = false
            defaultTypeface = null
            boundContext = null
            cache.clear()
        }
    }

    // The application Context [bindInterFace] last installed a loader for —
    // the identity check that makes the per-composition call free.
    @Volatile
    private var boundContext: android.content.Context? = null

    /** M-A, the runtime's own install: the renderer hands over its Context
     *  (`LocalContext.current` in ComponentRenderer.RenderComponent — the
     *  lane's seam patch) and this installs a loader for the BUNDLED Inter
     *  Regular the face-less label paints (`InterFontFamily`'s
     *  `R.font.inter_regular`). Idempotent per application Context, so the
     *  renderer may call it on every composition: only the first call per
     *  process (or a new Context) installs, the rest return at the check.
     *  The load itself stays lazy — it runs on the first face-less ch
     *  measurement, never on a composition that measures nothing. */
    fun bindInterFace(context: android.content.Context) {
        // The application Context outlives every Activity: holding it in
        // this process-wide object cannot leak a window.
        val app = context.applicationContext ?: context
        // Same Context as last time → the loader is already installed.
        if (boundContext === app) return
        installDefaultTypeface {
            // Resources.getFont is API 26 (Android O); below it the loader
            // answers null and defaultMeasuringTypeface logs the DEFAULT
            // fallback once — never a crash on minSdk 24.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                app.resources.getFont(com.styleconverter.runtime.R.font.inter_regular)
            else null
        }
        // Recorded AFTER the install, which clears it (a manual install
        // must forget the bound Context so a later bind re-installs).
        boundContext = app
    }

    /** M-B: the (ascent, descent) probe in px, both positive, for a Typeface
     *  at a size — Paint.FontMetrics on device (ascent is negative there, so
     *  it is negated). Injectable for the JVM, exactly like
     *  DocumentFontTypefaces.typefaceLoader: without it no unit test could
     *  pin the round+round arithmetic. */
    internal var verticalMetricsProbe: (android.graphics.Typeface?, Float) -> Pair<Float, Float>? =
        { typeface, sizePx ->
            val fm = android.graphics.Paint().apply { this.typeface = typeface; textSize = sizePx }.fontMetrics
            (-fm.ascent) to fm.descent
        }

    /**
     * The advance of '0' in px for [fontFamily] at [fontSizePx] along the
     * element's inline axis — the x-advance, or with [inlineAxisUpright]
     * the vertical advance (M-B) — or null when platform metrics are
     * unavailable (pure-JVM tests, or a defensive failure inside the
     * graphics stack). Callers must treat null as "use the 0.5em spec
     * fallback" — never as zero. The flag defaults to false so every
     * pre-wave-52 two-argument caller is byte-identical.
     */
    fun measure(fontFamily: FontFamily?, fontSizePx: Float, inlineAxisUpright: Boolean = false): Float? {
        // Cache key: see [cacheName] — generic singletons by name, document
        // families by generation-stamped file, Inter-painted families
        // (null / InterFontFamily) by installed loader, the rest "default".
        val key = Triple(cacheName(fontFamily), fontSizePx, inlineAxisUpright)
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] }
        val advance = measureUncached(fontFamily, fontSizePx, inlineAxisUpright)
        synchronized(cache) { cache[key] = advance }
        return advance
    }

    /** Stable name for the cache key — see [measure]. Internal (not private)
     *  so the unit suite can pin the keying without a device. */
    internal fun cacheName(fontFamily: FontFamily?): String = when (fontFamily) {
        // The five CSS generic families Compose models as singletons.
        FontFamily.Monospace -> "monospace"
        FontFamily.Serif -> "serif"
        FontFamily.SansSerif -> "sans-serif"
        FontFamily.Cursive -> "cursive"
        else -> {
            // wave-47 lane Z4 (SEAM 1): a registered document family measures
            // ITS OWN face, keyed by file path + registry generation so
            // document N's advance can never answer for document N+1's.
            val face = DocumentFontTypefaces.measuringFace(fontFamily)
            if (face != null) "doc:${DocumentFontTypefaces.generation}:${face.file.path}"
            // M-A: a family the label paints as Inter (null / InterFontFamily)
            // measures the INSTALLED Inter face when the renderer installed
            // one — a different face from "default", so a different key.
            else if (paintsInter(fontFamily) && defaultTypefaceLoader != null) "inter"
            // FontFamily.Default, any other family, or no loader: the
            // platform-DEFAULT measurement — the face those labels paint.
            else "default"
        }
    }

    /** M-A: does the label paint this family as the bundled Inter? True for
     *  null (ComponentRenderer's `textStyle.fontFamily ?: InterFontFamily`
     *  bottom-out — no font-family declared) and for InterFontFamily itself
     *  (CssFontFamilyResolver's answer for a stack naming `Inter`). False for
     *  `FontFamily.Default`: css-fonts-4 §5.2's UA default for a list of
     *  unavailable names, which Compose paints as the platform face, never
     *  Inter. Structural `==` so an equal font list counts as Inter too.
     *  Internal so the unit suite pins the split without a device. */
    internal fun paintsInter(fontFamily: FontFamily?): Boolean =
        fontFamily == null || fontFamily == com.styleconverter.runtime.typography.InterFontFamily

    /** Map the Compose family onto the android.graphics Typeface the text
     *  engine resolves it to (TextStyleApplier maps CSS "monospace" →
     *  FontFamily.Monospace etc.; Typeface mirrors that). */
    private fun resolveTypeface(fontFamily: FontFamily?): android.graphics.Typeface? = when (fontFamily) {
        FontFamily.Monospace -> android.graphics.Typeface.MONOSPACE
        FontFamily.Serif -> android.graphics.Typeface.SERIF
        FontFamily.SansSerif -> android.graphics.Typeface.SANS_SERIF
        // "cursive" has no Typeface constant; create() resolves the system
        // family of that name (falls back to default safely).
        FontFamily.Cursive -> android.graphics.Typeface.create("cursive", android.graphics.Typeface.NORMAL)
        // wave-47 lane Z4 (SEAM 1): a document @font-face family measures
        // THE REGISTERED face — the same file the label paints; null (not a
        // document family) falls through to the face the label paints:
        // Inter (M-A loader) for null / InterFontFamily, the platform
        // DEFAULT for FontFamily.Default and any other family — the
        // pre-wave-52 measurement, which matches what those labels paint.
        else -> DocumentFontTypefaces.typefaceFor(fontFamily)
            ?: if (paintsInter(fontFamily)) defaultMeasuringTypeface() else android.graphics.Typeface.DEFAULT
    }

    /** M-A: the face an Inter-painted element (null / InterFontFamily)
     *  measures against — the installed Inter loader's Typeface, else the
     *  platform DEFAULT with a one-shot breadcrumb (no silent fallthrough:
     *  the label paints Inter, so a DEFAULT measurement is a known
     *  55-vs-63 px gap, not a routine case). */
    private fun defaultMeasuringTypeface(): android.graphics.Typeface? {
        val loader = defaultTypefaceLoader
        if (loader != null) {
            // Memo hit (a cached null too): one resource load per install.
            synchronized(cache) { if (defaultTypefaceResolved) return defaultTypeface ?: platformDefault("Inter loader answered null") }
            // Load OUTSIDE the lock: a resource load is real I/O.
            val loaded = runCatching { loader() }.getOrNull()
            synchronized(cache) { defaultTypeface = loaded; defaultTypefaceResolved = true }
            return loaded ?: platformDefault("Inter loader answered null")
        }
        return platformDefault("no Inter loader installed")
    }

    /** The platform DEFAULT face, with the M-A breadcrumb said once. */
    private fun platformDefault(reason: String): android.graphics.Typeface? {
        // runCatching: android.util.Log is an unmocked stub on the JVM.
        if (defaultFaceLogged.compareAndSet(false, true)) runCatching {
            android.util.Log.i(
                "ChUnitMetrics",
                "ch:default-face — $reason; measuring the platform DEFAULT face, which the label " +
                    "does not paint (it paints Inter): face-less ch boxes may read 55/63 of the ref",
            )
        }
        return android.graphics.Typeface.DEFAULT
    }

    /** The actual platform measurement — isolated so measure() can memoize. */
    private fun measureUncached(fontFamily: FontFamily?, fontSizePx: Float, inlineAxisUpright: Boolean): Float? = try {
        // Face resolution isolated: on the JVM the android.graphics stub
        // throws or answers null here, and the upright probe below must
        // still run (with a null Typeface) so the pure suite can pin it.
        val typeface = runCatching { resolveTypeface(fontFamily) }.getOrNull()
        if (inlineAxisUpright) verticalAdvance(typeface, fontSizePx) else horizontalAdvance(typeface, fontSizePx)
    } catch (_: Throwable) {
        // Pure-JVM tests (unmocked android.graphics) land here → null →
        // spec fallback. Never crash a render path over a metric.
        null
    }

    /** The x-advance of '0': Paint.measureText returns the run's advance —
     *  exactly css-values-4's "advance measure" — in px because textSize is
     *  set in px (the runtime's px==dp space). */
    private fun horizontalAdvance(typeface: android.graphics.Typeface?, sizePx: Float): Float? {
        val paint = android.graphics.Paint().apply { this.typeface = typeface; textSize = sizePx }
        // Non-positive/NaN results mean the stub or a broken font — treat
        // as unavailable so the caller uses the 0.5em spec fallback.
        return paint.measureText("0").takeIf { it.isFinite() && it > 0f }
    }

    /** M-B: the vertical advance of an upright '0' = round(ascent) +
     *  round(descent) — the glyph's em box as Chromium sizes an upright
     *  line (19 + 5 = 24 at 20 px Inter, whose hhea 1984/494 over 2048 give
     *  19.375 / 4.82). Each half rounds separately, as the ref's 24 (not
     *  24.2) shows. Internal so the JVM pin can call it with the stub probe. */
    internal fun verticalAdvance(typeface: android.graphics.Typeface?, sizePx: Float): Float? {
        val (ascent, descent) = verticalMetricsProbe(typeface, sizePx) ?: return null
        // A broken probe (NaN/∞) is "unavailable", never a zero box.
        if (!ascent.isFinite() || !descent.isFinite()) return null
        return (Math.round(ascent) + Math.round(descent)).toFloat().takeIf { it > 0f }
    }
}

package com.styleconverter.runtime.spacing

// Wave 52 lane L8 (vertical-wedges) — JVM pins for the two ChUnitMetrics
// changes: M-A (WHICH face a face-less element measures ch against — the
// installed Inter loader, else the platform default) and M-B (WHICH axis —
// the vertical advance under `vertical-*` + `upright`). No Android graphics
// on the JVM, so the face branch is pinned through the memo KEY and the
// axis arithmetic through the injectable `verticalMetricsProbe`, exactly the
// seam pattern DocumentFontTypefacesTest uses for `typefaceLoader`.
//
// MUTATIONS EXECUTED (2026-09-25; RE-EXECUTED 2026-10-05 in the isolated HEAD
// export — tools/titan/results/wave52-vertical-wedges/_mutations-compose.log
// C1/C2 — each restored byte-exact, sha-256 verified):
//  • `cacheName`: the `defaultTypefaceLoader != null → "inter"` arm removed
//    → `a face-less family keys on the installed Inter loader` fails
//    ("default" ≠ "inter").
//  • `measureUncached`: the `inlineAxisUpright` branch routed to
//    `horizontalAdvance` → `upright ch is round(ascent) + round(descent)`
//    fails (null ≠ 24 — the JVM has no Paint).
//
// FIX PASS (skeptic must-fix, 2026-10-05): the Inter loader served EVERY
// non-singleton, non-document family, FontFamily.Default included — but a
// Default label paints Roboto, so `font-family: Arial; width: 10ch` sized
// 126 px around a ~111 px run. Pinned by `a FontFamily-Default label keeps
// the face it paints` on the VERBATIM wave51-fix payloads. MUTATIONS
// EXECUTED in an isolated HEAD export (_mutations-fix.log C8/C9/C11/C1b,
// each restored byte-exact, sha-256 verified):
//  • C8 `cacheName`: `paintsInter(fontFamily) && ` dropped (the pre-fix
//    gate) → fails (expected "default", was "inter" for Arial).
//  • C9 `resolveTypeface`: the `paintsInter` routing dropped (Default
//    measured through the loader) → fails (loader calls expected 0, was 1).
//  • C11 `paintsInter`: the InterFontFamily clause dropped → fails
//    (expected "inter", was "default" for the harness stack).
//  • C1b `cacheName`: the whole `"inter"` arm dropped → both face tests fail.

import androidx.compose.ui.text.font.FontFamily
import com.styleconverter.runtime.typography.CssFontFamilyResolver
import com.styleconverter.runtime.typography.InterFontFamily
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class ChUnitMetricsTest {

    // The production probe (Paint.FontMetrics — throws on the JVM), restored
    // after every test so the suite order can never leak a stub.
    private val productionProbe = ChUnitMetrics.verticalMetricsProbe
    // Same for the density probe (null on the JVM — no bound Context).
    private val productionDensity = ChUnitMetrics.devicePixelsPerPx

    @Before
    fun reset() {
        // No loader + a cleared memo: every test starts from the product state.
        ChUnitMetrics.installDefaultTypeface(null)
    }

    @After
    fun restore() {
        ChUnitMetrics.verticalMetricsProbe = productionProbe
        ChUnitMetrics.devicePixelsPerPx = productionDensity
        ChUnitMetrics.installDefaultTypeface(null)
    }

    @Test
    fun `a face-less family keys on the installed Inter loader`() {
        // Product state: no loader → the historical platform-DEFAULT key.
        assertEquals("default", ChUnitMetrics.cacheName(null))
        // The harness installs the Inter loader → a DIFFERENT face, so a
        // different key: a Roboto advance can never answer for Inter via memo.
        ChUnitMetrics.installDefaultTypeface { null }
        assertEquals("inter", ChUnitMetrics.cacheName(null))
        // Generic singletons are unaffected by the loader.
        assertEquals("monospace", ChUnitMetrics.cacheName(FontFamily.Monospace))
        assertEquals("serif", ChUnitMetrics.cacheName(FontFamily.Serif))
        // Removing the loader restores the product key.
        ChUnitMetrics.installDefaultTypeface(null)
        assertEquals("default", ChUnitMetrics.cacheName(null))
    }

    @Test
    fun `a FontFamily-Default label keeps the face it paints`() {
        // VERBATIM wave51-fix FontFamily payloads, resolved by the runtime's
        // one resolver exactly as the label's TextStyle is:
        //  • css-writing-modes/bidi-plaintext-br-001 — ["Arial"] (unavailable
        //    on Android → css-fonts-4 §5.2 UA default = FontFamily.Default)
        //  • css-counter-styles/…/disclosure-styles — ["system-ui"] (Default)
        //  • the harness stack (96 corpus declarations) — InterFontFamily
        val arial = CssFontFamilyResolver.resolve(Json.parseToJsonElement("""["Arial"]"""))
        val systemUi = CssFontFamilyResolver.resolve(Json.parseToJsonElement("""["system-ui"]"""))
        val harness = CssFontFamilyResolver.resolve(Json.parseToJsonElement(
            """["Inter","-apple-system","system-ui","Segoe UI","Roboto","Oxygen","Ubuntu","sans-serif"]"""))
        // Guard the premise: these ARE the families the label paints.
        assertSame(FontFamily.Default, arial)
        assertSame(FontFamily.Default, systemUi)
        assertSame(InterFontFamily, harness)
        // A counting loader: the face-resolution ROUTE is observable on the
        // JVM even though no Typeface can be built here.
        var loaderCalls = 0
        ChUnitMetrics.installDefaultTypeface { loaderCalls++; null }
        // Default paints Roboto (`textStyle.fontFamily ?: InterFontFamily`
        // keeps a non-null Default) → measures the platform DEFAULT, keyed so.
        assertEquals("default", ChUnitMetrics.cacheName(arial))
        assertEquals("default", ChUnitMetrics.cacheName(systemUi))
        // null (face-less) and InterFontFamily paint Inter → the loader's key.
        assertEquals("inter", ChUnitMetrics.cacheName(null))
        assertEquals("inter", ChUnitMetrics.cacheName(harness))
        // The measurement route agrees with the key: a Default measurement
        // never consults the Inter loader…
        ChUnitMetrics.verticalMetricsProbe = { _, sizePx -> (1984f / 2048f * sizePx) to (494f / 2048f * sizePx) }
        ChUnitMetrics.measure(arial, 20f, inlineAxisUpright = true)
        assertEquals(0, loaderCalls)
        // …while a face-less one does (once — memoised per install).
        ChUnitMetrics.measure(null, 20f, inlineAxisUpright = true)
        assertEquals(1, loaderCalls)
    }

    @Test
    fun `upright ch is round(ascent) + round(descent) - 24 at 20px Inter metrics`() {
        // Inter's hhea ascender/descender (1984 / 494 over 2048) at the size:
        // 19.375 / 4.82 at 20 px — Chromium rounds each half: 19 + 5 = 24.
        ChUnitMetrics.verticalMetricsProbe = { _, sizePx -> (1984f / 2048f * sizePx) to (494f / 2048f * sizePx) }
        assertEquals(24f, ChUnitMetrics.measure(null, 20f, inlineAxisUpright = true))
        // …so `width: 5ch` on the upright orange div is the ref's 120 px.
        assertEquals(120f, 5f * ChUnitMetrics.measure(null, 20f, inlineAxisUpright = true)!!)
        // The horizontal axis needs Paint.measureText, which the JVM has not:
        // null (the css-values-4 §6.1.1 0.5em fallback), never zero — and
        // never the upright memo (the axis is part of the key).
        assertNull(ChUnitMetrics.measure(null, 20f, inlineAxisUpright = false))
        assertNull(ChUnitMetrics.measure(null, 20f))
    }

    @Test
    fun `the memo separates the two axes and follows the loader`() {
        ChUnitMetrics.verticalMetricsProbe = { _, _ -> 19.375f to 4.82f }
        assertEquals(24f, ChUnitMetrics.measure(null, 20f, inlineAxisUpright = true))
        // A probe that now answers null must NOT be consulted: memo hit.
        ChUnitMetrics.verticalMetricsProbe = { _, _ -> null }
        assertEquals(24f, ChUnitMetrics.measure(null, 20f, inlineAxisUpright = true))
        // Installing a loader changes the face-less key and clears the memo,
        // so the next measurement re-runs the (now null) probe.
        ChUnitMetrics.installDefaultTypeface { null }
        assertNull(ChUnitMetrics.measure(null, 20f, inlineAxisUpright = true))
    }

    @Test
    fun `a broken probe is unavailable, never a zero box`() {
        // NaN metrics → null (spec fallback), not NaN and not 0.
        ChUnitMetrics.verticalMetricsProbe = { _, _ -> Float.NaN to 4f }
        assertNull(ChUnitMetrics.measure(null, 16f, inlineAxisUpright = true))
        // Zero metrics (a font with no ascent/descent) → null too.
        ChUnitMetrics.installDefaultTypeface { null } // new key → fresh measurement
        ChUnitMetrics.verticalMetricsProbe = { _, _ -> 0f to 0f }
        assertNull(ChUnitMetrics.measure(null, 16f, inlineAxisUpright = true))
        // The arithmetic itself, on the internal helper: 0.4 + 0.6 rounds
        // each half separately (0 + 1 = 1), not the sum (1.0).
        ChUnitMetrics.verticalMetricsProbe = { _, _ -> 10.4f to 10.6f }
        assertEquals(21f, ChUnitMetrics.verticalAdvance(null, 16f))
    }

    // ── wave-52 closing gate: ch is the FONT's advance, not a rasteriser's ──
    // The probe itself needs android.graphics (device-only), so the JVM pins
    // what it can see: the flag value, and — by source — that the production
    // probe measures under those flags through getRunAdvance. The device
    // evidence is the gate: css-text/hyphens/hyphens-manual-011 android,
    // `width: 10ch` box 194 px wide → the ref's 197.
    // MUTATIONS EXECUTED (2026-10-05, each restored): `Paint(ADVANCE_PAINT_FLAGS)`
    // → `Paint()` and `getRunAdvance("0", 0, 1, 0, 1, false, 1)` →
    // `measureText("0")` each turn the source pin red; dropping
    // LINEAR_TEXT_FLAG from the constant turns the flag pin red.

    @Test
    fun `the advance probe asks for linear sub-pixel metrics`() {
        // 0x40 | 0x80 — android.graphics.Paint.LINEAR_TEXT_FLAG and
        // SUBPIXEL_TEXT_FLAG (compile-time constants, so readable on the JVM).
        assertEquals(
            android.graphics.Paint.LINEAR_TEXT_FLAG or android.graphics.Paint.SUBPIXEL_TEXT_FLAG,
            ChUnitMetrics.ADVANCE_PAINT_FLAGS)
        assertEquals(0xC0, ChUnitMetrics.ADVANCE_PAINT_FLAGS)
    }

    @Test
    fun `the production probe measures the font advance - no hinting, no ceil`() {
        // Walk up to the repo root (the test's working dir is the module).
        var dir: java.io.File? = java.io.File(System.getProperty("user.dir") ?: ".").absoluteFile
        val rel = "runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/ChUnitMetrics.kt"
        while (dir != null && !java.io.File(dir, rel).exists()) dir = dir.parentFile
        val source = java.io.File(requireNotNull(dir) { "repo root not found" }, rel).readText()
        // The body of horizontalAdvance: from its signature to the next KDoc.
        val body = source.substringAfter("private fun horizontalAdvance(").substringBefore("/**")
        val code = body.lines().filterNot { it.trim().startsWith("//") }.joinToString("\n")
        // A plain Paint() hints the advance to a whole pixel…
        assertEquals(true, code.contains("android.graphics.Paint(ADVANCE_PAINT_FLAGS)"))
        // …and measureText ceils the run; getRunAdvance returns it as measured.
        assertEquals(true, code.contains("getRunAdvance(\"0\", 0, 1, 0, 1, false, 1)"))
        assertEquals(false, code.contains("measureText("))
    }

    // ── wave-52 closing gate: an N-ch box holds its N glyphs (fitSafePx) ──
    // The gate's density is 2.625 (420 dpi). MUTATIONS EXECUTED (2026-10-05,
    // closing-fixes-mutations.py F1–F4, each restored): fitSafePx → identity
    // or "strictly above" (an exact tie grows a pixel), and either `ch` arm
    // of SpacingResolve stripped of the snap, each turn the tests below red.

    @Test
    fun `a ch length is rounded up to a whole device pixel and an exact one stays`() {
        ChUnitMetrics.devicePixelsPerPx = { 2.625f }
        // css-overflow/line-clamp/block-ellipsis-023: `width: 32ch`, 13 px
        // monospace, '0' = 7.8 px → 249.6 px = 655.2 device px. Compose
        // rounded that box to 655 under a 655.2-px line; rounded UP it is 656.
        assertEquals(656f, ChUnitMetrics.fitSafePx(32f * 7.8f) * 2.625f, 1e-3f)
        // A length already on a device pixel does not move — the upright
        // `5ch` = 120 px = 315-device-px squares of ch-units-vrl-003/-004
        // (316 cost both cells their pass on wave52-probe2)…
        assertEquals(120f, ChUnitMetrics.fitSafePx(120f))
        // …and hyphens-manual-011's `10ch` at 32 px (19.2) = 192 px = 504.
        assertEquals(504f, ChUnitMetrics.fitSafePx(192f) * 2.625f, 1e-3f)
        // Float noise just above a whole pixel is that pixel, not the next one.
        assertEquals(504f, ChUnitMetrics.fitSafePx(504.0003f / 2.625f) * 2.625f, 1e-3f)
        // Always up, never by a whole device pixel.
        for (px in listOf(1f, 63f, 120.4f, 249.6f, 631f)) {
            val grown = (ChUnitMetrics.fitSafePx(px) - px) * 2.625f
            assertEquals("$px grew by $grown device px", true, grown > -1e-3f && grown < 1f)
        }
    }

    @Test
    fun `the snap leaves non-boxes and density-less callers alone`() {
        // No density (the JVM default, a caller outside the renderer): identity.
        assertEquals(249.6f, ChUnitMetrics.fitSafePx(249.6f))
        ChUnitMetrics.devicePixelsPerPx = { 2.625f }
        // A negative ch margin, a zero and a non-finite length do not move.
        assertEquals(-12.5f, ChUnitMetrics.fitSafePx(-12.5f))
        assertEquals(0f, ChUnitMetrics.fitSafePx(0f))
        assertEquals(true, ChUnitMetrics.fitSafePx(Float.NaN).isNaN())
        // A broken density is no density.
        ChUnitMetrics.devicePixelsPerPx = { 0f }
        assertEquals(249.6f, ChUnitMetrics.fitSafePx(249.6f))
        ChUnitMetrics.devicePixelsPerPx = { throw IllegalStateException("no display") }
        assertEquals(249.6f, ChUnitMetrics.fitSafePx(249.6f))
    }

    @Test
    fun `both ch resolution paths go through the snap`() {
        ChUnitMetrics.devicePixelsPerPx = { 2.625f }
        val ctx = SpacingContext(fontSizePx = 13f, chAdvancePx = 7.8f)
        // The bare unit — the exact wire shape of block-ellipsis-023's width.
        val bare = resolveToDp(
            com.styleconverter.runtime.core.types.LengthValue.Relative(
                32.0, com.styleconverter.runtime.core.types.LengthUnit.CH, null), ctx)
        assertEquals(656f, bare.value * 2.625f, 1e-3f)
        // …and the calc() mirror: the ch term is snapped, then 4 px are added.
        assertEquals(656f / 2.625f + 4f, evalCalc("calc(32ch + 4px)", ctx), 1e-3f)
        // The spec's 0.5em fallback (no measured advance) is a box too.
        val fallback = resolveToDp(
            com.styleconverter.runtime.core.types.LengthValue.Relative(
                10.0, com.styleconverter.runtime.core.types.LengthUnit.CH, null),
            SpacingContext(fontSizePx = 16.2f))
        assertEquals(213f, fallback.value * 2.625f, 1e-3f)   // 81 px = 212.6 → 213
    }
}

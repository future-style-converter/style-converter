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

    @Before
    fun reset() {
        // No loader + a cleared memo: every test starts from the product state.
        ChUnitMetrics.installDefaultTypeface(null)
    }

    @After
    fun restore() {
        ChUnitMetrics.verticalMetricsProbe = productionProbe
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
}

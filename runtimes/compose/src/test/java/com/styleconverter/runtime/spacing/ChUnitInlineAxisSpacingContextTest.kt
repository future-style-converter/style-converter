package com.styleconverter.runtime.spacing

// Wave 52 lane L8 (vertical-wedges, M-B) — the CALL-SITE pin: does
// StyleApplier.buildSpacingContext hand ChUnitMetrics the inline-axis flag?
//
// Ships in the SAME patch as the buildSpacingContext hunk
// (tools/titan/results/wave52-vertical-wedges/hunk-for-L4-1.patch, PLAN.md §9
// C4): it is red without the hunk by construction, so the two must land as
// one commit. ChUnitMetricsTest pins the measurement; this file pins that the
// production spacing context actually asks for the vertical advance.
//
// Verbatim IR: tools/titan/runs/wave51-fix/sections/css-writing-modes/
// per-test-ir/wpt__css-writing-modes__ch-units-vrl-005.json — the orange div
// `…__4-479` (VERTICAL_RL + UPRIGHT, `width: 5ch`, ref 120 wide) and the
// blue div `…__3-478` (horizontal inline-block, `height: 5ch`, ref 63 tall).
//
// MUTATION EXECUTED (2026-10-05, isolated HEAD export, restored byte-exact,
// sha-256 verified): the hunk's `inlineAxisUpright = …` argument removed →
// `the upright orange div measures the vertical advance` fails (null ≠ 24).

import com.styleconverter.runtime.StyleApplier
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ChUnitInlineAxisSpacingContextTest {

    // The production probe (Paint.FontMetrics — throws on the JVM), restored
    // after every test so a stub can never leak into another suite.
    private val productionProbe = ChUnitMetrics.verticalMetricsProbe

    /** One wire pair, parsed exactly as the IR reader hands it over. */
    private fun pair(type: String, json: String) = type to Json.parseToJsonElement(json)

    /** `wpt__css-writing-modes__ch-units-vrl-005__4-479`, verbatim. */
    private val orange = listOf(
        pair("FontSize", """{"px":20,"original":{"type":"length","px":20}}"""),
        pair("Color", """{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":"transparent"}"""),
        pair("BackgroundColor", """{"srgb":{"r":1,"g":0.6470588235294118,"b":0},"original":"orange"}"""),
        pair("WritingMode", "\"VERTICAL_RL\""),
        pair("TextOrientation", "\"UPRIGHT\""),
        pair("Width", """{"type":"length","original":{"v":5,"u":"CH"}}"""),
    )

    /** `wpt__css-writing-modes__ch-units-vrl-005__3-478`, verbatim. */
    private val blue = listOf(
        pair("FontSize", """{"px":20,"original":{"type":"length","px":20}}"""),
        pair("Color", """{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":"transparent"}"""),
        pair("BackgroundColor", """{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}"""),
        pair("Height", """{"type":"length","original":{"v":5,"u":"CH"}}"""),
        pair("Display", "\"INLINE_BLOCK\""),
    )

    @Before
    fun stubMetrics() {
        // A clean memo, and Inter's hhea halves at 20 px (19.375 / 4.82):
        // Chromium's round + round = 24, the upright '0' advance.
        ChUnitMetrics.installDefaultTypeface(null)
        ChUnitMetrics.verticalMetricsProbe = { _, sizePx -> (1984f / 2048f * sizePx) to (494f / 2048f * sizePx) }
    }

    @After
    fun restore() {
        ChUnitMetrics.verticalMetricsProbe = productionProbe
        ChUnitMetrics.installDefaultTypeface(null)
    }

    @Test
    fun `the upright orange div measures the vertical advance`() {
        val ctx = StyleApplier.buildSpacingContext(StyleApplier.extractConfig(orange))
        // 24 px per ch → `width: 5ch` = 120, the ref's orange square.
        assertEquals(24f, ctx.chAdvancePx!!, 0f)
    }

    @Test
    fun `the horizontal blue div keeps the x-advance`() {
        val ctx = StyleApplier.buildSpacingContext(StyleApplier.extractConfig(blue))
        // The x-advance needs Paint.measureText, absent on the JVM → null
        // (the 0.5em spec fallback) — and crucially NOT the stubbed 24.
        assertNull(ctx.chAdvancePx)
    }
}

package com.styleconverter.runtime.widgets

// Wave-22 lane INK, pin A-RC2 — the UA control-font metric pin. This is
// the test that makes the Arial-metric claim FALSIFIABLE: every number
// below is a width read off the css-ui appearance-auto-001 browser ref
// (tools/wpt/refs/<hash>/white-black-ink-font-lh/css-ui/), the same
// oracle the WEB capture reproduces at SSIM 1.000. The Swift twin
// (UAControlFontMetricsTests.swift) asserts the IDENTICAL numbers, so a
// table edit on one native fails the other native's suite.

import org.junit.Assert.assertEquals
import org.junit.Test

class UAControlFontMetricsTest {

    /** Chromium's UA control size — the size every assert below uses. */
    private val px = UAControlFontMetrics.CONTROL_FONT_PX

    @Test
    fun `control label advances match the ref ink bands`() {
        // "input-text" is the sharpest probe on the page: the ref's text
        // field paints its value from x91 to x145 inside a box at x87,
        // i.e. 55px of ink for a 54.84px advance run. (Inter, the face
        // wave 20 measured with, would have needed 61.43px — the whole
        // reason this table exists.)
        assertEquals(54.844f, UAControlFontMetrics.advance("input-text", px), 0.01f)
        // "button" drives the row-1 button box: 37.07 + the 16px chrome
        // band = 53.07, which snaps to the ref's border columns 29/82.
        assertEquals(37.070f, UAControlFontMetrics.advance("button", px), 0.01f)
        // The row-2 chain: each of these + 16 must reproduce the ref's
        // painted boxes (204..289, 294..382, 388..).
        assertEquals(70.423f, UAControlFontMetrics.advance("input-button", px), 0.01f)
        assertEquals(72.624f, UAControlFontMetrics.advance("input-submit", px), 0.01f)
        assertEquals(62.995f, UAControlFontMetrics.advance("input-reset", px), 0.01f)
        // The menulist label: ref ink x263..297 inside the box at x258.
        assertEquals(34.831f, UAControlFontMetrics.advance("select", px), 0.01f)
        // The listbox option — off-canvas in the 390px ref, pinned here
        // so the two natives cannot disagree about the listbox width.
        assertEquals(85.215f, UAControlFontMetrics.advance("select-multiple", px), 0.01f)
        // The file-input strings (appearance-auto-input-non-widget-001).
        assertEquals(71.146f, UAControlFontMetrics.advance("Choose File", px), 0.01f)
        assertEquals(84.492f, UAControlFontMetrics.advance("No file chosen", px), 0.01f)
    }

    @Test
    fun `monospace advance matches the ref textarea content`() {
        // The ref's textarea content "textarea" (8 chars) inks x19..81
        // against a text origin at box+3 = x19 — 8 x 8.0px advances.
        assertEquals(64.0f, UAControlFontMetrics.monoAdvance("textarea", px), 0.01f)
        // 0.6em is the pin; at the control size that is exactly 8px.
        assertEquals(8.0f, UAControlFontMetrics.MONO_ADVANCE_EM * px, 0.01f)
    }

    @Test
    fun `per-character lookup covers the ASCII band and falls back loudly`() {
        // Space is the narrow end of the band (569/2048 em) and 'W' the
        // wide end (1933/2048) — both straight out of Arial's hmtx.
        assertEquals(569f * px / 2048f, UAControlFontMetrics.charAdvance(' ', px), 1e-4f)
        assertEquals(1933f * px / 2048f, UAControlFontMetrics.charAdvance('W', px), 1e-4f)
        // Below and above the pinned band we take the documented
        // 'n'-width fallback (1139/2048) rather than a silent zero: a
        // zero-width char would collapse a control's box without a trace.
        val fallback = 1139f * px / 2048f
        assertEquals(fallback, UAControlFontMetrics.charAdvance('\u0001', px), 1e-4f)
        assertEquals(fallback, UAControlFontMetrics.charAdvance('é', px), 1e-4f)
        // The empty label is genuinely zero wide (a valueless input).
        assertEquals(0f, UAControlFontMetrics.advance("", px), 1e-6f)
    }

    @Test
    fun `the control size is Chromium's small-control 13 and a third`() {
        // html.css resolves `font: -webkit-small-control` to 16 x 5/6.
        assertEquals(13.3333f, px, 1e-4f)
        // The geometry pin table reads its FONT from here, so the size
        // the plan measures with and draws with cannot diverge.
        assertEquals(px, UAWidgetsGeometry.FONT, 1e-6f)
    }
}

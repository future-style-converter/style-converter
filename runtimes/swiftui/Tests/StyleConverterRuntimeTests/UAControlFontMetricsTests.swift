//
//  UAControlFontMetricsTests.swift
//  Wave-22 lane INK, pin A-RC2 — the UA control-font metric pin. This is
//  the test that makes the Arial-metric claim FALSIFIABLE: every number
//  below is a width read off the css-ui appearance-auto-001 browser ref
//  (tools/wpt/refs/<hash>/white-black-ink-font-lh/css-ui/), the same
//  oracle the WEB capture reproduces at SSIM 1.000. The Kotlin twin
//  (UAControlFontMetricsTest.kt) asserts the IDENTICAL numbers, so a
//  table edit on one native fails the other native's suite.
//

import XCTest
@testable import StyleConverterRuntime

final class UAControlFontMetricsTests: XCTestCase {

    /// Chromium's UA control size — the size every assert below uses.
    private let px = UAControlFontMetrics.CONTROL_FONT_PX

    func testControlLabelAdvancesMatchTheRefInkBands() {
        // "input-text" is the sharpest probe on the page: the ref's text
        // field paints its value from x91 to x145 inside a box at x87,
        // i.e. 55px of ink for a 54.84px advance run. (Inter, the face
        // wave 20 measured with, would have needed 61.43px — the whole
        // reason this table exists.)
        XCTAssertEqual(UAControlFontMetrics.advance("input-text", px), 54.844, accuracy: 0.01)
        // "button" drives the row-1 button box: 37.07 + the 16px chrome
        // band = 53.07, which snaps to the ref's border columns 29/82.
        XCTAssertEqual(UAControlFontMetrics.advance("button", px), 37.070, accuracy: 0.01)
        // The row-2 chain: each of these + 16 must reproduce the ref's
        // painted boxes (204..289, 294..382, 388..).
        XCTAssertEqual(UAControlFontMetrics.advance("input-button", px), 70.423, accuracy: 0.01)
        XCTAssertEqual(UAControlFontMetrics.advance("input-submit", px), 72.624, accuracy: 0.01)
        XCTAssertEqual(UAControlFontMetrics.advance("input-reset", px), 62.995, accuracy: 0.01)
        // The menulist label: ref ink x263..297 inside the box at x258.
        XCTAssertEqual(UAControlFontMetrics.advance("select", px), 34.831, accuracy: 0.01)
        // The listbox option — off-canvas in the 390px ref, pinned here
        // so the two natives cannot disagree about the listbox width.
        XCTAssertEqual(UAControlFontMetrics.advance("select-multiple", px), 85.215, accuracy: 0.01)
        // The file-input strings (appearance-auto-input-non-widget-001).
        XCTAssertEqual(UAControlFontMetrics.advance("Choose File", px), 71.146, accuracy: 0.01)
        XCTAssertEqual(UAControlFontMetrics.advance("No file chosen", px), 84.492, accuracy: 0.01)
    }

    func testMonospaceAdvanceMatchesTheRefTextareaContent() {
        // The ref's textarea content "textarea" (8 chars) inks x19..81
        // against a text origin at box+3 = x19 — 8 x 8.0px advances.
        XCTAssertEqual(UAControlFontMetrics.monoAdvance("textarea", px), 64.0, accuracy: 0.01)
        // 0.6em is the pin; at the control size that is exactly 8px.
        XCTAssertEqual(UAControlFontMetrics.MONO_ADVANCE_EM * px, 8.0, accuracy: 0.01)
    }

    func testPerCharacterLookupCoversASCIIAndFallsBackLoudly() {
        // Space is the narrow end of the band (569/2048 em) and 'W' the
        // wide end (1933/2048) — both straight out of Arial's hmtx.
        XCTAssertEqual(UAControlFontMetrics.charAdvance(" ", px), 569 * px / 2048, accuracy: 1e-4)
        XCTAssertEqual(UAControlFontMetrics.charAdvance("W", px), 1933 * px / 2048, accuracy: 1e-4)
        // Below and above the pinned band we take the documented
        // 'n'-width fallback (1139/2048) rather than a silent zero: a
        // zero-width char would collapse a control's box without a trace.
        let fallback = 1139 * px / 2048
        XCTAssertEqual(UAControlFontMetrics.charAdvance("\u{0001}", px), fallback, accuracy: 1e-4)
        XCTAssertEqual(UAControlFontMetrics.charAdvance("\u{00E9}", px), fallback, accuracy: 1e-4)
        // The empty label is genuinely zero wide (a valueless input).
        XCTAssertEqual(UAControlFontMetrics.advance("", px), 0, accuracy: 1e-6)
    }

    func testTheControlSizeIsChromiumsSmallControl() {
        // html.css resolves `font: -webkit-small-control` to 16 x 5/6.
        XCTAssertEqual(px, 13.3333, accuracy: 1e-4)
        // The geometry pin table reads its FONT from here, so the size
        // the plan measures with and draws with cannot diverge.
        XCTAssertEqual(px, UAWidgetsGeometry.FONT, accuracy: 1e-6)
    }
}

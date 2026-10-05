//
//  ChUnitInlineAxisTests.swift
//  Wave 52 lane L8 (vertical-wedges) — Catalyst pins for the iOS halves of
//  M-A (WHICH face a face-less `ch` measures), M-B (WHICH axis) and M-C (the
//  §7.3.1 budget actually reaching the upright layout).
//
//  Verbatim IR: tools/titan/runs/wave51-fix/sections/css-writing-modes/
//  per-test-ir/wpt__css-writing-modes__ch-units-vrl-005.json — the orange
//  div `…__4-479` (FontSize 20px, WritingMode VERTICAL_RL, TextOrientation
//  UPRIGHT, Width 5ch, text "00000"). Ref: 120×120 (5 × 24 px upright line
//  boxes); wave51-fix iOS drew the 60×25 horizontal label (5ch = 60 at SF).
//
//  The harness registers Inter from its bundle; this test bundle has no
//  Inter, so the M-A pin registers the harness's own Inter-Regular.otf for
//  the test's duration (process scope) and unregisters it in tearDown.
//
//  MUTATIONS EXECUTED (2026-10-05, each restored byte-exact, sha-256 verified):
//   • ChUnitMetrics.resolvedFont default arm → `UIFont.systemFont` only →
//     `testDefaultDesignMeasuresTheRegisteredInterFace` fails (SF '0' ≠ Inter).
//   • ChUnitMetrics.measure `if inlineAxisUpright { … }` arm removed →
//     `testUprightChIsRoundedAscentPlusDescent` fails (12.6 ≠ 24).
//   • VerticalUprightTextFlowLayout `fallbackPx:` → nil →
//     `testOverrideBudgetPlansTheOrangeRunAsOneColumn` fails (60×25 ≠ 13×120).
//   • StyleBuilder ch block `inlineAxisUpright:` → `false` →
//     `testStyleBuilderAsksForTheUprightAdvanceOnTheVerbatimWire` fails (12.6 ≠ 24).
//

import CoreText
import SwiftUI
import UIKit
import XCTest
@testable import StyleConverterRuntime

final class ChUnitInlineAxisTests: XCTestCase {

    /// The harness's own Inter Regular — the face the label paints.
    private var interURL: URL {
        // Tests/StyleConverterRuntimeTests → repo root → the harness bundle.
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("apps/ios-harness/StyleConverterTest/Resources/Inter-Regular.otf")
    }

    override func tearDown() {
        // Leave the process exactly as the suite found it: no Inter.
        var error: Unmanaged<CFError>?
        _ = CTFontManagerUnregisterFontsForURL(interURL as CFURL, .process, &error)
        super.tearDown()
    }

    /// Register the harness Inter for this test (process scope).
    private func registerInter() throws {
        var error: Unmanaged<CFError>?
        let ok = CTFontManagerRegisterFontsForURL(interURL as CFURL, .process, &error)
        // "already registered" (a prior test's leftover) is fine; anything
        // else means the file is missing and the pin would be vacuous.
        if !ok, UIFont(name: "Inter", size: 20) == nil {
            XCTFail("could not register \(interURL.path): \(String(describing: error?.takeRetainedValue()))")
        }
    }

    func testDefaultDesignMeasuresTheRegisteredInterFace() throws {
        // Without Inter (the bare test bundle) the default design keys on SF.
        XCTAssertEqual(ChUnitMetrics.defaultMeasuringFaceKey(sizePx: 20), "default")
        try registerInter()
        // With Inter registered (the harness process) it keys on Inter…
        XCTAssertEqual(ChUnitMetrics.defaultMeasuringFaceKey(sizePx: 20), "inter")
        // …and measures Inter's '0': 12.6 px at 20 px, so 5ch = 63 (the ref).
        let ch = try XCTUnwrap(ChUnitMetrics.zeroAdvancePx(rounded: false, monospaced: false,
                                                           serif: false, sizePx: 20))
        XCTAssertEqual(ch * 5, 63, accuracy: 0.5)
    }

    func testUprightChIsRoundedAscentPlusDescent() throws {
        try registerInter()
        // M-B: vertical-rl + upright → round(ascent) + round(descent) of the
        // SAME face — 19 + 5 = 24 at 20 px Inter, so `width: 5ch` = 120.
        let upright = try XCTUnwrap(ChUnitMetrics.zeroAdvancePx(
            rounded: false, monospaced: false, serif: false, sizePx: 20,
            inlineAxisUpright: VerticalInlineAxis.chAdvanceIsVertical(writingMode: .verticalRl,
                                                                      textOrientation: .upright)))
        XCTAssertEqual(upright, 24)
        XCTAssertEqual(upright * 5, 120)
        // The arithmetic on a known system face: each half rounds alone.
        let helvetica = CTFontCreateWithName("Helvetica" as CFString, 20, nil)
        let expected = Double(CTFontGetAscent(helvetica)).rounded()
            + Double(CTFontGetDescent(helvetica)).rounded()
        XCTAssertEqual(ChUnitMetrics.verticalAdvance(helvetica), expected)
        // Sideways keeps the x-advance (the 005 blue box's 63 / 5).
        let sideways = try XCTUnwrap(ChUnitMetrics.zeroAdvancePx(
            rounded: false, monospaced: false, serif: false, sizePx: 20,
            inlineAxisUpright: VerticalInlineAxis.chAdvanceIsVertical(writingMode: .verticalRl,
                                                                      textOrientation: .sideways)))
        XCTAssertEqual(sideways, 12.6, accuracy: 0.1)
    }

    /// `…ch-units-vrl-005__4-479` (orange, upright) and `…__3-478` (blue,
    /// horizontal) property lists, verbatim from the per-test IR.
    private func verbatim(_ json: String) throws -> [IRProperty] {
        try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8)).properties
    }

    func testStyleBuilderAsksForTheUprightAdvanceOnTheVerbatimWire() throws {
        try registerInter()
        let orange = try verbatim(#"""
        {"id":"o","name":"o","properties":[{"type":"FontSize","data":{"px":20,"original":{"type":"length","px":20}}},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":"transparent"}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0.6470588235294118,"b":0},"original":"orange"}},{"type":"WritingMode","data":"VERTICAL_RL"},{"type":"TextOrientation","data":"UPRIGHT"},{"type":"Width","data":{"type":"length","original":{"v":5,"u":"CH"}}}],"text":"00000"}
        """#)
        let blue = try verbatim(#"""
        {"id":"b","name":"b","properties":[{"type":"FontSize","data":{"px":20,"original":{"type":"length","px":20}}},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":"transparent"}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}},{"type":"Height","data":{"type":"length","original":{"v":5,"u":"CH"}}},{"type":"Display","data":"INLINE_BLOCK"}],"text":"00000"}
        """#)
        // The StyleBuilder ch block hands ChUnitMetrics the inline axis: the
        // upright orange box measures 24 (→ 120), the horizontal blue 12.6 (→ 63).
        XCTAssertEqual(StyleBuilder.build(from: orange).spacing.context.chAdvancePx, 24)
        XCTAssertEqual(try XCTUnwrap(StyleBuilder.build(from: blue).spacing.context.chAdvancePx), 12.6, accuracy: 0.1)
    }

    /// The orange run as the label builds it: fallback = the 60×25 horizontal
    /// label wave51-fix drew, glyphs = 13×24 upright '0's, inside the label's
    /// `.fixedSize(vertical: true)` (the nil-height proposal).
    @MainActor
    private func renderedSize(budgetOverridePx: CGFloat?) throws -> CGSize {
        let view = VerticalUprightTextFlow(text: "00000", stack: .rightToLeft,
                                           budgetOverridePx: budgetOverridePx) {
            Color.red.frame(width: 60, height: 25)
        } glyph: { _ in
            Color.green.frame(width: 13, height: 24)
        }
        .fixedSize(horizontal: false, vertical: true)
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        let image = try XCTUnwrap(renderer.cgImage, "ImageRenderer produced no image")
        return CGSize(width: image.width, height: image.height)
    }

    @MainActor
    func testOverrideBudgetPlansTheOrangeRunAsOneColumn() throws {
        // The seam passes the §7.3.1 ICB (568) → one column of five glyphs.
        let budget = VerticalUprightGate.budgetPx(ownBlockExtentPx: nil,
                                                  containingBlockHeightPx: nil,
                                                  icbBlockExtentPx: 568)
        XCTAssertEqual(try renderedSize(budgetOverridePx: budget), CGSize(width: 13, height: 120))
        // No override (every non-capture caller) → the historical decline.
        XCTAssertEqual(try renderedSize(budgetOverridePx: nil), CGSize(width: 60, height: 25))
    }
}

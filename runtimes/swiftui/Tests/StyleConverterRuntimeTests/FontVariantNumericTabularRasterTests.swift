//
//  FontVariantNumericTabularRasterTests.swift
//  StyleConverterRuntimeTests — wave 55, lane L5 (U3-ios) — the SEAM pin.
//  SHIPS INSIDE seam-6.patch (tools/titan/results/wave55-counter-suffix-
//  rows/), never on its own: it renders through PlaceholderLabel.labelFont
//  in ComponentRenderer, so it is red without the seam by design.
//
//  ## What it pins
//  `TextConfig.tabularNums` reaches the label's own face as
//  `Font.monospacedDigit()`: a run declaring `font-variant-numeric:
//  tabular-nums` lays its digits out at the font's fixed figure width.
//  Differential: two leaf runs with the typography of the counter-suffix
//  RTL marker run `counter-suffix__0__4__3` (FontSize, FontWeight 400,
//  FontStyle, WhiteSpace PRE — the positioning properties dropped so both
//  sit in flow), text "1111" so the per-digit advance difference
//  accumulates, one with and one without its `{"type":
//  "FontVariantNumeric","data":["TABULAR_NUMS"]}`.
//
//  ## Honest scope — why `ui-rounded`, not the wire's `Inter, …`
//  The test bundle does not register Inter, so the wire's family reaches
//  labelFont's legacy `.custom("Inter")`, which resolves to a SUBSTITUTE
//  face that ignores `monospacedDigit()` (measured in the lane's probe:
//  ink 55 px with and without). The generic `ui-rounded` routes through
//  `Font.system(size:design:)`, whose figures are proportional by default,
//  so this pins the WIRING (TextConfig.tabularNums → labelFont). That the
//  modifier reaches the bundled Inter's `tnum` on the PRODUCTION path was
//  measured separately with Inter registered for one isolated test process
//  (tools/titan/results/wave55-counter-suffix-rows/probe-monospacedDigit.out.txt:
//  the verbatim "1" run inks 7 px tabular vs 4 px proportional, the ref's
//  one.tf vs one); the device key `[T] counter-suffix ios` decides
//  (counter-suffix-rows §9 R3).
//
//  Mutation EXECUTED by the lane (bracketed in the lane note): with
//  ComponentRenderer.swift restored to the pre-seam bytes both runs ink at
//  one width and this goes red.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class FontVariantNumericTabularRasterTests: XCTestCase {

    /// One in-flow run of "1111" with the carrier's typography.
    private func run(tabular: Bool) throws -> IRComponent {
        let fvn = tabular ? #",{"type":"FontVariantNumeric","data":["TABULAR_NUMS"]}"# : ""
        return try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"run","name":"run","text":"1111",
         "properties":[{"type":"WhiteSpace","data":"PRE"},
           {"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},
           {"type":"FontFamily","data":["ui-rounded"]},
           {"type":"FontSize","data":{"px":32,"original":{"type":"length","px":32}}},
           {"type":"FontStyle","data":"normal"},{"type":"FontWeight","data":400}\(fvn)]}
        """.utf8))
    }

    /// The run's ink span (first to last inked column) in a white frame.
    @MainActor
    private func inkWidth(_ comp: IRComponent) throws -> Int {
        let view = VStack(alignment: .leading, spacing: 0) { ComponentHost(component: comp) }
            .frame(width: 358, alignment: .topLeading)
            .padding(16)
            .frame(width: 390, height: 80, alignment: .topLeading)
            .background(Color.white)
            .environment(\.wptCaptureMode, true)
            .environment(\.styleViewport, StyleViewport(width: 390, height: 844, rootContainingBlock: 358))
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        let cg = try XCTUnwrap(renderer.cgImage, "ImageRenderer produced no image")
        var buf = [UInt8](repeating: 0, count: cg.width * cg.height * 4)
        let ctx = try XCTUnwrap(CGContext(
            data: &buf, width: cg.width, height: cg.height, bitsPerComponent: 8,
            bytesPerRow: cg.width * 4, space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: cg.width, height: cg.height))
        var lo = Int.max, hi = -1
        for y in 0..<cg.height {
            for x in 0..<cg.width {
                let i = (y * cg.width + x) * 4
                if buf[i] < 128 && buf[i + 1] < 128 && buf[i + 2] < 128 { lo = min(lo, x); hi = max(hi, x) }
            }
        }
        XCTAssertGreaterThanOrEqual(hi, lo, "no ink")
        return hi - lo + 1
    }

    /// Tabular figures are wider than the face's proportional `1`s.
    @MainActor
    func testTabularNumsWidensTheDigitRun() throws {
        let tab = try inkWidth(try run(tabular: true))
        let prop = try inkWidth(try run(tabular: false))
        // Logged so the lane note can cite the measured widths verbatim.
        print("FontVariantNumericTabularRasterTests ink width tabular \(tab) proportional \(prop)")
        XCTAssertGreaterThan(tab, prop, "tabular-nums did not reach the label face")
    }
}

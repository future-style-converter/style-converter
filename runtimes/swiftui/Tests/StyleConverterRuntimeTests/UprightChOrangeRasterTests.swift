//
//  UprightChOrangeRasterTests.swift
//  Wave 52 lane L8 (vertical-wedges) — END-TO-END Catalyst raster pin for the
//  iOS ch-units-vrl fix: M-A (Inter face) + M-B (upright inline axis) through
//  StyleBuilder, and M-C (the §7.3.1 budget) through the ComponentRenderer
//  seam hunk. Ships INSIDE seam-2.patch (tools/titan/results/
//  wave52-vertical-wedges/) because its height half is red without the
//  seam by construction — the two must land in one commit.
//
//  Verbatim IR: tools/titan/runs/wave51-fix/sections/css-writing-modes/
//  per-test-ir/wpt__css-writing-modes__ch-units-vrl-005.json — component
//  `wpt__css-writing-modes__ch-units-vrl-005__4-479`, copied byte for byte.
//  Ref (frozen, Chromium + Inter): one 120×120 orange square. wave51-fix
//  iOS drew 60×25 (5ch at SF's 12 px, the run declined to a horizontal
//  label). Measured with this file (seam applied): 120×121.
//
//  MUTATIONS EXECUTED (2026-10-05, restored byte-exact, sha-256 verified):
//   • seam-2 not applied (the label keeps its nil `verticalUprightBudgetPx`
//     default — byte-equivalent to removing the argument) →
//     `testOrangeUprightDivIsTheRefsSquare` fails on height (25 ≠ 121 ± 1).
//   • seam-2's line-box-pin veto (`&& !uprightPlans`) disabled alone → the
//     same failure: the N × L pin clamps the planned column back to 25.
//

import CoreText
import SwiftUI
import UIKit
import XCTest
@testable import StyleConverterRuntime

final class UprightChOrangeRasterTests: XCTestCase {

    /// The harness's Inter Regular (the face the capture paints).
    private var interURL: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("apps/ios-harness/StyleConverterTest/Resources/Inter-Regular.otf")
    }

    override func setUpWithError() throws {
        // Process-scope registration, exactly what the harness bundle does.
        var error: Unmanaged<CFError>?
        if !CTFontManagerRegisterFontsForURL(interURL as CFURL, .process, &error),
           UIFont(name: "Inter", size: 20) == nil {
            XCTFail("could not register \(interURL.path)")
        }
    }

    override func tearDown() {
        // Leave the process without Inter, as the suite found it.
        var error: Unmanaged<CFError>?
        _ = CTFontManagerUnregisterFontsForURL(interURL as CFURL, .process, &error)
        super.tearDown()
    }

    /// `…ch-units-vrl-005__4-479`, verbatim from the per-test IR.
    private func orange() throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data(#"""
        {"id":"wpt__css-writing-modes__ch-units-vrl-005__4-479","name":"wpt__css-writing-modes__ch-units-vrl-005__4","properties":[{"type":"FontSize","data":{"px":20,"original":{"type":"length","px":20}}},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0,"a":0},"original":"transparent"}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0.6470588235294118,"b":0},"original":"orange"}},{"type":"WritingMode","data":"VERTICAL_RL"},{"type":"TextOrientation","data":"UPRIGHT"},{"type":"Width","data":{"type":"length","original":{"v":5,"u":"CH"}}}],"text":"00000"}
        """#.utf8))
    }

    /// The orange ink's bounding box on a white 240×240 stage, rendered in
    /// the composed-WPT environment (capture flag + the 358×568 ICB).
    @MainActor
    private func orangeBox() throws -> CGRect {
        let view = ComponentRenderer(component: try orange())
            .frame(width: 240, height: 240, alignment: .topLeading)
            .background(Color.white)
            .environment(\.wptCaptureMode, true)
            .environment(\.styleViewport, StyleViewport(width: 358, height: 568))
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        let cg = try XCTUnwrap(renderer.cgImage, "ImageRenderer produced no image")
        // Redraw into RGBA8 for format-stable reads.
        let w = cg.width, h = cg.height
        var buf = [UInt8](repeating: 0, count: w * h * 4)
        let ctx = try XCTUnwrap(CGContext(
            data: &buf, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        // Orange = (255, 165, 0) ± 24 per channel.
        var minX = w, minY = h, maxX = -1, maxY = -1
        for y in 0..<h {
            for x in 0..<w {
                let i = (y * w + x) * 4
                if buf[i] > 231, abs(Int(buf[i + 1]) - 165) < 24, buf[i + 2] < 24 {
                    minX = min(minX, x); maxX = max(maxX, x)
                    minY = min(minY, y); maxY = max(maxY, y)
                }
            }
        }
        guard maxX >= 0 else { return .zero }
        return CGRect(x: minX, y: minY, width: maxX - minX + 1, height: maxY - minY + 1)
    }

    @MainActor
    func testOrangeUprightDivIsTheRefsSquare() throws {
        let box = try orangeBox()
        // Width: `5ch` with the upright inline axis = 5 × 24 (M-A + M-B).
        XCTAssertEqual(box.width, 120, accuracy: 1, "orange width \(box.width)")
        // Height: five upright line boxes, one per '0' (M-C through the
        // seam). Each is one Inter-20 Text line box — the brief's open
        // question (24 vs 25) answered on Catalyst: 24.2 (ascent + descent
        // unrounded), so 5 × 24.2 = 121 against the ref's 120.
        XCTAssertEqual(box.height, 121, accuracy: 1, "orange height \(box.height)")
        print("[L8] orange box \(box)")
    }
}

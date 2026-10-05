//
//  UprightColTableRasterTests.swift
//  Wave 52 lane L8 (vertical-wedges, M-E) — END-TO-END Catalyst raster pin:
//  the green `<td>` of css-writing-modes/ch-units-vrl-003 takes its upright
//  `<col style="width: 5ch">`'s 120 px column width. Ships INSIDE
//  seam-4.patch (tools/titan/results/wave52-vertical-wedges/): its width
//  half is red without the seam (the td hugs its 6 px nbsp), so the two
//  land in one commit.
//
//  Verbatim IR: tools/titan/runs/wave51-fix/sections/css-writing-modes/
//  per-test-ir/wpt__css-writing-modes__ch-units-vrl-003.json — the table
//  `…__2-456` and its subtree, `slot` folded into `children`.
//  Ref (frozen, Chromium + Inter): a 120×120 green square.
//
//  MUTATION EXECUTED (2026-10-05, restored byte-exact, sha-256 verified):
//   • seam-4 not applied (the cell's min-width fold absent) →
//     `testUprightColWidensTheGreenCell` fails on width.
//

import CoreText
import SwiftUI
import UIKit
import XCTest
@testable import StyleConverterRuntime

final class UprightColTableRasterTests: XCTestCase {

    /// The harness's Inter Regular (the face the capture paints).
    private var interURL: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("apps/ios-harness/StyleConverterTest/Resources/Inter-Regular.otf")
    }

    override func setUpWithError() throws {
        var error: Unmanaged<CFError>?
        if !CTFontManagerRegisterFontsForURL(interURL as CFURL, .process, &error),
           UIFont(name: "Inter", size: 20) == nil {
            XCTFail("could not register \(interURL.path)")
        }
    }

    override func tearDown() {
        var error: Unmanaged<CFError>?
        _ = CTFontManagerUnregisterFontsForURL(interURL as CFURL, .process, &error)
        super.tearDown()
    }

    /// Green ink bounding box of the verbatim table on a white stage.
    @MainActor
    private func greenBox() throws -> CGRect {
        let table = try JSONDecoder().decode(IRComponent.self, from: Data(#"""
        {"id":"wpt__css-writing-modes__ch-units-vrl-003__2-456","name":"wpt__css-writing-modes__ch-units-vrl-003__2","properties":[{"type":"FontSize","data":{"px":20,"original":{"type":"length","px":20}}},{"type":"BorderCollapse","data":"COLLAPSE"},{"type":"BorderTopStyle","data":"NONE"},{"type":"BorderRightStyle","data":"NONE"},{"type":"BorderBottomStyle","data":"NONE"},{"type":"BorderLeftStyle","data":"NONE"}],"meta":{"sourceTag":"table","role":"ws-after"},"children":[{"id":"ch-units-vrl-003__2__0-457","name":"ch-units-vrl-003__2__0","properties":[{"type":"WritingMode","data":"VERTICAL_RL"},{"type":"TextOrientation","data":"UPRIGHT"},{"type":"Width","data":{"type":"length","original":{"v":5,"u":"CH"}}}],"meta":{"sourceTag":"col"}},{"id":"ch-units-vrl-003__2__1-458","name":"ch-units-vrl-003__2__1","properties":[],"meta":{"sourceTag":"tbody"},"children":[{"id":"ch-units-vrl-003__2__1__0-459","name":"ch-units-vrl-003__2__1__0","properties":[],"meta":{"sourceTag":"tr"},"children":[{"id":"ch-units-vrl-003__2__1__0__0-460","name":"ch-units-vrl-003__2__1__0__0","properties":[{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"Height","data":{"type":"length","original":{"v":5,"u":"CH"}}},{"type":"WritingMode","data":"VERTICAL_RL"},{"type":"TextOrientation","data":"UPRIGHT"}],"text":" ","meta":{"sourceTag":"td"}}]}]}]}
        """#.utf8))
        let view = ComponentRenderer(component: table)
            .frame(width: 240, height: 240, alignment: .topLeading)
            .background(Color.white)
            .environment(\.wptCaptureMode, true)
            .environment(\.styleViewport, StyleViewport(width: 358, height: 568))
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        let cg = try XCTUnwrap(renderer.cgImage, "ImageRenderer produced no image")
        let w = cg.width, h = cg.height
        var buf = [UInt8](repeating: 0, count: w * h * 4)
        let ctx = try XCTUnwrap(CGContext(
            data: &buf, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        // Green = (0, 128, 0) ± 24 per channel.
        var minX = w, minY = h, maxX = -1, maxY = -1
        for y in 0..<h {
            for x in 0..<w {
                let i = (y * w + x) * 4
                if buf[i] < 24, abs(Int(buf[i + 1]) - 128) < 24, buf[i + 2] < 24 {
                    minX = min(minX, x); maxX = max(maxX, x)
                    minY = min(minY, y); maxY = max(maxY, y)
                }
            }
        }
        guard maxX >= 0 else { return .zero }
        return CGRect(x: minX, y: minY, width: maxX - minX + 1, height: maxY - minY + 1)
    }

    @MainActor
    func testUprightColWidensTheGreenCell() throws {
        let box = try greenBox()
        print("[L8] green cell \(box)")
        // Width: the col's 5ch along its upright inline axis (M-E + M-B).
        XCTAssertEqual(box.width, 120, accuracy: 1, "green width \(box.width)")
        // Height: the td's own upright 5ch (M-B through StyleBuilder).
        XCTAssertEqual(box.height, 120, accuracy: 1, "green height \(box.height)")
    }
}

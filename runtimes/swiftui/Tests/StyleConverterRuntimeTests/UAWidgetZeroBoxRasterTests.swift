//
//  UAWidgetZeroBoxRasterTests.swift
//  Wave 52 lane L8 (vertical-wedges, M-G) — END-TO-END Catalyst raster pin:
//  a UA widget whose used box is 0px on one axis paints NO replica atom.
//
//  Verbatim IR: tools/titan/runs/wave51-fix/sections/css-writing-modes/
//  per-test-ir/wpt__css-writing-modes__forms__input-range-zero-inline-size.json
//  — the `writing-mode: vertical-lr` flex div `…__2-813` with its
//  `input[type=range]` `…__2__0-814` (post-load Width 16 × Height 0) as its
//  child, both copied byte for byte (only `slot` folded into `children`).
//  Ref: the div's 1 px green inline-start border and nothing else (the
//  range is `visibility: hidden; inline-size: 0`); wave51-fix iOS painted
//  the fixed 129×21 slider (accent 0,117,255 + grey track) — f 0.9747.
//
//  MUTATION EXECUTED (2026-10-05, restored byte-exact, sha-256 verified):
//   • UAWidgetsResolve.resolve's `if hasZeroUsedBox(properties) { … return
//     nil }` gate disabled → `testZeroBoxRangePaintsNoSlider` fails (accent
//     pixels > 0).
//

import SwiftUI
import XCTest
@testable import StyleConverterRuntime

final class UAWidgetZeroBoxRasterTests: XCTestCase {

    /// The vertical-lr div + its zero-box range, verbatim (see header).
    private func verticalDiv() throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data(#"""
        {"id":"wpt__css-writing-modes__forms__input-range-zero-inline-size__2-813","name":"wpt__css-writing-modes__forms__input-range-zero-inline-size__2","properties":[{"type":"Display","data":"FLEX"},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":{"r":255,"g":0,"b":0}}},{"type":"BorderInlineStartWidth","data":{"px":1}},{"type":"BorderInlineStartStyle","data":"SOLID"},{"type":"BorderInlineStartColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"WritingMode","data":"VERTICAL_LR"},{"type":"Position","data":"STATIC"},{"type":"Width","data":{"type":"length","px":16}},{"type":"Height","data":{"type":"length","px":0}},{"type":"BoxSizing","data":"CONTENT_BOX"},{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},{"type":"BorderTopWidth","data":{"px":1}},{"type":"BorderRightWidth","data":{"px":0}},{"type":"BorderBottomWidth","data":{"px":0}},{"type":"BorderLeftWidth","data":{"px":0}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"NONE"},{"type":"BorderBottomStyle","data":"NONE"},{"type":"BorderLeftStyle","data":"NONE"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":{"r":0,"g":128,"b":0}}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},{"type":"OverflowX","data":"VISIBLE"},{"type":"OverflowY","data":"VISIBLE"}],"meta":{"role":"ws-after"},"children":[{"id":"forms__input-range-zero-inline-size__2__0-814","name":"forms__input-range-zero-inline-size__2__0","properties":[{"type":"Position","data":"STATIC"},{"type":"Width","data":{"type":"length","px":16}},{"type":"Height","data":{"type":"length","px":0}},{"type":"BoxSizing","data":"CONTENT_BOX"},{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},{"type":"BorderTopWidth","data":{"px":0}},{"type":"BorderRightWidth","data":{"px":0}},{"type":"BorderBottomWidth","data":{"px":0}},{"type":"BorderLeftWidth","data":{"px":0}},{"type":"BorderTopStyle","data":"NONE"},{"type":"BorderRightStyle","data":"NONE"},{"type":"BorderBottomStyle","data":"NONE"},{"type":"BorderLeftStyle","data":"NONE"},{"type":"BorderTopColor","data":{"srgb":{"r":0.06274509803921569,"g":0.06274509803921569,"b":0.06274509803921569},"original":{"r":16,"g":16,"b":16}}},{"type":"BorderRightColor","data":{"srgb":{"r":0.06274509803921569,"g":0.06274509803921569,"b":0.06274509803921569},"original":{"r":16,"g":16,"b":16}}},{"type":"BorderBottomColor","data":{"srgb":{"r":0.06274509803921569,"g":0.06274509803921569,"b":0.06274509803921569},"original":{"r":16,"g":16,"b":16}}},{"type":"BorderLeftColor","data":{"srgb":{"r":0.06274509803921569,"g":0.06274509803921569,"b":0.06274509803921569},"original":{"r":16,"g":16,"b":16}}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":{"r":255,"g":255,"b":255}}},{"type":"Display","data":"BLOCK"},{"type":"OverflowX","data":"VISIBLE"},{"type":"OverflowY","data":"VISIBLE"}],"meta":{"sourceTag":"input","attrs":{"type":"range"}}}]}
        """#.utf8))
    }

    /// RGBA8 raster of the div on a white 240×240 stage, composed-WPT env.
    @MainActor
    private func raster() throws -> (px: [UInt8], w: Int, h: Int) {
        let view = ComponentRenderer(component: try verticalDiv())
            .frame(width: 240, height: 240, alignment: .topLeading)
            .background(Color.white)
            .environment(\.wptCaptureMode, true)
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
        return (buf, w, h)
    }

    /// Pixels within ±tol of an RGB colour.
    private func count(_ img: (px: [UInt8], w: Int, h: Int), _ r: Int, _ g: Int, _ b: Int, tol: Int = 40) -> Int {
        var n = 0
        for i in stride(from: 0, to: img.px.count, by: 4)
        where abs(Int(img.px[i]) - r) <= tol && abs(Int(img.px[i + 1]) - g) <= tol && abs(Int(img.px[i + 2]) - b) <= tol {
            n += 1
        }
        return n
    }

    @MainActor
    func testZeroBoxRangePaintsNoSlider() throws {
        let img = try raster()
        // No slider: neither the accent fill (0,117,255) nor the thumb/track
        // ink the 129×21 atom paints may appear anywhere.
        XCTAssertEqual(count(img, 0, 117, 255), 0, "accent (slider) pixels")
        // No red: the div is 16×0 content, so its red background only ever
        // sits under its own 1 px green border.
        XCTAssertEqual(count(img, 255, 0, 0, tol: 30), 0, "red pixels")
    }
}

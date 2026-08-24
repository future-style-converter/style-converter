//
//  FlowLayoutDistributionTests.swift
//  Wave 47, lane Z7 — FlowLayout content distribution.
//
//  PRODUCT-PATH raster pins (the FlexWrapStretchTests pattern: real IR
//  decoded into IRComponent, rendered through ComponentRenderer,
//  rasterized with ImageRenderer — nothing synthetic fed to the Layout)
//  for the two wave-47 FlowLayout additions:
//
//    • per-line `justify-content` (css-flexbox-1 §8.2 via css-align-3's
//      <content-distribution>) — WPT flex-gap-decorations-040…042/044/
//      050 distribute each wrapped line's leftover into its item gaps,
//      and the wave-46 iOS captures show every line packed flush-left
//      instead;
//    • `align-content` POSITIONING keywords (§9.6) — 047…049 create
//      row gaps purely from content distribution between lines, and the
//      wave-46 iOS captures show the lines packed at cross-start.
//
//  Both go through CSSFlexMath.mainOffsets, the same math the nowrap
//  path pins — these tests pin the WRAP wiring, not the arithmetic.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

@available(iOS 16.0, *)
final class FlowLayoutDistributionTests: XCTestCase {

    // MARK: - product-path raster helper (FlexWrapStretchTests pattern)

    /// Render a wrap-flex container with `count` fixed 50×30 blue items
    /// and the given extra container properties on a white 200×200
    /// stage.
    @MainActor
    private func render(containerProps: [String], count: Int)
        throws -> (px: [UInt8], w: Int, h: Int) {
        // Fixed-size items: explicit width AND height, so neither §8.4
        // item stretch nor §9.6 line stretch participates — the pins
        // isolate pure distribution.
        let kids = (1...count).map { n in
            #"{"id":"i\#(n)","name":"I\#(n)","properties":["# +
            #"{"type":"Width","data":{"type":"length","px":50}},"# +
            #"{"type":"Height","data":{"type":"length","px":30}},"# +
            #"{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}}]}"#
        }.joined(separator: ",")
        let props = ([
            #"{"type":"Display","data":"FLEX"}"#,
            #"{"type":"FlexWrap","data":"WRAP"}"#
        ] + containerProps).joined(separator: ",")
        let comp = try JSONDecoder().decode(IRComponent.self, from: Data(#"""
        {"id":"box","name":"Box","properties":[\#(props)],
         "children":[\#(kids)]}
        """#.utf8))
        let view = ZStack(alignment: .topLeading) {
            Color.white
            ComponentRenderer(component: comp)
        }.frame(width: 200, height: 200, alignment: .topLeading)
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        let cg = try XCTUnwrap(renderer.cgImage, "ImageRenderer produced no image")
        let w = cg.width, h = cg.height
        var buf = [UInt8](repeating: 0, count: w * h * 4)
        let ctx = try XCTUnwrap(CGContext(
            data: &buf, width: w, height: h, bitsPerComponent: 8,
            bytesPerRow: w * 4, space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        return (buf, w, h)
    }

    /// RGB triple at a raster point.
    private func rgb(_ img: (px: [UInt8], w: Int, h: Int),
                     _ x: Int, _ y: Int) -> (r: UInt8, g: UInt8, b: UInt8) {
        let i = (y * img.w + x) * 4
        return (img.px[i], img.px[i + 1], img.px[i + 2])
    }

    /// ±24 antialiasing tolerance, as in the other raster pins.
    private func matches(_ c: (r: UInt8, g: UInt8, b: UInt8),
                         _ t: (r: Int, g: Int, b: Int)) -> Bool {
        abs(Int(c.r) - t.r) <= 24 && abs(Int(c.g) - t.g) <= 24 && abs(Int(c.b) - t.b) <= 24
    }

    private let blue = (r: 0, g: 0, b: 255)
    private let white = (r: 255, g: 255, b: 255)

    private func expect(_ img: (px: [UInt8], w: Int, h: Int),
                        _ points: [(Int, Int)],
                        _ target: (r: Int, g: Int, b: Int),
                        _ what: String,
                        file: StaticString = #filePath, line: UInt = #line) {
        for (x, y) in points {
            XCTAssertTrue(matches(rgb(img, x, y), target),
                          "\(what): (\(x),\(y)) was \(rgb(img, x, y))",
                          file: file, line: line)
        }
    }

    // MARK: - §8.2 per-line justify-content

    /// The 040 shape in miniature: `width: 170; justify-content:
    /// space-between`, five 50×30 items → line 1 holds three (leftover
    /// 20 split into two 10px gaps: x 0/60/120), line 2 holds two
    /// (leftover 70 in one gap: x 0/120). The wave-46 capture packed
    /// both lines flush-left (items at x 0/50/100).
    @MainActor
    func testJustifySpaceBetweenDistributesPerLine() throws {
        let img = try render(containerProps: [
            #"{"type":"Width","data":{"type":"length","px":170}}"#,
            #"{"type":"JustifyContent","data":"SPACE_BETWEEN"}"#
        ], count: 5)
        // Line 1 (y 0–30): items at 0–50 / 60–110 / 120–170…
        expect(img, [(25, 15), (85, 15), (145, 15)], blue, "line-1 item")
        // …with the two distributed 10px gaps bare. x=55 is also blue
        // in the PACKED layout (item 2 at 50–100), so this pin fails
        // exactly when distribution is missing.
        expect(img, [(55, 15), (115, 15)], white, "line-1 distributed gap")
        // Line 2 (y 30–60): first item flush left, last flush right.
        expect(img, [(25, 45), (145, 45)], blue, "line-2 item")
        expect(img, [(85, 45)], white, "line-2 distributed gap")
    }

    /// No keyword → packed start, byte-compatible with the pre-wave-47
    /// accumulation loop (the guard that holds the committed Flex_Wrap
    /// baseline and every existing FlexWrapStretchTests pin).
    @MainActor
    func testNoJustifyKeywordStaysPacked() throws {
        let img = try render(containerProps: [
            #"{"type":"Width","data":{"type":"length","px":170}}"#
        ], count: 5)
        // Line 1 packed: items at 0–50 / 50–100 / 100–150, tail bare.
        expect(img, [(25, 15), (75, 15), (125, 15)], blue, "packed item")
        expect(img, [(160, 15)], white, "packed line tail")
        // Line 2 packed: 0–50 / 50–100.
        expect(img, [(25, 45), (75, 45)], blue, "packed line-2 item")
        expect(img, [(120, 45)], white, "packed line-2 tail")
    }

    // MARK: - §9.6 align-content positioning

    /// The 047 shape in miniature: `width: 110; height: 150;
    /// align-content: space-between`, four items → two 30px lines with
    /// the 90px leftover forming ONE distributed row gap (line 1 at y 0,
    /// line 2 at y 120). The wave-46 captures packed both lines at the
    /// cross start (y 0 and 30).
    @MainActor
    func testAlignContentSpaceBetweenPositionsLines() throws {
        let img = try render(containerProps: [
            #"{"type":"Width","data":{"type":"length","px":110}}"#,
            #"{"type":"Height","data":{"type":"length","px":150}}"#,
            #"{"type":"AlignContent","data":"SPACE_BETWEEN"}"#
        ], count: 4)
        // Line 1 at the cross start.
        expect(img, [(25, 15), (75, 15)], blue, "line 1")
        // The distributed 90px gap — y=45 is blue when lines pack.
        expect(img, [(25, 45), (25, 100)], white, "distributed line gap")
        // Line 2 flush with the cross end (y 120–150).
        expect(img, [(25, 135), (75, 135)], blue, "line 2 at cross end")
    }

    /// `align-content: center` positions the line block centred: two
    /// 30px lines in a 150px box → block at y 45–105.
    @MainActor
    func testAlignContentCenterCentresLineBlock() throws {
        let img = try render(containerProps: [
            #"{"type":"Width","data":{"type":"length","px":110}}"#,
            #"{"type":"Height","data":{"type":"length","px":150}}"#,
            #"{"type":"AlignContent","data":"CENTER"}"#
        ], count: 4)
        // Above and below the centred block stays bare.
        expect(img, [(25, 20), (25, 130)], white, "outside centred block")
        // The two lines inside it.
        expect(img, [(25, 60), (25, 90)], blue, "centred lines")
    }
}

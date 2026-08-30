//
//  FlowLayoutColumnWrapTests.swift
//  Wave 48, lane W7 — FlowLayout's column axis + static flex-basis.
//
//  PRODUCT-PATH raster pins (the FlowLayoutDistributionTests pattern:
//  real IR decoded into IRComponent, rendered through ComponentRenderer,
//  rasterized with ImageRenderer — nothing synthetic fed to the Layout)
//  for the two wave-48 FlowLayout additions:
//
//    • COLUMN-direction wrap (`flex-flow: column wrap`) — WPT css-gaps
//      flex-gap-decorations-043/045 lay wrapped COLUMNS side by side
//      horizontally; the wave48-cal iOS captures show stacked rows
//      because the pre-wave-48 layout was row-only (ios-ref SSIM
//      0.9122 / 0.8738).
//    • static `flex-basis` main sizing — 025's `flex-basis: 100%`
//      (serialized as the bare number 100) was dropped and both items
//      collapsed to intrinsic 0pt width (blank capture, ios-ref 0.8559).
//
//  The payloads reproduce the wave48-cal per-test IRs' property shapes
//  verbatim (same types, same value encodings); the 045-shape test drops
//  the container's 2px border so every expected coordinate below stays
//  on the content grid the arithmetic is quoted in (borders shift all
//  pins by 2 and change nothing about the line geometry under test).
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

@available(iOS 16.0, *)
final class FlowLayoutColumnWrapTests: XCTestCase {

    // MARK: - product-path raster helper (FlowLayoutDistributionTests pattern)

    /// Decode a full IRComponent JSON string, render it top-leading on a
    /// white stage of the given size, and return the raster.
    @MainActor
    private func render(_ json: String, stageW: CGFloat, stageH: CGFloat)
        throws -> (px: [UInt8], w: Int, h: Int) {
        let comp = try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8))
        let view = ZStack(alignment: .topLeading) {
            Color.white
            ComponentRenderer(component: comp)
        }.frame(width: stageW, height: stageH, alignment: .topLeading)
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

    private let blue = (r: 0, g: 123, b: 255)   // #007bff, the WPT item color
    private let teal = (r: 0, g: 128, b: 128)   // CSS `teal`
    private let white = (r: 255, g: 255, b: 255)

    private func expect(_ img: (px: [UInt8], w: Int, h: Int),
                        _ points: [(Int, Int)],
                        _ target: (r: Int, g: Int, b: Int),
                        _ label: String) {
        for (x, y) in points {
            let c = rgb(img, x, y)
            XCTAssertTrue(matches(c, target),
                          "\(label): expected ~\(target) at (\(x),\(y)), got \(c)")
        }
    }

    // MARK: - payload builders (wave48-cal per-test-ir property shapes)

    /// N 50×70 #007bff items — the 043/045 children, verbatim shapes.
    /// (Double-# raw-string delimiters: the color's own `"#007bff"` would
    /// otherwise close a single-# raw string at its `"#` byte pair.)
    private func columnItems(_ n: Int) -> String {
        (1...n).map { i in
            ##"{"id":"c\##(i)","name":"C\##(i)","properties":["## +
            ##"{"type":"Width","data":{"type":"length","px":50}},"## +
            ##"{"type":"Height","data":{"type":"length","px":70}},"## +
            ##"{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.4823529411764706,"b":1},"original":"#007bff"}},"## +
            ##"{"type":"Display","data":"FLEX"}]}"##
        }.joined(separator: ",")
    }

    // MARK: - 043: packed column wrap (zero leftover, space-between)

    @MainActor
    func testColumnWrapLaysLinesSideBySide_043Shape() throws {
        // flex-gap-decorations-043 minus its border (see header): 100×400
        // container, column wrap, justify-content space-between, nine
        // 50×70 items. §9.3 against the 400 budget → five per column
        // (5×70 = 350 ≤ 400); §9.4 step 8 leftover = 100 − 2×50 = 0 →
        // packed lines at x 0 and 50 — Chromium's exact geometry
        // (wave48-cal web capture: column 2's blue at canvas x 68..117 =
        // content 50..100).
        let json = #"""
        {"id":"box","name":"Box","properties":[
          {"type":"Display","data":"FLEX"},
          {"type":"FlexDirection","data":"COLUMN"},
          {"type":"FlexWrap","data":"WRAP"},
          {"type":"JustifyContent","data":"SPACE_BETWEEN"},
          {"type":"Width","data":{"type":"length","px":100}},
          {"type":"Height","data":{"type":"length","px":400}}],
         "children":[\#(columnItems(9))]}
        """#
        let img = try render(json, stageW: 150, stageH: 450)
        // Column 1, first item: x 0..50, y 0..70.
        expect(img, [(25, 35)], blue, "col1 item1")
        // Column 2, first item (index 5): x 50..100, y 0..70.
        expect(img, [(75, 35)], blue, "col2 item1")
        // Column 1 space-between: 4 gaps of (400−350)/4 = 12.5 → item 2
        // spans y 82.5..152.5.
        expect(img, [(25, 100)], blue, "col1 item2 distributed")
        expect(img, [(25, 75)], white, "col1 gap 1 open")
        // Column 2 space-between: 3 gaps of (400−280)/3 = 40 → item 2
        // spans y 110..180; y 75 sits in its first gap.
        expect(img, [(75, 145)], blue, "col2 item2 distributed")
        expect(img, [(75, 85)], white, "col2 gap 1 open")
        // Nothing to the right of the two columns.
        expect(img, [(120, 35)], white, "no third column")
        // THE ROW-ONLY REGRESSION SHAPE: the wave48-cal capture stacked
        // full-width rows, so y 90 (between col1 items 1 and 2) was
        // solid blue across the box — it must now be open in column 1.
        expect(img, [(25, 78)], white, "not row-stacked")
    }

    // MARK: - 045: §9.4 step 8 stretches the lines (column axis)

    @MainActor
    func testColumnWrapStretchesLines_045Shape() throws {
        // flex-gap-decorations-045 minus its border (see header): 120×400
        // container, column wrap, align-content stretch, column-gap 5,
        // nine 50×70 items → two 50pt-wide lines, leftover 120−100−5 =
        // 15 → each line stretches to 57.5; line 2 starts at x 62.5
        // (Chromium: content 62.5 = canvas x 81 in the wave48-cal web
        // capture). The pre-wave-48 FlowColumn-equivalent packing put
        // line 2 at x 55 — the 0.9232/0.8738 defect this pins against.
        let json = #"""
        {"id":"box","name":"Box","properties":[
          {"type":"Display","data":"FLEX"},
          {"type":"FlexDirection","data":"COLUMN"},
          {"type":"FlexWrap","data":"WRAP"},
          {"type":"JustifyContent","data":"SPACE_BETWEEN"},
          {"type":"AlignContent","data":"STRETCH"},
          {"type":"ColumnGap","data":{"type":"length","px":5}},
          {"type":"Width","data":{"type":"length","px":120}},
          {"type":"Height","data":{"type":"length","px":400}}],
         "children":[\#(columnItems(9))]}
        """#
        let img = try render(json, stageW: 170, stageH: 450)
        // Column 1 item at line start.
        expect(img, [(25, 35)], blue, "col1 item1")
        // Column 2 item 1 now spans x 62.5..112.5 — the stretched-line
        // origin. x 70 and 110 are interior; x 54 (inside the packed
        // pre-wave-48 position 55..105 but OUTSIDE the stretched one)
        // must be open.
        expect(img, [(70, 35), (110, 35)], blue, "col2 item1 at stretched line origin")
        expect(img, [(54, 35)], white, "packed origin vacated")
        // Column 2's own space-between: item 2 spans y 110..180.
        expect(img, [(70, 145)], blue, "col2 item2 distributed")
    }

    // MARK: - 025: percent flex-basis sizes the wrap item

    @MainActor
    func testPercentBasisSizesWrapItems_025Shape() throws {
        // flex-gap-decorations-025's children VERBATIM ("FlexBasis": 100
        // — the bare-number IRPercentage wire shape — with height 50 and
        // no width) in a row-wrap container with the fixture's 10px gaps.
        // The fixture's container has width:auto and gets its definite
        // main from the composed canvas's §10.3.3 block fill; here a
        // declared 300px width stands in as that definite main so the
        // static fold has its base on the product path too.
        // Expected: each item resolves 100% × 300 = 300pt → one per line
        // → two 300×50 teal bars at y 0..50 and 60..110 (row-gap 10).
        // Before wave 48 the basis was DROPPED: both items measured at
        // the 50pt product floor and shared one line — nothing teal
        // right of x 110, and no second bar below y 60.
        let items = (1...2).map { i in
            #"{"id":"t\#(i)","name":"T\#(i)","properties":["# +
            #"{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0.5019607843137255},"original":"teal"}},"# +
            #"{"type":"Height","data":{"type":"length","px":50}},"# +
            #"{"type":"FlexBasis","data":100}]}"#
        }.joined(separator: ",")
        let json = #"""
        {"id":"box","name":"Box","properties":[
          {"type":"Display","data":"FLEX"},
          {"type":"FlexWrap","data":"WRAP"},
          {"type":"RowGap","data":{"type":"length","px":10}},
          {"type":"ColumnGap","data":{"type":"length","px":10}},
          {"type":"Width","data":{"type":"length","px":300}}],
         "children":[\#(items)]}
        """#
        let img = try render(json, stageW: 350, stageH: 200)
        // Bar 1 spans the full 300pt basis — left, middle, and right.
        expect(img, [(20, 25), (150, 25), (280, 25)], teal, "bar 1 full basis width")
        // Bar 2 on its own line below the 10pt row gap.
        expect(img, [(150, 85), (280, 85)], teal, "bar 2 wrapped by basis")
        // The row gap between the bars is open (the fixture's gold row
        // rule is not declared here — this pin is layout-only).
        expect(img, [(150, 55)], white, "row gap open")
        // Nothing beyond the container's 300pt main.
        expect(img, [(320, 25)], white, "clamped to container main")
    }

    // MARK: - grow guard: a flexed basis keeps the measured fallback

    @MainActor
    func testGrowingItemIgnoresStaticBasis_007Guard() throws {
        // css-values calc-size-flex-007's guard shape: `flex: 1 1 0` puts
        // grow 1 (verbatim FlexGrowProperty envelope) next to the bare-
        // number basis 0. The wrap paths run no §9.7 resolution, so
        // adopting that 0%-basis would paint a zero-width box no browser
        // shows — the grow gate must keep the MEASURED size instead
        // (the 50pt product floor for this unsized leaf).
        let json = ##"""
        {"id":"box","name":"Box","properties":[
          {"type":"Display","data":"FLEX"},
          {"type":"FlexWrap","data":"WRAP"},
          {"type":"Width","data":{"type":"length","px":100}},
          {"type":"Height","data":{"type":"length","px":100}}],
         "children":[
          {"id":"g","name":"G","properties":[
            {"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.4823529411764706,"b":1},"original":"#007bff"}},
            {"type":"Height","data":{"type":"length","px":30}},
            {"type":"FlexGrow","data":{"value":{"type":"app.irmodels.properties.layout.flexbox.FlexGrowProperty.FlexGrowValue.Number","value":1},"normalizedValue":1}},
            {"type":"FlexBasis","data":0}]}]}
        """##
        let img = try render(json, stageW: 150, stageH: 150)
        // The item still paints at its measured/floor width — a zero
        // width (adopted 0% basis) would leave both points white.
        expect(img, [(10, 15), (40, 15)], blue, "grow guard keeps measured size")
    }
}

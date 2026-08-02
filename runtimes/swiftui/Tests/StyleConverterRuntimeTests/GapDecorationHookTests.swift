//
//  GapDecorationHookTests.swift
//  Wave 24, lane GAPS-I — the END-TO-END pin for the draw hook: real IR
//  on the wave-24 WIRE CONTRACT → ComponentRenderer → ImageRenderer.
//
//  GapDecorationSegmentsTests pins the geometry and
//  GapDecorationRasterTests pins the painter; this file pins the wiring
//  in between — the per-child anchor preference, the container overlay,
//  and the fact that a container WITHOUT the family paints nothing at
//  all (the ungated-safety claim).
//
//  The fixture is the ref-002 shape: a 110×110 wrap flex container with
//  four 50×50 items and 10px gaps, i.e. one column gap band at x50–60
//  and one row gap band at y50–60 in content-box space.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class GapDecorationHookTests: XCTestCase {

    /// The ref-002 container, with or without the gap-decoration
    /// longhands. Colours are spec sRGB (pure red / pure blue), NOT
    /// SwiftUI system colours, so the probes can assert exact primaries.
    private func container(decorated: Bool) throws -> IRComponent {
        let rules = decorated ? """
        ,{"type":"ColumnRuleColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},
         {"type":"ColumnRuleStyle","data":"SOLID"},
         {"type":"ColumnRuleWidth","data":{"type":"length","px":10}},
         {"type":"RowRuleColor","data":{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}},
         {"type":"RowRuleStyle","data":"SOLID"},
         {"type":"RowRuleWidth","data":{"type":"length","px":10}}
        """ : ""
        // Four identical 50×50 items; a plain background keeps them
        // distinguishable from both rules and from the white stage.
        let item = { (n: Int) in """
        {"id":"gapitem-\(n)","name":"I\(n)","properties":[
          {"type":"Width","data":{"type":"length","px":50}},
          {"type":"Height","data":{"type":"length","px":50}},
          {"type":"BackgroundColor","data":{"srgb":{"r":0.5,"g":0.5,"b":0.5},
                                            "original":"grey"}}]}
        """ }
        return try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"gapbox-1","name":"GapBox","properties":[
          {"type":"Display","data":"FLEX"},
          {"type":"FlexWrap","data":"WRAP"},
          {"type":"Width","data":{"type":"length","px":110}},
          {"type":"Height","data":{"type":"length","px":110}},
          {"type":"ColumnGap","data":{"type":"length","px":10}},
          {"type":"RowGap","data":{"type":"length","px":10}}\(rules)],
         "children":[\(item(1)),\(item(2)),\(item(3)),\(item(4))]}
        """.utf8))
    }

    /// Render the component on a white stage at scale 1.
    @MainActor
    private func render(_ comp: IRComponent) throws -> (px: [UInt8], w: Int, h: Int) {
        let view = ComponentRenderer(component: comp)
            .frame(width: 160, height: 160, alignment: .topLeading)
            .background(Color.white)
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

    /// Count pixels close to a target colour anywhere on the stage.
    private func count(_ img: (px: [UInt8], w: Int, h: Int),
                       _ t: (r: Int, g: Int, b: Int)) -> Int {
        var n = 0
        for i in stride(from: 0, to: img.px.count, by: 4) {
            // Tight ±16 window: the rules are flat fills, not gradients.
            if abs(Int(img.px[i]) - t.r) <= 16,
               abs(Int(img.px[i + 1]) - t.g) <= 16,
               abs(Int(img.px[i + 2]) - t.b) <= 16 { n += 1 }
        }
        return n
    }

    private let red = (r: 255, g: 0, b: 0)
    private let blue = (r: 0, g: 0, b: 255)

    /// The decorated container really paints both families through the
    /// live renderer: a 10×110 column band and a 110×10 row band, minus
    /// their 10×10 overlap which the row rule wins (rule-overlap's
    /// initial row-over-column). 1100 + 1100 − 100 = 2100 red+blue px,
    /// of which the column keeps 1100 − 100 = 1000.
    @MainActor
    func testDecoratedFlexContainerPaintsBothFamilies() throws {
        let img = try render(try container(decorated: true))
        let reds = count(img, red)
        let blues = count(img, blue)
        // Both families reached the canvas at all.
        XCTAssertGreaterThan(reds, 0, "no column-rule ink")
        XCTAssertGreaterThan(blues, 0, "no row-rule ink")
        // The row rule is a full 110×10 band; the column rule is the
        // merged 10×110 run minus the 10×10 square the row rule covers.
        // ±40px of slack absorbs the anchor/AA edges.
        XCTAssertEqual(Double(blues), 1100, accuracy: 40)
        XCTAssertEqual(Double(reds), 1000, accuracy: 40)
    }

    /// THE UNGATED-SAFETY PIN: the identical tree without the
    /// gap-decoration longhands paints ZERO rule ink. Every component in
    /// the committed baseline corpus is in this state, which is why
    /// wiring the hook into the flex container cannot move a baseline.
    @MainActor
    func testUndecoratedFlexContainerPaintsNoRuleInk() throws {
        let img = try render(try container(decorated: false))
        XCTAssertEqual(count(img, red), 0)
        XCTAssertEqual(count(img, blue), 0)
    }
}

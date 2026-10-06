//
//  MulticolSpannerHoistRasterTests.swift
//  Wave 52 (lane L3 · failure-ink, fix F3) — the RASTER pin for the
//  spanner containing-block hoist: the wave51-fix document (payloads) of
//  css-multicol/abspos-containing-block-outside-spanner rendered through a
//  replica of the composed capture canvas (the FixedHoistTests pattern:
//  358 content box, 16px frame, 390 wide, 600 min height, white, canvas-
//  root FixedHoistOverlay inset to the ICB) at scale 1.
//
//  What the frozen ref shows (tools/wpt/refs/9b5435e5…/white-black-ink-
//  font-lh-imgpad-htmlpins/css-multicol/abspos-containing-block-outside-
//  spanner.png): two GREEN 100×100 squares at the initial containing
//  block's top-left (16,16) and bottom-right corners, black paragraph text,
//  NO red. What wave51-fix iOS painted: the RED top-left probe uncovered
//  (f 0.9553, 20 000 red px) because the relpos column item claimed the
//  spanner's abspos children. Device-free evidence for the predicted flip;
//  the gate decides the cell.
//
//  Payloads: tools/titan/runs/wave51-fix/sections/css-multicol/per-test-ir/
//  wpt__css-multicol__abspos-containing-block-outside-spanner.json — all
//  nine roots with VERBATIM property payloads (empty 0×20 divs, the
//  paragraph, the two RED root-level probes, the multicol subtree); ids are
//  shortened and `meta` (sourceTag br/p, role line-break) is omitted, so the
//  paragraph's UA-margin path is not exercised — the replica sits 32px
//  higher than the real canvas, which moves neither ICB-corner assertion.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class MulticolSpannerHoistRasterTests: XCTestCase {

    /// Decode wire-shaped JSON — the production decode path.
    private func component(_ json: String) throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8))
    }

    /// One 100×100 inset-anchored probe (verbatim shape; color = red/green).
    private func probe(_ id: String, _ insets: String, green: Bool) -> String {
        let color = green
            ? #"{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}"#
            : #"{"srgb":{"r":1,"g":0,"b":0},"original":"red"}"#
        return #"{"id":"\#(id)","name":"div","properties":[{"type":"Position","data":"ABSOLUTE"},\#(insets),{"type":"BackgroundColor","data":\#(color)},{"type":"Height","data":{"type":"length","px":100}},{"type":"Width","data":{"type":"length","px":100}}]}"#
    }

    /// The nine roots of the test document, verbatim in shape and order.
    private func documentRoots() throws -> [IRComponent] {
        let empty = #"{"id":"e%d","name":"div","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":20}}]}"#
        let tl = #"{"type":"Top","data":{"px":0}},{"type":"Left","data":{"px":0}}"#
        let br = #"{"type":"Bottom","data":{"px":0}},{"type":"Right","data":{"px":0}}"#
        var roots: [IRComponent] = []
        for i in 0..<5 { roots.append(try component(String(format: empty, i))) }
        roots.append(try component(#"{"id":"p","name":"p","properties":[],"text":"There should be no red."}"#))
        roots.append(try component(probe("red-tl", tl, green: false)))
        roots.append(try component(probe("red-br", br, green: false)))
        roots.append(try component(#"""
        {"id":"multicol","name":"div","properties":[{"type":"ColumnCount","data":3},{"type":"ColumnGap","data":{"type":"length","original":{"v":1,"u":"EM"}}},{"type":"Width","data":{"type":"length","original":{"v":20,"u":"EM"}}}],
         "children":[{"id":"relpos","name":"div","properties":[{"type":"Position","data":"RELATIVE"}],
           "children":[{"id":"spanner","name":"div","properties":[{"type":"ColumnSpan","data":"ALL"},{"type":"Height","data":{"type":"length","px":50}}],
             "children":[\#(probe("green-tl", tl, green: true)),\#(probe("green-br", br, green: true))]}]}]}
        """#))
        return roots
    }

    /// The composed-canvas replica (FixedHoistTests.renderComposed, verbatim
    /// frame chain) — RGBA8 buffer at scale 1.
    @MainActor
    private func renderComposed(_ roots: [IRComponent]) throws -> (px: [UInt8], w: Int, h: Int) {
        let split = FixedHoist.split(roots: roots)
        let view = VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(split.flow.enumerated()), id: \.offset) { _, root in
                if FixedHoist.rendersInFlowAsStaticPosition(root) {
                    ComponentHost(component: root).modifier(StaticPositionAnchor())
                } else {
                    ComponentHost(component: root)
                }
            }
        }
        .frame(maxWidth: 390 - 32, alignment: .topLeading)
        .padding(WPTCanvas.canvasFramePx)
        .frame(minWidth: 390, maxWidth: 390, minHeight: 600, alignment: .topLeading)
        .fixedSize(horizontal: false, vertical: true)
        .background(Color.white)
        .overlay(alignment: .topLeading) {
            if !split.hoisted.isEmpty {
                FixedHoistOverlay(components: split.hoisted, canvasFrame: WPTCanvas.canvasFramePx)
            }
        }
        .environment(\.styleViewport, StyleViewport(width: 390, height: 844, rootContainingBlock: 358))
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

    /// RGB triple at a raster point.
    private func rgb(_ img: (px: [UInt8], w: Int, h: Int), _ x: Int, _ y: Int) -> (r: UInt8, g: UInt8, b: UInt8) {
        let i = (y * img.w + x) * 4
        return (img.px[i], img.px[i + 1], img.px[i + 2])
    }
    /// CSS `green` (0,128,0) with raster tolerance.
    private func isGreen(_ c: (r: UInt8, g: UInt8, b: UInt8)) -> Bool { c.r < 40 && c.g > 90 && c.g < 170 && c.b < 40 }
    /// CSS `red` (255,0,0) with raster tolerance — the failure ink.
    private func isRed(_ c: (r: UInt8, g: UInt8, b: UInt8)) -> Bool { c.r > 200 && c.g < 60 && c.b < 60 }

    /// The top-left probe: green must cover red at the ICB corner (16,16).
    @MainActor
    func testTopLeftGreenProbeCoversTheRedOneAtTheIcbCorner() throws {
        let img = try renderComposed(try documentRoots())
        XCTAssertEqual(img.w, 390)
        // Centre and two corners of the 100×100 square at (16,16)-(115,115).
        for (x, y) in [(66, 66), (20, 20), (110, 110)] {
            XCTAssertTrue(isGreen(rgb(img, x, y)),
                          "(\(x),\(y)) should be the hoisted green probe, got \(rgb(img, x, y)) — the relpos column item must not claim the spanner's abspos child (css-multicol-1 §6.1)")
        }
    }

    /// The bottom-right probe: `bottom:0; right:0` must resolve against the
    /// ICB (the brief's falsifier for the T3 flip), covering the red twin.
    @MainActor
    func testBottomRightGreenProbeCoversTheRedOneAtTheIcbCorner() throws {
        let img = try renderComposed(try documentRoots())
        XCTAssertGreaterThanOrEqual(img.h, 600)
        // The ICB's bottom-right corner is (w-16, h-16); the square spans 100px up-left of it.
        for (dx, dy) in [(50, 50), (5, 5), (95, 95)] {
            let x = img.w - 16 - dx, y = img.h - 16 - dy
            XCTAssertTrue(isGreen(rgb(img, x, y)),
                          "(\(x),\(y)) should be the hoisted green probe, got \(rgb(img, x, y)) — bottom/right must resolve against the ICB, not the padded canvas or the column item")
        }
    }

    /// No failure ink anywhere: both red probes are fully covered (the ref
    /// has zero red pixels; wave51-fix iOS had 20 000).
    @MainActor
    func testNoRedFailureInkSurvivesAnywhere() throws {
        let img = try renderComposed(try documentRoots())
        var red = 0
        for y in 0..<img.h { for x in 0..<img.w where isRed(rgb(img, x, y)) { red += 1 } }
        XCTAssertEqual(red, 0, "red failure ink still visible (\(red) px) — a probe is uncovered")
    }
}

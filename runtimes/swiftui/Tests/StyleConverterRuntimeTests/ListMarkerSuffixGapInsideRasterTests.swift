//
//  ListMarkerSuffixGapInsideRasterTests.swift
//  StyleConverterRuntimeTests — wave 55, lane L5 (U2i-ios) — the SEAM pin.
//  SHIPS INSIDE seam-3.patch (tools/titan/results/wave55-counter-suffix-
//  rows/), never on its own: it renders through ComponentRenderer's
//  `markerPlacement` HStack branch, so it is red without the seam by design.
//
//  ## What it pins
//  The inside-marker HStack spacing is `ListMarkerSuffixGap.points(
//  forMarker:)`: an inside `、` marker pushes its item's text along with
//  NO stand-in gap (css-counter-styles-3 §6.1; css-lists-3 §3.5 makes the
//  inside marker the item's first inline box), every other marker keeps
//  4 pt. Differential: the VERBATIM css3-counter-styles-005 list
//  (`…-005__1` font-size 25 px, `…-005__1__0` `<ol>` padding-left 8em +
//  `list-style-position: inside`, item `…__1__0__0` text "一、" baked "一、"
//  — tools/titan/runs/wave54-final/sections/css-counter-styles/per-test-ir/
//  wpt__css-counter-styles__cjk-decimal__css3-counter-styles-005.json) plus
//  ONE added control item whose marker is "一、" + U+200B (same ink, a
//  non-U+3001 tail). Measured wave54-final: item text x271 on both natives
//  vs the ref's x268.
//
//  Mutation EXECUTED by the lane (bracketed in the lane note): with
//  ComponentRenderer.swift restored to the pre-seam bytes both rows' text
//  starts at the same x and this goes red.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class ListMarkerSuffixGapInsideRasterTests: XCTestCase {

    /// The verbatim -005 list + one control row.
    private func insideList() throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"wpt__css-counter-styles__cjk-decimal__css3-counter-styles-005__1-381",
         "name":"wpt__css-counter-styles__cjk-decimal__css3-counter-styles-005__1",
         "properties":[{"type":"FontSize","data":{"px":25,"original":{"type":"length","px":25}}}],
         "meta":{"lang":"en"},
         "children":[
          {"id":"cjk-decimal__css3-counter-styles-005__1__0-382","name":"cjk-decimal__css3-counter-styles-005__1__0",
           "properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},
             {"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},
             {"type":"PaddingLeft","data":{"original":{"v":8,"u":"EM"}}},
             {"type":"ListStylePosition","data":"INSIDE"}],
           "meta":{"sourceTag":"ol","lang":"en"},
           "children":[
            {"id":"cjk-decimal__css3-counter-styles-005__1__0__0-383","name":"cjk-decimal__css3-counter-styles-005__1__0__0",
             "properties":[{"type":"ListStyleType","data":"cjk-decimal"}],"text":"一、",
             "meta":{"sourceTag":"li","role":"ws-after","markerText":"一、","lang":"en"}},
            {"id":"control-1","name":"control",
             "properties":[{"type":"ListStyleType","data":"cjk-decimal"}],"text":"一、",
             "meta":{"sourceTag":"li","markerText":"一、\u{200B}","lang":"en"}}
           ]}
         ]}
        """.utf8))
    }

    /// Composed capture geometry (the sibling raster suites' plumbing).
    @MainActor
    private func render(_ comp: IRComponent) throws -> (px: [UInt8], w: Int, h: Int) {
        let view = VStack(alignment: .leading, spacing: 0) { ComponentHost(component: comp) }
            .frame(width: 358, alignment: .topLeading)
            .padding(16)
            .frame(width: 390, height: 140, alignment: .topLeading)
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
        return (buf, cg.width, cg.height)
    }

    /// Per ink band: the inked column groups (a ≥ 6 px blank run splits) —
    /// the marker "一、" and the item text "一、" are two groups.
    private func bands(_ img: (px: [UInt8], w: Int, h: Int)) -> [[(lo: Int, hi: Int)]] {
        var out: [[(Int, Int)]] = [], cols = Set<Int>()
        func close() {
            var g: [(Int, Int)] = []
            for x in cols.sorted() {
                if let l = g.last, x - l.1 < 6 { g[g.count - 1] = (l.0, x) } else { g.append((x, x)) }
            }
            out.append(g); cols = []
        }
        for y in 0..<img.h {
            var any = false
            for x in 0..<img.w {
                let i = (y * img.w + x) * 4
                if img.px[i] < 160 && img.px[i + 1] < 160 && img.px[i + 2] < 160 { cols.insert(x); any = true }
            }
            if !any && !cols.isEmpty { close() }
        }
        if !cols.isEmpty { close() }
        return out
    }

    /// The U+3001 row's text starts 4 pt (the stand-in) LEFT of the
    /// control row's; both markers start at the content edge.
    @MainActor
    func testInsideIdeographicCommaMarkerTakesNoGap() throws {
        // Each row paints `一` (a wide stroke) above the low `、`, so blank
        // rows split a line into a `一` band and a `、` band: keep the `一`
        // bands — two groups (marker, item text), the first ≥ 15 px wide.
        let b = bands(try render(try insideList())).filter { $0.count >= 2 && $0[0].hi - $0[0].lo >= 15 }
        // Logged so the lane note can cite the measured columns verbatim.
        print("ListMarkerSuffixGapInsideRasterTests bands: \(b)")
        guard b.count == 2 else { return XCTFail("expected the two `一` bands, got \(b)") }
        // The marker is the first group, the item text the last.
        XCTAssertEqual(b[0][0].lo, b[1][0].lo, "markers must start at one x: \(b)")
        XCTAssertEqual(b[1].last!.lo - b[0].last!.lo, Int(ListMarkerRow.gapPt), "rows \(b)")
    }
}

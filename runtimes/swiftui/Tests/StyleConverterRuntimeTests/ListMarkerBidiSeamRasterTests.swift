//
//  ListMarkerBidiSeamRasterTests.swift
//  StyleConverterRuntimeTests — wave 55, lane L5 (U1-ios) — the SEAM pin.
//  SHIPS INSIDE seam-1.patch (tools/titan/results/wave55-counter-suffix-
//  rows/), never on its own: it renders through ComponentRenderer's
//  `markerPlacement`, so it is red on a tree without the seam by design
//  (the wave-52 ListMarkerOutsideHangRasterTests precedent).
//
//  ## What it pins
//  The renderer hands the outside hang's marker Text the ISOLATED string
//  (ListMarkerBidi.painted with the item's direction), so the VERBATIM
//  counter-suffix second `<ol>` (`counter-suffix__0__1`, `list-style-type:
//  hebrew`, ltr; items `__0__1__0` "foo" / `__0__1__1` "bar" baked "א." /
//  "ב." — tools/titan/runs/wave54-final/sections/css-counter-styles/
//  per-test-ir/wpt__css-counter-styles__counter-suffix.json) paints the
//  period AFTER the letter, as the ref does (wave54-final ref row 3: letter
//  x46-53, period x57; iOS: period x48, letter x51-57). The only change to
//  the wire payload is the root font size (32 px instead of the
//  document's 16 px) so the two glyphs separate by more than a pixel.
//
//  Mutation EXECUTED by the lane (bracketed in the lane note): with
//  ComponentRenderer.swift restored to the pre-seam bytes this goes red.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class ListMarkerBidiSeamRasterTests: XCTestCase {

    /// The counter-suffix hebrew list (property byte-shapes verbatim).
    private func hebrewList() throws -> IRComponent {
        // The `<li>` box model the wire carries (all zero) + line-height 150%.
        let liProps = """
        [{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},
         {"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},
         {"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},
         {"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},
         {"type":"LineHeight","data":{"multiplier":1.5,"original":{"type":"percentage","value":150}}}]
        """
        // Decoded through the real IR decoder (children form).
        return try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"root","name":"root",
         "properties":[{"type":"FontSize","data":{"px":32.0,"original":{"type":"length","px":32.0}}}],
         "children":[
          {"id":"counter-suffix__0__1-614","name":"counter-suffix__0__1",
           "properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},
             {"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},
             {"type":"PaddingTop","data":{"px":0}},
             {"type":"PaddingRight","data":{"original":{"v":3,"u":"EM"}}},
             {"type":"PaddingBottom","data":{"px":0}},
             {"type":"PaddingLeft","data":{"original":{"v":3,"u":"EM"}}},
             {"type":"LineHeight","data":{"multiplier":1.5,"original":{"type":"percentage","value":150}}},
             {"type":"ListStyleType","data":"hebrew"}],
           "meta":{"sourceTag":"ol","role":"ws-after"},
           "children":[
            {"id":"counter-suffix__0__1__0-615","name":"counter-suffix__0__1__0","text":"foo",
             "properties":\(liProps),"meta":{"sourceTag":"li","markerText":"א."}},
            {"id":"counter-suffix__0__1__1-616","name":"counter-suffix__0__1__1","text":"bar",
             "properties":\(liProps),"meta":{"sourceTag":"li","markerText":"ב."}}
           ]}
         ]}
        """.utf8))
    }

    /// The composed capture geometry (ListMarkerOutsideHangRasterTests'
    /// plumbing): a 358 px column in the 16 px canvas frame, white, scale 1.
    @MainActor
    private func render(_ comp: IRComponent) throws -> (px: [UInt8], w: Int, h: Int) {
        let view = VStack(alignment: .leading, spacing: 0) { ComponentHost(component: comp) }
            .frame(width: 358, alignment: .topLeading)
            .padding(16)
            .frame(width: 390, height: 160, alignment: .topLeading)
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

    /// Ink column groups LEFT of the content edge (16 + 3 em = 112) within
    /// the first ink band — the marker's glyphs (any blank column splits).
    private func firstRowMarkerGroups(_ img: (px: [UInt8], w: Int, h: Int)) -> [(lo: Int, hi: Int, ink: Int)] {
        var col = [Int](repeating: 0, count: img.w), started = false
        for y in 0..<img.h {
            var any = false
            for x in 0..<img.w {
                let i = (y * img.w + x) * 4
                if img.px[i] < 128 && img.px[i + 1] < 128 && img.px[i + 2] < 128 { col[x] += 1; any = true }
            }
            // Stop at the first blank row after the first band (row 1 only).
            if !any { if started { break } else { continue } }
            started = true
        }
        var groups: [(lo: Int, hi: Int, ink: Int)] = []
        for x in 0..<min(112, img.w) where col[x] > 0 {
            if let g = groups.last, x == g.hi + 1 { groups[groups.count - 1] = (g.lo, x, g.ink + col[x]) }
            else { groups.append((x, x, col[x])) }
        }
        return groups
    }

    /// Row 1 reads `א.` (period after the letter) — the ref's order.
    @MainActor
    func testHebrewMarkerPaintsThePeriodAfterTheLetter() throws {
        let groups = firstRowMarkerGroups(try render(try hebrewList()))
        // Logged so the lane note can cite the measured columns verbatim.
        print("ListMarkerBidiSeamRasterTests row-1 marker groups: \(groups)")
        guard groups.count >= 2 else { return XCTFail("row 1 marker rendered as \(groups)") }
        // The period is the smallest ink blob, the letter the largest.
        let period = groups.min { $0.ink < $1.ink }!, letter = groups.max { $0.ink < $1.ink }!
        XCTAssertGreaterThan(period.lo, letter.hi, "period painted before the letter: \(groups)")
    }
}

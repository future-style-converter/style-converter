//
//  ListMarkerOutsideHangRasterTests.swift
//  Wave 52, lane L6 (T5) — the RASTER pin for the `outside` marker hang.
//  SHIPS INSIDE seam-2.patch (tools/titan/results/wave52-counters-and-
//  lists/), never on its own: it renders through ComponentRenderer's
//  `markerPlacement`, so it is red on a tree without the seam by design.
//
//  ## What it pins
//  css-lists-3 §3.5: an `outside` ::marker hangs in the item's margin
//  area, before its start edge; the item's content edge does not move.
//  The pure geometry (ListMarkerOutsideHangTests) cannot see the renderer
//  wiring, so this renders the VERBATIM wave51-fix counter-suffix first
//  `<ol>` (per-test IR components `counter-suffix__0` (the 10em wrapper),
//  `__0__0` (the `<ol class="dec">`, padding 0 3em) and its `<li>`s
//  `__0__0__0` "foo" / `__0__0__1` "bar", baked "1." / "2.") and reads the
//  first row's ink columns.
//
//  ## The measured numbers (wave51-fix, pngjs over the run PNGs)
//  ref row 1: marker ink x 46–58, text ink x 64–87 (the `<ol>` content
//  edge is 16 canvas + 48 padding = 64). iOS today (HStack): marker
//  x 64–73, text x 80–103 — every item displaced by markerWidth + gap.
//
//  MUTATION PROOF (EXECUTED 2026-10-05 while verifying seam-2.patch, lane
//  L6; log tools/titan/results/wave52-counters-and-lists/seam-2.verify.log):
//  with ComponentRenderer.swift restored to HEAD (the HStack placement —
//  the seam's `hangsOutside` branch absent) this test went RED on both
//  assertions, groups [(66, 69), (74, 75), (82, 103)]: text at x 82, the
//  marker at 66–75 INSIDE the content edge. With the seam applied, green.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class ListMarkerOutsideHangRasterTests: XCTestCase {

    /// The counter-suffix first list, nested in the decoder's children
    /// form; property byte-shapes verbatim from the wave51-fix per-test IR.
    /// The extra root only pins the 16px font the em paddings resolve on.
    private func counterSuffixDecimal() throws -> IRComponent {
        // The `<li>` box model the wire carries (all zero) + line-height 150%.
        let liProps = """
        [{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},
         {"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},
         {"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},
         {"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},
         {"type":"LineHeight","data":{"multiplier":1.5,"original":{"type":"percentage","value":150}}}]
        """
        return try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"root","name":"root",
         "properties":[{"type":"FontSize","data":{"px":16.0,"original":{"type":"length","px":16.0}}}],
         "children":[
          {"id":"counter-suffix__0-610","name":"counter-suffix__0",
           "properties":[{"type":"Width","data":{"type":"length","original":{"v":10,"u":"EM"}}}],
           "children":[
            {"id":"counter-suffix__0__0-611","name":"counter-suffix__0__0",
             "properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},
               {"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}},
               {"type":"PaddingTop","data":{"px":0}},
               {"type":"PaddingRight","data":{"original":{"v":3,"u":"EM"}}},
               {"type":"PaddingBottom","data":{"px":0}},
               {"type":"PaddingLeft","data":{"original":{"v":3,"u":"EM"}}},
               {"type":"LineHeight","data":{"multiplier":1.5,"original":{"type":"percentage","value":150}}},
               {"type":"ListStyleType","data":"decimal"}],
             "meta":{"sourceTag":"ol","role":"ws-after"},
             "children":[
              {"id":"counter-suffix__0__0__0-612","name":"counter-suffix__0__0__0","text":"foo",
               "properties":\(liProps),"meta":{"sourceTag":"li","markerText":"1."}},
              {"id":"counter-suffix__0__0__1-613","name":"counter-suffix__0__0__1","text":"bar",
               "properties":\(liProps),"meta":{"sourceTag":"li","markerText":"2."}}
             ]}
           ]}
         ]}
        """.utf8))
    }

    /// The composed capture geometry (ListMarkerInFlowItemRasterTests'
    /// plumbing): a 358px column in the 16px canvas frame, 390 wide, white,
    /// scale 1 — so x columns compare directly with the ref PNG's.
    @MainActor
    private func render(_ comp: IRComponent) throws -> (px: [UInt8], w: Int, h: Int) {
        let view = VStack(alignment: .leading, spacing: 0) { ComponentHost(component: comp) }
            .frame(width: 358, alignment: .topLeading)
            .padding(16)
            .frame(width: 390, height: 120, alignment: .topLeading)
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

    /// The first ink band's column groups (gap > 3px splits a group) — a
    /// hung marker and its text are two groups either side of x 64.
    private func firstRowGroups(_ img: (px: [UInt8], w: Int, h: Int)) -> [(Int, Int)] {
        var inked = Set<Int>(), started = false
        for y in 0..<img.h {
            var row: [Int] = []
            for x in 0..<img.w {
                let i = (y * img.w + x) * 4
                if img.px[i] < 128 && img.px[i + 1] < 128 && img.px[i + 2] < 128 { row.append(x) }
            }
            if row.isEmpty { if started { break } else { continue } }
            started = true
            inked.formUnion(row)
        }
        let xs = inked.sorted()
        guard var start = xs.first else { return [] }
        var prev = start, groups: [(Int, Int)] = []
        for x in xs.dropFirst() {
            if x > prev + 3 { groups.append((start, prev)); start = x }
            prev = x
        }
        groups.append((start, prev))
        return groups
    }

    /// The hang: the item's text starts at the `<ol>`'s content edge (64)
    /// and the marker ink ends before it, inside the 48px padding.
    @MainActor
    func testOutsideMarkerHangsAndTheItemContentEdgeDoesNotMove() throws {
        let groups = firstRowGroups(try render(try counterSuffixDecimal()))
        // Logged so the lane note can cite the measured columns verbatim.
        print("ListMarkerOutsideHangRasterTests row-1 groups: \(groups)")
        guard groups.count >= 2 else {
            return XCTFail("row 1 rendered as \(groups) — expected marker + text groups")
        }
        // The LAST group is the item's text ("foo" has no inner gap > 3px);
        // every group before it is marker ink ("1" and "." can split).
        let text = groups[groups.count - 1], markerGroups = groups.dropLast()
        // Content edge unchanged: 16 canvas + 3em = 48 → 64 (+ the first
        // glyph's side bearing). The HStack put it at 82 (measured).
        XCTAssertEqual(Double(text.0), 65.0, accuracy: 3.0,
            "item text starts at x=\(text.0), not at the content edge 64 — groups \(groups)")
        // The marker hangs BEFORE the content edge (ref ink 46–58) …
        XCTAssertTrue(markerGroups.allSatisfy { $0.1 < 64 },
            "marker ink must end before x 64 — groups \(groups)")
        // … and stays inside the `<ol>`'s padding, never off the canvas.
        XCTAssertGreaterThanOrEqual(markerGroups.first?.0 ?? -1, 16,
            "marker ink left of the canvas frame — groups \(groups)")
    }
}

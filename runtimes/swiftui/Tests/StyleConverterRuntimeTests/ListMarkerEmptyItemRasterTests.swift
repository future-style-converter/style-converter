//
//  ListMarkerEmptyItemRasterTests.swift
//  Wave 52, lane L6 (T7's native half) — the RASTER pin for the empty-item
//  rule. SHIPS INSIDE seam-4.patch (tools/titan/results/wave52-counters-
//  and-lists/), never on its own: it renders through ComponentRenderer's
//  marker branch, so it is red on a tree without the seam by design.
//
//  ## What it renders
//  The VERBATIM wave51-fix cssom-pad-setter-invalid `<ol>` (per-test IR
//  component `…cssom-pad-setter-invalid__0`: ListStyleType "foo",
//  ListStylePosition INSIDE) and its three `<li>` as they reach the wire
//  AFTER lane L5's F-E (the 100×100 placeholder pair gone — an empty bag)
//  carrying the markers lane L6's T7 bake now stamps ("001." "002." "003.").
//  The ref paints three rows at a 20 px pitch (y 36–47, 56–67, 76–87).
//
//  MUTATION PROOF (EXECUTED 2026-10-05 while verifying seam-4.patch, lane
//  L6; log seam-4.verify.log): with ComponentRenderer.swift at HEAD (the
//  zero-size overlay for every inside item without text) the three markers
//  collapsed onto ONE ink band [(16, 28)] and this test went red; with the
//  seam, three bands [(16, 27), (32, 43), (48, 59)] (before the 16px root
//  pin; re-measured with it in seam-4.verify.log).
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class ListMarkerEmptyItemRasterTests: XCTestCase {

    /// The post-F-E + T7 wire, nested in the decoder's children form, under
    /// a root that only pins the browser-default 16px font (the cssom page
    /// declares none) so the marker line is the one a capture paints.
    private func cssomPadSetterInvalid() throws -> IRComponent {
        let lis = ["001.", "002.", "003."].enumerated().map { (i, m) -> String in
            "{\"id\":\"cssom__cssom-pad-setter-invalid__0__\(i)\",\"name\":\"li\(i)\",\"properties\":[],"
                + "\"meta\":{\"sourceTag\":\"li\",\"markerText\":\"\(m)\"}}"
        }
        return try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"root","name":"root",
         "properties":[{"type":"FontSize","data":{"px":16.0,"original":{"type":"length","px":16.0}}}],
         "children":[
          {"id":"wpt__css-counter-styles__cssom__cssom-pad-setter-invalid__0-673",
           "name":"wpt__css-counter-styles__cssom__cssom-pad-setter-invalid__0",
           "properties":[{"type":"ListStyleType","data":"foo"},{"type":"ListStylePosition","data":"INSIDE"}],
           "meta":{"sourceTag":"ol"},"children":[\(lis.joined(separator: ","))]}]}
        """.utf8))
    }

    /// Ink bands (rows with a pixel darker than 128 on every channel) of the
    /// composed capture geometry (ListMarkerInFlowItemRasterTests' plumbing).
    @MainActor
    private func bands(_ comp: IRComponent) throws -> [(top: Int, bottom: Int)] {
        let view = VStack(alignment: .leading, spacing: 0) { ComponentHost(component: comp) }
            .frame(width: 358, alignment: .topLeading)
            .padding(16)
            .frame(width: 390, height: 200, alignment: .topLeading)
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
        var out: [(top: Int, bottom: Int)] = [], top: Int? = nil
        for y in 0..<cg.height {
            // A row is inked when any pixel is dark on all three channels.
            let inked = (0..<cg.width).contains { x in
                let i = (y * cg.width + x) * 4
                return buf[i] < 128 && buf[i + 1] < 128 && buf[i + 2] < 128
            }
            if inked { if top == nil { top = y } } else if let t = top { out.append((t, y - 1)); top = nil }
        }
        if let t = top { out.append((t, cg.height - 1)) }
        return out
    }

    /// Each empty item is one marker line tall: three rows, none overlapping.
    @MainActor
    func testEmptyInsideItemsStackOneMarkerLineEach() throws {
        let b = try bands(try cssomPadSetterInvalid())
        // Logged so the lane note can cite the measured rows verbatim.
        print("ListMarkerEmptyItemRasterTests bands: \(b)")
        XCTAssertEqual(b.count, 3, "expected one marker row per empty item, got \(b)")
        guard b.count == 3 else { return }
        // The fix is "one line box per item", so the pin is NON-OVERLAP: each
        // row starts below the previous row's ink. The pitch itself is the
        // marker's own line box (measured 16 px here against the ref's 20 —
        // the residual is stated in the lane note, not pinned as correct).
        for (a, c) in zip(b, b.dropFirst()) {
            XCTAssertGreaterThan(c.top, a.bottom, "rows overlap — bands \(b)")
            XCTAssertGreaterThanOrEqual(c.top - a.top, 12, "row pitch \(c.top - a.top) — bands \(b)")
        }
    }
}

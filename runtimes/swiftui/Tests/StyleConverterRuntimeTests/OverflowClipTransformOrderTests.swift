//
//  OverflowClipTransformOrderTests.swift
//  Wave 50 (lane B8, BACKLOG queue 6(d)) — the raster pin for "iOS applies
//  the overflow clip OUTSIDE the transform".
//
//  THE RULE, both halves (the fixture's own _comment states it too):
//   * An element's OWN overflow clip is applied in its OWN coordinate
//     system and the transform then maps the already-clipped rendering into
//     the parent's space — css-overflow-3 §3 + css-transforms-1 §6 ("the
//     transform … is applied to the element's own coordinate system").
//     StyleBuilder therefore chains `.engineVisibility` INNER of
//     `.engineTransforms` (SwiftUI wraps later modifiers around earlier
//     ones, so inner = applied first).
//   * A DESCENDANT's transform, by contrast, happens before the ancestor's
//     clip: the child is painted (transform included) and the ancestor's
//     clip then cuts the result. That half is unaffected by the chain
//     order — the child sits inside the parent's content — and the second
//     test below is the guard that the reorder did not break it.
//
//  Evidence for the defect (wave49-final, frozen captures): css-backgrounds/
//  background-attachment-fixed-inside-transform-1 — `#outer` 300×700,
//  `rotate(45deg); overflow: hidden` — iOS ink bbox cols 216–389 (a strip
//  starting exactly at the element's un-rotated left frame edge) against web
//  13–389 and the frozen ref 16–373, at iOS ssim 0.9843 wptPass=true: a
//  visibly wrong PASS.
//
//  Payload: the converter's verbatim IR for the GATE-NET fixture
//  fixtures/combinations/radius-overflow-transform.json (`./gradlew
//  :converter:run --args="convert --from css --to ir -i <that fixture> -o
//  <dir>"`, committed converter @ 747b28e4), whitespace compacted, one
//  component per line. The fixture's `_expect` oracle carries the same two
//  numbers this file asserts (box [99,99] ±6 and [70,72] ±1).
//
//  Mutation-proven: moving `.engineVisibility(style.visibility)` back after
//  `.engineTransforms(style.transforms)` in StyleBuilder.applyStyle makes
//  testSelfRotateClipsInsideTheTransform fail with the 80×60 axis-aligned
//  rectangle (EXECUTED this wave: red bbox measured exactly 80×60 against the
//  99±6 pin, both axes); testAncestorClipStillAppliesAfterAChildTransform
//  stays green under the same mutation, which is exactly why it is here.
//

import Foundation
import SwiftUI
import XCTest
@testable import StyleConverterRuntime

final class OverflowClipTransformOrderTests: XCTestCase {

    // MARK: - Verbatim converter IR (flat v2 wire, slot-linked children)

    /// ROT_SelfRotate_ClippedChild: 80×60, `overflow:hidden`,
    /// `rotate(45deg)`, margin 24, with an oversized abspos red child that
    /// covers the whole box (so the visible shape IS the clip result).
    static let selfRotateIR = """
    {"irVersion":2,"minReaderVersion":2,"components":[
    {"id":"rot_selfrotate_clippedchild-007","name":"ROT_SelfRotate_ClippedChild","properties":[{"type":"Width","data":{"type":"length","px":80.0}},{"type":"Height","data":{"type":"length","px":60.0}},{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"},{"type":"Transform","data":{"type":"functions","list":[{"fn":"rotate","a":{"deg":45.0}}]}},{"type":"Position","data":"RELATIVE"},{"type":"MarginTop","data":{"px":24.0}},{"type":"MarginRight","data":{"px":24.0}},{"type":"MarginBottom","data":{"px":24.0}},{"type":"MarginLeft","data":{"px":24.0}}]},
    {"id":"bleed-008","name":"bleed","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Left","data":{"px":-20.0}},{"type":"Top","data":{"px":-20.0}},{"type":"Width","data":{"type":"length","px":120.0}},{"type":"Height","data":{"type":"length","px":100.0}},{"type":"BackgroundColor","data":{"srgb":{"r":0.9058823529411765,"g":0.2980392156862745,"b":0.23529411764705882},"original":"#e74c3c"}}],"slot":{"parent":"rot_selfrotate_clippedchild-007"}}
    ]}
    """

    /// ROT_SquareClip_TranslatedChild: 120×80 `overflow:hidden` parent, NO
    /// transform of its own; the abspos red child is translated +40px so the
    /// ancestor clip must cut it at the parent's right edge.
    static let squareClipIR = """
    {"irVersion":2,"minReaderVersion":2,"components":[
    {"id":"rot_squareclip_translatedchild-001","name":"ROT_SquareClip_TranslatedChild","properties":[{"type":"Width","data":{"type":"length","px":120.0}},{"type":"Height","data":{"type":"length","px":80.0}},{"type":"BackgroundColor","data":{"srgb":{"r":0.9450980392156862,"g":0.7686274509803922,"b":0.058823529411764705},"original":"#f1c40f"}},{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"},{"type":"Position","data":"RELATIVE"},{"type":"MarginTop","data":{"px":24.0}},{"type":"MarginRight","data":{"px":24.0}},{"type":"MarginBottom","data":{"px":24.0}},{"type":"MarginLeft","data":{"px":24.0}}]},
    {"id":"mover-002","name":"mover","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Left","data":{"px":10.0}},{"type":"Top","data":{"px":4.0}},{"type":"Width","data":{"type":"length","px":100.0}},{"type":"Height","data":{"type":"length","px":72.0}},{"type":"BackgroundColor","data":{"srgb":{"r":0.9058823529411765,"g":0.2980392156862745,"b":0.23529411764705882},"original":"#e74c3c"}},{"type":"Transform","data":{"type":"functions","list":[{"fn":"translate","x":{"px":40.0},"y":{"px":0.0}}]}}],"slot":{"parent":"rot_squareclip_translatedchild-001"}}
    ]}
    """

    // MARK: - Raster plumbing (Backface3DSubtreeRasterTests pattern)

    /// Decode through the runtime's real loader; the decoder composes the
    /// slot list, so the root component already carries its children.
    private func root(_ ir: String) throws -> IRComponent {
        let doc = try JSONDecoder().decode(IRDocument.self, from: Data(ir.utf8))
        return try XCTUnwrap(doc.components.first { $0.slot == nil })
    }

    /// Rasterise at scale 1 (one buffer pixel per logical point) and read
    /// the bytes back through a CGContext redraw, because ImageRenderer's
    /// native buffer layout is not guaranteed.
    @MainActor
    private func raster<V: View>(_ view: V) throws -> (px: [UInt8], w: Int, h: Int) {
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

    /// White 200×200 top-leading stage — the element's margin box pins to
    /// (0,0), so every expectation below is in fixture coordinates + 24.
    @MainActor
    private func stage(_ comp: IRComponent) throws -> (px: [UInt8], w: Int, h: Int) {
        try raster(ComponentRenderer(component: comp)
            .frame(width: 200, height: 200, alignment: .topLeading)
            .background(Color.white))
    }

    /// Bounding box of the fixture red (#e74c3c = 231,76,60). The tolerance
    /// band is wide on purpose: a rotated diamond's tips are heavily
    /// antialiased, which is the same reason the fixture's oracle carries
    /// boxTolerance 6 (its `_expect.note` shows the arithmetic).
    private func redBox(_ img: (px: [UInt8], w: Int, h: Int))
        -> (w: Int, h: Int, minX: Int, minY: Int)? {
        var minX = Int.max, minY = Int.max, maxX = -1, maxY = -1
        for y in 0..<img.h {
            for x in 0..<img.w {
                let i = (y * img.w + x) * 4
                let r = Int(img.px[i]), g = Int(img.px[i + 1]), b = Int(img.px[i + 2])
                // ±40 per channel around (231,76,60): accepts the fill and
                // its near-edge blend, rejects the yellow parent (241,196,15)
                // and the white stage.
                guard abs(r - 231) < 40, abs(g - 76) < 40, abs(b - 60) < 40 else { continue }
                minX = min(minX, x); minY = min(minY, y)
                maxX = max(maxX, x); maxY = max(maxY, y)
            }
        }
        return maxX >= 0 ? (maxX - minX + 1, maxY - minY + 1, minX, minY) : nil
    }

    // MARK: - The 6(d) pin

    /// css-transforms-1 §6: the 80×60 content is clipped FIRST, then the
    /// whole element rotates — so the red silhouette is a diamond of
    /// (80+60)·cos45° = 98.99 in each axis. Clip-outside-transform (the
    /// defect) leaves the axis-aligned 80×60 rectangle instead.
    @MainActor
    func testSelfRotateClipsInsideTheTransform() throws {
        let box = try XCTUnwrap(redBox(try stage(try root(Self.selfRotateIR))),
                                "no red ink — the clipped child did not paint at all")
        XCTAssertEqual(box.w, 99, accuracy: 6,
                       "red width \(box.w): 80 means the clip ran OUTSIDE the transform")
        XCTAssertEqual(box.h, 99, accuracy: 6,
                       "red height \(box.h): 60 means the clip ran OUTSIDE the transform")
    }

    /// The other half of the rule, unchanged by the reorder: the parent has
    /// no transform of its own, and its clip must still cut the child AFTER
    /// the child's `translate(40px)` — red x[50,150] cut at the parent's
    /// right edge 120 → 70 wide, full 72 tall. A clip applied before the
    /// child's transform would carry the whole 100×72 out.
    @MainActor
    func testAncestorClipStillAppliesAfterAChildTransform() throws {
        let box = try XCTUnwrap(redBox(try stage(try root(Self.squareClipIR))),
                                "no red ink — the translated child did not paint")
        XCTAssertEqual(box.w, 70, accuracy: 2,
                       "red width \(box.w): 100 means the clip ran BEFORE the child transform")
        XCTAssertEqual(box.h, 72, accuracy: 2, "red height \(box.h) off the child's height")
        // Position pins the cut side: the child starts at parent x=50
        // (10 + 40) and the stage offsets by the 24px margin.
        XCTAssertEqual(box.minX, 74, accuracy: 2, "red left edge off the translated child's origin")
    }
}

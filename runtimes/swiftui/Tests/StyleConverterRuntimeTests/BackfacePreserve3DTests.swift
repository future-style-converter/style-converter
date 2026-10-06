//
//  BackfacePreserve3DTests.swift
//  StyleConverterRuntimeTests — wave 52, lane L4 (native-near-misses T5).
//
//  `backface-visibility: hidden` under `transform-style: preserve-3d`
//  (css-transforms-2 §10 + §4.1.2): the culled element's CHILDREN are their
//  own planes and keep painting; only the element's own box is the back
//  face. Two pins:
//    1. the pure decision (BackfaceCulling.decide) — a truth table over
//       the flag × facing × flattening mode;
//    2. a RASTER pin on the VERBATIM wave51-fix wire of
//       css-transforms/composited-under-rotateY-180deg-preserve-3d
//       (tools/titan/runs/wave51-fix/sections/css-transforms/per-test-ir/
//       wpt__css-transforms__composited-under-rotateY-180deg-preserve-3d.json),
//       rendered through the real ComponentRenderer on the composed-capture
//       stage (the Backface3DSubtreeRasterTests plumbing): the green
//       100×100 child must be PRESENT. The wave51-fix iOS capture had zero
//       green pixels (ios f 0.9565, a blank canvas).
//
//  MUTATION EXECUTED (2026-09-25, this lane): with BackfaceCulling.decide's
//  last line changed to `return .hideSubtree` (the pre-wave-52
//  opacity(0)-the-content behaviour restored for preserve-3d), the raster
//  pin FAILS with 0 green pixels and the truth-table pin FAILS on the
//  preserve-3d row; every FLAT row stays green. Bytes restored, sha-verified.
//

import XCTest
import SwiftUI
import QuartzCore
@testable import StyleConverterRuntime

final class BackfacePreserve3DTests: XCTestCase {

    // MARK: - Pin 1: the decision truth table

    /// Aggregate builder (the Wave19InkClampBackfaceTests pattern).
    private func rotY(_ deg: CGFloat, hidden: Bool = true, preserve3D: Bool = false) -> TransformsAggregate {
        var a = TransformsAggregate()
        if deg != 0 { a.functions = [.rotate(x: 0, y: 1, z: 0, deg: deg)] }
        a.backfaceHidden = hidden
        a.preserve3D = preserve3D
        a.touched = true
        return a
    }

    func testDecisionSplitsByTransformStyle() {
        let id = CATransform3DIdentity
        // FLAT + back-facing + hidden: the flattened subtree is the back face.
        XCTAssertEqual(BackfaceCulling.decide(agg: rotY(180), inherited: id), .hideSubtree)
        // PRESERVE_3D + back-facing + hidden: only the own face — the target row.
        XCTAssertEqual(BackfaceCulling.decide(agg: rotY(180, preserve3D: true), inherited: id), .cullOwnFace)
        // Front-facing planes are never culled, whatever the mode.
        XCTAssertEqual(BackfaceCulling.decide(agg: rotY(0), inherited: id), .none)
        XCTAssertEqual(BackfaceCulling.decide(agg: rotY(45, preserve3D: true), inherited: id), .none)
        // `visible` never culls, even back-facing under preserve-3d.
        XCTAssertEqual(BackfaceCulling.decide(agg: rotY(180, hidden: false, preserve3D: true), inherited: id), .none)
    }

    func testDecisionStillSeesTheAncestorContext() {
        // hidden-001's red face: own 180° inside a rotateY(45°) context →
        // accumulated 225° → back-facing → FLAT face → whole subtree hidden
        // (the wave-19 predicate is untouched; only the mode split is new).
        let ctx = CATransform3DMakeRotation(45 * .pi / 180, 0, 1, 0)
        XCTAssertEqual(BackfaceCulling.decide(agg: rotY(180), inherited: ctx), .hideSubtree)
        // The green face (own 0°) in the same context stays visible.
        XCTAssertEqual(BackfaceCulling.decide(agg: rotY(0), inherited: ctx), .none)
    }

    // MARK: - Pin 2: the verbatim wire paints its green child

    /// The full flat component list of the target test — decoded through
    /// IRDocument so the slot list is composed exactly as the harness does.
    private func preserve3dRoot() throws -> IRComponent {
        let doc = try JSONDecoder().decode(IRDocument.self, from: Data("""
        {"irVersion":2,"minReaderVersion":2,"components":[
          {"id":"wpt__css-transforms__composited-under-rotatey-180deg-preserve-3d__0-091",
           "name":"wpt__css-transforms__composited-under-rotateY-180deg-preserve-3d__0",
           "properties":[
             {"type":"Transform","data":{"type":"functions","list":[{"fn":"rotateY","a":{"deg":180}}]}},
             {"type":"Width","data":{"type":"length","px":100}},
             {"type":"BackfaceVisibility","data":"HIDDEN"},
             {"type":"TransformStyle","data":"PRESERVE_3D"}
           ]},
          {"id":"composited-under-rotatey-180deg-preserve-3d__0__0-092",
           "name":"composited-under-rotateY-180deg-preserve-3d__0__0",
           "properties":[
             {"type":"Width","data":{"type":"length","px":100}},
             {"type":"Height","data":{"type":"length","px":100}},
             {"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},
             {"type":"WillChange","data":[{"type":"property-name","name":"transform"}]}
           ],
           "slot":{"parent":"wpt__css-transforms__composited-under-rotatey-180deg-preserve-3d__0-091"}}
        ]}
        """.utf8))
        return try XCTUnwrap(doc.components.first)
    }

    /// Rasterise at scale 1 into RGBA8 (Backface3DSubtreeRasterTests plumbing).
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

    /// Green ink census + bounding box (CSS `green` = rgb(0,128,0)).
    private func greenStats(_ img: (px: [UInt8], w: Int, h: Int))
        -> (count: Int, box: (minX: Int, minY: Int, maxX: Int, maxY: Int)?) {
        var n = 0, minX = Int.max, minY = Int.max, maxX = -1, maxY = -1
        for y in 0..<img.h { for x in 0..<img.w {
            let i = (y * img.w + x) * 4
            let r = Int(img.px[i]), g = Int(img.px[i + 1]), b = Int(img.px[i + 2])
            if r < 100, g > 80, g < 180, b < 100 {
                n += 1; minX = min(minX, x); minY = min(minY, y); maxX = max(maxX, x); maxY = max(maxY, y)
            }
        } }
        return (n, maxX >= 0 ? (minX, minY, maxX, maxY) : nil)
    }

    @MainActor
    func testPreserve3dCulledParentStillPaintsItsGreenChild() throws {
        // The composed-capture stage: 240×240 white, root at (0,0), harness
        // environment — the path the wave51-fix capture blanked on.
        let img = try raster(
            ComponentRenderer(component: try preserve3dRoot())
                .frame(width: 240, height: 240, alignment: .topLeading)
                .background(Color.white)
                .environment(\.wptCaptureMode, true)
                .environment(\.wptBlockFlowFillWidth, 358))
        let s = greenStats(img)
        // 1. The child paints AT ALL — wave51-fix iOS had ZERO green pixels.
        let box = try XCTUnwrap(s.box, "no green ink — the preserve-3d child was culled with its parent again")
        // 2. rotateY(180°) about the 100-wide parent's centre mirrors the
        //    100×100 child onto its own footprint: the square stays at
        //    (0,0)-(99,99) (±2 px for edge antialiasing on the flattened quad).
        XCTAssertEqual(box.minX, 0, accuracy: 2)
        XCTAssertEqual(box.maxX, 99, accuracy: 2)
        XCTAssertEqual(box.minY, 0, accuracy: 2)
        XCTAssertEqual(box.maxY, 99, accuracy: 2)
        // 3. Coverage: the whole 100×100 square, not a hairline.
        XCTAssertGreaterThan(s.count, 9000, "green ink far below the 100×100 child area")
    }

    @MainActor
    func testFlatCulledParentStillHidesItsChild() throws {
        // Negative control on the SAME wire minus `transform-style`: the
        // subtree is flattened into the parent's back-facing plane and the
        // canvas must stay blank — today's behaviour for the 11 flat carriers.
        let doc = try JSONDecoder().decode(IRDocument.self, from: Data("""
        {"irVersion":2,"minReaderVersion":2,"components":[
          {"id":"flat-control-parent","name":"flat-control-parent",
           "properties":[
             {"type":"Transform","data":{"type":"functions","list":[{"fn":"rotateY","a":{"deg":180}}]}},
             {"type":"Width","data":{"type":"length","px":100}},
             {"type":"BackfaceVisibility","data":"HIDDEN"}
           ]},
          {"id":"flat-control-child","name":"flat-control-child",
           "properties":[
             {"type":"Width","data":{"type":"length","px":100}},
             {"type":"Height","data":{"type":"length","px":100}},
             {"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}
           ],
           "slot":{"parent":"flat-control-parent"}}
        ]}
        """.utf8))
        let img = try raster(
            ComponentRenderer(component: try XCTUnwrap(doc.components.first))
                .frame(width: 240, height: 240, alignment: .topLeading)
                .background(Color.white)
                .environment(\.wptCaptureMode, true)
                .environment(\.wptBlockFlowFillWidth, 358))
        XCTAssertEqual(greenStats(img).count, 0, "a FLAT culled parent must still hide its flattened subtree")
    }
}

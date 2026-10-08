//
//  FloatAvoidLayoutRasterTests.swift
//  Wave 53 (lane L4 · float-avoid) — the RASTER pin for FloatAvoidLayout and
//  its ComponentRenderer seam, shipped INSIDE tools/titan/results/
//  wave53-float-avoid/seam-2.patch with the branch it pins (so the tree never
//  carries a pin that is red by construction).
//
//  FloatAvoidPlanTests pins the pure gate + placement with h injected; this
//  file EXECUTES the real Layout through ComponentRenderer under the composed-
//  WPT capture environment (the AbsposFlexOverlayRasterTests stage) on the
//  verbatim wave52-ship IR (FloatAvoidPlanTests' literals), and asserts where
//  the ink lands. Mutation M6 (PLAN.md §2 L4), executed and logged in the lane
//  note: with the seam branch absent, 001's orange paints at rows 300-319 and
//  002's at rows 300-319 — every test here goes red.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class FloatAvoidLayoutRasterTests: XCTestCase {

    /// Composed root 1 of a contain test (root 0 is the instruction <p>): the 400px float container.
    private func container(_ doc: String) throws -> IRComponent {
        try JSONDecoder().decode(IRDocument.self, from: Data(doc.utf8)).components[1]
    }

    /// Rasterize at scale 1 on a 420×420 WHITE stage, top-leading: the container's border box sits at raster
    /// (0,0), so plan coordinates are raster coordinates. Redrawn into RGBA8 for format-stable reads.
    @MainActor
    private func render(_ comp: IRComponent) throws -> (px: [UInt8], w: Int) {
        let view = ComponentRenderer(component: comp)
            .frame(width: 420, height: 420, alignment: .topLeading)
            .background(Color.white)
            // The composed-WPT capture environment (captureComposedDocument): the seam is capture-gated.
            .environment(\.wptCaptureMode, true)
            .environment(\.wptBlockFlowFillWidth, 358)
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        let cg = try XCTUnwrap(renderer.cgImage, "ImageRenderer produced no image")
        let w = cg.width, h = cg.height
        var buf = [UInt8](repeating: 0, count: w * h * 4)
        let ctx = try XCTUnwrap(CGContext(data: &buf, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                                          space: CGColorSpaceCreateDeviceRGB(),
                                          bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        return (buf, w)
    }

    /// Is the pixel at (x, y) within ±24 of `t` (antialiasing tolerance, the precedent's)?
    private func `is`(_ img: (px: [UInt8], w: Int), _ x: Int, _ y: Int, _ t: (Int, Int, Int)) -> Bool {
        let i = (y * img.w + x) * 4
        return abs(Int(img.px[i]) - t.0) <= 24 && abs(Int(img.px[i + 1]) - t.1) <= 24 && abs(Int(img.px[i + 2]) - t.2) <= 24
    }

    private let orange = (255, 165, 0), blue = (0, 0, 255), white = (255, 255, 255)

    /// 001: floats at (200,0) (0,100) (100,200); the 0-wide contained BFC at (0,200), so its 200×20 orange
    /// inline-block paints rows 200-219 OVER float 3's left 100px (Appendix E step 7 above step 5).
    @MainActor
    func testContainInlineSizeBfcFloats001OrangeBesideFloat3() throws {
        let img = try render(try container(FloatAvoidPlanTests.P001))
        // The three floats where §9.5.1 puts them (right floats from the 400px container edge).
        XCTAssertTrue(`is`(img, 300, 50, blue), "float 1 missing at (300,50)")
        XCTAssertTrue(`is`(img, 100, 150, blue), "float 2 missing at (100,150)")
        XCTAssertTrue(`is`(img, 300, 250, blue), "float 3 missing at (300,250)")
        // The orange beside float 3's top: rows 200-219, x 0-199, overlapping float 3 from x100.
        XCTAssertTrue(`is`(img, 50, 205, orange), "orange missing at (50,205)")
        XCTAssertTrue(`is`(img, 150, 215, orange), "orange not painted over float 3 at (150,215)")
        // …and NOT at the frozen stacked position below every float (rows 300-319).
        XCTAssertTrue(`is`(img, 50, 310, white), "orange still stacked below the floats at (50,310)")
    }

    /// 002: the same floats; the 20px-tall contained BFC fits at (0,0), so its 300×20 orange paints rows 0-19
    /// over float 1's left 100px.
    @MainActor
    func testContainInlineSizeBfcFloats002OrangeBesideFloat1() throws {
        let img = try render(try container(FloatAvoidPlanTests.P002))
        XCTAssertTrue(`is`(img, 50, 10, orange), "orange missing at (50,10)")
        XCTAssertTrue(`is`(img, 250, 10, orange), "orange not painted over float 1 at (250,10)")
        XCTAssertTrue(`is`(img, 350, 10, blue), "float 1 missing right of the orange at (350,10)")
        XCTAssertTrue(`is`(img, 50, 310, white), "orange still stacked below the floats at (50,310)")
    }
}

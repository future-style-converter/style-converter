//
//  FlexNowrapRasterTests.swift
//  Wave 52, lane L10 — end-to-end ImageRenderer pins (WPT capture env,
//  scale 1) on the VERBATIM wave51-fix IR in FlexNowrapIR.swift. 002 and
//  027 need the lane's seam patch (tools/titan/results/
//  wave52-flex-nowrap-gaps/seam-1.patch: the static flex plan's percent
//  arm and the painted-bounds gap anchor) applied to ComponentRenderer.swift.
//
//  EXECUTED MUTATIONS (…/wave52-flex-nowrap-gaps/mutations.log, last line
//  per id — all four re-run on THIS class with seam-1 applied under the
//  lock; each restored byte-exact, sha-verified):
//   SW-R1 seam flexMainPlan percent arm removed          → testRaster002… RED
//   SW-R2 painted-anchor overload disabled (`if false`)  → testRaster027… RED
//   SW-R3 Layout passes `outers: []`                     → testRaster027… RED
//   SW-G1 MarginApplier drops the negative pull          → testRaster003… RED
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class FlexNowrapRasterTests: XCTestCase {

    /// RGBA8 raster of `comp` at stage offset (ox, 0), WPT capture env, scale 1.
    @MainActor
    private func raster(_ comp: IRComponent, ox: CGFloat, w: CGFloat, h: CGFloat)
        throws -> (px: [UInt8], w: Int, h: Int) {
        let view = ComponentRenderer(component: comp)
            .padding(.leading, ox)
            .frame(width: w, height: h, alignment: .topLeading)
            .background(Color.white)
            .environment(\.wptCaptureMode, true)
        let renderer = ImageRenderer(content: view); renderer.scale = 1
        let cg = try XCTUnwrap(renderer.cgImage, "ImageRenderer produced no image")
        var buf = [UInt8](repeating: 0, count: cg.width * cg.height * 4)
        let ctx = try XCTUnwrap(CGContext(
            data: &buf, width: cg.width, height: cg.height, bitsPerComponent: 8,
            bytesPerRow: cg.width * 4, space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: cg.width, height: cg.height))
        return (buf, cg.width, cg.height)
    }

    /// True when the pixel at (x, y) is within ±tol of (r, g, b).
    private func isColour(_ img: (px: [UInt8], w: Int, h: Int), _ x: Int, _ y: Int,
                          _ r: Int, _ g: Int, _ b: Int, tol: Int = 24) -> Bool {
        let i = (y * img.w + x) * 4
        return abs(Int(img.px[i]) - r) <= tol && abs(Int(img.px[i + 1]) - g) <= tol
            && abs(Int(img.px[i + 2]) - b) <= tol
    }

    /// 002 end to end: the 100×100 box is solid green — no red pixel (the
    /// cell's colour veto) and no green spilling past x 100.
    @MainActor
    func testRaster002PaintsTheGreenSquareWithoutRed() throws {
        let img = try raster(try FlexNowrapIR.flexRoot(FlexNowrapIR.doc002), ox: 0, w: 160, h: 140)
        var red = 0, green = 0
        for y in 0..<100 { for x in 0..<100 {
            if isColour(img, x, y, 255, 0, 0) { red += 1 }
            if isColour(img, x, y, 0, 128, 0) { green += 1 }
        } }
        XCTAssertEqual(red, 0, "red container background shows through")
        XCTAssertGreaterThanOrEqual(green, 9_800)
        // Nothing green past the container's right edge (x 101…110).
        for y in 10..<90 { XCTAssertFalse(isColour(img, 104, y, 0, 128, 0), "green overflow at y \(y)") }
    }

    /// 003 (M2's at-risk carrier, the regression guard): red item (order 1)
    /// at 0…100, green (order 2, margin-left −100) margin box at 100 → border
    /// box 0…100 on top. M2 cannot move it (explicit widths floor §9.7 at
    /// 100; the delta is the LAST item's) — a green square, no red.
    @MainActor
    func testRaster003StaysAGreenSquare() throws {
        let img = try raster(try FlexNowrapIR.flexRoot(FlexNowrapIR.doc003), ox: 0, w: 220, h: 140)
        var red = 0, green = 0
        for y in 0..<100 { for x in 0..<100 {
            if isColour(img, x, y, 255, 0, 0) { red += 1 }
            if isColour(img, x, y, 0, 128, 0) { green += 1 }
        } }
        XCTAssertEqual(red, 0, "the order-1 red item shows")
        XCTAssertGreaterThanOrEqual(green, 9_800)
    }

    /// 027 end to end (the container alone, shifted +200 so the overflow
    /// is on stage; content box starts at 202 after the 2-px border): the
    /// five red rules sit at content x −100/−40/20/80/140, "One" paints
    /// at −150…−101, and "Six" ends at the container's inner edge (199).
    @MainActor
    func testRaster027PlacesItemsAndRulesByOuterSize() throws {
        let img = try raster(try FlexNowrapIR.flexRoot(FlexNowrapIR.doc027), ox: 200, w: 640, h: 120)
        // Probe row near the bottom of the line, below the item labels.
        let y = 2 + 45
        for ruleX in [-100, -40, 20, 80, 140] {
            // Rule centre column (content x + 5) is pure red.
            XCTAssertTrue(isColour(img, 202 + ruleX + 5, y, 255, 0, 0), "no rule at content x \(ruleX)")
        }
        XCTAssertFalse(isColour(img, 202 + 55, y, 255, 0, 0), "stale rule at content x 50")
        // "One" tint (rgba(96,139,168,.2) over white ≈ 223,232,238).
        XCTAssertTrue(isColour(img, 202 - 125, y, 223, 232, 238, tol: 12), "One not at −150")
        // "Six" stays inside the container: its tint at content x 175 and
        // the container's own right border right after content x 200.
        XCTAssertTrue(isColour(img, 202 + 175, y, 223, 232, 238, tol: 12), "Six not at 150")
        XCTAssertFalse(isColour(img, 202 + 215, y, 223, 232, 238, tol: 12), "Six overflows")
    }
}

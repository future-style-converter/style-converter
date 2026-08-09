//
//  RatioInlineSizeTests.swift
//  Wave 38 (lane N7) — pins for the css-sizing-4 §4.1 predicate that
//  keeps the composed-WPT block-flow fill off a box whose aspect ratio
//  already determines its inline size.
//
//  Two halves: the pure predicate's truth table, and the arithmetic it
//  hands over to (SizeApplierMath's ratio fill) — proving the pair
//  reproduces the Chromium ref's 100×100 square for the live
//  block-aspect-ratio-002 shape instead of a full-canvas bar.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class RatioInlineSizeTests: XCTestCase {

    /// The one shape the fix exists for: block-aspect-ratio-002's
    /// `height: 100px; aspect-ratio: 1/1` with no author width.
    func testDefiniteHeightPlusRatioDeterminesTheInlineSize() {
        var c = SizeConfig()
        c.height = .exact(px: 100)
        c.aspectRatio = AspectRatioValue(ratio: 1, isAuto: false)
        XCTAssertTrue(RatioInlineSize.determinesInlineSize(c))
    }

    /// `aspect-ratio: auto W/H` on a non-replaced box uses the W/H
    /// fallback as its ratio (the same gate SizeApplierMath applies), so
    /// isAuto must NOT suppress the predicate — block-aspect-ratio-006's
    /// second div is exactly this shape.
    func testAutoWithFallbackRatioStillDetermines() {
        var c = SizeConfig()
        c.height = .exact(px: 50)
        c.aspectRatio = AspectRatioValue(ratio: 4, isAuto: true)
        XCTAssertTrue(RatioInlineSize.determinesInlineSize(c))
    }

    /// A bare `aspect-ratio: auto` carries ratio 0 — nothing determined.
    func testBareAutoRatioDeterminesNothing() {
        var c = SizeConfig()
        c.height = .exact(px: 100)
        c.aspectRatio = AspectRatioValue(ratio: 0, isAuto: true)
        XCTAssertFalse(RatioInlineSize.determinesInlineSize(c))
    }

    /// An author width always wins (css-sizing-4 §4: the ratio only
    /// fills an AUTOMATIC axis), so the fill-width fold is irrelevant.
    func testAuthorWidthSuppressesThePredicate() {
        var c = SizeConfig()
        c.width = .exact(px: 20)
        c.height = .exact(px: 100)
        c.aspectRatio = AspectRatioValue(ratio: 1, isAuto: false)
        XCTAssertFalse(RatioInlineSize.determinesInlineSize(c))
    }

    /// No block size at all → the ratio determines nothing and the box
    /// must keep the CSS 2.1 §10.3.3 stretch-fit fill.
    func testNoHeightKeepsTheFill() {
        var c = SizeConfig()
        c.aspectRatio = AspectRatioValue(ratio: 1, isAuto: false)
        XCTAssertFalse(RatioInlineSize.determinesInlineSize(c))
    }

    /// A percent / calc / intrinsic block size is NOT definite here: the
    /// ratio fill would not synthesise a width, and suppressing the fill
    /// would collapse the box to its ideal size (strictly worse).
    func testIndefiniteHeightShapesKeepTheFill() {
        for h: LengthValue in [.relative(value: 100, unit: .percent, pxFallback: nil),
                               .calc(expression: "calc(50% + 10px)"),
                               .intrinsic(kind: .minContent),
                               .auto] {
            var c = SizeConfig()
            c.height = h
            c.aspectRatio = AspectRatioValue(ratio: 1, isAuto: false)
            XCTAssertFalse(RatioInlineSize.determinesInlineSize(c),
                           "\(h) must not count as a definite block size")
        }
    }

    /// No aspect-ratio at all — the overwhelmingly common case — keeps
    /// the fill, which is what makes the guard byte-safe for the corpus.
    func testNoRatioKeepsTheFill() {
        var c = SizeConfig()
        c.height = .exact(px: 100)
        XCTAssertFalse(RatioInlineSize.determinesInlineSize(c))
    }

    // MARK: - The arithmetic the predicate hands over to

    /// With the fill suppressed, SizeApplierMath fills the missing axis
    /// from the ratio: block-aspect-ratio-002 → 100×100, and
    /// calc-size-aspect-ratio-001 (`height: 100px; aspect-ratio: 1/2`,
    /// width an unmapped calc-size) → 50×100. Pinned through the same
    /// resolver the applier uses so the two halves cannot drift.
    func testRatioFillProducesTheRefGeometry() {
        for (ratio, expectedW) in [(1.0, 100.0), (0.5, 50.0), (4.0, 400.0)] {
            var c = SizeConfig()
            c.height = .exact(px: 100)
            c.aspectRatio = AspectRatioValue(ratio: ratio, isAuto: false)
            XCTAssertTrue(RatioInlineSize.determinesInlineSize(c))
            let h = SizeApplierResolve.exact(c.height,
                                             ctx: SpacingContext(), parent: 390)
            XCTAssertEqual(h, 100)
            XCTAssertEqual(h! * CGFloat(ratio), CGFloat(expectedW))
        }
    }

    // MARK: - End-to-end raster (the fold guard, not just the predicate)

    /// Render one pixel out of a real ComponentRenderer tree, exactly the
    /// way WPTBlockChildFillTests does, so the guard is proven where it
    /// actually lives (the wptBlockFlowFillWidth fold) rather than only in
    /// the predicate.
    ///
    /// The probed box is always a CHILD of a definite-width block parent,
    /// because that is the only shape in which the fold can fire: the
    /// `wptBlockFlowFillWidth` environment key defaults to nil (see
    /// WPTCaptureMode.swift) and is published by a block PARENT for its
    /// in-flow children (`wptChildFillWidth`). Rendering the box as the
    /// tree root would leave the channel nil, the fold dead, and the guard
    /// untested — which is exactly how the first cut of these two rasters
    /// read a vacuous pass and a false failure.
    @MainActor
    private func pixel(_ comp: IRComponent, wpt: Bool,
                       x: Int, y: Int) throws -> (r: UInt8, g: UInt8, b: UInt8) {
        let view = ComponentRenderer(component: comp)
            .environment(\.wptCaptureMode, wpt)
            .frame(width: 390, height: 200, alignment: .topLeading)
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
        let i = (y * w + x) * 4
        return (buf[i], buf[i + 1], buf[i + 2])
    }

    /// The live block-aspect-ratio-002 wire (`height: 100px;
    /// aspect-ratio: 1/1; background: green`), mounted as the in-flow child
    /// of a definite 300px block parent so the fill fold is armed — the
    /// ratio-derived box is 100px wide, so a probe at x = 280 (deep inside
    /// the fill the fold would have written, far outside the square) must
    /// be canvas WHITE, while x = 50 is green. Pre-fix both probes were
    /// green.
    @MainActor
    func testRatioBoxDoesNotFillTheContainingBlock() throws {
        let comp = try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"p","name":"BlockParent",
         "properties":[
           {"type":"Width","data":{"type":"length","px":300.0}}
         ],
         "children":[
           {"id":"r","name":"RatioBox",
            "properties":[
              {"type":"Height","data":{"type":"length","px":100.0}},
              {"type":"AspectRatio","data":{"ratio":{"w":1,"h":1},"normalizedRatio":1.0}},
              {"type":"BackgroundColor","data":{"srgb":{"r":0.0,"g":0.5,"b":0.0},"original":"green"}}
            ]}
         ]}
        """.utf8))
        let inside = try pixel(comp, wpt: true, x: 50, y: 50)
        XCTAssertGreaterThan(inside.g, 96, "the ratio-derived square must paint at x=50")
        XCTAssertLessThan(inside.r, 96, "x=50 must be the box's green, not canvas white")
        let outside = try pixel(comp, wpt: true, x: 280, y: 50)
        XCTAssertGreaterThan(outside.r, 192, "x=280 is past the 100px square — canvas white")
        XCTAssertGreaterThan(outside.g, 192, "x=280 is past the 100px square — canvas white")
    }

    /// The companion regression pin: the SAME child WITHOUT the ratio still
    /// fills its containing block, so the guard did not disarm CSS 2.1
    /// §10.3.3 for ordinary auto-width blocks. Identical tree to the test
    /// above minus the `aspect-ratio`, which is what makes the pair a
    /// controlled A/B on the predicate alone.
    @MainActor
    func testPlainAutoWidthBlockStillFills() throws {
        let comp = try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"p","name":"BlockParent",
         "properties":[
           {"type":"Width","data":{"type":"length","px":300.0}}
         ],
         "children":[
           {"id":"b","name":"PlainBox",
            "properties":[
              {"type":"Height","data":{"type":"length","px":100.0}},
              {"type":"BackgroundColor","data":{"srgb":{"r":0.0,"g":0.5,"b":0.0},"original":"green"}}
            ]}
         ]}
        """.utf8))
        let far = try pixel(comp, wpt: true, x: 280, y: 50)
        XCTAssertGreaterThan(far.g, 96, "a ratio-free auto-width block must still fill")
        XCTAssertLessThan(far.r, 96, "x=280 must be the filled box's green")
    }
}

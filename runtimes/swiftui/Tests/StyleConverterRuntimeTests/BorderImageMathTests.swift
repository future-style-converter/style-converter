//
//  BorderImageMathTests.swift
//  StyleConverterRuntimeTests
//
//  Pins the css-backgrounds-3 §5 border-image geometry (BorderImageMath)
//  behind the BI-IOS 9-slice painter: width resolution (§6.3), outset
//  resolution (§6.4), slice-line px conversion + the nine-region grid
//  (§6.1/§6.2), and the per-edge tile plans (§6.2 repeat keywords).
//  The §6.3 resolve pins mirror the Compose runtime's
//  BorderFidelityWave1Test ("number width is a multiple of the computed
//  border width" / the computed-width capture pair) value-for-value so
//  the natives cannot drift. Pure math only — no rendering.
//

import Foundation
import CoreGraphics
import XCTest
// @testable: BorderImageMath and the extractor are internal to the module.
@testable import StyleConverterRuntime

final class BorderImageMathTests: XCTestCase {

    // MARK: - §6.3 width resolution (mirrors Compose's resolve() pins)

    func testNumberWidthMultipliesComputedBorder() {
        // §6.3: number × computed width. 2 × 0 = 0 → paints nothing
        // (the Borders_C06 shape: border-image-width: 2, no border).
        XCTAssertEqual(BorderImageMath.resolveWidth(.number(2), computedBorder: 0,
                                                    slice: nil, boxExtent: 100), 0)
        // 2 × 20 = 40 for a real 20px border (Compose pin).
        XCTAssertEqual(BorderImageMath.resolveWidth(.number(2), computedBorder: 20,
                                                    slice: nil, boxExtent: 100), 40)
        // Initial value (absent) is the number 1 → 1 × computed width.
        XCTAssertEqual(BorderImageMath.resolveWidth(nil, computedBorder: 20,
                                                    slice: nil, boxExtent: 100), 20)
    }

    func testLengthAutoAndPercentWidths() {
        // Lengths stay literal regardless of the border (Compose pin: 12).
        XCTAssertEqual(BorderImageMath.resolveWidth(.length(12), computedBorder: 0,
                                                    slice: nil, boxExtent: 100), 12)
        // auto → the slice's absolute px size when available (§6.3;
        // Compose pin: slice 30 → 30).
        XCTAssertEqual(BorderImageMath.resolveWidth(
            .auto, computedBorder: 0,
            slice: BorderImageSliceEdge(value: 30, isPercent: false), boxExtent: 100), 30)
        // auto + PERCENT slice: the image px size is unknown at resolve
        // time → falls back to the computed border (Compose policy).
        XCTAssertEqual(BorderImageMath.resolveWidth(
            .auto, computedBorder: 7,
            slice: BorderImageSliceEdge(value: 50, isPercent: true), boxExtent: 100), 7)
        // §6.3 percentages refer to the border image AREA extent — iOS
        // resolves the true basis at draw time (50% of 200 = 100),
        // unlike Compose's computed-border approximation (its TODO).
        XCTAssertEqual(BorderImageMath.resolveWidth(.percent(50), computedBorder: 0,
                                                    slice: nil, boxExtent: 200), 100)
    }

    // MARK: - The applier's zero-paint gate

    func testCanPaintIsFalseOnlyWhenNothingCanEverPaint() {
        // Border-less element + the initial `1` multiplier → all sides
        // resolve to 0 at ANY box size → identity (Borders_C06).
        var cfg = BorderImageConfig()
        cfg.source = .url("x.png")
        XCTAssertFalse(BorderImageMath.canPaint(cfg))
        // A single literal length side flips the gate.
        cfg.widthLeft = .length(4)
        XCTAssertTrue(BorderImageMath.canPaint(cfg))
        // Percent widths are linear in the box extent (zero only at a
        // zero-size box) → the probe must treat them as paintable.
        var pct = BorderImageConfig()
        pct.source = .url("x.png")
        pct.widthTop = .percent(10)
        XCTAssertTrue(BorderImageMath.canPaint(pct))
    }

    // MARK: - §6.4 outset resolution (mirrors Compose's resolveOutset)

    func testOutsetResolution() {
        // Initial value 0 — no expansion.
        XCTAssertEqual(BorderImageMath.resolveOutset(nil, computedBorder: 10), 0)
        // <length> literal.
        XCTAssertEqual(BorderImageMath.resolveOutset(.length(5), computedBorder: 10), 5)
        // <number> × computed border-width.
        XCTAssertEqual(BorderImageMath.resolveOutset(.number(2), computedBorder: 10), 20)
        // auto / percent are not in the §6.4 grammar → inert 0.
        XCTAssertEqual(BorderImageMath.resolveOutset(.auto, computedBorder: 10), 0)
        XCTAssertEqual(BorderImageMath.resolveOutset(.percent(50), computedBorder: 10), 0)
    }

    // MARK: - §6.1 slice px + the nine-region source grid (30×30 image)

    func testSlicePxConversion() {
        // <number> is image px verbatim (§6.1).
        XCTAssertEqual(BorderImageMath.slicePx(
            BorderImageSliceEdge(value: 12, isPercent: false), extent: 30), 12)
        // <percentage> of the image's extent on that axis: 50% of 30 = 15.
        XCTAssertEqual(BorderImageMath.slicePx(
            BorderImageSliceEdge(value: 50, isPercent: true), extent: 30), 15)
        // Absent edge → 0 (Compose's toPixels(null) policy).
        XCTAssertEqual(BorderImageMath.slicePx(nil, extent: 30), 0)
    }

    func testNineGridPxSlicesOn30x30() {
        // Uniform 10px slices on a 30×30 image → nine equal 10×10 cells.
        let g = BorderImageMath.nineGrid(bounds: CGRect(x: 0, y: 0, width: 30, height: 30),
                                         top: 10, right: 10, bottom: 10, left: 10)
        XCTAssertEqual(g.topLeft, CGRect(x: 0, y: 0, width: 10, height: 10))
        XCTAssertEqual(g.topCenter, CGRect(x: 10, y: 0, width: 10, height: 10))
        XCTAssertEqual(g.topRight, CGRect(x: 20, y: 0, width: 10, height: 10))
        XCTAssertEqual(g.middleLeft, CGRect(x: 0, y: 10, width: 10, height: 10))
        XCTAssertEqual(g.center, CGRect(x: 10, y: 10, width: 10, height: 10))
        XCTAssertEqual(g.middleRight, CGRect(x: 20, y: 10, width: 10, height: 10))
        XCTAssertEqual(g.bottomLeft, CGRect(x: 0, y: 20, width: 10, height: 10))
        XCTAssertEqual(g.bottomCenter, CGRect(x: 10, y: 20, width: 10, height: 10))
        XCTAssertEqual(g.bottomRight, CGRect(x: 20, y: 20, width: 10, height: 10))
    }

    func testNineGridPercentSlicesOn30x30() {
        // 50% slices on 30×30 → 15px bands meeting in the middle: the
        // center and every edge band collapse to zero extent (nothing
        // between the slice lines).
        let s = BorderImageMath.slicePx(
            BorderImageSliceEdge(value: 50, isPercent: true), extent: 30)
        let g = BorderImageMath.nineGrid(bounds: CGRect(x: 0, y: 0, width: 30, height: 30),
                                         top: s, right: s, bottom: s, left: s)
        // Corners take the full quadrants.
        XCTAssertEqual(g.topLeft, CGRect(x: 0, y: 0, width: 15, height: 15))
        XCTAssertEqual(g.bottomRight, CGRect(x: 15, y: 15, width: 15, height: 15))
        // Middles are degenerate — the painter skips them.
        XCTAssertEqual(g.topCenter.width, 0)
        XCTAssertEqual(g.center.width, 0)
        XCTAssertEqual(g.center.height, 0)
    }

    func testNineGridProportionallyReducesOverlappingBands() {
        // §6.1/§6.2: left 40 + right 20 on a 30-wide box exceed the
        // width → both scale by 30/60: 20 and 10, center width 0.
        let g = BorderImageMath.nineGrid(bounds: CGRect(x: 0, y: 0, width: 30, height: 30),
                                         top: 0, right: 20, bottom: 0, left: 40)
        XCTAssertEqual(g.middleLeft.width, 20)
        XCTAssertEqual(g.middleRight.width, 10)
        XCTAssertEqual(g.center.width, 0)
        // The right band still ends flush with the box edge.
        XCTAssertEqual(g.middleRight.maxX, 30)
    }

    // MARK: - §6.2 edge tile plans (round / space / repeat / stretch)

    func testEdgePlanRoundRescalesToWholeTiles() {
        // round: 200pt band, 30px slice → round(6.67) = 7 tiles at the
        // fractional pitch 200/7 ≈ 28.571 (the BackgroundTileMath rule).
        let p = BorderImageMath.edgePlan(dest: 200, src: 30, mode: .round)
        XCTAssertEqual(p.origins.count, 7)
        XCTAssertEqual(p.tileSize, 200.0 / 7.0, accuracy: 0.001)
        // First tile flush with the band start, last flush with its end.
        XCTAssertEqual(p.origins.first!, 0)
        XCTAssertEqual(p.ends.last!, 200)
    }

    func testEdgePlanSpaceDistributesGaps() {
        // space: 100pt band, 30px slice → 3 whole tiles + 2 gaps of
        // (100 − 90)/2 = 5, first/last flush with the band ends (§3.7
        // arithmetic reused per §6.2).
        let p = BorderImageMath.edgePlan(dest: 100, src: 30, mode: .space)
        XCTAssertEqual(p.origins, [0, 35, 70])
        XCTAssertEqual(p.ends, [30, 65, 100])
        // The pitch stays the unscaled source length — gaps, not rescale.
        XCTAssertEqual(p.tileSize, 30)
    }

    func testEdgePlanSpaceFallsBackToStretchBelowTwoTiles() {
        // Fewer than two whole tiles (50/30 → 1) → the Compose count==1
        // branch stretches the single tile across the band; mirrored so
        // the natives paint the same degenerate case.
        let p = BorderImageMath.edgePlan(dest: 50, src: 30, mode: .space)
        XCTAssertEqual(p.origins, [0])
        XCTAssertEqual(p.ends, [50])
        XCTAssertEqual(p.tileSize, 50)
    }

    func testEdgePlanRepeatAndStretch() {
        // repeat: whole 30px tiles from the band start; the 4th tile
        // overflows to 120 and the painter clips it to the band (§6.2).
        let rep = BorderImageMath.edgePlan(dest: 100, src: 30, mode: .repeatTile)
        XCTAssertEqual(rep.origins, [0, 30, 60, 90])
        XCTAssertEqual(rep.ends, [30, 60, 90, 120])
        // stretch: one tile spanning the whole band.
        let st = BorderImageMath.edgePlan(dest: 100, src: 30, mode: .stretch)
        XCTAssertEqual(st.origins, [0])
        XCTAssertEqual(st.ends, [100])
        // Degenerate inputs plan nothing (no ÷0, no phantom tiles).
        XCTAssertTrue(BorderImageMath.edgePlan(dest: 0, src: 30, mode: .round).origins.isEmpty)
        XCTAssertTrue(BorderImageMath.edgePlan(dest: 100, src: 0, mode: .round).origins.isEmpty)
    }

    // MARK: - Extractor computed-border capture (mirrors Compose)

    func testExtractorCapturesZeroComputedWidthWithoutBorder() {
        // Borders_C06 shape: border-image on an element with NO
        // border-style → every computed width is 0 and the §6.3 number
        // multiplier can never paint (Compose pins the same pair).
        let cfg = BorderImageExtractor.extract(from: [
            IRProperty(type: "BorderImageSource",
                       data: .object(["type": .string("url"), "url": .string("b.png")])),
            IRProperty(type: "BorderImageWidth",
                       data: .object([
                           "top": .object(["type": .string("number"), "value": .double(2)]),
                           "right": .object(["type": .string("number"), "value": .double(2)]),
                           "bottom": .object(["type": .string("number"), "value": .double(2)]),
                           "left": .object(["type": .string("number"), "value": .double(2)]),
                       ])),
        ])
        XCTAssertEqual(cfg?.computedBorderTop, 0)
        XCTAssertEqual(cfg?.computedBorderLeft, 0)
        // The applier's gate must therefore hold this config to identity.
        XCTAssertFalse(BorderImageMath.canPaint(cfg!))
    }

    func testExtractorCapturesComputedWidthWhenBordered() {
        // A real 20px solid top border → computed top width 20 (the
        // §6.3 basis for the initial `1` / any number multiplier).
        let cfg = BorderImageExtractor.extract(from: [
            IRProperty(type: "BorderImageSource",
                       data: .object(["type": .string("url"), "url": .string("b.png")])),
            IRProperty(type: "BorderTopWidth", data: .object(["px": .double(20)])),
            IRProperty(type: "BorderTopStyle", data: .string("SOLID")),
        ])
        XCTAssertEqual(cfg?.computedBorderTop, 20)
        // Sides without any border-style stay at computed 0 (§4.3).
        XCTAssertEqual(cfg?.computedBorderBottom, 0)
        // With a bordered side the initial-value widths resolve > 0.
        XCTAssertTrue(BorderImageMath.canPaint(cfg!))
    }
}

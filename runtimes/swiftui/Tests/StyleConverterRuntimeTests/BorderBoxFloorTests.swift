//
//  BorderBoxFloorTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 40, lane T7 — pins the css-ui-3 §3.1 border-box floor
//  (StyleEngine/sizing/BorderBoxFloor.swift).
//
//  ── What is being protected ────────────────────────────────────────────────
//  css-ui/box-sizing-026: `box-sizing: border-box; border: 50px green solid;
//  width: 10px; height: 10px` is a 100×100 green square in every browser (the
//  content box floors at zero, the border box cannot shrink below its bands).
//  iOS took the 10px literally and let the `z-index: -1` red square behind it
//  show through — ssim 0.9974 with the COLOUR gate as the sole failure.
//
//  ── The invariant that must never break ────────────────────────────────────
//  The floor is armed by an EXPLICIT `box-sizing: border-box` ONLY. A
//  nil-inclusive predicate fires on 46 currently-PASSING iOS cells (measured
//  across all 30 wave39-final sections), because `nil` in this runtime means
//  "the IR declared nothing" while the CSS default is content-box. Both the
//  nil case and the content-box case are pinned inert.
//

import XCTest
import CoreGraphics
// @testable: BorderBoxFloor / SizeConfig / StyleBuilder are internal to the
// module — same access pattern as BoxSizingTests.swift.
@testable import StyleConverterRuntime

final class BorderBoxFloorTests: XCTestCase {

    private func props(_ pairs: [(String, IRValue)]) -> [IRProperty] {
        pairs.map { IRProperty(type: $0.0, data: $0.1) }
    }

    /// box-sizing-026's `#test` declarations, verbatim from
    /// tools/titan/runs/wave39-final/sections/css-ui/per-test-ir/
    /// wpt__css-ui__box-sizing-026.json.
    private func box026(boxSizing: String?) -> [IRProperty] {
        var pairs: [(String, IRValue)] = []
        if let boxSizing { pairs.append(("BoxSizing", .string(boxSizing))) }
        for side in ["Top", "Right", "Bottom", "Left"] {
            pairs.append(("Border\(side)Width", .object(["px": .double(50)])))
            pairs.append(("Border\(side)Style", .string("SOLID")))
            pairs.append(("Border\(side)Color",
                          .object(["srgb": .object(["r": .double(0),
                                                    "g": .double(0.5019607843137255),
                                                    "b": .double(0)])])))
        }
        pairs.append(("Width", .object(["type": .string("length"), "px": .double(10)])))
        pairs.append(("Height", .object(["type": .string("length"), "px": .double(10)])))
        return props(pairs)
    }

    private func exactPx(_ v: LengthValue?) -> Double? {
        if case .exact(let px)? = v { return px }
        return nil
    }

    // MARK: - 1. The per-axis arithmetic

    func testUnderBandSizeFloorsToTheBandSum() {
        XCTAssertEqual(exactPx(BorderBoxFloor.floored(.exact(px: 10), bandPx: 100)), 100)
    }

    func testOverBandSizeIsUntouched() {
        XCTAssertEqual(exactPx(BorderBoxFloor.floored(.exact(px: 200), bandPx: 100)), 200)
        // Exactly equal is not "smaller" — no change, no rounding wobble.
        XCTAssertEqual(exactPx(BorderBoxFloor.floored(.exact(px: 100), bandPx: 100)), 100)
    }

    func testZeroBandIsIdentity() {
        XCTAssertEqual(exactPx(BorderBoxFloor.floored(.exact(px: 10), bandPx: 0)), 10)
    }

    /// Only a DEFINITE px declaration is comparable — a percent/calc/auto axis
    /// resolves later against a basis this helper never sees.
    func testIndefiniteAxesPassThroughVerbatim() {
        XCTAssertEqual(BorderBoxFloor.floored(.auto, bandPx: 100), .auto)
        XCTAssertEqual(BorderBoxFloor.floored(.relative(value: 50, unit: .percent,
                                                        pxFallback: nil), bandPx: 100),
                       .relative(value: 50, unit: .percent, pxFallback: nil))
        XCTAssertNil(BorderBoxFloor.floored(nil, bandPx: 100))
    }

    // MARK: - 2. The trigger scope

    /// box-sizing-026 itself, end to end through StyleBuilder: 10px declared,
    /// 100px of border on each axis, 100px used.
    func testExplicitBorderBoxFloorsThroughStyleBuilder() {
        let style = StyleBuilder.build(from: box026(boxSizing: "BORDER_BOX"))
        XCTAssertEqual(exactPx(style.size.width), 100)
        XCTAssertEqual(exactPx(style.size.height), 100)
    }

    /// THE regression sentinel: an ABSENT box-sizing must NOT floor. This is
    /// the shape of the 46 passing css-grid/css-flexbox abspos cells whose
    /// declared width sits inside their own borders under the CSS
    /// content-box default.
    func testAbsentBoxSizingDoesNotFloor() {
        let style = StyleBuilder.build(from: box026(boxSizing: nil))
        XCTAssertEqual(exactPx(style.size.width), 10)
        XCTAssertEqual(exactPx(style.size.height), 10)
    }

    /// An explicit content-box keeps its own inflation path and must not be
    /// floored on top of it.
    func testExplicitContentBoxDoesNotFloor() {
        let style = StyleBuilder.build(from: box026(boxSizing: "CONTENT_BOX"))
        XCTAssertEqual(exactPx(style.size.width), 10)
        XCTAssertEqual(exactPx(style.size.height), 10)
    }

    /// A `border-style: none` side has USED width 0 (CSS 2.1 §8.5.3), so it
    /// contributes no band and cannot floor anything — the same gate
    /// contentBoxInflation uses, which is why both read one helper.
    func testStyleNoneBordersContributeNoBand() {
        var pairs: [(String, IRValue)] = [("BoxSizing", .string("BORDER_BOX"))]
        for side in ["Top", "Right", "Bottom", "Left"] {
            pairs.append(("Border\(side)Width", .object(["px": .double(50)])))
            pairs.append(("Border\(side)Style", .string("NONE")))
        }
        pairs.append(("Width", .object(["type": .string("length"), "px": .double(10)])))
        let style = StyleBuilder.build(from: props(pairs))
        XCTAssertEqual(exactPx(style.size.width), 10)
    }
}

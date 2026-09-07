//
//  OutlineShadedRingTests.swift
//  retro R5 (audit A7#3, iOS half) — the 3D outline styles paint the
//  css-backgrounds-3 §3.2 bevel, not a flat solid ring.
//
//  Band tables are pinned on the two VERBATIM pairs-02 carriers from the
//  A11 fixture run (fidelity__pairwise__pairs-02/ir.json):
//    PW_Borders_Sizing_04  OutlineStyle GROOVE, no width → css-ui-4 §3.2
//                          `medium` 3px → Chrome's 2px/1px split
//    PW_Borders_Sizing_02  OutlineStyle RIDGE, OutlineWidth 6px → 3px/3px
//  and cross-checked against BorderSideApplier's Blink palette so border
//  and outline bevels can never use two shade models on this platform.
//

import SwiftUI
import XCTest
@testable import StyleConverterRuntime

final class OutlineShadedRingTests: XCTestCase {

    private func obj(_ d: [String: IRValue]) -> IRValue { .object(d) }
    private func props(_ p: [(String, IRValue)]) -> [IRProperty] {
        p.map { IRProperty(type: $0.0, data: $0.1) }
    }
    private let base = Color(red: 142.0 / 255.0, green: 68.0 / 255.0, blue: 173.0 / 255.0, opacity: 1) // #8e44ad, the outline fixture's colour

    func testGrooveMediumWidthSplitsTwoOneLikeChrome() throws {
        // Verbatim PW_Borders_Sizing_04: style only → width 3 (medium).
        let cfg = try XCTUnwrap(OutlineExtractor.extract(from: props([("OutlineStyle", .string("GROOVE"))])))
        XCTAssertEqual(cfg.width, 3)
        XCTAssertEqual(cfg.style, .groove)
        XCTAssertTrue(OutlineShadedRing.isThreeD(cfg.style))
        let dark = BorderSideApplier.shade(base, light: false)
        let light = BorderSideApplier.shade(base, light: true)
        // Chrome probe (wave 5): top/left = DARK 2px out + light 1px in;
        // bottom/right = light 1px out + DARK 2px in.
        XCTAssertEqual(OutlineShadedRing.bands(style: .groove, width: cfg.width, base: base), [
            OutlineShadedBand(outerInset: 0, thickness: 2, topLeft: true,  colour: dark),
            OutlineShadedBand(outerInset: 2, thickness: 1, topLeft: true,  colour: light),
            OutlineShadedBand(outerInset: 0, thickness: 1, topLeft: false, colour: light),
            OutlineShadedBand(outerInset: 1, thickness: 2, topLeft: false, colour: dark),
        ])
    }

    func testRidgeSixPxSplitsEqualHalvesInverted() throws {
        // Verbatim PW_Borders_Sizing_02.
        let cfg = try XCTUnwrap(OutlineExtractor.extract(from: props([
            ("OutlineStyle", .string("RIDGE")),
            ("OutlineWidth", obj(["type": .string("length"), "px": .double(6)])),
        ])))
        XCTAssertEqual(cfg.width, 6)
        let dark = BorderSideApplier.shade(base, light: false)
        let light = BorderSideApplier.shade(base, light: true)
        // ridge = groove inverted: light ceil band outer on top/left,
        // inner on bottom/right.
        XCTAssertEqual(OutlineShadedRing.bands(style: .ridge, width: cfg.width, base: base), [
            OutlineShadedBand(outerInset: 0, thickness: 3, topLeft: true,  colour: light),
            OutlineShadedBand(outerInset: 3, thickness: 3, topLeft: true,  colour: dark),
            OutlineShadedBand(outerInset: 0, thickness: 3, topLeft: false, colour: dark),
            OutlineShadedBand(outerInset: 3, thickness: 3, topLeft: false, colour: light),
        ])
    }

    func testGrooveRidgeBandsAgreeWithTheBorderPainterPalette() {
        // One Blink model on this platform: the outer/inner colours per
        // side class equal BorderSideApplier.grooveRidgeBandColours.
        for style in [BorderStyleValue.groove, .ridge] {
            let bands = OutlineShadedRing.bands(style: style, width: 6, base: base)
            let tl = BorderSideApplier.grooveRidgeBandColours(style: style, isTopLeft: true, base: base)
            let br = BorderSideApplier.grooveRidgeBandColours(style: style, isTopLeft: false, base: base)
            // Top/left: bands[0] outer, bands[1] inner.
            XCTAssertEqual(bands[0].colour, tl.outer, "\(style) top/left outer")
            XCTAssertEqual(bands[1].colour, tl.inner, "\(style) top/left inner")
            // Bottom/right: bands[2] outer, bands[3] inner.
            XCTAssertEqual(bands[2].colour, br.outer, "\(style) bottom/right outer")
            XCTAssertEqual(bands[3].colour, br.inner, "\(style) bottom/right inner")
        }
    }

    func testInsetAndOutsetAreSingleFullWidthBandsPerSideClass() {
        let dark = BorderSideApplier.shade(base, light: false)
        let light = BorderSideApplier.shade(base, light: true)
        // inset: sunken — top/left dark, bottom/right the declared colour.
        XCTAssertEqual(OutlineShadedRing.bands(style: .inset, width: 10, base: base), [
            OutlineShadedBand(outerInset: 0, thickness: 10, topLeft: true,  colour: dark),
            OutlineShadedBand(outerInset: 0, thickness: 10, topLeft: false, colour: light),
        ])
        // outset: the inverse.
        XCTAssertEqual(OutlineShadedRing.bands(style: .outset, width: 10, base: base), [
            OutlineShadedBand(outerInset: 0, thickness: 10, topLeft: true,  colour: light),
            OutlineShadedBand(outerInset: 0, thickness: 10, topLeft: false, colour: dark),
        ])
    }

    func testFlatStylesHaveNoShadedBands() {
        // solid / dashed / dotted / double stay on OutlineShape's stroke path.
        for style in [BorderStyleValue.solid, .dashed, .dotted, .double, .none, .hidden] {
            XCTAssertFalse(OutlineShadedRing.isThreeD(style), "\(style)")
            XCTAssertTrue(OutlineShadedRing.bands(style: style, width: 4, base: base).isEmpty, "\(style)")
        }
    }

    func testTrapezoidsTileTheRingWithMiteredCorners() {
        // The overlay frame is the ring's OUTER rectangle: 100×50 here.
        let rect = CGRect(x: 0, y: 0, width: 100, height: 50)
        let dark = BorderSideApplier.shade(base, light: false)
        // Full-width top/left band: top quad spans the whole width and is
        // 3 tall; left quad spans the whole height and is 3 wide.
        let tl = OutlineShadedRing.trapezoids(
            OutlineShadedBand(outerInset: 0, thickness: 3, topLeft: true, colour: dark), in: rect)
        XCTAssertEqual(tl.count, 2)
        XCTAssertEqual(tl[0].boundingRect, CGRect(x: 0, y: 0, width: 100, height: 3))
        XCTAssertEqual(tl[1].boundingRect, CGRect(x: 0, y: 0, width: 3, height: 50))
        // Bottom/right band hugs the far edges.
        let br = OutlineShadedRing.trapezoids(
            OutlineShadedBand(outerInset: 0, thickness: 3, topLeft: false, colour: dark), in: rect)
        XCTAssertEqual(br[0].boundingRect, CGRect(x: 0, y: 47, width: 100, height: 3))
        XCTAssertEqual(br[1].boundingRect, CGRect(x: 97, y: 0, width: 3, height: 50))
        // An inner band (groove's 1px floor half at inset 2) sits inside the
        // outer band: 2px in on every side, 1 thick.
        let inner = OutlineShadedRing.trapezoids(
            OutlineShadedBand(outerInset: 2, thickness: 1, topLeft: true, colour: dark), in: rect)
        XCTAssertEqual(inner[0].boundingRect, CGRect(x: 2, y: 2, width: 96, height: 1))
        XCTAssertEqual(inner[1].boundingRect, CGRect(x: 2, y: 2, width: 1, height: 46))
    }
}

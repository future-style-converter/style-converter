//
//  FilterDropShadowColorTests.swift
//  Retro R4 (A7#1, Swift half) — drop-shadow()'s missing colour is the
//  element's `color`, at full alpha.
//
//  filter-effects-1 §6.1 (Supported Filter Functions), drop-shadow():
//  "the missing used color is taken from the color property". FilterApplier
//  substituted `.black.opacity(0.3)`, Compose substituted opaque black, web
//  let the browser resolve currentcolor — three renders for one wire. On
//  css-color/currentcolor-003 (`drop-shadow(333px 0 0 currentColor)`) the
//  ref paints red/green shadow copies at +333px; iOS painted GREY copies
//  and still scored 0.9919 PASS — a degenerate pass.
//
//  Both natives now share one rule (Compose lane R6 pins the Kotlin twin):
//  declared colour, else the resolved text colour, alpha 1. Payloads are
//  VERBATIM from tools/titan/runs/wave49-final/sections/css-color/
//  per-test-ir/wpt__css-color__currentcolor-003.json.
//

import Foundation
import SwiftUI
import XCTest
@testable import StyleConverterRuntime

final class FilterDropShadowColorTests: XCTestCase {

    /// The span `currentcolor-003__1__0`'s Filter property — the colour is
    /// the `{"original":"currentColor"}` wire with NO srgb block.
    static let corpusFilter = """
    [{"type": "Filter", "data": [{"fn": "drop-shadow", "x": {"px": 333}, "y": {"px": 0}, "r": {"px": 0}, "c": {"original": "currentColor"}}]}]
    """

    /// The parent block `wpt__css-color__currentcolor-003__1`'s Color —
    /// the value InheritedText merges into the span, i.e. what StyleBuilder
    /// threads as `style.text.color`.
    static let corpusParentColor = """
    {"type": "Color", "data": {"srgb": {"r": 1, "g": 0, "b": 0}, "original": {"r": 255, "g": 0, "b": 0}}}
    """

    private func decode<T: Decodable>(_ json: String, as: T.Type) throws -> T {
        try JSONDecoder().decode(T.self, from: Data(json.utf8))
    }

    /// sRGB components of a SwiftUI Color via UIKit bridging (the same
    /// route BorderSideApplier.shade uses — SwiftUI.Color has no channel
    /// accessors on iOS 16).
    private func rgba(_ c: Color) -> (r: CGFloat, g: CGFloat, b: CGFloat, a: CGFloat) {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        XCTAssertTrue(UIColor(c).getRed(&r, green: &g, blue: &b, alpha: &a), "colour must be RGB-convertible")
        return (r, g, b, a)
    }

    /// The corpus wire extracts as a colourless drop-shadow — `c` carries
    /// no srgb block, so the extractor hands the applier nil.
    func testCorpusWireExtractsAsColourlessDropShadow() throws {
        let props = try decode(Self.corpusFilter, as: [IRProperty].self)
        let cfg = try XCTUnwrap(FilterExtractor.extract(from: props))
        guard case .dropShadow(let x, let y, let blur, let color) = try XCTUnwrap(cfg.filter.first) else {
            return XCTFail("expected .dropShadow, got \(String(describing: cfg.filter.first))")
        }
        XCTAssertEqual(x, 333)
        XCTAssertEqual(y, 0)
        XCTAssertEqual(blur, 0)
        XCTAssertNil(color, "`{\"original\":\"currentColor\"}` must NOT resolve to a colour here")
    }

    /// The fix: a nil colour takes the element's resolved `color` — the
    /// parent's red for currentcolor-003's span — at alpha 1.
    func testColourlessShadowTakesTheElementColourAtFullAlpha() throws {
        let parent = try decode(Self.corpusParentColor, as: IRProperty.self)
        let currentColor = try XCTUnwrap(ValueExtractors.extractColor(parent.data))
        let used = FilterApplier.dropShadowColor(declared: nil, currentColor: currentColor,
                                                 wptCaptureMode: true)
        let c = rgba(used)
        XCTAssertEqual(c.r, 1, accuracy: 0.01)
        XCTAssertEqual(c.g, 0, accuracy: 0.01)
        XCTAssertEqual(c.b, 0, accuracy: 0.01)
        XCTAssertEqual(c.a, 1, accuracy: 0.001, "spec colour is the element's color — not a 30% tint of it")
    }

    /// A declared `<color>` always wins over the element colour.
    func testDeclaredColourWins() {
        let used = FilterApplier.dropShadowColor(declared: Color(red: 0, green: 0, blue: 1),
                                                 currentColor: Color(red: 1, green: 0, blue: 0),
                                                 wptCaptureMode: true)
        let c = rgba(used)
        XCTAssertEqual(c.b, 1, accuracy: 0.01)
        XCTAssertEqual(c.r, 0, accuracy: 0.01)
    }

    /// No `color` anywhere up the chain → the SAME bottom-out the border
    /// and outline appliers use (BorderSideApplier.fallbackInk): WPT canvas
    /// → spec black (UA `color: CanvasText`), dark stage → #eee (0.933).
    /// The three inks on one element can therefore never disagree.
    func testBottomOutFollowsTheSharedInkChain() {
        let wpt = rgba(FilterApplier.dropShadowColor(declared: nil, currentColor: nil, wptCaptureMode: true))
        let ink = rgba(WPTCanvas.textInk)
        XCTAssertEqual(wpt.r, ink.r, accuracy: 0.001)
        XCTAssertEqual(wpt.g, ink.g, accuracy: 0.001)
        XCTAssertEqual(wpt.b, ink.b, accuracy: 0.001)
        XCTAssertEqual(wpt.a, 1, accuracy: 0.001)
        let stage = rgba(FilterApplier.dropShadowColor(declared: nil, currentColor: nil, wptCaptureMode: false))
        XCTAssertEqual(stage.r, 0.933, accuracy: 0.005, "dark-stage bottom-out is the #eee body colour")
        XCTAssertEqual(stage.a, 1, accuracy: 0.001)
    }

    /// The exact defect, negatively: no branch may ever return the old
    /// `.black.opacity(0.3)` — alpha is 1 whatever resolved.
    func testNeverTheOldTranslucentBlack() {
        for (cur, wpt) in [(Color?.none, true), (Color?.none, false),
                           (Color(red: 0, green: 0.5, blue: 0), true)] {
            let c = rgba(FilterApplier.dropShadowColor(declared: nil, currentColor: cur, wptCaptureMode: wpt))
            XCTAssertEqual(c.a, 1, accuracy: 0.001, "drop-shadow colour must be opaque (§6.1 uses the color property verbatim)")
        }
    }
}

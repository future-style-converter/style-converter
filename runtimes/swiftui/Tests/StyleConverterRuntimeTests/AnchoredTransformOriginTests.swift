//
//  AnchoredTransformOriginTests.swift
//  StyleConverterRuntimeTests — wave 49, lane A6.
//
//  Pins the LENGTH `transform-origin` path for scale and 2D rotate.
//  The IR strings below are VERBATIM payloads from the wave-48 corpus
//  per-test IR (tools/titan/runs/wave48-final/sections/css-transforms/
//  per-test-ir/wpt__css-transforms__css-transform-scale-002.json,
//  component `css-transform-scale-002__1__1`), decoded through the real
//  IRComponent decoder and the real TransformsExtractor so the wire shape
//  stays honest.
//

import Foundation
import SwiftUI
import XCTest
@testable import StyleConverterRuntime

final class AnchoredTransformOriginTests: XCTestCase {

    // The green box of css-transform-scale-002: 100x100 at the origin of
    // its containing block, `transform-origin: 0 0`, `transform: scale(2)`.
    private func scale002Component() throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data("""
        {
          "id": "css-transform-scale-002__1__1-164",
          "name": "css-transform-scale-002__1__1",
          "properties": [
            {"type": "Position", "data": "ABSOLUTE"},
            {"type": "Top", "data": {"px": 0}},
            {"type": "Left", "data": {"px": 0}},
            {"type": "Width", "data": {"type": "length", "px": 100}},
            {"type": "Height", "data": {"type": "length", "px": 100}},
            {"type": "TransformOrigin", "data": {"x": {"type": "length", "px": 0},
                                                 "y": {"type": "length", "px": 0}}},
            {"type": "Transform", "data": {"type": "functions",
                                           "list": [{"fn": "scale", "x": 2, "y": 2}]}}
          ]
        }
        """.utf8))
    }

    /// The extractor must record the length origin as an explicit pixel
    /// override and LEAVE the fraction at its 0.5 default — that split is
    /// what the applier keys on, and it is why `.scaleEffect(anchor:)`
    /// alone could never render this correctly.
    func testLengthOriginExtractsAsPixelOverrideNotFraction() throws {
        let agg = try XCTUnwrap(TransformsExtractor.extract(from: scale002Component().properties))
        XCTAssertEqual(agg.origin?.xPx, 0)
        XCTAssertEqual(agg.origin?.yPx, 0)
        // The fractional channel still says "centre": a length has no
        // fractional form until the element's size is known.
        XCTAssertEqual(agg.origin?.unit.x, 0.5)
        XCTAssertEqual(agg.origin?.unit.y, 0.5)
    }

    /// The bug, as arithmetic. `scale(2)` about the top-left corner must
    /// keep that corner FIXED and send (100,100) to (200,200); about the
    /// centre it would send the corner to (−50,−50) — the exact (−50,−50)
    /// displacement measured in the wave-48 iOS capture.
    func testScaleAboutPixelOriginKeepsTheCornerFixed() throws {
        let agg = try XCTUnwrap(TransformsExtractor.extract(from: scale002Component().properties))
        let m = AnchoredTransformMath.anchoredScale(
            sx: 2, sy: 2,
            size: CGSize(width: 100, height: 100),
            anchor: agg.origin?.unit ?? .center,
            anchorXPx: agg.origin?.xPx, anchorYPx: agg.origin?.yPx)
        let topLeft = CGPoint.zero.applying(m)
        XCTAssertEqual(topLeft.x, 0, accuracy: 1e-6)
        XCTAssertEqual(topLeft.y, 0, accuracy: 1e-6)
        let bottomRight = CGPoint(x: 100, y: 100).applying(m)
        XCTAssertEqual(bottomRight.x, 200, accuracy: 1e-6)
        XCTAssertEqual(bottomRight.y, 200, accuracy: 1e-6)
    }

    /// Without the pixel override the closed form must reproduce the
    /// fractional anchor exactly, so switching route on the px flag alone
    /// cannot disturb any existing centre-anchored baseline.
    func testScaleWithoutPixelOriginMatchesTheCentreAnchor() {
        let m = AnchoredTransformMath.anchoredScale(
            sx: 2, sy: 2,
            size: CGSize(width: 100, height: 100),
            anchor: .center)
        // Centre is the fixed point; the corner flies to (−50,−50).
        let centre = CGPoint(x: 50, y: 50).applying(m)
        XCTAssertEqual(centre.x, 50, accuracy: 1e-6)
        XCTAssertEqual(centre.y, 50, accuracy: 1e-6)
        let topLeft = CGPoint.zero.applying(m)
        XCTAssertEqual(topLeft.x, -50, accuracy: 1e-6)
        XCTAssertEqual(topLeft.y, -50, accuracy: 1e-6)
    }

    /// Per-axis independence: css-transforms-1 §4 resolves the two axes
    /// separately, so `0 50%` must anchor at x = 0 px and y = half the
    /// box height.
    func testMixedLengthAndFractionOriginResolvesPerAxis() {
        let m = AnchoredTransformMath.anchoredScale(
            sx: 2, sy: 2,
            size: CGSize(width: 100, height: 100),
            anchor: UnitPoint(x: 0.5, y: 0.5),
            anchorXPx: 0, anchorYPx: nil)
        let fixed = CGPoint(x: 0, y: 50).applying(m)
        XCTAssertEqual(fixed.x, 0, accuracy: 1e-6)
        XCTAssertEqual(fixed.y, 50, accuracy: 1e-6)
    }

    /// 2D rotation about a length origin. A quarter turn about the
    /// top-left corner sends (100,0) to (0,100) in CSS's y-down space —
    /// the sign convention the Compose twin documents in
    /// `TransformListComposer.rotation`.
    func testRotationAboutPixelOriginTurnsClockwise() {
        let m = AnchoredTransformMath.anchoredRotation(
            deg: 90,
            size: CGSize(width: 100, height: 100),
            anchor: .center,
            anchorXPx: 0, anchorYPx: 0)
        let corner = CGPoint(x: 100, y: 0).applying(m)
        XCTAssertEqual(corner.x, 0, accuracy: 1e-5)
        XCTAssertEqual(corner.y, 100, accuracy: 1e-5)
        // The anchor itself is fixed.
        let anchorPoint = CGPoint.zero.applying(m)
        XCTAssertEqual(anchorPoint.x, 0, accuracy: 1e-5)
        XCTAssertEqual(anchorPoint.y, 0, accuracy: 1e-5)
    }

    /// The effects must forward the same closed form they document, so a
    /// regression in either wrapper is caught here and not only on device.
    func testEffectsForwardTheClosedForm() {
        let scaleEffect = AnchoredScaleEffect(sx: 2, sy: 2, anchor: .center,
                                              anchorXPx: 0, anchorYPx: 0)
        let expectedScale = ProjectionTransform(AnchoredTransformMath.anchoredScale(
            sx: 2, sy: 2, size: CGSize(width: 100, height: 100),
            anchor: .center, anchorXPx: 0, anchorYPx: 0))
        XCTAssertEqual(scaleEffect.effectValue(size: CGSize(width: 100, height: 100)),
                       expectedScale)

        let rotateEffect = AnchoredRotateEffect(deg: 30, anchor: .center,
                                                anchorXPx: 10, anchorYPx: 20)
        let expectedRotate = ProjectionTransform(AnchoredTransformMath.anchoredRotation(
            deg: 30, size: CGSize(width: 100, height: 100),
            anchor: .center, anchorXPx: 10, anchorYPx: 20))
        XCTAssertEqual(rotateEffect.effectValue(size: CGSize(width: 100, height: 100)),
                       expectedRotate)
    }
}

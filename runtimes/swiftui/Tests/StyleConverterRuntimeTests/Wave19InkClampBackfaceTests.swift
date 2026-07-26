//
//  Wave19InkClampBackfaceTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 19, lane INK+CLAMP+BACKFACE — three scoped iOS fixes:
//
//  RC-B1  border/outline currentColor bottom-out is MODE-SPLIT
//         (BorderSideApplier.fallbackInk): dark stage keeps the harness
//         #eee (Color(white:0.933)) the 327 baselines pin; WPT capture
//         bottoms out at spec BLACK — a real WPT page's inherit chain
//         ends at the UA `color: CanvasText` (html.css), so `border: 1px
//         solid` (colour omitted → currentcolor, css-color-4 §7.1) must
//         paint black like the browser-ref. Compose twin: the
//         defaultTextInk routing in BorderSideExtractor/OutlineExtractor
//         (WptBorderInkTest.kt).
//
//  RC-B6a line-clamp's LIVE wire is the sealed `{"type":"lines",
//         "count":N}` object (wave18 css-overflow block-ellipsis-001);
//         extractInt only read value/numeric, so the clamp NEVER engaged.
//
//  RC-B4a backface culling on the ACCUMULATED matrix (css-transforms-2
//         §5.1) — see BackfaceCulling.swift.
//

import Foundation
import SwiftUI
import XCTest
// @testable: the appliers/extractors under pin are internal.
@testable import StyleConverterRuntime

final class Wave19InkClampBackfaceTests: XCTestCase {

    // ── RC-B1: the border/outline ink mode-split ─────────────────────────

    func testDarkStageKeepsTheHarnessEeeBottomOut() {
        // wpt=false (the default everywhere outside the TITAN capture
        // path) → the historical #eee family — the committed baselines
        // depend on this side never moving.
        XCTAssertEqual(
            BorderSideApplier.fallbackInk(currentColor: nil, wptCaptureMode: false),
            Color(white: 0.933))
    }

    func testWptCaptureBottomsBorderInkOutAtSpecBlack() {
        // wpt=true → the corpus-v4.1 CanvasText black, via the SAME pinned
        // WPTCanvas.captureTextInk split text ink uses (css-break
        // background-image-000's `border: 1px solid` frames).
        XCTAssertEqual(
            BorderSideApplier.fallbackInk(currentColor: nil, wptCaptureMode: true),
            WPTCanvas.textInk)
    }

    func testElementColourWinsOverTheModeSplit() {
        // currentColor resolves to the element's own `color` FIRST in both
        // modes — the split only governs the bottom of the chain.
        let teal = Color(.sRGB, red: 0, green: 0.5, blue: 0.5, opacity: 1)
        XCTAssertEqual(
            BorderSideApplier.fallbackInk(currentColor: teal, wptCaptureMode: true), teal)
        XCTAssertEqual(
            BorderSideApplier.fallbackInk(currentColor: teal, wptCaptureMode: false), teal)
    }

    // ── RC-B6a: line-clamp live wire ─────────────────────────────────────

    // Succinct IR builders (the TransformsTests pattern).
    private func obj(_ d: [String: IRValue]) -> IRValue { .object(d) }
    private func clampProps(_ data: IRValue) -> [IRProperty] {
        [IRProperty(type: "LineClamp", data: data)]
    }

    func testLineClampParsesTheLiveLinesCountWire() {
        // The EXACT block-ellipsis-001 payload: {"type":"lines","count":2}.
        // Before RC-B6a this decoded to lines=nil (extractInt never reads
        // `count`) and the clamp never engaged — 3 rendered lines / 62px
        // vs the ref's 2-line / 41px box.
        let cfg = LineClampExtractor.extract(from: clampProps(
            obj(["type": .string("lines"), "count": .int(2)])))
        XCTAssertEqual(cfg?.lines, 2)
    }

    func testLineClampCountAcceptsJsonDoubles() {
        // Converter floats ({count: 3.0}) fold like Compose's floatOrNull.
        let cfg = LineClampExtractor.extract(from: clampProps(
            obj(["type": .string("lines"), "count": .double(3.0)])))
        XCTAssertEqual(cfg?.lines, 3)
    }

    func testLineClampNoneAndSubOneCountsClampOff() {
        // {"type":"none"} → off (the keyword branch reads the type key)…
        let none = LineClampExtractor.extract(from: clampProps(
            obj(["type": .string("none")])))
        XCTAssertNotNil(none)
        XCTAssertNil(none?.lines)
        // …and a sub-1 count is outside the css-overflow-4 §5 grammar
        // (<integer [1,∞]>) → off, not a 0-line clamp.
        let zero = LineClampExtractor.extract(from: clampProps(
            obj(["type": .string("lines"), "count": .int(0)])))
        XCTAssertNil(zero?.lines)
    }

    func testLineClampLegacyIntegerStillParses() {
        // The pre-existing bare-integer path must keep working.
        let cfg = LineClampExtractor.extract(from: clampProps(.int(4)))
        XCTAssertEqual(cfg?.lines, 4)
    }

    // ── RC-B4a: backface culling on the accumulated matrix ───────────────

    // Aggregate builders for the truth table.
    private func rotY(_ deg: CGFloat, hidden: Bool = true) -> TransformsAggregate {
        var a = TransformsAggregate()
        if deg != 0 { a.functions = [.rotate(x: 0, y: 1, z: 0, deg: deg)] }
        a.backfaceHidden = hidden
        a.touched = true
        return a
    }
    private func yMatrix(_ deg: CGFloat) -> CATransform3D {
        CATransform3DMakeRotation(deg * .pi / 180, 0, 1, 0)
    }

    func testOwnRotationOnlyTruthTable() {
        // No ancestors (identity context): 180° back-facing → culled;
        // 0° and 45° front-facing → kept. cos(θ) is the §5.1 z sign.
        XCTAssertTrue(BackfaceCulling.isCulled(agg: rotY(180), inherited: CATransform3DIdentity))
        XCTAssertFalse(BackfaceCulling.isCulled(agg: rotY(0), inherited: CATransform3DIdentity))
        XCTAssertFalse(BackfaceCulling.isCulled(agg: rotY(45), inherited: CATransform3DIdentity))
        // visibility: visible never culls, whatever the rotation.
        XCTAssertFalse(BackfaceCulling.isCulled(agg: rotY(180, hidden: false),
                                                inherited: CATransform3DIdentity))
    }

    func testAncestorContextReachesTheCullingDecision() {
        // backface-visibility-hidden-001: container rotateY(45°) context.
        // GREEN face (own 0°): accumulated 45° → front-facing → VISIBLE.
        XCTAssertFalse(BackfaceCulling.isCulled(agg: rotY(0), inherited: yMatrix(45)))
        // RED face (own 180°): accumulated 225° → back-facing → HIDDEN.
        XCTAssertTrue(BackfaceCulling.isCulled(agg: rotY(180), inherited: yMatrix(45)))
    }

    func testEdgeOnPlaneStaysVisible() {
        // §5.1 hides on STRICTLY negative z: an exactly edge-on 90° plane
        // (cos 90° == 0 up to fp noise) must not cull. Use the pure z to
        // avoid asserting through fp equality on the composed matrix.
        let z = BackfaceCulling.backfaceZ(yMatrix(90))
        XCTAssertEqual(z, 0, accuracy: 1e-9)
        XCTAssertFalse(z < 0)
    }

    func testMixedAxisRotationUsesRealMatrixMath() {
        // The old heuristic SUMMED X and Y degrees: rotateX(90)rotateY(90)
        // summed to 180 → culled, though the true composed rotation maps
        // the normal to ±x (z component 0, not negative). The matrix test
        // gets it right.
        var a = TransformsAggregate()
        a.functions = [.rotate(x: 1, y: 0, z: 0, deg: 90),
                       .rotate(x: 0, y: 1, z: 0, deg: 90)]
        a.backfaceHidden = true
        a.touched = true
        XCTAssertFalse(BackfaceCulling.isCulled(agg: a, inherited: CATransform3DIdentity))
    }

    func testContextPublicationRule() {
        // perspective ≠ none ESTABLISHES a 3D rendering context (§4)…
        var container = rotY(45, hidden: false)
        container.perspective = PerspectiveValue(distancePx: 1000)
        XCTAssertTrue(BackfaceCulling.publishes3DContext(container))
        // …preserve-3d EXTENDS one…
        var card = TransformsAggregate()
        card.preserve3D = true
        card.touched = true
        XCTAssertTrue(BackfaceCulling.publishes3DContext(card))
        // …and a touched FLAT element does neither (its subtree flattens).
        XCTAssertFalse(BackfaceCulling.publishes3DContext(rotY(45)))
    }

    func testAccumulationComposesRotationsAboutTheSameAxis() {
        // rotateY(45) context · rotateY(180) own == rotateY(225): the z
        // sign of the accumulated normal is cos(225°) < 0.
        let acc = BackfaceCulling.accumulate(own: yMatrix(180), inherited: yMatrix(45))
        XCTAssertEqual(BackfaceCulling.backfaceZ(acc),
                       cos(225 * CGFloat.pi / 180), accuracy: 1e-9)
    }
}

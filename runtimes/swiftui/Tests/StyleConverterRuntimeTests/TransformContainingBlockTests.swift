//
//  TransformContainingBlockTests.swift
//  Wave 35 (lane B1) — XCTest pins for the transform containing-block rule
//  table (css-transforms-1 §3 / css-transforms-2 §8), for the FixedHoist
//  strip it vetoes, and for the singular-transform paint rule.
//
//  THE CROSS-NATIVE PIN. `shapeMatrix` below is the SAME matrix, in the
//  SAME order, as the Kotlin twin's `TransformContainingBlockTest
//  .shapeMatrix` (runtimes/compose/src/test/java/com/styleconverter/
//  runtime/layout/position/TransformContainingBlockTest.kt). The two rule
//  tables are only "byte-parallel" if both suites accept the identical
//  rows, so a clause added to one platform and forgotten on the other
//  fails HERE rather than three waves later on a device.
//
//  Every expected value is browser-derived, not spec-derived — the
//  campaign scores against Chromium refs and Blink's
//  HasTransformRelatedProperty is broader than the prose. The measurement
//  is _diag35/laneB1/probe-chromium.mjs (six nesting probes,
//  getBoundingClientRect at 390×600); the `none`-form wire shapes are from
//  a live conversion of _diag35/laneB1/tf-none.json.
//

import XCTest
import SwiftUI
import QuartzCore
@testable import StyleConverterRuntime

final class TransformContainingBlockTests: XCTestCase {

    // MARK: - Wire helpers

    /// Decode a property list from wire-shaped JSON — the same decode path
    /// production IR takes, so these pins exercise real extraction.
    private func props(_ json: String) throws -> [IRProperty] {
        try JSONDecoder().decode([IRProperty].self, from: Data(json.utf8))
    }

    private func component(_ json: String) throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8))
    }

    /// (label, declarations JSON, establishes?) — clause-by-clause, used
    /// value first then that clause's `none` form, so a diff against the
    /// Kotlin twin reads line for line.
    private var shapeMatrix: [(String, String, Bool)] {
        [
            // ── no candidate property at all ─────────────────────────────
            ("bare box", "[]", false),
            ("position:relative only", #"[{"type":"Position","data":"RELATIVE"}]"#, false),
            // ── transform (css-transforms-1 §3, probe B/C) ───────────────
            ("transform: translateX(0)",
             #"[{"type":"Transform","data":{"type":"functions","list":[{"fn":"translateX","x":{"px":0}}]}}]"#,
             true),
            ("transform: none",
             #"[{"type":"Transform","data":{"type":"functions","list":[]}}]"#,
             false),
            // ── individual transform properties (css-transforms-2 §5) ────
            ("rotate: 45deg", #"[{"type":"Rotate","data":{"type":"angle","deg":45}}]"#, true),
            ("rotate: none", #"[{"type":"Rotate","data":{"type":"none"}}]"#, false),
            ("scale: none", #"[{"type":"Scale","data":{"type":"none"}}]"#, false),
            ("translate: none", #"[{"type":"Translate","data":{"type":"none"}}]"#, false),
            // ── perspective (css-transforms-2 §8, probe D) ───────────────
            ("perspective: 500px",
             #"[{"type":"Perspective","data":{"type":"length","px":500}}]"#, true),
            ("perspective: none",
             #"[{"type":"Perspective","data":{"type":"none"}}]"#, false),
            // ── transform-style (Blink preserves3D; probe E — NOT prose) ──
            ("transform-style: preserve-3d",
             #"[{"type":"TransformStyle","data":"PRESERVE_3D"}]"#, true),
            ("transform-style: flat",
             #"[{"type":"TransformStyle","data":"FLAT"}]"#, false),
            // ── will-change (Blink transform hint) ───────────────────────
            ("will-change: transform",
             #"[{"type":"WillChange","data":[{"type":"property-name","name":"transform"}]}]"#,
             true),
            ("will-change: opacity",
             #"[{"type":"WillChange","data":[{"type":"property-name","name":"opacity"}]}]"#,
             false),
        ]
    }

    // MARK: - The rule table

    func testCrossNativeShapeMatrixHoldsClauseForClause() throws {
        for (label, json, expected) in shapeMatrix {
            XCTAssertEqual(TransformContainingBlock.establishes(try props(json)),
                           expected, label)
        }
    }

    // MARK: - The hoist truth table the rule table feeds

    func testFixedDescendantUnderTransformedAncestorIsNotStripped() throws {
        // The exact 3d-rendering-context-and-fixpos tree: a transformed
        // relative `.cb` → a plain static `.parent` → a fixed `.abspos`.
        // The middle box carries NO transform, so only OR-accumulation down
        // the chain can reach the fixed grandchild. Left in the tree, the
        // fixed box renders through `.parent`'s out-of-flow overlay, which
        // anchors it at `.cb`'s origin — what Chromium paints (probe C).
        let cb = try component(#"""
        {"id":"cb","name":"cb","properties":[
          {"type":"Position","data":"RELATIVE"},
          {"type":"TransformStyle","data":"PRESERVE_3D"},
          {"type":"Transform","data":{"type":"functions","list":[{"fn":"translateX","x":{"px":0}}]}}],
         "children":[{"id":"parent","name":"parent","properties":[],
           "children":[{"id":"abspos","name":"abspos","properties":[
             {"type":"Position","data":"FIXED"},
             {"type":"Top","data":{"px":0}},{"type":"Left","data":{"px":0}}]}]}]}
        """#)
        let split = FixedHoist.split(roots: [cb])
        XCTAssertTrue(split.hoisted.isEmpty,
                      "no box may hoist out of a transformed subtree")
        // The subtree came back INTACT — the fixed grandchild is still
        // under `.parent`, so the renderer's overlay can find it.
        XCTAssertEqual(split.flow.first?.children?.first?.children?.first?.id, "abspos")
    }

    func testFixedDescendantWithoutTransformedAncestorStillStrips() throws {
        // The control that proves the FLAG, not the tree shape, is the
        // cause: the identical tree minus the transform hoists again (pin
        // S3 — a fixed box under a merely `position: relative` ancestor
        // still anchors at the viewport, css-position-3 §2.1).
        let cb = try component(#"""
        {"id":"cb","name":"cb","properties":[{"type":"Position","data":"RELATIVE"}],
         "children":[{"id":"parent","name":"parent","properties":[],
           "children":[{"id":"abspos","name":"abspos","properties":[
             {"type":"Position","data":"FIXED"},
             {"type":"Top","data":{"px":0}},{"type":"Left","data":{"px":0}}]}]}]}
        """#)
        let split = FixedHoist.split(roots: [cb])
        XCTAssertEqual(split.hoisted.map(\.id), ["abspos"])
    }

    func testATransformedROOTStillClaimsItsOwnFixedDescendants() throws {
        // A root has no ancestors, so the walk starts with the flag clear —
        // but the root's OWN transform must be folded in one level down, or
        // a fixed child of a transformed root would escape.
        let root = try component(#"""
        {"id":"root","name":"root","properties":[
          {"type":"Transform","data":{"type":"functions","list":[{"fn":"translateX","x":{"px":0}}]}}],
         "children":[{"id":"fx","name":"fx","properties":[
           {"type":"Position","data":"FIXED"},{"type":"Top","data":{"px":0}}]}]}
        """#)
        let split = FixedHoist.split(roots: [root])
        XCTAssertTrue(split.hoisted.isEmpty)
        XCTAssertEqual(split.flow.first?.children?.first?.id, "fx")
    }

    func testTheDefaultArgumentKeepsEveryPreWave35CallSiteIdentical() throws {
        // Additive-parameter discipline: omitting the flag must reproduce
        // the wave-17 always-strip behaviour exactly.
        let parent = try component(#"""
        {"id":"p","name":"p","properties":[],
         "children":[{"id":"fx","name":"fx","properties":[
           {"type":"Position","data":"FIXED"},{"type":"Top","data":{"px":0}}]}]}
        """#)
        XCTAssertEqual(FixedHoist.strippingFixedDescendants(parent).hoisted.map(\.id),
                       FixedHoist.strippingFixedDescendants(
                        parent, hasTransformedAncestor: false).hoisted.map(\.id))
    }

    // MARK: - Singular transforms (css-transforms-1 §3, iOS-only repair)

    func testSingularRotationsAreDetectedIndependentlyOfPerspective() {
        // The degenerate angles: a ±90°/±270° rotation about X or Y
        // collapses the projected box to a line. Verified numerically
        // against the same CATransform3D arithmetic in
        // _diag35/laneB1/singular-math.mjs before the change.
        XCTAssertTrue(SingularTransform.isSingularRotation(
            axisX: 1, axisY: 0, axisZ: 0, deg: 90, perspectivePx: nil))
        XCTAssertTrue(SingularTransform.isSingularRotation(
            axisX: 1, axisY: 0, axisZ: 0, deg: -90, perspectivePx: nil))
        XCTAssertTrue(SingularTransform.isSingularRotation(
            axisX: 0, axisY: 1, axisZ: 0, deg: 270, perspectivePx: nil))
        // A perspective prefix does NOT rescue it — the box is still
        // edge-on, and Chromium still paints nothing.
        XCTAssertTrue(SingularTransform.isSingularRotation(
            axisX: 1, axisY: 0, axisZ: 0, deg: 90, perspectivePx: 1000))
    }

    func testNonDegenerateRotationsAreNotSingular() {
        // The guard against over-hiding: everything the committed baselines
        // actually contain must stay visible.
        XCTAssertFalse(SingularTransform.isSingularRotation(
            axisX: 0, axisY: 1, axisZ: 0, deg: 45, perspectivePx: nil))
        XCTAssertFalse(SingularTransform.isSingularRotation(
            axisX: 0, axisY: 1, axisZ: 0, deg: 180, perspectivePx: nil))
        XCTAssertFalse(SingularTransform.isSingularRotation(
            axisX: 1, axisY: 0, axisZ: 0, deg: 45, perspectivePx: 1000))
        // A 2D rotate is (0,0,1) and never leaves the plane.
        XCTAssertFalse(SingularTransform.isSingularRotation(
            axisX: 0, axisY: 0, axisZ: 1, deg: 90, perspectivePx: nil))
        // Zero angle / null axis are the identity, not a degeneracy.
        XCTAssertFalse(SingularTransform.isSingularRotation(
            axisX: 1, axisY: 0, axisZ: 0, deg: 0, perspectivePx: nil))
        XCTAssertFalse(SingularTransform.isSingularRotation(
            axisX: 0, axisY: 0, axisZ: 0, deg: 90, perspectivePx: nil))
    }

    func testTheEpsilonLeavesNearlyEdgeOnBoxesVisible() {
        // 89.999° is 5 orders of magnitude above the threshold — a browser
        // still paints its sliver, and so must we.
        XCTAssertFalse(SingularTransform.isSingularRotation(
            axisX: 1, axisY: 0, axisZ: 0, deg: 89.999, perspectivePx: nil))
    }

    func testTheAggregateLevelPredicateSeesBothChannels() {
        // The applier asks about the whole aggregate: the function LIST and
        // the `rotate` LONGHAND both feed the same decision.
        var listAgg = TransformsAggregate()
        listAgg.functions = [.rotate(x: 1, y: 0, z: 0, deg: 90)]
        XCTAssertTrue(SingularTransform.isSingular(listAgg))

        var longhandAgg = TransformsAggregate()
        longhandAgg.rotate = .rotate(x: 0, y: 1, z: 0, deg: 90)
        XCTAssertTrue(SingularTransform.isSingular(longhandAgg))

        // An untouched aggregate, and a benign 2D one, are never singular.
        XCTAssertFalse(SingularTransform.isSingular(TransformsAggregate()))
        var benign = TransformsAggregate()
        benign.functions = [.translate(x: 10, y: 20, z: 0), .rotate(x: 0, y: 0, z: 1, deg: 45)]
        XCTAssertFalse(SingularTransform.isSingular(benign))
    }

    func testTheDeterminantSliceIsTheProjectiveOneSwiftUIMustInvert() {
        // Identity is 1; the rotateX(90°) slice is 0. Pinning the slice
        // itself (not just the boolean) is what makes the epsilon
        // meaningful — the 4×4 determinant of a rotation is always ±1 and
        // would never have caught this.
        XCTAssertEqual(SingularTransform.projectionSliceDeterminant(CATransform3DIdentity),
                       1, accuracy: 1e-12)
        let edgeOn = CATransform3DRotate(CATransform3DIdentity, .pi / 2, 1, 0, 0)
        XCTAssertEqual(SingularTransform.projectionSliceDeterminant(edgeOn),
                       0, accuracy: 1e-12)
    }
}

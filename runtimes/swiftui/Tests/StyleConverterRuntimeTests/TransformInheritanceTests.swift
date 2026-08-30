//
//  TransformInheritanceTests.swift
//  StyleConverterRuntimeTests — wave 49, lane A6.
//
//  Pins `transform: inherit` (css-cascade-4 §7.3.2). Both IR payloads are
//  VERBATIM from the wave-48 corpus per-test IR
//  (tools/titan/runs/wave48-final/sections/css-transforms/per-test-ir/
//  wpt__css-transforms__css-transform-inherit-scale.json), decoded through
//  the real IRComponent decoder and the real TransformsExtractor.
//

import Foundation
import XCTest
@testable import StyleConverterRuntime

final class TransformInheritanceTests: XCTestCase {

    // `.parent` — 50x50, `transform: scale(2)`.
    private func parentComponent() throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data("""
        {
          "id": "css-transform-inherit-scale__1__1-155",
          "name": "css-transform-inherit-scale__1__1",
          "properties": [
            {"type": "Width", "data": {"type": "length", "px": 50}},
            {"type": "Height", "data": {"type": "length", "px": 50}},
            {"type": "Position", "data": "ABSOLUTE"},
            {"type": "Top", "data": {"px": 75}},
            {"type": "Left", "data": {"px": 75}},
            {"type": "Transform", "data": {"type": "functions",
                                           "list": [{"fn": "scale", "x": 2, "y": 2}]}}
          ]
        }
        """.utf8))
    }

    // `.child` — 50x50, `transform: inherit`.
    private func childComponent() throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data("""
        {
          "id": "css-transform-inherit-scale__1__1__0-156",
          "name": "css-transform-inherit-scale__1__1__0",
          "properties": [
            {"type": "Position", "data": "ABSOLUTE"},
            {"type": "Transform", "data": {"type": "keyword", "keyword": "inherit"}},
            {"type": "Width", "data": {"type": "length", "px": 50}},
            {"type": "Height", "data": {"type": "length", "px": 50}}
          ]
        }
        """.utf8))
    }

    /// The keyword payload must reach the aggregate at all. Before wave 49
    /// the extractor's `{"type":"functions"}` guard rejected it and the
    /// whole property vanished — a silent drop, not a refusal.
    func testInheritKeywordIsExtractedAndFlagged() throws {
        let agg = try XCTUnwrap(TransformsExtractor.extract(from: childComponent().properties))
        XCTAssertTrue(agg.touched)
        XCTAssertTrue(agg.transformInherits)
        // The keyword replaces the entire value, so nothing is declared.
        XCTAssertTrue(agg.functions.isEmpty)
    }

    /// Resolution against the ancestor channel: the child must end up with
    /// the parent's own list. 50 px × 2 (own, now present) × 2 (ancestor,
    /// applied by the view hierarchy) = the 200 px green square the frozen
    /// reference paints.
    func testInheritResolvesToTheParentsComputedList() throws {
        let parent = try XCTUnwrap(TransformsExtractor.extract(from: parentComponent().properties))
        let child = try XCTUnwrap(TransformsExtractor.extract(from: childComponent().properties))
        let resolved = TransformInheritance.resolve(
            child, inherited: TransformInheritance.published(parent))
        XCTAssertEqual(resolved.functions.count, 1)
        guard case .scale(let x, let y, _) = resolved.functions.first else {
            return XCTFail("inherited value is not a scale")
        }
        XCTAssertEqual(x, 2, accuracy: 1e-6)
        XCTAssertEqual(y, 2, accuracy: 1e-6)
    }

    /// A chain of `inherit` carries the same matrix down — §7.3.2 is per
    /// property, applied at every level, so what an inheriting element
    /// publishes is what it resolved to.
    func testInheritChainRepublishesTheResolvedValue() throws {
        let parent = try XCTUnwrap(TransformsExtractor.extract(from: parentComponent().properties))
        let child = try XCTUnwrap(TransformsExtractor.extract(from: childComponent().properties))
        let resolved = TransformInheritance.resolve(
            child, inherited: TransformInheritance.published(parent))
        let grandchild = TransformInheritance.resolve(
            child, inherited: TransformInheritance.published(resolved))
        XCTAssertEqual(grandchild.functions.count, 1)
    }

    /// No ancestor transform → `inherit` computes to `none`, not to some
    /// stale value. This is the default of the environment channel.
    func testInheritWithNoAncestorTransformIsNone() throws {
        let child = try XCTUnwrap(TransformsExtractor.extract(from: childComponent().properties))
        let resolved = TransformInheritance.resolve(child, inherited: [])
        XCTAssertTrue(resolved.functions.isEmpty)
    }

    /// A non-inheriting aggregate must pass through byte-identical, so the
    /// channel cannot disturb any existing render.
    func testNonInheritingAggregateIsUntouched() throws {
        let parent = try XCTUnwrap(TransformsExtractor.extract(from: parentComponent().properties))
        let resolved = TransformInheritance.resolve(
            parent, inherited: [.translate(x: 999, y: 999, z: 0, xFrac: 0, yFrac: 0)])
        XCTAssertEqual(resolved.functions.count, 1)
        guard case .scale = resolved.functions.first else {
            return XCTFail("a declared transform must not be replaced")
        }
    }

    /// The other CSS-wide keywords. `transform` is NOT inherited, so
    /// css-cascade-4 §7.3 makes `unset` act as `initial`, and
    /// css-transforms-1 §3's initial value is `none` — touched, empty,
    /// and crucially NOT flagged as inheriting.
    func testUnsetAndInitialComputeToNone() throws {
        for keyword in ["initial", "unset", "revert", "revert-layer"] {
            let props = [IRProperty(type: "Transform",
                                    data: .object(["type": .string("keyword"),
                                                   "keyword": .string(keyword)]))]
            let agg = try XCTUnwrap(TransformsExtractor.extract(from: props),
                                    "\(keyword) produced no aggregate")
            XCTAssertTrue(agg.touched, "\(keyword) must mark the property present")
            XCTAssertFalse(agg.transformInherits, "\(keyword) must not inherit")
            XCTAssertTrue(agg.functions.isEmpty, "\(keyword) must compute to none")
        }
    }
}

//
//  ZoomTests.swift
//  StyleConverterRuntimeTests
//
//  Wave-37 zoom convergence — unit pins for the iOS half of CSS `zoom`
//  (css-viewport-1 §"The zoom property"). Byte-parallel twin of the
//  Compose `ZoomTest.kt`.
//
//  ── What is being protected ───────────────────────────────────────────
//  `zoom` used to reach iOS only through the rendering-hint bag, whose
//  generic `ValueExtractors.extractKeyword` fold reads
//  `{"type":"number","value":1.5}` as the STRING "number" — so the factor
//  was silently dropped while the property still looked "registered", and
//  the three Zoom_* cells of fixtures/visual-test.json diverged from the
//  web engine that does render zoom. The payloads below are the LIVE wire
//  shapes from out/tmpOutput.json for that fixture.
//
//  ── The invariant that must never break ───────────────────────────────
//  `hasZoom` must stay FALSE for every element outside a real factor —
//  absent property, `normal`, `reset`, explicit `zoom: 1`, and any
//  non-positive / non-finite value — because a true `hasZoom` inserts a
//  ZoomLayout node and a geometry effect, which would perturb the
//  committed 327-pair baseline everywhere else in the corpus.
//

import XCTest
// @testable: ZoomConfig / ZoomExtractor are internal to the module.
@testable import StyleConverterRuntime

final class ZoomTests: XCTestCase {

    private func props(_ v: IRValue) -> [IRProperty] {
        [IRProperty(type: "Zoom", data: v)]
    }

    // MARK: - Wire shapes (one per ZoomValue sealed variant)

    func testNumberVariantCarriesTheFactorVerbatim() {
        let c = ZoomExtractor.extract(from: props(.object([
            "type": .string("number"), "value": .double(1.5),
        ])))
        XCTAssertEqual(c?.factor ?? 0, 1.5, accuracy: 1e-6)
        XCTAssertEqual(c?.isNormal, false)
        XCTAssertEqual(c?.hasZoom, true)
    }

    /// The suffix is load-bearing: reading 150% as 150 would zoom two
    /// orders of magnitude too far.
    func testPercentageVariantDividesBy100() {
        let c = ZoomExtractor.extract(from: props(.object([
            "type": .string("percentage"), "value": .double(150),
        ])))
        XCTAssertEqual(c?.factor ?? 0, 1.5, accuracy: 1e-6)
        XCTAssertEqual(c?.hasZoom, true)
    }

    func testSubUnitFactorsSurvive() {
        let c = ZoomExtractor.extract(from: props(.object([
            "type": .string("number"), "value": .double(0.75),
        ])))
        XCTAssertEqual(c?.factor ?? 0, 0.75, accuracy: 1e-6)
    }

    /// css-viewport-1: `normal` computes to the same used scale as 1.
    /// `reset` is the legacy WebKit keyword a browser rejects outright, so
    /// an unzoomed render is the CONVERGENT reading, not a gap.
    func testNormalAndResetAreIdentity() {
        for tag in ["normal", "reset"] {
            let c = ZoomExtractor.extract(from: props(.object(["type": .string(tag)])))
            XCTAssertEqual(c?.factor ?? 0, 1.0, accuracy: 1e-6, "zoom: \(tag)")
            XCTAssertEqual(c?.isNormal, true, "zoom: \(tag)")
            XCTAssertEqual(c?.hasZoom, false, "zoom: \(tag) must not chain a scale node")
        }
    }

    func testExplicitZoomOneDoesNotChainANode() {
        let c = ZoomExtractor.extract(from: props(.object([
            "type": .string("number"), "value": .int(1),
        ])))
        XCTAssertEqual(c?.hasZoom, false)
    }

    /// The layout node divides the proposal by the factor; <= 0 would
    /// produce negative extents and a non-finite one would poison measure.
    func testNonPositiveFactorsDegradeToIdentity() {
        for v in [0.0, -2.0] {
            let c = ZoomExtractor.extract(from: props(.object([
                "type": .string("number"), "value": .double(v),
            ])))
            XCTAssertEqual(c?.hasZoom, false, "zoom: \(v) must not reach ZoomLayout")
        }
    }

    func testUnknownVariantIsIdentity() {
        let c = ZoomExtractor.extract(from: props(.object(["type": .string("future")])))
        XCTAssertEqual(c?.hasZoom, false)
    }

    /// Absent property → nil, so `engineZoom` chains as pure identity and
    /// the rest of the corpus keeps a byte-identical view tree.
    func testAbsentPropertyYieldsNil() {
        XCTAssertNil(ZoomExtractor.extract(from: []))
        XCTAssertNil(ZoomExtractor.extract(from: [IRProperty(type: "Opacity", data: .double(0.5))]))
    }

    func testLastWriteWins() {
        let c = ZoomExtractor.extract(from: [
            IRProperty(type: "Zoom", data: .object(["type": .string("number"), "value": .double(2)])),
            IRProperty(type: "Zoom", data: .object(["type": .string("percentage"), "value": .double(50)])),
        ])
        XCTAssertEqual(c?.factor ?? 0, 0.5, accuracy: 1e-6)
    }

    func testBarePayloadsStillDecode() {
        XCTAssertEqual(ZoomExtractor.extract(from: props(.double(2)))?.factor ?? 0, 2, accuracy: 1e-6)
        XCTAssertEqual(ZoomExtractor.extract(from: props(.string("normal")))?.hasZoom, false)
        XCTAssertEqual(ZoomExtractor.extract(from: props(.string("1.5")))?.factor ?? 0, 1.5, accuracy: 1e-6)
    }

    // MARK: - StyleBuilder wiring

    /// The factor has to survive the full build, not just the extractor —
    /// this is the pin that would have caught the rendering-hint bag
    /// swallowing it.
    func testStyleBuilderThreadsTheFactor() {
        let style = StyleBuilder.build(from: [
            IRProperty(type: "Zoom", data: .object(["type": .string("number"), "value": .double(1.5)])),
        ])
        XCTAssertEqual(style.zoom?.factor ?? 0, 1.5, accuracy: 1e-6)
        XCTAssertEqual(style.zoom?.hasZoom, true)
    }

    func testStyleBuilderLeavesZoomNilWhenAbsent() {
        let style = StyleBuilder.build(from: [
            IRProperty(type: "Opacity", data: .double(0.5)),
        ])
        XCTAssertNil(style.zoom)
    }
}

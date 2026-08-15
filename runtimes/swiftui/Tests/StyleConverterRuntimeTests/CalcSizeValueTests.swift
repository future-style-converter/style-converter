//
//  CalcSizeValueTests.swift
//  Wave 42 (lane W3) — css-values-5 calc-size() typed decode + arithmetic.
//
//  The wire payloads are VERBATIM converter output (checked against
//  `:converter:run` on fixtures/wpt/css-values/calc-size__calc-size-flex-002:
//    {"type":"calc-size","basis":"auto","factor":1,"offsetPx":40,
//     "original":"calc-size(auto, size + 40px)"}
//  ). The Layout half (CalcSizeSizeLayout) needs a SwiftUI layout pass, so —
//  the standing shape of this suite — every decision it makes that CAN be
//  pure lives in CalcSizeValue and is pinned here; the extractor-routing
//  pins prove the exclusive slot contract (calc XOR LengthValue).
//

import XCTest
@testable import StyleConverterRuntime

final class CalcSizeValueTests: XCTestCase {

    // Build the verbatim wire object for a calc-size payload.
    private func wire(basis: String, factor: Double, offset: Double) -> IRValue {
        .object([
            "type": .string("calc-size"),
            "basis": .string(basis),
            "factor": .double(factor),
            "offsetPx": .double(offset),
            "original": .string("calc-size(\(basis), size + \(offset)px)"),
        ])
    }

    // ── wire decode ──────────────────────────────────────────────────────

    func testDecodesTheVerbatimConverterWire() {
        // calc-size-flex-002's min-width payload shape.
        let v = CalcSizeValue.decode(wire(basis: "auto", factor: 1, offset: 40))
        XCTAssertEqual(v, CalcSizeValue(basis: .auto, factor: 1, offsetPx: 40))
    }

    func testDecodeIsAdditiveEveryOtherShapeRefuses() {
        // The neighbouring sizing shapes must keep their existing routes.
        XCTAssertNil(CalcSizeValue.decode(.object(["type": .string("length"),
                                                   "px": .double(100)])))
        XCTAssertNil(CalcSizeValue.decode(.string("auto")))
        XCTAssertNil(CalcSizeValue.decode(nil))
        // Unknown basis keyword = wire drift — refuse, never guess.
        XCTAssertNil(CalcSizeValue.decode(.object(["type": .string("calc-size"),
                                                   "basis": .string("anchor")])))
        // Discriminator without a basis is malformed — refuse.
        XCTAssertNil(CalcSizeValue.decode(.object(["type": .string("calc-size")])))
    }

    func testFactorAndOffsetDefaultToTheIdentityExpression() {
        // Same degradation as the web/Compose twins: `calc-size(b, size)`.
        let v = CalcSizeValue.decode(.object(["type": .string("calc-size"),
                                              "basis": .string("fit-content")]))
        XCTAssertEqual(v, CalcSizeValue(basis: .fitContent, factor: 1, offsetPx: 0))
    }

    // ── affine arithmetic ────────────────────────────────────────────────

    func testTargetPxEvaluatesTheCorpusFamily() {
        // min-max-001: span min-content 20, size + 80px → the ref's 100.
        XCTAssertEqual(CalcSizeValue(basis: .auto, factor: 1, offsetPx: 80).targetPx(20), 100)
        // min-max-003: 20 + 70 = 90 (the documented one-intrinsic
        // approximation — vs the ref's avail-clamped 100).
        XCTAssertEqual(CalcSizeValue(basis: .auto, factor: 1, offsetPx: 70).targetPx(20), 90)
        // flex-007's height: size / 2 over an 80 basis → 40.
        XCTAssertEqual(CalcSizeValue(basis: .auto, factor: 0.5, offsetPx: 0).targetPx(80), 40)
    }

    func testTargetPxFloorsNegativeResultsAtZero() {
        // aspect-ratio-003/004's `size - 50px` over a small basis must
        // clamp to 0 (non-negative sizing value space), keeping those
        // currently-PASSING cells unmoved.
        XCTAssertEqual(CalcSizeValue(basis: .auto, factor: 1, offsetPx: -50).targetPx(0), 0)
        XCTAssertEqual(CalcSizeValue(basis: .auto, factor: 1, offsetPx: -50).targetPx(30), 0)
    }

    // ── extractor routing (exclusive slots) ──────────────────────────────

    func testWidthRoutesToTheCalcSlotExclusively() {
        // calc-size-min-max-sizes-004's width property, verbatim shape.
        let cfg = SizeExtractor.extract(from: [
            IRProperty(type: "Width",
                       data: wire(basis: "fit-content", factor: 1, offset: 80)),
        ])
        // The calc slot carries it; the LengthValue slot stays empty so
        // no exact .frame can fight the layout wrapper.
        XCTAssertEqual(cfg.widthCalc,
                       CalcSizeValue(basis: .fitContent, factor: 1, offsetPx: 80))
        XCTAssertNil(cfg.width)
        // And the config counts as sized (the applier must not fast-skip).
        XCTAssertTrue(cfg.hasAny)
    }

    func testMinWidthCalcSizeKeepsTheIntrinsicFallback() {
        // THE MEASURED CONTRACT: calc-size-flex-001..006 PASS on iOS
        // because a min-width the engine cannot resolve degrades to "no
        // constraint" and the flex item renders its content-based minimum
        // (0.9751..0.9819). The typed wire must keep that exact behavior:
        // decode on the Min slot is deliberately NOT wired (see
        // SizeExtractor's calc-size comment).
        let cfg = SizeExtractor.extract(from: [
            IRProperty(type: "MinWidth",
                       data: wire(basis: "auto", factor: 1, offset: 20)),
        ])
        // No calc slot for min-width…
        XCTAssertNil(cfg.widthCalc)
        // …and the LengthValue slot holds `.unknown`, which
        // SizeApplierResolve.constraint maps to nil — no constraint, the
        // pre-typed-wire geometry byte-for-byte.
        XCTAssertEqual(cfg.minWidth, LengthValue.unknown)
    }
}

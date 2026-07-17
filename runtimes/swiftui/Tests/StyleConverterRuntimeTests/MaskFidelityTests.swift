//
//  MaskFidelityTests.swift
//  StyleConverterRuntimeTests
//
//  Lane-M mask fixes, iOS side. Two diagnosed divergences:
//   1. VERTICAL INVERSION — MaskApplier's old private startPoint/
//      endPoint used y-UP trig, so `mask-image: linear-gradient(black,
//      transparent)` (180deg default) faded bottom→top while web faded
//      top→bottom. Fix: delegate to GradientApplier.linearEndpoints,
//      the background path's css-images-3 §3.1.1 math — pinned here.
//   2. REPEATING CLAMP — `repeating-linear-gradient` collapsed to the
//      plain linear case (no flag on MaskLayer), and SwiftUI's Gradient
//      clamps after its last stop: one period then a solid clamp
//      instead of §3.4.3 tiling. Fix: repeating flag + period-expanded
//      stops (expandRepeatingStops) — pure math pinned here.
//

import Foundation
// SwiftUI: Gradient.Stop is the stop currency of MaskLayer.
import SwiftUI
import XCTest
// @testable: MaskApplier's helpers and the extractor are internal.
@testable import StyleConverterRuntime

final class MaskFidelityTests: XCTestCase {

    // ── 1. Linear endpoints: delegation + direction ──────────────────

    func testLinearMaskEndpointsDelegateToBackgroundMath() {
        // The mask path must be bit-identical to GradientApplier at any
        // angle/box — one owner for the §3.1.1 geometry.
        let size = CGSize(width: 160, height: 80)
        for deg in [0.0, 45.0, 90.0, 135.0, 180.0, 270.0] {
            let mask = MaskApplier.linearMaskEndpoints(angleDeg: deg, size: size)
            let bg = GradientApplier.linearEndpoints(angleDeg: deg, size: size)
            XCTAssertEqual(mask.start, bg.start, "start diverged at \(deg)deg")
            XCTAssertEqual(mask.end, bg.end, "end diverged at \(deg)deg")
        }
    }

    func testVerticalLinearMaskIsNoLongerInverted() {
        // 180deg = "to bottom" (css-images-3 §3.1.1): the gradient line
        // STARTS at the top edge and ENDS at the bottom edge. The old
        // trig returned start.y = 1, end.y = 0 — the inverted render.
        let (start, end) = MaskApplier.linearMaskEndpoints(
            angleDeg: 180, size: CGSize(width: 160, height: 80))
        XCTAssertLessThan(start.y, end.y, "180deg must run top→bottom")
        // And the default-less 0deg counterpart runs bottom→top.
        let (s0, e0) = MaskApplier.linearMaskEndpoints(
            angleDeg: 0, size: CGSize(width: 160, height: 80))
        XCTAssertGreaterThan(s0.y, e0.y, "0deg must run bottom→top")
    }

    // ── 2. Repeating stop expansion (pure math) ──────────────────────

    // black 0% → transparent 10%: the MaskImage_RepeatingLinear fixture.
    private let period10: [Gradient.Stop] = [
        .init(color: .black, location: 0.0),
        .init(color: .clear, location: 0.1),
    ]

    func testRepeatingExpansionTilesThePeriodAcrossTheLine() {
        let out = MaskApplier.expandRepeatingStops(period10)
        // 10 contiguous copies of the 0.1-wide period cover [0,1]:
        // 2 stops × 10 copies.
        XCTAssertEqual(out.count, 20)
        // First copy anchors the line start, last copy reaches the end.
        XCTAssertEqual(out.first?.location, 0.0)
        XCTAssertEqual(out.last!.location, 1.0, accuracy: 1e-9)
        // Locations must be non-decreasing or SwiftUI's Gradient warps.
        for i in 1..<out.count {
            XCTAssertLessThanOrEqual(out[i - 1].location, out[i].location,
                                     "stops out of order at index \(i)")
        }
        // Every copy restarts the pattern: even indices are the black
        // period starts, odd indices the transparent period ends.
        XCTAssertEqual(out[2].location, 0.1, accuracy: 1e-9)
        XCTAssertEqual(out[3].location, 0.2, accuracy: 1e-9)
    }

    func testFullSpanRepeatingEqualsPlainStops() {
        // §3.4.3: stops spanning the whole line tile to themselves —
        // expansion must be the identity (matches the Android
        // repeatingStopSpan null contract and web's native render).
        let full: [Gradient.Stop] = [
            .init(color: .black, location: 0.0),
            .init(color: .clear, location: 1.0),
        ]
        XCTAssertEqual(MaskApplier.expandRepeatingStops(full), full)
    }

    func testZeroWidthSpanIsLeftUntouched() {
        // No finite period — degrade to the plain clamped render rather
        // than divide by zero or spin the copy loop.
        let degenerate: [Gradient.Stop] = [
            .init(color: .black, location: 0.3),
            .init(color: .clear, location: 0.3),
        ]
        XCTAssertEqual(MaskApplier.expandRepeatingStops(degenerate), degenerate)
        // Single-stop and empty lists are equally inert.
        XCTAssertEqual(MaskApplier.expandRepeatingStops([]), [])
    }

    func testInteriorSpanCoversBothEndsOfTheLine() {
        // black 20% → clear 60%: repeats BOTH directions (§3.4.3), so
        // the expansion must reach location 0 (backward copy, clamped)
        // and location 1 (forward copy).
        let interior: [Gradient.Stop] = [
            .init(color: .black, location: 0.2),
            .init(color: .clear, location: 0.6),
        ]
        let out = MaskApplier.expandRepeatingStops(interior)
        // Accuracy-based: period arithmetic accumulates one ulp of float
        // noise before the boundary clamp.
        XCTAssertEqual(out.first!.location, 0.0, accuracy: 1e-9,
                       "backward copy must clamp to 0")
        XCTAssertEqual(out.last!.location, 1.0, accuracy: 1e-9,
                       "forward copy must clamp to 1")
    }

    // ── 3. Extractor: flags survive the wire ─────────────────────────

    private func props(_ p: [(String, IRValue)]) -> [IRProperty] {
        p.map { IRProperty(type: $0.0, data: $0.1) }
    }

    // Minimal black→transparent stop pair in the canonical srgb wire.
    private var wireStops: IRValue {
        .array([
            .object(["color": .object(["srgb": .object([
                "r": .double(0), "g": .double(0), "b": .double(0)])]),
                     "position": .double(0)]),
            .object(["color": .object(["srgb": .object([
                "r": .double(0), "g": .double(0), "b": .double(0),
                "a": .double(0)])]),
                     "position": .double(10)]),
        ])
    }

    func testRepeatingLinearKeepsItsFlag() {
        let cfg = MaskExtractor.extract(from: props([
            ("MaskImage", .array([.object([
                "type": .string("repeating-linear-gradient"),
                "angle": .object(["deg": .double(45)]),
                "stops": wireStops,
            ])])),
        ]))
        if case .linearGradient(_, _, let repeating) = cfg?.images.first ?? MaskLayer.none {
            XCTAssertTrue(repeating, "repeating-linear must keep its flag")
        } else {
            XCTFail("repeating-linear-gradient did not extract as a linear layer")
        }
    }

    func testRadialShapeKeywordSurvivesExtraction() {
        // "shape":"circle" from `radial-gradient(circle, …)`; absent →
        // nil so the applier renders the CSS default ellipse.
        let circle = MaskExtractor.extract(from: props([
            ("MaskImage", .array([.object([
                "type": .string("radial-gradient"),
                "shape": .string("circle"),
                "stops": wireStops,
            ])])),
        ]))
        if case .radialGradient(let shape, _) = circle?.images.first ?? MaskLayer.none {
            XCTAssertEqual(shape, "circle")
        } else {
            XCTFail("radial-gradient did not extract as a radial layer")
        }

        let ellipseDefault = MaskExtractor.extract(from: props([
            ("MaskImage", .array([.object([
                "type": .string("radial-gradient"),
                "stops": wireStops,
            ])])),
        ]))
        if case .radialGradient(let shape, _) = ellipseDefault?.images.first ?? MaskLayer.none {
            XCTAssertNil(shape, "omitted shape must extract as nil (ellipse default)")
        } else {
            XCTFail("shape-less radial-gradient did not extract as a radial layer")
        }
    }

    func testRepeatingConicIsNoLongerDropped() {
        // The old switch had no repeating-conic case: the layer vanished
        // and with it the whole mask. It must map to the conic layer.
        let cfg = MaskExtractor.extract(from: props([
            ("MaskImage", .array([.object([
                "type": .string("repeating-conic-gradient"),
                "stops": wireStops,
            ])])),
        ]))
        if case .conicGradient = cfg?.images.first ?? MaskLayer.none { /* ok */ }
        else { XCTFail("repeating-conic-gradient must extract as a conic layer") }
    }
}

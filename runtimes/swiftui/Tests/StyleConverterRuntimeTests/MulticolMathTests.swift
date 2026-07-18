//
//  MulticolMathTests.swift
//  StyleConverterRuntimeTests
//
//  Pins MulticolMath.usedColumns — the css-multicol-1 §3 used-value
//  pseudo-algorithm (iOS mirror of Android's wave-6
//  MultiColumnUsedValuesTest for resolveUsedColumns, extended with the
//  non-auto column-width branches). Wave-9 regression fix: the WPT
//  block-child fill uses this math so children of multicol containers
//  fill their COLUMN box (§2), not the container.
//

import XCTest
@testable import StyleConverterRuntime

final class MulticolMathTests: XCTestCase {

    // MARK: - §2 gate: both inputs auto ⇒ not a multicol container

    /// Neither count nor width ⇒ nil — column-rule alone must never
    /// invent column geometry (css-multicol-1 §2).
    func testBothAutoReturnsNil() {
        XCTAssertNil(MulticolMath.usedColumns(
            availableWidthPx: 300, requestedCount: nil,
            requestedWidthPx: nil, gapPx: 10),
            "no non-auto input: not a multicol container, no geometry")
    }

    /// A degenerate (<= 0) column-width behaves as auto — with no count
    /// either, still not a multicol container.
    func testZeroWidthAloneBehavesAsAuto() {
        XCTAssertNil(MulticolMath.usedColumns(
            availableWidthPx: 300, requestedCount: nil,
            requestedWidthPx: 0, gapPx: 10),
            "column-width 0 is degenerate — treated as auto")
    }

    // MARK: - Count-only branch (the css-break fixtures' shape)

    /// background-image-001 geometry: 3 columns, 10px gaps in a definite
    /// box → W = (U − 2·gap)/3 per §3's count branch.
    func testCountOnlyBranch() throws {
        let used = try XCTUnwrap(MulticolMath.usedColumns(
            availableWidthPx: 300, requestedCount: 3,
            requestedWidthPx: nil, gapPx: 10))
        XCTAssertEqual(used.count, 3, "count-only: N is the specified count")
        XCTAssertEqual(used.widthPx, (300.0 - 20.0) / 3.0, accuracy: 0.001,
            "count-only: W = (available − (N−1)·gap)/N")
    }

    /// `column-count: 1` — the single column IS the content box.
    func testSingleColumnFillsAvailable() throws {
        let used = try XCTUnwrap(MulticolMath.usedColumns(
            availableWidthPx: 300, requestedCount: 1,
            requestedWidthPx: nil, gapPx: 10))
        XCTAssertEqual(used.count, 1, "one column stays one column")
        XCTAssertEqual(used.widthPx, 300, "a single column spans the box")
    }

    /// Sub-1 counts are invalid per §3.1 (<integer [1,∞]>) — coerced to 1.
    func testInvalidCountCoercedToOne() throws {
        let used = try XCTUnwrap(MulticolMath.usedColumns(
            availableWidthPx: 300, requestedCount: 0,
            requestedWidthPx: nil, gapPx: 0))
        XCTAssertEqual(used.count, 1, "column-count < 1 coerces to 1")
    }

    // MARK: - Width branches (§3's floor((U+gap)/(W+gap)) fit)

    /// Width-only: 300px box, 140px columns, 10px gap →
    /// floor(310/150) = 2 columns of (300 − 10)/2 = 145px.
    func testWidthOnlyBranch() throws {
        let used = try XCTUnwrap(MulticolMath.usedColumns(
            availableWidthPx: 300, requestedCount: nil,
            requestedWidthPx: 140, gapPx: 10))
        XCTAssertEqual(used.count, 2, "width-only: N = floor((U+gap)/(W+gap))")
        XCTAssertEqual(used.widthPx, 145, accuracy: 0.001,
            "width-only: remaining space splits evenly across the fit")
    }

    /// A column-width wider than the box still yields one full-width
    /// column — N never drops below 1 (§3: at least one column box).
    func testOversizedWidthYieldsOneColumn() throws {
        let used = try XCTUnwrap(MulticolMath.usedColumns(
            availableWidthPx: 300, requestedCount: nil,
            requestedWidthPx: 500, gapPx: 10))
        XCTAssertEqual(used.count, 1, "oversized column-width: one column")
        XCTAssertEqual(used.widthPx, 300, "the lone column fills the box")
    }

    /// Both non-auto: the count CAPS the width-driven fit (§3) — 5 fit
    /// but only 2 requested → 2 columns share the space.
    func testCountCapsWidthFit() throws {
        let used = try XCTUnwrap(MulticolMath.usedColumns(
            availableWidthPx: 300, requestedCount: 2,
            requestedWidthPx: 50, gapPx: 0))
        XCTAssertEqual(used.count, 2, "both non-auto: count caps the fit")
        XCTAssertEqual(used.widthPx, 150, "capped columns split the space")
    }

    // MARK: - Android-parity guards (the wave-6 wedge lesson)

    /// The z-ordering-003 wedge shape: a 15px box with 5 columns and
    /// 16px gaps — the strict spec math yields a NEGATIVE width; the
    /// gap-fitting clamp must reduce to 1 column filling the box.
    func testNarrowerThanGapsClampsToOneColumn() throws {
        let used = try XCTUnwrap(MulticolMath.usedColumns(
            availableWidthPx: 15, requestedCount: 5,
            requestedWidthPx: nil, gapPx: 16))
        XCTAssertEqual(used.count, 1, "gaps that don't fit reduce the count")
        XCTAssertEqual(used.widthPx, 15, "the surviving column fills the box")
        XCTAssertGreaterThanOrEqual(used.widthPx, 0, "width is never negative")
    }

    /// Negative available space lays out as zero space; negative gaps
    /// are invalid CSS (css-align-3 §8) and treated as 0.
    func testNegativeInputsFloorToZero() throws {
        let used = try XCTUnwrap(MulticolMath.usedColumns(
            availableWidthPx: -50, requestedCount: 3,
            requestedWidthPx: nil, gapPx: -4))
        XCTAssertEqual(used.count, 3, "zero gap: every requested column fits")
        XCTAssertEqual(used.widthPx, 0, "no space: zero-width columns, not negative")
    }
}

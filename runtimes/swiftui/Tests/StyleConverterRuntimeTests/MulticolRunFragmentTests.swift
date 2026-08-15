//
//  MulticolRunFragmentTests.swift
//  Wave-42 lane W4 — the SHARED multi-child run pin table.
//
//  Pins MulticolRunFragment exactly; the Android twin
//  (runtimes/compose .../columns/MulticolRunFragmentTest.kt) carries the
//  SAME R rows byte-for-byte (native-pair parity gate). On Android the
//  twin is consumed by MulticolRunFragmentMeasure; iOS consumption waits
//  on the multi-child clone-row renderer seam (wave-42 report).
//

import XCTest
@testable import StyleConverterRuntime

final class MulticolRunFragmentTests: XCTestCase {

    /// R1 — a run that fits one column: sequential fill, no slices.
    func testR1RunFittingTheColumnHasNoFragments() {
        let plan = MulticolRunFragment.runPlan(
            childHeightsPx: [30, 40], columnBlockSize: 100,
            columnWidth: 80, gapPx: 10, columnCount: 3)
        // Children stack at cumulative offsets (block flow §9.4.1)…
        XCTAssertEqual(plan.yOffsetsPx, [0, 30])
        XCTAssertEqual(plan.totalBlockSizePx, 70)
        // …and an empty fragment list keeps the unfragmented identity.
        XCTAssertTrue(plan.fragments.isEmpty)
    }

    /// R2 — the floats-clear-000 strip shape (were floats real): T=253 over 3×100.
    func testR2OverflowingRunSlicesLikeASoleChildOfHeightT() {
        let plan = MulticolRunFragment.runPlan(
            childHeightsPx: [250, 3], columnBlockSize: 100,
            columnWidth: 100, gapPx: 0, columnCount: 3)
        XCTAssertEqual(plan.yOffsetsPx, [0, 250])
        XCTAssertEqual(plan.totalBlockSizePx, 253)
        // The slices ARE the wave-10 S-table with C = T — same geometry,
        // same N cap (the overflow clip).
        XCTAssertEqual(plan.fragments, [
            .init(columnIndex: 0, clipRect: CGRect(x: 0, y: 0, width: 100, height: 100),
                  translate: CGSize(width: 0, height: 0)),
            .init(columnIndex: 1, clipRect: CGRect(x: 100, y: 0, width: 100, height: 100),
                  translate: CGSize(width: 100, height: -100)),
            .init(columnIndex: 2, clipRect: CGRect(x: 200, y: 0, width: 100, height: 100),
                  translate: CGSize(width: 200, height: -200)),
        ])
        XCTAssertEqual(
            plan.fragments,
            FragmentGeometry.fragments(childBlockSize: 253, columnBlockSize: 100,
                                       columnWidth: 100, gapPx: 0, columnCount: 3))
    }

    /// R3 — negative measured heights are impossible boxes: floored at 0.
    func testR3NegativeChildHeightsFloorToZero() {
        let plan = MulticolRunFragment.runPlan(
            childHeightsPx: [-5, 60], columnBlockSize: 100,
            columnWidth: 80, gapPx: 0, columnCount: 2)
        // The negative child occupies nothing; its sibling starts at 0.
        XCTAssertEqual(plan.yOffsetsPx, [0, 0])
        XCTAssertEqual(plan.totalBlockSizePx, 60)
        XCTAssertTrue(plan.fragments.isEmpty)
    }

    /// R4 — a degenerate (non-positive) column block-size never slices.
    func testR4ZeroColumnBlockSizeProducesNoFragments() {
        // The definite-height gate excludes H ≤ 0 upstream, but the pure
        // module must not divide by it either — guard pinned.
        XCTAssertTrue(MulticolRunFragment.runPlan(
            childHeightsPx: [50], columnBlockSize: 0,
            columnWidth: 80, gapPx: 0, columnCount: 2).fragments.isEmpty)
    }

    /// R5 — the MONOLITHIC push (css-break-3 §4.1): an empty box has no
    /// class-A/B breakpoint inside it, so a column boundary may not pass
    /// through it. Pinned against WPT css-break avoid-border-break
    /// (`columns:2; column-fill:auto; height:200px`, a 175px spacer then a
    /// 100px box with a 30px green border): unpushed the boundary at 200
    /// cuts 25px into that border, while the reference needs the box whole
    /// in column 1. Byte-twin of the Android R5 row.
    func testR5MonolithicChildIsPushedIntoTheNextColumn() {
        let plan = MulticolRunFragment.runPlan(
            childHeightsPx: [175, 100], columnBlockSize: 200,
            columnWidth: 100, gapPx: 0, columnCount: 2,
            // The spacer is empty too, but it starts flush at 0 and never
            // straddles — only the second child moves.
            monolithic: [true, true])
        XCTAssertEqual(plan.yOffsetsPx, [0, 200])
        XCTAssertEqual(plan.totalBlockSizePx, 300)
        XCTAssertEqual(plan.fragments,
                       FragmentGeometry.fragments(childBlockSize: 300, columnBlockSize: 200,
                                                  columnWidth: 100, gapPx: 0, columnCount: 2))
    }

    /// R6 — a BREAKABLE child in the same shape is NOT pushed, and a
    /// monolithic child TALLER than the column still splits once it starts
    /// flush at a boundary (WPT css-break borders-001: `[95px spacer][120px
    /// bordered box]`, H=100 — pushed to column 1, then broken so the top
    /// border sits in column 1 and the bottom border in column 2, exactly
    /// as that reference describes). Byte-twin of the Android R6 row.
    func testR6BreakableSplitsAndOverTallMonolithBreaksAfterPush() {
        let breakable = MulticolRunFragment.runPlan(
            childHeightsPx: [175, 100], columnBlockSize: 200,
            columnWidth: 100, gapPx: 0, columnCount: 2, monolithic: [false, false])
        XCTAssertEqual(breakable.yOffsetsPx, [0, 175])
        XCTAssertEqual(breakable.totalBlockSizePx, 275)
        let overTall = MulticolRunFragment.runPlan(
            childHeightsPx: [95, 120], columnBlockSize: 100,
            columnWidth: 100, gapPx: 10, columnCount: 4, monolithic: [true, true])
        XCTAssertEqual(overTall.yOffsetsPx, [0, 100])
        XCTAssertEqual(overTall.totalBlockSizePx, 220)
        XCTAssertEqual(overTall.fragments.count, 3)
    }
}

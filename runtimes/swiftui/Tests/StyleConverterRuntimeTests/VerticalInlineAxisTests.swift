//
//  VerticalInlineAxisTests.swift
//  Wave 52 lane L8 (vertical-wedges) — pins for the pure inline-axis
//  decisions in `VerticalInlineAxis`.
//
//  Every case here has a Kotlin twin of the same name in
//  runtimes/compose/src/test/java/com/styleconverter/runtime/typography/text/
//  VerticalInlineAxisTest.kt; the two files are the contract that keeps the
//  natives from disagreeing about which ch boxes measure the vertical
//  advance or what budget an orthogonal run wraps against.
//
//  MUTATIONS EXECUTED (2026-09-25; RE-EXECUTED 2026-10-05 in the isolated HEAD
//  export — tools/titan/results/wave52-vertical-wedges/_mutations-swift.log
//  S3/S7 — each restored byte-exact, sha-256 verified):
//   • `chAdvanceIsVertical` with the `textOrientation == .upright` clause
//     dropped → `testChMeasuresTheVerticalAdvanceOnlyUnderVerticalStarUpright`
//     fails on the `.mixed` assertion.
//   • `orthogonalBudget` returning nil instead of the ICB →
//     `testOrthogonalBudget…` fails on `(nil, 568) == 568`, and
//     `testTheChUnitsVrl005OrangeRun…` fails on its non-nil plan.
//

import XCTest
@testable import StyleConverterRuntime

final class VerticalInlineAxisTests: XCTestCase {

    func testChMeasuresTheVerticalAdvanceOnlyUnderVerticalStarUpright() {
        // css-values-4 §6.1.1 × css-writing-modes-4 §5.1: the two vertical
        // modes with an explicit `upright` stand the '0' up.
        XCTAssertTrue(VerticalInlineAxis.chAdvanceIsVertical(writingMode: .verticalRl, textOrientation: .upright))
        XCTAssertTrue(VerticalInlineAxis.chAdvanceIsVertical(writingMode: .verticalLr, textOrientation: .upright))
        // `mixed` rotates the '0' (Vertical_Orientation R) — x-advance.
        XCTAssertFalse(VerticalInlineAxis.chAdvanceIsVertical(writingMode: .verticalRl, textOrientation: .mixed))
        // `sideways` rotates everything — x-advance.
        XCTAssertFalse(VerticalInlineAxis.chAdvanceIsVertical(writingMode: .verticalRl, textOrientation: .sideways))
        // The sideways-* modes ignore text-orientation altogether.
        XCTAssertFalse(VerticalInlineAxis.chAdvanceIsVertical(writingMode: .sidewaysRl, textOrientation: .upright))
        XCTAssertFalse(VerticalInlineAxis.chAdvanceIsVertical(writingMode: .sidewaysLr, textOrientation: .upright))
        // A horizontal mode's inline axis is horizontal by definition.
        XCTAssertFalse(VerticalInlineAxis.chAdvanceIsVertical(writingMode: .horizontalTb, textOrientation: .upright))
    }

    func testOrthogonalBudgetDefiniteAncestorWinsElseTheIcbElseNil() {
        // css-writing-modes-4 §7.3.1 at the composed capture's 568 px ICB.
        XCTAssertEqual(VerticalInlineAxis.orthogonalBudget(nearestDefiniteBlockSizePx: nil, icbBlockExtentPx: 568), 568)
        // A definite ancestor block size (a 16 px parent) outranks the ICB.
        XCTAssertEqual(VerticalInlineAxis.orthogonalBudget(nearestDefiniteBlockSizePx: 16, icbBlockExtentPx: 568), 16)
        // Nothing published (the dark stage) → nil → the historical decline.
        XCTAssertNil(VerticalInlineAxis.orthogonalBudget(nearestDefiniteBlockSizePx: nil, icbBlockExtentPx: nil))
        // Non-positive / non-finite readings are "not definite", not budgets.
        XCTAssertNil(VerticalInlineAxis.orthogonalBudget(nearestDefiniteBlockSizePx: 0, icbBlockExtentPx: nil))
        XCTAssertEqual(VerticalInlineAxis.orthogonalBudget(nearestDefiniteBlockSizePx: .nan, icbBlockExtentPx: 568), 568)
        XCTAssertNil(VerticalInlineAxis.orthogonalBudget(nearestDefiniteBlockSizePx: -1, icbBlockExtentPx: .infinity))
    }

    func testUprightBudgetABoundedProposalBeatsTheFallback() {
        // A definite available inline size is the budget, fallback or not.
        XCTAssertEqual(VerticalInlineAxis.uprightBudget(boundedPx: 40, fallbackPx: 568), 40)
        // Indefinite → the §7.3.1 fallback.
        XCTAssertEqual(VerticalInlineAxis.uprightBudget(boundedPx: nil, fallbackPx: 568), 568)
        // Indefinite and no fallback → nil → decline, as before wave 52.
        XCTAssertNil(VerticalInlineAxis.uprightBudget(boundedPx: nil, fallbackPx: nil))
    }

    func testTheChUnitsVrl005OrangeRunPlansOneColumnOfFiveAtTheIcbBudget() {
        // The orange `width: 5ch` upright `00000` of wave51-fix css-writing-
        // modes/ch-units-vrl-005: 24 px upright advance at 20 px Inter, a nil
        // proposal, the 568 px ICB published → one column of five glyphs
        // (the ref's 120×120 square).
        let glyphs = VerticalTextFlow.codePointsOf("00000")
        let budget = VerticalInlineAxis.uprightBudget(
            boundedPx: nil,
            fallbackPx: VerticalInlineAxis.orthogonalBudget(nearestDefiniteBlockSizePx: nil, icbBlockExtentPx: 568))
        XCTAssertEqual(VerticalTextFlow.uprightColumnIndices(glyphs: glyphs, charAdvancePx: 24, budgetPx: budget),
                       [[0, 1, 2, 3, 4]])
        // Without a published ICB the planner still declines — the pre-wave-52
        // picture (the horizontal label) on every non-capture path.
        let none = VerticalInlineAxis.uprightBudget(
            boundedPx: nil,
            fallbackPx: VerticalInlineAxis.orthogonalBudget(nearestDefiniteBlockSizePx: nil, icbBlockExtentPx: nil))
        XCTAssertNil(VerticalTextFlow.uprightColumnIndices(glyphs: glyphs, charAdvancePx: 24, budgetPx: none))
    }

    func testGateBudgetPrefersTheElementsOwnDefiniteExtent() {
        // The seam's resolver: own definite extent → containing block → ICB.
        XCTAssertEqual(VerticalUprightGate.budgetPx(ownBlockExtentPx: 100, containingBlockHeightPx: 300, icbBlockExtentPx: 568), 100)
        XCTAssertEqual(VerticalUprightGate.budgetPx(ownBlockExtentPx: nil, containingBlockHeightPx: 300, icbBlockExtentPx: 568), 300)
        XCTAssertEqual(VerticalUprightGate.budgetPx(ownBlockExtentPx: nil, containingBlockHeightPx: nil, icbBlockExtentPx: 568), 568)
        // A zero own extent (0px padding-box) is not definite for wrapping.
        XCTAssertEqual(VerticalUprightGate.budgetPx(ownBlockExtentPx: 0, containingBlockHeightPx: nil, icbBlockExtentPx: 568), 568)
        XCTAssertNil(VerticalUprightGate.budgetPx(ownBlockExtentPx: nil, containingBlockHeightPx: nil, icbBlockExtentPx: nil))
    }
}

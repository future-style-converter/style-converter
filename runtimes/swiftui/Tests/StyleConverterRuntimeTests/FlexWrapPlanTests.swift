//
//  FlexWrapPlanTests.swift
//  Wave 25, lane ISTRETCH — the PURE half of CAL-RC5.
//
//  Byte-parallel twin of the Compose lane's FlexWrapLinesTest: the same
//  cases against the same rules (css-flexbox-1 §9.3 line collection,
//  §9.4 step 8 / §9.6 align-content stretch), so a divergence between
//  the two runtimes shows up here rather than in a screenshot diff.
//
//  These pins are cheap and exact — FlexWrapPlan has no SwiftUI types.
//  The RASTER truth (does the item actually paint at that size through
//  ComponentRenderer?) lives in FlexWrapStretchTests; this file only
//  claims the arithmetic.
//

import XCTest
@testable import StyleConverterRuntime

final class FlexWrapPlanTests: XCTestCase {

    // MARK: - §9.3 line collection

    /// A gap counts against the line budget exactly like item size does
    /// (css-align-3 §8.1) — ref-002's four 50px items in a 110px box.
    func testGapCountsAgainstTheLineBudget() {
        let lines = FlexWrapPlan.breakLines(mainSizes: [50, 50, 50, 50],
                                            containerMain: 110, gap: 10)
        XCTAssertEqual(lines, [FlexWrapPlan.Line(first: 0, last: 1),
                               FlexWrapPlan.Line(first: 2, last: 3)])
    }

    /// Two items that fit on width alone still break when the gap pushes
    /// them over — the gap is not free.
    func testAGapThatOverflowsForcesTheEarlierBreak() {
        let lines = FlexWrapPlan.breakLines(mainSizes: [50, 50],
                                            containerMain: 100, gap: 10)
        XCTAssertEqual(lines, [FlexWrapPlan.Line(first: 0, last: 0),
                               FlexWrapPlan.Line(first: 1, last: 1)])
    }

    /// flex-gap-decorations-001: four 45px items, 10px gap, 100px box.
    func testDecorations001PacksTwoPerLine() {
        let lines = FlexWrapPlan.breakLines(mainSizes: [45, 45, 45, 45],
                                            containerMain: 100, gap: 10)
        XCTAssertEqual(lines, [FlexWrapPlan.Line(first: 0, last: 1),
                               FlexWrapPlan.Line(first: 2, last: 3)])
    }

    /// §9.3: a line always takes at least one item, even an oversized
    /// one — otherwise the collection would never terminate.
    func testAnOversizedItemStillGetsItsOwnLine() {
        let lines = FlexWrapPlan.breakLines(mainSizes: [500, 10],
                                            containerMain: 100, gap: 10)
        XCTAssertEqual(lines, [FlexWrapPlan.Line(first: 0, last: 0),
                               FlexWrapPlan.Line(first: 1, last: 1)])
    }

    /// An unbounded main axis cannot overflow, so nothing wraps.
    func testAnUnboundedContainerNeverWraps() {
        let lines = FlexWrapPlan.breakLines(mainSizes: [50, 50, 50],
                                            containerMain: .infinity, gap: 10)
        XCTAssertEqual(lines, [FlexWrapPlan.Line(first: 0, last: 2)])
    }

    /// No items ⇒ no lines (the empty-container case FlowLayout also
    /// has to survive without an index crash).
    func testNoItemsMeansNoLines() {
        XCTAssertEqual(FlexWrapPlan.breakLines(mainSizes: [],
                                               containerMain: 100, gap: 10), [])
    }

    /// `count` is the gap multiplier both axes use.
    func testLineCountExposesTheGapMultiplier() {
        XCTAssertEqual(FlexWrapPlan.Line(first: 0, last: 1).count, 2)
        XCTAssertEqual(FlexWrapPlan.Line(first: 3, last: 3).count, 1)
    }

    // MARK: - §9.4 step 8 / §9.6 line stretch

    /// The WPT-capture shape: zero-height lines in a definite box get
    /// the whole leftover, split equally.
    func testDefiniteCrossStretchesZeroHeightLines() {
        XCTAssertEqual(FlexWrapPlan.stretchLines(base: [0, 0],
                                                 containerCross: 110, gap: 10),
                       [50, 50])
    }

    /// The product-path shape: the 30pt harness floor plus a 20pt share
    /// is the same 50pt line.
    func testDecorations002StretchesToFiftyPointLines() {
        XCTAssertEqual(FlexWrapPlan.stretchLines(base: [30, 30],
                                                 containerCross: 110, gap: 10),
                       [50, 50])
    }

    /// Stretch ADDS to what the items already claim — it never replaces
    /// a taller line with the average: leftover 110 − 60 − 10 = 40, so
    /// each line gains 20 and the 40/20 asymmetry survives.
    func testStretchAddsToWhatTheItemsClaim() {
        XCTAssertEqual(FlexWrapPlan.stretchLines(base: [40, 20],
                                                 containerCross: 110, gap: 10),
                       [60, 40])
    }

    /// The lines sum EXACTLY to the container's cross size — points are
    /// fractional, so unlike the Compose twin there is no remainder to
    /// hand to the leading lines.
    func testTheLinesSumToTheContainerCross() {
        let out = FlexWrapPlan.stretchLines(base: [0, 0, 0],
                                            containerCross: 100, gap: 0)
        XCTAssertEqual(out.reduce(0, +), 100, accuracy: 0.0001)
        XCTAssertEqual(out, [100.0 / 3, 100.0 / 3, 100.0 / 3])
    }

    /// An auto (indefinite) cross size leaves the lines hugging.
    func testAnAutoCrossSizeLeavesTheLinesHugging() {
        XCTAssertEqual(FlexWrapPlan.stretchLines(base: [30, 30],
                                                 containerCross: nil, gap: 10),
                       [30, 30])
    }

    /// Overflowing lines are never COMPRESSED — CSS lets the container
    /// spill or clip, it does not shrink the lines.
    func testOverflowingLinesAreNeverCompressed() {
        XCTAssertEqual(FlexWrapPlan.stretchLines(base: [80, 80],
                                                 containerCross: 100, gap: 10),
                       [80, 80])
    }

    /// A single line absorbs the whole definite cross size.
    func testASingleLineAbsorbsTheWholeCross() {
        XCTAssertEqual(FlexWrapPlan.stretchLines(base: [30],
                                                 containerCross: 100, gap: 10),
                       [100])
    }

    // MARK: - The two preconditions

    /// css-align-3 §5.3 — only `normal`/`stretch` (and the `auto`
    /// spelling) distribute; every other keyword positions instead.
    func testOnlyNormalAndStretchDistribute() {
        XCTAssertTrue(FlexWrapPlan.alignContentStretches(nil))
        XCTAssertTrue(FlexWrapPlan.alignContentStretches(.normal))
        XCTAssertTrue(FlexWrapPlan.alignContentStretches(.stretch))
        XCTAssertTrue(FlexWrapPlan.alignContentStretches(.auto))
        for kw: AlignmentKeyword in [.center, .start, .end, .spaceBetween,
                                     .spaceAround, .spaceEvenly, .baseline] {
            XCTAssertFalse(FlexWrapPlan.alignContentStretches(kw), "\(kw) distributed")
        }
    }

    /// Step 8 needs BOTH a stretching keyword and a definite, finite
    /// cross size; anything else means "the lines hug".
    func testDefiniteCrossNeedsBothPreconditions() {
        XCTAssertEqual(FlexWrapPlan.definiteCross(alignContentStretches: true,
                                                  hasDefiniteCross: true,
                                                  cross: 110), 110)
        XCTAssertNil(FlexWrapPlan.definiteCross(alignContentStretches: false,
                                                hasDefiniteCross: true, cross: 110))
        XCTAssertNil(FlexWrapPlan.definiteCross(alignContentStretches: true,
                                                hasDefiniteCross: false, cross: 110))
        XCTAssertNil(FlexWrapPlan.definiteCross(alignContentStretches: true,
                                                hasDefiniteCross: true, cross: nil))
        XCTAssertNil(FlexWrapPlan.definiteCross(alignContentStretches: true,
                                                hasDefiniteCross: true,
                                                cross: .infinity))
    }

    // MARK: - The build-time cross estimate (iOS-only, no Compose twin)

    /// Compose measures its items through the intrinsics protocol; iOS
    /// has to know the cross size BEFORE the child view is built, so the
    /// estimate refuses everything it cannot derive from the IR.
    func testTheCrossEstimateRefusesWhatItCannotKnow() {
        // An explicit cross size is the frame extent, full stop.
        XCTAssertEqual(FlexWrapPlan.hypotheticalCross(
            explicitCrossPx: 20, clampedCross: false, isEmptyLeaf: false,
            hasCrossBands: false, emptyFloorPx: 30), 20)
        // An empty leaf is the harness floor (30 product / 0 WPT).
        XCTAssertEqual(FlexWrapPlan.hypotheticalCross(
            explicitCrossPx: nil, clampedCross: false, isEmptyLeaf: true,
            hasCrossBands: false, emptyFloorPx: 30), 30)
        XCTAssertEqual(FlexWrapPlan.hypotheticalCross(
            explicitCrossPx: nil, clampedCross: false, isEmptyLeaf: true,
            hasCrossBands: false, emptyFloorPx: 0), 0)
        // Content-sized: needs measurement → refuse.
        XCTAssertNil(FlexWrapPlan.hypotheticalCross(
            explicitCrossPx: nil, clampedCross: false, isEmptyLeaf: false,
            hasCrossBands: false, emptyFloorPx: 30))
        // A min/max clamp without a definite size → refuse.
        XCTAssertNil(FlexWrapPlan.hypotheticalCross(
            explicitCrossPx: nil, clampedCross: true, isEmptyLeaf: true,
            hasCrossBands: false, emptyFloorPx: 30))
        // Padding/border: the frame extent depends on box-sizing → refuse,
        // even when a cross size is declared.
        XCTAssertNil(FlexWrapPlan.hypotheticalCross(
            explicitCrossPx: 20, clampedCross: false, isEmptyLeaf: true,
            hasCrossBands: true, emptyFloorPx: 30))
    }
}

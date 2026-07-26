//
//  GridContentDistributionTests.swift
//  Wave 19 (lane GRID-DISTRIBUTION) — XCTest pins for
//  GridContentDistribution.trackOrigins: css-align-3 §5.3 content
//  distribution of the grid TRACK GROUP + direction:rtl column mirroring.
//
//  Shared-semantics contract: rows P1-P12 are lifted from the LIVE wave18
//  IR of the failing WPT family (css-grid descendant-static-position-002/
//  003/004 — 40/43/44px and 10px+33px column templates inside 100px / 20px
//  content boxes), the exact geometry Chrome renders for those captures.
//  THIS TABLE IS THE CROSS-PLATFORM CONTRACT: the Compose twin pins the
//  SAME rows in GridContentDistributionTest.kt, byte-for-byte.
//

import XCTest
// @testable: GridContentDistribution is module-internal.
@testable import StyleConverterRuntime

final class GridContentDistributionTests: XCTestCase {

    // Tolerance for Double math (all pins are exact binary fractions or
    // thirds — 1e-9 keeps the twin tables numerically honest).
    private let eps = 1e-9

    /// Shorthand: run the pure function and compare per-element.
    private func pin(_ widths: [Double], _ gap: Double, _ extent: Double,
                     _ justify: GridContentDistribution.Justify, _ rtl: Bool,
                     _ expected: [Double],
                     file: StaticString = #filePath, line: UInt = #line) {
        let got = GridContentDistribution.trackOrigins(
            trackWidths: widths, gap: gap, contentExtent: extent,
            justify: justify, rtl: rtl)
        XCTAssertEqual(got.count, expected.count, "origin count", file: file, line: line)
        for (i, pair) in zip(expected, got).enumerated() {
            XCTAssertEqual(pair.1, pair.0, accuracy: eps, "origin[\(i)]", file: file, line: line)
        }
    }

    // MARK: - LTR justify-content (descendant-static-position-003 geometry)

    /// P1 — 001-family regression pin: start never moves anything.
    func testP1StartKeepsGroupAtContentBoxOrigin() {
        pin([40], 0, 100, .start, false, [0])
    }

    /// P2 — 003 sub-grid 1: the +60px shift the lane's pixel proof cites.
    func testP2EndShifts40pxTrackByFull60pxLeftover() {
        pin([40], 0, 100, .end, false, [60])
    }

    /// P3 — 003 sub-grids 2/3 (43px template).
    func testP3EndShifts43pxTrackBy57px() {
        pin([43], 0, 100, .end, false, [57])
    }

    /// P4 — 003 sub-grids 5/6: both tracks shift together, order kept.
    func testP4EndPacks10Plus33GroupFlushRight() {
        pin([10, 33], 0, 100, .end, false, [57, 67])
    }

    /// P11 — LTR center: (100−40)/2 = 30.
    func testP11CenterSplitsLeftoverEvenly() {
        pin([40], 0, 100, .center, false, [30])
    }

    // MARK: - RTL mirroring (descendant-static-position-002 geometry)

    /// P5 — 002 sub-grids 1/4: start = right edge in RTL → left edge −20.
    func testP5RtlStartOverflows40pxTrackPastLeftEdgeOf20pxBox() {
        pin([40], 0, 20, .start, true, [-20])
    }

    /// P6 — 002 sub-grids 2/3.
    func testP6RtlStartOverflows43pxTrackToMinus23() {
        pin([43], 0, 20, .start, true, [-23])
    }

    /// P7 — 002 sub-grids 5/6: col 1 (10px) hugs the right edge, col 2
    /// (33px) spills 23px past the left edge.
    func testP7RtlReversesPhysicalColumnOrder() {
        pin([10, 33], 0, 20, .start, true, [10, -23])
    }

    // MARK: - RTL + center (descendant-static-position-004 geometry)

    /// P8 — 004 sub-grids 1/4: center is direction-symmetric.
    func testP8RtlCenterOf40pxTrackLandsAt30() {
        pin([40], 0, 100, .center, true, [30])
    }

    /// P9 — 004 sub-grids 2/3.
    func testP9RtlCenterOf44pxTrackLandsAt28() {
        pin([44], 0, 100, .center, true, [28])
    }

    /// P10 — 004 sub-grids 5/6: logical lead 28.5 → col 1 right of col 2.
    func testP10RtlCenterOf10Plus33GroupMirrorsThePair() {
        pin([10, 33], 0, 100, .center, true, [61.5, 28.5])
    }

    /// P12 — end = the LEFT physical edge in RTL; order still reversed.
    func testP12RtlEndPacksGroupFlushLeftWithCol1RightmostOfIt() {
        pin([10, 33], 0, 100, .end, true, [33, 0])
    }

    // MARK: - space-* distribution + §5.3 fallbacks

    /// P13 — leftover 50 into the single inner gap on top of the 10px gap.
    func testP13SpaceBetweenWidensInnerGapOnly() {
        pin([20, 20], 10, 100, .spaceBetween, false, [0, 80])
    }

    /// P14 — leftover 50: lead 12.5, between 10+25 → [12.5, 67.5].
    func testP14SpaceAroundLeadsWithHalfShare() {
        pin([20, 20], 10, 100, .spaceAround, false, [12.5, 67.5])
    }

    /// P15 — leftover 50 into 3 shares of 50/3 → [50/3, 190/3].
    func testP15SpaceEvenlyDealsNPlus1EqualShares() {
        pin([20, 20], 10, 100, .spaceEvenly, false, [50.0 / 3.0, 190.0 / 3.0])
    }

    /// P16 — leftover −20 → §5.3 fallback: start packing, plain gaps.
    func testP16SpaceBetweenFallsBackToStartOnOverflow() {
        pin([60, 60], 0, 100, .spaceBetween, false, [0, 60])
    }

    /// P17 — leftover −20 → centered overflow (−10 both sides).
    func testP17SpaceAroundFallsBackToCenterOnOverflow() {
        pin([60, 60], 0, 100, .spaceAround, false, [-10, 50])
    }

    /// P18 — 10+20+6gap = 36 → leftover 64; second origin 64+10+6.
    func testP18GapParticipatesInFootprintUnderEnd() {
        pin([10, 20], 6, 100, .end, false, [64, 80])
    }

    /// P19 — empty template distributes nothing.
    func testP19EmptyTemplateDistributesNothing() {
        pin([], 0, 100, .end, false, [])
    }

    // MARK: - chained: track sizing feeds distribution (003 end-to-end)

    /// The exact 003 sub-grid-1 pipeline: template {"px":40} → GridTrackMath
    /// sizing → distribution — proves the two pure halves compose.
    func testSized40pxTrackUnderJustifyContentEndLandsAt60() {
        let widths = GridTrackMath.columnWidths(
            tracks: [.fixed(px: 40)], containerWidth: 100,
            columnGap: 0, maxContent: [0])
        pin(widths.map(Double.init), 0, 100, .end, false, [60])
    }

    // MARK: - adapter (aggregate keyword → twin domain)

    /// The AlignmentKeyword fold: distribution-less keywords behave as
    /// start (css-align-3 §5.3 normal→stretch→start for non-auto tracks).
    func testJustifyAdapterFolds() {
        XCTAssertEqual(GridContentDistribution.justify(of: .end), .end)
        XCTAssertEqual(GridContentDistribution.justify(of: .selfEnd), .end)
        XCTAssertEqual(GridContentDistribution.justify(of: .center), .center)
        XCTAssertEqual(GridContentDistribution.justify(of: .spaceBetween), .spaceBetween)
        XCTAssertEqual(GridContentDistribution.justify(of: .spaceAround), .spaceAround)
        XCTAssertEqual(GridContentDistribution.justify(of: .spaceEvenly), .spaceEvenly)
        XCTAssertEqual(GridContentDistribution.justify(of: .start), .start)
        XCTAssertEqual(GridContentDistribution.justify(of: .stretch), .start)
        XCTAssertEqual(GridContentDistribution.justify(of: .normal), .start)
        XCTAssertEqual(GridContentDistribution.justify(of: nil), .start)
    }
}

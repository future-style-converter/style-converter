//
//  FlexNowrapOuterTests.swift
//  Wave 52, lane L10 (flex-nowrap-gaps) — pins for queue 7(b) (a percent
//  flex-basis on the NOWRAP path), M2 (a negative main-axis margin is part
//  of the OUTER size, css-flexbox-1 §9.2 step 3 / §9.5) and 7(a′) (gap
//  rule rects snap to whole points like Chromium's PixelSnappedIntRect).
//
//  Every IR payload is VERBATIM wave51-fix per-test IR (FlexNowrapIR.swift);
//  the end-to-end rasters live in FlexNowrapRasterTests.swift.
//
//  EXECUTED MUTATIONS (tools/titan/results/wave52-flex-nowrap-gaps/
//  mutations.log is the record; each restored byte-exact, sha-verified):
//   SW-B1 CSSFlexMath.percentBasis returns nil      → testPercentBasis… RED
//   SW-B2 FlexOuterFacts.boxExtra returns 0         → testPercentBasis… RED
//   SW-M1 mainOffsets ignores `outers`              → testNegativeMargin… RED
//   SW-M2 FlexOuterFacts.margins → zeros            → testNegativeMargin… RED
//   SW-S1 GapDecorationsPainter.snapped → identity  → testBandSnap… RED
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class FlexNowrapOuterTests: XCTestCase {

    // MARK: - helpers

    /// The flex claims of the container's children, in slot order.
    private func claims(_ json: String) throws -> [ItemPlacement.FlexClaim] {
        try (FlexNowrapIR.flexRoot(json).children ?? []).map { ItemPlacementExtractor.extract(from: $0.properties).flex }
    }

    // MARK: - 7(b): percent basis on the nowrap path (pin 1)

    /// Both 50% items resolve against the 100-px content box (§9.2 step
    /// 3.A); their content-box extras (border-left 5 / border-right 6,
    /// SOLID) make the outer sum 111 > 100, so §9.7 shrinks each content
    /// box by 5.5 → frames 49.5 + 50.5 = exactly the 100-px green square.
    func testPercentBasisResolvesAndShrinksByOuterSize() throws {
        let c = try claims(FlexNowrapIR.doc002)
        XCTAssertEqual(c.count, 2)
        let bases = c.map { CSSFlexMath.percentBasis($0, available: 100) }
        XCTAssertEqual(bases, [50, 50])
        // An indefinite container keeps the intrinsic fallback (§7.2.3).
        XCTAssertNil(CSSFlexMath.percentBasis(c[0], available: nil))
        let outers = c.map { CSSFlexMath.outer($0, horizontal: true, percentResolved: true) }
        XCTAssertEqual(outers, [5, 6])
        // §9.7 on outer sizes.
        let items = zip(bases, outers).map {
            CSSFlexMath.ItemInput(basis: $0.0!, min: 0, grow: 0, shrink: 1, outer: $0.1)
        }
        let sizes = CSSFlexMath.mainSizes(items: items, available: 100, gap: 0)
        XCTAssertEqual(sizes[0], 44.5, accuracy: 0.001)
        XCTAssertEqual(sizes[1], 44.5, accuracy: 0.001)
        let offs = CSSFlexMath.mainOffsets(sizes: sizes, available: 100, gap: 0,
                                           justify: nil, outers: outers)
        XCTAssertEqual(offs[1], 49.5, accuracy: 0.001)
    }

    // MARK: - M2: negative margin in the outer size (pin 3)

    /// 027: six 50-px shrink-0 items, gap 10, the first with
    /// margin-left −150. Outer sum 150 + gaps 50 = 200 = the container,
    /// and §9.5 places the margin boxes at [0, −90, −30, 30, 90, 150] —
    /// the ref's runs (container content at page x 218: "Two" at 128).
    func testNegativeMarginAdvancesByOuterSize() throws {
        let c = try claims(FlexNowrapIR.doc027)
        XCTAssertEqual(c.count, 6)
        let outers = c.map { CSSFlexMath.outer($0, horizontal: true, percentResolved: false) }
        XCTAssertEqual(outers, [-150, 0, 0, 0, 0, 0])
        let items = c.map { CSSFlexMath.ItemInput(basis: 50, min: 0, grow: $0.grow,
                                                  shrink: $0.shrink,
                                                  outer: CSSFlexMath.outer($0, horizontal: true,
                                                                           percentResolved: false)) }
        let sizes = CSSFlexMath.mainSizes(items: items, available: 200, gap: 10)
        XCTAssertEqual(sizes, [50, 50, 50, 50, 50, 50])
        let offs = CSSFlexMath.mainOffsets(sizes: sizes, available: 200, gap: 10,
                                           justify: nil, outers: outers)
        XCTAssertEqual(offs, [0, -90, -30, 30, 90, 150])
        // The painted box of "One" is its slot moved by the margin.
        XCTAssertEqual(GapDecorationsPainter.paintShift(c[0]), CGSize(width: -150, height: 0))
        XCTAssertEqual(GapDecorationsPainter.paintShift(c[1]), .zero)
    }

    // MARK: - 7(a′): band snap (pin 5)

    /// 046's Swift line boxes [0,56.67] [61.67,118.33] [123.33,180] with
    /// a 5-px row rule centred in each 5-px gap → whole-point bands
    /// [57,62) and [118,123) (canvas y 73–77 / 134–138 = the ref); 045's
    /// half-point column band at x 57.5 → [58,63) (canvas x 76–80).
    func testBandSnapMatchesChromiumPixelSnap() {
        let b1 = GapDecorationsPainter.snapped(CGRect(x: 0, y: 170.0 / 3, width: 140, height: 5))
        XCTAssertEqual(b1, CGRect(x: 0, y: 57, width: 140, height: 5))
        let b2 = GapDecorationsPainter.snapped(CGRect(x: 0, y: 355.0 / 3, width: 140, height: 5))
        XCTAssertEqual(b2, CGRect(x: 0, y: 118, width: 140, height: 5))
        let col = GapDecorationsPainter.snapped(CGRect(x: 57.5, y: 0, width: 5, height: 70))
        XCTAssertEqual(col, CGRect(x: 58, y: 0, width: 5, height: 70))
        let flat = CGRect(x: 50, y: 0, width: 10, height: 50)
        XCTAssertEqual(GapDecorationsPainter.snapped(flat), flat)
    }
}

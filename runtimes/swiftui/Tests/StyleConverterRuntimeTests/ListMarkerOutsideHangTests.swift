//
//  ListMarkerOutsideHangTests.swift
//  Wave 52, lane L6 (T5) — the `outside` marker hang.
//
//  TWIN of runtimes/compose/src/test/java/com/styleconverter/runtime/lists/
//  ListMarkerOutsideHangTest.kt: same measured numbers (wave51-fix
//  counter-suffix row 1 — the `<ol>` content edge at x 64, the ref's
//  marker ink x 46–58, text from 64; today's iOS marker at 64–73, text
//  at 80), same identities. The pure `place` is pinned here; the Layout
//  itself is exercised through the seam patch on the device A/B.
//
//  The verbatim counter-suffix `<li>` (per-test IR component
//  `counter-suffix__0__0__0`, wave51-fix) drives the per-item decisions:
//  it carries text, so the row aligns by baseline; its container's
//  resolved position is `outside` (the initial value), so it hangs.
//
//  MUTATION PROOF (EXECUTED 2026-09-25 at authoring, RE-EXECUTED
//  2026-10-05 by tools/titan/results/wave52-counters-and-lists/mutate.py,
//  entry `hang-width-swift` in mutations.log): making `place` report
//  `itemSize.width + markerSize.width + gap` as the pair's width — the
//  pre-T5 HStack behaviour — turned testPairReportsTheItemsSizeOnly red
//  (175.0 ≠ 158.0) and left the other five green; restored byte-exact.
//  The renderer wiring is raster-pinned separately, inside seam-2.patch
//  (ListMarkerOutsideHangRasterTests — red without the seam, green with).
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class ListMarkerOutsideHangTests: XCTestCase {

    private let gap = ListMarkerRow.gapPt
    private func hang(aligns: Bool, mb: CGFloat? = 18, ib: CGFloat? = 18) -> ListMarkerOutsideHang.Placement {
        ListMarkerOutsideHang.place(markerSize: CGSize(width: 13, height: 24), markerBaseline: mb,
                                    itemSize: CGSize(width: 158, height: 24), itemBaseline: ib,
                                    gap: gap, alignsByBaseline: aligns)
    }

    // MARK: - the two identities the brief names

    func testPairReportsTheItemsSizeOnly() {
        // css-lists-3 §3.5: the item's content edge does not move, so the
        // pair is exactly the item — the pin the pre-T5 HStack fails (175).
        let p = hang(aligns: true)
        XCTAssertEqual(p.width, 158)
        XCTAssertEqual(p.height, 24)
    }

    func testMarkerRightEdgePlusGapEqualsItemLeftEdge() {
        // markerX + markerWidth + gap == 0 — the item's border-box start.
        let p = hang(aligns: true)
        XCTAssertEqual(p.markerX + 13 + gap, 0)
        // With the item at page x 64 that is marker ink at 47–60 — the ref's
        // 46–58 within the gap's rounding.
        XCTAssertEqual(64 + p.markerX, 47)
    }

    // MARK: - block axis

    func testBaselineAlignmentOffsetsByTheBaselineDifference() {
        XCTAssertEqual(hang(aligns: true, mb: 18, ib: 20).markerY, 2)
        XCTAssertEqual(hang(aligns: true).markerY, 0)
    }

    func testTopAlignmentWhenNotAligningOrWithoutABaseline() {
        // The HStack's `.top` fallback, verbatim.
        XCTAssertEqual(hang(aligns: false, mb: 18, ib: 30).markerY, 0)
        XCTAssertEqual(hang(aligns: true, mb: nil, ib: 30).markerY, 0)
        XCTAssertEqual(hang(aligns: true, mb: 18, ib: nil).markerY, 0)
    }

    // MARK: - the gate

    func testOnlyAnOutsidePositionHangsTheMarker() {
        XCTAssertTrue(ListMarkerRow.hangsOutside(position: .outside))
        XCTAssertFalse(ListMarkerRow.hangsOutside(position: .inside))
        XCTAssertFalse(ListMarkerRow.hangsOutside(position: nil))
        // Disjoint with the inside overlay whatever the item exposes.
        for exposes in [true, false] {
            XCTAssertFalse(ListMarkerRow.rendersInsideOverlay(position: .outside, itemExposesTextBaseline: exposes))
        }
    }

    // MARK: - the verbatim corpus item drives the two per-item decisions

    func testCounterSuffixItemAlignsByBaselineAndHangsOutside() throws {
        // `counter-suffix__0__0` (the `<ol>`) and `counter-suffix__0__0__0`
        // (its first `<li>`, text "foo", baked "1."), wave51-fix per-test IR.
        let ol = try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"counter-suffix__0__0-611","name":"counter-suffix__0__0",
         "properties":[{"type":"PaddingLeft","data":{"original":{"v":3,"u":"EM"}}},
           {"type":"LineHeight","data":{"multiplier":1.5,"original":{"type":"percentage","value":150}}},
           {"type":"ListStyleType","data":"decimal"}],
         "meta":{"sourceTag":"ol"}}
        """.utf8))
        let li = try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"counter-suffix__0__0__0-612","name":"counter-suffix__0__0__0",
         "properties":[{"type":"LineHeight","data":{"multiplier":1.5,"original":{"type":"percentage","value":150}}}],
         "text":"foo","meta":{"sourceTag":"li","markerText":"1."}}
        """.utf8))
        let cfg = try XCTUnwrap(ListMarkerResolver.resolve(parentTag: ol.meta?.sourceTag,
                                                           parentProperties: ol.properties,
                                                           childProperties: li.properties))
        // The initial `list-style-position` is `outside` (css-lists-3
        // §3.1), nothing in the document changes it → the item hangs.
        XCTAssertTrue(ListMarkerRow.hangsOutside(position: cfg.position))
        // "foo" is in-flow text → the row shares the first baseline.
        XCTAssertTrue(ListMarkerRow.itemExposesTextBaseline(li))
        XCTAssertEqual(ListMarkerRow.rowAlignment(itemExposesTextBaseline: true), .firstTextBaseline)
    }
}

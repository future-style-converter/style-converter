//
//  VerticalFragmentGeometryTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 47 (lane Z2) — the VERTICAL-writing-mode fragment V-table and its
//  plan builder, pinned against the css-break reference geometry. The
//  IDENTICAL expected values are pinned on Android
//  (VerticalFragmentGeometryTest.kt), so drift here is a cross-platform
//  divergence, not a refactor. Reference derivations live in the Kotlin
//  twin's header (browser used values from the WPT sources + Chromium refs).
//

import XCTest
import CoreGraphics
@testable import StyleConverterRuntime

final class VerticalFragmentGeometryTests: XCTestCase {

    // MARK: - 1. The shared V-table (identical on Android)

    /// V1 — background-image-001 (vertical-rl): C=350 W=120 colH=150 G=10
    /// N=3 → right-aligned bands walking leftward (tx = W−C+i·W).
    func testV1BackgroundImage001VerticalRl() throws {
        let frags = try XCTUnwrap(VerticalFragmentGeometry.fragments(
            childBlockSize: 350, containerBlockSize: 120,
            columnInlineSize: 150, gapPx: 10, columnCount: 3,
            blockRtl: true))
        XCTAssertEqual(frags, [
            .init(columnIndex: 0,
                  clipRect: CGRect(x: 0, y: 0, width: 120, height: 150),
                  translate: CGSize(width: -230, height: 0)),
            .init(columnIndex: 1,
                  clipRect: CGRect(x: 0, y: 160, width: 120, height: 150),
                  translate: CGSize(width: -110, height: 160)),
            .init(columnIndex: 2,
                  clipRect: CGRect(x: 0, y: 320, width: 120, height: 150),
                  translate: CGSize(width: 10, height: 320)),
        ], "V1: bands [230,350) [110,230) [0,110), partial right-aligned")
    }

    /// V2 — background-image-002 (vertical-lr): same numbers, left-aligned
    /// bands walking rightward (tx = −i·W).
    func testV2BackgroundImage002VerticalLr() throws {
        let frags = try XCTUnwrap(VerticalFragmentGeometry.fragments(
            childBlockSize: 350, containerBlockSize: 120,
            columnInlineSize: 150, gapPx: 10, columnCount: 3,
            blockRtl: false))
        XCTAssertEqual(frags.map { $0.translate.width }, [0, -120, -240],
            "V2: lr bands walk rightward")
        XCTAssertEqual(frags.map { $0.clipRect.origin.y }, [0, 160, 320],
            "V2: columns stack vertically at i·(colH+G)")
    }

    /// V3/V4 — borders-006/007: C=270 W=100 colH=100 G=10 N=3.
    func testV3V4Borders() throws {
        let lr = try XCTUnwrap(VerticalFragmentGeometry.fragments(
            childBlockSize: 270, containerBlockSize: 100,
            columnInlineSize: 100, gapPx: 10, columnCount: 3,
            blockRtl: false))
        XCTAssertEqual(lr.map { $0.translate.width }, [0, -100, -200],
            "V3 borders-006 (vertical-lr): block-start border in fragment 0")
        let rl = try XCTUnwrap(VerticalFragmentGeometry.fragments(
            childBlockSize: 270, containerBlockSize: 100,
            columnInlineSize: 100, gapPx: 10, columnCount: 3,
            blockRtl: true))
        XCTAssertEqual(rl.map { $0.translate.width }, [-170, -70, 30],
            "V4 borders-007 (vertical-rl): mirrored bands, partial right-aligned")
    }

    /// V5 — the FITS case still yields one anchored fragment (rl
    /// right-aligns even fitting content — unlike the horizontal S2 nil).
    func testV5FitsCaseSingleAnchoredFragment() throws {
        let rl = try XCTUnwrap(VerticalFragmentGeometry.fragments(
            childBlockSize: 80, containerBlockSize: 120,
            columnInlineSize: 150, gapPx: 10, columnCount: 3, blockRtl: true))
        XCTAssertEqual(rl.map { $0.translate.width }, [40], "rl: tx = W−C")
        let lr = try XCTUnwrap(VerticalFragmentGeometry.fragments(
            childBlockSize: 80, containerBlockSize: 120,
            columnInlineSize: 150, gapPx: 10, columnCount: 3, blockRtl: false))
        XCTAssertEqual(lr.map { $0.translate.width }, [0], "lr: tx = 0")
    }

    /// V6 — overflow past N columns is clipped by the fragment cap; V7 —
    /// the §3.4 inline fit helper.
    func testV6CapAndV7InlineFit() throws {
        let frags = try XCTUnwrap(VerticalFragmentGeometry.fragments(
            childBlockSize: 500, containerBlockSize: 120,
            columnInlineSize: 150, gapPx: 10, columnCount: 3, blockRtl: false))
        XCTAssertEqual(frags.count, 3, "V6: F = ceil(500/120) capped at N=3")
        XCTAssertEqual(VerticalFragmentGeometry.columnInlineSize(
            availableInline: 470, columnCount: 3, gapPx: 10), 150, accuracy: 0.001)
        XCTAssertEqual(VerticalFragmentGeometry.columnInlineSize(
            availableInline: 320, columnCount: 3, gapPx: 10), 100, accuracy: 0.001)
    }

    // MARK: - 2. The plan builder's gates (pure, no render surface)

    /// Wire-shaped property decode (same helper style as
    /// FragmentGeometryTests).
    private func props(_ json: String) throws -> [IRProperty] {
        try JSONDecoder().decode([IRProperty].self, from: Data(json.utf8))
    }

    /// The css-break .mc child: `block-size: 350px` (SizeValue raw shape).
    private func wideChild() throws -> [IRProperty] {
        try props(#"[{"type":"BlockSize","data":{"px":350.0}}]"#)
    }

    private func threeColumns() -> ColumnsConfig {
        var cols = ColumnsConfig()
        cols.count = 3
        return cols
    }

    /// The background-image-001 plan through the PUBLIC route: vertical
    /// mode + capture → the V1 fragments with VStack slot geometry.
    func testVerticalPlanEngagesThroughFragmentPlanRoute() throws {
        let plan = try XCTUnwrap(ColumnsApplier.fragmentPlan(
            columns: threeColumns(), verticalWritingMode: true,
            siblingCount: 1, contentWidthPx: 120, contentHeightPx: 470,
            gapPx: 10, childProperties: try wideChild(),
            ctx: SpacingContext(), wptCaptureMode: true,
            verticalBlockRtl: true))
        XCTAssertTrue(plan.vertical, "the plan must mark itself vertical")
        XCTAssertTrue(plan.verticalBlockRtl, "rl rides into the plan")
        XCTAssertEqual(plan.fragments.map { $0.translate.width },
                       [-230, -110, 10], "the V1 band translations")
        XCTAssertEqual(plan.columnWidthPx, 120, "slot width = W")
        XCTAssertEqual(plan.columnBlockSizePx, 150, accuracy: 0.001,
                       "slot height = colH (470−20)/3")
    }

    /// Outside WPT capture the vertical route keeps the frozen logged bail
    /// (dark-stage protection).
    func testVerticalPlanIsCaptureGated() throws {
        XCTAssertNil(ColumnsApplier.fragmentPlan(
            columns: threeColumns(), verticalWritingMode: true,
            siblingCount: 1, contentWidthPx: 120, contentHeightPx: 470,
            gapPx: 10, childProperties: try wideChild(),
            ctx: SpacingContext(), wptCaptureMode: false,
            verticalBlockRtl: true),
            "no capture → the pre-wave-47 bail, byte-identical")
    }

    /// A post-load-extracted child (baked physical Width AND Height) keeps
    /// the frozen path — its baked geometry already encodes the browser's
    /// fragmentation (the anchor-position-multicol protection).
    func testVerticalPlanDeclinesBakedLayout() throws {
        let baked = try props(#"""
            [{"type":"BlockSize","data":{"px":350.0}},
             {"type":"Width","data":{"type":"length","px":450.0}},
             {"type":"Height","data":{"type":"length","px":20.0}}]
            """#)
        XCTAssertNil(ColumnsApplier.verticalFragmentPlan(
            columns: threeColumns(), siblingCount: 1,
            contentWidthPx: 120, contentHeightPx: 470, gapPx: 10,
            childProperties: baked, ctx: SpacingContext(), blockRtl: true),
            "baked used layout: fragmenting again would double-apply")
    }

    /// One column overflows instead of clipping (§8.2) and multi-child
    /// containers keep the frozen path — both decline.
    func testVerticalPlanDeclinesSingleColumnAndMultiChild() throws {
        var oneCol = ColumnsConfig()
        oneCol.count = 1
        XCTAssertNil(ColumnsApplier.verticalFragmentPlan(
            columns: oneCol, siblingCount: 1,
            contentWidthPx: 120, contentHeightPx: 470, gapPx: 10,
            childProperties: try wideChild(), ctx: SpacingContext(),
            blockRtl: false),
            "N == 1 overflows, never clips (the anchor-multicol-008 shape)")
        XCTAssertNil(ColumnsApplier.verticalFragmentPlan(
            columns: threeColumns(), siblingCount: 2,
            contentWidthPx: 120, contentHeightPx: 470, gapPx: 10,
            childProperties: try wideChild(), ctx: SpacingContext(),
            blockRtl: false),
            "multi-child vertical balancing is not built")
    }
}

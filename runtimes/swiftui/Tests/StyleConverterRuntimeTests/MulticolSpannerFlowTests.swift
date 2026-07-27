//
//  MulticolSpannerFlowTests.swift
//  Wave-21 lane MULTICOL — the SHARED spanner-flow pin table.
//
//  Pins MulticolSpannerFlow exactly; the Android twin
//  (runtimes/compose .../columns/MulticolSpannerFlowTest.kt) carries the
//  SAME SP/E/F/X rows byte-for-byte, so any change must land on both
//  platforms in the same commit (native-pair parity gate).
//
//  SP1–SP3 and SP6 are pinned against the LIVE wave21-gate IRs
//  (tools/titan/runs/wave21-gate/sections/css-multicol/per-test-ir/):
//  always-balancing-before-column-span, abspos-after-spanner,
//  as-column-flex-item, abspos-after-spanner-static-pos.
//

import XCTest
@testable import StyleConverterRuntime

final class MulticolSpannerFlowTests: XCTestCase {

    // Shorthand builders so the table rows read like the Kotlin twin.
    private func flow(_ h: Double) -> MulticolSpannerFlow.Child { .init(heightPx: h, role: .flow) }
    private func spanner(_ h: Double) -> MulticolSpannerFlow.Child { .init(heightPx: h, role: .spanner) }
    private func anchor() -> MulticolSpannerFlow.Child { .init(heightPx: 0, role: .static) }

    /// SP1 — always-balancing-before-column-span: 200px child balances into 2×100 before the 0-height spanner.
    func testSP1BalanceBeforeSpanner() {
        // css-multicol §6.3: pre-spanner content balances (H = ceil(200/2)).
        let plan = MulticolSpannerFlow.plan(children: [flow(200), spanner(0)], columnCount: 2)
        // Flow child starts at column 0 / y 0; spanner sits below the
        // balanced 100px row; container auto height = 100.
        XCTAssertEqual(plan.slots, [
            .init(role: .flow, columnIndex: 0, yPx: 0),
            .init(role: .spanner, columnIndex: 0, yPx: 100),
        ])
        XCTAssertEqual(plan.containerBlockSizePx, 100)
        // The sole flow child's replay fragmentainer is the balanced H.
        XCTAssertEqual(plan.soleFlowColumnBlockSizePx, 100)
    }

    /// SP2 — abspos-after-spanner: the abspos anchors in the post-spanner flow of column 1.
    func testSP2AbsposStaticPositionAfterSpanner() {
        // Children in IR order: spanner(h10), abspos, red(30), spacer(70).
        let plan = MulticolSpannerFlow.plan(
            children: [spanner(10), anchor(), flow(30), flow(70)], columnCount: 2)
        // Post-spanner segment: T=100, H=50; the abspos anchors at the
        // segment start of column 0 (y = spanner bottom = 10), exactly
        // where the red flow sibling lands — green then paints over red.
        XCTAssertEqual(plan.slots, [
            .init(role: .spanner, columnIndex: 0, yPx: 0),
            .init(role: .static, columnIndex: 0, yPx: 10),
            .init(role: .flow, columnIndex: 0, yPx: 10),
            .init(role: .flow, columnIndex: 0, yPx: 40),
        ])
        // Container: 10px spanner + 50px balanced segment.
        XCTAssertEqual(plan.containerBlockSizePx, 60)
        // Two flow children → no sole-flow fragmentainer.
        XCTAssertNil(plan.soleFlowColumnBlockSizePx)
    }

    /// SP3 — as-column-flex-item: auto-height sole child balances into 4×40.
    func testSP3AutoHeightSoleFlowBalance() {
        // §7.1: unconstrained multicol always balances — H = ceil(160/4).
        let plan = MulticolSpannerFlow.plan(children: [flow(160)], columnCount: 4)
        XCTAssertEqual(plan.slots, [.init(role: .flow, columnIndex: 0, yPx: 0)])
        // Container auto block-size = the balanced 40, not the child's 160.
        XCTAssertEqual(plan.containerBlockSizePx, 40)
        XCTAssertEqual(plan.soleFlowColumnBlockSizePx, 40)
    }

    /// SP4 — multi-flow sequential fill: R maps to column floor(R/H), offset R mod H.
    func testSP4SequentialFillAtBalancedH() {
        // T=90, H=ceil(90/2)=45: R = 0, 30, 60 → cols 0, 0, 1.
        let plan = MulticolSpannerFlow.plan(
            children: [flow(30), flow(30), flow(30)], columnCount: 2)
        XCTAssertEqual(plan.slots, [
            .init(role: .flow, columnIndex: 0, yPx: 0),
            .init(role: .flow, columnIndex: 0, yPx: 30),
            .init(role: .flow, columnIndex: 1, yPx: 15),
        ])
        XCTAssertEqual(plan.containerBlockSizePx, 45)
        XCTAssertNil(plan.soleFlowColumnBlockSizePx)
    }

    /// SP5 — a painted (non-zero) spanner splits two balanced segments.
    func testSP5MidListSpannerSplitsSegments() {
        // seg0 T=40 H=20; spanner 20 at y=20; seg1 T=60 H=30 from y=40.
        let plan = MulticolSpannerFlow.plan(
            children: [flow(40), spanner(20), flow(60)], columnCount: 2)
        XCTAssertEqual(plan.slots, [
            .init(role: .flow, columnIndex: 0, yPx: 0),
            .init(role: .spanner, columnIndex: 0, yPx: 20),
            .init(role: .flow, columnIndex: 0, yPx: 40),
        ])
        // 20 (seg0) + 20 (spanner) + 30 (seg1).
        XCTAssertEqual(plan.containerBlockSizePx, 70)
        XCTAssertNil(plan.soleFlowColumnBlockSizePx)
    }

    /// SP6 — abspos-after-spanner-static-pos: the abspos anchors mid-segment in column 2.
    func testSP6StaticAnchorMidSegment() {
        // IR order: flow70, spanner10, flow35, flow15, abspos, red-flow20.
        let plan = MulticolSpannerFlow.plan(
            children: [flow(70), spanner(10), flow(35), flow(15), anchor(), flow(20)],
            columnCount: 2)
        // seg0 T=70 H=35 → y 0..35; spanner 35..45; seg1 T=70 H=35:
        // R runs 0(35) 35(15) — the abspos sees R=50 → column 1, y 45+15.
        XCTAssertEqual(plan.slots, [
            .init(role: .flow, columnIndex: 0, yPx: 0),
            .init(role: .spanner, columnIndex: 0, yPx: 35),
            .init(role: .flow, columnIndex: 0, yPx: 45),
            .init(role: .flow, columnIndex: 1, yPx: 45),
            .init(role: .static, columnIndex: 1, yPx: 60),
            .init(role: .flow, columnIndex: 1, yPx: 60),
        ])
        XCTAssertEqual(plan.containerBlockSizePx, 80)
        XCTAssertNil(plan.soleFlowColumnBlockSizePx)
    }

    /// SP7 — degenerates: empty input, and sub-1 column counts coerce to 1.
    func testSP7EmptyAndCountFloor() {
        // No children → no slots, zero height.
        let empty = MulticolSpannerFlow.plan(children: [], columnCount: 2)
        XCTAssertEqual(empty.slots, [])
        XCTAssertEqual(empty.containerBlockSizePx, 0)
        // N=0 coerces to 1 → the single column holds the whole child.
        let one = MulticolSpannerFlow.plan(children: [flow(10)], columnCount: 0)
        XCTAssertEqual(one.slots, [.init(role: .flow, columnIndex: 0, yPx: 0)])
        XCTAssertEqual(one.containerBlockSizePx, 10)
    }

    /// SP8 — a static anchor at the segment's end clamps to the last column.
    func testSP8TrailingStaticClampsToLastColumn() {
        // T=100, H=50; the static sees R=100 → floor(100/50)=2, clamped
        // to column 1 at offset 100−1·50=50 (the column's bottom edge).
        let plan = MulticolSpannerFlow.plan(
            children: [flow(50), flow(50), anchor()], columnCount: 2)
        XCTAssertEqual(plan.slots, [
            .init(role: .flow, columnIndex: 0, yPx: 0),
            .init(role: .flow, columnIndex: 1, yPx: 0),
            .init(role: .static, columnIndex: 1, yPx: 50),
        ])
        XCTAssertEqual(plan.containerBlockSizePx, 50)
    }

    /// E1–E6 — the narrow engagement gate (dark-stage protection).
    func testEEngagementGate() {
        // E1: any spanner engages (SP2's children).
        XCTAssertTrue(MulticolSpannerFlow.engages(
            children: [spanner(10), anchor(), flow(30), flow(70)], columnCount: 2))
        // E2: sole flow child, 4 columns — balancing shortens (40 < 160).
        XCTAssertTrue(MulticolSpannerFlow.engages(children: [flow(160)], columnCount: 4))
        // E3: one column — balancing is the identity.
        XCTAssertFalse(MulticolSpannerFlow.engages(children: [flow(160)], columnCount: 1))
        // E4: multi-flow without a spanner keeps the greedy heuristic.
        XCTAssertFalse(MulticolSpannerFlow.engages(children: [flow(40), flow(40)], columnCount: 2))
        // E5: a 1px child cannot balance shorter (ceil(1/2)=1).
        XCTAssertFalse(MulticolSpannerFlow.engages(children: [flow(1)], columnCount: 2))
        // E6: a lone static is not a flow child.
        XCTAssertFalse(MulticolSpannerFlow.engages(children: [anchor()], columnCount: 2))
        // E7: a CONTENT-sized sole flow child (no declared Height) keeps
        // the legacy render — e.g. abspos-containing-block-outside-spanner's
        // relative wrapper hiding a nested spanner must not be sliced.
        XCTAssertFalse(MulticolSpannerFlow.engages(
            children: [.init(heightPx: 50, role: .flow, explicitBlockSize: false)],
            columnCount: 3))
    }

    /// F1–F4 — the sole-flow clip+translate replay gate.
    func testFReplayGate() {
        // F1: SP1's shape (0-height spanner) may replay.
        XCTAssertTrue(MulticolSpannerFlow.soleFlowFragmentReplay(children: [flow(200), spanner(0)]))
        // F2: statics forbid the replay (their ink would ghost per column).
        XCTAssertFalse(MulticolSpannerFlow.soleFlowFragmentReplay(
            children: [spanner(10), anchor(), flow(30), flow(70)]))
        // F3: a lone flow child replays (SP3's shape).
        XCTAssertTrue(MulticolSpannerFlow.soleFlowFragmentReplay(children: [flow(160)]))
        // F4: a painted (10px) spanner forbids the replay.
        XCTAssertFalse(MulticolSpannerFlow.soleFlowFragmentReplay(children: [flow(200), spanner(10)]))
    }

    /// X1–X3 — boundary-crossing detection (the whole-child-placement log gate).
    func testXBoundaryCrossing() {
        // X1: SP4's second child straddles the 45px line (30+30 > 45).
        XCTAssertTrue(MulticolSpannerFlow.anyFlowChildCrossesBoundary(
            children: [flow(30), flow(30), flow(30)], columnCount: 2))
        // X2: SP2's 70px spacer straddles the post-spanner 50px line.
        XCTAssertTrue(MulticolSpannerFlow.anyFlowChildCrossesBoundary(
            children: [spanner(10), anchor(), flow(30), flow(70)], columnCount: 2))
        // X3: two 40s at H=40 land exactly on the boundary — no straddle.
        XCTAssertFalse(MulticolSpannerFlow.anyFlowChildCrossesBoundary(
            children: [flow(40), flow(40)], columnCount: 2))
    }

    // MARK: - Role classification + iOS-only integrations

    /// A minimal IR child with the given properties (leaf, no children).
    private func component(_ id: String, _ properties: [IRProperty]) -> IRComponent {
        IRComponent(id: id, name: id, properties: properties,
                    selectors: nil, media: nil, children: nil, slot: nil,
                    text: nil, pseudos: nil, meta: nil)
    }

    /// A px Height property in the live wire shape ({"type":"length","px":N}).
    private func heightPx(_ px: Double) -> IRProperty {
        IRProperty(type: "Height", data: .object(["type": .string("length"), "px": .double(px)]))
    }

    /// R — span:all is SPANNER, abspos is STATIC, out-of-flow beats span.
    func testRRolesFor() {
        let spannerChild = component("s", [IRProperty(type: "ColumnSpan", data: .string("ALL"))])
        let absposChild = component("a", [IRProperty(type: "Position", data: .string("ABSOLUTE"))])
        let flowChild = component("f", [heightPx(30)])
        // An abspos spanner is out of flow — STATIC wins (css-position §2.1).
        let absposSpanner = component("as", [
            IRProperty(type: "ColumnSpan", data: .string("ALL")),
            IRProperty(type: "Position", data: .string("ABSOLUTE")),
        ])
        XCTAssertEqual(
            MulticolSpannerFlow.rolesFor(children: [spannerChild, absposChild, flowChild, absposSpanner]),
            [.spanner, .static, .flow, .static])
        // Leading text prepends an anonymous FLOW box (subview order).
        XCTAssertEqual(
            MulticolSpannerFlow.rolesFor(children: [spannerChild], leadingText: true),
            [.flow, .spanner])
        // specsFor pairs roles with the declared-block-size flag: the flow
        // child declares Height (explicit), the spanner does not; leading
        // text is content-sized by definition.
        XCTAssertEqual(
            MulticolSpannerFlow.specsFor(children: [spannerChild, flowChild], leadingText: true),
            [.init(role: .flow, explicitBlockSize: false),
             .init(role: .spanner, explicitBlockSize: false),
             .init(role: .flow, explicitBlockSize: true)])
    }

    /// B-RC5 — fragmentPlan's capture-only AUTO-height balanced branch
    /// (as-column-flex-item: U=40, N=4, C=160 → W=10, H=40, 4 fragments).
    func testFragmentPlanAutoHeightBalances() {
        var cfg = ColumnsConfig()
        cfg.touched = true
        cfg.count = 4
        let plan = ColumnsApplier.fragmentPlan(
            columns: cfg, verticalWritingMode: false, siblingCount: 1,
            contentWidthPx: 40, contentHeightPx: nil, gapPx: 0,
            childProperties: [heightPx(160)], ctx: SpacingContext(),
            wptCaptureMode: true)
        XCTAssertNotNil(plan)
        // The balanced fragmentainer: H = ceil(160/4) = 40, W = 10.
        XCTAssertEqual(plan?.columnBlockSizePx, 40)
        XCTAssertEqual(plan?.columnWidthPx, 10)
        XCTAssertEqual(plan?.fragments.count, 4)
        // Dark stage (capture off) keeps the wave-10 identity bail.
        XCTAssertNil(ColumnsApplier.fragmentPlan(
            columns: cfg, verticalWritingMode: false, siblingCount: 1,
            contentWidthPx: 40, contentHeightPx: nil, gapPx: 0,
            childProperties: [heightPx(160)], ctx: SpacingContext()))
    }

    /// B-RC4a — a column-span:all child itself never fragments (§6.2).
    func testFragmentPlanSpannerChildBails() {
        var cfg = ColumnsConfig()
        cfg.touched = true
        cfg.count = 2
        // Even with a definite container height and a tall declared
        // height, the spanner spans instead of flowing into columns.
        XCTAssertNil(ColumnsApplier.fragmentPlan(
            columns: cfg, verticalWritingMode: false, siblingCount: 1,
            contentWidthPx: 100, contentHeightPx: 100, gapPx: 0,
            childProperties: [heightPx(200), IRProperty(type: "ColumnSpan", data: .string("ALL"))],
            ctx: SpacingContext(), wptCaptureMode: true))
    }

    /// B-RC4b — the overlay shift for the live abspos-after-spanner IR:
    /// the abspos anchors at (0, 10), the post-spanner flow of column 1.
    func testAbsposStaticOffsetAfterSpanner() {
        var cfg = ColumnsConfig()
        cfg.touched = true
        cfg.count = 2
        // Siblings in IR source order (heights as declared on the wire).
        let siblings = [
            component("sp", [IRProperty(type: "ColumnSpan", data: .string("ALL")), heightPx(10)]),
            component("green", [IRProperty(type: "Position", data: .string("ABSOLUTE")),
                                heightPx(100)]),
            component("red", [heightPx(30)]),
            component("spacer", [heightPx(70)]),
        ]
        let shift = MulticolAbsposStatic.staticOffset(
            columns: cfg, siblings: siblings, childId: "green",
            contentWidthPx: 300, gapPx: 0, ctx: SpacingContext(),
            wptCaptureMode: true)
        // SP2's static slot: column 0 (x=0) at y=10 (spanner bottom).
        XCTAssertEqual(shift, CGSize(width: 0, height: 10))
        // Capture off → the frozen overlay anchor (dark-stage identity).
        XCTAssertEqual(MulticolAbsposStatic.staticOffset(
            columns: cfg, siblings: siblings, childId: "green",
            contentWidthPx: 300, gapPx: 0, ctx: SpacingContext(),
            wptCaptureMode: false), .zero)
        // No spanner BEFORE the abspos → identity (scope gate).
        XCTAssertEqual(MulticolAbsposStatic.staticOffset(
            columns: cfg, siblings: [siblings[1], siblings[0]], childId: "green",
            contentWidthPx: 300, gapPx: 0, ctx: SpacingContext(),
            wptCaptureMode: true), .zero)
    }

    /// B-RC4b (SP6) — mid-segment anchor: column 2's x offset resolves
    /// from the §3 used geometry (U=400, N=2, G=0 → W=200; y=60).
    func testAbsposStaticOffsetMidSegment() {
        var cfg = ColumnsConfig()
        cfg.touched = true
        cfg.count = 2
        // The live abspos-after-spanner-static-pos sibling list.
        let siblings = [
            component("f70", [heightPx(70)]),
            component("sp", [IRProperty(type: "ColumnSpan", data: .string("ALL")), heightPx(10)]),
            component("f35", [heightPx(35)]),
            component("f15", [heightPx(15)]),
            component("green", [IRProperty(type: "Position", data: .string("ABSOLUTE")),
                                heightPx(100)]),
            component("red", [heightPx(20)]),
        ]
        let shift = MulticolAbsposStatic.staticOffset(
            columns: cfg, siblings: siblings, childId: "green",
            contentWidthPx: 400, gapPx: 0, ctx: SpacingContext(),
            wptCaptureMode: true)
        // SP6's static slot: column 1 → x = 1·(200+0), y = 60.
        XCTAssertEqual(shift, CGSize(width: 200, height: 60))
    }
}

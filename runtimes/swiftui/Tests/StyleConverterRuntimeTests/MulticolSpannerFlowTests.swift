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
        // text is content-sized by definition. Wave-42: both real children
        // are empty leaves, so both are MONOLITHIC (css-break-3 §4.1 — no
        // class-A/B breakpoint inside them); the anonymous leading-text box
        // is NOT (its line boxes are class-B break points).
        XCTAssertEqual(
            MulticolSpannerFlow.specsFor(children: [spannerChild, flowChild], leadingText: true),
            [.init(role: .flow, explicitBlockSize: false),
             .init(role: .spanner, explicitBlockSize: false, monolithicContent: true),
             .init(role: .flow, explicitBlockSize: true, monolithicContent: true)])
    }

    // MARK: - Wave-42 lane W4: forced column breaks + continue:discard
    // BRK/DSC rows — the Android twin carries the SAME rows byte-for-byte.

    /// Shorthand: an in-flow child with `break-after: column`.
    private func breaker(_ h: Double) -> MulticolSpannerFlow.Child {
        .init(heightPx: h, role: .flowBreakAfter)
    }

    /// BRK1 — discard-multicol-003 live IR: 4 break-after chunks + spanner, N=3, discard.
    func testBRK1DiscardDropsOverflowChunkAndTail() {
        // Wire order: 4 one-line (19px) <p break-after:column> then the
        // flattened "Spanner 1". Chunks p1|p2|p3 own columns 0..2; p4 needs
        // the §8.2 overflow column → css-overflow-4 §3 discards it AND the
        // spanner after it; the container is one 19px line tall (the ref).
        let plan = MulticolSpannerFlow.plan(
            children: [breaker(19), breaker(19), breaker(19), breaker(19), spanner(19)],
            columnCount: 3, discardOverflow: true)
        XCTAssertEqual(plan.slots, [
            .init(role: .flowBreakAfter, columnIndex: 0, yPx: 0),
            .init(role: .flowBreakAfter, columnIndex: 1, yPx: 0),
            .init(role: .flowBreakAfter, columnIndex: 2, yPx: 0),
            .init(role: .flowBreakAfter, columnIndex: 0, yPx: 0, discarded: true),
            .init(role: .spanner, columnIndex: 0, yPx: 19, discarded: true),
        ])
        XCTAssertEqual(plan.containerBlockSizePx, 19)
        XCTAssertNil(plan.soleFlowColumnBlockSizePx)
    }

    /// BRK2 — discard-multicol-003's ref box: 3 chunks fill 3 columns one line tall.
    func testBRK2ThreeChunksFillThreeColumns() {
        let plan = MulticolSpannerFlow.plan(
            children: [breaker(19), breaker(19), breaker(19)], columnCount: 3)
        XCTAssertEqual(plan.slots, [
            .init(role: .flowBreakAfter, columnIndex: 0, yPx: 0),
            .init(role: .flowBreakAfter, columnIndex: 1, yPx: 0),
            .init(role: .flowBreakAfter, columnIndex: 2, yPx: 0),
        ])
        XCTAssertEqual(plan.containerBlockSizePx, 19)
    }

    /// BRK3 — without discard the 4th chunk takes a §8.2 OVERFLOW column (index 3).
    func testBRK3OverflowChunkKeepsFlowingWithoutDiscard() {
        let plan = MulticolSpannerFlow.plan(
            children: [breaker(19), breaker(19), breaker(19), breaker(19)], columnCount: 3)
        // Column index 3 ≥ N: the placement's i·(W+G) puts it past the
        // container's inline end — Chromium's visible overflow column.
        XCTAssertEqual(plan.slots, [
            .init(role: .flowBreakAfter, columnIndex: 0, yPx: 0),
            .init(role: .flowBreakAfter, columnIndex: 1, yPx: 0),
            .init(role: .flowBreakAfter, columnIndex: 2, yPx: 0),
            .init(role: .flowBreakAfter, columnIndex: 3, yPx: 0),
        ])
        // §8.2: overflow columns never grow the container block-size.
        XCTAssertEqual(plan.containerBlockSizePx, 19)
    }

    /// BRK4 — ≤N one-child chunks reproduce the greedy assignment (the green-cell guard).
    func testBRK4ChunkColumnsMatchGreedyForAtMostNChunks() {
        // balance-break-avoidance-001's shape class: every chunk one child
        // and chunks ≤ N — the plan must be render-identical to the greedy
        // path it replaces (engagement is then output-neutral).
        let heights: [Double] = [10, 20, 30]
        let plan = MulticolSpannerFlow.plan(
            children: heights.map { breaker($0) }, columnCount: 3)
        let greedy = MulticolDistribution.distribute(childHeightsPx: heights, columnCount: 3)
        // Same column per child, same in-column offset (all tops)…
        for i in heights.indices {
            XCTAssertEqual(plan.slots[i].columnIndex, greedy[i].columnIndex)
            XCTAssertEqual(plan.slots[i].yPx, greedy[i].yOffsetPx)
        }
        // …and the same container block-size (tallest chunk == tallest column).
        XCTAssertEqual(
            plan.containerBlockSizePx,
            MulticolDistribution.containerBlockSizePx(childHeightsPx: heights, slots: greedy))
    }

    /// BRK5 — a chunk may hold several children; the break ends it mid-segment.
    func testBRK5MultiChildChunkStacksBeforeTheBreak() {
        // Chunk 0 = {10-flow, 12-break} stacked; chunk 1 = {5-flow}.
        let plan = MulticolSpannerFlow.plan(
            children: [flow(10), breaker(12), flow(5)], columnCount: 2)
        XCTAssertEqual(plan.slots, [
            .init(role: .flow, columnIndex: 0, yPx: 0),
            .init(role: .flowBreakAfter, columnIndex: 0, yPx: 10),
            .init(role: .flow, columnIndex: 1, yPx: 0),
        ])
        // §7.1 forced-break balancing: H = the tallest chunk (22), not ceil(T/N).
        XCTAssertEqual(plan.containerBlockSizePx, 22)
    }

    /// DSC1 — the discard cascade crosses segments: later flow content drops too.
    func testDSC1DiscardLatchesAcrossSpannerAndSegment() {
        let plan = MulticolSpannerFlow.plan(
            children: [breaker(19), breaker(19), breaker(19), breaker(19),
                       spanner(10), flow(19)],
            columnCount: 3, discardOverflow: true)
        // The tail (spanner + post-spanner flow) is discarded wholesale…
        XCTAssertTrue(plan.slots[4].discarded)
        XCTAssertTrue(plan.slots[5].discarded)
        // …and contributes no block-size (container = the one retained row).
        XCTAssertEqual(plan.containerBlockSizePx, 19)
    }

    /// BRK6 — engagement: a forced break always takes the plan.
    func testBRK6BreakAfterChildrenEngage() {
        XCTAssertTrue(MulticolSpannerFlow.engages(
            children: [breaker(10), flow(10)], columnCount: 3))
    }

    /// BRK7 — rolesFor classifies break-after:column; flowCount counts it as flow.
    func testBRK7RolesForReadsBreakAfterColumn() {
        // The dm-003 wire shape: BreakAfter → "COLUMN" keyword.
        let breakChild = component("b", [IRProperty(type: "BreakAfter", data: .string("COLUMN"))])
        // `avoid` (and page keywords) must NOT force a column break.
        let avoidChild = component("v", [IRProperty(type: "BreakAfter", data: .string("AVOID"))])
        XCTAssertEqual(
            MulticolSpannerFlow.rolesFor(children: [breakChild, avoidChild]),
            [.flowBreakAfter, .flow])
        // The forced-break child is still IN FLOW for every counting gate
        // (multicolDistributes' ≥2 routing rides this).
        XCTAssertEqual(MulticolSpannerFlow.flowCount([.flowBreakAfter, .flow]), 2)
    }

    /// FLT1 — specsFor flags floated subtrees (the run fragmenter's float bail).
    func testFLT1SpecsForMarksFloatedSubtrees() {
        // floats-clear-multicol-000's .container: floats one level DOWN.
        let floatChild = component("fl", [IRProperty(type: "Float", data: .string("LEFT"))])
        let container = IRComponent(id: "c", name: "c", properties: [],
                                    selectors: nil, media: nil, children: [floatChild],
                                    slot: nil, text: nil, pseudos: nil, meta: nil)
        // `float: none` never counts — only actually floated boxes do.
        let noneFloat = component("nf", [IRProperty(type: "Float", data: .string("NONE"))])
        let specs = MulticolSpannerFlow.specsFor(children: [container, noneFloat])
        XCTAssertTrue(specs[0].floatedContent)
        XCTAssertFalse(specs[1].floatedContent)
    }

    /// FBK1 — specsFor flags the forced column breaks the ROLE list cannot
    /// see (the run fragmenter's forced-break bail). Pinned against the
    /// LIVE wave41-final IR of css-break block-in-inline-013/014
    /// (tools/titan/runs/wave41-final/sections/css-break/per-test-ir/):
    /// the multicol's own children are anonymous `<span>` wrappers with NO
    /// break property, and the `BreakBefore/BreakAfter: COLUMN` sits on
    /// their inner divs — so without this flag a run fragmenter sees four
    /// plain 100px flow children in a 400px fill:auto container, concludes
    /// T == H ("fits one column"), and stacks all four in column 0 instead
    /// of the one-box-per-column render that is Chromium's reference.
    /// Byte-twin of the Android FBK1 row.
    func testFBK1SpecsForMarksHiddenForcedColumnBreaks() {
        // block-in-inline-014's shape: span > div[break-after: column].
        let innerAfter = component("d", [IRProperty(type: "BreakAfter", data: .string("COLUMN"))])
        let span = IRComponent(id: "s", name: "s", properties: [],
                               selectors: nil, media: nil, children: [innerAfter],
                               slot: nil, text: nil, pseudos: nil, meta: nil)
        // block-in-inline-013's shape: span > div[break-before: column].
        let innerBefore = component("d2", [IRProperty(type: "BreakBefore", data: .string("COLUMN"))])
        let span2 = IRComponent(id: "s2", name: "s2", properties: [],
                                selectors: nil, media: nil, children: [innerBefore],
                                slot: nil, text: nil, pseudos: nil, meta: nil)
        // The child's OWN break-before is equally invisible to the roles
        // (which only classify break-AFTER) — so it must flag too.
        let ownBefore = component("ob", [IRProperty(type: "BreakBefore", data: .string("COLUMN"))])
        // The child's OWN break-after is NOT hidden — it becomes
        // .flowBreakAfter and the chunk walk honours it.
        let ownAfter = component("oa", [IRProperty(type: "BreakAfter", data: .string("COLUMN"))])
        // A PAGE break targets another fragmentation context (§4.1).
        let pageBreak = component("pg", [IRProperty(type: "BreakAfter", data: .string("PAGE"))])
        let specs = MulticolSpannerFlow.specsFor(
            children: [span, span2, ownBefore, ownAfter, pageBreak])
        XCTAssertTrue(specs[0].forcedBreakContent)
        XCTAssertTrue(specs[1].forcedBreakContent)
        XCTAssertTrue(specs[2].forcedBreakContent)
        XCTAssertFalse(specs[3].forcedBreakContent)
        XCTAssertFalse(specs[4].forcedBreakContent)
        // …and the own-break-after child is still the forced-break ROLE.
        XCTAssertEqual(specs[3].role, .flowBreakAfter)
        // Same call pins the MONOLITHIC flag (css-break-3 §4.1): the span
        // wrappers have children (breakable), the leaf boxes do not.
        XCTAssertFalse(specs[0].monolithicContent)
        XCTAssertFalse(specs[1].monolithicContent)
        XCTAssertTrue(specs[2].monolithicContent)
        // A leaf with TEXT is breakable — line boxes are class-B points.
        let texty = IRComponent(id: "t", name: "t", properties: [],
                                selectors: nil, media: nil, children: nil,
                                slot: nil, text: "Line 1", pseudos: nil, meta: nil)
        XCTAssertFalse(MulticolSpannerFlow.specsFor(children: [texty])[0].monolithicContent)
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

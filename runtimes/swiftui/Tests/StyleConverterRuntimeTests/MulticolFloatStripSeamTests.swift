//
//  MulticolFloatStripSeamTests.swift
//  Wave-45 lane X3 + wave-48 lane W3 — the float-strip CONSUMPTION pins
//  (FS-Z rows).
//
//  Pins MulticolFloatStripSeam.swift + MulticolFloatStripSlice.swift:
//  the zero-flow plan (the true twin of Kotlin FS-B1 — wave 44 could
//  only pin engagement because zeroFlowPlan did not exist on iOS), the
//  shared pre-measure predicate, the slice-replay measure geometry the
//  wave-48 row places with (sliceGeometry — it replaced wave-45's
//  whole-child columnSlot mapping), and the composition-time
//  EngagedStrip gate. Geometry rows are hand-derived from the same
//  frozen Chromium refs as MulticolFloatStripTests
//  (tools/wpt/refs/9b5435…/CSS2/floats-clear__floats-clear-multicol-*).
//

import XCTest
@testable import StyleConverterRuntime

final class MulticolFloatStripSeamTests: XCTestCase {

    // MARK: - IR builders (same live wire shapes as the FS table)

    /// A leaf component with just properties.
    private func component(_ id: String, _ properties: [IRProperty],
                           children: [IRComponent]? = nil) -> IRComponent {
        IRComponent(id: id, name: id, properties: properties,
                    selectors: nil, media: nil, children: children, slot: nil,
                    text: nil, pseudos: nil, meta: nil)
    }

    /// A px length wire: `{"type":"length","px":N}` (converter shape).
    private func px(_ n: Double) -> IRValue {
        .object(["type": .string("length"), "px": .double(n)])
    }

    /// One aqua float box exactly as the wave43-final IRs carry it.
    private func floatBox(_ id: String, _ side: String, _ h: Double) -> IRComponent {
        component(id, [IRProperty(type: "Float", data: .string(side)),
                       IRProperty(type: "Width", data: px(15)),
                       IRProperty(type: "Height", data: px(h))])
    }

    /// The `.container` (width:100%; two floats) of -000/002/balancing.
    private func container(_ h: Double = 250) -> IRComponent {
        component("container",
                  [IRProperty(type: "Width",
                              data: .object(["type": .string("percentage"), "value": .double(100)]))],
                  children: [floatBox("fL", "LEFT", h), floatBox("fR", "RIGHT", h)])
    }

    /// The -002 `.clear` (clear:left; medium orange border-bottom).
    private var clear002: IRComponent {
        component("clear", [IRProperty(type: "Clear", data: .string("LEFT")),
                            IRProperty(type: "BorderBottomStyle", data: .string("SOLID"))])
    }

    /// specsFor over the -002 IR shape — the real integration entry.
    private func specs002() -> [MulticolSpannerFlow.ChildSpec] {
        MulticolSpannerFlow.specsFor(children: [container(), clear002])
    }

    // MARK: - FS-Z1/Z2: the zero-flow plan (the Kotlin FS-B1 twin)

    /// FS-Z1 — the 002 shape zero-flows exactly the two floats, both
    /// fill modes (250px floats are a provable ink floor, so the §7.1
    /// balance branch's pre-measure condition holds too).
    func testFSZ1The002ShapeZeroFlowsTheFloats() {
        let specs = specs002()
        XCTAssertNotNil(MulticolFloatStrip.zeroFlowPlan(specs: specs, columnFillAuto: false))
        let plan = MulticolFloatStrip.zeroFlowPlan(specs: specs, columnFillAuto: true)
        // Scope: the multicol children (so their block loops ADOPT the
        // inherited plan — clearanceScopePlan's scopeIds gate) + floats.
        XCTAssertEqual(plan?.scopeIds, ["container", "clear", "fL", "fR"])
        // Adjustments: zero flow height for the floats, nothing else —
        // no appliedTopPx, so the §9.5.2 margin-override channel stays
        // untouched (the strip's clearance moves through the LAYOUT's
        // offsets, never through a margin rewrite).
        XCTAssertEqual(plan.map { Set($0.adjustments.keys) }, ["fL", "fR"])
        XCTAssertEqual(plan?.adjustments.values.allSatisfy {
            $0.zeroFlowHeight && $0.appliedTopPx == nil
        }, true)
    }

    /// FS-Z2 — non-engaging shapes produce no plan: nil specs (dark
    /// stage), float-less children, and the balance mode without a
    /// provable ink floor (every float height 0, no trailing band —
    /// the conservative side of the shared predicate, stated plainly
    /// in engagesPreMeasure's doc).
    func testFSZ2NoPlanForNonEngagingShapes() {
        XCTAssertNil(MulticolFloatStrip.zeroFlowPlan(specs: nil, columnFillAuto: true))
        XCTAssertNil(MulticolFloatStrip.zeroFlowPlan(
            specs: MulticolSpannerFlow.specsFor(children: [clear002]),
            columnFillAuto: true))
        // Zero-height floats AND an ink-free sibling (no border band):
        // the engaged shape whose balance branch has no provable C ≥ 1 —
        // the Kotlin FS-E2 conservative row. Fill keeps its definite H
        // and still engages the same shape.
        let inkFree = MulticolSpannerFlow.specsFor(children: [
            container(0),
            component("clear", [IRProperty(type: "Clear", data: .string("LEFT"))]),
        ])
        XCTAssertNil(MulticolFloatStrip.zeroFlowPlan(specs: inkFree, columnFillAuto: false))
        XCTAssertNotNil(MulticolFloatStrip.zeroFlowPlan(specs: inkFree, columnFillAuto: true))
        // The -002 cleared box's 3px border band IS provable trailing
        // ink, so THAT shape engages under balance too (see FS-Z3).
        XCTAssertNotNil(MulticolFloatStrip.zeroFlowPlan(
            specs: MulticolSpannerFlow.specsFor(children: [container(0), clear002]),
            columnFillAuto: false))
    }

    // MARK: - FS-Z3: the shared pre-measure predicate + the ink floor

    /// FS-Z3 — provableStripInkPx: the IR-only half of C. 002: the 250px
    /// float heights dominate; zero-height floats leave the cleared
    /// box's 3px band; nil/empty specs prove nothing.
    func testFSZ3ProvableInkFloor() {
        XCTAssertEqual(MulticolFloatStrip.provableStripInkPx(specs002()), 250)
        XCTAssertEqual(MulticolFloatStrip.provableStripInkPx(
            MulticolSpannerFlow.specsFor(children: [container(0), clear002])), 3)
        XCTAssertEqual(MulticolFloatStrip.provableStripInkPx(nil), 0)
        // engagesPreMeasure composes engages() with the balance-only
        // floor: fill engages the 002 shape, balance does too (250 ≥ 1).
        XCTAssertTrue(MulticolFloatStrip.engagesPreMeasure(specs002(), columnFillAuto: true))
        XCTAssertTrue(MulticolFloatStrip.engagesPreMeasure(specs002(), columnFillAuto: false))
        // Non-engaging shape: both modes false (shape gate first).
        XCTAssertFalse(MulticolFloatStrip.engagesPreMeasure(nil, columnFillAuto: true))
    }

    // MARK: - FS-Z4: the slice-replay measure geometry (wave 48, lane W3)

    /// The -balancing-002 `.clear` (clear:left; 5px orange border-bottom
    /// — the wire the per-test IR carries: BorderBottomWidth {"px":5}).
    private var clearBalancing002: IRComponent {
        component("clear", [IRProperty(type: "Clear", data: .string("LEFT")),
                            IRProperty(type: "BorderBottomStyle", data: .string("SOLID")),
                            IRProperty(type: "BorderBottomWidth",
                                       data: .object(["px": .double(5)]))])
    }

    /// FS-Z4 — sliceGeometry (the measure half's one shared resolver)
    /// on the live -002 / -balancing-002 shapes with the zero-flow
    /// measured heights the slice layout produces (float container 0 —
    /// all content out of flow — and the cleared box's border band).
    /// The band arithmetic y − band·h reproduces the fixture positions
    /// the refs pin: fill orange at column-2 local 50 (ref rows 161-163
    /// = content 50-52 of column 3), balance at local 80 (rows 171-175
    /// = content 80-84) — the same rows the deleted wave-45 columnSlot
    /// table carried, now expressed through the replay's own shift.
    func testFSZ4SliceGeometry() {
        // 002 under §7.2 fill (H=100): offsets [0, 250], h stays 100,
        // C = 250 + the 3px medium border band = 253, three slices
        // (ceil(253/100)) — the refs' 100+100+50(+3) aqua/orange split.
        let fill = MulticolFloatStrip.sliceGeometry(
            specs: specs002(), measuredHeightsPx: [0, 3],
            proposedBlockSizePx: 100, columnFillAuto: true,
            columnWidthPx: 100, columnGapPx: 0, columnCount: 3)
        XCTAssertEqual(fill?.yOffsetsPx, [0, 250])
        XCTAssertEqual(fill?.columnBlockSizePx, 100)
        XCTAssertEqual(fill?.stripInkPx, 253)
        XCTAssertEqual(fill?.fragments.count, 3)
        // Band 2's replay shift puts the cleared box at local 50 — the
        // ref's orange, immediately after the 250px aqua (161-163).
        XCTAssertEqual(fill.map { $0.yOffsetsPx[1] - 2 * $0.columnBlockSizePx }, 50)
        // -balancing-002 (initial §7.1 balance, declared H=100): the 5px
        // band makes C = 255 and the strip balances to h = ceil(255/3)
        // = 85 — the refs keep the 100px box with 85px columns.
        let bal = MulticolFloatStrip.sliceGeometry(
            specs: MulticolSpannerFlow.specsFor(children: [container(), clearBalancing002]),
            measuredHeightsPx: [0, 5],
            proposedBlockSizePx: 100, columnFillAuto: false,
            columnWidthPx: 100, columnGapPx: 0, columnCount: 3)
        XCTAssertEqual(bal?.columnBlockSizePx, 85)
        XCTAssertEqual(bal?.stripInkPx, 255)
        // Band 2's shift: 250 − 170 = 80 — the ref's orange rows 171-175.
        XCTAssertEqual(bal.map { $0.yOffsetsPx[1] - 2 * $0.columnBlockSizePx }, 80)
        // The place pass re-derives from the GRANTED height (h, not H) —
        // a fixed point: balance with H := 85 answers the same 85.
        XCTAssertEqual(MulticolFloatStrip.sliceGeometry(
            specs: MulticolSpannerFlow.specsFor(children: [container(), clearBalancing002]),
            measuredHeightsPx: [0, 5],
            proposedBlockSizePx: 85, columnFillAuto: false,
            columnWidthPx: 100, columnGapPx: 0, columnCount: 3)?.columnBlockSizePx, 85)
    }

    /// FS-Z4b — the probe branch (nil / non-finite proposal: SwiftUI's
    /// ideal and max probes, or an auto-height chain): no fragmentainer,
    /// so the spec-true auto-height answer — columns exactly as tall as
    /// the content: h = C under fill (§7.2), ceil(C/N) under balance
    /// (§7.1). The min probe (0) has no geometry at all.
    func testFSZ4bProbeBranch() {
        // Fill: the auto-height column IS the whole strip (C = 253).
        XCTAssertEqual(MulticolFloatStrip.sliceGeometry(
            specs: specs002(), measuredHeightsPx: [0, 3],
            proposedBlockSizePx: nil, columnFillAuto: true,
            columnWidthPx: 100, columnGapPx: 0, columnCount: 3)?.columnBlockSizePx, 253)
        // Balance: ceil(253/3) = 85 (the 3px-band variant's balanced h).
        XCTAssertEqual(MulticolFloatStrip.sliceGeometry(
            specs: specs002(), measuredHeightsPx: [0, 3],
            proposedBlockSizePx: nil, columnFillAuto: false,
            columnWidthPx: 100, columnGapPx: 0, columnCount: 3)?.columnBlockSizePx, 85)
        // The min probe (an explicit 0) has no fragmentainer — nil, and
        // the layout answers the probe with a zero-height claim.
        XCTAssertNil(MulticolFloatStrip.sliceGeometry(
            specs: specs002(), measuredHeightsPx: [0, 3],
            proposedBlockSizePx: 0, columnFillAuto: true,
            columnWidthPx: 100, columnGapPx: 0, columnCount: 3))
        // Desynced inputs (specs vs measured heights) decline through the
        // plan's defensive re-gate — the slice's flush-stack fallback.
        XCTAssertNil(MulticolFloatStrip.sliceGeometry(
            specs: specs002(), measuredHeightsPx: [0],
            proposedBlockSizePx: 100, columnFillAuto: true,
            columnWidthPx: 100, columnGapPx: 0, columnCount: 3))
    }

    // MARK: - FS-Z5: the composition-time EngagedStrip gate

    /// FS-Z5 — engagedStrip: non-nil exactly when BOTH halves may
    /// engage; each closed gate answers nil (definite H, N>1,
    /// horizontal-tb, proven specs).
    func testFSZ5EngagedStripGates() {
        let specs = specs002()
        // The engaged shape: threads the specs and the SAME plan the
        // environment publication will carry (one decision, two
        // consumers — the half-engage guard).
        let engaged = MulticolFloatStrip.engagedStrip(
            specs: specs, columnFillAuto: true, definiteColumnBlockSizePx: 100,
            usedColumnCount: 3, horizontalWritingMode: true)
        XCTAssertEqual(engaged?.specs.count, 2)
        XCTAssertEqual(engaged?.zeroFlowPlan,
                       MulticolFloatStrip.zeroFlowPlan(specs: specs, columnFillAuto: true))
        // No fragmentainer (auto height / degenerate H) → nil.
        XCTAssertNil(MulticolFloatStrip.engagedStrip(
            specs: specs, columnFillAuto: true, definiteColumnBlockSizePx: nil,
            usedColumnCount: 3, horizontalWritingMode: true))
        XCTAssertNil(MulticolFloatStrip.engagedStrip(
            specs: specs, columnFillAuto: true, definiteColumnBlockSizePx: 0,
            usedColumnCount: 3, horizontalWritingMode: true))
        // N == 1 overflows below instead of slicing → nil.
        XCTAssertNil(MulticolFloatStrip.engagedStrip(
            specs: specs, columnFillAuto: true, definiteColumnBlockSizePx: 100,
            usedColumnCount: 1, horizontalWritingMode: true))
        // Vertical writing modes are blocked-platform for the y-math.
        XCTAssertNil(MulticolFloatStrip.engagedStrip(
            specs: specs, columnFillAuto: true, definiteColumnBlockSizePx: 100,
            usedColumnCount: 3, horizontalWritingMode: false))
        // Unproven specs (dark stage nil) → nil.
        XCTAssertNil(MulticolFloatStrip.engagedStrip(
            specs: nil, columnFillAuto: true, definiteColumnBlockSizePx: 100,
            usedColumnCount: 3, horizontalWritingMode: true))
    }

    // MARK: - FS-Z7: the typed `column-fill` fold (ColumnsExtractor)

    /// FS-Z7 — the fill-mode wire: only the AUTO keyword flips fillAuto
    /// (css-multicol-1 §7.1); balance / absent keep the initial-balance
    /// false, and a duplicated declaration folds last-write-wins
    /// (css-cascade-5 §6.4.4 — the same idiom as continueDiscard).
    func testFSZ7ColumnFillExtraction() {
        // The live -002 wire: {"type":"ColumnFill","data":"AUTO"}.
        XCTAssertEqual(ColumnsExtractor.extract(from: [
            IRProperty(type: "ColumnFill", data: .string("AUTO")),
        ])?.fillAuto, true)
        // The initial balance — explicit or absent — stays false.
        XCTAssertEqual(ColumnsExtractor.extract(from: [
            IRProperty(type: "ColumnFill", data: .string("BALANCE")),
        ])?.fillAuto, false)
        XCTAssertEqual(ColumnsExtractor.extract(from: [
            IRProperty(type: "ColumnCount", data: .double(3)),
        ])?.fillAuto, false)
        // Last declaration wins: [AUTO, BALANCE] resolves balance.
        XCTAssertEqual(ColumnsExtractor.extract(from: [
            IRProperty(type: "ColumnFill", data: .string("AUTO")),
            IRProperty(type: "ColumnFill", data: .string("BALANCE")),
        ])?.fillAuto, false)
    }

    // MARK: - FS-Z6: wiring source pins (the MulticolDiscardThreading
    // precedent — the Layout protocol and the renderer's ViewBuilder only
    // run inside a live SwiftUI host, so the seam is pinned structurally)

    /// Walk up from THIS SOURCE FILE until the relative path resolves
    /// (#filePath is the only reliable anchor — the Catalyst test
    /// process's working directory sits outside the repo).
    private static func repoPath(_ relative: String) throws -> String {
        var dir = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        while dir.path != "/" {
            let candidate = dir.appendingPathComponent(relative)
            if FileManager.default.fileExists(atPath: candidate.path) {
                return candidate.path
            }
            dir = dir.deletingLastPathComponent()
        }
        throw XCTSkip("repo root not found for \(relative)")
    }

    /// The renderer's multicol branch (X3 hunk; W3 since wave 48)
    /// composes the slice-replay row for an engaged strip AND publishes
    /// the matching zero-flow plan on the floatClearancePlan environment
    /// — the two-consumer contract — while the non-engaged arm keeps the
    /// greedy layout with the wave-42 clearance scope plan.
    func testRendererMulticolBranchDeliversBothHalves() throws {
        let src = try String(contentsOfFile: Self.repoPath(
            "runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift"),
            encoding: .utf8)
        // The replay row (the strip's layout consumer since wave 48).
        XCTAssertTrue(src.contains("MulticolFloatStripSliceLayout("),
                      "the multicol branch must compose the slice-replay row for engaged strips")
        // The environment publication (the zero-flow paint consumer) on
        // the SAME engagement that composed the row.
        XCTAssertTrue(src.contains(".environment(\\.floatClearancePlan, stripSeam.zeroFlowPlan)"),
                      "the strip arm must publish the engaged zero-flow plan on floatClearancePlan")
        // The non-strip arm keeps the wave-42 clearance scope plan.
        XCTAssertTrue(src.contains(".environment(\\.floatClearancePlan, clearanceScopePlan)"),
                      "the greedy arm must keep publishing the clearance scope plan")
        // The wave-45 per-child anchor-column placement is GONE — the
        // greedy layout no longer carries a strip parameter (the seam-2
        // gap this replaced: float ink stacked in one overflowing column).
        XCTAssertFalse(src.contains("floatStrip: stripSeam"),
                       "the superseded greedy-layout strip parameter must not come back")
    }

    /// The slice layout (the measure half) consumes the shared FS
    /// geometry through sliceGeometry and clips each clone to its
    /// css-break-3 §4 band via the −band·h shift.
    func testSliceLayoutConsumesTheSharedGeometry() throws {
        let slice = try String(contentsOfFile: Self.repoPath(
            "runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/columns/MulticolFloatStripSlice.swift"),
            encoding: .utf8)
        XCTAssertTrue(slice.contains("MulticolFloatStrip.sliceGeometry("),
                      "the slice layout must resolve through the one shared geometry resolver")
        XCTAssertTrue(slice.contains("Double(bandIndex) * sp.columnBlockSizePx"),
                      "the slice placement must shift by the band's replay translate")
        // The resolver itself delegates to the FS-table plan — the one
        // geometry owner (no parallel strip walk anywhere on iOS).
        XCTAssertTrue(slice.contains("return plan(specs: specs"),
                      "sliceGeometry must delegate to MulticolFloatStrip.plan")
    }
}

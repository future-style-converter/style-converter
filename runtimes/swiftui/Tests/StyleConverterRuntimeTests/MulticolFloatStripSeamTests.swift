//
//  MulticolFloatStripSeamTests.swift
//  Wave-45 lane X3 — the float-strip CONSUMPTION pins (FS-Z rows).
//
//  Pins MulticolFloatStripSeam.swift: the zero-flow plan (now the true
//  twin of Kotlin FS-B1 — wave 44 could only pin engagement because
//  zeroFlowPlan did not exist on iOS), the shared pre-measure predicate,
//  the whole-child column mapping the layout places with, and the
//  composition-time EngagedStrip gate. Geometry rows are hand-derived
//  from the same frozen Chromium refs as MulticolFloatStripTests
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

    // MARK: - FS-Z4: the whole-child column mapping (columnSlot)

    /// FS-Z4 — ⌊y/h⌋ + local remainder, capped at N−1. Rows are the
    /// live fixture positions: the cleared box at strip 250 lands in
    /// column 2 at local 50 under fill (H=100 — ref orange rows 161-163
    /// = content 50-52 of column 3) and at local 80 under balance
    /// (h=85 — ref orange rows 171-175 = content 80-84).
    func testFSZ4ColumnSlotMapping() {
        // The float container anchors at strip 0 → column 0, local 0.
        XCTAssertEqual(MulticolFloatStrip.columnSlot(
            yOffsetPx: 0, columnBlockSizePx: 100, columnCount: 3).column, 0)
        // Fill 002: cleared box strip 250 → column 2, local 50.
        let fill = MulticolFloatStrip.columnSlot(
            yOffsetPx: 250, columnBlockSizePx: 100, columnCount: 3)
        XCTAssertEqual(fill.column, 2)
        XCTAssertEqual(fill.localYPx, 50)
        // Balance 002: h=85 → column 2, local 80.
        let bal = MulticolFloatStrip.columnSlot(
            yOffsetPx: 250, columnBlockSizePx: 85, columnCount: 3)
        XCTAssertEqual(bal.column, 2)
        XCTAssertEqual(bal.localYPx, 80)
        // 003's step-displaced container: strip 10 → column 0, local 10.
        XCTAssertEqual(MulticolFloatStrip.columnSlot(
            yOffsetPx: 10, columnBlockSizePx: 100, columnCount: 3).localYPx, 10)
        // Past the last column: capped at N−1, overflowing downward
        // (css-overflow-3 §2 / the wave-10 fill cap — no §8.2 overflow
        // columns for the strip).
        let over = MulticolFloatStrip.columnSlot(
            yOffsetPx: 350, columnBlockSizePx: 100, columnCount: 3)
        XCTAssertEqual(over.column, 2)
        XCTAssertEqual(over.localYPx, 150)
        // Degenerate inputs answer totally (a plan never produces them).
        XCTAssertEqual(MulticolFloatStrip.columnSlot(
            yOffsetPx: 7, columnBlockSizePx: 0, columnCount: 3).localYPx, 7)
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
    /// (css-multicol-1 §7.2); balance / absent keep the initial-balance
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

    /// The renderer's multicol branch (X3 hunk) threads the seam into
    /// the layout AND publishes the matching zero-flow plan on the
    /// floatClearancePlan environment — the two-consumer contract.
    func testRendererMulticolBranchDeliversBothHalves() throws {
        let src = try String(contentsOfFile: Self.repoPath(
            "runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift"),
            encoding: .utf8)
        // The layout parameter (the strip's geometry consumer).
        XCTAssertTrue(src.contains("floatStrip: stripSeam"),
                      "the multicol branch must thread the strip seam into MulticolGreedyLayout")
        // The environment publication (the zero-flow paint consumer) —
        // with the wave-42 clearance scope plan as the non-strip term.
        XCTAssertTrue(src.contains("stripSeam?.zeroFlowPlan ?? clearanceScopePlan"),
                      "the multicol branch must publish the zero-flow plan on floatClearancePlan")
    }

    /// MulticolGreedyLayout declares the seam parameter (defaulted nil —
    /// the dark-stage identity); its strip branch (split into
    /// MulticolGreedyLayoutStrip.swift by the file-size rule) consumes
    /// the shared FS geometry + the column mapping.
    func testGreedyLayoutConsumesTheSeam() throws {
        let host = try String(contentsOfFile: Self.repoPath(
            "runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/columns/MulticolGreedyLayout.swift"),
            encoding: .utf8)
        XCTAssertTrue(host.contains("var floatStrip: MulticolFloatStrip.EngagedStrip? = nil"),
                      "MulticolGreedyLayout must carry the seam, defaulted nil")
        let strip = try String(contentsOfFile: Self.repoPath(
            "runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/columns/MulticolGreedyLayoutStrip.swift"),
            encoding: .utf8)
        XCTAssertTrue(strip.contains("MulticolFloatStrip.plan("),
                      "the strip branch must build the shared FS geometry")
        XCTAssertTrue(strip.contains("MulticolFloatStrip.columnSlot("),
                      "the strip placement must map offsets through columnSlot")
    }
}

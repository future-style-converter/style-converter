//
//  MulticolDistributionTests.swift
//  StyleConverterRuntimeTests
//
//  Lane ios-multichild-multicol — two layers of proof, same shape as
//  WPTBlockChildFillTests:
//    1. the SHARED distribution pin table (D1–D7): byte-for-byte the
//       same scenarios as Android's JVM MultiColumnDistributionTest
//       (runtimes/compose …/columns/MultiColumnDistributionTest.kt).
//       The parity gate is "match ANDROID's greedy min-height
//       heuristic" — child order, min-height column choice, first-index
//       tie-break, flush stacking, tallest-column container height —
//       NOT css-multicol-1 §7.1's true balancing.
//    2. an end-to-end ImageRenderer raster pin: three fixed-height
//       children of a 2-column container land per the greedy rule
//       (child 1 in column 0, child 2 in column 1, child 3 back in
//       column 0 under child 1) — the pre-lane vertical stack would
//       leave column 1 empty at the probed pixel.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class MulticolDistributionTests: XCTestCase {

    // Shorthand for the D-table rows (mirrors the Kotlin typealias).
    private typealias Slot = MulticolDistribution.Slot

    // MARK: - The shared D-table (pure, no render surface)

    /// D1 — the raster-pin scenario: 3 equal children across 2 columns.
    /// Greedy trace: c0→col0@0, c1→col1@0, then c2 sees a 40/40 tie and
    /// the FIRST minimal index (column 0) wins.
    func testD1ThreeEqualChildrenInTwoColumns() {
        XCTAssertEqual(
            MulticolDistribution.distribute(childHeightsPx: [40, 40, 40], columnCount: 2),
            [Slot(columnIndex: 0, yOffsetPx: 0),
             Slot(columnIndex: 1, yOffsetPx: 0),
             Slot(columnIndex: 0, yOffsetPx: 40)],
            "D1 must match Android: tie breaks back to column 0")
    }

    /// D2 — mixed heights across 3 columns: after cols 0..2 fill to
    /// 10/30/20, the 4th child returns to the SHORTEST column (0), not
    /// a round-robin "next" one.
    func testD2FourthChildLandsOnShortestColumn() {
        XCTAssertEqual(
            MulticolDistribution.distribute(childHeightsPx: [10, 30, 20, 40], columnCount: 3),
            [Slot(columnIndex: 0, yOffsetPx: 0),
             Slot(columnIndex: 1, yOffsetPx: 0),
             Slot(columnIndex: 2, yOffsetPx: 0),
             Slot(columnIndex: 0, yOffsetPx: 10)],
            "D2 must match Android: min-height choice, not round-robin")
    }

    /// D3 — the tie-break rule is part of the parity contract: every
    /// pick ties, and the first-minimum answer alternates 0,1,0,1.
    func testD3AllEqualChildrenAlternateViaFirstIndexTieBreak() {
        XCTAssertEqual(
            MulticolDistribution.distribute(childHeightsPx: [20, 20, 20, 20], columnCount: 2),
            [Slot(columnIndex: 0, yOffsetPx: 0),
             Slot(columnIndex: 1, yOffsetPx: 0),
             Slot(columnIndex: 0, yOffsetPx: 20),
             Slot(columnIndex: 1, yOffsetPx: 20)],
            "D3 must match Android's minByOrNull first-minimum tie-break")
    }

    /// D4 — greedy is order-dependent, NOT optimal balancing: 50,10,10,50
    /// stacks 10+10+50 in column 1 (70 tall) where true balancing pairs
    /// 50+10 twice (60). Both platforms pin the GREEDY answer on purpose.
    func testD4GreedyAcceptsSuboptimalSeventyTallResult() {
        // The exact Android assignment for the order-dependent case.
        let slots = MulticolDistribution.distribute(childHeightsPx: [50, 10, 10, 50], columnCount: 2)
        XCTAssertEqual(
            slots,
            [Slot(columnIndex: 0, yOffsetPx: 0),
             Slot(columnIndex: 1, yOffsetPx: 0),
             Slot(columnIndex: 1, yOffsetPx: 10),
             Slot(columnIndex: 1, yOffsetPx: 20)],
            "D4 must pin the greedy assignment, not spec balancing")
        // Container height is the greedy 70, not the balanced 60.
        XCTAssertEqual(
            MulticolDistribution.containerBlockSizePx(childHeightsPx: [50, 10, 10, 50], slots: slots),
            70, "D4 container height must be the greedy 70")
    }

    /// D5 — fewer children than columns: the third column stays empty
    /// and the container height is the taller OCCUPIED column.
    func testD5TrailingColumnsStayEmpty() {
        // c0→col0, c1→col1; col2 never receives a child.
        let slots = MulticolDistribution.distribute(childHeightsPx: [25, 35], columnCount: 3)
        XCTAssertEqual(
            slots,
            [Slot(columnIndex: 0, yOffsetPx: 0),
             Slot(columnIndex: 1, yOffsetPx: 0)],
            "D5 must fill columns in order and leave the rest empty")
        // Height = 35 (the taller occupied column; empty col contributes 0).
        XCTAssertEqual(
            MulticolDistribution.containerBlockSizePx(childHeightsPx: [25, 35], slots: slots),
            35, "D5 container height must be the taller occupied column")
    }

    /// D6 — the single-child degenerate case: column 0 at offset 0, and
    /// the child's own height IS the container height (why the renderer
    /// gate can keep single-child containers on the vertical stack).
    func testD6SingleChildLandsInColumnZero() {
        let slots = MulticolDistribution.distribute(childHeightsPx: [100], columnCount: 2)
        XCTAssertEqual(slots, [Slot(columnIndex: 0, yOffsetPx: 0)],
                       "D6: a lone child must sit at the column-0 origin")
        XCTAssertEqual(
            MulticolDistribution.containerBlockSizePx(childHeightsPx: [100], slots: slots),
            100, "D6 container height must equal the lone child's height")
    }

    /// D7 — defensive coercion: a degenerate sub-1 count distributes as
    /// one flush column (unreachable via MulticolMath, floored anyway).
    func testD7SubOneColumnCountCoercesToSingleColumn() {
        XCTAssertEqual(
            MulticolDistribution.distribute(childHeightsPx: [10, 20], columnCount: 0),
            [Slot(columnIndex: 0, yOffsetPx: 0),
             Slot(columnIndex: 0, yOffsetPx: 10)],
            "D7 must floor the count at 1 like Android's maxOf(1, count)")
    }

    /// Empty input — no children means no slots and a 0-height container
    /// (Android's `columnHeights.maxOrNull() ?: 0` twin).
    func testEmptyChildListYieldsNoSlotsAndZeroHeight() {
        let slots = MulticolDistribution.distribute(childHeightsPx: [], columnCount: 3)
        XCTAssertEqual(slots, [], "no children must produce no slots")
        XCTAssertEqual(
            MulticolDistribution.containerBlockSizePx(childHeightsPx: [], slots: slots),
            0, "an empty distribution must report zero container height")
    }

    // MARK: - End-to-end raster pin (ImageRenderer)

    /// Render on a white canvas at scale 1 and return the RGB bytes at
    /// (x, y) — the same CGContext harness as WPTBlockChildFillTests /
    /// ContainingBlockHeightTests (product path: WPT flag untouched).
    @MainActor
    private func pixel(_ comp: IRComponent,
                       x: Int, y: Int) throws -> (r: UInt8, g: UInt8, b: UInt8) {
        // Product-path render: no capture flag — distribution is product
        // behaviour (Android distributes unconditionally), not WPT-gated.
        let view = ComponentRenderer(component: comp)
            .frame(width: 390, height: 100, alignment: .topLeading)
            .background(Color.white)
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        let cg = try XCTUnwrap(renderer.cgImage, "ImageRenderer produced no image")
        let w = cg.width, h = cg.height
        var buf = [UInt8](repeating: 0, count: w * h * 4)
        let ctx = try XCTUnwrap(CGContext(
            data: &buf, width: w, height: h, bitsPerComponent: 8,
            bytesPerRow: w * 4, space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        let i = (y * w + x) * 4
        return (buf[i], buf[i + 1], buf[i + 2])
    }

    /// The lane's raster pin: a 300px-wide 2-column container
    /// (column-gap 20 → used W = (300 − 20)/2 = 140, css-multicol-1 §3)
    /// with three 80×40 children (red, green, blue). Greedy (D1 shape):
    ///   red   → column 0 → box (0, 0)–(80, 40)
    ///   green → column 1 → box (160, 0)–(240, 40)  [x = 1·(140+20)]
    ///   blue  → column 0 → box (0, 40)–(80, 80)    [tie → first index]
    /// The pre-lane vertical stack put green at (0, 40) — so the probe
    /// inside column 1 discriminates the two layouts directly. Wire
    /// shapes pinned against live converter output: ColumnCount ships as
    /// a bare JSON number (ColumnCountSerializer), ColumnGap as the raw
    /// `{px}` length object (GapExtractor's extractLength).
    @MainActor
    func testThreeFixedHeightChildrenDistributeAcrossTwoColumns() throws {
        let comp = try JSONDecoder().decode(IRComponent.self, from: Data("""
        {"id":"p","name":"MulticolParent",
         "properties":[
           {"type":"Width","data":{"type":"length","px":300.0}},
           {"type":"ColumnCount","data":2.0},
           {"type":"ColumnGap","data":{"px":20.0}}
         ],
         "children":[
           {"id":"a","name":"A","properties":[
             {"type":"Width","data":{"type":"length","px":80.0}},
             {"type":"Height","data":{"type":"length","px":40.0}},
             {"type":"BackgroundColor","data":{"srgb":{"r":1.0,"g":0.0,"b":0.0},"original":"#ff0000"}}]},
           {"id":"b","name":"B","properties":[
             {"type":"Width","data":{"type":"length","px":80.0}},
             {"type":"Height","data":{"type":"length","px":40.0}},
             {"type":"BackgroundColor","data":{"srgb":{"r":0.0,"g":1.0,"b":0.0},"original":"#00ff00"}}]},
           {"id":"c","name":"C","properties":[
             {"type":"Width","data":{"type":"length","px":80.0}},
             {"type":"Height","data":{"type":"length","px":40.0}},
             {"type":"BackgroundColor","data":{"srgb":{"r":0.0,"g":0.0,"b":1.0},"original":"#0000ff"}}]}
         ]}
        """.utf8))
        // Child A (red) — top of column 0. Probe near its corner, away
        // from the centered block-font label glyphs.
        let a = try pixel(comp, x: 5, y: 5)
        XCTAssertGreaterThan(a.r, 192, "child A must paint red at column 0's top")
        XCTAssertLessThan(a.g, 64, "the column-0 top probe must be red, not white/green")
        // Child B (green) — top of column 1 at x = 140 + 20 = 160. The
        // pre-lane vertical stack left this pixel canvas-white.
        let b = try pixel(comp, x: 165, y: 5)
        XCTAssertGreaterThan(b.g, 192, "child B must distribute into column 1 (greedy rule)")
        XCTAssertLessThan(b.r, 64, "the column-1 probe must be green, not white")
        // Child C (blue) — back in column 0, flush under A (tie-break to
        // the first minimal column, y = 40).
        let c = try pixel(comp, x: 5, y: 45)
        XCTAssertGreaterThan(c.b, 192, "child C must stack under A in column 0")
        XCTAssertLessThan(c.r, 64, "the y=45 probe must be blue, not white/red")
        // Column 1's second row is EMPTY (only 3 children) — canvas white
        // proves nothing else was stacked or stretched there.
        let empty = try pixel(comp, x: 165, y: 45)
        XCTAssertGreaterThan(empty.r, 192, "column 1 must hold only child B (white below)")
        XCTAssertGreaterThan(empty.g, 192, "column 1 must hold only child B (white below)")
        XCTAssertGreaterThan(empty.b, 192, "column 1 must hold only child B (white below)")
    }
}

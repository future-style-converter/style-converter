//
//  CollapsedBorderConflictTests.swift
//  Wave-34 lane T (T2) — pins for CSS 2.1 §17.6.2.1 border conflict
//  resolution. Byte-parallel with `runtimes/compose/src/test/java/com/
//  styleconverter/runtime/table/CollapsedBorderConflictTest.kt`: same
//  cases, same order, same expectations.
//
//  The sixteen "origin precedence" cases below are not invented — they are
//  the sixteen floated tables of CSS2/borders/border-conflict-style-107,
//  read straight off `tools/wpt/css/CSS2/borders/border-conflict-style-107
//  .html`, where every conflict is 25px `solid` on both sides and the two
//  declarations "differ only in color" (green wins, red loses). That test
//  scores web-ref 0.782 / android-ref 0.2825 / ios-ref 0.2881 in the frozen
//  wave33-final gate; this file pins the half of the gap that is a pure
//  decision, and CollapsedBorderConflict's header records — honestly — the
//  halves that are not.
//

import XCTest
@testable import StyleConverterRuntime

final class CollapsedBorderConflictTests: XCTestCase {

    private typealias Conflict = CollapsedBorderConflict

    /// A 25px solid declaration from one origin — the test's own shape.
    private func solid25(_ origin: Conflict.Origin, order: Int = 0) -> Conflict.Edge {
        Conflict.Edge(style: .solid, widthPx: 25, origin: origin, order: order)
    }

    // MARK: - Rule 4: the sixteen origin-precedence cases of the WPT test

    func testTheSixteenBorderConflictStyle107TablesAllResolveToTheWinner() {
        // (loser, winner) exactly as the HTML enumerates them; index 1 is
        // always the `.winner` element, so every expectation is `1`.
        let cases: [(Conflict.Origin, Conflict.Origin)] = [
            (.row, .cell),          // 1  cell wins over row
            (.rowGroup, .cell),     // 2  cell wins over row group
            (.column, .cell),       // 3  cell wins over column
            (.columnGroup, .cell),  // 4  cell wins over column group
            (.table, .cell),        // 5  cell wins over table
            (.rowGroup, .row),      // 6  row wins over row group
            (.column, .row),        // 7  row wins over column
            (.columnGroup, .row),   // 8  row wins over column group
            (.table, .row),         // 9  row wins over table
            (.column, .rowGroup),   // 10 row group wins over column
            (.columnGroup, .rowGroup), // 11 row group wins over column group
            (.table, .rowGroup),    // 12 row group wins over table
            (.columnGroup, .column), // 13 column wins over column group
            (.table, .column),      // 14 column wins over table
            (.table, .columnGroup), // 15 column group wins over table
        ]
        for (i, c) in cases.enumerated() {
            XCTAssertEqual(Conflict.winner([solid25(c.0), solid25(c.1)]), 1,
                           "table \(i + 1): \(c.1) must beat \(c.0)")
        }
        // 16 "Table wins when alone" — a lone declaration wins by default.
        XCTAssertEqual(Conflict.winner([solid25(.table)]), 0)
    }

    func testOriginPrecedenceIsATotalOrderStrongestFirst() {
        let ranked: [Conflict.Origin] = [.cell, .row, .rowGroup, .column, .columnGroup, .table]
        // Strictly increasing rank, and the cell is rank 0.
        XCTAssertEqual(Conflict.originRank(ranked[0]), 0)
        for i in 1..<ranked.count {
            XCTAssertEqual(Conflict.originRank(ranked[i]),
                           Conflict.originRank(ranked[i - 1]) + 1,
                           "\(ranked[i]) must rank just below \(ranked[i - 1])")
        }
        // Handed ALL SIX at once — the resolver still picks the cell.
        XCTAssertEqual(Conflict.winner(ranked.map { solid25($0) }), 0)
    }

    // MARK: - Rule 1: hidden

    func testOneHiddenDeclarationSuppressesTheWholeEdge() {
        // §17.6.2.1 rule 1 — "any border with this value suppresses all
        // borders at this location". Even against a wider, stronger, more
        // senior neighbour.
        XCTAssertNil(Conflict.winner([
            Conflict.Edge(style: .double, widthPx: 99, origin: .cell),
            Conflict.Edge(style: .hidden, widthPx: 1, origin: .table),
        ]))
    }

    // MARK: - Rule 2: none

    func testAnAllNoneEdgePaintsNothing() {
        XCTAssertNil(Conflict.winner([
            Conflict.Edge(style: .none, widthPx: 25, origin: .cell),
            Conflict.Edge(style: .none, widthPx: 25, origin: .table),
        ]))
        // …and an empty edge list likewise.
        XCTAssertNil(Conflict.winner([]))
    }

    func testNoneLosesToAnyRealBorderHoweverJunior() {
        // The cell's `none` must NOT beat the table's solid, even though
        // the cell outranks the table — rule 2 comes first.
        XCTAssertEqual(Conflict.winner([
            Conflict.Edge(style: .none, widthPx: 25, origin: .cell),
            solid25(.table),
        ]), 1)
    }

    func testAZeroWidthDeclarationCarriesNoBorderToWinWith() {
        // §8.5.3 gives `none` a computed width of 0; the converse case — a
        // named style at width 0 — has nothing to paint either.
        XCTAssertEqual(Conflict.winner([
            Conflict.Edge(style: .solid, widthPx: 0, origin: .cell),
            solid25(.table),
        ]), 1)
    }

    // MARK: - Rule 3: width first, then style rank

    func testWiderWinsOverMoreSenior() {
        // "narrow borders are discarded in favor of wider ones" — width is
        // compared BEFORE origin, so the table's 30px beats the cell's 25.
        XCTAssertEqual(Conflict.winner([
            solid25(.cell),
            Conflict.Edge(style: .solid, widthPx: 30, origin: .table),
        ]), 1)
    }

    func testStyleRankIsDoubleSolidDashedDottedRidgeOutsetGrooveInset() {
        let order: [Conflict.Style] = [.double, .solid, .dashed, .dotted,
                                       .ridge, .outset, .groove, .inset]
        for i in 1..<order.count {
            XCTAssertEqual(Conflict.styleRank(order[i]),
                           Conflict.styleRank(order[i - 1]) + 1,
                           "\(order[i]) must rank just below \(order[i - 1])")
        }
        // Equal width, equal origin: the better style wins regardless of
        // the order they arrive in.
        for i in 1..<order.count {
            let edges = [
                Conflict.Edge(style: order[i], widthPx: 25, origin: .cell),
                Conflict.Edge(style: order[i - 1], widthPx: 25, origin: .cell),
            ]
            XCTAssertEqual(Conflict.winner(edges), 1,
                           "\(order[i - 1]) beats \(order[i])")
        }
    }

    func testStyleRankIsOnlyConsultedAfterWidth() {
        // A 25px `inset` (the weakest style) beats a 5px `double` (the
        // strongest) — rule 3 puts width first.
        XCTAssertEqual(Conflict.winner([
            Conflict.Edge(style: .double, widthPx: 5, origin: .cell),
            Conflict.Edge(style: .inset, widthPx: 25, origin: .table),
        ]), 1)
    }

    // MARK: - Rule 4's tail: document order

    func testSameOriginTiesBreakToTheEarlierDeclaration() {
        // "the one further to the left … and further to the top wins" —
        // supplied to the resolver as document order.
        XCTAssertEqual(Conflict.winner([
            solid25(.cell, order: 3),
            solid25(.cell, order: 1),
        ]), 1)
    }

    func testATotalTieIsResolvedDeterministicallyToTheFirst() {
        // Not a spec rule — a determinism guarantee, so the two natives
        // cannot paint different colours from identical inputs.
        XCTAssertEqual(Conflict.winner([solid25(.cell), solid25(.cell)]), 0)
    }

    // MARK: - Wire decoding

    func testBorderStyleKeywordsDecodeFromTheUppercasedWire() {
        XCTAssertEqual(Conflict.styleOf("SOLID"), .solid)
        XCTAssertEqual(Conflict.styleOf("hidden"), .hidden)
        XCTAssertEqual(Conflict.styleOf("Double"), .double)
        // An unnameable keyword cannot win, so it decodes to `none` rather
        // than trapping — the no-silent-fallthrough rule points the other
        // way here: a trap would take down an unrelated capture.
        XCTAssertEqual(Conflict.styleOf("WAVY"), .none)
        XCTAssertEqual(Conflict.styleOf(nil), .none)
    }

    func testSourceTagsMapToRule4Origins() {
        XCTAssertEqual(Conflict.originOf(sourceTag: "td"), .cell)
        XCTAssertEqual(Conflict.originOf(sourceTag: "TH"), .cell)
        XCTAssertEqual(Conflict.originOf(sourceTag: "tr"), .row)
        XCTAssertEqual(Conflict.originOf(sourceTag: "tbody"), .rowGroup)
        XCTAssertEqual(Conflict.originOf(sourceTag: "thead"), .rowGroup)
        XCTAssertEqual(Conflict.originOf(sourceTag: "tfoot"), .rowGroup)
        XCTAssertEqual(Conflict.originOf(sourceTag: "col"), .column)
        XCTAssertEqual(Conflict.originOf(sourceTag: "colgroup"), .columnGroup)
        XCTAssertEqual(Conflict.originOf(sourceTag: "table"), .table)
        // Not a table-internal element.
        XCTAssertNil(Conflict.originOf(sourceTag: "div"))
        XCTAssertNil(Conflict.originOf(sourceTag: nil))
    }

    // MARK: - Collapsed geometry (§17.6.2)

    func testACollapsedBorderIsCenteredOnTheGridLine() {
        let halves = Conflict.centeredHalves(25)
        XCTAssertEqual(halves.inner, 12.5)
        XCTAssertEqual(halves.outer, 12.5)
        // border-conflict-style-107's geometry, end to end: an empty cell
        // with a 25px collapsed border is 25 wide (0 + 12.5 + 12.5), its
        // table is 50 (25 + 12.5 + 12.5), and four floated tables make the
        // reference's 200px square. This is the number the natives' 390×1404
        // canvas is missing.
        let cellBox = 0 + halves.inner * 2
        let tableBox = cellBox + halves.outer * 2
        XCTAssertEqual(cellBox, 25)
        XCTAssertEqual(tableBox, 50)
        XCTAssertEqual(tableBox * 4, 200)
    }
}

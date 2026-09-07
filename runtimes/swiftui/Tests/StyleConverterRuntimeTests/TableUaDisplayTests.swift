//
//  TableUaDisplayTests.swift
//  Wave 38 (lane N2) — pins for the HTML UA DISPLAY channel and the
//  CSS 2.1 §17.5.2 shrink-to-fit predicate. Twin:
//  `runtimes/compose/src/test/java/com/styleconverter/runtime/table/
//  TableUaDisplayTest.kt` — same cases, same order, same names.
//
//  ## What this file protects
//  `TableBoxTree.roleOf` reads the wire's `Display` and nothing else, and
//  the css-tables corpus is HTML markup that declares none: the display
//  its `<table>`/`<tr>`/`<td>` boxes have comes from the HTML Standard's
//  rendering section (§15.3.8 Tables), which the converter does not ship.
//  MEASURED on the frozen wave37-final gate
//  (`tools/titan/runs/wave37-final/sections/css-tables`): 122 of the
//  section's 171 table-internal boxes carry a table `meta.sourceTag` and
//  NO table `Display`, so every one of them classified as an ordinary
//  block. `border-collapse-empty-cell` — a 2×2 grid of 50×50 bordered
//  cells whose reference is a 2×2 square — captured on BOTH natives as a
//  1×4 VERTICAL column of cells (ssim 0.9073 against the ref; web 1.0000).
//
//  The two contracts pinned here are the two that decide whether a bare
//  `<table>` becomes a table box at all:
//    1. the UA fallback fires ONLY where the wire declared no `display`
//       (the cascade's own precedence — an author keyword always wins);
//    2. a table box is SHRINK-TO-FIT, never a §10.3.3 block-level fill.
//
//  Everything under test is pure over the IR, so it pins without a render
//  surface — the discipline TableSeparatedTracksTests already follows.
//

import XCTest
@testable import StyleConverterRuntime

final class TableUaDisplayTests: XCTestCase {

    // MARK: - Wire helpers

    /// A keyword-valued declaration (`"TABLE"`), the bare-string wire form.
    private func kw(_ type: String, _ keyword: String) -> IRProperty {
        IRProperty(type: type, data: .string(keyword))
    }

    // MARK: - The UA tag → role table (HTML §15.3.8)

    func testEveryTableInternalHtmlTagGetsItsUaSheetRole() {
        // Straight from the HTML Standard's rendering section: these are
        // the six declarations the converter does not ship.
        XCTAssertEqual(TableBoxTree.uaRoleOf("table"), .table)
        XCTAssertEqual(TableBoxTree.uaRoleOf("tr"), .row)
        XCTAssertEqual(TableBoxTree.uaRoleOf("td"), .cell)
        XCTAssertEqual(TableBoxTree.uaRoleOf("th"), .cell)
        XCTAssertEqual(TableBoxTree.uaRoleOf("caption"), .caption)
        // §2.1's three group boxes share one role, as in the declared table.
        XCTAssertEqual(TableBoxTree.uaRoleOf("tbody"), .rowGroup)
        XCTAssertEqual(TableBoxTree.uaRoleOf("thead"), .rowGroup)
        XCTAssertEqual(TableBoxTree.uaRoleOf("tfoot"), .rowGroup)
    }

    func testTagMatchingIsCaseInsensitiveAndNonTableTagsStayNone() {
        // The wire lowercases `sourceTag`, but an uppercase tag must not
        // silently fall out of the table. (Retro sweep P2b, A6#11: the
        // second reader this comment used to cite for the same tolerance,
        // CollapsedBorderConflict.originOf, is deleted — it had no
        // production caller on either native.)
        XCTAssertEqual(TableBoxTree.uaRoleOf("TABLE"), .table)
        XCTAssertEqual(TableBoxTree.uaRoleOf("Td"), .cell)
        // Everything else is not a table-internal box, including the two
        // column tags — css-tables-3 §2.1 gives them no cell boxes.
        for t in ["div", "p", "span", "col", "colgroup", ""] {
            XCTAssertEqual(TableBoxTree.uaRoleOf(t), .none, "tag \(t)")
        }
        XCTAssertEqual(TableBoxTree.uaRoleOf(nil), .none)
    }

    // MARK: - Precedence: a declared display always wins

    func testADeclaredDisplayBeatsTheUaSheetInBothDirections() {
        // The UA origin is the lowest-priority one in the cascade, so it
        // may only FILL A GAP. Both directions matter: a `<table>` the
        // author blocked must not become a table box, and a `<div>` the
        // author tabled must stay one.
        XCTAssertEqual(
            TableBoxTree.roleOf([kw("Display", "BLOCK")], sourceTag: "table"), .none)
        XCTAssertEqual(
            TableBoxTree.roleOf([kw("Display", "TABLE")], sourceTag: "div"), .table)
        // …and a declared table keyword on a table tag is unchanged.
        XCTAssertEqual(
            TableBoxTree.roleOf([kw("Display", "TABLE_ROW")], sourceTag: "tr"), .row)
    }

    func testANilSourceTagReproducesTheDeclaredOnlyClassification() {
        // The frozen contract: passing no tag must answer exactly what the
        // one-argument roleOf answers, for every wire shape.
        let shapes: [[IRProperty]] = [
            [],
            [kw("Display", "BLOCK")],
            [kw("Display", "TABLE")],
            [kw("Display", "TABLE_ROW")],
            [kw("Display", "TABLE_CELL")],
            [kw("Display", "FLEX")],
        ]
        for props in shapes {
            XCTAssertEqual(TableBoxTree.roleOf(props, sourceTag: nil),
                           TableBoxTree.roleOf(props),
                           "shape \(props.map(\.type))")
        }
    }

    func testTheOptInChannelClassifiesABareHtmlTable() {
        // The css-tables shape: no Display anywhere, only the tags.
        XCTAssertEqual(TableBoxTree.roleOf([], sourceTag: "table"), .table)
        XCTAssertEqual(TableBoxTree.roleOf([], sourceTag: "tr"), .row)
        XCTAssertEqual(TableBoxTree.roleOf([], sourceTag: "td"), .cell)
        // Without the tag the same three wires are ordinary blocks — which
        // is precisely the frozen wave37-final behaviour this lane replaces.
        XCTAssertEqual(TableBoxTree.roleOf([], sourceTag: nil), .none)
    }

    // MARK: - §17.5.2 shrink-to-fit

    func testOnlyTheTableBoxItselfResistsTheBlockLevelFill() {
        // CSS 2.1 §17.5.2: a table's used width is the table layout
        // algorithm's max(min-content, min(max-content, available)) — it
        // hugs its columns. Rows and cells are sized BY the table, and an
        // ordinary block still fills (§10.3.3), so exactly one role
        // answers true.
        XCTAssertTrue(TableBoxTree.shrinkToFitBox(.table))
        for r: TableBoxTree.Role in [.row, .rowGroup, .cell, .caption, .none] {
            XCTAssertFalse(TableBoxTree.shrinkToFitBox(r), "role \(r)")
        }
    }

    // MARK: - The column tags

    func testGeneratesNoBoxesNamesExactlyTheTwoColumnTags() {
        XCTAssertTrue(TableBoxTree.generatesNoBoxes("col"))
        XCTAssertTrue(TableBoxTree.generatesNoBoxes("COLGROUP"))
        for t in ["table", "tr", "td", "tbody", "caption", "div"] {
            XCTAssertFalse(TableBoxTree.generatesNoBoxes(t), "tag \(t)")
        }
        XCTAssertFalse(TableBoxTree.generatesNoBoxes(nil))
    }
}

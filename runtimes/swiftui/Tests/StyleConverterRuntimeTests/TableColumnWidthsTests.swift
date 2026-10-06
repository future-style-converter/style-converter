//
//  TableColumnWidthsTests.swift
//  Wave 52 lane L8 (vertical-wedges, M-E) — Catalyst pins for the column-
//  width harvest (TableBoxTree.columnChains / columnWidthsPx /
//  cellColumnWidths): css-tables-3 §2.1 / §3.2, a UA-only `<col>` still
//  carries its column's specified width. Kotlin twin: TableColumnWidthsTest.kt.
//
//  Verbatim IR: tools/titan/runs/wave51-fix/sections/css-writing-modes/
//  per-test-ir/wpt__css-writing-modes__ch-units-vrl-003.json — the table
//  `…__2-456` with its col `…__2__0-457` (VERTICAL_RL + UPRIGHT, Width 5ch)
//  and tbody → tr → td `…__2__1__0__0-460`, `slot` folded into `children`.
//  Ref: the green td 120 wide (5 × 24, the upright '0' at 20 px Inter).
//
//  MUTATION EXECUTED (2026-10-05, isolated HEAD export, restored byte-exact,
//  sha-256 verified): `columnWidthsPx` mapping every chain to nil →
//  `testUprightColGivesItsCellTheColumnWidth` fails (nil ≠ 120).
//

import CoreText
import SwiftUI
import UIKit
import XCTest
@testable import StyleConverterRuntime

final class TableColumnWidthsTests: XCTestCase {

    /// The harness's Inter Regular (the face the capture measures with).
    private var interURL: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("apps/ios-harness/StyleConverterTest/Resources/Inter-Regular.otf")
    }

    override func setUpWithError() throws {
        // Process-scope registration, exactly what the harness bundle does.
        var error: Unmanaged<CFError>?
        if !CTFontManagerRegisterFontsForURL(interURL as CFURL, .process, &error),
           UIFont(name: "Inter", size: 20) == nil {
            XCTFail("could not register \(interURL.path)")
        }
    }

    override func tearDown() {
        // Leave the process without Inter, as the suite found it.
        var error: Unmanaged<CFError>?
        _ = CTFontManagerUnregisterFontsForURL(interURL as CFURL, .process, &error)
        super.tearDown()
    }

    /// The verbatim ch-units-vrl-003 table (see header).
    private func table003() throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data(#"""
        {"id":"wpt__css-writing-modes__ch-units-vrl-003__2-456","name":"wpt__css-writing-modes__ch-units-vrl-003__2","properties":[{"type":"FontSize","data":{"px":20,"original":{"type":"length","px":20}}},{"type":"BorderCollapse","data":"COLLAPSE"},{"type":"BorderTopStyle","data":"NONE"},{"type":"BorderRightStyle","data":"NONE"},{"type":"BorderBottomStyle","data":"NONE"},{"type":"BorderLeftStyle","data":"NONE"}],"meta":{"sourceTag":"table","role":"ws-after"},"children":[{"id":"ch-units-vrl-003__2__0-457","name":"ch-units-vrl-003__2__0","properties":[{"type":"WritingMode","data":"VERTICAL_RL"},{"type":"TextOrientation","data":"UPRIGHT"},{"type":"Width","data":{"type":"length","original":{"v":5,"u":"CH"}}}],"meta":{"sourceTag":"col"}},{"id":"ch-units-vrl-003__2__1-458","name":"ch-units-vrl-003__2__1","properties":[],"meta":{"sourceTag":"tbody"},"children":[{"id":"ch-units-vrl-003__2__1__0-459","name":"ch-units-vrl-003__2__1__0","properties":[],"meta":{"sourceTag":"tr"},"children":[{"id":"ch-units-vrl-003__2__1__0__0-460","name":"ch-units-vrl-003__2__1__0__0","properties":[{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"Height","data":{"type":"length","original":{"v":5,"u":"CH"}}},{"type":"WritingMode","data":"VERTICAL_RL"},{"type":"TextOrientation","data":"UPRIGHT"}],"text":" ","meta":{"sourceTag":"td"}}]}]}]}
        """#.utf8))
    }

    func testUprightColGivesItsCellTheColumnWidth() throws {
        let table = try table003()
        // The table's inheritable set = its own font-size (20px).
        let inherited = InheritedText.inheritable(from: table.properties)
        // One column chain: the bare <col>.
        XCTAssertEqual(TableBoxTree.columnChains(table.children).count, 1)
        // 5ch along the col's own upright inline axis = 5 × 24 = 120.
        XCTAssertEqual(TableBoxTree.columnWidthsPx(table.children, inherited: inherited), [120])
        // The renderer's lookup: the td (cell 0 of the only row) → 120.
        XCTAssertEqual(TableBoxTree.cellColumnWidths(table: table, inherited: inherited),
                       ["ch-units-vrl-003__2__1__0__0-460": 120])
    }

    func testDeclaredTableColumnBoxesHarvestNothing() throws {
        // css-tables/border-collapse-dynamic-col-001's shape: DECLARED
        // Display TABLE_COLUMN_GROUP / TABLE_COLUMN — no chain, no width.
        let table = try JSONDecoder().decode(IRComponent.self, from: Data(#"""
        {"id":"t","name":"t","properties":[{"type":"Display","data":"TABLE"}],"meta":{"sourceTag":"table"},
         "children":[{"id":"g","name":"g","meta":{"sourceTag":"colgroup"},"properties":[{"type":"Width","data":{"type":"length","px":84}},{"type":"Display","data":"TABLE_COLUMN_GROUP"}],
           "children":[{"id":"c","name":"c","meta":{"sourceTag":"col"},"properties":[{"type":"Width","data":{"type":"length","px":21}},{"type":"Display","data":"TABLE_COLUMN"}]}]}]}
        """#.utf8))
        XCTAssertTrue(TableBoxTree.columnChains(table.children).isEmpty)
        XCTAssertTrue(TableBoxTree.cellColumnWidths(table: table, inherited: []).isEmpty)
    }
}

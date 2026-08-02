//
//  GapDecorationsWireTests.swift
//  Wave 24, lane GAPS-I — the WIRE CONTRACT pin for the gap-decorations
//  family. Every shape below is decoded from JSON so the test sees the
//  converter's bytes, not a hand-built Swift value.
//
//  The ColumnRule* shapes are LIVE: copied out of
//  tools/titan/runs/wave23-final/sections/css-gaps/per-test-ir/
//  wpt__css-gaps__flex__flex-gap-decorations-001.json (component __1).
//  The RowRule*/Break/Inset/Overlap shapes are the wave-24 contract the
//  converter lane implements — byte-identical to their column twins.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class GapDecorationsWireTests: XCTestCase {

    /// Decode a `[IRProperty]` from a wire fragment.
    private func props(_ json: String) throws -> [IRProperty] {
        try JSONDecoder().decode([IRProperty].self, from: Data(json.utf8))
    }

    /// The live css-gaps IR shapes plus their wave-24 row twins.
    func testLiveWireShapesDecodeIntoBothFamilies() throws {
        let cfg = try XCTUnwrap(GapDecorationsExtractor.extract(from: props("""
        [{"type":"ColumnRuleColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},
                                           "original":"green"}},
         {"type":"ColumnRuleStyle","data":"SOLID"},
         {"type":"ColumnRuleWidth","data":{"type":"length","px":10}},
         {"type":"RowRuleColor","data":{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}},
         {"type":"RowRuleStyle","data":"DOUBLE"},
         {"type":"RowRuleWidth","data":{"type":"length","px":10}}]
        """)))
        // Widths arrive as typed length objects — extractKeyword would
        // have answered "length" here, which is why the extractor routes
        // widths/insets through extractPx (see its file header).
        XCTAssertEqual(cfg.column.widthPx, 10)
        XCTAssertEqual(cfg.row.widthPx, 10)
        XCTAssertEqual(cfg.column.style, .solid)
        XCTAssertEqual(cfg.row.style, .double)
        XCTAssertEqual(cfg.column.color, Color(.sRGB, red: 0, green: 0.5019607843137255,
                                               blue: 0, opacity: 1))
        // Both families paint ⇒ the container is decorated.
        XCTAssertTrue(cfg.isActive)
        // Un-declared knobs stay at their CSS initials. `*-rule-break`'s
        // initial is `normal` — measured, not assumed: Chrome reports
        // getComputedStyle(el).columnRuleBreak === "normal" on a bare
        // element and rejects `spanning-item` entirely.
        XCTAssertEqual(cfg.column.breakMode, .normal)
        XCTAssertEqual(cfg.row.breakMode, .normal)
        XCTAssertEqual(cfg.overlap, .rowOverColumn)
        XCTAssertEqual(cfg.column.insetPx, 0)
    }

    /// The three new keyword/length knobs, on the contract spellings.
    func testBreakInsetAndOverlapKeywords() throws {
        let cfg = try XCTUnwrap(GapDecorationsExtractor.extract(from: props("""
        [{"type":"ColumnRuleStyle","data":"SOLID"},
         {"type":"ColumnRuleWidth","data":{"type":"length","px":2}},
         {"type":"ColumnRuleBreak","data":"INTERSECTION"},
         {"type":"ColumnRuleInset","data":{"type":"length","px":-2}},
         {"type":"RowRuleBreak","data":"NORMAL"},
         {"type":"RowRuleInset","data":{"type":"length","px":4}},
         {"type":"RuleOverlap","data":"COLUMN_OVER_ROW"}]
        """)))
        XCTAssertEqual(cfg.column.breakMode, .intersection)
        XCTAssertEqual(cfg.row.breakMode, .normal)
        // NEGATIVE insets are legal and must survive unclamped — this is
        // ref 011's whole mechanism.
        XCTAssertEqual(cfg.column.insetPx, -2)
        XCTAssertEqual(cfg.row.insetPx, 4)
        XCTAssertEqual(cfg.overlap, .columnOverRow)
    }

    /// Unknown keywords keep the CSS initial rather than inventing a
    /// value, and a negative rule WIDTH clamps to zero (<length [0,∞]>).
    func testUnknownKeywordsAndOutOfRangeWidthAreSafe() throws {
        let cfg = try XCTUnwrap(GapDecorationsExtractor.extract(from: props("""
        [{"type":"ColumnRuleBreak","data":"TOTALLY_NEW_KEYWORD"},
         {"type":"RuleOverlap","data":"SIDEWAYS"},
         {"type":"ColumnRuleWidth","data":{"type":"length","px":-5}},
         {"type":"ColumnRuleStyle","data":"SOLID"}]
        """)))
        XCTAssertEqual(cfg.column.breakMode, .normal)
        XCTAssertEqual(cfg.overlap, .rowOverColumn)
        XCTAssertEqual(cfg.column.widthPx, 0)
        // Clamped width ⇒ no ink ⇒ the painter never runs.
        XCTAssertFalse(cfg.isActive)
    }

    /// A component with none of the family yields NO config at all —
    /// the nil that keeps the flex draw hook inert for the whole
    /// committed corpus.
    func testUnrelatedPropertiesYieldNoConfig() throws {
        let unrelated = try props("""
        [{"type":"Display","data":"FLEX"},
         {"type":"ColumnGap","data":{"type":"length","px":10}},
         {"type":"RowGap","data":{"type":"length","px":10}}]
        """)
        XCTAssertNil(GapDecorationsExtractor.extract(from: unrelated))
    }

    /// Every new name is registered, and the three shared column-rule
    /// longhands stay owned by ColumnsProperty (no double claim).
    func testRegistryClaimsTheNewNamesOnly() {
        for name in ["RowRuleColor", "RowRuleStyle", "RowRuleWidth",
                     "ColumnRuleBreak", "RowRuleBreak",
                     "ColumnRuleInset", "RowRuleInset", "RuleOverlap"] {
            XCTAssertTrue(PropertyRegistry.migrated.contains(name),
                          "\(name) is not registered")
        }
        XCTAssertTrue(GapDecorationsProperty.set.isDisjoint(with: ColumnsProperty.set))
    }
}

//
//  GapDecorationGrammarTests.swift
//  Wave 24, lane GAPS-I — regression pins for the three `<gap-rule-*>`
//  grammar facts the lane's first cut got wrong. Every number here was
//  MEASURED, not read off a spec draft:
//
//    • Chrome, on a bare element:
//        getComputedStyle(el).columnRuleBreak === "normal"
//        getComputedStyle(el).rowRuleWidth    === "3px"
//        CSS.supports("column-rule-break", "spanning-item") === false
//        CSS.supports("column-rule-break", "none")          === true
//      (probe script: tools/visual/_skeptic/probe-break.mjs, run under
//      the same puppeteer this campaign renders its refs with)
//    • wpt/css/css-gaps/parsing/rule-break-valid.html asserts exactly
//      none | normal | intersection.
//    • The converter really emits the `<line-width>` KEYWORD shape:
//      `row-rule-width: thin` → {"type":"keyword","value":"THIN"}
//      (RowRuleWidthProperty.RuleWidth.Keyword), which extractPx cannot
//      read because the object has no `px` member.
//

import XCTest
import SwiftUI
@testable import StyleConverterRuntime

final class GapDecorationGrammarTests: XCTestCase {

    /// Ref-003's 8-item / 3-line layout in content-box space.
    private let frames: [CGRect] = [
        CGRect(x: 0, y: 0, width: 50, height: 50),
        CGRect(x: 60, y: 0, width: 50, height: 50),
        CGRect(x: 120, y: 0, width: 50, height: 50),
        CGRect(x: 0, y: 60, width: 100, height: 50),
        CGRect(x: 110, y: 60, width: 50, height: 50),
        CGRect(x: 0, y: 120, width: 50, height: 50),
        CGRect(x: 60, y: 120, width: 50, height: 50),
        CGRect(x: 120, y: 120, width: 50, height: 50),
    ]
    private let size = CGSize(width: 170, height: 170)

    private func cfg(_ mode: GapRuleBreak, width: CGFloat? = 10) -> GapDecorationsConfig {
        GapDecorationsConfig(
            column: GapRuleSpec(color: .red, style: .solid, widthPx: width,
                                breakMode: mode),
            row: GapRuleSpec(color: .blue, style: .solid, widthPx: width,
                             breakMode: mode),
            overlap: .rowOverColumn, touched: true)
    }

    // MARK: - `rule-break: normal` is the INITIAL, not a "never break" mode

    /// The wire token "NORMAL" is what the converter emits for the CSS
    /// INITIAL value, so it must reproduce the ref-003 geometry the lane
    /// pinned. Regression for the first cut, which routed NORMAL to a
    /// collapse-to-container-extent branch and produced three 170-tall
    /// column rules where the ref has five 50-tall ones.
    func testNormalReproducesTheRef003ColumnGeometry() {
        let cols = GapDecorationSegments.build(
            items: frames, contentSize: size, mainHorizontal: true,
            config: cfg(.normal)
        ).filter { !$0.isRowRule }.map(\.rect)
        XCTAssertEqual(cols, [
            CGRect(x: 50, y: 0, width: 10, height: 50),
            CGRect(x: 110, y: 0, width: 10, height: 50),
            CGRect(x: 100, y: 60, width: 10, height: 50),
            CGRect(x: 50, y: 120, width: 10, height: 50),
            CGRect(x: 110, y: 120, width: 10, height: 50),
        ])
    }

    /// `spanning-item` is not a CSS keyword any more; the enum case only
    /// exists for wire tolerance and MUST be behaviourally identical to
    /// `.normal` (css-flexbox-1 §5.2 — no flex item spans a line, so
    /// "break at spanning items" has nothing to break at). The Compose
    /// twin documents the same equivalence.
    func testSpanningItemIsIndistinguishableFromNormal() {
        let a = GapDecorationSegments.build(items: frames, contentSize: size,
                                            mainHorizontal: true, config: cfg(.normal))
        let b = GapDecorationSegments.build(items: frames, contentSize: size,
                                            mainHorizontal: true, config: cfg(.spanningItem))
        XCTAssertEqual(a, b)
    }

    /// …and `intersection` still differs, so the equivalence above is not
    /// an accident of a dead code path.
    func testIntersectionStillDiffersFromNormal() {
        let a = GapDecorationSegments.build(items: frames, contentSize: size,
                                            mainHorizontal: true, config: cfg(.normal))
        let c = GapDecorationSegments.build(items: frames, contentSize: size,
                                            mainHorizontal: true, config: cfg(.intersection))
        XCTAssertNotEqual(a, c)
    }

    // MARK: - `<line-width>` keywords and the `medium` initial

    /// The keyword ladder Chromium uses (1 / 3 / 5 px), which the wire
    /// really carries and which used to make the family silently dark.
    func testLineWidthKeywordsResolveToChromiumsUsedValues() throws {
        for (kw, px) in [("THIN", CGFloat(1)), ("MEDIUM", 3), ("THICK", 5)] {
            let c = try XCTUnwrap(GapDecorationsExtractor.extract(from: [
                IRProperty(type: "RowRuleStyle", data: .string("SOLID")),
                IRProperty(type: "RowRuleWidth", data: .object([
                    "type": .string("keyword"), "value": .string(kw)])),
            ]))
            XCTAssertEqual(c.row.widthPx, px, "\(kw)")
            XCTAssertTrue(c.row.paints, "\(kw) must paint")
        }
    }

    /// A styled rule with NO declared width is a 3px rule (`medium` is
    /// the `*-rule-width` initial — Chrome reports rowRuleWidth "3px" on
    /// a bare element), not an absent one. Pinned at the geometry level
    /// so the initial reaches the painted rect, not just the config.
    func testOmittedWidthPaintsAtMedium() throws {
        let c = try XCTUnwrap(GapDecorationsExtractor.extract(from: [
            IRProperty(type: "ColumnRuleStyle", data: .string("SOLID")),
            IRProperty(type: "ColumnRuleColor", data: .object([
                "srgb": .object(["r": .double(1), "g": .double(0), "b": .double(0)])])),
        ]))
        XCTAssertNil(c.column.widthPx, "nothing was declared")
        XCTAssertTrue(c.column.paints)
        let cols = GapDecorationSegments.build(
            items: frames, contentSize: size, mainHorizontal: true, config: c
        ).filter { !$0.isRowRule }
        // Centred in the 50–60 band at the CSS initial 3px → 53.5…56.5.
        XCTAssertEqual(cols.first?.rect, CGRect(x: 53.5, y: 0, width: 3, height: 50))
    }

    /// An EXPLICIT zero width still paints nothing — the `medium` fallback
    /// must not resurrect `column-rule-width: 0`.
    func testExplicitZeroWidthStaysDark() throws {
        let c = try XCTUnwrap(GapDecorationsExtractor.extract(from: [
            IRProperty(type: "ColumnRuleStyle", data: .string("SOLID")),
            IRProperty(type: "ColumnRuleWidth", data: .object([
                "type": .string("length"), "px": .double(0)])),
        ]))
        XCTAssertFalse(c.column.paints)
        XCTAssertFalse(c.isActive)
    }

    /// `*-rule-style` is what keeps the committed corpus dark: a
    /// container that declares only a colour/width still paints nothing.
    func testStyleRemainsTheDarkStageGate() throws {
        let c = try XCTUnwrap(GapDecorationsExtractor.extract(from: [
            IRProperty(type: "ColumnRuleWidth", data: .object([
                "type": .string("length"), "px": .double(10)])),
            IRProperty(type: "ColumnRuleColor", data: .object([
                "srgb": .object(["r": .double(1), "g": .double(0), "b": .double(0)])])),
        ]))
        XCTAssertFalse(c.isActive)
    }

    // MARK: - non-length insets are reported, not guessed

    /// `column-rule-inset: -100%` converts to
    /// {"original":{"v":-100,"u":"PERCENT"}} — no `px`. It must fall back
    /// to 0 AND leave a breadcrumb (CLAUDE.md: no silent fallthroughs).
    func testPercentageInsetFallsBackToZero() throws {
        let c = try XCTUnwrap(GapDecorationsExtractor.extract(from: [
            IRProperty(type: "ColumnRuleInset", data: .object([
                "original": .object(["v": .double(-100), "u": .string("PERCENT")])])),
        ]))
        XCTAssertEqual(c.column.insetPx, 0)
    }
}

// MARK: - Column direction: the corpus DOES pin it

/// flex-gap-decorations-015 is a `flex-direction: column` test (so are
/// 016, 017, 018, 043, 045, 058 — the flex corpus is 64 tests, not 12),
/// and its ref gives the whole geometry directly:
///   #flexbox  120×170 content, border 2, column-gap 20, row-gap 10,
///             6 items of 50×50, wrap, column direction,
///             column-rule 20px red, row-rule 10px blue,
///             rule-overlap: column-over-row
///   ref divs  .column-gap  left 52  top 2   20×170
///             .row-1/.row-2  two 50×10 blue divs at left 2 / 72,
///                            top 52 and top 112
/// Minus the 2px border that is (50,0,20,170) for the column rule and
/// (0,50,50,10)+(70,50,50,10) / (0,110,…)+(70,110,…) for the row rules.
extension GapDecorationGrammarTests {

    func testRef015ColumnDirectionGeometry() {
        // Two columns of three items; main axis is vertical.
        let frames: [CGRect] = [
            CGRect(x: 0, y: 0, width: 50, height: 50),
            CGRect(x: 0, y: 60, width: 50, height: 50),
            CGRect(x: 0, y: 120, width: 50, height: 50),
            CGRect(x: 70, y: 0, width: 50, height: 50),
            CGRect(x: 70, y: 60, width: 50, height: 50),
            CGRect(x: 70, y: 120, width: 50, height: 50),
        ]
        let cfg = GapDecorationsConfig(
            // In a column container the COLUMN rule is the LINE-gap
            // family (it paints between the two columns).
            column: GapRuleSpec(color: .red, style: .solid, widthPx: 20),
            row: GapRuleSpec(color: .blue, style: .solid, widthPx: 10),
            overlap: .columnOverRow, touched: true)
        let segs = GapDecorationSegments.build(
            items: frames, contentSize: CGSize(width: 120, height: 170),
            mainHorizontal: false, config: cfg)
        // Row rules = item gaps = two 50-wide runs per band, holed over
        // the column gap. EXACTLY the ref's .row-1/.row-2 markup.
        XCTAssertEqual(segs.filter(\.isRowRule).map(\.rect), [
            CGRect(x: 0, y: 50, width: 50, height: 10),
            CGRect(x: 0, y: 110, width: 50, height: 10),
            CGRect(x: 70, y: 50, width: 50, height: 10),
            CGRect(x: 70, y: 110, width: 50, height: 10),
        ])
        // Column rule = line gap = one full-height 20px band at x50–70.
        XCTAssertEqual(segs.filter { !$0.isRowRule }.map(\.rect),
                       [CGRect(x: 50, y: 0, width: 20, height: 170)])
        // rule-overlap: column-over-row ⇒ the column rule paints LAST.
        XCTAssertEqual(segs.last?.isRowRule, false)
    }

    /// REVERSE FLOW IS UNSUPPORTED, and says so. `groupIntoLines` starts
    /// a new line whenever the main cursor moves backwards, so a
    /// `row-reverse` / `column-reverse` container — whose item anchors
    /// arrive in DOM order with DECREASING main positions — degenerates
    /// to one line per item. flex-gap-decorations-016 (column-reverse)
    /// and 056 (wrap-reverse) are therefore out of scope; this test
    /// documents the failure mode instead of letting it look correct.
    func testReverseMainAxisDegeneratesToOneLinePerItem() {
        // row-reverse: DOM order 1,2,3,4 lands right-to-left.
        let reversed = [CGRect(x: 60, y: 0, width: 50, height: 50),
                        CGRect(x: 0, y: 0, width: 50, height: 50)]
        let lines = GapDecorationLines.lines(from: reversed, mainHorizontal: true)
        XCTAssertEqual(lines.count, 2,
                       "known limitation: reverse flow reads as two lines")
        // …and the consequence: the real 50–60 item gap gets NO rule.
        let segs = GapDecorationSegments.build(
            items: reversed, contentSize: CGSize(width: 110, height: 50),
            mainHorizontal: true,
            config: GapDecorationsConfig(
                column: GapRuleSpec(color: .red, style: .solid, widthPx: 10),
                touched: true))
        XCTAssertTrue(segs.isEmpty)
    }

    /// `wrap-reverse` puts later lines ABOVE earlier ones, so the
    /// between-line span is negative and every line-gap rule is dropped.
    /// Also unsupported, also pinned rather than silently wrong.
    func testWrapReverseDropsEveryLineGapRule() {
        // Line 1 at the BOTTOM (y60–110), line 2 above it (y0–50).
        let frames = [CGRect(x: 0, y: 60, width: 50, height: 50),
                      CGRect(x: 60, y: 60, width: 50, height: 50),
                      CGRect(x: 0, y: 0, width: 50, height: 50),
                      CGRect(x: 60, y: 0, width: 50, height: 50)]
        let segs = GapDecorationSegments.build(
            items: frames, contentSize: CGSize(width: 110, height: 110),
            mainHorizontal: true,
            config: GapDecorationsConfig(
                row: GapRuleSpec(color: .blue, style: .solid, widthPx: 10),
                touched: true))
        XCTAssertTrue(segs.isEmpty,
                      "known limitation: wrap-reverse yields no row rules")
    }
}

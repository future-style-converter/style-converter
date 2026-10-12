//
//  FontVariantNumericTabularTests.swift
//  StyleConverterRuntimeTests — wave 55, lane L5 (U3-ios).
//
//  Pins the `font-variant-numeric` numeric-spacing route (css-fonts-4):
//  FontVariantNumericApplier sets `TypographyAggregate.tabularNums` and
//  StyleBuilder mirrors it into `TextConfig.tabularNums`, which the
//  label's face composes as `Font.monospacedDigit()` (that last hop is the
//  renderer seam; its raster pin rides with it in seam-6.patch).
//
//  The verbatim carrier is `counter-suffix__0__4__3` — the root-owned RTL
//  marker run "1" of tools/titan/runs/wave54-final/sections/
//  css-counter-styles/per-test-ir/wpt__css-counter-styles__counter-suffix
//  .json, whose wire carries `{"type":"FontVariantNumeric","data":
//  ["TABULAR_NUMS"]}` (the bake's copy of the UA `::marker` rule,
//  css-lists-3 §3.1.1).
//
//  Mutation EXECUTED by the lane (bracketed in tools/titan/results/
//  wave55-counter-suffix-rows/_note.md): restoring the identity applier
//  (`_ = cfg; _ = agg`) turns the tabular and StyleBuilder pins red.
//

import XCTest
@testable import StyleConverterRuntime

final class FontVariantNumericTabularTests: XCTestCase {

    /// One aggregate folded from a keyword list, the applier's only input.
    private func fold(_ keywords: [String]?) -> TypographyAggregate {
        var agg = TypographyAggregate()
        // nil models "no FontVariantNumeric declared" (extractor → nil).
        let cfg = keywords.map { FontVariantKeywordListConfig(keywords: $0) }
        FontVariantNumericApplier.contribute(cfg, into: &agg)
        return agg
    }

    /// The wire's SHOUTY enum, lower-cased by FontVariantKeywordParse, and
    /// the hyphenated CSS spelling both select tabular digits.
    func testTabularNumsSetsTheFlag() {
        XCTAssertEqual(fold(["tabular_nums"]).tabularNums, true)
        XCTAssertEqual(fold(["tabular-nums"]).tabularNums, true)
        // A multi-keyword value still carries the spacing keyword.
        XCTAssertEqual(fold(["lining_nums", "tabular_nums"]).tabularNums, true)
        // `touched` flips so a tabular-only component reaches TextConfig.
        XCTAssertTrue(fold(["tabular_nums"]).touched)
    }

    /// `proportional-nums` is the explicit opt-out.
    func testProportionalNumsClearsTheFlag() {
        XCTAssertEqual(fold(["proportional_nums"]).tabularNums, false)
    }

    /// Undeclared, `normal` and the other groups leave it unset.
    func testOtherwiseUnset() {
        XCTAssertNil(fold(nil).tabularNums)
        XCTAssertNil(fold(["normal"]).tabularNums)
        XCTAssertNil(fold(["slashed_zero"]).tabularNums)
    }

    /// End to end over the VERBATIM wire property list of the "1" run.
    func testStyleBuilderMirrorsTheVerbatimCarrierIntoTextConfig() throws {
        let json = """
        [{"type":"Position","data":"ABSOLUTE"},{"type":"Left","data":{"px":120.59}},
         {"type":"Top","data":{"px":2}},{"type":"Width","data":{"type":"length","px":10.38}},
         {"type":"Height","data":{"type":"length","px":20}},
         {"type":"LineHeight","data":{"original":{"type":"length","px":20}}},
         {"type":"WhiteSpace","data":"PRE"},{"type":"Direction","data":"LTR"},
         {"type":"TextAlign","data":"LEFT"},
         {"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":{"r":0,"g":0,"b":0}}},
         {"type":"FontFamily","data":["Inter","-apple-system","system-ui","Segoe UI","Roboto","Oxygen","Ubuntu","sans-serif"]},
         {"type":"FontSize","data":{"px":16,"original":{"type":"length","px":16}}},
         {"type":"FontStyle","data":"normal"},{"type":"FontWeight","data":400},
         {"type":"FontVariantNumeric","data":["TABULAR_NUMS"]}]
        """
        let props = try JSONDecoder().decode([IRProperty].self, from: Data(json.utf8))
        // The applier saw the wire keyword through the real extractor.
        XCTAssertEqual(StyleBuilder.build(from: props).typography?.tabularNums, true)
        // …and the label's TextConfig carries it to labelFont.
        XCTAssertTrue(StyleBuilder.build(from: props).text.tabularNums)
        // Without the declaration the label keeps the face default.
        let without = props.filter { $0.type != "FontVariantNumeric" }
        XCTAssertFalse(StyleBuilder.build(from: without).text.tabularNums)
    }
}

//
//  AllResetTests.swift
//  Wave 52 (lane L11, brief colour-not-reaching-text F1) — the ORDER-AWARE
//  `all` reset, GlobalExtractor.applyingAllReset(own:inherited:). Twin of
//  Compose AllResetTest.kt; the documents are VERBATIM wave51-fix per-test IR
//  (tools/titan/runs/wave51-fix/sections/…/per-test-ir/), so each pin speaks
//  for the gate cell it names.
//
//  MUTATION RECORD (executed 2026-10-05 by mutate-swift.sh in
//  tools/titan/results/wave52-all-reset-postload-colour/; GlobalExtractor
//  .swift restored byte-exact after each — sha256 fc36b903… before == after,
//  mutations.log):
//   M1 `return ([], [])` (the pre-wave-52 drop) → 6/7 FAIL (all but
//      `color red then all initial`, which the old drop also satisfies).
//   M2 own[0,i) kept whole → all-prop-001, color-red-then-all, last-all FAIL.
//   M3 §3.1 exemption removed → all-prop-001 FAILS.
//   M4 INITIAL keeps the channel → color-red-then-all, the channel pin and
//      last-all FAIL.
//   M5 `firstIndex` for `lastIndex` → last-all FAILS.
//

import XCTest
@testable import StyleConverterRuntime

final class AllResetTests: XCTestCase {

    // MARK: - helpers

    /// Decode a v2 document (slot list composed exactly as the harness does)
    /// and flatten its tree so lookups by id reach every level.
    private func components(_ json: String) throws -> [IRComponent] {
        let doc = try JSONDecoder().decode(IRDocument.self, from: Data(json.utf8))
        // Pre-order walk over the composed children.
        func walk(_ c: IRComponent) -> [IRComponent] { [c] + (c.children ?? []).flatMap(walk) }
        return doc.components.flatMap(walk)
    }

    /// One component by id (fails the test when absent).
    private func byId(_ json: String, _ id: String) throws -> IRComponent {
        try XCTUnwrap(try components(json).first { $0.id == id }, id)
    }

    /// The parent's published channel — its list filtered to inherited types.
    private func channel(_ parent: IRComponent) -> [IRProperty] {
        InheritedText.inheritable(from: parent.properties)
    }

    /// What mergedProperties folds after the reset (the seam-2 order).
    private func resolved(_ own: [IRProperty], _ inherited: [IRProperty]) -> [IRProperty] {
        let r = GlobalExtractor.applyingAllReset(own: own, inherited: inherited)
        return InheritedText.merge(own: r.own, inherited: r.inherited)
    }

    /// One wire-shaped property from a JSON object literal.
    private func prop(_ json: String) throws -> IRProperty {
        try JSONDecoder().decode(IRProperty.self, from: Data(json.utf8))
    }

    // MARK: - pins

    /// The four all-prop-*-color cells: the span's own green follows `all`
    /// and wins (§6.4); the parent's red never paints.
    func testAllPropColourQuartetResolvesTheOwnGreen() throws {
        let cases = [
            (initialColor, "wpt__css-cascade__all-prop-initial-color__0-014", "all-prop-initial-color__0__0-015"),
            (inheritColor, "wpt__css-cascade__all-prop-inherit-color__0-012", "all-prop-inherit-color__0__0-013"),
            (unsetColor, "wpt__css-cascade__all-prop-unset-color__0-021", "all-prop-unset-color__0__0-022"),
            (revertColor, "wpt__css-cascade__all-prop-revert-color__0-017", "all-prop-revert-color__0__0-018"),
        ]
        for (doc, parentId, spanId) in cases {
            let merged = resolved(try byId(doc, spanId).properties, channel(try byId(doc, parentId)))
            let colors = merged.filter { $0.type == "Color" }
            // Exactly one Color, the span's green (g = 128/255).
            XCTAssertEqual(colors.count, 1, spanId)
            XCTAssertEqual(colors.first?.data["srgb"]?["g"]?.doubleValue ?? -1, 0.50196, accuracy: 1e-4, spanId)
            // The `All` entry never survives.
            XCTAssertFalse(merged.contains { $0.type == "All" }, spanId)
        }
    }

    /// `color: red; all: initial` — the declaration BEFORE `all` is reset,
    /// and INITIAL drops the inherited blue too.
    func testColorRedThenAllInitialLeavesNoColour() throws {
        let red = try prop(#"{"type":"Color","data":{"srgb":{"r":1,"g":0,"b":0}}}"#)
        let blue = try prop(#"{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":1}}}"#)
        let merged = resolved([red, try prop(#"{"type":"All","data":"INITIAL"}"#)], [blue])
        XCTAssertFalse(merged.contains { $0.type == "Color" })
    }

    /// VERBATIM all-prop-001: the §3.1 exemption carrier (PLAN.md L11 P→f risk).
    func testAllProp001KeepsDirectionAndUnicodeBidi() throws {
        let test = try byId(allProp001, "wpt__css-cascade__all-prop-001__1-002")
        let r = GlobalExtractor.applyingAllReset(own: test.properties, inherited: [])
        XCTAssertEqual(r.own.map(\.type), ["Direction", "UnicodeBidi"])
        // The <bdo>: `direction: rtl` FOLLOWS `all` — kept; its channel is
        // what `.test` publishes after ITS reset (inherited types only).
        let bdo = try byId(allProp001, "all-prop-001__1__0-003")
        let rb = GlobalExtractor.applyingAllReset(own: bdo.properties,
                                                  inherited: InheritedText.inheritable(from: r.own))
        XCTAssertEqual(rb.own.map(\.type), ["Direction"])
        // INITIAL still lets the exempt inherited `direction` flow.
        XCTAssertEqual(rb.inherited.map(\.type), ["Direction"])
    }

    /// INHERIT / UNSET / REVERT* keep the channel; INITIAL drops it (§7.3).
    func testInitialDropsTheChannelAndTheOthersKeepIt() throws {
        let red = [try prop(#"{"type":"Color","data":{"srgb":{"r":1,"g":0,"b":0}}}"#)]
        XCTAssertFalse(resolved([try prop(#"{"type":"All","data":"INITIAL"}"#)], red).contains { $0.type == "Color" })
        for kw in ["INHERIT", "UNSET", "REVERT", "REVERT_LAYER", "revert-layer"] {
            let all = try prop("{\"type\":\"All\",\"data\":\"\(kw)\"}")
            XCTAssertTrue(resolved([all], red).contains { $0.type == "Color" }, kw)
        }
    }

    /// The LAST `all` governs (§6.4).
    func testLastAllGoverns() throws {
        let ch = [try prop(#"{"type":"Color","data":{"srgb":{"r":1,"g":0,"b":0}}}"#)]
        let initial = try prop(#"{"type":"All","data":"INITIAL"}"#)
        let unset = try prop(#"{"type":"All","data":"UNSET"}"#)
        let green = try prop(#"{"type":"Color","data":{"srgb":{"r":0,"g":0.5,"b":0}}}"#)
        let r = GlobalExtractor.applyingAllReset(own: [initial, unset, green], inherited: ch)
        XCTAssertEqual(r.inherited.count, 1)
        XCTAssertEqual(r.own.map(\.type), ["Color"])
        XCTAssertTrue(GlobalExtractor.applyingAllReset(own: [unset, initial], inherited: ch).inherited.isEmpty)
    }

    /// The order-aware twin of ContentsUnboxingTests' wave-18 pin (the
    /// hunk-for-L3-1.patch rewrite): declarations FOLLOWING `all` are kept,
    /// and an All-free list passes through untouched.
    func testDeclarationsAfterAllAreKeptAndAllFreeIsIdentity() throws {
        let comp = try JSONDecoder().decode(IRComponent.self, from: Data(#"""
        {"id":"a","name":"a","properties":[
           {"type":"All","data":"INITIAL"},
           {"type":"Width","data":{"type":"length","px":160}},
           {"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":1}}}]}
        """#.utf8))
        XCTAssertEqual(GlobalExtractor.applyingAllReset(own: comp.properties, inherited: []).own.map(\.type),
                       ["Width", "BackgroundColor"])
        let free = [try prop(#"{"type":"Width","data":{"type":"length","px":10}}"#)]
        XCTAssertEqual(GlobalExtractor.applyingAllReset(own: free, inherited: []).own.map(\.type), ["Width"])
    }

    /// VERBATIM display-contents-button: the RC6 root self-strip (init's
    /// ContentsUnboxing.resolve) removes `All` BEFORE the reset ever runs,
    /// so the 10px red border can never be revived by it (brief §9 check).
    func testDisplayContentsButtonRootStripsAllBeforeTheReset() throws {
        let button = try byId(displayContentsButton, "wpt__css-display__display-contents-button__2-036")
        let stripped = ContentsUnboxing.resolve(button).properties
        XCTAssertFalse(stripped.contains { $0.type == "All" || $0.type.hasPrefix("Border") || $0.type == "Display" })
        // The reset alone (web shape) keeps all 15 declarations after `all`.
        XCTAssertEqual(GlobalExtractor.applyingAllReset(own: button.properties, inherited: []).own.count, 15)
    }

    // MARK: - verbatim wave51-fix IR

    /// VERBATIM wave51-fix per-test IR (initialColor).
    private let initialColor = #"{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-cascade__all-prop-initial-color__0-014","name":"wpt__css-cascade__all-prop-initial-color__0","properties":[{"type":"Color","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}],"meta":{"sourceTag":"p"}},{"id":"all-prop-initial-color__0__0-015","name":"all-prop-initial-color__0__0","properties":[{"type":"All","data":"INITIAL"},{"type":"Color","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"wpt__css-cascade__all-prop-initial-color__0-014"},"text":"Test passes if this text is green.","meta":{"sourceTag":"span"}}]}"#
    /// VERBATIM wave51-fix per-test IR (inheritColor).
    private let inheritColor = #"{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-cascade__all-prop-inherit-color__0-012","name":"wpt__css-cascade__all-prop-inherit-color__0","properties":[{"type":"Color","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}],"meta":{"sourceTag":"p"}},{"id":"all-prop-inherit-color__0__0-013","name":"all-prop-inherit-color__0__0","properties":[{"type":"All","data":"INHERIT"},{"type":"Color","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"wpt__css-cascade__all-prop-inherit-color__0-012"},"text":"Test passes if this text is green.","meta":{"sourceTag":"span"}}]}"#
    /// VERBATIM wave51-fix per-test IR (unsetColor).
    private let unsetColor = #"{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-cascade__all-prop-unset-color__0-021","name":"wpt__css-cascade__all-prop-unset-color__0","properties":[{"type":"Color","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}],"meta":{"sourceTag":"p"}},{"id":"all-prop-unset-color__0__0-022","name":"all-prop-unset-color__0__0","properties":[{"type":"All","data":"UNSET"},{"type":"Color","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"wpt__css-cascade__all-prop-unset-color__0-021"},"text":"Test passes if this text is green.","meta":{"sourceTag":"span"}}]}"#
    /// VERBATIM wave51-fix per-test IR (revertColor).
    private let revertColor = #"{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-cascade__all-prop-revert-color__0-017","name":"wpt__css-cascade__all-prop-revert-color__0","properties":[{"type":"Color","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}],"meta":{"sourceTag":"p"}},{"id":"all-prop-revert-color__0__0-018","name":"all-prop-revert-color__0__0","properties":[{"type":"All","data":"REVERT"},{"type":"Color","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"wpt__css-cascade__all-prop-revert-color__0-017"},"text":"Test passes if this text is green.","meta":{"sourceTag":"span"}}]}"#
    /// VERBATIM wave51-fix per-test IR (allProp001).
    private let allProp001 = #"{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-cascade__all-prop-001__0-001","name":"wpt__css-cascade__all-prop-001__0","properties":[],"text":"Test passes if the digits are in order and there is no red.","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__css-cascade__all-prop-001__1-002","name":"wpt__css-cascade__all-prop-001__1","properties":[{"type":"Direction","data":"RTL"},{"type":"UnicodeBidi","data":"BIDI_OVERRIDE"},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BorderRightColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BorderBottomColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BorderLeftColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"Color","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"TextDecorationLine","data":["LINE_THROUGH"]},{"type":"FontWeight","data":{"weight":700,"original":"bold"}},{"type":"FontStyle","data":"italic"},{"type":"FontVariantCaps","data":"SMALL_CAPS"},{"type":"FontSize","data":{"px":20,"original":{"type":"length","px":20}}},{"type":"FontFamily","data":["monospace"]},{"type":"LineHeight","data":{"multiplier":1.2,"original":"normal"}},{"type":"OutlineStyle","data":"SOLID"},{"type":"OutlineColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"Float","data":"LEFT"},{"type":"LetterSpacing","data":{"px":0,"original":{"type":"length","original":{"v":1,"u":"EM"}}}},{"type":"Display","data":"LIST_ITEM"},{"type":"TextAlign","data":"CENTER"},{"type":"Width","data":{"type":"length","original":{"v":0.5,"u":"EM"}}},{"type":"MarginTop","data":{"original":{"v":10,"u":"EM"}}},{"type":"MarginRight","data":{"original":{"v":10,"u":"EM"}}},{"type":"MarginBottom","data":{"original":{"v":10,"u":"EM"}}},{"type":"MarginLeft","data":{"original":{"v":10,"u":"EM"}}},{"type":"OverflowX","data":"SCROLL"},{"type":"OverflowY","data":"SCROLL"},{"type":"All","data":"INITIAL"}],"text":"321","meta":{"runs":[{"child":"all-prop-001__1__0"},{"text":" 321"}]}},{"id":"all-prop-001__1__0-003","name":"all-prop-001__1__0","properties":[{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BorderRightColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BorderBottomColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BorderLeftColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"Color","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"TextDecorationLine","data":["LINE_THROUGH"]},{"type":"FontWeight","data":{"weight":700,"original":"bold"}},{"type":"FontStyle","data":"italic"},{"type":"FontVariantCaps","data":"SMALL_CAPS"},{"type":"FontSize","data":{"px":20,"original":{"type":"length","px":20}}},{"type":"FontFamily","data":["monospace"]},{"type":"LineHeight","data":{"multiplier":1.2,"original":"normal"}},{"type":"OutlineStyle","data":"SOLID"},{"type":"OutlineColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"Float","data":"LEFT"},{"type":"LetterSpacing","data":{"px":0,"original":{"type":"length","original":{"v":1,"u":"EM"}}}},{"type":"Display","data":"LIST_ITEM"},{"type":"TextAlign","data":"CENTER"},{"type":"Width","data":{"type":"length","original":{"v":0.5,"u":"EM"}}},{"type":"MarginTop","data":{"original":{"v":10,"u":"EM"}}},{"type":"MarginRight","data":{"original":{"v":10,"u":"EM"}}},{"type":"MarginBottom","data":{"original":{"v":10,"u":"EM"}}},{"type":"MarginLeft","data":{"original":{"v":10,"u":"EM"}}},{"type":"OverflowX","data":"SCROLL"},{"type":"OverflowY","data":"SCROLL"},{"type":"All","data":"INITIAL"},{"type":"Direction","data":"RTL"}],"slot":{"parent":"wpt__css-cascade__all-prop-001__1-002"},"text":"987 654","meta":{"sourceTag":"bdo"}}]}"#
    /// VERBATIM wave51-fix per-test IR (displayContentsButton).
    private let displayContentsButton = #"{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-display__display-contents-button__0-034","name":"wpt__css-display__display-contents-button__0","properties":[{"type":"FontKerning","data":"NONE"},{"type":"FontFeatureSettings","data":{"type":"features","features":[{"tag":"kern","value":0}]}}],"meta":{"role":"body-root"}},{"id":"wpt__css-display__display-contents-button__1-035","name":"wpt__css-display__display-contents-button__1","properties":[],"text":"You should see the word PASS below.","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__css-display__display-contents-button__2-036","name":"wpt__css-display__display-contents-button__2","properties":[{"type":"All","data":"INITIAL"},{"type":"FontKerning","data":"NONE"},{"type":"FontFeatureSettings","data":{"type":"features","features":[{"tag":"kern","value":0}]}},{"type":"BorderTopWidth","data":{"px":10}},{"type":"BorderRightWidth","data":{"px":10}},{"type":"BorderBottomWidth","data":{"px":10}},{"type":"BorderLeftWidth","data":{"px":10}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BorderRightColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BorderBottomColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BorderLeftColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"Display","data":"CONTENTS"}],"text":"PASS","meta":{"sourceTag":"button"}}]}"#
}

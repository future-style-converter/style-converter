//
//  ListMarkerTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 24, lane LF — B-RC3 (parts 1+2) pin table for
//  ListMarkerResolver + ListMarkerText.
//
//  TWIN of the Compose suite ListMarkerResolutionTest.kt — SAME cases,
//  SAME expected strings. Change one, change both.
//
//  ## The bug
//  Both native renderers synthesised the <li> marker from the PARENT's
//  meta.sourceTag alone — <ol> ⇒ "1.", <ul> ⇒ "•" — and threw the item's
//  own declarations away. Every list-style-type in the corpus painted a
//  plain disc.
//
//  ## The live artifact these cases come from
//  tools/titan/runs/wave23-final/sections/css-lists/per-test-ir/
//  wpt__css-lists__change-list-style-type-001.json — a flat v2 doc whose
//  `ul` components carry {"type":"ListStylePosition","data":"INSIDE"}
//  (UPPERCASE) and whose `li` children carry
//  {"type":"ListStyleType","data":"square"|"none"|"upper-roman"|"decimal"}
//  (lowercase-hyphenated). Both spellings are exercised — the case
//  normalisation is load-bearing on this wire.
//

import XCTest
@testable import StyleConverterRuntime

final class ListMarkerTests: XCTestCase {

    /// The exact `li` payload shape from the live css-lists doc.
    private func li(_ type: String) -> [IRProperty] {
        [IRProperty(type: "ListStyleType", data: .string(type))]
    }

    /// The exact `ul` payload from the live css-lists doc.
    private let ulInside = [IRProperty(type: "ListStylePosition", data: .string("INSIDE"))]

    private func marker(_ parentTag: String?,
                        _ parent: [IRProperty],
                        _ child: [IRProperty],
                        index: Int = 0) -> String? {
        guard let cfg = ListMarkerResolver.resolve(parentTag: parentTag,
                                                   parentProperties: parent,
                                                   childProperties: child) else { return nil }
        return ListMarkerText.marker(index: index, config: cfg)
    }

    // MARK: - B-RC3 part 1: the CHILD's own type wins

    func testChildListStyleTypeOverridesTheContainerDefault() {
        // The four live `change-list-style-type-001` values under a <ul>.
        XCTAssertEqual(marker("ul", ulInside, li("square")), "\u{25A0}")
        XCTAssertEqual(marker("ul", ulInside, li("none")), "")
        XCTAssertEqual(marker("ul", ulInside, li("upper-roman")), "I.")
        XCTAssertEqual(marker("ul", ulInside, li("decimal")), "1.")
    }

    func testWithoutAnOwnDeclarationTheUaDefaultStands() {
        // HTML §15.3.9 UA sheet: ul ⇒ disc, ol ⇒ decimal.
        XCTAssertEqual(marker("ul", ulInside, []), "\u{2022}")
        XCTAssertEqual(marker("ol", [], []), "1.")
        XCTAssertEqual(marker("ol", [], [], index: 2), "3.")
    }

    func testNonListContainersProduceNoMarkerAtAll() {
        // nil is how the renderer decides not to synthesise anything.
        XCTAssertNil(ListMarkerResolver.resolve(parentTag: "div",
                                                parentProperties: [],
                                                childProperties: li("square")))
        XCTAssertNil(ListMarkerResolver.resolve(parentTag: nil,
                                                parentProperties: [],
                                                childProperties: []))
        // …but menu/dir ARE list containers in the UA sheet.
        XCTAssertEqual(marker("menu", [], []), "\u{2022}")
        XCTAssertEqual(marker("DIR", [], []), "\u{2022}")
    }

    // MARK: - B-RC3 part 2: inheritance

    func testATypeDeclaredOnTheContainerReachesTheItem() {
        // css-lists-3 §3.1 list-style-type is Inherited: yes — the value
        // may sit on the <ul> (or, via the renderer's `childInherited`
        // set, on an ancestor) while the item declares nothing.
        let ulSquare = [IRProperty(type: "ListStyleType", data: .string("square"))]
        XCTAssertEqual(marker("ul", ulSquare, []), "\u{25A0}")
        // …and the item's own declaration still beats it.
        XCTAssertEqual(marker("ul", ulSquare, li("lower-roman")), "i.")
    }

    func testPositionResolvesFromTheContainerAndFromTheItem() {
        // Live wire spells it UPPERCASE; the item may override.
        XCTAssertEqual(ListMarkerResolver.resolve(parentTag: "ul",
                                                  parentProperties: ulInside,
                                                  childProperties: [])?.position, .inside)
        let outside = [IRProperty(type: "ListStylePosition", data: .string("outside"))]
        XCTAssertEqual(ListMarkerResolver.resolve(parentTag: "ul",
                                                  parentProperties: ulInside,
                                                  childProperties: outside)?.position, .outside)
        // Initial value when nobody declares it (css-lists-3 §3.1).
        XCTAssertEqual(ListMarkerResolver.resolve(parentTag: "ol",
                                                  parentProperties: [],
                                                  childProperties: [])?.position, .outside)
    }

    // MARK: - The counter-style table is now reachable from the renderer

    func testTheFullCounterStyleTableRoutesThrough() {
        // css-counter-styles-3 §6 predefined styles, index 0 unless noted.
        XCTAssertEqual(marker("ol", [], li("decimal-leading-zero")), "01.")
        XCTAssertEqual(marker("ol", [], li("lower-alpha")), "a.")
        XCTAssertEqual(marker("ol", [], li("upper-latin"), index: 1), "B.")
        XCTAssertEqual(marker("ol", [], li("lower-roman"), index: 3), "iv.")
        XCTAssertEqual(marker("ol", [], li("lower-greek")), "\u{03B1}.")
        XCTAssertEqual(marker("ol", [], li("armenian")), "\u{0531}.")
        XCTAssertEqual(marker("ol", [], li("georgian")), "\u{10D0}.")
        XCTAssertEqual(marker("ol", [], li("hebrew")), "\u{05D0}.")
        XCTAssertEqual(marker("ol", [], li("cjk-decimal")), "\u{4E00}.")
        XCTAssertEqual(marker("ol", [], li("hiragana")), "\u{3042}.")
        XCTAssertEqual(marker("ol", [], li("katakana")), "\u{30A2}.")
        XCTAssertEqual(marker("ol", [], li("hiragana-iroha")), "\u{3044}.")
        XCTAssertEqual(marker("ol", [], li("katakana-iroha")), "\u{30A4}.")
        XCTAssertEqual(marker("ul", [], li("circle")), "\u{25CB}")
    }

    /// Multi-digit / multi-glyph rows, so the additive systems are pinned
    /// past their first entry (the Compose twin shares these values).
    func testAdditiveSystemsPastTheFirstEntry() {
        XCTAssertEqual(marker("ol", [], li("decimal-leading-zero"), index: 11), "12.")
        XCTAssertEqual(marker("ol", [], li("upper-roman"), index: 13), "XIV.")
        XCTAssertEqual(marker("ol", [], li("lower-alpha"), index: 26), "aa.")
        XCTAssertEqual(marker("ol", [], li("cjk-decimal"), index: 11), "\u{4E00}\u{4E8C}.")
        // Hebrew 15 → tens(10)=י + units(5)=ה (the plain positional form
        // the Compose twin also emits).
        XCTAssertEqual(marker("ol", [], li("hebrew"), index: 14), "\u{05D9}\u{05D4}.")
        // Armenian 11 → tens(10)=Ժ + units(1)=Ա.
        XCTAssertEqual(marker("ol", [], li("armenian"), index: 10), "\u{053A}\u{0531}.")
        // Georgian 11 → tens(10)=ი + units(1)=ა (Mkhedruli, U+10D8/U+10D0).
        XCTAssertEqual(marker("ol", [], li("georgian"), index: 10), "\u{10D8}\u{10D0}.")
        // Georgian EXACTLY 10000 → the U+10F5 digit alone. Regression pin:
        // the first draft handed additive() a remainder of 0, which tripped
        // its own `num > 0` guard and appended a literal "0" — the ONLY
        // divergence found by the wave-24 exhaustive twin-table diff
        // (22 counter styles × 317 indices; Compose printed "ჵ.").
        XCTAssertEqual(marker("ol", [], li("georgian"), index: 9999), "\u{10F5}.")
        // …and the neighbours on both sides stay put.
        XCTAssertEqual(marker("ol", [], li("georgian"), index: 9998), "\u{10F0}\u{10E8}\u{10DF}\u{10D7}.")
        XCTAssertEqual(marker("ol", [], li("georgian"), index: 10000), "\u{10F5}\u{10D0}.")
    }

    // MARK: - The documented @counter-style gap

    func testUnresolvableCustomNameKeepsTheContainerDefault() {
        // `marker-text-matches-disc` / `-circle` declare `my-disc` /
        // `my-circle`, defined by an @counter-style rule the IR wire does
        // not carry. Keeping the container default is the documented
        // choice (and is what those two rules happen to define).
        XCTAssertEqual(marker("ul", ulInside, li("my-disc")), "\u{2022}")
        XCTAssertEqual(marker("ul", ulInside, li("my-circle")), "\u{2022}")
        XCTAssertEqual(marker("ol", [], li("my-circle")), "1.")
    }
}

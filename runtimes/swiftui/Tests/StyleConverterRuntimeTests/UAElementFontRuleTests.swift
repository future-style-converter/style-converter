//
//  UAElementFontRuleTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 40, lane T7 — pins the UA element-font cascade rule
//  (StyleEngine/typography/UAElementFontRule.swift).
//
//  ── What is being protected ────────────────────────────────────────────────
//  Eight depth-48 cells carry a heading with no author font declaration, and
//  eight carry a bare `<sup>`/`<sub>`; the natives paint them at the plain
//  16px body face while the browser ref applies Chromium's html.css. The two
//  ref-pixel measurements that pin the numbers are in the module header
//  (text-decoration-color's 23px h3 line pitch ÷ the ref's 1.25 line-height =
//  18.4 ≈ 1.17em, and inset-014's 15.58px monospace `ch` = a 26px = 2 × 13px
//  face).
//
//  ── The invariants that must never break ───────────────────────────────────
//   * an UNTAGGED component (the entire 327-pair baseline corpus, which
//     carries no `_tag` at all) must come out byte-identical;
//   * an AUTHOR font-size/weight must WIN (css-cascade-4 §6.1);
//   * an INHERITED font-size must LOSE, and must be substituted IN PLACE —
//     StyleBuilder reads `first(where: FontSize)`, so an appended entry would
//     never be seen.
//
//  The IR payloads are the LIVE wire shapes from
//  tools/titan/runs/wave39-final/sections/css-text-decor/per-test-ir/
//  wpt__css-text-decor__text-decoration-{color,inset-014}.json.
//

import XCTest
// @testable: UAElementFontRule / StyleBuilder / ComponentStyle are internal to
// the module — same access pattern as MonospaceUAFontSizeTests.swift.
@testable import StyleConverterRuntime

final class UAElementFontRuleTests: XCTestCase {

    private func props(_ pairs: [(String, IRValue)]) -> [IRProperty] {
        pairs.map { IRProperty(type: $0.0, data: $0.1) }
    }

    /// The px value of a list's first FontSize entry — the exact read
    /// StyleBuilder performs (`first`, not `last`).
    private func firstFontSizePx(_ list: [IRProperty]) -> Double? {
        list.first(where: { $0.type == "FontSize" })
            .flatMap { ValueExtractors.extractPx($0.data) }
            .map(Double.init)
    }

    private func firstFontWeight(_ list: [IRProperty]) -> IRValue? {
        list.first(where: { $0.type == "FontWeight" })?.data
    }

    /// inset-014's h1 FontFamily payload, verbatim.
    private let bareMonospace = IRValue.array([.string("monospace")])

    // MARK: - 1. The table

    func testHeadingMultipliersMatchTheUASheet() {
        XCTAssertEqual(UAElementFontRule.sizeMultiplier(forTag: "h1"), 2.0)
        XCTAssertEqual(UAElementFontRule.sizeMultiplier(forTag: "h2"), 1.5)
        XCTAssertEqual(UAElementFontRule.sizeMultiplier(forTag: "h3"), 1.17)
        XCTAssertEqual(UAElementFontRule.sizeMultiplier(forTag: "h4"), 1.0)
        XCTAssertEqual(UAElementFontRule.sizeMultiplier(forTag: "h5"), 0.83)
        XCTAssertEqual(UAElementFontRule.sizeMultiplier(forTag: "h6"), 0.67)
        // Case folding: the wire may carry any spelling of the tag.
        XCTAssertEqual(UAElementFontRule.sizeMultiplier(forTag: "H1"), 2.0)
    }

    func testSupAndSubUseTheSmallerRatio() {
        XCTAssertEqual(UAElementFontRule.sizeMultiplier(forTag: "sup") ?? 0,
                       1.0 / 1.2, accuracy: 1e-12)
        XCTAssertEqual(UAElementFontRule.sizeMultiplier(forTag: "sub") ?? 0,
                       1.0 / 1.2, accuracy: 1e-12)
        // …and are NOT bold — only the six headings are.
        XCTAssertFalse(UAElementFontRule.isBold(forTag: "sup"))
        XCTAssertFalse(UAElementFontRule.isBold(forTag: "sub"))
        XCTAssertTrue(UAElementFontRule.isBold(forTag: "h6"))
    }

    /// The tags deliberately left OUT (see the header's scope note): widening
    /// the table to `b`/`strong` would put currently-passing cells at risk.
    func testUntabledTagsAreInert() {
        XCTAssertNil(UAElementFontRule.sizeMultiplier(forTag: "b"))
        XCTAssertNil(UAElementFontRule.sizeMultiplier(forTag: "span"))
        XCTAssertNil(UAElementFontRule.sizeMultiplier(forTag: nil))
        XCTAssertFalse(UAElementFontRule.isBold(forTag: "b"))
    }

    // MARK: - 2. Byte-stability of everything outside the table

    /// THE regression sentinel: the 327-pair product corpus carries no
    /// `_tag`, so every one of its components must leave `apply` untouched.
    func testUntaggedComponentIsUnchanged() {
        let merged = props([
            ("Color", .object(["srgb": .object(["r": .double(0), "g": .double(0),
                                                "b": .double(0)])])),
            ("Width", .object(["type": .string("length"), "px": .double(100)])),
        ])
        let out = UAElementFontRule.apply(sourceTag: nil, own: [], merged: merged)
        XCTAssertEqual(out.count, merged.count)
        XCTAssertEqual(out.map(\.type), merged.map(\.type))
        XCTAssertNil(firstFontSizePx(out))
    }

    func testUntabledTagIsUnchanged() {
        let merged = props([("Color", .string("black"))])
        let out = UAElementFontRule.apply(sourceTag: "div", own: [], merged: merged)
        XCTAssertEqual(out.map(\.type), merged.map(\.type))
    }

    // MARK: - 3. The cascade

    /// text-decoration-color's `<h3>`: no author font declaration at all, so
    /// the UA sheet supplies 1.17 × 16 = 18.72px and bold.
    func testBareH3TakesTheUAFace() {
        let out = UAElementFontRule.apply(sourceTag: "h3", own: [],
                                          merged: props([("Color", .string("black"))]))
        XCTAssertEqual(firstFontSizePx(out) ?? 0, 18.72, accuracy: 1e-9)
        XCTAssertNotNil(firstFontWeight(out))
    }

    /// Author beats UA (css-cascade-4 §6.1) — the h1 shape the extractor
    /// already bakes 32px/bold onto must not be re-derived here.
    func testOwnFontSizeAndWeightWin() {
        let own = props([
            ("FontSize", .object(["px": .double(32)])),
            ("FontWeight", .object(["weight": .double(700)])),
        ])
        let out = UAElementFontRule.apply(sourceTag: "h1", own: own, merged: own)
        XCTAssertEqual(firstFontSizePx(out) ?? 0, 32, accuracy: 1e-9)
        XCTAssertEqual(out.filter { $0.type == "FontSize" }.count, 1)
    }

    /// The `font` SHORTHAND carries a size and a weight, so it blocks both
    /// halves exactly like the longhands.
    func testFontShorthandBlocksBothHalves() {
        let own = props([("Font", .string("italic 12px serif"))])
        let out = UAElementFontRule.apply(sourceTag: "h2", own: own, merged: own)
        XCTAssertNil(firstFontSizePx(out))
        XCTAssertNil(firstFontWeight(out))
    }

    /// Inheritance LOSES to a UA declaration (css-cascade-4 §4.3) — and the
    /// substitution is IN PLACE, so StyleBuilder's `first(where:)` read sees
    /// the UA value rather than the inherited one behind it.
    func testInheritedFontSizeIsSubstitutedInPlace() {
        let merged = props([
            ("FontSize", .object(["px": .double(16)])),   // inherited from body
            ("Color", .string("black")),
        ])
        let out = UAElementFontRule.apply(sourceTag: "h1", own: [], merged: merged)
        XCTAssertEqual(out.filter { $0.type == "FontSize" }.count, 1)
        XCTAssertEqual(firstFontSizePx(out) ?? 0, 32, accuracy: 1e-9)
        // Position preserved: the UA entry sits where the inherited one was.
        XCTAssertEqual(out.first?.type, "FontSize")
    }

    // MARK: - 4. The monospace base (inset-014)

    /// `h1 { font-family: monospace }` with no font-size: the UA `2em`
    /// resolves against the 13px fixed default, not 16 — the measurement in
    /// the module header (15.58px per `ch` ⇒ a 26px face).
    func testMonospaceHeadingResolvesAgainstTheFixedDefault() {
        let merged = props([("FontFamily", bareMonospace)])
        XCTAssertEqual(UAElementFontRule.emBasePx(merged: merged), 13.0)
        let out = UAElementFontRule.apply(sourceTag: "h1", own: [], merged: merged)
        XCTAssertEqual(firstFontSizePx(out) ?? 0, 26, accuracy: 1e-9)
    }

    /// A non-monospace family keeps the 16px medium.
    func testProportionalHeadingResolvesAgainstSixteen() {
        let merged = props([("FontFamily", .array([.string("Inter")]))])
        XCTAssertEqual(UAElementFontRule.emBasePx(merged: merged), 16.0)
        let out = UAElementFontRule.apply(sourceTag: "h1", own: [], merged: merged)
        XCTAssertEqual(firstFontSizePx(out) ?? 0, 32, accuracy: 1e-9)
    }

    /// An INHERITED font-size is the `em` base (css-values-4 §5.1.1), and it
    /// out-ranks the `medium` default — including the monospace one, which
    /// only stands in when nothing up the chain declared a size at all.
    func testInheritedSizeIsTheEmBase() {
        XCTAssertEqual(UAElementFontRule.emBasePx(
            merged: props([("FontSize", .object(["px": .double(20)]))])), 20.0)
        XCTAssertEqual(UAElementFontRule.emBasePx(merged: props([
            ("FontSize", .object(["px": .double(20)])),
            ("FontFamily", bareMonospace),
        ])), 20.0)
        let out = UAElementFontRule.apply(
            sourceTag: "sup", own: [],
            merged: props([("FontSize", .object(["px": .double(24)]))]))
        XCTAssertEqual(firstFontSizePx(out) ?? 0, 24.0 / 1.2, accuracy: 1e-9)
    }

    // MARK: - 5. The stacked-chain gate

    /// A heading that hosts child boxes stands down (the container stacks
    /// them; a 2em face only magnifies that — see headingAppliesTo for the
    /// measured cells). inset-005/011/014's `<h1>` wrapping a `<u>` is this
    /// shape, and all three must come out byte-identical to wave39-final.
    func testHeadingWithChildrenStandsDown() {
        let merged = props([("FontSize", .object(["px": .double(16)]))])
        let out = UAElementFontRule.apply(sourceTag: "h1", own: [], merged: merged,
                                          hasElementChildren: true)
        XCTAssertEqual(firstFontSizePx(out) ?? 0, 16, accuracy: 1e-9)
        XCTAssertNil(firstFontWeight(out))
        XCTAssertEqual(out.map(\.type), merged.map(\.type))
    }

    /// The sup/sub half is NOT gated — it moved every isolated cell in the
    /// right direction (subelements-002 0.7214 → 0.7257, -003 0.8947 →
    /// 0.9043), and a sup is a leaf in the whole corpus anyway.
    func testSupIsNotGatedByChildren() {
        let out = UAElementFontRule.apply(
            sourceTag: "sup", own: [],
            merged: props([("FontSize", .object(["px": .double(16)]))]),
            hasElementChildren: true)
        XCTAssertEqual(firstFontSizePx(out) ?? 0, 16.0 / 1.2, accuracy: 1e-9)
    }

    func testHeadingGatePredicate() {
        XCTAssertTrue(UAElementFontRule.headingAppliesTo(hasElementChildren: false))
        XCTAssertFalse(UAElementFontRule.headingAppliesTo(hasElementChildren: true))
    }

    // MARK: - 6. End-to-end through StyleBuilder

    /// The whole point of substituting in place: the built style's text face
    /// must carry the UA size, which is the value PlaceholderLabel paints.
    func testStyleBuilderConsumesTheInjectedFace() {
        let merged = UAElementFontRule.apply(
            sourceTag: "h3", own: [],
            merged: props([("FontSize", .object(["px": .double(16)]))]))
        let style = StyleBuilder.build(from: merged)
        XCTAssertEqual(Double(style.text.fontSize ?? 0), 18.72, accuracy: 1e-6)
    }
}

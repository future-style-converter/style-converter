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
        // HTML §15.3.7 UA sheet: ul ⇒ disc, ol ⇒ decimal.
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

    // MARK: - Wave 25 item 1: the UA rule beats an ANCESTOR's declaration

    /// The list-style half of `ListStyleUaRule.apply`'s output.
    ///
    /// Wave 30 (lane 3, fix B5) added a SECOND UA declaration to the same
    /// rule — `ul, menu, dir, ol { padding-inline-start: 40px }` (HTML
    /// §15.3.7) — so every container path now also carries a `PaddingLeft`
    /// entry. The wave-25 cases below are about the TYPE cascade and are
    /// kept focused on it by dropping that entry here; the padding has its
    /// own pins under "Wave 30".
    private func listStyleOnly(_ properties: [IRProperty]) -> [IRProperty] {
        properties.filter { $0.type.hasPrefix("ListStyle") }
    }

    /// TWIN of the Compose cases in ListMarkerResolutionTest.kt.
    func testAnAncestorTypeLosesToTheContainersUaRule() {
        // The inversion: `<div style="list-style-type:square"><ul><li>`.
        // The ul declares nothing, so the UA rule `ul { list-style-type:
        // disc }` (HTML §15.3.7) wins the cascade on the ul ELEMENT and
        // inheritance is never consulted (css-cascade-4 §4.3) — Chromium
        // paints a disc, not a square.
        let ancestor = [IRProperty(type: "ListStyleType", data: .string("square"))]
        let corrected = ListStyleUaRule.apply(sourceTag: "ul", own: [], merged: ancestor)
        XCTAssertEqual(listStyleOnly(corrected).count, 1)
        XCTAssertEqual(listStyleOnly(corrected)[0].type, "ListStyleType")
        XCTAssertEqual(marker("ul", corrected, []), "\u{2022}")
        // …and on an <ol> the UA value is decimal, not the ancestor's square.
        XCTAssertEqual(
            marker("ol", ListStyleUaRule.apply(sourceTag: "ol", own: [], merged: ancestor), []),
            "1.")
    }

    func testTheContainersOwnDeclarationStillBeatsTheUaRule() {
        // Author origin beats UA (css-cascade-4 §6.1). This is the live
        // `content-property/marker-text-matches-armenian` shape: the <ol>
        // itself carries list-style-type and must keep it. Guards against
        // "fix" attempts that merely reorder the fold.
        let own = [IRProperty(type: "ListStyleType", data: .string("armenian"))]
        let merged = ListStyleUaRule.apply(sourceTag: "ol", own: own, merged: own)
        XCTAssertEqual(listStyleOnly(merged).map { $0.type }, ["ListStyleType"])
        XCTAssertEqual(marker("ol", merged, []), "\u{0531}.")
    }

    func testTheUaRuleIsInertOutsideListContainers() {
        let ancestor = [IRProperty(type: "ListStyleType", data: .string("square"))]
        // Not a list container ⇒ the UA sheet declares nothing ⇒ untouched.
        XCTAssertNil(marker("div", ancestor, []))
        XCTAssertEqual(
            ListMarkerResolver.markerType(
                from: ListStyleUaRule.apply(sourceTag: "div", own: [], merged: ancestor)[0].data),
            .square)
        // A container with nothing inherited ⇒ nothing to displace.
        let onlyPosition = ListStyleUaRule.apply(sourceTag: "ul", own: [], merged: ulInside)
        XCTAssertEqual(listStyleOnly(onlyPosition).map { $0.type }, ["ListStylePosition"])
        // The unexpanded shorthand counts as an own type declaration.
        let ownShorthand = [IRProperty(type: "ListStyle", data: .string("square"))]
        XCTAssertEqual(
            ListMarkerResolver.markerType(
                from: ListStyleUaRule.apply(sourceTag: "ul",
                                            own: ownShorthand,
                                            merged: ancestor)[0].data),
            .square)
    }

    // MARK: - Wave 30 (lane 3, fix B5): the UA `padding-inline-start`

    /// Every px value the rule's output carries for `type`, in order.
    ///
    /// `type` defaults to `PaddingLeft`, the physical side the UA
    /// `padding-inline-start` maps to under the default `direction: ltr`;
    /// the rtl cases below pass `PaddingRight` (css-logical-1 §2.1).
    private func paddingPx(_ properties: [IRProperty],
                           _ type: String = "PaddingLeft") -> [Double] {
        properties.filter { $0.type == type }.compactMap {
            guard case .object(let o) = $0.data else { return nil }
            switch o["px"] {
            case .double(let d): return d
            case .int(let i): return Double(i)
            default: return nil
            }
        }
    }

    func testAListContainerWithNoAuthorPaddingTakesTheUa40px() {
        // HTML §15.3.7 `ul, menu, dir, ol { padding-inline-start: 40px }`.
        // The live shape: change-list-style-type-001's ten `<ul>`s declare
        // ListStylePosition and nothing else, and both natives laid their
        // items out 40px left of web (ink columns 17 vs 56) because nothing
        // supplied this.
        for tag in ["ul", "ol", "menu", "dir"] {
            let merged = ListStyleUaRule.apply(sourceTag: tag, own: [], merged: ulInside)
            XCTAssertEqual(paddingPx(merged),
                           [ListStyleUaRule.uaPaddingInlineStartPx],
                           "\(tag) must take the UA padding")
        }
        // A non-container gets nothing — the identity path.
        XCTAssertEqual(
            paddingPx(ListStyleUaRule.apply(sourceTag: "div", own: [], merged: ulInside)),
            [])
    }

    func testAnAuthorInlineStartPaddingBeatsTheUaRule() {
        // css-cascade-4 §6.1. The live shape: css3-counter-styles-007's
        // `<ol>` declares `padding-left: 8em` and must keep exactly that —
        // injecting 40px beside it would double-indent every one of its
        // 24 rows. Under the default ltr all three spellings block it: the
        // physical longhand for THIS direction, the logical longhand, and
        // the `Padding` shorthand — the last one defensive only, since the
        // converter's PaddingExpander always expands `padding` and no
        // `PaddingProperty` exists in the 558-property catalogue.
        let authored = [IRProperty(type: "PaddingLeft",
                                   data: .object(["px": .double(128)]))]
        XCTAssertEqual(paddingPx(ListStyleUaRule.apply(sourceTag: "ol",
                                                       own: authored,
                                                       merged: authored)), [128])
        for declared in ["PaddingInlineStart", "Padding"] {
            let own = [IRProperty(type: declared, data: .object(["px": .double(128)]))]
            XCTAssertEqual(paddingPx(ListStyleUaRule.apply(sourceTag: "ol",
                                                           own: own, merged: own)), [],
                           "\(declared) must block the UA padding")
        }
    }

    // MARK: - Wave 30 fix round (lane N, fix N1): the direction mapping

    /// TWIN of the Compose cases
    /// `anRtlContainerTakesTheUaPaddingOnTheRight` /
    /// `theAuthorBlockFollowsTheSameDirectionMapping` /
    /// `theDirectionHelpersAreTheSingleMapping`.
    func testAnRtlContainerTakesTheUaPaddingOnTheRight() {
        // css-logical-1 §2.1: the inline-START side of a `direction: rtl`
        // box is the RIGHT one, so HTML §15.3.7's `padding-inline-start:
        // 40px` must land there. The first cut of B5 injected the physical
        // `PaddingLeft` unconditionally — 40px on the wrong edge AND 40px
        // missing on the right, an 80px relative error on every rtl list.
        // Live exposure: fixtures/wpt/css-lists/list-marker-symbol-bidi.json
        // carries five `direction: rtl` `<ul>`s.
        let ua = ListStyleUaRule.uaPaddingInlineStartPx
        let rtl = [IRProperty(type: "Direction", data: .string("RTL"))] + ulInside
        let rtlOut = ListStyleUaRule.apply(sourceTag: "ul", own: rtl, merged: rtl)
        XCTAssertEqual(paddingPx(rtlOut, "PaddingRight"), [ua])
        XCTAssertEqual(paddingPx(rtlOut, "PaddingLeft"), [])
        // ltr — declared explicitly and (in the case above) by absence —
        // still lands on the left, so no committed ltr capture moves.
        let ltr = [IRProperty(type: "Direction", data: .string("LTR"))] + ulInside
        XCTAssertEqual(
            paddingPx(ListStyleUaRule.apply(sourceTag: "ul", own: ltr, merged: ltr)), [ua])
        // `direction` is Inherited: yes (css-writing-modes-4 §2.1) and sits
        // in InheritedText.inheritedTypes, so an ANCESTOR's rtl reaches the
        // container through the merged list alone.
        let inherited = [IRProperty(type: "Direction", data: .string("RTL"))]
        XCTAssertEqual(
            paddingPx(ListStyleUaRule.apply(sourceTag: "ul", own: [], merged: inherited),
                      "PaddingRight"), [ua])
        // A garbage/unresolved keyword is ltr, the CSS initial value.
        let junk = [IRProperty(type: "Direction", data: .string("sideways"))]
        XCTAssertEqual(
            paddingPx(ListStyleUaRule.apply(sourceTag: "ul", own: junk, merged: junk)), [ua])
    }

    func testTheAuthorBlockFollowsTheSameDirectionMapping() {
        let ua = ListStyleUaRule.uaPaddingInlineStartPx
        let dir = IRProperty(type: "Direction", data: .string("RTL"))
        // padding-right ON an rtl container IS its inline start ⇒ blocks.
        let ownRight = [dir, IRProperty(type: "PaddingRight",
                                        data: .object(["px": .double(128)]))]
        XCTAssertEqual(
            paddingPx(ListStyleUaRule.apply(sourceTag: "ol", own: ownRight, merged: ownRight),
                      "PaddingRight"), [128])
        // padding-LEFT on the same rtl container says nothing about the
        // inline start (css-logical-1 §2.1), so both declarations survive
        // the cascade exactly as they do in a browser: the author's 128px
        // on the left, the UA's 40px on the right.
        let ownLeft = [dir, IRProperty(type: "PaddingLeft",
                                       data: .object(["px": .double(128)]))]
        let mixed = ListStyleUaRule.apply(sourceTag: "ol", own: ownLeft, merged: ownLeft)
        XCTAssertEqual(paddingPx(mixed, "PaddingRight"), [ua])
        XCTAssertEqual(paddingPx(mixed, "PaddingLeft"), [128])
        // The logical longhand — and the defensive `Padding` guard — block
        // in BOTH directions, since they name the same side the UA does.
        for declared in ["PaddingInlineStart", "Padding"] {
            let own = [dir, IRProperty(type: declared, data: .object(["px": .double(128)]))]
            let out = ListStyleUaRule.apply(sourceTag: "ol", own: own, merged: own)
            XCTAssertEqual(paddingPx(out, "PaddingRight"), [],
                           "\(declared) must block the rtl UA padding")
            XCTAssertEqual(paddingPx(out, "PaddingLeft"), [])
        }
    }

    func testTheDirectionHelpersAreTheSingleMapping() {
        // The two natives are diffed against these, so pin them directly.
        XCTAssertEqual(ListStyleUaRule.startPaddingType(rtl: false), "PaddingLeft")
        XCTAssertEqual(ListStyleUaRule.startPaddingType(rtl: true), "PaddingRight")
        // Last entry wins, the fold convention this package uses.
        XCTAssertTrue(ListStyleUaRule.isRtl([
            IRProperty(type: "Direction", data: .string("LTR")),
            IRProperty(type: "Direction", data: .string("rtl"))]))
        XCTAssertFalse(ListStyleUaRule.isRtl([
            IRProperty(type: "Direction", data: .string("RTL")),
            IRProperty(type: "Direction", data: .string("LTR"))]))
        XCTAssertFalse(ListStyleUaRule.isRtl([]))
    }

    func testPositionAndImageStillInheritFromAnAncestor() {
        // The UA sheet declares NO list-style-position/-image on ul/ol, so
        // for those two inheritance is untouched by the rule — an
        // ancestor's `inside` must still reach the item.
        let ancestor = [
            IRProperty(type: "ListStylePosition", data: .string("INSIDE")),
            IRProperty(type: "ListStyleType", data: .string("square")),
        ]
        let corrected = ListStyleUaRule.apply(sourceTag: "ul", own: [], merged: ancestor)
        let cfg = ListMarkerResolver.resolve(parentTag: "ul",
                                             parentProperties: corrected,
                                             childProperties: [])
        XCTAssertEqual(cfg?.position, .inside)
        XCTAssertEqual(cfg?.type, .disc)
    }

    // MARK: - Wave 25 item 2: the `list-style` shorthand branch

    func testTheShorthandExpandsAllThreeComponents() {
        // css-lists-3 §3.6, any component order.
        let full = ListStyleShorthand.expand("square inside url(bullet.png)")
        XCTAssertEqual(full, ListStyleShorthand.Expansion(
            type: .square, position: .inside, image: "bullet.png"))
        let reordered = ListStyleShorthand.expand("url('b.png') upper-roman outside")
        XCTAssertEqual(reordered, ListStyleShorthand.Expansion(
            type: .upperRoman, position: .outside, image: "b.png"))
    }

    func testOmittedShorthandComponentsResetToTheirInitialValues() {
        // css-cascade-4 §3: a shorthand sets every longhand it omits to
        // the INITIAL value — it does not leave them inherited.
        XCTAssertEqual(ListStyleShorthand.expand("square"),
                       ListStyleShorthand.Expansion(
                        type: .square, position: .outside, image: nil))
    }

    func testTheShorthandsNoneAndGlobalKeywords() {
        // One `none` fills type AND image (§3.5).
        XCTAssertEqual(ListStyleShorthand.expand("none")?.type, .noMarker)
        // A `none` alongside an explicit type goes to the image slot only.
        XCTAssertEqual(ListStyleShorthand.expand("square none")?.type, .square)
        // Two `none`s: one per slot.
        XCTAssertEqual(ListStyleShorthand.expand("none none")?.type, .noMarker)
        // Three is one more than there are slots ⇒ invalid ⇒ nil.
        XCTAssertNil(ListStyleShorthand.expand("none none none"))
        // `inherit`/`unset` on an inherited property keep the running value
        // (nil = "caller keeps what it had").
        XCTAssertNil(ListStyleShorthand.expand("inherit"))
        XCTAssertNil(ListStyleShorthand.expand("unset"))
        XCTAssertNil(ListStyleShorthand.expand("revert"))
        // `initial` resets all three.
        XCTAssertEqual(ListStyleShorthand.expand("initial"),
                       ListStyleShorthand.Expansion(
                        type: .disc, position: .outside, image: nil))
    }

    func testAnInvalidShorthandDropsTheWholeDeclaration() {
        // css-syntax-3 §9: an invalid declaration is dropped, not
        // half-applied. An unresolvable @counter-style ident, a repeated
        // component, and two images all invalidate.
        XCTAssertNil(ListStyleShorthand.expand("my-counter inside"))
        XCTAssertNil(ListStyleShorthand.expand("square circle"))
        XCTAssertNil(ListStyleShorthand.expand("inside outside"))
        XCTAssertNil(ListStyleShorthand.expand("url(a.png) url(b.png)"))
        XCTAssertNil(ListStyleShorthand.expand("   "))
        XCTAssertNil(ListStyleShorthand.expand(nil))
    }

    func testTheShorthandReachesTheMarkerResolver() {
        // End-to-end: a foreign wire doc that puts the shorthand on the
        // <li> now paints the right glyph instead of the container default.
        let upperRoman = [IRProperty(type: "ListStyle", data: .string("upper-roman"))]
        XCTAssertEqual(marker("ul", ulInside, upperRoman), "I.")
        let none = [IRProperty(type: "ListStyle", data: .string("none"))]
        XCTAssertEqual(marker("ul", ulInside, none), "")
    }

    // MARK: - Wave 25 skeptic: the two twin-dump divergences

    func testAnInheritedShorthandKeepsItsPositionUnderTheUaRule() {
        // Regression: the UA rule used to REPLACE the whole inherited
        // entry with a ListStyleType, so when the ancestor's value arrived
        // as the unexpanded `list-style: circle inside` shorthand its
        // POSITION component died with it — the <li> fell back to
        // `outside`. Only the type is displaced (ul/ol carry no UA
        // list-style-position declaration); browser probe:
        // `<div style="list-style-position:inside"><ul><li>` computes
        // `inside` on the li in Chromium.
        let ancestorShorthand = [IRProperty(type: "ListStyle", data: .string("circle inside"))]
        let corrected = ListStyleUaRule.apply(sourceTag: "ul", own: [], merged: ancestorShorthand)
        let cfg = ListMarkerResolver.resolve(parentTag: "ul",
                                             parentProperties: corrected,
                                             childProperties: [])
        // Type: the UA `disc` beats the ancestor's `circle`…
        XCTAssertEqual(cfg?.type, .disc)
        // …but the position it carried still inherits.
        XCTAssertEqual(cfg?.position, .inside)
        // A longhand ListStyleType alongside it is still displaced.
        let both = [IRProperty(type: "ListStyle", data: .string("circle inside")),
                    IRProperty(type: "ListStyleType", data: .string("square"))]
        let fixed = ListStyleUaRule.apply(sourceTag: "ul", own: [], merged: both)
        let cfg2 = ListMarkerResolver.resolve(parentTag: "ul",
                                              parentProperties: fixed,
                                              childProperties: [])
        XCTAssertEqual(cfg2?.type, .disc)
        XCTAssertEqual(cfg2?.position, .inside)
    }

    func testTheShorthandsUrlFunctionNameIsCaseInsensitive() {
        // css-syntax-3 §4.3 — function names are case-insensitive. The
        // Compose twin lowercased the token for the component test but
        // stripped with the ORIGINAL casing, so `URL(a.png)` kept its
        // whole wrapper there while this side produced `a.png`.
        XCTAssertEqual(ListStyleShorthand.expand("URL(a.png)")?.image, "a.png")
        XCTAssertEqual(ListStyleShorthand.expand("url(a.png)")?.image, "a.png")
        // One quote layer only.
        XCTAssertEqual(ListStyleShorthand.expand("url(\"q.png\")")?.image, "q.png")
        XCTAssertEqual(ListStyleShorthand.expand("url(\"'a'\")")?.image, "'a'")
    }
}

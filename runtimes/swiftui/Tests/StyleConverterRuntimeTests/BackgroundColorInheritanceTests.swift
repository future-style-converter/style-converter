//
//  BackgroundColorInheritanceTests.swift
//  StyleConverterRuntimeTests — wave-49 lane A5.
//
//  `background-color: inherit` (css-cascade-5 §7.3.2).
//
//  The document under test is the VERBATIM css-color/currentcolor-002 IR:
//    tools/titan/runs/wave48-final/sections/css-color/per-test-ir/
//      wpt__css-color__currentcolor-002.json
//  ids, slot parents and property payloads copied unchanged; only the
//  properties this pass never reads (Width/Height/FontSize) are omitted.
//
//  Chrome 151 on that exact nesting computes the three backgrounds as
//  rgb(255,0,0) / rgb(255,0,0) / rgb(0,128,0) — measured with
//  getComputedStyle, not assumed.
//

import XCTest
@testable import StyleConverterRuntime

final class BackgroundColorInheritanceTests: XCTestCase {

    // MARK: - Wire helpers (verbatim payloads)

    private func v(_ s: String) -> IRValue {
        try! JSONDecoder().decode(IRValue.self, from: Data(s.utf8))
    }
    private func p(_ t: String, _ s: String) -> IRProperty { IRProperty(type: t, data: v(s)) }

    private let RED = #"{"srgb":{"r":1,"g":0,"b":0},"original":"red"}"#
    private let GREEN = #"{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}"#
    private let CURRENTCOLOR = #"{"original":"currentColor"}"#
    private let INHERIT = #"{"original":"inherit"}"#

    private func c(_ id: String, _ props: [IRProperty],
                   _ kids: [IRComponent]? = nil) -> IRComponent {
        IRComponent(id: id, name: id, properties: props,
                    selectors: nil, media: nil, children: kids, slot: nil,
                    text: nil, pseudos: nil, meta: nil)
    }

    /// The winning BackgroundColor payload after the pass, as JSON text —
    /// compared as text because IRValue is not Equatable.
    private func bgText(_ comp: IRComponent) -> String? {
        guard let d = comp.properties.last(where: { $0.type == "BackgroundColor" })?.data
        else { return nil }
        return describe(d)
    }

    /// Stable textual form of an IRValue, enough to compare wire payloads.
    private func describe(_ value: IRValue) -> String {
        switch value {
        case .null: return "null"
        case .bool(let b): return "\(b)"
        case .int(let i): return "\(i)"
        case .double(let d): return "\(d)"
        case .string(let s): return "\"\(s)\""
        case .array(let a): return "[" + a.map(describe).joined(separator: ",") + "]"
        case .object(let o):
            return "{" + o.keys.sorted().map { "\"\($0)\":" + describe(o[$0]!) }
                .joined(separator: ",") + "}"
        }
    }

    // MARK: - The corpus document

    private func currentcolor002() -> IRComponent {
        c("wpt__css-color__currentcolor-002__1-251",
          [p("Color", RED), p("BackgroundColor", CURRENTCOLOR)],
          [c("currentcolor-002__1__0-252",
             [p("BackgroundColor", INHERIT)],
             [c("currentcolor-002__1__0__0-253",
                [p("Color", GREEN), p("BackgroundColor", INHERIT)])])])
    }

    func testInheritCopiesTheParentsComputedValueUnresolved() {
        let out = BackgroundColorInheritance.resolve(currentcolor002())
        let middle = out.children![0]
        let inner = middle.children![0]
        // §7.3.2 hands down the parent's COMPUTED value, and §6.4 says
        // `currentcolor` computes to ITSELF — so the keyword, not red, must
        // arrive at both descendants.
        XCTAssertEqual(bgText(middle), describe(v(CURRENTCOLOR)))
        XCTAssertEqual(bgText(inner), describe(v(CURRENTCOLOR)))
    }

    func testTheKeywordThenResolvesPerElement() {
        // The composition that makes currentcolor-002 render green: middle
        // resolves against the inherited red, inner against its own green.
        let out = BackgroundColorInheritance.resolve(currentcolor002())
        let middle = out.children![0]
        let inner = middle.children![0]

        // ComponentRenderer merges the inherited `color` under a component's
        // own declarations before extraction, so model middle's merged list.
        let middleCfg = ColorExtractor.extract(from: [p("Color", RED)] + middle.properties)
        XCTAssertEqual(middleCfg?.background, .srgb(r: 1, g: 0, b: 0, a: 1))

        let innerCfg = ColorExtractor.extract(from: inner.properties)
        XCTAssertEqual(innerCfg?.background,
                       .srgb(r: 0, g: 0.5019607843137255, b: 0, a: 1))
    }

    // MARK: - Structural contract

    func testInheritOnARootDropsToTheInitialValue() {
        // css-backgrounds-3 §2.2: the initial value is transparent, and "no
        // declaration" is how every applier already spells that.
        let out = BackgroundColorInheritance.resolve(c("root", [p("BackgroundColor", INHERIT)]))
        XCTAssertNil(bgText(out))
        XCTAssertTrue(out.properties.allSatisfy { $0.type != "BackgroundColor" })
    }

    func testAnEarlierColourDoesNotResurfaceBehindALosingInherit() {
        // Last-wins: `background-color: red; background-color: inherit` on a
        // root computes to transparent, NOT red.
        let out = BackgroundColorInheritance.resolve(
            c("root", [p("BackgroundColor", RED), p("BackgroundColor", INHERIT)]))
        XCTAssertTrue(out.properties.allSatisfy { $0.type != "BackgroundColor" })
    }

    func testAChildsOwnColourBeatsTheParents() {
        let out = BackgroundColorInheritance.resolve(
            c("root", [p("BackgroundColor", RED)],
              [c("kid", [p("BackgroundColor", GREEN)])]))
        XCTAssertEqual(bgText(out.children![0]), describe(v(GREEN)))
    }

    func testInheritChainsThroughAParentThatAlsoInherits() {
        let out = BackgroundColorInheritance.resolve(
            c("root", [p("BackgroundColor", RED)],
              [c("mid", [p("BackgroundColor", INHERIT)],
                 [c("leaf", [p("BackgroundColor", INHERIT)])])]))
        XCTAssertEqual(bgText(out.children![0].children![0]), describe(v(RED)))
    }

    func testLeavesKeepChildrenNilRatherThanEmpty() {
        // The composer's absent-vs-empty discipline: the renderer's
        // placeholder branch keys on `children == nil`.
        XCTAssertNil(BackgroundColorInheritance.resolve(c("root", [p("Color", RED)])).children)
    }

    func testKeywordMatchedCaseInsensitivelyInBothWireShapes() {
        // css-values-4 §4.1 — CSS keywords are ASCII case-insensitive.
        for form in [#"{"original":"INHERIT"}"#, #""Inherit""#] {
            let out = BackgroundColorInheritance.resolve(
                c("root", [p("BackgroundColor", RED)],
                  [c("kid", [p("BackgroundColor", form)])]))
            XCTAssertEqual(bgText(out.children![0]), describe(v(RED)), "form \(form)")
        }
    }

    func testTheOtherCssWideKeywordsAreNotClaimed() {
        // initial/unset/revert compute to the initial value on a
        // non-inherited property, which no declaration already produces — so
        // they pass through untouched rather than being rewritten.
        for kw in ["initial", "unset", "revert", "revert-layer"] {
            let wire = #"{"original":"\#(kw)"}"#
            let out = BackgroundColorInheritance.resolve(
                c("root", [p("BackgroundColor", RED)],
                  [c("kid", [p("BackgroundColor", wire)])]))
            XCTAssertEqual(bgText(out.children![0]), describe(v(wire)), "keyword \(kw)")
        }
    }

    func testAColorMixParentIsHandedDownUnresolved() {
        // color-mix-currentcolor-001: the child must receive the MIX, not the
        // parent's evaluation of it, so `currentColor` inside re-resolves
        // against the child's own green (Chrome 151: color(srgb 0 0.501961 0)).
        let mixWire = #"{"original":{"type":"color-mix","colorSpace":"srgb","color1":"currentColor","percent1":50,"color2":"green"}}"#
        let out = BackgroundColorInheritance.resolve(
            c("outer", [p("Color", RED), p("BackgroundColor", mixWire)],
              [c("inner", [p("Color", GREEN), p("BackgroundColor", INHERIT)])]))
        let inner = out.children![0]
        XCTAssertEqual(bgText(inner), describe(v(mixWire)))
        let cfg = ColorExtractor.extract(from: inner.properties)
        guard case .srgb(let r, let g, let b, _)? = cfg?.background else {
            return XCTFail("child's mix did not resolve")
        }
        XCTAssertEqual(r, 0, accuracy: 0.002)
        XCTAssertEqual(g, 0.501961, accuracy: 0.002)
        XCTAssertEqual(b, 0, accuracy: 0.002)
    }

    func testResolveAllMapsAForest() {
        // IRComposer returns a forest; the decode seam calls resolveAll.
        let roots = BackgroundColorInheritance.resolveAll([
            c("a", [p("BackgroundColor", RED)], [c("a1", [p("BackgroundColor", INHERIT)])]),
            c("b", [p("BackgroundColor", GREEN)], [c("b1", [p("BackgroundColor", INHERIT)])]),
        ])
        XCTAssertEqual(bgText(roots[0].children![0]), describe(v(RED)))
        XCTAssertEqual(bgText(roots[1].children![0]), describe(v(GREEN)))
    }
}

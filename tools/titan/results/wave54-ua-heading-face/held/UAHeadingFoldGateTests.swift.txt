//
//  UAHeadingFoldGateTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 54 (lane L5, U2-ios) — pins StyleEngine/typography/UAHeadingFoldGate
//  on the VERBATIM wave53-final per-test IR (UAHeadingFaceWire below),
//  replaying seam-2's cascade step (ComponentRenderer.mergedProperties(now:))
//  over the decoded, slot-composed document. PLAN §2 L5 "Unit pins":
//   * inset-005 / -006 / -014 hosts → the face applies (the fold engages);
//   * inset-011 → stands down (the fold refuses its diverging decoration
//     colours) — the control whose capture must stay byte-identical;
//   * a leaf heading → applies.
//  Mutation: return the old `hasChildren` gate → the inset-005 pin is red.
//  Plus brief §4 D's soundness pin: the fold verdict on the PRE-UA list
//  equals the verdict on the POST-UA list (`Folded` is Equatable).
//

import XCTest
@testable import StyleConverterRuntime

/// The replay of seam-2 over a verbatim document — what the pins read.
enum UAHeadingFaceReplay {

    /// A replay that cannot find its target fails loudly.
    enum ReplayError: Error { case missing(String) }

    /// One element at the seam: its own list and its PRE-UA merged list.
    struct Site {
        let component: IRComponent
        let own: [IRProperty]
        let mergedPreUA: [IRProperty]
    }

    /// The renderer's in-flow children (ComponentRenderer.inFlowChildren).
    static func inFlow(_ c: IRComponent) -> [IRComponent] {
        (c.children ?? []).filter { !ComponentRenderer.isOutOfFlow($0) }
    }

    /// Root → target path, through the production decode + composer.
    static func path(_ doc: String, _ id: String) throws -> [IRComponent] {
        let roots = try JSONDecoder().decode(IRDocument.self, from: Data(doc.utf8)).components
        func walk(_ node: IRComponent, _ trail: [IRComponent]) -> [IRComponent]? {
            let here = trail + [node]
            if node.id == id { return here }
            for child in node.children ?? [] { if let hit = walk(child, here) { return hit } }
            return nil
        }
        for root in roots { if let hit = walk(root, []) { return hit } }
        // A missing id is a broken pin, never a skip.
        XCTFail("component \(id) missing from the verbatim document")
        throw ReplayError.missing(id)
    }

    /// seam-2's PRE-UA site for `id`, replaying every ancestor's seam: the
    /// premises the replay rests on (no `All`, no buckets, no var() on the
    /// path — so AllReset, the bucket fold and DynamicValueResolver are the
    /// identity) are ASSERTED per node.
    static func site(_ doc: String, _ id: String) throws -> Site {
        var inherited: [IRProperty] = []
        let nodes = try path(doc, id)
        for (index, node) in nodes.enumerated() {
            XCTAssertFalse(node.properties.contains { $0.type == "All" }, "no All on the path")
            XCTAssertTrue((node.selectors ?? []).isEmpty && (node.media ?? []).isEmpty, "no buckets")
            let own = node.properties
            let preUA = ListStyleUaRule.apply(
                sourceTag: node.meta?.sourceTag, own: own,
                merged: InheritedText.merge(own: InheritedText.resolvingCurrentColorOnColor(own),
                                            inherited: inherited))
            if index == nodes.count - 1 { return Site(component: node, own: own, mergedPreUA: preUA) }
            // An ancestor publishes its POST-UA list's inheritable subset.
            inherited = InheritedText.inheritable(from: seam(Site(component: node, own: own, mergedPreUA: preUA)))
        }
        throw ReplayError.missing(id)  // unreachable: the path ends at the target
    }

    /// seam-2's output: the UA font step with the fold gate's verdict.
    static func seam(_ s: Site) -> [IRProperty] {
        UAElementFontRule.apply(
            sourceTag: s.component.meta?.sourceTag, own: s.own, merged: s.mergedPreUA,
            hasElementChildren: UAHeadingFoldGate.standsDown(
                component: s.component, inFlowChildren: inFlow(s.component),
                mergedPreUA: s.mergedPreUA))
    }

    /// The fold exactly as the gate (and the renderer) calls it.
    static func fold(_ c: IRComponent, _ properties: [IRProperty]) -> InlineRunFlow.Folded? {
        InlineRunFlow.fold(runs: c.meta?.runs, children: FlexboxApplier.sorted(inFlow(c)),
                           totalChildCount: c.children?.count ?? 0,
                           containerProperties: properties, containerLang: c.meta?.lang)
    }
}

final class UAHeadingFoldGateTests: XCTestCase {

    private let inset005 = (UAHeadingFaceWire.inset005, "text-decoration-inset-005__1__0-069")
    private let inset006 = (UAHeadingFaceWire.inset006, "text-decoration-inset-006__1__0-076")
    private let inset014 = (UAHeadingFaceWire.inset014, "text-decoration-inset-014__1__0-110")
    private let inset011 = (UAHeadingFaceWire.inset011, "text-decoration-inset-011__1__0-095")

    /// First FontSize px — StyleBuilder's own (first-wins) read.
    private func sizePx(_ list: [IRProperty]) -> Double? {
        list.first(where: { $0.type == "FontSize" }).flatMap { ValueExtractors.extractPx($0.data) }.map(Double.init)
    }

    func testInset005FoldedHostTakesTheFace() throws {
        let s = try UAHeadingFaceReplay.site(inset005.0, inset005.1)
        // The verbatim host hosts a <u> — the wave-40 gate stood down here.
        XCTAssertEqual(s.component.children?.isEmpty, false)
        // The fold engages on the merged list (inherited black admits the
        // u's black TextDecorationColor)…
        let flow = try XCTUnwrap(UAHeadingFaceReplay.fold(s.component, s.mergedPreUA))
        XCTAssertEqual(flow.text, "the ultra-quick brown fox")
        // …so the heading half applies: 2 × 16, bold.
        XCTAssertFalse(UAHeadingFoldGate.standsDown(component: s.component,
                                                    inFlowChildren: UAHeadingFaceReplay.inFlow(s.component),
                                                    mergedPreUA: s.mergedPreUA))
        let out = UAHeadingFaceReplay.seam(s)
        XCTAssertEqual(sizePx(out) ?? 0, 32, accuracy: 1e-9)
        XCTAssertNotNil(out.first(where: { $0.type == "FontWeight" }))
    }

    func testInset006FoldedHostTakesTheFace() throws {
        let s = try UAHeadingFaceReplay.site(inset006.0, inset006.1)
        XCTAssertNotNil(UAHeadingFaceReplay.fold(s.component, s.mergedPreUA))
        XCTAssertEqual(sizePx(UAHeadingFaceReplay.seam(s)) ?? 0, 32, accuracy: 1e-9)
    }

    func testInset014FoldedMonospaceHostTakesTwentySix() throws {
        let s = try UAHeadingFaceReplay.site(inset014.0, inset014.1)
        XCTAssertNotNil(UAHeadingFaceReplay.fold(s.component, s.mergedPreUA))
        // 2 × the 13px monospace fixed default — the ref's 15.58px `ch`.
        XCTAssertEqual(sizePx(UAHeadingFaceReplay.seam(s)) ?? 0, 26, accuracy: 1e-9)
    }

    func testInset011BailingHostStandsDown() throws {
        let s = try UAHeadingFaceReplay.site(inset011.0, inset011.1)
        // Blue / green decoration over black ink: the span ring refuses.
        XCTAssertNil(UAHeadingFaceReplay.fold(s.component, s.mergedPreUA))
        XCTAssertTrue(UAHeadingFoldGate.standsDown(component: s.component,
                                                   inFlowChildren: UAHeadingFaceReplay.inFlow(s.component),
                                                   mergedPreUA: s.mergedPreUA))
        // The control's list is untouched (types and order identical).
        XCTAssertEqual(UAHeadingFaceReplay.seam(s).map(\.type), s.mergedPreUA.map(\.type))
    }

    func testLeafHeadingApplies() throws {
        let s = try UAHeadingFaceReplay.site(UAHeadingFaceWire.blockInInline015Print, "block-in-inline-015-print__0__0-190")
        XCTAssertFalse(UAHeadingFoldGate.standsDown(component: s.component, inFlowChildren: [],
                                                    mergedPreUA: s.mergedPreUA))
        XCTAssertEqual(sizePx(UAHeadingFaceReplay.seam(s)) ?? 0, 32, accuracy: 1e-9)
    }

    func testNonHeadingWithChildrenKeepsTheOldFlag() throws {
        // inset-005's <u>: a runs host with sup/sub children, tag u.
        let s = try UAHeadingFaceReplay.site(inset005.0, "text-decoration-inset-005__1__0__0-070")
        XCTAssertTrue(UAHeadingFoldGate.standsDown(component: s.component,
                                                   inFlowChildren: UAHeadingFaceReplay.inFlow(s.component),
                                                   mergedPreUA: s.mergedPreUA))
    }

    /// Brief §4 D: fold admission ignores FontSize / FontWeight, so the
    /// verdict on the PRE-UA list equals the one on the face-forced list.
    func testFoldVerdictIsInvariantUnderTheUAFace() throws {
        for (doc, id) in [inset005, inset006, inset014, inset011] {
            let s = try UAHeadingFaceReplay.site(doc, id)
            let post = UAElementFontRule.apply(sourceTag: s.component.meta?.sourceTag, own: s.own,
                                               merged: s.mergedPreUA, hasElementChildren: false)
            XCTAssertEqual(UAHeadingFaceReplay.fold(s.component, s.mergedPreUA),
                           UAHeadingFaceReplay.fold(s.component, post), id)
        }
    }
}

/// The VERBATIM per-test IR (tools/titan/runs/wave53-final/…/per-test-ir;
/// byte-identical in wave54-open), generated — never hand-edited — by
/// tools/titan/results/wave54-ua-heading-face/gen-wire-constants.py --swift.
enum UAHeadingFaceWire {

    /// tools/titan/runs/wave53-final/sections/css-break/per-test-ir/wpt__css-break__block-in-inline-015-print.json — sha256 1470c4b909ce0053706c2769b92a35a6f837b7aa03dea96aa59e4725d1c0920a (VERBATIM bytes)
    static let blockInInline015Print = #"""
{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-break__block-in-inline-015-print__0-188","name":"wpt__css-break__block-in-inline-015-print__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}}],"meta":{"role":"body-root"}},{"id":"wpt__css-break__block-in-inline-015-print__1-189","name":"wpt__css-break__block-in-inline-015-print__1","properties":[{"type":"Display","data":"INLINE"}],"meta":{"role":"ws-after"}},{"id":"block-in-inline-015-print__0__0-190","name":"block-in-inline-015-print__0__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}}],"slot":{"parent":"wpt__css-break__block-in-inline-015-print__1-189"},"text":"one","meta":{"sourceTag":"h1"}},{"id":"wpt__css-break__block-in-inline-015-print__2-191","name":"wpt__css-break__block-in-inline-015-print__2","properties":[{"type":"Display","data":"INLINE"}],"meta":{"role":"ws-after"}},{"id":"block-in-inline-015-print__1__0-192","name":"block-in-inline-015-print__1__0","properties":[{"type":"BreakAfter","data":"PAGE"}],"slot":{"parent":"wpt__css-break__block-in-inline-015-print__2-191"}},{"id":"wpt__css-break__block-in-inline-015-print__3-193","name":"wpt__css-break__block-in-inline-015-print__3","properties":[{"type":"Display","data":"INLINE"}],"meta":{"role":"ws-after"}},{"id":"block-in-inline-015-print__2__0-194","name":"block-in-inline-015-print__2__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}}],"slot":{"parent":"wpt__css-break__block-in-inline-015-print__3-193"},"text":"two","meta":{"sourceTag":"h1"}},{"id":"wpt__css-break__block-in-inline-015-print__4-195","name":"wpt__css-break__block-in-inline-015-print__4","properties":[{"type":"Display","data":"INLINE"}],"meta":{"role":"ws-after"}},{"id":"block-in-inline-015-print__3__0-196","name":"block-in-inline-015-print__3__0","properties":[{"type":"BreakAfter","data":"PAGE"}],"slot":{"parent":"wpt__css-break__block-in-inline-015-print__4-195"}},{"id":"wpt__css-break__block-in-inline-015-print__5-197","name":"wpt__css-break__block-in-inline-015-print__5","properties":[{"type":"Display","data":"INLINE"}],"meta":{"role":"ws-after"}},{"id":"block-in-inline-015-print__4__0-198","name":"block-in-inline-015-print__4__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}}],"slot":{"parent":"wpt__css-break__block-in-inline-015-print__5-197"},"text":"three","meta":{"sourceTag":"h1"}},{"id":"wpt__css-break__block-in-inline-015-print__6-199","name":"wpt__css-break__block-in-inline-015-print__6","properties":[{"type":"Display","data":"INLINE"}],"meta":{"role":"ws-after"}},{"id":"block-in-inline-015-print__5__0-200","name":"block-in-inline-015-print__5__0","properties":[{"type":"BreakAfter","data":"PAGE"}],"slot":{"parent":"wpt__css-break__block-in-inline-015-print__6-199"}},{"id":"wpt__css-break__block-in-inline-015-print__7-201","name":"wpt__css-break__block-in-inline-015-print__7","properties":[{"type":"Display","data":"INLINE"}]},{"id":"block-in-inline-015-print__6__0-202","name":"block-in-inline-015-print__6__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}}],"slot":{"parent":"wpt__css-break__block-in-inline-015-print__7-201"},"text":"four","meta":{"sourceTag":"h1"}}]}
"""#

    /// tools/titan/runs/wave53-final/sections/css-text-decor/per-test-ir/wpt__css-text-decor__text-decoration-inset-001.json — sha256 57d3cf016e74984147add9a2cbbce9bd5284c5cbeb5e5d11ae930fc53cbd2c02 (VERBATIM bytes)
    static let inset001 = #"""
{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text-decor__text-decoration-inset-001__0-050","name":"wpt__css-text-decor__text-decoration-inset-001__0","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":"white"}},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"meta":{"role":"body-root"}},{"id":"wpt__css-text-decor__text-decoration-inset-001__1-051","name":"wpt__css-text-decor__text-decoration-inset-001__1","properties":[{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"text":"The underline for \"brown\" should be offset by 10px to the right:","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__css-text-decor__text-decoration-inset-001__2-052","name":"wpt__css-text-decor__text-decoration-inset-001__2","properties":[{"type":"Position","data":"RELATIVE"},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}]},{"id":"text-decoration-inset-001__1__0-053","name":"text-decoration-inset-001__1__0","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"FontSize","data":{"px":32,"original":{"type":"length","px":32}}},{"type":"FontWeight","data":{"weight":700,"original":"bold"}},{"type":"MarginTop","data":{"px":21.44}},{"type":"MarginBottom","data":{"px":21.44}}],"slot":{"parent":"wpt__css-text-decor__text-decoration-inset-001__2-052"},"text":"the quick brown fox","meta":{"sourceTag":"h1","decorations":[{"line":"underline","color":"black"}]}}]}
"""#

    /// tools/titan/runs/wave53-final/sections/css-text-decor/per-test-ir/wpt__css-text-decor__text-decoration-inset-005.json — sha256 13530ccf4b3e1aa25f71ea121deb5d9f8f0609470a7c2b6c496dd36bc42ec9e6 (VERBATIM bytes)
    static let inset005 = #"""
{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text-decor__text-decoration-inset-005__0-066","name":"wpt__css-text-decor__text-decoration-inset-005__0","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":"white"}},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"meta":{"role":"body-root"}},{"id":"wpt__css-text-decor__text-decoration-inset-005__1-067","name":"wpt__css-text-decor__text-decoration-inset-005__1","properties":[{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"text":"The underline for \"ultra-quick brown\" should be extended by .25em at each end; super- and sub-scripts should be unaffected:","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__css-text-decor__text-decoration-inset-005__2-068","name":"wpt__css-text-decor__text-decoration-inset-005__2","properties":[{"type":"Position","data":"RELATIVE"},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}]},{"id":"text-decoration-inset-005__1__0-069","name":"text-decoration-inset-005__1__0","properties":[{"type":"Position","data":"ABSOLUTE"}],"slot":{"parent":"wpt__css-text-decor__text-decoration-inset-005__2-068"},"text":"the fox","meta":{"sourceTag":"h1","runs":[{"text":"the "},{"child":"text-decoration-inset-005__1__0__0"},{"text":" fox"}]}},{"id":"text-decoration-inset-005__1__0__0-070","name":"text-decoration-inset-005__1__0__0","properties":[{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"slot":{"parent":"text-decoration-inset-005__1__0-069"},"text":"ultra- bn","meta":{"sourceTag":"u","runs":[{"text":"ultra-"},{"child":"text-decoration-inset-005__1__0__0__0"},{"text":" b"},{"child":"text-decoration-inset-005__1__0__0__1"},{"text":"n"}]}},{"id":"text-decoration-inset-005__1__0__0__0-071","name":"text-decoration-inset-005__1__0__0__0","properties":[],"slot":{"parent":"text-decoration-inset-005__1__0__0-070"},"text":"quick","meta":{"sourceTag":"sup","role":"ws-after"}},{"id":"text-decoration-inset-005__1__0__0__1-072","name":"text-decoration-inset-005__1__0__0__1","properties":[],"slot":{"parent":"text-decoration-inset-005__1__0__0-070"},"text":"row","meta":{"sourceTag":"sub"}}]}
"""#

    /// tools/titan/runs/wave53-final/sections/css-text-decor/per-test-ir/wpt__css-text-decor__text-decoration-inset-006.json — sha256 802e7ac120bb32d6a7910efe19a2eb215b4fa39a3e66a0755e310ec1b9a88865 (VERBATIM bytes)
    static let inset006 = #"""
{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text-decor__text-decoration-inset-006__0-073","name":"wpt__css-text-decor__text-decoration-inset-006__0","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":"white"}},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"meta":{"role":"body-root"}},{"id":"wpt__css-text-decor__text-decoration-inset-006__1-074","name":"wpt__css-text-decor__text-decoration-inset-006__1","properties":[{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"text":"The underline for \"ultra-quick brown\" should be trimmed by .25em at each end; super- and sub-scripts should be unaffected:","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__css-text-decor__text-decoration-inset-006__2-075","name":"wpt__css-text-decor__text-decoration-inset-006__2","properties":[{"type":"Position","data":"RELATIVE"},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}]},{"id":"text-decoration-inset-006__1__0-076","name":"text-decoration-inset-006__1__0","properties":[{"type":"Position","data":"ABSOLUTE"}],"slot":{"parent":"wpt__css-text-decor__text-decoration-inset-006__2-075"},"text":"the fox","meta":{"sourceTag":"h1","runs":[{"text":"the "},{"child":"text-decoration-inset-006__1__0__0"},{"text":" fox"}]}},{"id":"text-decoration-inset-006__1__0__0-077","name":"text-decoration-inset-006__1__0__0","properties":[{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"slot":{"parent":"text-decoration-inset-006__1__0-076"},"text":"ultra- bwn","meta":{"sourceTag":"u","runs":[{"text":"ultra-"},{"child":"text-decoration-inset-006__1__0__0__0"},{"text":" b"},{"child":"text-decoration-inset-006__1__0__0__1"},{"text":"wn"}]}},{"id":"text-decoration-inset-006__1__0__0__0-078","name":"text-decoration-inset-006__1__0__0__0","properties":[],"slot":{"parent":"text-decoration-inset-006__1__0__0-077"},"text":"quick","meta":{"sourceTag":"sup","role":"ws-after"}},{"id":"text-decoration-inset-006__1__0__0__1-079","name":"text-decoration-inset-006__1__0__0__1","properties":[],"slot":{"parent":"text-decoration-inset-006__1__0__0-077"},"text":"ro","meta":{"sourceTag":"sub"}}]}
"""#

    /// tools/titan/runs/wave53-final/sections/css-text-decor/per-test-ir/wpt__css-text-decor__text-decoration-inset-011.json — sha256 f5edf74789d4b461d6ccd9019fb5c0738afea5f71e0b2f5cc5804c2164548270 (VERBATIM bytes)
    static let inset011 = #"""
{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text-decor__text-decoration-inset-011__0-092","name":"wpt__css-text-decor__text-decoration-inset-011__0","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":"white"}},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"FontFamily","data":["times new roman","serif"]}],"meta":{"role":"body-root"}},{"id":"wpt__css-text-decor__text-decoration-inset-011__1-093","name":"wpt__css-text-decor__text-decoration-inset-011__1","properties":[{"type":"FontFamily","data":["times new roman","serif"]},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"text":"The underline of the first phrase should be trimmed by 10px at both ends; the underline of the second should be offset by 10px to the left at both start and end:","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__css-text-decor__text-decoration-inset-011__2-094","name":"wpt__css-text-decor__text-decoration-inset-011__2","properties":[{"type":"Position","data":"RELATIVE"},{"type":"MarginLeft","data":{"px":10}},{"type":"FontFamily","data":["times new roman","serif"]},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"meta":{"lang":"ar"}},{"id":"text-decoration-inset-011__1__0-095","name":"text-decoration-inset-011__1__0","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"LineHeight","data":{"multiplier":2,"original":{"type":"number","value":2}}},{"type":"Width","data":{"type":"length","original":{"v":10,"u":"EM"}}},{"type":"Direction","data":"RTL"}],"slot":{"parent":"wpt__css-text-decor__text-decoration-inset-011__2-094"},"text":"—","meta":{"sourceTag":"h1","lang":"ar","runs":[{"child":"text-decoration-inset-011__1__0__0"},{"text":" —"},{"child":"text-decoration-inset-011__1__0__1"},{"text":" "},{"child":"text-decoration-inset-011__1__0__2"}]}},{"id":"text-decoration-inset-011__1__0__0-096","name":"text-decoration-inset-011__1__0__0","properties":[{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}},{"type":"TextUnderlineOffset","data":{"type":"length","px":20}}],"slot":{"parent":"text-decoration-inset-011__1__0-095"},"text":"صِف خَلقَ خَودِ كَمِثلِ الشَمسِ إِذ بَزَغَت","meta":{"sourceTag":"u","role":"ws-after","lang":"ar"}},{"id":"text-decoration-inset-011__1__0__1-097","name":"text-decoration-inset-011__1__0__1","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":0}}],"slot":{"parent":"text-decoration-inset-011__1__0-095"},"meta":{"sourceTag":"br","role":"line-break","lang":"ar"}},{"id":"text-decoration-inset-011__1__0__2-098","name":"text-decoration-inset-011__1__0__2","properties":[{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"TextUnderlineOffset","data":{"type":"length","px":20}}],"slot":{"parent":"text-decoration-inset-011__1__0-095"},"text":"يَحظى الضَجيعُ بِها نَجلاءَ مِعطارِ","meta":{"sourceTag":"u","lang":"ar"}}]}
"""#

    /// tools/titan/runs/wave53-final/sections/css-text-decor/per-test-ir/wpt__css-text-decor__text-decoration-inset-014.json — sha256 1445c4233d82b65ec96fbeed0ce515dc722397e3e537c71a943aceb7c17f4987 (VERBATIM bytes)
    static let inset014 = #"""
{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text-decor__text-decoration-inset-014__0-107","name":"wpt__css-text-decor__text-decoration-inset-014__0","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":"white"}},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"meta":{"role":"body-root"}},{"id":"wpt__css-text-decor__text-decoration-inset-014__1-108","name":"wpt__css-text-decor__text-decoration-inset-014__1","properties":[{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"text":"The underline for \"ultra-quick brown\" should be extended by 0.5ch at each end of each fragment:","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__css-text-decor__text-decoration-inset-014__2-109","name":"wpt__css-text-decor__text-decoration-inset-014__2","properties":[{"type":"Position","data":"RELATIVE"},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}]},{"id":"text-decoration-inset-014__1__0-110","name":"text-decoration-inset-014__1__0","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"FontFamily","data":["monospace"]},{"type":"Width","data":{"type":"length","original":{"v":16,"u":"CH"}}}],"slot":{"parent":"wpt__css-text-decor__text-decoration-inset-014__2-109"},"text":"the fox","meta":{"sourceTag":"h1","runs":[{"text":"the "},{"child":"text-decoration-inset-014__1__0__0"},{"text":" fox"}]}},{"id":"text-decoration-inset-014__1__0__0-111","name":"text-decoration-inset-014__1__0__0","properties":[{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BoxDecorationBreak","data":"CLONE"}],"slot":{"parent":"text-decoration-inset-014__1__0-110"},"text":"ultra-quick brown","meta":{"sourceTag":"u"}}]}
"""#

    /// tools/titan/runs/wave53-final/sections/css-text-decor/per-test-ir/wpt__css-text-decor__text-decoration-color.json — sha256 f815b121e532dd84948f6d482d1698dfcfaa7eea5c66fdd93820fb870b544861 (VERBATIM bytes)
    static let textDecorationColor = #"""
{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text-decor__text-decoration-color__0-021","name":"wpt__css-text-decor__text-decoration-color__0","properties":[],"text":"Each line of this test should match its text decoration color description:","meta":{"sourceTag":"h3","role":"ws-after"}},{"id":"wpt__css-text-decor__text-decoration-color__1-022","name":"wpt__css-text-decor__text-decoration-color__1","properties":[{"type":"TextDecorationLine","data":["UNDERLINE"]},{"type":"Color","data":{"srgb":{"r":0.5019607843137255,"g":0.5019607843137255,"b":0.5019607843137255},"original":"gray"}},{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}}],"text":"Gray text with blue underline"},{"id":"wpt__css-text-decor__text-decoration-color__2-023","name":"wpt__css-text-decor__text-decoration-color__2","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":20}}],"meta":{"sourceTag":"br","role":"line-break"}},{"id":"wpt__css-text-decor__text-decoration-color__3-024","name":"wpt__css-text-decor__text-decoration-color__3","properties":[{"type":"TextDecorationLine","data":["OVERLINE"]},{"type":"Color","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"text":"Green text with black overline"},{"id":"wpt__css-text-decor__text-decoration-color__4-025","name":"wpt__css-text-decor__text-decoration-color__4","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":20}}],"meta":{"sourceTag":"br","role":"line-break"}},{"id":"wpt__css-text-decor__text-decoration-color__5-026","name":"wpt__css-text-decor__text-decoration-color__5","properties":[{"type":"TextDecorationLine","data":["LINE_THROUGH"]},{"type":"TextDecorationColor","data":{"srgb":{"r":1,"g":0.8431372549019608,"b":0},"original":"gold"}}],"text":"Black text with gold line-through"},{"id":"wpt__css-text-decor__text-decoration-color__6-027","name":"wpt__css-text-decor__text-decoration-color__6","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":20}}],"meta":{"sourceTag":"br","role":"line-break"}},{"id":"wpt__css-text-decor__text-decoration-color__7-028","name":"wpt__css-text-decor__text-decoration-color__7","properties":[{"type":"TextDecorationLine","data":["UNDERLINE"]},{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}}],"text":"Black text with blue underline, gray overline and green line-through","meta":{"decorations":[{"line":"underline","color":"blue"},{"line":"overline","color":"gray"},{"line":"line-through","color":"green"}]}},{"id":"wpt__css-text-decor__text-decoration-color__8-029","name":"wpt__css-text-decor__text-decoration-color__8","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":20}}],"meta":{"sourceTag":"br","role":"line-break"}},{"id":"wpt__css-text-decor__text-decoration-color__9-030","name":"wpt__css-text-decor__text-decoration-color__9","properties":[]},{"id":"text-decoration-color__9__0-031","name":"text-decoration-color__9__0","properties":[{"type":"TextDecorationLine","data":["LINE_THROUGH"]},{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"wpt__css-text-decor__text-decoration-color__9-030"},"meta":{"sourceTag":"span"}},{"id":"text-decoration-color__9__0__0-032","name":"text-decoration-color__9__0__0","properties":[{"type":"TextDecorationLine","data":["OVERLINE"]},{"type":"TextDecorationColor","data":{"srgb":{"r":0.5019607843137255,"g":0.5019607843137255,"b":0.5019607843137255},"original":"gray"}}],"slot":{"parent":"text-decoration-color__9__0-031"},"text":"subscript text","meta":{"sourceTag":"sub","role":"ws-after"}},{"id":"text-decoration-color__9__0__1-033","name":"text-decoration-color__9__0__1","properties":[{"type":"TextDecorationLine","data":["UNDERLINE"]},{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}}],"slot":{"parent":"text-decoration-color__9__0-031"},"text":"superscript text","meta":{"sourceTag":"sup"}},{"id":"wpt__css-text-decor__text-decoration-color__10-034","name":"wpt__css-text-decor__text-decoration-color__10","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":20}}],"meta":{"sourceTag":"br","role":"line-break"}},{"id":"wpt__css-text-decor__text-decoration-color__11-035","name":"wpt__css-text-decor__text-decoration-color__11","properties":[{"type":"TextDecorationLine","data":["UNDERLINE"]},{"type":"Generic","data":{"propertyName":"-webkit-text-fill-color","rawValue":"transparent","_unmapped":true}},{"type":"Generic","data":{"propertyName":"-webkit-text-stroke-width","rawValue":"1px","_unmapped":true}},{"type":"Generic","data":{"propertyName":"-webkit-text-stroke-color","rawValue":"black","_unmapped":true}},{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"text":"Transparent fill with black stroke text and green underline"},{"id":"wpt__css-text-decor__text-decoration-color__12-036","name":"wpt__css-text-decor__text-decoration-color__12","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":20}}],"meta":{"sourceTag":"br","role":"line-break"}},{"id":"wpt__css-text-decor__text-decoration-color__13-037","name":"wpt__css-text-decor__text-decoration-color__13","properties":[{"type":"TextDecorationLine","data":["UNDERLINE"]},{"type":"Generic","data":{"propertyName":"-webkit-text-stroke-width","rawValue":"thin","_unmapped":true}},{"type":"Generic","data":{"propertyName":"-webkit-text-stroke-color","rawValue":"LinkText","_unmapped":true}}],"text":"LinkText stroke and underline with black fill"},{"id":"wpt__css-text-decor__text-decoration-color__14-038","name":"wpt__css-text-decor__text-decoration-color__14","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":20}}],"meta":{"sourceTag":"br","role":"line-break"}}]}
"""#

}

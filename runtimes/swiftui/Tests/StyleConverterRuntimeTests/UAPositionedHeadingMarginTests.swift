//
//  UAPositionedHeadingMarginTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 55 (lane L4, U1-ios) — pins StyleEngine/spacing/UAPositionedHeadingMargin
//  on the VERBATIM per-test IR of wave54-final (= wave55-open), spliced into
//  UAPositionedHeadingWire below by tools/titan/results/wave55-ua-heading-margin/
//  gen-wire55.py (never hand-edited; `--check-swift` re-verifies), replaying the
//  seam's cascade step root → target over the decoded, slot-composed document:
//   * inset-005 / -006 / -011 <h1> → 21.44 — mutation: the painted basis (10.72);
//   * inset-014 → 17.42 — mutations: em base 16; the 16px-root table (21);
//   * inset-001 (declares both edges) → unchanged — mutation: drop the per-edge guard;
//   * capture off → unchanged — mutation: ignore `enabled`;
//   * the in-flow text-decoration-color h3 → unchanged — mutation: drop the out-of-flow test;
//   * s-11-1-1b-005's abspos <p> → unchanged, logged — mutation: widen past headings;
//   * RELATIVE / STICKY, a vertical writing mode, unresolvable sizes → unchanged;
//   * the fold verdicts of the four hosts are unchanged by the step.
//  The seam CALL SITE is pinned by UAPositionedHeadingMarginSeamWiringTests,
//  which ships inside seam-2.patch with the hunk it reads (rule 2b).
//

import XCTest
@testable import StyleConverterRuntime

/// The seam's cascade step replayed over a verbatim document (U1-ios's own
/// replay: UAHeadingFaceReplay is U2-ios's and lands after this unit).
enum UAPositionedHeadingReplay {

    /// A replay that cannot find its target fails loudly.
    enum ReplayError: Error { case missing(String) }

    /// One element at the seam: its own list and its PRE-UA merged list.
    struct Site {
        let component: IRComponent
        let own: [IRProperty]
        let preUA: [IRProperty]
    }

    /// Root → target path through the production decode (slot-composed).
    static func path(_ doc: String, _ id: String) throws -> [IRComponent] {
        let roots = try JSONDecoder().decode(IRDocument.self, from: Data(doc.utf8)).components
        // Depth-first walk keeping the ancestry.
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

    /// The PRE-UA site for `id`: every ancestor's merge → ListStyleUaRule →
    /// font step → margin step → inheritable subset. The premises (no `All`,
    /// no buckets on the path; untagged ancestors, so the font gate is moot)
    /// are ASSERTED per node.
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
            if index == nodes.count - 1 { return Site(component: node, own: own, preUA: preUA) }
            // Ancestors on every pinned path carry no heading tag.
            XCTAssertFalse(UAElementFontRule.isBold(forTag: node.meta?.sourceTag), "heading ancestor")
            let font = UAElementFontRule.apply(sourceTag: node.meta?.sourceTag, own: own, merged: preUA,
                                               hasElementChildren: node.children?.isEmpty == false)
            let post = UAPositionedHeadingMargin.apply(sourceTag: node.meta?.sourceTag, own: own, preUA: preUA,
                                                       merged: font, enabled: true)
            inherited = InheritedText.inheritable(from: post)
        }
        throw ReplayError.missing(id)  // unreachable: the path ends at the target
    }
}

final class UAPositionedHeadingMarginTests: XCTestCase {

    private let inset005 = (UAPositionedHeadingWire.inset005, "text-decoration-inset-005__1__0-069")
    private let inset006 = (UAPositionedHeadingWire.inset006, "text-decoration-inset-006__1__0-076")
    private let inset011 = (UAPositionedHeadingWire.inset011, "text-decoration-inset-011__1__0-095")
    private let inset014 = (UAPositionedHeadingWire.inset014, "text-decoration-inset-014__1__0-110")

    /// The step on a site with the UA face HELD or APPLIED, capture on.
    private func step(_ s: UAPositionedHeadingReplay.Site, faceHeld: Bool, enabled: Bool = true) -> [IRProperty] {
        let font = UAElementFontRule.apply(sourceTag: s.component.meta?.sourceTag, own: s.own, merged: s.preUA,
                                           hasElementChildren: faceHeld)
        return UAPositionedHeadingMargin.apply(sourceTag: s.component.meta?.sourceTag, own: s.own,
                                               preUA: s.preUA, merged: font, enabled: enabled)
    }

    /// The single `type` entry's px (XCTFail on zero or several).
    private func px(_ list: [IRProperty], _ type: String) -> Double {
        let hits = list.filter { $0.type == type }
        XCTAssertEqual(hits.count, 1, type)
        return hits.first.flatMap { ValueExtractors.extractPx($0.data) }.map(Double.init) ?? .nan
    }

    /// A synthetic list from (type, value) pairs.
    private func props(_ p: [(String, IRValue)]) -> [IRProperty] { p.map { IRProperty(type: $0.0, data: $0.1) } }

    func testInset005_006_011TakeTwentyOnePointFourFourHeldOrApplied() throws {
        for (doc, id) in [inset005, inset006, inset011] {
            let s = try UAPositionedHeadingReplay.site(doc, id)
            // The verbatim heading declares no margin and no size.
            XCTAssertFalse(s.own.contains { $0.type.hasPrefix("Margin") || $0.type == "FontSize" })
            for held in [true, false] {
                let out = step(s, faceHeld: held)
                // .67 × 2 × 16 on both edges, whether or not the face is painted.
                XCTAssertEqual(px(out, "MarginTop"), 21.44, accuracy: 1e-6, "\(id) held=\(held)")
                XCTAssertEqual(px(out, "MarginBottom"), 21.44, accuracy: 1e-6)
            }
        }
    }

    func testInset014MonospaceTakesSeventeenPointFourTwo() throws {
        let out = step(try UAPositionedHeadingReplay.site(inset014.0, inset014.1), faceHeld: true)
        // 2em × 13 = 26 → 17.42, not 21.44 (base 16) and not 21 (the root table).
        XCTAssertEqual(px(out, "MarginTop"), 17.42, accuracy: 1e-6)
        XCTAssertEqual(px(out, "MarginBottom"), 17.42, accuracy: 1e-6)
    }

    func testInset001DeclaresBothEdgesAndIsUnchanged() throws {
        let s = try UAPositionedHeadingReplay.site(UAPositionedHeadingWire.inset001, "text-decoration-inset-001__1__0-053")
        let font = UAElementFontRule.apply(sourceTag: s.component.meta?.sourceTag, own: s.own, merged: s.preUA)
        // Author (the UA_H1_PROPS bake) wins per edge: identity.
        XCTAssertEqual(UAPositionedHeadingMargin.apply(sourceTag: "h1", own: s.own, preUA: s.preUA, merged: font, enabled: true), font)
    }

    func testOnlyMarginTopDeclaredTakesTheUABottomOnly() {
        let own = props([("Position", .string("ABSOLUTE")), ("MarginTop", .object(["px": .double(5)]))])
        let out = UAPositionedHeadingMargin.apply(sourceTag: "h1", own: own, preUA: own, merged: own, enabled: true)
        // The author's top kept verbatim; one UA bottom appended.
        XCTAssertEqual(px(out, "MarginTop"), 5, accuracy: 1e-9)
        XCTAssertEqual(px(out, "MarginBottom"), 21.44, accuracy: 1e-6)
    }

    func testCaptureOffIsUnchanged() throws {
        let s = try UAPositionedHeadingReplay.site(inset005.0, inset005.1)
        // The same step with the flag down: the font step's output, untouched.
        let font = UAElementFontRule.apply(sourceTag: "h1", own: s.own, merged: s.preUA, hasElementChildren: true)
        XCTAssertEqual(step(s, faceHeld: true, enabled: false), font)
    }

    func testInFlowHeadingsAreUnchanged() throws {
        // The verbatim text-decoration-color h3 declares nothing: only the out-of-flow test keeps it out.
        let h3 = try UAPositionedHeadingReplay.site(UAPositionedHeadingWire.textDecorationColor,
                                                    "wpt__css-text-decor__text-decoration-color__0-021")
        XCTAssertTrue(h3.own.isEmpty)
        XCTAssertEqual(step(h3, faceHeld: false), UAElementFontRule.apply(sourceTag: "h3", own: [], merged: h3.preUA))
        // The block-in-inline leaf h1s (in flow, margin 0 declared) too.
        let leaf = try UAPositionedHeadingReplay.site(UAPositionedHeadingWire.blockInInline015Print, "block-in-inline-015-print__0__0-190")
        XCTAssertEqual(step(leaf, faceHeld: false), UAElementFontRule.apply(sourceTag: "h1", own: leaf.own, merged: leaf.preUA))
    }

    func testRelativeStickyStaticAreUnchangedAndFixedFires() {
        for position in ["RELATIVE", "STICKY", "STATIC"] {
            let own = props([("Position", .string(position))])
            XCTAssertEqual(UAPositionedHeadingMargin.apply(sourceTag: "h2", own: own, preUA: own, merged: own, enabled: true), own, position)
        }
        // `fixed` is out of flow too (css-position-3 §2.1): .83 × 1.5 × 16.
        let fixed = props([("Position", .string("FIXED"))])
        XCTAssertEqual(px(UAPositionedHeadingMargin.apply(sourceTag: "h2", own: fixed, preUA: fixed, merged: fixed, enabled: true), "MarginTop"), 19.92, accuracy: 1e-6)
    }

    func testS1111b005AbsposParagraphIsOutOfScopeAndLogged() throws {
        let s = try UAPositionedHeadingReplay.site(UAPositionedHeadingWire.s11_1_1b_005, "css21-errata__s-11-1-1b-005__0-140")
        XCTAssertEqual(s.component.meta?.sourceTag, "p")
        XCTAssertEqual(UAPositionedHeadingMargin.apply(sourceTag: "p", own: s.own, preUA: s.preUA, merged: s.preUA, enabled: true), s.preUA)
        XCTAssertTrue(UAPositionedHeadingMargin.hasLogged("tag:p"))
    }

    func testVerticalWritingModeIsUnchangedAndLogged() {
        let own = props([("Position", .string("ABSOLUTE"))])
        // WritingMode inherits: it arrives through the merged list.
        let merged = own + props([("WritingMode", .string("VERTICAL_RL"))])
        XCTAssertEqual(UAPositionedHeadingMargin.apply(sourceTag: "h1", own: own, preUA: merged, merged: merged, enabled: true), merged)
        XCTAssertTrue(UAPositionedHeadingMargin.hasLogged("vertical"))
    }

    func testAuthorSizesAbsolutePxIsTheBasisRelativeAndShorthandAreLogged() {
        // 40px declared: .67 × 40 = 26.8 (B3).
        let px40 = props([("Position", .string("ABSOLUTE")), ("FontSize", .object(["px": .double(40)]))])
        XCTAssertEqual(px(UAPositionedHeadingMargin.apply(sourceTag: "h1", own: px40, preUA: px40, merged: px40, enabled: true), "MarginTop"), 26.8, accuracy: 1e-6)
        // 2em of an unknown parent, and the unexpanded `font`: unchanged, logged.
        let em = props([("Position", .string("ABSOLUTE")),
                        ("FontSize", .object(["original": .object(["type": .string("length"),
                                                                   "original": .object(["v": .double(2), "u": .string("EM")])])]))])
        XCTAssertEqual(UAPositionedHeadingMargin.apply(sourceTag: "h3", own: em, preUA: em, merged: em, enabled: true), em)
        XCTAssertTrue(UAPositionedHeadingMargin.hasLogged("size:h3"))
        let font = props([("Position", .string("ABSOLUTE")), ("Font", .string("bold 20px serif"))])
        XCTAssertEqual(UAPositionedHeadingMargin.apply(sourceTag: "h4", own: font, preUA: font, merged: font, enabled: true), font)
        XCTAssertTrue(UAPositionedHeadingMargin.hasLogged("size:h4"))
    }

    func testFoldVerdictsAreUnchangedByTheMarginStep() throws {
        for (doc, id) in [inset005, inset006, inset014, inset011] {
            let s = try UAPositionedHeadingReplay.site(doc, id)
            let inFlow = (s.component.children ?? []).filter { !ComponentRenderer.isOutOfFlow($0) }
            // The fold exactly as the renderer calls it, before and after the step.
            func fold(_ p: [IRProperty]) -> InlineRunFlow.Folded? {
                InlineRunFlow.fold(runs: s.component.meta?.runs, children: FlexboxApplier.sorted(inFlow),
                                   totalChildCount: s.component.children?.count ?? 0,
                                   containerProperties: p, containerLang: s.component.meta?.lang)
            }
            XCTAssertEqual(fold(s.preUA), fold(step(s, faceHeld: true)), id)
            // 005 / 006 / 014 fold, 011 refuses (its diverging decoration colours).
            XCTAssertEqual(fold(s.preUA) != nil, id != inset011.1, id)
        }
    }

    func testUntaggedOrMarginlessTagsAreUnchanged() {
        let own = props([("Position", .string("ABSOLUTE"))])
        // div / span / u / no tag: the UA sheet declares no block margin.
        for tag in [nil, "div", "span", "u"] as [String?] {
            XCTAssertEqual(UAPositionedHeadingMargin.apply(sourceTag: tag, own: own, preUA: own, merged: own, enabled: true), own)
        }
    }
}

/// The VERBATIM per-test IR (tools/titan/runs/wave54-final/…/per-test-ir), generated —
/// never hand-edited — by tools/titan/results/wave55-ua-heading-margin/gen-wire55.py.
enum UAPositionedHeadingWire {
    // ── BEGIN GENERATED WIRE (gen-wire55.py)
    /// tools/titan/runs/wave54-final/sections/css-break/per-test-ir/wpt__css-break__block-in-inline-015-print.json — sha256 1470c4b909ce0053706c2769b92a35a6f837b7aa03dea96aa59e4725d1c0920a (VERBATIM bytes)
    static let blockInInline015Print = #"""
{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-break__block-in-inline-015-print__0-188","name":"wpt__css-break__block-in-inline-015-print__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}}],"meta":{"role":"body-root"}},{"id":"wpt__css-break__block-in-inline-015-print__1-189","name":"wpt__css-break__block-in-inline-015-print__1","properties":[{"type":"Display","data":"INLINE"}],"meta":{"role":"ws-after"}},{"id":"block-in-inline-015-print__0__0-190","name":"block-in-inline-015-print__0__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}}],"slot":{"parent":"wpt__css-break__block-in-inline-015-print__1-189"},"text":"one","meta":{"sourceTag":"h1"}},{"id":"wpt__css-break__block-in-inline-015-print__2-191","name":"wpt__css-break__block-in-inline-015-print__2","properties":[{"type":"Display","data":"INLINE"}],"meta":{"role":"ws-after"}},{"id":"block-in-inline-015-print__1__0-192","name":"block-in-inline-015-print__1__0","properties":[{"type":"BreakAfter","data":"PAGE"}],"slot":{"parent":"wpt__css-break__block-in-inline-015-print__2-191"}},{"id":"wpt__css-break__block-in-inline-015-print__3-193","name":"wpt__css-break__block-in-inline-015-print__3","properties":[{"type":"Display","data":"INLINE"}],"meta":{"role":"ws-after"}},{"id":"block-in-inline-015-print__2__0-194","name":"block-in-inline-015-print__2__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}}],"slot":{"parent":"wpt__css-break__block-in-inline-015-print__3-193"},"text":"two","meta":{"sourceTag":"h1"}},{"id":"wpt__css-break__block-in-inline-015-print__4-195","name":"wpt__css-break__block-in-inline-015-print__4","properties":[{"type":"Display","data":"INLINE"}],"meta":{"role":"ws-after"}},{"id":"block-in-inline-015-print__3__0-196","name":"block-in-inline-015-print__3__0","properties":[{"type":"BreakAfter","data":"PAGE"}],"slot":{"parent":"wpt__css-break__block-in-inline-015-print__4-195"}},{"id":"wpt__css-break__block-in-inline-015-print__5-197","name":"wpt__css-break__block-in-inline-015-print__5","properties":[{"type":"Display","data":"INLINE"}],"meta":{"role":"ws-after"}},{"id":"block-in-inline-015-print__4__0-198","name":"block-in-inline-015-print__4__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}}],"slot":{"parent":"wpt__css-break__block-in-inline-015-print__5-197"},"text":"three","meta":{"sourceTag":"h1"}},{"id":"wpt__css-break__block-in-inline-015-print__6-199","name":"wpt__css-break__block-in-inline-015-print__6","properties":[{"type":"Display","data":"INLINE"}],"meta":{"role":"ws-after"}},{"id":"block-in-inline-015-print__5__0-200","name":"block-in-inline-015-print__5__0","properties":[{"type":"BreakAfter","data":"PAGE"}],"slot":{"parent":"wpt__css-break__block-in-inline-015-print__6-199"}},{"id":"wpt__css-break__block-in-inline-015-print__7-201","name":"wpt__css-break__block-in-inline-015-print__7","properties":[{"type":"Display","data":"INLINE"}]},{"id":"block-in-inline-015-print__6__0-202","name":"block-in-inline-015-print__6__0","properties":[{"type":"MarginTop","data":{"px":0}},{"type":"MarginRight","data":{"px":0}},{"type":"MarginBottom","data":{"px":0}},{"type":"MarginLeft","data":{"px":0}}],"slot":{"parent":"wpt__css-break__block-in-inline-015-print__7-201"},"text":"four","meta":{"sourceTag":"h1"}}]}
"""#

    /// tools/titan/runs/wave54-final/sections/css-text-decor/per-test-ir/wpt__css-text-decor__text-decoration-inset-001.json — sha256 57d3cf016e74984147add9a2cbbce9bd5284c5cbeb5e5d11ae930fc53cbd2c02 (VERBATIM bytes)
    static let inset001 = #"""
{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text-decor__text-decoration-inset-001__0-050","name":"wpt__css-text-decor__text-decoration-inset-001__0","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":"white"}},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"meta":{"role":"body-root"}},{"id":"wpt__css-text-decor__text-decoration-inset-001__1-051","name":"wpt__css-text-decor__text-decoration-inset-001__1","properties":[{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"text":"The underline for \"brown\" should be offset by 10px to the right:","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__css-text-decor__text-decoration-inset-001__2-052","name":"wpt__css-text-decor__text-decoration-inset-001__2","properties":[{"type":"Position","data":"RELATIVE"},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}]},{"id":"text-decoration-inset-001__1__0-053","name":"text-decoration-inset-001__1__0","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"FontSize","data":{"px":32,"original":{"type":"length","px":32}}},{"type":"FontWeight","data":{"weight":700,"original":"bold"}},{"type":"MarginTop","data":{"px":21.44}},{"type":"MarginBottom","data":{"px":21.44}}],"slot":{"parent":"wpt__css-text-decor__text-decoration-inset-001__2-052"},"text":"the quick brown fox","meta":{"sourceTag":"h1","decorations":[{"line":"underline","color":"black"}]}}]}
"""#

    /// tools/titan/runs/wave54-final/sections/css-text-decor/per-test-ir/wpt__css-text-decor__text-decoration-inset-005.json — sha256 13530ccf4b3e1aa25f71ea121deb5d9f8f0609470a7c2b6c496dd36bc42ec9e6 (VERBATIM bytes)
    static let inset005 = #"""
{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text-decor__text-decoration-inset-005__0-066","name":"wpt__css-text-decor__text-decoration-inset-005__0","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":"white"}},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"meta":{"role":"body-root"}},{"id":"wpt__css-text-decor__text-decoration-inset-005__1-067","name":"wpt__css-text-decor__text-decoration-inset-005__1","properties":[{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"text":"The underline for \"ultra-quick brown\" should be extended by .25em at each end; super- and sub-scripts should be unaffected:","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__css-text-decor__text-decoration-inset-005__2-068","name":"wpt__css-text-decor__text-decoration-inset-005__2","properties":[{"type":"Position","data":"RELATIVE"},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}]},{"id":"text-decoration-inset-005__1__0-069","name":"text-decoration-inset-005__1__0","properties":[{"type":"Position","data":"ABSOLUTE"}],"slot":{"parent":"wpt__css-text-decor__text-decoration-inset-005__2-068"},"text":"the fox","meta":{"sourceTag":"h1","runs":[{"text":"the "},{"child":"text-decoration-inset-005__1__0__0"},{"text":" fox"}]}},{"id":"text-decoration-inset-005__1__0__0-070","name":"text-decoration-inset-005__1__0__0","properties":[{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"slot":{"parent":"text-decoration-inset-005__1__0-069"},"text":"ultra- bn","meta":{"sourceTag":"u","runs":[{"text":"ultra-"},{"child":"text-decoration-inset-005__1__0__0__0"},{"text":" b"},{"child":"text-decoration-inset-005__1__0__0__1"},{"text":"n"}]}},{"id":"text-decoration-inset-005__1__0__0__0-071","name":"text-decoration-inset-005__1__0__0__0","properties":[],"slot":{"parent":"text-decoration-inset-005__1__0__0-070"},"text":"quick","meta":{"sourceTag":"sup","role":"ws-after"}},{"id":"text-decoration-inset-005__1__0__0__1-072","name":"text-decoration-inset-005__1__0__0__1","properties":[],"slot":{"parent":"text-decoration-inset-005__1__0__0-070"},"text":"row","meta":{"sourceTag":"sub"}}]}
"""#

    /// tools/titan/runs/wave54-final/sections/css-text-decor/per-test-ir/wpt__css-text-decor__text-decoration-inset-006.json — sha256 802e7ac120bb32d6a7910efe19a2eb215b4fa39a3e66a0755e310ec1b9a88865 (VERBATIM bytes)
    static let inset006 = #"""
{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text-decor__text-decoration-inset-006__0-073","name":"wpt__css-text-decor__text-decoration-inset-006__0","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":"white"}},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"meta":{"role":"body-root"}},{"id":"wpt__css-text-decor__text-decoration-inset-006__1-074","name":"wpt__css-text-decor__text-decoration-inset-006__1","properties":[{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"text":"The underline for \"ultra-quick brown\" should be trimmed by .25em at each end; super- and sub-scripts should be unaffected:","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__css-text-decor__text-decoration-inset-006__2-075","name":"wpt__css-text-decor__text-decoration-inset-006__2","properties":[{"type":"Position","data":"RELATIVE"},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}]},{"id":"text-decoration-inset-006__1__0-076","name":"text-decoration-inset-006__1__0","properties":[{"type":"Position","data":"ABSOLUTE"}],"slot":{"parent":"wpt__css-text-decor__text-decoration-inset-006__2-075"},"text":"the fox","meta":{"sourceTag":"h1","runs":[{"text":"the "},{"child":"text-decoration-inset-006__1__0__0"},{"text":" fox"}]}},{"id":"text-decoration-inset-006__1__0__0-077","name":"text-decoration-inset-006__1__0__0","properties":[{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"slot":{"parent":"text-decoration-inset-006__1__0-076"},"text":"ultra- bwn","meta":{"sourceTag":"u","runs":[{"text":"ultra-"},{"child":"text-decoration-inset-006__1__0__0__0"},{"text":" b"},{"child":"text-decoration-inset-006__1__0__0__1"},{"text":"wn"}]}},{"id":"text-decoration-inset-006__1__0__0__0-078","name":"text-decoration-inset-006__1__0__0__0","properties":[],"slot":{"parent":"text-decoration-inset-006__1__0__0-077"},"text":"quick","meta":{"sourceTag":"sup","role":"ws-after"}},{"id":"text-decoration-inset-006__1__0__0__1-079","name":"text-decoration-inset-006__1__0__0__1","properties":[],"slot":{"parent":"text-decoration-inset-006__1__0__0-077"},"text":"ro","meta":{"sourceTag":"sub"}}]}
"""#

    /// tools/titan/runs/wave54-final/sections/css-text-decor/per-test-ir/wpt__css-text-decor__text-decoration-inset-011.json — sha256 f5edf74789d4b461d6ccd9019fb5c0738afea5f71e0b2f5cc5804c2164548270 (VERBATIM bytes)
    static let inset011 = #"""
{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text-decor__text-decoration-inset-011__0-092","name":"wpt__css-text-decor__text-decoration-inset-011__0","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":"white"}},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"FontFamily","data":["times new roman","serif"]}],"meta":{"role":"body-root"}},{"id":"wpt__css-text-decor__text-decoration-inset-011__1-093","name":"wpt__css-text-decor__text-decoration-inset-011__1","properties":[{"type":"FontFamily","data":["times new roman","serif"]},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"text":"The underline of the first phrase should be trimmed by 10px at both ends; the underline of the second should be offset by 10px to the left at both start and end:","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__css-text-decor__text-decoration-inset-011__2-094","name":"wpt__css-text-decor__text-decoration-inset-011__2","properties":[{"type":"Position","data":"RELATIVE"},{"type":"MarginLeft","data":{"px":10}},{"type":"FontFamily","data":["times new roman","serif"]},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"meta":{"lang":"ar"}},{"id":"text-decoration-inset-011__1__0-095","name":"text-decoration-inset-011__1__0","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"LineHeight","data":{"multiplier":2,"original":{"type":"number","value":2}}},{"type":"Width","data":{"type":"length","original":{"v":10,"u":"EM"}}},{"type":"Direction","data":"RTL"}],"slot":{"parent":"wpt__css-text-decor__text-decoration-inset-011__2-094"},"text":"—","meta":{"sourceTag":"h1","lang":"ar","runs":[{"child":"text-decoration-inset-011__1__0__0"},{"text":" —"},{"child":"text-decoration-inset-011__1__0__1"},{"text":" "},{"child":"text-decoration-inset-011__1__0__2"}]}},{"id":"text-decoration-inset-011__1__0__0-096","name":"text-decoration-inset-011__1__0__0","properties":[{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}},{"type":"TextUnderlineOffset","data":{"type":"length","px":20}}],"slot":{"parent":"text-decoration-inset-011__1__0-095"},"text":"صِف خَلقَ خَودِ كَمِثلِ الشَمسِ إِذ بَزَغَت","meta":{"sourceTag":"u","role":"ws-after","lang":"ar"}},{"id":"text-decoration-inset-011__1__0__1-097","name":"text-decoration-inset-011__1__0__1","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":0}}],"slot":{"parent":"text-decoration-inset-011__1__0-095"},"meta":{"sourceTag":"br","role":"line-break","lang":"ar"}},{"id":"text-decoration-inset-011__1__0__2-098","name":"text-decoration-inset-011__1__0__2","properties":[{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"TextUnderlineOffset","data":{"type":"length","px":20}}],"slot":{"parent":"text-decoration-inset-011__1__0-095"},"text":"يَحظى الضَجيعُ بِها نَجلاءَ مِعطارِ","meta":{"sourceTag":"u","lang":"ar"}}]}
"""#

    /// tools/titan/runs/wave54-final/sections/css-text-decor/per-test-ir/wpt__css-text-decor__text-decoration-inset-014.json — sha256 1445c4233d82b65ec96fbeed0ce515dc722397e3e537c71a943aceb7c17f4987 (VERBATIM bytes)
    static let inset014 = #"""
{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text-decor__text-decoration-inset-014__0-107","name":"wpt__css-text-decor__text-decoration-inset-014__0","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":"white"}},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"meta":{"role":"body-root"}},{"id":"wpt__css-text-decor__text-decoration-inset-014__1-108","name":"wpt__css-text-decor__text-decoration-inset-014__1","properties":[{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"text":"The underline for \"ultra-quick brown\" should be extended by 0.5ch at each end of each fragment:","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__css-text-decor__text-decoration-inset-014__2-109","name":"wpt__css-text-decor__text-decoration-inset-014__2","properties":[{"type":"Position","data":"RELATIVE"},{"type":"Color","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}]},{"id":"text-decoration-inset-014__1__0-110","name":"text-decoration-inset-014__1__0","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"FontFamily","data":["monospace"]},{"type":"Width","data":{"type":"length","original":{"v":16,"u":"CH"}}}],"slot":{"parent":"wpt__css-text-decor__text-decoration-inset-014__2-109"},"text":"the fox","meta":{"sourceTag":"h1","runs":[{"text":"the "},{"child":"text-decoration-inset-014__1__0__0"},{"text":" fox"}]}},{"id":"text-decoration-inset-014__1__0__0-111","name":"text-decoration-inset-014__1__0__0","properties":[{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BoxDecorationBreak","data":"CLONE"}],"slot":{"parent":"text-decoration-inset-014__1__0-110"},"text":"ultra-quick brown","meta":{"sourceTag":"u"}}]}
"""#

    /// tools/titan/runs/wave54-final/sections/css-text-decor/per-test-ir/wpt__css-text-decor__text-decoration-color.json — sha256 f815b121e532dd84948f6d482d1698dfcfaa7eea5c66fdd93820fb870b544861 (VERBATIM bytes)
    static let textDecorationColor = #"""
{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-text-decor__text-decoration-color__0-021","name":"wpt__css-text-decor__text-decoration-color__0","properties":[],"text":"Each line of this test should match its text decoration color description:","meta":{"sourceTag":"h3","role":"ws-after"}},{"id":"wpt__css-text-decor__text-decoration-color__1-022","name":"wpt__css-text-decor__text-decoration-color__1","properties":[{"type":"TextDecorationLine","data":["UNDERLINE"]},{"type":"Color","data":{"srgb":{"r":0.5019607843137255,"g":0.5019607843137255,"b":0.5019607843137255},"original":"gray"}},{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}}],"text":"Gray text with blue underline"},{"id":"wpt__css-text-decor__text-decoration-color__2-023","name":"wpt__css-text-decor__text-decoration-color__2","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":20}}],"meta":{"sourceTag":"br","role":"line-break"}},{"id":"wpt__css-text-decor__text-decoration-color__3-024","name":"wpt__css-text-decor__text-decoration-color__3","properties":[{"type":"TextDecorationLine","data":["OVERLINE"]},{"type":"Color","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"text":"Green text with black overline"},{"id":"wpt__css-text-decor__text-decoration-color__4-025","name":"wpt__css-text-decor__text-decoration-color__4","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":20}}],"meta":{"sourceTag":"br","role":"line-break"}},{"id":"wpt__css-text-decor__text-decoration-color__5-026","name":"wpt__css-text-decor__text-decoration-color__5","properties":[{"type":"TextDecorationLine","data":["LINE_THROUGH"]},{"type":"TextDecorationColor","data":{"srgb":{"r":1,"g":0.8431372549019608,"b":0},"original":"gold"}}],"text":"Black text with gold line-through"},{"id":"wpt__css-text-decor__text-decoration-color__6-027","name":"wpt__css-text-decor__text-decoration-color__6","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":20}}],"meta":{"sourceTag":"br","role":"line-break"}},{"id":"wpt__css-text-decor__text-decoration-color__7-028","name":"wpt__css-text-decor__text-decoration-color__7","properties":[{"type":"TextDecorationLine","data":["UNDERLINE"]},{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}}],"text":"Black text with blue underline, gray overline and green line-through","meta":{"decorations":[{"line":"underline","color":"blue"},{"line":"overline","color":"gray"},{"line":"line-through","color":"green"}]}},{"id":"wpt__css-text-decor__text-decoration-color__8-029","name":"wpt__css-text-decor__text-decoration-color__8","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":20}}],"meta":{"sourceTag":"br","role":"line-break"}},{"id":"wpt__css-text-decor__text-decoration-color__9-030","name":"wpt__css-text-decor__text-decoration-color__9","properties":[]},{"id":"text-decoration-color__9__0-031","name":"text-decoration-color__9__0","properties":[{"type":"TextDecorationLine","data":["LINE_THROUGH"]},{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"wpt__css-text-decor__text-decoration-color__9-030"},"meta":{"sourceTag":"span"}},{"id":"text-decoration-color__9__0__0-032","name":"text-decoration-color__9__0__0","properties":[{"type":"TextDecorationLine","data":["OVERLINE"]},{"type":"TextDecorationColor","data":{"srgb":{"r":0.5019607843137255,"g":0.5019607843137255,"b":0.5019607843137255},"original":"gray"}}],"slot":{"parent":"text-decoration-color__9__0-031"},"text":"subscript text","meta":{"sourceTag":"sub","role":"ws-after"}},{"id":"text-decoration-color__9__0__1-033","name":"text-decoration-color__9__0__1","properties":[{"type":"TextDecorationLine","data":["UNDERLINE"]},{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0,"b":1},"original":"blue"}}],"slot":{"parent":"text-decoration-color__9__0-031"},"text":"superscript text","meta":{"sourceTag":"sup"}},{"id":"wpt__css-text-decor__text-decoration-color__10-034","name":"wpt__css-text-decor__text-decoration-color__10","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":20}}],"meta":{"sourceTag":"br","role":"line-break"}},{"id":"wpt__css-text-decor__text-decoration-color__11-035","name":"wpt__css-text-decor__text-decoration-color__11","properties":[{"type":"TextDecorationLine","data":["UNDERLINE"]},{"type":"Generic","data":{"propertyName":"-webkit-text-fill-color","rawValue":"transparent","_unmapped":true}},{"type":"Generic","data":{"propertyName":"-webkit-text-stroke-width","rawValue":"1px","_unmapped":true}},{"type":"Generic","data":{"propertyName":"-webkit-text-stroke-color","rawValue":"black","_unmapped":true}},{"type":"TextDecorationColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"text":"Transparent fill with black stroke text and green underline"},{"id":"wpt__css-text-decor__text-decoration-color__12-036","name":"wpt__css-text-decor__text-decoration-color__12","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":20}}],"meta":{"sourceTag":"br","role":"line-break"}},{"id":"wpt__css-text-decor__text-decoration-color__13-037","name":"wpt__css-text-decor__text-decoration-color__13","properties":[{"type":"TextDecorationLine","data":["UNDERLINE"]},{"type":"Generic","data":{"propertyName":"-webkit-text-stroke-width","rawValue":"thin","_unmapped":true}},{"type":"Generic","data":{"propertyName":"-webkit-text-stroke-color","rawValue":"LinkText","_unmapped":true}}],"text":"LinkText stroke and underline with black fill"},{"id":"wpt__css-text-decor__text-decoration-color__14-038","name":"wpt__css-text-decor__text-decoration-color__14","properties":[{"type":"Width","data":{"type":"length","px":0}},{"type":"Height","data":{"type":"length","px":20}}],"meta":{"sourceTag":"br","role":"line-break"}}]}
"""#

    /// tools/titan/runs/wave54-final/sections/CSS2/per-test-ir/wpt__CSS2__css21-errata__s-11-1-1b-005.json — sha256 0da87f253fd76f9a72410a679f7f8e44e38798088c85dc4333feacd441fa2ddb (VERBATIM bytes)
    static let s11_1_1b_005 = #"""
{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css2__css21-errata__s-11-1-1b-005__0-139","name":"wpt__CSS2__css21-errata__s-11-1-1b-005__0","properties":[{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"},{"type":"Display","data":"TABLE_CELL"},{"type":"BorderSpacing","data":{"type":"single","px":0}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":"white"}},{"type":"MarginTop","data":{"px":-15}},{"type":"MarginRight","data":{"px":8}},{"type":"MarginBottom","data":{"px":8}},{"type":"MarginLeft","data":{"px":8}},{"type":"Width","data":{"type":"length","px":20}},{"type":"Height","data":{"type":"length","px":20}}],"meta":{"role":"body-root"}},{"id":"css21-errata__s-11-1-1b-005__0-140","name":"css21-errata__s-11-1-1b-005__0","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Top","data":{"px":0}}],"slot":{"parent":"wpt__css2__css21-errata__s-11-1-1b-005__0-139"},"text":"Test passes if there is a black square below.","meta":{"sourceTag":"p"}}]}
"""#
    // ── END GENERATED WIRE
}

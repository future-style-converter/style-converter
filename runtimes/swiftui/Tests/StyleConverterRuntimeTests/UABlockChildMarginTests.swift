//
//  UABlockChildMarginTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 25, lane UAM (BD-RC3) — the SHARED pin tables for the UA default
//  block margins of block-level DESCENDANTS:
//    • U1-U10  the pure per-edge merge (UABlockChildMargin.swift), twin of
//              Compose's UaBlockChildMarginsTest.kt.
//    • REF-A..REF-E + B11 + UAM-PARENT the plan-level geometry, twin of
//              Compose's UaChildCollapsePlanTest.kt.
//
//  Every expectation is asserted with the IDENTICAL number on both
//  natives — divergence is precisely the bug class the shared table
//  exists to kill.
//
//  ## Provenance — MEASURED, not assumed
//  Five container shapes were probed in headless Chromium at a 16px root
//  (the methodology tools/titan/capture-browser-ref.mjs uses to build the
//  browser-ref). Each probe stacked 20px-tall bars inside a 300px block
//  container; the quoted rects are measured getBoundingClientRect values.
//  The rule they pin: a block CHILD's UA margin behaves exactly like a
//  declared one — it collapses with its siblings by max(), and it
//  collapses THROUGH the parent edge unless padding or a border
//  intervenes.
//
//  ## Dark-stage 327 protection
//  Every REF pin is asserted twice: once with uaBlockMargins = true (the
//  WPT-capture branch) and once with the default false, where the same
//  tree must build NO plan at all — the identity the property-fixture
//  pipeline and the 327 committed baselines ride on.
//

import XCTest
@testable import StyleConverterRuntime

final class UABlockChildMarginTests: XCTestCase {

    // MARK: - Helpers

    /// Decode an IRComponent from wire JSON — the production decode path,
    /// so the pins ride converter bytes (same convention as
    /// MarginCollapseTests).
    private func component(_ json: String) throws -> IRComponent {
        try JSONDecoder().decode(IRComponent.self, from: Data(json.utf8))
    }

    /// Decode a bare property list from wire JSON (for the U-table).
    private func props(_ json: String) -> [IRProperty] {
        try! JSONDecoder().decode([IRProperty].self, from: Data(json.utf8))
    }

    /// A 20px-tall prose bar with the given source tag, plus any extra
    /// declared properties — the probe's `<p></p>` / `<h2></h2>` shape.
    private func barJSON(id: String, tag: String, extra: String = "") -> String {
        """
        {"id":"\(id)","name":"\(id)",
         "properties":[
           {"type":"Height","data":{"type":"length","px":20.0}},
           {"type":"BackgroundColor","data":{"srgb":{"r":0.53,"g":0.53,"b":1.0},"original":"#8888ff"}}\(extra)
         ],
         "meta":{"sourceTag":"\(tag)"}}
        """
    }

    /// The probe's container: a 300px block div wrapping `children`.
    /// `extra` adds the padding / border variants; the explicit padding-0
    /// longhands are the live wire shape for an unpadded box.
    private func containerJSON(tag: String = "div",
                               extra: String = "",
                               children: [String]) -> String {
        """
        {"id":"ua_child_container-001","name":"Ua_Child_Container",
         "properties":[
           {"type":"Width","data":{"type":"length","px":300.0}},
           {"type":"PaddingTop","data":{"px":0.0}},
           {"type":"PaddingRight","data":{"px":0.0}},
           {"type":"PaddingBottom","data":{"px":0.0}},
           {"type":"PaddingLeft","data":{"px":0.0}},
           {"type":"BackgroundColor","data":{"srgb":{"r":0.8,"g":0.8,"b":0.8},"original":"#cccccc"}}\(extra)
         ],
         "children":[\(children.joined(separator: ","))],
         "meta":{"sourceTag":"\(tag)"}}
        """
    }

    /// Plan with UA injection ON (the WPT-capture branch).
    private func uaPlan(_ c: IRComponent) -> MarginCollapse.Plan? {
        MarginCollapse.containerPlan(component: c,
                                     style: StyleBuilder.build(from: c.properties),
                                     uaBlockMargins: true)
    }

    /// Plan with the DEFAULT (UA off) — the dark-stage identity branch.
    private func legacyPlan(_ c: IRComponent) -> MarginCollapse.Plan? {
        MarginCollapse.containerPlan(component: c,
                                     style: StyleBuilder.build(from: c.properties))
    }

    /// The container's content height for a stack of 20px bars: the
    /// per-child used margins plus the bar boxes (the geometry the
    /// measured rects are compared against).
    private func contentHeight(_ o: [MarginCollapseOverride], bar: CGFloat = 20) -> CGFloat {
        o.reduce(0) { $0 + $1.top + bar + $1.bottom }
    }

    /// Compact override literal.
    private func ov(_ t: CGFloat, _ b: CGFloat) -> MarginCollapseOverride {
        MarginCollapseOverride(top: t, bottom: b)
    }

    // MARK: - U1/U2: the UA table, tag by tag

    /// U1 — every tag the UA sheet gives a block margin, at a 16px root.
    /// Identical numbers to Compose's uaVerticalBlockMargins, which is why
    /// the composed ROOT stack and this CHILD fold can never disagree
    /// about what a `<p>` margin is worth.
    func testU1_uaVerticalTablePerTag() {
        let expected: [String: CGFloat] = [
            "p": 16, "h1": 21, "h2": 19, "h3": 16, "h4": 21, "h5": 27, "h6": 37,
            "ul": 16, "ol": 16, "blockquote": 16, "pre": 16, "figure": 16,
        ]
        for (tag, px) in expected {
            let v = UABlockMargin.childVertical(forTag: tag)
            XCTAssertEqual(v.top, px, "tag=\(tag) top")
            XCTAssertEqual(v.bottom, px, "tag=\(tag) bottom")
        }
    }

    /// U2 — flow containers and unknown/absent tags carry NO UA block
    /// margin, so a `<div>` wrapper never invents spacing. `<li>` is
    /// explicitly in this bucket; the lookup is case-insensitive.
    func testU2_flowContainersAndUnknownTagsAreZero() {
        for tag in ["div", "section", "article", "header", "footer",
                    "main", "nav", "aside", "li", "span", "unknown"] {
            let v = UABlockMargin.childVertical(forTag: tag)
            XCTAssertEqual(v.top, 0, "tag=\(tag)")
            XCTAssertEqual(v.bottom, 0, "tag=\(tag)")
        }
        XCTAssertEqual(UABlockMargin.childVertical(forTag: nil).top, 0)
        XCTAssertEqual(UABlockMargin.childVertical(forTag: "P").top, 16)
    }

    // MARK: - U3-U10: the per-edge cascade

    /// U3 — the dark-stage 327 identity branch: with the flag OFF the
    /// merge returns the caller's declared edges VERBATIM, whatever the
    /// tag. This is the whole baseline-protection contract.
    func testU3_disabledIsTheIdentity() {
        let a = UABlockMargin.childBlockEdges(tag: "p", properties: [],
                                              declared: (0, 0), enabled: false)
        XCTAssertEqual(a.top, 0); XCTAssertEqual(a.bottom, 0)
        let b = UABlockMargin.childBlockEdges(
            tag: "h1",
            properties: props(#"[{"type":"MarginTop","data":{"px":7.0}},{"type":"MarginBottom","data":{"px":3.0}}]"#),
            declared: (7, 3), enabled: false)
        XCTAssertEqual(b.top, 7); XCTAssertEqual(b.bottom, 3)
    }

    /// U4 — an UNDECLARED `<p>` child takes the UA 1em on both edges.
    /// This is the whole BD-RC3 defect: natives produced (0,0) here, so
    /// every nested paragraph sat 16px too high.
    func testU4_undeclaredParagraphTakesTheUADefault() {
        let e = UABlockMargin.childBlockEdges(tag: "p", properties: [],
                                              declared: (0, 0), enabled: true)
        XCTAssertEqual(e.top, 16); XCTAssertEqual(e.bottom, 16)
    }

    /// U5 — author > UA per EDGE (css-cascade-4 §6.1): a declared
    /// `margin-top: 40px` replaces the UA top and leaves the UA bottom
    /// intact (measured — ref probe case E below).
    func testU5_declaredTopWinsUndeclaredBottomKeepsUA() {
        let e = UABlockMargin.childBlockEdges(
            tag: "p", properties: props(#"[{"type":"MarginTop","data":{"px":40.0}}]"#),
            declared: (40, 0), enabled: true)
        XCTAssertEqual(e.top, 40); XCTAssertEqual(e.bottom, 16)
    }

    /// U6 — both edges declared: the UA table never contributes. A
    /// declared ZERO also wins (author `margin-bottom: 0` really means 0),
    /// which is why the declaration test is TYPE-NAME based.
    func testU6_bothEdgesDeclaredPassThroughIncludingZero() {
        let e = UABlockMargin.childBlockEdges(
            tag: "p",
            properties: props(#"[{"type":"MarginTop","data":{"px":4.0}},{"type":"MarginBottom","data":{"px":0.0}}]"#),
            declared: (4, 0), enabled: true)
        XCTAssertEqual(e.top, 4); XCTAssertEqual(e.bottom, 0)
    }

    /// U7 — the LOGICAL longhands the converter emits for
    /// `margin-block-start/end` count as declared (LTR-TB → top/bottom,
    /// css-logical-1 §4.2) — the same set the composed ROOT fold uses.
    func testU7_logicalBlockLonghandsCountAsDeclared() {
        let s = UABlockMargin.childBlockEdges(
            tag: "p", properties: props(#"[{"type":"MarginBlockStart","data":{"px":9.0}}]"#),
            declared: (9, 0), enabled: true)
        XCTAssertEqual(s.top, 9); XCTAssertEqual(s.bottom, 16)
        let e = UABlockMargin.childBlockEdges(
            tag: "p", properties: props(#"[{"type":"MarginBlockEnd","data":{"px":9.0}}]"#),
            declared: (0, 9), enabled: true)
        XCTAssertEqual(e.top, 16); XCTAssertEqual(e.bottom, 9)
    }

    /// U8 — INLINE-axis margins never declare a block edge: a child with
    /// only `margin-left` still takes both UA block defaults (§8.3.1 is
    /// vertical-only, and the inline sides pass through the applier).
    func testU8_inlineMarginsDoNotSuppressTheUADefaults() {
        let e = UABlockMargin.childBlockEdges(
            tag: "p",
            properties: props(#"[{"type":"MarginLeft","data":{"px":5.0}},{"type":"MarginRight","data":{"px":5.0}},{"type":"MarginInlineStart","data":{"px":5.0}}]"#),
            declared: (0, 0), enabled: true)
        XCTAssertEqual(e.top, 16); XCTAssertEqual(e.bottom, 16)
    }

    /// U9 — a `<div>` child with declared margins is untouched by the
    /// merge even with the flag ON (UA 0 on both edges), so mixing UA
    /// prose and plain wrappers in one container is safe.
    func testU9_divChildKeepsItsDeclaredMarginsVerbatim() {
        let e = UABlockMargin.childBlockEdges(
            tag: "div",
            properties: props(#"[{"type":"MarginTop","data":{"px":20.0}},{"type":"MarginBottom","data":{"px":20.0}}]"#),
            declared: (20, 20), enabled: true)
        XCTAssertEqual(e.top, 20); XCTAssertEqual(e.bottom, 20)
        let bare = UABlockMargin.childBlockEdges(tag: "div", properties: [],
                                                 declared: (0, 0), enabled: true)
        XCTAssertEqual(bare.top, 0); XCTAssertEqual(bare.bottom, 0)
    }

    /// U10 — heading tags feed their OWN (larger/smaller) em, so an `<h1>`
    /// child contributes 21px where a `<p>` contributes 16px; the §8.3.1
    /// fold then takes the max of the two adjoining values.
    func testU10_headingChildrenContributeTheirOwnEm() {
        let h1 = UABlockMargin.childBlockEdges(tag: "h1", properties: [],
                                               declared: (0, 0), enabled: true)
        XCTAssertEqual(h1.top, 21); XCTAssertEqual(h1.bottom, 21)
        let h6 = UABlockMargin.childBlockEdges(tag: "h6", properties: [],
                                               declared: (0, 0), enabled: true)
        XCTAssertEqual(h6.top, 37); XCTAssertEqual(h6.bottom, 37)
    }

    // MARK: - REF-A: unpadded, unbordered parent — collapse THROUGH

    /// REF-A. Probe `<div><p></p><p></p></div>`, measured rects: div top
    /// 16 / height 56, p1 top 16, p2 top 52, next sibling top 88. So the
    /// first `<p>`'s 1em ESCAPED above the div's border box, the interior
    /// gap is max(16,16)=16, the content box is 20+16+20=56, and the last
    /// `<p>`'s 1em escaped below (88 − 72 = 16) — CSS 2.1 §8.3.1
    /// collapse-through, which the existing hoist machinery already
    /// implements; the UA fold just supplies the edges.
    func testREF_A_unpaddedParentHoistsBothUAEdges() throws {
        let tree = try component(containerJSON(children: [barJSON(id: "p1", tag: "p"),
                                                          barJSON(id: "p2", tag: "p")]))
        let plan = try XCTUnwrap(uaPlan(tree))
        XCTAssertEqual(plan.hoistTop, 16)
        XCTAssertEqual(plan.hoistBottom, 16)
        XCTAssertEqual(plan.overrides, [ov(0, 0), ov(16, 0)])
        XCTAssertEqual(contentHeight(plan.overrides), 56)
        // REF-A′ — the SAME tree with the flag OFF builds no plan at all
        // (every declared margin is 0): the dark-stage 327 identity.
        XCTAssertNil(legacyPlan(tree))
    }

    // MARK: - REF-B: padding blocks the parent-edge collapse

    /// REF-B. Probe `<div style="padding:10px 0">` with the same two
    /// `<p>`s, measured: div top 88 / height 108, p1 top 114.
    /// 114 − 88 = 26 = 10 padding + 16 margin ⇒ the first child's UA
    /// margin stayed INSIDE (§8.3.1: parent and first-child margins are
    /// adjoining only with "no top border and no top padding").
    /// Height 108 = 10 + 16 + 20 + 16 + 20 + 16 + 10.
    func testREF_B_paddingKeepsTheUAEdgeMarginsInside() throws {
        let tree = try component(containerJSON(
            extra: #",{"type":"PaddingTop","data":{"px":10.0}},{"type":"PaddingBottom","data":{"px":10.0}}"#,
            children: [barJSON(id: "p1", tag: "p"), barJSON(id: "p2", tag: "p")]))
        let plan = try XCTUnwrap(uaPlan(tree))
        XCTAssertEqual(plan.hoistTop, 0)
        XCTAssertEqual(plan.hoistBottom, 0)
        XCTAssertEqual(plan.overrides, [ov(16, 0), ov(16, 16)])
        // Measured height 108 = this 88px content box + the 20px padding.
        XCTAssertEqual(contentHeight(plan.overrides), 88)
        XCTAssertNil(legacyPlan(tree))
    }

    // MARK: - REF-C: a border blocks it too

    /// REF-C. Probe `<div style="border-top:5px solid;border-bottom:5px
    /// solid">`, measured: div top 196 / height 98, p1 top 217.
    /// 217 − 196 = 21 = 5 border + 16 margin ⇒ same containment as REF-B.
    /// Height 98 = 5 + 16 + 20 + 16 + 20 + 16 + 5. Also measured: the
    /// PREVIOUS padded div's bottom (196) touches this div's top — the
    /// containment is symmetric, neither container leaked a margin.
    func testREF_C_aBorderKeepsTheUAEdgeMarginsInside() throws {
        let tree = try component(containerJSON(
            extra: #",{"type":"BorderTopWidth","data":{"px":5.0}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderBottomWidth","data":{"px":5.0}},{"type":"BorderBottomStyle","data":"SOLID"}"#,
            children: [barJSON(id: "p1", tag: "p"), barJSON(id: "p2", tag: "p")]))
        let plan = try XCTUnwrap(uaPlan(tree))
        XCTAssertEqual(plan.hoistTop, 0)
        XCTAssertEqual(plan.hoistBottom, 0)
        XCTAssertEqual(plan.overrides, [ov(16, 0), ov(16, 16)])
        // 98 measured = this 88px content box + the two 5px bands.
        XCTAssertEqual(contentHeight(plan.overrides), 88)
        XCTAssertNil(legacyPlan(tree))
    }

    // MARK: - REF-D: mixed tags collapse by max()

    /// REF-D. Probe `<div><h2></h2><p></p></div>`, measured: div/h2 top
    /// 313.90625 (flush — hoisted), h2 bottom 333.90625, p top 353.8125 ⇒
    /// interior gap 19.90625 = max(h2's .83em-of-24px, p's 1em).
    /// Chromium's exact h2 margin is 19.90625px (LayoutUnit 1/64); the
    /// Round-4 table pins the integer 19 for BOTH natives, so the pin here
    /// is 19 and the ~0.9px delta is a table-calibration matter for the
    /// root-stack lane, deliberately NOT changed (moving it would move
    /// every committed Round-4 root pin).
    func testREF_D_differingTagDefaultsCollapseToTheMax() throws {
        let tree = try component(containerJSON(children: [barJSON(id: "head", tag: "h2"),
                                                          barJSON(id: "body", tag: "p")]))
        let plan = try XCTUnwrap(uaPlan(tree))
        XCTAssertEqual(plan.hoistTop, 19)
        XCTAssertEqual(plan.hoistBottom, 16)
        XCTAssertEqual(plan.overrides, [ov(0, 0), ov(19, 0)])
        XCTAssertEqual(contentHeight(plan.overrides), 59)
        XCTAssertNil(legacyPlan(tree))
    }

    // MARK: - REF-E: author margins win per edge, then collapse

    /// REF-E. Probe `<div><p></p><p></p></div>` with
    /// `.authored > p { margin-top: 40px }`, measured: div/p1 top
    /// 413.8125 (flush), previous sibling bottom 373.8125 ⇒ 40px escaped
    /// above; p1 bottom 433.8125, p2 top 473.8125 ⇒ interior gap 40 =
    /// max(p1's UA bottom 16, p2's authored top 40); div height 80 =
    /// 20 + 40 + 20. The authored TOP replaced the UA top while the UA
    /// BOTTOM survived — the two edges cascade independently.
    func testREF_E_authoredTopWinsUABottomSurvives() throws {
        let authored = #",{"type":"MarginTop","data":{"px":40.0}}"#
        let tree = try component(containerJSON(children: [
            barJSON(id: "p1", tag: "p", extra: authored),
            barJSON(id: "p2", tag: "p", extra: authored),
        ]))
        let plan = try XCTUnwrap(uaPlan(tree))
        XCTAssertEqual(plan.hoistTop, 40)
        XCTAssertEqual(plan.hoistBottom, 16)
        XCTAssertEqual(plan.overrides, [ov(0, 0), ov(40, 0)])
        XCTAssertEqual(contentHeight(plan.overrides), 80)
    }

    // MARK: - B11 + the parent-own UA fold

    /// B11 — with UA injection ON, an INTERIOR child that is itself a
    /// hoisting block container bails the whole plan: its own hoisted band
    /// would stack on top of this fold's sibling gap, where the browser
    /// resolves the whole adjoining chain into one max(). B10 already
    /// covers the first/last positions; this widens it for the UA branch
    /// only, so no declared-margin plan changes shape.
    func testB11_interiorNestedHoistingContainerBailsTheUAPlan() throws {
        let nested = containerJSON(children: [barJSON(id: "inner", tag: "p")])
        let tree = try component(containerJSON(children: [
            barJSON(id: "p1", tag: "p"), nested, barJSON(id: "p2", tag: "p"),
        ]))
        XCTAssertNil(uaPlan(tree))
        // With the flag OFF the same tree is silent (no margins anywhere).
        XCTAssertNil(legacyPlan(tree))
    }

    /// UAM-PARENT — a `<blockquote>` parent's OWN 1em is folded into the
    /// hoist-band composition, so the band it adds for a `<p>` first child
    /// is max(16, 16) − 16 = 0. Without this the blockquote's margin
    /// (painted one level up, or by the composed root stack) and the
    /// child's band would double to 32px where the browser renders 16.
    func testUAMParent_blockquoteOwnUAMarginAbsorbsTheBand() throws {
        let tree = try component(containerJSON(
            tag: "blockquote",
            children: [barJSON(id: "p1", tag: "p"), barJSON(id: "p2", tag: "p")]))
        let plan = try XCTUnwrap(uaPlan(tree))
        XCTAssertEqual(plan.hoistTop, 0)
        XCTAssertEqual(plan.hoistBottom, 0)
        XCTAssertEqual(plan.overrides, [ov(0, 0), ov(16, 0)])
    }
}

//
//  AbsposGridStaticPositionTests.swift
//  Wave 11 (lane IOS grid-abspos) — XCTest pins for the abspos
//  static position inside a GRID container (css-grid-1 §9 + §9.2:
//  sole-grid-item alignment within the container's CONTENT box).
//
//  Shared-semantics contract: the Compose runtime pins the SAME
//  (childPx, contentPx, alignment) → offset tables for the scenarios
//  both lanes cover — the css/css-grid/abspos/grid-abspos-staticpos-*
//  WPT family. Fixture numbers, straight from those tests:
//    • *-large-border-padding: container width:100 height:500 with
//      padding 74/13/42/13 + border 23/23/45/23 (content box 100×500
//      under the WPT content-box default), abspos child 50×100
//      → center (500−100)/2 = 200 · end 500−100 = 400, and the shift
//      from the padding-box overlay anchor adds paddingTop 74 /
//      paddingLeft 13.
//    • the plain variants: container 100×100 (1px border), child 50×50
//      → center 25 · end 50.
//

import XCTest
// @testable: the helpers + IR model inits are internal.
@testable import StyleConverterRuntime

final class AbsposGridStaticPositionTests: XCTestCase {

    // MARK: - wire builders (shapes pinned against the LIVE converter)

    /// Typed px-object property, e.g. Width → {"px": 100}.
    private func px(_ type: String, _ v: Double) -> IRProperty {
        IRProperty(type: type, data: .object(["px": .double(v)]))
    }

    /// Typed keyword property, e.g. AlignSelf → "END".
    private func kw(_ type: String, _ v: String) -> IRProperty {
        IRProperty(type: type, data: .string(v))
    }

    /// The grid container of the *-large-border-padding WPT tests, as
    /// the converter emits it, with the WPT box-sizing default applied
    /// (the renderer's wave-11 fold) so declared 100×500 IS the content
    /// box. `extra` appends the per-test alignment declaration.
    private func largeBorderPaddingGrid(extra: [IRProperty] = []) -> ComponentStyle {
        var s = StyleBuilder.build(from: [
            kw("Display", "GRID"),
            px("Width", 100), px("Height", 500),
            px("PaddingTop", 74), px("PaddingRight", 13),
            px("PaddingBottom", 42), px("PaddingLeft", 13),
            px("BorderTopWidth", 23), px("BorderRightWidth", 23),
            px("BorderBottomWidth", 45), px("BorderLeftWidth", 23),
            kw("BorderTopStyle", "SOLID"), kw("BorderRightStyle", "SOLID"),
            kw("BorderBottomStyle", "SOLID"), kw("BorderLeftStyle", "SOLID"),
        ] + extra)
        // The renderer's WPT fold (pinned in WPTCaptureModeTests).
        s.size.boxSizing = SizeApplierMath.effectiveBoxSizing(
            declared: s.size.boxSizing, wptCaptureMode: true)
        return s
    }

    /// The abspos child of the same tests: 50×100, no insets.
    private var abspos5010: [IRProperty] {
        [kw("Position", "ABSOLUTE"), px("Width", 50), px("Height", 100)]
    }

    // MARK: - 1. partition: abspos children are NOT grid items

    /// css-grid-1 §9: an absolutely-positioned child of a grid container
    /// is out-of-flow and takes no grid cell — even when it CLAIMS a
    /// grid placement (grid-column etc. only choose its containing
    /// block, §9.1, they do not make it an item). The renderer's
    /// partition (inFlowChildren / overlayChildren, both filtered by
    /// isOutOfFlow) runs BEFORE grid placement, so the classification
    /// below is exactly what keeps abspos boxes out of GridPlacer.
    func testAbsposChildWithGridClaimIsOutOfFlow() throws {
        // A child that declares BOTH position:absolute and a grid line
        // claim — the wire shape of the descendant-static-position WPT
        // family's `.absolute { grid-column: 1 / 2 }` children.
        let json = #"""
        {"name":"abs","properties":[
            {"type":"Position","data":"ABSOLUTE"},
            {"type":"GridColumnStart","data":{"line":1}},
            {"type":"GridColumnEnd","data":{"line":2}}
        ]}
        """#
        let child = try JSONDecoder().decode(IRComponent.self,
                                             from: Data(json.utf8))
        // Out of flow ⇒ excluded from inFlowChildren ⇒ never a grid item.
        XCTAssertTrue(ComponentRenderer.isOutOfFlow(child))
    }

    /// The complement: a static-position child with the same grid claim
    /// stays IN flow (it IS a grid item) — the partition is driven by
    /// `position`, never by the presence of placement properties.
    func testStaticChildWithGridClaimStaysInFlow() throws {
        let json = #"""
        {"name":"item","properties":[
            {"type":"GridColumnStart","data":{"line":1}},
            {"type":"GridColumnEnd","data":{"line":2}}
        ]}
        """#
        let child = try JSONDecoder().decode(IRComponent.self,
                                             from: Data(json.utf8))
        XCTAssertFalse(ComponentRenderer.isOutOfFlow(child))
    }

    // MARK: - 2. effective spec (self beats items, css-align-3 §6.1)

    func testChildSelfClaimBeatsParentItems() {
        // Child align-self:center vs parent align-items:end → center.
        let spec = AbsposGridStaticPosition.effectiveSpec(
            childSelf: .init(base: .center, safe: false),
            parentItems: .end)
        XCTAssertEqual(spec, .init(base: .center, safe: false))
    }

    func testParentItemsFillTheAutoSlot() {
        // No self claim → the container's *-items default applies.
        let spec = AbsposGridStaticPosition.effectiveSpec(childSelf: nil,
                                                          parentItems: .center)
        XCTAssertEqual(spec, .init(base: .center, safe: false))
    }

    func testItemsKeywordFolds() {
        // The typed items-channel folds: self-end → end, self-start →
        // start (LTR normalization), and the abspos §6.1 rule that
        // stretch/normal/baseline carry NO static-position claim
        // (behave as start → nil → zero offset).
        XCTAssertEqual(AbsposGridStaticPosition.base(ofItems: .selfEnd), .end)
        XCTAssertEqual(AbsposGridStaticPosition.base(ofItems: .selfStart), .start)
        XCTAssertEqual(AbsposGridStaticPosition.base(ofItems: .end), .end)
        XCTAssertEqual(AbsposGridStaticPosition.base(ofItems: .center), .center)
        XCTAssertNil(AbsposGridStaticPosition.base(ofItems: .stretch))
        XCTAssertNil(AbsposGridStaticPosition.base(ofItems: .normal))
        XCTAssertNil(AbsposGridStaticPosition.base(ofItems: .baseline))
        XCTAssertNil(AbsposGridStaticPosition.base(ofItems: nil))
    }

    // MARK: - 3. the shared offset table (Compose-pinned identically)

    func testAxisOffsetLargeBorderPaddingTable() {
        // The *-large-border-padding scenarios: content 500, child 100,
        // paddingTop 74. center → 74 + (500−100)/2 = 274.
        XCTAssertEqual(AbsposGridStaticPosition.axisOffset(
            childPx: 100, contentPx: 500, paddingStart: 74,
            spec: .init(base: .center, safe: false)), 274)
        // end (and the flex-end/self-end folds) → 74 + 400 = 474.
        XCTAssertEqual(AbsposGridStaticPosition.axisOffset(
            childPx: 100, contentPx: 500, paddingStart: 74,
            spec: .init(base: .end, safe: false)), 474)
        // start / no claim → the content-box origin (paddingTop).
        XCTAssertEqual(AbsposGridStaticPosition.axisOffset(
            childPx: 100, contentPx: 500, paddingStart: 74,
            spec: .init(base: .start, safe: false)), 74)
        XCTAssertEqual(AbsposGridStaticPosition.axisOffset(
            childPx: 100, contentPx: 500, paddingStart: 74, spec: nil), 74)
    }

    func testAxisOffsetPlainVariantTable() {
        // The plain grid-abspos-staticpos-* scenarios: content 100,
        // child 50, no padding. center → 25 · end → 50 · start → 0.
        XCTAssertEqual(AbsposGridStaticPosition.axisOffset(
            childPx: 50, contentPx: 100, paddingStart: 0,
            spec: .init(base: .center, safe: false)), 25)
        XCTAssertEqual(AbsposGridStaticPosition.axisOffset(
            childPx: 50, contentPx: 100, paddingStart: 0,
            spec: .init(base: .end, safe: false)), 50)
        XCTAssertEqual(AbsposGridStaticPosition.axisOffset(
            childPx: 50, contentPx: 100, paddingStart: 0,
            spec: .init(base: .start, safe: false)), 0)
    }

    func testAxisOffsetDegradesHonestlyOnIndefiniteExtents() {
        // Auto-sized child / indefinite container → alignment cannot be
        // computed ahead of measurement; the content-box-origin shift
        // still applies (the §9.2 static rect starts at content edges).
        XCTAssertEqual(AbsposGridStaticPosition.axisOffset(
            childPx: nil, contentPx: 500, paddingStart: 74,
            spec: .init(base: .center, safe: false)), 74)
        XCTAssertEqual(AbsposGridStaticPosition.axisOffset(
            childPx: 100, contentPx: nil, paddingStart: 74,
            spec: .init(base: .end, safe: false)), 74)
    }

    // MARK: - 4. content extents (box-sizing aware)

    func testContentExtentUnderWptContentBoxDefault() {
        // With the WPT content-box default the declared 100×500 IS the
        // content box — the §9.2 alignment container of the refs.
        let s = largeBorderPaddingGrid()
        XCTAssertEqual(AbsposGridStaticPosition.contentExtent(s, vertical: true), 500)
        XCTAssertEqual(AbsposGridStaticPosition.contentExtent(s, vertical: false), 100)
    }

    func testContentExtentUnderBorderBoxStatusQuo() {
        // Same declarations WITHOUT the WPT default (dark stage): the
        // declared size reads border-box, so content = declared − bands:
        // v 500 − (74+42) − (23+45) = 316 · h 100 − 26 − 46 = 28.
        var s = largeBorderPaddingGrid()
        s.size.boxSizing = nil
        XCTAssertEqual(AbsposGridStaticPosition.contentExtent(s, vertical: true), 316)
        XCTAssertEqual(AbsposGridStaticPosition.contentExtent(s, vertical: false), 28)
    }

    // MARK: - 5. the full view-level shift (WPT scenarios end-to-end)

    func testStaticOffsetAlignItemsCenterLargeBorderPadding() {
        // grid-abspos-staticpos-align-items-center-large-border-padding:
        // parent align-items:center, child 50×100 with no insets →
        // shift (13, 274) from the padding-box overlay anchor (x is the
        // start outcome: justify unset → content-box origin).
        let s = largeBorderPaddingGrid(extra: [kw("AlignItems", "CENTER")])
        let off = AbsposGridStaticPosition.staticOffset(parentStyle: s,
                                                        childProperties: abspos5010)
        XCTAssertEqual(off, CGSize(width: 13, height: 274))
    }

    func testStaticOffsetAlignItemsEndFamilyLargeBorderPadding() {
        // The end / flex-end / self-end variants all land on the same
        // §9.2 outcome: y = 74 + 400 = 474 (converter wires: end→END,
        // flex-end→FLEX_END, self-end→SELF_END — all fold to .end).
        for wire in ["END", "FLEX_END", "SELF_END"] {
            let s = largeBorderPaddingGrid(extra: [kw("AlignItems", wire)])
            let off = AbsposGridStaticPosition.staticOffset(parentStyle: s,
                                                            childProperties: abspos5010)
            XCTAssertEqual(off, CGSize(width: 13, height: 474),
                           "align-items wire \(wire)")
        }
    }

    func testStaticOffsetAlignSelfBeatsAlignItems() {
        // The align-self-* variants declare the claim on the CHILD; a
        // conflicting parent default must lose (css-align-3 §6.1). The
        // converter folds align-self flex-end/self-end to "END" already.
        let s = largeBorderPaddingGrid(extra: [kw("AlignItems", "CENTER")])
        let child = abspos5010 + [kw("AlignSelf", "END")]
        let off = AbsposGridStaticPosition.staticOffset(parentStyle: s,
                                                        childProperties: child)
        XCTAssertEqual(off, CGSize(width: 13, height: 474))
    }

    func testStaticOffsetInsetGatesItsAxis() {
        // css-position-3 §3.5: an explicit inset REPLACES the static
        // position on its axis — PositionApplier owns it (anchored at
        // the PADDING box, §3.1) — while the other axis keeps §9.2.
        let s = largeBorderPaddingGrid(extra: [kw("AlignItems", "CENTER")])
        let child = abspos5010 + [px("Top", 10)]
        let off = AbsposGridStaticPosition.staticOffset(parentStyle: s,
                                                        childProperties: child)
        // Vertical gated (0); horizontal keeps the content-box origin.
        XCTAssertEqual(off, CGSize(width: 13, height: 0))
    }

    func testStaticOffsetZeroForNonGridParents() {
        // A block parent with the same padding/borders: §9.2 does not
        // apply — the pre-wave-11 padding-box anchor stands untouched.
        var s = largeBorderPaddingGrid(extra: [kw("AlignItems", "CENTER")])
        s.layout7?.display = .block
        XCTAssertEqual(AbsposGridStaticPosition.staticOffset(
            parentStyle: s, childProperties: abspos5010), .zero)
    }

    func testStaticOffsetJustifyAxis() {
        // The inline twin: justify-items:center centers the 50px child
        // in the 100px content width → x = 13 + 25 = 38; the block axis
        // keeps the content-box origin (74).
        let s = largeBorderPaddingGrid(extra: [kw("JustifyItems", "CENTER")])
        let off = AbsposGridStaticPosition.staticOffset(parentStyle: s,
                                                        childProperties: abspos5010)
        XCTAssertEqual(off, CGSize(width: 38, height: 74))
    }
}

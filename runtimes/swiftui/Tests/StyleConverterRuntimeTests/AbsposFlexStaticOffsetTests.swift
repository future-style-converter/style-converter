//
//  AbsposFlexStaticOffsetTests.swift
//  Wave 52 (lane L7, static-position T2) — XCTest pins for the iOS flex
//  overlay's static-position shift (AbsposStaticPosition.staticOffset in
//  AbsposStaticOffset.swift) on VERBATIM wave51-fix per-test IR:
//
//    S1 flex-abspos-staticpos-justify-self-001 (container __0__0-069, child
//       __0__0__0-070): padding 1/2/1/2, border 1, 16×10 content, 8×6
//       child → shift (2, 1) — the padding-start edges (was (0, 0): every
//       mark (−2,−1) off the ref, ios P 0.9921);
//    S2 position-absolute-containing-block-002 (container __0-350, child
//       __0__0-351): 180×180 content, padding-left/top 15, border 5,
//       align-items + justify-content CENTER, 100×100 child → (55, 55)
//       (was (47.5, 0): centred in the 195 padding box, align-items never
//       read — ios f 0.9373, green (69,21) vs ref (76,76));
//    S3 the call-site inputs: the seam passes flexContentSize (180), not
//       the §3.1 padding box (195) the percent channel keeps;
//    S4 a DECLARED align-self (even a claim-less one) blocks the
//       align-items fallback; an explicit cross inset keeps the axis 0.
//  MUTATIONS EXECUTED (tools/titan/results/wave52-static-position/
//  mutations.log): M-S1 drop the padding-start term → S1 (0,0), S2
//  (40,40), S4 fail; M-S2 drop the align-items fallback → S2 (y 15), S4 fail.
//

import XCTest
// @testable: AbsposStaticPosition + IRProperty init are internal.
@testable import StyleConverterRuntime

final class AbsposFlexStaticOffsetTests: XCTestCase {

    /// Decode a verbatim per-test IR `properties` array.
    private func props(_ json: String) -> [IRProperty] {
        try! JSONDecoder().decode([IRProperty].self, from: Data(json.utf8))
    }

    // flex-abspos-staticpos-justify-self-001, component __0__0-069 (verbatim).
    private lazy var jsContainer = props("""
    [{"type":"Display","data":"FLEX"},{"type":"FlexDirection","data":"ROW"},{"type":"PaddingTop","data":{"px":1}},{"type":"PaddingRight","data":{"px":2}},{"type":"PaddingBottom","data":{"px":1}},{"type":"PaddingLeft","data":{"px":2}},{"type":"BorderTopWidth","data":{"px":1}},{"type":"BorderRightWidth","data":{"px":1}},{"type":"BorderBottomWidth","data":{"px":1}},{"type":"BorderLeftWidth","data":{"px":1}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderRightColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderBottomColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BorderLeftColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":0},"original":"yellow"}},{"type":"MarginBottom","data":{"px":5}},{"type":"MarginRight","data":{"px":5}},{"type":"Float","data":"LEFT"},{"type":"Height","data":{"type":"length","px":10}},{"type":"Width","data":{"type":"length","px":16}}]
    """)

    // …its abspos child __0__0__0-070 (verbatim).
    private lazy var jsChild = props("""
    [{"type":"Position","data":"ABSOLUTE"},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0.5019607843137255},"original":"teal"}},{"type":"Height","data":{"type":"length","px":6}},{"type":"Width","data":{"type":"length","px":8}},{"type":"JustifySelf","data":{"type":"auto"}}]
    """)

    // position-absolute-containing-block-002, component __0-350 (verbatim).
    private lazy var cbContainer = props("""
    [{"type":"Position","data":"FIXED"},{"type":"Top","data":{"px":0}},{"type":"Left","data":{"px":0}},{"type":"Display","data":"FLEX"},{"type":"AlignItems","data":"CENTER"},{"type":"JustifyContent","data":"CENTER"},{"type":"Width","data":{"type":"length","px":180}},{"type":"Height","data":{"type":"length","px":180}},{"type":"BorderTopWidth","data":{"px":5}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":1,"g":1,"b":0},"original":"yellow"}},{"type":"PaddingTop","data":{"px":15}},{"type":"BorderLeftWidth","data":{"px":5}},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderLeftColor","data":{"srgb":{"r":1,"g":1,"b":0},"original":"yellow"}},{"type":"PaddingLeft","data":{"px":15}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":0},"original":"yellow"}}]
    """)

    // …its abspos child __0__0-351 (verbatim).
    private lazy var cbChild = props("""
    [{"type":"Position","data":"ABSOLUTE"},{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}]
    """)

    /// The container style exactly as the WPT-capture renderer sees it: the
    /// SAME engine build + the content-box default (SizeApplierMath).
    private func wptStyle(_ p: [IRProperty]) -> ComponentStyle {
        var s = StyleBuilder.build(from: p)
        s.size.boxSizing = SizeApplierMath.effectiveBoxSizing(
            declared: s.size.boxSizing, wptCaptureMode: true)
        return s
    }

    func testS1JustifySelf001ShiftsByThePaddingStartEdges() {
        // Content extents 16×10 (what the seam passes via flexContentSize).
        let shift = AbsposStaticPosition.staticOffset(
            containerProperties: jsContainer, childProperties: jsChild,
            containerW: 16, containerH: 10, wptCaptureMode: true)
        // Ref mark (19,18) = border-box origin (16,16) + border 1 + padding (2,1).
        XCTAssertEqual(shift.width, 2, accuracy: 1e-9)
        XCTAssertEqual(shift.height, 1, accuracy: 1e-9)
    }

    func testS2ContainingBlock002CentresInTheContentBoxOnBothAxes() {
        // justify-content center (main x) + align-items center (cross y).
        let shift = AbsposStaticPosition.staticOffset(
            containerProperties: cbContainer, childProperties: cbChild,
            containerW: 180, containerH: 180, wptCaptureMode: true)
        // 15 padding + (180 − 100)/2 = 55 → ref green (76,76) = 16 + 5 + 55.
        XCTAssertEqual(shift.width, 55, accuracy: 1e-9)
        XCTAssertEqual(shift.height, 55, accuracy: 1e-9)
    }

    func testS3CallSiteInputsAreTheContentBoxNotThePaddingBox() {
        // The seam's new arguments (flexContentSize = ContainingBlockBasis
        // .contentBox) vs the §3.1 padding box the percent channel keeps.
        let s = wptStyle(cbContainer)
        XCTAssertEqual(ContainingBlockBasis.contentBox(style: s, vertical: false), 180)
        XCTAssertEqual(ContainingBlockBasis.contentBox(style: s, vertical: true), 180)
        XCTAssertEqual(ContainingBlockBasis.paddingBox(style: s, vertical: false), 195)
        // justify-self-001: 16×10 content, 20×12 padding box.
        let j = wptStyle(jsContainer)
        XCTAssertEqual(ContainingBlockBasis.contentBox(style: j, vertical: false), 16)
        XCTAssertEqual(ContainingBlockBasis.contentBox(style: j, vertical: true), 10)
    }

    func testS4DeclaredAlignSelfAndInsetsBlockTheAlignItemsFallback() {
        // Typed `stretch` is a declaration: no align-items fallback → y is
        // the padding start only (an abspos stretch with a definite size is
        // start-aligned, css-align-3 §6.1).
        let stretch = cbChild + [IRProperty(type: "AlignSelf", data: .string("STRETCH"))]
        let a = AbsposStaticPosition.staticOffset(
            containerProperties: cbContainer, childProperties: stretch,
            containerW: 180, containerH: 180, wptCaptureMode: true)
        XCTAssertEqual(a.height, 15, accuracy: 1e-9)
        // `auto` is NOT a claim: §6.1 auto → the parent's align-items.
        let auto = cbChild + [IRProperty(type: "AlignSelf", data: .string("AUTO"))]
        let b = AbsposStaticPosition.staticOffset(
            containerProperties: cbContainer, childProperties: auto,
            containerW: 180, containerH: 180, wptCaptureMode: true)
        XCTAssertEqual(b.height, 55, accuracy: 1e-9)
        // A real `top` inset owns the axis (§3.5): 0, not the padding edge.
        let topInset = cbChild + [IRProperty(type: "Top", data: .object(["px": .double(7)]))]
        let c = AbsposStaticPosition.staticOffset(
            containerProperties: cbContainer, childProperties: topInset,
            containerW: 180, containerH: 180, wptCaptureMode: true)
        XCTAssertEqual(c.height, 0, accuracy: 1e-9)
        XCTAssertEqual(c.width, 55, accuracy: 1e-9)
    }
}

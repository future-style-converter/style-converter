//
//  Wave32ZeroContainingBlockTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 32 (lane P) — a DECLARED containing-block size of ZERO is
//  DEFINITE, not indefinite.
//
//  ## The measured defect (frozen wave31-final pixel evidence)
//  `ContainingBlockBasis.contentBox` / `.paddingBox` both ended in a
//  `v > 0 ? v : nil` guard. That guard conflated three different states:
//    • genuinely unknown (fit-content / auto) — must publish nil so `%`
//      children fall back per channel;
//    • legitimately ZERO;
//    • over-padded, where the arithmetic would go negative.
//  CSS treats only the first as indefinite: css-sizing-3 §5.1 resolves a
//  `<percentage>` against whatever the containing block's size is, and
//  CSS 2.1 §10.4 clamps a used width at 0 rather than making it auto.
//
//  css-tables/absolute-tables-015 declares
//    table { position:absolute; width:0; border-spacing:0 }
//    td    { width:100%; background:red; padding:0; line-height:0 }
//    span  { display:inline-block; width:100px; height:50px; background:green }
//  The table's basis resolved to 0 → the guard turned it into nil → the
//  `td`'s `100%` had no basis and fell back to the CANVAS width, so iOS
//  painted a 258×100 RED band at [116,88..373,187] beside the green
//  square. The reference and Android carry GREEN 100×100
//  [16,88..115,187] and NO red at all — Android is the parity oracle.
//
//  ## Blast radius, enumerated before the change
//  Across all 27 frozen wave31-final sections (324 tests) exactly ONE
//  component has a degenerate axis basis AND a percentage-sized
//  descendant on that axis: `absolute-tables-015__1__0` itself. 100 other
//  zero-sized boxes exist in the corpus; none has a percentage
//  descendant, so none can observe this channel change.
//

import XCTest
import SwiftUI
// @testable: ContainingBlockBasis / StyleBuilder / SizeApplierMath are
// internal to the module.
@testable import StyleConverterRuntime

final class Wave32ZeroContainingBlockTests: XCTestCase {

    // MARK: - wire builders (converter-pinned shapes)

    /// Typed length property — Width → {"type":"length","px":N}.
    private func len(_ type: String, _ v: Double) -> IRProperty {
        IRProperty(type: type, data: .object([
            "type": .string("length"), "px": .double(v)]))
    }

    /// Border/padding px-object property — {"px": N}.
    private func px(_ type: String, _ v: Double) -> IRProperty {
        IRProperty(type: type, data: .object(["px": .double(v)]))
    }

    /// Bare keyword property — BorderTopStyle → "SOLID".
    private func kw(_ type: String, _ v: String) -> IRProperty {
        IRProperty(type: type, data: .string(v))
    }

    /// Same fold the renderer applies before any basis math runs
    /// (an UNDECLARED box-sizing is content-box in WPT capture only).
    private func style(_ props: [IRProperty], wptFold: Bool) -> ComponentStyle {
        var s = StyleBuilder.build(from: props)
        s.size.boxSizing = SizeApplierMath.effectiveBoxSizing(
            declared: s.size.boxSizing, wptCaptureMode: wptFold)
        return s
    }

    // MARK: - the absolute-tables-015 wire

    /// The exact frozen wire for `absolute-tables-015__1__0`: an abspos
    /// table with `width: 0` and nothing else that touches the inline
    /// axis (border-spacing is not a box band).
    private var zeroWidthTableProps: [IRProperty] {
        [len("Width", 0)]
    }

    func testZeroDeclaredWidthPublishesADefiniteZeroBasis() {
        // Border-box lane (no BoxSizing declared, outside the WPT fold —
        // the frozen status quo path): 0 − 0 bands = 0, published.
        let borderBox = style(zeroWidthTableProps, wptFold: false)
        XCTAssertEqual(ContainingBlockBasis.contentBox(style: borderBox, vertical: false), 0,
                       "width:0 is a DEFINITE basis of 0 — a `%` child must resolve to 0, "
                       + "not fall back to the canvas width (absolute-tables-015's red band)")
        // Content-box lane (the WPT fold): the declared size IS the
        // content box, so the same 0 passes straight through.
        let contentBox = style(zeroWidthTableProps, wptFold: true)
        XCTAssertEqual(ContainingBlockBasis.contentBox(style: contentBox, vertical: false), 0,
                       "content-box: a declared 0 content box is still 0, still definite")
    }

    func testZeroDeclaredWidthPublishesADefiniteZeroPaddingBox() {
        // The abspos basis twin (css-position-3 §3.1) — an abspos child of
        // a zero-sized positioned ancestor resolves `%` against 0.
        for fold in [false, true] {
            let s = style(zeroWidthTableProps, wptFold: fold)
            XCTAssertEqual(ContainingBlockBasis.paddingBox(style: s, vertical: false), 0,
                           "padding box of a zero-sized ancestor is 0 (wptFold=\(fold))")
        }
    }

    func testAnOverPaddedBoxClampsToZeroRatherThanGoingIndefinite() {
        // 10px border box with a 20px padding pair: the content box would
        // be −10. CSS 2.1 §10.4 clamps the used value at 0; it does NOT
        // make the box auto, so the basis must be 0 and never nil.
        let s = style([len("Width", 10), px("PaddingLeft", 10), px("PaddingRight", 10)],
                      wptFold: false)
        XCTAssertEqual(ContainingBlockBasis.contentBox(style: s, vertical: false), 0,
                       "an over-padded content box clamps at 0, it does not become indefinite")
    }

    func testAnIndefiniteSizeStillPublishesNil() {
        // The state the guard was ACTUALLY for: no declared size at all.
        // A fit-content/auto ancestor's width is not statically definite,
        // so the channel must still reset to nil and let `%` children fall
        // back per channel. This is the byte-stability guarantee for the
        // whole corpus outside the one enumerated mover.
        let s = style([kw("Display", "BLOCK")], wptFold: false)
        XCTAssertNil(ContainingBlockBasis.contentBox(style: s, vertical: true),
                     "no declared block-size ⇒ indefinite ⇒ nil (percent-skip stays)")
        XCTAssertNil(ContainingBlockBasis.paddingBox(style: s, vertical: true),
                     "no declared block-size ⇒ indefinite ⇒ nil on the abspos basis too")
    }

    func testANonZeroBasisIsUntouchedByTheClamp() {
        // Regression guard for every ordinary box in the corpus: the
        // clamp must be a no-op above zero, on both bases and both lanes.
        let s = style([len("Width", 100), len("Height", 100),
                       px("BorderLeftWidth", 5), kw("BorderLeftStyle", "SOLID"),
                       px("PaddingLeft", 10)],
                      wptFold: false)
        // border-box lane: 100 − 10 padding − 5 border = 85 content box.
        XCTAssertEqual(ContainingBlockBasis.contentBox(style: s, vertical: false), 85)
        // padding box = border box − borders (padding stays inside).
        XCTAssertEqual(ContainingBlockBasis.paddingBox(style: s, vertical: false), 95)
    }
}

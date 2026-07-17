//
//  BoxSizingTests.swift
//  StyleConverterRuntimeTests
//
//  Lane BX — `box-sizing: content-box` on the iOS sizing lane.
//
//  Pins three behaviours the visual gate depends on:
//    1. TRI-STATE extraction — an ABSENT box-sizing must stay nil on
//       SizeConfig (unset ≠ content-box), because the whole width+padding
//       fixture corpus is captured against the web harness's
//       `* { box-sizing: border-box }` reset and any implicit content-box
//       default would inflate every frame.
//    2. Inflation arithmetic — css-sizing-3 §3: frame = content + padding +
//       border, e.g. width 100 + padding 16×2 + border 2×2 → frame 136.
//    3. Border-box sentinel — an EXPLICIT border-box (the fixture's passing
//       V1_border variant) and the unset case both produce zero inflation.
//

import XCTest
// @testable: SizeConfig / SizeExtractor / SizeApplierMath / StyleBuilder are
// internal to the module — same access pattern as SizingTests.swift.
@testable import StyleConverterRuntime

final class BoxSizingTests: XCTestCase {

    // Build an IRProperty list from (type,data) pairs — SizingTests style.
    private func props(_ pairs: [(String, IRValue)]) -> [IRProperty] {
        pairs.map { IRProperty(type: $0.0, data: $0.1) }
    }

    // MARK: - 1. Tri-state extraction (unset ≠ content-box)

    /// The regression sentinel: sizing props WITHOUT box-sizing must leave
    /// the slot nil so the applier keeps the border-box status quo.
    func testAbsentBoxSizingStaysNil() {
        let cfg = SizeExtractor.extract(from: props([
            ("Width", .object(["type": .string("length"), "px": .double(100)])),
        ]))
        XCTAssertNil(cfg.boxSizing)
    }

    /// Wire shape per BoxSizingPropertyParser.kt (flattened single-field
    /// data class): bare SHOUTY "CONTENT_BOX" / "BORDER_BOX" strings.
    func testExplicitKeywordsDecode() {
        let content = SizeExtractor.extract(from: props([
            ("BoxSizing", .string("CONTENT_BOX")),
        ]))
        XCTAssertEqual(content.boxSizing, .contentBox)
        let border = SizeExtractor.extract(from: props([
            ("BoxSizing", .string("BORDER_BOX")),
        ]))
        XCTAssertEqual(border.boxSizing, .borderBox)
    }

    /// Wire drift must never guess content-box (that would inflate frames
    /// against every border-box baseline) — unknown tokens stay UNSET.
    func testUnknownTokenLeavesSlotUnset() {
        let cfg = SizeExtractor.extract(from: props([
            ("BoxSizing", .string("PADDING_BOX")),
        ]))
        XCTAssertNil(cfg.boxSizing)
    }

    /// A lone box-sizing declaration has no size to reinterpret — it must
    /// not force the SizeApplier onto the chain (hasAny stays false).
    func testBoxSizingAloneDoesNotSetHasAny() {
        let cfg = SizeExtractor.extract(from: props([
            ("BoxSizing", .string("CONTENT_BOX")),
        ]))
        XCTAssertEqual(cfg.boxSizing, .contentBox)
        XCTAssertFalse(cfg.hasAny)
    }

    // MARK: - 2. Inflation arithmetic (css-sizing-3 §3)

    /// The task's canonical arithmetic: width 100 + padding 16×2 +
    /// border 2×2 → frame 136 (content-box: frame = content+padding+border).
    func testInflatedAxisContentBoxArithmetic() {
        XCTAssertEqual(
            SizeApplierMath.inflatedAxis(100, boxSizing: .contentBox, by: 36), 136)
    }

    /// Nil axes (auto / intrinsic) stay nil — content-box only reinterprets
    /// DEFINITE sizes, so there is nothing to inflate.
    func testInflatedAxisNilStaysNil() {
        XCTAssertNil(
            SizeApplierMath.inflatedAxis(nil, boxSizing: .contentBox, by: 36))
    }

    /// StyleBuilder resolves padding band + USED border widths per axis.
    /// padding 16 all round + a painting 2px solid border all round → 36/36.
    func testContentBoxInflationResolvesPaddingAndBorder() {
        var s = ComponentStyle()
        s.size.boxSizing = .contentBox
        s.spacing.padding = PaddingConfig(
            top: .exact(px: 16), right: .exact(px: 16),
            bottom: .exact(px: 16), left: .exact(px: 16))
        // Sides need a visible style — `border-style: none` has USED width 0
        // (CSS 2.1 §8.5.3) and must not inflate.
        let side = BorderSideConfig(width: 2, color: nil, style: .solid)
        s.borderSides = AllBordersConfig(top: side, end: side, bottom: side, start: side)
        let inf = StyleBuilder.contentBoxInflation(s)
        XCTAssertEqual(inf.h, 36)
        XCTAssertEqual(inf.v, 36)
    }

    /// Border widths without a visible style contribute NOTHING to the
    /// inflation band — mirrors backgroundClipInsets' hasBorder gate.
    func testStylelessBorderDoesNotInflate() {
        var s = ComponentStyle()
        s.size.boxSizing = .contentBox
        s.spacing.padding = PaddingConfig(
            top: .exact(px: 10), right: .exact(px: 10),
            bottom: .exact(px: 10), left: .exact(px: 10))
        let side = BorderSideConfig(width: 4, color: nil, style: BorderStyleValue.none)
        s.borderSides = AllBordersConfig(top: side, end: side, bottom: side, start: side)
        let inf = StyleBuilder.contentBoxInflation(s)
        XCTAssertEqual(inf.h, 20)
        XCTAssertEqual(inf.v, 20)
    }

    /// verticalPaddingPx is the new block-axis analogue of the wave-5
    /// horizontal helper — asymmetric sides must sum independently.
    func testVerticalPaddingPxSumsTopAndBottom() {
        var s = ComponentStyle()
        s.spacing.padding = PaddingConfig(
            top: .exact(px: 12), right: .exact(px: 3),
            bottom: .exact(px: 8), left: .exact(px: 5))
        XCTAssertEqual(StyleBuilder.verticalPaddingPx(s), 20)
        XCTAssertEqual(StyleBuilder.horizontalPaddingPx(s), 8)
    }

    // MARK: - 3. Border-box no-change sentinel (V1_border)

    /// Unset and explicit border-box both produce ZERO inflation — the
    /// applier's frame math is provably byte-identical to the status quo.
    func testBorderBoxAndUnsetProduceZeroInflation() {
        var s = ComponentStyle()
        s.spacing.padding = PaddingConfig(
            top: .exact(px: 10), right: .exact(px: 10),
            bottom: .exact(px: 10), left: .exact(px: 10))
        // Unset (nil) — the whole existing fixture corpus.
        XCTAssertEqual(StyleBuilder.contentBoxInflation(s).h, 0)
        XCTAssertEqual(StyleBuilder.contentBoxInflation(s).v, 0)
        // Explicit border-box — the fixture's passing V1_border variant.
        s.size.boxSizing = .borderBox
        XCTAssertEqual(StyleBuilder.contentBoxInflation(s).h, 0)
        XCTAssertEqual(StyleBuilder.contentBoxInflation(s).v, 0)
        // And the axis math passes values through untouched in both states.
        XCTAssertEqual(SizeApplierMath.inflatedAxis(180, boxSizing: nil, by: 36), 180)
        XCTAssertEqual(SizeApplierMath.inflatedAxis(180, boxSizing: .borderBox, by: 36), 180)
    }
}

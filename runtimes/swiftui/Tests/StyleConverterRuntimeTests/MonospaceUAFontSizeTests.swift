//
//  MonospaceUAFontSizeTests.swift
//  StyleConverterRuntimeTests
//
//  Wave 36, lane M8 — pins the UA fixed-default font-size quirk
//  (StyleEngine/typography/MonospaceUAFontSize.swift) and both consumers in
//  StyleBuilder. Byte-parallel twin of the Compose
//  `MonospaceUAFontSizeTest.kt`.
//
//  ── What is being protected ────────────────────────────────────────────────
//  `css/css-overflow/line-clamp/block-ellipsis-*` (20 depth-48 cells) declare a
//  bare `font-family: monospace` with NO `font-size`. Chromium resolves the
//  initial `medium` keyword against `defaultFixedFontSize` (13px) for those,
//  not against `defaultFontSize` (16px) — so the frozen browser-ref captures
//  were rasterised at 13px while both natives rendered 16px, missing on BOTH
//  the glyph advance (7.78 vs 9.6 px/char) and the line pitch (16.25 vs 19.5).
//
//  ── The invariant that must never break ────────────────────────────────────
//  The gate must stay OFF for every element that either declares a font-size or
//  does not lead its family list with the monospace generic — that is the whole
//  committed 327-pair baseline corpus plus `css-text/hyphens/hyphens-auto-
//  control` (`"Courier New", Courier, monospace`). Both halves are pinned.
//
//  The IR payloads are the LIVE wire shapes from
//  tools/titan/runs/wave35-final/sections/css-overflow/per-test-ir/
//  wpt__css-overflow__line-clamp__block-ellipsis-001.json and
//  .../css-text/per-test-ir/wpt__css-text__hyphens__hyphens-auto-control.json.
//

import XCTest
// @testable: MonospaceUAFontSize / StyleBuilder / ComponentStyle are internal
// to the module — same access pattern as BoxSizingTests.swift.
@testable import StyleConverterRuntime

final class MonospaceUAFontSizeTests: XCTestCase {

    private func props(_ pairs: [(String, IRValue)]) -> [IRProperty] {
        pairs.map { IRProperty(type: $0.0, data: $0.1) }
    }

    /// block-ellipsis-001's FontFamily payload, verbatim.
    private let bareMonospace = IRValue.array([.string("monospace")])
    /// hyphens-auto-control's FontFamily payload, verbatim.
    private let courierFirst = IRValue.array([
        .string("Courier New"), .string("Courier"), .string("monospace"),
    ])

    // MARK: - The family predicate

    func testBareMonospaceArmsTheGate() {
        XCTAssertTrue(MonospaceUAFontSize.appliesToFamily(bareMonospace))
    }

    func testUiMonospaceArmsTheGate() {
        XCTAssertTrue(MonospaceUAFontSize.appliesToFamily(.array([.string("ui-monospace")])))
    }

    /// Blink takes the generic from the FIRST family only; a concrete face
    /// first keeps kStandardFamily and therefore the 16px default.
    func testMonospaceBehindAConcreteFaceDoesNotArmTheGate() {
        XCTAssertFalse(MonospaceUAFontSize.appliesToFamily(courierFirst))
    }

    func testOtherGenericsDoNotArmTheGate() {
        XCTAssertFalse(MonospaceUAFontSize.appliesToFamily(.array([.string("serif")])))
        XCTAssertFalse(MonospaceUAFontSize.appliesToFamily(
            .array([.string("sans-serif"), .string("monospace")])))
    }

    func testAbsentOrMalformedFamilyNeverArmsTheGate() {
        XCTAssertFalse(MonospaceUAFontSize.appliesToFamily(nil))
        XCTAssertFalse(MonospaceUAFontSize.appliesToFamily(.array([])))
        XCTAssertFalse(MonospaceUAFontSize.appliesToFamily(.int(42)))
    }

    /// The object entry shapes FontFamilyExtractor accepts must be read in
    /// declaration order here too, or the two would disagree about "first".
    func testKeywordObjectEntriesKeepDeclarationOrder() {
        XCTAssertTrue(MonospaceUAFontSize.appliesToFamily(
            .array([.object(["keyword": .string("monospace")])])))
        XCTAssertFalse(MonospaceUAFontSize.appliesToFamily(
            .array([.object(["name": .string("Courier New")]),
                    .object(["keyword": .string("monospace")])])))
    }

    func testQuotedAndMixedCaseMonospaceStillArmsTheGate() {
        XCTAssertTrue(MonospaceUAFontSize.appliesToFamily(.array([.string("\"Monospace\"")])))
    }

    // MARK: - The property-list resolution

    func testNoFontSizePlusBareMonospaceResolvesTo13() {
        let px = MonospaceUAFontSize.resolvePx(from: props([("FontFamily", bareMonospace)]))
        XCTAssertEqual(px, 13.0)
    }

    /// hyphens-manual-010 shape: monospace AND `font-size: 32px`.
    func testExplicitFontSizeDisarmsTheGate() {
        XCTAssertNil(MonospaceUAFontSize.resolvePx(from: props([
            ("FontFamily", bareMonospace),
            ("FontSize", .object(["px": .double(32)])),
        ])))
    }

    /// A declared size the extractor cannot read is still a SPECIFIED value,
    /// so the `medium` keyword this quirk resolves never applies.
    func testUnparseableFontSizeStillDisarmsTheGate() {
        XCTAssertNil(MonospaceUAFontSize.resolvePx(from: props([
            ("FontFamily", bareMonospace),
            ("FontSize", .object(["nope": .bool(true)])),
        ])))
    }

    func testLastFontFamilyDeclarationDecides() {
        XCTAssertNil(MonospaceUAFontSize.resolvePx(from: props([
            ("FontFamily", bareMonospace),
            ("FontFamily", .array([.string("serif")])),
        ])))
    }

    // MARK: - Consumer 1: the font-relative SIZING base

    /// SpacingContext.fontSizePx is the resolution base for ch/em sizing —
    /// block-ellipsis-001's `width: 63.1ch`, block-ellipsis-029's `margin: 1em`.
    func testSpacingContextBaseBecomes13() {
        let s = StyleBuilder.build(from: props([("FontFamily", bareMonospace)]))
        XCTAssertEqual(s.spacing.context.fontSizePx, 13.0)
    }

    /// The branch the whole committed baseline corpus rides: unchanged 16.
    func testSpacingContextBaseStays16Elsewhere() {
        let s = StyleBuilder.build(from: props([("FontFamily", .array([.string("serif")]))]))
        XCTAssertEqual(s.spacing.context.fontSizePx, 16.0)
    }

    func testDeclaredFontSizeStillWinsTheSpacingBase() {
        let s = StyleBuilder.build(from: props([
            ("FontFamily", bareMonospace),
            ("FontSize", .object(["px": .double(32)])),
        ]))
        XCTAssertEqual(s.spacing.context.fontSizePx, 32.0)
    }

    // MARK: - Consumer 2: the rendered label font size

    func testTextConfigFontSizeBecomes13() {
        let s = StyleBuilder.build(from: props([("FontFamily", bareMonospace)]))
        XCTAssertEqual(s.text.fontSize, 13.0)
    }

    /// Everywhere else the slot must stay nil so every
    /// `textConfig.fontSize ?? 16` bottom-out is byte-identical to before.
    func testTextConfigFontSizeStaysNilElsewhere() {
        let s = StyleBuilder.build(from: props([("FontFamily", courierFirst)]))
        XCTAssertNil(s.text.fontSize)
    }
}

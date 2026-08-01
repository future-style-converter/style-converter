//
//  LineHeightNormalTests.swift
//  Wave 22, lane FONT — pins the DECLARED-`normal` line-height discriminator,
//  the shared three-state line-box decision, and the extractor/applier wiring.
//
//  ## What is being protected
//  `font: 92px Arial` RESETS line-height to `normal` (css-fonts-4 §4.3), and a
//  directly-matching declaration beats an inherited one — so Chromium lays
//  css/css-text-decor/text-decoration-dotted-001.html out on Arial's own
//  metrics (hhea asc 1854 + desc 434 + gap 67 over a 2048 upem = 1.1499em ≈
//  105.8px at 92px), NOT on the browser-ref injection's inherited
//  `line-height: 1.25` (115px) and NOT on the wire's legacy 1.2 compatibility
//  multiplier (110.4px). Until this wave the converter never emitted the reset,
//  the IR carried no LineHeight at all (verified against
//  tools/titan/runs/wave21-final/sections/css-text-decor/per-test-ir/
//  wpt__css-text-decor__text-decoration-dotted-001.json), and iOS applied its
//  ABSENT-line-height calibration — which for that test also CLAMPED the
//  single-line box to wptRefLineBoxPx (20pt) for a 92pt run.
//
//  ## The invariants that must never break
//   1. ABSENT line-height keeps the calibration EXACTLY as before — the whole
//      WPT corpus rides that branch.
//   2. OUTSIDE WPT capture, a declared `normal` keeps resolving through the
//      extractor's historical 1.2× number, so the committed 327-pair dark-stage
//      captures (fixtures/properties/typography/line-height.json
//      `LineHeight_Normal` → tools/visual/baseline/iOS__063_Typography_
//      LineHeight.png) cannot move — this lane may not re-capture them.
//  Both are pinned below.
//
//  Twin of the Compose suite runtimes/compose/src/test/java/com/styleconverter/
//  runtime/typography/LineHeightNormalTest.kt — same rows, same order.
//

import XCTest
@testable import StyleConverterRuntime

final class LineHeightNormalTests: XCTestCase {

    /// The exact `line-height: normal` payload the converter now emits for
    /// `font: 92px Arial, sans-serif` (verified by running :converter:run on
    /// fixtures/wpt/css-text-decor/text-decoration-dotted-001.json).
    private let normalWire = IRValue.object([
        "multiplier": .double(1.2),
        "original": .string("normal")
    ])

    /// A REAL authored `line-height: 1.2` — same multiplier, object `original`.
    private let numberWire = IRValue.object([
        "multiplier": .double(1.2),
        "original": .object(["type": .string("number"), "value": .double(1.2)])
    ])

    // MARK: - The discriminator

    func testDeclaredNormalIsRecognisedOnTheLiveWire() {
        // The whole lane hinges on this shape: `original` is a bare JSON
        // STRING only for the `normal` keyword (LineHeightSerializer.kt).
        XCTAssertTrue(LineHeightNormal.isDeclaredNormal(normalWire))
    }

    func testMultiplierAloneIsNotNormal() {
        // Must stay false, otherwise every unitless line-height in the corpus
        // would silently fall back to font-natural metrics.
        XCTAssertFalse(LineHeightNormal.isDeclaredNormal(numberWire))
    }

    func testLengthPercentageAndGlobalKeywordAreNotNormal() {
        XCTAssertFalse(LineHeightNormal.isDeclaredNormal(
            .object(["original": .object(["type": .string("length"),
                                          "px": .double(24)])])))
        XCTAssertFalse(LineHeightNormal.isDeclaredNormal(
            .object(["multiplier": .double(1.5),
                     "original": .object(["type": .string("percentage"),
                                          "value": .double(150)])])))
        // `line-height: inherit` rides an OBJECT at `original`, never the bare
        // string — the two encodings must not collide.
        XCTAssertFalse(LineHeightNormal.isDeclaredNormal(
            .object(["original": .object(["type": .string("keyword"),
                                          "keyword": .string("inherit")])])))
    }

    func testMalformedAndAbsentPayloadsAreNotNormal() {
        XCTAssertFalse(LineHeightNormal.isDeclaredNormal(nil))
        XCTAssertFalse(LineHeightNormal.isDeclaredNormal(.double(1.2)))
        XCTAssertFalse(LineHeightNormal.isDeclaredNormal(.array([])))
        XCTAssertFalse(LineHeightNormal.isDeclaredNormal(
            .object(["multiplier": .double(1.2)])))
    }

    // MARK: - Cascade form (what the renderer calls)

    func testPropertyListDottedOneShapeReportsNormal() {
        // The dotted-001 component after the FontExpander fix; order copied
        // from the live per-test IR.
        let props = [
            IRProperty(type: "FontSize", data: .object(["px": .double(92)])),
            IRProperty(type: "FontFamily", data: .array([.string("Arial"),
                                                         .string("sans-serif")])),
            IRProperty(type: "LineHeight", data: normalWire)
        ]
        XCTAssertTrue(LineHeightNormal.isDeclaredNormal(props))
    }

    func testPropertyListWithoutLineHeightReportsFalse() {
        // The PRE-fix dotted-001 shape — and every corpus component that never
        // declares line-height. Must stay false so the calibration keeps
        // running for them.
        let props = [
            IRProperty(type: "FontSize", data: .object(["px": .double(92)])),
            IRProperty(type: "FontFamily", data: .array([.string("Arial")]))
        ]
        XCTAssertFalse(LineHeightNormal.isDeclaredNormal(props))
    }

    func testPropertyListLastDeclarationWins() {
        // CSS cascade, both directions.
        XCTAssertTrue(LineHeightNormal.isDeclaredNormal([
            IRProperty(type: "LineHeight", data: numberWire),
            IRProperty(type: "LineHeight", data: normalWire)]))
        XCTAssertFalse(LineHeightNormal.isDeclaredNormal([
            IRProperty(type: "LineHeight", data: normalWire),
            IRProperty(type: "LineHeight", data: numberWire)]))
    }

    // MARK: - The shared three-state table (Compose twin pins the same rows)

    func testLineBoxSourceWptCaptureTable() {
        // Row 3 — a number/length was declared: use it verbatim.
        XCTAssertEqual(LineHeightNormal.lineBoxSource(hasDeclaredValue: true,
                                                      declaredNormal: false,
                                                      wptCapture: true), .declared)
        // Row 2 — `normal` declared: the face's own metrics (THE lane change).
        // `hasDeclaredValue` is TRUE here on purpose: the extractor turns the
        // wire's legacy 1.2 multiplier into a real value even for `normal`, and
        // `.natural` must still beat that stand-in under WPT capture.
        XCTAssertEqual(LineHeightNormal.lineBoxSource(hasDeclaredValue: true,
                                                      declaredNormal: true,
                                                      wptCapture: true), .natural)
        // Row 1 — nothing declared: the WPT calibration, UNCHANGED. This is
        // the row the entire corpus rides.
        XCTAssertEqual(LineHeightNormal.lineBoxSource(hasDeclaredValue: false,
                                                      declaredNormal: false,
                                                      wptCapture: true), .calibrated)
    }

    func testLineBoxSourceOutsideWptCaptureIsByteIdenticalToBefore() {
        // THE 327-pair dark-stage protection. With the flag off the `.natural`
        // row is unreachable: `normal` resolves through the extractor's 1.2×
        // value exactly as it always has, so no committed baseline can move.
        XCTAssertEqual(LineHeightNormal.lineBoxSource(hasDeclaredValue: true,
                                                      declaredNormal: true,
                                                      wptCapture: false), .declared)
        XCTAssertEqual(LineHeightNormal.lineBoxSource(hasDeclaredValue: true,
                                                      declaredNormal: false,
                                                      wptCapture: false), .declared)
        XCTAssertEqual(LineHeightNormal.lineBoxSource(hasDeclaredValue: false,
                                                      declaredNormal: false,
                                                      wptCapture: false), .calibrated)
    }

    // MARK: - Extractor / applier wiring

    func testExtractorFlagsNormalAndKeepsTheLegacyMultiplier() {
        // BOTH signals travel: the flag (so the renderer can apply the CSS
        // reading under WPT capture) AND the wire's legacy 1.2 multiplier (so
        // the dark-stage baselines that were captured with a 1.2 box do not
        // move). Dropping the multiplier here would be a baseline-moving edit.
        let cfg = LineHeightExtractor.extract(from: [
            IRProperty(type: "LineHeight", data: normalWire)])
        XCTAssertEqual(cfg?.isNormalKeyword, true)
        XCTAssertNil(cfg?.px)
        XCTAssertEqual(cfg?.multiplier, 1.2)
    }

    func testExtractorKeepsRealNumbersAndLengths() {
        // Regression guard — a real authored value must still be carried.
        let mult = LineHeightExtractor.extract(from: [
            IRProperty(type: "LineHeight", data: .object([
                "multiplier": .double(2),
                "original": .object(["type": .string("number"),
                                     "value": .double(2)])]))])
        XCTAssertEqual(mult?.multiplier, 2)
        XCTAssertEqual(mult?.isNormalKeyword, false)

        let px = LineHeightExtractor.extract(from: [
            IRProperty(type: "LineHeight", data: .object(["px": .double(40)]))])
        XCTAssertEqual(px?.px, 40)
        XCTAssertEqual(px?.isNormalKeyword, false)
    }

    func testApplierPublishesTheNormalFlagAlongsideTheNumber() {
        // The renderer picks between them (WPT → flag, elsewhere → number), so
        // the applier must publish BOTH — not one instead of the other.
        var agg = TypographyAggregate()
        LineHeightApplier.contribute(
            LineHeightConfig(multiplier: 1.2, isNormalKeyword: true), into: &agg)
        XCTAssertTrue(agg.lineHeightIsNormal)
        XCTAssertEqual(agg.lineHeightMultiplier, 1.2)
        XCTAssertTrue(agg.touched)
    }

    func testApplierClearsTheNormalFlagForANumericDeclaration() {
        // Last-write-wins: a numeric line-height must not inherit a stale flag
        // from a previous fold (the renderer would then ignore its number).
        var agg = TypographyAggregate()
        agg.lineHeightIsNormal = true
        LineHeightApplier.contribute(LineHeightConfig(px: 40), into: &agg)
        XCTAssertFalse(agg.lineHeightIsNormal)
        XCTAssertEqual(agg.lineHeightPx, 40)
    }

    // MARK: - The calibration point itself

    func testEffectiveLineHeightDeclaredNormalBeatsTheRefLineBox() {
        // THE lane change. `declared: 110.4` is what the extractor produces for
        // `line-height: normal` at 92pt (the wire's 1.2 multiplier); under WPT
        // capture the keyword must win over it and return nil = natural metrics.
        XCTAssertNil(ComponentRenderer.effectiveLineHeight(
            declared: 110.4, declaredNormal: true, wptCaptureMode: true),
            "declared `normal` must defer to the face, not to 1.2× or the pin")
        // ABSENT line-height keeps the 20pt calibration EXACTLY as before —
        // the invariant the whole corpus rides.
        XCTAssertEqual(ComponentRenderer.effectiveLineHeight(
            declared: nil, declaredNormal: false, wptCaptureMode: true),
            ComponentRenderer.wptRefLineBoxPx)
    }

    func testEffectiveLineHeightOutsideWptIsByteIdenticalToBefore() {
        // THE 327-pair dark-stage protection: with the flag off, a declared
        // `normal` keeps returning the extractor's 1.2× number (110.4 at 92pt),
        // which is precisely what the committed iOS baselines were captured
        // with. No committed capture can move.
        XCTAssertEqual(ComponentRenderer.effectiveLineHeight(
            declared: 110.4, declaredNormal: true, wptCaptureMode: false), 110.4)
        XCTAssertNil(ComponentRenderer.effectiveLineHeight(
            declared: nil, declaredNormal: false, wptCaptureMode: false))
    }

    func testEffectiveLineHeightDeclaredNumberStillWins() {
        // A real authored number, in both capture modes.
        XCTAssertEqual(ComponentRenderer.effectiveLineHeight(
            declared: 30, declaredNormal: false, wptCaptureMode: true), 30)
        XCTAssertEqual(ComponentRenderer.effectiveLineHeight(
            declared: 30, declaredNormal: false, wptCaptureMode: false), 30)
    }
}

//
//  LhUnitLineHeightTests.swift
//  StyleConverterRuntimeTests
//
//  Wave-43 lane V3 — the `lh` unit's line-height source (css-values-4
//  §6.1.1: lh = the element's USED line-height, not a hardcoded 1.2em).
//  Compose twin: spacing/LhUnitLineHeightTest.kt. Wire shapes are copied
//  VERBATIM from the live wave42-final IR (css-overflow/per-test-ir/
//  wpt__css-overflow__line-clamp__discard__discard-multicol-001.json):
//    {"type":"FontFamily","data":["monospace"]}
//    {"type":"Height","data":{"type":"length","original":{"v":2,"u":"LH"}}}
//  Never invent wire shapes.
//

import XCTest
// @testable: the resolver + pick helpers are internal to the module.
@testable import StyleConverterRuntime

final class LhUnitLineHeightTests: XCTestCase {

    // Resolve a relative value straight through the shared resolver.
    private func px(_ v: LengthValue, _ ctx: SpacingContext) -> CGFloat? {
        if case .px(let n) = SpacingResolver.resolve(v, ctx: ctx, isPadding: false) {
            return n
        }
        return nil
    }

    // ── The pure three-state pick ───────────────────────────────────────────

    func testDeclaredTypedLineHeightWinsVerbatimInBothModes() {
        // Author value beats every calibration (css-values-4 §6.1.1 — the
        // computed line-height of the element IS the lh basis).
        XCTAssertEqual(LhUnitLineHeight.usedLineHeightPx(
            declaredPx: 32, declaredNormal: false, fontSizePx: 16, wptCapture: true), 32)
        XCTAssertEqual(LhUnitLineHeight.usedLineHeightPx(
            declaredPx: 32, declaredNormal: false, fontSizePx: 16, wptCapture: false), 32)
    }

    func testDeclaredNormalTakesTheCalibratedRatioUnderWptCaptureOnly() {
        // Under capture the keyword's extracted 1.2 stand-in must NOT pose
        // as an author value — the ref's 1.25 ratio applies (20 @16px).
        XCTAssertEqual(LhUnitLineHeight.usedLineHeightPx(
            declaredPx: 19.2, declaredNormal: true, fontSizePx: 16, wptCapture: true)!,
            20, accuracy: 1e-9)
        // Outside capture the extracted 1.2 stand-in wins as DECLARED —
        // numerically the SAME 19.2 the legacy fallback computed, so the
        // dark stage cannot move (LineHeightNormal's "elsewhere" column).
        XCTAssertEqual(LhUnitLineHeight.usedLineHeightPx(
            declaredPx: 19.2, declaredNormal: true, fontSizePx: 16, wptCapture: false)!,
            19.2, accuracy: 1e-9)
    }

    func testAbsentLineHeightCalibratesUnderCaptureAndStaysNilOutside() {
        // Bare text under WPT capture lays on the pinned ref grid
        // (wptRefLineHeightRatio × font), so lh must measure that grid.
        XCTAssertEqual(LhUnitLineHeight.usedLineHeightPx(
            declaredPx: nil, declaredNormal: false, fontSizePx: 13, wptCapture: true)!,
            16.25, accuracy: 1e-9)
        // Outside capture: nil — the resolver keeps its historical
        // 1.2 × font-size arm and every committed baseline is untouched.
        XCTAssertNil(LhUnitLineHeight.usedLineHeightPx(
            declaredPx: nil, declaredNormal: false, fontSizePx: 13, wptCapture: false))
    }

    // ── The resolver consumes the threaded channel ──────────────────────────

    func testLhResolvesAgainstTheThreadedUsedLineHeight() {
        // The discard-multicol-001 wire: `height: 2lh` decodes to the LH
        // relative the resolver sees…
        let v = extractLength(.object([
            "type": .string("length"),
            "original": .object(["v": .double(2), "u": .string("LH")]),
        ]))
        guard case .relative(2, .lh, nil) = v else {
            return XCTFail("expected .relative(2, .lh, nil), got \(v)")
        }
        // …and 2lh on the WPT-threaded monospace channel (13 × 1.25 =
        // 16.25) is 32.5 — the ref's measured 33px content box, against
        // the wave-42 resolution of 31.2 that clipped the second row.
        var ctx = SpacingContext()
        ctx.fontSizePx = 13
        ctx.lineHeightPx = 16.25
        XCTAssertEqual(px(v, ctx)!, 32.5, accuracy: 1e-9)
    }

    func testLhKeepsTheHistoricalFallbackWhenNoChannelIsThreaded() {
        // The dark-stage byte-stability pin: a nil channel resolves
        // exactly as wave 42 did — 2 × 1.2 × 13 = 31.2.
        var ctx = SpacingContext()
        ctx.fontSizePx = 13
        XCTAssertEqual(px(.relative(value: 2, unit: .lh, pxFallback: nil), ctx)!,
                       31.2, accuracy: 1e-9)
    }

    func testCalcLhStaysInLockstepWithTheBareUnit() {
        // The lockstep rule: calc(1lh + 2px) and a bare 1lh must agree —
        // both read the same threaded channel (16.25 + 2 = 18.25)…
        var ctx = SpacingContext()
        ctx.fontSizePx = 13
        ctx.lineHeightPx = 16.25
        XCTAssertEqual(SpacingCalcEvaluator.evalPx("calc(1lh + 2px)", ctx: ctx)!,
                       18.25, accuracy: 1e-9)
        // …and both fall back to 1.2em together when it is nil.
        var dark = SpacingContext()
        dark.fontSizePx = 13
        XCTAssertEqual(SpacingCalcEvaluator.evalPx("calc(1lh + 2px)", ctx: dark)!,
                       17.6, accuracy: 1e-6)
    }

    func testRlhIsDeliberatelyUnmovedByTheLhChannel() {
        // No capture exercises rlh and the root is never styled — the 1.2
        // root ratio stays pinned even when an element channel is present.
        var ctx = SpacingContext()
        ctx.fontSizePx = 13
        ctx.lineHeightPx = 16.25
        XCTAssertEqual(px(.relative(value: 1, unit: .rlh, pxFallback: nil), ctx)!,
                       19.2, accuracy: 1e-9)
    }

    // ── The pick agrees with the text grid's own resolution ─────────────────

    func testPickMatchesEffectiveLineHeightOnEveryRow() {
        // The lh basis and the text line grid must be the SAME number in
        // every state where both exist, or a `height: 2lh` box could
        // disagree with the two text rows inside it. effectiveLineHeight
        // returns nil for .natural (face metrics) — the one state the
        // numeric pick approximates with the calibrated ratio (documented
        // in LhUnitLineHeight's header), so it is excluded here.
        // DECLARED row, both modes:
        XCTAssertEqual(
            LhUnitLineHeight.usedLineHeightPx(declaredPx: 32, declaredNormal: false,
                                              fontSizePx: 16, wptCapture: true),
            ComponentRenderer.effectiveLineHeight(declared: 32, declaredNormal: false,
                                                  fontSizePx: 16, wptCaptureMode: true)
                .map(Double.init))
        // CALIBRATED row under capture (13px font → 16.25):
        XCTAssertEqual(
            LhUnitLineHeight.usedLineHeightPx(declaredPx: nil, declaredNormal: false,
                                              fontSizePx: 13, wptCapture: true),
            ComponentRenderer.effectiveLineHeight(declared: nil, declaredNormal: false,
                                                  fontSizePx: 13, wptCaptureMode: true)
                .map(Double.init))
    }
}

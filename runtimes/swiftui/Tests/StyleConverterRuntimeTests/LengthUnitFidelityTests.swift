//
//  LengthUnitFidelityTests.swift
//  StyleConverterRuntimeTests
//
//  Wave-18 lane 2 — length-unit fidelity pins (the cross-platform pin
//  table P1–P13; the Compose twin lives in SpacingResolveLengthUnitsTest.kt
//  and ContentBoxPercentInflationTest.kt). Wire shapes are copied VERBATIM
//  from the live wave18-gate IRs:
//    * ch width — css-overflow/per-test-ir/wpt__css-overflow__block-
//      ellipsis-001.json: {"type":"length","original":{"v":63.1,"u":"CH"}}
//    * bare-number percent padding — css-sizing/per-test-ir/wpt__css-
//      sizing__abspos-auto-sizing-fit-content-percentage-003.json:
//      {"type":"PaddingLeft","data":50}
//  Never invent wire shapes.
//

import XCTest
// @testable: the resolver + metrics helpers are internal to the module.
@testable import StyleConverterRuntime

final class LengthUnitFidelityTests: XCTestCase {

    // Default context: fontSize 16, viewport 390×844, no containing block.
    private let ctx = SpacingContext()

    // Resolve a relative value straight through the shared resolver.
    private func px(_ v: LengthValue, _ ctx: SpacingContext,
                    isPadding: Bool = true) -> CGFloat? {
        if case .px(let n) = SpacingResolver.resolve(v, ctx: ctx, isPadding: isPadding) {
            return n
        }
        return nil
    }

    // ── Wire-shape pins (live IR, never invented) ───────────────────────────

    func testChWidthDecodesFromTheLiveBlockEllipsisWire() {
        // The EXACT Width payload the converter emits for `width: 63.1ch`.
        let v = extractLength(.object([
            "type": .string("length"),
            "original": .object(["v": .double(63.1), "u": .string("CH")]),
        ]))
        guard case .relative(63.1, .ch, nil) = v else {
            return XCTFail("expected .relative(63.1, .ch, nil), got \(v)")
        }
    }

    func testBareNumberPaddingDecodesToPercent() {
        // `padding-left: 50%` ships as the bare number 50 on the wire.
        let v = extractLengthPercentDefault(.double(50))
        guard case .relative(50, .percent, nil) = v else {
            return XCTFail("expected .relative(50, .percent, nil), got \(v)")
        }
    }

    // ── P1: ch — measured advance, spec 0.5em fallback ──────────────────────

    func testChFallsBackToHalfAnEm() {
        // css-values-4 §6.1.3: '0' is "assumed to be 0.5em wide" → 63.1×8.
        // The OLD resolver treated ch as 1em → 63.1×16 ≈ 1010px (wrong box
        // width AND wrong wrap points on block-ellipsis-001).
        let v = LengthValue.relative(value: 63.1, unit: .ch, pxFallback: nil)
        XCTAssertEqual(px(v, ctx)!, 504.8, accuracy: 1e-3)
    }

    func testChUsesMeasuredAdvanceWhenProvided() {
        // The measured advance must win over the em approximation.
        var measured = ctx
        measured.chAdvancePx = 9.6
        let v = LengthValue.relative(value: 63.1, unit: .ch, pxFallback: nil)
        XCTAssertEqual(px(v, measured)!, 63.1 * 9.6, accuracy: 1e-3)
    }

    func testChUnitMetricsMeasuresARealAdvance() {
        // On-device metrics: SF Mono's '0' advance at 16px is ~0.6em. The
        // measurement must be positive and clearly BELOW 1em (the old
        // wrong basis) — this is the metric that shrinks 63.1ch from
        // ~1010px to the ~600px neighborhood of the browser ref.
        let adv = ChUnitMetrics.zeroAdvancePx(
            rounded: false, monospaced: true, serif: false, sizePx: 16)
        XCTAssertNotNil(adv)
        XCTAssertGreaterThan(adv!, 0)
        XCTAssertLessThan(adv!, 16)
    }

    func testUsesChGateFiresOnlyOnCh() {
        // The StyleBuilder measurement gate — byte-stability for the
        // ch-free corpus (no CoreText work unless a value consumes it).
        XCTAssertTrue(ChUnitMetrics.usesCh(
            [.relative(value: 2, unit: .ch, pxFallback: nil)]))
        XCTAssertFalse(ChUnitMetrics.usesCh(
            [.exact(px: 20), .relative(value: 50, unit: .percent, pxFallback: nil), nil]))
    }

    // ── P2–P6: the font-relative fallback constants ─────────────────────────

    func testExResolvesToHalfAnEm() {
        // §6.1.3 x-height fallback: 0.5em (was 1em on iOS).
        let v = LengthValue.relative(value: 4, unit: .ex, pxFallback: nil)
        XCTAssertEqual(px(v, ctx)!, 32, accuracy: 1e-6)
    }

    func testIcAndCapResolveToOneEm() {
        // ic: §6.1.3 mandates 1em; cap: documented 1em approximation.
        XCTAssertEqual(px(.relative(value: 2, unit: .ic, pxFallback: nil), ctx)!, 32, accuracy: 1e-6)
        XCTAssertEqual(px(.relative(value: 2, unit: .cap, pxFallback: nil), ctx)!, 32, accuracy: 1e-6)
    }

    func testLhAndRlhResolveToOnePointTwoEms() {
        // `normal` line-height ≈ 1.2 × font-size (UA default sheets).
        XCTAssertEqual(px(.relative(value: 1, unit: .lh, pxFallback: nil), ctx)!, 19.2, accuracy: 1e-6)
        XCTAssertEqual(px(.relative(value: 1, unit: .rlh, pxFallback: nil), ctx)!, 19.2, accuracy: 1e-6)
    }

    // ── P10/P11/P12: the percent basis tri-state ────────────────────────────

    func testPercentBasisUsesDefiniteChannelInWptCapture() {
        // P10 — the env-threaded containing block wins.
        var c = ctx
        c.containingBlockWidthPx = 200
        XCTAssertEqual(
            SpacingResolver.percentBasisPx(ctx: c, wptCaptureMode: true), 200)
    }

    func testPercentBasisIsZeroWhenIndefiniteInWptCapture() {
        // P11 — css-position-3 §5.1: indefinite basis → percents behave as
        // 0. This is what keeps `padding-left: 50%` / `margin-left: -50%`
        // inside a fit-content abspos box at the ref's geometry.
        XCTAssertEqual(
            SpacingResolver.percentBasisPx(ctx: ctx, wptCaptureMode: true), 0)
    }

    func testPercentBasisIsNilOutsideWptCapture() {
        // P12 — no static basis outside WPT capture, even a definite one:
        // the appliers keep their legacy GeometryReader lane so the frozen
        // dark-stage corpus renders byte-identically.
        var c = ctx
        c.containingBlockWidthPx = 200
        XCTAssertNil(SpacingResolver.percentBasisPx(ctx: c, wptCaptureMode: false))
        XCTAssertNil(SpacingResolver.percentBasisPx(ctx: ctx, wptCaptureMode: false))
    }

    // ── P13: intrinsic-sizing percent bands resolve against zero ────────────

    func testPercentPaddingContributesZeroToContentBoxInflation() {
        // The abspos-003 child, verbatim wire: width 100px + padding-left
        // 50% (bare number). Under the WPT content-box fold the frame must
        // stay 100 — the old viewport basis inflated it to 295.
        var style = StyleBuilder.build(from: [
            IRProperty(type: "Width", data: .object([
                "type": .string("length"), "px": .double(100)])),
            IRProperty(type: "Height", data: .object([
                "type": .string("length"), "px": .double(100)])),
            IRProperty(type: "PaddingLeft", data: .double(50)),
        ])
        // The WPT fold ComponentRenderer applies (effectiveBoxSizing).
        style.size.boxSizing = .contentBox
        // No containing block threaded (the abspos fit-content ancestor
        // publishes nil) → the percent band is cyclic → zero (§5.2.1).
        XCTAssertEqual(StyleBuilder.contentBoxInflation(style).h, 0)
        XCTAssertEqual(StyleBuilder.contentBoxInflation(style).v, 0)
    }

    func testAbsolutePaddingStillInflatesTheContentBoxFrame() {
        // Sanity guard: the zero rule is PERCENT-only — px bands keep the
        // established inflation arithmetic (100 + 20 → h band 20).
        var style = StyleBuilder.build(from: [
            IRProperty(type: "Width", data: .object([
                "type": .string("length"), "px": .double(100)])),
            IRProperty(type: "PaddingLeft", data: .object(["px": .double(20)])),
        ])
        style.size.boxSizing = .contentBox
        XCTAssertEqual(StyleBuilder.contentBoxInflation(style).h, 20)
    }

    func testDefinitBasisResolvesIntrinsicPercentBands() {
        // P13's definite branch: a threaded 200px containing block makes
        // the 50% band contribute 100 to the inflation.
        var style = StyleBuilder.build(from: [
            IRProperty(type: "Width", data: .object([
                "type": .string("length"), "px": .double(100)])),
            IRProperty(type: "PaddingLeft", data: .double(50)),
        ])
        style.size.boxSizing = .contentBox
        style.spacing.context.containingBlockWidthPx = 200
        XCTAssertEqual(StyleBuilder.contentBoxInflation(style).h, 100)
    }
}


// Wave 19 follow-up — the line-clamp cap must survive to the label's
// inner .lineLimit call (TextConfig.lineClampLimit), because SwiftUI's
// innermost lineLimit wins and the renderer's label applies one directly
// (ComponentRenderer ~2566). Pins the StyleBuilder mirror on the LIVE
// wire shape {"type":"lines","count":2} from
// wpt__css-overflow__block-ellipsis-001 (wave18-final per-test IR).
final class LineClampBridgeTests: XCTestCase {
    // The clamp reaches TextConfig when the wire declares line-clamp: 2.
    func testLineClampCountReachesTextConfig() throws {
        let json = #"""
        {"id":"t","name":"t","properties":[
          {"type":"LineClamp","data":{"type":"lines","count":2}}
        ]}
        """#
        let comp = try JSONDecoder().decode(IRComponent.self,
                                            from: Data(json.utf8))
        let style = StyleBuilder.build(from: comp.properties)
        XCTAssertEqual(style.text.lineClampLimit, 2,
            "line-clamp count must ride TextConfig into the inner .lineLimit")
    }
    // No clamp declared → nil → the historical unlimited wrap unchanged.
    func testNoClampKeepsUnlimitedWrap() {
        let style = StyleBuilder.build(from: [])
        XCTAssertNil(style.text.lineClampLimit,
            "absent line-clamp must keep .lineLimit(nil) byte-for-byte")
    }
}

//
//  Wave18CleanupParityTests.swift
//  StyleConverterRuntimeTests
//
//  Wave-18 cleanup lane — pins for the three iOS parity fixes surfaced by
//  the executed skeptic probes:
//    B2  — real calc() evaluation (CalcEvaluator, the Compose evalCalc
//          port; Compose twin pins live in SpacingResolveLengthUnitsTest).
//    lane-3 — physical-vs-logical overflow resolves last-write-wins in
//          declaration order (Compose OverflowExtractor parity).
//    clip-003 — outline hoists past the element's OWN overflow clip
//          (StyleBuilder.inChainOutline / hoistedOutline slot pair).
//
//  Wire shapes are copied VERBATIM from a live converter run on minimal
//  fixtures (scratchpad wave18-fixtures.json → out/tmpOutput.json):
//    {"type":"PaddingLeft","data":{"expr":"calc(1em + 4px)"}}
//    {"type":"Width","data":{"type":"expression","expr":"calc(2ch + 4px)"}}
//    {"type":"MarginLeft","data":{"expr":"calc(10vw - 5px)"}}
//    {"type":"PaddingLeft","data":{"expr":"calc(50% + 10px)"}}
//    {"type":"OverflowY","data":"HIDDEN"},{"type":"OverflowBlock","data":"VISIBLE"}
//  (declaration order preserved by the converter). Never invent wire shapes.
//

import XCTest
// @testable: the resolver, evaluator, extractor and StyleBuilder helpers
// under test are internal to the module.
@testable import StyleConverterRuntime

final class Wave18CleanupParityTests: XCTestCase {

    // Default context: fontSize 16, viewport 390×844 → the iOS legacy
    // containing-block base is 390 − 32 = 358 (SpacingContext accessor).
    private let ctx = SpacingContext()

    // Resolve straight through the shared resolver, unwrapping .px.
    private func px(_ v: LengthValue, _ ctx: SpacingContext,
                    isPadding: Bool = true) -> CGFloat? {
        if case .px(let n) = SpacingResolver.resolve(v, ctx: ctx, isPadding: isPadding) {
            return n
        }
        return nil
    }

    // Live-wire helper shared with OverflowAxisClipTests.
    private func props(_ p: [(String, IRValue)]) -> [IRProperty] {
        p.map { IRProperty(type: $0.0, data: $0.1) }
    }

    // ── B2: wire-shape pins (live IR, never invented) ───────────────────────

    func testPaddingCalcWireDecodesToCalc() {
        // The EXACT PaddingLeft payload for `padding-left: calc(1em + 4px)`.
        let v = extractLength(.object(["expr": .string("calc(1em + 4px)")]))
        guard case .calc("calc(1em + 4px)") = v else {
            return XCTFail("expected .calc, got \(v)")
        }
    }

    func testWidthExpressionWireDecodesToCalc() {
        // The EXACT Width payload for `width: calc(2ch + 4px)` — the
        // sizing lane wraps the expr in {"type":"expression"}.
        let v = extractLength(.object([
            "type": .string("expression"),
            "expr": .string("calc(2ch + 4px)"),
        ]))
        guard case .calc("calc(2ch + 4px)") = v else {
            return XCTFail("expected .calc, got \(v)")
        }
    }

    // ── B2: the evaluator matches Compose evalCalc on the live wires ────────

    func testCalcEmPlusPxResolvesLikeCompose() {
        // calc(1em + 4px) @16px font = 20 — the value Compose renders and
        // the old naiveCalcPx dropped to .skip/0.
        XCTAssertEqual(px(.calc(expression: "calc(1em + 4px)"), ctx)!, 20, accuracy: 1e-6)
    }

    func testCalcChPlusPxUsesTheChPinTable() {
        // Unmeasured ch → the css-values-4 §6.1.1 0.5em assumption:
        // 2×8 + 4 = 20 (the Compose-side full resolution the skeptic saw).
        XCTAssertEqual(px(.calc(expression: "calc(2ch + 4px)"), ctx)!, 20, accuracy: 1e-6)
        // Measured advance wins — 2×10 + 4 = 24, the exact Compose pin
        // (SpacingResolveLengthUnitsTest `calc resolves ch …`).
        var measured = ctx
        measured.chAdvancePx = 10
        XCTAssertEqual(px(.calc(expression: "calc(2ch + 4px)"), measured)!, 24, accuracy: 1e-6)
    }

    func testCalcViewportAndPtUnitsResolve() {
        // calc(10vw - 5px) on the 390 canvas = 39 − 5 = 34 (live margin wire).
        XCTAssertEqual(px(.calc(expression: "calc(10vw - 5px)"), ctx, isPadding: false)!,
                       34, accuracy: 1e-6)
        // 3pt = 4px on the converter's 96-dpi baseline (4/3 ratio).
        XCTAssertEqual(px(.calc(expression: "calc(3pt)"), ctx)!, 4, accuracy: 1e-4)
    }

    func testCalcPercentRidesTheLegacyBase() {
        // Skeptic B3 — the SHARED asymmetry: calc-% never enters the WPT
        // percent lane on either native (the applier gates only see bare
        // percents), so % inside calc resolves against the platform's
        // LEGACY static base. iOS legacy base = containingBlockWidth
        // (358 here): 50% × 358 + 10 = 189. (Compose's twin pin is 199 on
        // its 390 viewport base — each side's own frozen legacy basis.)
        XCTAssertEqual(px(.calc(expression: "calc(50% + 10px)"), ctx)!, 189, accuracy: 1e-4)
        // A threaded ancestor containing block wins over the canvas
        // arithmetic, mirroring Compose's parentWidthPx-first rule.
        var threaded = ctx
        threaded.containingBlockWidthPx = 200
        XCTAssertEqual(px(.calc(expression: "calc(50% + 10px)"), threaded)!, 110, accuracy: 1e-4)
    }

    func testCalcGrammarMatchesComposeParser() {
        // Precedence: * binds tighter than + → 2×10 + 4 = 24.
        XCTAssertEqual(px(.calc(expression: "calc(2 * 10px + 4px)"), ctx)!, 24, accuracy: 1e-6)
        // Division: 100 / 4 = 25 (divide-by-zero would yield 0, not trap).
        XCTAssertEqual(px(.calc(expression: "calc(100px / 4)"), ctx)!, 25, accuracy: 1e-6)
        // Nested calc() strips one wrapper per level: (5+5) × 2 = 20.
        XCTAssertEqual(px(.calc(expression: "calc(calc(5px + 5px) * 2)"), ctx)!, 20, accuracy: 1e-6)
        // §10.1 whitespace rule: "10px-5px" is NOT a subtraction — the
        // parser stops after the first term (Compose parity: returns 10).
        XCTAssertEqual(px(.calc(expression: "calc(10px-5px)"), ctx)!, 10, accuracy: 1e-6)
        // Unknown unit poisons only its own operand (Compose safe-zero
        // else-arm): 4px + 0 = 4.
        XCTAssertEqual(px(.calc(expression: "calc(4px + 2foo)"), ctx)!, 4, accuracy: 1e-6)
    }

    func testCalcClampAndSkipLanes() {
        // Negative computed PADDING clamps to 0 (css-box non-negative rule)…
        XCTAssertEqual(px(.calc(expression: "calc(5px - 10px)"), ctx)!, 0, accuracy: 1e-6)
        // …while margins keep their sign.
        XCTAssertEqual(px(.calc(expression: "calc(5px - 10px)"), ctx, isPadding: false)!,
                       -5, accuracy: 1e-6)
        // Malformed expressions route to the tracked .skip (renders 0 —
        // the same pixels as Compose's catch-arm 0f, but auditable).
        guard case .skip = SpacingResolver.resolve(.calc(expression: "calc(banana)"),
                                                   ctx: ctx, isPadding: true) else {
            return XCTFail("malformed calc must .skip")
        }
    }

    func testCalcPercentIntrinsicBasisMatchesBarePercent() {
        // Skeptic follow-up (executed cross-native probe): the INTRINSIC-
        // sizing helpers (P13 lane) resolve an indefinite percent basis to
        // ZERO (css-sizing-3 §5.2.1) — and a calc-% must follow the same
        // rule as a bare %, like Compose's evalCalc does through its
        // percentIndefiniteAsZero context (Compose pin: calc(50% + 10px)
        // → 10 under PIZ). Pre-fix iOS rode the legacy 358 canvas base
        // here (189), diverging from both Compose and its own bare-%.
        var style = ComponentStyle()
        var pad = PaddingConfig()
        pad.left = .calc(expression: "calc(50% + 10px)")
        pad.right = .relative(value: 50, unit: .percent, pxFallback: nil)
        style.spacing.padding = pad
        // Indefinite basis: bare 50% → 0, calc(50% + 10px) → 0 + 10 = 10.
        XCTAssertEqual(StyleBuilder.horizontalPaddingPx(style), 10, accuracy: 1e-6)
        // Definite ancestor basis (200): both forms use it — calc gives
        // 0.5×200 + 10 = 110, bare gives 100 → 210 total (P10 parity).
        style.spacing.context.containingBlockWidthPx = 200
        XCTAssertEqual(StyleBuilder.horizontalPaddingPx(style), 210, accuracy: 1e-6)
        // The APPLIER lane is untouched: the default context still rides
        // the legacy 358 base (the pinned B3 number, 189).
        if case .px(let n) = SpacingResolver.resolve(
            .calc(expression: "calc(50% + 10px)"), ctx: ctx, isPadding: true) {
            XCTAssertEqual(n, 189, accuracy: 1e-4)
        } else {
            XCTFail("applier-lane calc must stay resolvable")
        }
    }

    // ── lane-3: physical-vs-logical overflow is last-write-wins ─────────────

    func testOverflowPhysicalThenLogicalLastWriteWins() {
        // Live wire, declaration order: overflow-y:hidden; overflow-block:
        // visible. The cascade's order-of-appearance rule makes the later
        // logical declaration win → visible → NO clip (Compose parity;
        // the old iOS fold let the physical longhand always beat it).
        let cfg = VisibilityExtractor.extract(from: props([
            ("OverflowY", .string("HIDDEN")),
            ("OverflowBlock", .string("VISIBLE")),
        ]))
        XCTAssertEqual(cfg?.overflowY, OverflowKind.visible)
        // The §3.1-coerced clip decision must agree: nothing clips.
        XCTAssertFalse(StyleBuilder.ownOverflowClips(cfg))
    }

    func testOverflowLogicalThenPhysicalLastWriteWins() {
        // Reverse declaration order: the later PHYSICAL longhand wins.
        let cfg = VisibilityExtractor.extract(from: props([
            ("OverflowBlock", .string("VISIBLE")),
            ("OverflowY", .string("HIDDEN")),
        ]))
        XCTAssertEqual(cfg?.overflowY, OverflowKind.hidden)
        // hidden on Y clips (css-overflow-3 §3).
        XCTAssertTrue(StyleBuilder.ownOverflowClips(cfg))
    }

    func testOverflowInlineMapsToXAndStillCascades() {
        // css-logical §4: inline axis → X under horizontal-tb; a later
        // logical write overrides an earlier physical one on the same axis.
        let cfg = VisibilityExtractor.extract(from: props([
            ("OverflowX", .string("HIDDEN")),
            ("OverflowInline", .string("AUTO")),
        ]))
        XCTAssertEqual(cfg?.overflowX, OverflowKind.auto)
        // Logical-only declarations keep mapping (regression guard for the
        // existing EffectsTests logical pin).
        let solo = VisibilityExtractor.extract(from: props([
            ("OverflowBlock", .string("HIDDEN")),
        ]))
        XCTAssertEqual(solo?.overflowY, OverflowKind.hidden)
    }

    // ── clip-003: the outline hoist slot pair ───────────────────────────────

    // A minimal paintable outline (solid 3px) for the slot tests.
    private var redRing: OutlineConfig {
        var o = OutlineConfig()
        o.style = .solid
        return o
    }

    func testOutlineStaysInChainWithoutOwnClip() {
        // No overflow declared → the legacy in-chain slot owns the paint
        // and the hoisted slot is identity — byte-stability for the whole
        // committed corpus (which never combines outline + own clip).
        var style = ComponentStyle()
        style.outline = redRing
        XCTAssertNotNil(StyleBuilder.inChainOutline(style))
        XCTAssertNil(StyleBuilder.hoistedOutline(style))
    }

    func testOutlineHoistsPastOwnOverflowClip() {
        // clip-003 geometry: a square that clips its own content must
        // still paint its outline ring — the hoisted slot takes over and
        // the in-chain slot goes identity (exactly one slot fires).
        var vis = VisibilityConfig()
        vis.overflowX = .clip
        vis.overflowY = .clip
        vis.touched = true
        var style = ComponentStyle()
        style.outline = redRing
        style.visibility = vis
        XCTAssertTrue(StyleBuilder.ownOverflowClips(vis))
        XCTAssertNil(StyleBuilder.inChainOutline(style))
        XCTAssertNotNil(StyleBuilder.hoistedOutline(style))
    }

    func testHiddenElementNeverResurrectsItsOutline() {
        // visibility:hidden is applied BEFORE the hoisted slot in the
        // chain, so the slot must stay empty or the ring would paint on
        // an invisible element (CSS 2.1 §11.2 hides the whole box).
        var vis = VisibilityConfig()
        vis.overflowX = .hidden
        vis.overflowY = .hidden
        vis.visibility = .hidden
        vis.touched = true
        var style = ComponentStyle()
        style.outline = redRing
        style.visibility = vis
        XCTAssertNil(StyleBuilder.inChainOutline(style))
        XCTAssertNil(StyleBuilder.hoistedOutline(style))
    }

    func testHoistDecisionUsesCoercedUsedValues() {
        // scroll/auto also clip (a scroll container always clips, §3) —
        // the hoist must follow the same §3.1-coerced decision the
        // VisibilityApplier makes, never a naive keyword check.
        var vis = VisibilityConfig()
        vis.overflowY = .scroll
        vis.touched = true
        XCTAssertTrue(StyleBuilder.ownOverflowClips(vis))
        // visible/visible (the clip-averse pair) must NOT hoist.
        var visb = VisibilityConfig()
        visb.overflowY = .visible
        visb.touched = true
        XCTAssertFalse(StyleBuilder.ownOverflowClips(visb))
        // Untouched/absent config → initial visible → no hoist.
        XCTAssertFalse(StyleBuilder.ownOverflowClips(nil))
    }
}

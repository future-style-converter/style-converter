//
//  DynamicValuesTests.swift
//  StyleConverterRuntimeTests
//
//  Wave-6 pins (issues #32 + #39):
//    • VariableStore — scope-chain merge with nearest-wins shadowing,
//      case-sensitive names, verbatim (empty-legal) values.
//    • VarSubstitutor — css-variables-1 §2.3: chain lookup, fallback,
//      NESTED fallback, guaranteed-invalid ⇒ nil, cycle safety.
//    • CalcEvaluator — css-values-3 §8: px arithmetic, nesting, mixed
//      units (%, em, rem, vw), × / ÷ typing rules, honest nil.
//    • DynamicValueResolver — the extraction-time rewrite: var colors
//      → srgb, expression lengths → px/percent shapes, em against the
//      INHERITED font size, guaranteed-invalid declarations dropped
//      (unset), static declarations byte-stable.
//    • #39 — SpacingContext geometry from the environment: the
//      host-published rootContainingBlock replaces the hardcoded
//      canvas arithmetic, with the legacy default preserved.
//

import XCTest
@testable import StyleConverterRuntime

final class DynamicValuesTests: XCTestCase {

    // MARK: - Helpers

    /// Decode an [IRProperty] from inline JSON — the decode path is the
    /// production path (same convention as FidelityWave3Tests).
    private func props(_ json: String) -> [IRProperty] {
        try! JSONDecoder().decode([IRProperty].self, from: Data(json.utf8))
    }

    /// Default calc bases used by most pins (canvas-like geometry).
    private var ctx: CalcEvaluator.EvalContext {
        var c = CalcEvaluator.EvalContext()
        c.percentBasisPx = 280 // definite containing block for % pins
        return c
    }

    // MARK: - VariableStore (scope chain)

    func testMergeShadowsInheritedDefinitions() {
        // TK_TwoLevelShadow: mid redefines --accent for its subtree.
        let root = VariableStore.merge(inherited: .empty,
                                       own: ["--accent": "#c0392b", "--gap": "6px"])
        let mid = VariableStore.merge(inherited: root, own: ["--accent": "#1a5276"])
        // Nearest definition wins; unshadowed names flow through.
        XCTAssertEqual(mid.value("--accent"), "#1a5276")
        XCTAssertEqual(mid.value("--gap"), "6px")
        // The outer scope is untouched (stores are immutable values).
        XCTAssertEqual(root.value("--accent"), "#c0392b")
    }

    func testNamesAreCaseSensitiveAndValuesVerbatim() {
        // css-variables-1 §2: `--Brand-Fg` and `--brand-fg` are distinct;
        // values keep their bytes (empty string is a LEGAL value).
        let store = VariableStore.merge(inherited: .empty,
                                        own: ["--Brand-Fg": "#ffffff", "--empty": ""])
        XCTAssertEqual(store.value("--Brand-Fg"), "#ffffff")
        XCTAssertNil(store.value("--brand-fg"))
        XCTAssertEqual(store.value("--empty"), "")
    }

    // MARK: - VarSubstitutor (§2.3)

    func testChainLookupAndFallback() {
        let store = VariableStore(definitions: ["--size": "64px"])
        // Defined → the raw value substitutes verbatim.
        XCTAssertEqual(VarSubstitutor.substitute("var(--size)", store: store), "64px")
        // Undefined + fallback → the fallback substitutes.
        XCTAssertEqual(VarSubstitutor.substitute("var(--nope, #e67e22)", store: store),
                       " #e67e22")
        // Undefined + NO fallback → guaranteed-invalid (nil).
        XCTAssertNil(VarSubstitutor.substitute("var(--nope)", store: store))
    }

    func testNestedFallbackWalksInnerVar() {
        // token-fallbacks TK_NestedFallback: var(--top, var(--mid, #fff)).
        let store = VariableStore(definitions: ["--mid": "#9b59b6"])
        // --top missing → inner var resolves --mid.
        XCTAssertEqual(
            VarSubstitutor.substitute("var(--top, var(--mid, #ffffff))", store: store)?
                .trimmingCharacters(in: .whitespaces),
            "#9b59b6")
        // Both missing → the literal innermost fallback survives.
        XCTAssertEqual(
            VarSubstitutor.substitute("var(--a, var(--b, #16a085))", store: .empty)?
                .trimmingCharacters(in: .whitespaces),
            "#16a085")
    }

    func testVarInsideCalcAndCycleSafety() {
        // Substitution is textual — the calc wrapper stays for the
        // evaluator: calc(var(--u) * 10) → calc(12px * 10).
        let store = VariableStore(definitions: ["--u": "12px"])
        XCTAssertEqual(VarSubstitutor.substitute("calc(var(--u) * 10)", store: store),
                       "calc(12px * 10)")
        // A definition may reference another variable (verbatim value).
        let chain = VariableStore(definitions: ["--a": "var(--b)", "--b": "4px"])
        XCTAssertEqual(VarSubstitutor.substitute("var(--a)", store: chain), "4px")
        // Cycles are guaranteed-invalid, not an infinite loop (§2.3).
        let cycle = VariableStore(definitions: ["--a": "var(--b)", "--b": "var(--a)"])
        XCTAssertNil(VarSubstitutor.substitute("var(--a)", store: cycle))
    }

    // MARK: - CalcEvaluator (§8)

    func testCalcPixelArithmeticAndNesting() {
        // Plain terms and two-term sums.
        XCTAssertEqual(CalcEvaluator.evaluatePx("64px", ctx: ctx), 64)
        XCTAssertEqual(CalcEvaluator.evaluatePx("calc(10px + 4px)", ctx: ctx), 14)
        // calc-mixed golden: nested calc divides the inner result.
        XCTAssertEqual(CalcEvaluator.evaluatePx("calc(calc(100% - 10px) / 2)", ctx: ctx),
                       135) // (280 − 10) / 2
        // Multiplication with a number side (token fixture CU_NestedAndVar).
        XCTAssertEqual(CalcEvaluator.evaluatePx("calc(12px * 10)", ctx: ctx), 120)
    }

    func testCalcMixedUnits() {
        var c = ctx
        c.fontSizePx = 20        // inherited font size (CU_EmInheritance)
        c.rootFontSizePx = 16    // web-harness root
        // % + px against the 280 containing block.
        XCTAssertEqual(CalcEvaluator.evaluatePx("calc(100% - 40px)", ctx: c), 240)
        // em against the inherited size; rem against the root.
        XCTAssertEqual(CalcEvaluator.evaluatePx("calc(1em + 4px)", ctx: c), 24)
        XCTAssertEqual(CalcEvaluator.evaluatePx("calc(2em + 4px)", ctx: c), 44)
        XCTAssertEqual(CalcEvaluator.evaluatePx("calc(2rem + 1px)", ctx: c), 33)
        // Viewport bases come from the (#39) environment geometry.
        c.viewportWidth = 400; c.viewportHeight = 800
        XCTAssertEqual(CalcEvaluator.evaluatePx("calc(10vw + 5vh)", ctx: c), 80)
    }

    func testCalcRefusesUnresolvableForms() {
        // % with an indefinite basis → nil (degrades to unset upstream).
        var c = ctx
        c.percentBasisPx = nil
        XCTAssertNil(CalcEvaluator.evaluatePx("calc(100% - 40px)", ctx: c))
        // length × length has no CSS type (§8.1); ÷ 0 is invalid.
        XCTAssertNil(CalcEvaluator.evaluatePx("calc(2px * 3px)", ctx: ctx))
        XCTAssertNil(CalcEvaluator.evaluatePx("calc(10px / 0)", ctx: ctx))
        // A bare number is not a length.
        XCTAssertNil(CalcEvaluator.evaluatePx("calc(2 * 3)", ctx: ctx))
        // Unknown units refuse rather than guess.
        XCTAssertNil(CalcEvaluator.evaluatePx("calc(2ch + 1px)", ctx: ctx))
    }

    // MARK: - DynamicValueResolver (extraction-time rewrite)

    func testVarColorRewritesToSrgbAndMissingDrops() {
        // token-theme `a` tile + token-fallbacks `missing`/`saved`.
        let store = VariableStore(definitions: ["--tile-a": "#e74c3c"])
        let list = props(#"""
        [
          {"type":"BackgroundColor","data":{"original":"var(--tile-a)"}},
          {"type":"Color","data":{"original":"var(--nope)"}},
          {"type":"BorderTopColor","data":{"original":"var(--nope, #e67e22)"}}
        ]
        """#)
        let out = DynamicValueResolver.resolve(properties: list, variables: store, calc: ctx)
        // Undefined --nope with no fallback drops (unset) → 2 survive.
        XCTAssertEqual(out.count, 2)
        // The defined reference became a static srgb the normal color
        // extractor reads (#e74c3c = 231,76,60).
        guard case .srgb(let r, let g, let b, let a) = extractColor(out[0].data) else {
            return XCTFail("expected srgb after var resolution")
        }
        XCTAssertEqual(r, 231.0 / 255.0, accuracy: 1e-9)
        XCTAssertEqual(g, 76.0 / 255.0, accuracy: 1e-9)
        XCTAssertEqual(b, 60.0 / 255.0, accuracy: 1e-9)
        XCTAssertEqual(a, 1.0)
        // The fallback lane saved the third declaration (#e67e22).
        XCTAssertEqual(out[1].type, "BorderTopColor")
        guard case .srgb = extractColor(out[1].data) else {
            return XCTFail("fallback color should resolve to srgb")
        }
    }

    func testExpressionLengthsRewriteToConcreteShapes() {
        // token-theme `b` tile: width/height/padding all var-driven.
        let store = VariableStore(definitions: ["--size": "64px", "--pad": "8px"])
        let list = props(#"""
        [
          {"type":"Width","data":{"type":"expression","expr":"var(--size)"}},
          {"type":"PaddingTop","data":{"expr":"var(--pad)"}},
          {"type":"MarginTop","data":{"expr":"calc(var(--pad) * 2)"}},
          {"type":"Height","data":{"type":"length","px":40.0}}
        ]
        """#)
        let out = DynamicValueResolver.resolve(properties: list, variables: store, calc: ctx)
        XCTAssertEqual(out.count, 4)
        // Sizing lane sees exact pixels.
        XCTAssertEqual(extractLength(out[0].data), .exact(px: 64))
        // Spacing lane too (the {px:N} shape).
        XCTAssertEqual(extractLengthPercentDefault(out[1].data), .exact(px: 8))
        // var-in-calc multiplies through the evaluator.
        XCTAssertEqual(extractLengthPercentDefault(out[2].data), .exact(px: 16))
        // Static values pass through untouched (byte-stable corpus).
        XCTAssertEqual(extractLength(out[3].data), .exact(px: 40))
    }

    func testPercentWidthStaysSymbolicAndHeightPercentDrops() {
        // A var holding "50%" must stay percentage so SizeApplier
        // resolves it against the LIVE containing block, not a
        // snapshot; a %-height calc has no basis and drops (auto).
        let store = VariableStore(definitions: ["--w": "50%"])
        let list = props(#"""
        [
          {"type":"Width","data":{"type":"expression","expr":"var(--w)"}},
          {"type":"Height","data":{"type":"expression","expr":"calc(100% - 10px)"}}
        ]
        """#)
        let out = DynamicValueResolver.resolve(properties: list, variables: store, calc: ctx)
        XCTAssertEqual(out.count, 1)
        XCTAssertEqual(extractLength(out[0].data),
                       .relative(value: 50, unit: .percent, pxFallback: nil))
    }

    func testEmResolvesAgainstInheritedFontSize() {
        // CU_EmInheritance: parent font-size 20px; child height
        // calc(1em + 4px) → 24. The element's own DYNAMIC font-size
        // resolves against the INHERITED size first (css-values-4 §6.1)
        // and then serves as the em basis for other properties.
        let store = VariableStore(definitions: ["--type": "18px"])
        let list = props(#"""
        [
          {"type":"FontSize","data":{"original":{"type":"expression","expr":"var(--type)"}}},
          {"type":"Height","data":{"type":"expression","expr":"calc(1em + 4px)"}}
        ]
        """#)
        let out = DynamicValueResolver.resolve(properties: list, variables: store,
                                               calc: ctx, inheritedFontSizePx: 20)
        XCTAssertEqual(out.count, 2)
        // FontSize var → 18px concrete.
        XCTAssertEqual(ValueExtractors.extractPx(out[0].data), 18)
        // Height 1em uses the ELEMENT size (18), not the inherited 20.
        XCTAssertEqual(extractLength(out[1].data), .exact(px: 22))

        // Without an own FontSize, em falls back to the inherited size.
        let noFs = props(#"[{"type":"Height","data":{"type":"expression","expr":"calc(2em + 4px)"}}]"#)
        let out2 = DynamicValueResolver.resolve(properties: noFs, variables: .empty,
                                                calc: ctx, inheritedFontSizePx: 20)
        XCTAssertEqual(extractLength(out2[0].data), .exact(px: 44))
    }

    func testFontSizeEmUsesInheritedBasisNotItself() {
        // calc-mixed golden: font-size: calc(1em + 2px) — em on
        // font-size refers to the PARENT's size (css-values-4 §6.1).
        let list = props(#"[{"type":"FontSize","data":{"original":{"type":"expression","expr":"calc(1em + 2px)"}}}]"#)
        let out = DynamicValueResolver.resolve(properties: list, variables: .empty,
                                               calc: ctx, inheritedFontSizePx: 20)
        XCTAssertEqual(ValueExtractors.extractPx(out[0].data), 22)
    }

    func testGenericBorderRadiusRetypes() {
        // token-theme `c` tile: the radius longhand parser still ships
        // var() as Generic — the resolver retypes it so the normal
        // BorderRadiusExtractor lane renders the rounded corners.
        let store = VariableStore(definitions: ["--pad": "8px"])
        let list = props(#"""
        [
          {"type":"Generic","data":{"propertyName":"border-top-left-radius","rawValue":"var(--pad)","_unmapped":true}},
          {"type":"Generic","data":{"propertyName":"unknown-prop","rawValue":"var(--pad)","_unmapped":true}}
        ]
        """#)
        let out = DynamicValueResolver.resolve(properties: list, variables: store, calc: ctx)
        // The radius became typed; the unknown Generic passed through
        // (still Generic — untouched, exactly as before wave 6).
        XCTAssertEqual(out.map(\.type), ["BorderTopLeftRadius", "Generic"])
        XCTAssertEqual(ValueExtractors.extractPx(out[0].data), 8)
        let cfg = BorderRadiusExtractor.extract(from: out)
        XCTAssertNotNil(cfg)
    }

    func testVarFnColorClassification() {
        // An unresolved var() color reaching extraction classifies as
        // .dynamic(.varFn), never .unknown — the honest drop signal.
        let raw = props(#"[{"type":"BackgroundColor","data":{"original":"var(--tile-a)"}}]"#)
        guard case .dynamic(let kind, _) = extractColor(raw[0].data) else {
            return XCTFail("expected dynamic classification for var color")
        }
        if case .varFn = kind {} else { XCTFail("expected .varFn, got \(kind)") }
    }

    // MARK: - #39 viewport-from-environment

    func testSpacingContextGeometryFromEnvironmentChannel() {
        // Legacy default (no host publication): canvas arithmetic keeps
        // the pre-wave-6 358 basis (Sizing_PercentWidth baseline pin).
        var ctx = SpacingContext()
        XCTAssertEqual(ctx.containingBlockWidth, 358)
        // Host-published initial containing block wins over the legacy
        // arithmetic (the harness publishes 358; an app its own width).
        ctx.rootContainingBlockPx = 500
        XCTAssertEqual(ctx.containingBlockWidth, 500)
        // An ancestor-published containing block still wins over both.
        ctx.containingBlockWidthPx = 260
        XCTAssertEqual(ctx.containingBlockWidth, 260)
        // The StyleViewport default fallback: rootContainingBlock is
        // the full width unless the host insets it (CSS: the initial
        // containing block spans the viewport).
        let vp = StyleViewport(width: 800, height: 600)
        XCTAssertEqual(vp.rootContainingBlock, 800)
        let inset = StyleViewport(width: 390, height: 844, rootContainingBlock: 358)
        XCTAssertEqual(inset.rootContainingBlock, 358)
    }
}

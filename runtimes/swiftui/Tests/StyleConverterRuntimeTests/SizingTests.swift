//
//  SizingTests.swift
//  StyleConverterRuntimeTests
//
//  XCTest port of the Phase 3 sizing launch self-test (formerly
//  StyleEngine/sizing/SizingSelfTest.swift). Check bodies unchanged —
//  every failure-collection site survives 1:1; the print-PASS/FAIL
//  summary becomes one XCTFail per collected failure name.
//

import XCTest
// @testable: sizing extractor + resolver are internal to the module.
@testable import StyleConverterRuntime

final class SizingTests: XCTestCase {

    // One test per section of the original self-test.
    func testLengthExtensionChecks() { for failure in Self.runLengthExtensionChecks() { XCTFail(failure) } }
    func testAspectRatioChecks()     { for failure in Self.runAspectRatioChecks()     { XCTFail(failure) } }
    func testSizeExtractorChecks()   { for failure in Self.runSizeExtractorChecks()   { XCTFail(failure) } }
    func testResolveChecks()         { for failure in Self.runResolveChecks()         { XCTFail(failure) } }
    // TITAN Round 5 (EM/REM) — width/height in em must resolve to px against
    // the effective font-size (WPT css-color/a98rgb sizes its box `12em`).
    func testEmRemSizingChecks()     { for failure in Self.runEmRemSizingChecks()     { XCTFail(failure) } }

    // Build an IRValue object concisely.
    private static func obj(_ d: [String: IRValue]) -> IRValue { .object(d) }
    // Build an IRProperty list from (type,data) pairs for the extractor.
    private static func props(_ pairs: [(String, IRValue)]) -> [IRProperty] {
        pairs.map { IRProperty(type: $0.0, data: $0.1) }
    }

    // MARK: - Phase 1 extractor extensions (new `.none`, fit-content(bound))

    private static func runLengthExtensionChecks() -> [String] {
        var f: [String] = []

        // {"type":"none"} → .none (used by `max-width: none`).
        if case .none = extractLength(obj(["type": .string("none")])) { }
        else { f.append("length-type-none") }

        // {"fit-content":{"px":200}} → intrinsic fit-content with bound.
        let fc = obj(["fit-content": obj(["px": .double(200)])])
        if case .intrinsic(.fitContent(let b?)) = extractLength(fc),
           case .exact(200) = b { } else { f.append("fit-content-bounded") }

        // Bare string "fit-content" → unbounded intrinsic.
        if case .intrinsic(.fitContent(nil)) = extractLength(.string("fit-content")) { }
        else { f.append("fit-content-bare") }

        // min-content / max-content still parse.
        if case .intrinsic(.minContent) = extractLength(.string("min-content")) { }
        else { f.append("min-content-still-works") }

        return f.map { "lengthExt/\($0)" }
    }

    // MARK: - AspectRatio

    private static func runAspectRatioChecks() -> [String] {
        var f: [String] = []

        // 16/9 shape.
        let r16_9 = obj(["ratio": obj(["w": .double(16), "h": .double(9)]),
                          "normalizedRatio": .double(16.0 / 9.0)])
        if let v = AspectRatioExtractor.extract(r16_9),
           abs(v.ratio - 16.0/9.0) < 0.001, !v.isAuto { }
        else { f.append("ratio-16-9") }

        // Numeric shape (1.5).
        let r1_5 = obj(["ratio": obj(["value": .double(1.5)]),
                         "normalizedRatio": .double(1.5)])
        if AspectRatioExtractor.extract(r1_5)?.ratio == 1.5 { }
        else { f.append("ratio-numeric") }

        // Bare "auto".
        if let v = AspectRatioExtractor.extract(.string("auto")),
           v.isAuto { } else { f.append("ratio-auto") }

        // "auto 16/9" → isAuto=true with ratio.
        let rAutoR = obj(["ratio": obj(["auto": .bool(true),
                                          "w": .double(16),
                                          "h": .double(9)]),
                           "normalizedRatio": .double(16.0 / 9.0)])
        if let v = AspectRatioExtractor.extract(rAutoR),
           v.isAuto, abs(v.ratio - 16.0/9.0) < 0.001 { }
        else { f.append("ratio-auto-plus") }

        // Missing field → nil.
        if AspectRatioExtractor.extract(obj([:])) == nil { }
        else { f.append("ratio-empty-nil") }

        return f.map { "aspectRatio/\($0)" }
    }

    // MARK: - SizeExtractor dispatch

    private static func runSizeExtractorChecks() -> [String] {
        var f: [String] = []

        // Physical width px.
        let c = SizeExtractor.extract(from: props([
            ("Width", obj(["type": .string("length"), "px": .double(200)])),
            ("Height", obj(["type": .string("length"), "px": .double(60)])),
        ]))
        if case .exact(200) = c.width ?? .unknown { } else { f.append("width-px") }
        if case .exact(60)  = c.height ?? .unknown { } else { f.append("height-px") }

        // MaxWidth: none → LengthValue.none.
        let c2 = SizeExtractor.extract(from: props([
            ("MaxWidth", obj(["type": .string("none")])),
        ]))
        if c2.maxWidth == LengthValue.none { } else { f.append("maxwidth-none") }

        // Logical inline-size raw px (no type wrapper).
        let c3 = SizeExtractor.extract(from: props([
            ("InlineSize", obj(["px": .double(250)])),
        ]))
        if case .exact(250) = c3.width ?? .unknown { } else { f.append("inlinesize-px") }

        // Logical block-size "auto" bare.
        let c4 = SizeExtractor.extract(from: props([("BlockSize", .string("auto"))]))
        if case .auto = c4.height ?? .unknown { } else { f.append("blocksize-auto") }

        // Logical inline-size bare number → percent.
        let c5 = SizeExtractor.extract(from: props([("InlineSize", .double(50))]))
        if case .relative(50, .percent, nil) = c5.width ?? .unknown { }
        else { f.append("inlinesize-bare-percent") }

        // Width percentage wrapped (`{type:"percentage", value:50}`).
        let c6 = SizeExtractor.extract(from: props([
            ("Width", obj(["type": .string("percentage"), "value": .double(50)])),
        ]))
        if case .relative(50, .percent, nil) = c6.width ?? .unknown { }
        else { f.append("width-percent-wrapped") }

        // AspectRatio collapses to bare string "auto".
        let c7 = SizeExtractor.extract(from: props([("AspectRatio", .string("auto"))]))
        if c7.aspectRatio?.isAuto == true { } else { f.append("ar-auto") }

        return f.map { "extractor/\($0)" }
    }

    // MARK: - SizeApplierResolve

    private static func runResolveChecks() -> [String] {
        var f: [String] = []
        let ctx = SpacingContext(fontSizePx: 16, viewportWidth: 390, viewportHeight: 844)

        // Exact px passes through.
        if SizeApplierResolve.exact(.exact(px: 150), ctx: ctx, parent: 390) == 150 { }
        else { f.append("exact-px") }

        // auto → nil (no frame attached).
        if SizeApplierResolve.exact(.auto, ctx: ctx, parent: 390) == nil { }
        else { f.append("exact-auto-nil") }

        // percent of parent 300 → 0.5 * 300 = 150.
        let pct = LengthValue.relative(value: 50, unit: .percent, pxFallback: nil)
        if SizeApplierResolve.exact(pct, ctx: ctx, parent: 300) == 150 { }
        else { f.append("exact-percent") }

        // Constraint: `.none` → nil (no clamp).
        if SizeApplierResolve.constraint(LengthValue.none, ctx: ctx, parent: 390) == nil { }
        else { f.append("constraint-none") }

        // Constraint px.
        if SizeApplierResolve.constraint(.exact(px: 100), ctx: ctx, parent: 390) == 100 { }
        else { f.append("constraint-px") }

        // Intrinsic min-content → wantsIntrinsic true, exact returns nil.
        let mc: LengthValue = .intrinsic(kind: .minContent)
        if SizeApplierResolve.wantsIntrinsic(mc),
           SizeApplierResolve.exact(mc, ctx: ctx, parent: 390) == nil { }
        else { f.append("min-content") }

        // Bounded fit-content. The resolver contract changed after this
        // check was written: `fit-content(W)` is a CLAMP, not an exact
        // size (see the rationale comment in SizeApplierResolve.exact),
        // so `exact` now returns nil for ALL intrinsic shapes, the axis
        // wants `.fixedSize` (wantsIntrinsic true), and the bound comes
        // back through `fitContentBound` as a maxWidth/Height cap. The
        // print-only self-test never surfaced the stale expectation.
        let fc: LengthValue = .intrinsic(kind: .fitContent(bound: .exact(px: 200)))
        if SizeApplierResolve.exact(fc, ctx: ctx, parent: 390) == nil,
           SizeApplierResolve.wantsIntrinsic(fc),
           SizeApplierResolve.fitContentBound(fc, ctx: ctx, parent: 390) == 200 { }
        else { f.append("fit-content-bounded") }

        return f.map { "resolve/\($0)" }
    }

    // MARK: - EM / REM sizing resolution (TITAN Round 5)

    // The four WPT css-color/a98rgb reftests size their swatch with
    // `width: 12em; height: 12em` (a98rgb-004 uses `6em` for one axis). The
    // converter cannot pre-resolve em — it is runtime-relative — so it emits
    // the length as `{"type":"length","original":{"v":12,"u":"EM"}}` with NO
    // `px` field (px is NULL / absent). These checks pin the two links in the
    // chain the runtime owns: (1) the extractor decodes that px-less wrapper
    // into `.relative(_, .em, nil)` rather than dropping it to `.unknown`, and
    // (2) SizeApplierResolve.exact multiplies it by the effective font-size —
    // 16pt CSS-initial `medium` by default — so the box is 12 × 16 = 192pt,
    // matching the browser-ref, instead of collapsing to content size.
    private static func runEmRemSizingChecks() -> [String] {
        var f: [String] = []

        // ── (1) Extraction: the exact a98rgb IR wire shape ───────────────
        // `{"type":"length","original":{"v":12,"u":"EM"}}` — no `px` key.
        // Must decode to `.relative(12, .em, nil)`; a `.unknown` here is the
        // "em dropped → box mis-sized" failure the round targets.
        let emWire = obj(["type": .string("length"),
                          "original": obj(["v": .double(12), "u": .string("EM")])])
        if case .relative(12, .em, nil) = extractLength(emWire) { }
        else { f.append("em-extract-relative") }

        // Same for rem so the root-relative unit is covered end-to-end.
        let remWire = obj(["type": .string("length"),
                           "original": obj(["v": .double(2), "u": .string("REM")])])
        if case .relative(2, .rem, nil) = extractLength(remWire) { }
        else { f.append("rem-extract-relative") }

        // ── (2) Resolution against the default 16pt root font-size ───────
        // CSS-initial `font-size: medium` = 16px; SpacingContext defaults to
        // 16 (PaddingConfig.swift). 12em × 16 = 192pt — the a98rgb box size.
        let ctx16 = SpacingContext(fontSizePx: 16, viewportWidth: 390, viewportHeight: 844)
        let em12 = LengthValue.relative(value: 12, unit: .em, pxFallback: nil)
        if SizeApplierResolve.exact(em12, ctx: ctx16, parent: 358) == 192 { }
        else { f.append("em12-at-16-is-192") }

        // a98rgb-004's short axis: 6em × 16 = 96pt.
        let em6 = LengthValue.relative(value: 6, unit: .em, pxFallback: nil)
        if SizeApplierResolve.exact(em6, ctx: ctx16, parent: 358) == 96 { }
        else { f.append("em6-at-16-is-96") }

        // Height axis (allowPercent:false) must resolve em identically — em is
        // not a percentage, so the height-percent guard must not swallow it.
        if SizeApplierResolve.exact(em12, ctx: ctx16, parent: 844, allowPercent: false) == 192 { }
        else { f.append("em12-height-is-192") }

        // rem resolves against the FIXED 16pt root, independent of the
        // element font-size (SpacingResolver's `.rem` → value × 16).
        let rem2 = LengthValue.relative(value: 2, unit: .rem, pxFallback: nil)
        if SizeApplierResolve.exact(rem2, ctx: ctx16, parent: 358) == 32 { }
        else { f.append("rem2-at-root-is-32") }

        // ── (3) Element font-size override threads through for em ─────────
        // When an ancestor/element sets `font-size: 24px`, em resolves
        // against 24, not the 16 root: 12em × 24 = 288pt. rem stays on 16.
        let ctx24 = SpacingContext(fontSizePx: 24, viewportWidth: 390, viewportHeight: 844)
        if SizeApplierResolve.exact(em12, ctx: ctx24, parent: 358) == 288 { }
        else { f.append("em12-at-24-is-288") }
        if SizeApplierResolve.exact(rem2, ctx: ctx24, parent: 358) == 32 { }
        else { f.append("rem2-root-ignores-fontsize") }

        // Min/Max constraints resolve em through the same lane (constraint()).
        if SizeApplierResolve.constraint(em12, ctx: ctx16, parent: 358) == 192 { }
        else { f.append("em12-constraint-is-192") }

        return f.map { "emRem/\($0)" }
    }
}

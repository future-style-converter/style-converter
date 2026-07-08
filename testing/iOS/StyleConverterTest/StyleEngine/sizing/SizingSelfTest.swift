//
//  SizingSelfTest.swift
//  StyleEngine/sizing — Phase 3.
//
//  Launch-time asserts for the Phase 3 sizing extractor + resolver.
//  Same pattern as SpacingSelfTest. Prints PASS / FAIL plus the list of
//  failing checks. Called from StyleConverterTestApp.init under DEBUG.
//

import Foundation

enum SizingSelfTest {

    // Single entry point. Runs every section and summarises results.
    static func run() {
        var f: [String] = []
        f += runLengthExtensionChecks()
        f += runAspectRatioChecks()
        f += runSizeExtractorChecks()
        f += runResolveChecks()

        if f.isEmpty {
            print("[SizingSelfTest] PASS — sizing engine green")
        } else {
            print("[SizingSelfTest] FAIL — \(f.count) check(s) failed:")
            f.forEach { print("  - \($0)") }
        }
    }

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

        // Bounded fit-content → exact returns bound, wantsIntrinsic false.
        let fc: LengthValue = .intrinsic(kind: .fitContent(bound: .exact(px: 200)))
        if SizeApplierResolve.exact(fc, ctx: ctx, parent: 390) == 200,
           !SizeApplierResolve.wantsIntrinsic(fc) { }
        else { f.append("fit-content-bounded") }

        return f.map { "resolve/\($0)" }
    }
}

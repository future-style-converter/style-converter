//
//  SpacingSelfTest.swift
//  StyleEngine/spacing — Phase 2.
//
//  Launch-time asserts for the Phase 2 spacing extractors + resolver.
//  Same pattern as CoreTypesSelfTest — called from the App's init block
//  under #if DEBUG, zero release cost. Prints a single PASS / FAIL line
//  with per-check failure names for fast triage in Simulator logs.
//

import Foundation

enum SpacingSelfTest {

    static func run() {
        var failures: [String] = []
        failures += runPaddingChecks()
        failures += runMarginChecks()
        failures += runGapChecks()
        failures += runMarginTrimChecks()
        failures += runResolverChecks()

        if failures.isEmpty {
            print("[SpacingSelfTest] PASS — all spacing extractors green")
        } else {
            print("[SpacingSelfTest] FAIL — \(failures.count) check(s) failed:")
            failures.forEach { print("  - \($0)") }
        }
    }

    // Build a property list quickly from (type, data) tuples.
    private static func props(_ pairs: [(String, IRValue)]) -> [IRProperty] {
        pairs.map { IRProperty(type: $0.0, data: $0.1) }
    }
    private static func obj(_ d: [String: IRValue]) -> IRValue { .object(d) }

    // MARK: - Padding

    private static func runPaddingChecks() -> [String] {
        var f: [String] = []
        // Absolute px — all four sides.
        let p = PaddingExtractor.extract(from: props([
            ("PaddingTop", obj(["px": .double(20)])),
            ("PaddingRight", obj(["px": .double(20)])),
            ("PaddingBottom", obj(["px": .double(20)])),
            ("PaddingLeft", obj(["px": .double(20)])),
        ]))
        if p?.top == .exact(px: 20) { } else { f.append("padding-px") }

        // Bare-number → percent.
        let p2 = PaddingExtractor.extract(from: props([("PaddingTop", .double(10))]))
        if case .some(.relative(10, .percent, nil)) = p2?.top { } else { f.append("padding-bare-percent") }

        // Logical mapping.
        let p3 = PaddingExtractor.extract(from: props([("PaddingBlockStart", obj(["px": .double(5)]))]))
        if p3?.top == .exact(px: 5) { } else { f.append("padding-block-start") }

        // Calc shape {expr:}.
        let p4 = PaddingExtractor.extract(from: props([("PaddingTop", obj(["expr": .string("calc(10px + 5px)")]))]))
        if case .some(.calc("calc(10px + 5px)")) = p4?.top { } else { f.append("padding-calc") }

        // No padding → nil.
        if PaddingExtractor.extract(from: props([("Width", obj(["px": .double(10)]))])) == nil { } else { f.append("padding-nil") }

        return f.map { "padding/\($0)" }
    }

    // MARK: - Margin

    private static func runMarginChecks() -> [String] {
        var f: [String] = []
        // Negative px preserved.
        let m = MarginExtractor.extract(from: props([("MarginTop", obj(["px": .double(-10)]))]))
        if m?.top == .exact(px: -10) { } else { f.append("margin-negative") }

        // Auto bare string.
        let m2 = MarginExtractor.extract(from: props([("MarginLeft", .string("auto"))]))
        if m2?.left == .auto { } else { f.append("margin-auto") }

        // margin auto 0 — top/bottom auto.
        let m3 = MarginExtractor.extract(from: props([
            ("MarginTop", .string("auto")),
            ("MarginRight", obj(["px": .double(0)])),
            ("MarginBottom", .string("auto")),
            ("MarginLeft", obj(["px": .double(0)])),
        ]))
        if m3?.verticalAutoAlignment == .center { } else { f.append("margin-v-auto-center") }
        // Explicit enum qualification — `horizontalAutoAlignment` on an
        // Optional makes bare `.none` resolve to `Optional.none` (nil),
        // which always false-positives this check.
        if m3?.horizontalAutoAlignment == HorizontalAutoAlignment.none { } else { f.append("margin-h-noauto") }

        // Horizontal both auto → center.
        let m4 = MarginExtractor.extract(from: props([
            ("MarginLeft", .string("auto")),
            ("MarginRight", .string("auto")),
        ]))
        if m4?.horizontalAutoAlignment == .center { } else { f.append("margin-h-auto-center") }

        return f.map { "margin/\($0)" }
    }

    // MARK: - Gap

    private static func runGapChecks() -> [String] {
        var f: [String] = []
        let g = GapExtractor.extract(from: props([
            ("RowGap", obj(["type": .string("length"), "px": .double(10)])),
            ("ColumnGap", obj(["type": .string("length"), "px": .double(40)])),
        ]))
        if g?.row == .exact(px: 10) { } else { f.append("gap-row") }
        if g?.column == .exact(px: 40) { } else { f.append("gap-col") }

        // Percentage gap (Gap_Percent fixture).
        let g2 = GapExtractor.extract(from: props([
            ("RowGap", obj(["type": .string("percentage"), "value": .double(5)])),
        ]))
        if case .some(.relative(5, .percent, nil)) = g2?.row { } else { f.append("gap-percent") }

        return f.map { "gap/\($0)" }
    }

    // MARK: - MarginTrim

    private static func runMarginTrimChecks() -> [String] {
        var f: [String] = []
        let t = MarginTrimExtractor.extract(from: props([("MarginTrim", .string("BLOCK_START"))]))
        if t?.mode == .blockStart { } else { f.append("margin-trim-block-start") }
        let t2 = MarginTrimExtractor.extract(from: props([("MarginTrim", .string("NONE"))]))
        // Explicit enum qualification — same Swift quirk as above.
        if t2?.mode == MarginTrimMode.none { } else { f.append("margin-trim-none") }
        return f.map { "marginTrim/\($0)" }
    }

    // MARK: - Resolver

    private static func runResolverChecks() -> [String] {
        var f: [String] = []
        let ctx = SpacingContext(fontSizePx: 16, viewportWidth: 390, viewportHeight: 844)

        // Exact px passes through.
        if case .px(20) = SpacingResolver.resolve(.exact(px: 20), ctx: ctx, isPadding: true) { } else { f.append("resolver-px") }
        // Padding negative clamped to 0.
        if case .px(0) = SpacingResolver.resolve(.exact(px: -5), ctx: ctx, isPadding: true) { } else { f.append("resolver-pad-neg") }
        // Margin negative preserved.
        if case .px(-5) = SpacingResolver.resolve(.exact(px: -5), ctx: ctx, isPadding: false) { } else { f.append("resolver-mgn-neg") }
        // em → 2em × 16 = 32.
        if case .px(32) = SpacingResolver.resolve(.relative(value: 2, unit: .em, pxFallback: nil), ctx: ctx, isPadding: true) { } else { f.append("resolver-em") }
        // rem → 1rem × 16 = 16, regardless of fontSizePx.
        let ctx2 = SpacingContext(fontSizePx: 24)
        if case .px(16) = SpacingResolver.resolve(.relative(value: 1, unit: .rem, pxFallback: nil), ctx: ctx2, isPadding: true) { } else { f.append("resolver-rem") }
        // vw → 5vw × 390/100 = 19.5.
        if case .px(let x) = SpacingResolver.resolve(.relative(value: 5, unit: .vw, pxFallback: nil), ctx: ctx, isPadding: true),
           abs(x - 19.5) < 0.001 { } else { f.append("resolver-vw") }
        // percent stays in percent lane.
        if case .percent(0.25) = SpacingResolver.resolve(.relative(value: 25, unit: .percent, pxFallback: nil), ctx: ctx, isPadding: true) { } else { f.append("resolver-percent") }
        // auto on padding → 0.
        if case .px(0) = SpacingResolver.resolve(.auto, ctx: ctx, isPadding: true) { } else { f.append("resolver-auto-pad") }
        // auto on margin → .auto.
        if case .auto = SpacingResolver.resolve(.auto, ctx: ctx, isPadding: false) { } else { f.append("resolver-auto-margin") }
        // calc(10px + 5px) → 15.
        if case .px(15) = SpacingResolver.resolve(.calc(expression: "calc(10px + 5px)"), ctx: ctx, isPadding: true) { } else { f.append("resolver-calc") }

        return f.map { "resolver/\($0)" }
    }
}

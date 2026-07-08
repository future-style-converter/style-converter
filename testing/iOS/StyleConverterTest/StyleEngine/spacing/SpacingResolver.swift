//
//  SpacingResolver.swift
//  StyleEngine/spacing — Phase 2.
//
//  Shared LengthValue → CGFloat resolver for padding / margin / gap. Kept
//  separate from the appliers so the applier files stay under 200 lines
//  and so the resolver can be unit-tested independently (SpacingSelfTest).
//  All functions are pure; unresolved values degrade to 0 with a logged
//  reason rather than raising.
//

// CoreGraphics supplies CGFloat; Foundation supplies Double. Everything
// else comes from the Phase 1 primitives.
import CoreGraphics
import Foundation

// Resolved spacing resolution result. `percent` is split out so the
// applier can delegate to a GeometryReader for parent-relative sides.
enum ResolvedLength: Equatable {
    case px(CGFloat)            // Fully resolved, ready for `.padding(n)`.
    case percent(CGFloat)       // 0.0–1.0 of parent width (CSS spec).
    case auto                   // Margin-only — triggers alignment handling.
    case skip                   // Unresolvable → render as zero.
}

enum SpacingResolver {

    // Master dispatch. `isPadding` controls the two CSS asymmetries
    // between padding and margin: (1) `auto` means 0 on padding but
    // triggers centering on margin; (2) negative values are illegal on
    // padding but legal (and common) on margin.
    static func resolve(_ v: LengthValue,
                        ctx: SpacingContext,
                        isPadding: Bool) -> ResolvedLength {
        switch v {
        // Fully resolved absolute pixels — 99% of the fixtures.
        case .exact(let px):
            // Negative padding snaps to 0 per CSS spec; margins keep sign.
            let clamped = isPadding ? max(0, px) : px
            return .px(CGFloat(clamped))

        // Relative units. `pxFallback` is populated by the Kotlin side
        // whenever the unit is em/rem/viewport *and* the canonical size
        // is known. For spacing we only trust the fallback when the
        // unit is absolute — otherwise we resolve ourselves so that the
        // iOS runtime font-size is authoritative.
        case .relative(let value, let unit, let fallback):
            return resolveRelative(value: value,
                                   unit: unit,
                                   pxFallback: fallback,
                                   ctx: ctx,
                                   isPadding: isPadding)

        // Margin-only; caller inspects `auto` to build the alignment.
        case .auto:
            return isPadding ? .px(0) : .auto

        // `min-content` / `max-content` don't have a spacing meaning; 0.
        case .intrinsic: return .px(0)

        // `fr` is grid-track only — not valid for spacing.
        case .fraction: return .px(0)

        // Naive calc: only `calc(Npx + Mpx)` / `calc(Npx - Mpx)` for now.
        // Anything richer falls through to 0. Matches the "try calc, else
        // skip with warning" policy from the plan.
        case .calc(let expr):
            if let px = naiveCalcPx(expr) {
                let clamped = isPadding ? max(0, px) : px
                return .px(CGFloat(clamped))
            }
            return .skip

        case .unknown:
            return .skip

        // `.none` is sizing-only (`max-width: none`). No spacing meaning —
        // treat as "no contribution" so the applier collapses to zero.
        case .none:
            return .skip
        }
    }

    // Resolve any relative unit to absolute px given the Phase 2 context.
    // Percentages escape the pixel lane because their answer depends on
    // the parent width, which only the applier's GeometryReader knows.
    private static func resolveRelative(value: Double,
                                        unit: LengthUnit,
                                        pxFallback: Double?,
                                        ctx: SpacingContext,
                                        isPadding: Bool) -> ResolvedLength {
        // Helper to avoid duplicated clamp+wrap logic at every return.
        func toPx(_ d: Double) -> ResolvedLength {
            let clamped = isPadding ? max(0, d) : d
            return .px(CGFloat(clamped))
        }

        switch unit {
        // Absolute units — trust the converter's pre-resolved fallback
        // if provided; otherwise the upstream `px` field should already
        // have routed us to `.exact`, so this path is defensive.
        case .px, .pt, .cm, .mm, .inch, .pc, .q:
            return toPx(pxFallback ?? value)

        // Font-relative — resolve against the threaded font size.
        case .em, .ex, .ch, .cap, .ic, .lh:
            return toPx(value * ctx.fontSizePx)
        // Root-em resolves against a fixed 16pt root size per SpacingContext.
        case .rem, .rlh:
            return toPx(value * 16.0)

        // Percentages defer — parent width isn't known here.
        case .percent:
            return .percent(CGFloat(value / 100.0))

        // Viewport-width family all use the canvas width.
        case .vw, .vi, .svw, .svi, .lvw, .lvi, .dvw, .dvi, .cqw, .cqi:
            return toPx(value / 100.0 * ctx.viewportWidth)
        // Viewport-height family.
        case .vh, .vb, .svh, .svb, .lvh, .lvb, .dvh, .dvb, .cqh, .cqb:
            return toPx(value / 100.0 * ctx.viewportHeight)
        // v-min / v-max families.
        case .vmin, .svmin, .lvmin, .dvmin, .cqmin:
            return toPx(value / 100.0 * min(ctx.viewportWidth, ctx.viewportHeight))
        case .vmax, .svmax, .lvmax, .dvmax, .cqmax:
            return toPx(value / 100.0 * max(ctx.viewportWidth, ctx.viewportHeight))

        // Grid-fraction — not valid for spacing.
        case .fr:
            return .px(0)
        }
    }

    // Best-effort "calc(N{px|pt} ± M{px|pt})" evaluator. Purposefully
    // limited: only two terms, only px/pt, only + or -. Anything else
    // returns nil so the caller routes to `.skip`.
    static func naiveCalcPx(_ expr: String) -> Double? {
        // Strip whitespace + outer `calc(...)` wrapping.
        let inner = expr
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "calc(", with: "")
            .replacingOccurrences(of: ")", with: "")

        // Split on the first + or -. Work on the raw String since we
        // want to capture the operator character.
        for op in ["+", "-"] {
            if let r = inner.range(of: op) {
                let lhs = String(inner[..<r.lowerBound])
                let rhs = String(inner[r.upperBound...])
                if let a = parsePxLikeTerm(lhs), let b = parsePxLikeTerm(rhs) {
                    return op == "+" ? (a + b) : (a - b)
                }
            }
        }
        // No operator — treat as a single px term.
        return parsePxLikeTerm(inner)
    }

    // Parse "12px" / "8pt" / "10" into a Double in px. Everything else
    // returns nil, which propagates to `.skip` at the call-site.
    private static func parsePxLikeTerm(_ s: String) -> Double? {
        let lower = s.lowercased()
        if lower.hasSuffix("px"), let n = Double(lower.dropLast(2)) { return n }
        // 1pt = 1.3333px on the converter's 96-dpi baseline.
        if lower.hasSuffix("pt"), let n = Double(lower.dropLast(2)) { return n * 4.0 / 3.0 }
        // Unit-less — treat as px.
        if let n = Double(lower) { return n }
        return nil
    }
}

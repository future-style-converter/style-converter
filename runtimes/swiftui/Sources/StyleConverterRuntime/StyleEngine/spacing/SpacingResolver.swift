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

    // Wave-18 lane 2 (pins P10/P11/P12) — the containing-block basis a
    // percent PADDING/MARGIN side resolves against (CSS 2.1 §8.3/§8.4:
    // padding-% and margin-% on ALL four sides use the containing block's
    // inline size):
    //   1. a DEFINITE ancestor basis from the renderer's env-threaded
    //      containing-block channel always wins (P10);
    //   2. indefinite basis in WPT capture → 0 (P11 — css-position-3 §5.1:
    //      inside an abspos auto/fit-content inline-size the basis is
    //      indefinite, and the percentage behaves as zero; this is what
    //      makes `padding-left: 50%` inside a fit-content abspos box match
    //      the browser ref instead of adding half a viewport);
    //   3. nil OUTSIDE WPT capture → "no static basis": the appliers keep
    //      their legacy GeometryReader lane so the frozen dark-stage
    //      corpus renders byte-identically (P12).
    // Twin of Compose's applier gate; the WPT flag is an argument here
    // because SwiftUI env values live on the applier, not the context.
    // The WHOLE static lane is WPT-gated — outside WPT capture even a
    // definite channel basis returns nil, because the dark-stage corpus
    // baselines are frozen against the GeometryReader lane and this
    // campaign only re-gates the WPT sections.
    static func percentBasisPx(ctx: SpacingContext,
                               wptCaptureMode: Bool) -> CGFloat? {
        // Non-WPT → no static basis; the applier keeps its legacy lane.
        guard wptCaptureMode else { return nil }
        // Definite parent content-box width published by ComponentRenderer
        // wins (P10); an indefinite basis resolves percents to 0 (P11).
        return CGFloat(ctx.containingBlockWidthPx ?? 0)
    }

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

        // Wave-18 cleanup (skeptic B2): full calc() evaluation through the
        // shared SpacingCalcEvaluator — the byte-parallel port of Compose's
        // evalCalc, covering the whole unit table (px/pt/em/rem/%/ch/vw/…)
        // instead of the old two-term px/pt-only naiveCalcPx that dropped
        // the live `calc(1em + 4px)` / `calc(2ch + 4px)` wires to 0/.skip.
        // Malformed expressions stay on the tracked `.skip` lane (renders
        // 0 — the same pixel outcome as Compose's catch-arm 0f).
        case .calc(let expr):
            if let px = SpacingCalcEvaluator.evalPx(expr, ctx: ctx) {
                // Negative computed padding clamps to 0 (css-box: padding
                // values must be non-negative); margins keep their sign.
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

        // Font-relative — wave-18 lane 2 splits the old single `× fontSize`
        // arm (which resolved 63.1ch as 63.1em ≈ 1010px instead of the
        // ~advance-based width) into the per-unit rules of the pin table,
        // mirrored line-for-line by the Compose resolveRelative():
        // Pin P1 — `ch` = advance width of '0' in the element's font
        // (css-values-4 §6.1.3), measured via ChUnitMetrics/CoreText when
        // the plumbing provided it; else the spec's own 0.5em assumption.
        case .ch:
            return toPx(value * (ctx.chAdvancePx ?? 0.5 * ctx.fontSizePx))
        // Pin P2 — `ex` = x-height; §6.1.3 mandates 0.5em when the metric
        // is impractical to determine (we don't measure x-height yet).
        case .ex:
            return toPx(value * 0.5 * ctx.fontSizePx)
        // Pin P3 — `ic` fallback is 1em per §6.1.3. Pin P4 — `cap`'s spec
        // fallback is the ascent; 1em is the documented approximation kept
        // identical to the previous behaviour (and to Compose).
        case .em, .cap, .ic:
            return toPx(value * ctx.fontSizePx)
        // Pin P5 — `lh` = used line-height; `normal` computes to ≈1.2 ×
        // font-size in every UA default sheet, the approximation we pin.
        case .lh:
            return toPx(value * 1.2 * ctx.fontSizePx)
        // Root-em resolves against a fixed 16pt root size per SpacingContext.
        case .rem:
            return toPx(value * 16.0)
        // Pin P6 — root line-height: the same 1.2 ratio on the 16px root.
        case .rlh:
            return toPx(value * 1.2 * 16.0)

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

    // Wave-18 cleanup: naiveCalcPx / parsePxLikeTerm deleted — the shared
    // SpacingCalcEvaluator (SpacingCalcEvaluator.swift, the Compose
    // evalCalc port) owns every calc() resolution reachable from resolve().
}

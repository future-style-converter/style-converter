//
//  BackdropConfig.swift
//  StyleEngine/effects/backdrop — lane BF-I (the SwiftUI backdrop twin).
//
//  CSS `backdrop-filter` (filter-effects-2 §2) filters the BACKDROP of an
//  element — everything already painted behind it — not the element's own
//  pixels. SwiftUI has no API for that: `.blur`/`.colorInvert` filter the
//  view they are attached to, and `.thinMaterial` is a system blur recipe
//  with its own tint (the pre-lane iOS approximation, removed for painting
//  a grey rectangle even with nothing behind it — see FilterApplier).
//
//  The lane implements it as a TWO-PASS capture instead:
//    PASS A  the composed canvas renders with every backdrop element's own
//            paint suppressed → a raster of "what is behind them";
//    PASS B  the canvas renders again; each backdrop element crops its own
//            border box out of the pass-A raster, runs the filter over
//            those pixels, and draws the result UNDER itself.
//
//  This file owns the first half of the honest-scope contract: which of
//  the CSS filter functions this lane can actually execute on a cropped
//  raster. Scope is INVERT + BLUR only. Everything else is recorded by
//  name in `unsupported` so the applier can log it once — never dropped
//  silently (CLAUDE.md: no silent fallthroughs), so the cross-platform
//  comparison stays honest about what iOS did and did not filter.
//
//  ADMISSION POLICY — ALL-OR-NOTHING, identical on both natives (wave-26
//  skeptic fix #2). A chain containing ANY function outside invert+blur is
//  refused ENTIRELY: no prefix is executed, no backplate is drawn, and the
//  element renders exactly as it did before this lane. The names of the
//  offending functions are still recorded in `unsupported` so the applier can
//  log the refusal (CLAUDE.md: no silent fallthroughs).
//
//  This planner used to execute the recognised PREFIX of a mixed chain
//  (`blur(6px) brightness(1.1)` → blur only) while Compose's
//  BackdropChain.of refused the whole thing, so the two natives painted
//  DIFFERENT images for the same fixture — iOS a half-filtered backdrop,
//  Android none, and the pair diff blamed a rendering bug rather than an
//  unimplemented value flavour. Refusing wholesale is the choice that keeps
//  the cross-platform comparison honest: an absent backdrop reads as "not
//  implemented", a half-applied one reads as "implemented, and wrong".
//

// CoreGraphics types (CGFloat) ride in through SwiftUI.
import SwiftUI

/// One executable backdrop operation — the filtered-raster subset of
/// `FilterFn`. Deliberately a SEPARATE type from FilterFn: FilterFn is the
/// full CSS palette (what the parser recognises), this is the palette the
/// pass-B image pipeline can run (what the platform can execute).
enum BackdropOp: Equatable {
    /// `invert(<amount>)` — per-channel, filter-effects-1 §8.6. Amount is
    /// NORMALISED to 0…1 here (the wire carries CSS's 0…100 percentage),
    /// because the byte math in BackdropImageOps is defined on 0…1 and the
    /// Compose twin normalises at the same boundary.
    case invert(amount: Double)
    /// `blur(<length>)` — a Gaussian blur whose STANDARD DEVIATION is the
    /// declared length (filter-effects-1 §8.2 defers to SVG's
    /// feGaussianBlur/stdDeviation). Carried in POINTS; the pixel radius is
    /// derived at draw time from the plate scale, so a 2× plate blurs by
    /// twice as many pixels for the same CSS length.
    case blur(sigma: CGFloat)
}

/// The resolved backdrop program for one element: the ops to execute, in
/// declared order, plus the names of every function this lane cannot run.
struct BackdropPlan: Equatable {
    /// Executable ops, in CSS declaration order — filters compose
    /// left-to-right (each sees the previous one's output), so order is
    /// part of the value, never sorted or de-duplicated.
    var ops: [BackdropOp] = []
    /// Function names this lane cannot execute, in declaration order and WITH
    /// duplicates. Non-empty means the WHOLE chain was refused (see the file
    /// header): `ops` is then empty and the applier logs this list, so the
    /// capture log names the refusal instead of implying a complete render.
    var unsupported: [String] = []

    /// True when there is nothing to draw — no executable op. The applier
    /// treats this as identity (the element renders exactly as it does
    /// today), so a fixture whose chain is out of scope never gets a
    /// backplate drawn under it.
    var isIdentity: Bool { ops.isEmpty }

    /// True when the chain was refused wholesale rather than being genuinely
    /// empty. `backdrop-filter: none`, `invert(0)` and `blur(0)` are exact
    /// identities and must NOT read as refusals — only an out-of-scope
    /// function sets this.
    var isRefused: Bool { !unsupported.isEmpty }

    /// Effective Gaussian σ (POINTS) of the whole chain, for sizing the
    /// sample padding only. Successive Gaussians compose as σ = √(Σσᵢ²), the
    /// same rule Compose's `BackdropChain.totalBlurSigmaPx` applies — with
    /// the single blur every in-corpus fixture carries it is just that blur's
    /// σ. √0 = 0, so an invert-only chain reports no spread and the sample
    /// collapses to the border box exactly.
    var totalBlurSigma: CGFloat {
        var sumSq: CGFloat = 0
        for op in ops {
            if case .blur(let sigma) = op { sumSq += sigma * sigma }
        }
        return sqrt(sumSq)
    }

    /// Pure planner: the declared `backdrop-filter` chain → this program.
    /// Called by FilterApplier with `FilterConfig.backdrop` verbatim.
    static func plan(from fns: [FilterFn]) -> BackdropPlan {
        // Accumulate into a local so the function stays a single pass over
        // the chain and the result is a value (no shared mutable state).
        var plan = BackdropPlan()
        for fn in fns {
            switch fn {
            case .invert(let pct):
                // CSS `invert(<number|percentage>)`: the parser normalises
                // both flavors to a 0…100 scale (FilterExtractor's `numv`),
                // so /100 lands on the spec's 0…1 amount. Clamped because
                // filter-effects-1 §8.6 defines the function only on
                // [0,1] — a malformed 300% must not push channels out of
                // range in the byte math.
                let amount = min(1.0, max(0.0, Double(pct) / 100.0))
                // amount == 0 is the IDENTITY invert (spec: "0% leaves the
                // input unchanged"). Recording it as an op would make the
                // plan non-identity and cost a whole second capture pass
                // for a no-op, so it is skipped — and it is NOT an
                // unsupported drop either: the visual result is exact.
                if amount > 0 { plan.ops.append(.invert(amount: amount)) }
            case .blur(let radius):
                // The IR carries the declared length in points (px). A
                // zero/negative radius is the identity blur, skipped for
                // the same reason as invert(0) — exact, not a drop.
                if radius > 0 { plan.ops.append(.blur(sigma: radius)) }
            // Everything below is a real CSS backdrop function this lane
            // does NOT execute. Each is recorded by its CSS name so the
            // applier's one-shot log names it — and, per the all-or-nothing
            // rule at the bottom of this function, its mere presence discards
            // every op collected above.
            case .brightness:  plan.unsupported.append("brightness")
            case .contrast:    plan.unsupported.append("contrast")
            case .grayscale:   plan.unsupported.append("grayscale")
            case .sepia:       plan.unsupported.append("sepia")
            case .saturate:    plan.unsupported.append("saturate")
            case .opacity:     plan.unsupported.append("opacity")
            case .hueRotate:   plan.unsupported.append("hue-rotate")
            case .dropShadow:  plan.unsupported.append("drop-shadow")
            case .url:         plan.unsupported.append("url")
            }
        }
        // ALL-OR-NOTHING (see the file header). One unrecognised function
        // refuses the WHOLE chain — the recognised prefix is discarded, not
        // executed — so this planner and Compose's BackdropChain.of admit
        // exactly the same set of chains. `unsupported` survives, because the
        // refusal still has to be disclosed.
        if !plan.unsupported.isEmpty { plan.ops = [] }
        return plan
    }
}

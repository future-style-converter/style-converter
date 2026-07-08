//
//  MarginApplier.swift
//  StyleEngine/spacing — Phase 2.
//
//  ViewModifier that applies a MarginConfig using OUTER `.padding()` so the
//  element's layout footprint grows by the margin amount — matching CSS's
//  "margin reserves space outside the border" semantics. The chain in
//  StyleBuilder is ordered so this modifier runs AFTER background+border,
//  meaning the padding it adds is genuinely outside the element's box.
//
//  Three asymmetries vs. PaddingApplier:
//    1. `.auto` on a side → frame-based alignment (centering / pushing),
//       because CSS `margin: auto` doesn't map to a pixel count.
//    2. Negative margins → `.offset()` to visually shift the element.
//       (CSS's sibling-collapse behavior for negative margin requires a
//       custom Layout and is out of scope — documented as a known gap.)
//    3. Percent margins resolve against parent width → GeometryReader.
//

// SwiftUI for modifier surface + GeometryReader, CoreGraphics for CGFloat.
import SwiftUI

// Modifier attached via the `engineSpacingMargin(_:context:)` view extension.
// Kept separate from PaddingApplier because of the `auto`/negative branches.
struct MarginApplier: ViewModifier {
    // Extracted config; nil / all-zero short-circuits the modifier.
    let config: MarginConfig?
    // Context threads through fontSize + viewport for relative units.
    let context: SpacingContext

    func body(content: Content) -> some View {
        // Short-circuit when there's nothing to apply — zero overhead on
        // components that don't set any margin longhand.
        guard let cfg = config, cfg.hasAny else { return AnyView(content) }

        // Resolve each side. `isPadding:false` preserves sign + `.auto`.
        let t = SpacingResolver.resolve(cfg.top, ctx: context, isPadding: false)
        let r = SpacingResolver.resolve(cfg.right, ctx: context, isPadding: false)
        let b = SpacingResolver.resolve(cfg.bottom, ctx: context, isPadding: false)
        let l = SpacingResolver.resolve(cfg.left, ctx: context, isPadding: false)

        // If any side is a percent we need parent width — GeometryReader it.
        let needsGeo = isPercent(t) || isPercent(r) || isPercent(b) || isPercent(l)
        // Pull auto flags once; applier will branch on them.
        let hAuto = cfg.horizontalAutoAlignment
        let vAuto = cfg.verticalAutoAlignment

        if needsGeo {
            return AnyView(
                GeometryReader { geo in
                    // Fallback 390 matches the capture-canvas width when the
                    // enclosing layout hasn't given us a concrete parent.
                    let pw = geo.size.width > 0 ? geo.size.width : 390
                    build(content, t: t, r: r, b: b, l: l,
                          parentWidth: pw, hAuto: hAuto, vAuto: vAuto)
                }
            )
        }

        // Common case — no percent sides, use the viewport as a width hint
        // only for any hypothetical future percent-edge math. Current
        // non-geo path never reads `parentWidth`, but keeping one signature
        // avoids a second helper.
        return AnyView(
            build(content, t: t, r: r, b: b, l: l,
                  parentWidth: CGFloat(context.viewportWidth),
                  hAuto: hAuto, vAuto: vAuto)
        )
    }

    // Compose the final view. Order matters: outer `.padding` creates the
    // CSS outer-space, then `.offset` handles the negative-margin pull,
    // then `.frame(maxWidth/maxHeight: .infinity, alignment:)` implements
    // `margin: auto` centering/pushing.
    @ViewBuilder
    private func build(_ content: Content,
                       t: ResolvedLength, r: ResolvedLength,
                       b: ResolvedLength, l: ResolvedLength,
                       parentWidth: CGFloat,
                       hAuto: HorizontalAutoAlignment,
                       vAuto: VerticalAutoAlignment) -> some View {
        // Resolve each side to a signed CGFloat. `.auto`/`.skip` → 0.
        let tpx = signedPx(t, parentWidth: parentWidth)
        let rpx = signedPx(r, parentWidth: parentWidth)
        let bpx = signedPx(b, parentWidth: parentWidth)
        let lpx = signedPx(l, parentWidth: parentWidth)

        // Split per-side into a non-negative "outer space" (→ `.padding`)
        // and a possibly-negative "pull" (→ `.offset`). A CSS margin of
        // `-10px` on the top maps to `.offset(y: -10)` — the element is
        // pulled upward without reserving space.
        let outerTop    = max(0, tpx)
        let outerBottom = max(0, bpx)
        let outerLeft   = max(0, lpx)
        let outerRight  = max(0, rpx)
        // Net vertical pull: negative top pulls up; negative bottom pulls
        // down (CSS: negative margin-bottom lets following siblings overlap
        // from below — here only the self-offset is reproduced).
        let pullY = min(0, tpx) - min(0, bpx)
        // Net horizontal pull, same logic across the axis.
        let pullX = min(0, lpx) - min(0, rpx)
        // Skip the `.offset` modifier entirely when all margins are ≥ 0.
        let hasPull = pullX != 0 || pullY != 0

        // Start from content, conditionally chain each modifier. The
        // `if`-branches guard against inserting no-op modifiers which would
        // still re-layout in SwiftUI.
        content
            // Outer padding → CSS positive margin (reserves outer space).
            .padding(EdgeInsets(top: outerTop, leading: outerLeft,
                                bottom: outerBottom, trailing: outerRight))
            // Negative-margin visual pull. Omitted when unused so we don't
            // trigger an extra layout pass for the 95% of components that
            // have non-negative margins.
            .modifier(OffsetIf(dx: pullX, dy: pullY, active: hasPull))
            // `margin: auto` → horizontal alignment inside parent's width.
            .modifier(HAutoFrameModifier(mode: hAuto))
            // Vertical `auto` counterpart — `margin: auto 0` etc.
            .modifier(VAutoFrameModifier(mode: vAuto))
    }

    // Convert a ResolvedLength into a signed pixel offset. `.auto`/`.skip`
    // collapse to 0 because their "spacing" is expressed via frame
    // alignment, not a numeric edge.
    private func signedPx(_ r: ResolvedLength, parentWidth: CGFloat) -> CGFloat {
        switch r {
        case .px(let n):      return n
        case .percent(let p): return p * parentWidth
        case .auto, .skip:    return 0
        }
    }

    // Pure predicate — kept inline because the `if case` at the call-site
    // would need four repetitions otherwise.
    private func isPercent(_ r: ResolvedLength) -> Bool {
        if case .percent = r { return true }
        return false
    }
}

// Conditionally applies `.offset(x:y:)`. We avoid unconditional `.offset`
// because it introduces a no-op render pass even when both deltas are 0.
private struct OffsetIf: ViewModifier {
    let dx: CGFloat
    let dy: CGFloat
    let active: Bool
    func body(content: Content) -> some View {
        if active { content.offset(x: dx, y: dy) } else { content }
    }
}

// Horizontal-auto frame handler. `.infinity` maxWidth expands the element's
// footprint to its parent's width; the alignment then slots the real
// content inside. Mirrors CSS's `margin: 0 auto` block-centering idiom.
private struct HAutoFrameModifier: ViewModifier {
    let mode: HorizontalAutoAlignment
    func body(content: Content) -> some View {
        switch mode {
        // No auto on either horizontal side — don't expand the frame.
        case .none:        content
        // Both left+right auto → CSS-equivalent centering.
        case .center:      content.frame(maxWidth: .infinity, alignment: .center)
        // Only right-auto → push element to the leading edge.
        case .pushLeft:    content.frame(maxWidth: .infinity, alignment: .leading)
        // Only left-auto → push element to the trailing edge.
        case .pushRight:   content.frame(maxWidth: .infinity, alignment: .trailing)
        }
    }
}

// Vertical counterpart. Only meaningful inside a bounded-height parent;
// the capture canvas provides one via `.fixedSize(vertical:true)` above
// the inner component, so `auto` still resolves correctly at capture time.
private struct VAutoFrameModifier: ViewModifier {
    let mode: VerticalAutoAlignment
    func body(content: Content) -> some View {
        switch mode {
        // No vertical auto — leave the layout alone.
        case .none:        content
        // Both top+bottom auto → vertical centering.
        case .center:      content.frame(maxHeight: .infinity, alignment: .center)
        // Only bottom-auto → pin to top of the expanded frame.
        case .pushTop:     content.frame(maxHeight: .infinity, alignment: .top)
        // Only top-auto → pin to bottom of the expanded frame.
        case .pushBottom:  content.frame(maxHeight: .infinity, alignment: .bottom)
        }
    }
}

// View helper — parallels `engineSpacingPadding(_:context:)`.
extension View {
    // Attach margin handling to a view. Safe to call with a nil config —
    // the applier early-returns with no modifier cost.
    func engineSpacingMargin(_ config: MarginConfig?,
                             context: SpacingContext) -> some View {
        modifier(MarginApplier(config: config, context: context))
    }
}

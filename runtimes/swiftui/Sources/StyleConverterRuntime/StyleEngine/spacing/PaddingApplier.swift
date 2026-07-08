//
//  PaddingApplier.swift
//  StyleEngine/spacing — Phase 2.
//
//  ViewModifier that applies a PaddingConfig. Absolute pixels go straight
//  into `.padding(side, n)`. Percent sides use a GeometryReader to read
//  the parent width (CSS spec says padding-% is always parent-WIDTH, even
//  for vertical sides). Unresolvable values render as 0.
//

// SwiftUI for ViewModifier + GeometryReader.
import SwiftUI

// Public modifier — attached by StyleBuilder.applyStyle via the view
// extension defined below. Zero cost when the config has no padding.
struct PaddingApplier: ViewModifier {
    // The padding to apply. Nil means "not touched" — we still emit the
    // modifier because the caller always chains it; body bails immediately.
    let config: PaddingConfig?
    // Render context threaded from StyleBuilder (fontSizePx, viewport size).
    let context: SpacingContext

    func body(content: Content) -> some View {
        // Fast path: no padding at all.
        guard let cfg = config, cfg.hasAny else { return AnyView(content) }

        // Resolve each side once. Percent lives in a separate lane so we
        // only attach a GeometryReader if at least one side needs it.
        let t = SpacingResolver.resolve(cfg.top, ctx: context, isPadding: true)
        let r = SpacingResolver.resolve(cfg.right, ctx: context, isPadding: true)
        let b = SpacingResolver.resolve(cfg.bottom, ctx: context, isPadding: true)
        let l = SpacingResolver.resolve(cfg.left, ctx: context, isPadding: true)

        // Does any side need parent-width? Keep to the simple `.padding`
        // path when not.
        let hasPercent =
            isPercent(t) || isPercent(r) || isPercent(b) || isPercent(l)

        if hasPercent {
            // GeometryReader path: reads parent width and multiplies.
            // We use a fallback of 390 when reading returns 0 (happens
            // in measurement passes before layout).
            return AnyView(
                GeometryReader { geo in
                    let parentW = geo.size.width > 0 ? geo.size.width : 390
                    content.padding(edges(top: t, right: r, bottom: b, left: l,
                                          parentWidth: parentW))
                }
            )
        }

        // All-absolute fast path.
        return AnyView(
            content.padding(edges(top: t, right: r, bottom: b, left: l,
                                  parentWidth: CGFloat(context.viewportWidth)))
        )
    }

    // Convert the four ResolvedLengths to an EdgeInsets. Percent sides
    // multiply by parentWidth per CSS spec.
    private func edges(top: ResolvedLength, right: ResolvedLength,
                       bottom: ResolvedLength, left: ResolvedLength,
                       parentWidth: CGFloat) -> EdgeInsets {
        EdgeInsets(
            top: pxOf(top, parentWidth: parentWidth),
            leading: pxOf(left, parentWidth: parentWidth),
            bottom: pxOf(bottom, parentWidth: parentWidth),
            trailing: pxOf(right, parentWidth: parentWidth)
        )
    }

    // Resolve one side to a concrete CGFloat. `.skip` collapses to 0 so
    // we never crash, matching the "degrade gracefully" rule.
    private func pxOf(_ r: ResolvedLength, parentWidth: CGFloat) -> CGFloat {
        switch r {
        case .px(let n):       return n
        case .percent(let p):  return p * parentWidth
        case .auto, .skip:     return 0
        }
    }

    // True iff the side is a percentage and therefore needs GeometryReader.
    private func isPercent(_ r: ResolvedLength) -> Bool {
        if case .percent = r { return true }
        return false
    }
}

// Thin View extension so StyleBuilder callers can write
// `.engineSpacingPadding(cfg, context)` without importing the modifier type.
extension View {
    func engineSpacingPadding(_ config: PaddingConfig?,
                              context: SpacingContext) -> some View {
        modifier(PaddingApplier(config: config, context: context))
    }
}

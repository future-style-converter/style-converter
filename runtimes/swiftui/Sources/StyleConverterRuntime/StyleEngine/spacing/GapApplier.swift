//
//  GapApplier.swift
//  StyleEngine/spacing — Phase 2.
//
//  Gap isn't a ViewModifier; it feeds the `spacing:` parameter of the
//  flex / grid container (HStack / VStack / LazyVGrid) created by
//  ComponentRenderer. This file exposes a helper that resolves a GapConfig
//  against a SpacingContext + parent width and returns final (row, col)
//  CGFloats ready for the container constructor.
//

// CoreGraphics for CGFloat.
import CoreGraphics

enum GapApplier {

    // Resolve a GapConfig to concrete pixel values. Percent gaps are
    // resolved against `parentWidth` — the CSS spec says % gap resolves
    // against the same dimension the axis runs along, but SwiftUI only
    // offers one `spacing:` per axis so we approximate: row uses height,
    // column uses width. When parentHeight is unknown we fall back to
    // viewportHeight from the context.
    static func resolve(_ cfg: GapConfig?,
                        context: SpacingContext,
                        parentWidth: CGFloat? = nil,
                        parentHeight: CGFloat? = nil) -> (row: CGFloat, column: CGFloat) {
        // Nothing configured → 0/0 means "use container default".
        guard let cfg = cfg else { return (0, 0) }

        let pw = parentWidth ?? CGFloat(context.viewportWidth)
        let ph = parentHeight ?? CGFloat(context.viewportHeight)

        // Row-gap is spacing between rows → percent resolves vs height.
        let row = pxOf(SpacingResolver.resolve(cfg.row, ctx: context, isPadding: true),
                       basis: ph)
        // Column-gap is spacing between columns → percent resolves vs width.
        let col = pxOf(SpacingResolver.resolve(cfg.column, ctx: context, isPadding: true),
                       basis: pw)
        return (row, col)
    }

    // Fold a ResolvedLength down to a CGFloat, using `basis` for percent.
    private static func pxOf(_ r: ResolvedLength, basis: CGFloat) -> CGFloat {
        switch r {
        case .px(let n):       return n
        case .percent(let p):  return p * basis
        case .auto, .skip:     return 0
        }
    }
}

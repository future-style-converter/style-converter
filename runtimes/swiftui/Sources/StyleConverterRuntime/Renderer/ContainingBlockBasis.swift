//
//  ContainingBlockBasis.swift
//  StyleConverterRuntime — wave 9 (containing-block height channel).
//
//  Pure geometry for the containing-block bases ComponentRenderer
//  publishes down the environment channels (ContainingBlock.swift):
//
//    • contentBox — the basis for IN-FLOW children (css-sizing-3 §5.1 /
//      CSS 2.1 §10.1: the containing block of a non-positioned box is
//      the CONTENT edge of its block-container ancestor). This is the
//      exact math that lived inline in ComponentRenderer.flexContentSize
//      since wave 2 — moved here so XCTests can pin it without a view.
//
//    • paddingBox — the basis for ABSOLUTELY-POSITIONED children
//      (css-position-3 §3.1: "the containing block is the PADDING box"
//      of the nearest positioned ancestor). The wave-8 overlay anchor
//      already insets by the border band only — offsets land on the
//      padding-box corner — but the PERCENT basis kept subtracting the
//      padding band too (content box), so `width/height: 100%` on an
//      abspos child of a padded ancestor under-resolved by the padding.
//      Wave 9 aligns the basis with the anchor: border box − borders.
//
//  Both return nil when the ancestor's own size is not statically
//  definite (fit-content / auto / unresolvable) — the channels then
//  reset to nil and children fall back per channel (canvas basis for
//  width, percent-skip for height), so this file never invents geometry.
//

// CoreGraphics for CGFloat; the style/config types are engine-internal.
import CoreGraphics

enum ContainingBlockBasis {

    /// The ancestor's DEFINITE border-box size on one axis, or nil.
    /// Width resolves against the ancestor's own threaded containing
    /// block (wave 3); height resolves against the wave-9 height basis
    /// when one exists — percent heights stay skipped without a basis
    /// (CSS 2.1 §10.5 auto degradation, the ScrollView rationale).
    static func definiteBorderBox(style: ComponentStyle,
                                  vertical: Bool) -> CGFloat? {
        // The threaded resolver context (viewport, bases, font size).
        let ctx = style.spacing.context
        if vertical {
            // Definite ancestor basis for the ancestor's OWN percent
            // height — nil means indefinite, so its percent height is
            // auto and the chain stays honestly nil.
            let basisH = ctx.containingBlockHeightPx.map { CGFloat($0) }
            return SizeApplierResolve.exact(style.size.height, ctx: ctx,
                                            // vh still needs the viewport
                                            // when no basis exists…
                                            parent: basisH ?? CGFloat(ctx.viewportHeight),
                                            // …but percent only resolves
                                            // against a DEFINITE basis.
                                            allowPercent: basisH != nil)
        }
        // Width axis — the same wave-3 lane SizeApplier paints with:
        // percent of the threaded containing block (canvas at root).
        return SizeApplierResolve.exact(style.size.width, ctx: ctx,
                                        parent: ctx.containingBlockWidth)
    }

    /// The resolved padding band on one axis (left+right or top+bottom),
    /// through the SAME resolver lane PaddingApplier paints with so the
    /// subtraction always agrees with the painted inset.
    static func paddingBand(style: ComponentStyle, vertical: Bool) -> CGFloat {
        // No padding config → zero band.
        guard let p = style.spacing.padding else { return 0 }
        // Per-edge resolution — mirrors the pre-wave-9 flexContentSize
        // byte-for-byte (percent padding resolves against the viewport
        // width there; kept identical to avoid moving any baseline).
        func px(_ lv: LengthValue) -> CGFloat {
            switch SpacingResolver.resolve(lv, ctx: style.spacing.context,
                                           isPadding: true) {
            case .px(let n):      return n
            case .percent(let f): return f * CGFloat(style.spacing.context.viewportWidth)
            case .auto, .skip:    return 0
            }
        }
        // Vertical axis sums top+bottom, horizontal left+right.
        return vertical ? (px(p.top) + px(p.bottom)) : (px(p.left) + px(p.right))
    }

    /// The PAINTED border band on one axis. `border-style: none` means a
    /// used width of 0 (CSS 2.1 §8.5.3) — the same hasBorder gate the
    /// paint chain uses, so geometry and paint can never disagree.
    static func borderBand(style: ComponentStyle, vertical: Bool) -> CGFloat {
        // No border config → zero band.
        guard let b = style.borderSides else { return 0 }
        // Used width per side: painted sides only.
        func bw(_ s: BorderSideConfig) -> CGFloat {
            s.hasBorder ? (s.effectiveWidth ?? 0) : 0
        }
        // Vertical axis sums top+bottom, horizontal start+end.
        return vertical ? (bw(b.top) + bw(b.bottom)) : (bw(b.start) + bw(b.end))
    }

    /// CONTENT-box basis (in-flow children): definite border-box size
    /// minus the padding band and the painted borders. Nil when the
    /// size is indefinite or the subtraction degenerates (≤ 0) — the
    /// exact contract flexContentSize has had since wave 2.
    static func contentBox(style: ComponentStyle, vertical: Bool) -> CGFloat? {
        // Indefinite ancestor → no basis to publish.
        guard let box = definiteBorderBox(style: style, vertical: vertical)
        else { return nil }
        // Border box − padding − borders = content box (CSS 2.1 §8.1).
        let v = box - paddingBand(style: style, vertical: vertical)
                    - borderBand(style: style, vertical: vertical)
        // Degenerate (over-padded) boxes publish nil, never negative.
        return v > 0 ? v : nil
    }

    /// PADDING-box basis (absolutely-positioned children, css-position-3
    /// §3.1): definite border-box size minus ONLY the painted borders —
    /// the ancestor's padding stays INSIDE the containing block, exactly
    /// like the wave-8 overlay anchor (border-band inset only).
    static func paddingBox(style: ComponentStyle, vertical: Bool) -> CGFloat? {
        // Indefinite ancestor → no basis to publish.
        guard let box = definiteBorderBox(style: style, vertical: vertical)
        else { return nil }
        // Border box − borders = padding box (padding NOT subtracted).
        let v = box - borderBand(style: style, vertical: vertical)
        // Degenerate boxes publish nil, never negative.
        return v > 0 ? v : nil
    }
}

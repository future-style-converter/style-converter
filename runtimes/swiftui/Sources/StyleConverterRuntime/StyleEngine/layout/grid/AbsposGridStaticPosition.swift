//
//  AbsposGridStaticPosition.swift
//  StyleConverterRuntime — wave 11 (lane IOS grid-abspos).
//
//  The STATIC POSITION of an absolutely-positioned child of a GRID
//  container. css-grid-1 §9: an abspos child is NOT a grid item — it is
//  out-of-flow and takes no grid cell (the renderer's wave-3 partition,
//  ComponentRenderer.isOutOfFlow, already keeps it out of grid
//  placement; the pins in AbsposGridStaticPositionTests re-assert that
//  for grid-shaped children). css-grid-1 §9.2: with auto insets its
//  static position is determined "as if it were the sole grid item in a
//  grid area whose edges coincide with the CONTENT edges of the grid
//  container" — i.e. the child self-aligns (align-self / justify-self,
//  defaulting to the container's align-items / justify-items,
//  css-align-3 §6.1) inside the container's CONTENT box.
//
//  Geometry: the wave-8 overlay ZStack anchors positioned children at
//  the PADDING-box corner (border-band inset — css-position-3 §3.1's
//  containing block for children WITH explicit insets, which is why the
//  anchor itself must not move). The §9.2 shift computed here therefore
//  starts from the padding-box corner: padding-start edge (padding box →
//  content box) + the sole-item alignment offset within the content
//  extent. Applied per axis, and ONLY on axes with NO explicit inset —
//  css-position-3 §3.5: a non-auto inset replaces the static position
//  on that axis (PositionApplier owns it then).
//
//  Shared-semantics contract: the Compose runtime carries the twin grid
//  static-position math; both platforms pin IDENTICAL (childPx,
//  contentPx, alignment) → offset tables where the scenarios overlap
//  (the css-grid abspos/grid-abspos-staticpos-* WPT family), the same
//  discipline as the wave-10 flex AbsposStaticAlignment tables.
//

// CoreGraphics for CGFloat/CGSize in the view-level convenience.
import CoreGraphics

enum AbsposGridStaticPosition {

    // MARK: - alignment resolution

    /// Container-side items keyword → static-position alignment base.
    /// css-align-3 §6.1 for the abspos static position: only the
    /// positional keywords move the box — `stretch`/`normal` behave as
    /// `start` for an absolutely-positioned box (no stretching without
    /// two insets), `auto`/`baseline`/distribution keywords carry no
    /// static-position claim either. Nil → the caller keeps the start
    /// (content-box origin) anchor, which IS the §9.2 start outcome.
    static func base(ofItems keyword: AlignmentKeyword?) -> AbsposStaticAlignment.Base? {
        switch keyword {
        // start-family: start + the LTR self-start fold (the runtime's
        // horizontal-tb LTR normalization — same fold as the wire table
        // in AbsposStaticAlignment.baseOf).
        case .start, .selfStart:
            return .start
        // The lone center keyword.
        case .center:
            return .center
        // end-family: end + the LTR self-end fold. The converter folds
        // `flex-end` on *-items to .end already (FlexboxExtractor).
        case .end, .selfEnd:
            return .end
        // stretch/normal/auto/baseline/space-* → start behaviour (§6.1
        // abspos rules) — expressed as "no claim" so offset stays 0.
        default:
            return nil
        }
    }

    /// The EFFECTIVE per-axis alignment spec for the sole-item
    /// hypothetical: the child's own self claim (align-self /
    /// justify-self, read through both wire channels incl. the
    /// safe/unsafe overflow keywords) wins; otherwise the container's
    /// *-items default applies (css-align-3 §6.1: `auto` self-alignment
    /// computes to the parent's items value). Items keywords arrive
    /// typed-only, so they never carry an overflow modifier → safe false.
    static func effectiveSpec(childSelf: AbsposStaticAlignment.Spec?,
                              parentItems: AlignmentKeyword?) -> AbsposStaticAlignment.Spec? {
        // Child self claim wins outright (css-align-3 §6.1 precedence).
        if let own = childSelf { return own }
        // Fall back to the container default; nil when unmappable.
        return base(ofItems: parentItems).map {
            AbsposStaticAlignment.Spec(base: $0, safe: false)
        }
    }

    // MARK: - geometry

    /// One-axis static-position offset measured from the overlay's
    /// PADDING-box anchor:
    ///
    ///   offset = paddingStart + crossOffset(child, content, spec)
    ///
    /// where crossOffset is the shared wave-10 factor math (start 0 ·
    /// center free/2 · end free, `safe` falls back to 0 on overflow —
    /// css-align-3 §4.4). Degraded-but-honest fallbacks:
    ///   • spec nil (no alignment claim on either channel) → the §9.2
    ///     start outcome: the content-box origin, i.e. paddingStart;
    ///   • content extent or child extent unknown (indefinite container
    ///     / auto-sized child) → alignment cannot be computed ahead of
    ///     measurement (same TODO as the flex lane's GeometryReader
    ///     note), so only the content-box-origin shift applies.
    static func axisOffset(childPx: Double?,
                           contentPx: Double?,
                           paddingStart: Double,
                           spec: AbsposStaticAlignment.Spec?) -> Double {
        // No claim → start alignment → the content-box origin exactly.
        guard let spec = spec else { return paddingStart }
        // Indefinite extents → honest degrade to the start outcome.
        guard let child = childPx, let content = contentPx else { return paddingStart }
        // Shared factor math — byte-identical to the flex lane (and to
        // the Compose twin's pinned table).
        return paddingStart + AbsposStaticAlignment.crossOffset(childPx: child,
                                                                containerPx: content,
                                                                spec: spec)
    }

    /// The grid container's CONTENT extent on one axis — the §9.2
    /// alignment container. Box-sizing aware (wave 11): under an
    /// effective `box-sizing: content-box` (the WPT capture default, or
    /// an explicit declaration) the DECLARED size already IS the content
    /// box (css-sizing-3 §3), so it is used verbatim; under the
    /// unset/border-box status quo the content box is declared − padding
    /// − painted borders (CSS 2.1 §8.1), via the same resolver helpers
    /// the percent channels use so geometry can never disagree with
    /// paint. Nil when the container's size is indefinite or the
    /// subtraction degenerates (≤ 0) — the caller then degrades to the
    /// start outcome instead of inventing geometry.
    static func contentExtent(_ style: ComponentStyle, vertical: Bool) -> CGFloat? {
        // Definite declared size on this axis, resolved through the
        // shared basis lane (percent/vh handling identical to the
        // containing-block channels). Nil = indefinite → no extent.
        guard let declared = ContainingBlockBasis.definiteBorderBox(style: style,
                                                                    vertical: vertical)
        else { return nil }
        // content-box declaration: declared size IS the content extent.
        if style.size.boxSizing == .contentBox {
            // Degenerate (≤ 0) declarations publish nil, never negative.
            return declared > 0 ? declared : nil
        }
        // border-box status quo: subtract the padding band + painted
        // borders (border-style:none = used width 0, CSS 2.1 §8.5.3).
        let v = declared
            - ContainingBlockBasis.paddingBand(style: style, vertical: vertical)
            - ContainingBlockBasis.borderBand(style: style, vertical: vertical)
        // Over-padded boxes publish nil, never negative.
        return v > 0 ? v : nil
    }

    /// The container's resolved padding START edge (top or leading, px)
    /// — the padding-box → content-box shift on one axis. Rides the
    /// SAME resolver lane PaddingApplier paints with (percent padding
    /// resolves against the viewport width, the documented
    /// PaddingApplier fallback basis) so the shift always agrees with
    /// the painted inset.
    static func paddingStartEdge(_ style: ComponentStyle, top: Bool) -> CGFloat {
        // No padding config → the padding box IS the content box.
        guard let p = style.spacing.padding else { return 0 }
        // Block axis reads the top edge, inline axis the left edge
        // (LTR horizontal-tb — the runtime's only writing mode today).
        let lv = top ? p.top : p.left
        // Shared resolution — mirrors ContainingBlockBasis.paddingBand
        // per edge, byte-for-byte.
        switch SpacingResolver.resolve(lv, ctx: style.spacing.context, isPadding: true) {
        case .px(let n):      return n
        case .percent(let f): return f * CGFloat(style.spacing.context.viewportWidth)
        case .auto, .skip:    return 0
        }
    }

    // MARK: - view-level convenience

    /// The FULL §9.2 static-position shift (from the overlay's
    /// padding-box top-leading anchor) for one abspos child of a grid
    /// container. Zero when:
    ///   • the parent is not a grid container (`display: grid` on the
    ///     Phase-7 aggregate) — block/flex ancestors keep their own
    ///     anchors (the flex lane computes its shift separately, and
    ///     display cannot be flex AND grid, so the two never compose);
    ///   • per axis, the child declares an explicit inset there —
    ///     css-position-3 §3.5, PositionApplier owns that axis (and the
    ///     inset containing block stays the PADDING box, §3.1, which is
    ///     exactly the untouched overlay anchor).
    /// The declared child px is read as the FRAME extent (border-box
    /// status quo — same documented under-count TODO for content-box
    /// children as the flex lane).
    static func staticOffset(parentStyle: ComponentStyle,
                             childProperties: [IRProperty]) -> CGSize {
        // Only grid containers get §9.2 treatment; everything else is
        // byte-identical to the pre-wave-11 render.
        guard parentStyle.layout7?.display == .grid else { return .zero }
        // The child's declared sizing bag — extents for the alignment
        // free-space math (only definite px participate).
        let size = SizeExtractor.extract(from: childProperties)
        // Block (vertical) axis — align-self ← align-items.
        var dy: CGFloat = 0
        if !AbsposStaticAlignment.hasCrossInset(childProperties, vertical: true) {
            // Effective block-axis spec: child align-self beats the
            // container's align-items default (css-align-3 §6.1).
            let spec = effectiveSpec(
                childSelf: AbsposStaticAlignment.resolveSelf(from: childProperties,
                                                             typedType: "AlignSelf",
                                                             cssName: "align-self"),
                parentItems: parentStyle.layout7?.alignItems)
            // Definite child height (frame extent) or nil.
            let childH: Double? = {
                if case .exact(let px)? = size.height { return px }
                return nil
            }()
            // paddingTop + alignment offset within the content height.
            dy = CGFloat(axisOffset(
                childPx: childH,
                contentPx: contentExtent(parentStyle, vertical: true).map(Double.init),
                paddingStart: Double(paddingStartEdge(parentStyle, top: true)),
                spec: spec))
        }
        // Inline (horizontal) axis — justify-self ← justify-items.
        var dx: CGFloat = 0
        if !AbsposStaticAlignment.hasCrossInset(childProperties, vertical: false) {
            // Effective inline-axis spec (same precedence, justify pair).
            let spec = effectiveSpec(
                childSelf: AbsposStaticAlignment.resolveSelf(from: childProperties,
                                                             typedType: "JustifySelf",
                                                             cssName: "justify-self"),
                parentItems: parentStyle.layout7?.justifyItems)
            // Definite child width (frame extent) or nil.
            let childW: Double? = {
                if case .exact(let px)? = size.width { return px }
                return nil
            }()
            // paddingLeft + alignment offset within the content width.
            dx = CGFloat(axisOffset(
                childPx: childW,
                contentPx: contentExtent(parentStyle, vertical: false).map(Double.init),
                paddingStart: Double(paddingStartEdge(parentStyle, top: false)),
                spec: spec))
        }
        // The composed shift from the padding-box anchor.
        return CGSize(width: dx, height: dy)
    }
}

//
//  AbsposStaticOffset.swift
//  StyleConverterRuntime — wave 19 (lane FLEX): the iOS VIEW-LEVEL half
//  of the abspos static-position resolver. The pure, Compose-twinned
//  core (types, axis-mapping table, per-axis offset math) lives in
//  AbsposStaticPosition.swift; this file adds the two helpers only the
//  iOS overlay path needs — the painted-frame extent read (RC-A6) and
//  the CGSize shift ComponentRenderer's positioned-children loop applies.
//  Split out to keep both files inside the ≤300-line contract.
//

// CoreGraphics for CGFloat/CGSize.
import CoreGraphics

extension AbsposStaticPosition {

    /// The child's PAINTED margin-box extent on each axis, from its IR:
    /// declared size + the content-box inflation bands + px margins.
    ///
    /// RC-A6 (wave 19): the wave-18 staticCrossOffset aligned the
    /// DECLARED extent (25px), but the painted frame under the WPT
    /// content-box default is declared + border/padding bands
    /// (SizeApplier's inflatedAxis — 25+4 for safe-003's 2px-bordered
    /// child), so every end/center alignment drifted +band (iOS y64 vs
    /// ref y60). Margins join because the static position places the
    /// MARGIN box (css-flexbox-1 §4.1 hypothetical item) and
    /// MarginApplier pads OUTSIDE the frame — safe-002's 5px margins.
    /// Non-px margin flavors (auto/percent) contribute 0 — css-position-3
    /// §3.7 resolves auto margins of a statically-positioned box to 0,
    /// and an unresolved percent has no honest basis here (documented).
    /// Nil axis = no definite declared size → the caller's honest no-op.
    static func childFrameExtents(childProperties: [IRProperty],
                                  wptCaptureMode: Bool) -> (w: CGFloat?, h: CGFloat?) {
        // Build the child's style through the SAME engine the renderer
        // uses — never a re-implemented reader.
        var s = StyleBuilder.build(from: childProperties)
        // Resolve the box-sizing tri-state exactly like styledContent
        // does (declared wins; unset defaults to content-box only in
        // WPT capture — the dark-stage border-box status quo is frozen).
        s.size.boxSizing = SizeApplierMath.effectiveBoxSizing(
            declared: s.size.boxSizing, wptCaptureMode: wptCaptureMode)
        // The border+padding inflation SizeApplier will paint with —
        // (0,0) unless the effective keyword is contentBox, so the
        // non-WPT path reproduces the wave-18 declared-extent numbers.
        let inflate = StyleBuilder.contentBoxInflation(s)
        // px margins only (see doc above) — the outer band MarginApplier
        // pads the frame with.
        func px(_ v: LengthValue?) -> CGFloat {
            if case .exact(let n)? = v { return CGFloat(n) }
            return 0
        }
        let m = s.spacing.margin
        let marginH = px(m?.left) + px(m?.right)
        let marginV = px(m?.top) + px(m?.bottom)
        // Declared exact sizes; any other flavor is indefinite here.
        var w: CGFloat?
        if case .exact(let n)? = s.size.width { w = CGFloat(n) + inflate.h + marginH }
        var h: CGFloat?
        if case .exact(let n)? = s.size.height { h = CGFloat(n) + inflate.v + marginV }
        return (w, h)
    }

    /// View-level convenience for ComponentRenderer's positioned-child
    /// overlay: the FULL static-position shift (as a CGSize from the
    /// overlay's top-leading PADDING-box anchor) for one abspos child of a
    /// flex container.
    ///
    /// Wave 52 (lane L7, static-position T2) — three corrections, measured
    /// on css-flexbox/abspos/position-absolute-containing-block-002 (iOS
    /// green (69,21) vs ref (76,76), f 0.9373) and
    /// flex-abspos-staticpos-justify-self-001 (every mark (−2,−1)):
    ///   (a) the alignment container is the CONTENT box — css-flexbox-1
    ///       §4.1 "as if it were the sole flex item in the flex container",
    ///       css-align-3 §6.2; `containerW/H` are now content extents (the
    ///       call site passes flexContentSize, not the §3.1 padding box);
    ///   (b) the shift starts at the padding-box anchor, so the padding-
    ///       START edge joins on every axis the static position owns —
    ///       the same term the grid twin adds
    ///       (AbsposGridStaticPosition.axisOffset);
    ///   (c) a child with NO own align-self claim (absent or `auto`) falls
    ///       back to the container's `align-items` (css-align-3 §6.1:
    ///       `auto` computes to the parent's align-items) — the Compose
    ///       flex loops already honour it through the Row/Column
    ///       alignment, which is why Android is right on all six cells.
    /// Per axis, zero when an explicit (non-auto) inset owns the axis
    /// (css-position-3 §3.5 — PositionApplier offsets from the padding box,
    /// the untouched anchor). Unresolvable extents (indefinite container,
    /// auto-sized child) degrade to the content-box origin (padding start),
    /// the start outcome — honest, never a guessed alignment.
    /// The caller gates on the ancestor being a flex container and on
    /// wptCaptureMode (the dark-stage corpus keeps the wave-18
    /// staticCrossOffset behavior byte-identically).
    static func staticOffset(containerProperties: [IRProperty],
                             childProperties: [IRProperty],
                             containerW: CGFloat?,
                             containerH: CGFloat?,
                             wptCaptureMode: Bool) -> CGSize {
        // Physical claims from the shared resolver (inset-gated inside) —
        // byte-parallel with the Compose twin, so the align-items fallback
        // is layered on top here, in the iOS-only half.
        var pos = resolveStatic(containerProperties: containerProperties,
                                childProperties: childProperties)
        // The container's style through the SAME engine the renderer uses:
        // its padding (paint-identical resolution) and align-items keyword.
        let containerStyle = StyleBuilder.build(from: containerProperties)
        // (c) align-items fallback on the CROSS axis when the child makes no
        // claim of its own and no inset owns that axis.
        pos = withAlignItemsFallback(pos, containerProperties: containerProperties,
                                     childProperties: childProperties,
                                     alignItems: containerStyle.layout7?.alignItems)
        // Painted margin-box extents (RC-A6 band + margin math).
        let ext = childFrameExtents(childProperties: childProperties,
                                    wptCaptureMode: wptCaptureMode)
        // One axis: inset → 0; else padding start + the shared alignment math
        // inside the content extent (start outcome when unresolvable).
        func off(_ spec: AxisSpec?, _ child: CGFloat?, _ content: CGFloat?,
                 vertical: Bool) -> CGFloat {
            // §3.5: a non-auto inset replaces the static position — the
            // overlay's padding-box anchor is already its containing block.
            if hasNonAutoInset(childProperties, vertical: vertical) { return 0 }
            // (b) the padding-box → content-box shift on this axis.
            let pad = AbsposGridStaticPosition.paddingStartEdge(containerStyle, top: vertical)
            // No claim, or an extent we cannot know ahead of measurement.
            guard let spec, let child, let content else { return pad }
            // (a) align inside the CONTENT extent (reversal-aware math).
            return pad + CGFloat(axisOffset(childPx: Double(child),
                                            containerPx: Double(content),
                                            spec: spec))
        }
        return CGSize(width: off(pos.x, ext.w, containerW, vertical: false),
                      height: off(pos.y, ext.h, containerH, vertical: true))
    }

    /// Wave 52 (lane L7, T2c) — layer the container's `align-items` onto
    /// the CROSS axis of a resolved static position when the child has no
    /// align-self of its own (css-align-3 §6.1: `align-self: auto` computes
    /// to the parent's `align-items`). A child that DECLARES align-self —
    /// typed non-auto, or any Generic `safe|unsafe <pos>` — keeps
    /// resolveStatic's answer even when that answer is "no claim" (an
    /// explicit `stretch`/`normal` behaves as start for an abspos box), and
    /// an explicit inset on the cross axis keeps the axis nil (§3.5).
    /// Non-positional items keywords (stretch/normal/baseline/absent) make
    /// no claim (AbsposGridStaticPosition.base(ofItems:) — the ONE table).
    static func withAlignItemsFallback(_ pos: StaticPos,
                                       containerProperties: [IRProperty],
                                       childProperties: [IRProperty],
                                       alignItems: AlignmentKeyword?) -> StaticPos {
        // Positional align-items only; nil leaves the resolver untouched.
        guard let base = AbsposGridStaticPosition.base(ofItems: alignItems) else { return pos }
        // The child's own align-self (typed non-auto, or Generic) wins.
        let declares = childProperties.contains { p in
            (p.type == "AlignSelf" &&
                ValueExtractors.extractKeyword(p.data)?.uppercased() != "AUTO") ||
            (p.type == "Generic" && {
                if case .object(let o) = p.data { return o["propertyName"]?.stringValue == "align-self" }
                return false
            }())
        }
        if declares { return pos }
        // Which physical axis is the cross axis, and its direction.
        let map = axisMap(
            flexDirection: containerProperties.first { $0.type == "FlexDirection" }
                .flatMap { ValueExtractors.extractKeyword($0.data) },
            writingMode: containerProperties.first { $0.type == "WritingMode" }
                .flatMap { ValueExtractors.extractKeyword($0.data) },
            direction: containerProperties.first { $0.type == "Direction" }
                .flatMap { ValueExtractors.extractKeyword($0.data) })
        // Cross = vertical for a horizontal main axis (css-flexbox-1 §5).
        let crossVertical = map.mainIsHorizontal
        // §3.5: a non-auto cross inset keeps the axis to PositionApplier.
        if hasNonAutoInset(childProperties, vertical: crossVertical) { return pos }
        // The fallback claim, carrying the cross axis's own reversal.
        let spec = AxisSpec(base: base, safe: false, reversed: map.crossReversed)
        return crossVertical
            ? StaticPos(x: pos.x, y: pos.y ?? spec, justifyTyped: pos.justifyTyped)
            : StaticPos(x: pos.x ?? spec, y: pos.y, justifyTyped: pos.justifyTyped)
    }
}

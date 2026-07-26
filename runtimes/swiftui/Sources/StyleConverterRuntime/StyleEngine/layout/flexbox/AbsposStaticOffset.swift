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
    /// overlay's top-leading padding-box anchor) for one abspos child of
    /// a flex container. Per axis, zero when any input is unresolvable:
    ///   • no claim (inset owns the axis per §3.5, or nothing aligns it);
    ///   • container extent indefinite (nil channel);
    ///   • child extent indefinite (auto-sized — cannot be safe-checked
    ///     or end-aligned ahead of measurement; honest skip, visible
    ///     here rather than silently absorbed).
    /// The caller gates on the ancestor being a flex container and on
    /// wptCaptureMode (the dark-stage corpus keeps the wave-18
    /// staticCrossOffset behavior byte-identically).
    static func staticOffset(containerProperties: [IRProperty],
                             childProperties: [IRProperty],
                             containerW: CGFloat?,
                             containerH: CGFloat?,
                             wptCaptureMode: Bool) -> CGSize {
        // Physical claims from the shared resolver (inset-gated inside).
        let pos = resolveStatic(containerProperties: containerProperties,
                                childProperties: childProperties)
        // Painted margin-box extents (RC-A6 band + margin math).
        let ext = childFrameExtents(childProperties: childProperties,
                                    wptCaptureMode: wptCaptureMode)
        // Per-axis shared math; 0 for any unresolvable input (see doc).
        func off(_ spec: AxisSpec?, _ child: CGFloat?, _ container: CGFloat?) -> CGFloat {
            guard let spec, let child, let container else { return 0 }
            return CGFloat(axisOffset(childPx: Double(child),
                                      containerPx: Double(container),
                                      spec: spec))
        }
        return CGSize(width: off(pos.x, ext.w, containerW),
                      height: off(pos.y, ext.h, containerH))
    }
}

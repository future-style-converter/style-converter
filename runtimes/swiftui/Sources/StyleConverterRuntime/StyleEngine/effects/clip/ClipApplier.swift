//
//  ClipApplier.swift
//  StyleEngine/effects/clip — Phase 8.
//
//  Consumes a `ClipConfig` and emits SwiftUI `.clipShape(_:)` /
//  `.mask(_:)` modifiers. Most shapes route through a GeometryReader
//  so percent-based coordinates resolve against the actual view box.
//

import SwiftUI

struct ClipApplier: ViewModifier {
    // Optional — nil means no clip applied.
    let config: ClipConfig?

    func body(content: Content) -> some View {
        // Short-circuit when nothing was written.
        guard let cfg = config, cfg.touched else { return AnyView(content) }

        // Legacy clip rect composes on top of the shape (CSS treats them
        // independently). We stack them: apply legacy first, shape second.
        var v: AnyView = AnyView(content)

        // clip-path branch. Every shape resolves against the css-masking-1
        // §7.1 REFERENCE BOX (wave 46, lane Y4): `ref` carries the
        // `<geometry-box>` keyword + the element's box metrics, and each
        // Shape derives the box from its border-box rect at draw time
        // (ClipReferenceBox). With no keyword and no metrics that IS the
        // rect — the pre-wave-46 arithmetic, byte for byte.
        let ref = ClipRef(box: cfg.geometryBox, metrics: cfg.box)
        if let shape = cfg.shape {
            switch shape {
            case .none:
                // Explicit no-op. Preserved as a touched-state marker
                // that the applier recognised the property.
                break
            case .geometryBoxOnly:
                // The reference box itself, corner curves included —
                // cuts an outline / shadow painted past the box (WPT
                // clip-path-marginBox-1b/1c/1d, contentBox-1d/1e).
                v = AnyView(v.clipShape(ReferenceBoxClip(ref: ref)))
            case .url(let id):
                // No SwiftUI equivalent for url(#id) — log once per run
                // and leave the view unclipped. TODO: SVG clip lookup.
                _ = id
            case .inset(let sides, let cr, let crFrac):
                // `.clipShape` with a rounded-rect inset. CSS `round
                // <pct>` resolves against the box at draw time — pass
                // the fraction through so InsetShape can compute it
                // from `rect.size` (matches the Android fix in
                // ClipPathApplier.kt).
                v = AnyView(v.clipShape(InsetShape(sides: sides, cornerRadius: cr,
                                                   cornerRadiusFraction: crFrac, ref: ref)))
            case .circle(let r, let kind, let cx, let cy):
                v = AnyView(v.clipShape(CircleClip(radius: r, kind: kind,
                                                    cx: cx, cy: cy, ref: ref)))
            case .ellipse(let rx, let ry, let rxK, let ryK, let cx, let cy):
                v = AnyView(v.clipShape(EllipseClip(rx: rx, ry: ry,
                                                     rxKind: rxK, ryKind: ryK,
                                                     cx: cx, cy: cy, ref: ref)))
            case .polygon(let pts):
                // css-masking-1 §7.1: `clip-rule` selects the polygon's
                // fill rule — threaded through `.clipShape`'s FillStyle
                // (the Path itself has no winding flag).
                v = AnyView(v.clipShape(PolygonClip(points: pts, ref: ref),
                                        style: FillStyle(eoFill: cfg.rule == .evenodd)))
            case .path(let d):
                // css-shapes-1 §3.1 `path(<fill-rule>?, …)`: the rule
                // INSIDE the function wins; `clip-rule` is the fallback.
                let eo = (cfg.pathFillRule ?? cfg.rule) == .evenodd
                v = AnyView(v.clipShape(SvgPathClip(data: d, ref: ref),
                                        style: FillStyle(eoFill: eo)))
            case .rect(let t, let r, let b, let l, let cr):
                v = AnyView(v.clipShape(RectClip(top: t, right: r, bottom: b,
                                                   left: l, cornerRadius: cr, ref: ref)))
            case .xywh(let x, let y, let w, let h, let cr):
                v = AnyView(v.clipShape(XywhClip(x: x, y: y, w: w, h: h,
                                                   cornerRadius: cr, ref: ref)))
            }
        }

        // Legacy clip rect: outset-oriented rect in CSS coordinates.
        if case .rect(let t, let r, let b, let l) = cfg.legacy {
            v = AnyView(v.clipShape(LegacyClipRect(top: t, right: r,
                                                    bottom: b, left: l)))
        }

        return v
    }
}

extension View {
    // Identity when nil — common case.
    func engineClipPath(_ config: ClipConfig?) -> some View {
        modifier(ClipApplier(config: config))
    }
}

// MARK: - Shape implementations

// The reference-box selector every clip shape carries: which
// `<geometry-box>` and the element's metrics. `frame(in:)` turns the
// border-box rect `.clipShape` hands the Shape into that box.
struct ClipRef: Equatable {
    let box: ClipGeometryBox
    let metrics: ClipBoxMetrics
    func frame(in rect: CGRect) -> ClipReferenceFrame {
        ClipReferenceBox.resolve(box, metrics: metrics, in: rect)
    }
}

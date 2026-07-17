//
//  BoxShadowApplier.swift
//  StyleEngine/effects/shadow — Phase 5.
//
//  Stacks one SwiftUI `.shadow(...)` per outset layer, and draws inset
//  layers as an overlay clipped to the rounded-box interior. Multiple
//  layers are supported because SwiftUI's `.shadow` is composable:
//  every call wraps the view in a new shadow-generating layer, so
//  repeated calls produce CSS-accurate stacking.
//
//  Spread is not a SwiftUI primitive; we approximate by scaling the
//  overlay shape by `spread / min(width,height)` before applying the
//  shadow. That's noticeably off for large spreads — documented
//  limitation, matches Android's shape of the workaround.
//
//  Blur radius: CSS blur roughly equals 2× SwiftUI's `.shadow(radius:)`
//  visually. We divide by 2 at apply time so the three renderers
//  look close enough for SSIM parity.
//

// SwiftUI for shadow / overlay primitives.
import SwiftUI

struct BoxShadowApplier: ViewModifier {
    // Nil / empty → identity.
    let config: BoxShadowConfig?
    // Optional radius so the inset-shadow mask hugs the rounded shape.
    let radius: BorderRadiusConfig?

    func body(content: Content) -> some View {
        guard let cfg = config, cfg.hasAny else { return AnyView(content) }
        // Split into outset + inset — order preserved within each bucket
        // so painting order follows CSS semantics.
        let outset = cfg.layers.filter { !$0.inset }
        let inset  = cfg.layers.filter {  $0.inset }

        // Start with the bare content; iterate outset layers from the
        // innermost (first in CSS) to the outermost so they compose
        // correctly. SwiftUI applies `.shadow` to the most recent view.
        var v: AnyView = AnyView(content)
        if !outset.isEmpty {
            // css-backgrounds-3 §7.1: an outer box-shadow is cast by the
            // element's BORDER BOX as one silhouette. SwiftUI's `.shadow`
            // instead shadows every opaque pixel of the modified view
            // INDIVIDUALLY — so the label Text inside a box cast its own
            // offset dark duplicate visible INSIDE the box (ghost text
            // under every offset shadow). `.compositingGroup()` flattens
            // the subtree into a single rasterized layer first (Apple
            // docs: "applies subsequent effects … to the view as a
            // whole"), so each `.shadow` below sees one flattened
            // silhouette and casts exactly ONE shadow.
            v = AnyView(v.compositingGroup())
        }
        for layer in outset {
            let colour = layer.color ?? .black.opacity(0.25)
            // CSS blur ≈ 2× SwiftUI radius.
            let radius = layer.blur / 2
            if layer.spread > 0 {
                // Spread path — SwiftUI's `.shadow(...)` doesn't take a
                // spread argument, so the only-spread case (`box-shadow:
                // 0 0 0 4px blue`) used to render nothing on iOS while
                // Android (`Compose Modifier.shadow` with native spread)
                // and web both painted a hard blue ring. Workaround:
                // draw a copy of the rounded shape behind the element,
                // expanded by `spread` on every side via a negative
                // padding. The shape is drawn at the shadow colour and
                // optionally blurred + offset to approximate the rest of
                // the layer's parameters. `.padding(-spread)` is the
                // standard SwiftUI idiom for "give me this much extra
                // size beyond my parent's bounds" — the shape paints
                // across the inflated frame, producing the spread halo.
                let cornerRadius = self.radius ?? BorderRadiusConfig()
                // Fidelity wave 1: CSS Backgrounds 3 §7.1.1 — an OUTER
                // shadow "is clipped out" over the border-box area: it
                // must never paint UNDER the element. The old negative-
                // padding fill covered the whole border box too, which
                // was invisible under opaque elements but tinted every
                // translucent one — borders/016_Decorated's `opacity:
                // 0.85` ellipse composited the blue ring through its
                // whole fill (pixel-probe: iOS fill (214,153,44) vs
                // web's clean (210,137,22)). Paint the spread halo,
                // then punch the border box back out with a
                // destination-out layer inside one compositing group.
                v = AnyView(v.background(
                    ZStack {
                        // The spread-expanded silhouette, blurred and
                        // offset per the layer (blur ≈ CSS/2, see header).
                        BorderRadiusShape(radius: cornerRadius)
                            .inset(by: -layer.spread)
                            .fill(colour)
                            .blur(radius: radius)
                            .offset(x: layer.x, y: layer.y)
                        // §7.1.1 clip: erase the border-box region so
                        // nothing of the shadow survives under the
                        // (possibly translucent) element itself.
                        BorderRadiusShape(radius: cornerRadius)
                            .fill(Color.black)
                            .blendMode(.destinationOut)
                    }
                    // The punch-out only composites against THIS pair —
                    // without the group it would erase the canvas too.
                    .compositingGroup()
                ))
            } else {
                // Pure blur (or zero) shadow — keep the lightweight
                // `.shadow(...)` path. It's a single Core Animation layer
                // and doesn't allocate an extra view.
                v = AnyView(v.shadow(color: colour, radius: radius,
                                     x: layer.x, y: layer.y))
            }
        }

        // Inset layers go on top as an overlay masked to the element's
        // rounded shape. Each layer is punched-hole geometry (see
        // InsetShadow below): a colour slab with the offset/inset border
        // box subtracted, blurred so the edge leaks into the interior.
        if !inset.isEmpty {
            let shape = BorderRadiusShape(radius: radius ?? BorderRadiusConfig())
            v = AnyView(v.overlay(
                ZStack {
                    ForEach(Array(inset.enumerated()), id: \.offset) { _, layer in
                        InsetShadow(layer: layer, shape: shape)
                    }
                }
                .allowsHitTesting(false)
            ))
        }
        return v
    }
}

// Private helper — one inset layer, drawn with punched-hole geometry
// mirroring Android's ShadowApplier.applyInsetShadows. The previous
// approximation stroked a CENTERED ring at the element edge and offset
// the whole blurred ring — which left an opaque colour band on all four
// edges regardless of the layer's (x, y), instead of the spec picture
// (css-backgrounds-3 §7.1: the inner shadow darkens the side the offset
// moves AWAY from and vanishes on the opposite side). Punched-hole fix:
//   1. fill a rect inflated by blur+spread with the shadow colour,
//   2. subtract the border-box shape offset by (x, y) and inset by
//      spread (even-odd fill leaves the hole empty),
//   3. blur by blur/2 (SwiftUI `.blur(radius:)` ≈ Gaussian σ; CSS blur r
//      means σ = r/2 — same conversion as the outset path header),
//   4. mask to the element shape so only the interior leak survives.
private struct InsetShadow: View {
    let layer: BoxShadowLayer
    let shape: BorderRadiusShape

    var body: some View {
        // Nil colour → CSS `currentColor` fallback, matching the outset
        // path's default translucent black.
        let colour = layer.color ?? .black.opacity(0.35)
        InsetShadowShape(box: shape, layer: layer)
            // Even-odd fill is what turns the two subpaths (outer rect +
            // offset/inset hole) into "everything BUT the hole".
            .fill(colour, style: FillStyle(eoFill: true))
            // Step 3: soften the hole edge; the inward half of the
            // blurred edge is the visible inner shadow.
            .blur(radius: InsetShadowGeometry.blurRadius(for: layer))
            // Step 4: clip to the element — the outer slab and the
            // outward blur half must never paint outside the box.
            .mask(shape)
    }
}

// The punched-hole path. Internal (not private) so XCTests can exercise
// path(in:) point-membership directly.
struct InsetShadowShape: Shape {
    // The element's border-box shape — supplies the rounded hole outline.
    let box: BorderRadiusShape
    // The inset layer whose offset/spread/blur drive the geometry.
    let layer: BoxShadowLayer

    func path(in rect: CGRect) -> Path {
        // All rects derive from one testable geometry computation.
        let g = InsetShadowGeometry.compute(bounds: rect, layer: layer)
        var p = Path()
        // Subpath 1 — the colour slab, inflated past the bounds so the
        // post-fill blur can't pull transparency in from the slab's own
        // outer edge (Android uses the same blur+spread+50 padding).
        p.addRect(g.outerRect)
        // Subpath 2 — the hole: the border-box shape shifted by the CSS
        // offset and shrunk by spread. BorderRadiusShape.inset(by:) also
        // shrinks each corner radius (clamped at 0), matching Android's
        // `(radius - spread).coerceAtLeast(0)` hole corners; a NEGATIVE
        // spread grows the hole outward the same way.
        p.addPath(box.inset(by: g.holeInset).path(in: g.holeRect))
        return p
    }
}

// Pure geometry for one inset layer — separated from the View so the
// parameters are pinnable by unit tests without rendering.
struct InsetShadowGeometry: Equatable {
    // The inflated colour slab (subpath 1).
    let outerRect: CGRect
    // The border-box rect shifted by the layer offset (hole placement).
    let holeRect: CGRect
    // Spread passed to BorderRadiusShape.inset(by:) — shrinks the hole
    // (positive spread pushes the shadow further into the element).
    let holeInset: CGFloat
    // SwiftUI blur radius: CSS blur r ⇒ Gaussian σ = r/2.
    let blurRadius: CGFloat

    // Single source of truth for the CSS→SwiftUI blur conversion, shared
    // by compute() and the view's `.blur(radius:)` call.
    static func blurRadius(for layer: BoxShadowLayer) -> CGFloat {
        layer.blur / 2
    }

    static func compute(bounds: CGRect, layer: BoxShadowLayer) -> InsetShadowGeometry {
        // Bleed distance: the slab edge must sit far enough out that its
        // own blurred edge can't leak back through the mask. blur +
        // max(spread, 0) + 50 mirrors Android ShadowApplier's
        // `outerPadding` so both platforms share one derivation.
        let pad = layer.blur + max(layer.spread, 0) + 50
        return InsetShadowGeometry(
            outerRect: bounds.insetBy(dx: -pad, dy: -pad),
            // css-backgrounds-3 §7.1: inset offsets move the SHADOW-FREE
            // hole in the offset direction, so the shadow band appears on
            // the top/left for a positive (x, y) — offsetting the hole
            // rect by (x, y) reproduces exactly that.
            holeRect: bounds.offsetBy(dx: layer.x, dy: layer.y),
            holeInset: layer.spread,
            blurRadius: blurRadius(for: layer)
        )
    }
}

// View chain helper.
extension View {
    func engineBoxShadow(_ config: BoxShadowConfig?,
                         radius: BorderRadiusConfig? = nil) -> some View {
        modifier(BoxShadowApplier(config: config, radius: radius))
    }
}

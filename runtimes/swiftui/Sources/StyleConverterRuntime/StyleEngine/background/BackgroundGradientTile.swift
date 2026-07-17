//
//  BackgroundGradientTile.swift
//  StyleEngine/background — gradient-geometry lane.
//
//  Ends the "gradients ignore background-size/position/repeat" gap:
//  BackgroundImageApplier routes a gradient layer here whenever any of
//  those knobs is non-initial, and this Canvas paints the tile lattice
//  from BackgroundImageGeometry — the SAME placement math url() raster
//  layers use — filling each tile rect with a GraphicsContext.Shading
//  gradient whose endpoints are computed against the TILE size and
//  translated per rect (css-backgrounds-3 §3.6/§3.7: the image is sized
//  and positioned first, then tiled; each copy is a full gradient).
//
//  Colour fidelity: stops go through GradientApplier.toGradient — the
//  identical sRGB-subdivided ramp the full-box view path uses — so the
//  geometry-routed and default renderings of the same gradient match.
//

import SwiftUI

/// The paint view for one GRADIENT layer with non-initial
/// size/position/repeat. Sized by the parent `.background(...)` slot,
/// which is exactly the CSS painting area (same contract as
/// BackgroundURLImageView).
struct BackgroundGradientTileView: View {
    /// The gradient layer (linear/radial/conic/repeating).
    let layer: BackgroundImageLayer
    /// Per-layer background-size (nil = auto = the full box, §3.9).
    let sizeLayer: BackgroundSizeLayer?
    /// Per-axis background-position (nil = 0%).
    let positionX: BackgroundAxisPosition?
    let positionY: BackgroundAxisPosition?
    /// Per-layer background-repeat (nil = repeat, the CSS initial).
    let repeatLayer: BackgroundRepeatLayer?

    var body: some View {
        Canvas { context, size in
            // The painting area bounds the pattern (§2.3): a Canvas draw
            // is NOT implicitly clipped to its frame, so phase-shifted /
            // anchor-offset tiles would bleed outside the element.
            var context = context
            context.clip(to: Path(CGRect(origin: .zero, size: size)))
            // Gradient tile size: auto/cover/contain = the box for an
            // intrinsic-less image; explicit px/% resolve per axis.
            let tile = BackgroundImageGeometry.gradientTileSize(box: size, size: sizeLayer)
            // §3.6 anchor: (box − tile) × fraction for keywords/percents,
            // plain inset for px — identical to the raster path.
            let anchor = BackgroundImageGeometry.origin(tile: tile, box: size,
                                                        x: positionX, y: positionY)
            // §3.7 per-axis modes → the real space/round arithmetic.
            let modes = BackgroundImageGeometry.repeatModes(repeatLayer)
            let plan = BackgroundImageGeometry.Placement(
                tileSize: tile, origin: anchor, repeatX: modes.x, repeatY: modes.y)
            // Runaway-lattice guard (wave-1 skeptic finding): "tiles are ≥ a
            // few px by construction" was wishful — percent/px sizes can be
            // arbitrarily small (1px tiles on a 200px box = 40k rects), and
            // tileRects hard-TRUNCATES at its 4096 cap, which would paint
            // only the top rows and leave the rest of the element blank.
            // Past the cap we degrade to ONE full-box fill (tile = box):
            // wrong tiling, but complete coverage and no truncation artifact
            // — mirroring the Compose cap fallback in ColorApplier.
            if BackgroundImageGeometry.tileCount(placement: plan, box: size) > 4096 {
                fill(CGRect(origin: .zero, size: size), in: context)
            } else {
                for rect in BackgroundImageGeometry.tileRects(placement: plan, box: size) {
                    fill(rect, in: context)
                }
            }
        }
    }

    /// Shade one tile rect with the layer's gradient. Endpoint math is
    /// per-flavour and mirrors GradientApplier's full-box view path so
    /// a full-box tile renders the same picture the default path does.
    private func fill(_ rect: CGRect, in context: GraphicsContext) {
        switch layer {
        case .linear(let angle, let stops):
            fillLinear(rect, in: context, angleDeg: angle, stops: stops)
        case .radial(let shape, let stops, let cx, let cy):
            fillRadial(rect, in: context, shape: shape, stops: stops, cx: cx, cy: cy)
        case .conic(let from, let stops, let cx, let cy):
            fillConic(rect, in: context, fromDeg: from, stops: stops, cx: cx, cy: cy)
        case .repeating(let kind, let angle, let stops):
            // Period-less repeating layers equal their plain base flavour
            // (GradientApplier header note 3) — same dispatch here.
            switch kind {
            case .linear: fillLinear(rect, in: context, angleDeg: angle, stops: stops)
            case .radial: fillRadial(rect, in: context, shape: "circle", stops: stops,
                                     cx: 0.5, cy: 0.5)
            case .conic:  fillConic(rect, in: context, fromDeg: angle, stops: stops,
                                    cx: 0.5, cy: 0.5)
            }
        case .none, .url:
            // Unreachable: BackgroundImageApplier routes url() to the
            // raster path and never geometry-routes `none`. Leave a
            // breadcrumb rather than fall through silently.
            PropertyTracker.logOnce(
                key: "bg-gradient-tile-kind",
                message: "BackgroundGradientTileView received a non-gradient layer — painting nothing (applier routing bug)")
        }
    }

    /// css-images-3 §3.1.1 endpoints via GradientApplier.linearEndpoints
    /// (angle nil = the CSS default 180deg "to bottom"), evaluated on the
    /// TILE size and translated to this rect's pixel space.
    private func fillLinear(_ rect: CGRect, in context: GraphicsContext,
                            angleDeg: Double?, stops: [BackgroundImageStop]) {
        let (s, e) = GradientApplier.linearEndpoints(angleDeg: angleDeg ?? 180,
                                                     size: rect.size)
        // UnitPoint fractions (may exceed 0…1 — fine) → absolute pixels.
        context.fill(Path(rect), with: .linearGradient(
            GradientApplier.toGradient(stops),
            startPoint: CGPoint(x: rect.minX + s.x * rect.width,
                                y: rect.minY + s.y * rect.height),
            endPoint: CGPoint(x: rect.minX + e.x * rect.width,
                              y: rect.minY + e.y * rect.height)))
    }

    /// Radial fill. `circle` uses the half-diagonal (≈ the CSS
    /// `farthest-corner` default, css-images-3 §3.5) exactly like
    /// GradientApplier.radial; the default `ellipse` mirrors that view
    /// path's render-circular-then-squash trick with Canvas transforms.
    private func fillRadial(_ rect: CGRect, in context: GraphicsContext,
                            shape: String?, stops: [BackgroundImageStop],
                            cx: Double, cy: Double) {
        let g = GradientApplier.toGradient(stops)
        let w = rect.width, h = rect.height
        if shape == "circle" {
            // `at <pos>` centre as 0…1 fractions of the tile → pixels.
            context.fill(Path(rect), with: .radialGradient(
                g,
                center: CGPoint(x: rect.minX + cx * w, y: rect.minY + cy * h),
                startRadius: 0,
                endRadius: sqrt(w * w + h * h) / 2))
        } else {
            // Ellipse: shade a max(w,h)-square circular gradient, then
            // scale the axes down to the tile aspect — the GraphicsContext
            // equivalent of the view path's `.scaleEffect(x:y:)`. Copying
            // the context keeps the transform/clip local to this tile
            // (GraphicsContext is a value type; state changes to a copy
            // don't affect the original, draws hit the same canvas).
            var c = context
            // Keep the oversized square inside THIS tile's rect.
            c.clip(to: Path(rect))
            c.translateBy(x: rect.midX, y: rect.midY)
            let m = max(w, h)
            c.scaleBy(x: w / m, y: h / m)
            // Centre in the pre-scale square: UnitPoint(cx, cy) of an
            // m×m frame measured from its midpoint.
            c.fill(Path(CGRect(x: -m / 2, y: -m / 2, width: m, height: m)),
                   with: .radialGradient(
                        g,
                        center: CGPoint(x: (cx - 0.5) * m, y: (cy - 0.5) * m),
                        startRadius: 0,
                        endRadius: m / 2))
        }
    }

    /// Conic fill. The −90° offset maps CSS's 0deg-at-12-o'clock
    /// convention (css-images-4 §2.2) onto the shading's 3-o'clock zero —
    /// the same constant GradientApplier.conic applies to
    /// AngularGradient (wave-1 fix).
    private func fillConic(_ rect: CGRect, in context: GraphicsContext,
                           fromDeg: Double?, stops: [BackgroundImageStop],
                           cx: Double, cy: Double) {
        context.fill(Path(rect), with: .conicGradient(
            GradientApplier.toGradient(stops),
            center: CGPoint(x: rect.minX + cx * rect.width,
                            y: rect.minY + cy * rect.height),
            angle: .degrees((fromDeg ?? 0) - 90)))
    }
}

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
//  translated per rect (css-backgrounds-3 §2.6/§2.4: the image is sized
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
            // Shader pitch: the FRACTIONAL per-axis tile size (round may
            // rescale to e.g. 200/7). tileRects returns pixel-SNAPPED
            // rects (±1px per tile, integer shared edges — no AA seam),
            // so gradient geometry must NOT be built from rect.size: it
            // stays pinned to this fractional pitch (css-images-4 §3.4.1)
            // and only the fill region is snapped.
            let shaderSize = BackgroundImageGeometry.shaderTileSize(placement: plan, box: size)
            if BackgroundImageGeometry.tileCount(placement: plan, box: size) > 4096 {
                // Full-box fallback: the box IS the tile, shader included.
                fill(CGRect(origin: .zero, size: size), shaderSize: size, in: context)
            } else {
                for rect in BackgroundImageGeometry.tileRects(placement: plan, box: size) {
                    fill(rect, shaderSize: shaderSize, in: context)
                }
            }
        }
    }

    /// Shade one tile rect with the layer's gradient. Endpoint math is
    /// per-flavour and mirrors GradientApplier's full-box view path so
    /// a full-box tile renders the same picture the default path does.
    /// `shaderSize` is the FRACTIONAL tile pitch the gradient geometry
    /// resolves against (css-images-4 §3.4.1) — `rect` is the snapped
    /// fill region and may differ from it by up to 1px per axis.
    private func fill(_ rect: CGRect, shaderSize: CGSize, in context: GraphicsContext) {
        switch layer {
        case .linear(let angle, let stops):
            fillLinear(rect, shaderSize: shaderSize, in: context, angleDeg: angle, stops: stops)
        case .radial(let shape, let stops, let cx, let cy):
            // Coords resolve against the FRACTIONAL shader tile — the
            // gradient box per css-images-4 §3.4.1 (px centers divide by
            // the tile axis; fractions pass through unchanged).
            fillRadial(rect, shaderSize: shaderSize, in: context,
                       shape: shape, stops: stops,
                       cx: cx.fraction(Double(shaderSize.width)),
                       cy: cy.fraction(Double(shaderSize.height)))
        case .conic(let from, let stops, let cx, let cy):
            // Same per-axis resolution as radial above.
            fillConic(rect, shaderSize: shaderSize, in: context,
                      fromDeg: from, stops: stops,
                      cx: cx.fraction(Double(shaderSize.width)),
                      cy: cy.fraction(Double(shaderSize.height)))
        case .repeating(let kind, let angle, let stops):
            // Same dispatch as GradientApplier.render with the repeating
            // flag set — the resolver tiles the stop span across the
            // per-tile gradient line (wave 46, header note 5 there).
            switch kind {
            case .linear: fillLinear(rect, shaderSize: shaderSize, in: context,
                                     angleDeg: angle, stops: stops, repeating: true)
            case .radial: fillRadial(rect, shaderSize: shaderSize, in: context,
                                     shape: "circle", stops: stops, cx: 0.5, cy: 0.5,
                                     repeating: true)
            case .conic:  fillConic(rect, shaderSize: shaderSize, in: context,
                                    fromDeg: angle, stops: stops, cx: 0.5, cy: 0.5,
                                    repeating: true)
            }
        case .color(let cv):
            // <color>-as-image: a solid fill has no internal geometry —
            // just fill the snapped tile rect with the resolved color.
            if case .srgb(let r, let g, let b, let a) = cv {
                context.fill(Path(rect), with: .color(
                    Color(red: r, green: g, blue: b, opacity: a)))
            }
        case .none, .url, .crossFade, .imageNotation:
            // Unreachable: BackgroundImageApplier routes url() to the
            // raster path, never geometry-routes `none`, renders
            // cross-fade() through GradientApplier's compositor (the
            // multi-image additive stack has no single-tile fill), and
            // resolves image() to its §2.1 winner BEFORE any geometry
            // routing (wave-49 lane A3), so a raw `.imageNotation` can
            // never reach a tile fill either. Leave a breadcrumb rather
            // than fall through silently.
            PropertyTracker.logOnce(
                key: "bg-gradient-tile-kind",
                message: "BackgroundImageApplier received a non-gradient layer — painting nothing (applier routing bug)")
        }
    }

    /// css-images-3 §3.1.1 endpoints via GradientApplier.linearEndpoints
    /// (angle nil = the CSS default 180deg "to bottom"), evaluated on the
    /// FRACTIONAL shader tile (never the snapped rect — §3.4.1 pins the
    /// gradient box to the tile) and translated to this rect's origin.
    private func fillLinear(_ rect: CGRect, shaderSize: CGSize, in context: GraphicsContext,
                            angleDeg: Double?, stops: [BackgroundImageStop],
                            repeating: Bool = false) {
        let (s, e) = GradientApplier.linearEndpoints(angleDeg: angleDeg ?? 180,
                                                     size: shaderSize)
        // <length> stops / the repeat period resolve against THIS tile's
        // gradient-line length (css-images-4 §3.4.1: the gradient box is
        // the tile) — same quantity the view path feeds toGradient.
        let gradient = GradientApplier.toGradient(
            stops,
            lengthPx: GradientApplier.lineLengthPx(angleDeg: angleDeg ?? 180, size: shaderSize),
            repeating: repeating)
        // UnitPoint fractions (may exceed 0…1 — fine) → absolute pixels,
        // scaled by the shader tile and anchored at the SNAPPED rect
        // origin: the ≤0.5px phase shift is invisible, the AA seam the
        // snapping closes was not. A snapped-wide rect clamps its final
        // sub-pixel column to the gradient's edge colour.
        context.fill(Path(rect), with: .linearGradient(
            gradient,
            startPoint: CGPoint(x: rect.minX + s.x * shaderSize.width,
                                y: rect.minY + s.y * shaderSize.height),
            endPoint: CGPoint(x: rect.minX + e.x * shaderSize.width,
                              y: rect.minY + e.y * shaderSize.height)))
    }

    /// Radial fill. `circle` uses the half-diagonal (≈ the CSS
    /// `farthest-corner` default, css-images-3 §3.2) exactly like
    /// GradientApplier.radial; the default `ellipse` mirrors that view
    /// path's render-circular-then-squash trick with Canvas transforms.
    private func fillRadial(_ rect: CGRect, shaderSize: CGSize, in context: GraphicsContext,
                            shape: String?, stops: [BackgroundImageStop],
                            cx: Double, cy: Double, repeating: Bool = false) {
        // Geometry (centre / radii) resolves against the FRACTIONAL
        // shader tile, not the snapped rect (§3.4.1 — see fillLinear).
        let w = shaderSize.width, h = shaderSize.height
        // <length> stops measure along the ray: the circle's end radius,
        // or the ellipse's HORIZONTAL radius √2·w/2 (css-images-3
        // §3.2.3) — mirrors GradientApplier.radial's lengths exactly.
        let rayPx: Double = shape == "circle"
            ? Double(sqrt(w * w + h * h) / 2)
            : Double(w) / 2 * 2.0.squareRoot()
        let g = GradientApplier.toGradient(stops, lengthPx: rayPx, repeating: repeating)
        if shape == "circle" {
            // `at <pos>` centre as 0…1 fractions of the tile → pixels,
            // anchored at the snapped rect's origin.
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
            // Keep the oversized square inside THIS tile's SNAPPED rect
            // (the fill region — the shader may overhang it sub-pixel).
            c.clip(to: Path(rect))
            // Centre of the fractional shader tile anchored at the
            // snapped origin (NOT rect.midX — the rect is up to 1px
            // wider/narrower than the tile the geometry is built on).
            c.translateBy(x: rect.minX + w / 2, y: rect.minY + h / 2)
            let m = max(w, h)
            c.scaleBy(x: w / m, y: h / m)
            // Centre in the pre-scale square: UnitPoint(cx, cy) of an
            // m×m frame measured from its midpoint. endRadius carries
            // the √2 farthest-CORNER factor (css-images-3 §3.2 — see
            // GradientApplier.ellipseEndRadius): after the (w/m, h/m)
            // squash the ellipse radii land on (√2·w/2, √2·h/2), exactly
            // the view path / Compose / Chromium geometry; the old m/2
            // stopped at the farthest SIDE and ended the fade 29% early.
            c.fill(Path(CGRect(x: -m / 2, y: -m / 2, width: m, height: m)),
                   with: .radialGradient(
                        g,
                        center: CGPoint(x: (cx - 0.5) * m, y: (cy - 0.5) * m),
                        startRadius: 0,
                        endRadius: GradientApplier.ellipseEndRadius(shaderSize)))
        }
    }

    /// Conic fill. The −90° offset maps CSS's 0deg-at-12-o'clock
    /// convention (css-images-4 §2.2) onto the shading's 3-o'clock zero —
    /// the same constant GradientApplier.conic applies to
    /// AngularGradient (wave-1 fix).
    private func fillConic(_ rect: CGRect, shaderSize: CGSize, in context: GraphicsContext,
                           fromDeg: Double?, stops: [BackgroundImageStop],
                           cx: Double, cy: Double, repeating: Bool = false) {
        // Centre resolves against the FRACTIONAL shader tile anchored at
        // the snapped rect origin (§3.4.1 — see fillLinear); the fill
        // region is the snapped rect. Conic stops are angles — no px.
        context.fill(Path(rect), with: .conicGradient(
            GradientApplier.toGradient(stops, repeating: repeating),
            center: CGPoint(x: rect.minX + cx * shaderSize.width,
                            y: rect.minY + cy * shaderSize.height),
            angle: .degrees((fromDeg ?? 0) - 90)))
    }
}

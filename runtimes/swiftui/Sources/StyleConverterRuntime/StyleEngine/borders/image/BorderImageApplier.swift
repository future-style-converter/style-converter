//
//  BorderImageApplier.swift
//  StyleEngine/borders/image — BI-IOS lane (9-slice painter).
//
//  Real CSS border-image (css-backgrounds-3 §6) for `url(...)` sources:
//  the source decodes through BackgroundURLImageResolver (percent-
//  encoded data URIs, file/bundle paths; remote = defined no-op), the
//  image is cut along the §6.1 slice lines into nine regions, and a
//  Canvas overlay paints corners fixed, edges per the §6.2 repeat
//  keyword (stretch / repeat / round / space via BorderImageMath.
//  edgePlan → BackgroundTileMath), and the center only when the slice
//  `fill` flag is set. Outsets (§6.4) expand the PAINT rect outward
//  with zero layout effect — Canvas draws are not clipped to the frame
//  (same property BackgroundURLImageView relies on), so the overlay
//  simply draws at negative origins.
//
//  Remaining honest gaps (each logged or spec-cited inline, never
//  silent): gradient sources are still identity + logOnce; edge tiles
//  use the source's pixel length as the tile pitch (mirroring Compose)
//  rather than §6.2's corner-scale-derived pitch; the overlay paints
//  ABOVE sibling content that intrudes into the border band (Compose
//  draws behind content — SwiftUI has no "behind content, above
//  background" slot in a modifier chain; content is normally inset off
//  the band by engineBorderContentInset so this is invisible in the
//  fixtures).
//

// SwiftUI for ViewModifier/Canvas; UIKit for the decoded UIImage.
import SwiftUI
import UIKit

struct BorderImageApplier: ViewModifier {
    /// Nil → no border-image-* property in the IR → identity.
    let config: BorderImageConfig?

    func body(content: Content) -> some View {
        // Identity for absent / `source: none` (the initial value).
        guard let cfg = config, cfg.hasBorderImage else { return AnyView(content) }
        // §6.3 zero-width early-out (probe basis — see canPaint): the
        // initial `1` multiplier on a border-less element resolves every
        // side to 0 → the border image paints NOTHING and must not
        // occupy any band (Compose pins the same outcome; Borders_C06
        // painted a phantom 8dp frame before this rule, SSIM 0.5861).
        guard BorderImageMath.canPaint(cfg) else { return AnyView(content) }
        switch cfg.source {
        case .none:
            // Unreachable behind hasBorderImage; the exhaustive switch
            // keeps the compiler honest.
            return AnyView(content)
        case .gradient(let expr):
            // Gradient sources still TODO (need the gradient string
            // re-parsed into a shader tile like Compose's
            // renderGradientToBitmap). Logged, identity — never a guess.
            PropertyTracker.logOnce(
                key: "border-image-gradient:\(expr.prefix(48))",
                message: "border-image gradient source not painted on iOS yet (BI-IOS covers url() only) — identity")
            return AnyView(content)
        case .url(let url):
            // Decode via the shared resolver — the resolver itself logs
            // remote/undecodable payloads once; we add the border-image
            // specific breadcrumb so the miss is attributable.
            guard let ui = BackgroundURLImageResolver.image(for: url) else {
                PropertyTracker.logOnce(
                    key: "border-image-decode:\(url.prefix(48))",
                    message: "border-image-source url('\(url.prefix(64))') failed to decode — identity (no 9-slice painted)")
                return AnyView(content)
            }
            // §6.4 outsets are box-independent (length literal / number
            // × computed border), so they resolve at modifier time.
            let outsets = BorderImageMath.resolvedOutsets(cfg)
            // Overlay: paints ABOVE the background chain (backgrounds
            // attach earlier via .background, so they sit behind) — the
            // §6 stacking, "drawn in place of the border". Hit testing
            // off: a border decoration must never eat touches.
            return AnyView(content.overlay(
                BorderImageNineSliceView(uiImage: ui, config: cfg, outsets: outsets)
                    .allowsHitTesting(false)
            ))
        }
    }
}

/// The paint view: one Canvas sized to the border box by the overlay,
/// drawing the nine regions into the outset-expanded border image area.
private struct BorderImageNineSliceView: View {
    /// Decoded source raster.
    let uiImage: UIImage
    /// Full border-image config (slices / widths / repeat / fill).
    let config: BorderImageConfig
    /// Pre-resolved §6.4 outsets (box-independent).
    let outsets: BorderImageMath.Sides

    var body: some View {
        Canvas { ctx, size in
            // Border image AREA = border box expanded by the outsets
            // (§6.4). Canvas draws are NOT clipped to the frame (see
            // BackgroundURLImageView), so the negative origin paints
            // outside the border box with zero layout effect.
            let area = CGRect(x: -outsets.left, y: -outsets.top,
                              width: size.width + outsets.left + outsets.right,
                              height: size.height + outsets.top + outsets.bottom)
            // §6.3 widths resolved against the AREA — the true
            // percentage basis, only knowable here at draw time.
            let w = BorderImageMath.resolvedWidths(config, box: area.size)
            // Intrinsic pixel size: UIImage.size is points — multiply by
            // scale so slice px address real pixels (same rule as the
            // background url() painter).
            let px = CGSize(width: uiImage.size.width * uiImage.scale,
                            height: uiImage.size.height * uiImage.scale)
            // §6.1 slice lines cut the SOURCE image (top/bottom offsets
            // are relative to the image height, left/right to its width).
            let src = BorderImageMath.nineGrid(
                bounds: CGRect(origin: .zero, size: px),
                top: BorderImageMath.slicePx(config.sliceTop, extent: px.height),
                right: BorderImageMath.slicePx(config.sliceRight, extent: px.width),
                bottom: BorderImageMath.slicePx(config.sliceBottom, extent: px.height),
                left: BorderImageMath.slicePx(config.sliceLeft, extent: px.width))
            // §6.2: the same nine-way cut on the destination area, with
            // the resolved widths as the band sizes.
            let dst = BorderImageMath.nineGrid(bounds: area, top: w.top, right: w.right,
                                               bottom: w.bottom, left: w.left)
            // Corners scale to their dest rects (§6.2 — corners are
            // never tiled; Compose's drawImageRect does the same).
            drawStretched(ctx, from: src.topLeft, to: dst.topLeft)
            drawStretched(ctx, from: src.topRight, to: dst.topRight)
            drawStretched(ctx, from: src.bottomLeft, to: dst.bottomLeft)
            drawStretched(ctx, from: src.bottomRight, to: dst.bottomRight)
            // Edges tile along their axis per the repeat keyword —
            // horizontal keyword rules the top/bottom bands, vertical
            // the left/right bands (§6.2).
            drawEdge(ctx, from: src.topCenter, to: dst.topCenter,
                     mode: config.repeatHorizontal, horizontal: true)
            drawEdge(ctx, from: src.bottomCenter, to: dst.bottomCenter,
                     mode: config.repeatHorizontal, horizontal: true)
            drawEdge(ctx, from: src.middleLeft, to: dst.middleLeft,
                     mode: config.repeatVertical, horizontal: false)
            drawEdge(ctx, from: src.middleRight, to: dst.middleRight,
                     mode: config.repeatVertical, horizontal: false)
            // Center paints ONLY with the slice `fill` flag (§6.1) —
            // stretched like Compose (spec'd center tiling is a follow-up
            // shared with the edge-pitch gap in the header).
            if config.sliceFill {
                drawStretched(ctx, from: src.center, to: dst.center)
            }
        }
    }

    /// Crop one source region out of the bitmap as a drawable Image.
    /// Nil for degenerate regions (a zero slice or an all-band image
    /// leaves empty middles — skipping matches Compose's empty rects
    /// drawing nothing). scale 1: the rects are raw pixels and every
    /// draw sizes the destination explicitly.
    private func cropped(_ rect: CGRect) -> Image? {
        guard rect.width >= 1, rect.height >= 1,
              let cg = uiImage.cgImage?.cropping(to: rect) else { return nil }
        return Image(decorative: cg, scale: 1)
    }

    /// Single scaled draw: source region stretched to fill `dstRect`
    /// (corners + the fill center + the stretch repeat fallbacks).
    private func drawStretched(_ ctx: GraphicsContext, from src: CGRect, to dstRect: CGRect) {
        // Empty destination band (zero width/outset side) → no paint.
        guard dstRect.width > 0, dstRect.height > 0, let img = cropped(src) else { return }
        ctx.draw(img, in: dstRect)
    }

    /// Tile one edge band along its axis per the §6.2 repeat keyword.
    /// The cross axis always stretches to the band's thickness (§6.2 —
    /// an edge tile fills the band depth-wise on every keyword).
    private func drawEdge(_ ctx: GraphicsContext, from src: CGRect, to dstRect: CGRect,
                          mode: BorderImageRepeat, horizontal: Bool) {
        // Empty band or empty slice → nothing to tile.
        guard dstRect.width > 0, dstRect.height > 0, let img = cropped(src) else { return }
        // Along-axis plan: dest = band length, src = slice pixel length
        // (the Compose tile-pitch basis — see the header's honest gaps).
        let plan = BorderImageMath.edgePlan(
            dest: horizontal ? dstRect.width : dstRect.height,
            src: horizontal ? src.width : src.height,
            mode: mode)
        // Clip to the band so REPEAT's overflowing last tile CLIPS
        // (§6.2 / Chromium) instead of squashing like Compose. The clip
        // costs nothing for the exact-fit modes.
        var band = ctx
        band.clip(to: Path(dstRect))
        // Draw each planned segment [origin, end) mapped into the band.
        for (o, e) in zip(plan.origins, plan.ends) {
            // Along-axis offset is band-relative; cross axis spans the
            // band thickness fully.
            let r = horizontal
                ? CGRect(x: dstRect.minX + o, y: dstRect.minY,
                         width: e - o, height: dstRect.height)
                : CGRect(x: dstRect.minX, y: dstRect.minY + o,
                         width: dstRect.width, height: e - o)
            band.draw(img, in: r)
        }
    }
}

// View chain helper — attached in StyleBuilder.applyStyle after the
// background chain and before radius/sides (the Phase 5 border order).
extension View {
    func engineBorderImage(_ config: BorderImageConfig?) -> some View {
        modifier(BorderImageApplier(config: config))
    }
}

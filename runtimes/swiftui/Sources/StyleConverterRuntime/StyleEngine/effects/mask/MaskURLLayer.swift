//
//  MaskURLLayer.swift
//  StyleEngine/effects/mask — Lane UM-IOS (real url() masks).
//
//  Resolves one `url()` mask-image layer to a raster and paints it with
//  the background url() pipeline (BackgroundURLImageResolver decode +
//  BackgroundURLImageView tiling), the same sharing pattern the mask
//  gradient branches use with GradientApplier. The replaced placeholder
//  painted Color.black.opacity(0.5) — a uniform HALF-mask that showed
//  the element at 50% everywhere, which css-masking-1 never produces:
//  a mask layer is the image's own alpha profile on success and
//  TRANSPARENT BLACK on failure ("an invalid image or reference is
//  treated as a transparent black image layer"), i.e. the element hides
//  completely. Failure paths (SVG url(#fragment) references, http(s)
//  remotes, undecodable payloads) therefore render Color.clear + a
//  PropertyTracker breadcrumb, never a half-visible guess.
//

import SwiftUI
import UIKit

/// Outcome of resolving one url() mask layer. A TWO-state enum on
/// purpose: css-masking-1 has no half-visible middle ground between
/// "use the image's alpha" and "transparent black layer", so the type
/// system forbids reintroducing a placeholder opacity.
enum MaskURLResolution: Equatable {
    /// Decoded raster — its alpha channel becomes the mask.
    case raster(UIImage)
    /// Failed load / unresolvable reference — transparent black
    /// (element hidden), the spec's failed-image semantics.
    case transparent
}

/// Namespace for the url() mask layer: resolution + the MaskConfig →
/// Background* geometry adapters. Static + pure so XCTest pins the
/// failure semantics and geometry defaults without rendering SwiftUI.
enum MaskURLLayer {

    /// Resolve `href` via the shared background decoder (data URIs —
    /// fully percent-encoded survive the converter's lowercasing —
    /// file paths, bundle resources). nil from the resolver covers all
    /// unresolvable flavors: url(#fragment) SVG references (no document
    /// to look the id up in), http(s) remotes (defined no-op for
    /// capture determinism), and broken payloads — each already logged
    /// by the resolver; we add the mask-specific consequence.
    static func resolve(_ href: String, mode: MaskMode) -> MaskURLResolution {
        // Shared decode + cache with background-image url() layers —
        // one owner for scheme handling, RFC 2397 parsing and the
        // percent-decode byte walk.
        guard let img = BackgroundURLImageResolver.image(for: href) else {
            PropertyTracker.logOnce(
                key: "mask-url-failed:\(href.prefix(48))",
                message: "mask-image url('\(href.prefix(64))') failed to resolve — rendering transparent (css-masking-1: a failed <mask-source> is a transparent black layer, so the element hides; the old 50% placeholder is gone). SVG url(#fragment) references and http(s) remotes are unsupported — use file or percent-encoded data URIs")
            return .transparent
        }
        // mask-mode: for a RASTER source the initial `match-source`
        // resolves to `alpha` (css-masking-1 §7.4.3) — SwiftUI's
        // `.mask` reads exactly the view's alpha channel, so drawing
        // the image directly is the correct default with no conversion.
        // TODO(SVG): `match-source` resolves to LUMINANCE for <mask>
        // element references; if url(#fragment) ever resolves (needs an
        // SVG document model), that path must luminance-fold like the
        // gradient branches do. Explicit `mask-mode: luminance` on a
        // raster needs a luminanceToAlpha pre-pass (CIFilter) — logged,
        // not silently mis-rendered as alpha.
        if mode == .luminance {
            PropertyTracker.logOnce(
                key: "mask-url-luminance",
                message: "mask-mode: luminance on a raster mask is TODO — using the alpha channel (needs a luminanceToAlpha pre-pass)")
        }
        return .raster(img)
    }

    // MARK: - MaskConfig → Background* geometry adapters

    /// mask-size → background-size layer. css-masking-1 defines
    /// mask-size by reference to background-size (css-backgrounds-3
    /// §3.9), so the mapping is 1:1 onto the shared geometry enum.
    static func sizeLayer(_ s: MaskSize) -> BackgroundSizeLayer {
        switch s {
        case .auto:    return .auto     // intrinsic pixels (§3.9 `auto auto`)
        case .cover:   return .cover    // smallest box-covering scale
        case .contain: return .contain  // largest box-fitting scale
        case .explicit(let w, let h, let wp, let hp):
            // Per-axis: points/percent captured separately by the
            // extractor collapse into the geometry's dimension enum.
            return .explicit(w: dim(points: w, fraction: wp),
                             h: dim(points: h, fraction: hp))
        }
    }

    /// One explicit axis. MaskExtractor stores percents as UNIT
    /// fractions (v/100); the geometry enum wants CSS percent, so
    /// scale back up. Neither present = the §3.9 `auto` axis
    /// (ratio-preserving against the resolved one).
    private static func dim(points: CGFloat?, fraction: CGFloat?) -> BackgroundSizeDim {
        if let px = points { return .px(Double(px)) }
        if let f = fraction { return .percent(Double(f) * 100) }
        return .auto
    }

    /// mask-repeat → background-repeat layer. The INITIAL mask-repeat
    /// is `repeat` (css-masking-1, same as background-repeat) — the
    /// config default `.repeatBoth` maps to repeat/repeat so an
    /// undeclared repeat TILES the mask across the whole box, matching
    /// the browser reference. Keywords are the lower-case strings
    /// BackgroundTileMath.mode consumes (§3.7 per-axis grammar).
    static func repeatLayer(_ r: MaskRepeat) -> BackgroundRepeatLayer {
        switch r {
        case .repeatBoth: return BackgroundRepeatLayer(x: "repeat",    y: "repeat")
        case .noRepeat:   return BackgroundRepeatLayer(x: "no-repeat", y: "no-repeat")
        // Single-axis shorthands: `repeat-x` = repeat no-repeat,
        // `repeat-y` = no-repeat repeat (css-backgrounds-3 §3.7).
        case .repeatX:    return BackgroundRepeatLayer(x: "repeat",    y: "no-repeat")
        case .repeatY:    return BackgroundRepeatLayer(x: "no-repeat", y: "repeat")
        // space/round run the real §3.7 gap/rescale arithmetic in
        // BackgroundTileMath — no approximation.
        case .round:      return BackgroundRepeatLayer(x: "round",     y: "round")
        case .space:      return BackgroundRepeatLayer(x: "space",     y: "space")
        }
    }

    /// mask-position → per-axis background-position anchors.
    /// Undeclared → (nil, nil): BackgroundImageGeometry treats a nil
    /// axis as the CSS initial 0% (top/left flush) — css-masking-1's
    /// mask-position initial is `0% 0%`, NOT the config's 0.5 default
    /// (that 0.5 exists for the gradient branches, whose css-images
    /// default center IS 50%). Declared fractions become percent
    /// anchors, resolved by the shared §3.6 free-space identity
    /// `(box − tile) × fraction`.
    static func positionAxes(_ cfg: MaskConfig) -> (x: BackgroundAxisPosition?,
                                                    y: BackgroundAxisPosition?) {
        guard cfg.positionDeclared else { return (nil, nil) }
        return (.percent(Double(cfg.position.x) * 100),
                .percent(Double(cfg.position.y) * 100))
    }

    // MARK: - The layer view

    /// The mask content for one url() layer. Success paints the raster
    /// through BackgroundURLImageView — the same Canvas tiler the
    /// background path uses, honoring mask-size / mask-position /
    /// mask-repeat via the pure BackgroundImageGeometry math (the
    /// Canvas fills the masked view's bounds, exactly the CSS painting
    /// area). Failure paints Color.clear: transparent black mask =
    /// element hidden (spec failed-load), replacing the old 50% grey.
    @ViewBuilder
    static func view(_ href: String, cfg: MaskConfig) -> some View {
        switch resolve(href, mode: cfg.mode) {
        case .raster(let img):
            // Adapt the mask geometry fields onto the background
            // painter's per-layer inputs (shared §3.6/§3.7/§3.9 math).
            let axes = positionAxes(cfg)
            BackgroundURLImageView(uiImage: img,
                                   sizeLayer: sizeLayer(cfg.size),
                                   positionX: axes.x,
                                   positionY: axes.y,
                                   repeatLayer: repeatLayer(cfg.repeatMode))
        case .transparent:
            // Fully transparent mask — `.mask` erases the element, the
            // honest spec behavior for a failed mask source.
            Color.clear
        }
    }
}

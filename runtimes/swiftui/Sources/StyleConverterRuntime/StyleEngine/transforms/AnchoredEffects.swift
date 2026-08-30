//
//  AnchoredEffects.swift
//  StyleEngine/transforms — wave 49, lane A6.
//
//  `transform-origin` expressed as a LENGTH, for the scale and 2D-rotate
//  functions.
//
//  THE DEFECT. css-transforms-1 §2 renders every transform function as
//  T(origin) · F · T(−origin), and §4 lets each axis of `transform-origin`
//  be a keyword, a percentage, OR a length. The extractor already models
//  that split faithfully — `TransformsExtractor.parseOriginAxis` writes a
//  fraction for keyword/percentage axes and an EXPLICIT PIXEL value into
//  `TransformOriginValue.xPx / .yPx` for length axes, leaving the fraction
//  at its 0.5 default because a length cannot be turned into a fraction
//  without the element's size. Two consumers honoured that pixel channel
//  (SkewEffect and Rotate3DEffect, both GeometryEffects that receive the
//  laid-out size); `.scaleEffect(x:y:anchor:)` and `.rotation3DEffect(
//  anchor:)` take only a fractional `UnitPoint`, so for them the pixel
//  origin was silently dropped and the transform ran about the CENTRE.
//
//  MEASURED, wave-48 corpus, `css-transforms/css-transform-scale-002`
//  (`transform-origin: 0 0; transform: scale(2)` on a 100x100 green box
//  at the origin of its containing block, over a red 98x98 marker the
//  green must completely cover):
//
//      frozen ref  green [16,88 .. 215,287]  200x200   — top-left anchored
//      web         green [16,88 .. 215,287]  200x200   ssim 1.0000  PASS
//      Android     green [16,88 .. 215,287]  200x200   ssim 0.9977  PASS
//      iOS         green [-34,38 .. 165,237] 200x200   ssim 0.8863  FAIL
//                  + 98x98 of RED still visible at [116,188 .. 213,285]
//
//  The iOS box is the same 200x200, displaced by exactly (−50, −50) — the
//  signature of scaling about the centre (66,138) instead of the declared
//  top-left corner (16,88). Compose gets this right because
//  `TransformApplier.applyTransformFunctions` resolves `originXDp` against
//  `size` inside the graphicsLayer block; this file is the iOS twin of
//  that resolution.
//

import SwiftUI

// MARK: - Closed forms

/// Conjugation of a linear 2x2 about the transform-origin, in the closed
/// form the sibling `TransformsMath.anchoredSkew` uses.
///
/// For any linear map L and anchor c, css-transforms-1 §2's
/// T(c) · L · T(−c) sends p to L·(p − c) + c, i.e. it keeps L's 2x2 block
/// and adds the constant translation c − L·c. Writing that out avoids
/// three matrix concatenations and, more importantly, avoids depending on
/// CGAffineTransform's row-vector operand order.
enum AnchoredTransformMath {

    /// CSS `scale(sx, sy)` about `transform-origin`.
    ///
    /// L = diag(sx, sy), so c − L·c = (cx·(1 − sx), cy·(1 − sy)).
    /// Sanity check against the carrier above: sx = 2, cx = 0 gives
    /// tx = 0 — the top-left corner is a fixed point, which is exactly
    /// what "scale(2) about 0 0 covers [0,0 .. 200,200]" means.
    static func anchoredScale(sx: CGFloat, sy: CGFloat,
                              size: CGSize, anchor: UnitPoint,
                              anchorXPx: CGFloat? = nil,
                              anchorYPx: CGFloat? = nil) -> CGAffineTransform {
        // Anchor in local px: an explicit LENGTH on an axis wins over the
        // fractional UnitPoint for THAT axis only (css-transforms-1 §4 resolves the two
        // axes independently, so `0 50%` is legal and mixed).
        let cx = anchorXPx ?? size.width * anchor.x
        let cy = anchorYPx ?? size.height * anchor.y
        return CGAffineTransform(a: sx, b: 0, c: 0, d: sy,
                                 tx: cx * (1 - sx), ty: cy * (1 - sy))
    }

    /// CSS `rotate(θ)` about `transform-origin`, 2D (z-axis) only.
    ///
    /// In CSS's y-down space a positive angle turns clockwise on screen:
    /// L = [[cosθ, −sinθ], [sinθ, cosθ]], which in CGAffineTransform's
    /// (a, b, c, d) field order — p ↦ (a·x + c·y + tx, b·x + d·y + ty) —
    /// is a = cos, b = sin, c = −sin, d = cos. The same sign convention
    /// `TransformListComposer.rotation` documents on the Compose side.
    static func anchoredRotation(deg: CGFloat,
                                 size: CGSize, anchor: UnitPoint,
                                 anchorXPx: CGFloat? = nil,
                                 anchorYPx: CGFloat? = nil) -> CGAffineTransform {
        let r = deg * .pi / 180                      // trig wants radians
        let cs = cos(r)
        let sn = sin(r)
        let cx = anchorXPx ?? size.width * anchor.x
        let cy = anchorYPx ?? size.height * anchor.y
        // c − L·c, componentwise.
        return CGAffineTransform(a: cs, b: sn, c: -sn, d: cs,
                                 tx: cx - (cx * cs - cy * sn),
                                 ty: cy - (cx * sn + cy * cs))
    }
}

// MARK: - Effects

/// `scale()` about a px `transform-origin`.
///
/// A GeometryEffect for the same reason SkewEffect and TranslateEffect
/// are: SwiftUI hands `effectValue` the element's FINAL laid-out size
/// without letting the effect participate in layout, which is exactly the
/// CSS reference box a length origin must resolve against. (A
/// GeometryReader wrapper would be greedy and resolve against the
/// parent's proposal — the wave-5 percentage-translate bug.)
struct AnchoredScaleEffect: GeometryEffect {
    var sx: CGFloat
    var sy: CGFloat
    var anchor: UnitPoint
    var anchorXPx: CGFloat?
    var anchorYPx: CGFloat?

    func effectValue(size: CGSize) -> ProjectionTransform {
        ProjectionTransform(AnchoredTransformMath.anchoredScale(
            sx: sx, sy: sy, size: size, anchor: anchor,
            anchorXPx: anchorXPx, anchorYPx: anchorYPx))
    }
}

/// 2D `rotate()` about a px `transform-origin`.
///
/// Used ONLY when a length origin is present. A rotation with a purely
/// fractional origin keeps `.rotation3DEffect(anchor:)`, which already
/// expresses it exactly — routing those through a ProjectionTransform
/// would re-rasterise every existing 2D-rotation baseline for no
/// behavioural gain.
struct AnchoredRotateEffect: GeometryEffect {
    var deg: CGFloat
    var anchor: UnitPoint
    var anchorXPx: CGFloat?
    var anchorYPx: CGFloat?

    func effectValue(size: CGSize) -> ProjectionTransform {
        ProjectionTransform(AnchoredTransformMath.anchoredRotation(
            deg: deg, size: size, anchor: anchor,
            anchorXPx: anchorXPx, anchorYPx: anchorYPx))
    }
}

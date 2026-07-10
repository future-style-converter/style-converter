//
//  MotionOffsetApplier.swift
//  StyleEngine/layout/advanced — fidelity wave 2.
//
//  Applies the CSS Motion Path transform (motion-1 §4) for the ray()
//  subset: the element's ANCHOR (offset-anchor: auto → transform-origin
//  → 50% 50%, i.e. the element centre) is translated to the point at
//  `offset-distance` along the ray starting at `offset-position`, and
//  the element rotates per `offset-rotate`. Layout position is not
//  affected (like transforms) — exactly what the web reference shows
//  for Layout_C14_OffsetPath (box mostly off-canvas at the top-left,
//  rotated 135°).
//
//  Geometry (pure, XCTest-pinned in MotionOffsetMath):
//    • ray bearing: 0deg points UP, clockwise positive (§3.2 — same
//      convention as CSS gradients).
//    • point at distance D: S + D·(sin θ, −cos θ).
//    • auto rotation: the element's inline (+x) axis aligns with the
//      path direction → CSS rotation = bearing − 90deg; reverse adds
//      180deg (§3.5).
//    • translation: anchor is the element centre; the element's flow
//      position puts its top-left at the containing-block origin in
//      the capture harness, so Δ = P − (w/2, h/2).
//

import SwiftUI

/// View-free math so the numbers are unit-testable.
enum MotionOffsetMath {

    /// Resolve one position axis against a containing-block extent.
    static func resolve(_ a: MotionAxis, extent: CGFloat) -> CGFloat {
        switch a {
        case .start:          return 0
        case .center:         return extent / 2
        case .end:            return extent
        case .px(let v):      return v
        case .percent(let p): return extent * p / 100
        }
    }

    /// Rotation in degrees for a ray path per offset-rotate (§3.5).
    static func rotation(rayDeg: CGFloat, rotate: MotionRotate) -> CGFloat {
        switch rotate {
        case .auto:            return rayDeg - 90   // inline axis along path
        case .reverse:         return rayDeg + 90   // auto + 180
        case .fixed(let deg):  return deg           // path direction ignored
        }
    }

    /// Translation that moves the element centre onto the ray point.
    /// `start` is the resolved offset-position; bearing 0 = up.
    static func translation(start: CGPoint,
                            rayDeg: CGFloat,
                            distance: CGFloat,
                            elementSize: CGSize) -> CGSize {
        let rad = rayDeg * .pi / 180
        // Point at `distance` along the ray (y grows DOWN on screen,
        // bearing 0 points up → negative y component).
        let px = start.x + distance * sin(rad)
        let py = start.y - distance * cos(rad)
        // Anchor = element centre (offset-anchor auto → 50% 50%).
        return CGSize(width: px - elementSize.width / 2,
                      height: py - elementSize.height / 2)
    }
}

/// Modifier attached by StyleBuilder.applyStyle. Identity when no
/// renderable offset-path arrived.
struct MotionOffsetApplier: ViewModifier {
    /// Extracted motion config (nil = family absent → identity).
    let config: MotionOffsetConfig?
    /// The element's own sizing bag — the anchor offset needs w/h.
    let size: SizeConfig
    /// Threaded context for unit resolution + the harness viewport.
    let context: SpacingContext

    func body(content: Content) -> some View {
        // Identity fast path.
        guard let cfg = config, case .ray(let rayDeg)? = cfg.path else {
            return AnyView(content)
        }
        // Element size: explicit CSS w/h resolved the same way the
        // SizeApplier does. Without BOTH axes the anchor is unknowable
        // statically — render unmoved rather than guess (TODO: measure
        // via GeometryReader when a fixture needs it).
        guard let w = SizeApplierResolve.exact(size.width, ctx: context,
                                               parent: CGFloat(context.viewportWidth)),
              let h = SizeApplierResolve.exact(size.height, ctx: context,
                                               parent: CGFloat(context.viewportHeight),
                                               allowPercent: false)
        else { return AnyView(content) }
        // Containing block ≈ the harness content box: canvas width minus
        // the 16px frame padding per side (web parity — the component's
        // parent on the capture page); its height is the element's own
        // flow slot. Only left/top/px positions are exact under this
        // approximation — the wave fixture uses `left top`.
        let cb = CGSize(width: CGFloat(context.viewportWidth) - 32, height: h)
        let start = CGPoint(x: MotionOffsetMath.resolve(cfg.positionX, extent: cb.width),
                            y: MotionOffsetMath.resolve(cfg.positionY, extent: cb.height))
        let rot = MotionOffsetMath.rotation(rayDeg: rayDeg, rotate: cfg.rotate)
        let delta = MotionOffsetMath.translation(start: start, rayDeg: rayDeg,
                                                 distance: cfg.distancePx,
                                                 elementSize: CGSize(width: w, height: h))
        // Rotate about the element centre (anchor), THEN translate the
        // rotated box — mirrors the motion-1 transform composition.
        return AnyView(content
            .rotationEffect(.degrees(rot))
            .offset(x: delta.width, y: delta.height))
    }
}

extension View {
    /// StyleBuilder call surface, matching the `.engine*` convention.
    func engineMotionOffset(_ config: MotionOffsetConfig?,
                            size: SizeConfig,
                            context: SpacingContext) -> some View {
        modifier(MotionOffsetApplier(config: config, size: size, context: context))
    }
}

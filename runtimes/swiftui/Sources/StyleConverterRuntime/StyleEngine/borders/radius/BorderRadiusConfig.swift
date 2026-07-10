//
//  BorderRadiusConfig.swift
//  StyleEngine/borders/radius — Phase 5.
//
//  Value struct for CSS `border-{top,right,bottom,left}-{left,right}-radius`
//  physical corners and the four logical equivalents
//  (`border-{start,end}-{start,end}-radius`). Each corner carries an
//  independent x / y radius so elliptical corners ("20px 10px") survive
//  round-trip — iOS's `RoundedRectangle(cornerRadius:)` only supports
//  square corners so we ship a custom Shape alongside (see
//  `BorderRadiusShape.swift`).
//
//  Mirrors Android's `radius/BorderRadiusConfig.kt`; the logical-to-
//  physical mapping assumes LTR writing mode (matches what the fixtures
//  exercise).
//

// CoreGraphics for `CGFloat`, SwiftUI transitively for Shape.
import SwiftUI

// Per-corner radii. Either side of the pair can be zero independently
// which is how CSS communicates a sharp corner on one axis and a curve
// on the other (`border-radius: 20px 0 0 0 / 10px 0 0 0`).
struct BorderRadiusCorner: Equatable {
    // Horizontal radius in points.
    var x: CGFloat = 0
    // Vertical radius in points.
    var y: CGFloat = 0
    // Percent radii (0–100). CSS Backgrounds 3 §5.1: "Percentages: refer
    // to corresponding dimension of the border box" — the horizontal
    // radius resolves against the box WIDTH, the vertical radius against
    // the box HEIGHT. We can't know the box size at extract time, so a
    // percent axis is carried symbolically and resolved by
    // `resolved(in:)` at draw time. `border-radius: 50%` on a 200×80 box
    // must produce a 100×40 elliptical corner (web behaviour) — NOT the
    // min(w,h)/2 capsule the old px-only placeholder produced.
    var xPercent: CGFloat? = nil
    var yPercent: CGFloat? = nil
    // True when every axis is zero/absent — the Shape drops the arc
    // altogether when both are zero so we get a crisp right angle.
    var isSquare: Bool { x <= 0 && y <= 0 && xPercent == nil && yPercent == nil }
    // Convenience for the common "circular corner" case.
    init(uniform r: CGFloat) { self.x = r; self.y = r }
    // Elliptical init — kept distinct from `uniform` for readability.
    init(x: CGFloat, y: CGFloat) { self.x = x; self.y = y }
    // Percent-aware init — either axis may independently be a percent.
    init(x: CGFloat, y: CGFloat, xPercent: CGFloat?, yPercent: CGFloat?) {
        self.x = x; self.y = y
        self.xPercent = xPercent; self.yPercent = yPercent
    }
    // Zero init used by the default-struct synthesis.
    init() {}

    /// Resolve this corner against a concrete border-box size. Percent
    /// axes multiply width (x) / height (y) per CSS Backgrounds 3 §5.1;
    /// absolute axes pass through unchanged.
    func resolved(in size: CGSize) -> BorderRadiusCorner {
        BorderRadiusCorner(
            x: xPercent.map { size.width * $0 / 100 } ?? x,
            y: yPercent.map { size.height * $0 / 100 } ?? y
        )
    }
}

// Four-corner bundle. The extractor maps logical corners into these
// buckets using the LTR assumption:
//   border-start-start-radius  → topLeft
//   border-start-end-radius    → topRight
//   border-end-end-radius      → bottomRight
//   border-end-start-radius    → bottomLeft
struct BorderRadiusConfig: Equatable {
    // North-west corner.
    var topLeft: BorderRadiusCorner = BorderRadiusCorner()
    // North-east corner.
    var topRight: BorderRadiusCorner = BorderRadiusCorner()
    // South-east corner.
    var bottomRight: BorderRadiusCorner = BorderRadiusCorner()
    // South-west corner.
    var bottomLeft: BorderRadiusCorner = BorderRadiusCorner()

    // Derived: true when at least one corner carries a non-zero radius.
    // Lets appliers skip the Shape allocation for the common square case.
    var hasAny: Bool {
        !topLeft.isSquare || !topRight.isSquare
            || !bottomRight.isSquare || !bottomLeft.isSquare
    }

    // Largest radius on the box — used by the outline applier for the
    // outset-follow-radius case. Percent axes are not resolvable here
    // (no box size in scope) and contribute their raw px component (0);
    // the outline path tolerates the underestimate.
    var maxRadius: CGFloat {
        max(topLeft.x, topLeft.y, topRight.x, topRight.y,
            bottomRight.x, bottomRight.y, bottomLeft.x, bottomLeft.y)
    }
}

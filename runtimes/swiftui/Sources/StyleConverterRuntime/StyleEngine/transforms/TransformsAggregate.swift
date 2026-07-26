//
//  TransformsAggregate.swift
//  StyleEngine/transforms — Phase 8.
//
//  CSS `transform`, the three longhand overrides (`rotate` / `scale` /
//  `translate`) and the ancillary `transform-origin`, `transform-box`,
//  `transform-style`, `perspective`, `perspective-origin`, `backface-
//  visibility` all converge on a single SwiftUI node at render time:
//  they map to the chain `.rotation3DEffect` / `.scaleEffect` / `.offset`
//  / `.rotationEffect` / `.projectionEffect` on one view. To keep each
//  extractor tiny we fold every property into a shared aggregate and let
//  `TransformsApplier` emit the ordered modifier chain in one place.
//
//  Rolled-up state produced by `TransformsExtractor`. All fields default
//  to nil / identity so an empty aggregate short-circuits in the applier.
//

// SwiftUI is needed for UnitPoint used by TransformOrigin.
import SwiftUI

// A single entry in the CSS `transform` function list. Matches the
// IR shape: `{ "fn": "translateX", "x": { "px": 20 } }` etc. We keep
// the full function palette from the parser — see
// `src/main/kotlin/app/parsing/css/properties/longhands/transforms/TransformPropertyParser.kt`.
enum TransformFn: Equatable {
    // 2D + 3D translations. Axes that aren't supplied default to 0.
    // CSS percentages on `translate` resolve against the element's own
    // size — those need GeometryReader at apply time, so we carry
    // optional `xFrac` / `yFrac` (0..1) alongside the absolute pts and
    // let the applier sum `x + size.width * xFrac` (and same for y).
    case translate(x: CGFloat, y: CGFloat, z: CGFloat,
                   xFrac: CGFloat = 0, yFrac: CGFloat = 0)
    // Uniform or per-axis scale. 1.0 is identity.
    case scale(x: CGFloat, y: CGFloat, z: CGFloat)
    // Single rotation around an arbitrary axis; (0,0,1) is the 2D case.
    case rotate(x: CGFloat, y: CGFloat, z: CGFloat, deg: CGFloat)
    // 2D skew on X / Y, expressed in degrees (CSS `skewX(10deg)` etc.).
    case skew(xDeg: CGFloat, yDeg: CGFloat)
    // Affine 2D matrix: a b c d e f → [a c e; b d f; 0 0 1]. CSS order.
    case matrix(a: CGFloat, b: CGFloat, c: CGFloat, d: CGFloat,
                e: CGFloat, f: CGFloat)
    // Perspective function inside transform — distinct from the
    // `perspective` longhand which applies to children. Value is the
    // viewing distance in points.
    case perspective(d: CGFloat)
}

// CSS `transform-origin` — x and y are independent length/percent/keyword
// pairs and z is a pure length. We normalise to a SwiftUI UnitPoint
// (percent-space) when possible, with a `points` fallback when explicit
// pixels were supplied.
struct TransformOriginValue: Equatable {
    // Fractional origin (0…1) when the origin was keyword / percentage-based.
    // Defaults to the centre (0.5, 0.5) to match CSS initial value.
    var unit: UnitPoint = .center
    // Optional pixel offset when a `<length>` component was supplied. The
    // applier resolves against the view bounds using GeometryReader.
    var xPx: CGFloat? = nil
    var yPx: CGFloat? = nil
    // Optional Z translation of the origin — only affects 3D transforms.
    // SwiftUI cannot natively shift the rotation anchor in Z, so we store
    // it and document the TODO in the applier.
    var zPx: CGFloat? = nil
}

// CSS `transform-box` — determines the reference box for origin. SwiftUI
// uses the view's own frame, so these all collapse to "border-box" in
// practice. We keep the keyword for documentation / future SVG routing.
enum TransformBoxKind: String, Equatable {
    case contentBox, borderBox, fillBox, strokeBox, viewBox
}

// CSS `perspective` + ancillary. The applier calls `.projectionEffect`
// with a depth-warped matrix when `perspective` is finite.
struct PerspectiveValue: Equatable {
    // Distance from the viewer in points. `nil` ≡ CSS `none` (no depth).
    var distancePx: CGFloat? = nil
    // Origin in unit-space (matches TransformOriginValue.unit).
    var origin: UnitPoint = .center
}

/// Rolled-up transforms state. Every extractor contributes fields here.
struct TransformsAggregate: Equatable {

    // Declared list of `transform: <fn> <fn> …` functions, in CSS order.
    // IMPORTANT: CSS applies the **first** function innermost (closest to
    // the element) and the last function outermost. SwiftUI modifier
    // chains apply innermost-first too, so we emit `functions` in the
    // same order the IR lists them.
    var functions: [TransformFn] = []

    // Longhand overrides. These sit on top of `functions` and, per CSS
    // spec, compose in the order: translate → rotate → scale on top of
    // whatever `transform` declared. They are tracked separately so the
    // applier can emit them in the correct composition order.
    var translate: TransformFn? = nil   // Always a .translate if set.
    var rotate: TransformFn? = nil      // Always a .rotate if set.
    var scale: TransformFn? = nil       // Always a .scale if set.

    // Origin + reference box.
    var origin: TransformOriginValue? = nil
    var box: TransformBoxKind? = nil

    // 3D container flag — CSS `transform-style: preserve-3d`. SwiftUI
    // applies all 3D effects in 2D-projection space anyway; the flag's
    // render-time job is EXTENDING the 3D rendering context (wave 19 —
    // the applier publishes the accumulated ancestor matrix to children
    // for §5.1 backface culling). Wave 20 (B-RC4): the old
    // `.drawingGroup()` wrap was removed — it rasterised the subtree
    // clipped to the element's bounds, blanking preserve-3d DIVs whose
    // only children are abspos faces (auto height 0 → empty texture).
    var preserve3D: Bool = false

    // Back-face culling. When true and the ACCUMULATED 3D matrix (own
    // rotations × the containing 3D rendering context — wave 19, see
    // BackfaceCulling.swift) turns the plane's normal away from the
    // viewer, the applier collapses the view to opacity 0.
    var backfaceHidden: Bool = false

    // `perspective` + origin (applies to CHILDREN in CSS; here we apply
    // to self as a best-effort since our gallery items don't nest).
    var perspective: PerspectiveValue? = nil

    // Touch flag — set true the moment any extractor contributes to the
    // aggregate so the applier can short-circuit.
    var touched: Bool = false
}

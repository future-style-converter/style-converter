//
//  GradientApplier.swift
//  StyleEngine/background — Phase 4.
//
//  Shared helpers that turn parsed BackgroundImageLayer values into
//  SwiftUI gradient Views. Lives separately from BackgroundImageApplier
//  because the layer-stacking logic alone is close to 200 lines; this
//  file owns the "one layer → one View" conversion.
//

import SwiftUI

enum GradientApplier {

    // Render one layer to an opaque `some View`. Returns an erased view
    // so BackgroundImageApplier can iterate and stack freely.
    static func render(_ layer: BackgroundImageLayer) -> AnyView {
        switch layer {
        case .none:
            // CSS `none` layer — paint Clear so stacking order is preserved.
            return AnyView(Color.clear)
        case .url:
            // URL/data-URI rendering is a non-goal for Phase 4; paint
            // Clear so the stack index math still works. Documented
            // limitation in the Phase 4 report.
            return AnyView(Color.clear)
        case .linear(let angle, let stops):
            return AnyView(linear(angle: angle, stops: stops))
        case .radial(let shape, let stops, let cx, let cy):
            return AnyView(radial(shape: shape, stops: stops, cx: cx, cy: cy))
        case .conic(let from, let stops, let cx, let cy):
            return AnyView(conic(fromDeg: from, stops: stops, cx: cx, cy: cy))
        case .repeating(_, let angle, let stops):
            // Stub: SwiftUI has no repeating-gradient primitive. We
            // fall back to a single-pass gradient (same stops) so the
            // layer still contributes colour and the rest of the stack
            // composites cleanly. Documented limitation.
            return AnyView(linear(angle: angle, stops: stops))
        }
    }

    // ── Linear ─────────────────────────────────────────────────────────

    // Convert a CSS `background` list of stops to SwiftUI gradient stops.
    // CSS 0% lives at the start, 100% at the end; nil positions let
    // SwiftUI interpolate automatically so we just omit them.
    private static func toGradient(_ stops: [BackgroundImageStop]) -> Gradient {
        // Resolve every colour to a SwiftUI Color; fall back to clear
        // when dynamic / unknown so downstream interpolation still works.
        let resolved: [Gradient.Stop] = stops.map { s in
            let color = s.color.toSwiftUIColor() ?? .clear
            // Position nil → spread evenly: SwiftUI doesn't accept nil,
            // so we use Gradient.Stop only when we have a position, and
            // fall back to a plain `.init(colors:)` path when no stops
            // had positions. But to keep a single Gradient type, we
            // emit .init(colors:) when every position is nil.
            return Gradient.Stop(color: color, location: CGFloat(s.position ?? -1))
        }
        // If every stop had nil (we coded as -1), use colors-only init.
        if resolved.allSatisfy({ $0.location < 0 }) {
            return Gradient(colors: resolved.map { $0.color })
        }
        // Otherwise we need real 0..1 locations. For nil entries we fall
        // back to even distribution.
        let total = max(1, stops.count - 1)
        let withDefaults = stops.enumerated().map { (i, s) -> Gradient.Stop in
            let c = s.color.toSwiftUIColor() ?? .clear
            let loc = s.position ?? (Double(i) / Double(total))
            return Gradient.Stop(color: c, location: CGFloat(loc))
        }
        return Gradient(stops: withDefaults)
    }

    // CSS angle (0deg = up) → SwiftUI (startPoint, endPoint) on a unit
    // square. 0deg means gradient goes bottom→top in CSS; map to that.
    private static func endpoints(forAngleDeg angle: Double) -> (UnitPoint, UnitPoint) {
        // Normalise angle to [0, 360).
        let a = ((angle.truncatingRemainder(dividingBy: 360)) + 360)
                .truncatingRemainder(dividingBy: 360)
        // Convert CSS convention (clockwise from north) to radians used
        // by our vector math. North = -PI/2 in standard math.
        let theta = (a - 90) * .pi / 180
        // Unit-circle end point from centre (0.5, 0.5).
        let dx = cos(theta) * 0.5
        let dy = sin(theta) * 0.5
        let start = UnitPoint(x: 0.5 - dx, y: 0.5 - dy)
        let end   = UnitPoint(x: 0.5 + dx, y: 0.5 + dy)
        return (start, end)
    }

    // LinearGradient constructor. `angle` default per CSS spec = 180deg
    // (i.e. to bottom, which means top→bottom direction).
    private static func linear(angle: Double?, stops: [BackgroundImageStop]) -> some View {
        let (s, e) = endpoints(forAngleDeg: angle ?? 180)
        return LinearGradient(gradient: toGradient(stops),
                              startPoint: s, endPoint: e)
    }

    // ── Radial ─────────────────────────────────────────────────────────

    // Radial fills from the centre out. Per CSS Images Module 3 §3.5
    // the default ending shape is `ellipse` and the default sizing is
    // `farthest-corner`, which means the gradient line reaches the
    // farthest corner of the box on each axis. The previous code hard-
    // coded endRadius=200 which produced a tiny bounded circle on every
    // element regardless of size — Audit_RadialEllipseClosestSide /
    // _ConicFrom30degAt25 etc. all rendered a small disc instead of
    // filling the box.
    //
    // We use GeometryReader to read the actual bounds, then for `circle`
    // shape pick a single radius (max of half-width / half-height for the
    // farthest-side default) and for the default `ellipse` we let
    // RadialGradient fill the full element by passing endRadius equal to
    // the diagonal half-length. Per-axis (rx ≠ ry) is faked by scaling
    // the gradient view to box ratio so the SwiftUI radial — which is
    // inherently circular against unit space — appears elliptical when
    // stretched. closest-side / farthest-side keywords aren't carried in
    // the iOS `radial(shape:)` config yet (see BackgroundImageLayer in
    // BackgroundImageConfig.swift), so we honour shape only.
    private static func radial(shape: String?, stops: [BackgroundImageStop],
                               cx: Double, cy: Double) -> some View {
        let unitCenter = UnitPoint(x: cx, y: cy)
        return GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            // Half-diagonal — distance to the farthest corner from the
            // centre (closest match to CSS `farthest-corner` default).
            let halfDiag = sqrt(w * w + h * h) / 2
            // For `circle` shape force a single radius equal to
            // half-diagonal so the gradient stays circular regardless of
            // box aspect ratio.
            if shape == "circle" {
                RadialGradient(gradient: toGradient(stops),
                               center: unitCenter,
                               startRadius: 0,
                               endRadius: halfDiag)
                    .frame(width: w, height: h)
            } else {
                // Ellipse default — RadialGradient is intrinsically
                // circular, so we render at the larger axis radius and
                // stretch via .scaleEffect to match the box's aspect.
                let r = max(w, h) / 2
                RadialGradient(gradient: toGradient(stops),
                               center: unitCenter,
                               startRadius: 0,
                               endRadius: r)
                    .frame(width: max(w, h), height: max(w, h))
                    .scaleEffect(x: w / max(w, h), y: h / max(w, h), anchor: .center)
                    .frame(width: w, height: h)
            }
        }
    }

    // ── Conic ──────────────────────────────────────────────────────────

    // AngularGradient is SwiftUI's conic equivalent. CSS's `from <angle>`
    // sets the starting position; we pass it through as the `angle`
    // parameter (which controls where colour 0% lives).
    private static func conic(fromDeg: Double?, stops: [BackgroundImageStop],
                              cx: Double, cy: Double) -> some View {
        AngularGradient(gradient: toGradient(stops),
                        center: UnitPoint(x: cx, y: cy),
                        angle: .degrees(fromDeg ?? 0))
    }
}

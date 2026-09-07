//
//  GradientApplier.swift
//  StyleEngine/background — Phase 4, reworked in fidelity wave 5.
//
//  Shared helpers that turn parsed BackgroundImageLayer values into
//  SwiftUI gradient Views. Lives separately from BackgroundImageApplier
//  because the layer-stacking logic alone is close to 200 lines; this
//  file owns the "one layer → one View" conversion.
//
//  Wave-5 rework (all 9 pairwise gradient rows were flagged, 0.87–0.91):
//    1. COLOUR SPACE — SwiftUI interpolates Gradient colours in a
//       perceptual space (red→blue passes washed pink); CSS legacy
//       gradients interpolate in plain sRGB (dark purple midtones,
//       css-images-3 §3.4.3 / css-color-4 §13: legacy srgb space). We
//       pre-subdivide every stop pair with sRGB-lerped micro-stops so
//       SwiftUI's own ramp is pinned to the sRGB line.
//    2. ANGLE GEOMETRY — endpoints were computed on a UNIT SQUARE, so
//       any non-square box squashed the angle (45deg on a 200×80 box
//       rendered ≈horizontal). The §3.1.1 endpoint math now runs in
//       PIXEL space (gradient-line length |w·sinθ| + |h·cosθ|).
//    3. REPEATING flavours routed to their base shape. The converter
//       drops px stop positions (period-less), and a repeating gradient
//       whose stops span the whole line is IDENTICAL to its plain
//       counterpart — which is exactly what the web reference renders —
//       but the old stub sent repeating-RADIAL through the vertical
//       LINEAR path (PW_Background_Sizing_03, red→blue bars).
//
//  Wave-46 rework (lane Y2, css-images natives — 14 iOS cells failed
//  where web passed, every one a stop/colour-math defect):
//    4. STOP FIXUP + <length> STOPS — the converter now carries px stop
//       positions (`positionLength`, wave 40), and css-images-3 §3.4.3 / css-images-4 §3.5.3
//       fixup (clamp-forward, even spread between positioned
//       neighbours) replaces the old "nil = i/(n−1)" spread. The
//       gradient-line length is known only at render time, so
//       `toGradient` takes it from the GeometryReader / Canvas size.
//    5. REAL REPEATING — the period (first→last stop) is materialised
//       as copies across the line (GradientStopResolver), so
//       `repeating-linear-gradient(…, white, black, white 30px)` paints
//       30px stripes instead of one ramp (gradient-border-box 0.645).
//    6. INTERPOLATION SPACE — `interp` (in hsl longer hue / in oklab /
//       …) is honoured through GradientRamp (css-color-4 §13:
//       premultiplied, hue arcs, powerless-hue carry). Clause-less
//       gradients keep note-1's sRGB subdivision BYTE-IDENTICAL.
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
        case .repeating(let kind, let angle, let stops):
            // Dispatch on the base kind with the repeating flag set —
            // the resolver tiles the stop span (header note 5). The
            // shape/centre of a repeating-radial are not carried by the
            // `.repeating` case yet (circle at center — wave-5 shape).
            switch kind {
            case .radial: return AnyView(radial(shape: "circle", stops: stops,
                                                cx: .center, cy: .center, repeating: true))
            case .conic:  return AnyView(conic(fromDeg: angle, stops: stops,
                                               cx: .center, cy: .center, repeating: true))
            case .linear: return AnyView(linear(angle: angle, stops: stops, repeating: true))
            }
        case .color(let cv):
            // <color>-as-image (cross-fade argument) — solid fill.
            // Implementation in CrossFadeApplier.swift (file split).
            return AnyView(solidColor(cv))
        case .crossFade(let args):
            // cross-fade() — weighted premultiplied SUM (§2.6.2).
            // Implementation in CrossFadeApplier.swift (file split).
            return AnyView(crossFade(args))
        case .imageNotation(let srcs, let fallback):
            // image() (§2.1) reaches this renderer whenever it is NOT the
            // top-level background layer — a cross-fade() argument, or the
            // knob-less path BackgroundImageApplier hands straight through.
            // Resolve the candidate chain with the SAME resolver the applier
            // uses (one rule, two entry points) and render the winner.
            return render(
                BackgroundImageLayer.resolveImageNotation(srcs: srcs, fallback: fallback))
        }
    }

    // ── Stop resolution ────────────────────────────────────────────────
    // RGBAStop / srgbSubdivided / toGradient / resolvedRamp
    // / lineLengthPx live in GradientApplier+Stops.swift (wave-46 file
    // split — the §3.4.3 pipeline outgrew this file's 200-line target).

    // ── Linear ─────────────────────────────────────────────────────────

    /// css-images-3 §3.1.1 endpoint math in PIXEL space (wave 5): the
    /// gradient line passes through the box centre at CSS angle θ
    /// (clockwise from north) with length |w·sinθ| + |h·cosθ| — the
    /// perpendiculars through its endpoints touch the box corners.
    /// Returned as UnitPoints for SwiftUI (may exceed 0…1 — fine).
    /// Internal so XCTest pins the geometry.
    static func linearEndpoints(angleDeg: Double,
                                size: CGSize) -> (start: UnitPoint, end: UnitPoint) {
        let rad = angleDeg * .pi / 180
        let s = sin(rad), c = cos(rad)
        let w = max(Double(size.width), 0.001)
        let h = max(Double(size.height), 0.001)
        // Gradient-line length in pixels (§3.1.1).
        let len = abs(w * s) + abs(h * c)
        // Direction unit vector in screen coords: 0deg → up = (0, −1).
        let dx = s * len / 2, dy = -c * len / 2
        return (UnitPoint(x: 0.5 - dx / w, y: 0.5 - dy / h),
                UnitPoint(x: 0.5 + dx / w, y: 0.5 + dy / h))
    }

    // LinearGradient constructor. `angle` default per CSS spec = 180deg
    // (to bottom). GeometryReader supplies the pixel box — inside the
    // `.background` slot it is proposed exactly the element's size.
    private static func linear(angle: Double?, stops: [BackgroundImageStop],
                               repeating: Bool = false) -> some View {
        GeometryReader { geo in
            let (s, e) = linearEndpoints(angleDeg: angle ?? 180, size: geo.size)
            // The line length resolves <length> stops / the repeat period.
            LinearGradient(gradient: toGradient(stops,
                                                lengthPx: lineLengthPx(angleDeg: angle ?? 180, size: geo.size),
                                                repeating: repeating),
                           startPoint: s, endPoint: e)
        }
    }

    // ── Radial ─────────────────────────────────────────────────────────

    /// Pre-squash radius of the DEFAULT ellipse ending shape, evaluated
    /// on the max(w,h) square the render-circular-then-stretch trick
    /// shades. css-images-3 §3.2: the default size is `farthest-corner`,
    /// whose ellipse "has the same aspect ratio [as] `farthest-side`"
    /// but is scaled to pass THROUGH the farthest corner. For a centred
    /// gradient the farthest-side aspect is (w/2 : h/2); putting the
    /// corner (w/2, h/2) on the ellipse (k·w/2, k·h/2) gives
    /// 1/k² + 1/k² = 1 → k = √2, i.e. radii (√2·w/2, √2·h/2) — exactly
    /// what Compose and Chromium paint. The pre-fix code stopped at
    /// (w/2, h/2), the farthest-SIDE ellipse: every default-radial fade
    /// on iOS ended 29% early (the live 0.85-gate residual). After the
    /// (w/m, h/m) squash a pre-squash radius of √2·m/2 lands on the
    /// √2-scaled per-axis radii, so this helper returns that value.
    /// Off-centre gradients still approximate with the centred factor —
    /// the off-centre refinement is a recorded follow-up. Internal so
    /// XCTest pins the factor once for all three call sites (view path
    /// here, MaskApplier, BackgroundGradientTileView).
    static func ellipseEndRadius(_ size: CGSize) -> CGFloat {
        // √2 × half the squash-source square's side (see doc above).
        max(size.width, size.height) / 2 * CGFloat(2.0.squareRoot())
    }

    // Radial fills from the centre out. Per CSS Images Module 3 §3.2
    // the default ending shape is `ellipse` and the default sizing is
    // `farthest-corner`. GeometryReader reads the actual bounds; for
    // `circle` a single radius (half-diagonal ≈ farthest-corner), for
    // the default `ellipse` the circular SwiftUI gradient is stretched
    // to the box aspect via scaleEffect.
    private static func radial(shape: String?, stops: [BackgroundImageStop],
                               cx: GradientCoord, cy: GradientCoord,
                               repeating: Bool = false) -> some View {
        return GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            // Resolve the per-axis coords against the ACTUAL box (A-RC8):
            // fractions pass through; px centers divide by the axis so
            // `at 100px 50px` lands exactly 100/50 px from the origin.
            let unitCenter = UnitPoint(x: cx.fraction(Double(w)),
                                       y: cy.fraction(Double(h)))
            // Half-diagonal — distance to the farthest corner from the
            // centre (closest match to CSS `farthest-corner` default).
            let halfDiag = sqrt(w * w + h * h) / 2
            if shape == "circle" {
                // <length> stops measure along the ray — the end radius
                // is the gradient-line length (css-images-3 §3.2.3).
                RadialGradient(gradient: toGradient(stops, lengthPx: Double(halfDiag),
                                                    repeating: repeating),
                               center: unitCenter,
                               startRadius: 0,
                               endRadius: halfDiag)
                    .frame(width: w, height: h)
            } else {
                // Ellipse default — render at the √2-scaled larger-axis
                // radius (farthest-CORNER, see ellipseEndRadius) and
                // stretch to the box's aspect. The gradient function
                // extends past the square frame; only the frame is
                // visible, so an endRadius larger than the frame simply
                // moves the 100% stop outside it — which is the point.
                // Ellipse <length> stops measure along the HORIZONTAL
                // radius (§3.2.3); after the (w/m, h/m) squash that is
                // √2·w/2 — the pre-squash circle shares the fraction.
                RadialGradient(gradient: toGradient(stops,
                                                    lengthPx: Double(w) / 2 * 2.0.squareRoot(),
                                                    repeating: repeating),
                               center: unitCenter,
                               startRadius: 0,
                               endRadius: ellipseEndRadius(geo.size))
                    .frame(width: max(w, h), height: max(w, h))
                    .scaleEffect(x: w / max(w, h), y: h / max(w, h), anchor: .center)
                    .frame(width: w, height: h)
            }
        }
    }

    // ── Conic ──────────────────────────────────────────────────────────

    // AngularGradient is SwiftUI's conic equivalent. CSS's `from <angle>`
    // sets the starting position. The −90° offset maps CSS's 0deg-at-12-
    // o'clock convention onto SwiftUI's trailing-edge zero (wave 1 fix).
    // FRACTION coords keep the original no-GeometryReader path BYTE-
    // IDENTICAL (dark-stage 327 protection: every pre-A-RC8 fixture is
    // percent/keyword-centered); PX coords need the box size, so only
    // they take the GeometryReader branch.
    private static func conic(fromDeg: Double?, stops: [BackgroundImageStop],
                              cx: GradientCoord, cy: GradientCoord,
                              repeating: Bool = false) -> some View {
        // Conic stops are angles/percents of the turn — no px length.
        Group {
            if cx.kind == .fraction && cy.kind == .fraction {
                // Legacy path — unchanged view hierarchy for fractions.
                AngularGradient(gradient: toGradient(stops, repeating: repeating),
                                center: UnitPoint(x: cx.value, y: cy.value),
                                angle: .degrees((fromDeg ?? 0) - 90))
            } else {
                // Length centers (A-RC8): resolve px → fraction against
                // the actual box before building the gradient.
                GeometryReader { geo in
                    AngularGradient(gradient: toGradient(stops, repeating: repeating),
                                    center: UnitPoint(x: cx.fraction(Double(geo.size.width)),
                                                      y: cy.fraction(Double(geo.size.height))),
                                    angle: .degrees((fromDeg ?? 0) - 90))
                }
            }
        }
    }
}

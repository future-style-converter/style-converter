//
//  OutlineShadedRing.swift
//  StyleEngine/borders/outline — retro R5 (audit A7#3, iOS half).
//
//  The 3D outline styles. css-ui-4 §3.3: `outline-style` takes the
//  css-backgrounds-3 §3.2 <line-style> values "with the same meaning", so
//  groove / ridge / inset / outset outlines are the same two-tone bevel
//  the border painter draws (BorderSideApplier), moved out to the outline
//  band [offset, offset + width] outside the border box. OutlineApplier
//  used to stroke all four as a flat solid ring — iOS never gained the
//  arms Compose's OutlineApplier.kt got in wave 5.
//
//  Geometry mirrors that Compose painter (drawShadedRing / halfRing /
//  drawSideBands, calibrated pixel-for-pixel against a headless-Chrome
//  probe in wave 5): each band is four mitered trapezoids between an
//  outer and an inner rectangle, meeting on the 45° corner diagonals.
//  groove/ridge split the ring into a CEIL(w/2) band and a FLOOR(w/2)
//  band; the ceil band is the DARK shade for groove / the declared colour
//  for ridge and sits at the ring's OUTER half on the top/left sides but
//  the INNER half on the bottom/right sides — Chrome, `3px groove`:
//  top/left = dark 2px out + light 1px in; bottom/right = light 1px out +
//  dark 2px in. Colours come from BorderSideApplier.shade — Blink's
//  Color::Dark() model — so border and outline bevels share ONE palette.
//

import SwiftUI

/// One shaded trapezoid pair (top+left or bottom+right) of one ring layer.
/// `outerInset` is measured inward from the ring's OUTER edge — the Canvas
/// edge, since OutlineApplier inflates its overlay by offset + width — so
/// a full-width band is (0, width) and groove's inner half is (ceil, floor).
struct OutlineShadedBand: Equatable {
    /// Distance from the ring's outer edge to this band's outer boundary.
    var outerInset: CGFloat
    /// The band's radial thickness.
    var thickness: CGFloat
    /// true → the top + left trapezoids; false → bottom + right.
    var topLeft: Bool
    /// The band's fill (one of BorderSideApplier.shade's two tones).
    var colour: Color
}

/// Pure band table + trapezoid geometry for the 3D outline styles.
enum OutlineShadedRing {

    /// The four 3D <line-style> keywords this painter owns; every other
    /// style stays on OutlineShape's stroke paths.
    static func isThreeD(_ style: BorderStyleValue) -> Bool {
        switch style {
        case .groove, .ridge, .inset, .outset: return true
        default: return false
        }
    }

    /// The band table for one style / width / declared colour.
    static func bands(style: BorderStyleValue, width w: CGFloat, base: Color) -> [OutlineShadedBand] {
        // Blink's two tones: Dark() and the declared colour (black → its
        // lightened-black fast path) — see BorderSideApplier.shade.
        let dark = BorderSideApplier.shade(base, light: false)
        let light = BorderSideApplier.shade(base, light: true)
        switch style {
        case .inset:
            // Sunken: top/left dark, bottom/right the declared colour — the
            // same per-side rule as BorderSideApplier's `.inset` arm.
            return [OutlineShadedBand(outerInset: 0, thickness: w, topLeft: true, colour: dark),
                    OutlineShadedBand(outerInset: 0, thickness: w, topLeft: false, colour: light)]
        case .outset:
            // Raised: the exact inverse of inset.
            return [OutlineShadedBand(outerInset: 0, thickness: w, topLeft: true, colour: light),
                    OutlineShadedBand(outerInset: 0, thickness: w, topLeft: false, colour: dark)]
        case .groove, .ridge:
            // Chrome's split: ceil half + floor half (equal for even widths).
            let tCeil = ceil(w / 2)
            let tFloor = w - tCeil
            // groove carves in (dark ceil band), ridge raises (light ceil band).
            let ceilColour = style == .groove ? dark : light
            let floorColour = style == .groove ? light : dark
            // Top/left: ceil band at the ring's OUTER half…
            var out = [OutlineShadedBand(outerInset: 0, thickness: tCeil, topLeft: true, colour: ceilColour)]
            if tFloor > 0 {
                // …floor band inside it; bottom/right invert the order.
                out.append(OutlineShadedBand(outerInset: tCeil, thickness: tFloor, topLeft: true, colour: floorColour))
                out.append(OutlineShadedBand(outerInset: 0, thickness: tFloor, topLeft: false, colour: floorColour))
            }
            // Bottom/right: ceil band at the ring's INNER half.
            out.append(OutlineShadedBand(outerInset: tFloor, thickness: tCeil, topLeft: false, colour: ceilColour))
            return out
        default:
            // Not a 3D style — OutlineShape strokes it; nothing to shade.
            return []
        }
    }

    /// The two mitered trapezoids of one band inside `rect` (the ring's
    /// outer rectangle). Corners meet on the 45° diagonal because both
    /// rectangles shrink by the same thickness on every side — the same
    /// quads Compose's drawSideBands paints.
    static func trapezoids(_ b: OutlineShadedBand, in rect: CGRect) -> [Path] {
        // Outer rectangle of THIS band.
        let oL = rect.minX + b.outerInset, oT = rect.minY + b.outerInset
        let oR = rect.maxX - b.outerInset, oB = rect.maxY - b.outerInset
        // Inner rectangle: one thickness further in.
        let iL = oL + b.thickness, iT = oT + b.thickness
        let iR = oR - b.thickness, iB = oB - b.thickness
        // Closed four-point polygon.
        func quad(_ p: [CGPoint]) -> Path {
            var path = Path()
            path.move(to: p[0])
            for q in p.dropFirst() { path.addLine(to: q) }
            path.closeSubpath()
            return path
        }
        if b.topLeft {
            return [
                // Top: outer TL → outer TR → inner TR → inner TL.
                quad([CGPoint(x: oL, y: oT), CGPoint(x: oR, y: oT), CGPoint(x: iR, y: iT), CGPoint(x: iL, y: iT)]),
                // Left: outer TL → inner TL → inner BL → outer BL.
                quad([CGPoint(x: oL, y: oT), CGPoint(x: iL, y: iT), CGPoint(x: iL, y: iB), CGPoint(x: oL, y: oB)]),
            ]
        }
        return [
            // Bottom: outer BL → inner BL → inner BR → outer BR.
            quad([CGPoint(x: oL, y: oB), CGPoint(x: iL, y: iB), CGPoint(x: iR, y: iB), CGPoint(x: oR, y: oB)]),
            // Right: outer TR → outer BR → inner BR → inner TR.
            quad([CGPoint(x: oR, y: oT), CGPoint(x: oR, y: oB), CGPoint(x: iR, y: iB), CGPoint(x: iR, y: iT)]),
        ]
    }
}

/// Canvas painter for a 3D outline ring. Sized by OutlineApplier's overlay,
/// whose frame IS the ring's outer rectangle (the border box inflated by
/// offset + width on every side).
struct OutlineShadedRingView: View {
    /// The outline's corner radii (already grown by OutlineApplier).
    let radius: BorderRadiusConfig
    /// groove / ridge / inset / outset.
    let style: BorderStyleValue
    /// The outline width — the full ring thickness.
    let width: CGFloat
    /// The resolved outline colour (declared → currentColor → fallback ink).
    let colour: Color

    var body: some View {
        Canvas { ctx, size in
            let rect = CGRect(origin: .zero, size: size)
            for band in OutlineShadedRing.bands(style: style, width: width, base: colour) {
                // GraphicsContext is a value type: clipping a COPY keeps the
                // clip local to this band (same idiom as the gradient tiler).
                var c = ctx
                if radius.hasAny {
                    // Rounded outline: confine the trapezoids to THIS band's
                    // rounded ring — outer path + inner path, even-odd — so
                    // the bevel follows the corner curve exactly like the
                    // stroked styles' `.strokeBorder` does.
                    var ring = BorderRadiusShape(radius: radius, inset: band.outerInset).path(in: rect)
                    ring.addPath(BorderRadiusShape(radius: radius,
                                                   inset: band.outerInset + band.thickness).path(in: rect))
                    c.clip(to: ring, style: FillStyle(eoFill: true))
                }
                for quad in OutlineShadedRing.trapezoids(band, in: rect) {
                    c.fill(quad, with: .color(band.colour))
                }
            }
        }
    }
}

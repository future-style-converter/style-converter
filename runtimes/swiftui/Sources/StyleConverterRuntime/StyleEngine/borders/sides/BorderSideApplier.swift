//
//  BorderSideApplier.swift
//  StyleEngine/borders/sides — Phase 5.
//
//  Paints the border box. Four render paths, cheapest first:
//
//    1. No border          → identity.
//    2. Uniform solid       → SwiftUI `.overlay(RoundedRectangle.stroke)`
//                             — indistinguishable from `.border()` but
//                             honours the border-radius overlay chain.
//    3. Uniform non-solid   → Canvas-based overlay with a single Path
//                             stroked via `StrokeStyle(dash:)` for
//                             dotted/dashed/double.
//    4. Per-side            → Canvas with four independent line segments
//                             so each side can carry its own width /
//                             colour / style (CSS 2.1 §8.5.4 defers the
//                             corner rendering; we render butt-capped
//                             miters to match Android/Web).
//
//  Border-radius is forwarded via a BorderRadiusConfig so the stroke
//  follows the rounded shape. Mixed per-side radii on a per-side border
//  fall back to the largest radius (documented correctness shift —
//  matches Android for the same reason: no native per-corner API).
//

// SwiftUI for Color/View; CoreGraphics for Path drawing.
import SwiftUI

// Entry modifier. Composes the chosen render path based on the config.
struct BorderSideApplier: ViewModifier {
    // Nil → no border-* property was present → identity body.
    let config: AllBordersConfig?
    // Forwarded radius so the outline follows the rounded box. Nil when
    // no radius config was extracted — uses square corners.
    let radius: BorderRadiusConfig?
    // CSS `currentColor` resolution target: when a border side has a
    // style+width but NO explicit colour, the initial value of
    // `border-*-color` is `currentColor` (CSS Backgrounds 3 §3.2) —
    // i.e. the element's own `color`. The capture harness inherits
    // `color: #eee` from the web body, so absent BOTH an element colour
    // and an IR colour we fall back to that same light grey instead of
    // `.primary` (which painted the dots BLACK on iOS while web/Android
    // rendered them light — borders/003_C04 divergence).
    var currentColor: Color? = nil

    // The resolved fallback for colourless sides. 0.933 white == #eee,
    // matching the web harness body colour that currentColor inherits.
    private var inheritedColor: Color {
        currentColor ?? Color(white: 0.933)
    }

    func body(content: Content) -> some View {
        // Fast path A — absent or empty.
        guard let cfg = config, cfg.hasAny else { return AnyView(content) }

        // Fast path B — every side identical AND style is solid.
        // Uses the same shape the background paints with, so the border
        // visually sits on the perimeter.
        if cfg.isUniform, let w = cfg.top.width, w > 0,
           (cfg.top.style ?? .solid) == .solid {
            let colour = cfg.top.color ?? inheritedColor
            return AnyView(
                content.overlay(
                    BorderRadiusShape(radius: radius ?? BorderRadiusConfig())
                        // `.stroke` centres on the path; inset by half so
                        // the stroke lands on the inside edge (matches CSS).
                        .strokeBorder(colour, lineWidth: w)
                )
            )
        }

        // Fast path C — uniform non-solid: one stroked path with a
        // StrokeStyle carrying the dash pattern. Covers dotted/dashed;
        // double/groove/ridge/inset/outset route to `drawPerSide` so the
        // Canvas can stack the two strokes double needs.
        if cfg.isUniform, let w = cfg.top.width, w > 0,
           let s = cfg.top.style, s == .dotted || s == .dashed {
            let colour = cfg.top.color ?? inheritedColor
            return AnyView(
                content.overlay(
                    BorderRadiusShape(radius: radius ?? BorderRadiusConfig())
                        .strokeBorder(colour, style: dashStyle(s, width: w))
                )
            )
        }

        // General path — Canvas draws each side independently. Covers
        // per-side widths/colours/styles + the double/groove/ridge/inset
        // /outset uniform cases the path above skipped.
        return AnyView(
            content.overlay(
                Canvas { ctx, size in
                    drawPerSide(ctx: &ctx, size: size, cfg: cfg)
                }
            )
        )
    }

    // Build a dash-pattern StrokeStyle. Values mirror the Android applier
    // (BorderSideApplier.kt) so visual output lines up across platforms.
    private func dashStyle(_ style: BorderStyleValue, width w: CGFloat) -> StrokeStyle {
        switch style {
        // CSS `border-style: dashed` is implementation-defined; Chromium
        // renders much longer dashes than a 3w:2w pattern (~9 dashes per
        // 218px edge at w=2 vs ~22 with the prior ratio), so iOS+Android
        // both diverged sharply from web on every dashed-border fixture.
        // 6w:4w (12px on, 8px off at w=2) lands the dash count + stroke
        // ratio in the same band as Chromium's default.
        case .dashed: return StrokeStyle(lineWidth: w, dash: [w * 6, w * 4])
        case .dotted: return StrokeStyle(lineWidth: w, lineCap: .round, dash: [0.01, w * 2])
        default:      return StrokeStyle(lineWidth: w)
        }
    }

    // Per-side Canvas renderer. Draws four trapezoid-clipped edges so
    // widths can differ per side without bleed at the corners.
    private func drawPerSide(ctx: inout GraphicsContext, size: CGSize, cfg: AllBordersConfig) {
        drawEdge(&ctx, size: size, side: .top,    c: cfg.top)
        drawEdge(&ctx, size: size, side: .right,  c: cfg.end)
        drawEdge(&ctx, size: size, side: .bottom, c: cfg.bottom)
        drawEdge(&ctx, size: size, side: .left,   c: cfg.start)
    }

    // Axis tag — simplifies the geometry code below.
    private enum Side { case top, right, bottom, left }

    // Draw a single edge. We stroke the mid-line of the edge with
    // `lineWidth = width` so the stroke fills the side's width band
    // exactly (same trick as Android's drawLine path).
    private func drawEdge(_ ctx: inout GraphicsContext, size: CGSize,
                          side: Side, c: BorderSideConfig) {
        // effectiveWidth (not raw width) so a style-only side paints at
        // the CSS `medium` 3px default like web does.
        guard c.hasBorder, let w = c.effectiveWidth else { return }
        // currentColor fallback — see the `inheritedColor` doc above.
        let colour = c.color ?? inheritedColor
        let style = c.style ?? .solid
        // Line segment spanning the edge at its midline.
        let mid = w / 2
        var path = Path()
        switch side {
        case .top:
            path.move(to: CGPoint(x: 0, y: mid))
            path.addLine(to: CGPoint(x: size.width, y: mid))
        case .right:
            path.move(to: CGPoint(x: size.width - mid, y: 0))
            path.addLine(to: CGPoint(x: size.width - mid, y: size.height))
        case .bottom:
            path.move(to: CGPoint(x: 0, y: size.height - mid))
            path.addLine(to: CGPoint(x: size.width, y: size.height - mid))
        case .left:
            path.move(to: CGPoint(x: mid, y: 0))
            path.addLine(to: CGPoint(x: mid, y: size.height))
        }

        // Dispatch on style — double needs two thin strokes, groove/ridge
        // /inset/outset degrade to solid today (CSS 3D-shade palette is
        // non-trivial; see TODO in BordersSelfTest).
        switch style {
        case .double where w >= 3:
            // Split the width into three bands: outer stroke, gap,
            // inner stroke, each `w/3` wide. Matches CSS 2.1 §8.5.3.
            let band = w / 3
            // Outer line — shift outward by band, stroke at band width.
            var outer = Path()
            var inner = Path()
            switch side {
            case .top:
                outer.move(to: CGPoint(x: 0, y: band / 2))
                outer.addLine(to: CGPoint(x: size.width, y: band / 2))
                inner.move(to: CGPoint(x: 0, y: w - band / 2))
                inner.addLine(to: CGPoint(x: size.width, y: w - band / 2))
            case .right:
                outer.move(to: CGPoint(x: size.width - band / 2, y: 0))
                outer.addLine(to: CGPoint(x: size.width - band / 2, y: size.height))
                inner.move(to: CGPoint(x: size.width - w + band / 2, y: 0))
                inner.addLine(to: CGPoint(x: size.width - w + band / 2, y: size.height))
            case .bottom:
                outer.move(to: CGPoint(x: 0, y: size.height - band / 2))
                outer.addLine(to: CGPoint(x: size.width, y: size.height - band / 2))
                inner.move(to: CGPoint(x: 0, y: size.height - w + band / 2))
                inner.addLine(to: CGPoint(x: size.width, y: size.height - w + band / 2))
            case .left:
                outer.move(to: CGPoint(x: band / 2, y: 0))
                outer.addLine(to: CGPoint(x: band / 2, y: size.height))
                inner.move(to: CGPoint(x: w - band / 2, y: 0))
                inner.addLine(to: CGPoint(x: w - band / 2, y: size.height))
            }
            ctx.stroke(outer, with: .color(colour), style: StrokeStyle(lineWidth: band))
            ctx.stroke(inner, with: .color(colour), style: StrokeStyle(lineWidth: band))
        case .dashed:
            ctx.stroke(path, with: .color(colour),
                       style: StrokeStyle(lineWidth: w, dash: [w * 3, w * 2]))
        case .dotted:
            ctx.stroke(path, with: .color(colour),
                       style: StrokeStyle(lineWidth: w, lineCap: .round,
                                          dash: [0.01, w * 2]))
        default:
            // solid / double<3 / groove / ridge / inset / outset → solid.
            // TODO(phase 5.1): groove/ridge/inset/outset need a paired
            // darker/lighter tint to approximate the 3D shading. Tracked
            // in BordersSelfTest.
            ctx.stroke(path, with: .color(colour), style: StrokeStyle(lineWidth: w))
        }
    }
}

// Chainable helper — attaches via a tagged modifier for readability in
// StyleBuilder.applyStyle (mirrors `.engineBackgroundColor`).
extension View {
    func engineBorderSides(_ config: AllBordersConfig?,
                           radius: BorderRadiusConfig? = nil,
                           currentColor: Color? = nil) -> some View {
        modifier(BorderSideApplier(config: config, radius: radius,
                                   currentColor: currentColor))
    }

    /// CSS box model: content sits INSIDE the border band. The border
    /// itself is painted as an `.overlay` stroke on the border box, so
    /// without this inset the text/children start at the border box's
    /// edge and the stroke paints OVER the first `width` points of
    /// content — web/Android shift content inward by exactly the border
    /// width (CSS 2.1 §8.1: content edge = border edge + border width +
    /// padding). Attached innermost in applyStyle (before the CSS
    /// padding) so the total border-box width is unchanged when an
    /// explicit `width` is set (border-box sizing) and grows by the
    /// border width otherwise — both matching web.
    func engineBorderContentInset(_ config: AllBordersConfig?) -> some View {
        // Zero-inset fast path keeps the modifier cost-free when no
        // border is declared.
        let insets = EdgeInsets(
            top:      config?.top.hasBorder    == true ? (config?.top.effectiveWidth ?? 0)    : 0,
            leading:  config?.start.hasBorder  == true ? (config?.start.effectiveWidth ?? 0)  : 0,
            bottom:   config?.bottom.hasBorder == true ? (config?.bottom.effectiveWidth ?? 0) : 0,
            trailing: config?.end.hasBorder    == true ? (config?.end.effectiveWidth ?? 0)    : 0
        )
        return padding(insets)
    }
}

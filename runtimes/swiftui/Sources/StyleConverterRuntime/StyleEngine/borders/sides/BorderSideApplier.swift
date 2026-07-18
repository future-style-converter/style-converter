//
//  BorderSideApplier.swift
//  StyleEngine/borders/sides — Phase 5.
//
//  Paints the border box. Three render paths, cheapest first:
//
//    1. No border          → identity.
//    2. Uniform solid       → SwiftUI `.overlay(RoundedRectangle.stroke)`
//                             — indistinguishable from `.border()` but
//                             honours the border-radius overlay chain.
//    3. Uniform dashed/dotted WITH border-radius
//                           → continuous strokeBorder perimeter along the
//                             rounded shape (see usesRoundedDashPerimeter
//                             for the tradeoff).
//    4. Per-side            → Canvas with four independent line segments
//                             so each side can carry its own width /
//                             colour / style (CSS 2.1 §8.5.4 defers the
//                             corner rendering; we render butt-capped
//                             miters to match Android/Web). Uniform
//                             dotted/dashed borders at radius ZERO ALSO
//                             take this path: the old single-perimeter
//                             StrokeStyle(dash:) overlay accrued dash
//                             phase around corners (and centred dots on
//                             the strokeBorder inset), so iOS drifted
//                             from BOTH web and Android — per-side
//                             strokes reset the pattern at every corner
//                             like they do.
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

        // Fast path C — uniform dashed/dotted WITH a border-radius:
        // continuous strokeBorder along the rounded shape. The wave-2
        // per-side rework below paints straight full-length edges, so
        // without this guard a uniform dashed border + border-radius
        // drew a SQUARE dashed perimeter over the rounded background
        // (self-inflicted regression). See usesRoundedDashPerimeter for
        // the phase-accrual tradeoff this routing accepts.
        if Self.usesRoundedDashPerimeter(cfg: cfg, radius: radius),
           let w = cfg.top.effectiveWidth {
            let colour = cfg.top.color ?? inheritedColor
            return AnyView(
                content.overlay(
                    BorderRadiusShape(radius: radius ?? BorderRadiusConfig())
                        // strokeBorder insets by half the width so the
                        // pattern band sits inside the border box, same
                        // as the solid fast path above.
                        .strokeBorder(colour, style: Self.roundedDashStrokeStyle(
                            cfg.top.style ?? .solid, width: w))
                )
            )
        }

        // General path — Canvas draws each side independently. Covers
        // per-side widths/colours/styles AND every uniform non-solid
        // style at radius zero. Uniform dotted/dashed used to take a
        // single-perimeter strokeBorder fast path here unconditionally,
        // but one continuous dash pattern accrues phase around the
        // corners (the pattern never resets), so the three corners after
        // the start drifted vs web/Android — both of which paint
        // per-side patterns that restart at each corner.
        return AnyView(
            content.overlay(
                Canvas { ctx, size in
                    drawPerSide(ctx: &ctx, size: size, cfg: cfg)
                }
            )
        )
    }

    // Routing predicate for uniform dashed/dotted borders: TRUE routes
    // the border to the continuous rounded strokeBorder perimeter (fast
    // path C), FALSE to the per-side Canvas. The wave-2 per-side rework
    // paints straight full-length edges, which is CORRECT at radius
    // zero (per-edge pattern reset, matching Chromium/Android) but drew
    // a square dashed/dotted outline OVER rounded corners whenever a
    // border-radius was set. With any non-zero radius we accept the
    // continuous path's corner phase accrual — the pattern not
    // resetting per side is the LESSER evil versus a square perimeter
    // on a rounded box (Chromium follows the rounded edge too; only its
    // phase behaviour differs). Internal static so XCTest pins the
    // routing (radius>0 → strokeBorder, radius==0 → per-side).
    static func usesRoundedDashPerimeter(cfg: AllBordersConfig,
                                         radius: BorderRadiusConfig?) -> Bool {
        // Only UNIFORM dashed/dotted borders have two competing paths —
        // per-side configs always need the Canvas, other styles have
        // their own routes.
        guard cfg.isUniform, let s = cfg.top.style, s == .dashed || s == .dotted,
              let w = cfg.top.effectiveWidth, w > 0 else { return false }
        // Any non-zero corner radius → the rounded perimeter wins.
        return radius?.hasAny == true
    }

    // StrokeStyle for the continuous rounded perimeter (fast path C).
    // Dashed reuses the shared width-conditional nominal rhythm
    // (dashedIntervals) — the per-EDGE fit is meaningless on one
    // continuous closed path, so the nominal on:off ratio is the best
    // available match to Chromium's rhythm. Dotted approximates circles
    // with a near-zero dash + round cap at a 2w pitch — the pre-wave-2
    // perimeter recipe: the cap swells each 0.01pt dash into a ~w-long
    // rounded dot that follows the curve, which beats square dots
    // painted off the rounded edge. Internal static so XCTest pins both
    // recipes alongside the routing.
    static func roundedDashStrokeStyle(_ style: BorderStyleValue,
                                       width w: CGFloat) -> StrokeStyle {
        style == .dotted
            ? StrokeStyle(lineWidth: w, lineCap: .round, dash: [0.01, w * 2])
            : StrokeStyle(lineWidth: w, dash: dashedIntervals(width: w))
    }

    // Dash intervals [on, off] in points for CSS `border-style: dashed`.
    // Dashed painting is UA-defined (css-backgrounds-3 §3.2 only says
    // "square-ended dashes"), so Chromium's painter is the cross-platform
    // reference, and its on:off rhythm is width-dependent:
    //   - Thick (w >= 3px): at the dashed fixture's w=5 Chromium paints
    //     ≈10px on / 5px off (~11 dashes on a 160px edge) — a 2w:1w
    //     rhythm. A fixed 6w:4w made native dashes 3x too long there.
    //   - Thin (w < 3px): the 6w:4w tuning was measured against Chromium
    //     at w=2 (12px on / 8px off ≈ 9 dashes on a 218px edge).
    // The NOMINAL rhythm every dashed edge starts from — the per-edge
    // fit below preserves this on:off ratio. `static` internal so
    // XCTest (@testable) can pin the interval choice; the Android
    // applier mirrors this rule (BorderSideApplier.kt).
    static func dashedIntervals(width w: CGFloat) -> [CGFloat] {
        w >= 3 ? [w * 2, w] : [w * 6, w * 4]
    }

    // Fit the nominal dashed rhythm to one edge of length `len` so the
    // edge starts AND ends on a full dash. Chromium adjusts the
    // dash/gap pair per edge so an integer number of dashes spans it
    // exactly; painting the fixed nominal rhythm truncated the final
    // dash mid-way at three of the four corners (the pattern phase
    // never reset). n = max(1, round-half-up((len + gap) / (dash +
    // gap))) full dashes — the numerator adds back the one trailing gap
    // the last dash does not need — then both intervals scale by the
    // single factor len / (n·dash + (n-1)·gap) so n dashes + (n-1) gaps
    // == len exactly. floor(x+0.5) rounding matches the Android helper
    // (kotlin.math.round is ties-to-even and disagreed on exact halves).
    static func fittedDashIntervals(length len: CGFloat, width w: CGFloat) -> [CGFloat] {
        // Nominal rhythm for this width — the ratio the fit preserves.
        let nominal = dashedIntervals(width: w)
        let dash = nominal[0]
        let gap = nominal[1]
        // Degenerate edge — nothing to fit against; keep the nominal.
        guard len > 0 else { return nominal }
        // Integer dash count closest to the nominal rhythm (min 1: an
        // edge shorter than one dash paints one full dash, i.e. solid).
        let n = max(1, Int(((len + gap) / (dash + gap) + 0.5).rounded(.down)))
        // One shared stretch/shrink factor keeps the on:off proportion.
        let scale = len / (CGFloat(n) * dash + CGFloat(n - 1) * gap)
        return [dash * scale, gap * scale]
    }

    // Number of dots Chromium fits on a dotted edge of length `len` with
    // dot diameter `w`: pitches = round-half-UP((len - w) / 2w), dots =
    // pitches + 1, first/last dot flush with the edge ends. Port of the
    // Android helper (BorderSideApplier.dottedDotCount) — including its
    // floor(x+0.5) rounding, which Chromium needs on the exact-half
    // case (240px side / 8px dots = 14.5 pitches → 16 dots, not 15).
    static func dottedDotCount(length len: CGFloat, width w: CGFloat) -> Int {
        // Degenerate inputs paint nothing (mirrors Android).
        guard len > 0, w > 0 else { return 0 }
        // Ideal centre-to-centre pitch is 2w (dot w + gap w); fit an
        // integer pitch count into the span between the two end dots.
        let pitches = Int(((len - w) / (2 * w) + 0.5).rounded(.down))
        return max(1, pitches + 1)
    }

    // Blink's Color::Dark()/Light() two-tone palette for the 3D border
    // styles (groove/ridge/inset/outset). 3D shading is UA-defined, so
    // the web reference engine's own arithmetic is the contract
    // (third_party/blink/renderer/platform/graphics/color.cc):
    //   - DARK band  — Dark(): v = max(r,g,b); every channel scales by
    //     the SUBTRACTIVE multiplier max(0, (v − 0.33) / v), i.e. the
    //     max channel drops by an absolute 0.33 and the others keep
    //     their ratios; black stays black via the max(0, ·) clamp. The
    //     previous flat ×0.65 was a one-point measurement of this model
    //     at v = 239/255 ((0.937−0.33)/0.937 = 0.648) that drifted for
    //     every other declared colour (mid greys darken twice as hard
    //     under Blink: 0.5 → 0.17, not 0.325).
    //   - LIGHT band — the declared colour UNCHANGED, except a BLACK
    //     base where both bands would collapse to black: Blink's
    //     Light() hardcodes lightened black rgb(84,84,84)
    //     (kLightenedBlack; 84/255 ≈ 0.33), keeping the carve visible
    //     on `groove black`.
    // Mirrors the Android applier's shade() so the natives cannot drift.
    // UIColor bridging (available via SwiftUI on iOS/Catalyst — same
    // approach as MaskApplier) extracts the RGBA components, since
    // SwiftUI.Color has no direct channel accessors on iOS 16.
    static func shade(_ base: Color, light: Bool) -> Color {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        // Non-RGB-convertible colours (dynamic/catalog) fall back to the
        // base rather than guessing — no silent wrong-colour band.
        guard UIColor(base).getRed(&r, green: &g, blue: &b, alpha: &a) else { return base }
        // Blink's "v": the max channel the absolute 0.33 shift scales by.
        let v = max(r, max(g, b))
        if light {
            // Light band = base, except black → kLightenedBlack (see doc).
            // Alpha carries over — Blink lightens in-gamut only.
            if v <= 0 {
                return Color(red: 84.0 / 255.0, green: 84.0 / 255.0,
                             blue: 84.0 / 255.0, opacity: a)
            }
            return base
        }
        // Dark band — subtractive multiplier; alpha untouched (Blink
        // darkens in-gamut without changing transparency).
        let m = v > 0 ? max(0, (v - 0.33) / v) : 0
        return Color(red: r * m, green: g * m, blue: b * m, opacity: a)
    }

    // (outer, inner) band colours for one groove/ridge edge. Blink
    // paints groove as two half-width SUB-BORDERS — the outer half with
    // the INSET rule, the inner half with OUTSET (ridge is the exact
    // inverse) — and each sub-border then darkens per side via the
    // shared Blink rule `dark ⇔ (side==top || side==left) ==
    // (substyle==inset)` (box_border_painter.cc CalculateBorderStyleColor,
    // the same rule the single-band inset/outset branches below apply).
    // Consequence: bottom/right sides INVERT the band order — groove is
    // dark-outer/light-inner on top/left but light-outer/dark-inner on
    // bottom/right, which is what makes the carve read as 3D. Internal
    // static so XCTest pins the full 2-style × 2-side-class truth table.
    static func grooveRidgeBandColours(style: BorderStyleValue, isTopLeft: Bool,
                                       base: Color) -> (outer: Color, inner: Color) {
        // Substyle of the OUTER half: groove carves in (inset), ridge
        // raises (outset). The inner half is always the opposite.
        let outerInset = style == .groove
        // Per-side darken rule applied to the outer half; the inner half
        // takes the opposite substyle, hence the opposite band.
        let outerDark = isTopLeft == outerInset
        return (shade(base, light: !outerDark),
                shade(base, light: outerDark))
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

    // Straight-line path for one edge whose stroke CENTRE sits `inset`
    // points inside the box's outer edge. Shared by every stroke this
    // painter draws: the main band (inset w/2), the two 1/3-width double
    // strokes, and the two half-width groove/ridge strokes.
    private func edgeLine(side: Side, size: CGSize, inset: CGFloat) -> Path {
        var path = Path()
        switch side {
        case .top:
            path.move(to: CGPoint(x: 0, y: inset))
            path.addLine(to: CGPoint(x: size.width, y: inset))
        case .right:
            path.move(to: CGPoint(x: size.width - inset, y: 0))
            path.addLine(to: CGPoint(x: size.width - inset, y: size.height))
        case .bottom:
            path.move(to: CGPoint(x: 0, y: size.height - inset))
            path.addLine(to: CGPoint(x: size.width, y: size.height - inset))
        case .left:
            path.move(to: CGPoint(x: inset, y: 0))
            path.addLine(to: CGPoint(x: inset, y: size.height))
        }
        return path
    }

    // Length of one edge along its axis — the fit basis for the dashed
    // and dotted per-edge patterns (edges span the full box dimension,
    // same as Android's sideGeometry, so corners overlap symmetrically).
    private func edgeLength(side: Side, size: CGSize) -> CGFloat {
        switch side {
        case .top, .bottom: return size.width
        case .left, .right: return size.height
        }
    }

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
        // Full-width band stroke centred w/2 inside the edge.
        let path = edgeLine(side: side, size: size, inset: w / 2)

        // Dispatch on style — double and groove/ridge stack two strokes;
        // dashed/dotted fit their pattern to this edge's length; inset/
        // outset pick a per-side band from the Chromium 3D palette.
        switch style {
        case .double where w >= 3:
            // Split the width into three bands: outer stroke, gap,
            // inner stroke, each `w/3` wide. Matches CSS 2.1 §8.5.3.
            let band = w / 3
            ctx.stroke(edgeLine(side: side, size: size, inset: band / 2),
                       with: .color(colour), style: StrokeStyle(lineWidth: band))
            ctx.stroke(edgeLine(side: side, size: size, inset: w - band / 2),
                       with: .color(colour), style: StrokeStyle(lineWidth: band))
        case .dashed:
            // Per-edge FITTED intervals — see fittedDashIntervals: the
            // edge starts AND ends on a full dash like Chromium/Android;
            // the fixed nominal rhythm truncated mid-dash at corners.
            ctx.stroke(path, with: .color(colour),
                       style: StrokeStyle(lineWidth: w,
                                          dash: Self.fittedDashIntervals(
                                              length: edgeLength(side: side, size: size),
                                              width: w)))
        case .dotted:
            // True spaced circles fitted per edge — the old near-zero
            // dash + round cap painted 2w-long oblongs whose phase never
            // reset at corners. See drawDotted / dottedDotCount.
            drawDotted(&ctx, size: size, side: side, width: w, colour: colour)
        case .groove, .ridge:
            // CSS 3D carve: two half-width parallel strokes (same
            // geometry as Android's drawGrooveOrRidge). Sub-2pt widths
            // cannot fit two visible bands — degrade to solid like
            // Android (and like Chromium's sub-pixel collapse).
            guard w >= 2 else {
                ctx.stroke(path, with: .color(colour), style: StrokeStyle(lineWidth: w))
                break
            }
            let half = w / 2
            // Band order is PER-SIDE (the old code painted the top/left
            // order on all four sides, flattening the 3D carve on the
            // bottom/right edges) — see grooveRidgeBandColours for the
            // Blink rule this ports.
            let bands = Self.grooveRidgeBandColours(
                style: style, isTopLeft: side == .top || side == .left, base: colour)
            ctx.stroke(edgeLine(side: side, size: size, inset: half / 2),
                       with: .color(bands.outer), style: StrokeStyle(lineWidth: half))
            ctx.stroke(edgeLine(side: side, size: size, inset: half / 2 + half),
                       with: .color(bands.inner), style: StrokeStyle(lineWidth: half))
        case .inset:
            // CSS inset: top/left take the dark band (sunken look),
            // bottom/right stay at the declared colour (Chromium's
            // light band == base). Mirrors Android's INSET branch.
            let dark = side == .top || side == .left
            ctx.stroke(path, with: .color(Self.shade(colour, light: !dark)),
                       style: StrokeStyle(lineWidth: w))
        case .outset:
            // CSS outset is the inverse of inset: top/left keep the
            // declared colour (raised), bottom/right go dark.
            let dark = side == .bottom || side == .right
            ctx.stroke(path, with: .color(Self.shade(colour, light: !dark)),
                       style: StrokeStyle(lineWidth: w))
        default:
            // solid / double<3 → single full-width stroke.
            ctx.stroke(path, with: .color(colour), style: StrokeStyle(lineWidth: w))
        }
    }

    // Render CSS `border-style: dotted` as a row of filled circles along
    // the edge's centreline — port of Android's drawDotted. Geometry
    // mirrors Chromium's dotted painter: dot diameter = border width,
    // first/last dot centres inset w/2 from the edge ends (flush with
    // the adjacent side), remaining dots evenly distributed so the
    // clear gap between neighbours is ≈ one width.
    private func drawDotted(_ ctx: inout GraphicsContext, size: CGSize,
                            side: Side, width w: CGFloat, colour: Color) {
        let len = edgeLength(side: side, size: size)
        // Chromium's integer dot fit — see dottedDotCount.
        let n = Self.dottedDotCount(length: len, width: w)
        guard n > 0 else { return }
        let r = w / 2
        // Distance of the centreline from the box's outer edge.
        let mid = w / 2
        // Even spacing between the two inset end-dot centres; a single
        // dot centres on the edge midpoint (edge shorter than one pitch).
        let step = n > 1 ? (len - w) / CGFloat(n - 1) : 0
        for i in 0..<n {
            // Distance along the edge of this dot's centre.
            let d = n > 1 ? r + step * CGFloat(i) : len / 2
            // Map (along-edge distance, centreline inset) to x/y.
            let centre: CGPoint
            switch side {
            case .top:    centre = CGPoint(x: d, y: mid)
            case .bottom: centre = CGPoint(x: d, y: size.height - mid)
            case .left:   centre = CGPoint(x: mid, y: d)
            case .right:  centre = CGPoint(x: size.width - mid, y: d)
            }
            // Filled circle of diameter w — Canvas fill, not a stroked
            // dash, so no cap geometry can stretch the dot.
            ctx.fill(Path(ellipseIn: CGRect(x: centre.x - r, y: centre.y - r,
                                            width: w, height: w)),
                     with: .color(colour))
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
        // Shared band computation (see AllBordersConfig.bandInsets) —
        // zero insets keep the modifier cost-free when no border is
        // declared, because `.padding(EdgeInsets())` is layout-identity.
        padding(AllBordersConfig.bandInsets(config))
    }
}

extension AllBordersConfig {
    /// The per-edge border BAND thickness as EdgeInsets — the distance
    /// from the border box's outer edge to the padding box (CSS 2.1
    /// §8.1: padding edge = border edge − border width). Factored out
    /// of `engineBorderContentInset` (wave 8, lane IOS paint-order) so
    /// TWO consumers share one truth:
    ///   • the content inset above (content sits inside the band), and
    ///   • ComponentRenderer's absolute-child overlay, which insets the
    ///     positioned-descendant ZStack from the border box down to the
    ///     PADDING box — the containing block css-position-3 §3.1
    ///     assigns to absolutely positioned boxes.
    /// `start`→leading / `end`→trailing keeps the extractor's logical
    /// sides mapped exactly like the content-inset path always did.
    static func bandInsets(_ config: AllBordersConfig?) -> EdgeInsets {
        EdgeInsets(
            // Each edge contributes only when it actually paints a band
            // (`hasBorder` — style ≠ none/hidden AND width > 0), same
            // gate the stroke painter uses, so band and inset agree.
            top:      config?.top.hasBorder    == true ? (config?.top.effectiveWidth ?? 0)    : 0,
            leading:  config?.start.hasBorder  == true ? (config?.start.effectiveWidth ?? 0)  : 0,
            bottom:   config?.bottom.hasBorder == true ? (config?.bottom.effectiveWidth ?? 0) : 0,
            trailing: config?.end.hasBorder    == true ? (config?.end.effectiveWidth ?? 0)    : 0
        )
    }
}

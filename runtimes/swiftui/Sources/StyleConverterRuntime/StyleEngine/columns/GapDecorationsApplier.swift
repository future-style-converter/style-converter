//
//  GapDecorationsApplier.swift
//  StyleEngine/columns — wave 24, lane GAPS-I. The DRAW HOOK.
//
//  GapRuleSegment[] → pixels, plus the plumbing that feeds the pure
//  GapDecorationSegments module real flex-item geometry:
//
//    • GapItemFramesKey  — a PreferenceKey carrying each in-flow child's
//      Anchor<CGRect>. SwiftUI's Layout protocol cannot draw, so the
//      item rects have to travel OUT of the layout as anchors and be
//      resolved back into the container's own coordinate space.
//    • .gapDecorationItemFrame(index:active:) — attached to each flex
//      child by ComponentRenderer, and ONLY when the container really
//      declares gap decorations. Inactive containers attach nothing, so
//      the whole baseline corpus is byte-untouched.
//    • .gapDecorations(config:mainHorizontal:) — attached to the flex
//      container; resolves the anchors, calls the pure segment builder,
//      and overlays the painter. It then RESETS the preference so a
//      nested decorated container can never see its ancestor's items.
//
//  Stroke recipes are the border machinery's, cited per case — a gap
//  rule is a CSS <line-style> line exactly like a border side, and the
//  WPT refs literally draw them with `border-left: 10px double red`
//  (flex-gap-decorations-004-ref) / `border-bottom: 10px dotted blue`
//  (005-ref), so parity with BorderSideApplier IS parity with the ref.
//

import SwiftUI

// MARK: - Item-frame channel

/// Per-child Anchor<CGRect>, keyed by flow index so the segment builder
/// receives items in DOM order (which is what line grouping needs).
struct GapItemFramesKey: PreferenceKey {
    /// No children measured yet.
    static let defaultValue: [Int: Anchor<CGRect>] = [:]
    /// Merge sibling contributions; identical keys cannot collide
    /// because the index is unique within one container.
    static func reduce(value: inout [Int: Anchor<CGRect>],
                       nextValue: () -> [Int: Anchor<CGRect>]) {
        value.merge(nextValue()) { a, _ in a }
    }
}

extension View {
    /// Publish this flex child's bounds for the gap-decorations painter.
    /// `active == false` returns the view UNCHANGED (not merely with an
    /// empty preference) so a non-decorated container pays nothing and
    /// its view tree is identical to the pre-wave-24 shape.
    @ViewBuilder
    func gapDecorationItemFrame(index: Int, active: Bool) -> some View {
        if active {
            // .anchorPreference is a transparent modifier: it reads the
            // resolved bounds and changes neither layout nor paint.
            anchorPreference(key: GapItemFramesKey.self, value: .bounds) {
                [index: $0]
            }
        } else {
            self
        }
    }

    /// Paint this flex container's gap decorations. Nil / inactive config
    /// returns the container unchanged.
    @ViewBuilder
    func gapDecorations(_ config: GapDecorationsConfig?,
                        mainHorizontal: Bool) -> some View {
        if let cfg = config, cfg.isActive {
            modifier(GapDecorationsOverlay(config: cfg, mainHorizontal: mainHorizontal))
        } else {
            self
        }
    }
}

/// Resolves the child anchors and overlays the painter.
struct GapDecorationsOverlay: ViewModifier {
    /// The container's resolved gap-decorations state.
    let config: GapDecorationsConfig
    /// True for flex-direction row/row-reverse.
    let mainHorizontal: Bool

    func body(content: Content) -> some View {
        content
            // overlayPreferenceValue sees exactly the preferences raised
            // by `content` — i.e. this container's own children.
            .overlayPreferenceValue(GapItemFramesKey.self) { anchors in
                GeometryReader { proxy in
                    // proxy's space IS the container's content box: the
                    // outer style chain applies padding/border further
                    // out, so (0,0) here is the CSS content origin —
                    // the space every ref coordinate is quoted in.
                    let frames = anchors.keys.sorted().map { proxy[anchors[$0]!] }
                    GapDecorationsPainter(
                        segments: GapDecorationSegments.build(
                            items: frames,
                            contentSize: proxy.size,
                            mainHorizontal: mainHorizontal,
                            config: config))
                }
                // Decorations are not interactive; never steal a tap.
                .allowsHitTesting(false)
            }
            // Stop the item anchors here. Without this reset a decorated
            // ANCESTOR would also receive these frames and group them
            // into phantom lines.
            .preference(key: GapItemFramesKey.self, value: [:])
    }
}

// MARK: - Painter

/// Draws a pre-computed segment list. Split out of the modifier so the
/// raster pins (GapDecorationRasterTests) can hand it a literal segment
/// table and rasterize it through ImageRenderer with no layout at all.
struct GapDecorationsPainter: View {
    /// Back-to-front paint order — `rule-overlap` is already baked in.
    let segments: [GapRuleSegment]
    /// `currentColor` fallback for a rule whose colour did not resolve.
    var inheritedColor: Color = .primary

    var body: some View {
        Canvas { ctx, _ in
            // Painter's algorithm: the list is already ordered so the
            // rule-overlap winner is last.
            for seg in segments {
                Self.draw(&ctx, seg, inherited: inheritedColor)
            }
        }
    }

    /// One segment. `isRowRule == false` ⇒ a VERTICAL line (thickness on
    /// x); row rules are horizontal. That equivalence is exact for both
    /// container axes — a column rule always runs down a column gap.
    static func draw(_ ctx: inout GraphicsContext,
                     _ seg: GapRuleSegment,
                     inherited: Color) {
        // Degenerate rects carry no ink.
        guard seg.rect.width > 0, seg.rect.height > 0 else { return }
        let colour = seg.color ?? inherited
        let vertical = !seg.isRowRule
        // Thickness = the rule width; length = the run.
        let w = vertical ? seg.rect.width : seg.rect.height
        let len = vertical ? seg.rect.height : seg.rect.width
        switch seg.style {
        // CSS 2.1 §8.5.3 double: three equal bands, outer two inked.
        // Mirrors BorderSideApplier's `case .double where w >= 3`,
        // including its sub-3px degrade to solid (Chromium collapses a
        // sub-pixel triple-split the same way).
        case .double where w >= 3:
            let band = w / 3
            ctx.fill(Path(bandRect(seg.rect, vertical: vertical,
                                   offset: 0, thickness: band)),
                     with: .color(colour))
            ctx.fill(Path(bandRect(seg.rect, vertical: vertical,
                                   offset: w - band, thickness: band)),
                     with: .color(colour))
        // Fitted dash rhythm — BorderSideApplier.fittedDashIntervals is
        // the Chromium-matched per-edge fit (starts AND ends on a full
        // dash); reused verbatim so a gap rule and a border side of the
        // same width dash identically.
        case .dashed:
            ctx.stroke(centreLine(seg.rect, vertical: vertical),
                       with: .color(colour),
                       style: StrokeStyle(lineWidth: w,
                                          dash: BorderSideApplier.fittedDashIntervals(
                                              length: len, width: w)))
        // Spaced circles of diameter w, first/last flush with the ends —
        // count from BorderSideApplier.dottedDotCount (the Chromium
        // round-half-up fit the Android applier also ports).
        case .dotted:
            drawDots(&ctx, seg.rect, vertical: vertical, width: w,
                     length: len, colour: colour)
        // groove/ridge/inset/outset are UA-defined 3D shading. css-gaps
        // inherits the border grammar, but no WPT gap test exercises
        // them; degrade to solid and say so rather than fall through.
        case .groove, .ridge, .inset, .outset:
            _ = PropertyTracker.logOnce(
                key: "gapdec.style3d." + seg.style.rawValue,
                message: "gap rule style \(seg.style.rawValue) → solid (no 3D shading yet)")
            ctx.fill(Path(seg.rect), with: .color(colour))
        // solid, sub-3px double, and none/hidden (already filtered by
        // GapRuleSpec.paints) all land on one filled band.
        default:
            ctx.fill(Path(seg.rect), with: .color(colour))
        }
    }

    /// A `thickness`-wide slice `offset` into the rule's thickness axis.
    private static func bandRect(_ r: CGRect, vertical: Bool,
                                 offset: CGFloat, thickness: CGFloat) -> CGRect {
        // Vertical rules slice along x, horizontal rules along y.
        vertical
            ? CGRect(x: r.minX + offset, y: r.minY, width: thickness, height: r.height)
            : CGRect(x: r.minX, y: r.minY + offset, width: r.width, height: thickness)
    }

    /// The rule's centreline, the path dashes are stroked along.
    private static func centreLine(_ r: CGRect, vertical: Bool) -> Path {
        Path { p in
            // Stroking a centreline with lineWidth == thickness paints
            // exactly the rule's rect (same trick as BorderSideApplier's
            // edgeLine(inset: w/2)).
            if vertical {
                p.move(to: CGPoint(x: r.midX, y: r.minY))
                p.addLine(to: CGPoint(x: r.midX, y: r.maxY))
            } else {
                p.move(to: CGPoint(x: r.minX, y: r.midY))
                p.addLine(to: CGPoint(x: r.maxX, y: r.midY))
            }
        }
    }

    /// Dotted: n circles of diameter `width`, centres evenly spaced with
    /// the first and last flush against the run's ends.
    private static func drawDots(_ ctx: inout GraphicsContext, _ r: CGRect,
                                 vertical: Bool, width w: CGFloat,
                                 length len: CGFloat, colour: Color) {
        let n = BorderSideApplier.dottedDotCount(length: len, width: w)
        // Zero dots = degenerate input; nothing to paint.
        guard n > 0 else { return }
        // Centre-to-centre pitch that puts the end dots flush.
        let pitch = n > 1 ? (len - w) / CGFloat(n - 1) : 0
        for i in 0..<n {
            // Distance of this dot's leading edge from the run's start.
            let at = CGFloat(i) * pitch
            let dot = vertical
                ? CGRect(x: r.minX, y: r.minY + at, width: w, height: w)
                : CGRect(x: r.minX + at, y: r.minY, width: w, height: w)
            ctx.fill(Path(ellipseIn: dot), with: .color(colour))
        }
    }
}

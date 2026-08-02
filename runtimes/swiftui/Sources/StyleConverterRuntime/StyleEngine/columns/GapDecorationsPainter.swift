//
//  GapDecorationsPainter.swift
//  StyleEngine/columns — wave 24 lane GAPS-I, split out at wave 25.
//
//  The INK half of the gap-decorations family: a GapRuleSegment[] (built
//  by the pure GapDecorationSegments module) → pixels. The other half —
//  the item-frame PreferenceKey and the container modifier that resolves
//  those anchors — stays in GapDecorationsApplier.swift; wave 25's
//  CAL-RC3 surface fix pushed the combined file past the 300-line split
//  threshold, and the seam between "collect geometry" and "stroke it" is
//  where the two halves were already independently testable (the raster
//  pins construct this view directly, with no layout at all).
//
//  Stroke recipes are the border machinery's, cited per case — a gap
//  rule is a CSS <line-style> line exactly like a border side, and the
//  WPT refs literally draw them with `border-left: 10px double red`
//  (flex-gap-decorations-004-ref) / `border-bottom: 10px dotted blue`
//  (005-ref), so parity with BorderSideApplier IS parity with the ref.
//

import SwiftUI

/// Draws a pre-computed segment list. Split out of the modifier so the
/// raster pins (GapDecorationRasterTests) can hand it a literal segment
/// table and rasterize it through ImageRenderer with no layout at all.
struct GapDecorationsPainter: View {
    /// Back-to-front paint order — `rule-overlap` is already baked in.
    let segments: [GapRuleSegment]
    /// `currentColor` fallback for a rule whose colour did not resolve.
    var inheritedColor: Color = .primary

    var body: some View {
        // WAVE 25 (CAL-RC3) — THE PAINT SURFACE IS THE SEGMENTS' UNION,
        // NOT THE CONTENT BOX. The wave-24 painter was a GREEDY Canvas
        // inside GapDecorationsOverlay's GeometryReader, so its surface
        // was exactly the container's CONTENT box, and a segment outside
        // that box was at the mercy of SwiftUI's rasterization bounds.
        //
        // MEASURED, both directions, through ImageRenderer at scale 1:
        //   • flex-gap-decorations-008 — a `width: 200px` NOWRAP
        //     container with six 50px `flex-shrink: 0` items overflows
        //     to 350px, so the five column rules sit at content-x 50,
        //     110, 170, 230 and 290 (the ref draws them at page x 52 …
        //     292, i.e. +2 for the border). Scanning the raster of the
        //     old painter finds ink at x 50–59 / 110–119 / 170–179 and
        //     NOTHING beyond: rules 4 and 5, 30 and 90 points outside
        //     the box, were erased. Two of five rules simply vanished.
        //   • flex-gap-decorations-011 — `column-rule-inset: -2px` grows
        //     each 50px run to 54px, so the outer runs reach 2px past
        //     the box at each end. Those 2px SURVIVED the old painter
        //     (end-to-end probe through ComponentRenderer: ink at stage
        //     y 18–71 / 78–131 for a content box at y 20–130).
        // So the erasure is not a clean clip — it is a rasterization
        // margin of a few points around the Canvas's frame, undocumented
        // and not ours to rely on in either direction.
        //
        // Sizing the Canvas to the union and offsetting it back removes
        // the dependency entirely: the surface is UNDER every segment,
        // however far out the segment landed. CSS agrees — a gap
        // decoration is painted in the container's own paint phase and
        // is clipped only by `overflow`, never by the content box.
        let stage = Self.paintBounds(segments)
        Canvas { ctx, _ in
            // Painter's algorithm: the list is already ordered so the
            // rule-overlap winner is last.
            for seg in segments {
                // Translate into the stage's local space — the stage's
                // origin is the union's, not (0,0), so every rect moves
                // by exactly −origin and relative geometry is preserved.
                var local = seg
                local.rect = seg.rect.offsetBy(dx: -stage.minX, dy: -stage.minY)
                Self.draw(&ctx, local, inherited: inheritedColor)
            }
        }
        // The Canvas is now exactly the union — a fixed-size view, so a
        // parent (GeometryReader / the raster pins' `.frame(…,
        // alignment: .topLeading)`) places it at the origin…
        .frame(width: stage.width, height: stage.height)
        // …and `.offset` — a PAINT-time transform that leaves the layout
        // size alone — slides it back onto the segments' own coordinates.
        .offset(x: stage.minX, y: stage.minY)
    }

    /// The union bounding box of every ink-bearing segment, snapped
    /// OUTWARD to whole points.
    ///
    /// `.integral` matters: a fractional origin would make the translate
    /// above land every rule edge on a fractional coordinate and resample
    /// its antialiasing, so identical geometry would rasterize
    /// differently depending on where the union happened to start.
    /// Integral origins keep the ink byte-identical to the pre-wave-25
    /// painter for every segment that was already inside the content box.
    static func paintBounds(_ segments: [GapRuleSegment]) -> CGRect {
        // `.null` is the identity for union (unlike `.zero`, which would
        // drag the box back to the origin and re-introduce the clip).
        var union = CGRect.null
        for seg in segments where seg.rect.width > 0 && seg.rect.height > 0 {
            union = union.union(seg.rect)
        }
        // No ink at all → an empty stage; the Canvas draws nothing.
        return union.isNull ? .zero : union.integral
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
    ///
    /// WAVE 25 (CAL-RC8) — MEASURED, NOT ASSUMED. The round for this wave
    /// carried a correction vector claiming Chrome rasterizes the dot of
    /// a 10px dotted rule at ~9.5px with a phase offset. It does not.
    /// Rendering the 005 ref's own declarations (`border-left: 10px
    /// dotted red` on a 50px run, `border-bottom: 10px dotted blue` on a
    /// 170px run) in the capture-browser-ref recipe's Chromium at scale 1
    /// and integrating the coverage of each dot gives:
    ///   • DIAMETER — dot ink 77.0px², identical to a control
    ///     `border-radius: 50%` 10×10 box measured with the same
    ///     estimator (77.0px²; the −1.9% vs π·5² is the estimator's own
    ///     antialiasing bias, which cancels). At 6px and 4px rules the
    ///     dot/control pair agrees the same way. The dot is a FULL
    ///     diameter-w circle — there is no 9.5px shrink to match.
    ///   • FIRST DOT — centre at run start + w/2 for every length from
    ///     15px to 200px, i.e. flush with the start. That is what the
    ///     `at = 0` term below already does.
    ///   • COUNT — 1/2/2/2/3/3/3/3/4/4/5/5/6/6/7/7/8/8/9/9/10/10/11 dots
    ///     for lengths 15…200 at w = 10, which is exactly
    ///     `BorderSideApplier.dottedDotCount`, every single length.
    ///   • PITCH — the ONE real difference, and it is sub-pixel: Blink's
    ///     dotted dash period is `2w − kEpsilon` (kEpsilon = 0.01), so
    ///     Chrome's measured pitch is the ideal `(len − w)/(n − 1)` minus
    ///     0.0096 and its LAST dot falls short of the run end by
    ///     (n − 1)·0.0096 — 0.083px at the 005 band (len 170, n 9). We
    ///     keep the exact fit (last dot flush) rather than port the
    ///     epsilon: 0.08px is an order of magnitude under one AA step,
    ///     and porting it would perturb every committed dotted-border
    ///     baseline byte for no visible gain. Pinned, with the measured
    ///     numbers, in GapDecorationRasterTests.
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

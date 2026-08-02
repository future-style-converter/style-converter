//
//  GapDecorationGeometry.swift
//  StyleEngine/columns — wave 24, lane GAPS-I. PURE, view-free.
//
//  The 1-D interval algebra every gap-decoration rect is built from.
//  Byte-parallel with the Compose twin
//  (runtimes/compose/.../columns/GapDecorationGeometry.kt): same type
//  and function names (GapInterval → GapSpan here only because `Gap
//  Interval` collides with nothing but reads worse in Swift's CGFloat
//  world; the member names union/subtract/inset/band/rect are identical).
//
//  Everything is in ONE coordinate space: the flex container's CONTENT
//  box, origin top-left. The draw hook resolves item bounds into that
//  space; this file does no unit work.
//

import CoreGraphics

/// A closed interval on one axis, in points. (Kotlin twin: GapInterval.)
struct GapSpan: Equatable {
    /// Lower edge.
    var start: CGFloat
    /// Upper edge.
    var end: CGFloat
    /// Only non-degenerate intervals can carry ink.
    var isPositive: Bool { end > start }
}

/// The interval algebra. (Kotlin twin: object GapIntervals.)
enum GapIntervals {

    /// Merge overlapping/touching intervals into a sorted disjoint set —
    /// used to build the INTERSECTION cut list out of two neighbouring
    /// lines' gap bands, which routinely overlap (ref 009's 102–112 and
    /// 112–122 fuse into one 102–122 cut).
    static func union(_ intervals: [GapSpan]) -> [GapSpan] {
        // Nothing to merge.
        guard !intervals.isEmpty else { return [] }
        // Sweep left to right so a single pass suffices.
        let sorted = intervals.filter(\.isPositive).sorted { $0.start < $1.start }
        var out: [GapSpan] = []
        for iv in sorted {
            // Extend the open interval when this one touches or overlaps.
            if let last = out.last, iv.start <= last.end {
                out[out.count - 1].end = max(last.end, iv.end)
            } else {
                out.append(iv)
            }
        }
        return out
    }

    /// `base` minus every cut — 0…n surviving pieces, left to right.
    static func subtract(base: GapSpan, cuts: [GapSpan]) -> [GapSpan] {
        // Start with the whole base and carve each cut out in order.
        var pieces = [base]
        for cut in union(cuts) {
            pieces = pieces.flatMap { piece -> [GapSpan] in
                // Disjoint → survives untouched.
                guard cut.end > piece.start, cut.start < piece.end else { return [piece] }
                var kept: [GapSpan] = []
                // Head piece before the cut.
                if cut.start > piece.start {
                    kept.append(GapSpan(start: piece.start, end: cut.start))
                }
                // Tail piece after the cut.
                if cut.end < piece.end {
                    kept.append(GapSpan(start: cut.end, end: piece.end))
                }
                return kept
            }
        }
        return pieces.filter(\.isPositive)
    }

    /// Move both ends inward by `insetPx` — NEGATIVE EXTENDS (ref 011's
    /// `column-rule-inset: -2px` grows a 50px run to 54px). Returns nil
    /// when a positive inset collapses the run to nothing.
    static func inset(_ iv: GapSpan, insetPx: CGFloat) -> GapSpan? {
        // Both ends move toward the middle by the same amount.
        let out = GapSpan(start: iv.start + insetPx, end: iv.end - insetPx)
        // A collapsed run paints nothing at all.
        return out.isPositive ? out : nil
    }

    /// The `widthPx`-wide rule band CENTRED in its gap (css-gaps-1
    /// inherits css-multicol-1 §5.2's centred rule). Ref 011 pins it: a
    /// 2px rule in the 52–62 band lands at 56–58.
    static func band(gap: GapSpan, widthPx: CGFloat) -> GapSpan {
        // Gap midpoint, then half the rule width on each side.
        let mid = (gap.start + gap.end) / 2
        return GapSpan(start: mid - widthPx / 2, end: mid + widthPx / 2)
    }

    /// Combine an x-interval and a y-interval into a rect.
    static func rect(horizontal: GapSpan, vertical: GapSpan) -> CGRect {
        CGRect(x: horizontal.start, y: vertical.start,
               width: horizontal.end - horizontal.start,
               height: vertical.end - vertical.start)
    }
}

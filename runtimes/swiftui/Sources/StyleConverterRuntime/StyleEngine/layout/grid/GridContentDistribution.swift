//
//  GridContentDistribution.swift
//  StyleEngine/layout/grid — wave 19 (lane GRID-DISTRIBUTION).
//
//  Content-distribution of the grid TRACK GROUP inside the grid container's
//  content box (css-align-3 §5.3 justify-content on grid tracks) +
//  direction:rtl column mirroring (css-grid-1 §7.1 / css-writing-modes: in
//  RTL the inline START edge is the RIGHT content edge, so column 1 sits
//  rightmost and the whole group packs toward the right). Before this wave
//  both natives pinned the track group at the LEFT content edge
//  unconditionally, so justify-content:end/center and direction:rtl grids
//  (wpt css-grid descendant-static-position-002/003/004) never shifted —
//  in-flow items AND the abspos static-position rects anchored inside them
//  stayed at the content-box start.
//
//  SHARED-SEMANTICS CONTRACT: this enum is the byte-parallel twin of the
//  Compose GridContentDistribution.kt — same function name, same argument
//  list, same math, and the SAME pin table
//  (GridContentDistributionTests.swift ↔ GridContentDistributionTest.kt)
//  so skeptic probes can diff them.
//

// Only Double math lives here — no SwiftUI/CoreGraphics dependency, so the
// function stays a pure, platform-free twin of the Kotlin object.
enum GridContentDistribution {

    /// The container justify-content domain the distribution math consumes.
    /// Deliberately its OWN enum (not AlignmentKeyword, not the Compose
    /// JustifyContent) so the twin function bodies stay literally identical
    /// across platforms; each platform adapts its container keyword domain
    /// via `justify(of:)` below / `justifyOf` on Compose.
    enum Justify {
        case start, end, center, spaceBetween, spaceAround, spaceEvenly
    }

    /// Physical origin (LEFT edge, px) of every column track, in LOGICAL
    /// track order (index 0 = grid column 1), measured from the content-box
    /// LEFT edge.
    ///
    ///  - `trackWidths`  resolved track widths in logical order (the output
    ///    of GridTrackMath.columnWidths — sizing runs FIRST, distribution
    ///    only moves the already-sized group, css-align-3 §5.3).
    ///  - `gap`          the used column-gap between adjacent tracks.
    ///  - `contentExtent` the grid content-box inline size the group aligns
    ///    within. For an indefinite (fit-content) grid the caller passes the
    ///    track footprint itself, making leftover 0 → distribution a no-op
    ///    and RTL a pure order mirror inside the hugged box.
    ///  - `justify`      the container's justify-content (adapted keyword).
    ///  - `rtl`          direction:rtl on the container — flips which
    ///    physical edge is the inline start AND reverses the physical column
    ///    order (logical positions are computed start-relative, then
    ///    mirrored).
    ///
    /// Overflow (leftover < 0) follows css-align-3 §5.3 default (unsafe)
    /// semantics: end/center simply overflow the start/both side(s), and the
    /// <content-distribution> fallbacks apply — space-between falls back to
    /// start, space-around/space-evenly fall back to center.
    static func trackOrigins(
        trackWidths: [Double],
        gap: Double,
        contentExtent: Double,
        justify: Justify,
        rtl: Bool
    ) -> [Double] {
        // Track count; an empty template distributes nothing.
        let n = trackWidths.count
        if n == 0 { return [] }
        // The group's footprint: track sum + the n-1 inner gaps (css-align-3
        // §3: gaps are part of the content to be distributed around).
        let total = trackWidths.reduce(0, +) + gap * Double(n - 1)
        // Free space in the alignment container (may be negative = overflow).
        let leftover = contentExtent - total
        // §5.3 keyword table → (leading offset from the inline-start edge,
        // spacing BETWEEN adjacent tracks). Distribution keywords widen the
        // between-track spacing on top of the used gap.
        let lead: Double
        let between: Double
        switch justify {
        // start (and the normal/stretch fallback the adapters fold here):
        // group flush at the inline-start edge, plain gaps.
        case .start:  lead = 0.0; between = gap
        // end: group flush at the inline-end edge — all leftover leads.
        case .end:    lead = leftover; between = gap
        // center: leftover split evenly on both sides (overflows both
        // sides equally when negative — §5.3 unsafe default).
        case .center: lead = leftover / 2.0; between = gap
        // space-between: first/last flush, leftover split into the n-1
        // inner gaps; <2 tracks or overflow → the §5.3 start fallback.
        case .spaceBetween:
            if leftover > 0.0 && n > 1 { lead = 0.0; between = gap + leftover / Double(n - 1) }
            else { lead = 0.0; between = gap }
        // space-around: each track gets equal space around it — half a
        // share outside the edges, full shares between; overflow → the
        // §5.3 center fallback.
        case .spaceAround:
            if leftover > 0.0 { lead = leftover / (2.0 * Double(n)); between = gap + leftover / Double(n) }
            else { lead = leftover / 2.0; between = gap }
        // space-evenly: n+1 equal shares (edges + between); overflow →
        // the §5.3 center fallback.
        case .spaceEvenly:
            if leftover > 0.0 { lead = leftover / Double(n + 1); between = gap + leftover / Double(n + 1) }
            else { lead = leftover / 2.0; between = gap }
        }
        // Logical positions: cumulative offsets from the INLINE-START edge
        // (left in LTR, right in RTL), lead first, then width+between steps.
        var p = lead
        let logical: [Double] = trackWidths.map { w in
            // Capture this track's start-relative offset, advance the cursor.
            let cur = p; p += w + between; return cur
        }
        // LTR: inline start IS the left edge — logical offsets are physical.
        if !rtl { return logical }
        // RTL: mirror each track about the content box — a track whose
        // start-relative offset is p with width w has its LEFT edge at
        // extent − p − w (this reverses physical column order AND lets an
        // overflowing group spill past the LEFT edge, exactly like Chrome
        // renders descendant-static-position-002's 40px track in a 20px box).
        return logical.enumerated().map { i, pos in contentExtent - pos - trackWidths[i] }
    }

    /// Adapter: the aggregate's container-level AlignmentKeyword (the wire
    /// value FlexboxExtractor parses off "JustifyContent") → the twin math's
    /// `Justify`. start/left folds arrive as `.start`, end/right as `.end`
    /// (ValueExtractors fold, mirroring extractDisplayConfig on Compose);
    /// everything without a distribution meaning for a fixed track group —
    /// nil / normal / stretch / auto / baseline / self-* — behaves as start
    /// per css-align-3 §5.3 (`normal` → `stretch`, which distributes nothing
    /// over non-auto tracks).
    static func justify(of keyword: AlignmentKeyword?) -> Justify {
        switch keyword {
        // end-family → group at inline end.
        case .end, .selfEnd:       return .end
        // the lone center keyword → group centered.
        case .center:              return .center
        // the three distribution keywords map 1:1.
        case .spaceBetween:        return .spaceBetween
        case .spaceAround:         return .spaceAround
        case .spaceEvenly:         return .spaceEvenly
        // start-family + no-claim keywords + absent property → start.
        default:                   return .start
        }
    }
}

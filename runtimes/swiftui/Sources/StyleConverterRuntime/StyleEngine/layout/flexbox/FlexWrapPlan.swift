//
//  FlexWrapPlan.swift
//  StyleEngine/layout/flexbox — the PURE half of the wrapping flex path.
//
//  Wave 25, lane ISTRETCH (CAL-RC5, round 3). Byte-parallel twin of
//  Compose's `layout/flexbox/FlexWrapLines.kt`: css-flexbox-1 §9.3
//  ("collect flex items into flex lines") plus §9.4 step 8 / §9.6
//  (`align-content: stretch` grows the lines into a DEFINITE container
//  cross size). Pure geometry — no SwiftUI types — so XCTest pins the
//  arithmetic directly instead of through a rasterized composition.
//
//  TWO CONSUMERS, ONE RULE SET. That is the whole point of this file:
//   • `FlowLayout` runs the plan at MEASURE time over the subviews'
//     measured sizes — it positions the lines.
//   • `ComponentRenderer.flexWrapStretchPlan` runs the identical plan at
//     BUILD time over statically-knowable IR facts — it decides the
//     forced cross size a stretch item's own paint chain must adopt.
//  If those two disagreed the item would paint at one size and be placed
//  at another, so both call THESE functions; neither reimplements them.
//
//  WHY THE BUILD-TIME RUN EXISTS AT ALL (the round-2 skeptic finding).
//  SwiftUI's `place(at:proposal:)` is a PROPOSAL: a subview is free to
//  ignore it, and an IR child does — it answers `sizeThatFits` with its
//  intrinsic cross size for every proposal, `.infinity` included (the
//  same inelasticity the grid and column-flex paths already documented
//  via `gridStretchHeight` / `flexStretchWidth`). Compose has no such
//  problem: `FlexWrapRow` measures its stretch items with a FIXED cross
//  band, `Constraints(0, main, lineCross, lineCross)`. To be honest to
//  that behaviour on iOS the size has to be folded into the child's own
//  SizeConfig BEFORE its style chain builds — i.e. computed here, at
//  build time, from facts the IR already carries.
//

import CoreGraphics

/// Pure line-collection + line-stretch arithmetic for `flex-wrap: wrap`.
enum FlexWrapPlan {

    /// One flex line as an INCLUSIVE index range into the item list —
    /// the same shape as Compose `FlexWrapLines.Line`.
    struct Line: Equatable {
        /// First item index on the line.
        let first: Int
        /// Last item index on the line (inclusive).
        let last: Int
        /// Item count — the gap arithmetic needs it on both axes.
        var count: Int { last - first + 1 }
    }

    /// §9.3 — greedy line collection along the main axis.
    ///
    /// - Parameters:
    ///   - mainSizes: each item's hypothetical main size, in points.
    ///   - containerMain: the line's main-axis budget. Pass `.infinity`
    ///     for an unbounded container: nothing can overflow, so nothing
    ///     wraps and every item lands on one line.
    ///   - gap: main-axis gap (`column-gap` for a row container) — it
    ///     counts against the budget exactly like item size does
    ///     (css-align-3 §8.1: gaps participate in line breaking).
    /// - Returns: lines in document order; empty only for no items.
    ///
    /// §9.3 requires at least ONE item per line even when that item alone
    /// overflows, so the fit test is skipped for a line's first item —
    /// otherwise a single oversized child would never terminate.
    static func breakLines(mainSizes: [CGFloat],
                           containerMain: CGFloat,
                           gap: CGFloat) -> [Line] {
        if mainSizes.isEmpty { return [] }
        var lines: [Line] = []
        // Index of the item that opened the line under construction.
        var first = 0
        // Running main-axis extent of that line.
        var used: CGFloat = 0
        for i in mainSizes.indices {
            // Cost of appending item i: its size, plus a gap unless it
            // is the line's first item.
            let add = mainSizes[i] + (i == first ? 0 : gap)
            if i > first, used + add > containerMain {
                // Doesn't fit → close the line; the next line's first
                // item is accepted unconditionally.
                lines.append(Line(first: first, last: i - 1))
                first = i
                used = mainSizes[i]
            } else {
                used += add
            }
        }
        lines.append(Line(first: first, last: mainSizes.count - 1))
        return lines
    }

    /// css-align-3 §5.3 — does `align-content` DISTRIBUTE the leftover
    /// cross space to the lines, or merely POSITION the line block?
    ///
    /// Only `normal` / `stretch` (and the `auto` spelling the extractor
    /// can produce) grow the lines. Every other keyword — `center`,
    /// `flex-start`, `space-between`, … — leaves the leftover as free
    /// space, so growing the lines under those would place them at
    /// coordinates no browser produces. Compose gates the identical step
    /// on the identical predicate (`alignContentStretches`).
    static func alignContentStretches(_ keyword: AlignmentKeyword?) -> Bool {
        switch keyword {
        case nil, .some(.normal), .some(.stretch), .some(.auto): return true
        default: return false
        }
    }

    /// The §9.4-step-8 PRECONDITION, factored out so both consumers ask
    /// the same question: step 8 fires only when the container's cross
    /// size is DEFINITE *and* `align-content` stretches.
    ///
    /// - Returns: the cross size to distribute into, or nil for the
    ///   "lines hug their content" case [stretchLines] returns intact.
    ///   A non-finite cross (an unbounded proposal) is never definite.
    static func definiteCross(alignContentStretches: Bool,
                              hasDefiniteCross: Bool,
                              cross: CGFloat?) -> CGFloat? {
        guard alignContentStretches, hasDefiniteCross,
              let c = cross, c.isFinite else { return nil }
        return c
    }

    /// §9.4 step 8 / §9.6 — `align-content: stretch` (the flex default
    /// via `normal`): when the container's cross size is definite and the
    /// lines leave free space, every line grows by an EQUAL share.
    ///
    /// - Parameters:
    ///   - base: per-line cross size, each the max hypothetical cross of
    ///     its items (§9.4 step 7).
    ///   - containerCross: the definite cross CONTENT size, or nil when
    ///     the container hugs its lines — the spec's "align-content has
    ///     no effect" case, returned unchanged.
    ///   - gap: cross-axis gap (`row-gap` for a row container).
    /// - Returns: per-line cross size after distribution. Never shrinks a
    ///   line: a negative leftover means the lines overflow, which CSS
    ///   permits (the container clips or spills, it does not compress).
    ///
    /// Compose splits the leftover as integers and hands the remainder to
    /// the leading lines, because Compose measures in whole pixels and a
    /// fractional split would leave a 1px seam for the gap-decoration
    /// painter. SwiftUI lays out in POINTS (CGFloat), so the exact
    /// division below is the same rule without the remainder step — the
    /// lines still sum exactly to the container's cross size.
    static func stretchLines(base: [CGFloat],
                             containerCross: CGFloat?,
                             gap: CGFloat) -> [CGFloat] {
        guard let avail = containerCross, !base.isEmpty else { return base }
        let gaps = gap * CGFloat(base.count - 1)
        let leftover = avail - base.reduce(0, +) - gaps
        guard leftover > 0 else { return base }
        let share = leftover / CGFloat(base.count)
        return base.map { $0 + share }
    }

    /// The BUILD-TIME hypothetical cross size of one flex item, or nil
    /// when it is not statically knowable — in which case the caller must
    /// abandon the whole plan rather than guess (no silent fallthrough:
    /// a wrong guess would paint an item at a size the Layout never
    /// places it at). `FlowLayout` needs no such estimate; it measures.
    ///
    /// - Parameters:
    ///   - explicitCrossPx: the item's declared cross size, already
    ///     resolved to points, or nil for an auto cross size.
    ///   - clampedCross: true when the item declares a min/max on the
    ///     cross axis — the clamp interacts with content measurement, so
    ///     the static estimate refuses it.
    ///   - isEmptyLeaf: true when the item has neither children nor text,
    ///     i.e. its content contributes NO cross size at all.
    ///   - hasCrossBands: true when the item declares padding, a border
    ///     or a margin — padding/border inflate the frame by an amount
    ///     that depends on the effective `box-sizing`, and a margin makes
    ///     the OUTER cross size §9.4 measures differ from the box size
    ///     the caller would inject. The estimate refuses all three.
    ///   - emptyFloorPx: the harness's synthetic minimum box height
    ///     (`StyleBuilder.minFloor` → 30 on the product/baseline path, 0
    ///     under WPT capture where MinBoxFloor is dropped).
    static func hypotheticalCross(explicitCrossPx: CGFloat?,
                                  clampedCross: Bool,
                                  isEmptyLeaf: Bool,
                                  hasCrossBands: Bool,
                                  emptyFloorPx: CGFloat) -> CGFloat? {
        // Padding/border on the item: frame extent depends on box-sizing
        // resolution the renderer does later — refuse rather than guess.
        if hasCrossBands { return nil }
        // A declared cross size IS the frame extent (border-box harness).
        if let px = explicitCrossPx { return px }
        // A min/max clamp without a definite size still needs the
        // content measurement this pass cannot do.
        if clampedCross { return nil }
        // Auto cross size: knowable only for a box with no content.
        return isEmptyLeaf ? emptyFloorPx : nil
    }
}

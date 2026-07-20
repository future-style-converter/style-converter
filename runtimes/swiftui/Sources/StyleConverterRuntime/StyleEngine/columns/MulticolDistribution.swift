//
//  MulticolDistribution.swift
//  StyleEngine/columns — lane ios-multichild-multicol.
//
//  Pure greedy multi-child column distribution: the iOS MIRROR of the
//  Android runtime's MultiColumnDistribution.kt (extracted from Compose's
//  MultiColumnDistributionLayout measure pass in the same lane). The
//  cross-platform gate is ANDROID PARITY, not css-multicol-1 §7.1's true
//  column balancing — Android approximates balancing with an
//  order-dependent greedy min-height heuristic, so this file pins the
//  identical algorithm and MulticolDistributionTests carries the same
//  D1–D7 pin table as the Android JVM MultiColumnDistributionTest.
//

// Foundation only for basic numerics — the function is view-free so the
// XCTest suite pins it without a render surface (MulticolMath pattern).
import Foundation

// Namespacing enum — all members static + pure (same pattern as
// MulticolMath / FragmentGeometry in this folder).
enum MulticolDistribution {

    /// One child's resolved placement: the used-column index it landed in
    /// and its block offset from that column's top. The inline (x)
    /// position is NOT part of the distribution — it derives from the
    /// used column geometry (columnIndex · (width + gap)) at the layout
    /// site, exactly as Android's placement loop computes it.
    struct Slot: Equatable {
        /// 0-based used-column index the child was assigned to.
        let columnIndex: Int
        /// Block offset from the column top — the column's height BEFORE this child.
        let yOffsetPx: Double
    }

    /// Distribute children (given by their measured block-sizes, in child
    /// order) into `columnCount` columns per Android's greedy rule:
    ///
    ///  - children are visited strictly in input (child) order;
    ///  - each child lands in the column with the smallest running height;
    ///  - ties break to the LOWEST column index (Kotlin `minByOrNull`
    ///    returns the first minimum — D3 pins the tie-break on both
    ///    platforms because it is part of the parity contract);
    ///  - children stack flush inside a column (no inter-item gap);
    ///  - heights are Double here (SwiftUI geometry is CGFloat-based)
    ///    where Android uses Int px — the shared pins use integral
    ///    values so the answers are bit-identical across the pair.
    ///
    /// - Parameters:
    ///   - childHeightsPx: each child's measured height in px, in the
    ///     exact order the layout received its subviews (order matters —
    ///     the heuristic is order-dependent by design, D4).
    ///   - columnCount: the USED column count (MulticolMath guarantees
    ///     >= 1 at the layout site; coerced defensively like Android so
    ///     a degenerate direct call can never index an empty array).
    /// - Returns: one Slot per child, index-aligned with the input.
    static func distribute(childHeightsPx: [Double], columnCount: Int) -> [Slot] {
        // Defensive floor — mirrors the Kotlin `maxOf(1, columnCount)`.
        let count = max(1, columnCount)
        // Running column heights — all columns start empty (height 0).
        var columnHeights = [Double](repeating: 0, count: count)
        // The per-child answer, built in child order.
        var slots: [Slot] = []
        // One slot per child — reserve to avoid growth churn.
        slots.reserveCapacity(childHeightsPx.count)
        // Visit children strictly in input order — the greedy choice for
        // child i depends on where children 0..<i landed.
        for height in childHeightsPx {
            // The column choice rule: smallest running height wins. The
            // STRICT `<` keeps the first (lowest) index on ties — the
            // exact behaviour of Kotlin's minByOrNull first-minimum.
            var target = 0
            // Scan the remaining columns for a strictly smaller height.
            for i in 1..<count where columnHeights[i] < columnHeights[target] {
                // A strictly shorter column displaces the current pick.
                target = i
            }
            // The child's block offset is the column height BEFORE it —
            // flush stacking, identical to Android's cumulative offsets.
            slots.append(Slot(columnIndex: target, yOffsetPx: columnHeights[target]))
            // Grow the chosen column by this child for the next pick.
            columnHeights[target] += height
        }
        // Index-aligned with the input by construction.
        return slots
    }

    /// The container's block-size for a distribution: the bottom edge of
    /// the lowest-reaching child, i.e. the tallest column — Android's
    /// `containerBlockSizePx` twin (empty columns contribute 0 and can
    /// never exceed an occupied one; no children → 0).
    ///
    /// - Parameters:
    ///   - childHeightsPx: the same heights `distribute` consumed.
    ///   - slots: the assignment `distribute` returned for them.
    static func containerBlockSizePx(childHeightsPx: [Double], slots: [Slot]) -> Double {
        // Max over every child's bottom edge (offset + own height).
        zip(slots, childHeightsPx).map { $0.yOffsetPx + $1 }.max() ?? 0
    }
}

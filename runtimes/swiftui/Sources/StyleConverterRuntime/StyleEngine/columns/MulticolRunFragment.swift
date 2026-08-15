//
//  MulticolRunFragment.swift
//  StyleEngine/columns — wave-42 lane W4.
//
//  Pure multi-child block-run fragment geometry: the iOS TWIN of the
//  Android runtime's MulticolRunFragment.kt. The shared R pin table lives
//  in MulticolRunFragmentTests.swift and the Android
//  MulticolRunFragmentTest.kt with the SAME rows (native-pair parity gate).
//
//  The model: a definite-height `column-fill: auto` multicol container's
//  plain flow children are ONE continuous block-axis run (each child
//  stacked at the cumulative height of its predecessors, CSS 2.1 §9.4.1),
//  and that run fills column boxes SEQUENTIALLY to H (css-multicol-1
//  §7.2) — the whole run fragments exactly like the wave-10 sole-child
//  case with C = T, so the same clip+translate pass draws it.
//
//  `column-fill: auto` ONLY, by design: under the `balance` initial value
//  Chromium distributes at the best BREAKPOINTS (class A child edges /
//  class B line edges), which the whole-child greedy distribution already
//  approximates on the corpus's green multi-child cells (css-break
//  block-in-inline-009/013/014, css-multicol baseline-001/002) — raw
//  ceil(T/N) slicing would re-introduce the mid-line tearing wave-42
//  removes elsewhere.
//
//  CONSUMPTION STATUS (iOS): pure module + pins only. iOS fragments via
//  renderer-composed clone rows (multicolFragmentRow), and a multi-child
//  clone row is a ComponentRenderer seam (core — out of this lane's
//  ownership); the seam design is in the wave-42 report. On Android the
//  Kotlin twin IS consumed (MulticolRunFragmentMeasure), so landing this
//  twin keeps the R table native-pair-pinned.
//

// CoreGraphics for the Fragment shapes; Foundation for max().
import CoreGraphics
import Foundation

// Namespacing enum — all members static + pure (MulticolMath pattern).
enum MulticolRunFragment {

    /// The run answer: where each child sits in the strip + the slices.
    struct RunPlan: Equatable {
        /// Block offset of each child in the CONTINUOUS run (column-0 /
        /// strip coordinates), index-aligned with the input heights.
        /// Usually the flush cumulative stack, except where a MONOLITHIC
        /// child was pushed past a column boundary (see runPlan's
        /// `monolithic` argument), which leaves a deliberate gap — the same
        /// empty column tail Chromium leaves when a box refuses to split.
        let yOffsetsPx: [Double]
        /// Total run block-size T (the slice source's C) — the sum of the
        /// clamped heights PLUS any monolithic-push gaps.
        let totalBlockSizePx: Double
        /// The css-break-3 §4 fragments over the whole run: empty when the
        /// run FITS one column (T ≤ H — §7.2 sequential fill keeps all
        /// content in column 0), else the FragmentGeometry slices, C = T.
        let fragments: [FragmentGeometry.Fragment]
    }

    /// Build the run plan for stacked child heights inside a definite-
    /// height fill:auto multicol container.
    ///
    /// - Parameters:
    ///   - childHeightsPx: measured child block-sizes at column width, in
    ///     child order (negative floored to 0 — boxes are never negative).
    ///   - columnBlockSize: H — the definite column block-size.
    ///   - columnWidth: W — the §3.4 used column width.
    ///   - gapPx: G — the used column-gap.
    ///   - columnCount: N — the §3.4 used column count (overflow cap).
    ///   - monolithic: per-child "this box has NO break point inside it"
    ///     flags, index-aligned with childHeightsPx (a short/empty list
    ///     reads as false = breakable). A box with no children and no text
    ///     has neither a class-A (between block-level siblings) nor a
    ///     class-B (between line boxes) breakpoint inside it (css-break-3
    ///     §4.1), so a column boundary may not pass through it: it moves
    ///     WHOLE to the next column. Evidence for the rule is in the Kotlin
    ///     twin's inline comment (WPT css-break borders-000/001/002 and
    ///     avoid-border-break reference prose).
    static func runPlan(childHeightsPx: [Double],
                        columnBlockSize: Double,
                        columnWidth: Double,
                        gapPx: Double,
                        columnCount: Int,
                        monolithic: [Bool] = []) -> RunPlan {
        // Cumulative stacking: child i starts where child i-1 ended (block
        // flow; margins already inside the measured heights on this path).
        var offsets: [Double] = []
        offsets.reserveCapacity(childHeightsPx.count)
        var y = 0.0
        for (index, raw) in childHeightsPx.enumerated() {
            // Negative measured heights are impossible boxes — floor at 0.
            let h = max(0, raw)
            // The monolithic push — skipped when the box already starts
            // flush at a boundary (a box taller than H then legitimately
            // breaks: the "does not fit anywhere" case of §5.2).
            let isMono = index < monolithic.count ? monolithic[index] : false
            if columnBlockSize > 0, h > 0, isMono,
               y.truncatingRemainder(dividingBy: columnBlockSize) != 0 {
                // The column this box starts in vs the one its LAST pixel
                // lands in — different ⇒ a boundary passes through it.
                let startCol = (y / columnBlockSize).rounded(.down)
                let endCol = ((y + h - 1) / columnBlockSize).rounded(.down)
                if startCol != endCol {
                    // Break before the box: it opens the next column.
                    y = (startCol + 1) * columnBlockSize
                }
            }
            // The child's strip offset BEFORE its own extent.
            offsets.append(y)
            y += h
        }
        // T ≤ H (or degenerate H): the run fits column 0 whole — no slices
        // (FragmentGeometry.fragments already answers nil for both, which
        // maps to the empty list here — same decision as the Kotlin twin).
        let fragments = FragmentGeometry.fragments(childBlockSize: y,
                                                   columnBlockSize: columnBlockSize,
                                                   columnWidth: columnWidth,
                                                   gapPx: gapPx,
                                                   columnCount: columnCount) ?? []
        return RunPlan(yOffsetsPx: offsets, totalBlockSizePx: y, fragments: fragments)
    }
}

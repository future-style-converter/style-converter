//
//  MulticolMath.swift
//  StyleEngine/columns — wave-9 regression fix.
//
//  Pure css-multicol-1 §3 used-value math: given a container's available
//  content width plus the computed column-count / column-width / column-gap,
//  produce the USED column count and per-column width. iOS mirror of the
//  Android `MultiColumnApplier.resolveUsedColumns` (wave 6, the multicol
//  wedge fix) extended with the non-auto column-width branches of the §3
//  pseudo-algorithm. First consumer: ComponentRenderer.wptChildFillWidth —
//  css-multicol-1 §2 says the containing block of a multicol element's
//  children is the COLUMN box, so the wave-9 WPT block-child auto-width
//  fill must use the column width as its basis, never the container width
//  (css-break background-image-001: a block child stretched across all
//  three columns and crashed SSIM 0.140 → 0.040).
//

// Foundation for floor().
import Foundation

// Namespacing enum — all members static + pure so XCTests pin the math
// without a render surface (same pattern as CSSFlexMath / MarginCollapse).
enum MulticolMath {

    /// The used multicol values after §3 fitting: the column count and
    /// per-column width that actually lay out, both guaranteed sane
    /// (count >= 1, widthPx >= 0) regardless of how narrow the container
    /// is — the Android wedge lesson: never emit a negative width.
    struct UsedColumns: Equatable {
        /// Used column count — always >= 1, never more than a specified count.
        let count: Int
        /// Used per-column width in px — always >= 0.
        let widthPx: Double
    }

    /// css-multicol-1 §3 pseudo-algorithm over the three input shapes:
    ///   • count only (width auto):  N := count
    ///   • width only (count auto):  N := max(1, floor((U + gap) / (W + gap)))
    ///   • both non-auto:            N := min(count, max(1, floor((U + gap) / (W + gap))))
    /// then the used width W' := max(0, (U − (N−1)·gap) / N) — algebraically
    /// identical to the spec's (U + gap)/N − gap form, floored at 0.
    ///
    /// Android-parity guard: additionally REDUCE the used count until
    /// (count−1)·gap fits in the available width. The strict spec text keeps
    /// N and clamps W to 0, but a run of zero-width columns is meaningless
    /// in a native layout (the Compose wedge fed a negative width into
    /// Constraints); a container narrower than its gaps renders 1 column
    /// filling the available width, matching the visible browser result
    /// (gaps have no painted extent of their own).
    ///
    /// - Parameters:
    ///   - availableWidthPx: container CONTENT-box width in px (negative → 0).
    ///   - requestedCount: computed `column-count`, nil for `auto`; values
    ///     < 1 are invalid per §3.1 (<integer [1,∞]>) and coerced to 1.
    ///   - requestedWidthPx: computed `column-width` in px, nil for `auto`
    ///     (or unresolvable — ColumnsConfig.widthPx contract); values <= 0
    ///     are degenerate and treated as `auto`.
    ///   - gapPx: used `column-gap` in px (negative is invalid per
    ///     css-align-3 §8 — gaps are non-negative — and treated as 0).
    /// - Returns: nil when BOTH inputs are auto — the element is not a
    ///   multicol container (§2) and no column geometry exists.
    static func usedColumns(availableWidthPx: Double,
                            requestedCount: Int?,
                            requestedWidthPx: Double?,
                            gapPx: Double) -> UsedColumns? {
        // Degenerate width (<= 0) behaves as auto — see the doc note above.
        let width = requestedWidthPx.flatMap { $0 > 0 ? $0 : nil }
        // §2: not a multicol container unless at least one input is non-auto.
        guard requestedCount != nil || width != nil else { return nil }
        // Defensive floors: negative available space lays out as zero space,
        // negative gaps are invalid CSS (css-align-3 §8).
        let available = max(0, availableWidthPx)
        let gap = max(0, gapPx)
        // §3.1: column-count is a positive <integer> — coerce sub-1 to 1.
        let requested = requestedCount.map { max(1, $0) }
        // §3 branch select — how many columns the inputs ask for.
        let asked: Int
        if let w = width {
            // Width-driven fit: how many W-wide columns (plus their gaps)
            // fit in U. floor((U + gap) / (W + gap)), never below 1.
            let fitting = max(1, Int(floor((available + gap) / (w + gap))))
            // Both non-auto: the count acts as a CAP on the fit (§3).
            asked = requested.map { min($0, fitting) } ?? fitting
        } else {
            // Count-only branch: N is exactly the specified count. The
            // guard above proves requested is non-nil here; the `?? 1`
            // keeps the expression total without a force-unwrap.
            asked = requested ?? 1
        }
        // Android-parity gap fitting: largest n with (n−1)·gap <= available,
        // i.e. the gaps alone still fit; a zero gap fits every asked column
        // by definition (also avoids the division by zero).
        let fitting = gap == 0 ? asked : min(asked, Int(available / gap) + 1)
        // Never fewer than one column — multicol always produces >= 1 box.
        let count = max(1, fitting)
        // Used width: remaining space split evenly, floored at 0 (available
        // can still barely exceed the gaps — a 0-width column is honest).
        let usedWidth = max(0, (available - Double(count - 1) * gap) / Double(count))
        return UsedColumns(count: count, widthPx: usedWidth)
    }
}

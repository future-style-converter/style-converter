//
//  FragmentGeometry.swift
//  StyleEngine/columns — wave 10 (the fragmentation contract).
//
//  Pure css-break-3 §4 fragment geometry for a multicol container's
//  single overflowing child (the wave-10 contract's fragmentGeometry
//  (C, H, W, G, N) function): a child whose block-size C exceeds the
//  column block-size H breaks into F = ceil(C/H) fragments, fragment i
//  rendered in column i (inline origin x_i = i·(W+G)) showing the
//  child's content slice [i·H, min((i+1)·H, C)). Backgrounds paint per
//  box-decoration-break:slice (css-break-3 §5.2, the initial value):
//  the child is painted ONCE as an unfragmented C-tall box and each
//  fragment shows a clipped, translated band of that continuous paint —
//  which is exactly the {clipRect, translate} pair emitted here. F is
//  capped at the used column count N with the [N·H, C) tail clipped,
//  matching Chromium's column-fill:auto overflow behaviour for this
//  fixture family (no column overflow strip is created).
//
//  Horizontal-tb only: vertical writing modes are classified
//  blocked-platform — the CALLER (ColumnsApplier.fragmentPlan) bails
//  with a logOnce before ever reaching this math.
//
//  Pure + SwiftUI-free so FragmentGeometryTests pins the shared S1–S5
//  table (identical expected values on Android's mirror) without a
//  render surface — same pattern as MulticolMath / CSSFlexMath.
//

// CoreGraphics only for CGRect/CGSize — the shapes the renderer's
// clip+translate draw pass consumes directly.
import CoreGraphics
// Foundation for ceil().
import Foundation

// Namespacing enum — all members static + pure (never instantiated).
enum FragmentGeometry {

    /// One fragment of the clip+translate draw pass: draw the FULL
    /// child paint translated by `translate`, clipped to `clipRect`
    /// (both in the multicol container's content-box coordinates).
    struct Fragment: Equatable {
        /// Which column box holds this fragment (0-based, < N).
        let columnIndex: Int
        /// Column i's rect: (i·(W+G), 0, W, H) — the clip of the pass.
        let clipRect: CGRect
        /// The continuous-paint translation: (+i·(W+G), −i·H). The
        /// +x moves the paint into column i; the −y slides the child's
        /// band [i·H, (i+1)·H) up to the column's block start so the
        /// clip exposes exactly that slice (css-break-3 §5.2 slice).
        let translate: CGSize
    }

    /// The contract's fragmentGeometry(C, H, W, G, N): the fragment
    /// list for one overflowing child, or nil when the child FITS
    /// (C <= H) — the identity case, where the existing unfragmented
    /// render path must stay byte-identical (shared table row S2).
    ///
    /// - Parameters:
    ///   - childBlockSize: C — the child's laid-out block-size in px.
    ///   - columnBlockSize: H — the column (container content-box)
    ///     block-size in px; non-positive H cannot host fragments
    ///     (division by zero / infinite F) → nil, identity path.
    ///   - columnWidth: W — the §3 used per-column inline size in px
    ///     (negative is impossible from MulticolMath; floored at 0
    ///     defensively so clip rects never have negative extent).
    ///   - gapPx: G — the used column-gap in px (css-align-3 §8: gaps
    ///     are non-negative; negative input is floored at 0).
    ///   - columnCount: N — the §3 used column count (>= 1 from
    ///     MulticolMath; sub-1 input is coerced to 1 defensively).
    /// - Returns: F = min(ceil(C/H), N) fragments — the N cap clips
    ///   the [N·H, C) tail (rows S4/S5: column-fill:auto overflow).
    static func fragments(childBlockSize: Double,
                          columnBlockSize: Double,
                          columnWidth: Double,
                          gapPx: Double,
                          columnCount: Int) -> [Fragment]? {
        // A non-positive column block-size can hold no fragment band —
        // and ceil(C/H) would be undefined/infinite. Identity path.
        guard columnBlockSize > 0 else { return nil }
        // S2 (the fits case): C <= H means no break point is ever
        // reached (css-break-3 §4) — nil keeps the existing render
        // path untouched, pinning "non-overflowing fixtures render
        // byte-identically".
        guard childBlockSize > columnBlockSize else { return nil }
        // Defensive floors mirroring MulticolMath's invariants (W >= 0,
        // G >= 0, N >= 1) so a hostile caller can't produce negative
        // rects or a zero-length fragment list.
        let w = max(0, columnWidth)
        let g = max(0, gapPx)
        let n = max(1, columnCount)
        // Unclamped fragment count: every full-or-partial H-band of the
        // child needs a column (css-break-3 §4: a break at each column
        // block-size boundary).
        let unclamped = Int(ceil(childBlockSize / columnBlockSize))
        // Cap at the used column count — content past column N−1 is
        // CLIPPED (Chromium column-fill:auto overflow: no extra column
        // is synthesized for this fixture family). Rows S4 and S5.
        let count = min(n, unclamped)
        // Build fragment i = 0..<F straight from the contract formulas.
        return (0..<count).map { i in
            // Column i's inline origin: x_i = i × (W + G).
            let x = Double(i) * (w + g)
            return Fragment(
                columnIndex: i,
                // The clip is the COLUMN rect — W×H at (x_i, 0).
                clipRect: CGRect(x: x, y: 0, width: w, height: columnBlockSize),
                // Slice translation: +x_i inline, −i·H block (see the
                // Fragment doc above for why the block shift is negative).
                translate: CGSize(width: x,
                                  height: -Double(i) * columnBlockSize))
        }
    }
}

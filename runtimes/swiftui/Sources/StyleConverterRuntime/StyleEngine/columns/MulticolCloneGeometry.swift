//
//  MulticolCloneGeometry.swift
//  StyleEngine/columns — wave-46 lane Y3.
//
//  Pure fragment geometry for css-break-3 §5.2 `box-decoration-break:
//  clone` inside a multicol fragmentainer — the CLONE twin of
//  FragmentGeometry's slice S-table, and the iOS TWIN of the Android
//  runtime's MulticolCloneGeometry.kt. The shared K-table (full-fragment
//  rows K1/K2/K3/K7) is pinned in MulticolCloneGeometryTests.swift and
//  the Android MulticolCloneGeometryTest.kt with the SAME expected
//  fragments (native-pair parity gate).
//
//  The model: §5.2 — "each box fragment is independently wrapped with the
//  border, padding, and margin … the border-radius … applied to each
//  fragment independently. The background is drawn independently in each
//  fragment." So, unlike slice, every fragment re-spends the child's own
//  block-axis bands (border + padding) on its decoration and the CONTENT
//  advances by `H − bands` per fragment; and every fragment paints the
//  child as a COMPLETE box of the fragment's size, with NO block-axis
//  translate (the same box again, not a scrolled band of one tall box).
//
//  PLATFORM DIVERGENCE, BY DESIGN (documented in both pin suites): a
//  SHORT last fragment (leftover content + bands < H). iOS re-renders the
//  child per fragment (ComponentRenderer.multicolFragmentRow composes one
//  ComponentHost per fragment, and the plan hands it a child whose
//  declared block-size is the fragment's), so the short fragment is ONE
//  exact entry of its own height. Compose can measure its single child
//  only once (at H) and approximates the short fragment as two clipped
//  bands of that H-tall paint — see MulticolCloneGeometry.kt. Full
//  fragments are byte-identical across the pair.
//

// CoreGraphics for the CGRect/CGSize fragment shapes; Foundation for ceil.
import CoreGraphics
import Foundation

// Namespacing enum — all members static + pure (MulticolMath pattern).
enum MulticolCloneGeometry {

    /// The child's own block-axis decoration bands in px — what every
    /// clone fragment wraps its content slice with (css-break-3 §5.2).
    struct Bands: Equatable {
        /// border-top + padding-top (block-start band under horizontal-tb).
        let blockStartPx: Double
        /// border-bottom + padding-bottom (block-end band).
        let blockEndPx: Double
        /// Both bands — what the unfragmented layout spends exactly once.
        var totalPx: Double { blockStartPx + blockEndPx }
    }

    /// One clone fragment: the shared clip/translate shape the renderer's
    /// fragment row consumes, plus the fragment's own border-box block-size
    /// (the size the child is re-rendered at for this column).
    struct CloneFragment: Equatable {
        /// Column rect clip + inline-only translate (x_i, 0).
        let fragment: FragmentGeometry.Fragment
        /// The fragment's border-box block-size: H for every full fragment,
        /// `leftover content + bands` for a short last one.
        let boxBlockSizePx: Double
    }

    /// Clone fragment list for a child whose UNFRAGMENTED border-box
    /// block-size is `childBlockSize` (C = content + both bands) inside a
    /// fragmentainer of block-size `columnBlockSize` (H), used column width
    /// W, gap G and used count N.
    ///
    /// Nil when NO content fits a fragment (H ≤ bands) or there is no
    /// fragmentainer (H ≤ 0): the clone model has no answer there and the
    /// caller falls back to slice with a logOnce. Fragment count
    /// F = ceil(content / (H − bands)), at least 1, capped at N exactly
    /// like the slice table (the column-fill:auto overflow cap — the tail
    /// never gets a column; a capped remainder renders as a full H box).
    static func cloneFragments(childBlockSize: Double,
                               columnBlockSize: Double,
                               bands: Bands,
                               columnWidth: Double,
                               gapPx: Double,
                               columnCount: Int) -> [CloneFragment]? {
        // Defensive floors mirroring FragmentGeometry: count is a positive
        // <integer> (css-multicol §3.2), gaps non-negative (css-align §8),
        // a negative width clips to nothing, bands cannot be negative
        // (CSS 2.1 §8.4/§8.5 — padding and border widths are ≥ 0).
        let n = max(1, columnCount)
        let g = max(0, gapPx)
        let w = max(0, columnWidth)
        let startBand = max(0, bands.blockStartPx)
        let endBand = max(0, bands.blockEndPx)
        let h = columnBlockSize
        // Per-fragment CONTENT capacity: the fragmentainer minus the bands
        // every clone fragment re-spends on its own decoration (§5.2).
        let capacity = h - startBand - endBand
        // No fragmentainer, or bands alone fill it: clone is undefined
        // (every fragment would be pure decoration) — nil, the caller's
        // slice fallback + logOnce.
        guard h > 0, capacity > 0 else { return nil }
        // The content block-size: the unfragmented box minus ONE pair of
        // bands (the unfragmented layout wraps the content exactly once).
        let content = max(0, childBlockSize - startBand - endBand)
        // At least one fragment (an empty clone box still owns column 0),
        // at most N — the slice table's identical overflow cap.
        let count = min(n, max(1, Int(ceil(content / capacity))))
        return (0..<count).map { i in
            // Inline origin of column i — exactly i gaps precede column i.
            let x = Double(i) * (w + g)
            // Content left for THIS fragment: every fragment before the
            // last spends a full capacity, the last takes the remainder.
            // When the N cap truncated the list the remainder exceeds the
            // capacity and the fragment is a full H box (overflow clipped).
            let remaining = content - Double(i) * capacity
            // The fragment's border-box block-size: remainder + its own
            // bands, never more than the fragmentainer.
            let boxH = min(h, remaining + startBand + endBand)
            // Clip = the fragment's own rect in column i; translate = the
            // inline shift ONLY — the child is re-rendered at boxH, so no
            // block-axis scroll exists (unlike the slice table's −i·H).
            return CloneFragment(
                fragment: FragmentGeometry.Fragment(
                    columnIndex: i,
                    clipRect: CGRect(x: x, y: 0, width: w, height: boxH),
                    translate: CGSize(width: x, height: 0)),
                boxBlockSizePx: boxH)
        }
    }
}

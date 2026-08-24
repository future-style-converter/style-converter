//
//  VerticalFragmentGeometry.swift
//  StyleEngine/columns — wave 47 (lane Z2).
//
//  Pure fragment geometry for css-break-3 §4 multicol fragmentation under
//  VERTICAL writing modes — the iOS half of the shared V-table (the Compose
//  twin is columns/VerticalFragmentGeometry.kt; VerticalFragmentGeometryTests
//  pins the identical expected values, so drift is a cross-platform
//  divergence, not a refactor).
//
//  Axes transpose against FragmentGeometry: the container's INLINE axis is
//  vertical, so the N column boxes STACK VERTICALLY, each colH tall
//  (colH = (availableInline − (N−1)·G) / N) and W wide, where W — the
//  container's content-box WIDTH — is the fragmentainer BLOCK size the child
//  breaks at. The child lays out ONCE as a continuous box of block extent C
//  (its used physical WIDTH) × colH; fragment i shows one W-wide physical
//  band of that continuous paint inside column i.
//
//  The BLOCK-FLOW DIRECTION decides which band fragment i shows and where a
//  partial band sits (css-writing-modes-4 §6.4):
//   * vertical-lr — bands walk rightward: [i·W, (i+1)·W), left-aligned
//     (translate x = −i·W);
//   * vertical-rl — bands walk leftward: [C−(i+1)·W, C−i·W), RIGHT-aligned;
//     one formula covers full and partial bands: translate x = W − C + i·W.
//

// CoreGraphics for CGRect/CGSize (the Fragment shape); Foundation for ceil.
import CoreGraphics
import Foundation

// Namespacing enum — all members static + pure (never instantiated).
enum VerticalFragmentGeometry {

    /// The vertical V-table. Reuses FragmentGeometry.Fragment: clipRect is
    /// column i's rect in container content coordinates — (0, y_i, W, colH)
    /// with y_i = i·(colH+G) — and translate carries (tx_i, +y_i). The
    /// renderer's VStack consumption supplies the vertical slot position
    /// itself and reads only translate.width, exactly like the horizontal
    /// HStack ignores translate.width and reads .height.
    ///
    /// UNLIKE the horizontal S-table, the FITS case (C ≤ W) still returns a
    /// single fragment: under vertical-rl even fitting content is
    /// RIGHT-aligned in its column (block-start is the right edge), which
    /// the unfragmented path cannot express — the Compose V5 row pins the
    /// same choice.
    ///
    /// - Parameters:
    ///   - childBlockSize: C — the child's block extent (used width) in px.
    ///   - containerBlockSize: W — the container's content-box width, the
    ///     fragmentainer block size (non-positive → nil, identity path).
    ///   - columnInlineSize: colH — one column box's height in px.
    ///   - gapPx: G — the used column-gap (vertical here; floored at 0).
    ///   - columnCount: N — the §3.4 used count (caps F — the
    ///     column-fill:auto overflow clip, same rule as horizontal).
    ///   - blockRtl: true for vertical-rl / sideways-rl (bands walk left).
    static func fragments(childBlockSize: Double,
                          containerBlockSize: Double,
                          columnInlineSize: Double,
                          gapPx: Double,
                          columnCount: Int,
                          blockRtl: Bool) -> [FragmentGeometry.Fragment]? {
        // No fragmentainer extent → nothing to slice at (identity path).
        guard containerBlockSize > 0 else { return nil }
        // Defensive floors mirroring the horizontal table's invariants.
        let w = containerBlockSize
        let colH = max(0, columnInlineSize)
        let g = max(0, gapPx)
        let n = max(1, columnCount)
        let c = max(0, childBlockSize)
        // F = ceil(C/W), at least 1 (the fits case — see the doc above),
        // capped at N (the overflow tail never gets a column).
        let count = min(n, max(1, Int(ceil(c / w))))
        return (0..<count).map { i in
            // Column i's vertical origin — exactly i gaps above it.
            let y = Double(i) * (colH + g)
            // Band translation (doc above): left-aligned −i·W for lr,
            // right-aligned W−C+i·W for rl.
            let tx = blockRtl ? w - c + Double(i) * w : -Double(i) * w
            return FragmentGeometry.Fragment(
                columnIndex: i,
                // The clip is column i's rect — full content width × colH.
                clipRect: CGRect(x: 0, y: y, width: w, height: colH),
                // (tx, +y): the y rides for container-coordinate parity
                // with the Compose draw replay; the VStack consumer
                // supplies the slot position and reads only tx.
                translate: CGSize(width: tx, height: y))
        }
    }

    /// colH = (availableInline − (N−1)·G) / N floored at 0 — the §3.4 fit
    /// transposed to the vertical inline axis (Compose twin:
    /// VerticalFragmentGeometry.columnInlineSizePx).
    static func columnInlineSize(availableInline: Double,
                                 columnCount: Int,
                                 gapPx: Double) -> Double {
        let n = max(1, columnCount)
        let g = max(0, gapPx)
        return max(0, (availableInline - Double(n - 1) * g) / Double(n))
    }
}

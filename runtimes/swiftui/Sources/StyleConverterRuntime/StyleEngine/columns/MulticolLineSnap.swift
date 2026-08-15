//
//  MulticolLineSnap.swift
//  StyleEngine/columns — wave-42 lane W4.
//
//  Pure line-box-snapped fragment geometry for css-break-3 §4 multicol
//  fragmentation: the iOS TWIN of the Android runtime's MulticolLineSnap.kt
//  (runtimes/compose .../columns/). The shared LS pin table lives in
//  MulticolLineSnapTests.swift and the Android MulticolLineSnapTest.kt with
//  the SAME rows, so both natives snap fragments to the same line
//  boundaries byte-identically.
//
//  The defect this models away (wave-41 evidence, css-overflow
//  discard-multicol-001/002/004): the raw FragmentGeometry slice translates
//  fragment i by −i·H, but a TEXT child is a stack of line boxes and
//  css-break-3 §4 only allows class-B breakpoints BETWEEN line boxes. When
//  H is not an exact line multiple the raw slice cuts glyphs and drifts per
//  column. The snapped model packs k = ⌊H/L⌋ WHOLE lines per column
//  (Chromium's behaviour): fragment i clips to the k-line band and
//  translates by −i·k·L — always a line-box boundary.
//
//  CONSUMPTION STATUS (iOS): pure module + pins only for now. The iOS
//  renderer has no route from a TEXT-ONLY multicol container to any
//  fragment pass at all (the wave-41 captures show the text at full
//  container width) — that routing seam lives in ComponentRenderer (core,
//  out of this lane's ownership) and is documented in the wave-42 report.
//  Landing the twin now keeps the LS table native-pair-pinned so the seam
//  wave only wires views, never re-derives math.
//

// Foundation for floor/abs; CoreGraphics for the CGRect/CGSize fragments.
import CoreGraphics
import Foundation

// Namespacing enum — all members static + pure (MulticolMath pattern).
enum MulticolLineSnap {

    /// The inline size the line-box probe measures at (the Compose twin's
    /// minIntrinsicHeight width): wide enough that fixture-scale text lays
    /// out as ONE line, so the probed height IS the line-box height L.
    ///
    /// Wave-42 skeptic S2: the shared value is 65534, not the 1_000_000
    /// this twin first carried, because the ANDROID half must build
    /// `Constraints(maxWidth:)` from it and Compose cannot represent a
    /// width above 262142 at all (it throws IllegalArgumentException out of
    /// Constraints packing, killing the capture composition). SwiftUI has no
    /// such packing limit, but the twins advertise ONE probe width or the
    /// pair silently probes different geometry — see the Kotlin twin's
    /// MAX_CONSTRAINT_WIDTH_PX banner for the bytecode evidence.
    static let lineProbeWidthPx: Double = 65_534

    /// The snapped fragment list, or nil when the line-stack heuristic
    /// declines — callers then keep the raw FragmentGeometry slice:
    ///  - no probe answer (L == nil) or degenerate inputs (L ≤ 0, H ≤ 0);
    ///  - L > H — a "line" taller than the column is a block child;
    ///  - fewer than 2 lines (a single line can never tear mid-line);
    ///  - C off the n-line grid by more than ±1px per line (mixed content).
    ///
    /// - Parameters:
    ///   - childBlockSize: C — the child's laid-out block-size at column
    ///     width (same input as the raw slice).
    ///   - columnBlockSize: H — the definite column block-size.
    ///   - lineBox: L — the single-line-box height from the probe.
    ///   - columnWidth: W — the §3.4 used column width.
    ///   - gapPx: G — the used column-gap.
    ///   - columnCount: N — the §3.4 used column count (the overflow cap).
    static func snappedFragments(childBlockSize: Double,
                                 columnBlockSize: Double,
                                 lineBox: Double?,
                                 columnWidth: Double,
                                 gapPx: Double,
                                 columnCount: Int) -> [FragmentGeometry.Fragment]? {
        // No probe answer → not knowably a line stack → raw slice.
        guard let l = lineBox else { return nil }
        // Degenerate geometry never snaps (mirrors FragmentGeometry's floors).
        guard l > 0, columnBlockSize > 0 else { return nil }
        // A "line" taller than the column is a block box, not a line stack.
        guard l <= columnBlockSize else { return nil }
        // Rounded line count n = round(C / L) — the Kotlin twin's integer
        // (2C+L)/(2L) rounding, spelled with rounded() on the Double side.
        let c = max(0, childBlockSize)
        let n = Int((c / l).rounded())
        // A single line (or empty child) can never tear mid-line — decline
        // so the identity-shaped raw geometry keeps owning that case.
        guard n >= 2 else { return nil }
        // Line-grid check: C must be n whole lines within ±1px per line
        // (platform text rounds each line box independently).
        guard abs(c - Double(n) * l) <= Double(n) else { return nil }
        // Lines per column: ⌊H / L⌋ — ≥ 1 by the L ≤ H gate above.
        let k = Int((columnBlockSize / l).rounded(.down))
        // Defensive floors mirroring FragmentGeometry (count ≥ 1, W/G ≥ 0).
        let count = max(1, columnCount)
        let w = max(0, columnWidth)
        let g = max(0, gapPx)
        // Fragment count: ⌈n / k⌉ columns of k lines, capped at N (the
        // column-fill:auto overflow clip, identical to the raw slice cap).
        let f = min(count, (n + k - 1) / k)
        // Each fragment's visible band is exactly its k whole lines — the
        // H − k·L remainder stays EMPTY (the next line moved whole to the
        // next column), the visible difference from the raw slice.
        let band = min(columnBlockSize, Double(k) * l)
        // Build fragment i: clip = column i's rect capped to the k-line
        // band; translate = (+x_i, −i·k·L) — a line-box boundary by
        // construction (the snap itself). Same Fragment type as the raw
        // slice so the clone-row pass consumes either interchangeably.
        return (0..<f).map { i in
            // Inline origin of column i — gaps sit BETWEEN columns (§3).
            let x = Double(i) * (w + g)
            // The LS-table row: snapped clip + snapped translate.
            return FragmentGeometry.Fragment(
                columnIndex: i,
                clipRect: CGRect(x: x, y: 0, width: w, height: band),
                translate: CGSize(width: x, height: -Double(i * k) * l))
        }
    }
}

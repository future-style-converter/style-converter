//
//  GridPlacer.swift
//  StyleEngine/layout/grid — fidelity wave 1.
//
//  The PURE placement half of the CSS-grid subset: the per-item request
//  model, the css-grid-1 §8.5 sparse row-flow auto-placement algorithm,
//  and the css-align-3 §6 self-alignment resolution. No SwiftUI layout
//  types — everything here is XCTest-pinned directly
//  (FidelityWave1Tests). The SwiftUI Layout that consumes these lives
//  in CSSGridLayout.swift; the track-sizing math in GridTrackMath.swift.
//

import SwiftUI
import CoreGraphics

// MARK: - Pure placement model

/// Placement request for one grid item, decoded from the item's
/// LayoutAggregate placement longhands. All lines 1-based like CSS;
/// nil means `auto` on that line.
struct GridItemRequest: Equatable {
    /// grid-column-start line (1-based) — nil = auto.
    var colStart: Int? = nil
    /// grid-column-end line (exclusive) — nil = auto.
    var colEnd: Int? = nil
    /// grid-row-start line (1-based) — nil = auto.
    var rowStart: Int? = nil
    /// grid-row-end line (exclusive) — nil = auto.
    var rowEnd: Int? = nil
    /// `span N` on the column axis when declared without lines.
    var colSpan: Int? = nil
    /// `span N` on the row axis when declared without lines.
    var rowSpan: Int? = nil
    /// NAMED lines (css-grid-1 §8.3 <custom-ident>), e.g. the
    /// `grid-row-start: media` the converter emits for `grid-area: media`.
    /// Carried as raw names on the claim — only the CONTAINER can resolve
    /// them (against its own grid-template-areas) via
    /// `GridPlacer.resolveNames` at measure time (wave 5: the old
    /// child-name↔area-name matcher dropped/duplicated items).
    var colStartName: String? = nil
    /// Named grid-column-end line — see colStartName.
    var colEndName: String? = nil
    /// Named grid-row-start line — see colStartName.
    var rowStartName: String? = nil
    /// Named grid-row-end line — see colStartName.
    var rowEndName: String? = nil
}

/// Resolved cell for one item after auto-placement: 0-based column/row
/// origin plus spans (≥1). Produced by `GridPlacer.assign`.
struct GridCell: Equatable {
    /// 0-based column index of the cell's left edge.
    var col: Int
    /// 0-based row index of the cell's top edge.
    var row: Int
    /// Number of column tracks covered (≥1).
    var colSpan: Int
    /// Number of row tracks covered (≥1).
    var rowSpan: Int
}

/// Per-axis self-alignment of an item inside its cell. `start` covers
/// CSS start/self-start/auto/normal/stretch (stretch of a fit-content
/// item degenerates to start — css-align-3 §9: a non-stretchable item
/// under `normal` is start-aligned).
enum GridItemAlign: Equatable {
    /// Anchor to the cell's leading/top edge (CSS default here).
    case start
    /// Centre inside the cell.
    case center
    /// Anchor to the cell's trailing/bottom edge.
    case end
}

// MARK: - Placement algorithm

enum GridPlacer {

    /// css-grid-1 §8.3 named-line resolution + negative-line
    /// normalisation, run by the CONTAINER (the only party that knows
    /// the template) before `assign`. Verified against the browser
    /// (puppeteer probe, wave 5):
    ///   • `grid-row-start: media` matches the implicit `media-start`
    ///     line a named template area contributes → the area's first
    ///     row line. The item does NOT copy the area's column/span —
    ///     the other three lines stay auto exactly like the browser
    ///     given this single longhand (the converter expands
    ///     `grid-area: <name>` to grid-row-start alone).
    ///   • A DANGLING name (no such area) resolves to the first
    ///     IMPLICIT line past the explicit grid (§8.3.1 "all implicit
    ///     lines are assumed to have that name"): explicit grid with R
    ///     rows has lines 1…R+1, so the name lands on line R+2 —
    ///     browser-verified: `ghost` in a 2-row grid sits in implicit
    ///     row 4 leaving an empty implicit row 3.
    ///   • Negative integers count from the end of the explicit grid
    ///     (§8.3): -1 = last explicit line = trackCount+1, i.e.
    ///     normalised = trackCount + 2 + N for N < 0.
    static func resolveNames(_ request: GridItemRequest,
                             areas: [[String]]?,
                             columnCount: Int,
                             explicitRowCount: Int) -> GridItemRequest {
        var rq = request
        // Bounding rectangle of a named area in 0-based cell indices —
        // nil when the template doesn't define the name (or no template).
        func rect(_ name: String) -> (r0: Int, r1: Int, c0: Int, c1: Int)? {
            guard let rows = areas else { return nil }
            var r0 = Int.max, r1 = Int.min, c0 = Int.max, c1 = Int.min
            // One scan over the template cells; areas are rectangular
            // per CSS grammar, so min/max bounds fully describe them.
            for (r, row) in rows.enumerated() {
                for (c, cell) in row.enumerated() where cell == name {
                    r0 = min(r0, r); r1 = max(r1, r)
                    c0 = min(c0, c); c1 = max(c1, c)
                }
            }
            return r0 == Int.max ? nil : (r0, r1, c0, c1)
        }
        // First implicit line index past an explicit axis of N tracks
        // (lines 1…N+1 are explicit; the dangling-name landing spot).
        let implicitRowLine = explicitRowCount + 2
        let implicitColLine = columnCount + 2
        // Named lines → numbers. `-start` names take the area's first
        // line, `-end` names the line just past it (r1+2 in 1-based).
        if let n = rq.rowStartName { rq.rowStart = rect(n).map { $0.r0 + 1 } ?? implicitRowLine }
        if let n = rq.rowEndName   { rq.rowEnd   = rect(n).map { $0.r1 + 2 } ?? implicitRowLine }
        if let n = rq.colStartName { rq.colStart = rect(n).map { $0.c0 + 1 } ?? implicitColLine }
        if let n = rq.colEndName   { rq.colEnd   = rect(n).map { $0.c1 + 2 } ?? implicitColLine }
        // Negative integers → count back from the explicit end line.
        if let v = rq.colStart, v < 0 { rq.colStart = columnCount + 2 + v }
        if let v = rq.colEnd,   v < 0 { rq.colEnd   = columnCount + 2 + v }
        if let v = rq.rowStart, v < 0 { rq.rowStart = explicitRowCount + 2 + v }
        if let v = rq.rowEnd,   v < 0 { rq.rowEnd   = explicitRowCount + 2 + v }
        return rq
    }

    /// css-grid-1 §8.5 sparse row-flow auto-placement, simplified to the
    /// subset the IR carries (no named lines, no dense packing).
    ///
    /// Two passes, per spec §8.5 step 1 + step 4:
    ///   1. Items with BOTH a definite row and a definite column are
    ///      locked in first (they never move the cursor).
    ///   2. Remaining items flow in order with the placement cursor:
    ///      definite-column items advance the cursor row until their
    ///      columns are free; fully-auto items take the next free slot
    ///      scanning row-major from the cursor.
    ///
    /// Returns one cell per request (same order) + the resulting row
    /// count (≥1 so callers can always build at least one row track).
    static func assign(_ requests: [GridItemRequest],
                       columnCount: Int) -> (cells: [GridCell], rowCount: Int) {
        // Guard the degenerate 0-column case — treat as a single column
        // so the math below never divides by / clamps into zero.
        let n = max(1, columnCount)
        // Occupancy set keyed by (row << 16 | col) — grids here are tiny
        // (fixture scale), so a Set of packed Ints is plenty.
        var occupied = Set<Int>()
        // Small helper: mark / test a span rectangle.
        func key(_ r: Int, _ c: Int) -> Int { (r << 16) | c }
        func isFree(row: Int, col: Int, colSpan: Int, rowSpan: Int) -> Bool {
            // Reject spans that fall off the explicit column count.
            if col + colSpan > n { return false }
            // Any occupied cell in the rectangle blocks placement.
            for r in row..<(row + rowSpan) {
                for c in col..<(col + colSpan) where occupied.contains(key(r, c)) {
                    return false
                }
            }
            return true
        }
        func mark(_ cell: GridCell) {
            // Stamp the whole span rectangle as taken.
            for r in cell.row..<(cell.row + cell.rowSpan) {
                for c in cell.col..<(cell.col + cell.colSpan) {
                    occupied.insert(key(r, c))
                }
            }
        }

        // Resolve one axis of a request to (start0, span): CSS lines are
        // 1-based and end-exclusive; spans clamp into the explicit grid.
        func axis(_ start: Int?, _ end: Int?, _ span: Int?, limit: Int?) -> (Int?, Int) {
            // start + end → span is the line difference (min 1).
            if let s = start, let e = end {
                return (s - 1, max(1, e - s))
            }
            // start only → span from the `span N` keyword or 1.
            if let s = start {
                return (s - 1, max(1, span ?? 1))
            }
            // end only → back-computed start from span (default 1).
            if let e = end {
                let sp = max(1, span ?? 1)
                return (e - 1 - sp, sp)
            }
            // No lines at all → auto start, span from keyword (capped at
            // the column count when this is the inline axis).
            var sp = max(1, span ?? 1)
            if let lim = limit { sp = min(sp, lim) }
            return (nil, sp)
        }

        // Working buffer — cells resolved per request index.
        var cells = [GridCell?](repeating: nil, count: requests.count)

        // Pass 1 — fully-determined items (definite row AND column).
        for (i, rq) in requests.enumerated() {
            let (c0, csp) = axis(rq.colStart, rq.colEnd, rq.colSpan, limit: n)
            let (r0, rsp) = axis(rq.rowStart, rq.rowEnd, rq.rowSpan, limit: nil)
            if let c = c0, let r = r0 {
                // Clamp negatives (malformed IR) to the origin.
                let cell = GridCell(col: max(0, min(c, n - 1)), row: max(0, r),
                                    colSpan: min(csp, n - max(0, min(c, n - 1))),
                                    rowSpan: rsp)
                cells[i] = cell
                mark(cell)
            }
        }

        // Pass 2 — flow the rest with the auto-placement cursor.
        var cursorRow = 0
        var cursorCol = 0
        for (i, rq) in requests.enumerated() where cells[i] == nil {
            let (c0raw, csp) = axis(rq.colStart, rq.colEnd, rq.colSpan, limit: n)
            let (r0raw, rsp) = axis(rq.rowStart, rq.rowEnd, rq.rowSpan, limit: nil)
            if let cWant = c0raw {
                // Definite column, auto row (§8.5 step 4.1): if the
                // requested column sits before the cursor column, the
                // cursor wraps to the next row first.
                let c = max(0, min(cWant, n - 1))
                if c < cursorCol { cursorRow += 1; cursorCol = 0 }
                // March the cursor's row down until the span rectangle
                // is free at that fixed column.
                var r = cursorRow
                while !isFree(row: r, col: c, colSpan: min(csp, n - c), rowSpan: rsp) { r += 1 }
                let cell = GridCell(col: c, row: r,
                                    colSpan: min(csp, n - c), rowSpan: rsp)
                cells[i] = cell
                mark(cell)
                // Cursor lands just past the placed item (same row).
                cursorRow = r
                cursorCol = c + cell.colSpan
            } else if let rWant = r0raw {
                // Definite row, auto column: scan that row's columns.
                let r = max(0, rWant)
                var c = 0
                // First free column in the row; fall to column 0 of the
                // row below only via span-overflow (clamped scan).
                while c + csp <= n && !isFree(row: r, col: c, colSpan: csp, rowSpan: rsp) { c += 1 }
                let cc = min(c, max(0, n - csp))
                let cell = GridCell(col: cc, row: r, colSpan: csp, rowSpan: rsp)
                cells[i] = cell
                mark(cell)
            } else {
                // Fully auto: row-major scan from the cursor position.
                var r = cursorRow
                var c = cursorCol
                // Find the first free rectangle; wrap columns per row.
                while true {
                    if c + csp <= n && isFree(row: r, col: c, colSpan: csp, rowSpan: rsp) { break }
                    c += 1
                    if c + csp > n { c = 0; r += 1 }
                }
                let cell = GridCell(col: c, row: r, colSpan: csp, rowSpan: rsp)
                cells[i] = cell
                mark(cell)
                cursorRow = r
                cursorCol = c + csp
            }
        }

        // Row count = furthest bottom edge over all cells (≥1).
        let rows = cells.compactMap { $0 }.map { $0.row + $0.rowSpan }.max() ?? 1
        // The buffer is fully populated by construction; the fallback
        // cell is unreachable but keeps the map total.
        return (cells.map { $0 ?? GridCell(col: 0, row: 0, colSpan: 1, rowSpan: 1) },
                max(1, rows))
    }

    /// Resolve one item's per-axis alignment from its own justify/align-
    /// self plus the container's justify/align-items defaults.
    /// css-align-3 §6: `auto` → container default → `normal` → behaves
    /// as `stretch` for grid items, which for our fit-content items
    /// degenerates to `start` (see file header).
    static func resolveAlign(self selfKw: AlignmentKeyword?,
                             items itemsKw: AlignmentKeyword?) -> GridItemAlign {
        // Item-level wins unless auto/nil; then the container default.
        let effective: AlignmentKeyword? = {
            if let s = selfKw, s != .auto { return s }
            return itemsKw
        }()
        switch effective {
        case .center:               return .center
        case .end, .selfEnd:        return .end
        // start / self-start / baseline (approx) / stretch / normal /
        // auto / nil all pin to the start edge for fit-content items.
        default:                    return .start
        }
    }
}

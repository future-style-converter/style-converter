//
//  CSSGridLayout.swift
//  StyleEngine/layout/grid — fidelity wave 1.
//
//  A real CSS-grid subset as a SwiftUI custom `Layout` (iOS 16+),
//  replacing the LazyVGrid mapping that (a) centred every item in its
//  cell, (b) ignored explicit grid-column/row placement + spans,
//  (c) ignored grid-template-rows track sizes, and (d) greedily expanded
//  to the full canvas width where web keeps `width: fit-content`
//  (grid-2col/000-015, nested-3level/014-015 divergences).
//
//  The pure halves live beside this file: GridPlacer.swift (placement,
//  css-grid-1 §8.5) and GridTrackMath.swift (track sizing, §7.2) — both
//  XCTest-pinned. This file owns only the SwiftUI Layout protocol
//  plumbing and the grid stretch-height environment key.
//

import SwiftUI
import CoreGraphics

// MARK: - The Layout

/// SwiftUI custom Layout implementing the CSS-grid subset above.
///
/// IR v2 Slot & Placement contract: per-item placement requests and
/// self-alignment arrive WITH the subviews via the ItemPlacementKey
/// layout value (attached by ComponentHost from each child's OWN
/// properties) — this Layout consumes ONLY the grid block and resolves
/// self-alignment against its own justify/align-items defaults
/// (css-align-3 §6). The pre-v2 parallel requests/justify/align arrays
/// (parent-computed from child models) are gone: the container knows
/// nothing about its arrivals until measure time.
struct CSSGridLayout: Layout {
    /// Column track kinds from grid-template-columns (≥1 entries; the
    /// renderer supplies `[.automatic]` when no template exists — CSS
    /// grids without a template have a single auto column).
    var tracks: [GridTrack.Kind]
    /// Row template kinds (nil = no grid-template-rows declared).
    var rowTemplate: [GridTrack.Kind]?
    /// First grid-auto-rows entry (implicit row sizing), nil = auto.
    var autoRows: GridTrack.Kind?
    /// Container `justify-items` default (inline axis, css-align-3 §6).
    var justifyItems: AlignmentKeyword? = nil
    /// Container `align-items` default (block axis, css-align-3 §6).
    var alignItems: AlignmentKeyword? = nil
    /// Resolved row gap in points (css-align-3 row-gap).
    var rowGap: CGFloat
    /// Resolved column gap in points.
    var columnGap: CGFloat
    /// True when the container has a definite inline size (explicit CSS
    /// width) — fr/% tracks then split the proposal; false = fit-content
    /// sizing (hug the tracks) matching the web harness default.
    var definiteWidth: Bool

    /// Read each subview's grid claims from the placement channel.
    /// nil placement = ANONYMOUS item (the harness's leading `_text`
    /// placeholder — not a component): auto-placed and start-aligned on
    /// both axes, the pre-v2 renderer's exact treatment (an anonymous
    /// grid item has no self-alignment properties). Real components
    /// resolve their justify/align-self against the container defaults.
    private func claims(_ subviews: Subviews)
        -> (requests: [GridItemRequest], justify: [GridItemAlign], align: [GridItemAlign]) {
        var requests: [GridItemRequest] = []
        var justify: [GridItemAlign] = []
        var align: [GridItemAlign] = []
        for sub in subviews {
            if let p = sub[ItemPlacementKey.self] {
                // Component subview — consume the grid block only
                // (design §2.2 resolution rule; other blocks are inert).
                requests.append(p.grid.request)
                justify.append(GridPlacer.resolveAlign(self: p.grid.justifySelf,
                                                       items: justifyItems))
                align.append(GridPlacer.resolveAlign(self: p.grid.alignSelf,
                                                     items: alignItems))
            } else {
                // Anonymous item — auto placement, start/start.
                requests.append(GridItemRequest())
                justify.append(.start)
                align.append(.start)
            }
        }
        return (requests, justify, align)
    }

    /// Shared geometry solver for both protocol methods: places every
    /// subview, returning cell rects + total size + per-item alignment.
    private func solve(proposalWidth: CGFloat?,
                       subviews: Subviews) -> (size: CGSize, frames: [CGRect], items: [CGSize],
                                               justify: [GridItemAlign], align: [GridItemAlign]) {
        // Intrinsic (ideal) size per item — the min-floor frame inside
        // each child guarantees the web-parity 50×30 minimum.
        let sizes = subviews.map { $0.sizeThatFits(.unspecified) }
        // Placement claims ride the subviews (v2 parent-data channel).
        let (reqs, justify, align) = claims(subviews)
        // Resolve cells via the pure placer.
        let (cells, rowCount) = GridPlacer.assign(reqs, columnCount: tracks.count)
        // Max-content per column (span-1 items only, like Android).
        var maxContent = [CGFloat](repeating: 0, count: tracks.count)
        for (i, cell) in cells.enumerated() where cell.colSpan == 1 && cell.col < tracks.count {
            maxContent[cell.col] = max(maxContent[cell.col], sizes[i].width)
        }
        // Definite width only when the CSS declared one AND SwiftUI
        // actually proposed a number.
        let containerW: CGFloat? = definiteWidth ? proposalWidth : nil
        let colWidths = GridTrackMath.columnWidths(tracks: tracks,
                                                   containerWidth: containerW,
                                                   columnGap: columnGap,
                                                   maxContent: maxContent)
        // Row heights from template/auto-rows/content.
        let rowHts = GridTrackMath.rowHeights(template: rowTemplate,
                                              autoRows: autoRows,
                                              rowCount: rowCount,
                                              items: zip(cells, sizes).map { ($0, $1.height) })
        // Prefix offsets for columns and rows (gap between tracks).
        var colX: [CGFloat] = [0]
        for w in colWidths { colX.append(colX.last! + w + columnGap) }
        var rowY: [CGFloat] = [0]
        for h in rowHts { rowY.append(rowY.last! + h + rowGap) }
        // Total = last offset minus the trailing gap.
        let totalW = max(0, colX.last! - columnGap)
        let totalH = max(0, rowY.last! - rowGap)
        // Cell rectangle per item (span-aware: width spans cover the
        // inner gaps too).
        let frames: [CGRect] = cells.map { cell in
            let c = min(cell.col, max(0, tracks.count - 1))
            let cEnd = min(cell.col + cell.colSpan, tracks.count)
            let x = colX[c]
            let w = max(0, colX[cEnd] - columnGap - x)
            let r = min(cell.row, max(0, rowCount - 1))
            let rEnd = min(cell.row + cell.rowSpan, rowCount)
            let y = rowY[r]
            let h = max(0, rowY[rEnd] - rowGap - y)
            return CGRect(x: x, y: y, width: w, height: h)
        }
        return (CGSize(width: totalW, height: totalH), frames, sizes, justify, align)
    }

    /// Report the grid's own size: track sum (fit-content) or the
    /// definite proposal width; height is always the row-track sum.
    func sizeThatFits(proposal: ProposedViewSize,
                      subviews: Subviews, cache: inout ()) -> CGSize {
        let solved = solve(proposalWidth: proposal.width, subviews: subviews)
        return solved.size
    }

    /// Anchor each item inside its cell per the resolved alignments.
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize,
                       subviews: Subviews, cache: inout ()) {
        let solved = solve(proposalWidth: proposal.width, subviews: subviews)
        for (i, sub) in subviews.enumerated() {
            let cell = solved.frames[i]
            let item = solved.items[i]
            // Inline-axis anchor: start (CSS default here) / center / end
            // — resolved by claims() from the subview's own placement
            // against this container's justify-items default.
            let jx: CGFloat = {
                switch solved.justify[i] {
                case .start:  return cell.minX
                case .center: return cell.minX + (cell.width - item.width) / 2
                case .end:    return cell.maxX - item.width
                }
            }()
            // Block-axis anchor mirrors the same rules vertically.
            let jy: CGFloat = {
                switch solved.align[i] {
                case .start:  return cell.minY
                case .center: return cell.minY + (cell.height - item.height) / 2
                case .end:    return cell.maxY - item.height
                }
            }()
            // Propose the item its own intrinsic size — stretch cases
            // are pre-baked into the child via the gridStretchHeight
            // environment (see ComponentRenderer), so no cell-size
            // proposal is needed.
            sub.place(at: CGPoint(x: bounds.minX + jx, y: bounds.minY + jy),
                      anchor: .topLeading,
                      proposal: ProposedViewSize(item))
        }
    }
}

// MARK: - Stretch-height environment

/// css-align-3 §9: `align-self: stretch` (the grid default via `normal`)
/// stretches an item with an AUTO block size to its row track. SwiftUI
/// can't impose that from outside — the child's own background paints at
/// its intrinsic frame — so the renderer injects the known row height
/// into the child's environment; ComponentRenderer folds it into the
/// child's SizeConfig BEFORE the style chain paints (grid-2col/010 d).
private struct GridStretchHeightKey: EnvironmentKey {
    /// Default nil = no stretch injection.
    static let defaultValue: CGFloat? = nil
}

extension EnvironmentValues {
    /// Row-track height a grid child should stretch to (nil = none).
    var gridStretchHeight: CGFloat? {
        get { self[GridStretchHeightKey.self] }
        set { self[GridStretchHeightKey.self] = newValue }
    }
}

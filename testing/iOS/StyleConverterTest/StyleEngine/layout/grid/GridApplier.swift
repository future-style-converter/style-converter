//
//  GridApplier.swift
//  StyleEngine/layout/grid — Phase 7, step 3 (grid).
//
//  Consumes LayoutAggregate grid fields and produces:
//    • A ContainerDecision.kind (.lazyVGrid(columns: [GridItem]) for
//      ordinary grids, .grid for template-areas grids, .lazyHGrid for
//      column-auto-flow). ComponentRenderer reads this decision to pick
//      the SwiftUI container.
//    • A per-child GridPlacement describing row/column spans, exposed
//      via a PreferenceKey so a parent can lay children out by area.
//
//  SwiftUI GridItem mapping:
//    • fr            → .flexible()        (weight approximation)
//    • fixed px      → .fixed(px)
//    • percent       → .flexible()        (TODO: resolve against parent)
//    • auto          → .flexible()        (SwiftUI's default)
//    • minmax(a, b)  → .flexible(min: a, max: b)     when both px known
//                      .adaptive(minimum: a)          when max is fr
//    • adaptive(a,b) → .adaptive(minimum: a, maximum: b)
//

import SwiftUI
import CoreGraphics

enum GridApplier {

    /// Per-child grid placement produced by the extractor. Picked up by
    /// the parent container via the PreferenceKey below to lay children
    /// out against named areas / numeric lines.
    struct Placement: Equatable {
        /// 1-based CSS column lines — inclusive start, exclusive end.
        /// Nil means "auto-placed".
        var columnStart: Int?
        var columnEnd:   Int?
        /// 1-based CSS row lines.
        var rowStart: Int?
        var rowEnd:   Int?
        /// Named area (grid-area: main) — resolved against the parent's
        /// gridTemplateAreas by the container.
        var areaName: String?
    }

    // MARK: - Container decision

    /// Emit the ContainerDecision.kind for a grid aggregate. Returns nil
    /// when the aggregate doesn't describe a grid container (no template
    /// columns, no auto-flow). Callers treat nil as "not my business".
    static func containerKind(for agg: LayoutAggregate) -> ContainerDecision.ContainerKind? {
        // No template + no auto-flow → not a grid container at this level.
        // (A child with just grid-area is handled by the Placement path.)
        let hasExplicit  = agg.gridTemplateColumns != nil || agg.gridTemplateRows != nil
        let hasAreas     = agg.gridTemplateAreas != nil
        let hasAutoFlow  = agg.gridAutoFlow != nil
        let hasAutoTrack = agg.gridAutoColumns != nil || agg.gridAutoRows != nil
        guard hasExplicit || hasAreas || hasAutoFlow || hasAutoTrack else {
            return nil
        }

        // Template-areas grids need SwiftUI's iOS 16 Grid / GridRow to
        // faithfully express spanning cells. LazyVGrid can't do spans.
        if hasAreas {
            return .grid
        }

        // Column-major auto-flow → LazyHGrid. Default is LazyVGrid.
        let isHorizontal = agg.gridAutoFlow == .column || agg.gridAutoFlow == .columnDense
        if isHorizontal {
            return .lazyHGrid
        }

        // Default — column template drives LazyVGrid's `columns` array.
        return .lazyVGrid
    }

    // MARK: - LazyVGrid columns

    /// Map GridTemplateColumns to SwiftUI [GridItem] for LazyVGrid(columns:).
    /// The `spacing` argument is the column gap (resolved by the caller
    /// out of LayoutAggregate/GapConfig). Falls back to a single flexible
    /// column when the aggregate is empty — matches the old renderer.
    static func gridItems(for list: GridTrackList?, columnGap: CGFloat?) -> [GridItem] {
        guard let tracks = list?.tracks, !tracks.isEmpty else {
            // Sensible default: one flexible column. Matches the previous
            // LazyVGrid(.adaptive(minimum: 80)) fallback behaviour at the
            // axis level without locking in a min size.
            return [GridItem(.flexible(), spacing: columnGap)]
        }
        // Map each parsed track to its closest GridItem.
        return tracks.map { track in
            switch track.kind {
            case .fixed(let px):
                // Absolute size — direct GridItem(.fixed(N)).
                return GridItem(.fixed(px), spacing: columnGap)
            case .flexible(let w):
                // SwiftUI has no `weight` on GridItem; `.flexible()`
                // divides remaining space equally. Approximation —
                // weights other than 1 round down to equal. TODO: custom
                // layout for true fr weighting.
                _ = w
                return GridItem(.flexible(), spacing: columnGap)
            case .automatic, .percent:
                // Both collapse to `.flexible()` for now — neither has
                // a direct SwiftUI expression. TODO: percent needs parent
                // GeometryReader to resolve; auto needs intrinsic sizing.
                return GridItem(.flexible(), spacing: columnGap)
            case .minmax(let lo, let hi):
                // When both bounds are px we can use `.flexible(min:max:)`;
                // when the max is nil (e.g. "1fr") use `.adaptive` with
                // the min as the floor.
                if let lo = lo, let hi = hi {
                    return GridItem(.flexible(minimum: lo, maximum: hi),
                                    spacing: columnGap)
                }
                if let lo = lo {
                    return GridItem(.adaptive(minimum: lo), spacing: columnGap)
                }
                return GridItem(.flexible(), spacing: columnGap)
            case .adaptive(let lo, let hi):
                // repeat(auto-fill, minmax(a, b)) — this is THE canonical
                // SwiftUI `.adaptive` use case.
                let minv = lo ?? 50
                if let hi = hi {
                    return GridItem(.adaptive(minimum: minv, maximum: hi),
                                    spacing: columnGap)
                }
                return GridItem(.adaptive(minimum: minv), spacing: columnGap)
            }
        }
    }

    // MARK: - Per-child placement extraction

    /// Build a Placement from a component's parsed LayoutAggregate.
    /// Returns nil when no grid-placement fields were set (then the child
    /// flows into the next auto-placed cell).
    static func placement(for agg: LayoutAggregate) -> Placement? {
        var p = Placement()
        var any = false
        // grid-area: when it's a name reference, stash in areaName for
        // the parent's template-areas lookup. When it's a span / number,
        // treat as row-start (CSS allows the shorthand to encode 4 lines
        // but we only model the first here; finer parse lands with the
        // proper grid-area shorthand extractor).
        if let ga = agg.gridArea {
            if let name = ga.name {
                p.areaName = name
                any = true
            } else if let ln = ga.line {
                p.rowStart = ln
                any = true
            }
        }
        if let gc = agg.gridColumn {
            // Numeric line → columnStart. Span → columnEnd = start+span.
            if let ln = gc.line {
                p.columnStart = ln
                any = true
            }
            if let span = gc.span {
                // Without an explicit start, span resolves against auto —
                // approximate as span-from-1.
                p.columnStart = p.columnStart ?? 1
                p.columnEnd = (p.columnStart ?? 1) + span
                any = true
            }
            if let name = gc.name {
                p.areaName = p.areaName ?? name
                any = true
            }
        }
        if let gr = agg.gridRow {
            if let ln = gr.line {
                p.rowStart = ln
                any = true
            }
            if let span = gr.span {
                p.rowStart = p.rowStart ?? 1
                p.rowEnd = (p.rowStart ?? 1) + span
                any = true
            }
        }
        return any ? p : nil
    }
}

// MARK: - Placement PreferenceKey

/// Preference key used to bubble a child's grid-placement up to the
/// nearest grid container. The container reads `[Placement]` and lays
/// children into the named / numbered cells.
///
/// Only used by the template-areas (iOS 16 Grid) path today; the
/// LazyVGrid path relies on child order since it has no span support.
struct GridPlacementKey: PreferenceKey {
    /// Default is an empty list — children contribute via `reduce`.
    static var defaultValue: [GridApplier.Placement] = []
    /// Append child placements in document order.
    static func reduce(value: inout [GridApplier.Placement],
                       nextValue: () -> [GridApplier.Placement]) {
        value.append(contentsOf: nextValue())
    }
}

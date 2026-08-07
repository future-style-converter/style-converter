//
//  TableSeparatedLayout.swift
//  Renderer — wave-34 lane T (T1).
//
//  The SwiftUI adapter over the pure `TableSeparatedTracks` twins
//  (StyleEngine/table/TableSeparatedTracks.swift ↔ Compose
//  table/TableSeparatedTracks.kt): CSS 2.1 §17.6.1 separated-borders
//  track placement for one table-internal box.
//
//  ComponentRenderer's `.block` case routes here INSTEAD of the plain
//  VStack when — composed-WPT capture only (the gate lives in
//  `ComponentRenderer.tableSeparatedPlan`) — the box declares a
//  `display` of `table` / `table-row-group` / `table-row` and the
//  separated model applies to its table.
//
//  Two parameterizations cover the whole model, which is why there is
//  one Layout here and not three:
//    • table box    → axis .vertical,   band = (h, v)  (§17.6.1's outer
//                     "distance between the border of the table and the
//                     borders of the cells on the edge of the table")
//    • row group    → axis .vertical,   band = .zero   (it sits INSIDE
//                     the table's band; §2.1 makes it transparent)
//    • table row    → axis .horizontal, band = .zero   (cells run in the
//                     inline direction, separated by the h spacing)
//
//  Everything else about the box — its declared width/height frame, its
//  background, its borders, its abspos overlay — is untouched: this
//  Layout only decides where the in-flow children sit inside it.
//

// SwiftUI for the Layout protocol; the track model is view-free.
import SwiftUI

/// Separated-borders track placement (iOS 16 Layout — same availability
/// discipline as CSSFlexLayout / FloatBlockLayout / MulticolGreedyLayout).
@available(iOS 16.0, *)
struct TableSeparatedLayout: Layout {

    /// The direction successive children advance in.
    enum Axis { case vertical, horizontal }

    /// `.vertical` for the table box and every row group (rows stack in
    /// the block direction); `.horizontal` for a row (cells run in the
    /// inline direction) — the §17.6.1 fact this lane exists for.
    let axis: Axis
    /// The spacing inserted BETWEEN successive children: the vertical
    /// border-spacing on the block axis, the horizontal one on the inline
    /// axis.
    let spacing: CGFloat
    /// §17.6.1's OUTER band, inset on all four sides. Non-zero for the
    /// table box only — a row group already sits inside it, and a row's
    /// cells are flush with the row box.
    let bandWidth: CGFloat
    /// The vertical half of the outer band (the horizontal half is
    /// `bandWidth`); kept separate because `border-spacing` takes two
    /// independent lengths.
    let bandHeight: CGFloat

    /// Measure every child once with the SAME proposal the place pass
    /// uses, so the two passes cannot derive different plans (the pure
    /// FloatBlockLayout / MulticolGreedyLayout shared-plan discipline).
    ///
    /// `.unspecified` throughout: a table-internal box in this corpus
    /// carries its own declared width/height on the wire (the converter
    /// serializes the used geometry), so proposing the container's
    /// bounds would let a stretch override the wire's number. The cells
    /// of abspos-container-change-dynamic-001 declare 11.05×20 and
    /// 10.47×20 content boxes; the plan below is the arithmetic that
    /// turns those into the reference's (33,18) lime origin.
    private func sizes(_ subviews: Subviews) -> [CGSize] {
        subviews.map { $0.sizeThatFits(.unspecified) }
    }

    /// The measured child extents along the track axis (the numbers the
    /// shared arithmetic consumes) and across it (the cross extent).
    private func extents(_ measured: [CGSize]) -> (along: [Double], cross: Double) {
        switch axis {
        case .vertical:
            return (measured.map { Double($0.height) },
                    Double(measured.map(\.width).max() ?? 0))
        case .horizontal:
            return (measured.map { Double($0.width) },
                    Double(measured.map(\.height).max() ?? 0))
        }
    }

    func sizeThatFits(proposal: ProposedViewSize,
                      subviews: Subviews,
                      cache: inout ()) -> CGSize {
        // An empty table-internal box still carries its band: an empty
        // `<table>` is 2×2 in the reference. `trackExtent` of an empty
        // list is exactly `band * 2`, the honest zero-row answer (the
        // declared frame usually wins anyway).
        let (along, cross) = extents(sizes(subviews))
        // Both extents through the SHARED arithmetic — the same function
        // Compose's adapter calls, so the two natives cannot disagree
        // about a table's own size.
        let alongExtent = CGFloat(TableSeparatedTracks.trackExtent(
            extents: along, spacing: Double(spacing),
            band: Double(axis == .vertical ? bandHeight : bandWidth)))
        // Across the axis the band still applies on both edges, but there
        // is no stacking — the widest track wins.
        switch axis {
        case .vertical:
            return CGSize(width: cross + bandWidth * 2, height: alongExtent)
        case .horizontal:
            return CGSize(width: alongExtent, height: cross + bandHeight * 2)
        }
    }

    func placeSubviews(in bounds: CGRect,
                       proposal: ProposedViewSize,
                       subviews: Subviews,
                       cache: inout ()) {
        guard !subviews.isEmpty else { return }
        let (along, _) = extents(sizes(subviews))
        // The §17.6.1 origins, from the SHARED arithmetic (band first,
        // then extent + spacing per track).
        let origins = TableSeparatedTracks.trackOrigins(
            extents: along, spacing: Double(spacing),
            band: Double(axis == .vertical ? bandHeight : bandWidth))
        for (i, sub) in subviews.enumerated() where i < origins.count {
            // The cross axis stays flush at the band — a table row's
            // cells are top-aligned here (vertical-align on cells is a
            // separate, unimplemented §17.5.3 question; the corpus's
            // cells are all the same height, so nothing hides behind
            // this note).
            let point: CGPoint = axis == .vertical
                ? CGPoint(x: bounds.minX + bandWidth,
                          y: bounds.minY + CGFloat(origins[i]))
                : CGPoint(x: bounds.minX + CGFloat(origins[i]),
                          y: bounds.minY + bandHeight)
            sub.place(at: point,
                      anchor: .topLeading,
                      // Same `.unspecified` the measure pass used, so the
                      // child keeps the geometry its own size chain
                      // resolved from the wire (P7's discipline).
                      proposal: .unspecified)
        }
    }
}

// MARK: - The used-spacing environment channel

/// Environment key carrying the enclosing TABLE box's used
/// `border-spacing` down to its rows.
///
/// `border-spacing` is declared on the TABLE (CSS 2.1 §17.6.1 — it
/// applies to `table` and `inline-table` boxes and inherits), but the
/// gap it produces is placed by the ROW, one or two levels below. The
/// table publishes; the row reads. Nil means "no table ancestor
/// published one", in which case a stray `display: table-row` still lays
/// its cells out inline — with zero spacing, the CSS initial value —
/// rather than inventing a band.
private struct TableBorderSpacingKey: EnvironmentKey {
    static let defaultValue: TableSeparatedTracks.Spacing? = nil
}

extension EnvironmentValues {
    /// The nearest enclosing table box's used `border-spacing`, or nil
    /// outside any table. Written by ComponentRenderer's table region for
    /// EVERY table box (value or nil), the same always-rewrite reset
    /// discipline the containing-block channels use, so a grandparent
    /// table's spacing can never leak into an unrelated subtree.
    var tableBorderSpacing: TableSeparatedTracks.Spacing? {
        get { self[TableBorderSpacingKey.self] }
        set { self[TableBorderSpacingKey.self] = newValue }
    }
}

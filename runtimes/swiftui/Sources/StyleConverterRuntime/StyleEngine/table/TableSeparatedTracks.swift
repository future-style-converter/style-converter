//
//  TableSeparatedTracks.swift
//  StyleEngine/table — wave-34 lane T (T1).
//
//  The SEPARATED-borders track model, CSS 2.1 §17.6.1, as a pure
//  decision table. Twin: `runtimes/compose/src/main/java/com/
//  styleconverter/runtime/table/TableSeparatedTracks.kt`.
//
//  ## The measured defect (frozen wave33-final pixel evidence)
//  css-tables/abspos-container-change-dynamic-001 renders a two-cell
//  table whose second `<td>` is turned `position: relative` and given a
//  100×100 lime abspos child. The reference paints that lime box at
//  (33,18)–(132,117): the padding-box corner of the SECOND cell, which
//  sits one border-spacing right of the first cell's 13.05px border box,
//  which itself sits one border-spacing in from the table's own edge
//  (16 + 2 + 13.0469 + 2 = 33.047, and 16 + 2 = 18).
//
//  iOS painted it at (16,38)–(115,137) — SSIM 0.9489 against the ref,
//  the LAST failing css-tables cell. The abspos ANCHOR was never wrong:
//  `ComponentRenderer.absoluteOverlay` already anchors on the positioned
//  ancestor's padding box (CSS 2.2 §10.1). What was wrong is where that
//  ancestor itself sat. iOS has no table display type — `FlexboxExtractor
//  .mapDisplay` folds `TABLE`/`TABLE_ROW`/`TABLE_CELL` to `.block` — so
//  the row's two cells STACKED VERTICALLY in the plain VStack: cell 2
//  landed one full cell-height below cell 1 (16 + 22 = 38, exactly the
//  measured y) at the table's own left edge (16, exactly the measured x),
//  and the lime box faithfully followed it there.
//
//  Two §17.6.1 facts close that 17×20 px gap:
//
//   1. **Cells of a row are laid out in the INLINE direction**, separated
//      by the horizontal border-spacing. (`Arrangement.inlineRow`.)
//   2. **The distance between the border of the table box and the borders
//      of the cells on the edge of the table is the border-spacing** —
//      an outer band on the table box, not just gaps between cells.
//      (`outerBandApplies`.)
//
//  ## Why `border-spacing` is not on the wire
//  The test declares none: `border-spacing: 2px` comes from the HTML
//  Standard's rendering UA stylesheet (§15.3.3 Tables — `table { border-
//  spacing: 2px; border-collapse: separate; }`), and this converter never
//  serializes UA defaults. `meta.sourceTag` is the only sighting of a
//  bare `<table>` element, exactly as `AbsposCbUsedHeight`'s H3 lane and
//  `AbsposInsetStretch.isTableBox` already use it. The CSS-authored case
//  (`display: table` on a `<div>`) gets the CSS initial value 0 instead —
//  the UA sheet targets the ELEMENT, not the display type.
//
//  The arithmetic here is pure: no SwiftUI, no view types, so XCTest pins
//  it directly (TableSeparatedTracksTests).
//

import CoreGraphics

enum TableSeparatedTracks {

    /// The used `border-spacing` of one table box, in px.
    struct Spacing: Equatable {
        /// §17.6.1 horizontal spacing — between adjacent cells in a row,
        /// and (with `outerBandApplies`) inside the table's left/right edges.
        let horizontalPx: Double
        /// §17.6.1 vertical spacing — between adjacent rows, and inside
        /// the table's top/bottom edges.
        let verticalPx: Double

        /// The CSS initial value of `border-spacing` (§17.6.1: `0`).
        static let zero = Spacing(horizontalPx: 0, verticalPx: 0)
    }

    /// How a box of a given role arranges its in-flow children under the
    /// separated model.
    enum Arrangement {
        /// Not a track-forming box — the caller keeps its normal path.
        case none
        /// Rows stack in the BLOCK direction, separated by the vertical
        /// border-spacing (§17.6.1). Used by the table box and by every
        /// row group, which is transparent for row ordering (§2.1).
        case blockStack
        /// Cells lay out in the INLINE direction, separated by the
        /// horizontal border-spacing (§17.6.1). This is the fix.
        case inlineRow
    }

    /// The HTML Standard rendering UA stylesheet's `border-spacing` for a
    /// `<table>` ELEMENT (HTML §15.3.3 Tables). Not a CSS initial value —
    /// `border-spacing`'s initial value is 0 (CSS 2.1 §17.6.1).
    static let htmlUaBorderSpacingPx: Double = 2

    /// The element names the UA sheet's `table` rule matches. `<table>`
    /// only: the HTML UA rule is written against that element, and
    /// `border-spacing` inherits, so no other tag needs to claim it.
    private static let uaTableTags: Set<String> = ["table"]

    /// The used `border-spacing` for a TABLE box, or nil when the
    /// separated model does not apply to it.
    ///
    /// nil when `border-collapse: collapse` — §17.6.2's collapsing model
    /// has no border-spacing at all ("the `border-spacing` property is
    /// ignored"), so a nil answer tells the caller to leave the box on its
    /// existing path rather than to insert zero-width tracks.
    ///
    /// - Parameters:
    ///   - properties: the table box's own resolved declarations.
    ///   - sourceTag: its `meta.sourceTag`, for the HTML UA default lane.
    static func usedSpacing(properties: [IRProperty], sourceTag: String?) -> Spacing? {
        // §17.6.2 — the collapsing model ignores border-spacing entirely.
        // Read the raw keyword through the shared decoder; anything other
        // than `collapse` (including an absent declaration) is `separate`,
        // which is both the CSS initial value and the HTML UA value.
        if let collapse = properties.first(where: { $0.type == "BorderCollapse" })?.data,
           ValueExtractors.normalize(ValueExtractors.extractKeyword(collapse)) == "COLLAPSE" {
            return nil
        }
        // An AUTHOR-declared border-spacing always wins over the UA sheet
        // (CSS 2.1 §6.4.1 cascade order: author beats user agent).
        if let declared = properties.first(where: { $0.type == "BorderSpacing" })?.data,
           let spacing = declaredSpacing(declared) {
            return spacing
        }
        // No declaration: the HTML UA sheet's 2px for a real `<table>`
        // element, the CSS initial 0 for everything else (a `display:
        // table` div is NOT matched by the UA rule).
        if let tag = sourceTag?.lowercased(), uaTableTags.contains(tag) {
            return Spacing(horizontalPx: htmlUaBorderSpacingPx,
                           verticalPx: htmlUaBorderSpacingPx)
        }
        return .zero
    }

    /// Decode the wire's `BorderSpacing` payload.
    ///
    /// The converter emits exactly two shapes (verified against
    /// `converter/…/irmodels/properties/table/BorderSpacingProperty.kt`):
    ///   • `{"type":"single","px":4}`
    ///   • `{"type":"two-values","horizontal":{"px":3},"vertical":{"px":5}}`
    /// Anything else (a `calc()`/`em` that stayed unresolved, i.e. the
    /// wire's `null`-means-runtime-dependent contract) answers nil, and
    /// the caller falls through to the UA/initial lane rather than
    /// inventing a number.
    private static func declaredSpacing(_ data: IRValue) -> Spacing? {
        guard case .object(let o) = data else { return nil }
        // Two-values form: independent horizontal / vertical lengths.
        if let h = ValueExtractors.extractPx(o["horizontal"]),
           let v = ValueExtractors.extractPx(o["vertical"]) {
            return Spacing(horizontalPx: Double(h), verticalPx: Double(v))
        }
        // Single form: one length used on both axes (§17.6.1).
        if let single = ValueExtractors.extractPx(data) {
            return Spacing(horizontalPx: Double(single), verticalPx: Double(single))
        }
        return nil
    }

    /// How a box of this role arranges its in-flow children (§17.6.1).
    static func arrangement(_ role: TableBoxTree.Role) -> Arrangement {
        switch role {
        // The table box stacks its rows / row groups in the block
        // direction; a row group is transparent for row ordering (§2.1)
        // and therefore stacks identically.
        case .table, .rowGroup: return .blockStack
        // The one behavioural change this module exists for.
        case .row: return .inlineRow
        // A cell / caption establishes a BLOCK CONTAINER for its contents
        // (§2.1) — it does not form tracks of its own.
        case .cell, .caption, .none: return .none
        }
    }

    /// Does this role carry the OUTER spacing band — §17.6.1's "distance
    /// between the border of the table box and the borders of the cells on
    /// the edge of the table"?
    ///
    /// The table box only. A row group sits INSIDE that band, so adding it
    /// there again would double-count the edge spacing.
    static func outerBandApplies(_ role: TableBoxTree.Role) -> Bool {
        role == .table
    }

    // MARK: - Track arithmetic

    /// The along-axis origins of consecutive tracks, relative to the
    /// container's own content origin.
    ///
    /// One band in from the edge, then each track's extent plus one
    /// spacing before the next — CSS 2.1 §17.6.1's two distances in one
    /// scan. This is the arithmetic that turns
    /// abspos-container-change-dynamic-001's two cells (13.0469 and
    /// 12.4688 border-box widths, 2px spacing, 2px band on the table one
    /// level up) into the reference's cell-2 x of 33.047.
    ///
    /// Shared by both natives so a row's cell origins cannot drift
    /// between them; the platform adapters only translate the result
    /// into `place(at:)` / `Placeable.place()`.
    static func trackOrigins(extents: [Double], spacing: Double, band: Double) -> [Double] {
        var out: [Double] = []
        out.reserveCapacity(extents.count)
        // The first track starts one band in from the content edge.
        var cursor = band
        for e in extents {
            out.append(cursor)
            // Advance past this track and the inter-track spacing.
            cursor += e + spacing
        }
        return out
    }

    /// The container's own extent along the track axis: every track, the
    /// n−1 gaps between them, and the band on BOTH edges.
    static func trackExtent(extents: [Double], spacing: Double, band: Double) -> Double {
        let gaps = spacing * Double(max(0, extents.count - 1))
        return extents.reduce(0, +) + gaps + band * 2
    }
}

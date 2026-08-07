//
//  CollapsedBorderConflict.swift
//  StyleEngine/table — wave-34 lane T (T2).
//
//  CSS 2.1 §17.6.2.1 BORDER CONFLICT RESOLUTION, as a pure decision
//  table. Twin: `runtimes/compose/src/main/java/com/styleconverter/
//  runtime/table/CollapsedBorderConflict.kt`.
//
//  ## The measured gap (frozen wave33-final pixel evidence)
//  CSS2/borders/border-conflict-style-107 is the worst-scoring test in the
//  frozen corpus: web-ref 0.782, android-ref 0.2825, ios-ref 0.2881. It
//  floats SIXTEEN `border-collapse: collapse` tables, each a single empty
//  cell, and puts a 25px `solid green` border on the element that CSS says
//  wins and a 25px `solid red` border on the one that loses. The reference
//  is one filled 200×200 green square (four tables per row, four rows) and
//  NO red at all.
//
//  Both natives paint 390×1404 canvases carrying 288 500 red pixels and
//  176 900 green — ink coverage 85.4% against the reference's 7.755%. The
//  captures show why: every one of `<table>`/`<tbody>`/`<tr>`/`<td>` paints
//  its OWN 25px border as an ordinary block box, nested one inside the
//  next, and the sixteen tables stack vertically instead of packing four to
//  a row. There is no collapsing model anywhere in either runtime — not the
//  conflict resolution, not the shared-grid-line geometry.
//
//  This file is the first of those two halves: given every border that
//  meets at one grid line, WHICH one paints. It is deliberately pure — no
//  SwiftUI, no colour type, no geometry — so it pins under XCTest and
//  stays byte-parallel with the Kotlin twin.
//
//  ## Honest scope (what this does NOT do yet)
//  Landing this module does not move border-conflict-style-107's score,
//  and this comment exists so nobody reads the test list and assumes it
//  did. Three things still stand between here and that green square, none
//  of them in this lane's owned regions:
//    1. the collapsed BOX MODEL — a collapsed table's used size is the
//       cell content plus the centred border halves (`centeredHalves`),
//       which neither runtime computes;
//    2. FLOAT packing of the sixteen `float: left` tables plus the
//       `br { clear: both }` breaks, which is why the natives' canvas is
//       1404px tall instead of 600;
//    3. the resolver's INPUTS — the `<col>` / `<colgroup>` / row-group
//       declarations reach a cell edge only once the renderers walk the
//       table box tree, and on iOS these tables carry no `Display` at all
//       (only `meta.sourceTag`), so `tableTrackPlan` never sees them.
//  What IS live: the decision table below, pinned on both natives against
//  the sixteen cases the test itself enumerates.
//

/// CSS 2.1 §17.6.2.1 — "Border conflict resolution".
///
/// > Borders are resolved as follows: … the border styles of the cells,
/// > rows, row groups, columns, column groups and the table itself that
/// > meet at each edge are compared, and the one that wins is painted.
enum CollapsedBorderConflict {

    /// The `border-style` keywords §17.6.2.1 ranks, worst to best.
    ///
    /// `hidden` is NOT part of the rank — rule 1 removes the edge entirely
    /// before ranking starts — but it is a member of the enum because it
    /// is a real keyword the wire can carry, and giving it a rank would
    /// let a caller accidentally treat it as "the strongest style" instead
    /// of "no border at all".
    enum Style {
        /// Rule 1: suppresses every other border at this location.
        case hidden
        /// Rule 2: lowest priority; an all-`none` edge paints nothing.
        case none
        // Rule 3's preference order, strongest first.
        case double, solid, dashed, dotted, ridge, outset, groove, inset
    }

    /// The element a border declaration came from — rule 4's precedence
    /// chain, strongest first.
    enum Origin {
        case cell, row, rowGroup, column, columnGroup, table
    }

    /// One border declaration meeting at a grid line.
    struct Edge: Equatable {
        /// Its used `border-style`.
        let style: Style
        /// Its used `border-width` in px. `none`/`hidden` compute to 0 per
        /// §8.5.3, but the width is kept independent here so the caller can
        /// hand over exactly what the wire said and let the rules — not the
        /// caller — decide what that means.
        let widthPx: Double
        /// Which element declared it (rule 4).
        let origin: Origin
        /// Its position in document order among declarations of the SAME
        /// origin — rule 4's final tie-break, "the one further to the left
        /// … and further to the top wins". Smaller is earlier.
        let order: Int

        init(style: Style, widthPx: Double, origin: Origin, order: Int = 0) {
            self.style = style
            self.widthPx = widthPx
            self.origin = origin
            self.order = order
        }
    }

    /// Rule 3's style preference, strongest first:
    /// `double`, `solid`, `dashed`, `dotted`, `ridge`, `outset`, `groove`,
    /// `inset`. Lower is stronger. `none`/`hidden` never reach the ranking
    /// (rules 2 and 1 handle them), so they sit past the end of the scale.
    static func styleRank(_ style: Style) -> Int {
        switch style {
        case .double: return 0
        case .solid:  return 1
        case .dashed: return 2
        case .dotted: return 3
        case .ridge:  return 4
        case .outset: return 5
        case .groove: return 6
        case .inset:  return 7
        // Off the scale on purpose — see the enum's doc.
        case .none, .hidden: return 8
        }
    }

    /// Rule 4's origin precedence: "a style set on a cell wins over one on
    /// a row, which wins over a row group, column, column group and,
    /// lastly, table". Lower is stronger.
    static func originRank(_ origin: Origin) -> Int {
        switch origin {
        case .cell:        return 0
        case .row:         return 1
        case .rowGroup:    return 2
        case .column:      return 3
        case .columnGroup: return 4
        case .table:       return 5
        }
    }

    /// The winning declaration's INDEX in `edges`, or nil when nothing is
    /// painted at this grid line.
    ///
    /// Returning an index rather than a resolved border keeps this module
    /// free of any colour or unit type, so the two natives can share one
    /// decision table and each read its own `BorderTopColor` off the
    /// winner. Rule 4 is expressed exactly that way in the spec — "if
    /// border styles differ only in color" — the rules never inspect the
    /// colour, they only decide whose colour paints.
    ///
    /// The four rules, in the spec's own order:
    ///  1. any `hidden` suppresses the whole edge → nil;
    ///  2. `none` has the lowest priority, and an all-`none` edge paints
    ///     nothing → nil (a zero used width is treated the same: §8.5.3
    ///     gives `none` a computed width of 0, so a 0-width declaration
    ///     carries no border to win with);
    ///  3. wider wins; on a width tie, the better style rank wins;
    ///  4. on a style tie, the stronger origin wins; on an origin tie, the
    ///     earlier declaration in document order wins.
    static func winner(_ edges: [Edge]) -> Int? {
        // Rule 1 — one `hidden` anywhere removes the edge for everyone.
        if edges.contains(where: { $0.style == .hidden }) { return nil }
        var bestIndex: Int?
        var best: Edge?
        for (i, e) in edges.enumerated() {
            // Rule 2 — `none` never wins, and neither does a declaration
            // whose used width is zero: there is no border there to paint.
            if e.style == .none || e.widthPx <= 0 { continue }
            guard let b = best, let bi = bestIndex else {
                best = e
                bestIndex = i
                continue
            }
            if beats(e, i, b, bi) {
                best = e
                bestIndex = i
            }
        }
        // Rule 2's tail — every declaration was `none` (or zero-width).
        return bestIndex
    }

    /// Does declaration `a` (at index `ai`) beat the current best `b` (at
    /// index `bi`)? Rules 3 and 4, applied in order and short-circuiting on
    /// the first difference — the shape a `Comparator` would have if this
    /// file were allowed to allocate one per edge.
    private static func beats(_ a: Edge, _ ai: Int, _ b: Edge, _ bi: Int) -> Bool {
        // Rule 3, first half — "narrow borders are discarded in favor of
        // wider ones".
        if a.widthPx != b.widthPx { return a.widthPx > b.widthPx }
        // Rule 3, second half — "if several have the same border-width
        // then styles are preferred in this order: double, solid, dashed,
        // dotted, ridge, outset, groove, and the lowest: inset".
        let ra = styleRank(a.style)
        let rb = styleRank(b.style)
        if ra != rb { return ra < rb }
        // Rule 4, first half — cell > row > row group > column >
        // column group > table.
        let oa = originRank(a.origin)
        let ob = originRank(b.origin)
        if oa != ob { return oa < ob }
        // Rule 4, second half — "when two elements of the same type
        // conflict, then the one further to the left … and further to the
        // top wins". The caller supplies that as document order.
        if a.order != b.order { return a.order < b.order }
        // Fully tied: keep the FIRST one seen, so the resolution is
        // deterministic and both natives pick the same declaration.
        return ai < bi
    }

    // MARK: - Wire → enum decoding

    /// A `border-*-style` keyword from the wire → `Style`.
    ///
    /// The IR emits the CSS keyword uppercased (`"SOLID"`, `"DOUBLE"`).
    /// Unknown keywords answer `.none` rather than trapping: §17.6.2.1
    /// only ever needs to know whether a declaration can win, and one it
    /// cannot name cannot win.
    static func styleOf(_ keyword: String?) -> Style {
        switch keyword?.uppercased() {
        case "HIDDEN": return .hidden
        case "DOUBLE": return .double
        case "SOLID":  return .solid
        case "DASHED": return .dashed
        case "DOTTED": return .dotted
        case "RIDGE":  return .ridge
        case "OUTSET": return .outset
        case "GROOVE": return .groove
        case "INSET":  return .inset
        default:       return .none
        }
    }

    /// An element's `meta.sourceTag` → the `Origin` its border
    /// declarations carry, or nil for a tag that is not a table-internal
    /// element.
    ///
    /// This is the ONE place the tag channel is authoritative for tables:
    /// §17.6.2.1's rule 4 is written in terms of the ELEMENTS (cell, row,
    /// row group, column, column group, table), and `<col>` / `<colgroup>`
    /// generate no boxes at all (css-tables-3 §2.1), so a display keyword
    /// could never identify them.
    static func originOf(sourceTag: String?) -> Origin? {
        switch sourceTag?.lowercased() {
        case "td", "th":               return .cell
        case "tr":                     return .row
        case "tbody", "thead", "tfoot": return .rowGroup
        case "col":                    return .column
        case "colgroup":               return .columnGroup
        case "table":                  return .table
        default:                       return nil
        }
    }

    // MARK: - Collapsed geometry (§17.6.2)

    /// The two halves of a collapsed border, which "is centered on the
    /// grid line" (§17.6.2).
    ///
    /// - Returns: `(inner, outer)` in px — the part painted on the CELL
    ///   side of the grid line and the part painted on the other side. The
    ///   split is exact halves; the caller rounds when it rasterises.
    ///
    /// This is the arithmetic border-conflict-style-107's geometry needs:
    /// its `<td>` has zero content and a 25px collapsed border, so the cell
    /// box is 0 + 12.5 + 12.5 = 25, the table box adds the outer halves at
    /// its own edges for 25 + 12.5 + 12.5 = 50, and four such tables
    /// floated in a row make the reference's 200×200 square exactly.
    static func centeredHalves(_ widthPx: Double) -> (inner: Double, outer: Double) {
        let half = widthPx / 2
        return (half, half)
    }
}

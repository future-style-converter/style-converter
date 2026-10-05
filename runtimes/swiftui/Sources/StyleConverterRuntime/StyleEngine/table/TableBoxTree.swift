//
//  TableBoxTree.swift
//  StyleEngine/table — wave-34 lane T.
//
//  The iOS half of the css-tables-3 §2.1 ROLE classification, twin of
//  `runtimes/compose/src/main/java/com/styleconverter/runtime/table/
//  TableBoxTree.kt` (wave 32, lane P).
//
//  ## What is ported, and what deliberately is not
//  Compose's twin carries four members. Two of them exist only to serve
//  Compose-side machinery that iOS does not have, so porting them would
//  create dead code that could silently drift:
//
//   • `rowsOf(children)` — the row-group SPLICE. Compose's
//     `ComponentRenderer.RenderTableContent` renders GRANDCHILDREN
//     directly (`RenderComponent(cellComponent)`), so an implied
//     `<tbody>` shifts every real `<td>` one level out of that loop's
//     reach and has to be spliced away. iOS has no such synthesizer: its
//     `ComponentRenderer` renders the REAL IR tree one level at a time,
//     so the row group renders itself and its rows stay exactly where the
//     wire put them. Nothing to splice.
//   • `establishesContainingBlock(component)` — Compose's
//     `CanvasRootHoist` mirror invariant (its composition side and its
//     pure walk must answer the positioned-ancestor question
//     identically). iOS anchors an abspos child on the ancestor's own
//     `.overlay` (ComponentRenderer.absoluteOverlay), so the question is
//     answered structurally by the view tree and there is no second
//     answer to keep in step.
//
//  What DOES port is the classification itself — the decision table
//  below is byte-parallel with the Kotlin `when`, keyword for keyword,
//  because both runtimes must agree on what a table-internal box IS.
//

// SwiftUI for the M-E environment channel (EnvironmentKey / CGFloat).
import SwiftUI

/// The css-tables-3 §2.1 internal table roles this runtime distinguishes.
enum TableBoxTree {

    /// `none` means "not a table-internal box" — every non-table display
    /// and every component with no `Display` declaration at all.
    enum Role {
        case table, rowGroup, row, cell, caption, none
    }

    /// Classify one component's DECLARED `display`.
    ///
    /// Reads the raw wire keyword through `ValueExtractors.extractKeyword`
    /// — the same decoder every other iOS extractor uses — and accepts
    /// both the underscore spelling the IR emits (`TABLE_ROW_GROUP`) and
    /// the CSS hyphen spelling (`table-row-group`), because the wire has
    /// carried both across waves and neither is worth a normalization pass.
    ///
    /// `inline-table` folds to `.table`: css-tables-3 §2.1 gives it the
    /// identical internal box tree and differs only in outer display,
    /// which this runtime expresses through the surrounding flow.
    ///
    /// DECLARED-KEYWORD ONLY, deliberately — the UA tag channel wave 34
    /// deferred here now lives NEXT DOOR, in `uaRoleOf` /
    /// `roleOf(_:sourceTag:)`, so a caller opts into it explicitly and this
    /// function keeps answering exactly what it answered when the frozen
    /// captures were measured.
    static func roleOf(_ properties: [IRProperty]) -> Role {
        // `Display` is the only wire that carries a table role; absent ⇒ none.
        // FIRST match, byte-parallel with the Kotlin twin's `firstOrNull` —
        // the converter emits at most one `Display` per component, so the
        // choice is only ever visible on a hand-authored duplicate, and the
        // two runtimes must pick the same one when it is.
        guard let raw = properties.first(where: { $0.type == "Display" })?.data else {
            return .none
        }
        // Normalize hyphen→underscore so one `switch` covers both spellings.
        let kw = ValueExtractors.normalize(ValueExtractors.extractKeyword(raw))
        switch kw {
        case "TABLE", "INLINE_TABLE":
            return .table
        // §2.1 lists three group boxes; all three are transparent for row
        // ordering, so they share one role.
        case "TABLE_ROW_GROUP", "TABLE_HEADER_GROUP", "TABLE_FOOTER_GROUP":
            return .rowGroup
        case "TABLE_ROW":
            return .row
        // A column / column-group generates no cell boxes of its own
        // (§2.1) — it only carries column styling this runtime does not
        // model yet, so it classifies as `none` and renders as ordinary
        // content rather than being silently swallowed.
        case "TABLE_CELL":
            return .cell
        case "TABLE_CAPTION":
            return .caption
        default:
            return .none
        }
    }

    // MARK: - The HTML UA display channel (wave 38, lane N2)

    /// The role the HTML UA stylesheet gives a source ELEMENT, or `.none`
    /// for a tag that is not table-internal. Twin of the Kotlin
    /// `TableBoxTree.uaRoleOf`, tag for tag.
    ///
    /// ## Why this channel has to exist
    /// `roleOf` reads the declared `Display` and nothing else, and the
    /// doc above records why wave 34 stopped there. The css-tables corpus
    /// is HTML, not CSS: its tables are ordinary
    /// `<table>`/`<tr>`/`<td>` markup with no author `display`, and the
    /// display they DO have comes from the HTML Standard's rendering
    /// section (§15.3.8 Tables) — `table { display: table }`,
    /// `tr { display: table-row }`, `td, th { display: table-cell }`,
    /// `tbody/thead/tfoot { display: table-*-group }`,
    /// `caption { display: table-caption }`. The converter does not ship
    /// the UA sheet, so that display never reaches the wire and
    /// `meta.sourceTag` is its only sighting — the same channel
    /// `TableSeparatedTracks.usedSpacing` already reads for the UA
    /// `border-spacing`. (Retro sweep P2b, A6#11: this sentence used to
    /// name a second reader, `CollapsedBorderConflict.originOf`, the CSS
    /// 2.1 §17.6.2.1 conflict-resolution decision table. That module never
    /// had a production caller on EITHER native and was deleted with its
    /// tests on both — 14 JVM tests on Compose (sweep P2a), 14 XCTest
    /// methods here; the collapsed-border gap is a table-LAYOUT gap, not a
    /// conflict-ranking one.)
    ///
    /// MEASURED (frozen wave37-final,
    /// `tools/titan/runs/wave37-final/sections/css-tables`): 122 of the
    /// section's table-internal boxes carry a table `meta.sourceTag` and
    /// NO table `Display`, so `roleOf` answers `.none` for them and every
    /// `<table>` renders as a plain block stack.
    /// `border-collapse-empty-cell` is the clearest picture — a 2×2 grid
    /// of 50×50 bordered cells whose reference is a 2×2 square, captured
    /// on BOTH natives as a 1×4 VERTICAL column (iOS/Android ssim 0.9073
    /// against the ref, web 1.0000).
    ///
    /// `col` / `colgroup` are deliberately absent: css-tables-3 §2.1
    /// gives them no cell boxes at all — see `generatesNoBoxes`.
    static func uaRoleOf(_ sourceTag: String?) -> Role {
        switch sourceTag?.lowercased() {
        case "table": return .table
        // §2.1's three group boxes share one role, exactly as in `roleOf`.
        case "tbody", "thead", "tfoot": return .rowGroup
        case "tr": return .row
        case "td", "th": return .cell
        case "caption": return .caption
        default: return .none
        }
    }

    /// The role of a box, reading the DECLARED `display` first and falling
    /// back to `uaRoleOf` only when the wire declared none.
    ///
    /// The precedence is the cascade's own: an author `display` beats the
    /// UA sheet, so a `<table style="display:block">` is a block box and a
    /// `<div style="display:table">` is a table. Falling back ONLY on the
    /// absent-`Display` case is what makes this additive — every box that
    /// already had a declared table role keeps the byte-identical
    /// classification wave 32/34 measured, and the only behaviour that
    /// moves is the `.none` answer this replaces.
    ///
    /// - Parameter sourceTag: the box's `meta.sourceTag`. Passing nil
    ///   reproduces `roleOf` exactly.
    static func roleOf(_ properties: [IRProperty], sourceTag: String?) -> Role {
        // A declared `display` — table or not — is authoritative: the UA
        // sheet is the LOWEST-priority origin, so it may only fill a gap.
        guard !properties.contains(where: { $0.type == "Display" }) else {
            return roleOf(properties)
        }
        return uaRoleOf(sourceTag)
    }

    /// Does this SOURCE TAG generate no boxes of its own inside a table?
    ///
    /// css-tables-3 §2.1: a `table-column` / `table-column-group` box
    /// "does not render" — it carries column styling and nothing else.
    /// Keyed on the TAG rather than the role on purpose, byte-parallel
    /// with the Kotlin twin: a DECLARED `display: table-column` keeps its
    /// exact pre-wave-38 passthrough (the shape
    /// `css-tables/border-collapse-dynamic-col-001` carries), while the
    /// UA-only `<col>` — which has no other reason to be in the box tree
    /// — is skipped by the caller.
    ///
    /// ONE iOS CALLER SINCE WAVE 52 (lane L8, M-E): `columnChains` uses it
    /// to find the UA-only column boxes whose `width` it harvests. It is
    /// still NOT wired into `inFlowChildren` — stated plainly rather than
    /// left to be discovered (wave-38 finish pass F1). The Kotlin twin needs
    /// it there because Compose SPLICES a row group's children into one flat row
    /// list (`TableBoxTree.rowsOf`), so a `<colgroup>` would land in that
    /// list and paint as a row of cells; this runtime never splices —
    /// `tableTrackPlan` gives the row group its own `.blockStack` plan and
    /// recurses — so a column box simply arrives as an ordinary, empty
    /// child track. MEASURED (finish pass F1, fresh 48-test css-tables
    /// re-run on the wave-38 tree): `border-collapse-dynamic-col-001`, the
    /// ONLY corpus test carrying `col`/`colgroup`, scored 0.9404 — the
    /// frozen wave37-final number to four places, i.e. the un-dropped
    /// column box costs this platform nothing today. Wiring it into
    /// `inFlowChildren` is therefore a MEASURED change, not a free one,
    /// and is deliberately left for a lane that can re-score it.
    static func generatesNoBoxes(_ sourceTag: String?) -> Bool {
        let t = sourceTag?.lowercased()
        return t == "col" || t == "colgroup"
    }

    /// Is a box with this role SHRINK-TO-FIT rather than a CSS 2.1
    /// §10.3.3 block-level fill?
    ///
    /// CSS 2.1 §17.5.2 / css-tables-3 §5: a table box with `width: auto`
    /// uses the table layout algorithm, whose used width is
    /// `max(min-content, min(max-content, available))` — it hugs its
    /// columns and only reaches the containing block when its content is
    /// that wide. It is NOT §10.3.3's "width:auto fills the containing
    /// block", which is what both runtimes' composed-WPT block-fill
    /// channels (iOS `wptChildFillWidth`, Compose `blockFlowWidth`)
    /// implement for ordinary block boxes.
    ///
    /// MEASURED (frozen wave37-final): `css-tables/background-clip-001` is
    /// a single `<td>` holding a 40×40 inline-block inside 30px collapsed
    /// borders, so the table is exactly 100×100 and the reference paints
    /// 10 000 green pixels. Both natives painted 35 800 — a 358×100 bar,
    /// the full composed-canvas content width — because the table consumed
    /// the block-fill channel. `box-shadow-001` and the three
    /// `height-distribution/extra-height-given-to-all-row-groups-00{1,2,5}`
    /// tests are the same picture and the same three ink counts.
    ///
    /// A ROW is deliberately NOT shrink-to-fit: §17.5.2 sizes rows to the
    /// table's used width.
    static func shrinkToFitBox(_ role: Role) -> Bool { role == .table }

    // MARK: - Wave 52 lane L8 (M-E): column width contributions

    // css-tables-3 §2.1: a column box "does not render", but §3.2 makes its
    // specified `width` an input to the column's min/max width. MEASURED on
    // wave51-fix css-writing-modes/ch-units-vrl-003/-004: the upright
    // `<col style="width: 5ch">` (120) never reached the green `<td>`, which
    // stayed 6 px wide on both natives (ref 120×120). Twin of the Kotlin
    // `TableBoxTree.columnChains` / `columnWidthsPx` — same chains, same
    // exclusion of a DECLARED `display` (css-tables/border-collapse-dynamic-
    // col-001's `table-column` boxes harvest nothing, so it cannot move).

    /// The table's column boxes in column order, each as the chain its width
    /// is read from: `[col]`, `[col, colgroup]` (group width = fallback), or
    /// `[colgroup]` for an empty group. UA-only boxes (no declared Display).
    static func columnChains(_ children: [IRComponent]?) -> [[IRComponent]] {
        // The same tag rule `generatesNoBoxes` states, with no declared role.
        let uaColumn = { (c: IRComponent) -> Bool in
            !c.properties.contains { $0.type == "Display" } && generatesNoBoxes(c.meta?.sourceTag)
        }
        var out: [[IRComponent]] = []
        for child in (children ?? []) where uaColumn(child) {
            // A `<colgroup>` with `<col>` children: one column per col.
            let cols = (child.children ?? []).filter { uaColumn($0) && $0.meta?.sourceTag?.lowercased() == "col" }
            if child.meta?.sourceTag?.lowercased() == "colgroup", !cols.isEmpty {
                out += cols.map { [$0, child] }
            } else {
                // A bare `<col>`, or an empty `<colgroup>`: one column.
                out.append([child])
            }
        }
        return out
    }

    /// Each column's specified width in px (nil = auto), in column order.
    /// `inherited` is the table's inheritable set — the column box inherits
    /// `font-size` / `writing-mode` like any element (the ch-units-vrl tests
    /// assert exactly that), and StyleBuilder measures its `ch` along the
    /// column's OWN inline axis (upright col: 5ch = 120).
    static func columnWidthsPx(_ children: [IRComponent]?, inherited: [IRProperty]) -> [CGFloat?] {
        columnChains(children).map { chain in
            // The col's own width first, the group's as the fallback.
            chain.lazy.compactMap { columnWidthPx($0, inherited: inherited) }.first
        }
    }

    /// One column box's declared `width` in px through the production
    /// StyleBuilder + SpacingResolver lane, or nil (no width / unresolvable).
    static func columnWidthPx(_ column: IRComponent, inherited: [IRProperty]) -> CGFloat? {
        // Own declarations win; inherited types fill the gaps.
        let style = StyleBuilder.build(from: InheritedText.merge(own: column.properties, inherited: inherited))
        guard let width = style.size.width else { return nil }
        // `isPadding: true` = never negative, `auto` = nothing to give.
        guard case .px(let px) = SpacingResolver.resolve(width, ctx: style.spacing.context, isPadding: true),
              px.isFinite, px > 0 else { return nil }
        return px
    }

    /// The renderer's lookup: cell component id → its column's width, for
    /// every cell of every row (row groups looked through, §2.1), by the
    /// cell's index in its row. Empty when the table has no column widths.
    static func cellColumnWidths(table: IRComponent, inherited: [IRProperty]) -> [String: CGFloat] {
        let widths = columnWidthsPx(table.children, inherited: inherited)
        guard widths.contains(where: { $0 != nil }) else { return [:] }
        var map: [String: CGFloat] = [:]
        // Rows: direct table-row children, or the rows inside a row group.
        let rows = (table.children ?? []).flatMap { child -> [IRComponent] in
            roleOf(child.properties, sourceTag: child.meta?.sourceTag) == .rowGroup ? (child.children ?? []) : [child]
        }.filter { roleOf($0.properties, sourceTag: $0.meta?.sourceTag) == .row }
        for row in rows {
            for (i, cell) in (row.children ?? []).enumerated() where i < widths.count {
                if let w = widths[i] { map[cell.id] = w }
            }
        }
        return map
    }
}

/// Environment channel for M-E: the enclosing table's `cellColumnWidths`
/// map. Published by the TABLE's ComponentRenderer (seam patch), read by
/// each cell, which folds its column's width into an unset `min-width`.
/// Default empty = no table above → nothing to fold (byte-identical).
private struct TableCellColumnWidthsKey: EnvironmentKey {
    static let defaultValue: [String: CGFloat] = [:]
}

extension EnvironmentValues {
    /// Cell id → its column's specified width (css-tables-3 §3.2), in px.
    var tableCellColumnWidths: [String: CGFloat] {
        get { self[TableCellColumnWidthsKey.self] }
        set { self[TableCellColumnWidthsKey.self] = newValue }
    }
}

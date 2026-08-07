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
    /// DECLARED-KEYWORD ONLY, deliberately. `meta.sourceTag` would also
    /// identify a `<tr>`/`<td>`, and the HTML UA sheet does give them
    /// table displays — but reading it here would silently re-classify 17
    /// frozen WPT captures (every `<table>` in CSS2, css-backgrounds and
    /// css-tables) rather than the one this classification was measured
    /// against. The tag channel is used for the UA `border-spacing`
    /// default ONLY (TableSeparatedTracks.usedSpacing), on boxes this
    /// function has already admitted.
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

    /// Does a box with this role render its content as an ordinary BLOCK
    /// CONTAINER rather than re-entering table layout?
    ///
    /// css-tables-3 §2.1: a `table-cell` box "establishes a block
    /// container box for its contents", and a `table-caption` is likewise
    /// a block container. iOS already reaches this outcome structurally —
    /// `FlexboxExtractor.mapDisplay` folds every unrecognized keyword
    /// (including `TABLE_CELL`) to `.block` — so this predicate exists to
    /// keep the two runtimes' decision tables readable side by side and
    /// to give the layout gate below one named place to ask.
    static func rendersAsBlockContainer(_ role: Role) -> Bool {
        role == .cell || role == .caption
    }
}

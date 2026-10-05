package com.styleconverter.runtime.table

// Wave 32 (lane P) — the CSS table BOX TREE, as a pure decision table.
//
// ## The measured defect (frozen wave31-final pixel evidence)
// `ComponentRenderer.RenderTableContent` implemented exactly one shape:
// "my children are rows, my grandchildren are cells". Two things in the
// real wire break that assumption, and both DROPPED a box:
//
//  1. **Row groups.** HTML parsing inserts an implied `<tbody>`, so the
//     extracted wire is table → TABLE_ROW_GROUP → TABLE_ROW → TABLE_CELL —
//     four levels, not three. The synthesizer then read the *tbody* as a
//     row and the *tr* as a cell, and every real `<td>` slid one level
//     deeper than the code that looks for it. MEASURED on
//     css-tables/abspos-container-change-dynamic-001: Android's capture
//     carries 160 non-white pixels total (just the "A"/"B" glyphs) against
//     the reference's 10 070 — the lime 100×100 abspos never painted at all.
//
//  2. **Cells are block containers, not tables.** css-tables-3 §2.1: a
//     `table-cell` box establishes a block container for its content; it
//     does NOT re-enter table layout. The old mapping sent TABLE_CELL to
//     `DisplayType.TABLE`, so a cell re-ran the row/cell synthesizer over
//     its own content: a child-less child became `PlaceholderContent`
//     (a text run — no background, no box), which is why
//     CSS2/abspos/static-inside-table-cell painted NEITHER the green
//     abspos NOR the red decoy on Android while iOS painted green.
//
// ## Why the drop is total rather than merely misplaced
// The canvas hoist keeps TWO mirrored decisions in step — the composition
// side (`ComponentRenderer.RenderComponent` → `CanvasRootHoist
// .interceptsInFlow`) and the pure walk (`CanvasRootHoist
// .collectCanvasHoisted`). Both ask "does this ABSOLUTE box have a
// positioned ancestor?", and `CanvasRootHoist`'s own kdoc states the
// invariant: "the two MUST mirror each other or a component gets dropped
// from flow but never overlaid."
//
// `RenderTableContent` broke the mirror. It renders GRANDCHILDREN with
// `RenderComponent(cellComponent)` — so the intermediate row/cell
// component's own `RenderComponent` never runs, and the
// `LocalHasPositionedAncestor` provider it would have installed never
// exists. The pure walk meanwhile reads the real IR tree and sees the
// `position: relative` `<td>`. On abspos-container-change-dynamic-001 the
// composition side therefore answered "no positioned ancestor" → hoist →
// compose nothing here; the walk answered "positioned ancestor" → not
// hoisted → no overlay slot. The lime box existed in neither place.
//
// This file is the shared, side-effect-free half of the repair: the role
// classification and the row-group splice, with no Compose types in sight
// so the whole table is pinnable on the JVM without Robolectric — the
// standing constraint of this suite (CanvasRootHoistTest, EndInsetAnchorTest).
// The composition-side wiring lives in ComponentRenderer's table region.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.types.ValueExtractors

object TableBoxTree {

    /**
     * The css-tables-3 §2.1 internal table roles this runtime distinguishes.
     *
     * `NONE` means "not a table-internal box" — every non-table display and
     * every component with no `Display` declaration at all, which in this
     * corpus is the overwhelming majority (only 4 of the 324 frozen
     * wave31-final tests declare ANY table display role).
     */
    enum class Role { TABLE, ROW_GROUP, ROW, CELL, CAPTION, NONE }

    /**
     * Classify one component's declared `display`.
     *
     * Reads the RAW wire keyword through [ValueExtractors.extractKeyword] —
     * the same decoder `ComponentRenderer.resolveDisplayConfig` uses — and
     * accepts both the underscore spelling the IR emits (`TABLE_ROW_GROUP`)
     * and the CSS hyphen spelling (`table-row-group`), because the wire has
     * carried both across waves and neither is worth a normalization pass.
     *
     * `inline-table` folds to [Role.TABLE]: css-tables-3 §2.1 gives it the
     * identical internal box tree and differs only in outer display, which
     * this runtime expresses through the surrounding flow, not here.
     */
    fun roleOf(properties: List<Pair<String, kotlinx.serialization.json.JsonElement?>>): Role {
        // `Display` is the only wire that carries a table role; absent ⇒ NONE.
        val raw = properties.firstOrNull { it.first == "Display" }?.second ?: return Role.NONE
        // Normalize hyphen→underscore so one `when` covers both spellings.
        val kw = ValueExtractors.extractKeyword(raw)?.uppercase()?.replace('-', '_') ?: return Role.NONE
        return when (kw) {
            "TABLE", "INLINE_TABLE" -> Role.TABLE
            // §2.1 lists three group boxes; all three are transparent for
            // row ordering, so they share one role.
            "TABLE_ROW_GROUP", "TABLE_HEADER_GROUP", "TABLE_FOOTER_GROUP" -> Role.ROW_GROUP
            "TABLE_ROW" -> Role.ROW
            // A column / column-group generates no cell boxes of its own
            // (§2.1) — it only carries column styling this runtime does not
            // model yet, so it classifies as NONE and renders as ordinary
            // content rather than being silently swallowed.
            "TABLE_CELL" -> Role.CELL
            "TABLE_CAPTION" -> Role.CAPTION
            else -> Role.NONE
        }
    }

    /** Convenience overload for the `IRProperty` list shape the renderer holds. */
    fun roleOf(component: IRComponent): Role =
        roleOf(component.properties.map { it.type to it.data })

    // ── The HTML UA display channel (wave 38, lane N2) ─────────────────

    /**
     * The role the HTML UA stylesheet gives a source ELEMENT, or [Role.NONE]
     * for a tag that is not table-internal.
     *
     * ## Why this channel has to exist
     * [roleOf] reads the declared `Display` and nothing else, and wave 34
     * wrote down exactly why it stopped there: reading the tag "would
     * silently re-classify 17 frozen WPT captures … rather than the one
     * this classification was measured against". That deferral is what this
     * function collects. The css-tables corpus is HTML, not CSS: the tables
     * in it are ordinary `<table>`/`<tr>`/`<td>` markup with no author
     * `display` at all, and the display they DO have comes from the HTML
     * Standard's rendering section (§15.3.8 Tables) — `table { display:
     * table }`, `tr { display: table-row }`, `td, th { display: table-cell }`,
     * `tbody/thead/tfoot { display: table-{row,header,footer}-group }`,
     * `caption { display: table-caption }`. The converter does not ship the
     * UA sheet, so that display never reaches the wire and `meta.sourceTag`
     * is its only sighting — the same channel
     * [TableSeparatedTracks.usedSpacing] already reads for the UA
     * `border-spacing`. (Retro sweep P2a, A6#11: this sentence used to
     * name a second reader, `CollapsedBorderConflict.originOf`, the CSS 2.1
     * §17.6.2.1 conflict-resolution decision table. That module never had a
     * production caller on either native and was deleted with its 14 JVM
     * tests; the collapsed-border gap is a table-LAYOUT gap, not a
     * conflict-ranking one — see the deletion note in the retro report.)
     *
     * MEASURED (frozen wave37-final, `tools/titan/runs/wave37-final/sections
     * /css-tables`): 122 of the section's table-internal boxes carry a table
     * `meta.sourceTag` and NO table `Display`, i.e. [roleOf] answers
     * [Role.NONE] for them and every `<table>` renders as a plain block
     * stack. `border-collapse-empty-cell` is the clearest picture — a 2×2
     * grid of 50×50 bordered cells whose reference is a 2×2 square, captured
     * on BOTH natives as a 1×4 VERTICAL column of cells (iOS/Android ssim
     * 0.9073 against the ref, web 1.0000).
     *
     * `col` / `colgroup` are deliberately absent: css-tables-3 §2.1 gives
     * them no cell boxes at all, so they have no ROLE in this table — see
     * [generatesNoBoxes], which is where the renderer drops them.
     */
    fun uaRoleOf(sourceTag: String?): Role = when (sourceTag?.lowercase()) {
        "table" -> Role.TABLE
        // §2.1's three group boxes share one role, exactly as in `roleOf`.
        "tbody", "thead", "tfoot" -> Role.ROW_GROUP
        "tr" -> Role.ROW
        "td", "th" -> Role.CELL
        "caption" -> Role.CAPTION
        else -> Role.NONE
    }

    /**
     * The role of a box, reading the DECLARED `display` first and falling
     * back to [uaRoleOf] only when the wire declared none.
     *
     * The precedence is the cascade's own: an author `display` beats the UA
     * sheet, so a `<table style="display:block">` is a block box and a
     * `<div style="display:table">` is a table. Falling back ONLY on the
     * absent-`Display` case is also what makes this additive — every box
     * that already had a declared table role keeps the byte-identical
     * classification wave 32/34 measured, and the ONLY behaviour that moves
     * is the [Role.NONE] answer this function replaces.
     *
     * @param sourceTag the box's `meta.sourceTag` (`IRComponent._tag`).
     *   Passing null reproduces [roleOf] exactly.
     */
    fun roleOf(
        properties: List<Pair<String, kotlinx.serialization.json.JsonElement?>>,
        sourceTag: String?
    ): Role {
        // A declared `display` — table or not — is authoritative: the UA
        // sheet is the LOWEST-priority origin, so it may only fill a gap.
        if (properties.any { it.first == "Display" }) return roleOf(properties)
        return uaRoleOf(sourceTag)
    }

    /** Convenience overload for the component shape the renderer holds. */
    fun roleOf(component: IRComponent, useUaTagDefaults: Boolean): Role =
        if (useUaTagDefaults) {
            roleOf(component.properties.map { it.type to it.data }, component._tag)
        } else {
            // The frozen path, character for character — no tag is read at
            // all, so a caller that has not opted in cannot drift.
            roleOf(component)
        }

    /**
     * Does this SOURCE TAG generate no boxes of its own inside a table?
     *
     * css-tables-3 §2.1: a `table-column` / `table-column-group` box "does
     * not render" — it carries column styling and nothing else. In the
     * declared-`display` world that never mattered, because [roleOf] answers
     * [Role.NONE] for `TABLE_COLUMN` and the box simply rode along as
     * ordinary content. Once [uaRoleOf] turns a bare `<table>` into a real
     * table box, its `<colgroup>` children would be swept into the ROW list
     * by [rowsOf] and painted as a row of cells that the browser paints
     * nowhere — so the row splice drops them.
     *
     * Keyed on the TAG rather than the role on purpose: a DECLARED
     * `display: table-column` keeps its exact pre-wave-38 passthrough (it is
     * the shape `css-tables/border-collapse-dynamic-col-001` carries, and
     * that test's frozen Android capture must not move), while the UA-only
     * `<col>` — which has no other reason to exist in the box tree — is
     * dropped.
     */
    fun generatesNoBoxes(sourceTag: String?): Boolean =
        sourceTag?.lowercase() == "col" || sourceTag?.lowercase() == "colgroup"

    /**
     * Is a box with this role SHRINK-TO-FIT rather than a CSS 2.1 §10.3.3
     * block-level fill?
     *
     * CSS 2.1 §17.5.2 / css-tables-3 §5: a table box with `width: auto` uses
     * the table layout algorithm, whose used width is
     * `max(min-content, min(max-content, available))` — it hugs its columns
     * and only reaches the containing block when its content is that wide.
     * It is NOT §10.3.3's "width:auto fills the containing block", which is
     * what both runtimes' composed-WPT block-fill channels
     * (Compose `blockFlowWidth`, iOS `wptChildFillWidth`) implement for
     * ordinary block boxes.
     *
     * MEASURED (frozen wave37-final): `css-tables/background-clip-001` is a
     * single `<td>` holding a 40×40 inline-block inside 30px collapsed
     * borders, so the table is exactly 100×100 and the reference paints
     * 10 000 green pixels. Both natives painted 35 800 — a 358×100 bar, the
     * full composed-canvas content width — because the table consumed the
     * block-fill channel. `box-shadow-001` and the three
     * `height-distribution/extra-height-given-to-all-row-groups-00{1,2,5}`
     * tests are the same picture and the same three ink counts.
     *
     * A ROW is deliberately NOT shrink-to-fit here: §17.5.2 sizes rows to
     * the table's used width, and the natives' row containers already stretch
     * to their table. Only the table box itself resists the fill.
     */
    fun shrinkToFitBox(role: Role): Boolean = role == Role.TABLE

    /**
     * Must the renderer ENFORCE §17.5.2's auto width on this box — i.e. hand
     * `TableApplier.Table(shrinkToFit = true)` so the table Column takes
     * `min(max-content, available)` instead of the canvas width?
     *
     * ## Why [shrinkToFitBox] alone was not enough (wave 39, lane A6)
     * [shrinkToFitBox] gates ONE fill: `ComponentRenderer`'s composed-WPT
     * `blockFlowWidth`. `TableApplier.TableRow` then adds its own
     * unconditional `fillMaxWidth`, and a `fillMaxWidth` child makes its
     * parent Column take the incoming max constraint — so removing the
     * table box's fill only moved it one level down and the table still
     * stretched to the full composed canvas. MEASURED on the frozen
     * wave38-final Android captures: `css-tables/background-clip-001`'s
     * 100×100 reference square captured as a ~358×100 bar (0.8891), with
     * `box-shadow-001` (0.8897), `anonymous-table-cell-margin-collapsing`
     * (0.8891) and `height-distribution/extra-height-given-to-all-row-groups-00{1,2,5}`
     * (0.8882 ×3) the same picture — every one of them an iOS PASS.
     *
     * Two inputs, one function, so the width decision cannot drift between
     * `ComponentRenderer` (which reads it) and this table (which owns it).
     *
     * @param role the box's css-tables-3 §2.1 role — [roleOf].
     * @param composedCapture the composed-WPT capture flag
     *   (`LocalWptComposedMode`). FALSE — the 327-pair dark stage and the
     *   per-component inbox path — keeps the frozen unconstrained Column, so
     *   those surfaces are byte-identical by construction.
     */
    fun enforcesAutoTableWidth(role: Role, composedCapture: Boolean): Boolean =
        composedCapture && shrinkToFitBox(role)

    /**
     * Does a box with this role render its content as an ordinary BLOCK
     * CONTAINER rather than re-entering table layout?
     *
     * css-tables-3 §2.1: a `table-cell` box "establishes a block container
     * box for its contents", and a `table-caption` is likewise a block
     * container. Routing them through the block path is what lets their
     * children reach `ComponentRenderer.RenderContent` — and therefore the
     * positioned-container branch that anchors an abspos child at this
     * box's padding box (CSS 2.2 §10.1), plus the mixed-content text run,
     * plus every ordinary background/border applier. The old TABLE routing
     * reached none of those.
     */
    fun rendersAsBlockContainer(role: Role): Boolean =
        role == Role.CELL || role == Role.CAPTION

    /**
     * The ROW boxes of a table, with row groups spliced away.
     *
     * css-tables-3 §2.1: `table-row-group` / `-header-group` /
     * `-footer-group` boxes contain rows; the rows of all groups, in
     * document order, are the table's rows. This runtime draws no
     * group-level chrome (no group background, no group border yet), so
     * splicing is loss-free here — and it is the ONLY way the implied
     * `<tbody>` every HTML parse inserts stops shifting the real cells one
     * level out of reach.
     *
     * Non-group children pass through unchanged and in place, so a table
     * whose children are already rows (the shape the pre-wave-32 code
     * assumed, and the shape most extracted fixtures carry) yields a list
     * IDENTICAL to `children` — byte-stability for everything that is not
     * a row group.
     *
     * Nested groups are NOT recursed into: §2.1 does not allow a group
     * inside a group, and one flat splice keeps this decision obviously
     * terminating.
     */
    fun rowsOf(children: List<IRComponent>?): List<IRComponent> =
        rowsOf(children, useUaTagDefaults = false)

    /**
     * [rowsOf] with the wave-38 UA tag channel switched on or off.
     *
     * With [useUaTagDefaults] false this is the frozen wave-32 splice,
     * character for character. With it true two things change, and only for
     * boxes the declared wire left unclassified:
     *  * a `<tbody>`/`<thead>`/`<tfoot>` that carries no `display` is
     *    recognised as a ROW GROUP and spliced, so its rows reach the row
     *    loop — the exact defect wave 32 fixed for the DECLARED spelling;
     *  * a UA-only `<col>` / `<colgroup>` is DROPPED rather than swept into
     *    the row list ([generatesNoBoxes] — css-tables-3 §2.1: a column box
     *    does not render).
     *
     * CORRECTION (wave-38 finish pass). The wave-38 first cut justified this
     * with "an HTML parse ALWAYS inserts an implied `<tbody>`". That is true
     * of the browser's DOM and FALSE of this wire: the extractor emits no
     * implied element, so the corpus carries `table → tr → td` directly, and
     * `<table><td>` markup arrives as `table → td` with no row box at all
     * (`css-tables/absolute-tables-013`, `-014`, `-008.tentative`,
     * `-011.tentative`). A `<tbody>` only reaches this splice when the
     * source really wrote one. The false premise is what made the caller's
     * fold assume a canonical tree — see [consumableRowList].
     */
    fun rowsOf(children: List<IRComponent>?, useUaTagDefaults: Boolean): List<IRComponent> {
        if (children.isNullOrEmpty()) return emptyList()
        // A UA-only column box has to go even when there is no group to
        // splice, so the fast path must also see it. Both reads are the
        // constant `false` on the frozen path, which keeps that branch exact.
        val dropsColumn = { c: IRComponent ->
            useUaTagDefaults && c.properties.none { it.type == "Display" } && generatesNoBoxes(c._tag)
        }
        // Fast path — nothing to splice and nothing to drop means the
        // caller's list is already the row list, returned as-is so no
        // allocation and no reordering.
        if (children.none { roleOf(it, useUaTagDefaults) == Role.ROW_GROUP || dropsColumn(it) }) {
            return children
        }
        val out = mutableListOf<IRComponent>()
        for (child in children) {
            when {
                // §2.1: a column / column-group generates no boxes at all.
                dropsColumn(child) -> Unit
                roleOf(child, useUaTagDefaults) == Role.ROW_GROUP -> {
                    // The group's own children ARE rows in document order.
                    // An empty group contributes nothing — correct: a
                    // `<tbody></tbody>` generates no row boxes.
                    child.children?.let { out += it }
                }
                else -> out += child
            }
        }
        return out
    }

    /**
     * Is [rowsOf]'s output a row list the renderer's table path can consume
     * WITHOUT LOSS?
     *
     * ## Why this predicate exists (wave-38 finish pass, the Android repair)
     * This runtime's table path is a structural REWRITE, not a decoration:
     * `ComponentRenderer.RenderTableContent` treats the table's children as
     * ROWS and each row's children as CELLS, renders the grandchildren
     * through `RenderComponent`, and renders a childless "row" as
     * `PlaceholderContent`. The ROW level itself is never rendered as a
     * component at all. That total rewrite is correct for the ONE shape it
     * was written against — `table → row → cell` — and lossy for every
     * other shape a real extracted document can carry.
     *
     * The iOS twin has no equivalent hazard and therefore no equivalent
     * guard: `ComponentRenderer.tableTrackPlan()` only picks a track
     * ARRANGEMENT for the same `inFlowChildren` array, and every child still
     * renders through its own full style chain. That asymmetry is the whole
     * reason this predicate is Compose-only.
     *
     * MEASURED (wave38-final vs wave37-final Android, browser-ref SSIM):
     * turning the UA display fold on cost 13 frozen Android passes across
     * CSS2 / css-tables / css-display / css-text-decor. EIGHT of them are
     * this predicate's — a non-`table → row → cell` shape the extractor
     * really emits, listed below with its capture. (The other five are the
     * canonical shape losing to the applier's fabricated cell border — see
     * `TableApplier.LocalTableFabricatedCellBorder`.)
     *  * `table → td` (the `<table><td>` markup of
     *    `css-tables/absolute-tables-008.tentative`, `-011.tentative`,
     *    `-013`, `-014`) — the extractor emits NO implied `<tbody>`/`<tr>`,
     *    so the `<td>` became the ROW and its `<span>` children became
     *    CELLS. 013 painted two side-by-side green boxes where the
     *    reference is one 100×100 square; 008/011 painted NOTHING at all
     *    (a childless "row" ⇒ `PlaceholderContent`, suppressed in composed
     *    capture ⇒ a zero-width table ⇒ its green background never landed).
     *  * `table → caption` (`css-tables/caption-relative-positioning`) —
     *    the caption became a childless ROW, so its own
     *    `position: relative` / `background` / `width` / `height` were
     *    never applied and the green square vanished, leaving only the
     *    parent's red.
     *  * `table → caption, tr` (`CSS2/css21-errata/s-11-1-1b-001`, `-002`,
     *    `-008`) — the empty `<caption>` was spliced into the row list as a
     *    phantom first row.
     *
     * The predicate is deliberately ALL-or-nothing: a partially consumable
     * list would still lose the non-row entries, and the block fall-back is
     * exactly the pre-wave-38 rendering, i.e. a known-good floor.
     *
     * An EMPTY row list is not consumable either — `RenderTableContent`'s
     * no-children branch emits a placeholder cell, which in composed capture
     * is a zero-size box (`css-tables/absolute-tables-012` is that shape).
     *
     * @param children the table box's children, unspliced.
     * @param useUaTagDefaults the same UA-channel opt-in [rowsOf] takes; the
     *   caller MUST pass the value it will pass to [rowsOf], or the guard
     *   and the splice would be answering about different lists.
     */
    fun consumableRowList(children: List<IRComponent>?, useUaTagDefaults: Boolean): Boolean {
        val rows = rowsOf(children, useUaTagDefaults)
        // No rows ⇒ nothing for the row loop to consume (see the kdoc's
        // absolute-tables-012 note).
        if (rows.isEmpty()) return false
        // Every entry must really BE a row. `roleOf` reads the declared
        // keyword first and the UA tag second, exactly as the splice did,
        // so a declared `display: table-row` and a bare `<tr>` both qualify.
        return rows.all { roleOf(it, useUaTagDefaults) == Role.ROW }
    }

    /**
     * Does this component establish a containing block for absolutely
     * positioned descendants — i.e. must the row/cell synthesizer publish
     * `CanvasRootHoist.LocalHasPositionedAncestor` for its subtree?
     *
     * CSS 2.2 §10.1: "the containing block is formed by the padding edge of
     * the nearest ancestor box which is positioned" — and §9.3.1 makes any
     * `position` other than `static` positioned. Table-internal boxes are
     * not excluded: a `position: relative` `<td>` IS the containing block
     * for its abspos children (the whole point of
     * css-tables/abspos-container-change-dynamic-001).
     *
     * Deliberately delegates to [com.styleconverter.runtime.layout.position
     * .CanvasRootHoist.establishesContainingBlock] rather than re-deriving
     * the predicate: the mirror invariant this fix restores is only worth
     * anything if BOTH sides keep asking the same function.
     */
    fun establishesContainingBlock(component: IRComponent): Boolean =
        com.styleconverter.runtime.layout.position.CanvasRootHoist
            .establishesContainingBlock(component.properties)

    // ── Wave 52 lane L8 (M-E): column width contributions ──────────────
    //
    // css-tables-3 §2.1: a column box "does not render", but it is not
    // nothing — §3.2 makes a column's specified `width` one of the inputs
    // to that column's min/max width. [rowsOf] drops the UA-only
    // `<col>`/`<colgroup>` boxes and, until wave 52, their `width` with
    // them. MEASURED on wave51-fix css-writing-modes/ch-units-vrl-003/-004:
    // `<col style="width: 5ch">` (upright → 120) never reached the green
    // `<td>`, which stayed 6 px wide on both natives (ref 120×120). The
    // census (census.json `colWithWidth`) finds 5 such documents; the 5th,
    // css-tables/border-collapse-dynamic-col-001, DECLARES `display:
    // table-column`, which [columnChains] excludes exactly as [rowsOf] does,
    // so that frozen capture cannot move.

    /**
     * The table's column boxes in column order, each as the chain its width
     * is read from: `[col]` for a direct `<col>`, `[col, colgroup]` for a
     * `<col>` inside a `<colgroup>` (the group's width is the fallback),
     * `[colgroup]` for an empty `<colgroup>` (one column of its own, §2.1).
     * UA-only boxes only (no declared `Display`) — the [rowsOf] drop rule.
     */
    fun columnChains(children: List<IRComponent>?): List<List<IRComponent>> {
        // The same predicate [rowsOf] drops by, with the UA channel on.
        val uaColumn = { c: IRComponent -> c.properties.none { it.type == "Display" } && generatesNoBoxes(c._tag) }
        val out = mutableListOf<List<IRComponent>>()
        for (child in children.orEmpty().filter(uaColumn)) {
            // A `<colgroup>` with `<col>` children: one column per col.
            val cols = child.children.orEmpty().filter { uaColumn(it) && it._tag?.lowercase() == "col" }
            if (child._tag?.lowercase() == "colgroup" && cols.isNotEmpty()) cols.forEach { out += listOf(it, child) }
            // A bare `<col>`, or an empty `<colgroup>`: one column.
            else out += listOf(child)
        }
        return out
    }

    /**
     * Each column's specified width in px (null = auto / unresolvable), in
     * column order — the minimum the renderer gives every cell of that
     * column. [inherited] is the table's inheritable set (the column box
     * inherits `font-size`, `writing-mode`, … like any element: the
     * ch-units-vrl tests assert exactly that).
     */
    fun columnWidthsPx(
        children: List<IRComponent>?,
        inherited: List<com.styleconverter.runtime.core.ir.IRProperty>,
    ): List<Float?> = columnChains(children).map { chain ->
        // The col's own width first, the group's as the fallback.
        chain.firstNotNullOfOrNull { columnWidthPx(it, inherited) }
    }

    /** One column box's declared `width` in px, resolved like any element's
     *  sizing value — the `ch` basis included, measured along the column's
     *  OWN inline axis (an upright col's 5ch is 120, a sideways one's 63). */
    internal fun columnWidthPx(
        column: IRComponent,
        inherited: List<com.styleconverter.runtime.core.ir.IRProperty>,
    ): Float? {
        // No declared width → this box contributes nothing.
        if (column.properties.none { it.type == "Width" }) return null
        // Own declarations win; inherited types fill the gaps (the channel
        // RenderComponent itself uses, so a col resolves like its cells).
        val merged = com.styleconverter.runtime.core.renderer.ComponentRenderer.mergeInherited(column.properties, inherited)
        val config = com.styleconverter.runtime.StyleApplier.extractConfig(merged.map { it.type to it.data })
        val width = config.layout.sizing.width ?: return null
        val base = com.styleconverter.runtime.StyleApplier.buildSpacingContext(config)
        // css-values-4 §6.1.1: the ch basis along THIS box's inline axis —
        // computed here so the harvest does not depend on the call-site flag.
        val ctx = if (com.styleconverter.runtime.spacing.usesChUnit(width)) base.copy(
            chAdvancePx = com.styleconverter.runtime.spacing.ChUnitMetrics.measure(
                config.typography.fontFamily, base.fontSizePx,
                com.styleconverter.runtime.typography.text.VerticalInlineAxis.chAdvanceIsVertical(
                    config.writingMode.writingMode, config.writingMode.textOrientation),
            ),
        ) else base
        // px == dp in this runtime; a zero / negative / non-finite answer
        // (an unresolvable percentage) is "auto", not a 0-wide column.
        return com.styleconverter.runtime.spacing.resolveToDp(width, ctx).value.takeIf { it.isFinite() && it > 0f }
    }
}

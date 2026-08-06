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
    fun rowsOf(children: List<IRComponent>?): List<IRComponent> {
        if (children.isNullOrEmpty()) return emptyList()
        // Fast path — no group anywhere means the caller's list is already
        // the row list, returned as-is so no allocation and no reordering.
        if (children.none { roleOf(it) == Role.ROW_GROUP }) return children
        val out = mutableListOf<IRComponent>()
        for (child in children) {
            if (roleOf(child) == Role.ROW_GROUP) {
                // The group's own children ARE rows in document order.
                // An empty group contributes nothing — correct: a
                // `<tbody></tbody>` generates no row boxes.
                child.children?.let { out += it }
            } else {
                out += child
            }
        }
        return out
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
}

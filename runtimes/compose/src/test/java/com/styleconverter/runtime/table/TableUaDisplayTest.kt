package com.styleconverter.runtime.table

// Wave 38 (lane N2) — JVM pins for the HTML UA DISPLAY channel and the
// CSS 2.1 §17.5.2 shrink-to-fit predicate. Twin:
// `runtimes/swiftui/Tests/StyleConverterRuntimeTests/TableUaDisplayTests.swift`
// — same cases, same order, same names.
//
// ## What this file protects
// `TableBoxTree.roleOf` reads the wire's `Display` and nothing else, and
// the css-tables corpus is HTML markup that declares none: the display its
// `<table>`/`<tr>`/`<td>` boxes have comes from the HTML Standard's
// rendering section (§15.3.8 Tables), which the converter does not ship.
// MEASURED on the frozen wave37-final gate
// (`tools/titan/runs/wave37-final/sections/css-tables`): 122 of the
// section's 171 table-internal boxes carry a table `meta.sourceTag` and NO
// table `Display`, so every one of them classified as an ordinary block.
// `border-collapse-empty-cell` — a 2×2 grid of 50×50 bordered cells whose
// reference is a 2×2 square — captured on BOTH natives as a 1×4 VERTICAL
// column of cells (ssim 0.9073 against the ref; web scored 1.0000).
//
// The two contracts pinned here are exactly the two that decide whether a
// bare `<table>` becomes a table box at all:
//   1. the UA fallback fires ONLY where the wire declared no `display`
//      (the cascade's own precedence — an author keyword always wins), and
//   2. a table box is SHRINK-TO-FIT, never a §10.3.3 block-level fill.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TableUaDisplayTest {

    // ── Wire helpers ───────────────────────────────────────────────────

    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))

    /** A component as the v2 wire carries it: a `meta.sourceTag` (decoded
     *  to `_tag`) and an OPTIONAL author `display`. */
    private fun box(
        id: String,
        tag: String? = null,
        display: String? = null,
        children: List<IRComponent>? = null,
    ) = IRComponent(
        id = id,
        name = id,
        properties = buildList { display?.let { add(prop("Display", "\"$it\"")) } },
        children = children,
        _tag = tag,
    )

    // ── The UA tag → role table (HTML §15.3.8) ─────────────────────────

    @Test
    fun `every table-internal HTML tag gets its UA sheet role`() {
        // Straight from the HTML Standard's rendering section: these are
        // the six declarations the converter does not ship.
        assertEquals(TableBoxTree.Role.TABLE, TableBoxTree.uaRoleOf("table"))
        assertEquals(TableBoxTree.Role.ROW, TableBoxTree.uaRoleOf("tr"))
        assertEquals(TableBoxTree.Role.CELL, TableBoxTree.uaRoleOf("td"))
        assertEquals(TableBoxTree.Role.CELL, TableBoxTree.uaRoleOf("th"))
        assertEquals(TableBoxTree.Role.CAPTION, TableBoxTree.uaRoleOf("caption"))
        // §2.1's three group boxes share one role, as in the declared table.
        assertEquals(TableBoxTree.Role.ROW_GROUP, TableBoxTree.uaRoleOf("tbody"))
        assertEquals(TableBoxTree.Role.ROW_GROUP, TableBoxTree.uaRoleOf("thead"))
        assertEquals(TableBoxTree.Role.ROW_GROUP, TableBoxTree.uaRoleOf("tfoot"))
    }

    @Test
    fun `tag matching is case-insensitive and non-table tags stay NONE`() {
        // The wire lowercases `sourceTag`, but an uppercase tag must not
        // silently fall out of the table. (Retro sweep P2a, A6#11: the
        // second reader this comment used to cite for the same tolerance,
        // CollapsedBorderConflict.originOf, is deleted — it had no
        // production caller on either native.)
        assertEquals(TableBoxTree.Role.TABLE, TableBoxTree.uaRoleOf("TABLE"))
        assertEquals(TableBoxTree.Role.CELL, TableBoxTree.uaRoleOf("Td"))
        // Everything else is not a table-internal box, including the two
        // column tags — css-tables-3 §2.1 gives them no cell boxes.
        for (t in listOf("div", "p", "span", "col", "colgroup", "", null)) {
            assertEquals("tag $t", TableBoxTree.Role.NONE, TableBoxTree.uaRoleOf(t))
        }
    }

    // ── Precedence: a declared display always wins ─────────────────────

    @Test
    fun `a declared display beats the UA sheet in both directions`() {
        // The UA origin is the lowest-priority one in the cascade, so it
        // may only FILL A GAP. Both directions matter: a `<table>` the
        // author blocked must not become a table box, and a `<div>` the
        // author tabled must stay one.
        assertEquals(
            TableBoxTree.Role.NONE,
            TableBoxTree.roleOf(box("t", tag = "table", display = "BLOCK").properties
                .map { it.type to it.data }, "table"))
        assertEquals(
            TableBoxTree.Role.TABLE,
            TableBoxTree.roleOf(box("d", tag = "div", display = "TABLE").properties
                .map { it.type to it.data }, "div"))
        // …and a declared table keyword on a table tag is unchanged.
        assertEquals(
            TableBoxTree.Role.ROW,
            TableBoxTree.roleOf(box("r", tag = "tr", display = "TABLE_ROW").properties
                .map { it.type to it.data }, "tr"))
    }

    @Test
    fun `a null sourceTag reproduces the declared-only classification`() {
        // The frozen contract: passing no tag must answer exactly what the
        // one-argument roleOf answers, for every wire shape.
        for (kw in listOf(null, "BLOCK", "TABLE", "TABLE_ROW", "TABLE_CELL", "FLEX")) {
            val c = box("x", tag = null, display = kw)
            val declaredOnly = TableBoxTree.roleOf(c)
            assertEquals(
                "display=$kw",
                declaredOnly,
                TableBoxTree.roleOf(c.properties.map { it.type to it.data }, null))
            // …and the opt-out overload never reads the tag either.
            assertEquals(
                "display=$kw opted out",
                declaredOnly,
                TableBoxTree.roleOf(box("x", tag = "table", display = kw),
                    useUaTagDefaults = false))
        }
    }

    @Test
    fun `the opt-in overload classifies a bare HTML table`() {
        // The css-tables shape: no Display anywhere, only the tags.
        assertEquals(TableBoxTree.Role.TABLE,
            TableBoxTree.roleOf(box("t", tag = "table"), useUaTagDefaults = true))
        assertEquals(TableBoxTree.Role.ROW,
            TableBoxTree.roleOf(box("r", tag = "tr"), useUaTagDefaults = true))
        assertEquals(TableBoxTree.Role.CELL,
            TableBoxTree.roleOf(box("c", tag = "td"), useUaTagDefaults = true))
        // Opted out, the same three wires are ordinary blocks — which is
        // precisely the frozen wave37-final behaviour this lane replaces.
        assertEquals(TableBoxTree.Role.NONE,
            TableBoxTree.roleOf(box("t", tag = "table"), useUaTagDefaults = false))
    }

    // ── §17.5.2 shrink-to-fit ──────────────────────────────────────────

    @Test
    fun `only the table box itself resists the block-level fill`() {
        // CSS 2.1 §17.5.2: a table's used width is the table layout
        // algorithm's max(min-content, min(max-content, available)) — it
        // hugs its columns. Rows and cells are sized BY the table, and an
        // ordinary block still fills (§10.3.3), so exactly one role
        // answers true.
        assertTrue(TableBoxTree.shrinkToFitBox(TableBoxTree.Role.TABLE))
        for (r in listOf(TableBoxTree.Role.ROW, TableBoxTree.Role.ROW_GROUP,
                         TableBoxTree.Role.CELL, TableBoxTree.Role.CAPTION,
                         TableBoxTree.Role.NONE)) {
            assertFalse("role $r", TableBoxTree.shrinkToFitBox(r))
        }
    }

    // ── The row splice under the UA channel ────────────────────────────

    @Test
    fun `an implied tbody is spliced only with the UA channel on`() {
        // An HTML parse ALWAYS inserts `<tbody>`, and it carries no
        // `display` — so before wave 38 the wave-32 splice never fired for
        // real markup and every `<td>` sat one level below the cell loop.
        val rowA = box("tr0", tag = "tr")
        val rowB = box("tr1", tag = "tr")
        val tbody = box("tbody", tag = "tbody", children = listOf(rowA, rowB))
        val children = listOf(tbody)

        val spliced = TableBoxTree.rowsOf(children, useUaTagDefaults = true)
        assertEquals(listOf(rowA, rowB), spliced)

        // Opted out: the tbody classifies as NONE and rides through as a
        // single "row" — the frozen behaviour, returned by IDENTITY so the
        // fast path is provably unchanged.
        assertSame(children, TableBoxTree.rowsOf(children, useUaTagDefaults = false))
        assertSame(children, TableBoxTree.rowsOf(children))
    }

    @Test
    fun `a UA-only column box is dropped, a declared one is not`() {
        // css-tables-3 §2.1: a column / column-group box does not render.
        // Once a bare `<table>` becomes a real table box, its `<colgroup>`
        // would otherwise be swept into the ROW list and painted as a row
        // of cells the browser paints nowhere.
        val col = box("col", tag = "colgroup")
        val row = box("tr", tag = "tr")
        assertEquals(listOf(row), TableBoxTree.rowsOf(listOf(col, row), useUaTagDefaults = true))

        // A DECLARED `display: table-column` keeps its exact pre-wave-38
        // passthrough — the shape css-tables/border-collapse-dynamic-col-001
        // carries, whose frozen Android capture must not move.
        val declaredCol = box("col", tag = "colgroup", display = "TABLE_COLUMN_GROUP")
        assertEquals(
            listOf(declaredCol, row),
            TableBoxTree.rowsOf(listOf(declaredCol, row), useUaTagDefaults = true))
    }

    @Test
    fun `a group-free row list is returned by identity on both channels`() {
        // Byte-stability guard: the fast path must not allocate or reorder
        // for the shape most extracted fixtures carry (rows handed
        // directly), with or without the UA channel.
        val rows = listOf(box("tr0", tag = "tr"), box("tr1", tag = "tr"))
        assertSame(rows, TableBoxTree.rowsOf(rows, useUaTagDefaults = true))
        assertSame(rows, TableBoxTree.rowsOf(rows, useUaTagDefaults = false))
    }

    @Test
    fun `generatesNoBoxes names exactly the two column tags`() {
        assertTrue(TableBoxTree.generatesNoBoxes("col"))
        assertTrue(TableBoxTree.generatesNoBoxes("COLGROUP"))
        for (t in listOf("table", "tr", "td", "tbody", "caption", "div", null)) {
            assertFalse("tag $t", TableBoxTree.generatesNoBoxes(t))
        }
    }

    // ── consumableRowList: the shapes the Compose table path may consume ──
    //
    // Wave-38 finish pass. Compose's `RenderTableContent` REWRITES the box
    // tree (children→rows, grandchildren→cells, the row level never
    // rendered as a component), so the UA display fold may only fire where
    // that rewrite is total. Every case below is a shape the extractor
    // really emits, named with the frozen Android capture the unguarded
    // fold broke.

    @Test
    fun `the canonical table row cell shape is consumable`() {
        // `css-text-decor/text-decoration-propagation-05`, and every
        // css-tables test the wave-38 fold legitimately improved.
        val cell = box("td", tag = "td")
        val row = box("tr", tag = "tr", children = listOf(cell))
        assertTrue(TableBoxTree.consumableRowList(listOf(row), useUaTagDefaults = true))
        // …and through an implied `<tbody>`, which the splice removes first.
        val tbody = box("tbody", tag = "tbody", children = listOf(row))
        assertTrue(TableBoxTree.consumableRowList(listOf(tbody), useUaTagDefaults = true))
    }

    @Test
    fun `a table whose child is a CELL is not consumable`() {
        // `css-tables/absolute-tables-013`: the extractor emits NO implied
        // `<tbody>`/`<tr>` for `<table><td>…`, so the `<td>` would become
        // the ROW and its two `<span>` children the CELLS — the frozen
        // capture went from one 100×100 green square to two side-by-side
        // boxes (0.9851 → 0.9301). `-008.tentative` / `-011.tentative` are
        // the childless version of the same shape and painted NOTHING.
        val cell = box("td", tag = "td", children = listOf(box("span", tag = "span")))
        assertFalse(TableBoxTree.consumableRowList(listOf(cell), useUaTagDefaults = true))
    }

    @Test
    fun `a table whose child is a CAPTION is not consumable`() {
        // `css-tables/caption-relative-positioning`: a childless caption
        // becomes a placeholder row, so its own relative position /
        // background / size are never applied — the green square vanished
        // and only the parent's red survived (0.9851 → 0.9462).
        val caption = box("caption", tag = "caption")
        assertFalse(TableBoxTree.consumableRowList(listOf(caption), useUaTagDefaults = true))
    }

    @Test
    fun `a caption beside a row makes the whole list unconsumable`() {
        // `CSS2/css21-errata/s-11-1-1b-001` / `-002` / `-008`: the empty
        // `<caption>` rode into the row list as a phantom first row
        // (0.9850 → 0.9272). All-or-nothing on purpose — consuming the
        // partial list would still lose the caption.
        val caption = box("caption", tag = "caption")
        val row = box("tr", tag = "tr", children = listOf(box("td", tag = "td")))
        assertFalse(TableBoxTree.consumableRowList(listOf(caption, row), useUaTagDefaults = true))
    }

    @Test
    fun `an empty or childless table is not consumable`() {
        // `css-tables/absolute-tables-012`: no rows means the row loop's
        // no-children branch, whose placeholder cell is a zero-size box in
        // composed capture.
        assertFalse(TableBoxTree.consumableRowList(null, useUaTagDefaults = true))
        assertFalse(TableBoxTree.consumableRowList(emptyList(), useUaTagDefaults = true))
        // An empty `<tbody>` splices to nothing, which is the same answer.
        val emptyGroup = box("tbody", tag = "tbody")
        assertFalse(TableBoxTree.consumableRowList(listOf(emptyGroup), useUaTagDefaults = true))
    }

    @Test
    fun `the declared channel answers about declared rows only`() {
        // With the UA channel off the predicate reads the keyword alone —
        // so a bare `<tr>` is not a row and a declared one is. This is the
        // shape the guard must never widen: the frozen declared-`display`
        // tables keep reaching the table path through their own branch.
        val bareRow = box("tr", tag = "tr")
        assertFalse(TableBoxTree.consumableRowList(listOf(bareRow), useUaTagDefaults = false))
        val declaredRow = box("r", tag = "tr", display = "TABLE_ROW")
        assertTrue(TableBoxTree.consumableRowList(listOf(declaredRow), useUaTagDefaults = false))
    }

    // ── §17.5.2 auto width ENFORCEMENT (wave 39, lane A6) ──────────────

    @Test
    fun `auto table width is enforced only for a table box in composed capture`() {
        // The two inputs of the gate the renderer hands to
        // TableApplier.Table(shrinkToFit = …). Composed capture is the
        // measured surface; every other surface (the 327-pair dark stage,
        // the per-component inbox path) must keep the frozen unconstrained
        // Column, so `false` there is the byte-stability guarantee.
        assertTrue(
            TableBoxTree.enforcesAutoTableWidth(TableBoxTree.Role.TABLE, composedCapture = true))
        assertFalse(
            TableBoxTree.enforcesAutoTableWidth(TableBoxTree.Role.TABLE, composedCapture = false))
    }

    @Test
    fun `no internal table box enforces auto width`() {
        // §17.5.2 sizes rows and cells BY the table's used width — only the
        // table box itself resists the fill, and an ordinary block (NONE)
        // still fills per §10.3.3. Mirrors the shrinkToFitBox pin so the
        // composed gate can never widen past it.
        for (r in listOf(TableBoxTree.Role.ROW, TableBoxTree.Role.ROW_GROUP,
                         TableBoxTree.Role.CELL, TableBoxTree.Role.CAPTION,
                         TableBoxTree.Role.NONE)) {
            assertFalse("role $r", TableBoxTree.enforcesAutoTableWidth(r, composedCapture = true))
        }
    }
}

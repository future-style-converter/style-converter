package com.styleconverter.runtime.table

// Wave 32 (lane P) — JVM pins for the CSS table BOX TREE (css-tables-3
// §2.1) and for the containing-block mirror the row/cell synthesizer owes
// the canvas hoist. Every decision here is pure over the IR, so the whole
// contract pins without Robolectric — the standing constraint of this
// suite (CanvasRootHoistTest / EndInsetAnchorTest).
//
// The two shapes under test are the exact wires from the frozen
// wave31-final gate:
//   • css-tables/abspos-container-change-dynamic-001 —
//     table → TABLE_ROW_GROUP → TABLE_ROW → [TABLE_CELL "A",
//     TABLE_CELL "B" (position: relative) → ABSOLUTE lime 100×100]
//   • CSS2/abspos/static-inside-table-cell —
//     TABLE_CELL → [ABSOLUTE green, in-flow red]

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TableBoxTreeTest {

    // ── Wire helpers ───────────────────────────────────────────────────

    // One IRProperty carrying a raw JSON literal, the shape the reader emits.
    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))

    // A component with a `display` keyword and an optional `position`.
    private fun box(
        id: String,
        display: String? = null,
        position: String? = null,
        children: List<IRComponent>? = null,
    ) = IRComponent(
        id = id,
        name = id,
        properties = buildList {
            display?.let { add(prop("Display", "\"$it\"")) }
            position?.let { add(prop("Position", "\"$it\"")) }
        },
        children = children,
    )

    // ── Role classification (css-tables-3 §2.1) ────────────────────────

    @Test
    fun `underscore and hyphen display spellings classify identically`() {
        // The wire has carried both spellings across waves; neither is
        // worth a normalization pass, so roleOf accepts both.
        assertEquals(TableBoxTree.Role.ROW_GROUP, TableBoxTree.roleOf(box("g", "TABLE_ROW_GROUP")))
        assertEquals(TableBoxTree.Role.ROW_GROUP, TableBoxTree.roleOf(box("g", "table-row-group")))
        assertEquals(TableBoxTree.Role.CELL, TableBoxTree.roleOf(box("c", "TABLE_CELL")))
        assertEquals(TableBoxTree.Role.CELL, TableBoxTree.roleOf(box("c", "table-cell")))
        assertEquals(TableBoxTree.Role.ROW, TableBoxTree.roleOf(box("r", "TABLE_ROW")))
        assertEquals(TableBoxTree.Role.CAPTION, TableBoxTree.roleOf(box("p", "TABLE_CAPTION")))
    }

    @Test
    fun `all three group boxes share the ROW_GROUP role`() {
        // §2.1 lists row/header/footer groups; all three are transparent
        // for row ordering, so the splice must treat them alike.
        for (kw in listOf("TABLE_ROW_GROUP", "TABLE_HEADER_GROUP", "TABLE_FOOTER_GROUP")) {
            assertEquals(kw, TableBoxTree.Role.ROW_GROUP, TableBoxTree.roleOf(box("g", kw)))
        }
    }

    @Test
    fun `inline-table folds to TABLE and non-table displays are NONE`() {
        // inline-table has the identical INTERNAL box tree (§2.1) and
        // differs only in outer display, which the surrounding flow owns.
        assertEquals(TableBoxTree.Role.TABLE, TableBoxTree.roleOf(box("t", "INLINE_TABLE")))
        assertEquals(TableBoxTree.Role.TABLE, TableBoxTree.roleOf(box("t", "TABLE")))
        assertEquals(TableBoxTree.Role.NONE, TableBoxTree.roleOf(box("d", "BLOCK")))
        assertEquals(TableBoxTree.Role.NONE, TableBoxTree.roleOf(box("d", "TABLE_COLUMN")))
        // No Display declaration at all — the overwhelmingly common case
        // (only 4 of the 324 frozen wave31-final tests declare any table role).
        assertEquals(TableBoxTree.Role.NONE, TableBoxTree.roleOf(box("d")))
    }

    @Test
    fun `cells and captions render as block containers, tables and rows do not`() {
        // css-tables-3 §2.1: a table-cell "establishes a block container
        // box for its contents" — it must NOT re-enter table layout, which
        // is what turned static-inside-table-cell's green abspos into a
        // PlaceholderContent text run with no box and no background.
        assertTrue(TableBoxTree.rendersAsBlockContainer(TableBoxTree.Role.CELL))
        assertTrue(TableBoxTree.rendersAsBlockContainer(TableBoxTree.Role.CAPTION))
        assertFalse(TableBoxTree.rendersAsBlockContainer(TableBoxTree.Role.TABLE))
        assertFalse(TableBoxTree.rendersAsBlockContainer(TableBoxTree.Role.ROW))
        assertFalse(TableBoxTree.rendersAsBlockContainer(TableBoxTree.Role.NONE))
    }

    // ── The row-group splice ───────────────────────────────────────────

    @Test
    fun `a group-free child list is returned by IDENTITY`() {
        // Byte-stability guarantee: a table that already hands rows
        // directly (the shape most extracted fixtures carry, e.g.
        // css-tables/absolute-tables-016) must see the SAME list object —
        // no allocation, no reordering, nothing for a baseline to notice.
        val rows = listOf(box("r0", "TABLE_ROW"), box("r1", "TABLE_ROW"))
        assertSame(rows, TableBoxTree.rowsOf(rows))
    }

    @Test
    fun `the implied tbody is spliced so real cells stay one level from the row`() {
        // The abspos-container-change-dynamic-001 shape. Before the splice
        // the synthesizer read the tbody as a ROW and the tr as a CELL,
        // putting both <td>s below the level the cell loop looks at.
        val tdA = box("tdA", "TABLE_CELL")
        val tdB = box("tdB", "TABLE_CELL", position = "RELATIVE")
        val tr = box("tr", "TABLE_ROW", children = listOf(tdA, tdB))
        val tbody = box("tbody", "TABLE_ROW_GROUP", children = listOf(tr))
        val rows = TableBoxTree.rowsOf(listOf(tbody))
        assertEquals("the tbody contributes its rows, not itself", listOf("tr"), rows.map { it.id })
        assertEquals("the row's children are the real cells",
            listOf("tdA", "tdB"), rows[0].children!!.map { it.id })
    }

    @Test
    fun `groups and loose rows interleave in document order`() {
        // A thead + a loose tr + a tbody must flatten in source order —
        // overlay/paint order downstream depends on it (CSS 2.1 Appendix E
        // tree order).
        val r0 = box("r0", "TABLE_ROW")
        val r1 = box("r1", "TABLE_ROW")
        val r2 = box("r2", "TABLE_ROW")
        val thead = box("thead", "TABLE_HEADER_GROUP", children = listOf(r0))
        val tbody = box("tbody", "TABLE_ROW_GROUP", children = listOf(r2))
        assertEquals(listOf("r0", "r1", "r2"),
            TableBoxTree.rowsOf(listOf(thead, r1, tbody)).map { it.id })
    }

    @Test
    fun `an empty group contributes no rows`() {
        // `<tbody></tbody>` generates no row boxes (§2.1) — and an
        // all-empty-group table must fall through to the placeholder
        // branch rather than emitting a phantom row.
        assertEquals(emptyList<String>(),
            TableBoxTree.rowsOf(listOf(box("tbody", "TABLE_ROW_GROUP"))).map { it.id })
        assertEquals(emptyList<String>(), TableBoxTree.rowsOf(null).map { it.id })
        assertEquals(emptyList<String>(), TableBoxTree.rowsOf(emptyList()).map { it.id })
    }

    // ── The containing-block mirror ────────────────────────────────────

    @Test
    fun `a positioned table cell establishes a containing block`() {
        // CSS 2.2 §10.1 / §9.3.1: any non-static box is the containing
        // block for its abspos descendants — table-internal boxes are NOT
        // excluded. This is the predicate RenderTableContent publishes on
        // CanvasRootHoist.LocalHasPositionedAncestor; when it was missing,
        // the composition side hoisted the lime abspos away while the pure
        // walk (which reads the real IR tree and DOES see the relative
        // <td>) gave it no overlay slot — the box vanished from both.
        assertTrue(TableBoxTree.establishesContainingBlock(
            box("tdB", "TABLE_CELL", position = "RELATIVE")))
        assertTrue(TableBoxTree.establishesContainingBlock(
            box("tdB", "TABLE_CELL", position = "ABSOLUTE")))
        assertTrue(TableBoxTree.establishesContainingBlock(
            box("tr", "TABLE_ROW", position = "STICKY")))
    }

    @Test
    fun `a static table cell does NOT establish a containing block`() {
        // The status quo for every ordinary table in the corpus — the flag
        // must stay false or an abspos that legitimately hoists to the
        // canvas root would be pinned to a cell instead.
        assertFalse(TableBoxTree.establishesContainingBlock(
            box("tdA", "TABLE_CELL", position = "STATIC")))
        assertFalse(TableBoxTree.establishesContainingBlock(box("tdA", "TABLE_CELL")))
        assertFalse(TableBoxTree.establishesContainingBlock(box("t", "TABLE")))
    }

    @Test
    fun `the mirror predicate is the hoist's own, not a re-derivation`() {
        // If these two ever disagree a box is dropped from flow with no
        // overlay slot — the exact wave-32 defect. Pin them together so a
        // future edit to either fails HERE first.
        for (kw in listOf("STATIC", "RELATIVE", "ABSOLUTE", "FIXED", "STICKY")) {
            val c = box("x", "TABLE_CELL", position = kw)
            assertEquals(
                "role-aware and hoist predicates must agree for position: $kw",
                com.styleconverter.runtime.layout.position.CanvasRootHoist
                    .establishesContainingBlock(c.properties),
                TableBoxTree.establishesContainingBlock(c),
            )
        }
    }
}

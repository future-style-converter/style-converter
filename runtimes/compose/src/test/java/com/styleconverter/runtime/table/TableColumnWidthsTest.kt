package com.styleconverter.runtime.table

// Wave 52 lane L8 (vertical-wedges, M-E) — JVM pins for the column-width
// harvest (TableBoxTree.columnChains / columnWidthsPx): css-tables-3 §2.1 /
// §3.2, a dropped UA-only `<col>`/`<colgroup>` still carries its column's
// specified width. Swift twin: TableColumnWidthsTests.swift.
//
// Verbatim IR (tools/titan/runs/wave51-fix/sections/…/per-test-ir/):
//  • css-writing-modes/ch-units-vrl-003 `…__2__0-457` col: WritingMode
//    VERTICAL_RL, TextOrientation UPRIGHT, Width 5ch; table FontSize 20px
//    (the inherited set). Ref: the green cell 120 wide.
//  • css-writing-modes/ch-units-vrl-004 `…__2__0-466` colgroup, same props.
//  • css-tables/border-collapse-dynamic-col-001 `…__0__0-089` colgroup /
//    `…-090` col: DECLARED Display TABLE_COLUMN_GROUP / TABLE_COLUMN, Width
//    84 / 21 px — must harvest NOTHING (Android P 0.9812 must not move).
//
// MUTATION EXECUTED (2026-10-05, isolated HEAD export, restored byte-exact,
// sha-256 verified): `columnWidthsPx` mapping every chain to null (the
// pre-wave-52 drop) → `an upright col contributes its 5ch column width`
// and `an empty colgroup is one column of its own` fail (null ≠ 120).

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.spacing.ChUnitMetrics
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TableColumnWidthsTest {

    // The production probe (Paint.FontMetrics, absent on the JVM), restored.
    private val productionProbe = ChUnitMetrics.verticalMetricsProbe

    /** Wire property from raw JSON, as the IR decoder hands it over. */
    private fun prop(type: String, json: String) = IRProperty(type, Json.parseToJsonElement(json))

    /** A component with a source tag and optional children. */
    private fun comp(id: String, tag: String, props: List<IRProperty>, kids: List<IRComponent>? = null) =
        IRComponent(id = id, name = id, properties = props, children = kids, _tag = tag)

    /** The upright col / colgroup property list of ch-units-vrl-003/-004. */
    private val uprightCol = listOf(
        prop("WritingMode", "\"VERTICAL_RL\""),
        prop("TextOrientation", "\"UPRIGHT\""),
        prop("Width", """{"type":"length","original":{"v":5,"u":"CH"}}"""),
    )

    /** The table's inheritable set: its own `font-size: 20px`. */
    private val tableInherited = listOf(prop("FontSize", """{"px":20,"original":{"type":"length","px":20}}"""))

    /** The table body: tbody → tr → td (no width of its own). */
    private val body = comp("tb", "tbody", emptyList(), listOf(comp("tr", "tr", emptyList(), listOf(comp("td", "td", emptyList())))))

    @Before
    fun stub() {
        // Inter's hhea halves at 20 px → round + round = 24 (the upright '0').
        ChUnitMetrics.installDefaultTypeface(null)
        ChUnitMetrics.verticalMetricsProbe = { _, size -> (1984f / 2048f * size) to (494f / 2048f * size) }
    }

    @After
    fun restore() {
        ChUnitMetrics.verticalMetricsProbe = productionProbe
        ChUnitMetrics.installDefaultTypeface(null)
    }

    @Test
    fun `an upright col contributes its 5ch column width`() {
        // ch-units-vrl-003: table → [col, tbody]; one column, 5 × 24 = 120.
        val children = listOf(comp("c", "col", uprightCol), body)
        assertEquals(1, TableBoxTree.columnChains(children).size)
        assertEquals(listOf(120f), TableBoxTree.columnWidthsPx(children, tableInherited))
    }

    @Test
    fun `an empty colgroup is one column of its own`() {
        // ch-units-vrl-004: table → [colgroup, tbody] with no <col> inside.
        val children = listOf(comp("g", "colgroup", uprightCol), body)
        assertEquals(listOf(120f), TableBoxTree.columnWidthsPx(children, tableInherited))
    }

    @Test
    fun `a declared table-column box harvests nothing`() {
        // border-collapse-dynamic-col-001: DECLARED displays keep their
        // pre-wave-38 passthrough, so no column chain exists at all.
        fun declared(id: String, tag: String, display: String, px: Int, kids: List<IRComponent>? = null) = comp(
            id, tag, listOf(prop("Width", """{"type":"length","px":$px}"""), prop("Display", "\"$display\"")), kids,
        )
        val cols = (0..3).map { declared("c$it", "col", "TABLE_COLUMN", 21) }
        val children = listOf(declared("g", "colgroup", "TABLE_COLUMN_GROUP", 84, cols), body)
        assertEquals(emptyList<List<IRComponent>>(), TableBoxTree.columnChains(children))
        assertEquals(emptyList<Float?>(), TableBoxTree.columnWidthsPx(children, tableInherited))
    }

    @Test
    fun `cols inside a colgroup are one column each, the group width as fallback`() {
        // <colgroup style="width:30px"><col style="width:40px"><col></colgroup>
        val px = { v: Int -> prop("Width", """{"type":"length","px":$v}""") }
        val group = comp("g", "colgroup", listOf(px(30)), listOf(comp("a", "col", listOf(px(40))), comp("b", "col", emptyList())))
        assertEquals(listOf(40f, 30f), TableBoxTree.columnWidthsPx(listOf(group, body), tableInherited))
        // No table children at all → no columns, never a crash.
        assertEquals(emptyList<Float?>(), TableBoxTree.columnWidthsPx(null, tableInherited))
    }
}

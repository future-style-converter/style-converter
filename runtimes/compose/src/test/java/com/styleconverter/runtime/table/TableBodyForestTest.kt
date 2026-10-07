package com.styleconverter.runtime.table

import com.styleconverter.runtime.core.ir.IRComponent
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * wave-53 lane L3 (item B) — pins for [TableBodyForest], the composed canvas's
 * `display: table` BODY rewrite (CSS 2.1 §17.2.1 rule 2, §8.3), on the VERBATIM
 * wave52-ship per-test IR (decoded + slot-composed exactly as the harness does).
 * The Swift twin is TableBodyForestTests; the stack consequence (0 gap above
 * the synthetic table) is pinned in the harness's ComposedCanvasTableBodyTest.
 *
 * WHY: s-11-1-1b-006's body children are sibling ROOTS, so `Display TABLE`
 * applied to an empty box (wave52-ship android P 0.9944, DEGENERATE: the
 * square at rows 51-70, ref 56-75).
 *
 * EXECUTED MUTATIONS (TableBodyForest.kt, this class run alone, source
 * restored byte-exact — sha256 in tools/titan/results/wave53-canvas-root/_note.md):
 *   BS  the run re-parented under the body-root (no fresh TABLE root) → shape red.
 *   BM1 the `Display ∈ {TABLE, INLINE_TABLE}` trigger dropped       → m1 red (a98rgb-003 —
 *       005 and 001 are identity even without it: 005's only other box is
 *       slotted under the body, 001 has no body-root).
 *   BM2 the §8.3 Margin* strip skipped                               → m2 red.
 *   BM3 the out-of-flow filter dropped                               → m3 red.
 *   BM4 a non-cell root placed straight into the row                 → m4 red.
 *   BM5 BorderSpacing not copied onto the synthetic table            → m5 red.
 */
class TableBodyForestTest {

    /** Decode + slot-compose a v2 document exactly as the harness does. */
    private fun rootsOf(componentsJson: String): List<IRComponent> =
        com.styleconverter.runtime.core.renderer.SlotComposer.compose(
            com.styleconverter.runtime.core.ir.IRDocumentDecoder.decode(
                """{"irVersion":2,"minReaderVersion":2,"components":[$componentsJson]}"""))

    /** CSS2/css21-errata/s-11-1-1b-006 — all four roots, verbatim (`body { display: table }`). */
    private val s006 = """
        {"id":"wpt__css2__css21-errata__s-11-1-1b-006__0-141","name":"wpt__CSS2__css21-errata__s-11-1-1b-006__0","properties":[{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"},{"type":"Display","data":"TABLE"},{"type":"BorderSpacing","data":{"type":"single","px":0}},{"type":"MarginTop","data":{"px":40}},{"type":"MarginRight","data":{"px":8}},{"type":"MarginBottom","data":{"px":8}},{"type":"MarginLeft","data":{"px":8}}],"meta":{"role":"body-root"}},
        {"id":"wpt__css2__css21-errata__s-11-1-1b-006__1-142","name":"wpt__CSS2__css21-errata__s-11-1-1b-006__1","properties":[{"type":"Generic","data":{"propertyName":"display","rawValue":"caption","_unmapped":true}},{"type":"MarginBottom","data":{"px":10}}],"meta":{"role":"ws-after"}},
        {"id":"wpt__css2__css21-errata__s-11-1-1b-006__2-143","name":"wpt__CSS2__css21-errata__s-11-1-1b-006__2","properties":[{"type":"Display","data":"TABLE_CELL"},{"type":"Width","data":{"type":"length","px":20}},{"type":"Height","data":{"type":"length","px":20}},{"type":"MarginTop","data":{"px":-15}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"meta":{"role":"ws-after"}},
        {"id":"wpt__css2__css21-errata__s-11-1-1b-006__3-144","name":"wpt__CSS2__css21-errata__s-11-1-1b-006__3","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Top","data":{"px":0}},{"type":"Left","data":{"px":8}}],"text":"Test passes if there is a black square below.","meta":{"sourceTag":"p"}}
    """.trimIndent()

    /** CSS2/css21-errata/s-11-1-1b-005 — verbatim (`display: table-cell` body). */
    private val s005 = """
        {"id":"wpt__css2__css21-errata__s-11-1-1b-005__0-139","name":"wpt__CSS2__css21-errata__s-11-1-1b-005__0","properties":[{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"},{"type":"Display","data":"TABLE_CELL"},{"type":"BorderSpacing","data":{"type":"single","px":0}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":1,"b":1},"original":"white"}},{"type":"MarginTop","data":{"px":-15}},{"type":"MarginRight","data":{"px":8}},{"type":"MarginBottom","data":{"px":8}},{"type":"MarginLeft","data":{"px":8}},{"type":"Width","data":{"type":"length","px":20}},{"type":"Height","data":{"type":"length","px":20}}],"meta":{"role":"body-root"}},
        {"id":"css21-errata__s-11-1-1b-005__0-140","name":"css21-errata__s-11-1-1b-005__0","properties":[{"type":"Position","data":"ABSOLUTE"},{"type":"Top","data":{"px":0}}],"slot":{"parent":"wpt__css2__css21-errata__s-11-1-1b-005__0-139"},"text":"Test passes if there is a black square below.","meta":{"sourceTag":"p"}}
    """.trimIndent()

    /** CSS2/css21-errata/s-11-1-1b-001 — verbatim (a real <table>, no body-root). */
    private val s001 = """
        {"id":"wpt__css2__css21-errata__s-11-1-1b-001__0-117","name":"wpt__CSS2__css21-errata__s-11-1-1b-001__0","properties":[],"text":"Test passes if there is a black square below.","meta":{"sourceTag":"p","role":"ws-after"}},
        {"id":"wpt__css2__css21-errata__s-11-1-1b-001__1-118","name":"wpt__CSS2__css21-errata__s-11-1-1b-001__1","properties":[{"type":"OverflowX","data":"HIDDEN"},{"type":"OverflowY","data":"HIDDEN"},{"type":"BorderSpacing","data":{"type":"single","px":0}}],"meta":{"sourceTag":"table"}},
        {"id":"css21-errata__s-11-1-1b-001__1__0-119","name":"css21-errata__s-11-1-1b-001__1__0","properties":[{"type":"MarginBottom","data":{"px":10}}],"slot":{"parent":"wpt__css2__css21-errata__s-11-1-1b-001__1-118"},"meta":{"sourceTag":"caption","role":"ws-after"}},
        {"id":"css21-errata__s-11-1-1b-001__1__1-120","name":"css21-errata__s-11-1-1b-001__1__1","properties":[],"slot":{"parent":"wpt__css2__css21-errata__s-11-1-1b-001__1-118"},"meta":{"sourceTag":"tr"}},
        {"id":"css21-errata__s-11-1-1b-001__1__1__0-121","name":"css21-errata__s-11-1-1b-001__1__1__0","properties":[{"type":"PaddingTop","data":{"px":0}},{"type":"PaddingRight","data":{"px":0}},{"type":"PaddingBottom","data":{"px":0}},{"type":"PaddingLeft","data":{"px":0}}],"slot":{"parent":"css21-errata__s-11-1-1b-001__1__1-120"},"meta":{"sourceTag":"td"}},
        {"id":"css21-errata__s-11-1-1b-001__1__1__0__0-122","name":"css21-errata__s-11-1-1b-001__1__1__0__0","properties":[{"type":"Width","data":{"type":"length","px":20}},{"type":"Height","data":{"type":"length","px":25}},{"type":"BorderTopWidth","data":{"px":10}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"MarginTop","data":{"px":-15}},{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0,"b":0},"original":"black"}}],"slot":{"parent":"css21-errata__s-11-1-1b-001__1__1__0-121"}}
    """.trimIndent()

    /** css-color/a98rgb-003 — verbatim (a colour-only body-root followed by in-flow roots). */
    private val a98 = """
        {"id":"wpt__css-color__a98rgb-003__0-005","name":"wpt__css-color__a98rgb-003__0","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.5019607843137255,"g":0.5019607843137255,"b":0.5019607843137255},"original":"grey"}}],"meta":{"role":"body-root"}},
        {"id":"wpt__css-color__a98rgb-003__1-006","name":"wpt__css-color__a98rgb-003__1","properties":[],"text":"Test passes if you see a single square, and not two rectangles of different colors.","meta":{"sourceTag":"p","role":"ws-after"}},
        {"id":"wpt__css-color__a98rgb-003__2-007","name":"wpt__css-color__a98rgb-003__2","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.996078431372549,"g":0.996078431372549,"b":0.996078431372549},"original":{"r":254,"g":254,"b":254}}},{"type":"Width","data":{"type":"length","original":{"v":12,"u":"EM"}}},{"type":"Height","data":{"type":"length","original":{"v":6,"u":"EM"}}},{"type":"MarginBottom","data":{"px":0}}],"meta":{"role":"ws-after"}},
        {"id":"wpt__css-color__a98rgb-003__3-008","name":"wpt__css-color__a98rgb-003__3","properties":[{"type":"BackgroundColor","data":{"srgb":{"r":0.9999300658875595,"g":1,"b":1},"original":{"type":"color","colorSpace":"a98-rgb","values":[1,1,1]}}},{"type":"Width","data":{"type":"length","original":{"v":12,"u":"EM"}}},{"type":"Height","data":{"type":"length","original":{"v":6,"u":"EM"}}},{"type":"MarginTop","data":{"px":0}}]}
    """.trimIndent()

    /** A component's Display enum leaf. */
    private fun displayOf(c: IRComponent) = (c.properties.lastOrNull { it.type == "Display" }?.data as? JsonPrimitive)?.content

    @Test
    fun shape_bodyRootUnchanged_tableRowCells_abspos() {
        val roots = rootsOf(s006)
        val out = TableBodyForest.rewrite(roots)
        assertEquals(3, out.size)
        // [body-root unchanged, TABLE#table → ROW → [CELL{1-142}, 2-143], 3-144 unchanged]
        assertSame(roots[0], out[0])
        val table = out[1]
        assertEquals(roots[0].id + "#table", table.id)
        assertEquals("TABLE", displayOf(table))
        val row = table.children!!.single()
        assertEquals("TABLE_ROW", displayOf(row))
        assertEquals(listOf(roots[0].id + "#cell0", roots[2].id), row.children!!.map { it.id })
        assertEquals(listOf(roots[1]), row.children!![0].children)
        assertSame(roots[3], out[2])
    }

    @Test
    fun m1_identity_onNonTableBodies() {
        // No trigger → the SAME instance (005: table-cell body; 001: no body-root;
        // a98rgb-003: a plain colour body followed by in-flow roots).
        for (doc in listOf(s005, s001, a98)) {
            val roots = rootsOf(doc)
            assertSame(roots, TableBodyForest.rewrite(roots))
        }
    }

    @Test
    fun m2_cellLosesItsMargins() {
        // CSS 2.1 §8.3: margin-top does not apply to a table cell.
        val td = TableBodyForest.rewrite(rootsOf(s006))[1].children!!.single().children!![1]
        assertFalse(td.properties.any { it.type.startsWith("Margin") })
        // …and keeps everything else (its 20×20 size and black fill).
        assertEquals(listOf("Display", "Width", "Height", "BackgroundColor"), td.properties.map { it.type })
    }

    @Test
    fun m3_absposStaysARoot_neverWrapped() {
        val roots = rootsOf(s006)
        val out = TableBodyForest.rewrite(roots)
        assertSame(roots[3], out[2])
        // Exactly two cells: the anonymous one and the td — the abspos <p> is in neither.
        val cells = out[1].children!!.single().children!!
        assertEquals(2, cells.size)
        assertTrue(cells.none { c -> c.id == roots[3].id || c.children.orEmpty().any { it.id == roots[3].id } })
    }

    @Test
    fun m4_nonCellRunIsWrappedInAnAnonymousCell() {
        val first = TableBodyForest.rewrite(rootsOf(s006))[1].children!!.single().children!![0]
        assertEquals("TABLE_CELL", displayOf(first))
    }

    @Test
    fun m5_tableCarriesTheBodysBorderSpacing() {
        val table = TableBodyForest.rewrite(rootsOf(s006))[1]
        assertEquals("""{"type":"single","px":0}""",
            table.properties.single { it.type == "BorderSpacing" }.data.toString())
    }
}

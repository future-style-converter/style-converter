package com.styleconverter.runtime.table

// wave-53 lane L3 (item B) — a `display: table` BODY forms a table on the
// composed canvas (CSS 2.1 §17.2.1 rule 2). Twin of SwiftUI
// StyleEngine/table/TableBodyForest.swift; the web harness lets Chrome do the
// same fixup (apps/web-harness/src/ui/CanvasTableBody.ts).
//
// WHY: the extractor emits html+body as ONE synthetic `body-root` whose element
// children are SIBLING ROOTS (extract-fixture.mjs slots them under the body
// only when it declares a height), so `Display TABLE` on the body-root applied
// to an EMPTY box and its children stacked as blocks. MEASURED on wave52-ship
// CSS2/css21-errata/s-11-1-1b-006 android P 0.9944 (DEGENERATE): the td's
// square at image rows 51-70 (a 10-px caption-div gap, then the td's own −15
// margin-top) where the reference — Chrome laying the body out as a table —
// has 56-75.
//
// The rewrite: the run of in-flow roots after the body-root becomes one
// synthetic TABLE (carrying the body's BorderSpacing / BorderCollapse) → one
// TABLE_ROW → cells, by §17.2.1 rule 2 ("generate missing child wrappers"):
// each run of consecutive non-cell roots goes into ONE anonymous TABLE_CELL,
// and cell roots pass through without their Margin* (CSS 2.1 §8.3: margins do
// not apply to table-internal boxes). Out-of-flow roots stay roots in place
// (they are hoisted to the ICB anyway). The body-root itself is untouched —
// every canvas resolver (colour, padding, margin) reads it.
//
// wave-54 lane L2 (TB-android) — re-landed from 276757ea (reverted at the
// wave-53 probe, d773ff6a) plus ONE marker: the three synthetic boxes carry
// `role = ANONYMOUS_ROLE`, the key TableCellHug.chrome reads so the anonymous
// table paints no demo cell stroke (CSS 2.1 §17.2.1: anonymous boxes carry no
// border) and sizes its cells at max-content (§17.5.2.2). The role lives only
// in memory (never serialized; every runtime reader compares role against
// "body-root" / "line-break" only, so the new value reads as null to all of
// them). ASYMMETRY, deliberate: the Swift twin keeps `meta: nil` — iOS has no
// demo stroke and TableSeparatedLayout already sizes cells at their ideal
// width (wave-53 B-ios shipped right), so editing it would rebuild iOS for
// nothing.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.layout.position.CanvasRootHoist
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

object TableBodyForest {

    /** The in-memory role of the synthetic table / row / cell (wave 54; read by TableCellHug.chrome). */
    const val ANONYMOUS_ROLE = "anonymous-table"

    /** Is [c] one of this forest's synthetic anonymous boxes? (§17.2.1; the wire never carries this role.) */
    fun isAnonymous(c: IRComponent): Boolean = c.role == ANONYMOUS_ROLE

    /** The body displays that make the body a table box (css-display-3 §2.3). */
    private val TABLE_BODY = setOf("TABLE", "INLINE_TABLE")

    /** Proper table children other than cells (§17.2.1): a run holding one bails. */
    private val PROPER_NON_CELL = setOf("TABLE_ROW", "TABLE_ROW_GROUP", "TABLE_HEADER_GROUP",
        "TABLE_FOOTER_GROUP", "TABLE_CAPTION", "TABLE_COLUMN", "TABLE_COLUMN_GROUP")

    /** The margin longhands §8.3 says do not apply to a table cell. */
    private val MARGIN_TYPES = setOf("MarginTop", "MarginRight", "MarginBottom", "MarginLeft",
        "MarginBlockStart", "MarginBlockEnd", "MarginInlineStart", "MarginInlineEnd")

    /** The body's table-box properties the synthetic table inherits (§17.6). */
    private val TABLE_BOX_TYPES = setOf("BorderSpacing", "BorderCollapse")

    /** A component's LAST declared `Display` enum leaf, uppercased (null when absent). */
    private fun displayOf(c: IRComponent): String? =
        (c.properties.lastOrNull { it.type == "Display" }?.data as? JsonPrimitive)?.contentOrNull?.uppercase()  // cascade: last wins

    /** Out of flow at the root: hoisted to the ICB, or an RC1 static-position abspos (no ancestors). */
    private fun isOutOfFlow(c: IRComponent): Boolean =
        CanvasRootHoist.shouldHoistToCanvasRoot(c.properties, hasPositionedAncestor = false) ||   // fixed / inset abspos
            CanvasRootHoist.rendersInFlowAsStaticPosition(c.properties, hasPositionedAncestor = false)  // RC1 abspos

    /** A length leaf that is a concrete 0 px (`{px:0}` or the wrapped `{original:{px:0}}`). */
    private fun isZeroPx(d: JsonElement): Boolean {
        val o = d as? JsonObject ?: return false                            // keywords (thin/medium/auto): not zero
        val px = (o["px"] as? JsonPrimitive)?.doubleOrNull                  // `{px:N}`
            ?: ((o["original"] as? JsonObject)?.get("px") as? JsonPrimitive)?.doubleOrNull  // `{original:{px:N}}`
        return px == 0.0                                                    // em / % / calc: not a known zero
    }

    /** The body declares a non-zero padding or border width: it paints its own box, so bail. */
    private fun paintsOwnEdges(body: IRComponent): Boolean = body.properties.any { p ->
        (p.type.startsWith("Padding") || (p.type.startsWith("Border") && p.type.endsWith("Width"))) &&
            !isZeroPx(p.data)                                               // 0 carriers: bail, never guess
    }

    /** A synthetic component: id/name derived from the body's, children attached, marked anonymous. */
    private fun synthetic(body: IRComponent, suffix: String, props: List<IRProperty>, kids: List<IRComponent>) =
        IRComponent(id = body.id + suffix, name = body.name + suffix, properties = props, children = kids,
            role = ANONYMOUS_ROLE)                                          // the §17.2.1 marker; no slot

    /** One `Display` enum leaf, the shape the converter emits (`{"type":"Display","data":"TABLE"}`). */
    private fun display(value: String) = IRProperty("Display", JsonPrimitive(value))   // read by DisplayExtractor

    /**
     * The rewritten forest, or [roots] itself (the SAME instance) when the
     * body-root is not a table, paints its own edges, has no in-flow run after
     * it, or that run already holds a proper non-cell table child.
     */
    fun rewrite(roots: List<IRComponent>): List<IRComponent> {
        val bodyIdx = roots.indexOfFirst { it.role == "body-root" }       // one body per document
        if (bodyIdx < 0) return roots                                       // no body bag at all
        val body = roots[bodyIdx]
        if (displayOf(body) !in TABLE_BODY || paintsOwnEdges(body)) return roots   // the trigger + edge bail
        val after = roots.drop(bodyIdx + 1)                                // the body's (sibling) children
        val run = after.filterNot(::isOutOfFlow)                           // the in-flow run
        if (run.isEmpty() || run.any { displayOf(it) in PROPER_NON_CELL }) return roots   // 0 carriers: bail
        // §17.2.1 rule 2: consecutive non-cells share ONE anonymous cell; cells pass through.
        val cells = mutableListOf<IRComponent>()                            // the row's children, in order
        val pending = mutableListOf<IRComponent>()                          // the open run of non-cells
        fun flush() {                                                       // close the open anonymous cell
            if (pending.isEmpty()) return                                   // nothing open
            cells += synthetic(body, "#cell${cells.size}", listOf(display("TABLE_CELL")), pending.toList())
            pending.clear()                                                 // the next non-cell opens a new one
        }
        for (r in run) {
            if (displayOf(r) == "TABLE_CELL") {                             // a proper cell
                flush()                                                     // ends any open anonymous cell
                cells += r.copy(properties = r.properties.filterNot { it.type in MARGIN_TYPES })  // §8.3
            } else pending += r                                             // joins the open anonymous cell
        }
        flush()                                                             // close the trailing run
        val row = synthetic(body, "#row", listOf(display("TABLE_ROW")), cells)   // rule 2's anonymous row
        val tableProps = listOf(display("TABLE")) + body.properties.filter { it.type in TABLE_BOX_TYPES }
        val table = synthetic(body, "#table", tableProps, listOf(row))     // the body's table box
        // The table takes the run's first position; out-of-flow roots keep their order after it.
        return roots.take(bodyIdx + 1) + table + after.filter(::isOutOfFlow)
    }
}

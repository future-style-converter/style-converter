package com.styleconverter.runtime.layout.position

// Wave 55 (lane L3, U1) — JVM pins for PositionedParentSlotGate, pure over
// the IR and three flags (no Robolectric here; the seam reading the flags is
// pinned by core/renderer/PositionedParentSlotSeamWiringTest):
//   (a) the `engages` truth table, every row;
//   (b) the verbatim selectors/dir-style-02a wire: exactly the children of
//       the four LTR absolute parents take the plain mount, never those of
//       the two RTL ones (the PLAIN trap, never shipped);
//   (c) the call-site helper: identity off the composed capture, the
//       one-element mount when engaged, the RTL guard's breadcrumb.
// Mutations executed red → restore → green (sha256) in
// tools/titan/results/wave55-positioned-parent-slot/_note.md.

import androidx.compose.ui.Modifier
import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.typography.TextStyleApplier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionedParentSlotGateTest {

    @Test
    fun `U1a engages - every row of the truth table`() {
        // An active host keeps every pre-wave-55 mount, whatever the rest.
        for (composed in listOf(true, false)) for (rtl in listOf(true, false)) {
            assertTrue("active host, composed=$composed rtl=$rtl", PositionedParentSlotGate.engages(true, composed, rtl))
        }
        // A host-inactive composed capture mounts an LTR parent (the fix)…
        assertTrue(PositionedParentSlotGate.engages(false, true, false))
        // …and withholds the RTL parent (the PLAIN trap: x = W + left).
        assertFalse(PositionedParentSlotGate.engages(false, true, true))
        // No composed capture (dark stage / inbox path) never mounts.
        assertFalse(PositionedParentSlotGate.engages(false, false, false))
        assertFalse(PositionedParentSlotGate.engages(false, false, true))
        // The breadcrumb names exactly the one row the RTL guard flips.
        assertTrue(PositionedParentSlotGate.withheldByRtlGuard(false, true, true))
        assertFalse(PositionedParentSlotGate.withheldByRtlGuard(true, true, true))
        assertFalse(PositionedParentSlotGate.withheldByRtlGuard(false, false, true))
        assertFalse(PositionedParentSlotGate.withheldByRtlGuard(false, true, false))
    }

    // One wire box: id, slot parent (null = a root) and its declarations.
    private data class Box(val id: String, val parent: String?, val properties: List<IRProperty>)

    // Decode a declaration list exactly as the per-test IR spells it.
    private fun box(id: String, parent: String?, declarations: String) = Box(
        id, parent,
        Json.parseToJsonElement("[$declarations]").jsonArray.map {
            IRProperty(it.jsonObject["type"]!!.jsonPrimitive.content, it.jsonObject["data"]!!)
        },
    )

    // tools/titan/runs/wave54-final/sections/selectors/per-test-ir/
    // wpt__selectors__dir-style-02a.json (byte-identical in wave55-open): the
    // relative roots of rows 4-9 and their subtrees, each box's Position /
    // Direction / Left / Top / Width / Height declarations VERBATIM, in wire
    // order (the only types the gate and the RTL origin read). The host is
    // inactive on this document (the lane census: nothing hoists, no RC1).
    private val wire = listOf(
        box("wpt__selectors__dir-style-02a__4-087", null,
            """{"type":"Direction","data":"LTR"},{"type":"Position","data":"RELATIVE"},""" +
            """{"type":"Width","data":{"type":"length","px":358}},{"type":"Height","data":{"type":"length","px":60}}"""),
        box("dir-style-02a__3__0-088", "wpt__selectors__dir-style-02a__4-087",
            """{"type":"Position","data":"ABSOLUTE"},{"type":"Width","data":{"type":"length","px":358}},""" +
            """{"type":"Height","data":{"type":"length","px":20}},{"type":"Left","data":{"px":0}},""" +
            """{"type":"Top","data":{"px":0}}"""),
        box("dir-style-02a__3__0__0-089", "dir-style-02a__3__0-088",
            """{"type":"Position","data":"ABSOLUTE"},{"type":"Left","data":{"px":0}},{"type":"Top","data":{"px":0}},""" +
            """{"type":"Width","data":{"type":"length","px":227.78}},{"type":"Height","data":{"type":"length","px":20}},""" +
            """{"type":"Direction","data":"LTR"}"""),
        box("dir-style-02a__3__1-090", "wpt__selectors__dir-style-02a__4-087",
            """{"type":"Direction","data":"LTR"},{"type":"Position","data":"ABSOLUTE"},""" +
            """{"type":"Width","data":{"type":"length","px":358}},{"type":"Height","data":{"type":"length","px":20}},""" +
            """{"type":"Left","data":{"px":0}},{"type":"Top","data":{"px":20}}"""),
        box("dir-style-02a__3__1__0-091", "dir-style-02a__3__1-090",
            """{"type":"Position","data":"ABSOLUTE"},{"type":"Left","data":{"px":0}},{"type":"Top","data":{"px":0}},""" +
            """{"type":"Width","data":{"type":"length","px":137.47}},{"type":"Height","data":{"type":"length","px":20}},""" +
            """{"type":"Direction","data":"LTR"}"""),
        box("dir-style-02a__3__2-092", "wpt__selectors__dir-style-02a__4-087",
            """{"type":"Direction","data":"RTL"},{"type":"Position","data":"ABSOLUTE"},""" +
            """{"type":"Width","data":{"type":"length","px":358}},{"type":"Height","data":{"type":"length","px":20}},""" +
            """{"type":"Left","data":{"px":0}},{"type":"Top","data":{"px":40}}"""),
        box("dir-style-02a__3__2__0-093", "dir-style-02a__3__2-092",
            """{"type":"Position","data":"ABSOLUTE"},{"type":"Left","data":{"px":4.61}},{"type":"Top","data":{"px":0}},""" +
            """{"type":"Width","data":{"type":"length","px":133.88}},{"type":"Height","data":{"type":"length","px":20}},""" +
            """{"type":"Direction","data":"LTR"}"""),
        box("dir-style-02a__3__2__1-094", "dir-style-02a__3__2-092",
            """{"type":"Position","data":"ABSOLUTE"},{"type":"Left","data":{"px":0}},{"type":"Top","data":{"px":0}},""" +
            """{"type":"Width","data":{"type":"length","px":4.61}},{"type":"Height","data":{"type":"length","px":20}},""" +
            """{"type":"Direction","data":"LTR"}"""),
        box("wpt__selectors__dir-style-02a__5-095", null,
            """{"type":"Direction","data":"LTR"},{"type":"Position","data":"RELATIVE"},""" +
            """{"type":"Width","data":{"type":"length","px":358}},{"type":"Height","data":{"type":"length","px":60}}"""),
        box("dir-style-02a__4__0-096", "wpt__selectors__dir-style-02a__5-095",
            """{"type":"Position","data":"ABSOLUTE"},{"type":"Width","data":{"type":"length","px":358}},""" +
            """{"type":"Height","data":{"type":"length","px":20}},{"type":"Left","data":{"px":0}},""" +
            """{"type":"Top","data":{"px":0}}"""),
        box("dir-style-02a__4__0__0-097", "dir-style-02a__4__0-096",
            """{"type":"Position","data":"ABSOLUTE"},{"type":"Left","data":{"px":4.61}},{"type":"Top","data":{"px":0}},""" +
            """{"type":"Width","data":{"type":"length","px":224.19}},{"type":"Height","data":{"type":"length","px":20}},""" +
            """{"type":"Direction","data":"LTR"}"""),
        box("dir-style-02a__4__0__1-098", "dir-style-02a__4__0-096",
            """{"type":"Position","data":"ABSOLUTE"},{"type":"Left","data":{"px":0}},{"type":"Top","data":{"px":0}},""" +
            """{"type":"Width","data":{"type":"length","px":4.61}},{"type":"Height","data":{"type":"length","px":20}},""" +
            """{"type":"Direction","data":"LTR"}"""),
        box("dir-style-02a__4__1-099", "wpt__selectors__dir-style-02a__5-095",
            """{"type":"Direction","data":"LTR"},{"type":"Position","data":"ABSOLUTE"},""" +
            """{"type":"Width","data":{"type":"length","px":358}},{"type":"Height","data":{"type":"length","px":20}},""" +
            """{"type":"Left","data":{"px":0}},{"type":"Top","data":{"px":20}}"""),
        box("dir-style-02a__4__1__0-100", "dir-style-02a__4__1-099",
            """{"type":"Position","data":"ABSOLUTE"},{"type":"Left","data":{"px":0}},{"type":"Top","data":{"px":0}},""" +
            """{"type":"Width","data":{"type":"length","px":137.47}},{"type":"Height","data":{"type":"length","px":20}},""" +
            """{"type":"Direction","data":"LTR"}"""),
        box("dir-style-02a__4__2-101", "wpt__selectors__dir-style-02a__5-095",
            """{"type":"Direction","data":"RTL"},{"type":"Position","data":"ABSOLUTE"},""" +
            """{"type":"Width","data":{"type":"length","px":358}},{"type":"Height","data":{"type":"length","px":20}},""" +
            """{"type":"Left","data":{"px":0}},{"type":"Top","data":{"px":40}}"""),
        box("dir-style-02a__4__2__0-102", "dir-style-02a__4__2-101",
            """{"type":"Position","data":"ABSOLUTE"},{"type":"Left","data":{"px":4.61}},{"type":"Top","data":{"px":0}},""" +
            """{"type":"Width","data":{"type":"length","px":133.88}},{"type":"Height","data":{"type":"length","px":20}},""" +
            """{"type":"Direction","data":"LTR"}"""),
        box("dir-style-02a__4__2__1-103", "dir-style-02a__4__2-101",
            """{"type":"Position","data":"ABSOLUTE"},{"type":"Left","data":{"px":0}},{"type":"Top","data":{"px":0}},""" +
            """{"type":"Width","data":{"type":"length","px":4.61}},{"type":"Height","data":{"type":"length","px":20}},""" +
            """{"type":"Direction","data":"LTR"}"""),
    )

    // Compose's USED direction: the renderer provides Rtl for a
    // `Direction: RTL` box (TextStyleApplier.extractDirection) and never
    // re-provides Ltr below it, so a box lays out RTL iff it or an ancestor
    // declares RTL.
    private fun laidOutRtl(id: String?): Boolean {
        val b = wire.firstOrNull { it.id == id } ?: return false
        return TextStyleApplier.extractDirection(b.properties) == TextStyleApplier.DirectionMode.RTL ||
            laidOutRtl(b.parent)
    }

    @Test
    fun `U1b dir-style-02a - the plain mount takes the LTR parents' children only`() {
        val byId = wire.associateBy { it.id }
        // Every child whose decision is the PLAIN mount (no RTL origin).
        val plain = wire.filter { child ->
            val parent = child.parent?.let(byId::get) ?: return@filter false
            PositionedParentSlotGate.decide(
                child.properties, parent.properties,
                hostActive = false, composedCapture = true, parentRtl = laidOutRtl(parent.id),
            ) == PositionedParentSlotGate.SlotMount.MOUNT
        }.map { it.id }.toSet()
        // Rows 4/5 (__3__0, __3__1) and 7/8 (__4__0, __4__1): five runs.
        assertEquals(
            setOf(
                "dir-style-02a__3__0__0-089", "dir-style-02a__3__1__0-091",
                "dir-style-02a__4__0__0-097", "dir-style-02a__4__0__1-098",
                "dir-style-02a__4__1__0-100",
            ),
            plain,
        )
        // Rows 6 and 9's parents lay out RTL: PLAIN would paint at W + left.
        assertTrue(laidOutRtl("dir-style-02a__3__2-092") && laidOutRtl("dir-style-02a__4__2-101"))
        assertFalse(laidOutRtl("dir-style-02a__4__0-096"))
    }

    // A plain `absolute` box with an optional `left`, as the reader emits it.
    private fun abs(left: Double? = null) = buildList {
        add(IRProperty("Position", Json.parseToJsonElement("\"ABSOLUTE\"")))
        left?.let { add(IRProperty("Left", Json.parseToJsonElement("""{"px":$it}"""))) }
    }

    @Test
    fun `U1c slotModifier - identity, the one-element mount, the RTL breadcrumb`() {
        // Dark stage / inbox: the identity Modifier itself, byte-stable.
        assertSame(Modifier, PositionedParentSlotGate.slotModifier(abs(4.61), abs(), false, false, false))
        // A host-inactive composed capture of an LTR parent: the mount.
        val mounted = PositionedParentSlotGate.slotModifier(abs(4.61), abs(), false, true, false)
        assertEquals(1, mounted.foldIn(0) { n, _ -> n + 1 })
        // A non-applying child (static parent) is never the slot's.
        assertSame(Modifier, PositionedParentSlotGate.slotModifier(abs(4.61), emptyList(), true, true, false))
        // An RTL parent with no px Width (no origin correction can apply, in
        // any state): withheld, and the breadcrumb is counted once.
        val key = PositionedParentSlotGate.RTL_UNMOUNTED_BREADCRUMB
        val before = PropertyTracker.getOccurrences(key)
        assertSame(Modifier, PositionedParentSlotGate.slotModifier(abs(4.61), abs(), false, true, true))
        assertEquals(before + 1, PropertyTracker.getOccurrences(key))
    }
}

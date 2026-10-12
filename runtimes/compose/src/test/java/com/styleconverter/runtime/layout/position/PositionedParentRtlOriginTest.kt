package com.styleconverter.runtime.layout.position

// Wave 55 (lane L3, U2) — JVM pins for PositionedParentRtlOrigin and the
// gate's RTL branch (pure; no Robolectric in this suite):
//   (a) originPx; (b) the applies / decline table; (c) the inkLeftPx model
//   and the element's physical `place`; (d) the verbatim wave54-final wires
//   of bidi-tab-001, dynamic-offset-rtl-001 and dir-style-02a row 6;
//   (e) the two-element mount, and the decline breadcrumb.
// Mutations executed red → restore → green (sha256) in
// tools/titan/results/wave55-positioned-parent-slot/_note.md.

import com.styleconverter.runtime.PropertyTracker
import com.styleconverter.runtime.layout.position.PositionedParentRtlOrigin.RtlOriginDecline
import com.styleconverter.runtime.core.ir.IRProperty
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionedParentRtlOriginTest {

    // Decode a declaration list exactly as the per-test IR spells it.
    private fun wire(declarations: String): List<IRProperty> =
        Json.parseToJsonElement("[$declarations]").jsonArray.map {
            IRProperty(it.jsonObject["type"]!!.jsonPrimitive.content, it.jsonObject["data"]!!)
        }

    // Shorthands for the synthetic rows of the tables below.
    private val abs = """{"type":"Position","data":"ABSOLUTE"}"""
    private val pxWidth = """{"type":"Width","data":{"type":"length","px":358}}"""
    private val left = """{"type":"Left","data":{"px":4.61}}"""
    private val right = """{"type":"Right","data":{"px":10}}"""

    @Test
    fun `U2a originPx is minus the bounded max width, none when unbounded`() {
        // The ink lands at colWidth − maxWidth + left = left (file header).
        assertEquals(-358, PositionedParentRtlOrigin.originPx(358))
        assertNull(PositionedParentRtlOrigin.originPx(null))
        // No origin places at 0: today's slot, unchanged.
        assertEquals(0, PositionedParentRtlOrigin.placedXPx(null))
    }

    @Test
    fun `U2b applies - the decline table`() {
        // Each row: parentRtl, parent wire, child wire → the decline reason.
        val o = PositionedParentRtlOrigin
        assertEquals(RtlOriginDecline.NONE, o.decline(true, wire("$abs,$pxWidth"), wire("$abs,$left")))
        assertEquals(RtlOriginDecline.NOT_RTL, o.decline(false, wire("$abs,$pxWidth"), wire("$abs,$left")))
        assertEquals(RtlOriginDecline.PARENT_WIDTH_NOT_PX, o.decline(true, wire(abs), wire("$abs,$left")))
        assertEquals(RtlOriginDecline.PARENT_WIDTH_NOT_PX,
            o.decline(true, wire("""$abs,{"type":"Width","data":{"type":"percentage","value":50}}"""), wire("$abs,$left")))
        assertEquals(RtlOriginDecline.NO_PHYSICAL_INLINE_INSET, o.decline(true, wire("$abs,$pxWidth"), wire(abs)))
        assertEquals(RtlOriginDecline.NO_PHYSICAL_INLINE_INSET,
            o.decline(true, wire("$abs,$pxWidth"), wire("""$abs,{"type":"InsetInlineStart","data":{"px":5}}""")))
        assertEquals(RtlOriginDecline.NONE, o.decline(true, wire("$abs,$pxWidth"), wire("$abs,$right")))
        assertEquals(RtlOriginDecline.BOTH_INLINE_INSETS, o.decline(true, wire("$abs,$pxWidth"), wire("$abs,$left,$right")))
        // Only the declines that keep today's placement carry a breadcrumb.
        assertNull(RtlOriginDecline.NONE.breadcrumb)
        assertNull(RtlOriginDecline.NOT_RTL.breadcrumb)
    }

    @Test
    fun `U2c inkLeftPx - the physical-left origin and the element's place`() {
        val o = PositionedParentRtlOrigin
        // dynamic-offset-rtl-001 today (host-active, mounted): x186 − 16.
        assertEquals(170f, o.inkLeftPx(100, 100, rtl = true, origin = false, leftPx = 70f), 1e-4f)
        // With the origin: the ref's x86 − 16.
        assertEquals(70f, o.inkLeftPx(100, 100, rtl = true, origin = true, leftPx = 70f), 1e-4f)
        // dir-style-02a row 6's text run: x16 + 4.61.
        assertEquals(4.61f, o.inkLeftPx(358, 358, rtl = true, origin = true, leftPx = 4.61f), 1e-4f)
        // LTR: the Column starts at 0 and `left` is all there is.
        assertEquals(70f, o.inkLeftPx(100, 100, rtl = false, origin = false, leftPx = 70f), 1e-4f)
        // A `right`-only 10-px box: W − w − right, physical right.
        assertEquals(80f, o.inkLeftPx(100, 100, rtl = true, origin = true, rightPx = 10f, boxWidthPx = 10), 1e-4f)
        // The element itself places with `place` at placedXPx — physical,
        // never `placeRelative` (which would mirror −maxWidth to +maxWidth).
        val src = sourceOf("layout/position/PositionedParentRtlOrigin.kt")
        val lambda = src.substring(src.indexOf("fun originModifier()"))
        assertTrue(lambda.contains("placeable.place(x = placedXPx(origin), y = 0)"))
        assertFalse(lambda.contains("placeRelative"))
    }

    // The runtime source, found by walking up to the repo root.
    private fun sourceOf(rel: String): String {
        val anchor = "runtimes/compose/src/main/java/com/styleconverter/runtime/$rel"
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, anchor).exists()) dir = dir.parentFile
        return File(requireNotNull(dir) { "repo root not found" }, anchor).readText()
    }

    // tools/titan/runs/wave54-final/sections/<sec>/per-test-ir/ (byte-identical
    // in wave55-open): each box's Position / Direction / Left / Top / Width /
    // Height declarations VERBATIM, in wire order. Pairs: parent, child,
    // whether the document's canvas host is active (the lane census).
    private val verbatim = listOf(
        // css-text/bidi/bidi-tab-001 `__2__0-038` → `__2__0__0-039` (host inactive).
        Triple(wire("""{"type":"Direction","data":"RTL"},$abs,{"type":"Left","data":{"px":12.03}},""" +
            """{"type":"Top","data":{"px":-2}},{"type":"Width","data":{"type":"length","px":108.38}},""" +
            """{"type":"Height","data":{"type":"length","px":24}}"""),
            wire("""$abs,{"type":"Left","data":{"px":0}},{"type":"Top","data":{"px":0}},""" +
                """{"type":"Width","data":{"type":"length","px":12.05}},""" +
                """{"type":"Height","data":{"type":"length","px":24}},{"type":"Direction","data":"LTR"}"""), false),
        // bidi-tab-001 `__3__0-041` → `__3__0__0-042` (host inactive).
        Triple(wire("""{"type":"Direction","data":"RTL"},$abs,{"type":"Left","data":{"px":0}},""" +
            """{"type":"Top","data":{"px":-2}},{"type":"Width","data":{"type":"length","px":108.38}},""" +
            """{"type":"Height","data":{"type":"length","px":24}}"""),
            wire("""$abs,{"type":"Left","data":{"px":96.33}},{"type":"Top","data":{"px":0}},""" +
                """{"type":"Width","data":{"type":"length","px":12.05}},""" +
                """{"type":"Height","data":{"type":"length","px":24}},{"type":"Direction","data":"LTR"}"""), false),
        // css-writing-modes/dynamic-offset-rtl-001 `__1-752` → `__0__0-753` (host ACTIVE).
        Triple(wire("""$abs,{"type":"Top","data":{"px":0}},{"type":"Left","data":{"px":0}},""" +
            """{"type":"Width","data":{"type":"length","px":100}},{"type":"Height","data":{"type":"length","px":100}},""" +
            """{"type":"Direction","data":"RTL"}"""),
            wire("""$abs,{"type":"Top","data":{"px":10}},{"type":"Width","data":{"type":"length","px":10}},""" +
                """{"type":"Height","data":{"type":"length","px":10}},{"type":"Left","data":{"px":70}}"""), true),
        // selectors/dir-style-02a row 6 `__3__2-092` → `__3__2__0-093` (host inactive).
        Triple(wire("""{"type":"Direction","data":"RTL"},$abs,{"type":"Width","data":{"type":"length","px":358}},""" +
            """{"type":"Height","data":{"type":"length","px":20}},{"type":"Left","data":{"px":0}},""" +
            """{"type":"Top","data":{"px":40}}"""),
            wire("""$abs,{"type":"Left","data":{"px":4.61}},{"type":"Top","data":{"px":0}},""" +
                """{"type":"Width","data":{"type":"length","px":133.88}},""" +
                """{"type":"Height","data":{"type":"length","px":20}},{"type":"Direction","data":"LTR"}"""), false),
    )

    @Test
    fun `U2d the verbatim RTL carriers take the origin in their own host state`() {
        for ((parent, child, hostActive) in verbatim) {
            // The origin applies (px Width, `left` only)…
            assertTrue(PositionedParentRtlOrigin.applies(true, parent, child))
            // …and the gate's RTL branch takes it, host active or not.
            assertEquals(
                PositionedParentSlotGate.SlotMount.MOUNT_RTL_ORIGIN,
                PositionedParentSlotGate.decide(child, parent, hostActive, composedCapture = true, parentRtl = true),
            )
            // The dark stage never does (no composed capture, no host).
            assertEquals(
                PositionedParentSlotGate.SlotMount.IDENTITY,
                PositionedParentSlotGate.decide(child, parent, false, composedCapture = false, parentRtl = true),
            )
        }
    }

    @Test
    fun `U2e the origin mount is two elements, and a declined origin is named`() {
        val child = wire("$abs,$left")
        // Default: the one-element wave-46 mount (PositionedParentFlowSlotTest).
        assertEquals(1, PositionedParentFlowSlot.mount(child).foldIn(0) { n, _ -> n + 1 })
        // rtlOrigin = true: the outer origin element + the anchor.
        assertEquals(2, PositionedParentFlowSlot.mount(child, rtlOrigin = true).foldIn(0) { n, _ -> n + 1 })
        // The gate hands the RTL carrier that two-element mount.
        val parent = wire("$abs,$pxWidth")
        assertEquals(2, PositionedParentSlotGate.slotModifier(child, parent, false, true, true).foldIn(0) { n, _ -> n + 1 })
        // An active host's RTL parent with no px Width keeps today's mount
        // (one element) and records the decline's breadcrumb.
        val key = PositionedParentRtlOrigin.PARENT_WIDTH_BREADCRUMB
        val before = PropertyTracker.getOccurrences(key)
        assertEquals(1, PositionedParentSlotGate.slotModifier(child, wire(abs), true, true, true).foldIn(0) { n, _ -> n + 1 })
        assertEquals(before + 1, PropertyTracker.getOccurrences(key))
        // The RTL-origin row of the gate: never the dark stage, never LTR.
        assertFalse(PositionedParentSlotGate.rtlOriginEngages(false, false, true))
        assertFalse(PositionedParentSlotGate.rtlOriginEngages(true, true, false))
        assertTrue(PositionedParentSlotGate.rtlOriginEngages(false, true, true))
        assertTrue(PositionedParentSlotGate.rtlOriginEngages(true, true, true))
    }
}

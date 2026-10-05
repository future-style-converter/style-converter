package com.styleconverter.runtime.core.renderer

// Wave-8 abspos unbounded-measure fix — JVM pins for the pure halves of
// the renderer change (the Modifier.layout wrapper itself needs a device;
// the decisions it hangs off do not):
//
//  1. isOutOfFlowChild — which children route through absposOverflowMeasure
//     (css-position-3 §2.1: absolute/fixed are out of flow; relative/
//     sticky/static are NOT and must keep the clamped in-flow measurement).
//  2. absposReportedAxis — the size the wrapper REPORTS to the parent:
//     the unbounded-measured size coerced back into the incoming
//     constraint envelope, so the parent's flow geometry is untouched
//     while the ink overflows (the flex-abspos-staticpos-align-self-safe
//     fixtures: a 69px child of a 50px container reports 50 and draws 69).
//  3. (wave 52, lane L7, static-position T1) the RC1 zero-flow mount stands
//     down for the child the css-grid-1 §9.2 overlay owns, on the VERBATIM
//     wave51-fix IR of css-grid/abspos/grid-abspos-staticpos-align-self-
//     center (child __0__0-242). MUTATION EXECUTED
//     (tools/titan/results/wave52-static-position/mutations.log):
//     M-K1 ignore `staticPositionOwned` in rendersInFlowAsStaticPosition →
//     K1a/K1c fail (the RC1 branch would re-mount the 0×0 anchor).

import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.layout.flexbox.AbsposStaticAlignment
import com.styleconverter.runtime.layout.position.CanvasRootHoist
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AbsposOverflowMeasureTest {

    // IR keyword wire shape: bare JSON string primitive (same as the
    // BlockCollapsePlanForTest Position props — the parser emits the
    // keyword uppercased; extractKeyword tolerates either case).
    private fun position(keyword: String) = listOf(
        IRProperty("Position", Json.parseToJsonElement("\"$keyword\""))
    )

    // ── 1. out-of-flow routing decision ──

    @Test
    fun `absolute and fixed are out of flow`() {
        // css-position-3 §2.1: both values take the box out of flow — the
        // fixture children declare `position: absolute`.
        assertTrue(ComponentRenderer.isOutOfFlowChild(position("ABSOLUTE")))
        assertTrue(ComponentRenderer.isOutOfFlowChild(position("FIXED")))
    }

    @Test
    fun `relative sticky static and undeclared stay in flow`() {
        // relative/sticky offset a box that KEEPS its flow slot — routing
        // them through the unbounded measure would let their specified
        // sizes escape legitimate container clamping.
        assertFalse(ComponentRenderer.isOutOfFlowChild(position("RELATIVE")))
        assertFalse(ComponentRenderer.isOutOfFlowChild(position("STICKY")))
        assertFalse(ComponentRenderer.isOutOfFlowChild(position("STATIC")))
        // No Position property at all → static per CSS initial value.
        assertFalse(ComponentRenderer.isOutOfFlowChild(emptyList()))
    }

    // ── 2. reported-size coercion ──

    @Test
    fun `oversized child reports the constraint max - the fixture numbers`() {
        // 69px border-box child (65 + 2·2 border) measured unbounded in a
        // 50px-content container: the parent flow sees 50 (its geometry is
        // byte-identical to the pre-fix clamp), the 69px ink overflows.
        assertEquals(50, ComponentRenderer.absposReportedAxis(69, 0, 50))
    }

    @Test
    fun `child smaller than the envelope reports its own size`() {
        // Nothing to coerce — a 30px abspos child in a 50px container
        // reports 30, exactly like the in-flow measurement would.
        assertEquals(30, ComponentRenderer.absposReportedAxis(30, 0, 50))
    }

    @Test
    fun `minimum constraints still bind the report`() {
        // propagateMinConstraints-style parents (min == max) must get the
        // size they demanded — the report never violates the envelope.
        assertEquals(40, ComponentRenderer.absposReportedAxis(10, 40, 50))
    }

    @Test
    fun `unbounded incoming max passes the measured size through`() {
        // Scrollable/intrinsic parents hand max == Constraints.Infinity;
        // coerceIn against it must return the measured size unchanged.
        assertEquals(69, ComponentRenderer.absposReportedAxis(69, 0, Int.MAX_VALUE))
    }

    // ── 3. wave-9 percent-size pre-resolution ──
    //
    // fillMaxWidth/fillMaxHeight(fraction) — SizingApplier's percent
    // mapping — are documented no-ops under the unbounded Constraints()
    // the wave-8 measure uses, so a `position:absolute; width:50%` child
    // would render with NO ink. resolveOutOfFlowPercentSizes rewrites the
    // percentage wire ({"type":"percentage","value":N} — pinned against
    // live converter output of `position:absolute; width:50%`) to the
    // frozen typed-length wire {"type":"length","px":…} against the
    // containing-block channel (css-position-3 §5.1).

    /** The live converter's percent-size wire for one axis property. */
    private fun percentProp(type: String, value: Double) = IRProperty(
        type, Json.parseToJsonElement("{\"type\":\"percentage\",\"value\":$value}")
    )

    /** Read the rewritten px back out of the typed-length wire. */
    private fun pxOf(p: IRProperty): Double? =
        ((p.data as? kotlinx.serialization.json.JsonObject)
            ?.get("px") as? kotlinx.serialization.json.JsonPrimitive)
            ?.content?.toDoubleOrNull()

    @Test
    fun `percent abspos sizes resolve to cb-fraction px`() {
        // width:50% of a 320px cb → 160px; height:40% of 200 → 80px —
        // exact constraint modifiers that survive the unbounded measure.
        val out = ComponentRenderer.resolveOutOfFlowPercentSizes(
            listOf(percentProp("Width", 50.0), percentProp("Height", 40.0)),
            com.styleconverter.runtime.core.variables.ContainingBlock(320f, 200f)
        )
        assertEquals(160.0, pxOf(out[0])!!, 1e-6)
        assertEquals(80.0, pxOf(out[1])!!, 1e-6)
    }

    @Test
    fun `logical sizes resolve against their mapped cb axis`() {
        // LTR horizontal-tb normalization: inline-size % → cb WIDTH,
        // block-size % → cb HEIGHT (css-logical-1 §4.1).
        val out = ComponentRenderer.resolveOutOfFlowPercentSizes(
            listOf(percentProp("InlineSize", 25.0), percentProp("BlockSize", 50.0)),
            com.styleconverter.runtime.core.variables.ContainingBlock(400f, 100f)
        )
        assertEquals(100.0, pxOf(out[0])!!, 1e-6)
        assertEquals(50.0, pxOf(out[1])!!, 1e-6)
    }

    @Test
    fun `unknown cb axis keeps the percentage wire`() {
        // Auto-sized ancestor (null axis): nothing honest to resolve
        // against — the percentage stays, and the LIST identity is
        // preserved (the frozen-baseline byte-stability contract).
        val props = listOf(percentProp("Width", 50.0))
        val out = ComponentRenderer.resolveOutOfFlowPercentSizes(
            props, com.styleconverter.runtime.core.variables.ContainingBlock(null, 200f))
        assertTrue("no rewrite ⇒ the SAME list instance", props === out)
    }

    @Test
    fun `px and unrelated properties pass through as the same instance`() {
        // px-specified overflow is exactly what the unbounded measure was
        // built for — it must NOT be touched; identity also covers the
        // whole static corpus (remember{} keys stay stable).
        val props = listOf(
            IRProperty("Width", Json.parseToJsonElement("{\"type\":\"length\",\"px\":69.0}")),
            position("ABSOLUTE").first()
        )
        val out = ComponentRenderer.resolveOutOfFlowPercentSizes(
            props, com.styleconverter.runtime.core.variables.ContainingBlock(320f, 200f))
        assertTrue(props === out)
    }

    // ── 3. wave 52 (lane L7, T1): the §9.2 overlay owns the static position ──

    // The verbatim `properties` array of the abspos child in
    // tools/titan/runs/wave51-fix/sections/css-grid/per-test-ir/
    // wpt__css-grid__abspos__grid-abspos-staticpos-align-self-center.json.
    private val alignSelfCenterChild: List<IRProperty> = Json.parseToJsonElement(
        """[{"type":"Position","data":"ABSOLUTE"},""" +
            """{"type":"Width","data":{"type":"length","px":50}},""" +
            """{"type":"Height","data":{"type":"length","px":50}},""" +
            """{"type":"BackgroundColor","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}},""" +
            """{"type":"AlignSelf","data":"CENTER"}]"""
    ).jsonArray.map { IRProperty(it.jsonObject["type"]!!.jsonPrimitive.content, it.jsonObject["data"]!!) }

    @Test
    fun `K1a the owned grid child is not mounted through the zero-flow anchor`() {
        // Today's truth (RC1): unpositioned grid parent, no inset → the
        // zero-flow mount fires and reports 0×0 to the overlay Box.
        assertTrue(CanvasRootHoist.rendersInFlowAsStaticPosition(alignSelfCenterChild, false, false, false))
        // With the overlay as owner the mount stands down (css-grid-1 §9.2).
        assertFalse(
            CanvasRootHoist.rendersInFlowAsStaticPosition(
                alignSelfCenterChild, false, false, false, staticPositionOwned = true,
            )
        )
    }

    @Test
    fun `K1b the pure twin - a 0x0 placeable lands factor x content, the real box factor x free`() {
        // The +25/+50 px Android defect, as the shared math sees it: content
        // 100, child 50. OLD (0×0 report): center 50, end 100 — NEW: 25, 50.
        val center = AbsposStaticAlignment.Spec(AbsposStaticAlignment.Base.CENTER, safe = false)
        val end = AbsposStaticAlignment.Spec(AbsposStaticAlignment.Base.END, safe = false)
        assertEquals(50.0, AbsposStaticAlignment.crossOffset(0.0, 100.0, center), 1e-9)
        assertEquals(100.0, AbsposStaticAlignment.crossOffset(0.0, 100.0, end), 1e-9)
        assertEquals(25.0, AbsposStaticAlignment.crossOffset(50.0, 100.0, center), 1e-9)
        assertEquals(50.0, AbsposStaticAlignment.crossOffset(50.0, 100.0, end), 1e-9)
        // Twin of iOS AbsposGridStaticPositionTests' plain-variant table:
        // ref green y 42..91 = 16 + 1 border + 25 (center), 67..116 (+50, end).
    }

    @Test
    fun `K1c ownership also releases the clip-veto clause, and nothing else`() {
        // Wave-49 A4 clause: an INSET abspos under a clip-path ancestor
        // would have hoisted, so RC1 claims it — unless the parent owns it.
        val insetChild = alignSelfCenterChild + IRProperty("Top", Json.parseToJsonElement("{\"px\":10}"))
        assertTrue(CanvasRootHoist.rendersInFlowAsStaticPosition(insetChild, false, false, true))
        assertFalse(CanvasRootHoist.rendersInFlowAsStaticPosition(insetChild, false, false, true, staticPositionOwned = true))
        // The default (no owner) keeps the wave-18 table: a positioned
        // ancestor still vetoes, a static box is still not RC1.
        assertFalse(CanvasRootHoist.rendersInFlowAsStaticPosition(alignSelfCenterChild, true))
        assertFalse(CanvasRootHoist.rendersInFlowAsStaticPosition(position("STATIC"), false))
    }
}

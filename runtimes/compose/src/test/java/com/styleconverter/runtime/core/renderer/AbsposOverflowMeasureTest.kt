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

import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
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
}

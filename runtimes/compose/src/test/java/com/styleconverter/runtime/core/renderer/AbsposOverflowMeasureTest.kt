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
}

package com.styleconverter.runtime.core.renderer

// Wave 33 (lane C) — JVM pins for the pure half of the Appendix-E paint
// order applied to a `z-index: auto` positioned child of a
// `position: relative` container (ComponentRenderer.absposPaintOrder →
// autoZForPositionedChild). The Modifier.zIndex application itself needs a
// device; the decision it hangs off does not.
//
// Measured defect (see autoZForPositionedChild's kdoc): the positioned-
// container branch emits every child into ONE Compose `Box` in DOCUMENT
// order, and a Box draws later children on top — so css-tables/
// absolute-tables-007's in-flow red block painted over the abspos green
// table that precedes it in the source. CSS 2.1 Appendix E paints in-flow
// blocks at step 4 and `z-index: auto` positioned descendants at step 8.
//
// Wave 35 (lane B1) — the WPT-capture gate is GONE. Appendix E has no
// capture mode, and iOS never gated it (its positioned-children `.overlay`
// is unconditional), so Android was the one platform whose SDUI consumers
// got a different paint order from the same IR than the capture harness
// did. The gate's stated cost — "block-flow.json's committed baseline
// moves" — was re-measured and does not exist: _diag35/laneB1/
// scan-appendixE.mjs enumerates all 87 fixture components carrying the
// observable shape and every one reports `baseline=none`, block-flow's
// B_RelativeAnchor included. The last test below pins the new,
// mode-free contract in place of the old "outside capture nothing moves".

import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AbsposPaintOrderTest {

    @Test fun `an auto-z abspos child is lifted above the in-flow siblings`() {
        // Appendix E step 8 over step 4. Strictly > 0 (the implicit z of
        // every in-flow sibling and of the negative-z backdrop layer).
        val z = ComponentRenderer.autoZForPositionedChild(
            listOf(prop("Position", "\"ABSOLUTE\"")))
        assertEquals(0.5f, z!!, 0f)
    }

    @Test fun `the lift is strictly between the in-flow zero and a declared positive z`() {
        // The value must beat step 4 (0) and lose to step 9 (any declared
        // positive integer PositionApplier puts on the child's own chain).
        val z = ComponentRenderer.autoZForPositionedChild(
            listOf(prop("Position", "\"ABSOLUTE\"")))!!
        assert(z > 0f) { "must paint above the in-flow siblings" }
        assert(z < 1f) { "must paint below a declared z-index: 1" }
    }

    @Test fun `a declared z-index is never shadowed`() {
        // PositionApplier already put the author's value on the child's own
        // modifier chain; an OUTER wrapper z would order the box in the
        // container instead of that value.
        for (declared in listOf("-1", "0", "2")) {
            assertNull(
                "z-index: $declared",
                ComponentRenderer.autoZForPositionedChild(
                    listOf(prop("Position", "\"ABSOLUTE\""), prop("ZIndex", declared)),
                )
            )
        }
    }

    @Test fun `the lift is mode-free — the same IR yields the same order everywhere`() {
        // Wave 35 (lane B1): the un-gate's contract. The decision depends on
        // the DECLARATIONS alone, so an SDUI host embedding this runtime and
        // the WPT capture harness paint the identical Appendix-E order from
        // the identical IR — the divergence the wave-33 capture gate created
        // on Android (and that iOS never had) is closed by construction.
        // fixtures/fidelity/trees/block-flow.json's B_RelativeAnchor is the
        // shape: an abspos red 60x30 at (20,10) declared BEFORE an in-flow
        // orange 100x30, which web and iOS both paint red-over-orange.
        val blockFlowShape = listOf(prop("Position", "\"ABSOLUTE\""))
        assertEquals(0.5f, ComponentRenderer.autoZForPositionedChild(blockFlowShape)!!, 0f)
    }

    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))
}

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
            listOf(prop("Position", "\"ABSOLUTE\"")), wptCaptureMode = true)
        assertEquals(0.5f, z!!, 0f)
    }

    @Test fun `the lift is strictly between the in-flow zero and a declared positive z`() {
        // The value must beat step 4 (0) and lose to step 9 (any declared
        // positive integer PositionApplier puts on the child's own chain).
        val z = ComponentRenderer.autoZForPositionedChild(
            listOf(prop("Position", "\"ABSOLUTE\"")), wptCaptureMode = true)!!
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
                    wptCaptureMode = true,
                )
            )
        }
    }

    @Test fun `outside WPT capture nothing moves`() {
        // The frozen dark-stage guarantee: fixtures/fidelity/trees/
        // block-flow.json carries the same shape (abspos red 60x30 at
        // (20,10) followed by an in-flow orange 100x30 at (0,0)) and its
        // committed baseline must not move in this wave.
        assertNull(
            ComponentRenderer.autoZForPositionedChild(
                listOf(prop("Position", "\"ABSOLUTE\"")), wptCaptureMode = false)
        )
    }

    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))
}

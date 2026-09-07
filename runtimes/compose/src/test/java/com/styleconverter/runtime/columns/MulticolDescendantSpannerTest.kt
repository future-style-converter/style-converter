package com.styleconverter.runtime.columns

// Plain-JVM JUnit4 — same suite style as MulticolSpannerFlowTest.
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The DS pin table for [MulticolDescendantSpanner] (wave-35 lane B3).
 *
 * Every row is built from the LIVE wave34-final per-test IR
 * (tools/titan/runs/wave34-final/sections/css-multicol/per-test-ir/) so the
 * gate is pinned against the shapes that actually reach the device, not
 * against invented ones. DS1/DS2 are the two halves of the WPT toggle pair
 * ancestor-toggle-spanner-001 / -002 — the same DOM with and without a
 * transform on the wrapper — which is exactly the css-multicol-1 §6.1
 * discriminator this gate implements.
 *
 * This file has NO iOS twin on purpose: the rewrite compensates for
 * Compose's constraint coercion (`Modifier.height(0.dp)` clamps its whole
 * subtree, SwiftUI's `frame()` does not), and the shared geometry twin
 * [MulticolSpannerFlow] is untouched by it.
 */
class MulticolDescendantSpannerTest {

    /** `{"type":"length","px":n}` — the live Width/Height wire shape. */
    private fun lengthPx(px: Int) = buildJsonObject {
        put("type", "length"); put("px", px)
    }

    /** `{"srgb":{...}}` — the live colour wire shape. */
    private fun srgb(r: Double, g: Double, b: Double, a: Double) = buildJsonObject {
        put("srgb", buildJsonObject { put("r", r); put("g", g); put("b", b); put("a", a) })
    }

    /** The green `column-span:all` box of ancestor-toggle-spanner-001. */
    private fun spanner(heightPx: Int = 100) = IRComponent(
        id = "spanner", name = "spanner",
        properties = listOf(
            IRProperty("ColumnSpan", JsonPrimitive("ALL")),
            IRProperty("Height", lengthPx(heightPx)),
            IRProperty("BackgroundColor", srgb(0.0, 0.5019607843137255, 0.0, 1.0)),
        ),
    )

    /**
     * The `#ancestor` wrapper of ancestor-toggle-spanner-001, verbatim from
     * the live IR: relative + z-index, the browser's POST-hoist used size
     * (42×0), transparent background, zero borders, overflow visible.
     */
    private fun wrapper(extra: List<IRProperty> = emptyList(), child: IRComponent = spanner()) =
        IRComponent(
            id = "ancestor", name = "ancestor",
            properties = listOf(
                IRProperty("Position", JsonPrimitive("RELATIVE")),
                IRProperty("Width", lengthPx(42)),
                IRProperty("Height", lengthPx(0)),
                IRProperty("BorderTopWidth", buildJsonObject { put("px", 0) }),
                IRProperty("BackgroundColor", srgb(0.0, 0.0, 0.0, 0.0)),
                IRProperty("Display", JsonPrimitive("BLOCK")),
                IRProperty("OverflowX", JsonPrimitive("VISIBLE")),
                IRProperty("OverflowY", JsonPrimitive("VISIBLE")),
            ) + extra,
            children = listOf(child),
        )

    /** A `columns: 2` container holding one child. */
    private fun container(child: IRComponent) = IRComponent(
        id = "multicol", name = "multicol",
        properties = listOf(IRProperty("ColumnCount", JsonPrimitive(2))),
        children = listOf(child),
    )

    /**
     * DS1 — ancestor-toggle-spanner-001 (transform REMOVED by the test's
     * script): the wrapper hosts a valid spanner candidate, so it promotes.
     * The promoted wrapper claims `column-span: all` and sheds Width/Height
     * — without the shed, Compose's 0-height envelope collapses the 100px
     * green spanner and the capture is pure red.
     */
    @Test
    fun `DS1 ancestor-toggle-spanner-001 wrapper promotes and sheds its sizing`() {
        val w = wrapper()
        assertTrue(MulticolDescendantSpanner.promotes(w))
        val out = MulticolDescendantSpanner.resolve(container(w))
        val promoted = out.children!![0]
        // Claims the span (the descendant's own wire envelope, reused).
        assertEquals("ALL", promoted.properties.last { it.type == "ColumnSpan" }.data.toString().trim('"'))
        // Sizing shed — nothing left to clamp the spanner's own 100×100.
        assertFalse(promoted.properties.any { it.type == "Width" || it.type == "Height" })
        // Everything else survives byte-for-byte, so the wrapper keeps its
        // place in the positioned-ancestor chain CanvasRootHoist walks.
        assertEquals("RELATIVE", promoted.properties.first { it.type == "Position" }.data.toString().trim('"'))
        assertSame(w.children, promoted.children)
        // And the shared classifier now sees a SPANNER at this level.
        assertEquals(
            listOf(MulticolSpannerFlow.Role.SPANNER),
            MulticolSpannerFlow.rolesFor(out.children),
        )
    }

    /**
     * DS2 — ancestor-toggle-spanner-002 (transform ADDED): the wrapper is an
     * independent formatting-context root, so the descendant is NOT a spanner
     * candidate (§6.2) and the container keeps the frozen column render. Two
     * independent clauses block it here (the transform AND the real 200px
     * height); both are asserted so a future relaxation of one is caught.
     */
    @Test
    fun `DS2 ancestor-toggle-spanner-002 transform ancestor blocks promotion`() {
        val transformed = wrapper(
            extra = listOf(IRProperty("Transform", JsonPrimitive("matrix(1,0,0,1,0,0)"))),
        )
        assertFalse(MulticolDescendantSpanner.promotes(transformed))
        // The height clause alone also blocks a real (non-collapsed) wrapper.
        val tall = IRComponent(
            id = "ancestor", name = "ancestor",
            properties = listOf(
                IRProperty("Position", JsonPrimitive("RELATIVE")),
                IRProperty("Height", lengthPx(200)),
            ),
            children = listOf(spanner(200)),
        )
        assertFalse(MulticolDescendantSpanner.promotes(tall))
    }

    /**
     * DS3 — abspos-containing-block-outside-spanner's wrapper: a bare
     * `position: relative` box with no declarations at all around a
     * `column-span:all` child. It promotes (nothing to shed), which is what
     * makes the spanner span all three columns instead of one.
     */
    @Test
    fun `DS3 bare relative wrapper around a spanner promotes`() {
        val bare = IRComponent(
            id = "rel", name = "rel",
            properties = listOf(IRProperty("Position", JsonPrimitive("RELATIVE"))),
            children = listOf(
                IRComponent(
                    id = "s", name = "s",
                    properties = listOf(
                        IRProperty("ColumnSpan", JsonPrimitive("ALL")),
                        IRProperty("Height", lengthPx(50)),
                    ),
                ),
            ),
        )
        assertTrue(MulticolDescendantSpanner.promotes(bare))
    }

    /**
     * DS5 — the end-to-end geometry for ancestor-toggle-spanner-001, as far
     * as pure math can carry it: after the rewrite the shared classifier
     * sees one SPANNER, and the §6.2 plan puts it at x=0 (full container
     * width, by [MulticolSpannerFlowMeasure]'s convention) and y=0 with the
     * container's auto block-size equal to the spanner's own 100px. That is
     * the green square covering the red box the ref shows; the Compose
     * measure pass beyond this point needs a device.
     */
    @Test
    fun `DS5 promoted wrapper plans as a full-width spanner at the origin`() {
        val out = MulticolDescendantSpanner.resolve(container(wrapper()))
        val roles = MulticolSpannerFlow.rolesFor(out.children)
        assertEquals(listOf(MulticolSpannerFlow.Role.SPANNER), roles)
        // The spanner measures 100 tall (its own declared height, no longer
        // clamped by the wrapper's shed 0px).
        val children = listOf(MulticolSpannerFlow.Child(100, MulticolSpannerFlow.Role.SPANNER))
        assertTrue(MulticolSpannerFlow.engages(children, 2))
        val plan = MulticolSpannerFlow.plan(children, 2)
        assertEquals(
            listOf(MulticolSpannerFlow.Slot(MulticolSpannerFlow.Role.SPANNER, 0, 0)),
            plan.slots,
        )
        assertEquals(100, plan.containerBlockSizePx)
    }

    /**
     * DS4 — the identity guarantees. Anything that paints, clips, floats,
     * hoists out of flow, carries dynamic buckets, is already a spanner, or
     * mixes a spanner with other column content keeps the frozen render, and
     * a container with no qualifying child returns the SAME instance.
     */
    @Test
    fun `DS4 non-qualifying wrappers keep the frozen render`() {
        // Painted background — promotion would re-measure it at container
        // width, visibly widening the paint.
        assertFalse(MulticolDescendantSpanner.promotes(
            wrapper(extra = listOf(IRProperty("BackgroundColor", srgb(1.0, 0.0, 0.0, 1.0))))))
        // A real border likewise.
        assertFalse(MulticolDescendantSpanner.promotes(
            wrapper(extra = listOf(IRProperty("BorderLeftWidth", buildJsonObject { put("px", 2) })))))
        // A clipping wrapper would cut the spanner it should release.
        assertFalse(MulticolDescendantSpanner.promotes(
            wrapper(extra = listOf(IRProperty("OverflowY", JsonPrimitive("CLIP"))))))
        // Out of flow / floated wrappers take no part in the column flow.
        assertFalse(MulticolDescendantSpanner.promotes(
            wrapper(extra = listOf(IRProperty("Position", JsonPrimitive("ABSOLUTE"))))))
        assertFalse(MulticolDescendantSpanner.promotes(
            wrapper(extra = listOf(IRProperty("Float", JsonPrimitive("LEFT"))))))
        // A nested multicol owns its own spanners.
        assertFalse(MulticolDescendantSpanner.promotes(
            wrapper(extra = listOf(IRProperty("ColumnCount", JsonPrimitive(2))))))
        // Text is ink the wrapper owns.
        assertFalse(MulticolDescendantSpanner.promotes(
            wrapper().copy(_text = "hi")))
        // Already a spanner — the wave-21 classifier already handles it.
        assertFalse(MulticolDescendantSpanner.promotes(
            wrapper(extra = listOf(IRProperty("ColumnSpan", JsonPrimitive("ALL"))))))
        // Spanner + sibling column content needs real §6.3 fragmentation.
        assertFalse(MulticolDescendantSpanner.promotes(
            wrapper().copy(children = listOf(
                spanner(),
                IRComponent(id = "sib", name = "sib",
                    properties = listOf(IRProperty("Height", lengthPx(20)))),
            ))))
        // No qualifying child anywhere → the SAME container instance out.
        val plain = container(IRComponent(id = "p", name = "p"))
        assertSame(plain, MulticolDescendantSpanner.resolve(plain))
        // Childless container → identity too (no scan to do).
        val leaf = IRComponent(id = "leaf", name = "leaf")
        assertSame(leaf, MulticolDescendantSpanner.resolve(leaf))
    }
}

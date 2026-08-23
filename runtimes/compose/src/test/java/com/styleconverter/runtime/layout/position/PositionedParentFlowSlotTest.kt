package com.styleconverter.runtime.layout.position

// Wave 46 (lane Y6) — JVM pins for the positioned-parent static-position
// flow slot (PositionedParentFlowSlot). Everything pinned here is pure over
// the IR: the applies() truth table, the slot-relative end-edge rule, the
// placement arithmetic the mount rides, and a replay of the corpus witness
// (css-color/composited-filters-under-opacity.html) proving this slot is the
// ONE owner of the zero footprint for that shape — neither the canvas hoist
// nor the RC1 static-position branch claims it (no drop, no double mount).
// No Robolectric in this suite, so the measure lambda itself is covered by
// CanvasRootHoistTest's anchorPlacePx pins + the end-edge pin below.

import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionedParentFlowSlotTest {

    // ── Wire helpers (same shapes as CanvasRootHoistTest) ──────────────────

    // Parse one JSON literal into the IRProperty data slot.
    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))

    // A position keyword + optional physical insets as the reader emits them.
    private fun positioned(
        keyword: String,
        left: Double? = null,
        top: Double? = null,
        right: Double? = null,
        bottom: Double? = null,
    ) = buildList {
        add(prop("Position", "\"$keyword\""))
        left?.let { add(prop("Left", """{"px":$it}""")) }
        top?.let { add(prop("Top", """{"px":$it}""")) }
        right?.let { add(prop("Right", """{"px":$it}""")) }
        bottom?.let { add(prop("Bottom", """{"px":$it}""")) }
    }

    // ── 1. applies(): the truth table ──────────────────────────────────────

    @Test
    fun `an absolute child of an absolute, fixed or sticky parent takes the slot`() {
        // CSS 2.1 §10.1: any non-static parent is the containing block; the
        // three keywords below are exactly the ones the block Column loop
        // renders (relative / transform-CB parents take the Box branch).
        val child = positioned("ABSOLUTE", left = 50.0)
        assertTrue(PositionedParentFlowSlot.applies(child, positioned("ABSOLUTE")))
        assertTrue(PositionedParentFlowSlot.applies(child, positioned("FIXED")))
        assertTrue(PositionedParentFlowSlot.applies(child, positioned("STICKY")))
    }

    @Test
    fun `a relative or static parent never takes the slot`() {
        // Relative: the positioned-container Box branch already mounts the
        // child at the containing block's origin (RenderAbsoluteChild).
        // Static: the child's containing block is an ANCESTOR, not this
        // parent — out of this slot's scope (documented in the file header).
        val child = positioned("ABSOLUTE", left = 50.0)
        assertFalse(PositionedParentFlowSlot.applies(child, positioned("RELATIVE")))
        assertFalse(PositionedParentFlowSlot.applies(child, positioned("STATIC")))
        assertFalse(PositionedParentFlowSlot.applies(child, emptyList()))
    }

    @Test
    fun `only out-of-flow children qualify`() {
        // css-position-3 §2.1: absolute and fixed leave the flow; relative,
        // sticky and static stay in it and keep their Column footprint.
        val parent = positioned("ABSOLUTE")
        assertTrue(PositionedParentFlowSlot.applies(positioned("ABSOLUTE"), parent))
        assertTrue(PositionedParentFlowSlot.applies(positioned("FIXED", top = 10.0), parent))
        assertFalse(PositionedParentFlowSlot.applies(positioned("RELATIVE", left = 5.0), parent))
        assertFalse(PositionedParentFlowSlot.applies(positioned("STICKY"), parent))
        assertFalse(PositionedParentFlowSlot.applies(positioned("STATIC"), parent))
        assertFalse(PositionedParentFlowSlot.applies(emptyList(), parent))
    }

    // ── 2. The slot-relative end edge (EndInsetAnchor A4/A5, one rule) ─────

    @Test
    fun `an end-anchored axis anchors at the slot's bounded extent, else keeps the start anchor`() {
        // A5: `right`-only axis + bounded Column width ⇒ the containing
        // block's content width is the end edge.
        assertEquals(358f, PositionedParentFlowSlot.slotEndEdgePx(anchorsFromEnd = true, boundedMaxPx = 358))
        // A4: a genuinely UNBOUNDED axis ⇒ null ⇒ start anchor — the child's
        // own −bottom offset stands, exactly as before wave 46. (This is the
        // out-of-flow-parent / intrinsic-pass case, NOT "the block axis": a
        // Column's own maxHeight is finite under any definite-height parent
        // and it hands the remaining space down — see the Y pin below.)
        assertNull(PositionedParentFlowSlot.slotEndEdgePx(anchorsFromEnd = true, boundedMaxPx = null))
        // A2/A3: a start-anchored or inset-free axis never end-anchors,
        // bounded or not.
        assertNull(PositionedParentFlowSlot.slotEndEdgePx(anchorsFromEnd = false, boundedMaxPx = 358))
        assertNull(PositionedParentFlowSlot.slotEndEdgePx(anchorsFromEnd = false, boundedMaxPx = null))
    }

    @Test
    fun `the BLOCK axis end-anchors too when the Column's height is bounded`() {
        // The pre-fix comments on this file, PositionedParentFlowSlot's
        // header and CanvasRootHoist.zeroFlowAnchor all claimed a Column
        // offers its children an UNBOUNDED height, so a `bottom`-only child
        // could only ever take A4. That is false: Compose's Column gives a
        // NON-WEIGHTED child a bounded mainAxisMax (the remaining main-axis
        // space) whenever its own maxHeight is finite — which is every
        // definite-height parent. The Y axis therefore travels the SAME
        // A4/A5 rule the X axis does, and only X was pinned until now.
        //
        // A `bottom: 20px; height: 40px` child of a 150px-tall abspos
        // parent: anchorsFromEndY is the wire predicate the mount reads …
        val bottomOnly = positioned("ABSOLUTE", bottom = 20.0)
        val cfg = PositionedAncestorAnchor.anchorGate(bottomOnly.map { it.type to it.data })
        assertTrue(cfg.anchorsFromEndY)
        // … the slot's bounded maxHeight is the end edge (A5) …
        val endEdge = PositionedParentFlowSlot.slotEndEdgePx(cfg.anchorsFromEndY, boundedMaxPx = 150)
        assertEquals(150f, endEdge)
        // … the box is placed flush with it (150 − 40 = 110) and its own
        // PositionApplier offset (−20) then pulls it to the §3.5.3 used
        // position 90 = 150 − 40 − 20.
        assertEquals(110, CanvasRootHoist.anchorPlacePx(originPx = 0, endEdgePx = endEdge, boxPx = 40))
        assertEquals(-20f, cfg.offsetY.value)
        // A `top`-only (or inset-free) child keeps the slot origin on Y,
        // bounded or not — the static position §10.6.4 prescribes.
        val topOnly = PositionedAncestorAnchor.anchorGate(
            positioned("ABSOLUTE", top = 10.0).map { it.type to it.data })
        assertFalse(topOnly.anchorsFromEndY)
        assertNull(PositionedParentFlowSlot.slotEndEdgePx(topOnly.anchorsFromEndY, boundedMaxPx = 150))
        // And an over-constrained `top: calc(); bottom:` withdraws to A4 on
        // the block axis exactly like the inline one (anchorGate's rule).
        val calcTop = listOf(
            prop("Position", "\"ABSOLUTE\""),
            prop("Top", """{"expr":"calc(10px + 5px)"}"""),
            prop("Bottom", """{"px":20}"""),
        )
        assertFalse(PositionedAncestorAnchor.anchorGate(calcTop.map { it.type to it.data }).anchorsFromEndY)
    }

    @Test
    fun `the end-edge feeds anchorPlacePx like the overlay's canvas edge does`() {
        // A `right: 20px; width: 100px` child of a 358px-wide abspos parent:
        // the slot places the box flush with the end edge (358 − 100 = 258)
        // and the child's own PositionApplier offset (−20) then pulls it to
        // the css-position-3 §3.5.3 used position 238. Same arithmetic the
        // canvas overlay pins at CanvasRootHoistTest.
        val endEdge = PositionedParentFlowSlot.slotEndEdgePx(anchorsFromEnd = true, boundedMaxPx = 358)
        assertEquals(258, CanvasRootHoist.anchorPlacePx(originPx = 0, endEdgePx = endEdge, boxPx = 100))
        // Start-anchored: the slot origin (0) — the static position.
        val none = PositionedParentFlowSlot.slotEndEdgePx(anchorsFromEnd = false, boundedMaxPx = 358)
        assertEquals(0, CanvasRootHoist.anchorPlacePx(originPx = 0, endEdgePx = none, boxPx = 100))
    }

    // ── 3. The mount is a real layout node, gated by the raw-wire anchorGate ─

    @Test
    fun `mount installs one layout element and reads the end anchor through anchorGate`() {
        // The mount must not be the identity Modifier (the caller passes
        // Modifier itself for the non-applying case).
        val mount = PositionedParentFlowSlot.mount(positioned("ABSOLUTE", left = 50.0))
        val elements = mount.foldIn(0) { n, _ -> n + 1 }
        assertEquals(1, elements)
        // anchorGate withdraws the end anchor when the START inset of the
        // same axis is declared but unreadable from the raw wire (a calc()
        // the live chain resolves to px) — the Box branch's rule, reused
        // verbatim so the two mounts can never disagree.
        val overConstrainedCalc = listOf(
            prop("Position", "\"ABSOLUTE\""),
            prop("Left", """{"expr":"calc(10px + 5px)"}"""),
            prop("Right", """{"px":20}"""),
        )
        val gated = PositionedAncestorAnchor.anchorGate(overConstrainedCalc.map { it.type to it.data })
        assertFalse(gated.anchorsFromEndX)
        // A plain `right`-only child keeps its end anchor.
        val rightOnly = positioned("ABSOLUTE", right = 20.0)
        assertTrue(PositionedAncestorAnchor.anchorGate(rightOnly.map { it.type to it.data }).anchorsFromEndX)
    }

    // ── 4. Corpus replay — composited-filters-under-opacity (one owner) ────

    // The per-test IR verbatim (wave45-final css-color): an abspos + opacity
    // parent, two abspos children `left: 0` / `left: 50px`, width 100,
    // height 150, no `top`. The ref paints them overlapping at the parent's
    // top; Android stacked them 150px apart.
    private val parent = listOf(
        prop("Position", "\"ABSOLUTE\""),
        prop("Opacity", """{"alpha":0.5,"original":{"type":"number","value":0.5}}"""),
    )
    private fun contentChild(leftPx: Int) = listOf(
        prop("Position", "\"ABSOLUTE\""),
        prop("Width", """{"type":"length","px":100}"""),
        prop("Height", """{"type":"length","px":150}"""),
        prop("Filter", """[{"fn":"invert","v":100}]"""),
        prop("Left", """{"px":$leftPx}"""),
    )

    @Test
    fun `composited-filters-under-opacity - both children take this slot and nothing else claims them`() {
        for (child in listOf(contentChild(0), contentChild(50))) {
            // The parent is an abspos containing block rendered through the
            // Column loop ⇒ this slot applies.
            assertTrue(PositionedParentFlowSlot.applies(child, parent))
            // The canvas hoist does NOT intercept a child with a positioned
            // ancestor (css-position-3 §3.1 — pin S4), so the child is
            // composed in flow where this slot can mount it …
            assertFalse(CanvasRootHoist.shouldHoistToCanvasRoot(child, hasPositionedAncestor = true))
            // … and the RC1 static-position branch stands down too (it owns
            // only the NO-positioned-ancestor case), so exactly ONE
            // zero-footprint anchor wraps the child — no double mount.
            assertFalse(CanvasRootHoist.rendersInFlowAsStaticPosition(child, hasPositionedAncestor = true))
        }
        // The parent itself is the RC1 case (no positioned ancestor, no
        // inset): in flow at its static position, zero footprint — unchanged
        // by wave 46.
        assertTrue(CanvasRootHoist.rendersInFlowAsStaticPosition(parent, hasPositionedAncestor = false))
        // With both children reporting 0×0, the second child's slot origin
        // is the SAME flow cursor as the first's, and each child's own
        // offset is its `left` — (0, 0) and (50, 0): the ref's overlap.
        val second = PositionExtractor.extractPositionConfig(contentChild(50).map { it.type to it.data })
        assertEquals(50f, second.offsetX.value)
        assertEquals(0f, second.offsetY.value)
        assertEquals(0, CanvasRootHoist.hoistedFlowReportPx())
    }
}

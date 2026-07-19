package com.styleconverter.runtime.layout.grid

// Wave 11 — the css-grid-1 §9/§9.2 abspos partition for grid containers.
//
// Before this fix GridRenderer.RenderGrid had NO out-of-flow filter: an
// absolutely-positioned child was auto-placed as a REAL grid item — it
// occupied cell (1,1), fed track intrinsics, and its size was clamped by
// its track. §9 says an abspos child of a grid container "is not a grid
// item" and "does not affect the sizing of the grid tracks"; §9.2 gives it
// a STATIC POSITION "as if it were the sole grid item in a grid area whose
// edges coincide with the content edges of the grid container".
//
// This suite pins the pure halves:
//   1. partitionOutOfFlow — order-preserving split + the WAVE-8 INDEX
//      LESSON (placements' childIndex must index the PARTITIONED in-flow
//      list, not the original children list);
//   2. absposStaticSpecs — the §9.2 sole-item alignment resolution
//      (self → container items → start, insets standing the axis down);
//   3. staticOverlayAlignment — the Base×Base → Compose Alignment fold.

import androidx.compose.ui.Alignment
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.renderer.ComponentRenderer
import com.styleconverter.runtime.layout.flexbox.AbsposStaticAlignment
import com.styleconverter.runtime.layout.flexbox.AbsposStaticAlignment.Base
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridAbsposPartitionTest {

    // IR helpers — same wire conventions as AbsposOverflowMeasureTest.
    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))

    private fun component(id: String, vararg props: IRProperty) =
        IRComponent(id = id, name = id, properties = props.toList())

    /** `position: absolute` — the keyword wire the parser emits. */
    private fun abspos() = prop("Position", "\"ABSOLUTE\"")

    // ── 1. The partition + index alignment ──────────────────────────────────

    @Test
    fun `partition splits abspos out and preserves relative order`() {
        // Mixed sibling order: in-flow a, abspos x, in-flow b, fixed y.
        val children = listOf(
            component("a"),
            component("x", abspos()),
            component("b"),
            component("y", prop("Position", "\"FIXED\"")),
        )
        val (flow, outOfFlow) = GridRenderer.partitionOutOfFlow(children)
        // §9: absolute AND fixed leave the item list; relative order of each
        // half is preserved (partition is stable).
        assertEquals(listOf("a", "b"), flow.map { it.id })
        assertEquals(listOf("x", "y"), outOfFlow.map { it.id })
    }

    @Test
    fun `relative and sticky children stay grid items`() {
        // css-position-3 §2.1: relative/sticky keep their flow slot — they
        // must keep participating in placement (only abspos/fixed leave).
        val children = listOf(
            component("r", prop("Position", "\"RELATIVE\"")),
            component("s", prop("Position", "\"STICKY\"")),
        )
        val (flow, outOfFlow) = GridRenderer.partitionOutOfFlow(children)
        assertEquals(listOf("r", "s"), flow.map { it.id })
        assertTrue(outOfFlow.isEmpty())
    }

    @Test
    fun `placement childIndex aligns with the partitioned in-flow list`() {
        // THE WAVE-8 LESSON: run the real placement pipeline over the
        // PARTITIONED list and check the render loop's lookup contract —
        // flowChildren[cell.childIndex] must recover the in-flow children in
        // order even though an abspos sibling sat BETWEEN them originally.
        val children = listOf(
            component("a"),
            component("x", abspos()),  // would have been auto-placed at (1,1) pre-fix
            component("b"),
        )
        val (flow, _) = GridRenderer.partitionOutOfFlow(children)
        // Same auto-flow spec shape RenderGrid builds: no explicit claims.
        val specs = flow.map { GridRenderer.GridPlacementSpec() }
        val placements = GridRenderer.placeItems(specs, columnCount = 2)
        // Two in-flow items auto-flow into row 0, columns 0 and 1 — the
        // abspos child no longer consumes cell (0,0) nor shifts indices.
        assertEquals(2, placements.size)
        assertEquals("a", flow[placements[0].childIndex].id)
        assertEquals(0, placements[0].col)
        assertEquals("b", flow[placements[1].childIndex].id)
        assertEquals(1, placements[1].col)
    }

    // ── 2. §9.2 static-position resolution ──────────────────────────────────

    /** Shorthand: resolve both axes with container defaults (no items claims). */
    private fun specs(
        vararg props: IRProperty,
        justifyItems: ComponentRenderer.JustifySelf? = null,
        alignItems: ComponentRenderer.AlignItems = ComponentRenderer.AlignItems.STRETCH,
    ) = GridRenderer.absposStaticSpecs(props.toList(), justifyItems, alignItems)

    @Test
    fun `nothing declared resolves both axes to start`() {
        // §9.2 sole item under `normal` everything: a non-stretchable abspos
        // box aligns start/start (top-left of the content box).
        val (inline, block) = specs(abspos())
        assertEquals(AbsposStaticAlignment.Spec(Base.START, safe = false), inline)
        assertEquals(AbsposStaticAlignment.Spec(Base.START, safe = false), block)
    }

    @Test
    fun `child align-self centers the block axis - typed wire`() {
        // align-self: center on the child (typed keyword channel).
        val (inline, block) = specs(abspos(), prop("AlignSelf", "\"CENTER\""))
        assertEquals(Base.START, inline.base)  // justify axis untouched
        assertEquals(AbsposStaticAlignment.Spec(Base.CENTER, safe = false), block)
    }

    @Test
    fun `safe center rides the Generic wire into the block spec`() {
        // css-align-3 §4.4 overflow keyword — ships via the Generic escape
        // hatch (the wave-10 pinned wire shape) and must keep safe=true so
        // absposOverflowMeasure's overflow fallback still fires.
        val (_, block) = specs(
            abspos(),
            prop("Generic", """{"propertyName":"align-self","rawValue":"safe center","_unmapped":true}""")
        )
        assertEquals(AbsposStaticAlignment.Spec(Base.CENTER, safe = true), block)
    }

    @Test
    fun `child justify-self drives the inline axis`() {
        // justify-self: end → inline END; block stays start.
        val (inline, block) = specs(abspos(), prop("JustifySelf", "\"END\""))
        assertEquals(AbsposStaticAlignment.Spec(Base.END, safe = false), inline)
        assertEquals(Base.START, block.base)
    }

    @Test
    fun `auto self falls back to container items values`() {
        // css-align-3 §6.2/§6.4: *-self:auto resolves to the container's
        // *-items — the sole-item alignment the 4 §9.2 WPT refs exercise.
        val (inline, block) = specs(
            abspos(),
            justifyItems = ComponentRenderer.JustifySelf.CENTER,
            alignItems = ComponentRenderer.AlignItems.CENTER,
        )
        assertEquals(Base.CENTER, inline.base)
        assertEquals(Base.CENTER, block.base)
    }

    @Test
    fun `container stretch makes no positional claim - start default`() {
        // align-items' INITIAL value stretch can't stretch an out-of-flow
        // box (css-flexbox-1 §4.1 precedent) → falls through to start, so a
        // bare grid container never re-centers its abspos children.
        val (_, block) = specs(
            abspos(),
            alignItems = ComponentRenderer.AlignItems.STRETCH,
        )
        assertEquals(Base.START, block.base)
    }

    @Test
    fun `an explicit inset stands the axis down to start`() {
        // css-position-3 §3.5: a non-auto inset REPLACES the static position
        // on its axis — the child's own PositionApplier offset would
        // otherwise double with the overlay alignment. top → block axis
        // stands down even though align-self asks for center; the inline
        // axis (no left/right) still honours justify-self.
        val (inline, block) = specs(
            abspos(),
            prop("Top", """{"px":10.0}"""),
            prop("AlignSelf", "\"CENTER\""),
            prop("JustifySelf", "\"CENTER\""),
        )
        assertEquals(AbsposStaticAlignment.Spec(Base.START, safe = false), block)
        assertEquals(Base.CENTER, inline.base)
    }

    // ── 3. Base×Base → Compose Alignment fold ───────────────────────────────

    @Test
    fun `overlay alignment folds to the canonical Compose alignments`() {
        // BiasAlignment carries the exact -1/0/+1 biases of the named
        // Alignment constants — equality pins the whole 3×3 fold's corners
        // and center.
        assertEquals(Alignment.TopStart,
            GridRenderer.staticOverlayAlignment(Base.START, Base.START))
        assertEquals(Alignment.Center,
            GridRenderer.staticOverlayAlignment(Base.CENTER, Base.CENTER))
        assertEquals(Alignment.BottomEnd,
            GridRenderer.staticOverlayAlignment(Base.END, Base.END))
        // Mixed axes: center inline / start block = TopCenter (the sole-item
        // `justify-items: center` WPT shape).
        assertEquals(Alignment.TopCenter,
            GridRenderer.staticOverlayAlignment(Base.CENTER, Base.START))
    }
}

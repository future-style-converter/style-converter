package com.styleconverter.runtime.layout.position

// Wave 49 (lane A4) — JVM pins for the UNESCAPABLE-CLIP ancestry channel:
// an ancestor's `clip-path` (css-masking-1 §5) takes an out-of-flow
// descendant back out of the canvas-root overlay, because the overlay is a
// SIBLING of that ancestor in the Compose tree and would leave the clip
// behind. Contrast CSS 2.1 §11.1.1, which explicitly LETS a positioned
// descendant escape an `overflow` clip when its containing block is outside
// the clipping box — the escape the hoist reproduces correctly and must keep.
//
// MEASURED on the wave-48 gate, WPT css-masking/clip-path/
// clip-path-blending-offset (an absolutely positioned, mix-blend-mode
// multiply red 100×100 child at left:40/top:50 inside a polygon-clipped
// 100×100 green box):
//   Chromium ref / web / iOS   green 5100 px at [16,16]-[115,115], red NONE
//   Android                    the same green PLUS red 10 000 px at
//                              [56,66]-[155,165]  (android-ref 0.9566 vs 1.0000)
//
// Every payload is copied VERBATIM out of that test's per-test IR
// (tools/titan/runs/wave48-final/sections/css-masking/per-test-ir/
// wpt__css-masking__clip-path__clip-path-blending-offset.json).

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipAncestorHoistVetoTest {

    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))

    /** `#clip-path { width/height:100px; overflow:hidden; background:green;
     *  clip-path: polygon(0 0, 100px 0, 100px 30px, 30px 30px, 30px 100px,
     *  0 100px) }` — component `…blending-offset__0-002`. */
    private fun clippingParentProps(): List<IRProperty> = listOf(
        prop("Width", """{"type":"length","px":100}"""),
        prop("Height", """{"type":"length","px":100}"""),
        prop("OverflowX", "\"HIDDEN\""),
        prop("OverflowY", "\"HIDDEN\""),
        prop(
            "BackgroundColor",
            """{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}""",
        ),
        prop(
            "ClipPath",
            """
            {"type":"polygon","points":[
              {"x":{"px":0},"y":{"px":0}},
              {"x":{"px":100},"y":{"px":0}},
              {"x":{"px":100},"y":{"px":30}},
              {"x":{"px":30},"y":{"px":30}},
              {"x":{"px":30},"y":{"px":100}},
              {"x":{"px":0},"y":{"px":100}}]}
            """.trimIndent(),
        ),
    )

    /** `#blend { width/height:100px; position:absolute; mix-blend-mode:
     *  multiply; left:40px; top:50px; background:red }` — `…__0__0-003`. */
    private fun blendChildProps(): List<IRProperty> = listOf(
        prop("Width", """{"type":"length","px":100}"""),
        prop("Height", """{"type":"length","px":100}"""),
        prop("Position", "\"ABSOLUTE\""),
        prop("MixBlendMode", "\"MULTIPLY\""),
        prop("Left", """{"px":40}"""),
        prop("Top", """{"px":50}"""),
        prop("BackgroundColor", """{"srgb":{"r":1,"g":0,"b":0},"original":"red"}"""),
    )

    private fun comp(
        id: String,
        properties: List<IRProperty> = emptyList(),
        children: List<IRComponent>? = null,
    ) = IRComponent(id = id, name = id, properties = properties, children = children)

    @Test fun `the abspos child hoists without the clip flag and is vetoed with it`() {
        val child = blendChildProps()
        // Wave-17/18 truth table, unchanged: an inset-anchored absolute box
        // with no positioned ancestor is an ICB-anchored hoist candidate.
        assertTrue(CanvasRootHoist.shouldHoistToCanvasRoot(child, hasPositionedAncestor = false))
        // …and a clipping ancestor takes it back out of the overlay.
        assertFalse(
            CanvasRootHoist.shouldHoistToCanvasRoot(
                child,
                hasPositionedAncestor = false,
                hasTransformedAncestor = false,
                hasClippingAncestor = true,
            ),
        )
    }

    @Test fun `a FIXED box is vetoed too - an unescapable clip does not grade by position`() {
        // clip-path-on-fixed-position-scroll's `.fixed` child: css-masking-1
        // §5 has no per-`position` clause, so the veto covers both classes.
        val fixed = listOf(
            prop("Position", "\"FIXED\""),
            prop("Bottom", """{"px":0}"""),
        )
        assertTrue(CanvasRootHoist.shouldHoistToCanvasRoot(fixed, hasPositionedAncestor = false))
        assertFalse(
            CanvasRootHoist.shouldHoistToCanvasRoot(
                fixed,
                hasPositionedAncestor = false,
                hasTransformedAncestor = false,
                hasClippingAncestor = true,
            ),
        )
    }

    @Test fun `the vetoed box renders in its flow slot with a zero flow footprint`() {
        val child = blendChildProps()
        // The wave-18 RC1 clause alone is FALSE for it (it declares insets)…
        assertFalse(CanvasRootHoist.rendersInFlowAsStaticPosition(child, hasPositionedAncestor = false))
        // …so without the wave-49 clause the veto would leave it reserving
        // ordinary flow space. With it, the box takes the RC1 zero-flow mount.
        assertTrue(
            CanvasRootHoist.rendersInFlowAsStaticPosition(
                child,
                hasPositionedAncestor = false,
                hasTransformedAncestor = false,
                hasClippingAncestor = true,
            ),
        )
    }

    @Test fun `a box that was never a hoist candidate keeps its wave-48 mount under a clip`() {
        // The INNER box of the other corpus documents that have an
        // out-of-flow box under a clip-path ancestor — css-masking
        // clip-path-circle-closest-corner / -farthest-corner /
        // ellipse-closest-farthest-corner and filter-effects
        // backdrop-filter-backdrop-root-clip-path-2: that child already HAS a
        // positioned ancestor, so it never hoisted and already renders inside
        // the clip through RenderAbsoluteChild. The veto must not claim it —
        // doing so would swap its mount for the zero-flow anchor.
        //
        // CORRECTED (wave 49 lane F1): lane A4 described those three
        // documents as a whole as "never hoist candidates". Executed over the
        // verbatim per-test IR (ClipHoistCorpusCrashTest), the clip-path
        // CARRIER in each one — `…__2-027` / `…__2-031` / `…__1-066` — is an
        // absolutely positioned root with insets and no positioned ancestor,
        // and it DOES hoist. Only its inner child is the shape pinned here.
        val child = blendChildProps()
        assertFalse(CanvasRootHoist.shouldHoistToCanvasRoot(child, hasPositionedAncestor = true))
        assertFalse(
            CanvasRootHoist.rendersInFlowAsStaticPosition(
                child,
                hasPositionedAncestor = true,
                hasTransformedAncestor = false,
                hasClippingAncestor = true,
            ),
        )
    }

    @Test fun `the pure walk drops the clipped subtree from the overlay list`() {
        val tree = comp(
            "wpt__css-masking__clip-path__clip-path-blending-offset__0-002",
            clippingParentProps(),
            children = listOf(
                comp("clip-path__clip-path-blending-offset__0__0-003", blendChildProps()),
            ),
        )
        // Composition-side interception and the overlay walk must agree, or a
        // box is dropped from flow with no slot to render in.
        assertEquals(emptyList<IRComponent>(), CanvasRootHoist.collectCanvasHoisted(listOf(tree)))
        // …and the host must still ACTIVATE, because the box now needs the
        // zero-flow anchor only a live host installs.
        assertTrue(CanvasRootHoist.hostActivates(listOf(tree)))
        // The interception itself must NOT fire — the box renders in place.
        assertFalse(
            CanvasRootHoist.interceptsInFlow(
                tree.children!![0],
                hostActive = true,
                hasPositionedAncestor = false,
                bypass = null,
                hasTransformedAncestor = false,
                hasClippingAncestor = true,
            ),
        )
    }

    @Test fun `an unclipped parent still hoists its abspos child - the frozen wave-17 path`() {
        // Identity guard for the other 1433 corpus documents: strip the
        // clip-path and the overlay list is exactly what wave 48 produced.
        val tree = comp(
            "unclipped-parent",
            clippingParentProps().filterNot { it.type == "ClipPath" },
            children = listOf(comp("abspos-child", blendChildProps())),
        )
        assertEquals(
            listOf("abspos-child"),
            CanvasRootHoist.collectCanvasHoisted(listOf(tree)).map { it.id },
        )
    }

    @Test fun `the clip flag accumulates down the walk like the other two ancestries`() {
        // A grandchild under a clipped grandparent is vetoed even though its
        // own parent declares nothing (css-masking-1 §5 — a clip-path never
        // un-clips a subtree).
        val tree = comp(
            "clipped-grandparent",
            clippingParentProps(),
            children = listOf(
                comp(
                    "plain-parent",
                    emptyList(),
                    children = listOf(comp("abspos-grandchild", blendChildProps())),
                ),
            ),
        )
        assertEquals(emptyList<IRComponent>(), CanvasRootHoist.collectCanvasHoisted(listOf(tree)))
    }
}

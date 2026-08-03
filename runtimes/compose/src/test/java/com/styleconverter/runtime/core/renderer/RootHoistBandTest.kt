package com.styleconverter.runtime.core.renderer

// Wave 26, lane RES residual 3a — the ROOT-STACK DOUBLE-COUNT pin table
// (B1..B6), Compose half.
//
// ## The defect
// A composed root's outer block spacing had TWO owners: the harness's
// root-stack gap fold (a Spacer between roots) and the root's OWN §8.3.1
// plan band (transparent padding outside its border box, carrying the
// first/last child's margin that escapes through an open parent edge).
// Both are outer spacing in the SAME adjoining-margin region, so they ADDED
// where CSS 2.1 §8.3.1 takes ONE max() over the whole chain.
//
// ## The model pinned here
// `ComponentRenderer.composedRootHoistBand` reports the band; the harness
// folds it into the root's stack contribution (UaBlockMargins.withHoistBand
// — pinned there with the arithmetic worked through) and suppresses the
// renderer's copy through `BlockMarginCollapse.LocalHoistBandSuppressedFor`.
// `root own edge + band` is exactly the root's COLLAPSED-THROUGH edge, so
// the existing n-ary fold resolves the whole chain.
//
// The iOS twin (ComposedRootStackTests / MarginCollapseTests) asserts the
// IDENTICAL numbers under the same B-names.
//
// ## Dark-stage 327 protection
// Every band pin is asserted TWICE — once with uaBlockMargins = true (the
// WPT-capture branch) and once with false, where the UA table is off and
// the band is whatever the DECLARED margins alone produce. The suppression
// channel defaults to null, so no non-composed render can reach the
// suppressed branch at all.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.spacing.BlockMarginCollapse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootHoistBandTest {

    // Wire literal → IRProperty (same convention as BlockCollapsePlanForTest).
    private fun prop(type: String, json: String): IRProperty =
        IRProperty(type, Json.parseToJsonElement(json) as JsonElement)

    // A 20px prose bar with a source tag and no declared margins — the UA
    // table supplies its block edges when uaBlockMargins is on.
    private fun bar(id: String, tag: String) = IRComponent(
        id = id, name = id,
        properties = listOf(prop("Height", """{"type":"length","px":20.0}""")),
        _tag = tag,
    )

    // An UNPADDED block root (explicit padding-0 longhands are the live wire
    // shape) with the given tag and children — both edge gates open, so the
    // children's edge margins hoist.
    private fun root(
        id: String,
        tag: String,
        children: List<IRComponent>,
        padTopPx: Double = 0.0,
    ) = IRComponent(
        id = id, name = id,
        properties = listOf(
            prop("Width", """{"type":"length","px":300.0}"""),
            prop("PaddingTop", """{"px":$padTopPx}"""),
            prop("PaddingRight", """{"px":0.0}"""),
            prop("PaddingBottom", """{"px":0.0}"""),
            prop("PaddingLeft", """{"px":0.0}"""),
        ),
        children = children,
        _tag = tag,
    )

    @Test
    fun b1_blockquoteRootWithProseChildEmitsNoBand() {
        // A `<blockquote>` root (UA 16) whose first/last child is a `<p>`
        // (UA 16): band = max(16, 16) − 16 = 0 on both edges, because the
        // plan already composes against the parent's OWN UA margin. The root
        // stack alone contributes the 16 — this is the case that was already
        // right and must stay right.
        val band = ComponentRenderer.composedRootHoistBand(
            root("r1", "blockquote", listOf(bar("r1__0", "p"))),
            uaBlockMargins = true,
        )
        assertEquals(0f, band.topPx, 0f)
        assertEquals(0f, band.bottomPx, 0f)
    }

    @Test
    fun b2_biggerChildMarginEscapesAsTheExcessOnly() {
        // Same `<blockquote>` root, but an `<h5>` child (UA 27): the band is
        // the EXCESS over the root's own 16, i.e. 11. Root stack 16 + band 11
        // = 27 = max(16, 27) — the collapsed-through edge.
        val band = ComponentRenderer.composedRootHoistBand(
            root("r2", "blockquote", listOf(bar("r2__0", "h5"))),
            uaBlockMargins = true,
        )
        assertEquals(11f, band.topPx, 0f)
        assertEquals(11f, band.bottomPx, 0f)
    }

    @Test
    fun b3_unmarginedRootPassesTheWholeChildEdgeThrough() {
        // A `<div>` root (UA 0) with `<p>` children: the whole 16 escapes,
        // and the root stack contributes 0 — so 0 + 16 = 16, again the
        // collapsed-through edge. This is the shape that makes the
        // double-count visible once a PREVIOUS root has a bigger bottom.
        val band = ComponentRenderer.composedRootHoistBand(
            root("r3", "div", listOf(bar("r3__0", "p"), bar("r3__1", "p"))),
            uaBlockMargins = true,
        )
        assertEquals(16f, band.topPx, 0f)
        assertEquals(16f, band.bottomPx, 0f)
    }

    @Test
    fun b4_darkStageBranchSeesNoUaMarginsAtAll() {
        // uaBlockMargins = false is the property-fixture pipeline: no UA
        // table, no declared margins ⇒ no plan ⇒ no band. The 327 committed
        // baselines ride on this branch.
        val band = ComponentRenderer.composedRootHoistBand(
            root("r4", "blockquote", listOf(bar("r4__0", "h5"))),
            uaBlockMargins = false,
        )
        assertEquals(0f, band.topPx, 0f)
        assertEquals(0f, band.bottomPx, 0f)
    }

    @Test
    fun b5_closedGateKeepsTheChildMarginInsideTheRoot() {
        // A padded root closes both §8.3.1 edge gates (padding separates the
        // margins), so nothing escapes and the band is 0 — the root stack is
        // then the ONLY owner, exactly as before this residual.
        val band = ComponentRenderer.composedRootHoistBand(
            root("r5", "div", listOf(bar("r5__0", "p")), padTopPx = 10.0),
            uaBlockMargins = true,
        )
        // A positive `padding-top` closes the TOP gate (§8.3.1 G1); the
        // bottom gate is still open, so that edge still escapes.
        assertEquals(0f, band.topPx, 0f)
        assertEquals(16f, band.bottomPx, 0f)
    }

    @Test
    fun b6_childlessRootHasNoBand() {
        // The (0,0) contract for "no plan": a leaf root emits nothing, so the
        // harness needs no null handling in the fold.
        val band = ComponentRenderer.composedRootHoistBand(
            bar("r6", "p"), uaBlockMargins = true,
        )
        assertEquals(0f, band.topPx, 0f)
        assertEquals(0f, band.bottomPx, 0f)
    }

    @Test
    fun suppressionChannelMatchesExactlyOneIdAndNeverLeaks() {
        // Null channel (every non-composed path) suppresses nothing.
        assertFalse(BlockMarginCollapse.suppressesHoistBand(null, "r1"))
        // The flagged root matches …
        assertTrue(BlockMarginCollapse.suppressesHoistBand("r1", "r1"))
        // … and nothing else does, including its own descendants: the
        // extractor's ids are hierarchical (`<root>__<i>`), so a child id
        // always EXTENDS its ancestor's and can never equal it.
        assertFalse(BlockMarginCollapse.suppressesHoistBand("r1", "r1__0"))
        assertFalse(BlockMarginCollapse.suppressesHoistBand("r1", "r1__0__2"))
        assertFalse(BlockMarginCollapse.suppressesHoistBand("r1", "r2"))
    }

    // ── B7/B8 — the accessor must report EXACTLY what the renderer paints ──
    // The harness suppresses the renderer's band UNCONDITIONALLY, so an
    // accessor that over-reports adds phantom spacing and one that
    // under-reports deletes real spacing. Both directions were live:

    @Test
    fun b7_nonBlockRootEmitsNoBandBecauseTheRendererPaintsNone() {
        // §8.3.1 collapsing is block-flow only, and the renderer gates its
        // band on `displayConfig.type == DisplayType.BLOCK` — so a flex /
        // grid / inline-block / table / multi-column root paints NOTHING.
        // Reporting the raw plan here made the harness fold 16px above and
        // below such a root that neither the browser nor the pre-wave-26
        // build had. The SwiftUI twin carries these guards inside
        // MarginCollapsePlanner (GATE 1/2), so it always returned 0 —
        // this pins the Compose alignment.
        listOf(
            """{"keyword":"flex"}""",
            """{"keyword":"grid"}""",
            """{"keyword":"inline-block"}""",
            """{"keyword":"table"}""",
        ).forEach { display ->
            val r = IRComponent(
                id = "r7", name = "r7",
                properties = listOf(
                    prop("Display", display),
                    prop("PaddingTop", """{"px":0.0}"""),
                    prop("PaddingBottom", """{"px":0.0}"""),
                ),
                children = listOf(bar("r7__0", "p"), bar("r7__1", "p")),
                _tag = "div",
            )
            val band = ComponentRenderer.composedRootHoistBand(r, uaBlockMargins = true)
            assertEquals("display=$display top", 0f, band.topPx, 0f)
            assertEquals("display=$display bottom", 0f, band.bottomPx, 0f)
        }
        // `column-count` routes the same way (extractDisplayConfig →
        // MULTI_COLUMN), so a css-multicol root is covered too.
        val multicol = IRComponent(
            id = "r7c", name = "r7c",
            properties = listOf(
                prop("ColumnCount", """{"number":2}"""),
                prop("PaddingTop", """{"px":0.0}"""),
                prop("PaddingBottom", """{"px":0.0}"""),
            ),
            children = listOf(bar("r7c__0", "p"), bar("r7c__1", "p")),
            _tag = "div",
        )
        val band = ComponentRenderer.composedRootHoistBand(multicol, uaBlockMargins = true)
        assertEquals(0f, band.topPx, 0f)
        assertEquals(0f, band.bottomPx, 0f)
    }

    @Test
    fun b8_displayContentsFirstChildIsUnboxedBeforePlanning() {
        // RenderComponent resolves `display: contents` BEFORE anything reads
        // the child list, so the renderer plans against the GRANDCHILD.
        // Planning against the raw tree saw the wrapper `<div>` (UA 0) and
        // reported a 0 band while the renderer painted 27 (the `<h5>`'s UA
        // margin) — and the harness's unconditional suppression then deleted
        // those 27px outright.
        val wrapper = IRComponent(
            id = "r8__0", name = "w",
            properties = listOf(prop("Display", """{"keyword":"contents"}""")),
            children = listOf(bar("r8__0__0", "h5")),
            _tag = "div",
        )
        val r = root("r8", "div", listOf(wrapper, bar("r8__1", "p")))
        val band = ComponentRenderer.composedRootHoistBand(r, uaBlockMargins = true)
        assertEquals(27f, band.topPx, 0f)
        assertEquals(16f, band.bottomPx, 0f)
    }
}

package com.styleconverter.runtime.core.renderer

// Pins ComponentRenderer.blockCollapsePlanFor — the IR-level bridge that
// builds the CSS2 §8.3.1 collapse plan for the default block child loop.
// Component/property shapes are VERBATIM live-converter output (`--to ir`
// on fixtures/properties/spacing/margin-trim.json: margins {"px":20.0},
// `padding: 0` as explicit {"px":0.0} per side, MarginTrim as "NONE") —
// the wave-5 lesson says tests pin to live wire, never assumed shapes.
//
// Covered here: the campaign fixture plan (max rule + both hoists), the
// structural silence gates (no children / _text / relative / list parents,
// margin-less corpora), the logged fallback reasons (auto margins,
// out-of-flow and floated children), and the padded-parent gate-off.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRMedia
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.spacing.CollapsedMargin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BlockCollapsePlanForTest {

    // Wire literal → IRProperty, mirroring the flex-sizes pin suite.
    private fun prop(type: String, json: String): IRProperty =
        IRProperty(type, Json.parseToJsonElement(json) as JsonElement)

    // A margin-trim fixture child: 100x40 bar with margin: 20px (expanded
    // by the converter to the four physical longhands).
    private fun bar(name: String, marginPx: Double = 20.0) = IRComponent(
        id = name, name = name,
        properties = listOf(
            prop("Width", """{"type":"length","px":100.0}"""),
            prop("Height", """{"type":"length","px":40.0}"""),
            prop("MarginTop", """{"px":$marginPx}"""),
            prop("MarginRight", """{"px":$marginPx}"""),
            prop("MarginBottom", """{"px":$marginPx}"""),
            prop("MarginLeft", """{"px":$marginPx}"""),
            prop("BackgroundColor", """{"srgb":{"r":0.2,"g":0.6,"b":0.86},"original":"#3498db"}"""),
        )
    )

    // The MarginTrim_None parent, verbatim from the live wire (see file
    // header). `extra` lets each test perturb one aspect.
    private fun parent(
        children: List<IRComponent>?,
        extra: List<IRProperty> = emptyList(),
        text: String? = null,
        tag: String? = null,
    ) = IRComponent(
        id = "margintrim_none-001", name = "MarginTrim_None",
        properties = listOf(
            prop("Width", """{"type":"length","px":300.0}"""),
            prop("MarginTrim", "\"NONE\""),
            prop("PaddingTop", """{"px":0.0}"""),
            prop("PaddingRight", """{"px":0.0}"""),
            prop("PaddingBottom", """{"px":0.0}"""),
            prop("PaddingLeft", """{"px":0.0}"""),
            prop("BackgroundColor", """{"srgb":{"r":0.2,"g":0.2,"b":0.2},"original":"#333333"}"""),
        ) + extra,
        children = children,
        _text = text,
        _tag = tag,
    )

    /** The diagnosed 0.832 divergence, resolved: the fixture parent's plan
     *  hoists 20px on both edges and collapses interior gaps to 20px, so
     *  the parent content box sums to web's 160px (40+60+60) instead of
     *  the legacy 240px (3×80 stacked margin boxes). */
    @Test
    fun `margin-trim fixture builds the full collapse plan`() {
        val result = ComponentRenderer.blockCollapsePlanFor(
            parent(children = listOf(bar("a-002"), bar("b-003"), bar("c-004")))
        )
        // In-scope container — no fallback to report.
        assertNull(result.fallbackReason)
        val plan = result.plan
        assertNotNull(plan)
        // Both edge margins escape through the padding-0/border-0 parent.
        assertEquals(20f, plan!!.hoistTopPx, 0.001f)
        assertEquals(20f, plan.hoistBottomPx, 0.001f)
        // First child flush, interior gaps carry max(20,20)=20 exactly once.
        assertEquals(
            listOf(CollapsedMargin(0f, 0f), CollapsedMargin(20f, 0f), CollapsedMargin(20f, 0f)),
            plan.perChild
        )
        // Geometry: 40px bars → parent content height 40 + 60 + 60 = 160
        // (web's Chromium-probed 300x160 parent box).
        val height = plan.perChild.sumOf { (it.topPx + 40f + it.bottomPx).toDouble() }
        assertEquals(160.0, height, 0.001)
    }

    /** Margin-less children build NO plan — the static corpus renders
     *  byte-identically to the frozen baseline (identity guarantee). */
    @Test
    fun `margin-less children build no plan and log nothing`() {
        val result = ComponentRenderer.blockCollapsePlanFor(
            parent(children = listOf(bar("a", marginPx = 0.0), bar("b", marginPx = 0.0)))
        )
        assertNull(result.plan)
        assertNull(result.fallbackReason)
    }

    /** Structural silence gates: these containers are handled by OTHER
     *  render paths (leaf box / text sibling / overlay / marker rows), so
     *  skipping is correct and reason-less. */
    @Test
    fun `structurally out-of-scope parents are silently skipped`() {
        // No children at all → leaf path.
        val leaf = ComponentRenderer.blockCollapsePlanFor(parent(children = null))
        assertNull(leaf.plan); assertNull(leaf.fallbackReason)
        // Leading _text renders as an extra first sibling the index-aligned
        // plan cannot describe.
        val texty = ComponentRenderer.blockCollapsePlanFor(
            parent(children = listOf(bar("a")), text = "hello")
        )
        assertNull(texty.plan); assertNull(texty.fallbackReason)
        // position:relative parents take RenderContent's overlay-Box branch.
        val relative = ComponentRenderer.blockCollapsePlanFor(
            parent(children = listOf(bar("a")), extra = listOf(prop("Position", "\"RELATIVE\"")))
        )
        assertNull(relative.plan); assertNull(relative.fallbackReason)
        // <ul>/<ol> children route through the list-marker Row path.
        val list = ComponentRenderer.blockCollapsePlanFor(
            parent(children = listOf(bar("a")), tag = "ul")
        )
        assertNull(list.plan); assertNull(list.fallbackReason)
    }

    /** Out-of-scope value flavors fall back WITH a reason (the composable
     *  call site logs it once — no silent fallthroughs). */
    @Test
    fun `unemulated flavors report a fallback reason`() {
        // margin: auto in the block axis → centering path owns it.
        val auto = ComponentRenderer.blockCollapsePlanFor(
            parent(
                children = listOf(
                    bar("a").copy(
                        properties = bar("a").properties.filter { it.type != "MarginTop" } +
                            prop("MarginTop", "\"auto\"")
                    ),
                    bar("b"),
                )
            )
        )
        assertNull(auto.plan)
        assertEquals("auto/negative/relative child margin", auto.fallbackReason)
        // An absolutely positioned child is out of the collapsing flow
        // (§8.3.1 applies to in-flow boxes only).
        val abspos = ComponentRenderer.blockCollapsePlanFor(
            parent(
                children = listOf(
                    bar("a").copy(properties = bar("a").properties + prop("Position", "\"ABSOLUTE\"")),
                    bar("b"),
                )
            )
        )
        assertNull(abspos.plan)
        assertEquals("out-of-flow child", abspos.fallbackReason)
        // A floated child's margins never collapse (§8.3.1).
        val floated = ComponentRenderer.blockCollapsePlanFor(
            parent(
                children = listOf(
                    bar("a").copy(properties = bar("a").properties + prop("Float", "\"LEFT\"")),
                    bar("b"),
                )
            )
        )
        assertNull(floated.plan)
        assertEquals("floated child", floated.fallbackReason)
    }

    /** A block-axis padded parent still collapses SIBLING margins but keeps
     *  the edge margins inside (§8.3.1: padding separates parent/child
     *  margins) — the gate-off case, not a fallback. */
    @Test
    fun `padded parent collapses siblings but does not hoist`() {
        val result = ComponentRenderer.blockCollapsePlanFor(
            parent(
                children = listOf(bar("a"), bar("b")),
                // Perturb: padding-top 8px (padding-bottom stays 0).
                extra = listOf(prop("PaddingTop", """{"px":8.0}""")),
            )
        )
        assertNull(result.fallbackReason)
        val plan = result.plan
        assertNotNull(plan)
        // Top gate closed: first child keeps its full 20px inside; the
        // bottom edge (still zero padding, no border) hoists.
        assertEquals(0f, plan!!.hoistTopPx, 0.001f)
        assertEquals(20f, plan.hoistBottomPx, 0.001f)
        assertEquals(
            listOf(CollapsedMargin(20f, 0f), CollapsedMargin(20f, 0f)),
            plan.perChild
        )
    }

    // ── SHARED S1–S12 PIN TABLE ──────────────────────────────────────────
    // The UNIFIED COLLAPSE GATE CONTRACT's scenario pins, at the IR level.
    // Expected values are IDENTICAL to the iOS lane (MarginCollapseTests.
    // swift) so the two native implementations cannot drift silently. S1–S4
    // (pure fold math) and the gate-only halves of S5–S8 are pinned in
    // BlockMarginCollapseTest; here S5–S8 pin the full-plan integration and
    // S9–S12 pin the container-level bails B1/B3/B5/B9.

    /** S5 parent padding-top 8 → the top gate closes (no top hoist), the
     *  interior collapse and the bottom hoist stay live. */
    @Test
    fun `S5 top padding closes top hoist, interior collapse survives`() {
        val result = ComponentRenderer.blockCollapsePlanFor(
            parent(
                children = listOf(bar("a"), bar("b"), bar("c")),
                extra = listOf(prop("PaddingTop", """{"px":8.0}""")),
            )
        )
        assertNull(result.fallbackReason)
        val plan = result.plan!!
        assertEquals(0f, plan.hoistTopPx, 0.001f)
        assertEquals(20f, plan.hoistBottomPx, 0.001f)
        assertEquals(
            listOf(CollapsedMargin(20f, 0f), CollapsedMargin(20f, 0f), CollapsedMargin(20f, 0f)),
            plan.perChild
        )
    }

    /** S6 parent overflow-x hidden → both gates close (a single non-visible
     *  axis establishes a BFC, G3): NO hoists, interior collapse survives. */
    @Test
    fun `S6 overflow-x hidden closes both hoists, interior collapse survives`() {
        val result = ComponentRenderer.blockCollapsePlanFor(
            parent(
                children = listOf(bar("a"), bar("b"), bar("c")),
                extra = listOf(prop("OverflowX", "\"hidden\"")),
            )
        )
        assertNull(result.fallbackReason)
        val plan = result.plan!!
        assertEquals(0f, plan.hoistTopPx, 0.001f)
        assertEquals(0f, plan.hoistBottomPx, 0.001f)
        // Both edges closed: first keeps its top, last keeps its bottom; the
        // interior gaps still collapse to max(20,20)=20 (not 40).
        assertEquals(
            listOf(CollapsedMargin(20f, 0f), CollapsedMargin(20f, 0f), CollapsedMargin(20f, 20f)),
            plan.perChild
        )
    }

    /** S7 parent position:absolute → both gates close (out-of-flow parent,
     *  G4): NO hoists, interior collapse survives. */
    @Test
    fun `S7 absolute parent closes both hoists, interior collapse survives`() {
        val result = ComponentRenderer.blockCollapsePlanFor(
            parent(
                children = listOf(bar("a"), bar("b"), bar("c")),
                extra = listOf(prop("Position", "\"ABSOLUTE\"")),
            )
        )
        assertNull(result.fallbackReason)
        val plan = result.plan!!
        assertEquals(0f, plan.hoistTopPx, 0.001f)
        assertEquals(0f, plan.hoistBottomPx, 0.001f)
        assertEquals(
            listOf(CollapsedMargin(20f, 0f), CollapsedMargin(20f, 0f), CollapsedMargin(20f, 20f)),
            plan.perChild
        )
    }

    /** S8 parent max-height 500px → the bottom gate stays OPEN (max-height
     *  does not pin the used height, G5): both edges hoist as usual. */
    @Test
    fun `S8 max-height leaves both hoists open`() {
        val result = ComponentRenderer.blockCollapsePlanFor(
            parent(
                children = listOf(bar("a"), bar("b"), bar("c")),
                extra = listOf(prop("MaxHeight", """{"type":"length","px":500.0}""")),
            )
        )
        assertNull(result.fallbackReason)
        val plan = result.plan!!
        assertEquals(20f, plan.hoistTopPx, 0.001f)
        assertEquals(20f, plan.hoistBottomPx, 0.001f)
        assertEquals(
            listOf(CollapsedMargin(0f, 0f), CollapsedMargin(20f, 0f), CollapsedMargin(20f, 0f)),
            plan.perChild
        )
    }

    /** S9 any child display:none → NO plan (bail B1): a hidden child
     *  generates no box, so its margins must not fold into visible siblings. */
    @Test
    fun `S9 display none child bails with no plan`() {
        val hidden = bar("a").copy(
            properties = bar("a").properties + prop("Display", "\"none\"")
        )
        val result = ComponentRenderer.blockCollapsePlanFor(
            parent(children = listOf(hidden, bar("b")))
        )
        assertNull(result.plan)
        assertEquals("display:none child", result.fallbackReason)
    }

    /** S10 any child float:left → NO plan (bail B3): §8.3.1 says floating
     *  boxes' margins never collapse. */
    @Test
    fun `S10 floated child bails with no plan`() {
        val floated = bar("a").copy(
            properties = bar("a").properties + prop("Float", "\"LEFT\"")
        )
        val result = ComponentRenderer.blockCollapsePlanFor(
            parent(children = listOf(floated, bar("b")))
        )
        assertNull(result.plan)
        assertEquals("floated child", result.fallbackReason)
    }

    /** S11 a child with a MEDIA-bucket margin → NO plan (bail B5): the
     *  bucket could re-declare the very margin the static plan overrides. */
    @Test
    fun `S11 media-bucket child margin bails with no plan`() {
        val busy = bar("a").copy(
            media = listOf(
                IRMedia(
                    query = "(min-width: 768px)",
                    properties = listOf(prop("MarginTop", """{"px":40.0}"""))
                )
            )
        )
        val result = ComponentRenderer.blockCollapsePlanFor(
            parent(children = listOf(busy, bar("b")))
        )
        assertNull(result.plan)
        assertEquals("bucket-declared child margin", result.fallbackReason)
    }

    /** S12 a middle sibling with no _text, no children, no explicit height
     *  and BOTH vertical margins (20,20) declared → NO plan (bail B9): its
     *  own margins self-collapse into an n-ary max the pairwise fold cannot
     *  reproduce. */
    @Test
    fun `S12 self-collapsing middle child bails with no plan`() {
        // A zero-extent block with both vertical margins declared (no Height,
        // no _text, no children) — the §8.3.1 self-collapsing candidate.
        val hollow = IRComponent(
            id = "hollow-003", name = "hollow",
            properties = listOf(
                prop("MarginTop", """{"px":20.0}"""),
                prop("MarginBottom", """{"px":20.0}"""),
            )
        )
        val result = ComponentRenderer.blockCollapsePlanFor(
            parent(children = listOf(bar("a"), hollow, bar("c")))
        )
        assertNull(result.plan)
        assertEquals("self-collapsing child", result.fallbackReason)
    }
}

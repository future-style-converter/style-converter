package com.styleconverter.runtime.layout.position

// Wave 17 — JVM pins for the out-of-flow contract (css-position-3 §2.1/
// §3.1/§3.2), the S-table from the confirmed wave-17 diagnosis. The hoist
// decision, the descendant walk, the interception truth table and the
// zero-flow-size report are all pure over the IR, so the whole contract is
// pinnable without Robolectric (the suite's standing constraint — same
// style as AbsposOverflowMeasureTest / FragmentGeometryTest wiring pins).
//
// Canvas geometry the S-table assumes: 390x600 composed WPT canvas, 16px
// pad — the overlay anchors at the UNPADDED origin (0,0), so a hoisted
// box's painted canvas position is exactly its PositionConfig inset offset.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasRootHoistTest {

    // ── Wire helpers (shapes copied from PositionLayoutExtractorTest so
    //    parser drift fails in one obvious place first) ─────────────────────

    // Parse one JSON literal into the IRProperty data slot.
    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))

    // A position keyword + optional physical px insets, as the reader emits
    // them ({"type":"length","px":N} — the frozen typed-length wire).
    private fun positioned(keyword: String, left: Double? = null, top: Double? = null) =
        buildList {
            add(prop("Position", "\"$keyword\""))
            left?.let { add(prop("Left", """{"type":"length","px":$it}""")) }
            top?.let { add(prop("Top", """{"type":"length","px":$it}""")) }
        }

    // Minimal component factory — ids double as assertion labels.
    private fun comp(
        id: String,
        properties: List<IRProperty> = emptyList(),
        children: List<IRComponent>? = null,
    ) = IRComponent(id = id, name = id, properties = properties, children = children)

    // The overlay contributes (0,0) — a hoisted box paints at its inset
    // offset from the UNPADDED canvas origin. Deriving the anchor through
    // the SAME extractor the live chain uses keeps this pin honest.
    private fun canvasAnchorPx(properties: List<IRProperty>): Pair<Float, Float> {
        val config = PositionExtractor.extractPositionConfig(properties.map { it.type to it.data })
        return config.offsetX.value to config.offsetY.value
    }

    // ── S1: root-level fixed Left:100 Top:0 → canvas (100,0), no flow box ──

    @Test fun `S1 root-level fixed hoists and anchors at canvas (100,0)`() {
        val fixed = comp("s1", positioned("fixed", left = 100.0, top = 0.0))
        // Hoist decision: fixed always leaves flow for the canvas root.
        assertTrue(CanvasRootHoist.shouldHoistToCanvasRoot(fixed.properties, hasPositionedAncestor = false))
        // In-flow interception: composes NOTHING in the parent (S5's mechanism).
        assertTrue(CanvasRootHoist.interceptsInFlow(fixed, hostActive = true, hasPositionedAncestor = false, bypass = null))
        // Painted anchor = unpadded origin + insets = (100, 0).
        assertEquals(100f to 0f, canvasAnchorPx(fixed.properties))
    }

    // ── S2: root-level absolute Left:100 Top:0 → canvas (100,0) ────────────
    // The measured web capture pins the ICB anchor at the UNPADDED canvas
    // edge (absolute ancestor left:100 landed at x=100, not 116): with no
    // positioned ancestor an absolute box hoists exactly like fixed.

    @Test fun `S2 root-level absolute hoists to the same unpadded anchor`() {
        val abs = comp("s2", positioned("absolute", left = 100.0, top = 0.0))
        // No positioned ancestor → containing block is the ICB (the canvas).
        assertTrue(CanvasRootHoist.shouldHoistToCanvasRoot(abs.properties, hasPositionedAncestor = false))
        // Same anchor arithmetic as S1 — F1 and F2 share the unpadded basis.
        assertEquals(100f to 0f, canvasAnchorPx(abs.properties))
    }

    // ── S3: fixed child of an in-flow relative parent at (116,16) ──────────
    // css-position-3 §3.2: fixed ignores positioned ancestors entirely — the
    // child paints at canvas (116,16), NOT parent + offset = (232,32).

    @Test fun `S3 fixed hoists even under a positioned ancestor — anchor is inset, not parent+inset`() {
        val child = comp("s3", positioned("fixed", left = 116.0, top = 16.0))
        // The positioned-ancestor flag must be IRRELEVANT for fixed.
        assertTrue(CanvasRootHoist.shouldHoistToCanvasRoot(child.properties, hasPositionedAncestor = true))
        assertTrue(CanvasRootHoist.shouldHoistToCanvasRoot(child.properties, hasPositionedAncestor = false))
        // Anchor = (116,16) from the canvas origin — the parent's flow slot
        // at (116,16) contributes nothing (the diagnosed bug doubled it).
        assertEquals(116f to 16f, canvasAnchorPx(child.properties))
    }

    // ── S4: absolute under a positioned ancestor stays with wave-8/9 ───────
    // Ancestor at (50,40) with a 5px border: the child's left:10 anchors at
    // the ancestor's padding box → 50 (border-box x) + 5 (border band) + 10
    // (inset) = 65. Structurally that is the existing machinery: the
    // relative parent's overlay Box sits inside its border content inset
    // (StyleApplier.borderContentInset), and the child's own absoluteOffset
    // adds the 10 — so the pin here is that the hoist DOESN'T claim the box
    // (no canvas re-anchor) and the applied inset stays 10.

    @Test fun `S4 absolute with positioned ancestor is NOT hoisted — existing containing-block machinery owns it`() {
        val child = comp("s4", positioned("absolute", left = 10.0))
        // Positioned ancestor present → no canvas hoist (pin: wave-8/9 path).
        assertFalse(CanvasRootHoist.shouldHoistToCanvasRoot(child.properties, hasPositionedAncestor = true))
        // No interception either — the parent's positioned branch renders it.
        assertFalse(CanvasRootHoist.interceptsInFlow(child, hostActive = true, hasPositionedAncestor = true, bypass = null))
        // The inset the overlay machinery applies from the padding box is 10
        // (the 50+5 comes from the ancestor's box geometry, not this config).
        assertEquals(10f to 0f, canvasAnchorPx(child.properties))
    }

    // ── S5: no flow space reserved for an out-of-flow box ──────────────────

    @Test fun `S5 fixed sibling is intercepted while its in-flow sibling is not`() {
        val fixed = comp("s5-fixed", positioned("fixed", left = 10.0, top = 10.0))
        val block = comp("s5-block") // plain static block sibling
        // The fixed box composes nothing in flow…
        assertTrue(CanvasRootHoist.interceptsInFlow(fixed, hostActive = true, hasPositionedAncestor = false, bypass = null))
        // …the sibling renders normally, so it starts where the fixed box
        // would have been (Compose reserves space only for composed nodes).
        assertFalse(CanvasRootHoist.interceptsInFlow(block, hostActive = true, hasPositionedAncestor = false, bypass = null))
    }

    @Test fun `S5 overlay slots report ZERO flow size — hoisted ink never grows the canvas`() {
        // css-position-3 §3: an out-of-flow box never sizes its ancestors;
        // the zero-size anchor report is the wiring that guarantees it.
        assertEquals(0, CanvasRootHoist.hoistedFlowReportPx())
    }

    // ── The composition-side gates: host activation + overlay bypass ───────

    @Test fun `hostless paths never intercept — the frozen-baseline identity pin`() {
        val fixed = comp("baseline-fixed", positioned("fixed", left = 100.0))
        // The dark-stage per-component canvas and the 327-pair baseline run
        // with no Host (LocalActive false) → legacy behavior, byte-identical.
        assertFalse(CanvasRootHoist.interceptsInFlow(fixed, hostActive = false, hasPositionedAncestor = false, bypass = null))
    }

    @Test fun `the overlay's own render bypasses interception by INSTANCE identity`() {
        val fixed = comp("bypass", positioned("fixed", left = 100.0))
        // Same instance → passes through (the overlay slot rendering itself).
        assertFalse(CanvasRootHoist.interceptsInFlow(fixed, hostActive = true, hasPositionedAncestor = false, bypass = fixed))
        // An equal-but-distinct instance is NOT the bypass — reference
        // identity, because an IR tree never aliases a node.
        val twin = comp("bypass", positioned("fixed", left = 100.0))
        assertTrue(CanvasRootHoist.interceptsInFlow(fixed, hostActive = true, hasPositionedAncestor = false, bypass = twin))
    }

    // ── The pure walk: any-depth collection mirroring the interception ─────

    @Test fun `walk collects fixed at depth 2 under static ancestors, in document order`() {
        val grandchild = comp("gc-fixed", positioned("fixed", left = 5.0))
        val rootFixed = comp("root-fixed", positioned("fixed", left = 1.0))
        val tree = listOf(
            rootFixed,
            comp("static-parent", children = listOf(comp("static-mid", children = listOf(grandchild)))),
        )
        // Both collected, document order preserved (tree paint order for
        // equal z — CSS 2.1 Appendix E).
        assertEquals(listOf(rootFixed, grandchild), CanvasRootHoist.collectCanvasHoisted(tree))
    }

    @Test fun `walk splits absolute by positioned ancestry exactly like the interception`() {
        // absolute under a RELATIVE parent → ancestor-owned, NOT collected.
        val underRelative = comp("abs-under-rel", positioned("absolute", left = 10.0))
        val relParent = comp("rel-parent", positioned("relative"), children = listOf(underRelative))
        // absolute under a STATIC parent → ICB-anchored, collected.
        val underStatic = comp("abs-under-static", positioned("absolute", left = 20.0))
        val staticParent = comp("static-parent", children = listOf(underStatic))
        assertEquals(
            listOf(underStatic),
            CanvasRootHoist.collectCanvasHoisted(listOf(relParent, staticParent)),
        )
    }

    @Test fun `walk recurses INTO hoisted subtrees — fixed inside fixed both anchor at the canvas`() {
        // css-position-3 §3.2: a fixed box inside a fixed box still anchors
        // at the viewport; its absolute sibling anchors at the (positioned)
        // fixed parent instead.
        val innerFixed = comp("inner-fixed", positioned("fixed", left = 30.0))
        val innerAbs = comp("inner-abs", positioned("absolute", left = 40.0))
        val outerFixed = comp("outer-fixed", positioned("fixed"), children = listOf(innerFixed, innerAbs))
        assertEquals(
            // Outer first (document order), inner fixed collected through
            // the hoisted subtree, absolute left to its positioned parent.
            listOf(outerFixed, innerFixed),
            CanvasRootHoist.collectCanvasHoisted(listOf(outerFixed)),
        )
    }

    @Test fun `walk returns EMPTY for an out-of-flow-free document — the host identity fast path`() {
        val doc = listOf(
            comp("a", positioned("relative", left = 3.0)),
            comp("b", children = listOf(comp("c", positioned("sticky")))),
        )
        // relative/sticky/static stay in flow (css-position-3 §2.1) — the
        // Host renders such documents with no wrapper Box and no locals,
        // byte-identical to the pre-wave-17 composed render.
        assertTrue(CanvasRootHoist.collectCanvasHoisted(doc).isEmpty())
    }

    // ── The ancestry threading both sides share ────────────────────────────

    @Test fun `every non-static position establishes a containing block for descendants`() {
        // CSS 2.1 §10.1: relative/absolute/fixed/sticky all make the box a
        // containing block for absolute descendants; static does not.
        assertTrue(CanvasRootHoist.establishesContainingBlock(positioned("relative")))
        assertTrue(CanvasRootHoist.establishesContainingBlock(positioned("absolute")))
        assertTrue(CanvasRootHoist.establishesContainingBlock(positioned("fixed")))
        assertTrue(CanvasRootHoist.establishesContainingBlock(positioned("sticky")))
        assertFalse(CanvasRootHoist.establishesContainingBlock(positioned("static")))
        assertFalse(CanvasRootHoist.establishesContainingBlock(emptyList()))
    }
}

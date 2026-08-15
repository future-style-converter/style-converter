package com.styleconverter.runtime.layout

// Wave-42 lane W5 — JVM pins for the pure §9.5.2 clearance twins
// (FloatClearance.kt ↔ iOS FloatClearance.swift). Every pinned value is
// mirrored VERBATIM in FloatClearanceTests.swift so a skeptic probe can
// diff the two suites. Geometry comes from the six failing CSS2/
// floats-clear cells of the wave-41 gate (T7 evidence), each expected
// position computed BY HAND from the fixture's CSS in the test comments.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.ir.IRSlot
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatClearanceTest {

    // Bare keyword wire shape ({"type":"Float","data":"LEFT"}).
    private fun kw(type: String, k: String) =
        IRProperty(type, Json.parseToJsonElement("\"$k\""))

    // Margin/padding wire shape ({"px":N}).
    private fun px(type: String, v: Double) =
        IRProperty(type, Json.parseToJsonElement("""{"px":$v}"""))

    // Sized-length wire shape ({"type":"length","px":N} — Width/Height).
    private fun len(type: String, v: Double) =
        IRProperty(type, Json.parseToJsonElement("""{"type":"length","px":$v}"""))

    // Component builder; roots carry no slot (the resolve attach gate).
    private fun comp(
        id: String,
        props: List<IRProperty>,
        children: List<IRComponent>? = null,
        slotParent: String? = null,
        text: String? = null,
    ) = IRComponent(
        id = id, name = id, properties = props, children = children,
        _text = text, slot = slotParent?.let { IRSlot(parent = it) },
    )

    // ── The six wave-41 cells, expected geometry hand-computed ──

    @Test
    fun `adjoining-float-before-clearance - forced clearance pins the box at the float bottom`() {
        // red > [wrapper > [float L 100x50], E(clear:left, mt:400, h:50)].
        // Hypothetical: the 400 collapses through wrapper AND red's top
        // edge, pulling the float down with it (§8.3.1 adjoining) — so
        // clearance is forced NO MATTER how large the margin is, and E's
        // top border edge lands at the float bottom: appliedTop = 50.
        val root = comp("red", listOf(len("Width", 100.0)), listOf(
            comp("w", emptyList(), listOf(
                comp("f", listOf(kw("Float", "LEFT"), len("Width", 100.0), len("Height", 50.0))))),
            comp("e", listOf(px("MarginTop", 400.0), kw("Clear", "LEFT"), len("Height", 50.0))),
        ))
        val plan = FloatClearance.resolve(root)
        assertNotNull(plan)
        // The float leaves the flow; E's margin is replaced by the
        // clearance-adjusted 50 (400 absorbed by negative clearance).
        assertEquals(true, plan!!.adjustments["f"]?.zeroFlowHeight)
        assertEquals(50.0, plan.adjustments["e"]?.appliedTopPx)
        assertEquals(0.0, plan.adjustments["e"]?.appliedBottomPx)
        // Scope covers every box so nested collapse plans suppress.
        assertEquals(setOf("red", "w", "f", "e"), plan.scopeIds)
    }

    @Test
    fun `adjoining-float-new-fc - clearance inside a BFC root`() {
        // red(overflow:hidden) > inner > [float L 100x50,
        //   E(mt:300, clear:left, overflow:hidden, 100x50)].
        // The 300 collapses through inner's top edge (red's BFC edge
        // stops the chain) — inner would move, pulling its float: forced.
        val root = comp("red", listOf(
            len("Width", 100.0), kw("OverflowX", "HIDDEN"), kw("OverflowY", "HIDDEN")), listOf(
            comp("inner", emptyList(), listOf(
                comp("f", listOf(kw("Float", "LEFT"), len("Width", 100.0), len("Height", 50.0))),
                comp("e", listOf(px("MarginTop", 300.0), kw("Clear", "LEFT"),
                    kw("OverflowX", "HIDDEN"), kw("OverflowY", "HIDDEN"),
                    len("Width", 100.0), len("Height", 50.0))),
            )),
        ))
        val plan = FloatClearance.resolve(root)
        assertNotNull(plan)
        // E's border top = float bottom (50); the 300 is absorbed.
        assertEquals(true, plan!!.adjustments["f"]?.zeroFlowHeight)
        assertEquals(50.0, plan.adjustments["e"]?.appliedTopPx)
    }

    @Test
    fun `clear-on-child-with-margins-2 - right clear past the right float only`() {
        // red(oh) > [fR(right, h20), middle > [fL(left, h100),
        //   wrapper > [E(clear:right, h80, mt16)]]].
        // The 16 collapses up to red's BFC edge without displacing fR
        // (fR anchors in red, which is never crossed): hyp 16 < fR
        // bottom 20 → clearance → border top at 20 (browser: c=4+16).
        val root = comp("red", listOf(
            len("Width", 100.0), kw("OverflowX", "HIDDEN"), kw("OverflowY", "HIDDEN")), listOf(
            comp("fR", listOf(kw("Float", "RIGHT"), len("Height", 20.0))),
            comp("middle", emptyList(), listOf(
                comp("fL", listOf(kw("Float", "LEFT"), len("Height", 100.0))),
                comp("wrapper", emptyList(), listOf(
                    comp("e", listOf(kw("Clear", "RIGHT"), len("Height", 80.0), px("MarginTop", 16.0))))),
            )),
        ))
        val plan = FloatClearance.resolve(root)
        assertNotNull(plan)
        // BOTH floats leave the flow (left one included — its 100px of
        // stacked height is the Android-only wave-41 failure signature).
        assertEquals(true, plan!!.adjustments["fR"]?.zeroFlowHeight)
        assertEquals(true, plan.adjustments["fL"]?.zeroFlowHeight)
        assertEquals(20.0, plan.adjustments["e"]?.appliedTopPx)
    }

    @Test
    fun `clear-on-parent-with-margins - the -1000 child margin is absorbed`() {
        // red(200x200) > [f(left 200x100), E(clear:left, mt:100) >
        //   [child(h:100, mt:-1000)]].
        // E's top-margin group is {100, -1000} (parent/first-child
        // adjoining) — clearance absorbs the WHOLE group: E's border top
        // lands at the float bottom (100) and the child renders flush at
        // E's content top (its -1000 must not paint it offscreen, the
        // wave-41 capture's missing-green defect).
        val root = comp("red", listOf(len("Width", 200.0), len("Height", 200.0)), listOf(
            comp("f", listOf(kw("Float", "LEFT"), len("Width", 200.0), len("Height", 100.0))),
            comp("e", listOf(kw("Clear", "LEFT"), px("MarginTop", 100.0)), listOf(
                comp("child", listOf(len("Height", 100.0), px("MarginTop", -1000.0))))),
        ))
        val plan = FloatClearance.resolve(root)
        assertNotNull(plan)
        assertEquals(true, plan!!.adjustments["f"]?.zeroFlowHeight)
        assertEquals(100.0, plan.adjustments["e"]?.appliedTopPx)
        // The absorbed chain member's margins zero out entirely.
        assertEquals(0.0, plan.adjustments["child"]?.appliedTopPx)
        assertEquals(0.0, plan.adjustments["child"]?.appliedBottomPx)
    }

    @Test
    fun `negative-clearance-after-adjoining-float - red box collapses to 50px`() {
        // red > [f(left 100x50), E(clear:left, mt:200)] — then a sibling
        // ROOT follows in the document. Forced clearance (the 200 would
        // pull the float): E's border top = 50, so red's auto height is
        // 50 and the following green root lands at 50..100 (the ref's
        // lower green half).
        val root = comp("red", listOf(len("Width", 100.0)), listOf(
            comp("f", listOf(kw("Float", "LEFT"), len("Width", 100.0), len("Height", 50.0))),
            comp("e", listOf(kw("Clear", "LEFT"), px("MarginTop", 200.0))),
        ))
        val plan = FloatClearance.resolve(root)
        assertNotNull(plan)
        assertEquals(true, plan!!.adjustments["f"]?.zeroFlowHeight)
        assertEquals(50.0, plan.adjustments["e"]?.appliedTopPx)
    }

    @Test
    fun `clear-on-parent - identical geometry to the frozen accidental pass`() {
        // red(200x200) > [f(left 200x100), E(clear:left) > [child(h100)]].
        // Wave-41 passed this by ACCIDENT (float stacked 100 + E at 100).
        // The honest model must produce the SAME pixels: float out of
        // flow at 0..100, E's border top = clear line 100.
        val root = comp("red", listOf(len("Width", 200.0), len("Height", 200.0)), listOf(
            comp("f", listOf(kw("Float", "LEFT"), len("Width", 200.0), len("Height", 100.0))),
            comp("e", listOf(kw("Clear", "LEFT")), listOf(
                comp("child", listOf(len("Height", 100.0))))),
        ))
        val plan = FloatClearance.resolve(root)
        assertNotNull(plan)
        assertEquals(100.0, plan!!.adjustments["e"]?.appliedTopPx)
        assertEquals(0.0, plan.adjustments["child"]?.appliedTopPx)
    }

    // ── No-clearance and bail pins (the identity guarantees) ──

    @Test
    fun `hypothetical past the float - no clearance, identity`() {
        // outer(padding-top:1) > [f(left 100x50), E(clear:left, mt:25) >
        //   [child(mt:150)]]. The padding band blocks the ascent (no
        //   pull); the collapsed group {25,150}=150 is past the float
        //   bottom 51 → §9.5.2 introduces NO clearance → null (the
        //   clear-on-parent-with-margins-no-clearance discriminator).
        val root = comp("outer", listOf(px("PaddingTop", 1.0)), listOf(
            comp("f", listOf(kw("Float", "LEFT"), len("Width", 100.0), len("Height", 50.0))),
            comp("e", listOf(kw("Clear", "LEFT"), px("MarginTop", 25.0)), listOf(
                comp("child", listOf(px("MarginTop", 150.0))))),
        ))
        assertNull(FloatClearance.resolve(root))
    }

    @Test
    fun `bails - shapes outside the proven scope resolve to null`() {
        val f = comp("f", listOf(kw("Float", "LEFT"), len("Width", 50.0), len("Height", 50.0)))
        // Two clear boxes need sequential clearance state → null.
        assertNull(FloatClearance.resolve(comp("r", emptyList(), listOf(
            f, comp("e1", listOf(kw("Clear", "LEFT"))), comp("e2", listOf(kw("Clear", "LEFT")))))))
        // A float INSIDE the cleared subtree (adjoining-float-nested-
        // forced-clearance's inner 10px float) → null.
        assertNull(FloatClearance.resolve(comp("r", emptyList(), listOf(
            f, comp("e", listOf(kw("Clear", "BOTH")), listOf(
                comp("f2", listOf(kw("Float", "LEFT"), len("Height", 10.0)))))))))
        // ≥2 consecutive same-side floats are the wave-19 RUN shape → null.
        assertNull(FloatClearance.resolve(comp("r", emptyList(), listOf(
            f, comp("f2", listOf(kw("Float", "LEFT"), len("Height", 50.0))),
            comp("e", listOf(kw("Clear", "LEFT")))))))
        // A float with no provable height (auto) → null.
        assertNull(FloatClearance.resolve(comp("r", emptyList(), listOf(
            comp("fa", listOf(kw("Float", "LEFT"), len("Width", 50.0))),
            comp("e", listOf(kw("Clear", "LEFT")))))))
        // Text anywhere in scope makes flow heights unknowable → null.
        assertNull(FloatClearance.resolve(comp("r", emptyList(), listOf(
            f, comp("e", listOf(kw("Clear", "LEFT")))), text = "x")))
        // A positioned box in scope leaves the block-flow model → null
        // (clear-on-parent-with-margins-no-clearance's relative outer).
        assertNull(FloatClearance.resolve(comp("r", listOf(kw("Position", "RELATIVE")), listOf(
            f, comp("e", listOf(kw("Clear", "LEFT")))))))
        // Mid-tree non-BFC attach (slot parent, no overflow) → null: the
        // scope could miss same-BFC floats above it.
        assertNull(FloatClearance.resolve(comp("r", emptyList(), listOf(
            f, comp("e", listOf(kw("Clear", "LEFT")))), slotParent = "above")))
        // Clear with no same-side float (clear:right, left float) → null.
        assertNull(FloatClearance.resolve(comp("r", emptyList(), listOf(
            f, comp("e", listOf(kw("Clear", "RIGHT")))))))
        // A UA-margin tag in scope (only bare/div boxes are modeled).
        assertNull(FloatClearance.resolve(comp("r", emptyList(), listOf(
            f,
            IRComponent(id = "p", name = "p", properties = listOf(kw("Clear", "LEFT")), _tag = "p")))))
        // An INTERMEDIATE wrapper with a block-start margin: the 30px is
        // adjoining to the root's top margin (§8.3.1) and may escape the
        // root, so the float's literal ledger position (30+50) is not
        // provable — bail rather than clear to a made-up line.
        assertNull(FloatClearance.resolve(comp("r", emptyList(), listOf(
            comp("w", listOf(px("MarginTop", 30.0)), listOf(f)),
            comp("e", listOf(kw("Clear", "LEFT"), px("MarginTop", 400.0)))))))
        // …and the mirror on the CLEARED box's own chain (its wrapper,
        // not the box itself — the box's own margin IS modeled).
        assertNull(FloatClearance.resolve(comp("r", emptyList(), listOf(
            f,
            comp("w2", listOf(px("MarginTop", 30.0)), listOf(
                comp("e", listOf(kw("Clear", "LEFT")))))))))
        // A float carrying its OWN block-axis padding: under the CSS
        // initial content-box its margin edge is mt+pt+h+pb+mb, but the
        // ledger adds only margins + height, so the clear line would be
        // SHORT by the padding band (here 10px) — bail, never guess.
        // Without this bail the same tree resolves appliedTop = 50.
        assertNull(FloatClearance.resolve(comp("r", emptyList(), listOf(
            comp("fp", listOf(kw("Float", "LEFT"), len("Width", 50.0), len("Height", 50.0),
                px("PaddingBottom", 10.0))),
            comp("e", listOf(kw("Clear", "LEFT")))))))
        // …and a BoxSizing wire alone (zero padding) bails too: it decides
        // WHETHER `height` already contains the padding band, and the
        // projection cannot prove the used value either way.
        assertNull(FloatClearance.resolve(comp("r", emptyList(), listOf(
            comp("fb", listOf(kw("Float", "LEFT"), len("Width", 50.0), len("Height", 50.0),
                kw("BoxSizing", "BORDER_BOX"))),
            comp("e", listOf(kw("Clear", "LEFT")))))))
    }

    @Test
    fun `bfc-root reads match the twin - logical overflow in, non-keyword out`() {
        // The two Kotlin/Swift divergence cases of the projection's BFC
        // read, pinned VERBATIM in FloatClearanceTests.swift. A BFC root
        // blocks the §8.3.1 ascent, so disagreeing here means the two
        // natives compute different clearance from identical bytes.
        // (a) `overflow-block: hidden` IS a BFC root: css-overflow-3 §3
        // maps the logical axis onto the block axis (horizontal-tb under
        // this lane's WritingMode bail) and a non-visible axis makes a
        // scroll container (CSS 2.1 §9.4.1). iOS read only the three
        // physical keys and missed it.
        assertEquals(true,
            FloatClearanceModel.project(comp("r", listOf(kw("OverflowBlock", "HIDDEN"))), null)?.bfcRoot)
        // (b) a NON-KEYWORD Overflow payload is undecodable, so the used
        // value stays the VISIBLE initial (OverflowExtractor's else-branch)
        // — never a BFC root. iOS treated the failed decode as "not
        // visible" and turned every such box into one.
        assertEquals(false,
            FloatClearanceModel.project(comp("r", listOf(px("Overflow", 5.0))), null)?.bfcRoot)
        // The `{"type":"length"}` flavor too — iOS's keyword extractor
        // reads `type` as a keyword, so this one decoded to "LENGTH".
        assertEquals(false,
            FloatClearanceModel.project(comp("r", listOf(len("Overflow", 5.0))), null)?.bfcRoot)
        // The recognized non-visible keywords still make a BFC root.
        assertEquals(true,
            FloatClearanceModel.project(comp("r", listOf(kw("Overflow", "HIDDEN"))), null)?.bfcRoot)
    }

    @Test
    fun `run shape stays with FloatRowPacking - segment still sees it`() {
        // The run bail above must NOT orphan the wave-19 model: the same
        // two-float streak still segments as a run (pins P1/P2 hold).
        val facts = listOf(
            FloatRowPacking.ChildFacts(floatsLeft = true, clearBreaksLeft = false),
            FloatRowPacking.ChildFacts(floatsLeft = true, clearBreaksLeft = false),
        )
        val segs = FloatRowPacking.segment(facts)
        assertEquals(1, segs.size)
        assertTrue(segs.single().isRun)
    }
}

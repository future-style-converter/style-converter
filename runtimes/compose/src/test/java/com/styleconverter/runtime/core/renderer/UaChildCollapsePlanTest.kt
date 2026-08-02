package com.styleconverter.runtime.core.renderer

// Wave 25, lane UAM (BD-RC3) — the REF-A..REF-E pin table: UA default
// block margins folded into the CSS 2.1 §8.3.1 plan the block child stack
// already builds (ComponentRenderer.blockCollapsePlanFor).
//
// Every expectation here is asserted with the IDENTICAL number by the iOS
// twin (UABlockChildMarginTests.swift, same REF-A..REF-E names), so the
// two natives cannot drift.
//
// ## Provenance — MEASURED, not assumed
// Five container shapes were probed in headless Chromium at a 16px root
// (the methodology tools/titan/capture-browser-ref.mjs uses to build the
// browser-ref). Each probe stacked 20px-tall bars inside a 300px block
// container; the quoted rects are the measured getBoundingClientRect
// values. The rule they pin: a block CHILD's UA margin behaves exactly
// like a declared one — it collapses with its siblings by max(), and it
// collapses THROUGH the parent edge unless padding or a border intervenes.
//
// ## Dark-stage 327 protection
// Every REF pin is asserted TWICE: once with uaBlockMargins = true (the
// WPT-capture branch) and once with the default false, where the same
// tree must build NO plan at all — the identity the property-fixture
// pipeline and the 327 committed baselines ride on.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.spacing.CollapsedMargin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UaChildCollapsePlanTest {

    // Wire literal → IRProperty (same convention as BlockCollapsePlanForTest).
    private fun prop(type: String, json: String): IRProperty =
        IRProperty(type, Json.parseToJsonElement(json) as JsonElement)

    // A 20px-tall prose bar with the given source tag and NO declared
    // margins — the probe's `<p></p>` / `<h2></h2>` shape.
    private fun bar(id: String, tag: String, extra: List<IRProperty> = emptyList()) =
        IRComponent(
            id = id, name = id,
            properties = listOf(
                prop("Height", """{"type":"length","px":20.0}"""),
                prop("BackgroundColor",
                     """{"srgb":{"r":0.53,"g":0.53,"b":1.0},"original":"#8888ff"}"""),
            ) + extra,
            _tag = tag,
        )

    // The probe's container: a 300px block div. `extra` adds the padding /
    // border variants; the explicit padding-0 longhands are the live wire
    // shape the converter emits for an unpadded box.
    private fun container(children: List<IRComponent>, extra: List<IRProperty> = emptyList()) =
        IRComponent(
            id = "ua_child_container-001", name = "Ua_Child_Container",
            properties = listOf(
                prop("Width", """{"type":"length","px":300.0}"""),
                prop("PaddingTop", """{"px":0.0}"""),
                prop("PaddingRight", """{"px":0.0}"""),
                prop("PaddingBottom", """{"px":0.0}"""),
                prop("PaddingLeft", """{"px":0.0}"""),
                prop("BackgroundColor",
                     """{"srgb":{"r":0.8,"g":0.8,"b":0.8},"original":"#cccccc"}"""),
            ) + extra,
            children = children,
            _tag = "div",
        )

    // Plan with UA injection ON (the WPT-capture branch).
    private fun uaPlan(c: IRComponent) =
        ComponentRenderer.blockCollapsePlanFor(c, uaBlockMargins = true)

    // The container's own content height for a stack of 20px bars: the
    // per-child applied margins plus the bar boxes (the geometry the
    // measured rects below are compared against).
    private fun contentHeight(perChild: List<CollapsedMargin>, barPx: Float = 20f): Float =
        perChild.fold(0f) { acc, m -> acc + m.topPx + barPx + m.bottomPx }

    // ── REF-A: unpadded, unbordered parent — margins collapse THROUGH ───

    /** REF-A. Probe `<div><p></p><p></p></div>`, measured rects:
     *  div top 16 / height 56, p1 top 16, p2 top 52, next sibling top 88.
     *  So: the first `<p>`'s 1em ESCAPED above the div's border box (the
     *  div starts 16px down), the interior gap is max(16,16)=16, the
     *  content box is 20+16+20=56, and the last `<p>`'s 1em escaped below
     *  (88 − 72 = 16). This is CSS 2.1 §8.3.1 collapse-through, and the
     *  existing hoist machinery already implements it — the UA fold just
     *  supplies the edges. */
    @Test
    fun `REF-A - unpadded parent hoists both ua edges`() {
        val r = uaPlan(container(listOf(bar("p1", "p"), bar("p2", "p"))))
        assertNull(r.fallbackReason)
        val plan = requireNotNull(r.plan)
        // Both edges escape the parent's border box, in full (parent's own
        // margin is 0, so band = max(16, 0) − 0 = 16).
        assertEquals(16f, plan.hoistTopPx, 0.001f)
        assertEquals(16f, plan.hoistBottomPx, 0.001f)
        // First child flush inside; the single interior gap rides child 1.
        assertEquals(listOf(CollapsedMargin(0f, 0f), CollapsedMargin(16f, 0f)), plan.perChild)
        // Measured content height: 56.
        assertEquals(56f, contentHeight(plan.perChild), 0.001f)
    }

    /** REF-A′ — the SAME tree with the flag OFF builds no plan at all
     *  (every declared margin is 0), so the dark-stage 327 corpus and the
     *  whole property-fixture pipeline render byte-identically. */
    @Test
    fun `REF-A prime - ua off builds no plan`() {
        val r = ComponentRenderer.blockCollapsePlanFor(
            container(listOf(bar("p1", "p"), bar("p2", "p"))))
        assertNull(r.plan)
        assertNull(r.fallbackReason)
    }

    // ── REF-B: padding blocks the parent-edge collapse ──────────────────

    /** REF-B. Probe `<div style="padding:10px 0">` with the same two
     *  `<p>`s, measured: div top 88 / height 108, p1 top 114.
     *  114 − 88 = 26 = 10 padding + 16 margin ⇒ the first child's UA
     *  margin stayed INSIDE (§8.3.1: parent and first-child margins are
     *  adjoining only with "no top border and no top padding"). Height
     *  108 = 10 + 16 + 20 + 16 + 20 + 16 + 10. */
    @Test
    fun `REF-B - padding keeps the ua edge margins inside`() {
        val r = uaPlan(container(
            listOf(bar("p1", "p"), bar("p2", "p")),
            extra = listOf(prop("PaddingTop", """{"px":10.0}"""),
                           prop("PaddingBottom", """{"px":10.0}""")),
        ))
        assertNull(r.fallbackReason)
        val plan = requireNotNull(r.plan)
        // Nothing escapes — both gates closed by the padding.
        assertEquals(0f, plan.hoistTopPx, 0.001f)
        assertEquals(0f, plan.hoistBottomPx, 0.001f)
        // First child keeps its full UA top; last keeps its full UA bottom.
        assertEquals(listOf(CollapsedMargin(16f, 0f), CollapsedMargin(16f, 16f)), plan.perChild)
        // Measured height 108 = the 88px content box + the 20px padding.
        assertEquals(88f, contentHeight(plan.perChild), 0.001f)
    }

    // ── REF-C: a border blocks it too ──────────────────────────────────

    /** REF-C. Probe `<div style="border-top:5px solid;border-bottom:5px
     *  solid">`, measured: div top 196 / height 98, p1 top 217.
     *  217 − 196 = 21 = 5 border + 16 margin ⇒ same containment as REF-B.
     *  Height 98 = 5 + 16 + 20 + 16 + 20 + 16 + 5. Also measured: the
     *  PREVIOUS padded div's bottom (196) touches this div's top, i.e.
     *  neither container leaked a margin — the containment is symmetric. */
    @Test
    fun `REF-C - a border keeps the ua edge margins inside`() {
        val r = uaPlan(container(
            listOf(bar("p1", "p"), bar("p2", "p")),
            // Live border wire shapes (width `{"px":…}` + keyword style) —
            // both declared so the G2 gate reads the same USED width on
            // BOTH natives (iOS resolves hasBorder from the style+width
            // pair, Compose from the resolved width alone).
            extra = listOf(prop("BorderTopWidth", """{"px":5.0}"""),
                           prop("BorderTopStyle", "\"SOLID\""),
                           prop("BorderBottomWidth", """{"px":5.0}"""),
                           prop("BorderBottomStyle", "\"SOLID\"")),
        ))
        assertNull(r.fallbackReason)
        val plan = requireNotNull(r.plan)
        assertEquals(0f, plan.hoistTopPx, 0.001f)
        assertEquals(0f, plan.hoistBottomPx, 0.001f)
        assertEquals(listOf(CollapsedMargin(16f, 0f), CollapsedMargin(16f, 16f)), plan.perChild)
        // 98 measured = this 88px content box + the two 5px bands.
        assertEquals(88f, contentHeight(plan.perChild), 0.001f)
    }

    // ── REF-D: mixed tags collapse by max() ────────────────────────────

    /** REF-D. Probe `<div><h2></h2><p></p></div>`, measured: div/h2 top
     *  313.90625 (flush — hoisted), h2 bottom 333.90625, p top 353.8125 ⇒
     *  interior gap 19.90625 = max(h2's .83em-of-24px, p's 1em).
     *  Chromium's exact h2 margin is 19.90625px (LayoutUnit 1/64); the
     *  Round-4 table pins the integer 19 for BOTH natives, so the pin
     *  below is 19 and the ~0.9px delta is a table-calibration matter for
     *  the root-stack lane, deliberately NOT changed here (moving it would
     *  move every committed Round-4 root pin). */
    @Test
    fun `REF-D - differing tag defaults collapse to the max`() {
        val r = uaPlan(container(listOf(bar("head", "h2"), bar("body", "p"))))
        assertNull(r.fallbackReason)
        val plan = requireNotNull(r.plan)
        // The h2's larger top margin is what escapes above the parent.
        assertEquals(19f, plan.hoistTopPx, 0.001f)
        // The trailing `<p>`'s 1em escapes below.
        assertEquals(16f, plan.hoistBottomPx, 0.001f)
        // Interior gap = max(h2 bottom 19, p top 16) = 19, carried once.
        assertEquals(listOf(CollapsedMargin(0f, 0f), CollapsedMargin(19f, 0f)), plan.perChild)
        assertEquals(59f, contentHeight(plan.perChild), 0.001f)
    }

    // ── REF-E: author margins win per edge, then collapse ──────────────

    /** REF-E. Probe `<div><p></p><p></p></div>` with
     *  `.authored > p { margin-top: 40px }`, measured: div/p1 top
     *  413.8125 (flush), previous sibling bottom 373.8125 ⇒ 40px escaped
     *  above; p1 bottom 433.8125, p2 top 473.8125 ⇒ interior gap 40 =
     *  max(p1's UA bottom 16, p2's authored top 40); div height 80 =
     *  20 + 40 + 20. The authored TOP replaced the UA top while the UA
     *  BOTTOM survived — the two edges cascade independently. */
    @Test
    fun `REF-E - authored top wins, ua bottom survives, gap is the max`() {
        val authored = listOf(prop("MarginTop", """{"px":40.0}"""))
        val r = uaPlan(container(listOf(bar("p1", "p", authored), bar("p2", "p", authored))))
        assertNull(r.fallbackReason)
        val plan = requireNotNull(r.plan)
        // The authored 40 escapes above (author > UA, css-cascade-4 §6.1).
        assertEquals(40f, plan.hoistTopPx, 0.001f)
        // The last child's UNDECLARED bottom still takes the UA 1em.
        assertEquals(16f, plan.hoistBottomPx, 0.001f)
        // Interior gap = max(16, 40) = 40, carried once by child 1.
        assertEquals(listOf(CollapsedMargin(0f, 0f), CollapsedMargin(40f, 0f)), plan.perChild)
        // Measured content height 80.
        assertEquals(80f, contentHeight(plan.perChild), 0.001f)
    }

    // ── B11: the UA-only nested-hoist-chain conservatism ───────────────

    /** B11 — with UA injection ON, an INTERIOR child that is itself a
     *  hoisting block container bails the whole plan: its own hoisted band
     *  would stack on top of this fold's sibling gap, where the browser
     *  resolves the whole adjoining chain into one max(). B10 already
     *  covers the first/last positions; this widens it for the UA branch
     *  only, so no declared-margin plan changes shape. */
    @Test
    fun `B11 - interior nested hoisting container bails the ua plan`() {
        val nested = container(listOf(bar("inner", "p"))).let {
            IRComponent(id = "mid", name = "mid", properties = it.properties,
                        children = it.children, _tag = "div")
        }
        val r = uaPlan(container(listOf(bar("p1", "p"), nested, bar("p2", "p"))))
        assertNull(r.plan)
        assertEquals("nested-hoist chain (ua)", r.fallbackReason)
        // With the flag OFF the same tree is silent (no margins anywhere).
        val off = ComponentRenderer.blockCollapsePlanFor(
            container(listOf(bar("p1", "p"), nested, bar("p2", "p"))))
        assertNull(off.plan)
        assertNull(off.fallbackReason)
    }

    /** UAM-PARENT — a `<blockquote>` parent's OWN 1em is folded into the
     *  hoist-band composition, so the band it adds for a `<p>` first child
     *  is max(16, 16) − 16 = 0. Without this the blockquote's margin
     *  (painted one level up, or by the composed root stack) and the
     *  child's band would double to 32px where the browser renders 16. */
    @Test
    fun `UAM-PARENT - a blockquote parent's own ua margin absorbs the band`() {
        val bq = IRComponent(
            id = "bq", name = "bq",
            properties = container(emptyList()).properties,
            children = listOf(bar("p1", "p"), bar("p2", "p")),
            _tag = "blockquote",
        )
        val plan = requireNotNull(uaPlan(bq).plan)
        // Nothing extra escapes — the parent already carries the 1em.
        assertEquals(0f, plan.hoistTopPx, 0.001f)
        assertEquals(0f, plan.hoistBottomPx, 0.001f)
        // The interior collapse is unaffected by the parent's own margin.
        assertEquals(listOf(CollapsedMargin(0f, 0f), CollapsedMargin(16f, 0f)), plan.perChild)
    }
}

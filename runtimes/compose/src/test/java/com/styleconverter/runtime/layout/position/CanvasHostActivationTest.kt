package com.styleconverter.runtime.layout.position

// Wave 21 (A-RC7) — host ACTIVATION pins. The wave-18 RC1 static-position
// zero-flow anchor is host-gated in ComponentRenderer, but activation was
// hoisted-boxes-only: a document whose ONLY out-of-flow boxes are no-inset
// absolutes never activated the Host, so those boxes rendered as ordinary
// flow blocks and BLOCK-STACKED with phantom flow space. Live failure:
// css-images conic-gradient-line-height-relative-units-001/002 (Android
// 0.806) — two absolute no-inset roots whose ref OVERLAPS them at the
// canvas origin; the composed capture stacked them vertically instead.
//
// Fix under pin: hostActivates == anyOutOfFlowBox — true for EITHER
// out-of-flow class (css-position-3 §2.1 knows only one "out of flow";
// §3.1 static-position vs inset anchoring differ only in WHERE the box
// paints, never in whether it occupies flow space).
//
// Parity oracle: iOS applies its StaticPositionAnchor UNGATED — the
// composed canvas (apps/ios-harness/.../CaptureCanvas.swift) wraps a root
// on FixedHoist.rendersInFlowAsStaticPosition alone, with no does-anything-
// hoist precondition — so 001/002 already zero-flow there. These pins bring
// the Compose activation to (at least) that breadth.
//
// Same JVM-only style + wire helpers as CanvasRootHoistTest (kept as its
// own file per the ≤200-line rule).

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasHostActivationTest {

    // Parse one JSON literal into the IRProperty data slot (frozen wire).
    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))

    // A position keyword + optional physical px insets, as the reader emits.
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

    // ── A1: THE fixed bug shape — static-position-only documents activate ──

    @Test fun `A1 a document of ONLY no-inset absolute roots activates the host`() {
        // The conic-gradient-…-001/002 IR shape: two absolute roots, zero
        // insets (Width/Height/ZIndex/background props don't matter to the walk).
        val doc = listOf(
            comp("conic-green", positioned("absolute")),
            comp("conic-red", positioned("absolute")),
        )
        // Pre-fix this was FALSE (no box hoists) → both roots kept natural
        // flow size and stacked; the ref overlaps them at the canvas origin.
        assertTrue(CanvasRootHoist.hostActivates(doc))
        // The overlay walk still collects NOTHING — activation without
        // slots is exactly the locals-only Host branch this wave added.
        assertTrue(CanvasRootHoist.collectCanvasHoisted(doc).isEmpty())
    }

    // ── A2: hoisted documents keep activating (superset, never narrower) ──

    @Test fun `A2 any hoist-eligible box still activates - the wave-19 behavior is kept`() {
        // An inset absolute root hoists (wave-18 RC1 table) → activates.
        assertTrue(CanvasRootHoist.hostActivates(listOf(comp("h", positioned("absolute", left = 10.0)))))
        // A no-inset FIXED root hoists (kept wave-17 behavior) → activates.
        assertTrue(CanvasRootHoist.hostActivates(listOf(comp("f", positioned("fixed")))))
    }

    // ── A3: in-flow-only documents never activate (baseline identity) ──────

    @Test fun `A3 an out-of-flow-free document does NOT activate - identity fast path pinned`() {
        // relative/sticky/static all stay in flow (css-position-3 §2.1) —
        // the Host must keep rendering these documents with no locals and
        // no wrapper (the frozen composed-baseline discipline).
        val doc = listOf(
            comp("rel", positioned("relative", left = 3.0)),
            comp("wrap", children = listOf(comp("sticky", positioned("sticky")))),
        )
        assertFalse(CanvasRootHoist.hostActivates(doc))
    }

    // ── A4: positioned-ancestor threading matches the render-side rule ─────

    @Test fun `A4 a no-inset absolute UNDER a positioned ancestor does not activate on its own`() {
        // CSS 2.1 §10.1: the relative parent is the containing block — the
        // wave-8/9 positioned-children machinery owns this box (it is NOT
        // in the RC1 static-position class; iOS's twin rule table agrees:
        // FixedHoist.rendersInFlowAsStaticPosition also requires no
        // positioned ancestor), so the host has nothing to gate.
        val doc = listOf(
            comp("rel-parent", positioned("relative"),
                children = listOf(comp("abs-child", positioned("absolute")))),
        )
        assertFalse(CanvasRootHoist.hostActivates(doc))
    }

    // ── A5: the walk recurses — depth must not hide an activation cause ────

    @Test fun `A5 a static-position box at depth under STATIC ancestors activates`() {
        // Static wrappers establish no containing block (CSS 2.1 §10.1), so
        // the deep no-inset absolute is still ICB-anchored static-position —
        // the walk must find it exactly like collectCanvasHoisted finds a
        // deep fixed box.
        val doc = listOf(
            comp("outer", children = listOf(
                comp("mid", children = listOf(comp("deep-abs", positioned("absolute")))))),
        )
        assertTrue(CanvasRootHoist.hostActivates(doc))
    }

    // ── A6: activation is the UNION of the two out-of-flow classes ─────────

    @Test fun `A6 anyOutOfFlowBox is exactly hoisted-or-static-position - no third class`() {
        // Hoisted only → true (A2 re-checked through the internal walk).
        assertTrue(CanvasRootHoist.anyOutOfFlowBox(listOf(comp("f", positioned("fixed")))))
        // Static-position only → true (A1 re-checked).
        assertTrue(CanvasRootHoist.anyOutOfFlowBox(listOf(comp("a", positioned("absolute")))))
        // Neither → false: relative carries an inset but is IN flow — an
        // inset alone must never activate (css-position-3 §3.5: relative
        // insets are visual shifts of an in-flow box).
        assertFalse(CanvasRootHoist.anyOutOfFlowBox(listOf(comp("r", positioned("relative", left = 9.0)))))
    }
}

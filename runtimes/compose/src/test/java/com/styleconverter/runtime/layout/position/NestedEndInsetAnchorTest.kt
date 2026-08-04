package com.styleconverter.runtime.layout.position

// Wave 28 (lane NE) — JVM pins for END-EDGE anchoring inside a POSITIONED
// ANCESTOR (css-position-3 §3.5.3), the nested twin of the wave-22 canvas
// slot EndInsetAnchorTest pins. Pure decisions + arithmetic, so the whole
// N1–N9 table runs without Robolectric — this suite's standing constraint.
//
// ## The oracles
// 1. fixtures/properties/layout/position-right-bottom.json — RB_Px: a 300×120
//    `position:relative` parent whose 60×40 `position:absolute` child declares
//    ONLY `right:20px; bottom:20px`. The CSS used position is
//    (300−60−20, 120−40−20) = (220, 60). Before this lane the nested mount
//    (ComponentRenderer.RenderAbsoluteChild → absposOverflowMeasure) placed
//    every child at the slot origin, so the child's own negated offset landed
//    it at (−20, −20) — outside the parent's top-left corner.
// 2. iOS, which already gets this right and is therefore the parity twin:
//    StyleEngine/layout/position/PositionApplier.anchoredInsetOffset gives the
//    same child `.frame(maxWidth:.infinity, maxHeight:.infinity, alignment:
//    .bottomTrailing)` inside the positioned parent's `.overlay(alignment:
//    .topLeading){ZStack…}`, then `.offset(x:−20, y:−20)` — i.e. fill the
//    containing block, sit flush with its end edge, pull inward by the inset.
//    PositionedAncestorAnchor is that same two-term composition for Compose.

import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.renderer.ComponentRenderer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NestedEndInsetAnchorTest {

    // ── Wire helpers (identical shapes to EndInsetAnchorTest) ─────────────

    /** One IR property from a JSON literal (the frozen wire byte shape). */
    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))

    /** An inset's wire shape, as the converter emits it: `{"px":N}`. */
    private fun inset(type: String, px: Double) = prop(type, """{"px":$px}""")

    /** Resolve a property list through the LIVE extractor the mount uses. */
    private fun config(properties: List<IRProperty>) =
        PositionExtractor.extractPositionConfig(properties.map { it.type to it.data })

    /** RB_Px's child, verbatim from the fixture: abspos + right/bottom 20. */
    private fun rbPxChild() = config(
        listOf(prop("Position", "\"ABSOLUTE\""), inset("Right", 20.0), inset("Bottom", 20.0))
    )

    // ── N1: which children take the end-anchored mount ────────────────────

    @Test
    fun `N1 the RB_Px child asks for the end-anchored mount on both axes`() {
        val c = rbPxChild()
        // The per-axis wave-22 predicates are unchanged…
        assertTrue(c.anchorsFromEndX)
        assertTrue(c.anchorsFromEndY)
        // …and the wave-28 OR is what the nested mount actually gates on.
        assertTrue(c.anchorsFromEndAnyAxis)
        assertTrue(PositionedAncestorAnchor.anchors(c))
    }

    @Test
    fun `N2 start-anchored, over-constrained and inset-free children keep the wave-8 mount`() {
        // A3 start-only (`left`/`top`) — the overwhelming majority, and the
        // shape every committed baseline capture contains.
        val startOnly = config(
            listOf(prop("Position", "\"ABSOLUTE\""), inset("Left", 20.0), inset("Top", 10.0))
        )
        assertFalse(PositionedAncestorAnchor.anchors(startOnly))
        // A2 over-constrained — LTR resolves in favour of left/top, which the
        // slot origin + positive offset already render.
        val bothSides = config(
            listOf(
                prop("Position", "\"ABSOLUTE\""),
                inset("Left", 10.0), inset("Right", 20.0),
                inset("Top", 5.0), inset("Bottom", 30.0),
            )
        )
        assertFalse(PositionedAncestorAnchor.anchors(bothSides))
        // A3 all-auto insets — the box is at its static position (§3.1);
        // RB_Auto's `right:auto; bottom:auto` extracts to exactly this list.
        val noInset = config(listOf(prop("Position", "\"ABSOLUTE\"")))
        assertFalse(PositionedAncestorAnchor.anchors(noInset))
    }

    @Test
    fun `N3 only out-of-flow schemes take the mount — relative sticky static never do`() {
        // A7: a relative box's inset is a displacement from its STATIC
        // position (§3.4), a sticky box's is a scroll threshold (§3.6), and a
        // static box has no used inset at all (§2.1). RenderAbsoluteChild
        // only sees absolute/fixed children, so this is the belt-and-braces
        // half of the same gate PositionConfig already applies.
        listOf("\"RELATIVE\"", "\"STICKY\"", "\"STATIC\"").forEach { keyword ->
            val c = config(
                listOf(prop("Position", keyword), inset("Right", 20.0), inset("Bottom", 20.0))
            )
            assertFalse("$keyword must not take the end-anchored mount",
                PositionedAncestorAnchor.anchors(c))
        }
        // …while both out-of-flow schemes do (a fixed child in a hostless
        // render keeps the legacy parent anchor — the iOS un-hoisted rule).
        listOf("\"ABSOLUTE\"", "\"FIXED\"").forEach { keyword ->
            val c = config(
                listOf(prop("Position", keyword), inset("Right", 20.0), inset("Bottom", 20.0))
            )
            assertTrue("$keyword must take the end-anchored mount",
                PositionedAncestorAnchor.anchors(c))
        }
    }

    // ── N4: the containing-block extent (A1/A4) ───────────────────────────

    @Test
    fun `N4 an anchored axis takes the mounting Box's extent — an unbounded one does not`() {
        // A1: the positioned parent's overlay Box is fillMaxSize, so its max
        // constraint IS the containing block this child anchors in.
        assertEquals(300, PositionedAncestorAnchor.axisFillPx(true, 300))
        // A4: an UNBOUNDED constraint (auto-sized ancestor in an unbounded
        // context) leaves nothing honest to measure from — documented
        // degradation to the pre-wave-28 start anchor, not a guess.
        assertNull(PositionedAncestorAnchor.axisFillPx(true, null))
        // A3: a start-anchored axis never fills, even with a known extent.
        assertNull(PositionedAncestorAnchor.axisFillPx(false, 300))
    }

    // ── N5: the report (why the mount is layout-direction-proof) ──────────

    @Test
    fun `N5 an anchored axis reports the containing block — no slack for RTL alignment`() {
        // Filling the axis is the Compose spelling of iOS's
        // `.frame(maxWidth: .infinity)`: with reported == the Box's extent
        // there is no slack for the mounting Box's direction-aware
        // Alignment.TopStart to hand to the right edge under RTL, so the end
        // placement below cannot be double-counted.
        assertEquals(300, PositionedAncestorAnchor.axisReportPx(300, inkPx = 60, minPx = 0, maxPx = 300))
        // Even when the ink overflows the containing block.
        assertEquals(300, PositionedAncestorAnchor.axisReportPx(300, inkPx = 500, minPx = 0, maxPx = 300))
    }

    @Test
    fun `N5 an un-anchored axis reports EXACTLY what the wave-8 mount reports`() {
        // Byte-stability: axisReportPx delegates to the single owner of the
        // §2.1 report rule, so the un-anchored axes of a mixed box (and every
        // frozen baseline shape) keep absposOverflowMeasure's geometry.
        listOf(
            Triple(60, 0, 300),   // fits
            Triple(500, 0, 300),  // overflows → clamps to the envelope
            Triple(10, 40, 300),  // below the minimum → clamps up
        ).forEach { (ink, min, max) ->
            assertEquals(
                ComponentRenderer.absposReportedAxis(ink, min, max),
                PositionedAncestorAnchor.axisReportPx(null, ink, min, max)
            )
        }
    }

    // ── N6/N7: the placement arithmetic, against the two oracles ──────────

    @Test
    fun `N6 the placement is EndInsetAnchor's arithmetic — one rule table, two slots`() {
        // The nested mount must never grow a second copy of the A5/A6 math:
        // pin the delegation itself across the fitting, overflowing and
        // un-anchored cases.
        listOf(300 to 60, 300 to 500, 120 to 40).forEach { (fill, ink) ->
            assertEquals(EndInsetAnchor.placePx(fill.toFloat(), ink),
                PositionedAncestorAnchor.axisPlacePx(fill, ink))
        }
        // A6: no fill ⇒ the slot origin ⇒ byte-identical to the wave-8 place.
        assertEquals(0, PositionedAncestorAnchor.axisPlacePx(null, 60))
    }

    @Test
    fun `N7 RB_Px lands at (220,60) — the css-position-3 used position`() {
        val c = rbPxChild()
        // Term 1, this mount: the box's end edge flush with the containing
        // block's (300 − 60 = 240, 120 − 40 = 80).
        val anchoredX = PositionedAncestorAnchor.axisPlacePx(300, inkPx = 60)
        val anchoredY = PositionedAncestorAnchor.axisPlacePx(120, inkPx = 40)
        assertEquals(240, anchoredX)
        assertEquals(80, anchoredY)
        // Term 2, the child's own PositionApplier offset: the negated inset.
        assertEquals(-20f, c.offsetX.value)
        assertEquals(-20f, c.offsetY.value)
        // Composed: cb − box − inset, matching the web oracle and the iOS
        // `.bottomTrailing` frame + `.offset(−20,−20)` twin. The pre-fix
        // Compose mount produced 0 + (−20) = −20 on both axes.
        assertEquals(220f, anchoredX + c.offsetX.value)
        assertEquals(60f, anchoredY + c.offsetY.value)
    }

    @Test
    fun `N7 RB_Zero and RB_Negative compose the same way`() {
        // RB_Zero: `right:0; bottom:0` sits flush in the parent's corner —
        // (240, 80) for the fixture's 300×120 parent and 60×40 child.
        val zero = config(
            listOf(prop("Position", "\"ABSOLUTE\""), inset("Right", 0.0), inset("Bottom", 0.0))
        )
        assertEquals(240f, PositionedAncestorAnchor.axisPlacePx(300, 60) + zero.offsetX.value)
        assertEquals(80f, PositionedAncestorAnchor.axisPlacePx(120, 40) + zero.offsetY.value)
        // RB_Negative: `right:-10; bottom:-10` hangs the box 10px OUTSIDE the
        // parent's end edges (§2.1 allows the overflow; place() is unclipped).
        val negative = config(
            listOf(prop("Position", "\"ABSOLUTE\""), inset("Right", -10.0), inset("Bottom", -10.0))
        )
        assertEquals(250f, PositionedAncestorAnchor.axisPlacePx(300, 60) + negative.offsetX.value)
        assertEquals(90f, PositionedAncestorAnchor.axisPlacePx(120, 40) + negative.offsetY.value)
    }

    @Test
    fun `N8 mixed axes resolve independently — left-16 bottom-0`() {
        // EndInsetAnchorTest's A3 mixed shape, now through the nested mount:
        // the inline axis keeps the slot origin (its `left:16` offset does
        // the work), the block axis fills and anchors.
        val c = config(
            listOf(prop("Position", "\"ABSOLUTE\""), inset("Left", 16.0), inset("Bottom", 0.0))
        )
        assertTrue(PositionedAncestorAnchor.anchors(c))
        val fillX = PositionedAncestorAnchor.axisFillPx(c.anchorsFromEndX, 300)
        val fillY = PositionedAncestorAnchor.axisFillPx(c.anchorsFromEndY, 120)
        assertNull(fillX)
        assertEquals(120, fillY)
        // X: wave-8 report + origin placement + the positive `left` offset.
        assertEquals(60, PositionedAncestorAnchor.axisReportPx(fillX, 60, 0, 300))
        assertEquals(16f, PositionedAncestorAnchor.axisPlacePx(fillX, 60) + c.offsetX.value)
        // Y: filled report + end placement + the (zero) negated `bottom`.
        assertEquals(120, PositionedAncestorAnchor.axisReportPx(fillY, 40, 0, 120))
        assertEquals(80f, PositionedAncestorAnchor.axisPlacePx(fillY, 40) + c.offsetY.value)
    }

    @Test
    fun `N9 an oversized end-anchored child overflows toward the START edge`() {
        // css-position-3 §2.1: an out-of-flow box may overflow its containing
        // block, and clamping the placement at 0 would move its END edge —
        // the one edge `right` pins. 300 − 500 = −200, drawn unclipped.
        assertEquals(-200, PositionedAncestorAnchor.axisPlacePx(300, inkPx = 500))
        // The REPORT still fits the parent (the box never sizes its ancestor).
        assertEquals(300, PositionedAncestorAnchor.axisReportPx(300, 500, 0, 300))
    }

    @Test
    fun `N9 logical inset-inline-end and inset-block-end take the mount too`() {
        // css-logical-1 §4.1 — the engine's LTR horizontal-tb normalization
        // folds inline-end into right and block-end into bottom before the
        // predicate runs, so the logical spellings anchor identically.
        val c = config(
            listOf(
                prop("Position", "\"ABSOLUTE\""),
                inset("InsetInlineEnd", 20.0), inset("InsetBlockEnd", 20.0),
            )
        )
        assertTrue(PositionedAncestorAnchor.anchors(c))
        // The physical slots the mount and the applier both read.
        assertEquals(20.dp, c.resolvedEnd)
        assertEquals(20.dp, c.resolvedBottom)
        // …and the applier's half is the same negated inset as the physical
        // spelling, so the composition is identical.
        assertEquals(-20f, c.offsetX.value)
        assertEquals(-20f, c.offsetY.value)
    }
}

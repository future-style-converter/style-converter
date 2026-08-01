package com.styleconverter.runtime.layout.position

// Wave 22 (B-RC3) — JVM pins for END-EDGE anchoring of out-of-flow boxes
// (css-position-3 §3.5.3). Pure over the IR + plain arithmetic, so the whole
// A1–A8 table is pinnable without Robolectric (the suite's standing
// constraint — same style as CanvasRootHoistTest).
//
// The oracle for every expected coordinate is the wave21-final WEB capture of
// css-multicol/abspos-containing-block-outside-spanner (web scores the pixel
// truth here): on the 390×600 canvas its two 100×100 absolute roots paint at
// (0,0)-(99,99) [`top:0; left:0`] and (290,500)-(389,599) [`bottom:0;
// right:0`]. The pre-fix Android capture contained exactly ONE red region, at
// (0,0)-(99,99), for the two red divs — both anchored top-left.

import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EndInsetAnchorTest {

    // ── Wire helpers (same shapes as CanvasRootHoistTest) ─────────────────

    /** One IR property from a JSON literal (the frozen wire byte shape). */
    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))

    /** The multicol root's inset wire shape: `{"px":N}`. */
    private fun inset(type: String, px: Double) = prop(type, """{"px":$px}""")

    /** Resolve a property list through the LIVE extractor the chain uses. */
    private fun config(properties: List<IRProperty>) =
        PositionExtractor.extractPositionConfig(properties.map { it.type to it.data })

    // ── A1–A3: which axis anchors from the end edge ───────────────────────

    @Test
    fun `A1 the multicol bottom-right root anchors from BOTH end edges`() {
        // The live IR of component __7: absolute + Bottom:0 + Right:0.
        val c = config(
            listOf(prop("Position", "\"ABSOLUTE\""), inset("Bottom", 0.0), inset("Right", 0.0))
        )
        assertTrue(c.anchorsFromEndX)
        assertTrue(c.anchorsFromEndY)
    }

    @Test
    fun `A2 over-constrained insets keep the START anchor — left-top wins in LTR`() {
        // css-position-3 §3.5.3: with both sides declared the LTR containing
        // block ignores `right`/`bottom`, which the existing start anchor +
        // positive offset already render.
        val c = config(
            listOf(
                prop("Position", "\"ABSOLUTE\""),
                inset("Left", 10.0), inset("Right", 20.0),
                inset("Top", 5.0), inset("Bottom", 30.0),
            )
        )
        assertFalse(c.anchorsFromEndX)
        assertFalse(c.anchorsFromEndY)
        // …and the offset the applier emits is the START inset, unchanged.
        assertEquals(10f, c.offsetX.value)
        assertEquals(5f, c.offsetY.value)
    }

    @Test
    fun `A3 start-only and inset-free boxes keep the START anchor`() {
        // The live IR of component __6: absolute + Top:0 + Left:0 — the
        // wave-17 canvas-origin anchor, unchanged.
        val startOnly = config(
            listOf(prop("Position", "\"ABSOLUTE\""), inset("Top", 0.0), inset("Left", 0.0))
        )
        assertFalse(startOnly.anchorsFromEndX)
        assertFalse(startOnly.anchorsFromEndY)
        // The wave-18 RC1 all-auto-inset box: static position, no anchoring.
        val noInset = config(listOf(prop("Position", "\"ABSOLUTE\"")))
        assertFalse(noInset.anchorsFromEndX)
        assertFalse(noInset.anchorsFromEndY)
    }

    @Test
    fun `A3 logical inset-inline-end and inset-block-end anchor like the physical sides`() {
        // css-logical-1 §4.1 — the engine's LTR horizontal-tb normalization
        // folds inline-end into right and block-end into bottom
        // (PositionConfig.resolvedEnd / resolvedBottom).
        val c = config(
            listOf(
                prop("Position", "\"ABSOLUTE\""),
                inset("InsetInlineEnd", 0.0), inset("InsetBlockEnd", 0.0),
            )
        )
        assertTrue(c.anchorsFromEndX)
        assertTrue(c.anchorsFromEndY)
    }

    @Test
    fun `A3 mixed axes resolve independently`() {
        // `left: 16; bottom: 0` — start anchor on X, end anchor on Y.
        val c = config(
            listOf(prop("Position", "\"ABSOLUTE\""), inset("Left", 16.0), inset("Bottom", 0.0))
        )
        assertFalse(c.anchorsFromEndX)
        assertTrue(c.anchorsFromEndY)
    }

    // ── A4: no honest containing-block extent ⇒ start anchor ──────────────

    @Test
    fun `A4 an unknown containing-block extent keeps the start anchor`() {
        // Documented degradation, not a silent fallthrough: with no extent
        // there is nothing to measure the end edge from, so the box keeps
        // the pre-wave-22 placement.
        assertNull(EndInsetAnchor.endEdge(anchorsFromEnd = true, containingBlockExtent = null))
        assertEquals(0, EndInsetAnchor.placePx(null, boxPx = 100))
    }

    @Test
    fun `A4 a start-anchored axis never takes an end edge even when the extent is known`() {
        assertNull(EndInsetAnchor.endEdge(anchorsFromEnd = false, containingBlockExtent = 390.dp))
    }

    @Test
    fun `A1 an end-anchored axis takes the canvas extent verbatim`() {
        // The composed canvas is the containing block for hoisted boxes —
        // 390×600, matching the browser-ref viewport the oracle is captured at.
        assertEquals(390.dp, EndInsetAnchor.endEdge(anchorsFromEnd = true, containingBlockExtent = 390.dp))
        assertEquals(600.dp, EndInsetAnchor.endEdge(anchorsFromEnd = true, containingBlockExtent = 600.dp))
    }

    // ── A5/A6: the placement arithmetic against the web oracle ────────────

    @Test
    fun `A5 the multicol root lands at canvas (290,500) — the web oracle`() {
        // Anchor: end edge − box. 390 − 100 = 290, 600 − 100 = 500. The
        // box's own PositionApplier offset is −right = −0 / −bottom = −0,
        // so the painted origin is exactly (290,500) — matching the web
        // capture's second red region at x 290..389 / y 500..599.
        assertEquals(290, EndInsetAnchor.placePx(390f, boxPx = 100))
        assertEquals(500, EndInsetAnchor.placePx(600f, boxPx = 100))
    }

    @Test
    fun `A5 a non-zero end inset composes with the applier's negated offset`() {
        // `right: 20` on a 100px box in a 390px containing block: the anchor
        // gives 290 and PositionApplier adds −20 → 270 = cb − box − right,
        // the css-position-3 §3.5.3 used position.
        val anchored = EndInsetAnchor.placePx(390f, boxPx = 100)
        val c = config(listOf(prop("Position", "\"ABSOLUTE\""), inset("Right", 20.0)))
        assertEquals(-20f, c.offsetX.value)
        assertEquals(270f, anchored + c.offsetX.value)
    }

    @Test
    fun `A5 an oversized box anchors NEGATIVE and overflows toward the start edge`() {
        // An out-of-flow box may overflow its containing block
        // (css-position-3 §2.1); Compose's place() draws unclipped, matching
        // the ref. No floor at 0 — clamping would move the END edge.
        assertEquals(-110, EndInsetAnchor.placePx(390f, boxPx = 500))
    }

    @Test
    fun `A6 the start anchor is exactly the pre-wave-22 slot origin`() {
        // The byte-stability pin: every start-anchored, static-position and
        // unknown-extent box still places at 0 on both axes.
        assertEquals(0, EndInsetAnchor.placePx(null, boxPx = 0))
        assertEquals(0, EndInsetAnchor.placePx(null, boxPx = 100))
    }

    @Test
    fun `A6 fractional extents round like every other placement in the lane`() {
        // Compose places on whole px; round() matches
        // ComponentRenderer.absposOverflowMeasure's roundToInt discipline.
        assertEquals(290, EndInsetAnchor.placePx(389.6f, boxPx = 100))
        assertEquals(289, EndInsetAnchor.placePx(389.4f, boxPx = 100))
    }

    // ── A7: only OUT-OF-FLOW boxes anchor from an end edge ────────────────

    @Test
    fun `A7 a relative box with only bottom is a displacement, never an end anchor`() {
        // css-position-3 §3.4: on a relatively positioned box an inset
        // displaces the box from its STATIC position and reserves the
        // original flow space — it is not a containing-block anchor. The
        // live corpus witness is css-backgrounds/background-334's root __2
        // (`position: relative; bottom: 160px`), whose correct render is
        // "160px UP from where it would have been", which offsetY already
        // produces from the start anchor. Anchoring it at the containing
        // block's bottom edge would teleport it.
        val c = config(listOf(prop("Position", "\"RELATIVE\""), inset("Bottom", 160.0)))
        assertFalse(c.anchorsFromEndY)
        assertFalse(c.anchorsFromEndX)
        // The displacement itself is UNCHANGED by the gate.
        assertEquals(-160f, c.offsetY.value)
    }

    @Test
    fun `A7 static and sticky never anchor from an end edge either`() {
        // A static box has no used inset at all (css-position-3 §2.1);
        // sticky insets are scroll thresholds, not anchors (§3.6).
        listOf("\"STATIC\"", "\"STICKY\"").forEach { keyword ->
            val c = config(listOf(prop("Position", keyword),
                inset("Right", 10.0), inset("Bottom", 10.0)))
            assertFalse("$keyword must not end-anchor", c.anchorsFromEndX)
            assertFalse("$keyword must not end-anchor", c.anchorsFromEndY)
        }
    }

    @Test
    fun `A7 absolute and fixed still anchor - the gate is byte-neutral for them`() {
        // The two schemes CanvasRootHoist actually hoists keep the wave-22
        // behavior exactly: the multicol root and its fixed twin both
        // end-anchor on both axes.
        listOf("\"ABSOLUTE\"", "\"FIXED\"").forEach { keyword ->
            val c = config(listOf(prop("Position", keyword),
                inset("Right", 0.0), inset("Bottom", 0.0)))
            assertTrue("$keyword must end-anchor", c.anchorsFromEndX)
            assertTrue("$keyword must end-anchor", c.anchorsFromEndY)
        }
    }
}

package com.styleconverter.runtime.layout.position

// Wave 42 (lane W8) — JVM pins for the CSS2 §10.3.7 abspos SHRINK-TO-FIT
// width, the fix for "abspos never wraps".
//
// Measured defect (wave-41 T5): the out-of-flow anchors measured every box
// UNBOUNDED, i.e. used the PREFERRED (single-line max-content) width
// unconditionally, while §10.3.7 says a width:auto abspos box uses
// min(max(preferred-minimum, available), preferred). On WPT
// filter-effects/backdrop-filter-edge-pixels.html (android-ref 0.9211) the
// abspos prose container (`position:absolute; top:100px`, width auto) runs
// its text off the canvas unwrapped where Chromium wraps at the ICB edge;
// filter-effects/backdrop-filter-clip-rect.html's inset-free abspos prose
// div (the RC1 static-position mount) shows the same defect.
//
// The mechanism: zeroFlowAnchor gains a ShrinkToFitSpec — width-auto boxes
// measure with maxWidth = max(preferred-minimum, available) so text wraps
// at the available width while fixed-width content keeps its declared size
// and overflows (the frozen behaviour). All decisions are pure over the IR
// / plain ints, pinned here without Robolectric (the suite's standing
// constraint).

import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShrinkToFitTest {

    // Parse one JSON literal into the IRProperty data slot (the same wire
    // helper shape as CanvasRootHoistTest, so parser drift fails there
    // first).
    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))

    // ── widthIsAuto — which declaration lists engage §10.3.7 ─────────────

    @Test
    fun `edge-pixels prose container is width-auto`() {
        // The EXACT wire of the measured WPT box (per-test IR of
        // wave41-final: Position ABSOLUTE + Top {"px":100} + WillChange):
        // no Width, no InlineSize → §10.3.7's auto clause applies.
        val properties = listOf(
            prop("Position", "\"ABSOLUTE\""),
            prop("Top", """{"px":100}"""),
        )
        assertTrue(CanvasRootHoist.widthIsAuto(properties))
    }

    @Test
    fun `declared width disables the clamp - overflow stays the rendering`() {
        // edge-pixels' `.box` (Width 100px): §10.3.7's first clause uses
        // the declared value, and css-position-3 §2.1 lets it OVERFLOW the
        // containing block — the unbounded measure must be kept.
        val properties = listOf(
            prop("Position", "\"ABSOLUTE\""),
            prop("Width", """{"type":"length","px":100}"""),
        )
        assertFalse(CanvasRootHoist.widthIsAuto(properties))
        // The logical spelling counts as a declared width too.
        assertFalse(
            CanvasRootHoist.widthIsAuto(
                listOf(prop("InlineSize", """{"type":"length","px":100}"""))
            )
        )
    }

    @Test
    fun `min and max clamps leave width auto`() {
        // css-sizing-3 §5: min/max are clamps over the computed width —
        // they do not make `width: auto` definite, so shrink-to-fit still
        // applies.
        val properties = listOf(
            prop("Position", "\"ABSOLUTE\""),
            prop("MinWidth", """{"type":"length","px":10}"""),
            prop("MaxWidth", """{"type":"length","px":500}"""),
        )
        assertTrue(CanvasRootHoist.widthIsAuto(properties))
    }

    // ── shrinkAvailableWidth — §10.3.7's available width ─────────────────

    @Test
    fun `no declared insets - available is the full ICB width`() {
        // edge-pixels: no horizontal inset at all → the whole 358dp ICB
        // content width is available (the width Chromium wraps at).
        assertEquals(358f, CanvasRootHoist.shrinkAvailableWidth(358.dp, null, null).value, 0f)
    }

    @Test
    fun `declared left inset shrinks the available span`() {
        // left: 50px → available = 358 − 50 (a box anchored 50 in can only
        // stretch to the far edge).
        assertEquals(308f, CanvasRootHoist.shrinkAvailableWidth(358.dp, 50.dp, null).value, 0f)
        // Both insets declared subtract together (the §10.3.7 formula).
        assertEquals(288f, CanvasRootHoist.shrinkAvailableWidth(358.dp, 50.dp, 20.dp).value, 0f)
    }

    @Test
    fun `negative left widens and past-the-edge floors at zero`() {
        // A negative inset genuinely widens the remainder (signed math)…
        assertEquals(368f, CanvasRootHoist.shrinkAvailableWidth(358.dp, (-10).dp, null).value, 0f)
        // …and an anchor past the far edge cannot yield a negative band.
        assertEquals(0f, CanvasRootHoist.shrinkAvailableWidth(358.dp, 400.dp, null).value, 0f)
    }

    // ── shrinkToFitMaxPx — the measure band ──────────────────────────────

    @Test
    fun `wrapping text takes the available width as its band`() {
        // preferred-min (longest word) below available → band = available;
        // the box wraps there and min(…, preferred) falls out of
        // content-sized measurement.
        assertEquals(358, CanvasRootHoist.shrinkToFitMaxPx(preferredMinPx = 60, availablePx = 358))
    }

    @Test
    fun `preferred minimum floors the band - fixed content overflows`() {
        // A 500px unbreakable child on a 358px canvas: §10.3.7 clamps at
        // the preferred MINIMUM, never squeezing below it — the box stays
        // 500 wide and overflows, exactly like the frozen unbounded
        // measure rendered it.
        assertEquals(500, CanvasRootHoist.shrinkToFitMaxPx(preferredMinPx = 500, availablePx = 358))
    }

    @Test
    fun `intrinsic refusal keeps the frozen unbounded measure`() {
        // IntrinsicChannel.probe returns null for a subcomposed subtree
        // (no intrinsic channel); the band must be null so the caller
        // falls back to Constraints() — mis-wrapping one box is refused in
        // favour of never squeezing an unknowable minimum.
        assertNull(CanvasRootHoist.shrinkToFitMaxPx(preferredMinPx = null, availablePx = 358))
    }

    // ── staticPositionShrinkToFit — the RC1 mount's spec ─────────────────

    @Test
    fun `clip-rect prose div gets a slot-constraint spec`() {
        // clip-rect's first div: ABSOLUTE via the `div { position:
        // absolute }` rule, NO insets, NO width → RC1 static-position
        // mount; availableX stays null so the anchor reads its own flow
        // slot's incoming maxWidth (which IS the §10.3.7 static-position
        // remainder).
        val spec = CanvasRootHoist.staticPositionShrinkToFit(
            listOf(prop("Position", "\"ABSOLUTE\""))
        )
        assertNotNull(spec)
        assertTrue(spec!!.widthAuto)
        assertNull(spec.availableX)
    }

    @Test
    fun `declared-width static-position box gets no spec`() {
        // Width present → null spec → zeroFlowAnchor keeps the frozen
        // unbounded measure (byte-identity for every sized box).
        assertNull(
            CanvasRootHoist.staticPositionShrinkToFit(
                listOf(
                    prop("Position", "\"ABSOLUTE\""),
                    prop("Width", """{"type":"length","px":100}"""),
                )
            )
        )
    }
}

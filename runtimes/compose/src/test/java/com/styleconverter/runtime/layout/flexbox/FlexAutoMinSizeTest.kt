package com.styleconverter.runtime.layout.flexbox

import androidx.compose.ui.unit.Constraints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Wave 39 (lane A6) — pins css-flexbox-1 §4.5's automatic minimum size band.
 *
 * The Modifier itself needs a Compose layout pass (and therefore Robolectric,
 * which this suite deliberately does not use), so the whole decision lives in
 * [FlexAutoMinSize.mainBand] / [FlexAutoMinSize.squeezed] and this file pins
 * that arithmetic instead — the same "extract the decision, pin it on the
 * JVM" shape as TableBoxTree / FragmentGeometry.
 */
class FlexAutoMinSizeTest {

    // ── The defect this lane fixes ─────────────────────────────────────

    @Test
    fun `a zero-width container never squeezes an item below its min-content`() {
        // The exact css-values/calc-size/calc-size-flex-001 shape: a flex
        // container with a ZERO main size, one item whose min-content main
        // size is 80px (a fixed 80px grandchild). Compose's Row offers 0;
        // §4.5 says the item is 80 and OVERFLOWS.
        val (min, max) = FlexAutoMinSize.mainBand(availableMax = 0, contentMinMain = 80)
        assertEquals(80, min)
        // The ceiling has to rise with the floor or the band is malformed
        // (min > max), which Constraints rejects outright.
        assertEquals(80, max)
    }

    @Test
    fun `an item that already fits is measured with the frozen band`() {
        // The overwhelmingly common case, and the byte-stability argument:
        // the floor is below the offer, so `measure` sees the SAME maxWidth
        // it saw before this lane and a minWidth the item was going to
        // exceed anyway.
        val (min, max) = FlexAutoMinSize.mainBand(availableMax = 358, contentMinMain = 80)
        assertEquals(80, min)
        assertEquals(358, max)
        assertFalse(FlexAutoMinSize.squeezed(availableMax = 358, contentMinMain = 80))
    }

    @Test
    fun `squeezed is exactly available-below-min-content`() {
        // The predicate that tells the diagnosis from the no-op: only a
        // container offering LESS than the content minimum can trigger §4.5.
        assertTrue(FlexAutoMinSize.squeezed(availableMax = 0, contentMinMain = 80))
        assertTrue(FlexAutoMinSize.squeezed(availableMax = 79, contentMinMain = 80))
        assertFalse(FlexAutoMinSize.squeezed(availableMax = 80, contentMinMain = 80))
        // An unbounded measure cannot squeeze anything by definition.
        assertFalse(
            FlexAutoMinSize.squeezed(
                availableMax = Constraints.Infinity, contentMinMain = 80
            )
        )
    }

    // ── Degenerate inputs ──────────────────────────────────────────────

    @Test
    fun `an unbounded offer keeps an unbounded ceiling`() {
        // Never turn Infinity into a finite max — that would CAP an item
        // that was free to grow (the absposOverflowMeasure path hands
        // exactly these constraints down).
        val (min, max) = FlexAutoMinSize.mainBand(
            availableMax = Constraints.Infinity, contentMinMain = 120
        )
        assertEquals(120, min)
        assertEquals(Constraints.Infinity, max)
    }

    // ── The mechanism ships disarmed (wave-39 hotfix) ──────────────────

    @Test
    fun `the mechanism gate is off until a lane can make it pay`() {
        // Guards the constant itself: both ComponentRenderer call sites read
        // it, and flipping it back on without re-running the two isolation
        // sections would silently re-lose contain-inline-size-flexitem. The
        // measured ledger (0 wins, 1 pass lost, 9 captures lost) is in the
        // constant's banner; this test is the tripwire on it.
        assertFalse(FlexAutoMinSize.MECHANISM_ENABLED)
    }

    // ── The intrinsic channel is OPTIONAL (wave-39 hotfix) ─────────────

    @Test
    fun `a subtree that refuses intrinsics yields no band at all`() {
        // Compose's SubcomposeLayout family (BoxWithConstraints — which is
        // how MultiColumnApplier renders every multicol box — lazy lists,
        // TabRow) installs LayoutNode.NoIntrinsicsMeasurePolicy, whose four
        // intrinsic entry points do nothing but throw IllegalStateException.
        // Unguarded, that throw killed the capture composition: css-multicol
        // went from 45 Android captures at wave38-final to 36 at
        // wave39-final, the nine losses being exactly the flex-wrapped
        // multicol tests (as-column-flex-item, baseline-000…007).
        val refusal = IllegalStateException(
            "Asking for intrinsic measurements of SubcomposeLayout layouts " +
                "is not supported."
        )
        assertNull(FlexAutoMinSize.probeIntrinsic { throw refusal })
    }

    @Test
    fun `a subtree that answers intrinsics still gets its floor`() {
        // The guard must not cost the mechanism anything where the channel
        // exists — the overwhelming majority of flex items.
        assertEquals(80, FlexAutoMinSize.probeIntrinsic { 80 })
    }

    @Test
    fun `a non-refusal failure keeps propagating`() {
        // Narrow by type: Constraints packing raises IllegalArgumentException
        // and a broken measure policy raises anything at all. Swallowing
        // those would hide a real bug behind a silently mis-sized box, which
        // is the opposite of the no-silent-fallthrough rule.
        try {
            FlexAutoMinSize.probeIntrinsic { throw IllegalArgumentException("packing") }
            fail("IllegalArgumentException must not be swallowed by the guard")
        } catch (expected: IllegalArgumentException) {
            assertEquals("packing", expected.message)
        }
    }

    @Test
    fun `a zero or negative intrinsic degrades to the no-op floor`() {
        // A child that reports nothing (or mis-reports) must never produce a
        // negative constraint — Constraints throws on those, which would
        // crash the capture rather than merely mis-size a box.
        assertEquals(0 to 358, FlexAutoMinSize.mainBand(358, 0))
        assertEquals(0 to 358, FlexAutoMinSize.mainBand(358, -5))
        assertFalse(FlexAutoMinSize.squeezed(availableMax = 0, contentMinMain = -5))
    }
}

package com.styleconverter.runtime.layout

import androidx.compose.ui.unit.Constraints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pins [IntrinsicChannel] — the shared guard for Compose's OPTIONAL
 * intrinsic channel and the fixed-band arithmetic behind all four guarded
 * `width/height(IntrinsicSize.Min/Max)` twins (table + sizing call sites).
 *
 * The modifiers themselves need a Compose layout pass (and therefore
 * Robolectric, which this suite deliberately does not use), so the whole
 * decision lives in [IntrinsicChannel.fixedBand] / [IntrinsicChannel.probe]
 * and this file pins that instead — the same "extract the decision, pin it
 * on the JVM" shape as TableBoxTree / FlexAutoMinSize.
 */
class IntrinsicChannelTest {

    // ── fixedBand: IntrinsicSizeModifier's constrain(fixed*(w)) math ───

    @Test
    fun `an answered intrinsic fixes both bounds at the intrinsic`() {
        // The eight css-tables wins' shape: a 200px max-content table on a
        // 358px canvas — §17.5.2's used width is the max-content width, and
        // BOTH bounds pin there so the rows' fillMaxWidth fills the table,
        // not the canvas.
        assertEquals(200 to 200, IntrinsicChannel.fixedBand(200, 0, 358))
    }

    @Test
    fun `the incoming max caps the intrinsic — min of max-content and available`() {
        // §17.5.2's `min(max-content, available)`: a 500px-wide table on a
        // 358px canvas is used at 358 — the exact clamp
        // `constraints.constrain(Constraints.fixedWidth(w))` performs.
        assertEquals(358 to 358, IntrinsicChannel.fixedBand(500, 0, 358))
    }

    @Test
    fun `the incoming min floors the intrinsic`() {
        // enforceIncoming = true keeps a parent-imposed minimum: a 40px
        // table forced to at least 100 measures at 100, exactly as the
        // foundation-layout original coerces.
        assertEquals(100 to 100, IntrinsicChannel.fixedBand(40, 100, 358))
    }

    @Test
    fun `an unbounded incoming max never caps the intrinsic`() {
        // Infinity available ⇒ the used size IS the max-content size; the
        // band must not collapse Infinity into the bounds.
        assertEquals(720 to 720, IntrinsicChannel.fixedBand(720, 0, Constraints.Infinity))
    }

    @Test
    fun `a refused channel keeps the incoming band verbatim`() {
        // The whole point of the guard: no intrinsic ⇒ the box measures
        // with byte-for-byte the constraints it had before the mechanism
        // existed — mis-sized at worst, never a dead capture.
        assertEquals(5 to 358, IntrinsicChannel.fixedBand(null, 5, 358))
    }

    @Test
    fun `a mis-reported negative intrinsic can never produce invalid Constraints`() {
        // A broken child reporting a negative intrinsic must clamp to the
        // incoming min (Constraints rejects negatives outright, which would
        // crash the capture rather than merely mis-size a box).
        assertEquals(0 to 0, IntrinsicChannel.fixedBand(-5, 0, 358))
    }

    // ── probe: the supports-check contract (shared by flex + table) ────

    @Test
    fun `a subtree that refuses intrinsics yields null`() {
        // NoIntrinsicsMeasurePolicy's exact behaviour: IllegalStateException
        // out of all four intrinsic entry points (SubcomposeLayout family —
        // BoxWithConstraints, lazy lists, TabRow; see the banner's bytecode
        // proof). The guard converts the throw into a logged fallback.
        val refusal = IllegalStateException(
            "Asking for intrinsic measurements of SubcomposeLayout layouts " +
                "is not supported."
        )
        assertNull(IntrinsicChannel.probe("Test", "context") { throw refusal })
    }

    @Test
    fun `a subtree that answers intrinsics passes the answer through`() {
        // The guard must cost nothing where the channel exists — every
        // current css-tables capture and the overwhelming majority of boxes.
        assertEquals(80, IntrinsicChannel.probe("Test", "context") { 80 })
    }

    @Test
    fun `a non-refusal failure keeps propagating`() {
        // Narrow by type: Constraints packing raises IllegalArgumentException
        // and a broken measure policy raises anything at all. Swallowing
        // those would hide a real bug behind a silently mis-sized box, which
        // is the opposite of the no-silent-fallthrough rule.
        try {
            IntrinsicChannel.probe("Test", "context") { throw IllegalArgumentException("packing") }
            fail("IllegalArgumentException must not be swallowed by the guard")
        } catch (expected: IllegalArgumentException) {
            assertEquals("packing", expected.message)
        }
    }
}

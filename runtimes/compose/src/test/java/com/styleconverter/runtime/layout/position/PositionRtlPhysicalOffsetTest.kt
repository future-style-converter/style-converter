package com.styleconverter.runtime.layout.position

// Wave 12 — CSS insets are PHYSICAL under RTL (css-position-3 §3.1: `left`
// always measures from the physical LEFT edge; only the inset-inline-*
// logical spellings are direction-aware, and both extractors resolve those
// into physical slots before the applier runs).
//
// Diagnosis (confirmed, pixel-measured): the relative-position branch built
// its displacement with Modifier.offset(x, y), which is LAYOUT-DIRECTION-
// AWARE — under the Direction:RTL provider (ComponentRenderer wraps rtl
// components in LocalLayoutDirection=Rtl) a CSS left:-20px mirrored to
// +20px physical, so the three rtl abspos-autopos containers rendered at
// x56 instead of x16 (+40px total error). The fix routes BOTH position
// appliers through Modifier.absoluteOffset (rtlAware=false), exactly like
// the absolute branch already did.
//
// Pinning strategy (this suite's standing no-device contract): both
// foundation offset factories emit the same OffsetElement class differing
// only in its `rtlAware` flag, so the JVM pin folds the built modifier
// chain and reads that flag (plus the raw x displacement) reflectively —
// rtlAware=false IS "left:-20 stays physical −20 under any direction".

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.layout.InsetRect
import com.styleconverter.runtime.layout.LayoutConfig
import com.styleconverter.runtime.layout.PositionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionRtlPhysicalOffsetTest {

    // ── reflective helpers over the folded modifier chain ────────────────

    /** Every Modifier.Element in the chain, in fold order. */
    private fun elementsOf(m: Modifier): List<Modifier.Element> =
        m.foldIn(mutableListOf<Modifier.Element>()) { acc, e -> acc.apply { add(e) } }

    /** The single foundation OffsetElement the applier emitted. */
    private fun offsetElementOf(m: Modifier): Modifier.Element {
        // Both offset() and absoluteOffset() build the same element class
        // ("OffsetElement" in foundation-layout); the flag tells them apart.
        val hits = elementsOf(m).filter { it.javaClass.simpleName.contains("Offset") }
        assertEquals("exactly one offset element expected", 1, hits.size)
        return hits.first()
    }

    /** Read a named backing field off the element (private in the library). */
    private fun fieldOf(element: Any, name: String): Any? {
        val f = element.javaClass.declaredFields.first { it.name == name }
        // The library element's fields are private — reflection is the
        // device-free window into which factory built it.
        f.isAccessible = true
        return f.get(element)
    }

    /** The element's rtlAware flag: true = offset(), false = absoluteOffset(). */
    private fun rtlAwareOf(element: Any): Boolean = fieldOf(element, "rtlAware") as Boolean

    /** The element's x displacement in dp (Dp is an inline class — the
     *  backing field may surface as either Dp or its raw Float). */
    private fun xDpOf(element: Any): Float = when (val v = fieldOf(element, "x")) {
        is Dp -> v.value
        is Float -> v
        else -> error("unexpected x field shape: $v")
    }

    // ── style-engine applier (PositionLayoutApplier) ─────────────────────

    @Test
    fun `style-engine relative left -20 is physical - not layout-direction-aware`() {
        // The rtl abspos-autopos container's inset: position:relative; left:-20px.
        val m = PositionLayoutApplier.childModifier(
            LayoutConfig(position = PositionKind.Relative, inset = InsetRect(left = -20f))
        )
        val e = offsetElementOf(m)
        // rtlAware=false ⇒ absoluteOffset ⇒ the −20 stays physical −20 under
        // LayoutDirection.Rtl (the fix); rtlAware=true was the +20 mirror bug.
        assertFalse("relative offset must ignore layout direction", rtlAwareOf(e))
        // And the displacement itself is the CSS value verbatim.
        assertEquals(-20f, xDpOf(e), 0.001f)
    }

    @Test
    fun `style-engine absolute branch stays physical too`() {
        // The absolute branch always used absoluteOffset — pinned so the two
        // branches can never diverge again.
        val m = PositionLayoutApplier.childModifier(
            LayoutConfig(position = PositionKind.Absolute, inset = InsetRect(left = 10f))
        )
        assertFalse(rtlAwareOf(offsetElementOf(m)))
    }

    // ── legacy applier (PositionApplier — the live StyleApplier chain) ───

    @Test
    fun `legacy relative left -20 is physical - the live render chain`() {
        // The LIVE modifier chain (StyleApplier → LayoutFacade →
        // PositionApplier.applyPosition) is where the rtl containers were
        // pixel-measured at +40px error — pin its element flag directly.
        val m = PositionApplier.applyPosition(
            Modifier,
            PositionConfig(type = PositionType.RELATIVE, start = (-20).dp)
        )
        val e = offsetElementOf(m)
        assertFalse("legacy applyOffset must ignore layout direction", rtlAwareOf(e))
        // offsetX resolves start (=CSS left) verbatim: physical −20.
        assertEquals(-20f, xDpOf(e), 0.001f)
    }

    @Test
    fun `legacy right-only offset keeps the negative-x convention`() {
        // CSS right:10px = move 10px toward the LEFT (physical) — offsetX
        // returns −10 and the flag keeps it physical under RTL.
        val m = PositionApplier.applyPosition(
            Modifier,
            PositionConfig(type = PositionType.RELATIVE, end = 10.dp)
        )
        val e = offsetElementOf(m)
        assertFalse(rtlAwareOf(e))
        assertEquals(-10f, xDpOf(e), 0.001f)
    }

    @Test
    fun `zero offsets emit no offset element at all`() {
        // The no-movement guard is untouched: nothing to displace, nothing
        // in the chain (byte-identical to the pre-fix fast path).
        val m = PositionApplier.applyPosition(
            Modifier,
            PositionConfig(type = PositionType.RELATIVE)
        )
        assertTrue(elementsOf(m).none { it.javaClass.simpleName.contains("Offset") })
    }
}

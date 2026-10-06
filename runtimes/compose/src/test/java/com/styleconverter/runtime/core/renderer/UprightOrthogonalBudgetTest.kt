package com.styleconverter.runtime.core.renderer

// Wave 52 lane L8 (vertical-wedges, M-C) — the MEASURE-level pin for the
// css-writing-modes-4 §7.3.1 orthogonal-flow fallback budget.
//
// VerticalInlineAxisTest pins the pure budget arithmetic; this file pins that
// `uprightFlowMeasurePolicy` actually CONSUMES it — the step a mutation of the
// policy (dropping `fallbackPx = fallbackBudgetPx`) would silently undo while
// every pure pin stayed green. Plain JVM: MeasurePolicy / MeasureScope /
// Placeable are plain Kotlin, so the measure runs against hand-built fakes
// with no device, the same shape VerticalRunIntrinsicsTest uses.
//
// Verbatim IR: tools/titan/runs/wave51-fix/sections/css-writing-modes/
// per-test-ir/wpt__css-writing-modes__ch-units-vrl-005.json — component
// `wpt__css-writing-modes__ch-units-vrl-005__4-479` (orange div: FontSize
// 20px, WritingMode VERTICAL_RL, TextOrientation UPRIGHT, Width 5ch, text
// "00000"), a root with `height: auto` all the way up, so the measure sees an
// UNBOUNDED height constraint. Ref: one 120×120 orange square (five 24 px
// upright line boxes); wave51-fix Android drew the 55×72 rotated fallback.
//
// MUTATION EXECUTED (2026-10-05, restored byte-exact, sha-256 verified):
//  • VerticalRunIntrinsics.kt `fallbackPx = fallbackBudgetPx` → `fallbackPx =
//    null` → `an unbounded constraint plans against the ICB fallback` fails
//    (declines = 1, size 55×72 instead of 13×120).
//  • FIX PASS C10 (2026-10-05, isolated HEAD export, _mutations-fix.log):
//    VerticalTextFlowLayout.kt `if (!wptCaptureMode) null` → `if (false)
//    null` → `the fallback is WPT-capture-only, like the Swift twin` fails
//    (300.0 ≠ null).

import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.styleconverter.runtime.typography.text.LineStack
import com.styleconverter.runtime.typography.text.VerticalInlineAxis
import org.junit.Assert.assertEquals
import org.junit.Test

class UprightOrthogonalBudgetTest {

    /** Density-1 measure scope: the policy only calls `layout(w, h) {}`. */
    private val scope = object : MeasureScope {
        override val density: Float = 1f
        override val fontScale: Float = 1f
        override val layoutDirection: LayoutDirection = LayoutDirection.Ltr
    }

    /** A placeable of a fixed size; placement is never executed here. */
    private class FakePlaceable(w: Int, h: Int) : Placeable() {
        // `measuredSize` is the protected channel `width`/`height` read.
        init { measuredSize = IntSize(w, h) }
        // No alignment lines on a glyph fake.
        override fun get(alignmentLine: AlignmentLine): Int = AlignmentLine.Unspecified
        // The test asserts the MeasureResult size, never the placement.
        override fun placeAt(position: IntOffset, zIndex: Float, layerBlock: (GraphicsLayerScope.() -> Unit)?) = Unit
    }

    /** A measurable that always measures to [w]×[h], whatever the constraint. */
    private class FakeMeasurable(val w: Int, val h: Int) : Measurable {
        override val parentData: Any? = null
        override fun measure(constraints: Constraints): Placeable = FakePlaceable(w, h)
        // Intrinsics are not consulted by `measure`; answer the fixed size.
        override fun minIntrinsicWidth(height: Int) = w
        override fun maxIntrinsicWidth(height: Int) = w
        override fun minIntrinsicHeight(width: Int) = h
        override fun maxIntrinsicHeight(width: Int) = h
    }

    /** Slot 0 = the rotated fallback as wave51-fix drew it (55×72: "00000"
     *  sideways at 55 px + the 2×4 dp padding), slots 1…5 = upright "0"
     *  glyphs, 13 wide (Inter '0' 12.6 px rounded up by Text) × 24 advance. */
    private fun orangeRun(): List<Measurable> =
        listOf(FakeMeasurable(55, 72)) + List(5) { FakeMeasurable(13, 24) }

    /** The orange div's incoming constraint: its own `width: 5ch` box (120),
     *  an UNBOUNDED height — `height: auto` up the whole chain. */
    private val unboundedHeight = Constraints(minWidth = 0, maxWidth = 120)

    /** Measure "00000" with the given fallback; returns (size, decline count). */
    private fun measure(fallbackBudgetPx: Double?, constraints: Constraints): Pair<IntSize, Int> {
        var declines = 0
        // The production policy, built exactly as VerticalUprightTextFlow does.
        val policy = uprightFlowMeasurePolicy(
            glyphs = listOf("0", "0", "0", "0", "0"),
            stack = LineStack.RIGHT_TO_LEFT,
            fallbackBudgetPx = fallbackBudgetPx,
        ) { declines++ }
        val result = with(policy) { scope.measure(orangeRun(), constraints) }
        return IntSize(result.width, result.height) to declines
    }

    @Test
    fun `an unbounded constraint plans against the ICB fallback`() {
        // §7.3.1: no definite ancestor block size → the 568 px ICB, resolved
        // through the same VerticalInlineAxis call the composable makes.
        val icb = VerticalInlineAxis.orthogonalBudget(null, 568.0)
        val (size, declines) = measure(icb, unboundedHeight)
        // One column of five upright glyphs: 13 wide, 5 × 24 = 120 tall.
        assertEquals(IntSize(13, 120), size)
        assertEquals(0, declines)
    }

    @Test
    fun `without a fallback the unbounded run still declines to the rotated slot`() {
        // Every non-capture path (uprightFallbackBudgetPx → null) — byte-
        // identical to pre-wave-52: the decline hook fires and the rotated
        // fallback's frame wins.
        val (size, declines) = measure(null, unboundedHeight)
        assertEquals(IntSize(55, 72), size)
        assertEquals(1, declines)
    }

    @Test
    fun `a bounded constraint outranks the fallback`() {
        // A definite 48 px available inline size wraps at two glyphs per
        // column even though a 568 px fallback is on offer: 3 columns
        // (2 + 2 + 1) → 39 wide, 48 tall.
        val bounded = Constraints(minWidth = 0, maxWidth = 120, minHeight = 0, maxHeight = 48)
        val (size, declines) = measure(568.0, bounded)
        assertEquals(IntSize(39, 48), size)
        assertEquals(0, declines)
    }

    @Test
    fun `the fallback is WPT-capture-only, like the Swift twin`() {
        // FIX PASS (skeptic should-fix): `LocalContainingBlock` is published
        // on EVERY surface, so the gate is the capture flag. Product (false):
        // even a definite 300 px containing block and a viewport yield no
        // budget → the measure declines exactly as pre-wave-52.
        assertEquals(null, uprightFallbackBudgetPx(false, 300.0, 568.0))
        val (size, declines) = measure(uprightFallbackBudgetPx(false, 300.0, 568.0), unboundedHeight)
        assertEquals(IntSize(55, 72), size)
        assertEquals(1, declines)
        // Composed capture of the verbatim 005 orange div: `height: auto` up
        // the chain (no definite containing block) → the 568 px ICB…
        assertEquals(568.0, uprightFallbackBudgetPx(true, null, 568.0))
        // …and a definite ancestor block size outranks the ICB.
        assertEquals(300.0, uprightFallbackBudgetPx(true, 300.0, 568.0))
    }
}

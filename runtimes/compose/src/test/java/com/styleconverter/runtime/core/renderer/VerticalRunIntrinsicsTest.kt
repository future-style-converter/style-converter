package com.styleconverter.runtime.core.renderer

// Plain-JVM pin for the wave-48 W1 explicit vertical-flow intrinsics —
// the fix for the LargeDimension (32767) poisoning that composed every
// vertical-writing-mode root of css-writing-modes/direction-upright-002
// to 8264 px (measured chain in VerticalRunIntrinsics' banner). The
// MeasurePolicy interfaces are plain Kotlin, so the intrinsic quartet is
// exercised here against hand-built IntrinsicMeasurable fakes with NO
// device — the same "extract the decision, pin it on the JVM" shape as
// FlexAutoMinSize / TableBoxTree.
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import com.styleconverter.runtime.typography.text.LineStack
import org.junit.Assert.assertEquals
import org.junit.Test

class VerticalRunIntrinsicsTest {

    /** Minimal density-only scope — the intrinsic overrides never touch
     *  density, but the receiver type demands one. */
    private val scope = object : IntrinsicMeasureScope {
        override val density: Float = 1f
        override val fontScale: Float = 1f
        override val layoutDirection: LayoutDirection = LayoutDirection.Ltr
    }

    /**
     * An intrinsic fake that RECORDS which query it was asked and with
     * what hint — the whole point of the fix is that the wrapper asks
     * the child the TRANSPOSED question instead of fake-measuring it.
     */
    private class FakeChild(
        val minW: Int = 11, val maxW: Int = 13,
        val minH: Int = 17, val maxH: Int = 19,
    ) : IntrinsicMeasurable {
        var lastQuery: String? = null
        var lastHint: Int? = null
        override val parentData: Any? = null
        override fun minIntrinsicWidth(height: Int): Int {
            lastQuery = "minW"; lastHint = height; return minW
        }
        override fun maxIntrinsicWidth(height: Int): Int {
            lastQuery = "maxW"; lastHint = height; return maxW
        }
        override fun minIntrinsicHeight(width: Int): Int {
            lastQuery = "minH"; lastHint = width; return minH
        }
        override fun maxIntrinsicHeight(width: Int): Int {
            lastQuery = "maxH"; lastHint = width; return maxH
        }
    }

    // ── The rotated run's transposition quartet ────────────────────────

    @Test
    fun rotatedQuartetTransposesQueryAndPassesHintThrough() {
        val policy = rotatedRunMeasurePolicy(90f)
        val child = FakeChild()
        with(policy) {
            // Width queries become the child's HEIGHT queries (the wrapper's
            // width IS the child's height under the swap), hint verbatim.
            assertEquals(17, scope.minIntrinsicWidth(listOf(child), 123))
            assertEquals("minH" to 123, child.lastQuery to child.lastHint)
            assertEquals(19, scope.maxIntrinsicWidth(listOf(child), 456))
            assertEquals("maxH" to 456, child.lastQuery to child.lastHint)
            // Height queries become the child's WIDTH queries.
            assertEquals(11, scope.minIntrinsicHeight(listOf(child), 789))
            assertEquals("minW" to 789, child.lastQuery to child.lastHint)
            assertEquals(13, scope.maxIntrinsicHeight(listOf(child), 42))
            assertEquals("maxW" to 42, child.lastQuery to child.lastHint)
        }
    }

    @Test
    fun rotatedQuartetNeverAnswersTheLargeDimensionSentinel() {
        // The regression shape: before the fix, an unbounded-axis query
        // against a rotated run answered 32767 (LargeDimension). The
        // explicit quartet can only ever answer what the CHILD answers.
        val policy = rotatedRunMeasurePolicy(90f)
        val child = FakeChild(minW = 12, maxW = 12, minH = 20, maxH = 20)
        with(policy) {
            // The exact query the <td> chain asked on device (hint =
            // Infinity is what an unbounded table probe passes down).
            assertEquals(20, scope.maxIntrinsicWidth(listOf(child), Constraints.Infinity))
            assertEquals(12, scope.minIntrinsicHeight(listOf(child), Constraints.Infinity))
        }
    }

    // ── The upright flow's glyph-based intrinsic model ─────────────────

    /** Three glyphs, each 10 wide with a 20 px advance — the "ABC" run. */
    private fun glyphFakes() = List(3) { FakeChild(minW = 10, maxW = 10, minH = 20, maxH = 20) }

    @Test
    fun uprightMaxHeightIsTheUnwrappedColumn() {
        val policy = uprightFlowMeasurePolicy(listOf("A", "B", "C"), LineStack.RIGHT_TO_LEFT) {}
        val fallback = FakeChild()
        with(policy) {
            // max-content inline extent: every advance summed = 3 × 20.
            assertEquals(60, scope.maxIntrinsicHeight(listOf(fallback) + glyphFakes(), 500))
            // min: a column can break after every glyph — one advance.
            assertEquals(20, scope.minIntrinsicHeight(listOf(fallback) + glyphFakes(), 500))
        }
    }

    @Test
    fun uprightWidthPlansColumnsAtTheQueriedBudget() {
        val policy = uprightFlowMeasurePolicy(listOf("A", "B", "C"), LineStack.RIGHT_TO_LEFT) {}
        val fallback = FakeChild()
        with(policy) {
            // Budget 40 fits 2 glyphs per column (advance 20) → 2 columns
            // of width 10 → 20: the planner's own arithmetic, mirrored.
            assertEquals(20, scope.maxIntrinsicWidth(listOf(fallback) + glyphFakes(), 40))
            // Unbounded budget: one unwrapped column → the widest glyph.
            assertEquals(10, scope.maxIntrinsicWidth(listOf(fallback) + glyphFakes(), Constraints.Infinity))
            // min-content cross extent: the widest single glyph, always.
            assertEquals(10, scope.minIntrinsicWidth(listOf(fallback) + glyphFakes(), 40))
        }
    }

    // ── wave-48 S4 coherence pins: the three EXECUTED probe shapes ─────

    @Test
    fun uprightGlyphlessRunAnswersTheFallbackUnswapped() {
        // No glyphs at all: the measure's decline path lays slot 0 out
        // UNSWAPPED (`layout(fallback.width, fallback.height)`), so each
        // intrinsic must relay the fallback's SAME-AXIS answer. The
        // pre-fix elvis arms transposed — answering 107/109/101/103 for
        // this fake — a DOUBLE swap on top of the fallback's own
        // already-transposed rotated quartet (S4 probe, wave 48).
        val policy = uprightFlowMeasurePolicy(emptyList(), LineStack.RIGHT_TO_LEFT) {}
        val fb = FakeChild(minW = 101, maxW = 103, minH = 107, maxH = 109)
        with(policy) {
            // Width queries answer the fallback's WIDTHS, hint verbatim.
            assertEquals(101, scope.minIntrinsicWidth(listOf(fb), 555))
            assertEquals(103, scope.maxIntrinsicWidth(listOf(fb), 555))
            // Height queries answer the fallback's HEIGHTS.
            assertEquals(107, scope.minIntrinsicHeight(listOf(fb), 666))
            assertEquals(109, scope.maxIntrinsicHeight(listOf(fb), 666))
        }
    }

    @Test
    fun uprightAllCollapsibleSpaceRunAnswersTheFallbackUnswapped() {
        // Two collapsible spaces: css-text-3 §4.1.3 collapsing empties
        // every planned column, so uprightColumnIndices declines at
        // EVERY budget and the measure renders the 200×40 fallback —
        // yet the pre-fix intrinsics quoted glyph metrics (maxW=5,
        // maxH=36; S4 probe). The decline predicate is now shared
        // (fallbackOwnsRun runs the planner itself), so the fallback's
        // unswapped answers win on all four arms.
        val policy = uprightFlowMeasurePolicy(listOf(" ", " "), LineStack.RIGHT_TO_LEFT) {}
        val fb = FakeChild(minW = 200, maxW = 200, minH = 40, maxH = 40)
        // Space glyph slots: 5 px wide, 18 px advance — real metrics a
        // measured space carries, none of which may leak into answers.
        val spaces = List(2) { FakeChild(minW = 5, maxW = 5, minH = 18, maxH = 18) }
        with(policy) {
            assertEquals(200, scope.minIntrinsicWidth(listOf(fb) + spaces, 100))
            assertEquals(200, scope.maxIntrinsicWidth(listOf(fb) + spaces, 100))
            assertEquals(40, scope.minIntrinsicHeight(listOf(fb) + spaces, 100))
            assertEquals(40, scope.maxIntrinsicHeight(listOf(fb) + spaces, 100))
        }
    }

    @Test
    fun uprightMaxWidthUsesTheMeasuresFirstGlyphAdvance() {
        // Heterogeneous advances (the font-fallback shape): 20 px and
        // 30 px glyphs, both 10 px wide, budget 50. The measure reads
        // its ONE advance off the FIRST glyph (20) → capacity 2 → plan
        // [[0, 1]] → one 10-px column. The pre-fix intrinsic read the
        // MAX advance (30) → capacity 1 → plan [[0], [1]] → 20 px,
        // disagreeing with the layout that actually renders (S4 probe:
        // "upright advance drift @budget=50"). Pinned: FIRST, like the
        // measure.
        val policy = uprightFlowMeasurePolicy(listOf("A", "厂"), LineStack.RIGHT_TO_LEFT) {}
        val fb = FakeChild()
        val glyphs = listOf(
            FakeChild(minW = 10, maxW = 10, minH = 20, maxH = 20),
            FakeChild(minW = 10, maxW = 10, minH = 30, maxH = 30),
        )
        with(policy) {
            assertEquals(10, scope.maxIntrinsicWidth(listOf(fb) + glyphs, 50))
        }
    }
}

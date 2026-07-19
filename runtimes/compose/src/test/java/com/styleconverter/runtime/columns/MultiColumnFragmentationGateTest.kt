package com.styleconverter.runtime.columns

// Wave 11 — the multicol fragmentation GATE actually firing through
// MultiColumnLayout (the wave-10 integration gap).
//
// Diagnosis (confirmed): MultiColumnLayout wraps its content in
// BoxWithConstraints, whose Box measure policy (propagateMinConstraints =
// false) LOOSENS minHeight to 0 before MultiColumnDistributionLayout
// measures. The wave-10 gate read `constraints.hasFixedHeight` INSIDE that
// layout — min != max there on every real path, so the css-break-3 branch
// NEVER engaged in the render tree; its unit tests passed only because they
// handed the inner layout synthetic TIGHT constraints directly.
//
// The fix threads `containerBlockSizeDefinite` — this.constraints
// .hasFixedHeight read at the BoxWithConstraintsScope boundary, where the
// incoming constraints are STILL tight — down to the gate. This suite is the
// integration-SHAPED pin feasible without Robolectric (the suite's standing
// no-device contract, same as FragmentGeometryTest's draw-wiring scan): it
// models the EXACT constraint transformation the Box performs between the
// two read points, drives the same pure gate function both composables call,
// and source-scans the threading so the wiring cannot silently regress.

import androidx.compose.ui.unit.Constraints
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiColumnFragmentationGateTest {

    // The fixture geometry: a multicol container whose style chain declared
    // height:400px — Modifier.height() hands BoxWithConstraints TIGHT
    // vertical constraints (min == max == 400).
    private val outerTight = Constraints.fixed(300, 400)

    // What MultiColumnDistributionLayout actually receives in the real tree:
    // the Box between the boundary and the Layout zeroes the mins
    // (propagateMinConstraints = false) while PRESERVING the maxes.
    private val innerLoosened = Constraints(
        minWidth = 0, maxWidth = 300, minHeight = 0, maxHeight = 400
    )

    // ── 1. The integration premise: the Box really destroys the signal ──────

    @Test
    fun `loosened inner constraints no longer read as fixed-height`() {
        // The wave-10 bug's premise, pinned: the boundary sees tight height,
        // the inner layout does not — reading hasFixedHeight inside the
        // Layout alone is structurally dead in the real render tree.
        assertTrue("boundary constraints must be tight", outerTight.hasFixedHeight)
        assertFalse("Box-loosened constraints lose min==max", innerLoosened.hasFixedHeight)
        // The max survives the loosening — that's why threading ONLY the
        // definiteness boolean (not the size) is sufficient.
        assertEquals(400, innerLoosened.maxHeight)
    }

    // ── 2. The gate through the threaded signal (the fix) ───────────────────

    @Test
    fun `threaded boundary signal engages the gate under loosened constraints`() {
        // MultiColumnLayout computes containerBlockSizeDefinite from the
        // OUTER constraints and threads it; the gate then yields the
        // fragmentainer H from the surviving max — the branch that renders
        // the css-break WPT family fragments on device.
        val boundarySignal = outerTight.hasFixedHeight
        assertEquals(
            400,
            MultiColumnApplier.fragmentainerBlockSizePx(boundarySignal, innerLoosened))
    }

    @Test
    fun `without the thread the gate stays dead - the wave-10 gap pinned`() {
        // Regression sentinel: the pre-fix read (no boundary signal, only the
        // loosened inner constraints) must still evaluate to "no
        // fragmentainer" — proving the thread is the load-bearing part, not
        // an incidental refactor.
        assertNull(MultiColumnApplier.fragmentainerBlockSizePx(false, innerLoosened))
    }

    @Test
    fun `direct tight constraints still engage without the thread`() {
        // The wave-10 unit-test path (synthetic tight constraints straight
        // into the distribution layout — MasonryLayout-style direct callers)
        // keeps working via the OR: no behaviour retired.
        assertEquals(
            400,
            MultiColumnApplier.fragmentainerBlockSizePx(false, outerTight))
    }

    @Test
    fun `auto and unbounded block-sizes never produce a fragmentainer`() {
        // css-break-3 §2: no definite block-size → no fragmentainer, even
        // with the boundary signal asserted (belt-and-braces: an Infinity max
        // means the container grows instead of fragmenting)…
        val unbounded = Constraints(minWidth = 0, maxWidth = 300, minHeight = 0, maxHeight = Constraints.Infinity)
        assertNull(MultiColumnApplier.fragmentainerBlockSizePx(true, unbounded))
        // …and a zero-height container has no visible fragments.
        assertNull(MultiColumnApplier.fragmentainerBlockSizePx(true, Constraints.fixed(300, 0)))
    }

    // ── 3. Source scan: the thread is wired through the REAL composables ────
    //
    // Feasible-without-Robolectric wiring pin (FragmentGeometryTest
    // precedent): the boundary read, the parameter hand-off at every hop,
    // and the gate consuming the shared pure function must all be present in
    // MultiColumnLayout's actual source — this is what makes the suite
    // "through MultiColumnLayout" rather than inner-layout-only.

    /** The applier source, located by walking up from the test working dir. */
    private val applierSource: String by lazy {
        val rel = "runtimes/compose/src/main/java/com/styleconverter/runtime/columns/MultiColumnApplier.kt"
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, rel).exists()) dir = dir.parentFile
        File(requireNotNull(dir) { "repo root ($rel) not found above ${System.getProperty("user.dir")}" }, rel)
            .readText()
    }

    @Test
    fun `MultiColumnLayout reads definiteness at the BoxWithConstraints boundary`() {
        // The read must happen on the SCOPE's incoming constraints (still
        // tight), not inside the distribution layout.
        assertTrue(
            "boundary-level hasFixedHeight read missing",
            applierSource.contains("val containerBlockSizeDefinite = this.constraints.hasFixedHeight"))
    }

    @Test
    fun `the signal is threaded through every hop to the gate`() {
        // Both intermediate composables must hand the flag down — four
        // hand-offs total: MultiColumnLayout→WithRules, MultiColumnLayout→
        // Simple, WithRules→DistributionLayout, Simple→DistributionLayout.
        assertEquals(
            "containerBlockSizeDefinite must be passed at each hop",
            4,
            Regex("containerBlockSizeDefinite = containerBlockSizeDefinite")
                .findAll(applierSource).count())
        // …and the gate must consume the shared pure decision, not a local
        // hasFixedHeight re-read.
        assertTrue(
            "gate must consume fragmentainerBlockSizePx",
            applierSource.contains(
                "fragmentainerBlockSizePx(containerBlockSizeDefinite, constraints) != null"))
    }
}

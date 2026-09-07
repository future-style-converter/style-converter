package com.styleconverter.runtime.columns

// Plain-JVM JUnit4 — same suite style as FragmentGeometryTest.
import com.styleconverter.runtime.columns.FragmentGeometry.Fragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The SHARED multi-child run pin table (wave-42 lane W4) — pins
 * [MulticolRunFragment] exactly; the iOS twin
 * (runtimes/swiftui .../MulticolRunFragmentTests.swift) carries the SAME
 * R rows byte-for-byte (native-pair parity gate).
 *
 * The run model: a definite-height `column-fill: auto` multicol container's
 * plain flow children stack as ONE continuous strip (CSS 2.1 §9.4.1) and
 * the strip fills columns sequentially to H (css-multicol-1 §7.1) — the
 * same clip+translate replay as the wave-10 sole-child slice, with C = T.
 * The ENGAGEMENT gates (fill:auto only, no floats/statics/spanners/forced
 * breaks, capture only) live in MulticolRunFragmentMeasure and keep every
 * wave-41 green multi-child cell (css-break block-in-inline-009/013/014,
 * css-multicol baseline-001/002 — all fill:balance) on the greedy path.
 */
class MulticolRunFragmentTest {

    /** R1 — a run that fits one column: sequential fill, no slices. */
    @Test
    fun `R1 run fitting the column stacks in column 0 with no fragments`() {
        val plan = MulticolRunFragment.runPlan(listOf(30, 40), 100, 80, 10, 3)
        // Children stack at cumulative offsets (block flow §9.4.1)…
        assertEquals(listOf(0, 30), plan.yOffsetsPx)
        assertEquals(70, plan.totalBlockSizePx)
        // …and an empty fragment list keeps the plain drawContent identity.
        assertTrue(plan.fragments.isEmpty())
    }

    /** R2 — the floats-clear-000 strip shape (were floats real): T=253 over 3×100 columns. */
    @Test
    fun `R2 overflowing run slices exactly like a sole child of height T`() {
        val plan = MulticolRunFragment.runPlan(listOf(250, 3), 100, 100, 0, 3)
        assertEquals(listOf(0, 250), plan.yOffsetsPx)
        assertEquals(253, plan.totalBlockSizePx)
        // The slices ARE the wave-10 S-table with C = T — same geometry,
        // same N cap (the overflow clip).
        assertEquals(
            listOf(
                Fragment(0, 0, 0, 100, 100, 0, 0),
                Fragment(1, 100, 0, 100, 100, 100, -100),
                Fragment(2, 200, 0, 100, 100, 200, -200)
            ),
            plan.fragments
        )
        assertEquals(
            FragmentGeometry.fragmentGeometry(253, 100, 100, 0, 3),
            plan.fragments
        )
    }

    /** R3 — negative measured heights are impossible boxes: floored at 0. */
    @Test
    fun `R3 negative child heights floor to zero in the strip`() {
        val plan = MulticolRunFragment.runPlan(listOf(-5, 60), 100, 80, 0, 2)
        // The negative child occupies nothing; its sibling starts at 0.
        assertEquals(listOf(0, 0), plan.yOffsetsPx)
        assertEquals(60, plan.totalBlockSizePx)
        assertTrue(plan.fragments.isEmpty())
    }

    /** R4 — a degenerate (non-positive) column block-size never slices. */
    @Test
    fun `R4 zero column block-size produces no fragments`() {
        // The caller's definite-height gate excludes H ≤ 0, but the pure
        // module must not divide by it either — guard pinned.
        assertTrue(MulticolRunFragment.runPlan(listOf(50), 0, 80, 0, 2).fragments.isEmpty())
    }

    /**
     * R5 — the MONOLITHIC push (css-break-3 §4.1): an empty box has no
     * class-A/B breakpoint inside it, so a column boundary may not pass
     * through it. Pinned against WPT css-break avoid-border-break —
     * `columns:2; column-fill:auto; height:200px` over a 175px spacer and
     * a 100px box with a 30px green border: unpushed the boundary at 200
     * would cut 25px into that border, and the reference (a filled green
     * 100px square, the container being shifted so only column 1 is
     * on-screen) needs the box whole in column 1.
     */
    @Test
    fun `R5 monolithic child is pushed whole into the next column`() {
        val plan = MulticolRunFragment.runPlan(
            listOf(175, 100), 200, 100, 0, 2,
            // The spacer is empty too, but it starts flush at 0 and never
            // straddles — only the second child moves.
            monolithic = listOf(true, true)
        )
        assertEquals(listOf(0, 200), plan.yOffsetsPx)
        assertEquals(300, plan.totalBlockSizePx)
        // Two columns: [0,200) holds the spacer, [200,300) the whole box.
        assertEquals(FragmentGeometry.fragmentGeometry(300, 200, 100, 0, 2), plan.fragments)
    }

    /**
     * R6 — a BREAKABLE child in the same shape is NOT pushed (it splits at
     * the boundary), and a monolithic child TALLER than the column still
     * splits once it starts flush at a boundary. Pinned against WPT
     * css-break borders-001: `[95px spacer][120px bordered box]`, H=100 —
     * the box is pushed to column 1 and then breaks, putting its top
     * border in column 1 and its bottom border in column 2, which is
     * exactly what that test's reference prose asks for.
     */
    @Test
    fun `R6 breakable children split while an over-tall monolith breaks after its push`() {
        // Breakable: no push, the strip stays flush-stacked.
        val breakable = MulticolRunFragment.runPlan(
            listOf(175, 100), 200, 100, 0, 2, monolithic = listOf(false, false)
        )
        assertEquals(listOf(0, 175), breakable.yOffsetsPx)
        assertEquals(275, breakable.totalBlockSizePx)
        // borders-001: the 120px box is pushed to 100, then spans 100..220
        // — it starts flush now, so the boundary at 200 legitimately
        // splits it (a box taller than H fits nowhere, css-break-3 §4.4).
        val overTall = MulticolRunFragment.runPlan(
            listOf(95, 120), 100, 100, 10, 4, monolithic = listOf(true, true)
        )
        assertEquals(listOf(0, 100), overTall.yOffsetsPx)
        assertEquals(220, overTall.totalBlockSizePx)
        // Three columns of the 220px strip: 0-100, 100-200, 200-220.
        assertEquals(3, overTall.fragments.size)
    }
}

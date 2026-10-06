package com.styleconverter.runtime.typography.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave 52 lane L8 (vertical-wedges) — pins for the pure inline-axis
 * decisions in [VerticalInlineAxis]. Every case has a Swift twin of the same
 * name in `runtimes/swiftui/Tests/StyleConverterRuntimeTests/
 * VerticalInlineAxisTests.swift`; the two files are the contract that keeps
 * the natives from disagreeing about which ch boxes measure the vertical
 * advance or what budget an orthogonal run wraps against.
 *
 * MUTATIONS EXECUTED (2026-09-25; RE-EXECUTED 2026-10-05 in the isolated HEAD
 * export — tools/titan/results/wave52-vertical-wedges/_mutations-compose.log
 * C3/C4 — each restored byte-exact, sha-256 verified):
 *  • `chAdvanceIsVertical` with the `textOrientation == UPRIGHT` clause
 *    dropped → `ch measures the vertical advance only under vertical-* +
 *    upright` fails on the MIXED assertion.
 *  • `orthogonalBudget` returning null instead of the ICB → `orthogonal
 *    budget …` fails on `(null, 568) == 568`, and `the ch-units-vrl-005
 *    orange run …` fails on its non-null plan.
 */
class VerticalInlineAxisTest {

    @Test
    fun `ch measures the vertical advance only under vertical-star + upright`() {
        // css-values-4 §6.1.1 × css-writing-modes-4 §5.1: the two vertical
        // modes with an explicit `upright` stand the '0' up.
        assertTrue(VerticalInlineAxis.chAdvanceIsVertical(WritingModeValue.VERTICAL_RL, TextOrientationValue.UPRIGHT))
        assertTrue(VerticalInlineAxis.chAdvanceIsVertical(WritingModeValue.VERTICAL_LR, TextOrientationValue.UPRIGHT))
        // `mixed` rotates the '0' (Vertical_Orientation R) — x-advance.
        assertFalse(VerticalInlineAxis.chAdvanceIsVertical(WritingModeValue.VERTICAL_RL, TextOrientationValue.MIXED))
        // `sideways` rotates everything — x-advance.
        assertFalse(VerticalInlineAxis.chAdvanceIsVertical(WritingModeValue.VERTICAL_RL, TextOrientationValue.SIDEWAYS))
        // The sideways-* modes ignore text-orientation altogether.
        assertFalse(VerticalInlineAxis.chAdvanceIsVertical(WritingModeValue.SIDEWAYS_RL, TextOrientationValue.UPRIGHT))
        assertFalse(VerticalInlineAxis.chAdvanceIsVertical(WritingModeValue.SIDEWAYS_LR, TextOrientationValue.UPRIGHT))
        // A horizontal mode's inline axis is horizontal by definition.
        assertFalse(VerticalInlineAxis.chAdvanceIsVertical(WritingModeValue.HORIZONTAL_TB, TextOrientationValue.UPRIGHT))
    }

    @Test
    fun `orthogonal budget - a definite ancestor wins, else the ICB, else null`() {
        // css-writing-modes-4 §7.3.1 at the composed capture's 568 px ICB.
        assertEquals(568.0, VerticalInlineAxis.orthogonalBudget(null, 568.0))
        // A definite ancestor block size (a 16 px parent) outranks the ICB.
        assertEquals(16.0, VerticalInlineAxis.orthogonalBudget(16.0, 568.0))
        // Nothing published (the dark stage) → null → the historical decline.
        assertNull(VerticalInlineAxis.orthogonalBudget(null, null))
        // Non-positive / non-finite readings are "not definite", not budgets.
        assertNull(VerticalInlineAxis.orthogonalBudget(0.0, null))
        assertEquals(568.0, VerticalInlineAxis.orthogonalBudget(Double.NaN, 568.0))
        assertNull(VerticalInlineAxis.orthogonalBudget(-1.0, Double.POSITIVE_INFINITY))
    }

    @Test
    fun `upright budget - a bounded constraint beats the fallback`() {
        // A definite available inline size is the budget, fallback or not.
        assertEquals(40.0, VerticalInlineAxis.uprightBudget(40.0, 568.0))
        // Indefinite → the §7.3.1 fallback.
        assertEquals(568.0, VerticalInlineAxis.uprightBudget(null, 568.0))
        // Indefinite and no fallback → null → decline, as before wave 52.
        assertNull(VerticalInlineAxis.uprightBudget(null, null))
    }

    @Test
    fun `the ch-units-vrl-005 orange run plans one column of five at the ICB budget`() {
        // The orange `width: 5ch` upright `00000` of wave51-fix css-writing-
        // modes/ch-units-vrl-005: 24 px upright advance at 20 px Inter, an
        // unbounded height constraint, the 568 px ICB published → one
        // column of five glyphs (the ref's 120×120 square).
        val glyphs = VerticalTextFlow.codePointsOf("00000")
        val budget = VerticalInlineAxis.uprightBudget(null, VerticalInlineAxis.orthogonalBudget(null, 568.0))
        assertEquals(listOf(listOf(0, 1, 2, 3, 4)), VerticalTextFlow.uprightColumnIndices(glyphs, 24.0, budget))
        // Without a published ICB the planner still declines — the pre-wave-52
        // picture (the rotated run) on every non-composed path.
        val none = VerticalInlineAxis.uprightBudget(null, VerticalInlineAxis.orthogonalBudget(null, null))
        assertNull(VerticalTextFlow.uprightColumnIndices(glyphs, 24.0, none))
    }
}

package com.styleconverter.runtime.background

// PIN TABLE — cross-fade() weight normalization (css-images-4 §2.6.2).
// The SAME rows exist in the byte-parallel twins:
//   runtimes/web/tests/background/BackgroundImageCrossFadeCenter.test.ts
//   runtimes/swiftui/Tests/StyleConverterRuntimeTests/CrossFadeMathTests.swift
// Any edit here must be mirrored in both — the three engines must share
// one normalization or their composited alphas drift apart.

import org.junit.Assert.assertEquals
import org.junit.Test

class CrossFadeMathTest {

    // Exact-equality helper: the math is pure arithmetic on doubles, so
    // the twins can pin identical outputs without epsilon fuzz.
    private fun check(input: List<Double?>, expected: List<Double>) {
        assertEquals(expected, CrossFadeMath.normalizeWeights(input))
    }

    @Test
    fun `six 10 percent layers keep 0_10 each (target-alpha total 0_6)`() {
        // cross-fade-target-alpha.html: sum 60 ≤ 100, none omitted — the
        // 40% remainder renders transparent (total alpha 0.6).
        check(List(6) { 10.0 }, List(6) { 0.10 })
    }

    @Test
    fun `two omitted weights split evenly (premultiplied-alpha 50-50)`() {
        // cross-fade-premultiplied-alpha.html: no percentages authored.
        check(listOf(null, null), listOf(0.5, 0.5))
    }

    @Test
    fun `legacy 25 percent pair resolves 75-25`() {
        // Older two-arg syntax: the parser stores both weights explicitly.
        check(listOf(75.0, 25.0), listOf(0.75, 0.25))
    }

    @Test
    fun `over-100 sums scale down proportionally`() {
        // §2.6.2 rule: 150+50=200 → ×(100/200) → 75/25.
        check(listOf(150.0, 50.0), listOf(0.75, 0.25))
    }

    @Test
    fun `omitted weights share the floored remainder`() {
        // 60 specified + 2 omitted → remainder 40 → 20 each.
        check(listOf(60.0, null, null), listOf(0.6, 0.2, 0.2))
    }

    @Test
    fun `specified over 100 with omissions gives omitted images zero`() {
        // Remainder floors at 0 (§2.6.2); the specified entry scales to 1.
        check(listOf(120.0, null), listOf(1.0, 0.0))
    }
}

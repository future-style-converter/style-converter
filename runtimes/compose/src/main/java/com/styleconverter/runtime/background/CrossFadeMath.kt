package com.styleconverter.runtime.background

// CrossFadeMath.kt — cross-fade() weight normalization (css-images-4
// §2.6.2). BYTE-PARALLEL TWIN of:
//   runtimes/web/src/engine/background/CrossFadeMath.ts
//   runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/background/CrossFadeMath.swift
// The three implementations must keep IDENTICAL math and pin tables — the
// IR ships AUTHORED weights (null = omitted) and every platform runs this
// same normalization so a weight-rounding divergence can never split the
// three renderings.
//
// Spec rules (css-images-4 §2.6.2):
//  1. Sum the specified percentages; if the sum exceeds 100%, scale every
//     specified percentage proportionally so the sum is exactly 100%.
//  2. If any percentages were OMITTED: subtract the specified sum from
//     100%, floor at 0%, and divide the result equally between all images
//     with omitted percentages.
//  3. If the final sum is below 100% the remainder renders as TRANSPARENT
//     (the target-alpha WPT: six 10% layers → total alpha 0.6) — engines
//     get this for free because the weights simply sum below 1.
object CrossFadeMath {

    /**
     * Normalize authored weights (percent domain, null = omitted) to
     * effective per-image FRACTIONS (0..1). Input order is preserved.
     */
    fun normalizeWeights(weights: List<Double?>): List<Double> {
        // Sum of the author-specified percentages (rule 1 input).
        val specified = weights.sumOf { it ?: 0.0 }
        // Count of omitted entries (rule 2 input).
        val missing = weights.count { it == null }
        // Rule 2: equal share of the floored-at-zero remainder.
        val fill = if (missing > 0) maxOf(0.0, 100.0 - specified) / missing else 0.0
        // Rule 1: >100% totals scale down proportionally. Omitted entries
        // take their fill AFTER scaling per the spec order (scaling applies
        // to the *specified* percentages; with any omission the remainder
        // is already 0 when specified ≥ 100, so fill = 0 either way).
        val scale = if (specified > 100.0) 100.0 / specified else 1.0
        // Apply: specified → scaled; omitted → equal fill. Percent → fraction.
        return weights.map { (if (it == null) fill else it * scale) / 100.0 }
    }
}

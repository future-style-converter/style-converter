package com.styleconverter.test.style.math

// Phase 10 facade — MathTypographyExtractor handles math-depth / math-
// shift / math-style for MathML-rendering contexts. Compose has no
// MathML renderer, so the applier is a no-op on Android mobile.
//
// Parser-gap note:
//   * MathDepth: `auto`, `auto-add`, `add(N)`, integer, Raw fallback,
//     global keywords.

import com.styleconverter.test.style.PropertyRegistry

/** Registers 3 math-typography IR properties under the `math` owner. */
object MathRegistration {

    init {
        PropertyRegistry.migrated(
            "MathDepth", "MathShift", "MathStyle",
            owner = "math"
        )
    }
}

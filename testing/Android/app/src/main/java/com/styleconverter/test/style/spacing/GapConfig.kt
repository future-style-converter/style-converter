package com.styleconverter.test.style.spacing

// GapConfig — container-level row-gap and column-gap. CSS shorthand `gap: N`
// expands to `row-gap: N; column-gap: N` (no standalone Gap property reaches
// us — the converter always emits both longhands). See
// examples/properties/spacing/gap.json for fixture coverage.
//
// IR shapes (DIFFERENT from padding/margin — all tagged):
//   {"type": "length", "px": 10}                 → Exact 10px
//   {"type": "length", "original": {"v":1,"u":"EM"}}  → Relative EM
//   {"type": "percentage", "value": 5}           → Relative PERCENT

import com.styleconverter.test.style.core.types.LengthValue

/**
 * Row / column gap for a flex or grid container. Null = unspecified,
 * renderer falls back to 0 (the CSS initial value).
 */
data class GapConfig(
    val rowGap: LengthValue? = null,
    val columnGap: LengthValue? = null,
) {
    val hasGap: Boolean
        get() = rowGap != null || columnGap != null
}

package com.styleconverter.test.style.spacing

// MarginTrimConfig — CSS `margin-trim` controls which children have their
// adjoining margins trimmed by the container. Compose has no equivalent
// primitive; the Applier is a documented no-op for Phase 2. We still model
// the config so PropertyTracker sees the property as "implemented stub".
//
// Fixture: examples/properties/spacing/margin-trim.json (7 components).
// Note (fixture spec): the combined form "block inline" is NOT parsed by the
// upstream CSS parser today. We therefore only model the seven keyword
// variants the parser actually emits.

/** Subset of CSS margin-trim keywords the IR parser supports. */
enum class MarginTrimKeyword {
    /** No trimming — CSS initial value. */
    NONE,

    /** Trim margins in the block axis (both ends). */
    BLOCK,

    /** Trim margins in the inline axis (both ends). */
    INLINE,

    /** Trim the block-start margin of the first child. */
    BLOCK_START,

    /** Trim the block-end margin of the last child. */
    BLOCK_END,

    /** Trim the inline-start margin. */
    INLINE_START,

    /** Trim the inline-end margin. */
    INLINE_END,
}

/**
 * Container-level margin-trim configuration. One active keyword per
 * container (the IR never emits combined forms today). Default = NONE, i.e.
 * no trimming, matching the CSS initial value.
 */
data class MarginTrimConfig(
    val value: MarginTrimKeyword = MarginTrimKeyword.NONE,
) {
    /** True when the config does any trimming at all. */
    val hasMarginTrim: Boolean
        get() = value != MarginTrimKeyword.NONE
}

package com.styleconverter.test.style.regions

/**
 * Region fragment value options.
 */
enum class RegionFragmentValue {
    AUTO,
    BREAK
}

/**
 * CSS continue property values.
 * Controls whether content continues after a break.
 */
enum class ContinueValue {
    AUTO,
    DISCARD
}

/**
 * CSS bookmark-state property values.
 * For PDF bookmark generation in paged media.
 */
enum class BookmarkStateValue {
    OPEN,
    CLOSED
}

/**
 * CSS leader() function type values.
 * For table of contents leader patterns.
 */
enum class LeaderType {
    DOTTED,
    SOLID,
    SPACE
}

/**
 * CSS footnote-display property values.
 * Controls footnote rendering behavior.
 */
enum class FootnoteDisplayValue {
    BLOCK,
    INLINE,
    COMPACT
}

/**
 * CSS footnote-policy property values.
 * Controls footnote placement policy.
 */
enum class FootnotePolicyValue {
    AUTO,
    LINE,
    BLOCK
}

/**
 * Configuration for CSS Regions properties.
 * Used for flowing content between named regions.
 */
data class RegionFlowConfig(
    val flowFrom: String? = null,
    val flowInto: String? = null,
    val regionFragment: RegionFragmentValue = RegionFragmentValue.AUTO,
    val continueValue: ContinueValue = ContinueValue.AUTO,
    val bookmarkLevel: Int? = null,
    val bookmarkLabel: String? = null,
    val bookmarkState: BookmarkStateValue = BookmarkStateValue.OPEN,
    val footnoteDisplay: FootnoteDisplayValue = FootnoteDisplayValue.BLOCK,
    val footnotePolicy: FootnotePolicyValue = FootnotePolicyValue.AUTO,
    val leaderType: LeaderType? = null
) {
    val hasRegionFlow: Boolean
        get() = flowFrom != null ||
                flowInto != null ||
                regionFragment != RegionFragmentValue.AUTO ||
                continueValue != ContinueValue.AUTO ||
                bookmarkLevel != null ||
                leaderType != null
}

package com.styleconverter.test.style.scrolling

import androidx.compose.ui.unit.Dp

/**
 * Comprehensive configuration for scroll-related CSS properties.
 *
 * ## Supported Properties
 * - ScrollBehavior: smooth, auto
 * - ScrollSnapType: axis + strictness
 * - ScrollSnapAlign: start, center, end, none
 * - ScrollSnapStop: normal, always
 * - ScrollMargin: physical and logical margins
 * - ScrollPadding: physical and logical padding
 * - OverscrollBehavior: auto, contain, none
 * - ScrollbarWidth: auto, thin, none
 * - ScrollbarColor: thumb and track colors
 * - ScrollbarGutter: stable, always
 *
 * ## Usage
 * ```kotlin
 * val config = ScrollExtractor.extractScrollConfig(properties)
 * // Use config to configure scroll behavior in Compose
 * ```
 */
data class ScrollConfig(
    /** Scroll behavior (smooth vs instant) */
    val behavior: ScrollBehaviorMode = ScrollBehaviorMode.AUTO,
    /** Scroll snap type configuration */
    val snapType: ScrollSnapType? = null,
    /** Scroll snap alignment */
    val snapAlign: ScrollSnapAlign = ScrollSnapAlign(),
    /** Scroll snap stop mode */
    val snapStop: ScrollSnapStopMode = ScrollSnapStopMode.NORMAL,
    /** Scroll margins for snap area */
    val scrollMargin: ScrollMargin = ScrollMargin(),
    /** Scroll padding for snap container */
    val scrollPadding: ScrollPadding = ScrollPadding(),
    /** Overscroll behavior */
    val overscroll: OverscrollConfig = OverscrollConfig(),
    /** Scrollbar styling */
    val scrollbar: ScrollbarConfig = ScrollbarConfig()
) {
    val hasScrollConfig: Boolean
        get() = behavior != ScrollBehaviorMode.AUTO ||
                snapType != null ||
                scrollMargin.hasMargin ||
                scrollPadding.hasPadding ||
                overscroll.hasOverscroll ||
                scrollbar.hasScrollbar

    val hasSnapping: Boolean
        get() = snapType != null && snapType.enabled
}

/**
 * Scroll behavior mode.
 */
enum class ScrollBehaviorMode {
    /** Instant scrolling (default) */
    AUTO,
    /** Smooth animated scrolling */
    SMOOTH
}

/**
 * Scroll snap type configuration.
 */
data class ScrollSnapType(
    val axis: ScrollSnapAxis,
    val strictness: ScrollSnapStrictness
) {
    val enabled: Boolean = true
}

/**
 * Scroll snap axis values.
 */
enum class ScrollSnapAxis {
    X, Y, BLOCK, INLINE, BOTH
}

/**
 * Scroll snap strictness values.
 */
enum class ScrollSnapStrictness {
    /** Must snap to snap point */
    MANDATORY,
    /** Snap if close enough */
    PROXIMITY
}

/**
 * Scroll snap alignment.
 */
data class ScrollSnapAlign(
    val blockAxis: ScrollSnapAlignValue = ScrollSnapAlignValue.NONE,
    val inlineAxis: ScrollSnapAlignValue = ScrollSnapAlignValue.NONE
)

/**
 * Individual scroll snap alignment value.
 */
enum class ScrollSnapAlignValue {
    NONE, START, CENTER, END
}

/**
 * Scroll snap stop mode.
 */
enum class ScrollSnapStopMode {
    /** May pass over snap points */
    NORMAL,
    /** Must stop on first snap point */
    ALWAYS
}

/**
 * Scroll margin configuration.
 */
data class ScrollMargin(
    val top: Dp? = null,
    val right: Dp? = null,
    val bottom: Dp? = null,
    val left: Dp? = null,
    val blockStart: Dp? = null,
    val blockEnd: Dp? = null,
    val inlineStart: Dp? = null,
    val inlineEnd: Dp? = null
) {
    val hasMargin: Boolean
        get() = top != null || right != null || bottom != null || left != null ||
                blockStart != null || blockEnd != null || inlineStart != null || inlineEnd != null
}

/**
 * Scroll padding configuration.
 */
data class ScrollPadding(
    val top: Dp? = null,
    val right: Dp? = null,
    val bottom: Dp? = null,
    val left: Dp? = null,
    val blockStart: Dp? = null,
    val blockEnd: Dp? = null,
    val inlineStart: Dp? = null,
    val inlineEnd: Dp? = null
) {
    val hasPadding: Boolean
        get() = top != null || right != null || bottom != null || left != null ||
                blockStart != null || blockEnd != null || inlineStart != null || inlineEnd != null
}

/**
 * Overscroll behavior configuration.
 */
data class OverscrollConfig(
    val x: OverscrollBehaviorMode = OverscrollBehaviorMode.AUTO,
    val y: OverscrollBehaviorMode = OverscrollBehaviorMode.AUTO
) {
    val hasOverscroll: Boolean
        get() = x != OverscrollBehaviorMode.AUTO || y != OverscrollBehaviorMode.AUTO
}

/**
 * Overscroll behavior mode.
 */
enum class OverscrollBehaviorMode {
    /** Default browser behavior (may chain to parent) */
    AUTO,
    /** Prevent scroll chaining, local overscroll effect only */
    CONTAIN,
    /** No overscroll effect at all */
    NONE
}

/**
 * Scrollbar styling configuration.
 */
data class ScrollbarConfig(
    val width: ScrollbarWidth = ScrollbarWidth.AUTO,
    val thumbColor: androidx.compose.ui.graphics.Color? = null,
    val trackColor: androidx.compose.ui.graphics.Color? = null,
    val gutter: ScrollbarGutter = ScrollbarGutter.AUTO
) {
    val hasScrollbar: Boolean
        get() = width != ScrollbarWidth.AUTO || thumbColor != null || trackColor != null ||
                gutter != ScrollbarGutter.AUTO
}

/**
 * Scrollbar width values.
 */
enum class ScrollbarWidth {
    AUTO, THIN, NONE
}

/**
 * Scrollbar gutter values.
 */
enum class ScrollbarGutter {
    AUTO, STABLE, STABLE_BOTH_EDGES
}

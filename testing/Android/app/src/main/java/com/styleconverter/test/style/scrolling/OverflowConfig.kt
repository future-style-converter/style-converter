package com.styleconverter.test.style.scrolling

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Configuration for overflow-related styling properties.
 *
 * ## Supported Properties
 * - OverflowX/Y: Horizontal and vertical overflow behavior
 * - OverflowAnchor: Scroll anchoring behavior
 * - OverflowClipMargin: Margin for clip overflow
 * - OverflowClipMarginBox: Reference box for clip margin
 *
 * ## Usage
 * ```kotlin
 * val config = OverflowExtractor.extractOverflowConfig(properties)
 * val modifier = OverflowApplier.applyOverflow(Modifier, config)
 * ```
 */
data class OverflowConfig(
    /** Horizontal overflow behavior */
    val overflowX: OverflowBehavior = OverflowBehavior.VISIBLE,
    /** Vertical overflow behavior */
    val overflowY: OverflowBehavior = OverflowBehavior.VISIBLE,
    /** Scroll anchor behavior */
    val anchor: OverflowAnchorMode = OverflowAnchorMode.AUTO,
    /** Clip margin when overflow is clip */
    val clipMargin: Dp? = null,
    /** Reference box for clip margin */
    val clipMarginBox: OverflowClipMarginBox = OverflowClipMarginBox.PADDING_BOX
) {
    val hasOverflow: Boolean
        get() = overflowX != OverflowBehavior.VISIBLE || overflowY != OverflowBehavior.VISIBLE

    val shouldClip: Boolean
        get() = overflowX == OverflowBehavior.HIDDEN || overflowY == OverflowBehavior.HIDDEN ||
                overflowX == OverflowBehavior.CLIP || overflowY == OverflowBehavior.CLIP

    val isScrollableX: Boolean
        get() = overflowX == OverflowBehavior.SCROLL || overflowX == OverflowBehavior.AUTO

    val isScrollableY: Boolean
        get() = overflowY == OverflowBehavior.SCROLL || overflowY == OverflowBehavior.AUTO

    /** Check if any scroll behavior is enabled */
    val isScrollable: Boolean
        get() = isScrollableX || isScrollableY

    /** Get effective clip margin (default 0) */
    val effectiveClipMargin: Dp
        get() = clipMargin ?: 0.dp
}

/**
 * Overflow behavior modes.
 *
 * CSS: overflow, overflow-x, overflow-y property values.
 */
enum class OverflowBehavior {
    /** Content is not clipped and may be visible outside the box */
    VISIBLE,
    /** Content is clipped, no scrollbars */
    HIDDEN,
    /** Content is clipped, scrollbars always shown */
    SCROLL,
    /** Content is clipped, scrollbars shown only when needed */
    AUTO,
    /** Content is clipped to the element's box (same as hidden but clips at element edge) */
    CLIP
}

/**
 * Overflow anchor modes.
 *
 * CSS: overflow-anchor property values.
 * Controls how scroll position is adjusted when content above changes.
 */
enum class OverflowAnchorMode {
    /** Browser determines whether to anchor scroll position */
    AUTO,
    /** Disable scroll anchoring */
    NONE
}

/**
 * Reference boxes for overflow clip margin.
 *
 * CSS: overflow-clip-margin box keywords.
 * Determines which box edge the clip margin is measured from.
 */
enum class OverflowClipMarginBox {
    /** Content box edge */
    CONTENT_BOX,
    /** Padding box edge (default) */
    PADDING_BOX,
    /** Border box edge */
    BORDER_BOX
}

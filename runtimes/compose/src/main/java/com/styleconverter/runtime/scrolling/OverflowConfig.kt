package com.styleconverter.runtime.scrolling

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
    val clipMarginBox: OverflowClipMarginBox = OverflowClipMarginBox.PADDING_BOX,
    /**
     * Wave 41 (lane T6) — the block-level `line-clamp` height cap in px,
     * or null when the component declares no fixed-count clamp. Computed
     * by [LineClampCap.capPx] (N line boxes + the vertical padding +
     * border bands that measure inside the overflow node) and consumed by
     * [OverflowApplier.applyOverflow], which chains a Y-ink clip + the
     * [lineClampHeightCap] layout cap so a clamp root whose line boxes
     * live in CHILD components is capped like css-overflow-4 §5 demands
     * (`continue: discard` — content past the Nth line box is not
     * rendered). Leaf components are unaffected: their Text(maxLines)
     * measurement already sits at/under this cap (see LineClampCap's
     * SLACK_PX note).
     */
    val lineClampCapPx: Float? = null
) {
    val hasOverflow: Boolean
        get() = overflowX != OverflowBehavior.VISIBLE || overflowY != OverflowBehavior.VISIBLE

    /**
     * Used value for the X axis after css-overflow-3 §3.1 coercion (see
     * [usedOverflow]) — clip decisions must read used, not specified, values.
     */
    val usedOverflowX: OverflowBehavior
        get() = usedOverflow(overflowX, overflowY)

    /** Used value for the Y axis — the §3.1 mirror of [usedOverflowX]. */
    val usedOverflowY: OverflowBehavior
        get() = usedOverflow(overflowY, overflowX)

    /**
     * True when painted ink must be clipped at the box edge on the X axis.
     * Per css-overflow-3 §3 every non-`visible` used value clips: `hidden`
     * and `clip` obviously, but ALSO `scroll`/`auto` — a scroll container
     * always clips to its padding box even before any scrolling happens.
     */
    val clipsX: Boolean
        get() = axisClips(usedOverflowX)

    /** Y-axis twin of [clipsX] — same §3 rule on the vertical used value. */
    val clipsY: Boolean
        get() = axisClips(usedOverflowY)

    /**
     * True when ANY axis needs paint clipping. NOTE: unlike the pre-wave-18
     * definition this includes `scroll`/`auto` (scroll containers clip, §3)
     * and is derived from USED values, so `overflow-x: hidden` alone also
     * clips Y (visible → auto coercion). Single- vs both-axis routing is
     * the applier's job via [clipsX]/[clipsY].
     */
    val shouldClip: Boolean
        get() = clipsX || clipsY

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

    companion object {
        /**
         * css-overflow-3 §3.1 used-value coercion, as a pure function so the
         * iOS twin (`OverflowClipRules.usedOverflow(_:other:)` in
         * StyleEngine/visibility/AxisClipRect.swift) can be diffed against it
         * by cross-native probes:
         *  - `visible` beside a non-{visible,clip} axis is used as `auto`
         *    (you cannot scroll one axis while the other paints unclipped);
         *  - `clip` beside a non-{visible,clip} axis is used as `hidden`
         *    (clip forbids scrolling, so it hardens to the scrollable clip);
         *  - any pair drawn from {visible, clip} keeps its specified value —
         *    THIS is the single-axis-clip case WPT css-overflow clip-003
         *    exercises (`overflow-x: clip` + `overflow-y: visible`).
         */
        fun usedOverflow(axis: OverflowBehavior, other: OverflowBehavior): OverflowBehavior {
            // "The other axis escapes clipping" — the only values §3.1 lets
            // coexist with an unclipped/unscrolled partner are visible + clip.
            val otherEscapes = other == OverflowBehavior.VISIBLE || other == OverflowBehavior.CLIP
            return when {
                // visible must become a scroll container when its partner is one.
                axis == OverflowBehavior.VISIBLE && !otherEscapes -> OverflowBehavior.AUTO
                // clip cannot pair with a scroll container; §3.1 says it hardens to hidden.
                axis == OverflowBehavior.CLIP && !otherEscapes -> OverflowBehavior.HIDDEN
                // Everything else is used as specified.
                else -> axis
            }
        }

        /**
         * css-overflow-3 §3: every used value except `visible` clips painted
         * ink at the box edge (scroll containers included). Pure twin of iOS
         * `OverflowClipRules.axisClips(_:)`.
         */
        fun axisClips(used: OverflowBehavior): Boolean = used != OverflowBehavior.VISIBLE
    }
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

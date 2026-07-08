package com.styleconverter.test.style.scrolling

import androidx.compose.ui.unit.Dp

/**
 * Scroll timeline axis value.
 */
enum class ScrollTimelineAxisValue {
    BLOCK,
    INLINE,
    X,
    Y
}

/**
 * CSS scroller value for scroll() function.
 */
enum class ScrollerValue {
    NEAREST,
    ROOT,
    SELF
}

/**
 * CSS animation-composition type values.
 */
enum class AnimationCompositionValue {
    REPLACE,
    ADD,
    ACCUMULATE
}

/**
 * CSS timeline-scope value.
 */
sealed interface TimelineScopeValue {
    data object None : TimelineScopeValue
    data object All : TimelineScopeValue
    data class Names(val names: List<String>) : TimelineScopeValue
}

/**
 * CSS animation-timeline type.
 */
sealed interface AnimationTimelineValue {
    data object Auto : AnimationTimelineValue
    data object None : AnimationTimelineValue
    data class Named(val name: String) : AnimationTimelineValue
    data class Scroll(
        val scroller: ScrollerValue = ScrollerValue.NEAREST,
        val axis: ScrollTimelineAxisValue = ScrollTimelineAxisValue.BLOCK
    ) : AnimationTimelineValue
    data class View(
        val axis: ScrollTimelineAxisValue = ScrollTimelineAxisValue.BLOCK,
        val insetStart: Dp? = null,
        val insetEnd: Dp? = null
    ) : AnimationTimelineValue
}

/**
 * CSS animation-range name keywords.
 */
enum class TimelineRangeName {
    NORMAL,
    COVER,
    CONTAIN,
    ENTRY,
    EXIT,
    ENTRY_CROSSING,
    EXIT_CROSSING
}

/**
 * CSS animation-range value.
 */
sealed interface AnimationRangeValue {
    data object Normal : AnimationRangeValue
    data class Percentage(val value: Float) : AnimationRangeValue
    data class Length(val value: Dp) : AnimationRangeValue
    data class NamedRange(val name: TimelineRangeName, val offset: Float? = null) : AnimationRangeValue
}

/**
 * CSS view-timeline-inset value.
 */
sealed interface ViewTimelineInsetValue {
    data object Auto : ViewTimelineInsetValue
    data class Length(val value: Dp) : ViewTimelineInsetValue
    data class Percentage(val value: Float) : ViewTimelineInsetValue
}

/**
 * Configuration for CSS scroll-driven animations timeline properties.
 */
data class ScrollTimelineConfig(
    val scrollTimelineName: String? = null,
    val scrollTimelineAxis: ScrollTimelineAxisValue = ScrollTimelineAxisValue.BLOCK,
    val viewTimelineName: String? = null,
    val viewTimelineAxis: ScrollTimelineAxisValue = ScrollTimelineAxisValue.BLOCK,
    val viewTimelineInset: String? = null,
    val animationTimeline: AnimationTimelineValue = AnimationTimelineValue.Auto,
    val animationRangeStart: AnimationRangeValue = AnimationRangeValue.Normal,
    val animationRangeEnd: AnimationRangeValue = AnimationRangeValue.Normal,
    val animationComposition: AnimationCompositionValue = AnimationCompositionValue.REPLACE,
    val timelineScope: TimelineScopeValue = TimelineScopeValue.None
) {
    val hasScrollTimeline: Boolean
        get() = scrollTimelineName != null ||
                scrollTimelineAxis != ScrollTimelineAxisValue.BLOCK ||
                viewTimelineName != null ||
                viewTimelineAxis != ScrollTimelineAxisValue.BLOCK ||
                viewTimelineInset != null ||
                animationTimeline != AnimationTimelineValue.Auto
}

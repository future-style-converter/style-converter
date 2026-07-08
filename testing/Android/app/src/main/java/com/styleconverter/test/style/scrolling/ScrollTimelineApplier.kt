package com.styleconverter.test.style.scrolling

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest

/**
 * Applies CSS scroll-driven animations to Compose.
 *
 * ## CSS Property Mapping
 * - scroll-timeline → Named scroll progress source
 * - view-timeline → Element visibility progress source
 * - animation-timeline: scroll() → Link animation to scroll position
 * - animation-timeline: view() → Link animation to element visibility
 * - animation-range → Define active range within timeline
 *
 * ## Compose Implementation Strategy
 *
 * CSS scroll-driven animations link animation progress to scroll position.
 * In Compose, we achieve this by:
 *
 * 1. **Scroll Progress**: Derive a 0-1 progress value from scroll state
 * 2. **View Progress**: Derive progress from element's visibility in viewport
 * 3. **Animation Values**: Map progress to animated property values
 * 4. **Range Mapping**: Apply animation-range-start/end constraints
 *
 * ## Usage
 * ```kotlin
 * val scrollState = rememberScrollState()
 * val progress by ScrollTimelineApplier.rememberScrollProgress(scrollState)
 *
 * Box(
 *     modifier = Modifier.graphicsLayer {
 *         alpha = progress  // Fade in as user scrolls
 *         translationY = (1 - progress) * 100  // Slide up
 *     }
 * )
 * ```
 *
 * ## Limitations
 * - Named timelines require manual wiring (no automatic scope lookup)
 * - animation-composition modes have limited support
 * - Complex range expressions need pre-computation
 */
object ScrollTimelineApplier {

    /**
     * Create a scroll progress state from ScrollState.
     *
     * Returns a 0.0-1.0 value representing scroll progress.
     *
     * @param scrollState The scroll state to track
     * @return State with progress value
     */
    @Composable
    fun rememberScrollProgress(scrollState: ScrollState): State<Float> {
        return remember(scrollState) {
            derivedStateOf {
                if (scrollState.maxValue > 0) {
                    scrollState.value.toFloat() / scrollState.maxValue.toFloat()
                } else {
                    0f
                }
            }
        }
    }

    /**
     * Create a scroll progress state from LazyListState.
     *
     * Uses first visible item and offset for progress calculation.
     *
     * @param listState The lazy list state to track
     * @param totalItems Total number of items in list
     * @return State with progress value
     */
    @Composable
    fun rememberScrollProgress(listState: LazyListState, totalItems: Int): State<Float> {
        return remember(listState, totalItems) {
            derivedStateOf {
                if (totalItems <= 1) return@derivedStateOf 0f

                val firstVisibleIndex = listState.firstVisibleItemIndex
                val firstVisibleOffset = listState.firstVisibleItemScrollOffset

                // Approximate progress based on visible item
                val itemProgress = firstVisibleIndex.toFloat() / (totalItems - 1).toFloat()

                // Add sub-item progress (approximation)
                val subProgress = if (listState.layoutInfo.visibleItemsInfo.isNotEmpty()) {
                    val firstItem = listState.layoutInfo.visibleItemsInfo.first()
                    if (firstItem.size > 0) {
                        firstVisibleOffset.toFloat() / firstItem.size.toFloat() / totalItems
                    } else 0f
                } else 0f

                (itemProgress + subProgress).coerceIn(0f, 1f)
            }
        }
    }

    /**
     * Create a view timeline progress state.
     *
     * Tracks element visibility within the viewport.
     * Returns 0.0 when element enters, 1.0 when fully visible or exiting.
     *
     * @param viewportHeight Height of the scrollable viewport
     * @return Pair of (modifier to attach, progress state)
     */
    @Composable
    fun rememberViewProgress(viewportHeight: Dp): ViewProgressState {
        val elementTop = remember { mutableFloatStateOf(0f) }
        val elementHeight = remember { mutableFloatStateOf(0f) }
        val viewportHeightPx = viewportHeight.value // Approximate conversion

        val progress = remember {
            derivedStateOf {
                calculateViewProgress(
                    elementTop = elementTop.floatValue,
                    elementHeight = elementHeight.floatValue,
                    viewportHeight = viewportHeightPx
                )
            }
        }

        val modifier = Modifier.onGloballyPositioned { coordinates ->
            elementTop.floatValue = coordinates.positionInRoot().y
            elementHeight.floatValue = coordinates.size.height.toFloat()
        }

        return ViewProgressState(modifier, progress)
    }

    /**
     * Calculate view progress based on element position.
     *
     * Maps CSS view() timeline behavior:
     * - 0.0: Element just entering viewport (bottom edge at viewport bottom)
     * - 0.5: Element centered in viewport
     * - 1.0: Element exiting viewport (top edge at viewport top)
     */
    private fun calculateViewProgress(
        elementTop: Float,
        elementHeight: Float,
        viewportHeight: Float
    ): Float {
        if (viewportHeight <= 0 || elementHeight <= 0) return 0f

        // Element fully below viewport
        if (elementTop >= viewportHeight) return 0f

        // Element fully above viewport
        if (elementTop + elementHeight <= 0) return 1f

        // Element partially or fully visible
        // Progress from entering (0) to exiting (1)
        val visibleStart = viewportHeight // When element top is at viewport bottom
        val visibleEnd = -elementHeight   // When element bottom is at viewport top

        val progress = (visibleStart - elementTop) / (visibleStart - visibleEnd)
        return progress.coerceIn(0f, 1f)
    }

    /**
     * Apply animation range constraints to progress.
     *
     * Maps CSS animation-range-start and animation-range-end.
     *
     * @param progress Raw progress 0-1
     * @param rangeStart Start of active range (0-1)
     * @param rangeEnd End of active range (0-1)
     * @return Constrained progress within range
     */
    fun applyRange(progress: Float, rangeStart: Float, rangeEnd: Float): Float {
        if (rangeStart >= rangeEnd) return 0f
        if (progress < rangeStart) return 0f
        if (progress > rangeEnd) return 1f

        return (progress - rangeStart) / (rangeEnd - rangeStart)
    }

    /**
     * Convert AnimationRangeValue to a 0-1 float.
     *
     * @param range The range value
     * @param elementHeight Element height for length calculations
     * @param viewportHeight Viewport height for calculations
     * @return Progress value 0-1
     */
    fun rangeValueToFloat(
        range: AnimationRangeValue,
        elementHeight: Float = 0f,
        viewportHeight: Float = 0f
    ): Float {
        return when (range) {
            is AnimationRangeValue.Normal -> 0f
            is AnimationRangeValue.Percentage -> range.value / 100f
            is AnimationRangeValue.Length -> {
                if (viewportHeight > 0) range.value.value / viewportHeight else 0f
            }
            is AnimationRangeValue.NamedRange -> {
                when (range.name) {
                    TimelineRangeName.NORMAL -> 0f
                    TimelineRangeName.COVER -> 0f  // Full range
                    TimelineRangeName.CONTAIN -> 0.25f  // When fully contained
                    TimelineRangeName.ENTRY -> 0f  // Entry phase start
                    TimelineRangeName.EXIT -> 0.5f  // Exit phase start
                    TimelineRangeName.ENTRY_CROSSING -> 0f
                    TimelineRangeName.EXIT_CROSSING -> 1f
                }
            }
        }
    }

    /**
     * Create animated values driven by scroll progress.
     *
     * @param progress Scroll/view progress state
     * @param config Timeline configuration
     * @return ScrollAnimatedValues with common animated properties
     */
    @Composable
    fun rememberScrollAnimatedValues(
        progress: State<Float>,
        config: ScrollTimelineConfig
    ): ScrollAnimatedValues {
        val rangeStart = rangeValueToFloat(config.animationRangeStart)
        val rangeEnd = rangeValueToFloat(config.animationRangeEnd).let {
            if (it <= rangeStart) 1f else it
        }

        val constrainedProgress by remember(progress, rangeStart, rangeEnd) {
            derivedStateOf {
                applyRange(progress.value, rangeStart, rangeEnd)
            }
        }

        return ScrollAnimatedValues(
            progress = constrainedProgress,
            alpha = constrainedProgress,
            scale = 0.5f + (constrainedProgress * 0.5f),  // 0.5 to 1.0
            translationY = (1f - constrainedProgress) * 100f,  // 100 to 0
            rotation = constrainedProgress * 360f  // 0 to 360
        )
    }

    /**
     * Apply scroll-driven animation to modifier.
     *
     * @param modifier Starting modifier
     * @param values Animated values from scroll progress
     * @param effects Which effects to apply
     * @return Modified Modifier with scroll-driven animations
     */
    fun applyScrollAnimation(
        modifier: Modifier,
        values: ScrollAnimatedValues,
        effects: ScrollAnimationEffects = ScrollAnimationEffects.FadeIn
    ): Modifier {
        return modifier.graphicsLayer {
            if (effects.fade) {
                alpha = values.alpha
            }
            if (effects.scale) {
                scaleX = values.scale
                scaleY = values.scale
            }
            if (effects.slideUp) {
                translationY = values.translationY
            }
            if (effects.rotate) {
                rotationZ = values.rotation
            }
        }
    }

    /**
     * Create a smooth scroll-driven animation with easing.
     *
     * Uses Animatable to smooth out scroll jitter.
     *
     * @param targetProgress Raw scroll progress
     * @return Smoothed progress state
     */
    @Composable
    fun rememberSmoothedProgress(targetProgress: State<Float>): State<Float> {
        val animatable = remember { Animatable(0f) }

        LaunchedEffect(Unit) {
            snapshotFlow { targetProgress.value }
                .collectLatest { target ->
                    animatable.animateTo(
                        targetValue = target,
                        animationSpec = tween(
                            durationMillis = 150,
                            easing = LinearEasing
                        )
                    )
                }
        }

        return remember { derivedStateOf { animatable.value } }
    }

    /**
     * Get scroll axis from config.
     *
     * @param config ScrollTimelineConfig
     * @return True if horizontal (X/inline), false if vertical (Y/block)
     */
    fun isHorizontalAxis(config: ScrollTimelineConfig): Boolean {
        return config.scrollTimelineAxis == ScrollTimelineAxisValue.X ||
                config.scrollTimelineAxis == ScrollTimelineAxisValue.INLINE
    }

    /**
     * Registry for named timelines.
     *
     * Allows components to register and lookup scroll timelines by name.
     */
    class TimelineRegistry {
        private val scrollTimelines = mutableMapOf<String, State<Float>>()
        private val viewTimelines = mutableMapOf<String, State<Float>>()

        fun registerScrollTimeline(name: String, progress: State<Float>) {
            scrollTimelines[name] = progress
        }

        fun registerViewTimeline(name: String, progress: State<Float>) {
            viewTimelines[name] = progress
        }

        fun getScrollTimeline(name: String): State<Float>? = scrollTimelines[name]
        fun getViewTimeline(name: String): State<Float>? = viewTimelines[name]

        fun clear() {
            scrollTimelines.clear()
            viewTimelines.clear()
        }
    }
}

/**
 * View progress state with modifier and progress.
 */
data class ViewProgressState(
    val modifier: Modifier,
    val progress: State<Float>
)

/**
 * Animated values derived from scroll progress.
 */
data class ScrollAnimatedValues(
    val progress: Float,
    val alpha: Float,
    val scale: Float,
    val translationY: Float,
    val rotation: Float
)

/**
 * Configuration for which scroll animation effects to apply.
 */
data class ScrollAnimationEffects(
    val fade: Boolean = false,
    val scale: Boolean = false,
    val slideUp: Boolean = false,
    val rotate: Boolean = false
) {
    companion object {
        val None = ScrollAnimationEffects()
        val FadeIn = ScrollAnimationEffects(fade = true)
        val ScaleUp = ScrollAnimationEffects(scale = true)
        val SlideUp = ScrollAnimationEffects(slideUp = true)
        val FadeAndSlide = ScrollAnimationEffects(fade = true, slideUp = true)
        val All = ScrollAnimationEffects(fade = true, scale = true, slideUp = true, rotate = true)
    }
}

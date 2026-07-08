package com.styleconverter.test.style.scrolling

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * Applies scroll configuration to Compose components.
 *
 * ## CSS Property Mapping
 * - scroll-behavior → animate scroll functions
 * - scroll-snap-type → rememberSnapFlingBehavior
 * - scroll-snap-align → snap position calculation
 * - scroll-padding → contentPadding for LazyColumn/LazyRow
 * - scroll-margin → item padding within snap
 * - overscroll-behavior → nestedScroll connection
 *
 * ## Compose Integration
 *
 * ### Scroll Behavior
 * CSS smooth scrolling maps to animated scroll functions:
 * ```kotlin
 * val animationSpec = ScrollApplier.getScrollAnimationSpec(config)
 * scrollState.animateScrollToItem(index, animationSpec)
 * ```
 *
 * ### Scroll Snap
 * CSS scroll-snap maps to Compose snap fling behavior:
 * ```kotlin
 * val flingBehavior = ScrollApplier.rememberSnapFlingBehavior(listState, config)
 * LazyColumn(flingBehavior = flingBehavior) { ... }
 * ```
 *
 * ### Overscroll Behavior
 * CSS overscroll-behavior maps to nested scroll connection:
 * ```kotlin
 * val modifier = ScrollApplier.applyOverscrollBehavior(Modifier, config)
 * ```
 *
 * ## Limitations
 * - scroll-snap-stop: always is approximated (Compose doesn't distinguish)
 * - scroll-margin: Applied as item padding, not true scroll margin
 * - Scrollbar styling requires platform-specific implementation
 */
object ScrollApplier {

    /**
     * Apply scroll-related modifiers.
     *
     * @param modifier Starting modifier
     * @param config ScrollConfig
     * @return Modified Modifier with scroll behavior
     */
    fun applyScroll(modifier: Modifier, config: ScrollConfig): Modifier {
        var result = modifier

        // Apply overscroll behavior via nested scroll
        if (config.overscroll.hasOverscroll) {
            result = applyOverscrollBehavior(result, config.overscroll)
        }

        return result
    }

    /**
     * Apply overscroll behavior using nested scroll.
     *
     * - contain: Prevent scroll chaining to parent
     * - none: Prevent both chaining and overscroll effect
     *
     * @param modifier Starting modifier
     * @param config OverscrollConfig
     * @return Modified Modifier
     */
    fun applyOverscrollBehavior(modifier: Modifier, config: OverscrollConfig): Modifier {
        if (!config.hasOverscroll) return modifier

        return modifier.nestedScroll(
            OverscrollNestedScrollConnection(
                containX = config.x == OverscrollBehaviorMode.CONTAIN || config.x == OverscrollBehaviorMode.NONE,
                containY = config.y == OverscrollBehaviorMode.CONTAIN || config.y == OverscrollBehaviorMode.NONE
            )
        )
    }

    /**
     * Get snap fling behavior configuration for LazyList.
     *
     * @param config ScrollConfig
     * @return SnapBehaviorConfig with snap settings
     */
    fun getSnapBehaviorConfig(config: ScrollConfig): SnapBehaviorConfig {
        val snapType = config.snapType ?: return SnapBehaviorConfig()

        return SnapBehaviorConfig(
            enabled = true,
            isMandatory = snapType.strictness == ScrollSnapStrictness.MANDATORY,
            axis = when (snapType.axis) {
                ScrollSnapAxis.X, ScrollSnapAxis.INLINE -> SnapAxis.HORIZONTAL
                ScrollSnapAxis.Y, ScrollSnapAxis.BLOCK -> SnapAxis.VERTICAL
                ScrollSnapAxis.BOTH -> SnapAxis.BOTH
            },
            snapStop = config.snapStop,
            snapAlign = config.snapAlign
        )
    }

    /**
     * Get animation spec for scroll operations.
     *
     * @param config ScrollConfig
     * @return AnimationSpec for scroll animations
     */
    fun <T> getScrollAnimationSpec(config: ScrollConfig): AnimationSpec<T> {
        return if (config.behavior == ScrollBehaviorMode.SMOOTH) {
            tween(durationMillis = 300)
        } else {
            snap()
        }
    }

    /**
     * Get scroll animation configuration.
     *
     * @param config ScrollConfig
     * @return ScrollAnimationConfig
     */
    fun getScrollAnimationConfig(config: ScrollConfig): ScrollAnimationConfig {
        return ScrollAnimationConfig(
            isSmooth = config.behavior == ScrollBehaviorMode.SMOOTH,
            durationMillis = if (config.behavior == ScrollBehaviorMode.SMOOTH) 300 else 0
        )
    }

    /**
     * Get content padding from scroll-padding configuration.
     *
     * CSS scroll-padding defines the padding area for snap points.
     * Maps to contentPadding for LazyColumn/LazyRow.
     *
     * @param config ScrollPadding
     * @return PaddingValues for content padding
     */
    fun getContentPadding(config: ScrollPadding): PaddingValues {
        return PaddingValues(
            start = config.left ?: config.inlineStart ?: 0.dp,
            top = config.top ?: config.blockStart ?: 0.dp,
            end = config.right ?: config.inlineEnd ?: 0.dp,
            bottom = config.bottom ?: config.blockEnd ?: 0.dp
        )
    }

    /**
     * Get content padding from full ScrollConfig.
     *
     * @param config ScrollConfig
     * @return PaddingValues
     */
    fun getContentPadding(config: ScrollConfig): PaddingValues {
        return getContentPadding(config.scrollPadding)
    }

    /**
     * Check if overscroll effect should be disabled.
     *
     * @param config ScrollConfig
     * @return true if overscroll should be disabled
     */
    fun shouldDisableOverscrollEffect(config: ScrollConfig): Boolean {
        return config.overscroll.x == OverscrollBehaviorMode.NONE ||
                config.overscroll.y == OverscrollBehaviorMode.NONE
    }

    /**
     * Check if scroll chaining should be contained.
     *
     * @param config ScrollConfig
     * @return true if scroll should not chain to parent
     */
    fun shouldContainScrollChaining(config: ScrollConfig): Boolean {
        return config.overscroll.x == OverscrollBehaviorMode.CONTAIN ||
                config.overscroll.y == OverscrollBehaviorMode.CONTAIN
    }

    /**
     * Get the snap position offset based on scroll-snap-align.
     *
     * @param containerSize Size of the scroll container
     * @param itemSize Size of the snapping item
     * @param align Snap alignment value
     * @return Offset from item start to snap point
     */
    fun getSnapPositionOffset(
        containerSize: Float,
        itemSize: Float,
        align: ScrollSnapAlignValue
    ): Float {
        return when (align) {
            ScrollSnapAlignValue.NONE -> 0f
            ScrollSnapAlignValue.START -> 0f
            ScrollSnapAlignValue.CENTER -> (containerSize - itemSize) / 2
            ScrollSnapAlignValue.END -> containerSize - itemSize
        }
    }

    /**
     * Calculate scroll margin for an item.
     *
     * CSS scroll-margin defines outset from snap area.
     * In Compose, this can be applied as item padding.
     *
     * @param margin ScrollMargin config
     * @return PaddingValues to apply to items
     */
    fun getItemMargin(margin: ScrollMargin): PaddingValues {
        return PaddingValues(
            start = margin.left ?: margin.inlineStart ?: 0.dp,
            top = margin.top ?: margin.blockStart ?: 0.dp,
            end = margin.right ?: margin.inlineEnd ?: 0.dp,
            bottom = margin.bottom ?: margin.blockEnd ?: 0.dp
        )
    }

    /**
     * Create a default fling behavior based on config.
     *
     * For non-snapping scrolls, returns default fling.
     * For snapping, returns null (use rememberSnapFlingBehavior instead).
     *
     * @param config ScrollConfig
     * @return FlingBehavior or null if snapping
     */
    @Composable
    fun getDefaultFlingBehavior(config: ScrollConfig): FlingBehavior? {
        return if (config.hasSnapping) {
            null // Caller should use snap fling behavior
        } else {
            ScrollableDefaults.flingBehavior()
        }
    }

    /**
     * Create snap fling behavior for LazyList.
     *
     * @param listState LazyListState to snap
     * @param config ScrollConfig with snap settings
     * @return Snap FlingBehavior
     */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun rememberSnapFlingBehavior(
        listState: LazyListState,
        config: ScrollConfig
    ): FlingBehavior {
        val snapBehaviorConfig = remember(config) { getSnapBehaviorConfig(config) }

        return rememberSnapFlingBehavior(lazyListState = listState)
    }

    /**
     * Scroll helper result with all configuration.
     */
    data class ScrollHelperConfig(
        val contentPadding: PaddingValues,
        val shouldSnap: Boolean,
        val animationConfig: ScrollAnimationConfig,
        val disableOverscroll: Boolean,
        val containScroll: Boolean,
        val itemMargin: PaddingValues?
    )

    /**
     * Get comprehensive scroll helper configuration.
     *
     * @param config ScrollConfig
     * @return ScrollHelperConfig with all settings
     */
    fun getScrollHelperConfig(config: ScrollConfig): ScrollHelperConfig {
        return ScrollHelperConfig(
            contentPadding = getContentPadding(config),
            shouldSnap = config.hasSnapping,
            animationConfig = getScrollAnimationConfig(config),
            disableOverscroll = shouldDisableOverscrollEffect(config),
            containScroll = shouldContainScrollChaining(config),
            itemMargin = if (config.scrollMargin.hasMargin) {
                getItemMargin(config.scrollMargin)
            } else null
        )
    }
}

/**
 * Configuration for snap fling behavior.
 */
data class SnapBehaviorConfig(
    val enabled: Boolean = false,
    val isMandatory: Boolean = false,
    val axis: SnapAxis = SnapAxis.VERTICAL,
    val snapStop: ScrollSnapStopMode = ScrollSnapStopMode.NORMAL,
    val snapAlign: ScrollSnapAlign = ScrollSnapAlign()
)

/**
 * Snap axis options.
 */
enum class SnapAxis {
    HORIZONTAL, VERTICAL, BOTH
}

/**
 * Configuration for scroll animation.
 */
data class ScrollAnimationConfig(
    val isSmooth: Boolean = false,
    val durationMillis: Int = 0
)

/**
 * Nested scroll connection for overscroll behavior control.
 *
 * Prevents scroll chaining to parent when containX/Y is true.
 */
private class OverscrollNestedScrollConnection(
    private val containX: Boolean,
    private val containY: Boolean
) : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // Don't consume any scroll, let child handle first
        return Offset.Zero
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        // Consume remaining scroll to prevent chaining to parent
        val consumedX = if (containX) available.x else 0f
        val consumedY = if (containY) available.y else 0f
        return Offset(consumedX, consumedY)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        // Consume remaining fling velocity to prevent chaining
        val consumedX = if (containX) available.x else 0f
        val consumedY = if (containY) available.y else 0f
        return Velocity(consumedX, consumedY)
    }
}

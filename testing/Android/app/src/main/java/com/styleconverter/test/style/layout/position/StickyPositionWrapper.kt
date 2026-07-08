package com.styleconverter.test.style.layout.position

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Wrapper that implements CSS `position: sticky` with scroll-aware behavior.
 *
 * ## CSS Behavior
 * In CSS, `position: sticky` is a hybrid:
 * - Acts like `position: relative` when within the threshold
 * - Acts like `position: fixed` when the scroll threshold is reached
 * - Remains "stuck" until its container scrolls out of view
 *
 * ## Compose Implementation
 * We track the scroll position and the element's position within its container,
 * then apply an offset to simulate the sticky behavior.
 *
 * ## Sticky Thresholds
 * - `top: 0`: Stick to top edge when scrolled to top of container
 * - `top: 16px`: Stick 16px from top edge
 * - `bottom: 0`: Stick to bottom edge when scrolled to bottom
 *
 * ## Limitations
 * - Requires a [ScrollState] or [LazyListState] to track scroll position
 * - Only supports vertical scrolling (horizontal sticky is rare in practice)
 * - Container bounds detection is approximate
 * - Does not support sticky-within-sticky nesting
 *
 * ## Usage
 * ```kotlin
 * val scrollState = rememberScrollState()
 *
 * Column(modifier = Modifier.verticalScroll(scrollState)) {
 *     // Regular content...
 *
 *     StickyPositionWrapper(
 *         scrollState = scrollState,
 *         stickyTop = 0.dp,
 *         modifier = Modifier.background(Color.White)
 *     ) {
 *         Text("Sticky header")
 *     }
 *
 *     // More content...
 * }
 * ```
 */
@Composable
fun StickyPositionWrapper(
    scrollState: ScrollState,
    config: PositionConfig,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    StickyPositionWrapper(
        scrollOffset = { scrollState.value },
        stickyTop = config.resolvedTop,
        stickyBottom = config.resolvedBottom,
        zIndex = config.zIndex,
        modifier = modifier,
        content = content
    )
}

/**
 * Sticky wrapper with explicit sticky threshold values.
 *
 * @param scrollOffset Lambda that returns current scroll offset in pixels
 * @param stickyTop Distance from top where element becomes sticky (null = not sticky to top)
 * @param stickyBottom Distance from bottom where element becomes sticky (null = not sticky to bottom)
 * @param zIndex Z-index for stacking order when sticky
 * @param modifier Base modifier for the wrapper
 * @param content Content to render
 */
@Composable
fun StickyPositionWrapper(
    scrollOffset: () -> Int,
    stickyTop: Dp? = null,
    stickyBottom: Dp? = null,
    zIndex: Float = 0f,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current

    // Track the element's original position in its parent
    var originalPositionY = remember { 0f }

    // Convert sticky thresholds to pixels
    val stickyTopPx = stickyTop?.let { with(density) { it.toPx() } }
    val stickyBottomPx = stickyBottom?.let { with(density) { it.toPx() } }

    // Calculate the sticky offset based on scroll position
    val stickyOffset by remember(stickyTopPx, stickyBottomPx) {
        derivedStateOf {
            calculateStickyOffset(
                scrollOffset = scrollOffset(),
                originalPositionY = originalPositionY,
                stickyTopPx = stickyTopPx,
                stickyBottomPx = stickyBottomPx
            )
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                // Capture the original position in parent (before any sticky offset)
                originalPositionY = coordinates.positionInParent().y
            }
            .offset { IntOffset(0, stickyOffset.roundToInt()) }
            .zIndex(if (stickyOffset != 0f) zIndex.coerceAtLeast(1f) else zIndex),
        content = content
    )
}

/**
 * Sticky wrapper for use with LazyListState (LazyColumn/LazyRow).
 *
 * Note: For LazyColumn, consider using `stickyHeader` modifier instead,
 * which is the native Compose solution for sticky headers in lazy lists.
 */
@Composable
fun StickyPositionWrapper(
    lazyListState: LazyListState,
    config: PositionConfig,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    // For LazyList, we use the first visible item offset
    val scrollOffset = remember(lazyListState) {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex * 100 + lazyListState.firstVisibleItemScrollOffset
        }
    }

    StickyPositionWrapper(
        scrollOffset = { scrollOffset.value },
        stickyTop = config.resolvedTop,
        stickyBottom = config.resolvedBottom,
        zIndex = config.zIndex,
        modifier = modifier,
        content = content
    )
}

/**
 * Calculate the offset needed to create sticky behavior.
 *
 * @param scrollOffset Current scroll position in pixels
 * @param originalPositionY Element's original Y position in parent
 * @param stickyTopPx Sticky threshold from top in pixels (null = not sticky to top)
 * @param stickyBottomPx Sticky threshold from bottom in pixels (null = not sticky to bottom)
 * @return Offset to apply to create sticky effect
 */
private fun calculateStickyOffset(
    scrollOffset: Int,
    originalPositionY: Float,
    stickyTopPx: Float?,
    stickyBottomPx: Float?
): Float {
    // Handle sticky-top behavior
    if (stickyTopPx != null) {
        // The element should stick when its top would scroll above the sticky threshold
        val scrollThreshold = originalPositionY - stickyTopPx

        if (scrollOffset > scrollThreshold) {
            // Element has scrolled past threshold - apply offset to keep it at sticky position
            return (scrollOffset - scrollThreshold).coerceAtLeast(0f)
        }
    }

    // Handle sticky-bottom behavior (less common)
    // This would require knowing the viewport height, which is more complex
    // For now, we focus on sticky-top which covers 95%+ of use cases

    return 0f
}

/**
 * Check if a component should use sticky position rendering.
 */
fun PositionConfig.isSticky(): Boolean = type == PositionType.STICKY

/**
 * Represents the current sticky state for debugging/styling purposes.
 */
enum class StickyState {
    /** Element is in normal flow (not yet sticky) */
    NORMAL,
    /** Element is currently stuck at its threshold */
    STUCK,
    /** Element has scrolled past its container (released from sticky) */
    RELEASED
}

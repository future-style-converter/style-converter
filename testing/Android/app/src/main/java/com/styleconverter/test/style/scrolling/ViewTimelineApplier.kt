package com.styleconverter.test.style.scrolling

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies CSS view-timeline to Compose.
 *
 * ## CSS Property Mapping
 * - view-timeline-name → Named timeline for element visibility
 * - view-timeline-axis → Block (vertical) or inline (horizontal)
 * - view-timeline-inset → Viewport inset for visibility calculations
 * - animation-range: entry/exit/contain/cover → Phase-specific progress
 *
 * ## View Timeline Phases
 *
 * CSS view() timeline has distinct phases:
 *
 * ```
 * |        Viewport        |
 * |________________________|
 *           ↑
 *    [Element entering]  → entry phase (0% → 100%)
 *           ↓
 *    [Element visible]   → contain phase (element fully in view)
 *           ↓
 *    [Element exiting]   → exit phase (0% → 100%)
 *           ↓
 * ```
 *
 * ## Usage
 * ```kotlin
 * val viewState = ViewTimelineApplier.rememberViewTimeline(viewportHeight = 800.dp)
 *
 * Box(
 *     modifier = viewState.modifier.then(
 *         Modifier.graphicsLayer {
 *             // Fade in during entry
 *             alpha = viewState.entryProgress.value
 *             // Slide up during entry
 *             translationY = (1 - viewState.entryProgress.value) * 50f
 *         }
 *     )
 * )
 * ```
 */
object ViewTimelineApplier {

    /**
     * Create a view timeline state for an element.
     *
     * Tracks element's visibility phases in the viewport.
     *
     * @param viewportHeight Height of the scrollable viewport
     * @param insetTop Top inset (shrinks effective viewport)
     * @param insetBottom Bottom inset (shrinks effective viewport)
     * @return ViewTimelineState with all phase progress values
     */
    @Composable
    fun rememberViewTimeline(
        viewportHeight: Dp,
        insetTop: Dp = 0.dp,
        insetBottom: Dp = 0.dp
    ): ViewTimelineState {
        val elementBounds = remember { mutableStateOf<Rect?>(null) }
        val viewportHeightPx = viewportHeight.value
        val insetTopPx = insetTop.value
        val insetBottomPx = insetBottom.value

        // Calculate effective viewport
        val effectiveViewportTop = insetTopPx
        val effectiveViewportBottom = viewportHeightPx - insetBottomPx
        val effectiveViewportHeight = effectiveViewportBottom - effectiveViewportTop

        // Overall progress (cover range)
        val coverProgress = remember {
            derivedStateOf {
                calculateCoverProgress(
                    elementBounds.value,
                    effectiveViewportTop,
                    effectiveViewportBottom
                )
            }
        }

        // Entry phase progress
        val entryProgress = remember {
            derivedStateOf {
                calculateEntryProgress(
                    elementBounds.value,
                    effectiveViewportTop,
                    effectiveViewportBottom
                )
            }
        }

        // Exit phase progress
        val exitProgress = remember {
            derivedStateOf {
                calculateExitProgress(
                    elementBounds.value,
                    effectiveViewportTop,
                    effectiveViewportBottom
                )
            }
        }

        // Contain phase (element fully visible)
        val containProgress = remember {
            derivedStateOf {
                calculateContainProgress(
                    elementBounds.value,
                    effectiveViewportTop,
                    effectiveViewportBottom
                )
            }
        }

        // Current phase
        val currentPhase = remember {
            derivedStateOf {
                determinePhase(
                    elementBounds.value,
                    effectiveViewportTop,
                    effectiveViewportBottom
                )
            }
        }

        val modifier = Modifier.onGloballyPositioned { coordinates ->
            elementBounds.value = coordinates.boundsInRoot()
        }

        return ViewTimelineState(
            modifier = modifier,
            coverProgress = coverProgress,
            entryProgress = entryProgress,
            exitProgress = exitProgress,
            containProgress = containProgress,
            currentPhase = currentPhase
        )
    }

    /**
     * Calculate cover progress (full timeline from entry start to exit end).
     *
     * 0.0 = Element just starting to enter (top edge at viewport bottom)
     * 1.0 = Element fully exited (bottom edge at viewport top)
     */
    private fun calculateCoverProgress(
        bounds: Rect?,
        viewportTop: Float,
        viewportBottom: Float
    ): Float {
        if (bounds == null) return 0f

        val elementTop = bounds.top
        val elementBottom = bounds.bottom
        val elementHeight = bounds.height

        // Total travel distance: from fully below to fully above
        val totalDistance = (viewportBottom - viewportTop) + elementHeight
        val startPosition = viewportBottom  // Element top at viewport bottom
        val currentDistance = startPosition - elementTop

        return (currentDistance / totalDistance).coerceIn(0f, 1f)
    }

    /**
     * Calculate entry progress.
     *
     * 0.0 = Element just starting to enter (top edge at viewport bottom)
     * 1.0 = Element fully entered (bottom edge at viewport bottom OR top at viewport top)
     */
    private fun calculateEntryProgress(
        bounds: Rect?,
        viewportTop: Float,
        viewportBottom: Float
    ): Float {
        if (bounds == null) return 0f

        val elementTop = bounds.top
        val elementBottom = bounds.bottom
        val elementHeight = bounds.height

        // Entry phase: from element top at viewport bottom to element fully visible
        // For small elements: until element bottom reaches viewport bottom
        // For large elements: until element top reaches viewport top

        val viewportHeight = viewportBottom - viewportTop

        if (elementTop >= viewportBottom) {
            // Not yet entered
            return 0f
        }

        if (elementHeight <= viewportHeight) {
            // Small element: entry complete when bottom edge enters viewport
            val entryDistance = elementHeight
            val traveled = viewportBottom - elementTop
            return (traveled / entryDistance).coerceIn(0f, 1f)
        } else {
            // Large element: entry complete when top reaches viewport top
            val entryDistance = viewportHeight
            val traveled = viewportBottom - elementTop
            return (traveled / entryDistance).coerceIn(0f, 1f)
        }
    }

    /**
     * Calculate exit progress.
     *
     * 0.0 = Element just starting to exit (top edge at viewport top)
     * 1.0 = Element fully exited (bottom edge at viewport top)
     */
    private fun calculateExitProgress(
        bounds: Rect?,
        viewportTop: Float,
        viewportBottom: Float
    ): Float {
        if (bounds == null) return 0f

        val elementTop = bounds.top
        val elementBottom = bounds.bottom
        val elementHeight = bounds.height

        val viewportHeight = viewportBottom - viewportTop

        // Exit phase starts when element starts leaving from top
        if (elementTop >= viewportTop) {
            // Not yet exiting
            return 0f
        }

        if (elementBottom <= viewportTop) {
            // Fully exited
            return 1f
        }

        // In exit phase
        val exitDistance = elementHeight.coerceAtMost(viewportHeight)
        val traveled = viewportTop - elementTop
        return (traveled / exitDistance).coerceIn(0f, 1f)
    }

    /**
     * Calculate contain progress (element fully visible in viewport).
     *
     * 0.0 = Element just became fully visible
     * 1.0 = Element about to start exiting
     */
    private fun calculateContainProgress(
        bounds: Rect?,
        viewportTop: Float,
        viewportBottom: Float
    ): Float {
        if (bounds == null) return 0f

        val elementTop = bounds.top
        val elementBottom = bounds.bottom
        val elementHeight = bounds.height
        val viewportHeight = viewportBottom - viewportTop

        // Element must fit in viewport for contain phase
        if (elementHeight > viewportHeight) {
            // Element larger than viewport - no contain phase
            return if (elementTop <= viewportTop && elementBottom >= viewportBottom) 1f else 0f
        }

        // Check if fully contained
        val isFullyContained = elementTop >= viewportTop && elementBottom <= viewportBottom
        if (!isFullyContained) return 0f

        // Progress through contain phase
        val containStartY = viewportBottom - elementHeight  // When element just fully entered
        val containEndY = viewportTop  // When element starts exiting
        val containDistance = containStartY - containEndY

        if (containDistance <= 0) return 1f

        val traveled = containStartY - elementTop
        return (traveled / containDistance).coerceIn(0f, 1f)
    }

    /**
     * Determine the current visibility phase.
     */
    private fun determinePhase(
        bounds: Rect?,
        viewportTop: Float,
        viewportBottom: Float
    ): ViewTimelinePhase {
        if (bounds == null) return ViewTimelinePhase.HIDDEN

        val elementTop = bounds.top
        val elementBottom = bounds.bottom
        val elementHeight = bounds.height
        val viewportHeight = viewportBottom - viewportTop

        return when {
            elementBottom <= viewportTop -> ViewTimelinePhase.EXITED
            elementTop >= viewportBottom -> ViewTimelinePhase.HIDDEN
            elementTop < viewportTop -> ViewTimelinePhase.EXITING
            elementHeight <= viewportHeight && elementBottom > viewportBottom -> ViewTimelinePhase.ENTERING
            elementTop >= viewportTop && elementBottom <= viewportBottom -> ViewTimelinePhase.CONTAINED
            else -> ViewTimelinePhase.VISIBLE
        }
    }

    /**
     * Apply entry animation modifiers.
     *
     * Common pattern for fade+slide entry animation.
     *
     * @param modifier Starting modifier
     * @param entryProgress Entry progress 0-1
     * @param slideDistance Distance to slide in dp
     * @return Modified Modifier with entry animation
     */
    fun applyEntryAnimation(
        modifier: Modifier,
        entryProgress: Float,
        slideDistance: Float = 50f
    ): Modifier {
        return modifier.graphicsLayer {
            alpha = entryProgress
            translationY = (1f - entryProgress) * slideDistance
        }
    }

    /**
     * Apply exit animation modifiers.
     *
     * @param modifier Starting modifier
     * @param exitProgress Exit progress 0-1
     * @param slideDistance Distance to slide out in dp
     * @return Modified Modifier with exit animation
     */
    fun applyExitAnimation(
        modifier: Modifier,
        exitProgress: Float,
        slideDistance: Float = 50f
    ): Modifier {
        return modifier.graphicsLayer {
            alpha = 1f - exitProgress
            translationY = -exitProgress * slideDistance
        }
    }

    /**
     * Apply parallax effect based on cover progress.
     *
     * @param modifier Starting modifier
     * @param coverProgress Cover progress 0-1
     * @param parallaxFactor Parallax intensity (0.5 = half speed)
     * @param maxOffset Maximum offset in pixels
     * @return Modified Modifier with parallax effect
     */
    fun applyParallax(
        modifier: Modifier,
        coverProgress: Float,
        parallaxFactor: Float = 0.3f,
        maxOffset: Float = 100f
    ): Modifier {
        return modifier.graphicsLayer {
            translationY = (coverProgress - 0.5f) * 2f * maxOffset * parallaxFactor
        }
    }

    /**
     * Apply scale animation based on visibility.
     *
     * @param modifier Starting modifier
     * @param progress Progress 0-1
     * @param minScale Minimum scale at progress 0
     * @param maxScale Maximum scale at progress 1
     * @return Modified Modifier with scale animation
     */
    fun applyScaleAnimation(
        modifier: Modifier,
        progress: Float,
        minScale: Float = 0.8f,
        maxScale: Float = 1f
    ): Modifier {
        val scale = minScale + (progress * (maxScale - minScale))
        return modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    }
}

/**
 * View timeline state with all phase progress values.
 */
data class ViewTimelineState(
    /** Modifier to attach for position tracking */
    val modifier: Modifier,
    /** Overall progress from entry start to exit end */
    val coverProgress: State<Float>,
    /** Entry phase progress (0 = entering, 1 = entered) */
    val entryProgress: State<Float>,
    /** Exit phase progress (0 = not exiting, 1 = exited) */
    val exitProgress: State<Float>,
    /** Contain phase progress (only when fully visible) */
    val containProgress: State<Float>,
    /** Current visibility phase */
    val currentPhase: State<ViewTimelinePhase>
)

/**
 * View timeline visibility phases.
 */
enum class ViewTimelinePhase {
    /** Element not yet visible (below viewport) */
    HIDDEN,
    /** Element entering viewport from bottom */
    ENTERING,
    /** Element partially visible but not fully contained */
    VISIBLE,
    /** Element fully contained within viewport */
    CONTAINED,
    /** Element exiting viewport from top */
    EXITING,
    /** Element no longer visible (above viewport) */
    EXITED
}

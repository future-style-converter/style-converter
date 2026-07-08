package com.styleconverter.test.style.animations

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.styleconverter.test.style.scrolling.AnimationRangeValue
import com.styleconverter.test.style.scrolling.AnimationTimelineValue
import com.styleconverter.test.style.scrolling.ScrollTimelineApplier
import com.styleconverter.test.style.scrolling.ScrollTimelineConfig
import com.styleconverter.test.style.scrolling.ViewTimelineApplier
import com.styleconverter.test.style.scrolling.ViewTimelinePhase
import com.styleconverter.test.style.scrolling.ViewTimelineState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.pow

/**
 * Integrates CSS scroll-driven animations with Compose animation system.
 *
 * ## CSS Property Integration
 *
 * This integrator bridges:
 * - ScrollTimelineApplier (scroll-timeline, view-timeline)
 * - AnimationConfig (animation-*, transition-*)
 *
 * ## Usage Pattern
 *
 * ```kotlin
 * // 1. Get scroll/view progress
 * val scrollState = rememberScrollState()
 * val progress by ScrollTimelineApplier.rememberScrollProgress(scrollState)
 *
 * // 2. Create animation from config
 * val animation = AnimationTimelineIntegrator.createScrollAnimation(
 *     progress = progress,
 *     animationConfig = animationConfig,
 *     timelineConfig = timelineConfig
 * )
 *
 * // 3. Apply to modifier
 * Box(
 *     modifier = Modifier.graphicsLayer {
 *         alpha = animation.alpha
 *         scaleX = animation.scale
 *         scaleY = animation.scale
 *         translationY = animation.translationY
 *     }
 * )
 * ```
 *
 * ## Keyframe Interpolation
 *
 * CSS @keyframes are mapped to progress-based interpolation:
 * - 0% keyframe → progress 0.0
 * - 50% keyframe → progress 0.5
 * - 100% keyframe → progress 1.0
 *
 * The timing function is applied to the progress value before interpolation.
 */
object AnimationTimelineIntegrator {

    /**
     * Create a scroll-driven animation state.
     *
     * @param scrollState ScrollState to track
     * @param animationConfig CSS animation configuration
     * @param timelineConfig CSS timeline configuration
     * @return ScrollDrivenAnimation with animated values
     */
    @Composable
    fun rememberScrollDrivenAnimation(
        scrollState: ScrollState,
        animationConfig: AnimationConfig,
        timelineConfig: ScrollTimelineConfig = ScrollTimelineConfig()
    ): ScrollDrivenAnimation {
        val rawProgress by ScrollTimelineApplier.rememberScrollProgress(scrollState)

        return remember(animationConfig, timelineConfig) {
            createScrollAnimation(rawProgress, animationConfig, timelineConfig)
        }
    }

    /**
     * Create a scroll-driven animation from LazyListState.
     *
     * @param listState LazyListState to track
     * @param totalItems Total items in list
     * @param animationConfig CSS animation configuration
     * @param timelineConfig CSS timeline configuration
     * @return ScrollDrivenAnimation
     */
    @Composable
    fun rememberScrollDrivenAnimation(
        listState: LazyListState,
        totalItems: Int,
        animationConfig: AnimationConfig,
        timelineConfig: ScrollTimelineConfig = ScrollTimelineConfig()
    ): ScrollDrivenAnimation {
        val rawProgress by ScrollTimelineApplier.rememberScrollProgress(listState, totalItems)

        return remember(animationConfig, timelineConfig) {
            createScrollAnimation(rawProgress, animationConfig, timelineConfig)
        }
    }

    /**
     * Create a view-timeline-driven animation.
     *
     * @param viewState ViewTimelineState from ViewTimelineApplier
     * @param animationConfig CSS animation configuration
     * @param timelineConfig CSS timeline configuration
     * @return ViewDrivenAnimation
     */
    @Composable
    fun rememberViewDrivenAnimation(
        viewState: ViewTimelineState,
        animationConfig: AnimationConfig,
        timelineConfig: ScrollTimelineConfig = ScrollTimelineConfig()
    ): ViewDrivenAnimation {
        val entryProgress by viewState.entryProgress
        val exitProgress by viewState.exitProgress
        val coverProgress by viewState.coverProgress
        val phase by viewState.currentPhase

        val easing = remember(animationConfig) {
            animationConfig.getTimingFunction(0).toEasing()
        }

        return ViewDrivenAnimation(
            modifier = viewState.modifier,
            entryAnimation = createScrollAnimation(entryProgress, animationConfig, timelineConfig),
            exitAnimation = createScrollAnimation(exitProgress, animationConfig, timelineConfig),
            coverAnimation = createScrollAnimation(coverProgress, animationConfig, timelineConfig),
            phase = phase,
            rawEntryProgress = entryProgress,
            rawExitProgress = exitProgress,
            rawCoverProgress = coverProgress
        )
    }

    /**
     * Create animation values from progress.
     *
     * @param rawProgress Raw scroll/view progress 0-1
     * @param animationConfig Animation configuration
     * @param timelineConfig Timeline configuration
     * @return ScrollDrivenAnimation with computed values
     */
    fun createScrollAnimation(
        rawProgress: Float,
        animationConfig: AnimationConfig,
        timelineConfig: ScrollTimelineConfig
    ): ScrollDrivenAnimation {
        // Apply range constraints
        val rangeStart = rangeToFloat(timelineConfig.animationRangeStart)
        val rangeEnd = rangeToFloat(timelineConfig.animationRangeEnd).let {
            if (it <= rangeStart) 1f else it
        }

        val constrainedProgress = ScrollTimelineApplier.applyRange(rawProgress, rangeStart, rangeEnd)

        // Apply easing
        val easing = animationConfig.getTimingFunction(0).toEasing()
        val easedProgress = easing.transform(constrainedProgress)

        // Get animation direction
        val direction = animationConfig.getDirection(0)
        val directedProgress = when (direction) {
            AnimationDirection.NORMAL -> easedProgress
            AnimationDirection.REVERSE -> 1f - easedProgress
            AnimationDirection.ALTERNATE -> if (rawProgress < 0.5f) easedProgress * 2f else 2f - easedProgress * 2f
            AnimationDirection.ALTERNATE_REVERSE -> if (rawProgress < 0.5f) 1f - easedProgress * 2f else easedProgress * 2f - 1f
        }

        return ScrollDrivenAnimation(
            progress = directedProgress,
            alpha = directedProgress,
            scale = 0.5f + (directedProgress * 0.5f),
            translationY = (1f - directedProgress) * 100f,
            translationX = 0f,
            rotation = directedProgress * 360f,
            rawProgress = rawProgress,
            easedProgress = easedProgress
        )
    }

    /**
     * Convert AnimationRangeValue to float 0-1.
     */
    private fun rangeToFloat(range: AnimationRangeValue): Float {
        return ScrollTimelineApplier.rangeValueToFloat(range)
    }

    /**
     * Convert TimingFunctionConfig to Compose Easing.
     */
    fun TimingFunctionConfig.toEasing(): Easing {
        // Check for steps (not fully supported, fall back to linear segments)
        if (isSteps && stepsCount != null) {
            return StepsEasing(stepsCount, stepsPosition ?: "end")
        }

        // Cubic bezier
        val points = cubicBezier ?: return LinearEasing
        if (points.size < 4) return LinearEasing

        return CubicBezierEasing(
            points[0].toFloat(),
            points[1].toFloat(),
            points[2].toFloat(),
            points[3].toFloat()
        )
    }

    /**
     * Apply scroll-driven animation to modifier.
     *
     * @param modifier Starting modifier
     * @param animation Computed animation values
     * @param effects Which effects to apply
     * @return Modified Modifier
     */
    fun applyAnimation(
        modifier: Modifier,
        animation: ScrollDrivenAnimation,
        effects: AnimationEffects = AnimationEffects.FadeIn
    ): Modifier {
        return modifier.graphicsLayer {
            if (effects.fade) alpha = animation.alpha
            if (effects.scaleX) scaleX = animation.scale
            if (effects.scaleY) scaleY = animation.scale
            if (effects.translateX) translationX = animation.translationX
            if (effects.translateY) translationY = animation.translationY
            if (effects.rotate) rotationZ = animation.rotation
        }
    }

    /**
     * Interpolate between keyframe values based on progress.
     *
     * @param progress Animation progress 0-1
     * @param keyframes Map of progress (0-1) to values
     * @return Interpolated value
     */
    fun interpolateKeyframes(progress: Float, keyframes: Map<Float, Float>): Float {
        if (keyframes.isEmpty()) return 0f
        if (keyframes.size == 1) return keyframes.values.first()

        val sortedKeys = keyframes.keys.sorted()

        // Find surrounding keyframes
        var lowerKey = sortedKeys.first()
        var upperKey = sortedKeys.last()

        for (key in sortedKeys) {
            if (key <= progress) lowerKey = key
            if (key >= progress) {
                upperKey = key
                break
            }
        }

        if (lowerKey == upperKey) return keyframes[lowerKey] ?: 0f

        val lowerValue = keyframes[lowerKey] ?: 0f
        val upperValue = keyframes[upperKey] ?: 0f

        // Linear interpolation between keyframes
        val localProgress = (progress - lowerKey) / (upperKey - lowerKey)
        return lowerValue + (upperValue - lowerValue) * localProgress
    }

    /**
     * Create keyframe-based animation values.
     *
     * @param progress Animation progress 0-1
     * @param keyframes Keyframe definitions
     * @return KeyframeAnimationValues
     */
    fun createKeyframeAnimation(
        progress: Float,
        keyframes: KeyframeDefinition
    ): KeyframeAnimationValues {
        return KeyframeAnimationValues(
            opacity = keyframes.opacity?.let { interpolateKeyframes(progress, it) } ?: 1f,
            translateX = keyframes.translateX?.let { interpolateKeyframes(progress, it) } ?: 0f,
            translateY = keyframes.translateY?.let { interpolateKeyframes(progress, it) } ?: 0f,
            scale = keyframes.scale?.let { interpolateKeyframes(progress, it) } ?: 1f,
            rotation = keyframes.rotation?.let { interpolateKeyframes(progress, it) } ?: 0f
        )
    }
}

/**
 * Scroll-driven animation values.
 */
data class ScrollDrivenAnimation(
    /** Final animated progress after easing and direction */
    val progress: Float,
    /** Opacity/alpha value */
    val alpha: Float,
    /** Scale factor */
    val scale: Float,
    /** Vertical translation */
    val translationY: Float,
    /** Horizontal translation */
    val translationX: Float,
    /** Rotation in degrees */
    val rotation: Float,
    /** Raw scroll progress before easing */
    val rawProgress: Float,
    /** Progress after easing but before direction */
    val easedProgress: Float
)

/**
 * View-timeline-driven animation.
 */
data class ViewDrivenAnimation(
    /** Modifier to attach for position tracking */
    val modifier: Modifier,
    /** Animation values for entry phase */
    val entryAnimation: ScrollDrivenAnimation,
    /** Animation values for exit phase */
    val exitAnimation: ScrollDrivenAnimation,
    /** Animation values for full cover range */
    val coverAnimation: ScrollDrivenAnimation,
    /** Current visibility phase */
    val phase: ViewTimelinePhase,
    /** Raw entry progress */
    val rawEntryProgress: Float,
    /** Raw exit progress */
    val rawExitProgress: Float,
    /** Raw cover progress */
    val rawCoverProgress: Float
) {
    /** Get the appropriate animation for current phase */
    val currentAnimation: ScrollDrivenAnimation
        get() = when (phase) {
            ViewTimelinePhase.ENTERING -> entryAnimation
            ViewTimelinePhase.EXITING -> exitAnimation
            else -> coverAnimation
        }
}

/**
 * Configuration for which animation effects to apply.
 */
data class AnimationEffects(
    val fade: Boolean = false,
    val scaleX: Boolean = false,
    val scaleY: Boolean = false,
    val translateX: Boolean = false,
    val translateY: Boolean = false,
    val rotate: Boolean = false
) {
    companion object {
        val None = AnimationEffects()
        val FadeIn = AnimationEffects(fade = true)
        val ScaleUp = AnimationEffects(scaleX = true, scaleY = true)
        val SlideUp = AnimationEffects(translateY = true)
        val FadeAndSlide = AnimationEffects(fade = true, translateY = true)
        val All = AnimationEffects(true, true, true, true, true, true)
    }
}

/**
 * Keyframe definition for custom animations.
 */
data class KeyframeDefinition(
    /** Opacity keyframes: progress (0-1) to opacity (0-1) */
    val opacity: Map<Float, Float>? = null,
    /** TranslateX keyframes: progress to pixels */
    val translateX: Map<Float, Float>? = null,
    /** TranslateY keyframes: progress to pixels */
    val translateY: Map<Float, Float>? = null,
    /** Scale keyframes: progress to scale factor */
    val scale: Map<Float, Float>? = null,
    /** Rotation keyframes: progress to degrees */
    val rotation: Map<Float, Float>? = null
) {
    companion object {
        /** Fade in animation */
        val FadeIn = KeyframeDefinition(
            opacity = mapOf(0f to 0f, 1f to 1f)
        )

        /** Slide up with fade */
        val SlideUpFade = KeyframeDefinition(
            opacity = mapOf(0f to 0f, 1f to 1f),
            translateY = mapOf(0f to 50f, 1f to 0f)
        )

        /** Scale up animation */
        val ScaleUp = KeyframeDefinition(
            scale = mapOf(0f to 0.5f, 1f to 1f)
        )

        /** Bounce effect */
        val Bounce = KeyframeDefinition(
            translateY = mapOf(
                0f to 0f,
                0.4f to -30f,
                0.6f to -15f,
                0.8f to -4f,
                1f to 0f
            )
        )

        /** Rotate in */
        val RotateIn = KeyframeDefinition(
            rotation = mapOf(0f to -180f, 1f to 0f),
            opacity = mapOf(0f to 0f, 1f to 1f)
        )
    }
}

/**
 * Values from keyframe interpolation.
 */
data class KeyframeAnimationValues(
    val opacity: Float,
    val translateX: Float,
    val translateY: Float,
    val scale: Float,
    val rotation: Float
)

/**
 * Cubic bezier easing function.
 */
private class CubicBezierEasing(
    private val x1: Float,
    private val y1: Float,
    private val x2: Float,
    private val y2: Float
) : Easing {
    override fun transform(fraction: Float): Float {
        // Binary search for t where bezierX(t) = fraction
        var t = fraction
        for (i in 0 until 10) {
            val x = bezierX(t)
            val dx = x - fraction
            if (kotlin.math.abs(dx) < 0.001f) break
            t -= dx / bezierDerivativeX(t)
            t = t.coerceIn(0f, 1f)
        }
        return bezierY(t)
    }

    private fun bezierX(t: Float): Float {
        val mt = 1f - t
        return 3f * mt * mt * t * x1 + 3f * mt * t * t * x2 + t * t * t
    }

    private fun bezierY(t: Float): Float {
        val mt = 1f - t
        return 3f * mt * mt * t * y1 + 3f * mt * t * t * y2 + t * t * t
    }

    private fun bezierDerivativeX(t: Float): Float {
        val mt = 1f - t
        return 3f * mt * mt * x1 + 6f * mt * t * (x2 - x1) + 3f * t * t * (1f - x2)
    }
}

/**
 * Steps easing function (approximation).
 */
private class StepsEasing(
    private val steps: Int,
    private val position: String
) : Easing {
    override fun transform(fraction: Float): Float {
        val step = (fraction * steps).toInt().coerceIn(0, steps - 1)
        return when (position) {
            "start", "jump-start" -> (step + 1).toFloat() / steps
            "end", "jump-end" -> step.toFloat() / steps
            "jump-both" -> (step + 1).toFloat() / (steps + 1)
            "jump-none" -> if (steps > 1) step.toFloat() / (steps - 1) else fraction
            else -> step.toFloat() / steps
        }
    }
}

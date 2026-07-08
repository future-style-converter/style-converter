package com.styleconverter.test.style.animations

import androidx.compose.animation.core.*

/**
 * Helper for applying animation configuration to Compose.
 *
 * Note: CSS animations are @keyframes-based, while Compose uses state-driven
 * animations. This applier provides utilities to map CSS animation parameters
 * to Compose animation specs.
 */
object AnimationApplier {

    /**
     * Create a tween animation spec from timing function config.
     */
    fun <T> createTweenSpec(
        config: AnimationConfig,
        index: Int = 0
    ): TweenSpec<T> {
        val durationMs = config.getDuration(index).toInt()
        val delayMs = config.getDelay(index).toInt()
        val easing = config.getTimingFunction(index).toEasing()

        return tween(
            durationMillis = durationMs,
            delayMillis = delayMs,
            easing = easing
        )
    }

    /**
     * Create a repeatable animation spec for finite iterations.
     */
    fun <T> createRepeatableSpec(
        config: AnimationConfig,
        index: Int = 0
    ): RepeatableSpec<T>? {
        val iterationCount = config.getIterationCount(index)
        if (iterationCount is AnimationIterationCount.Infinite) {
            return null // Use infiniteRepeatable instead
        }

        val count = (iterationCount as? AnimationIterationCount.Count)?.value?.toInt() ?: 1
        if (count <= 1) return null

        return repeatable(
            iterations = count,
            animation = createTweenSpec(config, index),
            repeatMode = config.getDirection(index).toRepeatMode()
        )
    }

    /**
     * Create an infinite repeatable animation spec.
     */
    fun <T> createInfiniteRepeatableSpec(
        config: AnimationConfig,
        index: Int = 0
    ): InfiniteRepeatableSpec<T> {
        return infiniteRepeatable(
            animation = createTweenSpec(config, index),
            repeatMode = config.getDirection(index).toRepeatMode()
        )
    }

    /**
     * Create a transition tween spec.
     */
    fun <T> createTransitionTweenSpec(
        config: TransitionConfig,
        propertyIndex: Int = 0
    ): TweenSpec<T> {
        val durationMs = config.getDuration(propertyIndex).toInt()
        val delayMs = config.getDelay(propertyIndex).toInt()
        val easing = config.getTimingFunction(propertyIndex).toEasing()

        return tween(
            durationMillis = durationMs,
            delayMillis = delayMs,
            easing = easing
        )
    }
}

/**
 * Convert timing function config to Compose Easing.
 */
fun TimingFunctionConfig.toEasing(): Easing {
    // Handle steps (approximation)
    if (isSteps && stepsCount != null) {
        // Steps can't be perfectly mapped to Compose
        // Use a linear approximation
        return LinearEasing
    }

    // Handle cubic-bezier
    val cb = cubicBezier
    if (cb != null && cb.size == 4) {
        return CubicBezierEasing(
            cb[0].toFloat(),
            cb[1].toFloat(),
            cb[2].toFloat(),
            cb[3].toFloat()
        )
    }

    // Fallback based on original keyword
    return when (original?.lowercase()) {
        "linear" -> LinearEasing
        "ease" -> FastOutSlowInEasing
        "ease-in" -> FastOutLinearInEasing
        "ease-out" -> LinearOutSlowInEasing
        "ease-in-out" -> FastOutSlowInEasing
        else -> FastOutSlowInEasing
    }
}

/**
 * Convert animation direction to Compose RepeatMode.
 */
fun AnimationDirection.toRepeatMode(): RepeatMode {
    return when (this) {
        AnimationDirection.NORMAL -> RepeatMode.Restart
        AnimationDirection.REVERSE -> RepeatMode.Reverse
        AnimationDirection.ALTERNATE -> RepeatMode.Reverse
        AnimationDirection.ALTERNATE_REVERSE -> RepeatMode.Reverse
    }
}

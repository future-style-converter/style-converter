package com.styleconverter.test.style.animations

/**
 * Complete animation configuration extracted from CSS animation properties.
 *
 * ## Supported Properties
 * - animation-name: keyframe references
 * - animation-duration: timing
 * - animation-timing-function: easing
 * - animation-delay: start delay
 * - animation-iteration-count: repeat count
 * - animation-direction: playback direction
 * - animation-fill-mode: before/after styles
 * - animation-play-state: running/paused
 *
 * ## Compose Mapping
 * ```kotlin
 * val config = AnimationExtractor.extractAnimationConfig(properties)
 *
 * // Infinite animation
 * if (config.hasInfiniteAnimation()) {
 *     val infiniteTransition = rememberInfiniteTransition()
 *     val value by infiniteTransition.animateFloat(
 *         initialValue = 0f,
 *         targetValue = 1f,
 *         animationSpec = infiniteRepeatable(
 *             animation = tween(
 *                 durationMillis = config.getDuration(0).toInt(),
 *                 delayMillis = config.getDelay(0).toInt(),
 *                 easing = config.getTimingFunction(0).toEasing()
 *             ),
 *             repeatMode = config.getDirection(0).toRepeatMode()
 *         )
 *     )
 * }
 * ```
 */
data class AnimationConfig(
    /** Animation names (references to @keyframes) */
    val names: List<String> = emptyList(),
    /** Duration in milliseconds for each animation */
    val durations: List<Long> = emptyList(),
    /** Timing functions (easing) for each animation */
    val timingFunctions: List<TimingFunctionConfig> = emptyList(),
    /** Delay in milliseconds before each animation starts */
    val delays: List<Long> = emptyList(),
    /** Iteration count for each animation */
    val iterationCounts: List<AnimationIterationCount> = emptyList(),
    /** Direction for each animation */
    val directions: List<AnimationDirection> = emptyList(),
    /** Fill mode for each animation */
    val fillModes: List<AnimationFillMode> = emptyList(),
    /** Play state for each animation */
    val playStates: List<AnimationPlayState> = emptyList(),
    /** Whether any animation properties were defined */
    val hasAnimations: Boolean = false
) {
    /** Get the effective duration for animation at index */
    fun getDuration(index: Int): Long = durations.getOrElse(index) { durations.firstOrNull() ?: 0L }

    /** Get the effective delay for animation at index */
    fun getDelay(index: Int): Long = delays.getOrElse(index) { delays.firstOrNull() ?: 0L }

    /** Get the effective timing function for animation at index */
    fun getTimingFunction(index: Int): TimingFunctionConfig =
        timingFunctions.getOrElse(index) { timingFunctions.firstOrNull() ?: TimingFunctionConfig.EASE }

    /** Get the effective iteration count for animation at index */
    fun getIterationCount(index: Int): AnimationIterationCount =
        iterationCounts.getOrElse(index) { iterationCounts.firstOrNull() ?: AnimationIterationCount.Count(1.0) }

    /** Get the effective direction for animation at index */
    fun getDirection(index: Int): AnimationDirection =
        directions.getOrElse(index) { directions.firstOrNull() ?: AnimationDirection.NORMAL }

    /** Get the effective fill mode for animation at index */
    fun getFillMode(index: Int): AnimationFillMode =
        fillModes.getOrElse(index) { fillModes.firstOrNull() ?: AnimationFillMode.NONE }

    /** Get the effective play state for animation at index */
    fun getPlayState(index: Int): AnimationPlayState =
        playStates.getOrElse(index) { playStates.firstOrNull() ?: AnimationPlayState.RUNNING }

    /** Check if any animation should run infinitely */
    fun hasInfiniteAnimation(): Boolean =
        iterationCounts.any { it is AnimationIterationCount.Infinite }

    /** Get the number of animations defined */
    val animationCount: Int
        get() = names.size
}

/**
 * CSS animation-direction values.
 */
enum class AnimationDirection {
    /** Plays forward each iteration */
    NORMAL,
    /** Plays backward each iteration */
    REVERSE,
    /** Plays forward, then backward, alternating */
    ALTERNATE,
    /** Plays backward, then forward, alternating */
    ALTERNATE_REVERSE
}

/**
 * CSS animation-fill-mode values.
 */
enum class AnimationFillMode {
    /** No styles applied outside animation */
    NONE,
    /** Retains final keyframe styles after animation */
    FORWARDS,
    /** Applies initial keyframe styles before animation */
    BACKWARDS,
    /** Applies both forwards and backwards behavior */
    BOTH
}

/**
 * CSS animation-play-state values.
 */
enum class AnimationPlayState {
    /** Animation is playing */
    RUNNING,
    /** Animation is paused */
    PAUSED
}

/**
 * CSS animation-iteration-count value.
 */
sealed interface AnimationIterationCount {
    data object Infinite : AnimationIterationCount
    data class Count(val value: Double) : AnimationIterationCount
}

/**
 * Timing function configuration.
 * Normalized to cubic-bezier control points when possible.
 */
data class TimingFunctionConfig(
    /** Cubic bezier control points (x1, y1, x2, y2), null for steps */
    val cubicBezier: List<Double>?,
    /** Steps count, null for cubic-bezier */
    val stepsCount: Int?,
    /** Steps position (start, end, jump-start, etc.), null for cubic-bezier */
    val stepsPosition: String?,
    /** Original keyword for reference */
    val original: String?
) {
    companion object {
        val LINEAR = TimingFunctionConfig(listOf(0.0, 0.0, 1.0, 1.0), null, null, "linear")
        val EASE = TimingFunctionConfig(listOf(0.25, 0.1, 0.25, 1.0), null, null, "ease")
        val EASE_IN = TimingFunctionConfig(listOf(0.42, 0.0, 1.0, 1.0), null, null, "ease-in")
        val EASE_OUT = TimingFunctionConfig(listOf(0.0, 0.0, 0.58, 1.0), null, null, "ease-out")
        val EASE_IN_OUT = TimingFunctionConfig(listOf(0.42, 0.0, 0.58, 1.0), null, null, "ease-in-out")
    }

    /** Check if this is a steps-based timing function */
    val isSteps: Boolean
        get() = stepsCount != null

    /** Check if this is a cubic-bezier timing function */
    val isCubicBezier: Boolean
        get() = cubicBezier != null
}

/**
 * CSS transition configuration.
 */
data class TransitionConfig(
    /** Properties to transition */
    val properties: List<String> = emptyList(),
    /** Duration in milliseconds for each property */
    val durations: List<Long> = emptyList(),
    /** Timing functions for each property */
    val timingFunctions: List<TimingFunctionConfig> = emptyList(),
    /** Delays in milliseconds for each property */
    val delays: List<Long> = emptyList(),
    /** Transition behavior (normal, allow-discrete) */
    val behaviors: List<TransitionBehavior> = emptyList(),
    /** Whether any transition is defined */
    val hasTransitions: Boolean = false
) {
    /** Get effective duration for property at index */
    fun getDuration(index: Int): Long = durations.getOrElse(index) { durations.firstOrNull() ?: 0L }

    /** Get effective delay for property at index */
    fun getDelay(index: Int): Long = delays.getOrElse(index) { delays.firstOrNull() ?: 0L }

    /** Get effective timing function for property at index */
    fun getTimingFunction(index: Int): TimingFunctionConfig =
        timingFunctions.getOrElse(index) { timingFunctions.firstOrNull() ?: TimingFunctionConfig.EASE }

    /** Check if a specific property has transition */
    fun hasTransitionFor(property: String): Boolean {
        return properties.isEmpty() || // "all" case
                properties.any { it == "all" || it == property }
    }
}

/**
 * CSS transition-behavior values.
 */
enum class TransitionBehavior {
    /** Normal transition (only continuous properties) */
    NORMAL,
    /** Allow discrete property transitions */
    ALLOW_DISCRETE
}

/**
 * CSS transition-property target value.
 */
sealed interface TransitionTargetValue {
    /** Transition all animatable properties */
    data object All : TransitionTargetValue
    /** No transitions */
    data object None : TransitionTargetValue
    /** Transition specific properties */
    data class Properties(val names: List<String>) : TransitionTargetValue
}

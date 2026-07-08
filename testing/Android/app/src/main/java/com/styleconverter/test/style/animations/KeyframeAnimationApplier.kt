package com.styleconverter.test.style.animations

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Applies CSS @keyframes animations to Compose modifiers.
 *
 * ## CSS Features
 * ```css
 * @keyframes customAnimation {
 *     0% { opacity: 0; transform: translateY(-20px); }
 *     50% { opacity: 0.5; }
 *     100% { opacity: 1; transform: translateY(0); }
 * }
 *
 * .element {
 *     animation: customAnimation 1s ease-in-out infinite;
 * }
 * ```
 *
 * ## Usage
 * ```kotlin
 * val modifier = KeyframeAnimationApplier.applyKeyframeAnimation(
 *     baseModifier = Modifier.size(100.dp),
 *     animationName = "customAnimation",
 *     config = animationConfig
 * )
 * ```
 */
object KeyframeAnimationApplier {

    /**
     * Apply a keyframe animation to a modifier.
     *
     * @param baseModifier The base modifier to animate
     * @param animationName Name of the @keyframes to apply
     * @param config Animation configuration (duration, timing, etc.)
     * @return Animated modifier
     */
    @Composable
    fun applyKeyframeAnimation(
        baseModifier: Modifier,
        animationName: String,
        config: AnimationConfig
    ): Modifier {
        val keyframes = KeyframeRegistry.get(animationName) ?: return baseModifier

        if (!keyframes.isValid) return baseModifier

        val isInfinite = config.hasInfiniteAnimation()
        val duration = config.getDuration(0).toInt().coerceAtLeast(100)
        val delay = config.getDelay(0).toInt()
        val easing = config.getTimingFunction(0).toEasing()
        val direction = config.getDirection(0)
        val playState = config.getPlayState(0)

        // Check if animation is paused
        if (playState == AnimationPlayState.PAUSED) {
            // Apply first keyframe state when paused
            val initial = keyframes.interpolate(0f)
            return applyInterpolatedState(baseModifier, initial)
        }

        return if (isInfinite) {
            applyInfiniteAnimation(baseModifier, keyframes, duration, delay, easing, direction)
        } else {
            val iterations = when (val count = config.getIterationCount(0)) {
                is AnimationIterationCount.Count -> count.value.toInt().coerceAtLeast(1)
                AnimationIterationCount.Infinite -> Int.MAX_VALUE
            }
            val fillMode = config.getFillMode(0)
            applyFiniteAnimation(baseModifier, keyframes, duration, delay, easing, direction, iterations, fillMode)
        }
    }

    /**
     * Apply infinite keyframe animation.
     */
    @Composable
    private fun applyInfiniteAnimation(
        baseModifier: Modifier,
        keyframes: CSSKeyframes,
        duration: Int,
        delay: Int,
        easing: Easing,
        direction: AnimationDirection
    ): Modifier {
        val infiniteTransition = rememberInfiniteTransition(label = keyframes.name)

        val repeatMode = when (direction) {
            AnimationDirection.ALTERNATE, AnimationDirection.ALTERNATE_REVERSE -> RepeatMode.Reverse
            else -> RepeatMode.Restart
        }

        val progress by infiniteTransition.animateFloat(
            initialValue = if (direction == AnimationDirection.REVERSE || direction == AnimationDirection.ALTERNATE_REVERSE) 1f else 0f,
            targetValue = if (direction == AnimationDirection.REVERSE || direction == AnimationDirection.ALTERNATE_REVERSE) 0f else 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = duration,
                    delayMillis = delay,
                    easing = easing
                ),
                repeatMode = repeatMode
            ),
            label = "${keyframes.name}_progress"
        )

        val interpolated = keyframes.interpolate(progress)
        return applyInterpolatedState(baseModifier, interpolated)
    }

    /**
     * Apply finite (non-infinite) keyframe animation with fill-mode support.
     */
    @Composable
    private fun applyFiniteAnimation(
        baseModifier: Modifier,
        keyframes: CSSKeyframes,
        duration: Int,
        delay: Int,
        easing: Easing,
        direction: AnimationDirection,
        iterations: Int,
        fillMode: AnimationFillMode = AnimationFillMode.NONE
    ): Modifier {
        var animationState by remember { mutableStateOf(AnimationState.NOT_STARTED) }
        var currentIteration by remember { mutableIntStateOf(0) }

        val animatable = remember { Animatable(0f) }

        // Determine initial and final values based on direction
        val (initialProgress, finalProgress) = when (direction) {
            AnimationDirection.REVERSE, AnimationDirection.ALTERNATE_REVERSE -> 1f to 0f
            else -> 0f to 1f
        }

        // Apply fill-mode: backwards - apply initial keyframe during delay
        val shouldApplyBackwards = fillMode == AnimationFillMode.BACKWARDS || fillMode == AnimationFillMode.BOTH
        val shouldApplyForwards = fillMode == AnimationFillMode.FORWARDS || fillMode == AnimationFillMode.BOTH

        LaunchedEffect(Unit) {
            // Set initial state for backwards fill
            if (shouldApplyBackwards) {
                animatable.snapTo(initialProgress)
            }

            kotlinx.coroutines.delay(delay.toLong())
            animationState = AnimationState.RUNNING

            repeat(iterations) { iteration ->
                currentIteration = iteration
                val isReverse = when (direction) {
                    AnimationDirection.REVERSE -> true
                    AnimationDirection.ALTERNATE -> iteration % 2 == 1
                    AnimationDirection.ALTERNATE_REVERSE -> iteration % 2 == 0
                    else -> false
                }

                if (isReverse) {
                    animatable.snapTo(1f)
                    animatable.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = duration, easing = easing)
                    )
                } else {
                    animatable.snapTo(0f)
                    animatable.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = duration, easing = easing)
                    )
                }
            }

            // Apply fill-mode: forwards - persist final keyframe
            if (shouldApplyForwards) {
                // Keep the final value (already set by animation)
            } else {
                // Reset to initial state
                animatable.snapTo(0f)
            }

            animationState = AnimationState.FINISHED
        }

        val interpolated = keyframes.interpolate(animatable.value)
        return applyInterpolatedState(baseModifier, interpolated)
    }

    /**
     * Apply interpolated keyframe values to a modifier.
     */
    private fun applyInterpolatedState(
        baseModifier: Modifier,
        state: InterpolatedKeyframe
    ): Modifier {
        var modifier = baseModifier

        // Apply opacity
        state.opacity?.let { opacity ->
            modifier = modifier.alpha(opacity.coerceIn(0f, 1f))
        }

        // Apply transforms via graphicsLayer for performance
        val hasTransform = state.hasTransform
        val hasFilters = state.hasFilter

        if (hasTransform || hasFilters) {
            modifier = modifier.graphicsLayer {
                // Transforms
                state.translateX?.let { translationX = it }
                state.translateY?.let { translationY = it }
                state.scaleX?.let { scaleX = it }
                state.scaleY?.let { scaleY = it }
                state.rotateZ?.let { rotationZ = it }
                state.rotateX?.let { rotationX = it }
                state.rotateY?.let { rotationY = it }

                // Apply filter effects via ColorMatrix
                if (hasFilters) {
                    val matrix = buildColorMatrix(state)
                    if (matrix != null) {
                        // Note: RenderEffect is Android 12+ only
                        // For broader compatibility, we apply what we can
                    }
                }
            }
        }

        // Apply blur filter (separate as it uses Modifier.blur)
        state.blur?.let { blurRadius ->
            if (blurRadius > 0) {
                modifier = modifier.blur(blurRadius.dp)
            }
        }

        // Apply background color
        state.backgroundColor?.let { color ->
            modifier = modifier.background(color)
        }

        // Apply size if present
        state.width?.let { width ->
            modifier = modifier.width(width.dp)
        }
        state.height?.let { height ->
            modifier = modifier.height(height.dp)
        }

        // Apply position offset
        val hasOffset = state.left != null || state.top != null
        if (hasOffset) {
            modifier = modifier.offset {
                IntOffset(
                    x = state.left?.dp?.roundToPx() ?: 0,
                    y = state.top?.dp?.roundToPx() ?: 0
                )
            }
        }

        return modifier
    }

    /**
     * Build a ColorMatrix for filter effects.
     *
     * Supports: brightness, contrast, grayscale, hue-rotate, saturate, sepia
     */
    private fun buildColorMatrix(state: InterpolatedKeyframe): ColorMatrix? {
        val hasFilters = state.brightness != null || state.contrast != null ||
                state.grayscale != null || state.hueRotate != null ||
                state.saturate != null || state.sepia != null

        if (!hasFilters) return null

        val matrix = ColorMatrix()

        // Apply brightness (0-200, 100 = normal)
        state.brightness?.let { brightness ->
            val factor = brightness / 100f
            val brightnessMatrix = ColorMatrix(floatArrayOf(
                factor, 0f, 0f, 0f, 0f,
                0f, factor, 0f, 0f, 0f,
                0f, 0f, factor, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.timesAssign(brightnessMatrix)
        }

        // Apply contrast (0-200, 100 = normal)
        state.contrast?.let { contrast ->
            val factor = contrast / 100f
            val translate = 128f * (1f - factor)
            val contrastMatrix = ColorMatrix(floatArrayOf(
                factor, 0f, 0f, 0f, translate,
                0f, factor, 0f, 0f, translate,
                0f, 0f, factor, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.timesAssign(contrastMatrix)
        }

        // Apply grayscale (0-100)
        state.grayscale?.let { grayscale ->
            val amount = (grayscale / 100f).coerceIn(0f, 1f)
            val invAmount = 1f - amount
            val grayscaleMatrix = ColorMatrix(floatArrayOf(
                0.2126f + 0.7874f * invAmount, 0.7152f - 0.7152f * invAmount, 0.0722f - 0.0722f * invAmount, 0f, 0f,
                0.2126f - 0.2126f * invAmount, 0.7152f + 0.2848f * invAmount, 0.0722f - 0.0722f * invAmount, 0f, 0f,
                0.2126f - 0.2126f * invAmount, 0.7152f - 0.7152f * invAmount, 0.0722f + 0.9278f * invAmount, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.timesAssign(grayscaleMatrix)
        }

        // Apply hue-rotate (degrees)
        state.hueRotate?.let { degrees ->
            val radians = Math.toRadians(degrees.toDouble()).toFloat()
            val cosVal = cos(radians)
            val sinVal = sin(radians)

            val hueMatrix = ColorMatrix(floatArrayOf(
                0.213f + cosVal * 0.787f - sinVal * 0.213f,
                0.715f - cosVal * 0.715f - sinVal * 0.715f,
                0.072f - cosVal * 0.072f + sinVal * 0.928f,
                0f, 0f,
                0.213f - cosVal * 0.213f + sinVal * 0.143f,
                0.715f + cosVal * 0.285f + sinVal * 0.140f,
                0.072f - cosVal * 0.072f - sinVal * 0.283f,
                0f, 0f,
                0.213f - cosVal * 0.213f - sinVal * 0.787f,
                0.715f - cosVal * 0.715f + sinVal * 0.715f,
                0.072f + cosVal * 0.928f + sinVal * 0.072f,
                0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.timesAssign(hueMatrix)
        }

        // Apply saturate (0-200, 100 = normal)
        state.saturate?.let { saturate ->
            val amount = saturate / 100f
            val invAmount = 1f - amount
            val saturateMatrix = ColorMatrix(floatArrayOf(
                0.2126f + 0.7874f * amount, 0.7152f - 0.7152f * amount, 0.0722f - 0.0722f * amount, 0f, 0f,
                0.2126f - 0.2126f * amount, 0.7152f + 0.2848f * amount, 0.0722f - 0.0722f * amount, 0f, 0f,
                0.2126f - 0.2126f * amount, 0.7152f - 0.7152f * amount, 0.0722f + 0.9278f * amount, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.timesAssign(saturateMatrix)
        }

        // Apply sepia (0-100)
        state.sepia?.let { sepia ->
            val amount = (sepia / 100f).coerceIn(0f, 1f)
            val invAmount = 1f - amount
            val sepiaMatrix = ColorMatrix(floatArrayOf(
                0.393f + 0.607f * invAmount, 0.769f - 0.769f * invAmount, 0.189f - 0.189f * invAmount, 0f, 0f,
                0.349f - 0.349f * invAmount, 0.686f + 0.314f * invAmount, 0.168f - 0.168f * invAmount, 0f, 0f,
                0.272f - 0.272f * invAmount, 0.534f - 0.534f * invAmount, 0.131f + 0.869f * invAmount, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            matrix.timesAssign(sepiaMatrix)
        }

        return matrix
    }

    /**
     * Create an animated value from keyframes.
     * Useful for more complex animations that need manual control.
     */
    @Composable
    fun rememberKeyframeAnimationState(
        animationName: String,
        config: AnimationConfig
    ): State<InterpolatedKeyframe> {
        val keyframes = KeyframeRegistry.get(animationName)

        return if (keyframes == null || !keyframes.isValid) {
            remember { mutableStateOf(InterpolatedKeyframe()) }
        } else {
            val isInfinite = config.hasInfiniteAnimation()
            val duration = config.getDuration(0).toInt().coerceAtLeast(100)
            val easing = config.getTimingFunction(0).toEasing()

            if (isInfinite) {
                val infiniteTransition = rememberInfiniteTransition(label = animationName)
                val progress by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = duration, easing = easing),
                        repeatMode = config.getDirection(0).toRepeatMode()
                    ),
                    label = "${animationName}_progress"
                )

                remember { derivedStateOf { keyframes.interpolate(progress) } }
            } else {
                val animatable = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    animatable.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = duration, easing = easing)
                    )
                }
                remember { derivedStateOf { keyframes.interpolate(animatable.value) } }
            }
        }
    }

    /**
     * Animation state for finite animations.
     */
    private enum class AnimationState {
        NOT_STARTED,
        RUNNING,
        FINISHED
    }
}

// Note: toEasing() and toRepeatMode() extension functions are defined in AnimationApplier.kt

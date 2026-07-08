package com.styleconverter.test.style.animations

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Composable function that creates an animated modifier based on AnimationConfig.
 *
 * CSS animations are keyframe-based, while Compose uses state-driven animations.
 * This provides a bridge by animating common properties like:
 * - opacity (fade in/out)
 * - rotation (spin)
 * - scale (grow/shrink)
 * - translation (slide)
 *
 * ## Usage
 * ```kotlin
 * val animatedModifier = animatedModifier(
 *     baseModifier = Modifier.size(100.dp),
 *     animationConfig = config,
 *     animationName = "fadeIn"
 * )
 * Box(modifier = animatedModifier) { ... }
 * ```
 */
@Composable
fun animatedModifier(
    baseModifier: Modifier,
    animationConfig: AnimationConfig,
    transitionConfig: TransitionConfig = TransitionConfig()
): Modifier {
    if (!animationConfig.hasAnimations) {
        return baseModifier
    }

    // Get animation name
    val animationName = animationConfig.names.firstOrNull() ?: return baseModifier

    // First, try to find registered keyframes
    val keyframes = KeyframeRegistry.get(animationName)
    if (keyframes != null && keyframes.isValid) {
        return KeyframeAnimationApplier.applyKeyframeAnimation(
            baseModifier = baseModifier,
            animationName = animationName,
            config = animationConfig
        )
    }

    // No registered @keyframes for this animation name.
    //
    // iOS + Web render this case as identity (no visible animation without
    // a @keyframes definition) — CSS animations without keyframes are a
    // no-op per spec. The previous Android fallback used substring matching
    // ("fade", "spin", "pulse", "slide", "bounce", "shake", "rotate",
    // "scale") on the animation-name to synthesize a built-in animation
    // from first principles. That caused guaranteed cross-platform
    // divergence for any fixture that included those substrings in its
    // animation-name — the Phase 12 audit confirmed this via
    // `AnimName_Multi: fade-in, slide-up, pulse` rendering motion on
    // Android while static on iOS/Web.
    //
    // Matching the no-op behaviour is CSS-spec-correct. If a future phase
    // adds a @keyframes registry + proper parser support, that path goes
    // through KeyframeAnimationApplier above (already wired).
    return baseModifier
}

/**
 * Fade animation (opacity).
 */
@Composable
private fun fadeAnimation(baseModifier: Modifier, config: AnimationConfig): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "fade")

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = config.getDuration(0).toInt().coerceAtLeast(300),
                delayMillis = config.getDelay(0).toInt(),
                easing = config.getTimingFunction(0).toEasing()
            ),
            repeatMode = config.getDirection(0).toRepeatMode()
        ),
        label = "fadeAlpha"
    )

    return baseModifier.alpha(alpha)
}

/**
 * Rotation animation.
 */
@Composable
private fun rotateAnimation(baseModifier: Modifier, config: AnimationConfig): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "rotate")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = config.getDuration(0).toInt().coerceAtLeast(1000),
                delayMillis = config.getDelay(0).toInt(),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    return baseModifier.rotate(rotation)
}

/**
 * Scale/pulse animation.
 */
@Composable
private fun scaleAnimation(baseModifier: Modifier, config: AnimationConfig): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "scale")

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = config.getDuration(0).toInt().coerceAtLeast(500),
                delayMillis = config.getDelay(0).toInt(),
                easing = config.getTimingFunction(0).toEasing()
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    return baseModifier.scale(scale)
}

/**
 * Slide animation (translation).
 */
@Composable
private fun slideAnimation(baseModifier: Modifier, config: AnimationConfig): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "slide")

    val offsetX by infiniteTransition.animateFloat(
        initialValue = -50f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = config.getDuration(0).toInt().coerceAtLeast(500),
                delayMillis = config.getDelay(0).toInt(),
                easing = config.getTimingFunction(0).toEasing()
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetX"
    )

    return baseModifier.graphicsLayer {
        translationX = offsetX
    }
}

/**
 * Bounce animation.
 */
@Composable
private fun bounceAnimation(baseModifier: Modifier, config: AnimationConfig): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "bounce")

    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -20f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = config.getDuration(0).toInt().coerceAtLeast(300),
                delayMillis = config.getDelay(0).toInt(),
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounceY"
    )

    return baseModifier.graphicsLayer {
        translationY = offsetY
    }
}

/**
 * Shake animation.
 */
@Composable
private fun shakeAnimation(baseModifier: Modifier, config: AnimationConfig): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "shake")

    val offsetX by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 100,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shakeX"
    )

    return baseModifier.graphicsLayer {
        translationX = offsetX
    }
}

/**
 * Generic animation combining multiple properties.
 */
@Composable
private fun genericAnimation(baseModifier: Modifier, config: AnimationConfig): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "generic")

    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = config.getDuration(0).toInt().coerceAtLeast(1000),
                delayMillis = config.getDelay(0).toInt(),
                easing = config.getTimingFunction(0).toEasing()
            ),
            repeatMode = config.getDirection(0).toRepeatMode()
        ),
        label = "progress"
    )

    return baseModifier.alpha(0.5f + progress * 0.5f)
}

/**
 * Create a one-shot animation modifier for transitions.
 * Used when a component enters/exits or state changes.
 */
@Composable
fun transitionModifier(
    baseModifier: Modifier,
    transitionConfig: TransitionConfig,
    targetState: Boolean = true
): Modifier {
    if (!transitionConfig.hasTransitions) {
        return baseModifier
    }

    val transition = updateTransition(targetState, label = "stateTransition")

    val alpha by transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = transitionConfig.getDuration(0).toInt().coerceAtLeast(300),
                delayMillis = transitionConfig.getDelay(0).toInt(),
                easing = transitionConfig.getTimingFunction(0).toEasing()
            )
        },
        label = "transitionAlpha"
    ) { state ->
        if (state) 1f else 0f
    }

    return baseModifier.alpha(alpha)
}

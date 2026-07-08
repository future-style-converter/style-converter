package com.styleconverter.test.style.animations

import androidx.compose.ui.graphics.Color

/**
 * CSS @keyframes definition model.
 *
 * ## CSS Example
 * ```css
 * @keyframes fadeSlide {
 *     0% {
 *         opacity: 0;
 *         transform: translateX(-100px);
 *     }
 *     50% {
 *         opacity: 0.5;
 *         transform: translateX(-50px);
 *     }
 *     100% {
 *         opacity: 1;
 *         transform: translateX(0);
 *     }
 * }
 * ```
 *
 * ## Compose Usage
 * ```kotlin
 * val keyframes = KeyframeRegistry.get("fadeSlide")
 * val progress by infiniteTransition.animateFloat(...)
 * val interpolated = keyframes.interpolate(progress)
 * modifier.graphicsLayer {
 *     alpha = interpolated.opacity ?: 1f
 *     translationX = interpolated.translateX ?: 0f
 * }
 * ```
 */
data class CSSKeyframes(
    /** Name of the @keyframes rule */
    val name: String,
    /** List of keyframes at different percentages */
    val keyframes: List<Keyframe>
) {
    /** Check if this keyframe definition is valid */
    val isValid: Boolean get() = keyframes.isNotEmpty()

    /**
     * Interpolate property values at a given progress (0-1).
     */
    fun interpolate(progress: Float): InterpolatedKeyframe {
        if (keyframes.isEmpty()) return InterpolatedKeyframe()

        val percentage = (progress * 100).coerceIn(0f, 100f)

        // Find surrounding keyframes
        val sorted = keyframes.sortedBy { it.percentage }
        val before = sorted.lastOrNull { it.percentage <= percentage } ?: sorted.first()
        val after = sorted.firstOrNull { it.percentage >= percentage } ?: sorted.last()

        // Calculate local progress between keyframes
        val localProgress = if (before.percentage == after.percentage) {
            1f
        } else {
            ((percentage - before.percentage) / (after.percentage - before.percentage)).coerceIn(0f, 1f)
        }

        return interpolateBetween(before, after, localProgress)
    }

    /**
     * Interpolate between two keyframes.
     */
    private fun interpolateBetween(from: Keyframe, to: Keyframe, progress: Float): InterpolatedKeyframe {
        return InterpolatedKeyframe(
            opacity = lerpNullable(from.opacity, to.opacity, progress),
            translateX = lerpNullable(from.translateX, to.translateX, progress),
            translateY = lerpNullable(from.translateY, to.translateY, progress),
            translateZ = lerpNullable(from.translateZ, to.translateZ, progress),
            scaleX = lerpNullable(from.scaleX, to.scaleX, progress),
            scaleY = lerpNullable(from.scaleY, to.scaleY, progress),
            scaleZ = lerpNullable(from.scaleZ, to.scaleZ, progress),
            rotateX = lerpNullable(from.rotateX, to.rotateX, progress),
            rotateY = lerpNullable(from.rotateY, to.rotateY, progress),
            rotateZ = lerpNullable(from.rotateZ, to.rotateZ, progress),
            skewX = lerpNullable(from.skewX, to.skewX, progress),
            skewY = lerpNullable(from.skewY, to.skewY, progress),
            backgroundColor = lerpColorNullable(from.backgroundColor, to.backgroundColor, progress),
            color = lerpColorNullable(from.color, to.color, progress),
            borderColor = lerpColorNullable(from.borderColor, to.borderColor, progress),
            width = lerpNullable(from.width, to.width, progress),
            height = lerpNullable(from.height, to.height, progress),
            top = lerpNullable(from.top, to.top, progress),
            left = lerpNullable(from.left, to.left, progress),
            right = lerpNullable(from.right, to.right, progress),
            bottom = lerpNullable(from.bottom, to.bottom, progress),
            paddingTop = lerpNullable(from.paddingTop, to.paddingTop, progress),
            paddingRight = lerpNullable(from.paddingRight, to.paddingRight, progress),
            paddingBottom = lerpNullable(from.paddingBottom, to.paddingBottom, progress),
            paddingLeft = lerpNullable(from.paddingLeft, to.paddingLeft, progress),
            marginTop = lerpNullable(from.marginTop, to.marginTop, progress),
            marginRight = lerpNullable(from.marginRight, to.marginRight, progress),
            marginBottom = lerpNullable(from.marginBottom, to.marginBottom, progress),
            marginLeft = lerpNullable(from.marginLeft, to.marginLeft, progress),
            borderRadius = lerpNullable(from.borderRadius, to.borderRadius, progress),
            borderWidth = lerpNullable(from.borderWidth, to.borderWidth, progress),
            fontSize = lerpNullable(from.fontSize, to.fontSize, progress),
            letterSpacing = lerpNullable(from.letterSpacing, to.letterSpacing, progress),
            lineHeight = lerpNullable(from.lineHeight, to.lineHeight, progress),
            blur = lerpNullable(from.blur, to.blur, progress),
            brightness = lerpNullable(from.brightness, to.brightness, progress),
            contrast = lerpNullable(from.contrast, to.contrast, progress),
            grayscale = lerpNullable(from.grayscale, to.grayscale, progress),
            hueRotate = lerpNullable(from.hueRotate, to.hueRotate, progress),
            saturate = lerpNullable(from.saturate, to.saturate, progress),
            sepia = lerpNullable(from.sepia, to.sepia, progress)
        )
    }

    private fun lerpNullable(from: Float?, to: Float?, progress: Float): Float? {
        return when {
            from != null && to != null -> from + (to - from) * progress
            from != null -> from
            to != null -> to
            else -> null
        }
    }

    private fun lerpColorNullable(from: Color?, to: Color?, progress: Float): Color? {
        return when {
            from != null && to != null -> lerp(from, to, progress)
            from != null -> from
            to != null -> to
            else -> null
        }
    }

    private fun lerp(start: Color, stop: Color, fraction: Float): Color {
        return Color(
            red = start.red + (stop.red - start.red) * fraction,
            green = start.green + (stop.green - start.green) * fraction,
            blue = start.blue + (stop.blue - start.blue) * fraction,
            alpha = start.alpha + (stop.alpha - start.alpha) * fraction
        )
    }
}

/**
 * A single keyframe at a specific percentage.
 */
data class Keyframe(
    /** Percentage (0-100) for this keyframe */
    val percentage: Float,
    // Transform properties
    val opacity: Float? = null,
    val translateX: Float? = null,
    val translateY: Float? = null,
    val translateZ: Float? = null,
    val scaleX: Float? = null,
    val scaleY: Float? = null,
    val scaleZ: Float? = null,
    val rotateX: Float? = null,
    val rotateY: Float? = null,
    val rotateZ: Float? = null,
    val skewX: Float? = null,
    val skewY: Float? = null,
    // Color properties
    val backgroundColor: Color? = null,
    val color: Color? = null,
    val borderColor: Color? = null,
    // Size properties
    val width: Float? = null,
    val height: Float? = null,
    // Position properties
    val top: Float? = null,
    val left: Float? = null,
    val right: Float? = null,
    val bottom: Float? = null,
    // Spacing properties
    val paddingTop: Float? = null,
    val paddingRight: Float? = null,
    val paddingBottom: Float? = null,
    val paddingLeft: Float? = null,
    val marginTop: Float? = null,
    val marginRight: Float? = null,
    val marginBottom: Float? = null,
    val marginLeft: Float? = null,
    // Border properties
    val borderRadius: Float? = null,
    val borderWidth: Float? = null,
    // Typography properties
    val fontSize: Float? = null,
    val letterSpacing: Float? = null,
    val lineHeight: Float? = null,
    // Filter properties
    val blur: Float? = null,
    val brightness: Float? = null,
    val contrast: Float? = null,
    val grayscale: Float? = null,
    val hueRotate: Float? = null,
    val saturate: Float? = null,
    val sepia: Float? = null
) {
    companion object {
        /** Create a keyframe for "from" (0%) */
        fun from(builder: Keyframe.() -> Keyframe): Keyframe = Keyframe(0f).builder()

        /** Create a keyframe for "to" (100%) */
        fun to(builder: Keyframe.() -> Keyframe): Keyframe = Keyframe(100f).builder()

        /** Create a keyframe at specific percentage */
        fun at(percentage: Float, builder: Keyframe.() -> Keyframe): Keyframe =
            Keyframe(percentage).builder()
    }
}

/**
 * Result of interpolating keyframes at a specific progress.
 * All values are nullable - only animated properties have values.
 */
data class InterpolatedKeyframe(
    // Transform properties
    val opacity: Float? = null,
    val translateX: Float? = null,
    val translateY: Float? = null,
    val translateZ: Float? = null,
    val scaleX: Float? = null,
    val scaleY: Float? = null,
    val scaleZ: Float? = null,
    val rotateX: Float? = null,
    val rotateY: Float? = null,
    val rotateZ: Float? = null,
    val skewX: Float? = null,
    val skewY: Float? = null,
    // Color properties
    val backgroundColor: Color? = null,
    val color: Color? = null,
    val borderColor: Color? = null,
    // Size properties
    val width: Float? = null,
    val height: Float? = null,
    // Position properties
    val top: Float? = null,
    val left: Float? = null,
    val right: Float? = null,
    val bottom: Float? = null,
    // Spacing properties
    val paddingTop: Float? = null,
    val paddingRight: Float? = null,
    val paddingBottom: Float? = null,
    val paddingLeft: Float? = null,
    val marginTop: Float? = null,
    val marginRight: Float? = null,
    val marginBottom: Float? = null,
    val marginLeft: Float? = null,
    // Border properties
    val borderRadius: Float? = null,
    val borderWidth: Float? = null,
    // Typography properties
    val fontSize: Float? = null,
    val letterSpacing: Float? = null,
    val lineHeight: Float? = null,
    // Filter properties
    val blur: Float? = null,
    val brightness: Float? = null,
    val contrast: Float? = null,
    val grayscale: Float? = null,
    val hueRotate: Float? = null,
    val saturate: Float? = null,
    val sepia: Float? = null
) {
    /** Check if any transform property is animated */
    val hasTransform: Boolean get() =
        translateX != null || translateY != null || translateZ != null ||
        scaleX != null || scaleY != null || scaleZ != null ||
        rotateX != null || rotateY != null || rotateZ != null ||
        skewX != null || skewY != null

    /** Check if any color property is animated */
    val hasColor: Boolean get() =
        backgroundColor != null || color != null || borderColor != null

    /** Check if any filter property is animated */
    val hasFilter: Boolean get() =
        blur != null || brightness != null || contrast != null ||
        grayscale != null || hueRotate != null || saturate != null || sepia != null
}

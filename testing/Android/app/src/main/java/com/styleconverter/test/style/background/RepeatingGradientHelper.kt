package com.styleconverter.test.style.background

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import com.styleconverter.test.style.color.ColorStop
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Helper for creating repeating gradient brushes.
 *
 * ## CSS Repeating Gradients
 *
 * CSS supports three types of repeating gradients:
 * - `repeating-linear-gradient()`
 * - `repeating-radial-gradient()`
 * - `repeating-conic-gradient()`
 *
 * Repeating gradients take the color stops and repeat them infinitely in both directions.
 * The repeat pattern is determined by the positions of the color stops.
 *
 * ## Compose Implementation
 *
 * ### Linear Gradients
 * Compose's `Brush.linearGradient()` supports `TileMode.Repeated`, which handles
 * the repeating behavior when proper start/end offsets are calculated.
 *
 * ### Radial Gradients
 * Compose's `Brush.radialGradient()` supports `TileMode.Repeated`, which repeats
 * the gradient outward from the center.
 *
 * ### Conic/Sweep Gradients
 * Compose's `Brush.sweepGradient()` does NOT support TileMode. To simulate repeating,
 * we manually expand the color stops to cover 360 degrees multiple times.
 *
 * ## Example
 * ```kotlin
 * // CSS: repeating-linear-gradient(45deg, red 0px, blue 20px)
 * // This creates red-to-blue stripes every 20px at 45 degrees
 *
 * val brush = RepeatingGradientHelper.createRepeatingLinearGradient(
 *     angle = 45f,
 *     colorStops = listOf(ColorStop(Color.Red, 0f), ColorStop(Color.Blue, 20f / elementSize)),
 *     size = Size(100f, 100f)
 * )
 * ```
 */
object RepeatingGradientHelper {

    /**
     * Create a repeating linear gradient brush.
     *
     * @param angle Gradient angle in degrees (CSS convention: 0deg = to top)
     * @param colorStops Color stops with positions (0-1 range)
     * @param size Size of the target area (for calculating repeat length)
     * @return Brush for the repeating gradient
     */
    fun createRepeatingLinearGradient(
        angle: Float,
        colorStops: List<ColorStop>,
        size: Size = Size(500f, 500f)
    ): Brush? {
        if (colorStops.size < 2) return null

        // Convert CSS angle to radians
        // CSS: 0deg = to top (up), 90deg = to right
        val angleRad = (90 - angle) * PI.toFloat() / 180f

        // Calculate the gradient vector length (diagonal of the element)
        val diagonalLength = sqrt(size.width * size.width + size.height * size.height)

        // Find the pattern length from color stops
        val minPos = colorStops.minOfOrNull { it.position } ?: 0f
        val maxPos = colorStops.maxOfOrNull { it.position } ?: 1f
        val patternLength = (maxPos - minPos).coerceIn(0.01f, 1f) * diagonalLength

        // Calculate start and end points for one pattern
        val centerX = size.width / 2
        val centerY = size.height / 2
        val halfPattern = patternLength / 2

        val startX = centerX - cos(angleRad) * halfPattern
        val startY = centerY + sin(angleRad) * halfPattern
        val endX = centerX + cos(angleRad) * halfPattern
        val endY = centerY - sin(angleRad) * halfPattern

        // Normalize color stops to 0-1 range for this pattern
        val normalizedStops = normalizeColorStops(colorStops)

        return Brush.linearGradient(
            colorStops = normalizedStops,
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            tileMode = TileMode.Repeated
        )
    }

    /**
     * Create a repeating radial gradient brush.
     *
     * @param centerX Center X position (0-1 fraction of width)
     * @param centerY Center Y position (0-1 fraction of height)
     * @param colorStops Color stops with positions (0-1 range)
     * @param size Size of the target area (for calculating radius)
     * @return Brush for the repeating gradient
     */
    fun createRepeatingRadialGradient(
        centerX: Float,
        centerY: Float,
        colorStops: List<ColorStop>,
        size: Size = Size(500f, 500f)
    ): Brush? {
        if (colorStops.size < 2) return null

        // Calculate the center in pixel coordinates
        val center = Offset(centerX * size.width, centerY * size.height)

        // Find the pattern radius from color stops
        val minPos = colorStops.minOfOrNull { it.position } ?: 0f
        val maxPos = colorStops.maxOfOrNull { it.position } ?: 1f
        val patternLength = (maxPos - minPos).coerceIn(0.01f, 1f)

        // Calculate radius for one pattern (based on smaller dimension)
        val baseRadius = minOf(size.width, size.height) / 2
        val patternRadius = patternLength * baseRadius

        // Normalize color stops
        val normalizedStops = normalizeColorStops(colorStops)

        return Brush.radialGradient(
            colorStops = normalizedStops,
            center = center,
            radius = patternRadius,
            tileMode = TileMode.Repeated
        )
    }

    /**
     * Create a repeating conic (sweep) gradient brush.
     *
     * Since Compose's sweepGradient doesn't support TileMode, we simulate repeating
     * by expanding the color stops to fill 360 degrees with multiple repetitions.
     *
     * @param centerX Center X position (0-1 fraction of width)
     * @param centerY Center Y position (0-1 fraction of height)
     * @param startAngle Starting angle in degrees
     * @param colorStops Color stops with positions (0-1 range)
     * @param size Size of the target area (for calculating center)
     * @param repetitions Number of times to repeat the pattern (default: auto-calculated)
     * @return Brush for the repeating gradient
     */
    fun createRepeatingConicGradient(
        centerX: Float,
        centerY: Float,
        startAngle: Float,
        colorStops: List<ColorStop>,
        size: Size = Size(500f, 500f),
        repetitions: Int = 0
    ): Brush? {
        if (colorStops.size < 2) return null

        // Calculate center
        val center = Offset(centerX * size.width, centerY * size.height)

        // Find the pattern coverage
        val minPos = colorStops.minOfOrNull { it.position } ?: 0f
        val maxPos = colorStops.maxOfOrNull { it.position } ?: 1f
        val patternCoverage = (maxPos - minPos).coerceIn(0.01f, 1f)

        // Calculate how many repetitions we need to fill 360 degrees
        val reps = if (repetitions > 0) {
            repetitions
        } else {
            (1f / patternCoverage).toInt().coerceIn(2, 10)
        }

        // Expand color stops for the repetitions
        val expandedStops = mutableListOf<Pair<Float, Color>>()

        for (rep in 0 until reps) {
            val offset = rep.toFloat() / reps

            colorStops.forEach { stop ->
                val newPos = (offset + (stop.position - minPos) / patternCoverage / reps)
                    .coerceIn(0f, 1f)
                expandedStops.add(newPos to stop.color)
            }
        }

        // Sort by position and remove duplicates
        val sortedStops = expandedStops
            .sortedBy { it.first }
            .distinctBy { "%.4f".format(it.first) }
            .toTypedArray()

        // Ensure we have at least 2 stops
        if (sortedStops.size < 2) {
            val first = colorStops.first()
            val last = colorStops.last()
            return Brush.sweepGradient(
                colorStops = arrayOf(0f to first.color, 1f to last.color),
                center = center
            )
        }

        return Brush.sweepGradient(
            colorStops = sortedStops,
            center = center
        )
    }

    /**
     * Normalize color stops to 0-1 range.
     *
     * Ensures first stop is at 0 and last stop is at 1 for proper tiling.
     */
    private fun normalizeColorStops(colorStops: List<ColorStop>): Array<Pair<Float, Color>> {
        if (colorStops.isEmpty()) return emptyArray()

        val minPos = colorStops.minOf { it.position }
        val maxPos = colorStops.maxOf { it.position }
        val range = (maxPos - minPos).coerceAtLeast(0.01f)

        return colorStops.map { stop ->
            val normalizedPos = ((stop.position - minPos) / range).coerceIn(0f, 1f)
            normalizedPos to stop.color
        }.toTypedArray()
    }

    /**
     * Create stripes pattern as a repeating linear gradient.
     *
     * Utility for common stripe patterns like zebra stripes or progress bars.
     *
     * @param color1 First stripe color
     * @param color2 Second stripe color
     * @param angle Stripe angle in degrees
     * @param stripeWidth Width of each stripe as fraction of element size
     * @param size Element size
     * @return Brush for stripe pattern
     */
    fun createStripesPattern(
        color1: Color,
        color2: Color,
        angle: Float = 45f,
        stripeWidth: Float = 0.1f,
        size: Size = Size(500f, 500f)
    ): Brush? {
        // Create hard-edge stripes
        val stops = listOf(
            ColorStop(color1, 0f),
            ColorStop(color1, 0.5f - 0.001f),
            ColorStop(color2, 0.5f),
            ColorStop(color2, 1f - 0.001f),
            ColorStop(color1, 1f)
        )

        return createRepeatingLinearGradient(angle, stops, size)
    }

    /**
     * Create a checkerboard pattern using two sweepGradient layers.
     *
     * Note: True checkerboard requires multiple layers or custom drawing.
     * This provides an approximation using gradients.
     *
     * @param color1 First color
     * @param color2 Second color
     * @param size Element size
     * @return Brush approximating a checkerboard pattern
     */
    fun createCheckerboardApproximation(
        color1: Color,
        color2: Color,
        size: Size = Size(500f, 500f)
    ): Brush? {
        // For true checkerboard, need custom drawing
        // This creates a 4-quadrant pattern as an approximation
        val stops = listOf(
            ColorStop(color1, 0f),
            ColorStop(color1, 0.249f),
            ColorStop(color2, 0.25f),
            ColorStop(color2, 0.499f),
            ColorStop(color1, 0.5f),
            ColorStop(color1, 0.749f),
            ColorStop(color2, 0.75f),
            ColorStop(color2, 1f)
        )

        return createRepeatingConicGradient(
            centerX = 0.5f,
            centerY = 0.5f,
            startAngle = 0f,
            colorStops = stops,
            size = size,
            repetitions = 1
        )
    }
}

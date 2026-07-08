package com.styleconverter.test.style.typography.text

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints

/**
 * Applies writing mode properties to Compose modifiers and layouts.
 *
 * ## CSS writing-mode Support
 * - horizontal-tb: Default horizontal text (no modification needed)
 * - vertical-rl: Vertical text, right-to-left columns (traditional CJK)
 * - vertical-lr: Vertical text, left-to-right columns
 * - sideways-rl/lr: Rotated text
 *
 * ## Compose Limitations
 * Compose doesn't natively support vertical text flow. We approximate it using:
 * - graphicsLayer rotation for sideways modes
 * - Custom Layout for true vertical text (swaps width/height constraints)
 *
 * ## Usage
 * ```kotlin
 * val config = TextExtractor.extractWritingModeConfig(properties)
 *
 * // Option 1: Apply rotation modifier
 * val modifier = WritingModeApplier.applyWritingMode(Modifier, config)
 *
 * // Option 2: Use vertical text wrapper
 * WritingModeApplier.VerticalTextWrapper(config) {
 *     Text("Vertical text")
 * }
 * ```
 */
object WritingModeApplier {

    /**
     * Apply writing mode as a rotation modifier.
     *
     * This is a simple approach that rotates the entire content.
     * For sideways modes, this works well.
     * For true vertical-rl/lr, consider using VerticalTextWrapper instead.
     */
    fun applyWritingMode(modifier: Modifier, config: WritingModeConfig): Modifier {
        if (!config.hasWritingMode || !config.isVertical) {
            return modifier
        }

        val rotation = when (config.writingMode) {
            WritingModeValue.VERTICAL_RL -> 90f
            WritingModeValue.VERTICAL_LR -> -90f
            WritingModeValue.SIDEWAYS_RL -> 90f
            WritingModeValue.SIDEWAYS_LR -> -90f
            else -> 0f
        }

        return if (rotation != 0f) {
            modifier.graphicsLayer {
                rotationZ = rotation
            }
        } else {
            modifier
        }
    }

    /**
     * Apply writing mode with proper constraint swapping for vertical text.
     *
     * This modifier swaps width/height constraints so that text
     * can flow vertically while maintaining proper sizing.
     */
    fun applyVerticalTextModifier(modifier: Modifier, config: WritingModeConfig): Modifier {
        if (!config.isVertical) return modifier

        return modifier.then(VerticalTextModifier(config))
    }

    /**
     * A wrapper composable for vertical text content.
     *
     * This handles proper constraint swapping and rotation for
     * true vertical text layout (CJK languages).
     */
    @Composable
    fun VerticalTextWrapper(
        config: WritingModeConfig,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) {
        if (!config.isVertical) {
            Box(modifier = modifier) {
                content()
            }
            return
        }

        val rotation = when (config.writingMode) {
            WritingModeValue.VERTICAL_RL -> 90f
            WritingModeValue.VERTICAL_LR -> -90f
            WritingModeValue.SIDEWAYS_RL -> 90f
            WritingModeValue.SIDEWAYS_LR -> -90f
            else -> 0f
        }

        // Custom layout that swaps constraints and rotates content
        Layout(
            content = content,
            modifier = modifier.graphicsLayer { rotationZ = rotation }
        ) { measurables, constraints ->
            // Swap width and height constraints for vertical text
            val swappedConstraints = Constraints(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth
            )

            val placeables = measurables.map { it.measure(swappedConstraints) }

            // Layout dimensions are swapped
            val width = placeables.maxOfOrNull { it.height } ?: 0
            val height = placeables.maxOfOrNull { it.width } ?: 0

            layout(width, height) {
                placeables.forEach { placeable ->
                    // Center the rotated content
                    val x = (width - placeable.height) / 2
                    val y = (height - placeable.width) / 2
                    placeable.place(x, y)
                }
            }
        }
    }

    /**
     * Get rotation angle for text orientation within vertical text.
     *
     * Used when rendering individual characters in vertical text:
     * - mixed: Rotate Latin characters 90°, keep CJK upright
     * - upright: All characters upright
     * - sideways: All characters rotated 90°
     */
    fun getCharacterRotation(config: WritingModeConfig, isLatinCharacter: Boolean): Float {
        if (!config.isVertical) return 0f

        return when (config.textOrientation) {
            TextOrientationValue.MIXED -> if (isLatinCharacter) 90f else 0f
            TextOrientationValue.UPRIGHT -> 0f
            TextOrientationValue.SIDEWAYS -> 90f
        }
    }

    /**
     * Check if a character is a Latin/horizontal script character.
     *
     * CJK characters should remain upright in vertical text,
     * while Latin characters are typically rotated.
     */
    fun isLatinCharacter(char: Char): Boolean {
        return char.code < 0x3000 // Basic check: CJK starts around 0x3000
    }
}

/**
 * Custom modifier for vertical text constraint handling.
 */
private class VerticalTextModifier(
    private val config: WritingModeConfig
) : Modifier.Element

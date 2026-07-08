package com.styleconverter.test.style.typography

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Applies CSS line-clamp and related text truncation properties.
 *
 * ## CSS Property Mapping
 * - -webkit-line-clamp / line-clamp → maxLines + overflow: ellipsis
 * - max-lines → maxLines parameter
 * - text-overflow: ellipsis → TextOverflow.Ellipsis
 * - overflow: hidden → Modifier.heightIn with calculated max height
 *
 * ## Compose Mapping
 * Line clamping in Compose is achieved through:
 * 1. `maxLines` parameter on Text composable
 * 2. `TextOverflow.Ellipsis` for truncation indicator
 * 3. Optional height constraint for visual consistency
 *
 * ## Usage
 * ```kotlin
 * LineClampApplier.ClampedText(
 *     text = "Long text that might span multiple lines...",
 *     maxLines = 3,
 *     textStyle = TextStyle(fontSize = 16.sp)
 * )
 * ```
 */
object LineClampApplier {

    /**
     * Configuration for line clamping behavior.
     */
    data class LineClampConfig(
        /** Maximum number of lines to display */
        val maxLines: Int = Int.MAX_VALUE,
        /** Whether to show ellipsis when truncated */
        val showEllipsis: Boolean = true,
        /** Whether to enforce height constraint based on line count */
        val enforceHeight: Boolean = false,
        /** Custom line height (overrides textStyle.lineHeight) */
        val lineHeight: TextUnit? = null
    ) {
        val isActive: Boolean
            get() = maxLines < Int.MAX_VALUE

        companion object {
            val None = LineClampConfig()
            fun lines(count: Int) = LineClampConfig(maxLines = count)
        }
    }

    /**
     * Get maxLines value from TypographyConfig.
     *
     * Considers both lineClamp (CSS -webkit-line-clamp) and maxLines,
     * with lineClamp taking precedence.
     */
    fun getMaxLines(config: TypographyConfig): Int {
        return config.lineClamp ?: config.maxLines ?: Int.MAX_VALUE
    }

    /**
     * Get TextOverflow from config.
     *
     * When line clamping is active, ellipsis is typically desired.
     */
    fun getTextOverflow(config: TypographyConfig): TextOverflow {
        val hasClamp = config.lineClamp != null && config.lineClamp > 0
        val explicitEllipsis = config.textOverflow == TextOverflow.Ellipsis

        return when {
            hasClamp || explicitEllipsis -> TextOverflow.Ellipsis
            config.textOverflow != null -> config.textOverflow
            else -> TextOverflow.Clip
        }
    }

    /**
     * Calculate the maximum height for a given number of lines.
     *
     * @param lineCount Number of lines
     * @param fontSize Font size
     * @param lineHeight Line height (or null to use default 1.2x multiplier)
     * @return Maximum height in Dp
     */
    fun calculateMaxHeight(
        lineCount: Int,
        fontSize: TextUnit,
        lineHeight: TextUnit? = null
    ): Dp {
        if (lineCount >= Int.MAX_VALUE) return Dp.Infinity

        val fontSizeValue = if (fontSize.isSp) fontSize.value else 16f
        val effectiveLineHeight = when {
            lineHeight != null && lineHeight.isSp -> lineHeight.value
            lineHeight != null && lineHeight.isEm -> fontSizeValue * lineHeight.value
            else -> fontSizeValue * 1.2f // Default line height multiplier
        }

        return (effectiveLineHeight * lineCount).dp
    }

    /**
     * Composable that renders text with line clamping.
     *
     * @param text The text to display
     * @param maxLines Maximum number of lines
     * @param textStyle Text style to apply
     * @param overflow Text overflow behavior
     * @param modifier Modifier for the Text composable
     */
    @Composable
    fun ClampedText(
        text: String,
        maxLines: Int,
        textStyle: TextStyle = TextStyle.Default,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        modifier: Modifier = Modifier
    ) {
        val effectiveMaxLines = if (maxLines <= 0) Int.MAX_VALUE else maxLines

        Text(
            text = text,
            style = textStyle,
            maxLines = effectiveMaxLines,
            overflow = if (effectiveMaxLines < Int.MAX_VALUE) overflow else TextOverflow.Clip,
            modifier = modifier
        )
    }

    /**
     * Composable that renders text with height-constrained line clamping.
     *
     * This version also enforces a maximum height based on line count,
     * ensuring consistent visual appearance.
     *
     * @param text The text to display
     * @param config Line clamp configuration
     * @param textStyle Text style to apply
     * @param modifier Modifier for the container
     */
    @Composable
    fun HeightConstrainedText(
        text: String,
        config: LineClampConfig,
        textStyle: TextStyle = TextStyle.Default,
        modifier: Modifier = Modifier
    ) {
        if (!config.isActive) {
            Text(
                text = text,
                style = textStyle,
                modifier = modifier
            )
            return
        }

        val lineHeight = config.lineHeight ?: textStyle.lineHeight
        val maxHeight = remember(config.maxLines, textStyle.fontSize, lineHeight) {
            calculateMaxHeight(config.maxLines, textStyle.fontSize, lineHeight)
        }

        val constrainedModifier = if (config.enforceHeight && maxHeight != Dp.Infinity) {
            modifier.heightIn(max = maxHeight)
        } else {
            modifier
        }

        Text(
            text = text,
            style = textStyle,
            maxLines = config.maxLines,
            overflow = if (config.showEllipsis) TextOverflow.Ellipsis else TextOverflow.Clip,
            modifier = constrainedModifier
        )
    }

    /**
     * Apply line clamping settings from TypographyConfig to Text parameters.
     *
     * @param config Typography configuration
     * @return Triple of (maxLines, overflow, softWrap)
     */
    fun extractTextParameters(config: TypographyConfig): TextParameters {
        val maxLines = getMaxLines(config)
        val overflow = getTextOverflow(config)
        val softWrap = TypographyApplier.shouldWrap(config)

        return TextParameters(
            maxLines = maxLines,
            overflow = overflow,
            softWrap = softWrap
        )
    }

    /**
     * Text parameters extracted from typography config.
     */
    data class TextParameters(
        val maxLines: Int = Int.MAX_VALUE,
        val overflow: TextOverflow = TextOverflow.Clip,
        val softWrap: Boolean = true
    )

    /**
     * Check if text would be truncated at the given constraints.
     *
     * This is useful for showing "Read more" buttons or tooltips.
     *
     * Note: This requires measuring the text, which is expensive.
     * Consider using TextLayoutResult.hasVisualOverflow instead.
     */
    @Composable
    fun wouldTruncate(
        text: String,
        maxLines: Int,
        textStyle: TextStyle,
        maxWidth: Dp
    ): Boolean {
        // This would require text measurement which is complex
        // In practice, use TextLayoutResult.hasVisualOverflow
        // or TextLayoutResult.lineCount comparison
        return text.length > 50 && maxLines < 5 // Simple heuristic
    }
}

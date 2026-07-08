package com.styleconverter.test.style.typography

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Applies CSS text-emphasis to Compose text.
 *
 * ## CSS Property Mapping
 * - text-emphasis-style → Emphasis mark character (dot, circle, triangle, etc.)
 * - text-emphasis-color → Color of the emphasis marks
 * - text-emphasis-position → Position (over/under, right/left for vertical text)
 *
 * ## Compose Implementation
 * Since Compose doesn't natively support text-emphasis, we render emphasis marks
 * as a separate text layer positioned above or below each character.
 *
 * ## Usage
 * ```kotlin
 * TextEmphasisApplier.TextWithEmphasis(
 *     text = "重要なテキスト",
 *     config = TextEmphasisConfig(style = TextEmphasisStyle.FILLED_DOT),
 *     textStyle = TextStyle(fontSize = 16.sp)
 * )
 * ```
 *
 * ## Limitations
 * - Performance: Renders each character separately for emphasis positioning
 * - Vertical text: Limited support (use with WritingModeApplier)
 * - Mixed scripts: Emphasis typically only for CJK characters
 */
object TextEmphasisApplier {

    /**
     * Get the emphasis mark character for a given style.
     */
    fun getEmphasisMark(style: TextEmphasisStyle): String {
        return when (style) {
            TextEmphasisStyle.NONE -> ""
            TextEmphasisStyle.FILLED_DOT -> "•"       // U+2022 BULLET
            TextEmphasisStyle.OPEN_DOT -> "◦"         // U+25E6 WHITE BULLET
            TextEmphasisStyle.FILLED_CIRCLE -> "●"    // U+25CF BLACK CIRCLE
            TextEmphasisStyle.OPEN_CIRCLE -> "○"      // U+25CB WHITE CIRCLE
            TextEmphasisStyle.FILLED_DOUBLE_CIRCLE -> "◉" // U+25C9 FISHEYE
            TextEmphasisStyle.OPEN_DOUBLE_CIRCLE -> "◎"   // U+25CE BULLSEYE
            TextEmphasisStyle.FILLED_TRIANGLE -> "▲"  // U+25B2 BLACK UP-POINTING TRIANGLE
            TextEmphasisStyle.OPEN_TRIANGLE -> "△"    // U+25B3 WHITE UP-POINTING TRIANGLE
            TextEmphasisStyle.FILLED_SESAME -> "﹅"   // U+FE45 SESAME DOT
            TextEmphasisStyle.OPEN_SESAME -> "﹆"     // U+FE46 WHITE SESAME DOT
        }
    }

    /**
     * Get the vertical offset for emphasis marks.
     *
     * @param position Emphasis position
     * @param fontSize Base font size
     * @return Vertical offset (negative for above, positive for below)
     */
    fun getEmphasisOffset(position: TextEmphasisPosition, fontSize: TextUnit): Dp {
        val baseFontSizeDp = if (fontSize.isSp) fontSize.value.dp else 16.dp
        return when (position) {
            TextEmphasisPosition.OVER_RIGHT,
            TextEmphasisPosition.OVER_LEFT -> -(baseFontSizeDp * 0.8f)
            TextEmphasisPosition.UNDER_RIGHT,
            TextEmphasisPosition.UNDER_LEFT -> baseFontSizeDp * 0.2f
        }
    }

    /**
     * Get the font size for emphasis marks (typically smaller than base text).
     *
     * @param fontSize Base font size
     * @return Emphasis mark font size (approximately 50% of base)
     */
    fun getEmphasisFontSize(fontSize: TextUnit): TextUnit {
        return if (fontSize.isSp) {
            (fontSize.value * 0.5f).sp
        } else {
            8.sp // Default fallback
        }
    }

    /**
     * Check if a character should receive emphasis marks.
     *
     * Emphasis marks are typically only applied to CJK ideographs,
     * not punctuation or Latin characters.
     *
     * @param char Character to check
     * @return true if emphasis should be applied
     */
    fun shouldEmphasize(char: Char): Boolean {
        val code = char.code
        return when {
            // CJK Unified Ideographs
            code in 0x4E00..0x9FFF -> true
            // CJK Extension A
            code in 0x3400..0x4DBF -> true
            // Hiragana
            code in 0x3040..0x309F -> true
            // Katakana
            code in 0x30A0..0x30FF -> true
            // Hangul Syllables
            code in 0xAC00..0xD7AF -> true
            // Skip whitespace and punctuation
            char.isWhitespace() -> false
            char in "。、！？「」『』（）【】" -> false
            else -> false
        }
    }

    /**
     * Composable that renders text with emphasis marks.
     *
     * @param text The text to render
     * @param config Text emphasis configuration
     * @param textStyle Base text style
     * @param modifier Modifier for the container
     */
    @Composable
    fun TextWithEmphasis(
        text: String,
        config: TextEmphasisConfig,
        textStyle: TextStyle = TextStyle.Default,
        modifier: Modifier = Modifier
    ) {
        if (!config.hasEmphasis || config.style == TextEmphasisStyle.NONE) {
            Text(text = text, style = textStyle, modifier = modifier)
            return
        }

        val emphasisMark = getEmphasisMark(config.style)
        val emphasisColor = config.color ?: textStyle.color
        val emphasisFontSize = getEmphasisFontSize(textStyle.fontSize)
        val emphasisOffset = getEmphasisOffset(config.position, textStyle.fontSize)

        val isAbove = config.position == TextEmphasisPosition.OVER_RIGHT ||
                config.position == TextEmphasisPosition.OVER_LEFT

        Row(modifier = modifier) {
            text.forEach { char ->
                if (shouldEmphasize(char)) {
                    CharacterWithEmphasis(
                        char = char,
                        emphasisMark = emphasisMark,
                        textStyle = textStyle,
                        emphasisColor = emphasisColor,
                        emphasisFontSize = emphasisFontSize,
                        emphasisOffset = emphasisOffset,
                        isAbove = isAbove
                    )
                } else {
                    Text(
                        text = char.toString(),
                        style = textStyle
                    )
                }
            }
        }
    }

    /**
     * Renders a single character with its emphasis mark.
     */
    @Composable
    private fun CharacterWithEmphasis(
        char: Char,
        emphasisMark: String,
        textStyle: TextStyle,
        emphasisColor: Color,
        emphasisFontSize: TextUnit,
        emphasisOffset: Dp,
        isAbove: Boolean
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Main character
            Text(
                text = char.toString(),
                style = textStyle
            )

            // Emphasis mark
            Text(
                text = emphasisMark,
                style = TextStyle(
                    color = emphasisColor,
                    fontSize = emphasisFontSize,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.offset(y = emphasisOffset)
            )
        }
    }

    /**
     * Simple version that adds emphasis marks inline (less accurate positioning).
     *
     * This is a simpler fallback that prepends/appends emphasis marks to text
     * rather than positioning them precisely.
     *
     * @param text Original text
     * @param config Emphasis configuration
     * @return Text with emphasis marks inserted
     */
    fun addEmphasisMarksSimple(text: String, config: TextEmphasisConfig): String {
        if (!config.hasEmphasis) return text

        val mark = getEmphasisMark(config.style)
        if (mark.isEmpty()) return text

        val isAbove = config.position == TextEmphasisPosition.OVER_RIGHT ||
                config.position == TextEmphasisPosition.OVER_LEFT

        return buildString {
            text.forEach { char ->
                if (shouldEmphasize(char)) {
                    if (isAbove) {
                        append(mark)
                        append('\n')
                        append(char)
                    } else {
                        append(char)
                        append('\n')
                        append(mark)
                    }
                } else {
                    append(char)
                }
            }
        }
    }
}

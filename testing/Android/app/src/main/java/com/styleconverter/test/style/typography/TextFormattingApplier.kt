package com.styleconverter.test.style.typography

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Applies CSS text formatting properties to Compose text.
 *
 * ## CSS Properties
 * ```css
 * .formatted-text {
 *     text-transform: uppercase;
 *     white-space: nowrap;
 *     word-break: break-all;
 *     overflow-wrap: break-word;
 *     hyphens: auto;
 *     tab-size: 4;
 *     text-indent: 2em;
 * }
 * ```
 *
 * ## Compose Mapping
 *
 * | CSS Property | Compose Equivalent | Notes |
 * |--------------|-------------------|-------|
 * | text-transform | String transformation | Pre-process text |
 * | white-space | LineBreak | Partial support |
 * | word-break | LineBreak | Partial support |
 * | overflow-wrap | LineBreak | Partial support |
 * | hyphens | Hyphens | Android 13+ |
 * | tab-size | Replace tabs with spaces | Pre-process |
 * | text-indent | TextIndent | First line only |
 *
 * ## Usage
 * ```kotlin
 * TextFormattingApplier.FormattedText(
 *     text = "Hello World",
 *     config = textFormattingConfig,
 *     style = TextStyle.Default
 * )
 * ```
 */
object TextFormattingApplier {

    /**
     * Apply text formatting to a string.
     *
     * @param text Original text
     * @param config Text formatting configuration
     * @return Transformed text
     */
    fun applyTextTransform(text: String, config: TextFormattingConfig): String {
        // Apply text-transform
        var result = when (config.textTransform) {
            TextTransformValue.NONE -> text
            TextTransformValue.UPPERCASE -> text.uppercase(Locale.getDefault())
            TextTransformValue.LOWERCASE -> text.lowercase(Locale.getDefault())
            TextTransformValue.CAPITALIZE -> text.capitalizeWords()
            TextTransformValue.FULL_WIDTH -> text.toFullWidth()
            TextTransformValue.FULL_SIZE_KANA -> text // Not directly supported
        }

        // Apply tab-size (replace tabs with spaces)
        if (config.tabSize != 8) {
            result = result.replace("\t", " ".repeat(config.tabSize))
        }

        // Apply white-space processing
        result = when (config.whiteSpace) {
            WhiteSpaceValue.NORMAL -> result.collapseWhitespace()
            WhiteSpaceValue.NOWRAP -> result.collapseWhitespace()
            WhiteSpaceValue.PRE -> result // Preserve all
            WhiteSpaceValue.PRE_WRAP -> result // Preserve all
            WhiteSpaceValue.PRE_LINE -> result.collapseWhitespacePreservingNewlines()
            WhiteSpaceValue.BREAK_SPACES -> result
        }

        return result
    }

    /**
     * Get TextStyle with formatting applied.
     *
     * @param baseStyle Base text style
     * @param config Text formatting configuration
     * @return TextStyle with formatting applied
     */
    fun getTextStyle(
        baseStyle: TextStyle = TextStyle.Default,
        config: TextFormattingConfig
    ): TextStyle {
        val lineBreak = getLineBreak(config)
        val hyphens = getHyphens(config)
        val textIndent = if (config.textIndent > 0.dp) {
            TextIndent(firstLine = config.textIndent.value.sp)
        } else {
            TextIndent.None
        }

        return baseStyle.copy(
            lineBreak = lineBreak,
            hyphens = hyphens
        )
    }

    /**
     * Get ParagraphStyle with text indent.
     *
     * @param config Text formatting configuration
     * @return ParagraphStyle with text indent
     */
    fun getParagraphStyle(config: TextFormattingConfig): ParagraphStyle {
        return ParagraphStyle(
            textIndent = if (config.textIndent > 0.dp) {
                TextIndent(firstLine = config.textIndent.value.sp)
            } else {
                TextIndent.None
            }
        )
    }

    /**
     * Map config to Compose LineBreak.
     */
    private fun getLineBreak(config: TextFormattingConfig): LineBreak {
        // Combine white-space, word-break, and overflow-wrap into LineBreak
        return when {
            // No wrapping
            config.whiteSpace == WhiteSpaceValue.NOWRAP -> LineBreak.Simple

            // Aggressive breaking
            config.wordBreak == WordBreakValue.BREAK_ALL ||
            config.overflowWrap == OverflowWrapValue.ANYWHERE -> LineBreak.Heading

            // Break on words only
            config.wordBreak == WordBreakValue.KEEP_ALL -> LineBreak.Simple

            // Break words if needed
            config.overflowWrap == OverflowWrapValue.BREAK_WORD ||
            config.wordBreak == WordBreakValue.BREAK_WORD -> LineBreak.Paragraph

            // Pre-formatted text
            config.whiteSpace in listOf(
                WhiteSpaceValue.PRE,
                WhiteSpaceValue.PRE_WRAP,
                WhiteSpaceValue.PRE_LINE
            ) -> LineBreak.Simple

            // Default
            else -> LineBreak.Paragraph
        }
    }

    /**
     * Map config to Compose Hyphens.
     */
    private fun getHyphens(config: TextFormattingConfig): Hyphens {
        return when (config.hyphens) {
            HyphensValue.NONE -> Hyphens.None
            HyphensValue.MANUAL -> Hyphens.None // Soft hyphens only
            HyphensValue.AUTO -> Hyphens.Auto
        }
    }

    /**
     * Composable that displays formatted text.
     *
     * @param text Text content
     * @param config Text formatting configuration
     * @param style Base text style
     * @param modifier Modifier for the text
     */
    @Composable
    fun FormattedText(
        text: String,
        config: TextFormattingConfig,
        style: TextStyle = TextStyle.Default,
        modifier: Modifier = Modifier
    ) {
        val transformedText = remember(text, config) {
            applyTextTransform(text, config)
        }

        val textStyle = remember(style, config) {
            getTextStyle(style, config)
        }

        // Apply text-indent as padding for first line effect
        val indentModifier = if (config.textIndent > 0.dp) {
            Modifier.padding(start = config.textIndent)
        } else {
            Modifier
        }

        Text(
            text = transformedText,
            style = textStyle,
            modifier = modifier.then(indentModifier),
            softWrap = config.whiteSpace != WhiteSpaceValue.NOWRAP
        )
    }

    /**
     * Composable for pre-formatted text (white-space: pre).
     *
     * Preserves whitespace and line breaks exactly as in source.
     *
     * @param text Pre-formatted text
     * @param style Base text style
     * @param modifier Modifier for the text
     */
    @Composable
    fun PreformattedText(
        text: String,
        style: TextStyle = TextStyle.Default,
        modifier: Modifier = Modifier
    ) {
        // Use a monospace-style approach for pre-formatted text
        Text(
            text = text,
            style = style.copy(
                lineBreak = LineBreak.Simple
            ),
            modifier = modifier,
            softWrap = true // pre-wrap behavior
        )
    }

    /**
     * Extension function to capitalize first letter of each word.
     */
    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { word ->
            word.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault())
                else it.toString()
            }
        }
    }

    /**
     * Convert half-width characters to full-width (for Asian text).
     */
    private fun String.toFullWidth(): String {
        return buildString {
            for (char in this@toFullWidth) {
                append(
                    when {
                        char in '!' .. '~' -> (char.code + 0xFEE0).toChar()
                        char == ' ' -> '\u3000' // Full-width space
                        else -> char
                    }
                )
            }
        }
    }

    /**
     * Collapse consecutive whitespace into single space.
     */
    private fun String.collapseWhitespace(): String {
        return replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Collapse whitespace but preserve newlines.
     */
    private fun String.collapseWhitespacePreservingNewlines(): String {
        return lines().joinToString("\n") { line ->
            line.replace(Regex("[ \\t]+"), " ").trim()
        }
    }

    /**
     * Notes about text formatting support.
     */
    object Notes {
        const val HYPHENS = """
            Compose Hyphens.Auto requires Android 13 (API 33) or later.
            On older devices, automatic hyphenation won't work.

            Soft hyphens (­) are always supported and work with Hyphens.None.
        """

        const val WHITE_SPACE = """
            CSS white-space has complex behavior that doesn't map directly:

            - normal: Collapse whitespace, wrap at word boundaries
            - nowrap: Collapse whitespace, no wrapping
            - pre: Preserve whitespace and newlines, no wrapping
            - pre-wrap: Preserve whitespace and newlines, wrap if needed
            - pre-line: Collapse whitespace, preserve newlines, wrap

            Compose's LineBreak provides partial support, but pre-formatting
            requires text preprocessing.
        """

        const val TEXT_INDENT = """
            CSS text-indent only affects the first line of a block.
            In Compose, we can use TextIndent in ParagraphStyle, but
            for simple cases, padding on the first line works.

            For hanging indents (negative values), additional handling
            is needed.
        """

        const val TEXT_TRANSFORM = """
            text-transform is applied to the source string before rendering.
            This means search/selection may not match the original text.

            For accessibility, consider keeping the original text in
            content descriptions.
        """
    }
}

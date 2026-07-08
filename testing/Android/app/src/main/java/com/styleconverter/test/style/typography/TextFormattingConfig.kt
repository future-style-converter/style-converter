package com.styleconverter.test.style.typography

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Text transform value options.
 */
enum class TextTransformValue {
    NONE,
    CAPITALIZE,
    UPPERCASE,
    LOWERCASE,
    FULL_WIDTH,
    FULL_SIZE_KANA
}

/**
 * White space value options.
 */
enum class WhiteSpaceValue {
    NORMAL,
    NOWRAP,
    PRE,
    PRE_WRAP,
    PRE_LINE,
    BREAK_SPACES
}

/**
 * Word break value options.
 */
enum class WordBreakValue {
    NORMAL,
    BREAK_ALL,
    KEEP_ALL,
    BREAK_WORD
}

/**
 * Overflow wrap value options.
 */
enum class OverflowWrapValue {
    NORMAL,
    BREAK_WORD,
    ANYWHERE
}

/**
 * Hyphens value options.
 */
enum class HyphensValue {
    NONE,
    MANUAL,
    AUTO
}

/**
 * Configuration for CSS text formatting properties.
 */
data class TextFormattingConfig(
    val textTransform: TextTransformValue = TextTransformValue.NONE,
    val whiteSpace: WhiteSpaceValue = WhiteSpaceValue.NORMAL,
    val wordBreak: WordBreakValue = WordBreakValue.NORMAL,
    val overflowWrap: OverflowWrapValue = OverflowWrapValue.NORMAL,
    val hyphens: HyphensValue = HyphensValue.MANUAL,
    val tabSize: Int = 8,
    val textIndent: Dp = 0.dp
) {
    val hasTextFormatting: Boolean
        get() = textTransform != TextTransformValue.NONE ||
                whiteSpace != WhiteSpaceValue.NORMAL ||
                wordBreak != WordBreakValue.NORMAL ||
                overflowWrap != OverflowWrapValue.NORMAL ||
                hyphens != HyphensValue.MANUAL ||
                tabSize != 8 ||
                textIndent != 0.dp
}

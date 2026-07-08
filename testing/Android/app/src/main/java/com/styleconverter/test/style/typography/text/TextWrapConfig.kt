package com.styleconverter.test.style.typography.text

import androidx.compose.ui.unit.Dp

/**
 * Configuration for CSS text wrapping and overflow properties.
 *
 * ## Supported Properties
 * - text-wrap: wrap, nowrap, balance, pretty, stable
 * - white-space: normal, nowrap, pre, pre-wrap, pre-line, break-spaces
 * - word-break: normal, break-all, keep-all, break-word
 * - overflow-wrap: normal, break-word, anywhere
 * - hyphens: none, manual, auto
 * - hyphenate-character: auto, string
 * - hyphenate-limit-chars: limits for hyphenation
 */
data class TextWrapConfig(
    val textWrap: TextWrapValue = TextWrapValue.WRAP,
    val whiteSpace: WhiteSpaceValue = WhiteSpaceValue.NORMAL,
    val wordBreak: WordBreakValue = WordBreakValue.NORMAL,
    val overflowWrap: OverflowWrapValue = OverflowWrapValue.NORMAL,
    val hyphens: HyphensValue = HyphensValue.MANUAL,
    val hyphenateCharacter: String = "auto",
    val textIndent: Dp? = null
) {
    val hasTextWrap: Boolean
        get() = textWrap != TextWrapValue.WRAP ||
                whiteSpace != WhiteSpaceValue.NORMAL ||
                wordBreak != WordBreakValue.NORMAL ||
                overflowWrap != OverflowWrapValue.NORMAL ||
                hyphens != HyphensValue.MANUAL ||
                textIndent != null

    val shouldWrap: Boolean
        get() = textWrap != TextWrapValue.NOWRAP &&
                whiteSpace !in listOf(WhiteSpaceValue.NOWRAP, WhiteSpaceValue.PRE)

    val preserveWhitespace: Boolean
        get() = whiteSpace in listOf(
            WhiteSpaceValue.PRE,
            WhiteSpaceValue.PRE_WRAP,
            WhiteSpaceValue.PRE_LINE,
            WhiteSpaceValue.BREAK_SPACES
        )

    val preserveLineBreaks: Boolean
        get() = whiteSpace in listOf(
            WhiteSpaceValue.PRE,
            WhiteSpaceValue.PRE_WRAP,
            WhiteSpaceValue.PRE_LINE,
            WhiteSpaceValue.BREAK_SPACES
        )

    companion object {
        val Default = TextWrapConfig()
        val NoWrap = TextWrapConfig(
            textWrap = TextWrapValue.NOWRAP,
            whiteSpace = WhiteSpaceValue.NOWRAP
        )
        val PreWrap = TextWrapConfig(whiteSpace = WhiteSpaceValue.PRE_WRAP)
    }
}

/**
 * CSS text-wrap property values.
 */
enum class TextWrapValue {
    /** Normal wrapping at allowed break points */
    WRAP,
    /** No wrapping */
    NOWRAP,
    /** Balance line lengths */
    BALANCE,
    /** Optimize for readability */
    PRETTY,
    /** Avoid content shifts during editing */
    STABLE
}

/**
 * CSS white-space property values.
 */
enum class WhiteSpaceValue {
    /** Collapse whitespace, wrap text */
    NORMAL,
    /** Collapse whitespace, no wrap */
    NOWRAP,
    /** Preserve whitespace and line breaks */
    PRE,
    /** Preserve whitespace, wrap when needed */
    PRE_WRAP,
    /** Collapse whitespace, preserve line breaks */
    PRE_LINE,
    /** Preserve whitespace, break at spaces */
    BREAK_SPACES
}

/**
 * CSS word-break property values.
 */
enum class WordBreakValue {
    /** Use default line break rules */
    NORMAL,
    /** Allow breaks at any character */
    BREAK_ALL,
    /** Don't break CJK words */
    KEEP_ALL,
    /** Legacy value similar to overflow-wrap */
    BREAK_WORD
}

/**
 * CSS overflow-wrap property values.
 */
enum class OverflowWrapValue {
    /** Only break at allowed points */
    NORMAL,
    /** Break words to prevent overflow */
    BREAK_WORD,
    /** Break anywhere if needed */
    ANYWHERE
}

/**
 * CSS hyphens property values.
 */
enum class HyphensValue {
    /** No hyphenation */
    NONE,
    /** Hyphenate at &shy; only */
    MANUAL,
    /** Automatic hyphenation */
    AUTO
}

/**
 * CSS text-wrap-mode property values.
 * Separable from text-wrap for finer control.
 */
enum class TextWrapModeValue {
    /** Allow wrapping */
    WRAP,
    /** No wrapping */
    NOWRAP
}

/**
 * CSS text-wrap-style property values.
 * Controls wrapping optimization.
 */
enum class TextWrapStyleValue {
    /** Default browser behavior */
    AUTO,
    /** Balance line lengths */
    BALANCE,
    /** Optimize for readability */
    PRETTY,
    /** Avoid content shifts */
    STABLE
}

/**
 * CSS text-spacing property values.
 * Controls CJK character spacing.
 */
enum class TextSpacingValue {
    /** Normal spacing */
    NORMAL,
    /** No autospacing */
    NONE,
    /** Space between ideographs and alphabetics */
    AUTO
}

/**
 * CSS text-space-trim property values.
 * Controls trimming of spaces around punctuation.
 */
enum class TextSpaceTrimValue {
    /** No trimming */
    NONE,
    /** Trim spaces at start of line */
    SPACE_FIRST,
    /** Trim all redundant spaces */
    SPACE_ALL,
    /** Trim around fullwidth punctuation */
    TRIM_START
}

/**
 * CSS white-space-collapse property values.
 * Controls whitespace collapsing behavior.
 */
enum class WhiteSpaceCollapseValue {
    /** Collapse runs of whitespace */
    COLLAPSE,
    /** Keep sequences of whitespace but collapse newlines */
    PRESERVE,
    /** Preserve all whitespace */
    PRESERVE_BREAKS,
    /** Keep spaces, collapse line breaks */
    PRESERVE_SPACES,
    /** Break at all spaces */
    BREAK_SPACES
}

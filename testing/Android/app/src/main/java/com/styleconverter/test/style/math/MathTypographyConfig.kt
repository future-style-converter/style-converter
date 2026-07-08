package com.styleconverter.test.style.math

import androidx.compose.ui.unit.Dp

/**
 * Math style value options.
 */
enum class MathStyleValue {
    NORMAL,
    COMPACT
}

/**
 * Math shift value options.
 */
enum class MathShiftValue {
    NORMAL,
    COMPACT
}

/**
 * Math depth value options.
 * CSS: math-depth property for MathML nesting depth.
 */
sealed interface MathDepthValue {
    data object AutoAdd : MathDepthValue
    data class Add(val n: Int) : MathDepthValue
    data class Absolute(val value: Int) : MathDepthValue
}

/**
 * CSS hanging-punctuation property values.
 * Controls punctuation hanging in CJK typography.
 */
enum class HangingPunctuationValue {
    NONE,
    FIRST,
    LAST,
    FORCE_END,
    ALLOW_END
}

/**
 * CSS initial-letter-align property values.
 * Controls drop cap alignment.
 */
enum class InitialLetterAlignValue {
    AUTO,
    ALPHABETIC,
    HANGING,
    IDEOGRAPHIC
}

/**
 * CSS block-ellipsis property values.
 * Controls block-level text overflow ellipsis.
 */
sealed interface BlockEllipsisValue {
    data object None : BlockEllipsisValue
    data object Auto : BlockEllipsisValue
    data class Custom(val value: String) : BlockEllipsisValue
}

/**
 * CSS text-autospace property values.
 * Controls autospacing for CJK text.
 */
enum class TextAutospaceValue {
    NORMAL,
    NO_AUTOSPACE,
    IDEOGRAPH_ALPHA,
    IDEOGRAPH_NUMERIC,
    IDEOGRAPH_PARENTHESIS
}

/**
 * Configuration for CSS MathML typography properties.
 */
data class MathTypographyConfig(
    val mathStyle: MathStyleValue = MathStyleValue.NORMAL,
    val mathShift: MathShiftValue = MathShiftValue.NORMAL,
    val mathDepth: Int = 0,
    val hangingPunctuation: Set<HangingPunctuationValue> = emptySet(),
    val initialLetterSize: Float? = null,
    val initialLetterDrop: Int? = null,
    val initialLetterAlign: InitialLetterAlignValue = InitialLetterAlignValue.AUTO,
    val maxLines: Int? = null,
    val blockEllipsis: BlockEllipsisValue = BlockEllipsisValue.None,
    val lineHeightStep: Dp? = null,
    val textAutospace: TextAutospaceValue = TextAutospaceValue.NORMAL
) {
    val hasMathTypography: Boolean
        get() = mathStyle != MathStyleValue.NORMAL ||
                mathShift != MathShiftValue.NORMAL ||
                mathDepth != 0 ||
                hangingPunctuation.isNotEmpty() ||
                initialLetterSize != null ||
                maxLines != null ||
                lineHeightStep != null ||
                textAutospace != TextAutospaceValue.NORMAL
}

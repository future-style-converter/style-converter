package com.styleconverter.test.style.typography

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Configuration for typography-related styling properties.
 *
 * This config aggregates all text styling properties into a single structure
 * that can be used to build a TextStyle or apply text-related modifiers.
 *
 * ## Supported Properties
 * - Font properties: family, size, weight, style, stretch
 * - Spacing: letter spacing, line height, word spacing, tab size
 * - Alignment: text align, text align last
 * - Decoration: underline, line-through (with color, thickness, style)
 * - Overflow: text overflow, max lines, line clamp
 * - Transform: uppercase, lowercase, capitalize
 * - Shadow: text shadow
 * - Baseline: baseline shift (sub, super)
 * - Direction: LTR, RTL
 * - Line breaking: line break rules, word break, hyphens
 *
 * ## Usage
 * ```kotlin
 * val config = TypographyExtractor.extractTypographyConfig(properties)
 * val textStyle = TypographyApplier.buildTextStyle(config)
 * ```
 */
data class TypographyConfig(
    /** Font family (serif, sans-serif, monospace, cursive, or default) */
    val fontFamily: FontFamily? = null,
    /** Font size as TextUnit (sp) */
    val fontSize: TextUnit? = null,
    /** Font weight (100-900 or named weights) */
    val fontWeight: FontWeight? = null,
    /** Font style (normal or italic) */
    val fontStyle: FontStyle? = null,
    /** Font stretch as percentage (50-200, where 100 is normal) */
    val fontStretch: Float? = null,
    /** Letter spacing as TextUnit (sp) */
    val letterSpacing: TextUnit? = null,
    /** Line height as TextUnit (sp) */
    val lineHeight: TextUnit? = null,
    /** Text alignment (start, end, center, justify) */
    val textAlign: TextAlign? = null,
    /** Text alignment for last line */
    val textAlignLast: TextAlignLast? = null,
    /** Text decoration (underline, line-through, none) */
    val textDecoration: TextDecoration? = null,
    /** Extended text decoration with color, thickness, style */
    val textDecorationExtended: TextDecorationConfig? = null,
    /** Text overflow behavior (clip, ellipsis, visible) */
    val textOverflow: TextOverflow? = null,
    /** Text color */
    val color: Color? = null,
    /** Text transform (uppercase, lowercase, capitalize, none) */
    val textTransform: TextTransform? = null,
    /** First line indent as TextUnit (sp) */
    val textIndent: TextUnit? = null,
    /** Word spacing as TextUnit (sp) */
    val wordSpacing: TextUnit? = null,
    /** White space handling */
    val whiteSpace: WhiteSpace? = null,
    /** Maximum number of lines */
    val maxLines: Int? = null,
    /** Line clamp (CSS -webkit-line-clamp equivalent) */
    val lineClamp: Int? = null,
    /** Text shadow */
    val textShadow: TextShadowConfig? = null,
    /** Baseline shift (subscript, superscript) */
    val baselineShift: BaselineShift? = null,
    /** Tab size in spaces or pixels */
    val tabSize: TabSizeConfig? = null,
    /** Text direction (LTR or RTL) */
    val direction: DirectionMode? = null,
    /** Line break rules */
    val lineBreak: LineBreakMode? = null,
    /** Word break rules */
    val wordBreak: WordBreakMode? = null,
    /** Overflow wrap rules */
    val overflowWrap: OverflowWrapMode? = null,
    /** Hyphenation mode */
    val hyphens: HyphensMode? = null,
    /** Custom hyphenation character */
    val hyphenateCharacter: String? = null,
    /** Text justify method */
    val textJustify: TextJustifyMode? = null
) {
    /**
     * Returns true if any typography-related property is set.
     * Used to determine if text styling should be applied.
     */
    val hasTypography: Boolean
        get() = fontFamily != null || fontSize != null || fontWeight != null ||
                fontStyle != null || letterSpacing != null || lineHeight != null ||
                textAlign != null || textDecoration != null || color != null ||
                textShadow != null || baselineShift != null || fontStretch != null
}

/**
 * Extended text decoration configuration with color, thickness, and style.
 */
data class TextDecorationConfig(
    /** The decoration line (underline, line-through) */
    val line: TextDecoration? = null,
    /** Color of the decoration line */
    val color: Color? = null,
    /** Thickness of the decoration line in dp */
    val thickness: Dp? = null,
    /** Style of the decoration line */
    val style: TextDecorationStyle = TextDecorationStyle.SOLID,
    /** Offset of underline from text baseline in dp */
    val underlineOffset: Dp? = null,
    /** Position of underline relative to text */
    val underlinePosition: TextUnderlinePosition = TextUnderlinePosition.AUTO
)

/**
 * Text decoration line styles.
 */
enum class TextDecorationStyle {
    SOLID, DOUBLE, DOTTED, DASHED, WAVY
}

/**
 * Text underline position values.
 *
 * CSS: text-underline-position property.
 */
enum class TextUnderlinePosition {
    /** Default position (typically alphabetic baseline) */
    AUTO,
    /** Position underline below the alphabetic baseline */
    UNDER,
    /** Position for left vertical text */
    LEFT,
    /** Position for right vertical text */
    RIGHT,
    /** From the font file (OpenType) */
    FROM_FONT
}

/**
 * Text shadow configuration.
 */
data class TextShadowConfig(
    val offsetX: Dp = 0.dp,
    val offsetY: Dp = 0.dp,
    val blurRadius: Dp = 0.dp,
    val color: Color = Color.Black
) {
    /**
     * Convert to Compose Shadow.
     */
    fun toShadow(): Shadow = Shadow(
        color = color,
        offset = Offset(offsetX.value, offsetY.value),
        blurRadius = blurRadius.value
    )
}

/**
 * Tab size configuration for monospace text.
 */
data class TabSizeConfig(
    /** Tab width in number of spaces */
    val spaces: Int? = null,
    /** Tab width in pixels */
    val pixels: Float? = null
) {
    /** Get effective tab width in spaces (default: 8) */
    val effectiveSpaces: Int
        get() = spaces ?: (pixels?.let { (it / 8f).toInt().coerceAtLeast(1) }) ?: 8

    /** Generate space string for tab replacement */
    fun toSpaceString(): String = " ".repeat(effectiveSpaces)
}

/**
 * Text transformation options.
 *
 * CSS: text-transform property values.
 * Note: Compose doesn't have built-in text-transform; must be applied to string content.
 */
enum class TextTransform {
    /** No transformation */
    NONE,
    /** Transform all characters to uppercase */
    UPPERCASE,
    /** Transform all characters to lowercase */
    LOWERCASE,
    /** Capitalize the first letter of each word */
    CAPITALIZE
}

/**
 * White space handling options.
 *
 * CSS: white-space property values.
 * Compose support varies; primarily affects text wrapping behavior.
 */
enum class WhiteSpace {
    /** Collapse sequences of whitespace, wrap text as needed */
    NORMAL,
    /** Collapse whitespace, do not wrap text */
    NOWRAP,
    /** Preserve whitespace, do not wrap text */
    PRE,
    /** Preserve whitespace, wrap text as needed */
    PRE_WRAP,
    /** Collapse whitespace sequences, preserve newlines, wrap as needed */
    PRE_LINE,
    /** Preserve all whitespace including trailing spaces, wrap as needed */
    BREAK_SPACES
}

/**
 * Text alignment for last line.
 *
 * CSS: text-align-last property.
 */
enum class TextAlignLast {
    AUTO, START, END, LEFT, RIGHT, CENTER, JUSTIFY
}

/**
 * Text direction mode.
 *
 * CSS: direction property.
 */
enum class DirectionMode {
    LTR, RTL;

    /** Convert to Compose LayoutDirection */
    fun toLayoutDirection(): LayoutDirection = when (this) {
        LTR -> LayoutDirection.Ltr
        RTL -> LayoutDirection.Rtl
    }
}

/**
 * Line break mode for CJK text.
 *
 * CSS: line-break property.
 */
enum class LineBreakMode {
    AUTO, LOOSE, NORMAL, STRICT, ANYWHERE;

    /** Convert to Compose LineBreak */
    fun toComposeLineBreak(): LineBreak = when (this) {
        AUTO -> LineBreak.Simple
        LOOSE -> LineBreak.Simple
        NORMAL -> LineBreak.Paragraph
        STRICT -> LineBreak.Heading
        ANYWHERE -> LineBreak.Simple
    }
}

/**
 * Word break mode.
 *
 * CSS: word-break property.
 */
enum class WordBreakMode {
    NORMAL, BREAK_ALL, KEEP_ALL, BREAK_WORD
}

/**
 * Overflow wrap mode.
 *
 * CSS: overflow-wrap (word-wrap) property.
 */
enum class OverflowWrapMode {
    NORMAL, BREAK_WORD, ANYWHERE
}

/**
 * Hyphenation mode.
 *
 * CSS: hyphens property.
 */
enum class HyphensMode {
    NONE, MANUAL, AUTO
}

/**
 * Text justify mode.
 *
 * CSS: text-justify property.
 */
enum class TextJustifyMode {
    AUTO, NONE, INTER_WORD, INTER_CHARACTER
}

/**
 * Text emphasis configuration.
 *
 * CSS: text-emphasis property for East Asian typography.
 * Adds emphasis marks above or below text.
 */
data class TextEmphasisConfig(
    /** Emphasis mark style */
    val style: TextEmphasisStyle = TextEmphasisStyle.NONE,
    /** Color of the emphasis marks */
    val color: Color? = null,
    /** Position of emphasis marks */
    val position: TextEmphasisPosition = TextEmphasisPosition.OVER_RIGHT
) {
    val hasEmphasis: Boolean
        get() = style != TextEmphasisStyle.NONE

    companion object {
        val None = TextEmphasisConfig()
        val Dot = TextEmphasisConfig(style = TextEmphasisStyle.FILLED_DOT)
        val Circle = TextEmphasisConfig(style = TextEmphasisStyle.FILLED_CIRCLE)
    }
}

/**
 * Text emphasis style values.
 */
enum class TextEmphasisStyle {
    NONE,
    FILLED_DOT,
    OPEN_DOT,
    FILLED_CIRCLE,
    OPEN_CIRCLE,
    FILLED_DOUBLE_CIRCLE,
    OPEN_DOUBLE_CIRCLE,
    FILLED_TRIANGLE,
    OPEN_TRIANGLE,
    FILLED_SESAME,
    OPEN_SESAME
}

/**
 * Text emphasis position.
 */
enum class TextEmphasisPosition {
    OVER_RIGHT,    // Default for horizontal-tb
    UNDER_RIGHT,
    OVER_LEFT,
    UNDER_LEFT
}

/**
 * Font variant configuration.
 *
 * CSS: font-variant-* properties for OpenType features.
 */
data class FontVariantConfig(
    /** Small-caps and all-small-caps */
    val caps: FontVariantCaps = FontVariantCaps.NORMAL,
    /** Ligature settings */
    val ligatures: FontVariantLigatures = FontVariantLigatures.NORMAL,
    /** Numeric figure settings */
    val numeric: FontVariantNumeric = FontVariantNumeric(),
    /** East Asian variants */
    val eastAsian: FontVariantEastAsian = FontVariantEastAsian.NORMAL,
    /** Emoji rendering */
    val emoji: FontVariantEmoji = FontVariantEmoji.NORMAL,
    /** Subscript/superscript position */
    val position: FontVariantPosition = FontVariantPosition.NORMAL,
    /** Alternates */
    val alternates: FontVariantAlternates = FontVariantAlternates.NORMAL,
    /** Font feature settings (OpenType features) */
    val fontFeatureSettings: List<FontFeatureSetting> = emptyList(),
    /** Font kerning mode */
    val fontKerning: FontKerningValue = FontKerningValue.AUTO,
    /** Font optical sizing mode */
    val fontOpticalSizing: FontOpticalSizingValue = FontOpticalSizingValue.AUTO
) {
    val hasFontVariant: Boolean
        get() = caps != FontVariantCaps.NORMAL ||
                ligatures != FontVariantLigatures.NORMAL ||
                position != FontVariantPosition.NORMAL ||
                fontFeatureSettings.isNotEmpty() ||
                fontKerning != FontKerningValue.AUTO ||
                fontOpticalSizing != FontOpticalSizingValue.AUTO

    companion object {
        val Default = FontVariantConfig()
        val SmallCaps = FontVariantConfig(caps = FontVariantCaps.SMALL_CAPS)
        val NoLigatures = FontVariantConfig(ligatures = FontVariantLigatures.NONE)
    }
}

/**
 * Single font feature setting.
 *
 * CSS: font-feature-settings: "liga" on, "calt" 1
 */
data class FontFeatureSetting(
    /** OpenType feature tag (e.g., "liga", "smcp", "onum") */
    val tag: String,
    /** Feature value: 0=off, 1=on, or specific alternate number */
    val value: Int = 1
) {
    val isEnabled: Boolean
        get() = value > 0
}

/**
 * Font kerning values.
 *
 * CSS: font-kerning property.
 */
enum class FontKerningValue {
    /** Browser chooses when to apply kerning */
    AUTO,
    /** Always apply kerning */
    NORMAL,
    /** Never apply kerning */
    NONE
}

/**
 * Font optical sizing values.
 *
 * CSS: font-optical-sizing property.
 */
enum class FontOpticalSizingValue {
    /** Enable optical sizing (default for variable fonts) */
    AUTO,
    /** Disable optical sizing */
    NONE
}

/**
 * Font variant caps values.
 */
enum class FontVariantCaps {
    NORMAL,
    SMALL_CAPS,
    ALL_SMALL_CAPS,
    PETITE_CAPS,
    ALL_PETITE_CAPS,
    UNICASE,
    TITLING_CAPS
}

/**
 * Font variant ligatures values.
 */
enum class FontVariantLigatures {
    NORMAL,
    NONE,
    COMMON_LIGATURES,
    NO_COMMON_LIGATURES,
    DISCRETIONARY_LIGATURES,
    NO_DISCRETIONARY_LIGATURES,
    HISTORICAL_LIGATURES,
    NO_HISTORICAL_LIGATURES,
    CONTEXTUAL,
    NO_CONTEXTUAL
}

/**
 * Font variant numeric configuration.
 */
data class FontVariantNumeric(
    val figure: NumericFigure = NumericFigure.NORMAL,
    val spacing: NumericSpacing = NumericSpacing.NORMAL,
    val fraction: NumericFraction = NumericFraction.NORMAL,
    val ordinal: Boolean = false,
    val slashedZero: Boolean = false
)

enum class NumericFigure {
    NORMAL, LINING_NUMS, OLDSTYLE_NUMS
}

enum class NumericSpacing {
    NORMAL, PROPORTIONAL_NUMS, TABULAR_NUMS
}

enum class NumericFraction {
    NORMAL, DIAGONAL_FRACTIONS, STACKED_FRACTIONS
}

/**
 * Font variant east asian values.
 */
enum class FontVariantEastAsian {
    NORMAL,
    JIS78, JIS83, JIS90, JIS04,
    SIMPLIFIED, TRADITIONAL,
    FULL_WIDTH, PROPORTIONAL_WIDTH,
    RUBY
}

/**
 * Font variant emoji values.
 */
enum class FontVariantEmoji {
    NORMAL, TEXT, EMOJI, UNICODE
}

/**
 * Font variant position values.
 */
enum class FontVariantPosition {
    NORMAL, SUB, SUPER
}

/**
 * Font variant alternates values.
 */
enum class FontVariantAlternates {
    NORMAL, HISTORICAL_FORMS
}

/**
 * Font synthesis configuration.
 *
 * CSS: font-synthesis property for controlling synthetic font faces.
 */
data class FontSynthesisConfig(
    /** Allow synthesizing bold weight */
    val weight: Boolean = true,
    /** Allow synthesizing italic style */
    val style: Boolean = true,
    /** Allow synthesizing small-caps */
    val smallCaps: Boolean = true,
    /** Allow synthesizing subscript/superscript positions */
    val position: Boolean = true
) {
    val hasFontSynthesis: Boolean
        get() = !weight || !style || !smallCaps || !position

    companion object {
        val Default = FontSynthesisConfig()
        val None = FontSynthesisConfig(weight = false, style = false, smallCaps = false, position = false)
        val WeightOnly = FontSynthesisConfig(style = false, smallCaps = false, position = false)
        val StyleOnly = FontSynthesisConfig(weight = false, smallCaps = false, position = false)
    }
}

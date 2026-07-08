package com.styleconverter.test.style.typography

import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineBreak.Strategy
import androidx.compose.ui.text.style.LineBreak.Strictness
import androidx.compose.ui.text.style.LineBreak.WordBreak as ComposeWordBreak

/**
 * Applies CSS text wrapping and line breaking properties to Compose text.
 *
 * ## CSS Property Mapping
 * - white-space → softWrap + text preprocessing
 * - word-break → LineBreak.WordBreak
 * - overflow-wrap → LineBreak.Strategy
 * - hyphens → Hyphens (API 23+)
 * - line-break → LineBreak.Strictness
 *
 * ## Compose Mapping
 * Compose uses a unified LineBreak class that combines:
 * - Strategy: How words are broken (Balanced, HighQuality, Simple)
 * - Strictness: CJK line break rules (Default, Loose, Normal, Strict)
 * - WordBreak: Whether to break words (Default, Phrase)
 *
 * ## Limitations
 * - `white-space: pre` requires text preprocessing (preserve newlines/spaces)
 * - `word-break: break-all` has limited support
 * - Hyphenation requires API 23+ and language context
 * - `overflow-wrap: anywhere` not fully supported
 *
 * ## Usage
 * ```kotlin
 * val wrapConfig = TextWrapApplier.extractWrapConfig(typographyConfig)
 * val lineBreak = TextWrapApplier.buildLineBreak(wrapConfig)
 * val hyphens = TextWrapApplier.getHyphens(wrapConfig)
 * val softWrap = TextWrapApplier.shouldSoftWrap(wrapConfig)
 * ```
 */
object TextWrapApplier {

    /**
     * Configuration for text wrapping behavior.
     */
    data class TextWrapConfig(
        /** CSS white-space property */
        val whiteSpace: WhiteSpace = WhiteSpace.NORMAL,
        /** CSS word-break property */
        val wordBreak: WordBreakMode = WordBreakMode.NORMAL,
        /** CSS overflow-wrap property */
        val overflowWrap: OverflowWrapMode = OverflowWrapMode.NORMAL,
        /** CSS hyphens property */
        val hyphens: HyphensMode = HyphensMode.NONE,
        /** CSS line-break property */
        val lineBreak: LineBreakMode = LineBreakMode.AUTO,
        /** Custom hyphenation character */
        val hyphenateCharacter: String? = null
    ) {
        /**
         * Check if text should wrap (based on white-space).
         */
        val shouldWrap: Boolean
            get() = whiteSpace != WhiteSpace.NOWRAP && whiteSpace != WhiteSpace.PRE

        /**
         * Check if whitespace should be preserved (not collapsed).
         */
        val preserveWhitespace: Boolean
            get() = whiteSpace == WhiteSpace.PRE ||
                    whiteSpace == WhiteSpace.PRE_WRAP ||
                    whiteSpace == WhiteSpace.PRE_LINE ||
                    whiteSpace == WhiteSpace.BREAK_SPACES

        /**
         * Check if newlines should be preserved.
         */
        val preserveNewlines: Boolean
            get() = whiteSpace == WhiteSpace.PRE ||
                    whiteSpace == WhiteSpace.PRE_WRAP ||
                    whiteSpace == WhiteSpace.PRE_LINE

        /**
         * Check if any non-default wrapping behavior is configured.
         */
        val hasCustomWrapping: Boolean
            get() = whiteSpace != WhiteSpace.NORMAL ||
                    wordBreak != WordBreakMode.NORMAL ||
                    overflowWrap != OverflowWrapMode.NORMAL ||
                    hyphens != HyphensMode.NONE ||
                    lineBreak != LineBreakMode.AUTO

        companion object {
            val Default = TextWrapConfig()
            val NoWrap = TextWrapConfig(whiteSpace = WhiteSpace.NOWRAP)
            val PreserveAll = TextWrapConfig(whiteSpace = WhiteSpace.PRE)
            val BreakAnywhere = TextWrapConfig(
                wordBreak = WordBreakMode.BREAK_ALL,
                overflowWrap = OverflowWrapMode.ANYWHERE
            )
        }
    }

    /**
     * Extract TextWrapConfig from TypographyConfig.
     *
     * @param config TypographyConfig with wrapping properties
     * @return TextWrapConfig with extracted values
     */
    fun extractWrapConfig(config: TypographyConfig): TextWrapConfig {
        return TextWrapConfig(
            whiteSpace = config.whiteSpace ?: WhiteSpace.NORMAL,
            wordBreak = config.wordBreak ?: WordBreakMode.NORMAL,
            overflowWrap = config.overflowWrap ?: OverflowWrapMode.NORMAL,
            hyphens = config.hyphens ?: HyphensMode.NONE,
            lineBreak = config.lineBreak ?: LineBreakMode.AUTO,
            hyphenateCharacter = config.hyphenateCharacter
        )
    }

    /**
     * Check if text should soft-wrap based on config.
     *
     * Used for the `softWrap` parameter on Text composable.
     *
     * @param config TypographyConfig
     * @return true if text should wrap
     */
    fun shouldSoftWrap(config: TypographyConfig): Boolean {
        val whiteSpace = config.whiteSpace ?: WhiteSpace.NORMAL
        return whiteSpace != WhiteSpace.NOWRAP && whiteSpace != WhiteSpace.PRE
    }

    /**
     * Build a Compose LineBreak from CSS properties.
     *
     * Combines word-break, overflow-wrap, and line-break into a single LineBreak.
     *
     * @param config TextWrapConfig with wrapping rules
     * @return Compose LineBreak
     */
    fun buildLineBreak(config: TextWrapConfig): LineBreak {
        val strategy = getStrategy(config)
        val strictness = getStrictness(config.lineBreak)
        val wordBreak = getWordBreak(config)

        return LineBreak(
            strategy = strategy,
            strictness = strictness,
            wordBreak = wordBreak
        )
    }

    /**
     * Build LineBreak from TypographyConfig.
     *
     * Convenience method that extracts config and builds LineBreak.
     */
    fun buildLineBreak(config: TypographyConfig): LineBreak {
        return buildLineBreak(extractWrapConfig(config))
    }

    /**
     * Get Compose Hyphens setting from config.
     *
     * Note: Hyphenation requires:
     * - API 23+ for basic support
     * - API 33+ for full control
     * - Proper language/locale setting
     * - A hyphenation dictionary for the language
     *
     * @param config TextWrapConfig
     * @return Compose Hyphens value
     */
    fun getHyphens(config: TextWrapConfig): Hyphens {
        return when (config.hyphens) {
            HyphensMode.NONE -> Hyphens.None
            HyphensMode.MANUAL -> Hyphens.None // Compose doesn't have manual mode
            HyphensMode.AUTO -> Hyphens.Auto
        }
    }

    /**
     * Get Compose Hyphens from TypographyConfig.
     */
    fun getHyphens(config: TypographyConfig): Hyphens {
        return when (config.hyphens) {
            HyphensMode.NONE, null -> Hyphens.None
            HyphensMode.MANUAL -> Hyphens.None
            HyphensMode.AUTO -> Hyphens.Auto
        }
    }

    /**
     * Preprocess text based on white-space rules.
     *
     * CSS white-space affects how whitespace is collapsed and newlines are handled.
     * Since Compose doesn't handle this automatically, we preprocess the text.
     *
     * @param text Original text
     * @param whiteSpace WhiteSpace mode
     * @return Preprocessed text
     */
    fun preprocessText(text: String, whiteSpace: WhiteSpace): String {
        return when (whiteSpace) {
            WhiteSpace.NORMAL -> {
                // Collapse all whitespace sequences to single spaces
                // Convert newlines to spaces
                text.replace(WHITESPACE_SEQUENCE, " ").trim()
            }
            WhiteSpace.NOWRAP -> {
                // Same as normal but no wrapping (handled by softWrap)
                text.replace(WHITESPACE_SEQUENCE, " ").trim()
            }
            WhiteSpace.PRE -> {
                // Preserve all whitespace and newlines exactly
                text
            }
            WhiteSpace.PRE_WRAP -> {
                // Preserve whitespace, allow wrapping
                text
            }
            WhiteSpace.PRE_LINE -> {
                // Collapse spaces but preserve newlines
                text.lines().joinToString("\n") { line ->
                    line.replace(SPACE_SEQUENCE, " ").trim()
                }
            }
            WhiteSpace.BREAK_SPACES -> {
                // Like pre-wrap but with visible trailing spaces
                // Compose handles this similarly to pre-wrap
                text
            }
        }
    }

    /**
     * Convert tabs to spaces based on tab size.
     *
     * @param text Text with tabs
     * @param tabSizeConfig Tab size configuration
     * @return Text with tabs converted to spaces
     */
    fun convertTabs(text: String, tabSizeConfig: TabSizeConfig?): String {
        if (tabSizeConfig == null) return text
        return text.replace("\t", tabSizeConfig.toSpaceString())
    }

    /**
     * Apply full text preprocessing based on typography config.
     *
     * @param text Original text
     * @param config TypographyConfig
     * @return Preprocessed text
     */
    fun applyTextPreprocessing(text: String, config: TypographyConfig): String {
        var result = text

        // Convert tabs if tab size is specified
        result = convertTabs(result, config.tabSize)

        // Apply white-space rules
        result = preprocessText(result, config.whiteSpace ?: WhiteSpace.NORMAL)

        // Apply text transform
        result = applyTextTransform(result, config.textTransform)

        return result
    }

    /**
     * Apply text transform (uppercase, lowercase, capitalize).
     *
     * @param text Original text
     * @param transform TextTransform mode
     * @return Transformed text
     */
    fun applyTextTransform(text: String, transform: TextTransform?): String {
        return when (transform) {
            TextTransform.UPPERCASE -> text.uppercase()
            TextTransform.LOWERCASE -> text.lowercase()
            TextTransform.CAPITALIZE -> text.capitalizeWords()
            TextTransform.NONE, null -> text
        }
    }

    /**
     * Get the LineBreak strategy from CSS properties.
     *
     * Maps overflow-wrap and white-space to Compose Strategy:
     * - Simple: Fast, basic line breaking
     * - HighQuality: Slower, better text layout
     * - Balanced: Even line lengths (good for headings)
     */
    private fun getStrategy(config: TextWrapConfig): Strategy {
        return when {
            // break-spaces needs high quality for proper spacing
            config.whiteSpace == WhiteSpace.BREAK_SPACES -> Strategy.HighQuality
            // anywhere/break-word allows aggressive breaking
            config.overflowWrap == OverflowWrapMode.ANYWHERE -> Strategy.HighQuality
            config.overflowWrap == OverflowWrapMode.BREAK_WORD -> Strategy.HighQuality
            config.wordBreak == WordBreakMode.BREAK_ALL -> Strategy.HighQuality
            config.wordBreak == WordBreakMode.BREAK_WORD -> Strategy.HighQuality
            // Default to simple for performance
            else -> Strategy.Simple
        }
    }

    /**
     * Get the LineBreak strictness from CSS line-break property.
     *
     * Primarily affects CJK text line breaking rules.
     */
    private fun getStrictness(lineBreak: LineBreakMode): Strictness {
        return when (lineBreak) {
            LineBreakMode.AUTO -> Strictness.Default
            LineBreakMode.LOOSE -> Strictness.Loose
            LineBreakMode.NORMAL -> Strictness.Normal
            LineBreakMode.STRICT -> Strictness.Strict
            LineBreakMode.ANYWHERE -> Strictness.Loose
        }
    }

    /**
     * Get the LineBreak word break setting.
     *
     * Controls whether words can be broken.
     */
    private fun getWordBreak(config: TextWrapConfig): ComposeWordBreak {
        return when {
            // keep-all prevents word breaks (use Phrase mode)
            config.wordBreak == WordBreakMode.KEEP_ALL -> ComposeWordBreak.Phrase
            // break-all forces word breaks but Compose doesn't have this
            // We use Default and rely on Strategy.HighQuality
            config.wordBreak == WordBreakMode.BREAK_ALL -> ComposeWordBreak.Default
            else -> ComposeWordBreak.Default
        }
    }

    /**
     * Capitalize the first letter of each word.
     */
    private fun String.capitalizeWords(): String {
        return split(WORD_BOUNDARY_REGEX).joinToString("") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    // Regex patterns for text preprocessing
    private val WHITESPACE_SEQUENCE = Regex("\\s+")
    private val SPACE_SEQUENCE = Regex("[ \\t]+")
    private val WORD_BOUNDARY_REGEX = Regex("(?<=\\s)|(?=\\s)")

    /**
     * Result of building wrap parameters for Text composable.
     */
    data class WrapParameters(
        val softWrap: Boolean,
        val lineBreak: LineBreak,
        val hyphens: Hyphens
    )

    /**
     * Build all wrap parameters at once.
     *
     * @param config TypographyConfig
     * @return WrapParameters with all settings
     */
    fun buildWrapParameters(config: TypographyConfig): WrapParameters {
        val wrapConfig = extractWrapConfig(config)
        return WrapParameters(
            softWrap = shouldSoftWrap(config),
            lineBreak = buildLineBreak(wrapConfig),
            hyphens = getHyphens(wrapConfig)
        )
    }

    /**
     * Recommended LineBreak presets based on content type.
     */
    object Presets {
        /** Body text - balanced, normal strictness */
        val Paragraph = LineBreak.Paragraph

        /** Headings - balanced line lengths */
        val Heading = LineBreak.Heading

        /** Simple breaking for UI labels */
        val Simple = LineBreak.Simple

        /** CJK-optimized strict breaking */
        val StrictCJK = LineBreak(
            strategy = Strategy.HighQuality,
            strictness = Strictness.Strict,
            wordBreak = ComposeWordBreak.Default
        )

        /** Allow breaking anywhere for narrow containers */
        val BreakAnywhere = LineBreak(
            strategy = Strategy.HighQuality,
            strictness = Strictness.Loose,
            wordBreak = ComposeWordBreak.Default
        )
    }
}

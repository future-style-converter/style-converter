package com.styleconverter.test.style.typography

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.sp

/**
 * Applies typography configuration to Compose TextStyle.
 *
 * ## Supported Features
 * - Font properties: family, size, weight, style, stretch
 * - Spacing: letter spacing, line height
 * - Alignment: text align
 * - Decoration: underline, line-through (with color, thickness, style tracking)
 * - Color: text color
 * - Shadow: text shadow
 * - Baseline: baseline shift (subscript, superscript)
 * - Line break: CJK line breaking rules
 *
 * ## Limitations
 * - Word spacing: Not directly supported in TextStyle
 * - Text transform: Must be applied to string content, not style
 * - White space: Affects wrapping behavior, handled separately
 * - Tab size: Must be applied by replacing tabs in content
 * - Extended decoration (color, thickness, style): Compose has limited support
 *
 * ## Usage
 * ```kotlin
 * val config = TypographyExtractor.extractTypographyConfig(properties)
 * val textStyle = TypographyApplier.buildTextStyle(config)
 *
 * // For text transform:
 * val transformer = TypographyApplier.getTextTransformer(config)
 * Text(text = transformer(originalText), style = textStyle)
 * ```
 */
object TypographyApplier {

    /**
     * Build a TextStyle from TypographyConfig.
     *
     * Combines all typography properties into a single TextStyle that can be
     * applied to Text composables.
     *
     * @param config TypographyConfig containing all typography properties
     * @return TextStyle with all applicable properties set
     */
    fun buildTextStyle(config: TypographyConfig): TextStyle {
        var style = TextStyle.Default

        // Apply font family if specified
        config.fontFamily?.let {
            style = style.copy(fontFamily = it)
        }

        // Apply font size if specified
        config.fontSize?.let {
            style = style.copy(fontSize = it)
        }

        // Apply font weight if specified
        config.fontWeight?.let {
            style = style.copy(fontWeight = it)
        }

        // Apply font style if specified
        config.fontStyle?.let {
            style = style.copy(fontStyle = it)
        }

        // Font-stretch: deliberately not applied via TextGeometricTransform.
        // CSS `font-stretch` only renders a visible change when the font
        // exposes a width axis (variable fonts) or has a matching width
        // family member; default system sans-serifs on iOS/web/Android
        // have neither, so the property is a no-op there. Compose's
        // `TextGeometricTransform(scaleX)` is an unconditional geometric
        // scale — applying it forges a "stretch" iOS/web don't render and
        // forces text wrapping that breaks SSIM (the
        // `Typography_FontUltraExpanded` fixture went 0.46 iOS-Android
        // because Android scaled to 200% and wrapped). The value is
        // still parsed (in `TypographyExtractor`) so audit coverage is
        // intact — but we don't draw a fake stretch.
        // config.fontStretch is intentionally read-only here.

        // Apply letter spacing if specified
        config.letterSpacing?.let {
            style = style.copy(letterSpacing = it)
        }

        // Apply line height if specified
        config.lineHeight?.let {
            style = style.copy(lineHeight = it)
        }

        // Apply text alignment if specified
        config.textAlign?.let {
            style = style.copy(textAlign = it)
        }

        // Apply text decoration if specified
        config.textDecoration?.let {
            style = style.copy(textDecoration = it)
        }

        // Apply text color if specified
        config.color?.let {
            style = style.copy(color = it)
        }

        // Apply text indent if specified
        config.textIndent?.let { indent ->
            style = style.copy(
                textIndent = TextIndent(firstLine = indent, restLine = 0.sp)
            )
        }

        // Apply text shadow if specified
        config.textShadow?.let { shadowConfig ->
            style = style.copy(shadow = shadowConfig.toShadow())
        }

        // Apply baseline shift if specified
        config.baselineShift?.let {
            style = style.copy(baselineShift = it)
        }

        // Apply line break mode if specified
        config.lineBreak?.let {
            style = style.copy(lineBreak = it.toComposeLineBreak())
        }

        return style
    }

    /**
     * Apply text transform by transforming the actual string.
     *
     * Compose doesn't have built-in text-transform support, so this returns
     * a function that transforms the text content before rendering.
     *
     * @param config TypographyConfig containing text transform setting
     * @return Function that transforms a string according to the config
     *
     * ## Usage
     * ```kotlin
     * val transformer = TypographyApplier.getTextTransformer(config)
     * Text(text = transformer("hello world"))
     * // Result for UPPERCASE: "HELLO WORLD"
     * // Result for CAPITALIZE: "Hello World"
     * ```
     */
    fun getTextTransformer(config: TypographyConfig): (String) -> String {
        return when (config.textTransform) {
            TextTransform.UPPERCASE -> { text -> text.uppercase() }
            TextTransform.LOWERCASE -> { text -> text.lowercase() }
            TextTransform.CAPITALIZE -> { text ->
                text.split(" ").joinToString(" ") { word ->
                    word.replaceFirstChar { char ->
                        if (char.isLowerCase()) char.titlecase() else char.toString()
                    }
                }
            }
            TextTransform.NONE, null -> { text -> text }
        }
    }

    /**
     * Get max lines from config.
     *
     * Considers both lineClamp (CSS -webkit-line-clamp) and maxLines,
     * with lineClamp taking precedence.
     *
     * @param config TypographyConfig containing max lines settings
     * @return Int max lines value, or Int.MAX_VALUE if not specified
     */
    fun getMaxLines(config: TypographyConfig): Int {
        return config.lineClamp ?: config.maxLines ?: Int.MAX_VALUE
    }

    /**
     * Check if text should wrap based on white space setting.
     *
     * @param config TypographyConfig containing white space setting
     * @return true if text should wrap, false if it should not
     */
    fun shouldWrap(config: TypographyConfig): Boolean {
        return when (config.whiteSpace) {
            WhiteSpace.NOWRAP -> false
            WhiteSpace.PRE -> false
            WhiteSpace.NORMAL,
            WhiteSpace.PRE_WRAP,
            WhiteSpace.PRE_LINE,
            WhiteSpace.BREAK_SPACES,
            null -> true
        }
    }

    /**
     * Check if whitespace should be preserved based on white space setting.
     *
     * @param config TypographyConfig containing white space setting
     * @return true if whitespace should be preserved (including newlines)
     */
    fun shouldPreserveWhitespace(config: TypographyConfig): Boolean {
        return when (config.whiteSpace) {
            WhiteSpace.PRE,
            WhiteSpace.PRE_WRAP,
            WhiteSpace.PRE_LINE,
            WhiteSpace.BREAK_SPACES -> true
            WhiteSpace.NORMAL,
            WhiteSpace.NOWRAP,
            null -> false
        }
    }

    /**
     * Get text overflow from config, providing a sensible default.
     *
     * @param config TypographyConfig containing text overflow setting
     * @return TextOverflow value, or Clip as default
     */
    fun getTextOverflow(config: TypographyConfig): androidx.compose.ui.text.style.TextOverflow {
        return config.textOverflow ?: androidx.compose.ui.text.style.TextOverflow.Clip
    }

    /**
     * Check if config implies text should be truncated with ellipsis.
     *
     * @param config TypographyConfig
     * @return true if ellipsis should be used for overflow
     */
    fun hasEllipsis(config: TypographyConfig): Boolean {
        return config.textOverflow == androidx.compose.ui.text.style.TextOverflow.Ellipsis ||
                (config.lineClamp != null && config.lineClamp > 0)
    }

    /**
     * Apply tab size by replacing tabs with spaces.
     *
     * Compose Text doesn't natively support CSS tab-size, so we replace tabs with spaces.
     *
     * @param text The input text that may contain tabs
     * @param config TypographyConfig containing tab size setting
     * @return Text with tabs replaced by appropriate number of spaces
     */
    fun applyTabSize(text: String, config: TypographyConfig): String {
        if (!text.contains('\t')) return text
        val tabConfig = config.tabSize ?: return text.replace("\t", "        ") // Default 8 spaces
        return text.replace("\t", tabConfig.toSpaceString())
    }

    /**
     * Get the effective layout direction from config.
     *
     * @param config TypographyConfig containing direction setting
     * @return LayoutDirection, or null if not specified
     */
    fun getLayoutDirection(config: TypographyConfig): androidx.compose.ui.unit.LayoutDirection? {
        return config.direction?.toLayoutDirection()
    }

    /**
     * Process text content by applying all text transformations.
     *
     * Applies in order:
     * 1. Tab replacement (if tab size is set)
     * 2. Word spacing (if set)
     * 3. Text transform (uppercase, lowercase, capitalize)
     *
     * @param text The original text
     * @param config TypographyConfig containing transformation settings
     * @return Transformed text
     */
    fun processTextContent(text: String, config: TypographyConfig): String {
        var result = text

        // Apply tab size
        result = applyTabSize(result, config)

        // Apply word spacing
        result = applyWordSpacing(result, config)

        // Apply text transform
        val transformer = getTextTransformer(config)
        result = transformer(result)

        return result
    }

    /**
     * Apply word spacing by inserting additional spacing characters between words.
     *
     * Since Compose TextStyle doesn't support word-spacing directly, we simulate it
     * by inserting Unicode spacing characters between words. This approach:
     * - Works with any font
     * - Respects text wrapping
     * - Maintains accessibility (screen readers still see spaces)
     *
     * @param text The input text
     * @param config TypographyConfig containing word spacing setting
     * @return Text with adjusted word spacing
     */
    fun applyWordSpacing(text: String, config: TypographyConfig): String {
        val wordSpacing = config.wordSpacing ?: return text
        val spacingValue = wordSpacing.value

        // No change needed for normal spacing
        if (spacingValue == 0f) return text

        return if (spacingValue > 0) {
            // Positive word spacing: add thin/hair spaces after regular spaces
            // Use combination of Unicode space characters to approximate the spacing
            val extraSpacing = buildWordSpacingString(spacingValue)
            text.replace(" ", " $extraSpacing")
        } else {
            // Negative word spacing: Replace some spaces with narrower characters
            // This is approximate since we can't truly reduce space width
            val narrowSpace = "\u200A" // Hair space (thinnest standard space)
            text.replace("  ", " $narrowSpace") // Reduce double spaces
        }
    }

    /**
     * Build a string of Unicode spacing characters to approximate the desired word spacing.
     *
     * Uses a combination of:
     * - Em Space (U+2003): width of 1em (~16px for 16sp font)
     * - En Space (U+2002): width of 0.5em (~8px)
     * - Thin Space (U+2009): width of ~0.2em (~3px)
     * - Hair Space (U+200A): width of ~0.1em (~1.5px)
     *
     * @param spacingValue The desired additional spacing in sp
     * @return String of spacing characters
     */
    private fun buildWordSpacingString(spacingValue: Float): String {
        if (spacingValue <= 0) return ""

        val builder = StringBuilder()
        var remaining = spacingValue

        // Em space = ~16sp
        while (remaining >= 16) {
            builder.append('\u2003')
            remaining -= 16
        }

        // En space = ~8sp
        while (remaining >= 8) {
            builder.append('\u2002')
            remaining -= 8
        }

        // Four-per-em space = ~4sp
        while (remaining >= 4) {
            builder.append('\u2005')
            remaining -= 4
        }

        // Thin space = ~2sp
        while (remaining >= 2) {
            builder.append('\u2009')
            remaining -= 2
        }

        // Hair space = ~1sp
        while (remaining >= 1) {
            builder.append('\u200A')
            remaining -= 1
        }

        return builder.toString()
    }

    /**
     * Check if word spacing is configured.
     *
     * @param config TypographyConfig
     * @return true if word spacing is set to a non-zero value
     */
    fun hasWordSpacing(config: TypographyConfig): Boolean {
        val spacing = config.wordSpacing ?: return false
        return spacing.value != 0f
    }
}

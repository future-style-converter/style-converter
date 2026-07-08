package com.styleconverter.test.style.typography

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontVariation

/**
 * Applies font variant configuration to Compose text styles.
 *
 * ## CSS Property Mapping
 * - font-variant-caps → Small caps via font feature "smcp" / "c2sc"
 * - font-variant-ligatures → Ligature features "liga", "dlig", "hlig", "calt"
 * - font-variant-numeric → Numeric features "onum", "lnum", "tnum", "pnum", "frac"
 * - font-variant-position → Subscript/superscript "subs", "sups"
 * - font-feature-settings → Direct OpenType feature control
 * - font-kerning → Kerning feature "kern"
 *
 * ## Compose Limitations
 * - Font feature settings require variable fonts or OpenType fonts
 * - Not all fonts support all features
 * - Some features may need custom font loading
 *
 * ## Usage
 * ```kotlin
 * val fontVariantConfig = FontVariantConfig(caps = FontVariantCaps.SMALL_CAPS)
 * val features = FontVariantApplier.buildFontFeatureSettings(fontVariantConfig)
 * // Use with custom font or SpanStyle.fontFeatureSettings
 * ```
 */
object FontVariantApplier {

    /**
     * Build a font feature settings string from FontVariantConfig.
     *
     * Returns a CSS-like font-feature-settings string that can be used
     * with fonts that support these OpenType features.
     *
     * @param config FontVariantConfig containing all variant settings
     * @return String in format "smcp" on, "liga" off, etc.
     */
    fun buildFontFeatureSettings(config: FontVariantConfig): String {
        val features = mutableListOf<String>()

        // Apply caps
        when (config.caps) {
            FontVariantCaps.SMALL_CAPS -> features.add("\"smcp\" 1")
            FontVariantCaps.ALL_SMALL_CAPS -> {
                features.add("\"smcp\" 1")
                features.add("\"c2sc\" 1")
            }
            FontVariantCaps.PETITE_CAPS -> features.add("\"pcap\" 1")
            FontVariantCaps.ALL_PETITE_CAPS -> {
                features.add("\"pcap\" 1")
                features.add("\"c2pc\" 1")
            }
            FontVariantCaps.UNICASE -> features.add("\"unic\" 1")
            FontVariantCaps.TITLING_CAPS -> features.add("\"titl\" 1")
            FontVariantCaps.NORMAL -> { /* No feature needed */ }
        }

        // Apply ligatures
        when (config.ligatures) {
            FontVariantLigatures.NONE -> {
                features.add("\"liga\" 0")
                features.add("\"clig\" 0")
                features.add("\"dlig\" 0")
                features.add("\"hlig\" 0")
                features.add("\"calt\" 0")
            }
            FontVariantLigatures.COMMON_LIGATURES -> features.add("\"liga\" 1")
            FontVariantLigatures.NO_COMMON_LIGATURES -> features.add("\"liga\" 0")
            FontVariantLigatures.DISCRETIONARY_LIGATURES -> features.add("\"dlig\" 1")
            FontVariantLigatures.NO_DISCRETIONARY_LIGATURES -> features.add("\"dlig\" 0")
            FontVariantLigatures.HISTORICAL_LIGATURES -> features.add("\"hlig\" 1")
            FontVariantLigatures.NO_HISTORICAL_LIGATURES -> features.add("\"hlig\" 0")
            FontVariantLigatures.CONTEXTUAL -> features.add("\"calt\" 1")
            FontVariantLigatures.NO_CONTEXTUAL -> features.add("\"calt\" 0")
            FontVariantLigatures.NORMAL -> { /* Default features */ }
        }

        // Apply numeric variants
        val numeric = config.numeric
        when (numeric.figure) {
            NumericFigure.LINING_NUMS -> features.add("\"lnum\" 1")
            NumericFigure.OLDSTYLE_NUMS -> features.add("\"onum\" 1")
            NumericFigure.NORMAL -> { }
        }
        when (numeric.spacing) {
            NumericSpacing.TABULAR_NUMS -> features.add("\"tnum\" 1")
            NumericSpacing.PROPORTIONAL_NUMS -> features.add("\"pnum\" 1")
            NumericSpacing.NORMAL -> { }
        }
        when (numeric.fraction) {
            NumericFraction.DIAGONAL_FRACTIONS -> features.add("\"frac\" 1")
            NumericFraction.STACKED_FRACTIONS -> features.add("\"afrc\" 1")
            NumericFraction.NORMAL -> { }
        }
        if (numeric.ordinal) features.add("\"ordn\" 1")
        if (numeric.slashedZero) features.add("\"zero\" 1")

        // Apply position
        when (config.position) {
            FontVariantPosition.SUB -> features.add("\"subs\" 1")
            FontVariantPosition.SUPER -> features.add("\"sups\" 1")
            FontVariantPosition.NORMAL -> { }
        }

        // Apply alternates
        when (config.alternates) {
            FontVariantAlternates.HISTORICAL_FORMS -> features.add("\"hist\" 1")
            FontVariantAlternates.NORMAL -> { }
        }

        // Apply kerning
        when (config.fontKerning) {
            FontKerningValue.NORMAL -> features.add("\"kern\" 1")
            FontKerningValue.NONE -> features.add("\"kern\" 0")
            FontKerningValue.AUTO -> { /* Browser decides */ }
        }

        // Apply explicit font feature settings
        config.fontFeatureSettings.forEach { setting ->
            features.add("\"${setting.tag}\" ${setting.value}")
        }

        return features.joinToString(", ")
    }

    /**
     * Get FontVariation settings for variable fonts.
     *
     * Returns a list of FontVariation.Setting for use with variable fonts.
     * Note: Requires API 26+ for full support.
     *
     * @param config FontVariantConfig containing variation settings
     * @return List of FontVariation.Setting
     */
    fun buildFontVariationSettings(config: FontVariantConfig): List<FontVariation.Setting> {
        val settings = mutableListOf<FontVariation.Setting>()

        // Optical sizing for variable fonts
        if (config.fontOpticalSizing == FontOpticalSizingValue.NONE) {
            settings.add(FontVariation.Setting("opsz", 0f))
        }

        return settings
    }

    /**
     * Check if font variants require special font support.
     *
     * @param config FontVariantConfig to check
     * @return true if the config requires OpenType font features
     */
    fun requiresFontFeatures(config: FontVariantConfig): Boolean {
        return config.caps != FontVariantCaps.NORMAL ||
                config.ligatures != FontVariantLigatures.NORMAL ||
                config.numeric != FontVariantNumeric() ||
                config.position != FontVariantPosition.NORMAL ||
                config.alternates != FontVariantAlternates.NORMAL ||
                config.fontFeatureSettings.isNotEmpty()
    }

    /**
     * Apply small-caps transformation to text.
     *
     * This is a fallback for when font features aren't available.
     * It converts lowercase letters to uppercase at a smaller size.
     *
     * Note: This is a visual approximation, not true small-caps.
     *
     * @param text Input text
     * @return Transformed text (uppercase) - actual size reduction must be done in styling
     */
    fun applySmallCapsFallback(text: String): String {
        // For true small-caps, we'd need to render at smaller size
        // This just uppercases - the caller should reduce font size for lowercase chars
        return text.uppercase()
    }

    /**
     * Get recommended scale factor for small-caps fallback.
     *
     * True small-caps are typically 70-80% of the x-height.
     */
    fun getSmallCapsScaleFactor(): Float = 0.75f

    /**
     * Check if a character was originally lowercase (for small-caps rendering).
     *
     * @param original Original character
     * @param current Current (possibly transformed) character
     * @return true if the character should be rendered as small-cap
     */
    fun shouldRenderAsSmallCap(original: Char, current: Char): Boolean {
        return original.isLowerCase() && current.isUpperCase()
    }
}

package app.parsing.css.properties.longhands

import app.irmodels.*
import app.irmodels.properties.layout.position.InsetValue
import app.parsing.css.properties.primitiveParsers.ColorParser
import app.parsing.css.properties.primitiveParsers.LengthParser

/**
 * Factory functions for creating common property parsers.
 *
 * These factories eliminate boilerplate code by providing reusable parser implementations
 * for common patterns like color properties, spacing properties, etc.
 *
 * Usage:
 * ```kotlin
 * // Instead of creating a separate BorderTopColorPropertyParser file:
 * "border-top-color" to colorParser(::BorderTopColorProperty)
 *
 * // Instead of creating a separate PaddingTopPropertyParser file:
 * "padding-top" to paddingParser(::PaddingTopProperty)
 * ```
 */
object PropertyParserFactory {

    /** CSS global keywords that are valid for any property */
    val globalKeywords = setOf("inherit", "initial", "unset", "revert", "revert-layer")

    // ========== Color Parsers ==========

    /**
     * Create a parser for simple color properties.
     *
     * Delegates to ColorParser and wraps result in the given property constructor.
     *
     * @param constructor Function that creates the IRProperty from an IRColor
     */
    inline fun <reified T : IRProperty> colorParser(
        crossinline constructor: (IRColor) -> T
    ): PropertyParser = object : PropertyParser {
        override fun parse(value: String): IRProperty? {
            val color = ColorParser.parse(value) ?: return null
            return constructor(color)
        }
    }

    // ========== Padding Parsers ==========

    /**
     * Create a parser for padding properties.
     *
     * Supports: length, percentage, expressions (calc, var, etc.), keywords.
     *
     * @param constructor Function that creates the IRProperty from a PaddingValue
     */
    inline fun <reified T : IRProperty> paddingParser(
        crossinline constructor: (PaddingValue) -> T
    ): PropertyParser = object : PropertyParser {
        override fun parse(value: String): IRProperty? {
            val trimmed = value.trim()

            // Check for CSS expressions (calc, clamp, min, max, var)
            if (LengthParser.isExpression(trimmed)) {
                return constructor(PaddingValue.Expression(trimmed))
            }

            // Check for global keywords
            val lower = trimmed.lowercase()
            if (lower in globalKeywords) {
                return constructor(PaddingValue.Keyword(lower))
            }

            // Try to parse as length
            val length = LengthParser.parse(trimmed) ?: return null

            val paddingValue = if (length.unit == IRLength.LengthUnit.PERCENT) {
                PaddingValue.Percentage(IRPercentage(length.value))
            } else {
                PaddingValue.Length(length)
            }

            return constructor(paddingValue)
        }
    }

    // ========== Margin Parsers ==========

    /**
     * Create a parser for margin properties.
     *
     * Supports: length, percentage, "auto", expressions.
     *
     * @param constructor Function that creates the IRProperty from a MarginValue
     */
    inline fun <reified T : IRProperty> marginParser(
        crossinline constructor: (MarginValue) -> T
    ): PropertyParser = object : PropertyParser {
        override fun parse(value: String): IRProperty? {
            val trimmed = value.trim()
            val lower = trimmed.lowercase()

            // Check for "auto" keyword
            if (lower == "auto") {
                return constructor(MarginValue.Auto())
            }

            // Check for CSS expressions (calc, clamp, min, max, var)
            if (LengthParser.isExpression(trimmed)) {
                return constructor(MarginValue.Expression(trimmed))
            }

            // Check for global keywords (treat as expression)
            if (lower in globalKeywords) {
                return constructor(MarginValue.Expression(lower))
            }

            // Try to parse as length
            val length = LengthParser.parse(trimmed) ?: return null

            val marginValue = if (length.unit == IRLength.LengthUnit.PERCENT) {
                MarginValue.Percentage(IRPercentage(length.value))
            } else {
                MarginValue.Length(length)
            }

            return constructor(marginValue)
        }
    }

    // ========== Border Width Parsers ==========

    /**
     * Create a parser for border-width properties.
     *
     * Supports: thin, medium, thick, length.
     *
     * @param constructor Function that creates the IRProperty from a BorderWidthValue
     */
    inline fun <reified T : IRProperty> borderWidthParser(
        crossinline constructor: (BorderWidthValue) -> T
    ): PropertyParser = object : PropertyParser {
        override fun parse(value: String): IRProperty? {
            val trimmed = value.trim()
            val lower = trimmed.lowercase()

            // Check for keyword widths
            if (lower in setOf("thin", "medium", "thick")) {
                return constructor(BorderWidthValue.fromKeyword(lower))
            }

            // Check for CSS expressions
            if (LengthParser.isExpression(trimmed)) {
                // For expressions, use medium as default normalized value
                return constructor(BorderWidthValue.fromKeyword(trimmed))
            }

            // Check for global keywords
            if (lower in globalKeywords) {
                return constructor(BorderWidthValue.fromKeyword(lower))
            }

            // Try to parse as length
            val length = LengthParser.parse(trimmed) ?: return null
            return constructor(BorderWidthValue.fromLength(length))
        }
    }

    // ========== Border Style Parsers ==========

    /**
     * Create a parser for border-style properties.
     *
     * @param constructor Function that creates the IRProperty from a LineStyle
     */
    inline fun <reified T : IRProperty> borderStyleParser(
        crossinline constructor: (LineStyle) -> T
    ): PropertyParser = object : PropertyParser {
        private val styleMap = mapOf(
            "none" to LineStyle.NONE,
            "hidden" to LineStyle.HIDDEN,
            "dotted" to LineStyle.DOTTED,
            "dashed" to LineStyle.DASHED,
            "solid" to LineStyle.SOLID,
            "double" to LineStyle.DOUBLE,
            "groove" to LineStyle.GROOVE,
            "ridge" to LineStyle.RIDGE,
            "inset" to LineStyle.INSET,
            "outset" to LineStyle.OUTSET
        )

        override fun parse(value: String): IRProperty? {
            val style = styleMap[value.trim().lowercase()] ?: return null
            return constructor(style)
        }
    }

    // ========== Scroll Padding Parsers ==========

    /**
     * Create a parser for scroll-padding properties.
     *
     * Supports: length, percentage, "auto", expressions.
     *
     * @param constructor Function that creates the IRProperty from a ScrollPaddingValue
     */
    inline fun <reified T : IRProperty> scrollPaddingParser(
        crossinline constructor: (ScrollPaddingValue) -> T
    ): PropertyParser = object : PropertyParser {
        override fun parse(value: String): IRProperty? {
            val trimmed = value.trim()
            val lower = trimmed.lowercase()

            // Check for "auto" keyword
            if (lower == "auto") {
                return constructor(ScrollPaddingValue.Auto())
            }

            // Check for CSS expressions
            if (LengthParser.isExpression(trimmed)) {
                return constructor(ScrollPaddingValue.Raw(trimmed))
            }

            // Check for global keywords
            if (lower in globalKeywords) {
                return constructor(ScrollPaddingValue.Keyword(lower))
            }

            // Try to parse as length
            val length = LengthParser.parse(trimmed) ?: return null

            val scrollPaddingValue = if (length.unit == IRLength.LengthUnit.PERCENT) {
                ScrollPaddingValue.Percentage(IRPercentage(length.value))
            } else {
                ScrollPaddingValue.Length(length)
            }

            return constructor(scrollPaddingValue)
        }
    }

    // ========== Inset Parsers (top, right, bottom, left, inset-*) ==========

    /**
     * Create a parser for inset/position properties (top, right, bottom, left, inset-block-start, etc.).
     *
     * Supports: length, percentage, "auto", expressions.
     *
     * @param constructor Function that creates the IRProperty from an InsetValue
     */
    inline fun <reified T : IRProperty> insetParser(
        crossinline constructor: (InsetValue) -> T
    ): PropertyParser = object : PropertyParser {
        override fun parse(value: String): IRProperty? {
            val trimmed = value.trim()
            val lower = trimmed.lowercase()

            // Check for "auto" keyword
            if (lower == "auto") {
                return constructor(InsetValue.Auto())
            }

            // Check for CSS expressions
            if (LengthParser.isExpression(trimmed)) {
                return constructor(InsetValue.Expression(trimmed))
            }

            // Check for global keywords (treat as expression)
            if (lower in globalKeywords) {
                return constructor(InsetValue.Expression(lower))
            }

            // Try to parse as length
            val length = LengthParser.parse(trimmed) ?: return null

            val insetValue = if (length.unit == IRLength.LengthUnit.PERCENT) {
                InsetValue.PercentageValue(IRPercentage(length.value))
            } else {
                InsetValue.LengthValue(length)
            }

            return constructor(insetValue)
        }
    }

    // ========== Keyword Parsers ==========

    /**
     * Create a parser for simple keyword-only properties.
     *
     * @param allowedKeywords Set of valid keywords
     * @param constructor Function that creates the IRProperty from the keyword string
     */
    inline fun <reified T : IRProperty> keywordParser(
        allowedKeywords: Set<String>,
        crossinline constructor: (String) -> T
    ): PropertyParser = object : PropertyParser {
        override fun parse(value: String): IRProperty? {
            val lower = value.trim().lowercase()
            if (lower !in allowedKeywords && lower !in globalKeywords) return null
            return constructor(lower)
        }
    }
}

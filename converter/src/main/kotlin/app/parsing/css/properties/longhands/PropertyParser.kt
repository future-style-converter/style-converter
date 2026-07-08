package app.parsing.css.properties.longhands

import app.irmodels.IRProperty

/**
 * Interface for longhand CSS property parsers.
 *
 * ## Purpose
 * Each parser transforms a raw CSS value string into a specific IRProperty subclass.
 * Parsers are registered in PropertyParserRegistry and invoked via property name lookup.
 *
 * ## Implementation Options
 * 1. **Object parser**: For complex parsing logic (see FontSizePropertyParser)
 * 2. **Factory parser**: Use PropertyParserFactory for common patterns (see colorParser())
 *
 * ## Example Implementation
 * ```kotlin
 * object MyPropertyParser : PropertyParser {
 *     override fun parse(value: String): IRProperty? {
 *         val parsed = parseValue(value) ?: return null
 *         return MyProperty(parsed)
 *     }
 * }
 * ```
 *
 * ## Parsing Convention
 * - Return `null` if parsing fails (triggers GenericProperty fallback)
 * - Normalize values at parse time (colors → sRGB, angles → degrees, etc.)
 * - Preserve original representation for CSS regeneration
 *
 * @see PropertyParserRegistry for the parser lookup map
 * @see PropertyParserFactory for factory-based parser creation
 */
interface PropertyParser {
    /**
     * Parse a CSS property value into a specific IRProperty instance.
     *
     * @param value The property value as a string
     * @return The specific IRProperty subclass, or null if parsing fails
     */
    fun parse(value: String): IRProperty?
}

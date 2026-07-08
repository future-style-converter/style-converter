package app.parsing.css.properties.shorthands

/**
 * Interface for CSS shorthand property expanders.
 *
 * ## Purpose
 * Shorthand properties combine multiple related properties into one declaration.
 * This interface defines expanders that break them into individual longhand properties
 * for uniform processing by property parsers.
 *
 * ## Architecture
 * ```
 * CSS Shorthand
 *     ↓ ShorthandRegistry.expand()
 * Map<String, String> of longhands
 *     ↓ PropertyParserRegistry.parse() for each
 * List<IRProperty>
 * ```
 *
 * ## Expansion Patterns
 * 1. **1-4 value pattern** (padding, margin, inset):
 *    - 1 value: all sides same
 *    - 2 values: vertical | horizontal
 *    - 3 values: top | horizontal | bottom
 *    - 4 values: top | right | bottom | left
 *
 * 2. **Component properties** (border, outline, animation):
 *    - Parse different value types (width, style, color)
 *    - Assign each to its respective longhand
 *
 * 3. **Logical properties** (margin-block, padding-inline):
 *    - 1 value: start and end same
 *    - 2 values: start | end
 *
 * ## Example
 * ```kotlin
 * // Input: padding: 10px 20px
 * // Output:
 * {
 *   "padding-top": "10px",
 *   "padding-right": "20px",
 *   "padding-bottom": "10px",
 *   "padding-left": "20px"
 * }
 * ```
 *
 * @see ShorthandRegistry for the expander lookup map
 * @see PropertiesParser for how expansion fits in the pipeline
 */
interface ShorthandExpander {
    /**
     * Expand a shorthand property value into longhand properties.
     *
     * @param value The shorthand value (e.g., "10px 20px" for padding)
     * @return Map of longhand property names to their values
     */
    fun expand(value: String): Map<String, String>
}

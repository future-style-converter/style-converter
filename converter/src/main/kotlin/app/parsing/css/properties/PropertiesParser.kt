package app.parsing.css.properties

import app.irmodels.IRProperty
import app.parsing.css.CssPropertyValue
import app.parsing.css.properties.shorthands.ShorthandRegistry
import app.parsing.css.properties.longhands.PropertyParserRegistry

/**
 * Main orchestrator for CSS property parsing.
 *
 * Process:
 * 1. Validate properties (filter invalid CSS property names)
 * 2. Expand shorthands to longhands (padding: 10px → padding-top/right/bottom/left)
 * 3. Parse each longhand into specific IRProperty (background-color → BackgroundColorProperty)
 * 4. Fallback to GenericProperty for properties without specific parsers
 *
 * Example:
 * Input: {
 *   "padding": "10px 20px",
 *   "background-color": "#FF0000",
 *   "opacity": "0.5"
 * }
 *
 * Output: [
 *   PaddingTopProperty(Length(10px)),
 *   PaddingRightProperty(Length(20px)),
 *   PaddingBottomProperty(Length(10px)),
 *   PaddingLeftProperty(Length(20px)),
 *   BackgroundColorProperty(IRColor.Hex("#FF0000")),
 *   GenericProperty("opacity", "0.5")  // No parser yet
 * ]
 */
object PropertiesParser {

    /**
     * Normalize CSS property name to lowercase kebab-case.
     * - Converts camelCase to kebab-case: "backgroundColor" → "background-color"
     * - Lowercases all: "DISPLAY" → "display", "DiSpLaY" → "display"
     */
    private fun normalizePropertyName(name: String): String {
        // If already contains hyphens, just lowercase
        if (name.contains("-")) {
            return name.lowercase()
        }

        // Check if it looks like camelCase (lowercase start, has uppercase)
        val isCamelCase = name.isNotEmpty() &&
                name[0].isLowerCase() &&
                name.any { it.isUpperCase() }

        return if (isCamelCase) {
            // Convert camelCase to kebab-case
            name.replace(Regex("([a-z])([A-Z])")) { matchResult ->
                "${matchResult.groupValues[1]}-${matchResult.groupValues[2].lowercase()}"
            }.lowercase()
        } else {
            // Just lowercase (handles DISPLAY, DiSpLaY, etc.)
            name.lowercase()
        }
    }

    /**
     * Parse a map of CSS properties into a list of specific IRProperty instances.
     *
     * @param properties Map of property name → CssPropertyValue
     * @return Mutable list of IRProperty instances (specific types from irmodels/)
     */
    fun parse(properties: Map<String, CssPropertyValue>): MutableList<IRProperty> {
        val result = mutableListOf<IRProperty>()

        // Normalize property names (camelCase to kebab-case, mixed case to lowercase)
        val normalizedProperties = properties.mapKeys { (name, _) -> normalizePropertyName(name) }

        // Step 1: Validate CSS properties (filter out invalid property names)
        val validProperties = normalizedProperties.filter { (name, _) ->
            CssPropertyValidator.isValidProperty(name)
        }

        // Optional: Log removed invalid properties for debugging
        val invalidProperties = normalizedProperties.keys - validProperties.keys
        if (invalidProperties.isNotEmpty()) {
            println("[CSS Parser] Removed invalid properties: ${invalidProperties.joinToString(", ")}")
        }

        // Step 2: Expand shorthands to longhands
        val expandedProperties = mutableMapOf<String, String>()

        for ((name, cssValue) in validProperties) {
            val value = cssValue.value
            // Property names are already normalized by camelToKebab

            if (ShorthandRegistry.isShorthand(name)) {
                // Expand shorthand into multiple longhands
                val expanded = ShorthandRegistry.expand(name, value)
                expandedProperties.putAll(expanded)

                println("[CSS Parser] Expanded '$name: $value' → ${expanded.keys.joinToString(", ")}")
            } else {
                // Keep longhand as-is
                expandedProperties[name] = value
            }
        }

        // Step 3: Parse each longhand property into specific IRProperty
        for ((name, value) in expandedProperties) {
            val property = PropertyParserRegistry.parse(name, value)

            if (property != null) {
                // Successfully parsed to specific property class
                result.add(property)
            } else {
                // Fallback: create generic property wrapper
                result.add(GenericProperty(name, value))
                println("[CSS Parser] No parser for '$name', using GenericProperty")
            }
        }

        println("[CSS Parser] Parsed ${result.size} properties (${result.count { it is GenericProperty }} generic)")

        return result
    }
}

package app.parsing.css.selectors

import app.irmodels.IRSelector
import app.irmodels.IRProperty
import app.parsing.css.CssSelector
import app.parsing.css.properties.PropertiesParser

/**
 * Pseudo-class selector parser for CSS state-based styles.
 *
 * Converts CssSelector objects (raw selector + properties) into IRSelector objects
 * with fully parsed property types. Strips the leading ':' from pseudo-class names.
 *
 * ## Example
 * ```
 * Input:  CssSelector(selector = ":hover", properties = {"color": "blue"})
 * Output: IRSelector(condition = "hover", properties = [ColorProperty(...)])
 * ```
 *
 * ## Supported Pseudo-classes
 * - State: :hover, :active, :focus, :focus-within, :focus-visible
 * - Form: :disabled, :enabled, :checked, :invalid, :valid
 * - Structural: :first-child, :last-child, :nth-child(n)
 *
 * @param selectors List of raw selector definitions
 * @return List of IRSelector with parsed properties
 * @see PropertiesParser for property parsing logic
 */
fun parseSelectors(selectors: List<CssSelector>?): List<IRSelector> {
    if (selectors == null) return emptyList()
    return selectors.map { sel ->
        // Parse directly to specific properties
        val properties = PropertiesParser.parse(sel.properties)
        // Remove leading ':' from pseudo-class selectors (e.g., ":hover" → "hover")
        val cleanCondition = sel.selector.removePrefix(":")
        IRSelector(condition = cleanCondition, properties = properties)
    }
}
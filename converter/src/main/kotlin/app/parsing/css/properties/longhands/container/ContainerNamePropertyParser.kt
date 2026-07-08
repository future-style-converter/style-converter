package app.parsing.css.properties.longhands.container

import app.irmodels.IRProperty
import app.irmodels.properties.container.ContainerNameProperty
import app.irmodels.properties.container.ContainerNameValue
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `container-name` property.
 *
 * Syntax: none | <custom-ident>+
 *
 * Examples:
 * - "none" → None
 * - "sidebar" → Single("sidebar")
 * - "sidebar card" → Multiple(["sidebar", "card"])
 */
object ContainerNamePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()

        if (trimmed.isEmpty()) return null

        val lower = trimmed.lowercase()

        // Handle "none" keyword
        if (lower == "none") {
            return ContainerNameProperty(ContainerNameValue.None)
        }

        // Handle multiple names (space-separated)
        val names = trimmed.split("\\s+".toRegex())
        val containerValue = if (names.size == 1) {
            ContainerNameValue.Single(names[0])
        } else {
            ContainerNameValue.Multiple(names)
        }

        return ContainerNameProperty(containerValue)
    }
}

package app.parsing.css.properties.longhands.container

import app.irmodels.IRProperty
import app.irmodels.properties.container.ContainerProperty
import app.irmodels.properties.container.ContainerTypeValue
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `container` shorthand property.
 * Note: Treated as longhand for simplicity in IR model.
 *
 * Syntax: <container-name> [ / <container-type> ]?
 *
 * Examples:
 * - container: size
 * - container: inline-size
 * - container: myContainer / size
 * - container: myContainer
 */
object ContainerPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()

        if (trimmed.isEmpty()) return null

        // Split by '/' to separate name and type
        val parts = trimmed.split("/").map { it.trim() }

        return when (parts.size) {
            1 -> {
                // Could be just type or just name
                val part = parts[0].lowercase()
                when (part) {
                    "size" -> ContainerProperty(name = null, type = ContainerTypeValue.SIZE)
                    "inline-size" -> ContainerProperty(name = null, type = ContainerTypeValue.INLINE_SIZE)
                    "normal" -> ContainerProperty(name = null, type = ContainerTypeValue.NORMAL)
                    else -> {
                        // Assume it's a name with default type (normal)
                        ContainerProperty(name = parts[0], type = ContainerTypeValue.NORMAL)
                    }
                }
            }
            2 -> {
                // name / type
                val name = if (parts[0].lowercase() == "none") null else parts[0]
                val type = when (parts[1].lowercase()) {
                    "size" -> ContainerTypeValue.SIZE
                    "inline-size" -> ContainerTypeValue.INLINE_SIZE
                    "normal" -> ContainerTypeValue.NORMAL
                    else -> return null
                }
                ContainerProperty(name = name, type = type)
            }
            else -> null
        }
    }
}

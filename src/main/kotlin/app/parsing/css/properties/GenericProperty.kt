package app.parsing.css.properties

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Generic property wrapper for properties that don't have specific parsers yet.
 *
 * This is a fallback used when:
 * - A property is valid CSS but we haven't implemented a parser for it yet
 * - A property fails to parse with its specific parser
 *
 * When serialized to JSON, this will show as a generic property with just the name and value,
 * allowing us to gradually implement specific parsers without breaking the system.
 *
 * Example:
 * - opacity: 0.5 → GenericProperty("opacity", "0.5")
 * - box-shadow: 0 2px 4px rgba(0,0,0,0.1) → GenericProperty("box-shadow", "0 2px 4px rgba(0,0,0,0.1)")
 */
@Serializable
data class GenericProperty(
    val name: String,
    val rawValue: String
) : IRProperty {
    override val propertyName: String = name
}

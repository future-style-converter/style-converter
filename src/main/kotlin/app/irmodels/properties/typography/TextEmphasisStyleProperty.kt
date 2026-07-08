package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `text-emphasis-style` property.
 * Sets style of emphasis marks.
 */
@Serializable
data class TextEmphasisStyleProperty(
    val style: TextEmphasisStyle
) : IRProperty {
    override val propertyName = "text-emphasis-style"
}

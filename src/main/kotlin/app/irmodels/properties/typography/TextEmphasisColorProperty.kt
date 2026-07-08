package app.irmodels.properties.typography

import app.irmodels.IRColor
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `text-emphasis-color` property.
 * Sets color of emphasis marks.
 */
@Serializable
data class TextEmphasisColorProperty(
    val color: IRColor
) : IRProperty {
    override val propertyName = "text-emphasis-color"
}

package app.irmodels.properties.borders

import app.irmodels.*
import kotlinx.serialization.Serializable

@Serializable
data class OutlineWidthProperty(
    val width: BorderWidthProperty.BorderWidth
) : IRProperty {
    override val propertyName = "outline-width"
}

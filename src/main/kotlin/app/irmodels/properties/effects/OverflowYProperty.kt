package app.irmodels.properties.effects

import app.irmodels.*
import kotlinx.serialization.Serializable

@Serializable
data class OverflowYProperty(
    val value: OverflowValue
) : IRProperty {
    override val propertyName = "overflow-y"
}

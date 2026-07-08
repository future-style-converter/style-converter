package app.irmodels.properties.effects

import app.irmodels.*
import kotlinx.serialization.Serializable

@Serializable
data class OverflowXProperty(
    val value: OverflowValue
) : IRProperty {
    override val propertyName = "overflow-x"
}

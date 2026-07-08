package app.irmodels.properties.effects

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class MaskPositionXProperty(
    val value: MaskPositionValue
) : IRProperty {
    override val propertyName = "mask-position-x"
}

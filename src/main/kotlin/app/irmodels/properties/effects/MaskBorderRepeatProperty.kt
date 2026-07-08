package app.irmodels.properties.effects

import app.irmodels.*
import kotlinx.serialization.Serializable

@Serializable
data class MaskBorderRepeatProperty(
    val repeat: MaskBorderRepeatValue
) : IRProperty {
    override val propertyName = "mask-border-repeat"
}

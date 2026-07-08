package app.irmodels.properties.effects

import app.irmodels.*
import kotlinx.serialization.Serializable

@Serializable
data class MaskBorderModeProperty(
    val mode: MaskBorderModeValue
) : IRProperty {
    override val propertyName = "mask-border-mode"
}

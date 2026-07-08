package app.irmodels.properties.rhythm

import app.irmodels.IRLength
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class BlockStepSizeProperty(val value: IRLength) : IRProperty {
    override val propertyName = "block-step-size"
}

package app.irmodels.properties.rhythm

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class BlockStepRoundProperty(val value: BlockStepRoundValue) : IRProperty {
    override val propertyName = "block-step-round"
}

package app.irmodels.properties.rhythm

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class BlockStepInsertProperty(val value: BlockStepInsertValue) : IRProperty {
    override val propertyName = "block-step-insert"
}

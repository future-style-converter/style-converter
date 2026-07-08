package app.irmodels.properties.rhythm

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class BlockStepAlignProperty(val value: BlockStepAlignValue) : IRProperty {
    override val propertyName = "block-step-align"
}

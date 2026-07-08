package app.irmodels.properties.rhythm

import app.irmodels.IRLength
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class BlockStepProperty(
    val size: IRLength?,
    val insert: BlockStepInsertValue?,
    val align: BlockStepAlignValue?,
    val round: BlockStepRoundValue?
) : IRProperty {
    override val propertyName = "block-step"
}

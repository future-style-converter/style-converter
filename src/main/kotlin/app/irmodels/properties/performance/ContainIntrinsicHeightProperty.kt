package app.irmodels.properties.performance

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class ContainIntrinsicHeightProperty(val value: ContainIntrinsicValue) : IRProperty {
    override val propertyName = "contain-intrinsic-height"
}

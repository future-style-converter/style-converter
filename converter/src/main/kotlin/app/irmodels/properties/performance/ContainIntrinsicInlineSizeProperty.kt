package app.irmodels.properties.performance

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class ContainIntrinsicInlineSizeProperty(val value: ContainIntrinsicValue) : IRProperty {
    override val propertyName = "contain-intrinsic-inline-size"
}

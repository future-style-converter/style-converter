package app.irmodels.properties.performance

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class ContainIntrinsicSizeProperty(
    val width: ContainIntrinsicValue,
    val height: ContainIntrinsicValue? = null
) : IRProperty {
    override val propertyName = "contain-intrinsic-size"
}

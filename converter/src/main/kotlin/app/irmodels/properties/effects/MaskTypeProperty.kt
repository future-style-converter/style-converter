package app.irmodels.properties.effects

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class MaskTypeValue {
    LUMINANCE,
    ALPHA
}

@Serializable
data class MaskTypeProperty(
    val value: MaskTypeValue
) : IRProperty {
    override val propertyName = "mask-type"
}

package app.irmodels.properties.effects

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class MaskOriginValue {
    CONTENT_BOX,
    PADDING_BOX,
    BORDER_BOX,
    FILL_BOX,
    STROKE_BOX,
    VIEW_BOX
}

@Serializable
data class MaskOriginProperty(
    val value: MaskOriginValue
) : IRProperty {
    override val propertyName = "mask-origin"
}

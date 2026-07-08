package app.irmodels.properties.effects

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class MaskClipValue {
    CONTENT_BOX,
    PADDING_BOX,
    BORDER_BOX,
    FILL_BOX,
    STROKE_BOX,
    VIEW_BOX,
    NO_CLIP
}

@Serializable
data class MaskClipProperty(
    val value: MaskClipValue
) : IRProperty {
    override val propertyName = "mask-clip"
}

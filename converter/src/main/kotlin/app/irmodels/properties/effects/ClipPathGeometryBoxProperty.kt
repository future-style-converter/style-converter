package app.irmodels.properties.effects

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class ClipPathGeometryBoxValue {
    FILL_BOX,
    STROKE_BOX,
    VIEW_BOX,
    MARGIN_BOX,
    BORDER_BOX,
    PADDING_BOX,
    CONTENT_BOX
}

/**
 * Represents the CSS `clip-path-geometry-box` property.
 * Specifies the reference box for clip-path.
 */
@Serializable
data class ClipPathGeometryBoxProperty(
    val value: ClipPathGeometryBoxValue
) : IRProperty {
    override val propertyName = "clip-path-geometry-box"
}

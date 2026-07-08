package app.irmodels.properties.svg

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class PaintOrderValue {
    NORMAL,
    FILL,
    STROKE,
    MARKERS
}

/**
 * Represents the CSS `paint-order` property.
 * Specifies the order in which fill, stroke, and markers are painted.
 */
@Serializable
data class PaintOrderProperty(
    val values: List<PaintOrderValue>
) : IRProperty {
    override val propertyName = "paint-order"
}

package app.irmodels.properties.layout.advanced

import app.irmodels.IRProperty
import app.irmodels.IRLength
import app.irmodels.IRPercentage
import app.irmodels.IRAngle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class GeometryBox {
    @SerialName("border-box") BORDER_BOX,
    @SerialName("padding-box") PADDING_BOX,
    @SerialName("content-box") CONTENT_BOX,
    @SerialName("margin-box") MARGIN_BOX,
    @SerialName("fill-box") FILL_BOX,
    @SerialName("stroke-box") STROKE_BOX,
    @SerialName("view-box") VIEW_BOX
}

@Serializable
sealed interface OffsetPathValue {
    @Serializable
    @SerialName("none")
    data object None : OffsetPathValue

    @Serializable
    @SerialName("path-string")
    data class PathString(val path: String) : OffsetPathValue  // SVG path data

    @Serializable
    @SerialName("url")
    data class Url(val url: String) : OffsetPathValue  // Reference to SVG element

    @Serializable
    @SerialName("ray")
    data class Ray(
        val angle: IRAngle,
        val size: String? = null,  // closest-side, closest-corner, farthest-side, farthest-corner, sides
        val contain: Boolean = false,
        val position: String? = null  // "at center center" etc.
    ) : OffsetPathValue

    @Serializable
    @SerialName("circle")
    data class Circle(
        val radius: String,  // Length, percentage, or keyword like "closest-side"
        val position: String? = null  // "at center" etc.
    ) : OffsetPathValue

    @Serializable
    @SerialName("ellipse")
    data class Ellipse(
        val radiusX: String,
        val radiusY: String,
        val position: String? = null
    ) : OffsetPathValue

    @Serializable
    @SerialName("polygon")
    data class Polygon(
        val fillRule: String? = null,  // nonzero, evenodd
        val points: List<String>  // List of "x% y%" pairs
    ) : OffsetPathValue

    @Serializable
    @SerialName("inset")
    data class Inset(
        val offsets: String,  // "10% 20% 10% 20%" etc.
        val borderRadius: String? = null  // "round 20px" etc.
    ) : OffsetPathValue

    @Serializable
    @SerialName("rect")
    data class Rect(
        val top: String,
        val right: String,
        val bottom: String,
        val left: String,
        val borderRadius: String? = null
    ) : OffsetPathValue

    @Serializable
    @SerialName("xywh")
    data class Xywh(
        val x: String,
        val y: String,
        val width: String,
        val height: String,
        val borderRadius: String? = null
    ) : OffsetPathValue

    @Serializable
    @SerialName("geometry-box")
    data class GeometryBoxValue(val box: GeometryBox) : OffsetPathValue

    @Serializable
    @SerialName("shape-with-box")
    data class ShapeWithBox(val shape: String, val box: GeometryBox) : OffsetPathValue

    @Serializable
    @SerialName("keyword")
    data class Keyword(val keyword: String) : OffsetPathValue

    @Serializable
    @SerialName("raw")
    data class Raw(val value: String) : OffsetPathValue
}

/**
 * Represents the CSS `offset` shorthand property.
 * Animates an element along a motion path.
 */
@Serializable
data class OffsetProperty(
    val path: OffsetPathValue? = null,
    val distance: IRPercentage? = null,
    val rotate: IRAngle? = null
) : IRProperty {
    override val propertyName = "offset"
}

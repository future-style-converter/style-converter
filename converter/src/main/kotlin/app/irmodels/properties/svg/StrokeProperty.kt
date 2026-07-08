package app.irmodels.properties.svg

import app.irmodels.IRColor
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class StrokeProperty(
    val value: StrokeValue
) : IRProperty {
    override val propertyName = "stroke"

    constructor(color: IRColor) : this(StrokeValue.ColorValue(color))

    @Serializable
    sealed interface StrokeValue {
        @Serializable data class ColorValue(val color: IRColor) : StrokeValue
        @Serializable data class None(val unit: Unit = Unit) : StrokeValue
        @Serializable data class UrlReference(val url: String, val fallback: IRColor? = null) : StrokeValue
        @Serializable data class ContextFill(val unit: Unit = Unit) : StrokeValue
        @Serializable data class ContextStroke(val unit: Unit = Unit) : StrokeValue
        @Serializable data class Keyword(val keyword: String) : StrokeValue
        @Serializable data class Raw(val raw: String) : StrokeValue
    }

    val stroke: IRColor?
        get() = when (value) {
            is StrokeValue.ColorValue -> value.color
            else -> null
        }
}

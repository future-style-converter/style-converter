package app.irmodels.properties.svg

import app.irmodels.IRColor
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class FillProperty(
    val value: FillValue
) : IRProperty {
    override val propertyName = "fill"

    constructor(color: IRColor) : this(FillValue.ColorValue(color))

    @Serializable
    sealed interface FillValue {
        @Serializable data class ColorValue(val color: IRColor) : FillValue
        @Serializable data class None(val unit: Unit = Unit) : FillValue
        @Serializable data class UrlReference(val url: String, val fallback: IRColor? = null) : FillValue
        @Serializable data class ContextFill(val unit: Unit = Unit) : FillValue
        @Serializable data class ContextStroke(val unit: Unit = Unit) : FillValue
        @Serializable data class Keyword(val keyword: String) : FillValue
    }

    val color: IRColor?
        get() = when (value) {
            is FillValue.ColorValue -> value.color
            else -> null
        }
}

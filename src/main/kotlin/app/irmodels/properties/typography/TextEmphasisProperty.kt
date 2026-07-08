package app.irmodels.properties.typography

import app.irmodels.IRColor
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface TextEmphasisStyle {
    @Serializable @SerialName("none") data object None : TextEmphasisStyle
    @Serializable @SerialName("filled") data object Filled : TextEmphasisStyle
    @Serializable @SerialName("open") data object Open : TextEmphasisStyle
    @Serializable @SerialName("dot") data object Dot : TextEmphasisStyle
    @Serializable @SerialName("circle") data object Circle : TextEmphasisStyle
    @Serializable @SerialName("double-circle") data object DoubleCircle : TextEmphasisStyle
    @Serializable @SerialName("triangle") data object Triangle : TextEmphasisStyle
    @Serializable @SerialName("sesame") data object Sesame : TextEmphasisStyle
    @Serializable @SerialName("filled-dot") data object FilledDot : TextEmphasisStyle
    @Serializable @SerialName("open-dot") data object OpenDot : TextEmphasisStyle
    @Serializable @SerialName("filled-circle") data object FilledCircle : TextEmphasisStyle
    @Serializable @SerialName("open-circle") data object OpenCircle : TextEmphasisStyle
    @Serializable @SerialName("filled-double-circle") data object FilledDoubleCircle : TextEmphasisStyle
    @Serializable @SerialName("open-double-circle") data object OpenDoubleCircle : TextEmphasisStyle
    @Serializable @SerialName("filled-triangle") data object FilledTriangle : TextEmphasisStyle
    @Serializable @SerialName("open-triangle") data object OpenTriangle : TextEmphasisStyle
    @Serializable @SerialName("filled-sesame") data object FilledSesame : TextEmphasisStyle
    @Serializable @SerialName("open-sesame") data object OpenSesame : TextEmphasisStyle
    @Serializable @SerialName("custom") data class Custom(val character: String) : TextEmphasisStyle

    // Backwards compatible enum-style constants
    companion object {
        val NONE = None
        val FILLED = Filled
        val OPEN = Open
        val DOT = Dot
        val CIRCLE = Circle
        val DOUBLE_CIRCLE = DoubleCircle
        val TRIANGLE = Triangle
        val SESAME = Sesame
    }
}

/**
 * Represents the CSS `text-emphasis` shorthand property.
 * Applies emphasis marks to text (commonly used in East Asian typography).
 */
@Serializable
data class TextEmphasisProperty(
    val style: TextEmphasisStyle,
    val color: IRColor? = null
) : IRProperty {
    override val propertyName = "text-emphasis"
}

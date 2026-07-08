package app.irmodels.properties.borders

import app.irmodels.IRProperty
import app.irmodels.IRLength
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `border-width` property.
 */
@Serializable
data class BorderWidthProperty(
    val width: BorderWidth
) : IRProperty {
    override val propertyName = "border-width"

    /**
     * Border width values.
     */
    @Serializable
    sealed interface BorderWidth {
        @Serializable
        @SerialName("length")
        data class Length(val value: IRLength) : BorderWidth

        @Serializable
        @SerialName("keyword")
        data class Keyword(val value: BorderWidthKeyword) : BorderWidth
    }

    @Serializable
    enum class BorderWidthKeyword {
        THIN,
        MEDIUM,
        THICK
    }
}

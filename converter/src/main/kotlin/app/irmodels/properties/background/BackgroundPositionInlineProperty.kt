package app.irmodels.properties.background

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `background-position-inline` property.
 * Sets the inline-direction position of a background image.
 */
@Serializable
data class BackgroundPositionInlineProperty(
    val position: PositionInline
) : IRProperty {
    override val propertyName = "background-position-inline"

    @Serializable
    sealed interface PositionInline {
        @SerialName("length")
        @Serializable
        data class LengthValue(val length: IRLength) : PositionInline

        @SerialName("percentage")
        @Serializable
        data class PercentageValue(val percentage: IRPercentage) : PositionInline

        @SerialName("keyword")
        @Serializable
        data class Keyword(val value: InlineKeyword) : PositionInline
    }

    enum class InlineKeyword {
        START, CENTER, END
    }
}

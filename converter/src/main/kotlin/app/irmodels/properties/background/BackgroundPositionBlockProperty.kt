package app.irmodels.properties.background

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `background-position-block` property.
 * Sets the block-direction position of a background image.
 */
@Serializable
data class BackgroundPositionBlockProperty(
    val position: PositionBlock
) : IRProperty {
    override val propertyName = "background-position-block"

    @Serializable
    sealed interface PositionBlock {
        @SerialName("length")
        @Serializable
        data class LengthValue(val length: IRLength) : PositionBlock

        @SerialName("percentage")
        @Serializable
        data class PercentageValue(val percentage: IRPercentage) : PositionBlock

        @SerialName("keyword")
        @Serializable
        data class Keyword(val value: BlockKeyword) : PositionBlock
    }

    enum class BlockKeyword {
        START, CENTER, END
    }
}

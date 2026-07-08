package app.irmodels.properties.images

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ObjectPositionProperty(
    val position: Position
) : IRProperty {
    override val propertyName = "object-position"

    @Serializable
    data class Position(
        val x: ObjectPositionValue,
        val y: ObjectPositionValue
    )

    @Serializable
    sealed interface ObjectPositionValue {
        @SerialName("length")
        @Serializable
        data class LengthValue(val length: IRLength) : ObjectPositionValue

        @SerialName("percentage")
        @Serializable
        data class PercentageValue(val percentage: IRPercentage) : ObjectPositionValue

        @SerialName("keyword")
        @Serializable
        data class Keyword(val value: PositionKeyword) : ObjectPositionValue

        @SerialName("keyword-offset")
        @Serializable
        data class KeywordOffset(val keyword: PositionKeyword, val offset: IRLength) : ObjectPositionValue

        @SerialName("global")
        @Serializable
        data class GlobalKeyword(val value: String) : ObjectPositionValue

        @SerialName("raw")
        @Serializable
        data class Raw(val value: String) : ObjectPositionValue
    }

    enum class PositionKeyword {
        LEFT, CENTER, RIGHT, TOP, BOTTOM
    }
}

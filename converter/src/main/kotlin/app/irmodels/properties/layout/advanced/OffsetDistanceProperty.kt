package app.irmodels.properties.layout.advanced

import app.irmodels.IRPercentage
import app.irmodels.IRLength
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface OffsetDistanceValue {
    @Serializable
    @SerialName("percentage")
    data class Percentage(val value: IRPercentage) : OffsetDistanceValue

    @Serializable
    @SerialName("length")
    data class Length(val value: IRLength) : OffsetDistanceValue

    @Serializable
    @SerialName("keyword")
    data class Keyword(val keyword: String) : OffsetDistanceValue

    @Serializable
    @SerialName("raw")
    data class Raw(val value: String) : OffsetDistanceValue
}

/**
 * Represents the CSS `offset-distance` property.
 * Specifies position along a motion path.
 */
@Serializable
data class OffsetDistanceProperty(
    val value: OffsetDistanceValue
) : IRProperty {
    override val propertyName = "offset-distance"
}

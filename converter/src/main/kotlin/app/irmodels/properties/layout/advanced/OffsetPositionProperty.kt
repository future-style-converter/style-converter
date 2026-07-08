package app.irmodels.properties.layout.advanced

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `offset-position` property.
 * Specifies the initial position for offset path.
 */
@Serializable
sealed interface OffsetPositionValue {
    @Serializable
    @SerialName("position")
    data class Position(val x: AnchorValue, val y: AnchorValue) : OffsetPositionValue

    @Serializable
    @SerialName("auto")
    data object Auto : OffsetPositionValue

    @Serializable
    @SerialName("normal")
    data object Normal : OffsetPositionValue

    @Serializable
    @SerialName("keyword")
    data class Keyword(val keyword: String) : OffsetPositionValue

    @Serializable
    @SerialName("raw")
    data class Raw(val value: String) : OffsetPositionValue
}

@Serializable
data class OffsetPositionProperty(
    val value: OffsetPositionValue
) : IRProperty {
    override val propertyName = "offset-position"

    // Convenience constructor for backward compatibility
    constructor(x: AnchorValue, y: AnchorValue) : this(OffsetPositionValue.Position(x, y))
}

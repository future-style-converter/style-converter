package app.irmodels.properties.layout.advanced

import app.irmodels.IRLength
import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface AnchorValue {
    @Serializable
    @SerialName("length")
    data class Length(val value: IRLength) : AnchorValue

    @Serializable
    @SerialName("percentage")
    data class Percentage(val value: IRPercentage) : AnchorValue

    @Serializable
    @SerialName("auto")
    data object Auto : AnchorValue

    @Serializable
    @SerialName("center")
    data object Center : AnchorValue

    @Serializable
    @SerialName("left")
    data object Left : AnchorValue

    @Serializable
    @SerialName("right")
    data object Right : AnchorValue

    @Serializable
    @SerialName("top")
    data object Top : AnchorValue

    @Serializable
    @SerialName("bottom")
    data object Bottom : AnchorValue
}

/**
 * Represents the CSS `offset-anchor` property.
 * Specifies the anchor point for offset positioning.
 */
@Serializable
sealed interface OffsetAnchorValue {
    @Serializable
    @SerialName("position")
    data class Position(val x: AnchorValue, val y: AnchorValue) : OffsetAnchorValue

    @Serializable
    @SerialName("auto")
    data object Auto : OffsetAnchorValue

    @Serializable
    @SerialName("keyword")
    data class Keyword(val keyword: String) : OffsetAnchorValue

    @Serializable
    @SerialName("raw")
    data class Raw(val value: String) : OffsetAnchorValue
}

@Serializable
data class OffsetAnchorProperty(
    val value: OffsetAnchorValue
) : IRProperty {
    override val propertyName = "offset-anchor"

    // Convenience constructors for backward compatibility
    constructor(x: AnchorValue, y: AnchorValue) : this(OffsetAnchorValue.Position(x, y))
}

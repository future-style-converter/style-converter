package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface BlockEllipsisValue {
    @Serializable
    @SerialName("none")
    data object None : BlockEllipsisValue

    @Serializable
    @SerialName("auto")
    data object Auto : BlockEllipsisValue

    @Serializable
    @SerialName("custom")
    data class Custom(val value: String) : BlockEllipsisValue
}

/**
 * Represents the CSS `block-ellipsis` property.
 * Specifies the string to use when text overflows in block direction.
 */
@Serializable
data class BlockEllipsisProperty(
    val value: BlockEllipsisValue
) : IRProperty {
    override val propertyName = "block-ellipsis"
}

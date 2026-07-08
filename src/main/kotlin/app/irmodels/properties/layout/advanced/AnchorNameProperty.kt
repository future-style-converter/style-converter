package app.irmodels.properties.layout.advanced

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `anchor-name` property.
 * Names an element to be used as an anchor.
 */
@Serializable
data class AnchorNameProperty(
    val value: AnchorNameValue
) : IRProperty {
    override val propertyName = "anchor-name"
}

@Serializable
sealed interface AnchorNameValue {
    @Serializable
    @SerialName("none")
    data object None : AnchorNameValue

    @Serializable
    @SerialName("single")
    data class Single(val name: String) : AnchorNameValue

    @Serializable
    @SerialName("multiple")
    data class Multiple(val names: List<String>) : AnchorNameValue
}

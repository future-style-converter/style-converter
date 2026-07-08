package app.irmodels.properties.images

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ObjectViewBoxValue {
    @SerialName("none")
    @Serializable
    data object None : ObjectViewBoxValue

    @SerialName("inset")
    @Serializable
    data class Inset(
        val top: IRLength,
        val right: IRLength,
        val bottom: IRLength,
        val left: IRLength
    ) : ObjectViewBoxValue
}

/**
 * Represents the CSS `object-view-box` property.
 * Specifies the view box over an element's contents.
 */
@Serializable
data class ObjectViewBoxProperty(
    val value: ObjectViewBoxValue
) : IRProperty {
    override val propertyName = "object-view-box"
}

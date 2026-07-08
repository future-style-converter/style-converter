package app.irmodels.properties.transforms

import app.irmodels.IRLength
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `perspective` property.
 *
 * Sets the distance from the viewer to the z=0 plane for 3D transforms.
 *
 * Syntax: none | <length>
 */
@Serializable
data class PerspectiveProperty(val value: PerspectiveValue) : IRProperty {
    override val propertyName: String = "perspective"
}

@Serializable
sealed interface PerspectiveValue {
    @Serializable
    @SerialName("none")
    data object None : PerspectiveValue

    @Serializable
    @SerialName("length")
    data class Length(val distance: IRLength) : PerspectiveValue
}

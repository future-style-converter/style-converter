package app.irmodels.properties.rendering

import app.irmodels.IRAngle
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ImageOrientationValue {
    @Serializable
    @SerialName("none")
    data object None : ImageOrientationValue

    @Serializable
    @SerialName("from-image")
    data object FromImage : ImageOrientationValue

    @Serializable
    @SerialName("angle")
    data class Angle(val value: IRAngle, val flip: Boolean = false) : ImageOrientationValue
}

/**
 * Represents the CSS `image-orientation` property.
 * Corrects image orientation.
 */
@Serializable
data class ImageOrientationProperty(
    val value: ImageOrientationValue
) : IRProperty {
    override val propertyName = "image-orientation"
}

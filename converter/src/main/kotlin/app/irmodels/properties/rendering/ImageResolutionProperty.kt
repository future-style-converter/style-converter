package app.irmodels.properties.rendering

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ImageResolutionValue {
    @Serializable
    @SerialName("from-image")
    data object FromImage : ImageResolutionValue

    @Serializable
    @SerialName("dppx")
    data class Dppx(val value: Double) : ImageResolutionValue

    @Serializable
    @SerialName("keyword")
    data class Keyword(val keyword: String) : ImageResolutionValue

    @Serializable
    @SerialName("raw")
    data class Raw(val value: String) : ImageResolutionValue
}

/**
 * Represents the CSS `image-resolution` property.
 * Specifies intrinsic resolution of images (dppx).
 */
@Serializable
data class ImageResolutionProperty(
    val value: ImageResolutionValue
) : IRProperty {
    override val propertyName = "image-resolution"
}

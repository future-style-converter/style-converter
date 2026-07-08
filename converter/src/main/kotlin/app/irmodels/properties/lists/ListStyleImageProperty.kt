package app.irmodels.properties.lists

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ListStyleImageProperty(
    val image: ListImage
) : IRProperty {
    override val propertyName = "list-style-image"

    @Serializable
    sealed interface ListImage {
        @Serializable
        @SerialName("none")
        data class None(val unit: kotlin.Unit = kotlin.Unit) : ListImage

        @Serializable
        @SerialName("url")
        data class Url(val url: String) : ListImage
    }
}

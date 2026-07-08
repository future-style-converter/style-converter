package app.irmodels.properties.background

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BackgroundAttachmentProperty(
    val attachments: List<Attachment>
) : IRProperty {
    override val propertyName = "background-attachment"

    @Serializable
    sealed interface Attachment {
        @Serializable
        @SerialName("scroll")
        data object SCROLL : Attachment

        @Serializable
        @SerialName("fixed")
        data object FIXED : Attachment

        @Serializable
        @SerialName("local")
        data object LOCAL : Attachment

        @Serializable
        @SerialName("global-keyword")
        data class GlobalKeyword(val keyword: String) : Attachment
    }
}

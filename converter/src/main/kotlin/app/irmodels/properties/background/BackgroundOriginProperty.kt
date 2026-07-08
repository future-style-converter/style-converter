package app.irmodels.properties.background

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BackgroundOriginProperty(
    val origins: List<Origin>
) : IRProperty {
    override val propertyName = "background-origin"

    @Serializable
    sealed interface Origin {
        @Serializable
        @SerialName("border-box")
        data object BORDER_BOX : Origin

        @Serializable
        @SerialName("padding-box")
        data object PADDING_BOX : Origin

        @Serializable
        @SerialName("content-box")
        data object CONTENT_BOX : Origin

        @Serializable
        @SerialName("global")
        data class GlobalKeyword(val keyword: String) : Origin
    }
}

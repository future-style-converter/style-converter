package app.irmodels.properties.print

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface BookmarkTargetValue {
    @Serializable
    @SerialName("self")
    data object Self : BookmarkTargetValue

    @Serializable
    @SerialName("url")
    data class Url(val url: String) : BookmarkTargetValue

    @Serializable
    @SerialName("attr")
    data class Attr(val attributeName: String) : BookmarkTargetValue
}

/**
 * Represents the CSS `bookmark-target` property.
 * Specifies target of PDF bookmark link.
 */
@Serializable
data class BookmarkTargetProperty(
    val value: BookmarkTargetValue
) : IRProperty {
    override val propertyName = "bookmark-target"
}

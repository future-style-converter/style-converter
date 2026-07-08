package app.irmodels.properties.print

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface BookmarkLabelValue {
    @Serializable
    @SerialName("content")
    data class Content(val value: String) : BookmarkLabelValue

    @Serializable
    @SerialName("attr")
    data class Attr(val attributeName: String) : BookmarkLabelValue
}

/**
 * Represents the CSS `bookmark-label` property.
 * Specifies label for PDF bookmark.
 */
@Serializable
data class BookmarkLabelProperty(
    val value: BookmarkLabelValue
) : IRProperty {
    override val propertyName = "bookmark-label"
}

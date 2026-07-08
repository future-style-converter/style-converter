package app.irmodels.properties.print

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface BookmarkLevelValue {
    @Serializable
    @SerialName("none")
    data object None : BookmarkLevelValue

    @Serializable
    @SerialName("integer")
    data class Integer(val value: IRNumber) : BookmarkLevelValue
}

/**
 * Represents the CSS `bookmark-level` property.
 * Specifies level of bookmark in PDF outline.
 */
@Serializable
data class BookmarkLevelProperty(
    val value: BookmarkLevelValue
) : IRProperty {
    override val propertyName = "bookmark-level"
}

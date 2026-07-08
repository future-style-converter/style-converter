package app.irmodels.properties.print

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class BookmarkStateValue {
    OPEN,
    CLOSED
}

/**
 * Represents the CSS `bookmark-state` property.
 * Specifies whether PDF bookmark is initially open or closed.
 */
@Serializable
data class BookmarkStateProperty(
    val value: BookmarkStateValue
) : IRProperty {
    override val propertyName = "bookmark-state"
}

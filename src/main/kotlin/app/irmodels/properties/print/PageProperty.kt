package app.irmodels.properties.print

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface PageValue {
    @Serializable
    @SerialName("auto")
    data object Auto : PageValue

    @Serializable
    @SerialName("named")
    data class Named(val name: String) : PageValue
}

/**
 * Represents the CSS `page` property.
 * Specifies a named page type for an element.
 */
@Serializable
data class PageProperty(
    val value: PageValue
) : IRProperty {
    override val propertyName = "page"
}

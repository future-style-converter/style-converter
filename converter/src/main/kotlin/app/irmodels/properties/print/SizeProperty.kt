package app.irmodels.properties.print

import app.irmodels.IRLength
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Page size value for the CSS `size` property.
 * Named PageSizeValue to avoid conflict with SizeValue in ValueTypes.kt.
 */
@Serializable
sealed interface PageSizeValue {
    @Serializable
    @SerialName("auto")
    data object Auto : PageSizeValue

    @Serializable
    @SerialName("page-size")
    data class Dimensions(val width: IRLength, val height: IRLength? = null) : PageSizeValue

    @Serializable
    @SerialName("named")
    data class Named(val name: String, val orientation: String? = null) : PageSizeValue
}

/**
 * Represents the CSS `size` property.
 * Specifies page size and orientation for @page.
 */
@Serializable
data class SizeProperty(
    val value: PageSizeValue
) : IRProperty {
    override val propertyName = "size"
}

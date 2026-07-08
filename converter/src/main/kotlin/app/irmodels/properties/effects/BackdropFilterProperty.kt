package app.irmodels.properties.effects

import app.irmodels.IRProperty
import app.irmodels.properties.color.FilterFunction
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `backdrop-filter` property.
 * Uses the shared FilterFunction type from color package.
 */
@Serializable
data class BackdropFilterProperty(
    val filters: List<FilterFunction>? = null,
    val raw: String? = null,  // For complex expressions with var(), etc.
    val keyword: String? = null  // For inherit, initial, unset, etc.
) : IRProperty {
    override val propertyName = "backdrop-filter"

    companion object {
        fun fromFilters(filters: List<FilterFunction>) = BackdropFilterProperty(filters = filters)
        fun fromRaw(value: String) = BackdropFilterProperty(raw = value)
        fun fromKeyword(keyword: String) = BackdropFilterProperty(keyword = keyword)
    }
}

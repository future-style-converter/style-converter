package app.irmodels.properties.color

import app.irmodels.*
import kotlinx.serialization.Serializable

@Serializable
data class FilterProperty(
    val filters: FilterValue
) : IRProperty {
    override val propertyName = "filter"

    @Serializable(with = FilterValueSerializer::class)
    sealed interface FilterValue {
        @Serializable data class None(val unit: Unit = Unit) : FilterValue
        @Serializable data class FilterList(val functions: List<FilterFunction>) : FilterValue
        @Serializable data class UrlReference(val url: String) : FilterValue
        @Serializable data class Keyword(val keyword: String) : FilterValue
        @Serializable data class Raw(val value: String) : FilterValue // For complex expressions with var(), etc.
    }
}

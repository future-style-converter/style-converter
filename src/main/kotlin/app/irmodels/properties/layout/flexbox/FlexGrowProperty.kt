package app.irmodels.properties.layout.flexbox

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class FlexGrowProperty(
    val value: FlexGrowValue,
    /** Normalized value as Double for cross-platform use. Null if expression/keyword. */
    val normalizedValue: Double? = null
) : IRProperty {
    override val propertyName = "flex-grow"

    @Serializable
    sealed interface FlexGrowValue {
        @Serializable data class Number(val value: Double) : FlexGrowValue
        @Serializable data class Expression(val expr: String) : FlexGrowValue
        @Serializable data class Keyword(val keyword: String) : FlexGrowValue
    }

    companion object {
        fun fromNumber(value: Double) = FlexGrowProperty(
            value = FlexGrowValue.Number(value),
            normalizedValue = value
        )
        fun fromExpression(expr: String) = FlexGrowProperty(
            value = FlexGrowValue.Expression(expr),
            normalizedValue = null
        )
        fun fromKeyword(keyword: String) = FlexGrowProperty(
            value = FlexGrowValue.Keyword(keyword),
            normalizedValue = null
        )
    }
}

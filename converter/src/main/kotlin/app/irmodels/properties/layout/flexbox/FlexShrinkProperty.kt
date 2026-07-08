package app.irmodels.properties.layout.flexbox

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class FlexShrinkProperty(
    val value: FlexShrinkValue,
    /** Normalized value as Double for cross-platform use. Null if expression/keyword. */
    val normalizedValue: Double? = null
) : IRProperty {
    override val propertyName = "flex-shrink"

    @Serializable
    sealed interface FlexShrinkValue {
        @Serializable data class Number(val value: Double) : FlexShrinkValue
        @Serializable data class Expression(val expr: String) : FlexShrinkValue
        @Serializable data class Keyword(val keyword: String) : FlexShrinkValue
    }

    companion object {
        fun fromNumber(value: Double) = FlexShrinkProperty(
            value = FlexShrinkValue.Number(value),
            normalizedValue = value
        )
        fun fromExpression(expr: String) = FlexShrinkProperty(
            value = FlexShrinkValue.Expression(expr),
            normalizedValue = null
        )
        fun fromKeyword(keyword: String) = FlexShrinkProperty(
            value = FlexShrinkValue.Keyword(keyword),
            normalizedValue = null
        )
    }
}

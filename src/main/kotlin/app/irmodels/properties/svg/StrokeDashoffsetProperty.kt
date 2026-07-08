package app.irmodels.properties.svg

import app.irmodels.IRLength
import app.irmodels.IRNumber
import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class StrokeDashoffsetProperty(
    val value: DashoffsetValue
) : IRProperty {
    override val propertyName = "stroke-dashoffset"

    constructor(offset: IRLength) : this(DashoffsetValue.LengthValue(offset))

    @Serializable
    sealed interface DashoffsetValue {
        @Serializable data class LengthValue(val length: IRLength) : DashoffsetValue
        @Serializable data class NumberValue(val number: IRNumber) : DashoffsetValue
        @Serializable data class PercentageValue(val percentage: IRPercentage) : DashoffsetValue
        @Serializable data class Keyword(val keyword: String) : DashoffsetValue
    }

    val offset: IRLength
        get() = when (value) {
            is DashoffsetValue.LengthValue -> value.length
            is DashoffsetValue.NumberValue -> IRLength.fromPx(value.number.value)
            is DashoffsetValue.PercentageValue -> IRLength.fromRelative(value.percentage.value, IRLength.LengthUnit.PERCENT)
            else -> IRLength.fromPx(0.0)
        }
}

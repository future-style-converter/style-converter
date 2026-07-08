package app.irmodels.properties.svg

import app.irmodels.IRLength
import app.irmodels.IRNumber
import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class StrokeDasharrayProperty(
    val value: DasharrayValue
) : IRProperty {
    override val propertyName = "stroke-dasharray"

    constructor(dasharray: List<IRLength>) : this(DasharrayValue.Lengths(dasharray))

    @Serializable
    sealed interface DasharrayValue {
        @Serializable data class None(val unit: Unit = Unit) : DasharrayValue
        @Serializable data class Lengths(val list: List<IRLength>) : DasharrayValue
        @Serializable data class Numbers(val list: List<IRNumber>) : DasharrayValue
        @Serializable data class Mixed(val raw: String) : DasharrayValue // For complex values
        @Serializable data class Keyword(val keyword: String) : DasharrayValue
    }

    val dasharray: List<IRLength>
        get() = when (value) {
            is DasharrayValue.Lengths -> value.list
            else -> emptyList()
        }
}

package app.irmodels.properties.spacing

import app.irmodels.*
import app.irmodels.IRLength
import app.irmodels.IRPercentage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `gap` property (shorthand for row-gap and column-gap).
 */
@Serializable
data class GapProperty(
    val gap: LengthPercentageOrNormal
) : IRProperty {
    override val propertyName = "gap"

    /**
     * Gap value can be length, percentage, "normal" keyword, or raw (for calc, var, etc.).
     */
    @Serializable
    sealed interface LengthPercentageOrNormal {
        @Serializable
        @SerialName("length")
        data class Length(val value: IRLength) : LengthPercentageOrNormal

        @Serializable
        @SerialName("percentage")
        data class Percentage(val value: IRPercentage) : LengthPercentageOrNormal

        @Serializable
        @SerialName("normal")
        data class Normal(val unit: Unit = Unit) : LengthPercentageOrNormal

        @Serializable
        @SerialName("raw")
        data class Raw(val value: String) : LengthPercentageOrNormal
    }
}

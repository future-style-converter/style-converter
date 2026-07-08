package app.irmodels.properties.table

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BorderSpacingProperty(
    val spacing: Spacing
) : IRProperty {
    override val propertyName = "border-spacing"

    @Serializable
    sealed interface Spacing {
        @SerialName("single")
        @Serializable
        data class Single(val length: IRLength) : Spacing

        @SerialName("two-values")
        @Serializable
        data class TwoValues(
            val horizontal: IRLength,
            val vertical: IRLength
        ) : Spacing
    }
}

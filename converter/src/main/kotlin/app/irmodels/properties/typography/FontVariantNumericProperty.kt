package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class FontVariantNumericProperty(
    val values: List<NumericVariant>
) : IRProperty {
    override val propertyName = "font-variant-numeric"

    enum class NumericVariant {
        NORMAL,
        ORDINAL,
        SLASHED_ZERO,
        LINING_NUMS,
        OLDSTYLE_NUMS,
        PROPORTIONAL_NUMS,
        TABULAR_NUMS,
        DIAGONAL_FRACTIONS,
        STACKED_FRACTIONS
    }
}

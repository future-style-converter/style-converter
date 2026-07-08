package app.irmodels.properties.columns

import app.irmodels.IRProperty
import app.irmodels.properties.spacing.GapProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `column-gap` property.
 * Uses shared LengthPercentageOrNormal type from GapProperty.
 */
@Serializable
data class ColumnGapProperty(
    val gap: GapProperty.LengthPercentageOrNormal
) : IRProperty {
    override val propertyName = "column-gap"
}

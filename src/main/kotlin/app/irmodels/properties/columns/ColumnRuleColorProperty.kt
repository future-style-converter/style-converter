package app.irmodels.properties.columns

import app.irmodels.*
import kotlinx.serialization.Serializable

@Serializable
data class ColumnRuleColorProperty(
    val color: IRColor
) : IRProperty {
    override val propertyName = "column-rule-color"
}

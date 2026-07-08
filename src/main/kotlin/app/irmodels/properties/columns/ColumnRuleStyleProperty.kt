package app.irmodels.properties.columns

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class ColumnRuleStyleProperty(
    val style: RuleStyle
) : IRProperty {
    override val propertyName = "column-rule-style"

    enum class RuleStyle {
        NONE, HIDDEN, DOTTED, DASHED, SOLID,
        DOUBLE, GROOVE, RIDGE, INSET, OUTSET
    }
}

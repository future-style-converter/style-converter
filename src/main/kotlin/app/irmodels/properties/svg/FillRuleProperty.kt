package app.irmodels.properties.svg

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class FillRule {
    NONZERO,
    EVENODD
}

@Serializable
data class FillRuleProperty(
    val rule: FillRule
) : IRProperty {
    override val propertyName = "fill-rule"
}

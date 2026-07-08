package app.irmodels.properties.table

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class BorderCollapseProperty(
    val collapse: BorderCollapse
) : IRProperty {
    override val propertyName = "border-collapse"

    enum class BorderCollapse {
        SEPARATE, COLLAPSE
    }
}

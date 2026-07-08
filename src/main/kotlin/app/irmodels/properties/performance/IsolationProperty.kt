package app.irmodels.properties.performance

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class IsolationProperty(
    val isolation: Isolation
) : IRProperty {
    override val propertyName = "isolation"

    enum class Isolation {
        AUTO,
        ISOLATE
    }
}

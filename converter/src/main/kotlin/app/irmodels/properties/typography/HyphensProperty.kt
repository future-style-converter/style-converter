package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class HyphensProperty(
    val value: Hyphens
) : IRProperty {
    override val propertyName = "hyphens"

    enum class Hyphens {
        NONE, MANUAL, AUTO
    }
}

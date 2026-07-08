package app.irmodels.properties.svg

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class StrokeMiterlimitProperty(
    val miterlimit: IRNumber  // >= 1.0
) : IRProperty {
    override val propertyName = "stroke-miterlimit"
}

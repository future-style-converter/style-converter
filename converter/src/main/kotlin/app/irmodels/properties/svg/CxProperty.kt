package app.irmodels.properties.svg

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class CxProperty(val value: SvgLengthValue) : IRProperty {
    override val propertyName = "cx"
}

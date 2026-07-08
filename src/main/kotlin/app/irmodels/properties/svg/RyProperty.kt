package app.irmodels.properties.svg

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class RyProperty(val value: SvgLengthValue) : IRProperty {
    override val propertyName = "ry"
}

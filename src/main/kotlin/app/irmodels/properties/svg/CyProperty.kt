package app.irmodels.properties.svg

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class CyProperty(val value: SvgLengthValue) : IRProperty {
    override val propertyName = "cy"
}

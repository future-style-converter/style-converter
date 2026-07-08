package app.irmodels.properties.effects

import app.irmodels.*
import app.irmodels.IRUrl
import kotlinx.serialization.Serializable

@Serializable
data class MaskBorderSourceProperty(
    val source: IRUrl
) : IRProperty {
    override val propertyName = "mask-border-source"
}

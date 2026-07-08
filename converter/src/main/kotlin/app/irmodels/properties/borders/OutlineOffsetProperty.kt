package app.irmodels.properties.borders

import app.irmodels.IRLength
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class OutlineOffsetProperty(
    val offset: IRLength
) : IRProperty {
    override val propertyName = "outline-offset"
}

package app.irmodels.properties.sizing

import app.irmodels.*
import kotlinx.serialization.Serializable

/** CSS `max-inline-size` property. */
@Serializable
data class MaxInlineSizeProperty(val size: SizeValue) : IRProperty {
    override val propertyName = "max-inline-size"
}

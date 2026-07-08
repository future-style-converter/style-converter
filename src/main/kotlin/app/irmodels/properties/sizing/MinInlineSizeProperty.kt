package app.irmodels.properties.sizing

import app.irmodels.*
import kotlinx.serialization.Serializable

/** CSS `min-inline-size` property. */
@Serializable
data class MinInlineSizeProperty(val size: SizeValue) : IRProperty {
    override val propertyName = "min-inline-size"
}

package app.irmodels.properties.sizing

import app.irmodels.*
import kotlinx.serialization.Serializable

/** CSS `inline-size` property. */
@Serializable
data class InlineSizeProperty(val size: SizeValue) : IRProperty {
    override val propertyName = "inline-size"
}

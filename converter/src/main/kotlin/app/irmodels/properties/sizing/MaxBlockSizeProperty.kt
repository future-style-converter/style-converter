package app.irmodels.properties.sizing

import app.irmodels.*
import kotlinx.serialization.Serializable

/** CSS `max-block-size` property. */
@Serializable
data class MaxBlockSizeProperty(val size: SizeValue) : IRProperty {
    override val propertyName = "max-block-size"
}

package app.irmodels.properties.sizing

import app.irmodels.*
import kotlinx.serialization.Serializable

/** CSS `min-block-size` property. */
@Serializable
data class MinBlockSizeProperty(val size: SizeValue) : IRProperty {
    override val propertyName = "min-block-size"
}

package app.irmodels.properties.sizing

import app.irmodels.*
import kotlinx.serialization.Serializable

/** CSS `block-size` property. */
@Serializable
data class BlockSizeProperty(val size: SizeValue) : IRProperty {
    override val propertyName = "block-size"
}

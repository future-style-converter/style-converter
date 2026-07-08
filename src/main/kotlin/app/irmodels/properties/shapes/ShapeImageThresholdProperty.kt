package app.irmodels.properties.shapes

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `shape-image-threshold` property.
 * Sets alpha channel threshold for extracting shape from image.
 */
@Serializable
data class ShapeImageThresholdProperty(
    val threshold: IRNumber  // 0.0 to 1.0
) : IRProperty {
    override val propertyName = "shape-image-threshold"
}

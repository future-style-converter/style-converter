package app.irmodels.properties.rendering

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class PrintColorAdjustValue {
    ECONOMY,
    EXACT
}

/**
 * Represents the CSS `print-color-adjust` property.
 * Controls color adjustments when printing.
 */
@Serializable
data class PrintColorAdjustProperty(
    val value: PrintColorAdjustValue
) : IRProperty {
    override val propertyName = "print-color-adjust"
}

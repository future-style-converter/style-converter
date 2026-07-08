package app.irmodels.properties.math

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class MathShiftValue {
    NORMAL,
    COMPACT
}

/**
 * Represents the CSS `math-shift` property.
 * Specifies whether superscripts should be shifted in a compact style.
 */
@Serializable
data class MathShiftProperty(
    val value: MathShiftValue
) : IRProperty {
    override val propertyName = "math-shift"
}

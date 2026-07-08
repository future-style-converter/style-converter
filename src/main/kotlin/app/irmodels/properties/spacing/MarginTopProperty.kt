package app.irmodels.properties.spacing

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * CSS `margin-top` property - sets top margin spacing.
 *
 * Uses MarginValue which supports:
 * - `Length`: Absolute length (normalized to pixels when possible)
 * - `Percentage`: Relative to containing block width
 * - `Auto`: Automatic margin calculation
 * - `Expression`: CSS expressions like calc() or var()
 *
 * @see MarginValue for value type details
 */
@Serializable
data class MarginTopProperty(
    val margin: MarginValue
) : IRProperty {
    override val propertyName = "margin-top"
}

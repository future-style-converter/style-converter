package app.irmodels.properties.table

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `empty-cells` property.
 *
 * ## CSS Property
 * **Syntax**: `empty-cells: show | hide`
 *
 * ## Description
 * Sets whether borders and backgrounds appear around cells that have no visible content.
 *
 * @property value The empty-cells value
 * @see [MDN empty-cells](https://developer.mozilla.org/en-US/docs/Web/CSS/empty-cells)
 */
@Serializable
data class EmptyCellsProperty(
    val value: EmptyCells
) : IRProperty {
    override val propertyName = "empty-cells"

    enum class EmptyCells {
        SHOW,
        HIDE
    }
}

package app.irmodels.properties.layout.grid

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `grid-template` property.
 *
 * ## CSS Property
 * **Syntax**: `grid-template: <grid-template-rows> / <grid-template-columns> | <grid-template-areas>`
 *
 * ## Description
 * Shorthand property for grid-template-rows, grid-template-columns, and grid-template-areas.
 * Defines the grid structure with rows, columns, and named areas.
 *
 * ## Examples
 * ```kotlin
 * GridTemplateProperty(
 *     rows = "100px 1fr",
 *     columns = "200px 1fr 1fr",
 *     areas = null
 * )
 * ```
 *
 * @property rows Grid template rows (optional)
 * @property columns Grid template columns (optional)
 * @property areas Grid template areas (optional)
 * @see [MDN grid-template](https://developer.mozilla.org/en-US/docs/Web/CSS/grid-template)
 */
@Serializable
data class GridTemplateProperty(
    val rows: String? = null,
    val columns: String? = null,
    val areas: String? = null
) : IRProperty {
    override val propertyName = "grid-template"
}

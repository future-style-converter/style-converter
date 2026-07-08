package app.irmodels.properties.columns

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `column-span` property.
 *
 * ## CSS Property
 * **Syntax**: `column-span: none | all`
 *
 * ## Description
 * Determines whether an element should span across all columns in a multi-column layout.
 *
 * ## Examples
 * ```kotlin
 * ColumnSpanProperty(span = ColumnSpan.ALL)  // Element spans all columns
 * ColumnSpanProperty(span = ColumnSpan.NONE) // Element stays in one column
 * ```
 *
 * @property span The column span value
 * @see [MDN column-span](https://developer.mozilla.org/en-US/docs/Web/CSS/column-span)
 */
@Serializable
data class ColumnSpanProperty(
    val span: ColumnSpan
) : IRProperty {
    override val propertyName = "column-span"
}

/**
 * Represents column-span values.
 */
@Serializable
enum class ColumnSpan {
    /**
     * Element does not span across columns (default).
     */
    NONE,

    /**
     * Element spans across all columns, creating a spanning element.
     */
    ALL
}

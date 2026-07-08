package app.irmodels.properties.columns

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `column-fill` property.
 *
 * ## CSS Property
 * **Syntax**: `column-fill: auto | balance | balance-all`
 *
 * ## Description
 * Controls how content is distributed across columns. Determines whether columns
 * should be filled sequentially or balanced.
 *
 * ## Examples
 * ```kotlin
 * ColumnFillProperty(fill = ColumnFill.BALANCE)
 * ColumnFillProperty(fill = ColumnFill.AUTO)
 * ```
 *
 * @property fill The column fill behavior
 * @see [MDN column-fill](https://developer.mozilla.org/en-US/docs/Web/CSS/column-fill)
 */
@Serializable
data class ColumnFillProperty(
    val fill: ColumnFill
) : IRProperty {
    override val propertyName = "column-fill"
}

/**
 * Represents column-fill values.
 */
@Serializable
enum class ColumnFill {
    /**
     * Columns are filled sequentially. Content fills the first column,
     * then moves to the next.
     */
    AUTO,

    /**
     * Content is divided equally between columns, balancing their heights.
     */
    BALANCE,

    /**
     * Like balance, but also balances the last row when there are multiple fragments.
     */
    BALANCE_ALL
}

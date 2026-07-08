package com.styleconverter.test.style.columns

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * Configuration for CSS multi-column layout properties.
 *
 * ## Supported Properties
 * - column-count: Number of columns
 * - column-width: Preferred column width
 * - column-gap: Gap between columns
 * - column-rule-width: Rule line width
 * - column-rule-style: Rule line style
 * - column-rule-color: Rule line color
 * - column-span: Span all columns
 * - column-fill: How to fill columns
 *
 * ## Compose Mapping
 * Compose doesn't have native multi-column layout. This config can be used
 * with LazyVerticalGrid or custom layouts.
 */
data class MultiColumnConfig(
    /** Number of columns (null = auto based on width) */
    val columnCount: Int? = null,
    /** Preferred column width */
    val columnWidth: Dp? = null,
    /** Gap between columns */
    val columnGap: Dp? = null,
    /** Rule line width */
    val ruleWidth: Dp? = null,
    /** Rule line style */
    val ruleStyle: ColumnRuleStyle = ColumnRuleStyle.NONE,
    /** Rule line color */
    val ruleColor: Color? = null,
    /** Whether element spans all columns */
    val span: ColumnSpan = ColumnSpan.NONE,
    /** How columns are filled */
    val fill: ColumnFill = ColumnFill.BALANCE
) {
    val hasMultiColumn: Boolean
        get() = columnCount != null || columnWidth != null

    val hasRule: Boolean
        get() = ruleStyle != ColumnRuleStyle.NONE && ruleWidth != null && ruleWidth.value > 0

    /** Get effective column count based on count and width */
    fun getEffectiveColumnCount(containerWidth: Dp): Int {
        if (columnCount != null) return columnCount

        if (columnWidth != null && columnWidth.value > 0) {
            val gap = columnGap?.value ?: 0f
            val availableWidth = containerWidth.value
            return maxOf(1, ((availableWidth + gap) / (columnWidth.value + gap)).toInt())
        }

        return 1
    }
}

/**
 * Column rule style values.
 */
enum class ColumnRuleStyle {
    NONE, HIDDEN, SOLID, DASHED, DOTTED, DOUBLE,
    GROOVE, RIDGE, INSET, OUTSET
}

/**
 * Column span values.
 */
enum class ColumnSpan {
    /** Element does not span columns */
    NONE,
    /** Element spans all columns */
    ALL
}

/**
 * Column fill values.
 */
enum class ColumnFill {
    /** Fill columns sequentially */
    AUTO,
    /** Balance content across columns */
    BALANCE,
    /** Balance all columns including last */
    BALANCE_ALL
}

package com.styleconverter.runtime.columns

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
    val fill: ColumnFill = ColumnFill.BALANCE,
    /**
     * True when the container's writing-mode is vertical (vertical-rl/-lr,
     * sideways-rl/-lr). The css-break-3 fragmentation pass in
     * [MultiColumnApplier] is horizontal-tb only (vertical modes are
     * classified blocked-platform), so it bails — with a one-time log — when
     * this flag is set. Extracted from the WritingMode IR property by
     * [MultiColumnExtractor].
     */
    val verticalWritingMode: Boolean = false,
    /**
     * Wave-47 lane Z2 — true when the vertical writing mode's BLOCK axis
     * runs right→left (`vertical-rl` / `sideways-rl`, css-writing-modes-4
     * §6.4): the vertical fragmentation pass walks the child's physical
     * bands leftward and right-aligns the partial one. Meaningful only
     * when [verticalWritingMode] is true; false otherwise.
     */
    val verticalBlockRtl: Boolean = false,
    /**
     * True when the container declares `continue: discard` (css-overflow-4
     * §3, the IR `Continue` keyword — wave-42 lane W4): content that lands
     * in an OVERFLOW column (css-multicol-1 §8.1), and everything after it
     * in flow order, is discarded instead of painted. Consumed by the
     * spanner-flow plan (MulticolSpannerFlow.plan's discardOverflow);
     * default false keeps every other caller byte-identical.
     */
    val continueDiscard: Boolean = false
) {
    val hasMultiColumn: Boolean
        get() = columnCount != null || columnWidth != null

    val hasRule: Boolean
        get() = ruleStyle != ColumnRuleStyle.NONE && ruleWidth != null && ruleWidth.value > 0

    /** Get effective column count based on count and width */
    fun getEffectiveColumnCount(containerWidth: Dp): Int {
        // column-count is a positive <integer> per css-multicol §3.2 — coerce so a
        // malformed 0/negative count can never reach the layout dividers downstream
        // (SimpleColumnGrid's rowCount = (n + count - 1) / count would divide by zero).
        if (columnCount != null) return maxOf(1, columnCount)

        if (columnWidth != null && columnWidth.value > 0) {
            // Negative gaps are invalid CSS (css-align §8); unguarded they could zero
            // or flip the divisor below — floor at 0.
            val gap = maxOf(0f, columnGap?.value ?: 0f)
            val availableWidth = containerWidth.value
            // css-multicol §3.4 auto-count fitting: floor((available + gap) / (width + gap)).
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

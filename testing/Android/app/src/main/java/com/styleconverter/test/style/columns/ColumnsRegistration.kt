package com.styleconverter.test.style.columns

// Phase 10 facade — MultiColumnExtractor already produces a full
// MultiColumnConfig; MultiColumnApplier already lays out `column-count` /
// `column-width` via a FlowRow-style renderer. This facade just claims
// the eight column-* IR property names on the PropertyRegistry.

import com.styleconverter.test.style.PropertyRegistry

/**
 * Registers 8 column-* CSS Multicol IR properties under the `columns`
 * owner.
 */
object ColumnsRegistration {

    init {
        PropertyRegistry.migrated(
            "ColumnCount", "ColumnWidth", "ColumnGap",
            "ColumnRuleWidth", "ColumnRuleStyle", "ColumnRuleColor",
            "ColumnSpan", "ColumnFill",
            owner = "columns"
        )
    }
}

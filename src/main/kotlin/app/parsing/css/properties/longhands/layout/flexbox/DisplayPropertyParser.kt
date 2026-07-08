package app.parsing.css.properties.longhands.layout.flexbox

import app.irmodels.IRProperty
import app.irmodels.properties.layout.flexbox.DisplayProperty
import app.parsing.css.properties.longhands.PropertyParser

object DisplayPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val displayValue = when (trimmed) {
            "none" -> DisplayProperty.DisplayValue.NONE
            "block" -> DisplayProperty.DisplayValue.BLOCK
            "inline" -> DisplayProperty.DisplayValue.INLINE
            "inline-block" -> DisplayProperty.DisplayValue.INLINE_BLOCK
            "flex" -> DisplayProperty.DisplayValue.FLEX
            "inline-flex" -> DisplayProperty.DisplayValue.INLINE_FLEX
            "grid" -> DisplayProperty.DisplayValue.GRID
            "inline-grid" -> DisplayProperty.DisplayValue.INLINE_GRID
            "table" -> DisplayProperty.DisplayValue.TABLE
            "inline-table" -> DisplayProperty.DisplayValue.INLINE_TABLE
            "table-row" -> DisplayProperty.DisplayValue.TABLE_ROW
            "table-cell" -> DisplayProperty.DisplayValue.TABLE_CELL
            "table-column" -> DisplayProperty.DisplayValue.TABLE_COLUMN
            "table-row-group" -> DisplayProperty.DisplayValue.TABLE_ROW_GROUP
            "table-column-group" -> DisplayProperty.DisplayValue.TABLE_COLUMN_GROUP
            "table-header-group" -> DisplayProperty.DisplayValue.TABLE_HEADER_GROUP
            "table-footer-group" -> DisplayProperty.DisplayValue.TABLE_FOOTER_GROUP
            "table-caption" -> DisplayProperty.DisplayValue.TABLE_CAPTION
            "list-item" -> DisplayProperty.DisplayValue.LIST_ITEM
            "contents" -> DisplayProperty.DisplayValue.CONTENTS
            "flow-root" -> DisplayProperty.DisplayValue.FLOW_ROOT
            "ruby" -> DisplayProperty.DisplayValue.RUBY
            "ruby-base" -> DisplayProperty.DisplayValue.RUBY_BASE
            "ruby-text" -> DisplayProperty.DisplayValue.RUBY_TEXT
            "ruby-base-container" -> DisplayProperty.DisplayValue.RUBY_BASE_CONTAINER
            "ruby-text-container" -> DisplayProperty.DisplayValue.RUBY_TEXT_CONTAINER
            // Two-value syntax: map to inner display type
            "block flex" -> DisplayProperty.DisplayValue.FLEX
            "block grid" -> DisplayProperty.DisplayValue.GRID
            // Vendor prefixes
            "-webkit-box", "-webkit-flex" -> DisplayProperty.DisplayValue.FLEX
            "-ms-flexbox" -> DisplayProperty.DisplayValue.FLEX
            "-webkit-inline-box", "-webkit-inline-flex" -> DisplayProperty.DisplayValue.INLINE_FLEX
            else -> return null
        }
        return DisplayProperty(displayValue)
    }
}

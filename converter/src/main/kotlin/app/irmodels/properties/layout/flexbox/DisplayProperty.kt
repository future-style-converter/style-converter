package app.irmodels.properties.layout.flexbox

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class DisplayProperty(
    val value: DisplayValue
) : IRProperty {
    override val propertyName = "display"

    enum class DisplayValue {
        NONE, BLOCK, INLINE, INLINE_BLOCK,
        FLEX, INLINE_FLEX,
        GRID, INLINE_GRID,
        TABLE, INLINE_TABLE, TABLE_ROW, TABLE_CELL, TABLE_COLUMN,
        TABLE_ROW_GROUP, TABLE_COLUMN_GROUP, TABLE_HEADER_GROUP,
        TABLE_FOOTER_GROUP, TABLE_CAPTION,
        LIST_ITEM, CONTENTS, FLOW_ROOT,
        RUBY, RUBY_BASE, RUBY_TEXT, RUBY_BASE_CONTAINER, RUBY_TEXT_CONTAINER
    }
}
